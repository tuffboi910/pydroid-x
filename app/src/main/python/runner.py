import ast
import builtins
import contextlib
import importlib
import io
import json
import os
import shutil
import sys
import traceback


class _Scope:
    def __init__(self, kind, parent=None):
        self.kind = kind
        self.parent = parent
        self.defined = set()
        self.globals = set()
        self.nonlocals = set()
        self.wildcard_import = False

    @property
    def module(self):
        scope = self
        while scope.parent is not None:
            scope = scope.parent
        return scope


_SCOPE_NODES = (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)
_COMP_NODES = (ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp)


class _DeclarationCollector(ast.NodeVisitor):
    def __init__(self, scope):
        self.scope = scope

    def visit_Global(self, node):
        self.scope.globals.update(node.names)

    def visit_Nonlocal(self, node):
        self.scope.nonlocals.update(node.names)

    def visit_FunctionDef(self, node):
        return None

    visit_AsyncFunctionDef = visit_FunctionDef
    visit_ClassDef = visit_FunctionDef
    visit_Lambda = visit_FunctionDef
    visit_ListComp = visit_FunctionDef
    visit_SetComp = visit_FunctionDef
    visit_DictComp = visit_FunctionDef
    visit_GeneratorExp = visit_FunctionDef


def _find_nonlocal_scope(scope, name):
    parent = scope.parent
    while parent is not None:
        if parent.kind in ("function", "lambda", "comprehension") and name in parent.defined:
            return parent
        parent = parent.parent
    return None


class _DefinitionCollector(ast.NodeVisitor):
    def __init__(self, scope):
        self.scope = scope

    def _bind(self, name):
        if not name:
            return
        if name in self.scope.globals:
            self.scope.module.defined.add(name)
            return
        if name in self.scope.nonlocals:
            target = _find_nonlocal_scope(self.scope, name)
            if target is not None:
                target.defined.add(name)
            return
        self.scope.defined.add(name)

    def visit_Name(self, node):
        if isinstance(node.ctx, (ast.Store, ast.Param)):
            self._bind(node.id)

    def visit_FunctionDef(self, node):
        self._bind(node.name)

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_ClassDef(self, node):
        self._bind(node.name)

    def visit_Lambda(self, node):
        return None

    def visit_ListComp(self, node):
        return None

    visit_SetComp = visit_ListComp
    visit_DictComp = visit_ListComp
    visit_GeneratorExp = visit_ListComp

    def visit_Import(self, node):
        for alias in node.names:
            self._bind(alias.asname or alias.name.split(".")[0])

    def visit_ImportFrom(self, node):
        for alias in node.names:
            if alias.name == "*":
                self.scope.wildcard_import = True
            else:
                self._bind(alias.asname or alias.name)

    def visit_ExceptHandler(self, node):
        if node.name:
            self._bind(node.name)
        self.generic_visit(node)

    def visit_MatchAs(self, node):
        if node.name:
            self._bind(node.name)
        self.generic_visit(node)

    def visit_MatchStar(self, node):
        if node.name:
            self._bind(node.name)

    def visit_MatchMapping(self, node):
        if node.rest:
            self._bind(node.rest)
        self.generic_visit(node)


def _prepare_scope(scope, nodes, args=None):
    declarations = _DeclarationCollector(scope)
    for node in nodes:
        declarations.visit(node)

    if args is not None:
        for arg in args.posonlyargs + args.args + args.kwonlyargs:
            scope.defined.add(arg.arg)
        if args.vararg:
            scope.defined.add(args.vararg.arg)
        if args.kwarg:
            scope.defined.add(args.kwarg.arg)

    definitions = _DefinitionCollector(scope)
    for node in nodes:
        definitions.visit(node)


def _resolve(name, scope):
    if name in dir(builtins) or name in {"__name__", "__file__"}:
        return True

    if name in scope.globals:
        module = scope.module
        return name in module.defined or module.wildcard_import

    if name in scope.nonlocals:
        return _find_nonlocal_scope(scope, name) is not None

    if name in scope.defined or scope.wildcard_import:
        return True

    parent = scope.parent
    while parent is not None:
        # Function-like scopes do not close over a class namespace.
        if parent.kind == "class" and scope.kind in ("function", "lambda", "comprehension"):
            parent = parent.parent
            continue
        if name in parent.defined or parent.wildcard_import:
            return True
        parent = parent.parent
    return False


class _UndefinedNameAnalyzer(ast.NodeVisitor):
    def __init__(self, scope, source, line_starts, issues, seen):
        self.scope = scope
        self.source = source
        self.source_lines = source.splitlines(True)
        self.line_starts = line_starts
        self.issues = issues
        self.seen = seen

    def _child(self, kind, nodes, args=None):
        child = _Scope(kind, self.scope)
        _prepare_scope(child, nodes, args)
        return _UndefinedNameAnalyzer(child, self.source, self.line_starts, self.issues, self.seen)

    def _visit_outer_values(self, values):
        for value in values:
            if value is not None:
                self.visit(value)

    def visit_Name(self, node):
        if not isinstance(node.ctx, ast.Load) or _resolve(node.id, self.scope):
            return
        line_text = self.source_lines[node.lineno - 1]
        char_column = len(line_text.encode("utf-8")[:node.col_offset].decode("utf-8", errors="ignore"))
        start = self.line_starts[node.lineno - 1] + char_column
        end = start + len(node.id)
        key = (start, end, node.id)
        if key not in self.seen:
            self.seen.add(key)
            self.issues.append({
                "start": start,
                "end": end,
                "message": "Undefined name: " + node.id,
                "fatal": False,
            })

    def visit_FunctionDef(self, node):
        self._visit_outer_values(node.decorator_list)
        self._visit_outer_values(node.args.defaults)
        self._visit_outer_values(node.args.kw_defaults)
        child = self._child("function", node.body, node.args)
        for item in node.body:
            child.visit(item)

    visit_AsyncFunctionDef = visit_FunctionDef

    def visit_ClassDef(self, node):
        self._visit_outer_values(node.decorator_list)
        self._visit_outer_values(node.bases)
        self._visit_outer_values([keyword.value for keyword in node.keywords])
        child = self._child("class", node.body)
        for item in node.body:
            child.visit(item)

    def visit_Lambda(self, node):
        self._visit_outer_values(node.args.defaults)
        self._visit_outer_values(node.args.kw_defaults)
        child = self._child("lambda", [node.body], node.args)
        child.visit(node.body)

    def _visit_comprehension(self, node, values):
        generators = node.generators
        if not generators:
            self._visit_outer_values(values)
            return

        # Python evaluates the first iterable in the enclosing scope.
        self.visit(generators[0].iter)
        child_scope = _Scope("comprehension", self.scope)
        child_analyzer = _UndefinedNameAnalyzer(
            child_scope, self.source, self.line_starts, self.issues, self.seen
        )

        def bind_target(target):
            collector = _DefinitionCollector(child_scope)
            collector.visit(target)

        bind_target(generators[0].target)
        for condition in generators[0].ifs:
            child_analyzer.visit(condition)

        for generator in generators[1:]:
            child_analyzer.visit(generator.iter)
            bind_target(generator.target)
            for condition in generator.ifs:
                child_analyzer.visit(condition)

        for value in values:
            child_analyzer.visit(value)

    def visit_ListComp(self, node):
        self._visit_comprehension(node, [node.elt])

    def visit_SetComp(self, node):
        self._visit_comprehension(node, [node.elt])

    def visit_GeneratorExp(self, node):
        self._visit_comprehension(node, [node.elt])

    def visit_DictComp(self, node):
        self._visit_comprehension(node, [node.key, node.value])



def _utf16_len(value):
    """Android EditText offsets are UTF-16 code units, not Python code points."""
    return len(value.encode("utf-16-le")) // 2


def _node_utf16_range(source_lines, line_starts_utf16, node, name=None):
    line_index = max(0, (getattr(node, "lineno", 1) or 1) - 1)
    line_text = source_lines[line_index] if line_index < len(source_lines) else ""
    byte_column = max(0, getattr(node, "col_offset", 0) or 0)
    prefix = line_text.encode("utf-8")[:byte_column].decode("utf-8", errors="ignore")
    start = line_starts_utf16[line_index] + _utf16_len(prefix)
    token = name if name is not None else getattr(node, "id", "")
    return start, start + _utf16_len(token)


class _CallArityAnalyzer(ast.NodeVisitor):
    """Conservative direct-call arity checks. Ambiguous calls are deliberately skipped."""
    _BUILTINS = {
        "abs": (1, 1), "aiter": (1, 1), "all": (1, 1), "any": (1, 1),
        "ascii": (1, 1), "bin": (1, 1), "bool": (0, 1), "callable": (1, 1),
        "chr": (1, 1), "complex": (0, 2), "divmod": (2, 2), "enumerate": (1, 2),
        "float": (0, 1), "format": (1, 2), "hash": (1, 1), "hex": (1, 1),
        "id": (1, 1), "int": (0, 2), "isinstance": (2, 2), "issubclass": (2, 2),
        "iter": (1, 2), "len": (1, 1), "list": (0, 1), "memoryview": (1, 1),
        "next": (1, 2), "object": (0, 0), "oct": (1, 1), "ord": (1, 1),
        "pow": (2, 3), "repr": (1, 1), "reversed": (1, 1), "round": (1, 2),
        "set": (0, 1), "slice": (1, 3), "str": (0, 3), "sum": (1, 2),
        "tuple": (0, 1), "type": (1, 3),
    }

    def __init__(self, source, tree, line_starts_utf16, issues, seen):
        self.source = source
        self.source_lines = source.splitlines(True)
        self.line_starts_utf16 = line_starts_utf16
        self.issues = issues
        self.seen = seen
        self.signatures = {}
        self._collect_functions(tree)

    @staticmethod
    def _signature(node):
        args = node.args
        positional = list(args.posonlyargs) + list(args.args)
        required = max(0, len(positional) - len(args.defaults))
        maximum = None if args.vararg else len(positional)
        required_kwonly = sum(1 for default in args.kw_defaults if default is None)
        return required, maximum, required_kwonly

    def _collect_functions(self, tree):
        class Collector(ast.NodeVisitor):
            def __init__(self, target):
                self.target = target

            def visit_FunctionDef(self, node):
                self.target.setdefault(node.name, _CallArityAnalyzer._signature(node))
                self.generic_visit(node)

            visit_AsyncFunctionDef = visit_FunctionDef

        Collector(self.signatures).visit(tree)

    def visit_Call(self, node):
        self.generic_visit(node)
        if not isinstance(node.func, ast.Name):
            return
        if any(isinstance(arg, ast.Starred) for arg in node.args):
            return
        if any(keyword.arg is None for keyword in node.keywords):
            return

        name = node.func.id
        signature = self.signatures.get(name) or self._BUILTINS.get(name)
        if signature is None:
            return
        minimum, maximum, required_kwonly = signature
        positional = len(node.args)
        supplied_keywords = {kw.arg for kw in node.keywords if kw.arg}
        # If a function requires keyword-only arguments we cannot safely know their names
        # from a compact signature, so only check positional overflow in that case.
        too_few = required_kwonly == 0 and positional + len(supplied_keywords) < minimum
        too_many = maximum is not None and positional > maximum
        if not (too_few or too_many):
            return

        if maximum is None or minimum != maximum:
            expected = f"{minimum}" if maximum is None else f"{minimum}-{maximum}"
        else:
            expected = str(minimum)
        got = positional + len(supplied_keywords)
        start, end = _node_utf16_range(self.source_lines, self.line_starts_utf16, node.func, name)
        key = (start, end, name, "arity")
        if key in self.seen:
            return
        self.seen.add(key)
        self.issues.append({
            "start": start,
            "end": end,
            "line": getattr(node, "lineno", 1) or 1,
            "column": (getattr(node, "col_offset", 0) or 0) + 1,
            "message": f"{name}() expected {expected} argument(s), got {got}",
            "fatal": False,
        })


def diagnose(source):
    """Return syntax errors and conservative static warnings with Android-safe ranges."""
    try:
        compile(source, "<editor>", "exec")
        tree = ast.parse(source)
    except SyntaxError as exc:
        lines = source.splitlines(True)
        line_no = max(1, exc.lineno or 1)
        line_index = min(line_no - 1, max(0, len(lines) - 1))
        before_lines = "".join(lines[:line_index])
        line_text = lines[line_index] if lines else ""
        char_column = max(0, (exc.offset or 1) - 1)
        prefix = line_text[:char_column]
        start = _utf16_len(before_lines) + _utf16_len(prefix)
        token_text = line_text[char_column:char_column + 1]
        end = start + max(1, _utf16_len(token_text))
        return json.dumps([{
            "start": start,
            "end": end,
            "line": line_no,
            "column": char_column + 1,
            "message": exc.msg,
            "fatal": True,
        }])

    source_lines = source.splitlines(True)
    line_starts_utf16 = [0]
    running = 0
    for line in source_lines:
        running += _utf16_len(line)
        line_starts_utf16.append(running)
    if not source_lines:
        source_lines = [""]

    module_scope = _Scope("module")
    _prepare_scope(module_scope, tree.body)
    issues = []
    seen = set()

    # Undefined-name analysis uses UTF-16 offsets because Android EditText indexes
    # Java/Kotlin strings in UTF-16 code units.
    analyzer = _UndefinedNameAnalyzer(module_scope, source, [0], issues, seen)
    analyzer.source_lines = source_lines
    analyzer.line_starts = line_starts_utf16
    original_visit_name = analyzer.visit_Name

    def visit_name_utf16(node):
        if not isinstance(node.ctx, ast.Load) or _resolve(node.id, analyzer.scope):
            return
        start, end = _node_utf16_range(source_lines, line_starts_utf16, node, node.id)
        key = (start, end, node.id)
        if key not in seen:
            seen.add(key)
            issues.append({
                "start": start,
                "end": end,
                "line": node.lineno,
                "column": node.col_offset + 1,
                "message": "Undefined name: " + node.id,
                "fatal": False,
            })

    analyzer.visit_Name = visit_name_utf16
    for item in tree.body:
        analyzer.visit(item)

    _CallArityAnalyzer(source, tree, line_starts_utf16, issues, seen).visit(tree)
    issues.sort(key=lambda item: (item["start"], item["end"], item["message"]))
    return json.dumps(issues)

def complete(source, cursor, project_dir):
    """Return Jedi's best local completion as JSON. Runs fully offline."""
    try:
        import jedi
        before = source[:cursor]
        line = before.count("\n") + 1
        column = len(before.rsplit("\n", 1)[-1])
        path = os.path.join(project_dir, "main.py")
        items = jedi.Script(source, path=path).complete(line, column)
        if not items:
            return "{}"
        item = items[0]
        suffix = item.complete
        cursor_back = 0
        if item.type in ("function", "class") and not suffix.endswith(")"):
            suffix += "()"
            cursor_back = 1
        return json.dumps({
            "label": item.name + ("()" if cursor_back else ""),
            "suffix": suffix,
            "cursor_back": cursor_back,
            "type": item.type,
            "doc": item.docstring(raw=True)[:240],
        })
    except Exception:
        return "{}"


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


def _purge_project_modules(project_dir):
    root = os.path.realpath(project_dir)
    prefix = root + os.sep
    for name, module in list(sys.modules.items()):
        path = getattr(module, "__file__", None)
        if not path:
            continue
        try:
            real_path = os.path.realpath(path)
        except (OSError, TypeError, ValueError):
            continue
        if real_path == root or real_path.startswith(prefix):
            sys.modules.pop(name, None)
    for current_root, directories, _ in os.walk(root):
        if "__pycache__" in directories:
            shutil.rmtree(os.path.join(current_root, "__pycache__"), ignore_errors=True)
            directories.remove("__pycache__")
    importlib.invalidate_caches()


def run_code(source, filename, project_dir, bridge):
    old_input = builtins.input
    old_cwd = os.getcwd()
    old_sys_path = list(sys.path)
    old_trace = sys.gettrace()
    old_dont_write_bytecode = sys.dont_write_bytecode
    out = _Stream(bridge, False)
    err = _Stream(bridge, True)

    def android_input(prompt=""):
        if prompt:
            out.write(prompt)
        value = bridge.readLine()
        if value is None:
            raise KeyboardInterrupt()
        out.write(str(value) + "\n")
        return str(value)

    trace_ticks = 0

    def stop_trace(frame, event, arg):
        nonlocal trace_ticks
        trace_ticks += 1
        if trace_ticks >= 64:
            trace_ticks = 0
            if bridge.shouldStop():
                raise KeyboardInterrupt()
        return stop_trace

    try:
        os.makedirs(project_dir, exist_ok=True)
        os.chdir(project_dir)
        _purge_project_modules(project_dir)
        if project_dir not in sys.path:
            sys.path.insert(0, project_dir)
        builtins.input = android_input
        sys.dont_write_bytecode = True
        scope = {"__name__": "__main__", "__file__": filename}
        sys.settrace(stop_trace)
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            exec(compile(source, filename, "exec"), scope, scope)
        bridge.exited(0)
    except KeyboardInterrupt:
        err.write("\nProgram interrupted\n")
        bridge.exited(130)
    except SystemExit as exc:
        if isinstance(exc.code, int):
            code = exc.code
        elif exc.code is None:
            code = 0
        else:
            err.write(str(exc.code) + "\n")
            code = 1
        bridge.exited(code)
    except BaseException:
        err.write(traceback.format_exc())
        bridge.exited(1)
    finally:
        sys.settrace(old_trace)
        builtins.input = old_input
        sys.dont_write_bytecode = old_dont_write_bytecode
        os.chdir(old_cwd)
        sys.path[:] = old_sys_path


def version():
    return sys.version
