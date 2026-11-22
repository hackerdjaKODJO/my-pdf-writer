# Ink & paper feel — open-source review (2026-11-22)

Behaviour references only. No third-party code, brushes, textures, fonts or
assets were copied (AGENTS.md). Both sources are permissive but the app stays
fully offline and dependency-free.

## Sources

- [perfect-freehand](https://github.com/steveruizok/perfect-freehand) (MIT):
  input points → spline points → outline polygon; options `size`, `thinning`
  (pressure→width), `smoothing`, `streamline`, `simulatePressure` (velocity
  fallback), `easing`, and `start`/`end` taper with caps; quadratic-smoothed
  rendering.
- [libmypaint / brushlib](https://github.com/mypaint/libmypaint) (ISC):
  dab-based brush engine with pressure/velocity mappings, hardness and texture.

## What we verified in our own pipeline

- Replay-safe `InkContext` is derived from the FULL stroke and sliced on reveal;
  neutral/finger strokes are byte-identical (`prs` omitted, all multipliers 1f).
- We already implement the same concepts in original Kotlin: 1:2:1 + lazy-brush
  + Chaikin smoothing, pressure curves, per-style width/flow/grain, fountain
  taper, brush envelope, ballpoint skipping, graphite grain by arc length.

## Adopted in v0.8.52

1. **Pressure calibration continuity.** A true full-press stylus sample now
   teaches the learned upper bound (it still stores neutral 1f), and the first
   three samples of a stroke bootstrap the range directly instead of ramping
   with EWMA. Note/PDF canvases use the same per-stroke bootstrap.
2. **No ink from a lifted nib.** `simulatedPressure` no longer boosts a
   near-zero contact back to 0.65 because the pen moved slowly.
3. **Deterministic paper fibre.** A fixed 420-mark pseudo-random tooth layer is
   drawn inside the shared `PaperBackground`, so editor, thumbnails, template
   studio and exported PDF show the same sheet. No image asset.
4. **Layered sheet shadow.** Three fade-out rects replace the flat one, screen
   only (export intentionally has no shadow).
5. **Granular graphite.** Light-grain pencil strokes vary deposit density per
   sample, so graphite reads as particles rather than a flat grey band.
6. **One minimum ink width floor** (`0.05px`) for every outline builder;
   previously ballpoint outline and brush/fountain ribbon used different floors.

## Deliberately NOT adopted

- Copying perfect-freehand/libmypaint source or brush data (policy + replay parity).
- Simulating pressure for finger/legacy ink at capture time — would destroy the
  byte-identical neutral identity asserted across the ink test suite.
- Full polygon-union flattening of self-crossings (needs a clipping engine and
  would change every historic stroke).
- Replacing our spline/outline pipeline wholesale; our `InkContext` slicing
  contract keeps replay and export stable.
- New permissions, haptics/VIBRATE, network or third-party visual assets.
