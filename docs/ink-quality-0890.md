# Commercial ink quality — 0.8.90

This document records the evidence, scope and invariants for the 0.8.90
pressure/handwriting pass. It is a behavioral reference, not permission to copy
competitor code or assets.

## Method and limits

- Target source: My PDF Writer `0.8.89` / commit `9ab7ab2`.
- Read-only static source audit plus APK inspection. No competitor app was
  executed or installed; no pressure-sensitive hardware was attached.
- Tools: project source/tests, existing decompiled outputs, `aapt2`, and the
  Androguard 3.3.5 DEX parser. REA Android providers lacked local apktool/JADX
  prerequisites, so nothing was installed or reconfigured.
- Class/resource names prove an affordance exists, not its runtime formula,
  default, ordering or visual quality.

## Reference artifacts

| App | Package / version | SHA-256 |
| --- | --- | --- |
| Samsung Notes | `com.samsung.android.app.notes` / `4.4.48.15` | `484d3dd599355789508034ba90af9a35e09f81532564a992c131710632a467ce` |
| StarNote | `com.onyx.galaxy.global.note` / `1.5.4.1` | `0d7ec1d4de20cc0565215ee757d38a0fcf29b3d892def4944317dfd01b608897` |
| Flexcil | `com.flexcil.flexcilnote` / `1.5.1.4` | `76ede392aed40bcb89d7ec5500b925b773995d1868eb879278dbab9a30621bdb` |

## Observed reference patterns

### Samsung Notes

- `SpenObjectStroke` carries pressure, tilt/orientation, timestamps, points,
  tool provenance and pen settings.
- `SpenMotionEvent.EventInfo` represents pressure/tilt/orientation and whether an
  event was resampled.
- Prediction, palm rejection and finger drawing are separate writing options.
- Pen attributes include fixed/variable width, fixed/variable alpha, curve and
  particle controls; dedicated native engines exist for fountain/brush/ink.
- Erasers are target-aware (stroke/highlighter) and highlighter has a dedicated
  blending path.

### StarNote

- Ink pen controls expose nib, pressure, thickness, stabilization and velocity.
- Velocity has sensitivity, ignore threshold and lower/upper bounds.
- Eraser supports pressure-sensitive size and target filters.
- Pen-only versus finger/stylus writing modes and palm rejection are explicit.

### Flexcil

- Drawing points store x, y and a third float used as nib width.
- Fountain dynamics normalize pressure, smooth it, apply a speed modifier and
  rate-limit width changes.
- Speed is derived from timestamped distance/time.
- Presets persist smoothing level; eraser modes include pressure and
  highlighter-scoped deletion.
- A separate low-latency surface renders incremental point batches.

These are structural signals. Exact competitor formulas remain unknown.

## Current My PDF Writer strengths

- Shared `effectiveInkPressure()` capture and `AnnotationPainter` across PDF,
  notes, replay and export.
- Adaptive min/max calibration, dead zone, gamma curves and asymmetric temporal
  smoothing.
- Distinct ballpoint/fountain/brush/pencil/highlighter response profiles.
- Deterministic dry-skip, graphite grain, dwell pools and paper texture.
- Full-stroke context is derived before replay slicing for most renderers.
- Effective pressure is persisted per point; neutral legacy JSON remains exact.

## Gaps selected for 0.8.90

1. **No per-point timing.** Speed and replay were sample-count based, so event
   rate and dwell density changed physical behavior.
2. **Non-finite pressure bug.** NaN/Infinity became `1f` before the smoother and
   could poison its state.
3. **Scale-dependent effects.** Fixed pixel dwell/texture thresholds differed
   between editor, export and thumbnails.
4. **Pencil replay drift.** Revealed pencil segments recomputed speed reference
   and taper from the prefix.
5. **Note preview/commit mismatch.** Short strokes bypassed commit refinement;
   shape widths could change on release.
6. **Eraser divergence.** PDF and notes used different radii/hit geometry; PDF
   lacked stylus-eraser routing and pressure radius.

## Implemented design

### Backward-compatible timing

- `NormalizedPoint.timeMs: Long?` is relative to stroke start.
- `Annotation.timesMs: List<Long>?` serializes as optional `ptms`.
- Missing/invalid timing falls back to the existing uniform `durationMs` model.
- Existing `pts`, `prs`, `ct`, `dur` and neutral-pressure omission are unchanged.

### Pressure and input

- Time-normalized attack/release smoothing with the existing sample-based API
  retained for compatibility.
- Non-finite raw pressure is rejected before calibration/smoothing.
- Constant-pressure stylus holds emit bounded timed samples for true dwell.
- PDF stylus erasers route to the eraser tool.

### Dynamics and replay

- Timestamped speed uses physical page distance/time; legacy strokes retain
  sample-distance fallback.
- Dwell radius is a canonical fraction of page size and strength is time-based.
- Pencil replay uses full-stroke speed/taper/deposit references.
- Replay point count honors nonuniform `ptms` timing.

### Stroke preparation and erasing

- Note preview and commit share one pure preparation pipeline.
- Refinement preserves endpoints, pressure extrema and timestamps.
- One normalized eraser radius, capsule hit test and trail densifier serve PDF
  and notes.
- Stylus pressure scales eraser radius within bounds; finger input stays fixed.

## Deferred to a later pass

- AndroidView/MotionEvent coalesced-history bridge and front-buffer wet layer.
- Motion prediction and device-specific latency tuning.
- Tilt/azimuth capture and tilt-sensitive nib rendering.
- Full causal rewrite of stroke-global means/local alpha for every pen.
- Annotation overlays in continuous/spread readers.
- Per-device persisted pressure profiles and custom curve editing.

These require hardware/runtime validation or broader rendering changes and are
not claimed as complete.

## Testable invariants

- Legacy JSON and neutral strokes render exactly as before.
- NaN/Infinity never changes calibrator or smoother state.
- Same physical velocity at 60/120/240 Hz yields equivalent bounded dynamics.
- Constant-pressure timed hold creates one bounded pool; movement creates none.
- Dwell decisions match at 2.0x, 2.2x and 2.5x render scales.
- Pencil replay prefix width/alpha/deposit equals the full-stroke plan.
- Note preview equals commit for ordinary strokes of all tested lengths.
- Pressure extrema and timestamps survive refinement.
- Eraser preview radius equals hit radius; sparse fast trails still hit.
- Stylus + eraser erases in PDF; finger eraser radius remains fixed.

## Verification policy

JVM tests cover timing, smoothing, dynamics, replay, preparation, eraser geometry
and JSON compatibility. Instrumentation tests must compile. A real stylus device
is still required to validate latency, calibration range and subjective feel.
