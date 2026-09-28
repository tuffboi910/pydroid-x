# PY4U upgrade status

Branch: `py4u/masterpiece-upgrade` in `tuffboi910/pydroid-x` (draft PR #5). The default `main` branch is unchanged. The local checkout tracks the pushed upgrade branch. Every remote checkpoint was verified against the local Git tree before its ref was advanced.

## Implemented and checked

- Editor: reduced typing allocations and model sync, incremental line index, worker syntax tokenization, visible-area color spans, smart typed pairs and Backspace, indentation and comment actions, in-file Find/Replace/Replace All/Go to Line, current-file Problems navigation, and hardware shortcuts for Save, Run, Find, Replace, Undo, Redo, Quick Open, and Command Palette.
- Console: coalesced output before UI dispatch, bounded live text layout, retained output Copy, autoscroll control, and project-file traceback navigation. Python modules imported from one project are removed from the module cache after its run.
- Data safety: serialized atomic writes across activity recreation, ordered automatic file rename, save on Android backgrounding, bounded local file history, deliberate restore preview, and recovery points before Astro edits/restores.
- Projects: runnable Blank, Hello World, CLI, Calculator, Automation, Guessing Game, and CSV Data starters. Five nontrivial scripts passed local parse and execution checks.
- Astro: editor-selection question handoff; bounded project names, diagnostics, and recent Console context only when code sharing is enabled; existing preview/Accept/Reject flow retained.
- Navigation: searchable command palette and Quick Open backed by actual actions.

## Verification

- Host Python analyzer/runtime suite: 22 tests pass with declared Jedi 0.19.2 installed.
- Android CI runs 202–206 and 208–211 passed unit tests and debug APK packaging. Run 207 exposed a mistaken Unicode-tail test assertion; the assertion was corrected and run 208 passed.
- Run 212 (Astro context) passed. Run 213 (project starters) was still in progress at this document update. The latest atomic-read recovery change awaits Android CI.
- Git diff checks and clean local status were verified at each checkpoint. No physical Android device or emulator was attached to this Work environment.

## Incomplete or unverified

- Measure typing, scrolling, Console throughput, keyboard transitions, and touch accuracy on a physical high-refresh device. UI geometry, animation timing, haptics, accessibility font scaling, rotation, and process-death recovery require device checks.
- The editor still needs project-wide search/Problems, robust file tabs and per-file cursor/scroll restoration, folding/sticky context if justified, and more real-world IME testing.
- Project actions still lack rename/duplicate/move/delete/recovery UI and folder navigation. History uses at most 12 snapshots per file, throttled to one per minute; files over 1 MB skip automatic snapshots.
- Astro lacks per-hunk acceptance and constrained project-agent operations. Live provider and offline-model behavior need configured credentials/models and device testing.
- Android runtime package installation is not implemented. The terminal truthfully reports this; installed-package listing already works. Do not advertise unsupported wheels as installable.
- Console search, richer ANSI rendering, and large-output interaction beyond the retained window remain. Broad UI/motion polish and first-run/accessibility passes remain.

The older `b119229` checkout was left intact. The previously lost `f386e22` object was not recovered; this branch reconstructs new work from the valid `a86c076` remote base.
