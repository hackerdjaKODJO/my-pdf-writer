# Whole-app menu and UX redesign — 0.8.26

> Clean-room behavioral survey of public documentation for GoodReader,
> GoodNotes, Notability, Flexcil, PDF Expert and Xodo. No third-party code,
> assets, layouts, icons, colors, copy, templates or branding were reused.
> My PDF Writer remains offline-only: no INTERNET, accounts, cloud, ads or
> analytics.

## Why this pass happened

The 0.8.25 app had four different menu grammars:

- Home: three tabs, two persistent filter-chip rows, FAB menu and card menus.
- Reader: an 8-slot dock, a second undo/style row, a 29-action PDF menu with a
  hidden simple/expanded switch, and a 10-action selection bar.
- Note editor: duplicated undo/redo/style/favorite controls and a ~32-row
  `노트 메뉴` junk drawer.
- Split mode: a separate floating toolbar and another tool sheet.

The same action often had two to four entry points. PDF search, page navigation,
display settings and note export required three to five taps.

## Survey conclusions used

Three read-only agents surveyed public vendor manuals and audited the app. The
changes below follow five principles:

1. One canonical location for each action.
2. One primary menu per screen; rare actions behind a clearly labeled advanced
   section.
3. Bottom thumb-reach tools; top bar for identity and one-tap essentials.
4. Stable labels; do not use one word for two different features.
5. Destructive actions separated from daily actions and confirmed.

## What changed

### Home / library

- Persistent scope and folder/tag chip rows were replaced by one compact row:
  `필터` + active-count badge, `정렬`, `보기`.
- `필터` opens one sheet with `보기` (전체/즐겨찾기/휴지통), `폴더`, `태그`,
  plus `초기화`/`적용`.
- Active filters appear as one removable chip row only when needed.
- The Home overflow was replaced by a `설정` sheet available from every tab:
  backup/restore, trash, tags, PDF tools, help, offline info, and debug trace.
- PDF cards gained a `⋮`/long-press menu with `최근 목록에서 제거`; source PDFs
  are not deleted.
- PDF tools are grouped by task: import/convert, pages, OCR/search,
  security/info, forms, share/print.

### PDF reader

- Top bar: back, title, one-tap `검색`, `노트`, bookmark, `PDF 메뉴`.
- Dock reduced from eight slots to six:
  `보기 · 펜 · 형광펜 · 지우개 · 텍스트 선택 · 주석`.
  `주석` opens one `주석 도구` sheet for text boxes, shapes, underline,
  strikeout, stamp, signature and redaction.
- PDF menu no longer has a hidden simple/expanded mode. It is one grouped sheet:
  `읽기`, `페이지`, `보기`, `내보내기`, and a collapsed `고급` section.
  Reading modes and audio have small sub-sheets.
- Text selection is markup-first:
  `형광펜 · 밑줄 · 취소선 · 복사 · 발췌 · 더보기`.
  Translation, dictionary, add-to-pane, edit, share and clear live in `더보기`.
- Split mode reuses the same annotation sheet and selection ordering.

### Note editor

- Top bar: back, title, favorite, `PDF로 내보내기`, one `노트 메뉴`.
  Duplicate undo/redo moved out of the top bar.
- Dock: one style action (the separate color dot was removed), undo/redo and
  page pill remain.
- `노트 메뉴` reduced to ~10 top rows:
  name, page manager, templates, layers, search, study cards, audio, writing
  settings, export, source PDF, help.
- Page operations live in the page manager, not the main menu.
- Writing options are grouped in `필기 설정`; templates, learning cards and
  audio open focused sub-sheets.
- Compact pane removes duplicate favorite/style controls. Its header remains the
  single excerpt drop target.

## Deferred

- Global three-destination shell (`홈 · 문서 · 도구`) and removal of the Tags
  tab; this needs a larger navigation/state migration.
- PDF/note favorites/trash/star colors parity and 30-day trash.
- A unified `위치` sheet with bookmarks/outlines/annotations tabs.
- Recent-document switcher and true file tabs.
- All iPad-first or cloud-only patterns: sidebars, multi-window, cloud sync,
  browser, mail/Wi-Fi transfer, marketplace, AI.

## Verification

- JVM: **528 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` and R8
  `assembleRelease` pass.
- No `INTERNET` permission; arm64-only APK.
- Instrumentation was compiled but not run (no adb device here).

## QA follow-up before release

A read-only QA pass found one P1 split-mode regression and several small phone
issues; all were fixed before the 0.8.26 build:

- The shared `주석 도구` sheet now always starts with `보기 · 펜 · 형광펜 · 지우개`,
  so split mode can arm the basic PDF tools without closing the study pane.
- The same sheet is vertically scrollable on short screens and large font sizes.
- Home quick actions switch to a 2×2 grid below 400 dp, preventing
  `PDF 가져오기` truncation at 320–360 dp.
- Home filter reset now clears the active search query as well as scope, folder
  and tag, and the filter sheet shows the current search with a clear action.
