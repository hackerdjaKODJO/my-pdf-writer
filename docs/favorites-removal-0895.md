# Favorite-pen strip removal — 0.8.95

The favorite-pen strip is no longer shown below the PDF annotation tools, in the
PDF/note style sheets, or as a separate quick-access registry strip.

- Saved pen presets remain available in the pen box.
- `favorites_v1` remains in `pen_box.xml` only for backup/restore compatibility;
  no active UI reads or exposes it as a favorite strip.
- Shared active pen settings, preset application, dynamic pen artwork and all
  existing JSON/annotation formats are unchanged.
- Instrumentation assertions now verify that “즐겨찾는 펜” is absent from the PDF
  annotation/style surfaces.

## Verification

- 1,319 JVM tests pass; 166 instrumentation cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass; no `INTERNET`, arm64 only, debug APK
  v2-signed.
- Installable: `/sdcard/Download/MyPDFWriter-0.8.95-arm64.apk`  
  SHA-256: `097c2d2e7ebc567a4e366485228b502e1908db63ac9f0245faa688a9934277ce`
- Unsigned release SHA-256:
  `99e595c78184073a0ca42f8cd76cc800b5155ab19ab0a6d99222d4b563de6744`
