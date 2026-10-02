import hashlib
import importlib
import io
import json
import pathlib
import sys
import tempfile
import unittest
import zipfile
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).parents[2] / "main" / "python"))
import runtime_packages


def wheel_bytes(malicious=False, native=False):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as wheel:
        wheel.writestr("py4usample/__init__.py", "value = 42\n")
        wheel.writestr("py4usample-1.0.dist-info/METADATA", "Name: py4usample\nVersion: 1.0\n")
        wheel.writestr("py4usample-1.0.dist-info/WHEEL", "Wheel-Version: 1.0\nRoot-Is-Purelib: true\n")
        if malicious:
            wheel.writestr("../escaped.txt", "bad")
        if native:
            wheel.writestr("py4usample/native.so", b"native")
    return buffer.getvalue()


class FakeIndex:
    def __init__(self, wheel):
        self.wheel = wheel
        self.filename = "py4usample-1.0-py3-none-any.whl"
        self.release = {"filename": self.filename, "packagetype": "bdist_wheel",
                        "size": len(wheel), "url": "https://files.pythonhosted.org/" + self.filename,
                        "digests": {"sha256": hashlib.sha256(wheel).hexdigest()}}

    def __call__(self, url, timeout):
        if url.startswith("https://pypi.org/"):
            return io.BytesIO(json.dumps({"releases": {"1.0": [self.release]}}).encode())
        return io.BytesIO(self.wheel)


class RuntimePackageTests(unittest.TestCase):
    def test_runtime_package_state_and_import_take_precedence_over_bundled_copy(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            bundled = pathlib.Path(root) / "bundled-copy"
            bundled.mkdir()
            (bundled / "py4usample").mkdir()
            (bundled / "py4usample" / "__init__.py").write_text("value = -1\n")
            sys.path.insert(0, str(bundled))
            try:
                runtime_packages.install(["py4usample==1.0"], root, lambda _: None,
                                         FakeIndex(wheel_bytes()))
                self.assertEqual(42, importlib.import_module("py4usample").value)
                self.assertIn(("py4usample", "1.0", "runtime"), runtime_packages.package_state(root))
                self.assertIn("py4usample", runtime_packages.importable_module_state(root))
            finally:
                sys.path.remove(str(bundled))

    def test_late_commit_failure_rolls_back_earlier_distribution(self):
        def wheel(name, dependency=""):
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as archive:
                archive.writestr(name + "/__init__.py", "new")
                archive.writestr(name + "-1.0.dist-info/METADATA",
                                 "Name: %s\nVersion: 1.0\n%s" % (name, dependency))
                archive.writestr(name + "-1.0.dist-info/WHEEL", "Root-Is-Purelib: true\n")
            return buffer.getvalue()
        wheels = {"py4usample": wheel("py4usample", "Requires-Dist: py4uoptional==1.0\n"),
                  "py4uoptional": wheel("py4uoptional")}
        class Index:
            def __call__(self, url, timeout):
                name = "py4uoptional" if "py4uoptional" in url else "py4usample"
                data = wheels[name]
                if url.startswith("https://pypi.org/"):
                    filename = name + "-1.0-py3-none-any.whl"
                    return io.BytesIO(json.dumps({"releases": {"1.0": [{
                        "filename": filename, "packagetype": "bdist_wheel", "size": len(data),
                        "url": "https://files.pythonhosted.org/" + filename,
                        "digests": {"sha256": hashlib.sha256(data).hexdigest()}}]}}).encode())
                return io.BytesIO(data)
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            previous = pathlib.Path(root) / "py4uoptional"
            previous.mkdir()
            (previous / "old.txt").write_text("previous")
            real_replace = runtime_packages.os.replace
            def fail_second(source, destination):
                if destination == str(pathlib.Path(root) / "py4usample"):
                    raise OSError("simulated late failure")
                return real_replace(source, destination)
            messages = []
            with mock.patch.object(runtime_packages.os, "replace", side_effect=fail_second):
                with self.assertRaisesRegex(OSError, "simulated late failure"):
                    runtime_packages.install(["py4usample"], root, messages.append, Index())
            self.assertEqual("previous", (previous / "old.txt").read_text())
            self.assertFalse((pathlib.Path(root) / "py4usample").exists())
            self.assertFalse(any(item.startswith("Installed") for item in messages))

    def test_pure_wheel_extra_installs_declared_optional_dependency(self):
        def make_wheel(name, metadata):
            buffer = io.BytesIO()
            with zipfile.ZipFile(buffer, "w") as wheel:
                wheel.writestr(name + "/__init__.py", "value = 1\n")
                wheel.writestr(name + "-1.0.dist-info/METADATA", metadata)
                wheel.writestr(name + "-1.0.dist-info/WHEEL", "Root-Is-Purelib: true\n")
            return buffer.getvalue()
        parent = make_wheel("py4usample", "Name: py4usample\nVersion: 1.0\nProvides-Extra: feature\n"
                            "Requires-Dist: py4uoptional==1.0; extra == 'feature'\n")
        optional = make_wheel("py4uoptional", "Name: py4uoptional\nVersion: 1.0\n")
        class ExtrasIndex:
            def __call__(self, url, timeout):
                name = "py4uoptional" if "py4uoptional" in url else "py4usample"
                wheel = optional if name == "py4uoptional" else parent
                if url.startswith("https://pypi.org/"):
                    filename = name + "-1.0-py3-none-any.whl"
                    release = {"filename": filename, "packagetype": "bdist_wheel",
                               "size": len(wheel), "url": "https://files.pythonhosted.org/" + filename,
                               "digests": {"sha256": hashlib.sha256(wheel).hexdigest()}}
                    return io.BytesIO(json.dumps({"releases": {"1.0": [release]}}).encode())
                return io.BytesIO(wheel)
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            self.assertEqual(["py4uoptional 1.0", "py4usample 1.0"], runtime_packages.install(
                ["py4usample[feature]"], root, lambda _: None, ExtrasIndex()))
            self.assertTrue((pathlib.Path(root) / "py4uoptional").exists())
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            self.assertEqual(["py4uoptional 1.0", "py4usample 1.0"], runtime_packages.install(
                ["py4usample", "py4usample[feature]"], root, lambda _: None, ExtrasIndex()))
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "Unsupported extras"):
                runtime_packages.install(["py4usample[typo]"], root, lambda _: None, ExtrasIndex())

    def test_conflicting_requests_do_not_partially_install(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "Conflicting dependency constraints"):
                runtime_packages.install(["py4usample==1.0", "py4usample==2.0"], root,
                                         lambda _: None, FakeIndex(wheel_bytes()))
            self.assertFalse((pathlib.Path(root) / "py4usample").exists())

    def test_unavailable_dependency_does_not_install_parent(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as wheel:
            wheel.writestr("py4usample/__init__.py", "value = 1")
            wheel.writestr("py4usample-1.0.dist-info/METADATA",
                           "Name: py4usample\nVersion: 1.0\nRequires-Dist: missing-package==1\n")
            wheel.writestr("py4usample-1.0.dist-info/WHEEL", "Root-Is-Purelib: true\n")
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaises(ValueError):
                runtime_packages.install(["py4usample"], root, lambda _: None,
                                         FakeIndex(buffer.getvalue()))
            self.assertFalse((pathlib.Path(root) / "py4usample").exists())

    def test_normalized_release_key_is_used_without_key_error(self):
        index = FakeIndex(wheel_bytes())
        class NormalizedIndex:
            def __call__(self, url, timeout):
                if url.startswith("https://pypi.org/"):
                    return io.BytesIO(json.dumps({"releases": {"v1.0": [index.release]}}).encode())
                return io.BytesIO(index.wheel)
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            self.assertEqual(["py4usample 1.0"], runtime_packages.install(
                ["py4usample==1.0"], root, lambda _: None, NormalizedIndex()))

    def test_mismatched_internal_metadata_is_rejected(self):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as wheel:
            wheel.writestr("py4usample/__init__.py", "value = 1")
            wheel.writestr("wrong-1.0.dist-info/METADATA", "Name: py4usample\nVersion: 1.0\n")
            wheel.writestr("wrong-1.0.dist-info/WHEEL", "Root-Is-Purelib: true\n")
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "metadata directory"):
                runtime_packages.install(["py4usample"], root, lambda _: None, FakeIndex(buffer.getvalue()))

    def tearDown(self):
        sys.modules.pop("py4usample", None)
        sys.path[:] = [path for path in sys.path if "py4u-package-test" not in path]

    def test_installed_pure_wheel_imports(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            messages = []
            self.assertEqual(["py4usample 1.0"], runtime_packages.install(
                ["py4usample==1.0"], root, messages.append, FakeIndex(wheel_bytes())))
            self.assertEqual(42, importlib.import_module("py4usample").value)
            self.assertIn("Installed py4usample 1.0\n", messages)

    def test_traversal_wheel_rejected_without_install(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "unsafe path"):
                runtime_packages.install(["py4usample"], root, lambda _: None,
                                         FakeIndex(wheel_bytes(malicious=True)))
            self.assertFalse((pathlib.Path(root) / "py4usample").exists())

    def test_checksum_mismatch_rejected(self):
        index = FakeIndex(wheel_bytes())
        index.release["digests"]["sha256"] = "0" * 64
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "checksum"):
                runtime_packages.install(["py4usample"], root, lambda _: None, index)

    def test_native_payload_rejected_despite_universal_filename(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(ValueError, "native code"):
                runtime_packages.install(["py4usample"], root, lambda _: None,
                                         FakeIndex(wheel_bytes(native=True)))

    def test_stop_aborts_before_network_or_install(self):
        with tempfile.TemporaryDirectory(prefix="py4u-package-test-") as root:
            with self.assertRaisesRegex(InterruptedError, "stopped"):
                runtime_packages.install(["py4usample"], root, lambda _: None,
                                         FakeIndex(wheel_bytes()), cancelled=lambda: True)
            self.assertFalse((pathlib.Path(root) / "py4usample").exists())
