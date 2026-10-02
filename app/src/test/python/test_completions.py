import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest

PATH = pathlib.Path(__file__).parents[2] / "main" / "python" / "runner.py"
sys.path.insert(0, str(PATH.parent))
SPEC = importlib.util.spec_from_file_location("completion_runner", PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class CompletionTests(unittest.TestCase):
    def complete(self, source, name, cursor=None):
        cursor = len(source) if cursor is None else cursor
        offset = len(source[:cursor].encode("utf-16-le")) // 2
        with tempfile.TemporaryDirectory() as folder:
            result = json.loads(runner.complete(source, offset, folder))
        return next(item for item in result["items"] if item["label"].removesuffix("()") == name)

    def apply(self, source, item):
        raw = source.encode("utf-16-le")
        return (raw[:item["replace_start"]*2] + item["insert_text"].encode("utf-16-le") + raw[item["replace_end"]*2:]).decode("utf-16-le")

    def test_print_has_signature_docs_and_inside_parentheses_cursor(self):
        item = self.complete("pri", "print")
        self.assertEqual("print()", self.apply("pri", item))
        self.assertEqual(1, item["cursor_back"])
        self.assertIn("print(", item["signature"])
        self.assertTrue(item["doc"])

    def test_error_class_uses_exact_capitalization(self):
        item = self.complete("va", "ValueError")
        self.assertEqual("ValueError()", self.apply("va", item))

    def test_existing_parentheses_not_duplicated(self):
        item = self.complete("pri(123)", "print", 3)
        self.assertEqual("print(123)", self.apply("pri(123)", item))

    def test_replaces_remaining_identifier_after_caret(self):
        item = self.complete("prinx", "print", 3)
        self.assertEqual("print()", self.apply("prinx", item))

    def test_import_does_not_add_call_parentheses(self):
        source = "from builtins import ValueErr"
        item = self.complete(source, "ValueError")
        self.assertEqual("from builtins import ValueError", self.apply(source, item))

    def test_unicode_before_cursor_uses_utf16_offsets(self):
        source = "message = '😀'\npri"
        item = self.complete(source, "print")
        self.assertEqual("message = '😀'\nprint()", self.apply(source, item))

    def test_empty_results_are_valid_json(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual({}, json.loads(runner.complete("zxq_unlikely_name", 17, folder)))


if __name__ == "__main__":
    unittest.main()
