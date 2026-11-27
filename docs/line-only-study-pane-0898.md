# Line-only PDF/note study pane — 0.8.98

The study pane previously overlaid a floating title/source row with resize
presets, link, tool visibility and close controls above the note canvas. That row
also forced a non-zero compact top inset.

## Changed

- Removed the floating study-pane header and its top inset.
- The note canvas now starts immediately below the PDF viewer's 24dp resize line.
- Height adjustment remains on the slim divider: drag continuously or double-tap
  to switch between 50% and 85%.
- System Back still closes the pane through the existing `onBack` path.
- PDF excerpt drop now targets the whole pane instead of only the old header.
- `원본 PDF 링크` remains available as a Note menu row and opens the same local
  To/From index.
- Compact note tools remain visible in the existing floating dock.

No document data, link identity, JSON, preference key or permission changed.

## Verification

- 1,318 JVM tests pass; 165 instrumentation cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass; no `INTERNET`, arm64 only, debug APK
  v2-signed.
- Installable: `/sdcard/Download/MyPDFWriter-0.8.98-arm64.apk`  
  SHA-256: `a64665294f7cd28ba4621403f630ab6dc3ece52711d2b48edb6fdac57b77121b`
- Unsigned release SHA-256:
  `053bf6ff89498135348dff7be6d3e3e024643000fdfbb59824708a17300874b0`
- No adb device was available for visual/touch validation.
