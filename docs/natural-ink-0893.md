# Natural handwriting pass — 0.8.93

## Why EasyNotes felt smoother

Static analysis of EasyNotes `1.3.66.1117` (`classes5.dex`) shows a deliberately
simple freehand pipeline:

- `ActionPathPen.onMove` rejects movement until either axis reaches `1.0 dp`.
- Accepted raw points are rendered as a midpoint-quadratic chain:
  `moveTo(P0)`, `quadTo(Pi, midpoint(Pi, Pi+1))`, then `lineTo(Pn)` on lift.
- Color/highlighter/dash pens use one constant-width `Path`; there is no pressure,
  tilt, historical-event or palm-rejection branch in that path.
- Completed ink is cached in a dry bitmap and only the active pen is drawn wet.

My PDF Writer already had the same midpoint renderer for neutral ink, but its
default note pipeline added three layers ahead of it: 25% non-causal stabilization,
a trailing lazy brush, and Chaikin corner cutting. Coalesced history also bypassed
the movement gate entirely. The visible tip could move backward when the next
sample arrived, and pressure-varied ballpoint could switch to a noisy offset
outline. Those differences—not a lack of filters—were making lines look crooked.

## Implemented changes

### Capture and centerline

- Movement is now filtered by approximately `1 dp`, matching EasyNotes' tolerance,
  for both current and coalesced historical samples.
- Sub-`1 dp` movement accumulates against the last accepted point instead of being
  stored as geometry. True stationary contact below `0.1 px` still emits dwell ink.
- Motion prediction is excluded from note geometry; noisy two-point velocity no
  longer bends the canonical centerline. The buffer API remains available for
  future transient overlays.
- Default `필기 보정` and `부드러운 필기` are now off. The default path is:
  accepted raw points → one midpoint-quadratic renderer. Both remain available as
  opt-in corrections.

### Ballpoint and ribbons

- Ballpoint keeps a continuous midpoint-quadratic centerline for neutral and
  pressure-varied ink. Local pressure still controls segment flow; interior pieces
  use butt caps and only the two stroke terminals are round, avoiding per-sample
  blobs without flattening pressure into one stroke-wide alpha.
- Pressure-varied fountain/brush ribbon edges use midpoint-quadratic tracks.
  Neutral legacy fountain/brush retain their original polygon rendering.
- Pressure-varied fountain, brush and pencil end envelopes use physical arc length
  rather than sample index; neutral legacy envelopes retain index-based behavior.
- Pressure response endpoints are now truly exact; the 1:2:1 smoother no longer
  blends the first/last sample with a clamped neighbour.
- Cold calibration requires at least `0.12` pressure span before normalization,
  preventing ordinary startup noise from mapping to a full-pressure spike.
- Non-finite stylus pressure reuses the previous effective pressure instead of
  creating a full-pressure spike.

## Compatibility

- Existing annotations and JSON are unchanged and continue through the same
  persisted point model.
- Neutral legacy ballpoint/highlighter paths remain continuous midpoint curves;
  neutral fountain/brush retain their polygon and index-envelope rendering.
- Optional stabilization, lazy brush, Chaikin and RDP helpers remain available.
- No new permissions, network access, assets, fonts or competitor code.

## Verification

- 1,309 JVM tests pass, covering the distance gate, opt-in defaults, cold pressure
  calibration, endpoint response preservation, physical arc envelopes and renderer
  selection. Instrumentation suite: 164 cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass. Debug APK is v2-signed; release is
  unsigned locally. Both are arm64-only and contain no `INTERNET` permission.
- Installable APK: `/sdcard/Download/MyPDFWriter-0.8.93-arm64.apk`  
  SHA-256: `5f1a3c9f07c31bb38e5bae844f2451d293ef2b614789f967b54504dfaf0ec1c8`
- Unsigned R8 release SHA-256:
  `00fc831d756f15bf48d093d4c01a60ec6d2e595153c24494735fbc0b7931494e`
- No adb device or pressure-sensitive stylus was available in this environment.
  Subjective nib feel, input-to-ink latency and palm behavior still require a real
  device pass.
