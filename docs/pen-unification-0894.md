# Unified PDF/note pen settings and style-aware artwork — 0.8.94

## What was wrong

`pen_box.xml` was already shared, but the PDF viewer and note editor each loaded
their own copy and later wrote a complete snapshot back. In split mode the last
surface to change one field could overwrite another surface's newer settings.
Favorites/presets and pressure fields had the same stale-copy problem.

Two note-only defects also broke parity:

- Applying a favorite restored its curve but not `pressureInk`.
- Note highlighter preview used `highlightWidth`, while commit used pen width.

## Implemented

- One process-wide `PenSettingsRepository` owns ink, saved pens and ordered
  favorites as a `StateFlow<PenSettingsSnapshot>`.
- Every change is an atomic latest-state transform. PDF and note collect the same
  state, so either surface updates the other immediately.
- Existing keys remain unchanged: `pen_box.xml`, `ink_v1`, `presets_v1`,
  `favorites_v1`. Missing/empty favorites semantics, nullable preset pressure
  fields and annotation JSON are preserved.
- Backup restore holds a write barrier and refreshes the repository after the
  preference file is replaced.
- `InkSettings.resolve(tool)` is the single pen/highlighter material projection.
  Note highlighter preview and commit now use the same selected width/opacity.
- Highlighter presets no longer overwrite the active pen style.
- Favorite thumbnails key their sample pressure on the preset's pressure fields.
- Existing original instrument artwork is extracted into `PenInstrumentArtwork`.
  Pen Studio now shows a large “선택한 펜” portrait, and PDF/note dock pen slots
  render the selected ballpoint, fountain, brush or pencil.
- Highlighter controls intentionally omit the pen portrait because highlighter
  ink is a different material.

## Tests

- Repository: field-update preservation, atomic preset application, external
  reload/clear, failed-write rollback, concurrent updates, restore barrier,
  missing-vs-empty favorites and registry pruning.
- Ink: pen/highlighter resolution, highlighter preset style preservation, and
  note highlighter preview→commit width parity.
- PDF projection: shared ink changes cannot alter document/tool/zoom state.
- Instrumentation: selected portrait changes with pen style; PDF dock exposes the
  selected style as state description and updates without reopening.

No new permission, network, cloud, account or third-party asset is involved.

## Verification

- 1,320 JVM tests pass; 166 instrumentation cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass; both are arm64-only with no `INTERNET`.
- Debug APK is v2-signed. Installable:
  `/sdcard/Download/MyPDFWriter-0.8.94-arm64.apk`  
  SHA-256: `553462e551dd3af9ff2a3f99578097606e911439b54d3a91a349b8d98ea1a934`
- Unsigned R8 release SHA-256:
  `47c3a0f1d2e8bc6bbd64dde9f646c552e6b9efdd115d4a6592940e53b0b45ad5`
- No adb device was available; visual pen-artwork and split-pane live-sync
  behavior still need a real-device check.
