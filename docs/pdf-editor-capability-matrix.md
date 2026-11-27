# PDF editor capability matrix (Foxit static comparison)

This matrix compares the implemented app at `0.8.99` with the static Foxit
feature inventory. Foxit observations come from
[`foxit-static-analysis-2026-06.md`](foxit-static-analysis-2026-06.md); they are
not runtime-verified behavior.

## Core editing

| Capability | Before 0.8.100 | 0.8.100 target |
| --- | --- | --- |
| Create pen/highlighter/text/shape/stamp/signature annotations | Implemented | Preserved |
| Select and move/resize/rotate app annotations | Missing | Implemented for app-created annotations |
| Duplicate/delete/reorder app annotations | Delete only | Implemented |
| Edit existing text/stamp/signature content and basic style | Missing | Implemented |
| Underline/strikeout | Implemented | Preserved |
| Squiggly underline | Missing | Implemented as drag and selected-text markup |
| Polygon/polyline/cloud/callout/area highlight | Missing | Later stage |
| Edit original PDF text/image objects | Missing | Later stage; current replacement is visual-only |
| Image/audio/video/link/attachment annotations | Missing/partial | Later stage |
| Native PDF annotation interoperability | Missing | Later stage; app annotations remain sidecar objects |

## Document, forms and output

| Capability | Status |
| --- | --- |
| Page insert/extract/rotate/reorder/delete/duplicate | Implemented; arbitrary-position PDF/image import remains partial |
| Persistent crop/resize and page labels | Missing |
| Outline hierarchy write-back | Partial; sidecar bookmarks only |
| Existing AcroForm filling | Partial; detached list, generic controls |
| Form field authoring and on-page widgets | Missing |
| Reusable drawn/typed visual signatures | Missing |
| Certificate/digital signatures | Missing; separate security product |
| Password output protection | Implemented in PDF Tools with fixed permissions |
| Permanent text-aware redaction | Partial; rectangle redaction rasterizes affected export pages |
| Watermark/page numbers | Implemented as text overlay |
| Flatten/optimize | Partial; export flatten exists, optimizer is minimal |
| Merge/split/extract | Implemented at basic page level |
| OCR | Implemented with bundled ML Kit |
| Gallery images to PDF | Implemented; camera scanning/edge detection missing |
| Print/share/PDF-to-images/metadata | Implemented |
| Cloud/AI/accounts/subscription/RMS | Intentionally excluded |

## Architectural boundary

The current editor stores app annotations in `DocState` JSON and flattens them
into export overlays. `0.8.100` adds an object editor for those annotations; it
does not claim to edit arbitrary native PDF annotation objects or rewrite source
text. The next stages add content edits, forms/signatures, page/output control
and advanced offline tools as described in
[`foxit-parity-roadmap.md`](foxit-parity-roadmap.md).
