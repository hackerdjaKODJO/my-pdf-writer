# Real-pen pressure feel — 0.8.69

Behaviour references only (GoodNotes / Samsung Notes / Flexcil public product
behaviour and the perfect-freehand `thinning` concept already reviewed in
`ink-paper-feel-opensource.md`). No third-party code, brush data, textures or
assets were copied; everything below is original Kotlin in the existing
pipeline.

## What was missing

Through 0.8.68 the app already had pressure curves, adaptive calibration,
stationary-nib samples, nib-angle fountain ribbons, graphite grain and end
taper. Four gaps remained between that and the feel of a real pencil / brush /
ballpoint in commercial note apps:

1. **Digitizer noise reached the ink directly.** Pressure arrives 120-240×/s
   with ±0.02..0.05 sensor jitter; only a 3-sample spatial kernel smoothed it,
   so width/darkness shimmered along a stroke.
2. **Speed dynamics were incomplete.** Only the ballpoint outline and pencil
   deposit responded to pace. A real brush flicks thin and dry; a fountain nib
   starves on a fast pull and wets up on a slow one.
3. **Ballpoint width swelled up to ~3× with pressure.** A ballpoint's ball is
   fixed-size: pressure reads as ink flow, not width.
4. **Dwelling did nothing visible for fountain/brush.** Leaning on a stationary
   nib should pool ink into a darker blob.

## Implemented

### 1. Temporal pressure low-pass — `data/PressureSmoother`

An original asymmetric EMA applied at capture, AFTER calibration and the curve
preset, to the effective value that gets stored:

- attack `0.55` (press swells promptly), release `0.30` (lift eases out like a
  nib leaving paper);
- first sample of every stroke passes through exactly (reset per stroke);
- a full-press sample snaps to exactly `1f`, so neutral/finger/legacy identity
  is preserved end to end; non-finite input never teaches the filter.

Wired into both `NoteCanvas` and `PdfCanvas` through the shared
`effectiveInkPressure` helper (new optional `smoother` parameter; null keeps
the old behaviour byte-for-byte). Session-scoped, never persisted, no new
setting — smoothing is transparent, like in commercial apps.

### 2. Speed-aware width — `pdf/InkDynamics`

`velocityWidthFactor(speed, reference, influence, variation)` multiplies nib
width by pace: exactly `1f` at the stroke's mean pace and for neutral ink,
`1 ± influence` at the extremes (bounded, finite, deterministic).

| Style | Influence | Feel |
| --- | --- | --- |
| BRUSH | 0.35 | fast flick = thin dry core inside the unchanged halo; slow pull = wet swell |
| FOUNTAIN | 0.20 | nib starves on fast pulls, wets on slow ones |
| PENCIL | 0.12 | subtle thinning; darkness already uses `depositGain` |

Speeds are central-difference page-px per sample (`strokeSpeeds`), derived once
from the FULL stroke in `InkContext` and sliced by replay — the same contract
as `outlineWidths`, `fountainSmoothed` and `brushEnvelope`.

### 3. Ballpoint identity

- `outlineWidthSeries` gains `minWidthFactor`; the ballpoint passes
  `BALLPOINT_MIN_WIDTH_FACTOR = 0.88`, so its outline stays within ~±6% of the
  neutral width instead of collapsing to 35%. Every other style keeps the
  original band; the default parameter keeps all existing callers unchanged.
- `ballpointSpeedFlow` reweights stroke darkness by pace (slow samples count
  more): a careful slow stroke reads wetter than a quick scribble. Neutral
  variation falls back to the plain mean exactly.
- A slightly heavier contact dot (`BALLPOINT_CONTACT_GAIN = 1.12`) marks where
  the ball first touched paper — varied ink only.

### 4. Dwell pooling — `InkDynamics.dwellPools`

Runs of ≥3 consecutive samples moving <1.5 page-px pool ink: the fountain and
brush renderers draw a soft darker blob at the run's anchor, radius
`localHalfWidth × (1 + 0.35 × strength)`, alpha `0.30 × strength × flow`.
Strength grows with dwell length (capped), at most 8 pools per stroke, neutral
ink never pools. Replay keeps a pool only once its anchor sample is revealed
(`poolsForReveal`); its strength never changes mid-reveal.

## Compatibility contract (unchanged)

- Neutral / finger / legacy strokes render byte-for-byte as before: every new
  effect is gated on `pressureVariation > PRESSURE_VARIATION_EPSILON` or on the
  varied-only renderer paths, and the smoother snaps full press to `1f`.
- Replay derives everything from the full stroke (`InkContext.speeds`,
  `speedReference`, `pools`, `depositFlow`) and slices; revealing a prefix
  never re-derives dynamics. `drawPencil` now uses the full-stroke variation
  from the context instead of re-measuring the revealed prefix.
- Preview, committed ink and flattened PDF export share `AnnotationPainter`.
- No data-format change (`prs` stays optional), no permissions, no network.

## Verification

- New JVM tests: `PressureSmootherTest` (10), `InkDynamicsTest` (13),
  `InkContextDynamicsTest` (6) — neutral identity, bounds, determinism,
  replay slicing, attack/release asymmetry, jitter reduction, dwell detection.
- `./.termux-build.sh :app:testDebugUnitTest`, `:app:assembleDebug
  -Parm64Only=true`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`.
- Real stylus feel still requires hardware; no pressure-sensitive device is
  attached here, so no hardware claim is made.
