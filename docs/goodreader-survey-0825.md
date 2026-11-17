# GoodReader iOS survey → offline Android features (0.8.25)

> Clean-room survey of **public** GoodReader documentation only
> (`goodreader.com` manual pages and App Store listing). No app binary was
> downloaded, installed, decompiled or instrumented. No GoodReader code,
> layouts, icons, colors, strings, templates or branding were reused. All
> implementation is original Material 3 with our own Korean labels.

## Method

Three independent read-only agents worked in parallel:

1. Surveyed official GoodReader feature pages and ranked features by phone
   value, offline compatibility and implementation effort.
2. Audited the current My PDF Writer source for existing/gap features.
3. Proposed a phone-first information architecture and explicitly flagged
   offline and small-screen constraints.

A fourth agent implemented the reader-side features and a fifth implemented
the Home/import features in separate worktrees; both branches were merged and
re-tested together.

## What GoodReader does that mattered

The official material describes a Finder-like file manager (folders, Find,
Recents, Starred, Trash, sort/view setup), a file-tabs panel for switching
recent documents, reading helpers (text search, reflow, read aloud, bookmarks,
outlines, annotation summary), page management (thumbnails, rotate, reorder,
extract, split/merge), per-document day/night and brightness, crop margins,
page lock for accidental-touch protection, and annotation undo/redo/copy-paste.

## What was already present

The source audit confirmed we already ship: continuous scrolling, text search,
reflow, read aloud, bookmarks/outlines, grouped annotation list with filters,
thumbnail page management with rotate/delete/reorder/extract/split/merge, PDF
password tools, flatten export, SAF import/share, study-note split pane with a
draggable divider, and local ZIP backup.

## Implemented in 0.8.25

- **문서별 야간 모드 + 화면 밝기**: persisted in `DocState` with backward-compatible
  JSON; brightness is clamped to 0.4–1.0 and applied as a non-interactive scrim
  over the page in single, continuous, spread and reflow readers.
- **페이지 잠금**: a session lock that gates ordinary page jumps (page pill,
  search hits, bookmarks, outlines, annotation rows) while keeping pinch zoom,
  annotation tools, text selection and study-note source jumps available. A
  visible badge offers one-tap unlock.
- **내부 PDF 링크**: link rectangles and internal page destinations are read from
  the source PDF with PDFBox, remapped through the page layout, and tapped in
  VIEW mode to jump to the target page. External URIs are only copied to the
  clipboard; no browser or network permission is added.
- **공유 PDF 가져오기**: `ACTION_SEND` for `application/pdf`, with a pure URI
  resolver that prefers `data`, then `EXTRA_STREAM`, then clip items. Carriers
  are consumed to prevent duplicate opens.
- **홈 정렬**: saveable sort menu with `최근 수정순 / 이름순 / 최근 열기순 /
  페이지 수순` for both notes and PDFs, backed by pure sortable helpers.
- The simple PDF menu's primary set now includes **화면 설정** instead of the
  old standalone night-mode row; all 28 actions remain reachable in expanded
  mode.

## Explicitly skipped

Cloud sync (Dropbox/OneDrive/Google Drive/WebDAV/SMB/FTP), accounts,
auto-sync, built-in web browser, Wi-Fi file transfer, mail-server access,
remote repositories, widgets, Bluetooth page turners, licensed content and DRM/
watermark circumvention. All conflict with the offline/no-account/rights-safe
product boundary or add little on a phone.

## Deferred to later patches

- Crop margins as a viewport-only transform (geometry risk across render, text
  hit-testing, annotations and export).
- Unified `위치` sheet combining bookmarks, outlines and annotations.
- Recent-document switcher / file tabs with explicit study-pane save.
- PDF favorites/trash/star colors (extends `RecentDoc` and backup schema).
- Annotation clipboard across pages, secure photocopy and local PDF hyperlinks
  authored inside the app.

## Verification

- JVM: **505 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` and R8
  `assembleRelease` pass.
- No `INTERNET` permission; only runtime `RECORD_AUDIO` for local audio notes.
- Instrumentation was compiled but not executed (no adb device here).
