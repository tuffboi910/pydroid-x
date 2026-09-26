import importlib.util
import json
import pathlib
import tempfile
import unittest

RUNNER_PATH = pathlib.Path(__file__).parents[2] / "main" / "python" / "runner.py"
SPEC = importlib.util.spec_from_file_location("py4u_runner", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)

def messages(source):
    return [item["message"] for item in json.loads(runner.diagnose(source))]


class FakeBridge:
    def __init__(self):
        self.output = []
        self.exit_code = None

    def write(self, value, error):
        self.output.append(value)

    def readLine(self):
        return None

    def shouldStop(self):
        return False

    def exited(self, code):
        self.exit_code = code


class DiagnosticTests(unittest.TestCase):
    def test_exception_aliases_are_valid_in_each_handler(self):
        source = """
try:
    int('x')
except ValueError as error:
    print(error)
try:
    {}['missing']
except KeyError as second_error:
    print(second_error)
"""
        self.assertEqual([], messages(source))

    def test_real_undefined_name_is_still_reported(self):
        self.assertEqual(["Undefined name: missing_value"], messages("print(missing_value)\n"))

    def test_large_valid_program_has_zero_false_warnings(self):
        source = """
import json
from pathlib import Path as FilePath
GLOBAL_TOTAL = 3
class Ledger:
    scale = GLOBAL_TOTAL
    def __init__(self, values):
        self.values = values
    def calculate(self):
        local_offset = 2
        def nested(multiplier):
            nonlocal local_offset
            local_offset += 1
            return [value * multiplier + local_offset for value in self.values if value > 0]
        try:
            with FilePath(__file__).open('w') as handle:
                handle.write(json.dumps(nested(self.scale)))
        except (OSError, ValueError) as error:
            print(error)
        finally:
            print('finished')
        mapping = {key: value for key, value in enumerate(self.values)}
        transform = lambda item: mapping.get(item, GLOBAL_TOTAL)
        for index, value in enumerate(self.values):
            if index:
                print(transform(value))
        return mapping
def update_global():
    global GLOBAL_TOTAL
    GLOBAL_TOTAL += 1
ledger = Ledger([1, 2, 3])
ledger.calculate()
update_global()
"""
        self.assertEqual([], messages(source))

    def test_complex_program_keeps_genuine_errors(self):
        source = """
def outer(items):
    captured = 4
    def inner():
        return captured + actually_missing
    return [inner() for item in items]
"""
        self.assertEqual(["Undefined name: actually_missing"], messages(source))

    def test_unicode_before_name_uses_android_utf16_offsets(self):
        source = "label = '🐍'\nprint(not_defined)\n"
        issue = json.loads(runner.diagnose(source))[0]
        prefix = source[:source.index("not_defined")]
        expected = len(prefix.encode("utf-16-le")) // 2
        self.assertEqual(expected, issue["start"])
        self.assertEqual(2, issue["line"])
        self.assertEqual(7, issue["column"])

    def test_match_capture_is_defined(self):
        source = """
value = {"x": 3}
match value:
    case {"x": captured}:
        print(captured)
"""
        self.assertEqual([], messages(source))

    def test_comprehension_target_does_not_leak(self):
        source = "values = [1, 2]\nsquares = [item * item for item in values]\nprint(item)\n"
        self.assertEqual(["Undefined name: item"], messages(source))

    def test_function_local_does_not_leak_to_module(self):
        source = "def build():\n    secret = 3\n    return secret\nprint(secret)\n"
        self.assertEqual(["Undefined name: secret"], messages(source))

    def test_nested_closure_resolves_outer_local(self):
        source = """
def outer():
    value = 4
    def inner():
        return value
    return inner()
"""
        self.assertEqual([], messages(source))

    def test_method_does_not_see_class_namespace_as_local(self):
        source = """
class Example:
    value = 4
    def read(self):
        return value
"""
        self.assertEqual(["Undefined name: value"], messages(source))

    def test_direct_function_call_arity_is_reported(self):
        source = """
def total(a, b, c):
    return a + b + c
value = total(1, 2)
"""
        self.assertEqual(
            ["total() expected 3 argument(s), got 2"],
            messages(source),
        )

    def test_valid_direct_function_call_has_no_arity_warning(self):
        source = """
def total(a, b=0):
    return a + b
print(total(1))
print(total(1, 2))
"""
        self.assertEqual([], messages(source))

    def test_builtin_arity_is_conservative(self):
        self.assertEqual(["len() expected 1 argument(s), got 2"], messages("len([], [])\n"))
        self.assertEqual([], messages("print(1, 2, 3)\n"))

    def test_syntax_errors_are_fatal_but_name_warnings_are_not(self):
        syntax = json.loads(runner.diagnose("if True print('x')\n"))[0]
        semantic = json.loads(runner.diagnose("return 3\n"))[0]
        name = json.loads(runner.diagnose("print(missing)\n"))[0]
        self.assertTrue(syntax["fatal"])
        self.assertTrue(semantic["fatal"])
        self.assertFalse(name["fatal"])

class RunnerExecutionTests(unittest.TestCase):
    def test_system_exit_uses_requested_exit_code_without_traceback(self):
        with tempfile.TemporaryDirectory() as project:
            bridge = FakeBridge()
            runner.run_code("raise SystemExit(7)\n", "main.py", project, bridge)
            self.assertEqual(7, bridge.exit_code)
            self.assertNotIn("Traceback", "".join(bridge.output))

    def test_project_import_is_reloaded_after_file_changes(self):
        with tempfile.TemporaryDirectory() as project:
            helper = pathlib.Path(project) / "helper.py"
            helper.write_text("VALUE = 1\n", encoding="utf-8")
            first = FakeBridge()
            runner.run_code("import helper\nprint(helper.VALUE)\n", "main.py", project, first)
            helper.write_text("VALUE = 2\n", encoding="utf-8")
            second = FakeBridge()
            runner.run_code("import helper\nprint(helper.VALUE)\n", "main.py", project, second)
            self.assertIn("1", "".join(first.output))
            self.assertIn("2", "".join(second.output))


if __name__ == "__main__":
    unittest.main()
