# PY4U reconstruction checklist

Reconstruction base: `a86c076` (`main`, newest valid remote state available after `f386e22` was declared permanently lost).
The older `b119229` checkout is untouched. This checklist tracks reconstructed work only; Android UI behavior still needs physical-device verification.

## Checkpoints

| Area | Status | Evidence |
|---|---|---|
| Recoverable Git state | Complete | Refs, remote branches and PR refs, reflogs, object stores, and workspace history were searched; no `f386e22` object exists. |
| Performance source audit | Complete | Found a full-document string copy, full newline-index rebuild with temporary collections, and full undo snapshot on almost every keystroke. |
| Editor typing allocation reduction | Implemented | The view retains its editable text, updates newline offsets incrementally, batches model snapshots for 120 ms, and groups typing history at 400 ms. |
| Syntax highlighting UI work | Implemented, Android suite pending | Lexer runs on a cancelable low-priority worker; stale results are discarded before spans are applied. |
| Editor and completion draw allocations | Improved | Reuses editor paints, draws indentation guides without per-line strings/lists, and reuses completion popup paints, path, docs layout, and hit bounds. |
| Save/run/navigation consistency | Implemented | Pending editor changes flush on focus loss, page changes, detach, Start/Stop, and New File. |
| Smart typed pairs and closer skipping | Implemented, Android suite pending | Added pair insertion, existing closer skip, and empty-pair Backspace handling with regression tests. |
| Smart indentation and selection actions | Implemented, Android suite pending | Enter copies indentation and adds configured width after a code colon; Tab/Shift+Tab and comment toggle work on selected lines. Width is configurable in Editor settings. |
| Current-file Problems navigation | Implemented, Android suite pending | Editor issue taps and a header count open an explanation with location, cause, possible fix, and a jump to the code. Project-wide aggregation remains. |
| Regression coverage | Added, Android suite pending | Added randomized newline-index, editor batching/history, typed pairs, indentation, selection, and comment tests. The current environment has no Gradle, Android SDK, or Kotlin compiler. |
| Python diagnostics baseline | Passed | 13 existing runner diagnostic/execution tests pass on host Python 3.12. |
| Completion test baseline | Blocked in host | Six Jedi completion tests cannot run because host Python has no `jedi`; PY4U declares Jedi as an Android build dependency. |
| Android build/device profile | Blocked in this environment | No Gradle executable, wrapper, Android SDK, Kotlin compiler, emulator, or attached Android device is available. |

## Requested upgrade areas

| Area | Status |
|---|---|
| Editor hot path and large-file behavior | In progress; allocation fixes and worker tokenization implemented; span application and Android frame-time profiling remain to verify. |
| Editor intelligence: typed pairs, indentation, selection comment actions | Implemented, Android suite pending |
| Editor intelligence: find/replace, project search, go-to-line, folding, sticky headers, minimap, visible whitespace | Partial base exists; audit and reconstruction remain. |
| Python diagnostics and Problems navigation | Current-file panel and tap explanations implemented; project-wide aggregation and Android verification remain. |
| Code actions and safe refactors | Not reconstructed. |
| Astro context, diff review, hunk acceptance, agent checkpoints | Partial base exists; richer context and review remain. |
| Console controls, ANSI, search, clickable tracebacks, reliable stop/input | Partial base exists; audit and missing controls remain. |
| Project files, templates, recovery, multi-tab/session restoration | Partial base exists; audit and missing operations remain. |
| Package manager and `requirements.txt` | Partial base exists; verify actual Android support and rebuild missing actions. |
| Command palette and unified search | Not reconstructed. |
| Theme/settings/accessibility/keyboard shortcuts/haptics | Partial base exists; audit and missing controls remain. |
| Onboarding and beginner mode | Not reconstructed. |
| Session restore, file history, deletion recovery | Not reconstructed or verified. |
| Motion polish and final device QA | Partial base exists; keep restrained and avoid cursor animation, pill buttons, fake metrics, emoji icons, AI imagery/copy, and web-only work. |

## Verified upgrade checkpoints

- The editor, diagnostics, Console output coalescing, and atomic save checkpoint is backed up on `py4u/masterpiece-upgrade` and reviewed in draft PR #5.
- GitHub Actions run 202 passed the Python suite, Android unit tests, and debug APK build. Physical-device behavior and frame timing remain unverified.
- The next checkpoint adds in-file Find, Replace, Replace All, Go to Line, and keyboard Find/Replace shortcuts. Its Android CI run is pending.
- GitHub Actions runs 203, 204, and 205 passed their Python suites, Android unit tests, and debug APK builds. The next history checkpoint is pending CI.
- Saves now serialize automatic renames, and Android `ON_STOP` flushes pending editor edits before saving. History retains at most 12 versions per file, with a one-minute cadence; large files over 1 MB skip automatic snapshots.
- GitHub Actions run 206 passed Android tests and APK packaging for the history checkpoint. Console now limits live layout to its newest 32,000 characters while retaining more output for Copy; its own CI is pending.
- Run 207 reached APK packaging but failed one Console unit assertion about an emoji boundary; the assertion was corrected. Runtime exceptions now identify their actual project source file, and project imports are purged after each run so another project cannot reuse stale modules. New CI is pending.
- Syntax tokenization already ran on a worker, but applying every token span still happened on the UI thread. The next checkpoint limits span application to a prefetched viewport and refreshes it on scroll/resize. Actual frame-time profiling remains a physical-device task.
- GitHub Actions runs 208, 209, and 210 passed Android unit tests and APK builds. Command palette and Quick Open are implemented with actual actions and hardware shortcuts; Android CI is pending for this checkpoint.
- Astro gets an editor selection handoff. With explicit code sharing enabled, it receives bounded project names, current diagnostics, and recent Console output. Provider behavior still needs configured-credential and device verification.
- Project creation now offers seven concrete starter choices. Five nontrivial starters were parsed and run with host Python using deterministic stdin where needed. Android project-picker UI awaits CI/device verification.

## Current files changed

- `app/src/main/java/com/pydroidx/app/LineBreakIndex.kt`
- `app/src/main/java/com/pydroidx/app/PythonSyntaxHighlighter.kt`
- `app/src/main/java/com/pydroidx/app/CompletionPopup.kt`
- `app/src/main/java/com/pydroidx/app/PythonEditorView.kt`
- `app/src/main/java/com/pydroidx/app/MainActivity.kt`
- `app/src/main/java/com/pydroidx/app/EditorChrome.kt`
- `app/src/test/java/com/pydroidx/app/LineBreakIndexTest.kt`
- `app/src/test/java/com/pydroidx/app/PythonSyntaxHighlighterTest.kt`
- `app/src/test/java/com/pydroidx/app/CompletionPopupTest.kt`
- `app/src/test/java/com/pydroidx/app/PythonEditorViewTest.kt`

## Next work

1. Continue the editor hot-path audit, especially safely applying many syntax spans and validating scroll/typing frame times.
2. Reconstruct find/replace and go-to-line, then project-wide Problems navigation with focused tests.
3. Continue Console, project, and Astro work in small verified checkpoints.
4. Run Android unit tests/build and device checks as soon as an Android build environment is available; do not claim those checks passed here.
