# Realistic pen dynamics — 0.8.31

## Evidence boundary

Samsung Notes 4.4.48.15 was inspected read-only (`aapt2`, `unzip -Z1`, public
Samsung pages). REA Android analysis is unavailable on this host. No Samsung
code, engine, mask, brush asset, font, color table or wording was copied.

Key observations:
- Three core pen identities: `펜`(pen/ballpoint concept), `연필`, `만년필`.
- Each has a separate native engine and a distinct picker preview tip:
  uniform band (pen), tapered/wobbly wedge (fountain), serrated tooth (pencil).
- Texture masks exist for pencil/brush families but **not** for pen/fountain.
- Width and opacity are independent fixed/variable controls.
- Pressure is hardware-normalized; finger input stays neutral.

## Implemented (original Canvas logic)

`PenStrokeGeometry.kt` adds pure, deterministic helpers:
- `isNeutralPressureStroke`, `pressureVariation`
- `ballpointSkip`, `ballpointDotAlpha`
- `fountainTaper`
- `pencilSegmentAlpha`, `pencilSegmentWidth`

### 펜 / ballpoint
- Fixed tip width; no taper, no grain.
- Local pressure flow controls ink deposit per short segment.
- Below the contact threshold, a deterministic skip draws only a faint dot,
  producing the dry start / "needs contact" look.
- Neutral pressure keeps the old single smooth path exactly.

### 만년필 / fountain
- Existing calligraphic ribbon and nib-angle anisotropy preserved.
- A subtle symmetric start/end taper is applied only when pressure varies;
  neutral strokes keep the old untapered geometry.
- Wet, smooth fill; no texture mask, matching the evidence.

### 연필 / pencil
- Three graphite passes now vary by point: local width, darkness and
  deterministic grain amplitude.
- Light contact is pale, granular and broken-edged; firm contact darkens and
  widens. Neutral pressure reproduces the old 0.62 width, 0.52/0.34/0.34 pass
  alphas and 0.0004/0.0011 grain constants exactly.

### Compatibility
- Preview and PDF export share `AnnotationPainter`, so what you write is what
  exports.
- Replay derives all factors from the full stroke before truncation, so ink
  density does not change while a stroke is revealed.
- No data-format, permission or offline-boundary changes.

## Verification

- JVM: **656 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- Real stylus feel still requires hardware; no device is attached here.
