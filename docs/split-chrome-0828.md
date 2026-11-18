# Split-view divider and toolbox placement — 0.8.28

## Goal

When a PDF and a study note are open together, make the resize control obvious
and keep the PDF/note toolboxes from covering the reading and writing areas.

## Implemented

### Center divider

- Resize hit row increased from 18 dp to **28 dp**.
- A visible pill shows a grip and the current note share (`노트 N%`).
- Drag up/down resizes within the existing 35–85% range using the existing pure
  helper.
- Tap opens a compact popup with `50% · 70% · 85%` presets; double-tap toggles
  between 50% and 85%.
- A trailing `PDF 도구` chip toggles PDF toolbar visibility (default visible).
  The note header's own drag/resize and dismiss behavior remains available.

### PDF toolbox placement

- The floating PDF toolbar moved from the bottom overlay to a **top-center**
  translucent strip, clear of the top-start back button and any top-end search
  chip. It still reserves no layout height.
- A pure density policy reduces the toolbar to essential page controls when the
  note pane takes more than 80% (very short PDF strip); resizing back or using a
  preset restores the full toolbar.
- The toolbar scrolls horizontally on narrow phones; all callbacks remain.
- Selection actions are unchanged and still markup-first.

### Note toolbox placement

- The study-pane header gained a `도구 표시/숨기기` toggle.
- With tools visible, the existing compact dock stays at the lower edge.
- With tools hidden, the whole dock is removed and one small `도구` restore pill
  sits at the bottom-right, above the navigation inset and away from the writing
  hotspot.
- The pane header remains the single excerpt drop target; drag-resize, size
  presets, links and close are unchanged.

## Notes

- Divider drag no longer dismisses the pane on overshoot; dismissal remains on
  the pane header's grip. This avoids accidental pane loss while resizing.
- Compact dock visual rows remain dense by design; the new toggle and restore
  pill are 44 dp targets.
- Toggle state is session-scoped; reopening the pane starts with tools visible.

## Verification

- JVM: **581 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- Instrumentation compiled but not executed (no adb device here).
