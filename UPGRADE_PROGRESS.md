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
- Runtime packages: terminal `pip install PACKAGE` resolves dependencies from PyPI and installs compatible pure-Python wheels into app-private storage with hash checks, bounded downloads/extraction, path validation, and atomic per-distribution replacement. Native wheels, scripts, extras, direct URLs, and unsupported install layouts report an error. Android CI passed; device verification remains.
- Astro edits: preview now separates independent line changes and allows accepting any selected subset after a recovery checkpoint. Diff work is bounded for large files and the original snapshot is checked again before applying. Android CI passed; device verification remains.
- Console ANSI: bounded visible output interprets basic foreground, bright, bold, and RGB SGR colors while suppressing escape controls. Copy, search, and Astro context use readable text. Android CI passed; device verification remains.
- Lifecycle: retained ViewModel initialization is idempotent across activity recreation, so rotation does not reload the on-disk file over a live editor buffer. Android CI passed; device verification remains.
- Console search: tapping a retained-output hit opens a bounded readable window starting at that line, pauses live tail updates, and Auto on resumes the latest output. Android CI passed; device verification remains.
- Astro project context: an explicit sharing chip can include live current code and bounded nested Python sources in a question. File IO runs on the AI worker; hidden/history folders and oversized sources are skipped. This mode is analysis-only. Android CI passed; device verification remains.
- Runtime package hardening: redirects outside PyPI, oversized metadata, and native libraries hidden in universal-tagged wheels are rejected. Android CI passed.
- Astro project context is budgeted per file so a long active buffer does not crowd out the rest of the project; on-device models receive a smaller context based on their configured token window. Android CI passed.
- Project-context replies are analysis-only in the IDE: fenced code from that mode cannot become an Apply action because the shared active file may be truncated. Current-file sharing retains guarded edit previews. Android CI passed.
- Runtime package downloads and extraction check the existing Stop signal between bounded chunks and clean staging on cancellation. Android CI passed.

## Verification

- Host Python analyzer/runtime suite: 29 tests pass with declared Jedi 0.19.2 and packaging 25.0 installed, including live-buffer project diagnostics, pure-wheel install/import, hostile wheel rejection, cancellation, file/line/severity reporting, file-size bounds, and UTF-16 offsets.
- Android CI runs 202–206 and 208–211 passed unit tests and debug APK packaging. Run 207 exposed a mistaken Unicode-tail test assertion; the assertion was corrected and run 208 passed.
- Runs 212 (Astro context) and 213 (project starters) passed. Run 214 caught a Kotlin scope error in history preview; run 215 carried the same error. Corrected run 230, project-search run 231, queued-save run 232, corrected tab run 234, Console run 235, rename recovery run 236, autosave refresh run 237, duplication run 238, and the final guard run 239 passed Android tests and APK packaging. Run 233 caught an editor-location visibility issue and was fixed in 234. Project-wide Problems source tests pass locally; its Android CI run is pending.
- Git diff checks and clean local status were verified at each checkpoint. No physical Android device or emulator was attached to this Work environment.
- The nested project/browser batch passes 24 host Python tests with Jedi 0.19.2 and `git diff --check`; Android CI run 241 passed unit tests and debug APK packaging.
- Tab controls passed 24 host Python tests and Android CI run 242, including unit tests and debug APK packaging.
- Documentation-only run 243 passed. Run 244 stopped in host test setup because CI omitted the bundled `packaging` dependency; run 245 corrected the workflow and passed.
- Corrected installer runs 245 and 246–254 passed Python tests, Android unit tests, and debug APK packaging. Run 244 failed because the host CI environment lacked the bundled packaging dependency; workflow setup was corrected in 245. The latest code run is 254.

## Remaining source work

- The editor still needs tab pinning, persistence for recently closed recovery, folding/sticky context if justified, and more real-world IME testing. Find in Project scans nested Python files and caps results at 200. Project Problems scans nested Python files, caps at 300 files and 8 MB total, skips files above 1 MB, and requires an explicit scan. Failed writes still need on-disk recovery when storage becomes available; the in-memory pending text survives only while the process lives.
- History uses at most 12 snapshots per file, throttled to one per minute; files over 1 MB skip automatic snapshots. Recovery needs a durable journal for unsaved buffers and failed writes across process death.
- Astro still lacks constrained multi-file edit proposals and agent operations. Project context is read-only input.
- Runtime installation currently supports compatible pure-Python wheels only; native Android wheels, package extras, and wheel script/data layouts need separate implementation. Dependency conflict resolution and upgrades of already imported modules need more work.
- Full terminal cursor-motion/progress controls and interaction beyond the retained output window remain. Broader UI/motion polish, accessibility, and performance profiling remain.

## Physical-device checks

- Measure typing, scrolling, Console throughput, keyboard transitions, and touch accuracy on a high-refresh Android device. Check UI geometry, animation timing, haptics, accessibility font scaling, rotation, and process-death restoration.
- Exercise folder moves/delete recovery, Astro preview and sharing, on-device/online AI with configured models or credentials, and real PyPI downloads/imports under network and storage failure conditions.

The older `b119229` checkout was left intact. The previously lost `f386e22` object was not recovered; this branch reconstructs new work from the valid `a86c076` remote base.
