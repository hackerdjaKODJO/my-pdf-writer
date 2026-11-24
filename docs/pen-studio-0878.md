# Pen Studio — pressure and writing-tool settings (0.8.78)

Five agents independently audited pressure capture/dynamics, settings UX,
rendering/export parity, persistence/tests, and commercial product patterns.
The implementation below addresses the highest-impact coherent slice without
adding tilt, cloud features, new brush engines, or third-party assets.

## Delivered

### Shared Pen Studio

`PenStudio.kt` is now used by both `NoteStyleSheet` and `ViewerStyleSheet` for
PEN/HIGHLIGHT tools:

- live `AnnotationPainter` pressure-ramp preview;
- white PDF, dark PDF, and note-paper backgrounds;
- shared palette, pen type, canonical width range, and opacity;
- stylus pressure on/off;
- sensitivity presets `민감 / 보통 / 둔감` with a real response graph;
- plain-Korean explanations and next-stroke-only scope.

### Complete favorite-pen behavior

`PenPreset` gains optional `pressureCurve` and `pressureInk` fields. Missing
fields inherit current settings, so old presets and backups keep their current
behavior. Newly saved pens restore their full pressure behavior in PDF and
notes. Highlighter presets use their existing width/opacity fields for the
independent highlighter settings.

### Independent highlighter settings

`InkSettings.highlightOpacity` is additive and defaults to 1. PDF and note
preview, commit, replay, and export now use the selected highlighter width and
opacity. PDF live preview no longer hardcodes 0.02 width / 1.0 opacity.

### Correctness fixes

- PDF pressure curve/enabled state is owned by `ViewerUiState` and persisted by
  `PdfViewModel`; the viewer no longer reads a stale one-time preference value.
- PDF pressure input restarts on document/page/pressure-setting changes.
- Note preview receives the actual stabilization strength instead of always
  using the default.
- Recognized shapes are no longer overwritten by stabilization of raw points.
- PDF and note pen sliders share the stored 0.001..0.030 range.
- User-facing opacity copy is `불투명도`.

## Compatibility

- Existing `ink_v1` JSON remains valid; missing `ho` defaults to 1.
- Existing `presets_v1` JSON remains valid; missing `pc`/`pi` inherits live
  pressure settings.
- Existing annotations are not migrated or reinterpreted.
- Backup format and `pen_box.xml` contract are unchanged.

## Tests

- JVM: highlighter opacity migration/clamping, full preset behavior restore,
  pressure fields in preset JSON, active-style comparison.
- E2E: `PenStudioE2ETest` verifies shared preview/controls, response selection,
  highlighter opacity, and hidden pen-type controls.
- Full `testDebugUnitTest` and `assembleDebugAndroidTest` pass.

## Deferred

Physical tilt, per-device persisted calibration UI, custom curve control points,
retroactive restyling, brush-marketplace features, and real-device stylus
qualification remain separate hardware/feature phases.
