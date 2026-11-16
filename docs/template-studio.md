# Template Studio 1.0

Template Studio is an original, local-only notebook paper editor. It extends the
existing 11 `PaperTemplate` patterns; it does not import Flexcil or other
third-party templates, covers, fonts, colours or assets.

## Data

`NotePage` gains four optional JSON fields:

| Field | Values | Legacy default |
| --- | --- | --- |
| `orientation` | `PORTRAIT`, `LANDSCAPE` | `PORTRAIT` |
| `tint` | `WARM`, `WHITE`, `PALE_YELLOW`, `LIGHT_GRAY` | `WARM` |
| `customBackground` | portable asset file name, e.g. `tpl-8e5a.png` | `null` |
| `customBackgroundName` | user-visible label, max 80 chars | `""` |

All fields are read with safe fallbacks, so notebooks written before this
feature load unchanged. `Notebook.toJson()` writes `customBackground` /
`customBackgroundName` only when an asset is present. A page never stores an
absolute path or a `content://` URI; the key resolves below the app-private
`files/templates/` directory. New and duplicated pages copy the current page's
paper settings, including a custom background. Study-note pages keep the legacy
portrait/warm defaults.

## Orientation policy — A4 re-cut, never stretch

Every notebook page is a real A4 sheet:

- portrait: 595 x 842 pt
- landscape: 842 x 595 pt
- editor: 2.5x PDF points; export: 2x PDF points

Changing orientation **re-cuts the sheet; it never distorts handwriting**:

1. Annotation points are converted from normalized coordinates to physical
   points on the old sheet, then to normalized coordinates on the new sheet.
2. The ink group is translated only as far as needed to fit inside the new
   sheet. If it already fits, it keeps its physical position.
3. Stroke width is converted relative to page width and text size relative to
   page height, so a pen line keeps the same physical thickness.
4. Ink is never rotated or non-uniformly scaled. When the physical ink is
   larger than the new sheet, the overflow is centred and clipped in the editor
   and raster PDF export. The data is not clamped; undo restores the previous
   page state.
5. The studio previews the actual result and warns when one or more target
   pages would crop existing ink before the user applies the change.

This behaviour matches cutting a new sheet of paper to the other orientation:
the writing keeps its size, fits where it can, and is cropped where it cannot.
It is intentionally not a "stretch to fill" or "rotate text sideways" policy.

## Paper tint

`PaperBackground` draws the tint fill together with contrast-adjusted template
lines. The same painter is used by the editor canvas, page rail, page manager,
studio preview and raster PDF export. Dark theme can dim the tint palette, but
the notebook surface stays paper-like rather than becoming a black app panel.

## Custom image / PDF backgrounds

`커스텀 배경` in the studio accepts JPEG, PNG, WebP and PDF page 1 through SAF
(`OpenDocument`), with no storage permission. The import pipeline is local:

- The selected stream is copied to a private temporary file with a byte limit
  (20 MB image, 60 MB PDF). File type is detected from the magic bytes, not only
  from the provider MIME value.
- Images are decoded with bounds/dimension checks, EXIF rotation is applied,
  and the longest edge is normalised to 2480 px (enough for the 2.5x A4 editor).
- PDFs are rendered with Android `PdfRenderer` on a white page at the same
  maximum edge. Only page 1 is used.
- The result is written as PNG below `files/templates/` with a fresh
  `tpl-<uuid>.png` key; the temporary content-URI copy is deleted in every exit
  path. The notebook stores only that portable key.
- Unreadable, unsupported, encrypted/corrupt or oversized files show a Korean
  error in the studio and leave the current page unchanged.

The studio preview shows the pending image together with the real annotation
painter. `배경 이름` renames the page label, `제거` reveals the paper again, and
apply scope is still current page or every page. Importing and then cancelling
deletes the session-only asset. Removing an applied background keeps its file
until the editor closes, so the studio's one-step undo can restore the image;
the close-time cleanup deletes it only when no live notebook still references
the key.

### Cover policy

The custom image uses **cover**, not contain: it is scaled uniformly until both
page dimensions are filled, centred, and clipped by the page. It is never
stretched. A wide image on a portrait page crops the top/bottom; a tall image on
a landscape page crops the sides. The same math is shared by the editor canvas,
page rail, page manager, studio preview and raster PDF export. Applying one
asset to mixed portrait/landscape pages therefore covers each sheet without
changing its aspect.

The custom image is painted after the paper fill/template lines and before ink.
An opaque image visually replaces the template/tint; a transparent PNG lets the
selected paper show through. Paper template, tint and orientation remain stored
underneath, so removing the background restores them immediately. The quick
template dialog warns when the current page has a custom background.

### Rendering and backup

`PaperBackground.drawCustomBackground` is the single renderer used by
`NoteCanvas`, both thumbnail surfaces, the studio preview and `NoteExporter`.
Annotations, layers, hidden-layer export choices and WYSIWYG opacity are
unchanged; only the page background is extended.

Local ZIP backup includes `files/templates/` as a first-class data directory.
The existing manifest/count checks and canonical path-traversal rejection cover
the new entries. Restore replaces the directory atomically with the other known
data roots. A missing asset after a partial restore is non-fatal: the page falls
back to its chosen paper template/tint.

## UI

The note overflow keeps the existing quick template dialog and adds
`템플릿` → `NoteTemplateStudioSheet`:

- preview of the current page with the pending template/tint/orientation
- `커스텀 배경` import/preview/name/remove section
- the existing 11-template grid
- paper tint chips (warm, white, pale yellow, light gray)
- portrait / landscape chips with crop warning
- cover colour and cover style
- apply to current page or every page
- one undo step for the whole studio result

## Scope

Included: per-page orientation, paper tints, studio preview/apply, cover
editing, mixed-orientation export, page-manager/duplicate preservation, and
local custom image/PDF first-page backgrounds.

Explicitly later: camera capture, stickers, page-size presets other than A4, and
cloud/network template libraries. The app remains offline with no new
permissions.

## Verification

JVM coverage (no device required):

- legacy notebook JSON defaults to no custom background;
- relative key sanitisation rejects absolute paths, traversal, nested paths,
  drive letters, NUL/control characters and non-image extensions;
- JPEG/PNG/WebP/PDF magic-byte detection;
- cover placement for portrait, landscape, equal-aspect and invalid sizes;
- Template Studio choice persistence for current/all/removal and name
  normalisation;
- local ZIP backup includes `files/templates/`, restores the exact bytes and
  rejects a `files/templates/../../` traversal entry.

Android-only verification (manual on a device, because it needs
`BitmapFactory`, `PdfRenderer` and SAF): import a photo, import a PDF first
page, verify the editor/rail/page-manager preview and exported PDF match, rotate
the same asset between portrait/landscape, cancel an import to confirm the
session file is removed, then back up and restore the notebook.
