# Unobtrusive PDF navigation — 0.8.70

User report: the floating zoom pill and page pill at the bottom of the PDF
view cover the page itself and get in the way of reading.

## Change

1. **Zoom pill removed** from both readers (paged `ViewerScreen` and
   `ContinuousReaderScreen`). Zoom stays on the gestures that already exist:
   two-finger pinch and double-tap fit↔200%. The now-unused
   `ViewerZoomPill`, the stacked/side-by-side bottom-controls layout policy
   and the `stepReaderZoom` button policy were deleted with their tests.
2. **Page navigation docked below the page** instead of floating over it:
   - Paged viewer: a 56 dp layout row (`ViewerBottomPageBar`) under the canvas
     Box hosts the existing `ViewerPageNavigator` (‹ 3 / 12 ›, counter opens
     the page sheet). The canvas sizes itself above the row, so no control
     ever renders on top of the PDF.
   - Continuous reader: the same bar sits under the page list. ‹/› scroll one
     page (`continuousReaderScrollTarget`, clamped), the counter opens a
     numeric page-jump dialog sharing `parsePageJump`. The list's old 60 dp
     bottom padding (reserved for the floating indicator) drops to 8 dp, so
     reading gains space.
3. **Visibility policy** (`viewerBottomPageBarVisible`): the bar shows only in
   the full-screen reader with chrome visible. Split/study mode keeps its
   floating top toolbar (the strip is too short for a permanent row), and
   immersive auto-hide removes the bar together with the top app bar and tool
   dock — page swipes still turn pages there.

## Split/study mode follow-up (0.8.71)

The first pass only fixed the full-screen reader — in split mode the
`SplitPdfToolbar` still floated a page pill over the PDF strip. That pill is
now removed from the floating toolbar; page navigation moves into the
48dp control row below the PDF strip (`SplitDivider`), alongside the resize
grip and PDF-tool chip. A full-width `Row` with `SpaceBetween` and an
ellipsizing grip label keeps all three controls from overlapping on narrow
phones. Split toolbar inventory, JVM/E2E tests and the help text were updated.

## Unchanged

- Split-mode chrome, page sheet, tool dock, selection bars, tab strip, lock
  badge and drop target keep their contracts; the split E2E page controls
  ("이전 페이지"/"다음 페이지"/"페이지 선택") are untouched.
- Pinch/double-tap zoom, fit-zoom horizontal page swipe, zoom persistence and
  the continuous reader's shared zoom/pan model are untouched.
- TalkBack vocabulary stays: the docked navigator reuses
  `viewerPagePillDescription` ("페이지 n / m, 목록 열기") and the same
  prev/next labels.

## Help / guide

- In-app help and `docs/usage-guide.html` no longer mention the ± buttons;
  they describe pinch + double-tap zoom and the docked 하단 페이지 바.
- Content guards updated: `HelpCenterContentTest`, `UsageGuideContentTest`,
  `HomeAndHelpE2ETest` now assert the new vocabulary.

## Verification

- New JVM tests: `ViewerBottomPageBarTest` (visibility policy, bar height vs
  48 dp targets, scroll-target clamping); stale zoom-step/stacking tests
  removed from `ReaderZoomTest`.
- `./.termux-build.sh :app:testDebugUnitTest`, `:app:assembleDebug
  -Parm64Only=true`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`.
- On-device visual check still recommended (no adb device attached here).
