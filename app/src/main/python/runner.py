import builtins
import contextlib
import io
import os
import sys
import traceback
import json
import ast

def diagnose(source):
    """Return syntax errors and unresolved Python names with exact ranges."""
    try:
        tree = ast.parse(source)
    except SyntaxError as exc:
        lines = source.splitlines(True)
        line = max(1, exc.lineno or 1)
        start = sum(len(value) for value in lines[:line - 1]) + max(0, (exc.offset or 1) - 1)
        end = min(len(source), start + 1)
        return json.dumps([{"start": start, "end": end, "message": exc.msg}])

    defined = set(dir(builtins)) | {"__name__", "__file__"}
    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and isinstance(node.ctx, (ast.Store, ast.Param)):
            defined.add(node.id)
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            defined.add(node.name)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                defined.update(arg.arg for arg in node.args.posonlyargs + node.args.args + node.args.kwonlyargs)
                if node.args.vararg: defined.add(node.args.vararg.arg)
                if node.args.kwarg: defined.add(node.args.kwarg.arg)
        elif isinstance(node, ast.Import):
            defined.update(alias.asname or alias.name.split('.')[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom):
            defined.update(alias.asname or alias.name for alias in node.names if alias.name != '*')

    line_starts = [0]
    for index, char in enumerate(source):
        if char == '\n': line_starts.append(index + 1)
    issues = []
    seen = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load) and node.id not in defined:
            start = line_starts[node.lineno - 1] + node.col_offset
            end = start + len(node.id)
            key = (start, end)
            if key not in seen:
                seen.add(key)
                issues.append({"start": start, "end": end, "message": "Undefined name: " + node.id})
    return json.dumps(issues)

def complete(source, cursor, project_dir):
    """Return Jedi's best local completion as JSON. Runs fully offline."""
    try:
        import jedi
        before = source[:cursor]
        line = before.count('\n') + 1
        column = len(before.rsplit('\n', 1)[-1])
        path = os.path.join(project_dir, 'main.py')
        items = jedi.Script(source, path=path).complete(line, column)
        if not items:
            return '{}'
        item = items[0]
        suffix = item.complete
        cursor_back = 0
        if item.type in ('function', 'class') and not suffix.endswith(')'):
            suffix += '()'
            cursor_back = 1
        return json.dumps({
            'label': item.name + ('()' if cursor_back else ''),
            'suffix': suffix,
            'cursor_back': cursor_back,
            'type': item.type,
            'doc': item.docstring(raw=True)[:240]
        })
    except Exception:
        return '{}'

class _Stream(io.TextIOBase):
    def __init__(self, bridge, error=False):
        self.bridge = bridge
        self.error = error
    def write(self, value):
        if value:
            self.bridge.write(str(value), self.error)
        return len(value)
    def flush(self):
        return None

def run_code(source, filename, project_dir, bridge):
    old_input = builtins.input
    old_cwd = os.getcwd()
    out = _Stream(bridge, False)
    err = _Stream(bridge, True)
    def android_input(prompt=''):
        if prompt:
            out.write(prompt)
        value = bridge.readLine()
        if value is None:
            raise KeyboardInterrupt()
        out.write(str(value) + '\n')
        return str(value)
    try:
        os.makedirs(project_dir, exist_ok=True)
        os.chdir(project_dir)
        if project_dir not in sys.path:
            sys.path.insert(0, project_dir)
        builtins.input = android_input
        scope = {'__name__': '__main__', '__file__': filename}
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            exec(compile(source, filename, 'exec'), scope, scope)
        bridge.exited(0)
    except KeyboardInterrupt:
        err.write('\nProgram interrupted\n')
        bridge.exited(130)
    except BaseException:
        err.write(traceback.format_exc())
        bridge.exited(1)
    finally:
        builtins.input = old_input
        os.chdir(old_cwd)

def version():
    return sys.version
