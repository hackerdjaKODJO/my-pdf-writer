# Continuous pan left-edge cutoff — 0.8.73

Four agents analysed this in parallel: headroom/effect interaction, trace-log
forensics, independent coordinate-model verification, and test-coverage audit.

## User report

> 좌우로 움직이긴 해, 근데 처음 확대를 시작한 좌측 끝부분 더 이상으로는 왼쪽으로 못가

"Horizontal pan works, but I cannot go further left than the left edge point
where the zoom was first started."

## Diagnosis (two compounding causes)

### 1. Symmetric 25% headroom cut off the page's left side
`readerPanWithHeadroom` clamped zoom results into `[25%, 75%]` of `maxPan`.
At viewport W=1080, zoom 2.0: `maxPan = 1080`, band `[270, 810]`. A left-edge
focal produced `focalPan = 0` → forced to **270**, so visible page pixels were
`[270, 1350]` = 12.5%–62.5%. The page's leftmost 12.5% was off-screen from the
moment zoom landed — exactly the "zoom-start left edge" the user named.

The original purpose of the band (v0.8.45) was to guarantee the FIRST drag
after an edge-focal zoom is not a no-op. But the page is left-aligned
(`Alignment.CenterStart`, pan 0 = page left edge), so a left-edge focal landing
at pan 0 is **correct** — there is no hidden content to reach. Only the
right-side cap is needed (a right-edge focal at maxPan would make a leftward
drag a no-op).

### 2. v0.8.72's effect re-applied the band after user drags
`LaunchedEffect(viewportWidthPx, zoom)` used `readerPanWithHeadroom` on every
zoom change. Its body is equivalent to `pan = clamp(pan, 25%, 75%)`, so a user
drag to pan 0 could be yanked back to 270 when a zoom-triggered composition
landed after a pan frame — the "sticky" feel. The re-clamp was also invisible
in logs/trace.

The v0.8.72 synchronous `gestureZoom` fix (routing drags to pan instead of
page-swipe) was correct and is retained.

## Fix

1. **`ReaderZoom.readerPanWithHeadroom`**: cap only the upper end
   (`coerceAtMost(maxPan - reserve).coerceAtLeast(0f)`). Left-edge focal now
   yields pan 0; page leftmost content visible immediately.
2. **`ContinuousReaderScreen` effect**: use plain `clampReaderPan`
   (`[0, maxPan]`) — never re-apply headroom to a user pan. Add a
   `PdfContinuousTrace.zoomPan("reclamp", ...)` event when the clamp actually
   changes pan, so future regressions are visible in `logcat -s
   PdfContinuousPan` and the trace sheet.
3. **`readerRestoredPan`** keeps the same right-only policy for saved zoom.
4. **Tests**: rewritten `ReaderPanTest` cases for left-edge/right-edge/reclamp;
   new E2E `rightDragAfterZoomReachesTheTrueLeftEdgeNotTheHeadroomBand`
   asserts the page's left border reaches its fit-zoom position after a large
   rightward drag.

## Log note
No adb device was attached, so no live logcat could be pulled. The re-clamp
path previously emitted no trace at all; it now does. The trace logcat tags
are `PdfContinuousPan` (pan/zoomPan/pageSwipe), `PdfContinuousPinch`,
`PdfContinuousRender`; in-app tag `PDF_CONTINUOUS`.

## Verification
- `./.termux-build.sh :app:testDebugUnitTest` + `:app:assembleDebugAndroidTest`.
- Full arm64 debug + release builds.
- No permission/network/data-format changes.
