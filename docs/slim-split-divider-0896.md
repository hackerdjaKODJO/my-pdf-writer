# Slim PDF/note split divider — 0.8.96

The split-view middle row previously reserved 48dp and carried page navigation,
a percentage pill with a size popup and a PDF-tools visibility chip. On a phone
this left too little usable height for both the PDF strip and the note pane.

## Changed

- The PDF/note boundary is now a **24dp drag row with one slim grip**.
- Removed from the middle row:
  - previous/next/page-list navigation
  - percentage label and size-preset popup trigger
  - PDF-tools show/hide chip
- Dragging still adjusts the pane fraction across the existing clamped range.
- Double-tap still switches between the documented 50%/85% endpoints; TalkBack
  exposes “기본 노트 높이로” as the accessibility click action.
- PDF tools remain available in the existing floating PDF chrome.
- Page access remains available from the PDF menu/page list.
- The note header's own 50/70/85% preset popup is unchanged.

## Verification

- Divider is 24dp; preset rows and all other controls keep their existing sizes.
- JVM layout/TalkBack tests and split-chrome instrumentation tests updated.
- No schema, permission, network, backend or stored-document change.

## Verification

- 1,318 JVM tests pass; 164 instrumentation cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass; no `INTERNET`, arm64 only, debug APK
  v2-signed.
- Installable: `/sdcard/Download/MyPDFWriter-0.8.96-arm64.apk`  
  SHA-256: `0be0b1374c53e0af8df3b79f768a89b0b56fa153e49d265b88bf3c13b9bb0a10`
- Unsigned release SHA-256:
  `20dcd975940fb881cdee4cac745a15f70b9ab62567e0d4a386e67c76038d3eae`
