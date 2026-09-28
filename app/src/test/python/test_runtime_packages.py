import importlib.util
import io
import pathlib
import tempfile
import unittest
import zipfile

RUNNER_PATH = pathlib.Path(__file__).parents[2] / "main" / "python" / "runner.py"
SPEC = importlib.util.spec_from_file_location("py4u_runner_packages", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class RuntimePackageTests(unittest.TestCase):
    def test_package_spec_validation(self):
        self.assertEqual(("requests", None), runner._parse_package_spec("requests"))
        self.assertEqual(("humanize", "4.10.0"), runner._parse_package_spec("humanize==4.10.0"))
        for bad in ("", "../evil", "name >= 1", "https://example.com/x.whl"):
            with self.assertRaises(ValueError):
                runner._parse_package_spec(bad)

    def test_selects_only_universal_pure_python_wheels(self):
        payload = {"urls": [
            {"filename": "thing-1.0-cp314-cp314-android.whl", "size": 100, "url": "https://x/native"},
            {"filename": "thing-1.0-py3-none-any.whl", "size": 200, "url": "https://x/pure"},
        ]}
        self.assertEqual("thing-1.0-py3-none-any.whl", runner._select_universal_wheel(payload)["filename"])

    def test_rejects_native_files_and_path_traversal(self):
        for member in ("../escape.py", "pkg/native.so"):
            data = io.BytesIO()
            with zipfile.ZipFile(data, "w") as archive:
                archive.writestr(member, b"x")
            data.seek(0)
            with zipfile.ZipFile(data) as archive:
                with self.assertRaises(ValueError):
                    runner._safe_wheel_entries(archive)

    def test_accepts_normal_pure_python_wheel_entries(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as archive:
            archive.writestr("pkg/__init__.py", b"x=1")
            archive.writestr("pkg-1.0.dist-info/METADATA", b"Name: pkg\nVersion: 1.0\n")
        data.seek(0)
        with zipfile.ZipFile(data) as archive:
            self.assertEqual(2, len(runner._safe_wheel_entries(archive)))


if __name__ == "__main__":
    unittest.main()
