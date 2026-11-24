# Continuous reader diagonal scrolling — 0.8.77

## Request

After the pan fix, the user reported that diagonal one-finger dragging did not
scroll the continuous reader.

## Cause

The 0.8.76 horizontal orientation-lock detector consumed the whole
`PointerInputChange` after horizontal slop. Compose consumption is not
axis-specific, so the same event's vertical delta could no longer reach the
LazyColumn. When vertical slop won first, the pan detector was canceled
instead.

## Fix

- Move one-finger horizontal pan back into the existing raw
  `PointerEventPass.Initial` observer, but keep it **non-consuming**.
- The observer tracks the active pointer, accumulates horizontal slop, applies
  `panReaderAfterDrag`, and leaves every one-finger event untouched for the
  child LazyColumn. A diagonal gesture therefore updates panX and native
  vertical scroll/fling from the same pointer stream.
- On a two-finger pinch, reset pan tracking. When one finger remains, explicitly
  re-arm pan on that retained pointer without waiting for all-up.
- Fit-zoom page swipe remains horizontal-dominant; diagonal movement stays with
  vertical scrolling.
- The 0.8.76 explicit page Layout and true-edge coordinate fix are unchanged.

## Tests

- `diagonalDragPansHorizontallyAndScrollsVertically`: asserts both page-left
  displacement and page-top displacement after a zoomed diagonal drag.
- `verticalOnlyDragScrollsWithoutHorizontalPan`: asserts native vertical
  movement with no horizontal pan.
- Existing retained-finger and true-edge regressions retained.

## Verification

- `testDebugUnitTest` and `assembleDebugAndroidTest` pass.
- arm64 debug APK delivered as 0.8.77 / versionCode 86.
- Instrumentation execution still requires a connected device.
