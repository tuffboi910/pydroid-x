import builtins
import contextlib
import io
import os
import sys
import traceback

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
