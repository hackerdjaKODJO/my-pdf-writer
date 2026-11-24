# Continuous reader pan — standard drag detector (0.8.75)

## Why another fix

The v0.8.74 handoff was installed and tested by the user: **the page still did
not move at all** when dragging right after zooming. That rules out the
"early drag frames are discarded" theory. The manual one-finger pan state
machine inside the raw `PointerEventPass.Initial` observer is itself
unreliable on the device (branch/gate/state bookkeeping), so it was removed
rather than patched again.

## Fix

`ContinuousReaderScreen.kt`:

- The raw Initial-pass observer now owns only **pinch zoom** (two-finger) and
  **fit-zoom page-swipe detection**. Its one-finger pan branch and all the
  `panPointerId` / `panTotalX` / `panStarted` / `zoomLandedMidGesture` state
  were deleted.
- One-finger panning is now a dedicated `pointerInput` modifier using Compose's
  `detectHorizontalDragGestures`. The detector owns slop, pointer-id tracking
  and event consumption; `onHorizontalDrag` calls `panReaderAfterDrag` with
  the detector's `dragAmount`. It consumes only horizontal movement after
  slop, so the LazyColumn keeps its native vertical fling.
- Page-swipe delivery at all-up is suppressed whenever the reader is zoomed,
  so a zoom landing mid-swipe can never turn the page.
- The DEBUG HUD (zoom/render/bitmap/pan overlay) and its `ReaderRenderInfo`
  state were removed — it covered the PDF the user was trying to read.

## Tests

- `ReaderPinchE2ETest` drag injections now use 8 incremental move frames:
  `detectHorizontalDragGestures` consumes the slop-crossing frame, so a single
  large `moveTo` would never produce an `onHorizontalDrag` callback.
- Existing pan/zoom E2E cases retained; JVM pan math unchanged (ReaderZoom.kt).

## Verification

- `./.termux-build.sh :app:testDebugUnitTest :app:assembleDebugAndroidTest
  -Parm64Only=true` — pass.
- arm64 debug APK copied to Downloads with version 0.8.75.
- No device attached, so E2E execution still needs an on-device run.
