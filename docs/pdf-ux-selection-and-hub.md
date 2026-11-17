# PDF selection, direct notes and simplified hub

Branch: `work/pdf-ux` (0.8.6 UX line, no version bump in this worktree).

## Problems addressed

- The PDF dock labelled text selection as **올가미**, a note-editor term with no
  visible active state. Many users never found PDF text markup.
- A tap had to land almost exactly on a glyph. Natural finger taps in the gap
  between words fell through to a tiny drag rectangle and selected nothing.
- The PDF hub listed every viewer action (29 rows) at once, with a second row of
  quick-jump chips. The note entry point was buried in the "문서" group.
- Opening the study pane left two stacked dock rows, taking vertical space away
  from the PDF and note.

## Text selection flow

1. The dock now has a primary **텍스트 선택** action in the old 올가미 slot.
   The text-annotation tool is labelled **글상자** so the two actions cannot be
   confused. The old `올가미` wording is gone from the PDF viewer.
2. Entering selection loads word geometry for the current page. The mode shows a
   primary-colored banner while loading and loaded, and an error-colored banner
   plus an explicit **텍스트 선택 종료** action when the page has no text layer.
3. A tap selects the nearest word under the finger. A drag selects every word
   intersecting the dragged rectangle. The word hit-testing is a pure helper in
   `PdfTextLayout.wordsForGesture`, covered by JVM tests; `PdfCanvas` only maps
   pointer coordinates and draws the existing overlay.
4. `PdfViewModel.loadTextWords` now sets `textLoading = true` synchronously
   before starting extraction. Previously the UI could briefly see "not loading
   and no words", which looked like a dead selection mode on slow/scanned PDFs.
5. The existing action bar is preserved: copy, Google Translate, excerpt, add to
   study pane, highlight, underline, strikeout, clear selection. Highlight,
   underline and strikeout still use the same `addTextMarkup` line geometry.

### Root cause of the "selection does not work" report

Selection code existed, but its discoverability and hit-testing were fragile:

- the entry was named after a note-editor lasso, not PDF text;
- overlapping/tiny word boxes required an exact hit;
- a tap with normal jitter could be treated as a drag rectangle that matched no
  word in inter-word whitespace;
- extraction started in a coroutine, so the first composition could show a
  no-text state before `textLoading` became true.

The fix promotes the action, adds an active/empty/loading state, and moves hit
testing into a tested pure helper with a finger-tolerant nearest-word fallback.

## Direct note entry

The PDF top bar now always shows a labelled **노트** action next to the bookmark
and hub buttons. It invokes `onOpenStudyNote(StudySource(...))` when the host
provides it (the host resolves/creates the study notebook). Without that
callback it falls back to the existing `StudyNoteSheet`. The primary hub tile
for **스터디 노트** remains.

## Hub layout

- The hub opens in a simple mode with two-column primary tiles:
  검색, 텍스트 선택, 페이지 관리, 주석 목록, 주석 숨기기/표시, PDF 내보내기 and
  스터디 노트 (when the host supports the live note pane).
- A full-width **모든 도구 보기** control expands the same sheet into the
  existing 읽기 / 문서 / 내보내기 groups, with every action still reachable.
  **간단히 보기** collapses it again.
- Primary rows are tinted in the expanded list. No actions were removed.

## Dense dock with the study pane

When `studyPane != null`, `ViewerToolDock(compact = true)` shows one tool row and
drops the separate undo/redo/style row. Undo, redo and **스타일** are available in
the **더보기** sheet's quick-action row, so no control is lost. The dock keeps
보기, 펜, 형광펜, 지우개, 글상자, 도형, 텍스트 선택 and 더보기. Text selection,
page navigation, the PDF back button and the pane's own close action stay
visible.

## Tests

- JVM: `PdfTextLayoutTest` covers tap tolerance, page filtering, blank words and
  drag intersection; `PdfHubSheetTest` covers primary-only simple mode, the
  seven-action cap, and full reachability in expanded mode.
- Instrumentation: `PdfCanvasSelectionE2ETest` composes `PdfCanvas` and verifies
  tap and drag gestures call `onTextSelection` (compiled with
  `assembleDebugAndroidTest`; requires a device to execute).

## Limitations

- Scanned/image-only PDFs still have no selectable text; the UI now says so and
  offers an explicit exit instead of leaving a silent mode.
- The hub's expansion state is per-open (simple by default); it is not persisted.
- No on-device UI/gesture test was run in this environment.
