import hashlib
import importlib
import io
import json
import pathlib
import sys
import tempfile
import unittest
import zipfile

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
