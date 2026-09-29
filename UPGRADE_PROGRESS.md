# PY4U upgrade status

Branch: `py4u/masterpiece-upgrade` in `tuffboi910/pydroid-x` (draft PR #5). The default `main` branch is unchanged. The local checkout tracks the pushed upgrade branch. Every remote checkpoint was verified against the local Git tree before its ref was advanced.

## Implemented and checked

- Editor: reduced typing allocations and model sync, incremental line index, worker syntax tokenization, visible-area color spans, smart typed pairs and Backspace, indentation and comment actions, in-file Find/Replace/Replace All/Go to Line, current-file and project-wide Problems navigation, per-file selection/scroll restoration, and hardware shortcuts for Save, Run, Find, Replace, Undo, Redo, Quick Open, and Command Palette. Autosave now coalesces saved-file-list refreshes rather than causing a Compose list update after every write.
- Console: coalesced output before UI dispatch, bounded live text layout, retained output Copy and bounded Search, selectable visible text, autoscroll control, and project-file traceback navigation. Python modules imported from one project are removed from the module cache after its run.
- Data safety: serialized atomic writes across activity recreation, ordered automatic file rename with state rollback on failure, save on Android backgrounding, bounded local file history, deliberate restore preview, and recovery points before Astro edits/restores. Queued or failed save text stays in memory so rapid file switches read the newest version.
- Projects: runnable Blank, Hello World, CLI, Calculator, Automation, Guessing Game, and CSV Data starters. Five nontrivial scripts passed local parse and execution checks. A file can be duplicated through the serialized atomic writer and opened after success.
- Astro: editor-selection question handoff; bounded project names, diagnostics, and recent Console context only when code sharing is enabled; existing preview/Accept/Reject flow retained.
- Navigation: searchable command palette, Quick Open, bounded background Find in Project with direct file/line navigation, and a horizontal open-file tab strip with close and Ctrl+W. Open tabs and selected file persist per project.
- Project browser: nested folder navigation and creation; bounded recursive Python file listing; user-driven rename, move, and recoverable delete; recovery notice restored after process restart; nested paths across saved files, tabs, project search, diagnostics, traceback navigation, and file history. Android CI passed; physical-device verification remains.
- Tabs: the strip now allows moving a tab left or right and reopening the last closed file. Android CI passed; physical-device verification remains.
- Runtime packages: terminal `pip install PACKAGE` resolves dependencies from PyPI and installs compatible pure-Python wheels into app-private storage with hash checks, bounded downloads/extraction, path validation, and atomic per-distribution replacement. Native wheels, scripts, extras, direct URLs, and unsupported install layouts report an error. This batch awaits Android CI and device verification.
- Astro edits: preview now separates independent line changes and allows accepting any selected subset after a recovery checkpoint. Diff work is bounded for large files and the original snapshot is checked again before applying. This batch awaits Android CI and device verification.
- Console ANSI: bounded visible output interprets basic foreground, bright, bold, and RGB SGR colors while suppressing escape controls. Copy, search, and Astro context use readable text. This batch awaits Android CI and device verification.

## Verification

- Host Python analyzer/runtime suite: 24 tests pass with declared Jedi 0.19.2 installed, including live-buffer project diagnostics, file/line/severity reporting, file-size bounds, and UTF-16 offsets.
- Android CI runs 202–206 and 208–211 passed unit tests and debug APK packaging. Run 207 exposed a mistaken Unicode-tail test assertion; the assertion was corrected and run 208 passed.
- Runs 212 (Astro context) and 213 (project starters) passed. Run 214 caught a Kotlin scope error in history preview; run 215 carried the same error. Corrected run 230, project-search run 231, queued-save run 232, corrected tab run 234, Console run 235, rename recovery run 236, autosave refresh run 237, duplication run 238, and the final guard run 239 passed Android tests and APK packaging. Run 233 caught an editor-location visibility issue and was fixed in 234. Project-wide Problems source tests pass locally; its Android CI run is pending.
- Git diff checks and clean local status were verified at each checkpoint. No physical Android device or emulator was attached to this Work environment.
- The nested project/browser batch passes 24 host Python tests with Jedi 0.19.2 and `git diff --check`; Android CI run 241 passed unit tests and debug APK packaging.
- Tab controls passed 24 host Python tests and Android CI run 242, including unit tests and debug APK packaging.
- Documentation-only run 243 passed. Pure-wheel installer host suite: 27 Python tests pass, including synthetic install/import and rejected traversal/checksum cases. Run 244 stopped in the host test setup because CI installed Jedi but omitted the already bundled `packaging` library; the workflow dependency has been corrected and the Android build is pending.

## Incomplete or unverified

- Measure typing, scrolling, Console throughput, keyboard transitions, and touch accuracy on a physical high-refresh device. UI geometry, animation timing, haptics, accessibility font scaling, rotation, and process-death recovery require device checks.
- The editor still needs tab pinning, persistence for recently closed recovery, folding/sticky context if justified, and more real-world IME testing. Find in Project scans nested Python files and caps results at 200. Project Problems scans nested Python files, caps at 300 files and 8 MB total, skips files above 1 MB, and requires an explicit scan. Failed writes still need on-disk recovery when storage becomes available; the in-memory pending text survives only while the process lives.
- Project browser/action behavior needs physical-device testing, including folder navigation, file moves, and delete recovery across process death. History uses at most 12 snapshots per file, throttled to one per minute; files over 1 MB skip automatic snapshots.
- Astro still lacks constrained project-agent operations. Per-hunk preview/selection needs Android CI and device verification; live provider and offline-model behavior need configured credentials/models and device testing.
- Runtime installation currently supports compatible pure-Python wheels only; native Android wheels, package extras, and wheel script/data layouts need separate implementation. Validate network installs, dependency graphs, storage failure, and imports on an Android device.
- Console search results do not yet jump to the corresponding live-output scroll position; full cursor-motion/progress control and large-output interaction beyond the retained window remain. Broad UI/motion polish and first-run/accessibility passes remain.

The older `b119229` checkout was left intact. The previously lost `f386e22` object was not recovered; this branch reconstructs new work from the valid `a86c076` remote base.
