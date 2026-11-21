# GoodReader patterns applied offline — 0.8.36

> Clean-room behavior review of GoodReader's public feature descriptions.
> No app binary was downloaded, installed, decompiled or instrumented; no
> GoodReader code, layout, icon, color, string or branding was reused. Three
> independent agents reviewed the current app and implemented original Kotlin
> equivalents in separate worktrees, which were merged and re-tested together.

## What was reviewed

The earlier survey (`docs/goodreader-survey-0825.md`) and the current 0.8.35
source were re-checked against the patterns that matter most on a phone:

- unified **Locations** panel (bookmarks / outlines / annotations in one place)
- annotation summary export grouped by page
- **File Tabs** and recent-document switching
- auto-hide reading chrome / tap-centre menu toggle
- view-only **crop margins** with odd/even page policy

## Implemented in 0.8.36

### Locations panel
`학습 메뉴 → 읽기 → 위치` opens one bottom sheet with sticky `북마크 / 목차 / 주석`
tabs. The 주석 tab groups annotations by page, shows type/color chips and text
snippets, supports counted filters (`전체 / 필기 / 텍스트 / 도형 / 가림`), and
jumps to the tapped page. All earlier separate sheets remain reachable.

`주석 요약 내보내기` builds a deterministic Markdown report through pure
`AnnotationSummary` and writes it offline with `ACTION_CREATE_DOCUMENT`.

### Document tabs and recents
Opening PDFs keeps a session-only MRU tab list. At two or more documents a
compact tab strip appears with a `+N` overflow; tap switches documents, the close
button removes a tab, and the panel offers close-others, close-all, hide/show
tab bar, auto-hide mode and recent documents. The filename in the top bar also
opens the recents switcher. Study-pane saving still follows the existing docId
change effect.

Auto-hide mode is enabled by default on phones with `smallestScreenWidthDp < 600`;
tapping the centre of the page toggles the chrome in full-page VIEW mode. An
active tool, text selection, a sheet or split mode always keeps the chrome
visible. Back and a floating back/menu entry remain reachable in immersive mode.

### View-only crop margins
`화면 설정 → 여백 자르기` exposes left/top/right/bottom sliders and an odd/even
page option. Values are normalized 0..1, clamped so at least 10 % of each axis
remains visible, and saved per document in `display.crop`. The same transform is
applied to page bitmaps, ink, markup, text, links and memo cards in the standard,
continuous and spread readers, so hit-testing stays aligned. Export and the
source PDF are untouched; legacy JSON omits the new key entirely.

## Compatibility / verification

- No network, account, analytics or new permission.
- No copied source/assets/branding; no dependency or ABI change.
- Legacy document state loads with crop 0 and unchanged annotation JSON.
- JVM suite: **830 tests**, 0 failures.
- `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` pass.
- Instrumentation tests compile but are not run: no adb device is attached.

## Deferred GoodReader items

- annotation clipboard copy/paste across pages
- password-protect/export of a private copy
- live text-to-speech page reading beyond the existing TTS handoff
- NFC/external-storage browser integration
