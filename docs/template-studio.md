# Template Studio 1.0

Template Studio is an original, local-only notebook paper editor. It extends the
existing 11 `PaperTemplate` patterns; it does not import Flexcil or other
third-party templates, covers, fonts, colours or assets.

## Data

`NotePage` gains two optional JSON fields:

| Field | Values | Legacy default |
| --- | --- | --- |
| `orientation` | `PORTRAIT`, `LANDSCAPE` | `PORTRAIT` |
| `tint` | `WARM`, `WHITE`, `PALE_YELLOW`, `LIGHT_GRAY` | `WARM` |

Both fields are read with safe fallbacks, so notebooks written before this
feature load unchanged. `Notebook.toJson()` writes both fields explicitly.
New and duplicated pages copy the current page's paper settings. Study-note
pages keep the legacy portrait/warm defaults.

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

## UI

The note overflow keeps the existing quick template dialog and adds
`템플릿` → `NoteTemplateStudioSheet`:

- preview of the current page with the pending template/tint/orientation
- the existing 11-template grid
- paper tint chips (warm, white, pale yellow, light gray)
- portrait / landscape chips with crop warning
- cover colour and cover style
- apply to current page or every page
- one undo step for the whole studio result

## Scope

Included: per-page orientation, paper tints, studio preview/apply, cover
editing, mixed-orientation export, page-manager/duplicate preservation.

Explicitly later: custom image/PDF template import, camera capture, stickers,
page-size presets other than A4, and cloud/network template libraries. The app
remains offline with no new permissions.
