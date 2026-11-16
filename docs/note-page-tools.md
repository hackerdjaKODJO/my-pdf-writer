# Note page tools — rotation, photo pages and search

This document covers the original, local-only note page tools added on top of the
0.8.4 Template Studio and page manager. No third-party code, templates, fonts,
assets or branding are used, and the app stays offline.

## Page rotation

`NotePaper.rotatePage(page, clockwise)` turns one physical A4 sheet by 90° and
swaps portrait (595 x 842 pt) with landscape (842 x 595 pt). Unlike the Template
Studio re-cut (`NotePaper.reorient`), rotation deliberately turns the page
content with the sheet.

- Clockwise: normalized `(x, y) -> (1 - y, x)`.
- Counter-clockwise: normalized `(x, y) -> (y, 1 - x)`.
- Stroke width is converted by `oldWidth / newWidth`; font size by
  `oldHeight / newHeight`. Physical pen thickness and text size therefore stay
  the same on the new A4 sheet.
- Per-point `pressure`, annotation ids, `createdAt`, `durationMs`, opacity,
  pen style, stamps, text, layer ids, layers and page identity are preserved.
- `textRotation` advances by 90° clockwise or 270° counter-clockwise so text
  remains attached to its handwriting and turns with the sheet.
- Page ids, page order, source PDF links, notebook bookmarks and the page-manager
  selection are unchanged. Rotating clockwise and then counter-clockwise returns
  the original geometry and rotation.
- The custom-background key and display name are preserved. The image is not
  copied or physically rotated. `PaperBackground.drawCustomBackground` re-runs
  its cover crop for the new orientation: a portrait photo on a landscape page
  crops more from the top/bottom, and rotating back restores the original crop.
  This is intentionally a re-cover, never a stretch, and it matches how the same
  mixed-portrait/landscape background behaves in Template Studio and export.
- Rotation is one notebook change, so the editor's normal undo stack restores
  the whole page in one step.

Controls:

- Note overflow → `페이지` → `시계 방향 회전` / `반시계 방향 회전`. If pages
  are selected, the action applies to the selection; otherwise it applies to the
  current page.
- Page manager → `↻ 시계 회전` / `↺ 반시계 회전`. The same target rule applies:
  selected pages first, current page when the selection is empty.

There is no arbitrary-angle page rotation and no raster rotation of the custom
background asset. Extreme ink that already lies outside the paper is clipped by
the existing editor/export clipping, exactly like Template Studio's crop policy.

## Photo insertion

`CustomTemplateStore.importImage` remains the only photo import pipeline. SAF
`OpenDocument` accepts JPEG/PNG/WebP, validates magic bytes, applies EXIF
rotation, bounds the decoded size, and writes one app-private PNG below
`files/templates/` with a `tpl-<uuid>.png` key. There is no second storage format
and no storage permission. The same directory is already included in local ZIP
backup/restore.

Two explicit actions keep the distinction visible:

- `사진 페이지 추가` imports an image and inserts a new page directly after the
  target page. The new page inherits the target page's A4 orientation, template
  and tint, starts with one empty default layer and no source-PDF link, and uses
  the imported PNG as its custom background. The editor jumps to the new page;
  page order, annotations, existing page ids and notebook bookmarks after the
  insertion are remapped safely. This is one undo step.
- `현재 페이지 사진 배경` imports an image and applies it only as the target
  page's custom background. Orientation, template, tint, annotations, layers and
  source link stay untouched. Replacing an old background is one undo step; the
  old file is kept until the editor closes so undo can restore it.

The actions are available in the note overflow; `사진 페이지` is also in the page
manager. Imported assets are queued for close-time cleanup and deleted only when
no live notebook still references the key. A cancelled picker imports nothing;
an invalid or oversized image shows a Korean error and leaves the note unchanged.

## Note search

`NoteSearch.search(notebook, query)` is pure Kotlin and searches only local note
content:

- page memo;
- `TEXT` annotation text;
- `STAMP` label;
- `SIGNATURE` text, falling back to its stamp label.

Matches are case-insensitive. Handwriting strokes, PDF text, OCR, audio and
network content are not searched. A blank or whitespace-only query returns no
results.

Each hit carries the live page index, stable page id, a `NoteSearchType`, the
annotation id (null for a memo) and a short one-line snippet. Results are
deterministic: page order first, then the page memo, then annotations in stored
order. Snippets collapse whitespace, keep at most
`NoteSearch.SNIPPET_MAX_LENGTH` characters of content, and mark a cropped start or
end with an ellipsis.

`NoteSearchSheet` is opened from note overflow → `노트 검색`. It has a query
field, result count/page count, type labels, snippets and explicit empty states.
Tapping a result jumps to that page and closes the sheet. Search state is
session-only; no OCR, network, permission or new asset is involved.

## Verification

JVM tests:

- `NoteRotationTest` — clockwise/counter-clockwise mapping, round-trip, text
  rotation normalization, pressure, physical stroke/font size, custom fields,
  layers, page identity and empty pages.
- `NotePageRotationTest` — page-manager selection/current-page rotation, ids,
  order, source links, bookmarks, pressure, photo-page insertion/bookmarks and
  invalid asset keys.
- `NoteSearchTest` — blank queries, case-insensitive matching, searched types,
  deterministic ordering, snippets and empty notebooks.

Android-only verification: pick a photo for a new page and for the current page,
rotate a mixed portrait/landscape notebook, undo/redo each action, open the
Template Studio, and export a PDF. This needs SAF and `BitmapFactory`, so it is a
manual on-device check.
