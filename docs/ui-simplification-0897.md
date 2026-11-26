# Multi-agent UI simplification — 0.8.97

Reference analysis was static and behavior-only: Flexcil, StarNote, EasyNotes,
Samsung Notes, All PDF Reader, MyReader and PF Brief APKs were inspected with
AAPT/dexdump/resources. No APK was installed or executed, and no competitor code,
assets, fonts, templates, wording or branding was copied.

## Observed direction

- MyReader keeps three library verbs (Search, Sort/View, More) and combines view
  layout + ordering in one sheet.
- Flexcil and Samsung Notes use a small persistent tool core plus contextual or
  overflow actions; advanced tools are not permanent peers of primary actions.
- All PDF Reader hides most home actions until selection and uses a compact page
  pill rather than permanent progress chrome.
- StarNote/EasyNotes keep undo/redo in the editor chrome, not duplicated again in
  menu rows.
- Returning users should see one resume surface, not both a continuation card
  and a second welcome/start block.

## Implemented

- Home top bar now exposes **Search + Home settings**. The duplicate Help icon was
  removed; Help remains one row inside Home settings.
- Home library controls now use **Filter + one View menu**. The View menu groups
  layout (grid/list) and sorting, replacing two permanent buttons.
- A returning Home shows only the continuation card. The generic start/welcome
  block is limited to genuinely empty libraries.
- PDF `메뉴` no longer draws five tiny per-row Help icons. It keeps exactly one
  Help task row.
- The PDF bookmark row is state-aware: `현재 페이지 북마크` changes to
  `현재 페이지 북마크 해제` and keeps the selected state.
- Note editor top bar no longer duplicates Help; the Help row in 노트 메뉴 remains.
- Note menu no longer duplicates Undo/Redo already present in the top bar and
  compact dock.
- Successful PDF auto-save confirmation is transient (2.5 s); SAVING and FAILED
  states remain persistent, and FAILED keeps the retry action.

## Compatibility

No document schema, preferences key, annotation JSON, page model or permission
changed. All destructive and advanced actions remain reachable in their existing
menus/sheets.

## Verification

- 1,321 JVM tests pass; 165 instrumentation cases across 29 classes compile.
- Arm64 Debug and R8 Release builds pass; no `INTERNET`, arm64 only, debug APK
  v2-signed.
- Installable: `/sdcard/Download/MyPDFWriter-0.8.97-arm64.apk`  
  SHA-256: `8a908b7a53f2423944b44cf7d3c1d05c34d39fa928144998a975c2f8e2f63158`
- Unsigned release SHA-256:
  `328eaad58eb44721a58dbb7ac7fba89a8ce7fea5b259ce408fd6e00d9c290aa0`
- No adb device was available for visual/touch validation.
