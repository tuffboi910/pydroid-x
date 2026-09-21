# PyDroid X

PyDroid X is an open-source Android Python IDE focused on reliable local execution and phone-first editing

## Current milestone

- Real embedded CPython 3.14 via Chaquopy 17
- ARM64 Android APK
- Offline execution with stdout, stderr and tracebacks
- Interactive `input()` without blocking the UI
- Stop control and dedicated worker thread
- Persistent `main.py` project
- Dark Compose editor, terminal and programming-key toolbar
- Actual runtime version displayed from `sys.version`

This repository is under active development toward project exploration, rich syntax highlighting, offline IntelliSense, package management, SAF import/export and BYOK AI providers

## Build

Use JDK 17, Android SDK 35 and Gradle 8.11.1

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`

## Security

Python code runs inside the app sandbox and can access resources granted to the app  Third-party code and packages should still be treated as untrusted

## Runtime notes

Android cannot provide every desktop OS facility  `curses`, `readline`, `tkinter`, `turtle`, and most process-based multiprocessing are unavailable  Package compatibility depends on Android ARM64 wheels or pure-Python distributions
