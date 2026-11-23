# Continuous-reader pan-after-zoom regression — 0.8.72

Three agents investigated in parallel (pan math, pointer pipeline, git history)
and converged on the root cause below.

## Symptom

In the continuous reader, zoom in with a pinch/double-tap, then immediately
drag right — the page does not pan, so the left part of the zoomed page cannot
be seen. This is the third recurrence of the v0.8.43–45 "headroom" bug.

## Root cause

`ContinuousReaderScreen` routed one-finger gestures with:

```kotlin
if (latestZoom.value > READER_ZOOM_MIN) { /* pan */ } else { /* fit-zoom page swipe */ }
```

`latestZoom = rememberUpdatedState(zoom)` is refreshed **only during
composition**. `setZoom` writes `zoom` from a pointer callback, so the next
one or two pointer frames can still see the old fit value. On a real device
(touch ~120 Hz, frames ~60 Hz) this is a reliable window: the retained finger
after a pinch is classified as a fit-zoom page swipe, `swipeDelivered` is set,
and the page turns (or nothing moves) instead of panning.

The pan math itself (`readerMaxPanPx`, `clampReaderPan`,
`panReaderAfterDrag`, `readerPanWithHeadroom`) was verified intact — every
invariant from v0.8.43/44/45 is still present and JVM-tested.

Secondary gaps found by the agents:
- The saved-zoom restore seed called bare `panAfterZoom` (no 25% headroom), so
  it could plant `panX` at a clamp edge where a rightward drag is a no-op.
- The re-clamp effect key omitted `zoom`, and used plain `clampReaderPan`,
  which does not preserve headroom when the viewport changes.
- Pan/swipe tracking could leak between gestures after a missed pointer-up.

## Fix

1. **Synchronous gesture zoom snapshot** (`gestureZoom`): `setZoom` writes it
   before writing composable `zoom`; the raw `PointerEventPass.Initial`
   observer reads only `gestureZoom.floatValue`. Initialized from the saved
   `zoom` and re-synced in the effect, so restored zoom is also correct.
   `rememberUpdatedState(zoom)` is removed from gesture routing entirely.
2. **Fresh-gesture reset**: on the first pointer-down of a new gesture, reset
   `panPointerId`, `panTotalX`, `panStarted`, `swipeDelivered`, `swipeTotalX/Y`.
3. **Restore seed through headroom**: new pure helper `readerRestoredPan(zoom,
   viewportWidthPx)` calls `readerPanWithHeadroom` from fit with a centred
   focal, so restored zoom always leaves ≥25% travel in both directions.
4. **Re-clamp preserves headroom**: `LaunchedEffect(viewportWidthPx, zoom)`
   now uses `readerPanWithHeadroom` (not `clampReaderPan`) while zoomed, so
   a viewport change can never park the page at an edge.

## Tests

- `ReaderPanTest`: restored pan leaves both directions reachable at every zoom
  step; re-clamping a headroom pan never parks it at a clamp edge (15 cases).
- `ReaderPinchE2ETest.zoomThenImmediateRightDragInOneGesturePansInsteadOfTurningPage`:
  zoom and rightward drag injected in ONE touch block (no `waitForIdle`
  between) — asserts zoom applies, no page change is dispatched, and the page
  stays visible.

## Verification

- `./.termux-build.sh :app:testDebugUnitTest` → 1041 tests, 0 failures.
- `:app:assembleDebugAndroidTest` compiles (on-device run needs a device).
- Architecture unchanged: no new permissions, no network, pan model untouched.
