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

## Floating split chrome

When `studyPane != null`, the viewer no longer emits its TopAppBar or its bottom
tool dock. The PDF canvas starts at the top safe edge and the study pane takes
85% of the full remaining height (same clamp and 50/70/85% presets, so the pane
keeps the same fraction while gaining the height the bars used to consume).

All PDF controls become compact floating overlays over the PDF strip:

- a 36 dp translucent back button at the top-left;
- a rounded floating row at the bottom of the strip with the page pill
  `‹ n / N ›` (previous/next and a page-sheet tap), the text-selection toggle
  (idle/loading/active/empty state), the current-page bookmark star, the PDF tool
  picker and the PDF hub.

The large PDF hint pill and the PDF-side study drop target are hidden in split
mode; the study-pane header keeps its own capture target. Text selection swaps
the floating row for a compact selection bar
(highlight/underline/strikeout/copy/more/close) that overlays the strip instead
of adding a permanent row. "More" keeps Google Translate, excerpt, clipboard,
add-to-pane and clear reachable. The selected-text status chip remains a drag
source into the pane, and the floating PDF tool picker still exposes every
annotation tool plus undo/redo and the style sheet, so removing the dock does
not remove any annotation capability.

The full-screen viewer (`studyPane == null`) keeps its original app bar, dock,
hint pill, drop target, selection bar and page navigator unchanged.

## Tests

- JVM: `PdfTextLayoutTest` covers tap tolerance, page filtering, blank words and
  drag intersection; `PdfHubSheetTest` covers primary-only simple mode, the
  seven-action cap, and full reachability in expanded mode.
  `PdfSplitChromeTest` covers the split/focus chrome policy (app bar, dock, hint
  and drop-target visibility), the selection toggle states and the 36 dp target
  range.
- Instrumentation: `PdfCanvasSelectionE2ETest` composes `PdfCanvas` and verifies
  tap and drag gestures call `onTextSelection`; `PdfSplitChromeE2ETest` checks
  the floating toolbar and selection bar dispatch every control. Both are
  compiled with `assembleDebugAndroidTest`; device execution is not available in
  this environment.

## Limitations

- Scanned/image-only PDFs still have no selectable text; the UI now says so and
  offers an explicit exit instead of leaving a silent mode.
- The hub's expansion state is per-open (simple by default); it is not persisted.
- At the default 85% pane the PDF strip is about 15% of the screen, so the page
  is a context preview behind the floating controls. The 50/70% presets give it
  more room; the pane fraction itself is unchanged.
- No on-device UI/gesture test was run in this environment.
