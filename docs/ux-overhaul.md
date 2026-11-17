# UX overhaul — v0.8.1

Goal: make the common PDF and note-taking actions reachable with one hand on a
phone, without removing any existing capability. The redesign borrows interaction
patterns only — no code, assets, templates, fonts, or branding — from the
behavioral notes in `starnote-reverse-engineering.md`, `flexcil-analysis.md`, and
`all-pdf-reader-analysis.md`.

## What was hard to use

- The PDF top bar packed search, page organize, export and a 20-item overflow menu
  into one row, while the bottom area showed every color and slider at once.
- Page navigation and zoom lived in a dense bottom bar.
- The notebook editor showed tools, palette, sliders and page thumbnails together.
- The home screen led with a large hero instead of the user's last document or
  the four things users do most often.

## Viewer

- Top bar: back, document title + page/progress, current-page bookmark star, and
  one **PDF 허브** button.
- **PDF 허브** groups every secondary action into 읽기 / 문서 / 내보내기 with icons
  and count/state labels. The sheet closes before invoking the action.
- Bottom tool dock: 보기, 펜, 형광펜, 지우개, 텍스트, 도형, 올가미, 더보기 +
  undo/redo. A color swatch and **스타일** pill appear only for tools that use them.
- **스타일** bottom sheet: palette, thickness/font size, opacity, shape/stamp/
  signature/redact choices.
- Floating page pill `‹ 3 / 12 ›` opens the existing page sheet; pinch zoom stays
  gesture-driven. Fit reset is in the hub.
- Text selection keeps its contextual action bar (copy/translate/excerpt/
  highlight/underline/strikeout) and drag-to-study-note target.
- Lasso note: the PDF canvas has no annotation-object lasso model, so the dock's
  올가미 entry reuses the existing text-selection mode. This is a deliberate
  behavior mapping, not a new selection model.

## Home

- Compact top bar (title/subtitle, search, one overflow).
- **이어서** cards for the last opened PDF and the most recently edited
  non-trashed notebook.
- Quick actions: PDF 열기, 새 노트, PDF 도구, 백업/복원.
- Notes/PDF/Tags tabs, folders, tags, favorites, trash, grid/list, and all
  create/edit/delete flows are unchanged. Start content hides while filtering or
  searching.

## Notebook editor

- Top bar: back, tap-to-rename title, undo/redo, favorite, one overflow.
- Bottom dock: pen, highlighter, eraser, lasso (free/rectangle tray), text, shape
  (rectangle/arrow/line/circle tray), undo/redo, color + **스타일**, page pill.
- **스타일** sheet: palette, pen type, thickness, opacity, text size, and a button
  to the existing pen box.
- Overflow sheet: templates, layers, outline/bookmarks, audio notes, audio replay,
  source PDF, page add/duplicate/move/delete, export, finger-writing and zoom lock.
- Wide screens keep the page rail; phones use the page pill and thumbnail sheet.

## Focused study pane (0.8.6)

- The PDF study pane opens at **85%** of the PDF content area (clamped 35–85%,
  presets 50/70/85%). 95% is intentionally not offered: it leaves only a 5% PDF
  strip, below the 15% minimum needed to keep the source page readable.
- The pane header is a single compact floating overlay: one 44 dp row with a
  resize grip, notebook title + source, a tiny size popup, capture, link and close
  actions. The redundant "PDF 위 스터디 노트" heading and the permanent full-width
  capture strip are gone.
- The size popup exposes the 50/70/85% presets. Dragging the grip (or the
  title/source block) resizes continuously; pulling below the minimum height plus
  the dismissal overshoot closes the pane.
- PDF excerpts can be dropped directly on the floating header. The capture action
  reveals one compact drop row when the user wants an explicit target; the PDF
  selection bar's `패널에 추가` flow is unchanged.
- `NoteEditorScreen(compact = true)` hides the editor's own top app bar, replaces
  the large always-on hint pill with a one-line contextual hint for non-pen tools,
  and floats a translucent rounded dock over the lower edge of the canvas. The
  dock has one 34 dp primary tool row and one 32 dp action row; colour/style,
  undo/redo, favourite, overflow and the page pill sit in the slim row. Templates,
  layers, outline, search, audio, source PDF, page tools, export, finger writing,
  pressure curves and trace stay in the existing sheets.
- The compact canvas is not inset by a bottom bar: it fills the pane behind the
  floating dock and header, so the paper occupies more of the pane. Full-screen
  note editing keeps its original top bar, dock and hint behaviour.

## Verification (0.8.1)

- `:app:assembleDebug`, `:app:testDebugUnitTest` (168 tests), and
  `:app:assembleRelease` (R8, unsigned locally) pass.
- APK permissions: no `INTERNET`; `RECORD_AUDIO` remains runtime-only for audio
  notes.
- 16 KB zip data alignment and ELF `LOAD` alignment checked for both native
  libraries and the release APK.
- No on-device UI test was possible in this environment (no adb device), and the
  README screenshots predate this redesign.
