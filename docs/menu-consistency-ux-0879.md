# Menu consistency and UX simplification — 0.8.79

Six agents audited global information architecture, PDF viewer journeys, note
editor workflows, Home/Tools/accessibility, reference-app patterns, and
navigation/state safety. Reference material included MyReader screenshots,
Flexcil/StarNote/All PDF Reader analyses, Ridi/Librera reviews, and PDFCraft IA.
No third-party code, assets, copy, or branding were copied.

## Delivered

### PDF menu

- Renamed `학습 메뉴` to `PDF 메뉴` everywhere, including Help and usage guide.
- First-level groups are now `보기 · 주석 · 노트 · 파일·설정`.
- Frequent actions stay visible; audio, fit-screen, post-its, document style,
  page-text copy, auto-hide, page tools, security and sharing move to one
  `추가 도구` sub-sheet.
- Added `PdfHubActionKind`: only navigation rows show a chevron. Commands and
  toggles no longer imply another screen.
- Export is direct on the first level.

### Split viewer

- The default 85% study pane keeps `PDF 메뉴` available. Previously it hid the
  only route to export, read modes and display settings.
- Bookmark remains a full-density shortcut; the menu is always present.
- Split Back is labelled `뒤로` and uses the same reducer as system Back.

### Back behavior

- System Back, top-bar Back, immersive Back and split Back now call one
  `handleViewerBack` reducer: close study pane → clear selection → close PDF.

### Note menu

- Regrouped into `노트 · 추가 · 파일 · 설정`.
- Ink settings moved from the ambiguous `복습` group to `설정`.
- Source PDF and PDF export live under `파일`.
- `복습 카드` renamed to `카드`.

### Home and Tools

- Home continuation cards appear only in the Notes section.
- Empty PDF section now has one primary import action and no duplicate banner
  or irrelevant sort/view header.
- Home PDF overflow is `최근 PDF 메뉴`, not `학습 메뉴`.
- Protected PDFs expose `암호 해제` instead of a dead-end message.
- Password fields use masking and password keyboards.

### Organize

- Screen title is `페이지 정리`; document name is secondary.
- Bulk delete asks for count-aware confirmation.
- Rotation/reorder labels are explicit: `왼쪽 회전`, `오른쪽 회전`,
  `한 칸 앞으로`, `한 칸 뒤로`.
- Primary action labels no longer force 10sp text.

## Compatibility and scope

No data format, permission, network, annotation, or export behavior changed.
Existing capabilities remain reachable; this patch changes hierarchy and
labels, not feature availability. Larger navigation-state refactors (reader
position return, full sheet back stack, pending SAF durability) remain separate
follow-ups.

## Tests

- Menu inventory/order/migration tests updated.
- Split toolbar unit + E2E now require PDF menu in compact mode.
- Canonical `UiCopy` test added.
- Help and usage-guide content tests updated.
- Organize bulk-delete confirmation/cancel E2E added.
- Full JVM suite and Android-test compilation pass.
