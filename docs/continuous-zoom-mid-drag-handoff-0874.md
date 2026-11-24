# Continuous reader: zoom lands mid-drag — 0.8.74

Four agents analysed this in parallel (pan math, gesture pipeline, rendering
path / other read modes, test & build forensics). No device was attached, so
conclusions are code-verified and JVM-tested; the E2E regression compiles but
needs a device to run.

## User report

> 연속보기모드에서 화면이 확대되면 확대가 시작된 시작 왼쪽 이점보다 더
> 좌측으로는 이동이 안되어 pdf 전체 내용을 볼 수 없다

"In continuous view, once the screen zooms in I cannot pan further left than
the left edge point where the zoom started, so I cannot see the whole PDF."

## What the agents found

1. **Clamp math is already correct at HEAD** (v0.8.73). `readerMaxPanPx`,
   `clampReaderPan`, `panReaderAfterDrag`, `readerPanWithHeadroom` and
   `panAfterZoom` in `ReaderZoom.kt` have no lower floor; `pan = 0` is the page's
   true left edge and is reachable. Rendering (`Alignment.CenterStart`,
   `requiredWidth(pageWidth)`, `offset(-pan)`) exposes every page pixel. No
   horizontal scroll state or band crop creates a boundary. 15/15 JVM pan
   tests pass.
2. **No alternate code line.** HEAD is `f88bd84` (v0.8.73); all branches/tags
   that contain the continuous reader point at it. The single-page `PdfCanvas`
   still has the old symmetric band, but it is not the continuous mode.
3. **The surviving defect is a gesture-routing race.** The outer raw observer
   read `gestureZoom.floatValue` **per frame** to choose pan vs. fit-zoom page
   swipe. A double tap is committed by `detectTapGestures.onDoubleTap` on the
   tap detector's own schedule, so the first frames of a rightward drag can
   still see fit zoom: those frames go to the page-swipe accumulator, the zoom
   then lands and re-anchors `panX` at the focal, and the already-travelled
   distance is thrown away (the new branch starts its accumulators from zero).
   With a right-edge focal the zoom seeds pan near `0.75 * maxPan`, so losing
   the early travel reads exactly as "the page is stuck at the zoom-start
   point" — the left portion never appears in that gesture. A stale
   page-swipe flag could also turn the page on release.

## Fix (`ContinuousReaderScreen.kt`)

- Track zoom state **per gesture** (`gestureZoomed`, `zoomLandedMidGesture`)
  instead of re-reading it per frame.
- When a zoom lands mid-drag, hand the in-flight swipe over to pan: adopt the
  pressed pointer id, transfer `swipeTotalX` into `panTotalX`, and **apply that
  travel to `panX`** so the rightward push that began before the zoom landed
  counts toward reaching the page's true left edge in the same gesture.
- Once zoom has landed, keep the gesture in the pan branch for its remainder,
  and never deliver a fit-zoom page swipe (`zoomHandoff` trace added).
- Reset the per-gesture zoom flags on first down and on all-up.

The verified clamp math and headroom policy in `ReaderZoom.kt` are untouched.

## Tests

- `ReaderPanTest`: `drag travel accumulated before the zoom lands still reaches
  pan zero` — right-focal seed + 0.95 W pre-zoom travel reaches pan 0.
- `ReaderPinchE2ETest`:
  `doubleTapThenImmediateRightDragReachesTrueLeftEdgeWithoutTurningPage` —
  double tap at 0.85 W followed by a right drag in a separate block with **no**
  `waitForIdle`, asserting zoom applies, no page change, and the page's left
  border returns to its fit-zoom position.

## Verification

- `./.termux-build.sh :app:testDebugUnitTest` — pass (16 `ReaderPanTest` cases).
- `./.termux-build.sh :app:assembleDebug :app:assembleDebugAndroidTest
  -Parm64Only=true` — both pass.
- arm64 debug APK copied to `~/Downloads/MyPDFWriter-0.8.74-arm64.apk` and
  `/sdcard/Download/`; no `INTERNET` permission, `RECORD_AUDIO` only.
- No data-format, permission, or architecture changes.
