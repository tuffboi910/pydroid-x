# Mobile editor regression checklist

Version 0.2.0 keeps the existing Python runtime, project files, package tools and helper.
It replaces the autocomplete insertion/session/rendering logic and compacts the editor chrome.

## Automated checks

- Python: real Jedi completions, signatures/docs, case correction, imports, existing parentheses,
  mid-token replacement and UTF-16 offsets; existing diagnostics/runtime/input tests.
- JVM: completion snapshot/range validation, invalidation and exact selected item insertion;
  existing history, console, project and indentation tests.
- Robolectric: two-second debounce, second-row touch release, drag/cancel, stale asynchronous
  results, completion undo/redo. These simulate Android views, not a physical phone keyboard.

## Device checks still required before calling this release fully verified

1. On a narrow phone with the keyboard open, type `pri`: no list before two idle seconds.
   Continue typing, move the cursor, select text, switch files: previous results must disappear.
2. Type `va`, tap `ValueError()`: insert exactly that item with the cursor inside parentheses.
   Drag off the row or cancel the gesture: do not insert. Undo and redo the whole completion.
3. Scroll a long file vertically, then deliberately horizontally with word wrap off. Verify
   the gutter, current-line highlight and popup remain aligned with text. Repeat with wrap on.
4. Open the keyboard near the bottom line and rotate the device. Popup should remain within
   the editor and not cover the caret; docs collapse when there is insufficient space.
5. Run `name = input('Name: '); print('Hello', name)`. Typed input stays visible above the
   keyboard; Send clears the entry without echoing it into output. Repeat in Terminal.
6. Trigger a runtime error, inspect Details, use Fix and Ask Astro. Check large output,
   Stop, new/open/save files, imports and package installation separately.
7. Test at 320dp/360dp/412dp widths and with larger system fonts. Verify tab labels do not
   wrap, undo/redo remain beside the filename, and navigation works by tapping the header.

No claim of complete VS Code parity or a bug-free release is implied by passing these checks.
