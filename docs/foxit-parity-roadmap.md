# Offline Foxit-style editor parity roadmap

This roadmap translates the static Foxit inventory in
[`foxit-static-analysis-2026-06.md`](foxit-static-analysis-2026-06.md) into an
offline implementation plan for My PDF Writer. It targets behavior and user
workflows, not Foxit code, assets, branding or subscription services.

## Non-negotiable constraints

- No `INTERNET`, cloud SDK, accounts, ads, analytics or cloud AI.
- Keep the immutable source PDF, canonical `DocState`, read-back-verified saves,
  source fingerprint checks and existing backup format compatible.
- Additive optional JSON fields only; legacy documents must render and export as
  before.
- Keep the six-slot PDF dock. New tools belong in `주석 도구`, the contextual row,
  the selection bar or `추가 도구`.
- All expensive PDF work runs off Main with generation guards and atomic output.
- Imported assets use app-private stable IDs, never persisted SAF paths/URIs.
- Every user-visible PDF flow gets privacy-safe `PdfFlowTrace` events and tests.

## Stage 1 — Annotation object editing and safe redaction (next)

Deliver a real object editor for annotations already created by the app:

- Tap selection with visible bounds and handles.
- Move, resize, rotate, duplicate, delete and reorder.
- Edit text/stamp/signature content and per-annotation style.
- Add fill, border/dash and arrow-end style fields with legacy defaults.
- Add squiggly underline.
- Add selected-text redaction and an explicit `가림 적용` save-copy workflow.
- Permanent redaction output must be sanitized and tested for text removal.

Primary files: `Models.kt`, `PdfCanvas.kt`, `ViewerScreen.kt`, `ViewerSheets.kt`,
`ViewerToolDock.kt`, `PdfViewModel.kt`, `AnnotationPainter.kt`, `PdfExporter.kt`,
plus new pure geometry/hit-test helpers.

Acceptance: one manipulation equals one undo step; edits survive save/reopen and
export; rotated/cropped pages stay aligned; legacy JSON is unchanged; permanent
redaction never mutates the source and fails closed when sanitization is unsafe.

## Stage 2 — Real content edits

- Insert styled text into page content.
- Replace/delete selected text as a content edit, not only a visual cover.
- Insert image objects with move/resize/rotate/opacity.
- Add internal-page and external links.
- Apply permanent replacement/removal to a separate output copy.

Introduce `DocState.contentEdits`, `PdfEditPlan`, `PdfContentEditor`,
`PdfAssetStore`, `PdfRedactor` and `PdfLinkWriter`. PDFBox-Android 2.0.27 has no
robust general redaction API, so the first safe implementation rasterizes only
affected pages, adds replacement content afterward, preserves untouched pages as
vector/searchable content and discloses the tradeoff.

Acceptance: source SHA-256 is unchanged; edited content survives reopen and
undo/redo; removed/replaced text is absent from normal extraction on affected
pages; untouched pages remain searchable; failures publish no partial output.

## Stage 3 — On-page forms and reusable visual signatures

- Detect and fill AcroForm fields directly on the page.
- Author/move/resize/rename/delete text, checkbox, radio, combo and list fields.
- Previous/next field navigation, reset and FDF/validated JSON data round-trip.
- Draw/type reusable signatures and initials with transparent backgrounds.
- Explicit form flattening; no cryptographic-signature claim without a real
  certificate-signing implementation.

Primary files: new `PdfFormLayout`, `PdfFormWriter`, `PdfSignatureStore`,
`PdfFormSheet`, `PdfSignaturePad`; extend `Models.kt`, `PdfExporter.kt`,
`BackupManager.kt`, `PdfViewModel.kt` and page remapping.

Acceptance: values are visible without viewer regeneration; authored fields and
signatures survive save/reopen/export; XFA/JavaScript submit actions fail closed;
flattened forms have visible values but no editable widgets.

## Stage 4 — Page layout and output control

- Insert pages from PDF/image at any position; replace or merge page ranges.
- Persistent manual crop/reset and optional resize/custom page size.
- Page labels and editable outline hierarchy with correct final destinations.
- Ranged header/footer and watermark settings.
- Export selected ranges with flattened annotations/redactions/forms and image
  quality options; keep save-as distinct from autosave.

Extend `PageSpec` for blank/imported/image pages, assets, crop and target size.
Introduce stable outline IDs and `PdfOutputOptions`. All page-indexed entities
must use one old-to-new page map.

Acceptance: crop/resize is visible and exported; annotations, notes, memo cards,
forms, links, assets and bookmarks follow page operations; imported assets are
backed up; existing page-operation tests remain green.

## Stage 5 — Advanced offline parity

- Comment threads/replies/resolved state.
- Annotation import/export and embedded attachments with limits.
- Area highlight, cloud, callout, polygon/polyline and measurement tools.
- Offline side-by-side/overlay/text/page comparison.
- Real image downsampling/reuse, metadata cleanup and before/after size report.
- Password-permission matrix for print/copy/modify/forms/assembly.
- Large-document performance and accessibility hardening.

Certificate signing, RMS/AIP, XFA, cloud collaboration and AI remain explicitly
out of scope unless separately designed.

## Delivery policy

Each stage may span multiple patch releases. The first implementation delivery
uses `0.8.100` / versionCode 109. Every delivery increments patch/versionCode by
exactly one and runs the full debug, JVM, Android-test compilation and R8 release
matrix before the arm64 APK is copied and pushed.
