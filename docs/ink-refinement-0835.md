# Open-source handwriting review and original ink refinement — 0.8.35

## Survey

Three read-only agents surveyed permissive open-source projects and our current
handwriting pipeline. Relevant public references:

- `steveruizok/perfect-freehand` (MIT) — pressure/velocity → variable-width
  filled stroke outline; streamline + taper + offset tracks.
- `dulnan/lazy-brush` (MIT) — trailing brush / "lazy area" tremor filter.
- `casiez/OneEuroFilter` (BSD/algorithm) and `stroke-stabilizer` (MIT) —
  adaptive low-pass and filter-pipeline ideas.
- `rough-stuff/rough` (MIT) — deterministic sketchy/jitter design.
- `paperjs/paper.js` (MIT) — RDP/Catmull-Rom/Chaikin smoothing/simplification.
- Android `MotionEvent` history, `MotionEventPredictor`, Jetpack Ink /
  `google/ink` (Apache 2.0) — low-latency input and wet/dry ink architecture.

Excluded: current tldraw SDK (custom/proprietary license), AGPL projects,
vendored unlicensed calligrapher weights, and any copied code/assets/fonts.
Everything below is an **original Kotlin implementation of published behavior**
with no new dependency, permission, schema key or network use.

## Implemented

### Variable-width outline
New pure `PenStrokeGeometry` helpers derive a filled outline from pressure and
velocity, with tapered ends and round caps. `AnnotationPainter` gates this to
strokes with real pressure variation: legacy, finger and uniform-pressure
strokes keep the old per-style renderer byte-for-byte. Replay slices
full-stroke widths rather than recomputing them.

### Lazy brush + RDP + Chaikin
New pure `NoteInk` helpers add trailing-brush smoothing, iterative
Ramer–Douglas–Peucker simplification and bounded Chaikin corner cutting.
`refineStroke` runs once at commit behind session toggles (`부드러운 필기`,
`점 간소화`); stored strokes are never reprocessed on load, and live preview is
unchanged. Endpoints and `pts`/`prs` alignment are preserved.

### Graphite texture
Pencil grain is now arc-length-seeded rather than a repeating point-index sine
for non-neutral strokes. Neutral strokes keep the exact old constants, so old
notebooks export identically.

### Pressure calibration
A session-scoped `PressureCalibrator` EWMA-learns the stylus range and maps it
into 0..1 with a dead zone before the existing `PressureCurve` runs. A stylus
that only reports 0.2–0.7 can now reach full response. Finger/mouse/eraser stay
neutral; raw pressure traces remain uncalibrated.

## Compatibility / verification

- No JSON schema, backup format, dependency, permission or ABI change.
- Neutral-pressure strokes keep old geometry/alpha/export output.
- Preview and export still share `AnnotationPainter`.
- JVM: **773 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- Real stylus calibration/feel needs a device; no adb device was available.

## Deferred

- Coalesced `MotionEvent` history / prediction / front-buffer wet ink
  (requires an AndroidView-based input bridge).
- One Euro causal filter and per-preset feel persistence.
- Live eraser preview and time-based replay reveal.
