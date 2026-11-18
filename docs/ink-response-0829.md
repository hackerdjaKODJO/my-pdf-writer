# Per-pen pressure response — 0.8.29

## Evidence boundary

Samsung Notes 4.4.48.15 was inspected read-only with `aapt2` and `unzip -Z1`;
REA Android analysis remains unavailable on this android/arm64 host. Resource
keys and public Samsung material show that pressure affects **both thickness and
color/opacity**, that variable vs fixed thickness/opacity are first-class modes,
and that pens have characteristic shape signatures (fountain nib, calligraphy
angle, pencil grain, watercolor wash, oil texture, airbrush density). No
Samsung code, brush engine, asset, font, color table or branded name was copied.

## Why 0.8.28 felt like "only thickness"

- Pressure was stored per point but rendered through a single function,
  `inkPressureWidthFactor`, used only by fountain and brush geometry.
- BALLPOINT (the default), HIGHLIGHT and PENCIL ignored pressure entirely.
- The width range (0.35–1), endpoint floors and triple smoothing flattened the
  visible change.

## Implemented

### Pure model (`data/InkPressure.kt`)

`InkResponse(width, flow, grain)` with per-style profiles:

| Style | Width | Flow (ink darkness) | Grain (texture/wash) |
| --- | --- | --- | --- |
| 볼펜 | constant | `0.45 + 0.55p` | unchanged |
| 만년필 | legacy pressure width | `0.65 + 0.35p` | unchanged |
| 붓 | legacy pressure width | `0.40 + 0.60p` | `0.50 + 0.50p` |
| 연필 | `0.55 + 0.45p` | `0.35 + 0.65p` | `0.40 + 0.60p` |
| 형광펜 | constant | `0.70 + 0.30p` | unchanged |

All factors are finite, bounded, monotonic and **exactly 1f at pressure 1f**, so
legacy strokes and finger input render unchanged. `inkResponseSeries` uses the
same 1:2:1 smoothing kernel as the old nib code; `meanInkFlow` drives single-Paint
paths.

### Painter (`pdf/AnnotationPainter.kt`)

- Ballpoint/highlight: pressure now fades the ink instead of changing tip width.
- Fountain: flow deepens the fill while the calligraphic ribbon geometry stays
  byte-identical to before.
- Brush: flow fades core/wash and grain thins the halo, giving a dry-brush look.
- Pencil: pressure changes width, pass darkness and deterministic grain amplitude.
- Replay derives response from the **full** stroke before truncation, so ink
  density does not change while a stroke is revealed.
- Preview and PDF export share the same painter.

### Capture (`ui/NoteCanvas.kt`)

`effectiveInkPressure` samples the **active pointer's current type**, so a stylus
that joins a finger-led stroke is pressure-sampled from the next move. Fingers,
mouse, eraser and disabled pressure stay neutral. The curve is still applied once
at capture.

## Skipped

S-Pen tilt/air actions, 4096-level calibration, cloud sync, AI brush generation,
licensed brushes, and any new permission.

## Verification

- JVM: **603 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- No `INTERNET` permission; arm64-only.
- Real stylus tuning still needs hardware; no device is attached here.
