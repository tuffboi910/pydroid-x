import importlib.util
import json
import pathlib
import unittest

RUNNER_PATH = pathlib.Path(__file__).parents[2] / "main" / "python" / "runner.py"
SPEC = importlib.util.spec_from_file_location("py4u_runner", RUNNER_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)

def messages(source):
    return [item["message"] for item in json.loads(runner.diagnose(source))]

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

    def test_unicode_before_name_uses_character_offsets(self):
        source = "label = '🐍'\nprint(not_defined)\n"
        issue = json.loads(runner.diagnose(source))[0]
        self.assertEqual(source.index("not_defined"), issue["start"])
        self.assertEqual("not_defined", source[issue["start"]:issue["end"]])

if __name__ == "__main__":
    unittest.main()
