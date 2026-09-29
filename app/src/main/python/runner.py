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
import difflib
import shlex
import importlib.metadata


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


def diagnose(source):
    """Return syntax errors and conservative unresolved-name warnings with exact ranges."""
    def utf16_offset(index):
        return len(source[:max(0, index)].encode("utf-16-le")) // 2

    def serialize(issues):
        for issue in issues:
            issue["start_utf16"] = utf16_offset(issue["start"])
            issue["end_utf16"] = utf16_offset(issue["end"])
        return json.dumps(issues)

    try:
        compile(source, "<editor>", "exec")
        tree = ast.parse(source)
    except SyntaxError as exc:
        lines = source.splitlines(True)
        line = max(1, exc.lineno or 1)
        start = sum(len(value) for value in lines[:line - 1]) + max(0, (exc.offset or 1) - 1)
        end = min(len(source), start + 1)
        return serialize([{
            "start": start,
            "end": end,
            "message": exc.msg,
            "fatal": True,
        }])

    line_starts = [0]
    for index, char in enumerate(source):
        if char == "\n":
            line_starts.append(index + 1)

    module_scope = _Scope("module")
    _prepare_scope(module_scope, tree.body)
    issues = []
    seen = set()
    analyzer = _UndefinedNameAnalyzer(module_scope, source, line_starts, issues, seen)
    for item in tree.body:
        analyzer.visit(item)
    issues.sort(key=lambda item: (item["start"], item["end"]))
    return serialize(issues)


def diagnose_project(current_source, current_filename, project_dir, max_files=300,
                     max_file_bytes=1_000_000, max_total_bytes=8_000_000):
    """Diagnose bounded top-level Python files, using the live buffer for the active file."""
    root = os.path.realpath(project_dir)
    current_filename = os.path.basename(current_filename)
    try:
        names = sorted(name for name in os.listdir(root)
                       if name.lower().endswith(".py")
                       and os.path.isfile(os.path.join(root, name)))
    except OSError:
        names = []
    if current_filename.lower().endswith(".py"):
        names = [current_filename] + [name for name in names if name != current_filename]

    results = []
    total_bytes = 0
    for name in names[:max(1, max_files)]:
        path = os.path.join(root, name)
        if name == current_filename:
            source = current_source
        else:
            try:
                size = os.path.getsize(path)
                if size > max_file_bytes:
                    continue
                with open(path, "r", encoding="utf-8", errors="replace") as stream:
                    source = stream.read(max_file_bytes + 1)
            except (OSError, UnicodeError):
                continue
        encoded_size = len(source.encode("utf-8", errors="replace"))
        if encoded_size > max_file_bytes or total_bytes + encoded_size > max_total_bytes:
            continue
        total_bytes += encoded_size
        for issue in json.loads(diagnose(source)):
            start = issue["start"]
            line = source.count("\n", 0, start) + 1
            lines = source.splitlines()
            preview = lines[line - 1].strip()[:160] if line <= len(lines) else ""
            results.append({
                "file": name,
                "line": line,
                "severity": "error" if issue.get("fatal") else "warning",
                "message": issue["message"],
                "preview": preview,
                "start_utf16": issue["start_utf16"],
                "end_utf16": issue["end_utf16"],
                "fatal": issue.get("fatal", False),
            })
    return json.dumps(results)


def complete(source, cursor, project_dir):
    """Return replacement edits in Android UTF-16 coordinates. Runs offline."""
    try:
        import jedi
        # Android selection offsets count UTF-16 code units, not Python codepoints.
        cursor = len(source.encode("utf-16-le")[:max(0, cursor) * 2].decode("utf-16-le", errors="ignore"))
        before = source[:cursor]
        line = before.count("\n") + 1
        column = len(before.rsplit("\n", 1)[-1])
        path = os.path.join(project_dir, "main.py")
        items = jedi.Script(source, path=path).complete(line, column)
        if not items:
            return "{}"
        start = cursor
        while start > 0 and (source[start - 1].isalnum() or source[start - 1] == "_"):
            start -= 1
        end = cursor
        while end < len(source) and (source[end].isalnum() or source[end] == "_"):
            end += 1
        current_line = before.rsplit("\n", 1)[-1].lstrip()
        importing = current_line.startswith(("import ", "from "))
        def utf16(position):
            return len(source[:position].encode("utf-16-le")) // 2

        def serialized(completion):
            insert_text = completion.name
            cursor_back = 0
            callable_item = completion.type in ("function", "class")
            if callable_item and not importing and not source[end:].lstrip().startswith("("):
                insert_text += "()"
                cursor_back = 1
            signatures = completion.get_signatures() if callable_item else []
            return {
                "label": completion.name + ("()" if callable_item and not importing else ""),
                "insert_text": insert_text,
                "replace_start": utf16(start),
                "replace_end": utf16(end),
                "cursor_back": cursor_back,
                "type": completion.type,
                "signature": signatures[0].to_string() if signatures else "",
                "doc": completion.docstring(raw=True)[:600],
            }

        return json.dumps({
            "items": [serialized(item) for item in items[:3]],
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


def _purge_project_modules(project_dir, clear_bytecode=True):
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
    if clear_bytecode:
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
    except BaseException as exc:
        raw = traceback.format_exc()
        extracted = traceback.extract_tb(exc.__traceback__)
        project_root = os.path.realpath(project_dir)
        frame = next((item for item in reversed(extracted)
                      if os.path.realpath(item.filename).startswith(project_root + os.sep)), None)
        source_file = (exc.filename or filename) if isinstance(exc, SyntaxError) else (frame.filename if frame else filename)
        line = (exc.lineno or 1) if isinstance(exc, SyntaxError) else (frame.lineno if frame else 1)
        code_line = (exc.text or "").strip() if isinstance(exc, SyntaxError) else ((frame.line or "").strip() if frame else "")
        title = type(exc).__name__
        replace_from = ""
        replace_to = ""
        if isinstance(exc, NameError):
            missing = getattr(exc, "name", None) or "that name"
            match = difflib.get_close_matches(missing, list(dir(builtins)) + list(scope.keys()), n=1, cutoff=0.72)
            replace_from = missing if match else ""
            replace_to = match[0] if match else ""
            explanation = "Python doesn’t know what ‘%s’ means." % missing
            hint = ("Did you mean ‘%s’?" % replace_to) if replace_to else "Define it first, or put quotes around it if it should be text."
        elif isinstance(exc, TypeError):
            explanation = "This operation received the wrong kind or number of values: %s" % str(exc)
            hint = "Check the values and arguments used on this line."
        elif isinstance(exc, IndexError):
            explanation = "You tried to use a list position that doesn’t exist."
            hint = "List positions start at 0 and must be smaller than len(the_list)."
        elif isinstance(exc, KeyError):
            explanation = "This dictionary has no key named %s." % str(exc)
            hint = "Check the key spelling, or use dictionary.get(key)."
        elif isinstance(exc, ValueError):
            explanation = "The value has the right type, but Python can’t use it here: %s" % str(exc)
            hint = "Check the input or conversion on this line."
        elif isinstance(exc, ZeroDivisionError):
            explanation = "This line tried to divide by zero."
            hint = "Make sure the divisor isn’t 0 before dividing."
        elif isinstance(exc, AttributeError):
            explanation = "That object doesn’t have the property or function you asked for: %s" % str(exc)
            hint = "Check the object type and the attribute spelling."
        elif isinstance(exc, ModuleNotFoundError):
            explanation = "Python can’t find the module %s." % str(exc)
            hint = "Check its spelling or install the package first."
        else:
            explanation = str(exc) or "Python stopped because of an unexpected error."
            hint = "Open Details for the technical traceback, or ask Astro for help."
        bridge.reportError(title, explanation, line, code_line, hint, raw,
                           source_file, replace_from, replace_to)
        err.write("\n%s on line %s: %s\n" % (title, line, explanation))
        bridge.exited(1)
    finally:
        sys.settrace(old_trace)
        _purge_project_modules(project_dir, clear_bytecode=False)
        builtins.input = old_input
        sys.dont_write_bytecode = old_dont_write_bytecode
        os.chdir(old_cwd)
        sys.path[:] = old_sys_path


def version():
    return sys.version


def run_terminal_command(command, project_dir, bridge):
    """Project-scoped terminal for Android, where a desktop shell isn't available."""
    out, err = _Stream(bridge, False), _Stream(bridge, True)
    try:
        parts = shlex.split(command)
        if not parts:
            bridge.exited(0)
            return
        name, args = parts[0].lower(), parts[1:]
        root = os.path.realpath(project_dir)

        def project_path(value):
            path = os.path.realpath(os.path.join(root, value))
            if path != root and not path.startswith(root + os.sep):
                raise ValueError("Path must stay inside this project")
            return path

        if name == "help":
            out.write("Commands: help, pwd, ls, cat FILE, python FILE.py, packages, mkdir DIR, touch FILE, clear\n")
        elif name == "pwd":
            out.write(root + "\n")
        elif name in ("ls", "dir"):
            target = project_path(args[0] if args else ".")
            for item in sorted(os.listdir(target), key=str.lower):
                out.write(item + ("/" if os.path.isdir(os.path.join(target, item)) else "") + "\n")
        elif name == "cat":
            if not args: raise ValueError("Usage: cat FILE")
            with open(project_path(args[0]), "r", encoding="utf-8") as handle:
                out.write(handle.read() + "\n")
        elif name == "mkdir":
            if not args: raise ValueError("Usage: mkdir DIR")
            os.makedirs(project_path(args[0]), exist_ok=True)
        elif name == "touch":
            if not args: raise ValueError("Usage: touch FILE")
            open(project_path(args[0]), "a", encoding="utf-8").close()
        elif name == "packages" or (name == "pip" and (not args or args[0] == "list")):
            rows = sorted((d.metadata.get("Name", d.name), d.version) for d in importlib.metadata.distributions())
            out.write("Installed packages:\n" + "\n".join("%s %s" % row for row in rows) + "\n")
        elif name in ("python", "py"):
            if not args:
                out.write(sys.version + "\n")
            else:
                filename = args[0]
                with open(project_path(filename), "r", encoding="utf-8") as handle:
                    run_code(handle.read(), filename, root, bridge)
                return
        elif name == "pip" and args and args[0] == "install":
            raise ValueError("Only Android-compatible packages can be installed; the full installer is not ready yet")
        else:
            raise ValueError("Unknown command: %s. Type help." % name)
        bridge.exited(0)
    except BaseException as exc:
        err.write("%s: %s\n" % (type(exc).__name__, str(exc)))
        bridge.exited(1)
