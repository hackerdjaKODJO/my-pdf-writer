# Continuous reader pan — five-agent re-audit (0.8.76)

## User confirmation

The user installed 0.8.75 and confirmed the right drag after zoom still caused
**zero page movement**. Five agents independently audited gesture delivery,
layout/snapshot placement, zoom math, test/artifact validity, and host/other
reader paths. They converged on two real defects.

## Root causes

### 1. Retained finger had no pan detector after pinch

The raw Initial-pass observer consumes two-finger events. In Compose Foundation
1.10.0, `detectHorizontalDragGestures` treats that consumption as cancellation;
`awaitEachGesture` then waits for **all** pointers to rise. Lifting one finger
and dragging the other therefore produced no pan callback.

### 2. `requiredWidth` silently centered the oversized page

The old page layout assumed `Alignment.CenterStart` placed the real page origin
at `x = 0`. Compose coerces the oversized placeable and adds an
apparent-to-real centering offset. At `W = 1080, zoom = 2`, the real origin was:

```text
-pageWidth/2 + viewportWidth/2 - pan = -540 - pan
```

not `-pan`. At pan 0 the true page-left edge was still 540 px off-screen; at
maxPan the right side ended 540 px before the viewport right. Both page edges
were therefore unreachable even though the pan arithmetic claimed they were.

## Fix

- Replace `detectHorizontalDragGestures` with the orientation-lock overload of
  `detectDragGestures(Orientation.Horizontal)`. It retries slop detection after
  another node consumes the gesture while a pointer remains down, so the
  retained finger is picked back up after pinch.
- Replace `BoxWithConstraints` + `requiredWidth` + `Modifier.offset` with an
  explicit `Layout`: measure the Surface at the exact integer zoomed page
  width/height, keep the Layout itself viewport-wide, and place the Surface at
  `x = -pan`. This makes the coordinate model match `ReaderZoom.kt`.
- Compute `readerPageWidthPx` and `readerMaxPanPx` from the same integer width
  the layout measures, eliminating zero-pixel zoom states.
- Keep the DEBUG HUD removed (done in 0.8.75).

## Regression tests

- JVM: tiny zoom has zero effective overflow; one rounded pixel gives one pixel
  of pan.
- E2E:
  - pinch → lift one finger → retained-finger drag, split across injection
    blocks, asserts actual displacement;
  - immediate retained-finger drag after pinch asserts displacement and no
    page turn;
  - right drag reaches exact fit-zoom left edge;
  - left drag reaches exact viewport right edge;
  - removed impossible edge-focal thresholds and obsolete handoff-only test.

## Verification

- `testDebugUnitTest` and `assembleDebugAndroidTest` pass.
- No device attached: instrumentation still requires on-device execution.
- arm64 debug APK delivered as 0.8.76 / versionCode 85.
