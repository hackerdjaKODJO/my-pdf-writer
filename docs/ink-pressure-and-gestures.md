# Ink pressure curves and gesture undo/redo

Status: unreleased worktree `work/ink-gestures`, based on the 0.8.4 code line.
This document is the implementation note for the two ink input additions. It
does not claim hardware validation and does not change the app version.

## Pressure response

`PressureCurve` is a pure mapping in `data/InkPressure.kt`:

| Preset | Korean label | Mapping | Intended feel |
| --- | --- | --- | --- |
| `SOFT` | 부드럽게 | `raw^0.65` | Light contact becomes visibly thicker sooner |
| `LINEAR` | 선형 | identity | Default; matches pre-curve 0.8.4 behavior |
| `FIRM` | 강하게 | `raw^1.55` | Requires a firmer press before the stroke swells |

All presets are exact identities at raw pressure `0` and `1`. Out-of-range
samples are clamped, and non-finite samples fall back to the neutral pressure
`1`. Fingers, mice and legacy strokes therefore keep the neutral value, and the
existing `prs` JSON array remains optional and unchanged.

The note overflow `필기 설정` sheet contains a `필압 반응` selector. It is
session-only, defaults to `LINEAR`, and is disabled when `필압 잉크` is off. The
selector shows a compact `25→75%` raw-to-effective example; it is an illustration,
not a calibration claim.

The debug trace records a `pressure-range` event with the selected curve and the
raw/effective minimum and maximum for one stylus gesture. It never stores or logs
page coordinates. A second `pressure` event records the committed point count,
the number of points below neutral, and the stored pressure range.

**Hardware limitation:** no physical pressure-sensitive tablet was attached
while this change was written. The curve shapes are deterministic software
presets. Real digitizers differ in reported range, dead zone, noise and
linearity, so device-specific pressure tuning and on-device verification are
still required. The app does not claim that these presets match any particular
stylus.

## Gesture undo/redo

`NoteGestureClassifier` in `ui/NoteGestures.kt` is a pure classifier:

- exactly two pointers = undo;
- exactly three pointers = redo;
- duration at most `400 ms`;
- centroid movement at most `1.2 ×` the platform touch slop;
- rejected when a drawable stroke began, a stylus/eraser was seen, or a
  pan/zoom changed the view.

`NoteCanvas` supplies the platform touch slop and accumulates pointer movement.
The `제스처 실행 취소` toggle in `필기 설정` defaults to on and is session-only.
The callbacks are optional (`onGestureUndo`/`onGestureRedo` are nullable with
`null` defaults), so existing callers and the E2E tests compile unchanged.
When the gesture fires, `NoteEditorScreen` calls its normal `undo()`/`redo()`
functions, so the 60-step history, page-index clamping and `NoteTrace` events
are unchanged.

The trigger is deliberately limited to navigation gestures. In finger-writing
mode, the first finger begins a drawable stroke, so a multi-finger tap is not
reinterpreted as undo/redo. Stylus-led gestures and lasso strokes are likewise
excluded. Gesture tracing contains only the action name and pointer count.

## Verification

- `./.termux-build.sh :app:assembleDebug`
- `./.termux-build.sh :app:testDebugUnitTest`
- `./.termux-build.sh :app:assembleDebugAndroidTest`

Unit coverage includes curve endpoints, clamping, non-finite inputs, preset
ordering, neutral/legacy pressure preservation, non-zero width, diagnostic
ranges, gesture pointer counts, movement/time thresholds, and the default-on
toggle. On-device multi-touch and pressure behaviour still needs a real device
test; no such claim is made here.
