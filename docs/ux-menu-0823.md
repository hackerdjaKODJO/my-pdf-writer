# Menu and navigation pass — 0.8.23

> Clean-room behavioral review. Flexcil evidence is limited to the existing
> static APK resource notes in `docs/flexcil-analysis.md` and
> `docs/flexcil-apk-feature-ledger.md`. No Flexcil code, assets, layouts, copy,
> icons, templates or branding were copied or reused.

## Review method

Three independent read-only agents reviewed:

1. Flexcil menu/navigation patterns from the existing static evidence.
2. The current My PDF Writer home, viewer, note editor and study-pane source.
3. The app-level navigation/back-stack and information architecture.

The reports agreed on four high-leverage gaps that were safe to ship in one
patch:

- create/import access disappears when the library is filtered or searched;
- Home tab, scope, search and folder/tag filters reset after opening a document;
- the PDF tab leads with two large promotional cards before recent documents;
- the PDF action menu is called a "hub", uses a grid icon, and spends its small
  primary set on actions already available in the top bar or dock.

## Implemented in 0.8.23

### Home

- Added a persistent **＋ 만들기** FAB with `새 노트`, `PDF 가져오기`, and `빈 PDF`.
  It remains available in every tab, scope, and search state.
- Home tab, scope, search query, grid/list mode, folder filter, and tag filter
  are now saveable. `MainActivity` wraps the Home subtree in a
  `SaveableStateHolder`, so opening a note/PDF and pressing Back restores the
  same place instead of resetting to `노트 / 전체 / page 1`.
- The title follows the selected tab (`내 노트`, `내 PDF`, `태그`).
- The PDF tab now shows recent documents first. The import banner only appears
  when the library has no PDFs, and the PDF tools entry is a compact icon in the
  section header instead of a second full-width card.
- The tag tab now actually filters by the search query; a selected tag chip
  clears on tap. Filtered empty states offer **필터 초기화**.

### PDF viewer

- Renamed the user-facing **PDF 허브** to **PDF 메뉴** and replaced the apps-grid
  icon with the standard menu icon. Help, onboarding, README, the usage guide,
  and the split-chrome test were updated together.
- The simple PDF menu now promotes unique actions:
  `검색 · 목차 · 북마크 · 페이지 관리 · 주석 목록 · 주석 숨기기 · PDF 내보내기`.
  `텍스트 선택` and `스터디 노트` remain available in the dock, top bar, and
  expanded menu, so no capability was removed.
- The position subtitle says `위치 n%` instead of `읽음 n%`, since the value is
  page position, not reading completion.
- The viewer dock names the active extra tool (`밑줄`, `취소선`, `스탬프`,
  `서명`, `가림`) and the chosen shape (`사각형`, `화살표`, `직선`, `타원`)
  instead of only highlighting the generic `더보기`/`도형` slot.

### Navigation

- `ToolsScreen` now handles the system Back button and returns to Home or the
  viewer, instead of finishing the Activity.

## Deferred

- PDF favorites/trash and sort need a `RecentDoc`/library schema change.
- A fullscreen reading mode and tap-to-toggle chrome.
- Multi-select in the Home library and annotation-panel bulk delete.
- A reversible note→PDF breadcrumb and full-screen study-pane expansion.
- Persisted notebook resume page (the session-level Home restore is shipped;
  page-level resume still needs a sidecar).

These remain candidates for later patch releases; they were not claimed as
delivered here.

## Verification

- JVM: 468 tests, 0 failures.
- Instrumentation APK compiles, including:
  - persistent Home FAB;
  - Home tab restoration after leaving/returning;
  - PDF split-chrome `PDF 메뉴` target;
  - existing pinch/zoom and study-pane coverage.
- No on-device execution was possible in this environment (no adb device).

## 0.8.24 QA follow-up

A post-implementation read-only review found no P0/P1 blockers. The following
P2 items were fixed in 0.8.24:

- An empty PDF library with a search query now says there are no PDFs and keeps
  the import action, instead of claiming the search found nothing.
- **필터 초기화** clears folder/tag/search while preserving the current
  즐겨찾기/휴지통 scope.
- The selected tag chip's clear icon is labeled for accessibility.
- Saved enum state restore is defensive against a future enum rename.
- The shape slot icon now matches the active shape label.
