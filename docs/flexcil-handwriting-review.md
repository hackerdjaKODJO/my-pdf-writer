# Flexcil handwriting review — My PDF Writer 0.8.2

## 1. Scope and evidence

This is a **documentation-only, static review** written against My PDF Writer
**0.8.2 / versionCode 11** on branch `work/flexcil-review`. It compares the
handwriting-note surface described in `docs/flexcil-analysis.md` with the current
note editor code and with the 0.8.3 work described in
`/data/data/com.termux/files/usr/tmp/opencode/pdfw/FLEXCIL-HANDWRITING-PLAN.md`.

Evidence used:

- `docs/flexcil-analysis.md`: static APK resources, manifest, bundled assets,
  `aapt2` output and public Play material for Flexcil 1.5.1.4. **No Flexcil code
  was decompiled, executed, instrumented or copied.**
- My PDF Writer source at 0.8.2: `NoteEditorScreen.kt`, `NoteCanvas.kt`,
  `NoteStyleSheet.kt`, `NoteLasso.kt`, `PenBoxSheet.kt`, `Notebook.kt`,
  `Models.kt`, `AnnotationPainter.kt`, `NoteExporter.kt`, `StudyNoteBuilder.kt`,
  `BackupManager.kt`, the manifest, and unit/E2E tests.
- `docs/ux-overhaul.md` for the already-shipped phone layout.

Claims about My PDF Writer describe **0.8.2 code that exists in this worktree**.
Stabilization, shape recognition, partial erase, PDF annotation filtering and
lasso transform/clipboard were **planned 0.8.3 work when this review was written**.
They have since landed in the 0.8.3 implementation; see §1.1 for the delivered
status and differences from the original acceptance notes. Flexcil claims are
resource/manifest/Play evidence only; they do not prove that a flow is reachable,
enabled or working in this build. There are no runtime claims for either app. All
label quotations below are short functional evidence; no Flexcil copy,
screenshots, templates, fonts, icons or branding are reproduced.

### 1.1 Implementation status — 0.8.3 and 0.8.4 delivered

The 0.8.3 delivery implements the primary handwriting gaps with original code. The 0.8.4 delivery adds the study pane, pressure ink, template studio and page/export organization described below; see the 0.8.4 status after the 0.8.3 table.

| Delivered | Default / behavior | Notes |
| --- | --- | --- |
| Stroke stabilization | **On**, fixed gentle strength 0.25; toggle `필기 설정 → 필기 보정` | PEN/HIGHLIGHT commit points are smoothed; preview stays raw; endpoints and point count preserved |
| Shape recognition | **Off** opt-in; `필기 설정 → 도형 자동 인식` | Rough LINE/RECT/ELLIPSE only; conservative thresholds; active tool stays PEN |
| Partial eraser | **On**; `필기 설정 → 부분 지우개` | Splits freehand/highlighter/underline/strikeout/line/arrow/signature polylines; text/stamp/filled shapes remove whole; active layer respected |
| PDF annotation hide | Hub `주석 숨기기`; display only | Persistent banner; stored/exported data unchanged |
| PDF annotation panel | Filters `전체/필기/텍스트/도형/가림`; page-grouped rows | Flat list replaced with grouped panel; jump/delete preserved |
| Lasso transform | Selection `편집` sheet: nudge, scale, rotate, copy/paste | A4-aspect-aware rotation; one undo step per action; dashed selection bounds |
| Selection clipboard | Session-only, notebook-scoped | Fresh IDs, active-layer remap, immediate replay timing; does not survive closing the editor |

Known limits: stabilization strength is fixed; shape recognition is axis-aligned
and conservative; split ARROW segments each keep an arrowhead; copied ink is
session-only; arbitrary non-90° rotation is not represented for two-corner shapes.

**0.8.4 delivered (after this 0.8.2 baseline):** a real `NoteEditorScreen` hosted
in a PDF bottom pane with detached save/export/open-source-page; stylus pressure
capture for fountain/brush ink with legacy-safe optional JSON; a Template Studio
with per-page A4 portrait/landscape, four paper tints and cover colour/style; a
note page manager with `전체/필기` filters and multi-select duplicate/delete; and
note export presets (all/current/selected, include hidden layers). Known 0.8.4
limits: the pane is modal rather than resizable split, pressure curves are not
device-tuned, custom image/PDF templates are not imported, and page-manager
rotation/photo insertion remains later. The remaining queue is custom template
media, gesture undo/redo, note-content search and pressure tuning on hardware.

## 2. Feature matrix

Baseline is **0.8.2**; the 0.8.3 delivery status and the differences from the
original acceptance notes are recorded in §1.1. Rows without a version marker
describe 0.8.2 behavior; 0.8.3 rows are marked delivered where applicable.

| Area | Flexcil evidence (static) | My PDF Writer current (0.8.2) | Gap / priority |
| --- | --- | --- | --- |
| Pen and highlighter | Pen/highlighter tools; `DefaultPens.json` types/colours/widths; fountain/laser/highlighter; pen toolbar up to 30 pens, 50 favourites advertised | `Tool.PEN`/`HIGHLIGHT`; 4 `PenStyle` values (ballpoint, fountain, brush, pencil); 8-colour palette; width/opacity; highlighter width floor and opacity forced to 1; preview and committed ink use `AnnotationPainter` | Core present. Add eraser scoping and later pressure. **Preserve** |
| Pen presets / pen box | Pen toolbar management, favourites, per-pen settings | `PenBoxStore` with 5 original defaults; add/save, rename, delete; applying a `PenPreset` sets tool, colour, width, opacity and style; local `SharedPreferences` | Presets present. Direct dock access is weak (Style → PenBox). **P2 polish** |
| Stroke stabilization | `pen_stroke_stabilization` string; smoothing field in default pens | **0.8.3 delivered:** gentle endpoint-preserving stabilization, default on at 0.25; toggle in note overflow | Closed; strength slider later |
| Pressure / fountain dynamics | Premium pressure-fountain header; static evidence, runtime not verified | **0.8.4 delivered:** optional stylus pressure in points (`prs` JSON), fountain/brush width response, finger fallback 1.0, pressure-preserving edits | Schema/rendering delivered; hardware tuning later |
| Eraser: whole stroke | `eraser_stroke_path`, `eraser_pressure_eraser`, `eraser_include_highlighter`, `eraser_clear_page`, page-region erasers | Whole-object mode remains available; **0.8.3 adds default partial splitting** for freehand/highlighter/underline/strikeout/line/arrow/signature; text/stamp/filled shapes remove whole; active layer only | Whole mode remains toggle-off fallback; partial delivered 0.8.3 |
| Eraser: partial / scoped | `eraser_split_path`, highlighter-only, clear page, left/right page regions | **0.8.3 delivered:** partial stroke splitting, opt-in; highlighter-only/page-clear absent | Partial shipped. Highlighter-only/page erase **P2, later** |
| Lasso selection | Free/rect lasso; `lasso_setting_enable_pen/highlighter/image/textbox`; lasso editing videos | Free and rectangular lasso; `NoteLasso` hit-tests pen, highlighter, text, stamps, signatures, shapes; selection bar recolours, duplicates with a small offset, deletes, clears | Selection present. No selectable-type filter. **P1** |
| Lasso editing / transform | Static evidence proves lasso editing; exact move/resize/rotate set is **not** enumerated, so treat transform parity as a UX expectation, not verified | **0.8.3 delivered:** selection bounds, nudge, scale, ±90° rotation and session clipboard; duplicate remains | Closed in 0.8.3; on-device gesture polish and arbitrary-angle rotation later |
| Shape / straight line | Shape drawing, `straight_line`, `shape_polygon_side`; manual shape tools | Manual shapes plus **0.8.3 opt-in** LINE/RECT/ELLIPSE recognition from PEN; no polygon sides | Closed for line/rect/ellipse; polygon later |
| Gestures / input modes | Eraser gesture; undo/redo gesture; one-finger pan in pen/gesture mode; popup gestures | Stylus draws and fingers pan by default; finger-writing toggle; pinch zoom 1–6×, two-finger pan, double-tap reset, zoom lock; undo/redo buttons only | Deliberately deferred; gesture conflicts are real. **P2, later** |
| Templates / covers / orientation / custom media | 198 A4 PDFs (99 portrait, 99 landscape); cover/template editor; orientation icons; import from Files/Gallery; apply all/selected/default; page-size warning; stickers/photos | 11 original templates; **0.8.4 Template Studio** with per-page A4 portrait/landscape, four paper tints, cover colour/style and crop warning; custom image/PDF media absent | Orientation/tint/cover delivered; custom media **later** |
| Layers | No layer-specific Flexcil string in the static evidence; do not infer Flexcil layer parity | Per-page `NoteLayer` with name, visible, locked, opacity; add/rename/delete with annotation reassignment; reorder; active-layer filtering; layer opacity applied to render and export; `NoteLayersTest` | Not a Flexcil-derived gap; this is a local strength. **Preserve** |
| Page manager | Page-manager thumbnails, All/Annotated filter, add template/photo page, paste copied pages, merge/extract | PDF `OrganizeScreen` strong. **0.8.4 note manager:** `전체/필기` filter, multi-select duplicate/delete, annotation counts, correct page-orientation thumbnails; rotate/photo insert absent | Core note manager delivered; rotate/photo later |
| Export | `pdfexport_opt_original`/`flatten`, include background/drawing/image/sticky/text; free watermark and 10-page share cap advertised | PDF: flattened WYSIWYG export and extraction. **0.8.4 note export presets:** all/current/selected, include hidden layers, mixed portrait/landscape A4, WYSIWYG layer opacity | Presets delivered; editable-original note export not promised |
| Study-note capture / backlinks | Bottom/side study-note pane, popup document, L/box/hold capture, automatic source link, To/From links panel, jump back | **0.8.4 live bottom pane** hosts the real note editor over the PDF, detached save/export, existing source-page jump and excerpt append. No draggable split/To-From panel or source-rectangle capture | Pane loop delivered; resizable split/drag capture **later** |
| Audio replay | Recording service, 6 h advertised, 0.5×–2.0× playback, note sync, handwriting replay, multi-document sync, premium | Local recording/playback per notebook/PDF; markers; `Annotation.createdAt`/`durationMs`; `AudioReplayController` reveals strokes point by point and supports seek; no speed control | Core replay present and free/local. Speed/multi-doc **P2, later**; preserve timestamps |
| Split / popup / second document | Two-document split, floating resizable popup, popup↔split swap, bottom study pane, 2/4-page spreads | Read-only 2/4-page spread of one PDF; `PopupPageDialog` shows one page; no second document, no resizable floating window, no live note pane | Largest shell gap, tablet-first complexity. **P2, later** |
| Search / panels | Title, main-text and text-box search; page-grouped annotation panel with All/Annotations/Links/Maskings filters, snippets, page numbers, multi-delete; hide-all mode | PDF full-text search with page hits/snippets and on-page highlight; `AnnotationSheet` is a flat list sorted by page/type; note home searches title/tags only; no note annotation panel or hide mode | PDF panel filters/hide are 0.8.3 Agent B. Note panel/search **P2** |

## 3. Handwriting UX patterns worth borrowing on a phone

Borrow the **interaction idea**, not Flexcil's layout, colours, icons or copy.
All My PDF Writer labels and visuals should remain original Material 3.

| Pattern | What Flexcil demonstrates | Original phone translation |
| --- | --- | --- |
| Thumb reach | Compact tool pill, page navigation and side panels around a content-first reader | Keep the note dock and page pill at the bottom; keep the top bar for back/title/overflow only. Put style, eraser mode and pen-box sheets in the lower half of the screen; respect navigation-bar insets and 44 dp targets. Do not add a permanent side rail on phones. |
| Context sheets | One panel per task (pen toolbar, annotation panel, page manager, study note) | Keep the existing tool-aware style sheet: show colour/width/opacity only when the active tool uses them. Add an eraser-mode sheet and a selection sheet rather than one mega-menu. Remember each sheet's last choice for the session. |
| Selection handles and actions | Lasso selects objects, then exposes editing actions | In lasso mode draw a high-contrast bounding box with four corner handles and one rotate handle. Put a floating action bar above or below the box: move, duplicate, delete, recolour, copy, paste. Keep the top five visible and overflow the rest. Handles must claim the gesture before canvas pan. |
| Pen box | A small set of favourite pens is always close to the page | Show 3–5 saved pens as compact swatches plus a “more” slot; tap applies, long-press edits. Tapping the active colour should open the pen box directly, with the style sheet still one step away. |
| Eraser modes | Whole-stroke, partial, highlighter-only and page erase are distinct choices | Use a segmented control with plain labels: 전체 획 / 부분 / 형광펜만 / 페이지. Keep whole-stroke as the safe default; partial is opt-in. Show the eraser radius preview and name the active mode in the hint strip. Ask before page clear and make it undoable. |
| Gestures | Eraser and undo/redo can be gestures; one-finger pan is configurable | Preserve the current stylus-draw / finger-pan default and the explicit finger-writing toggle. Do not add gesture undo/redo in 0.8.3. If added later, make it opt-in and test it against pinch, pan, lasso and stylus palm rejection. Do not copy the three-finger popup pinch. |
| Feedback and undo | Destructive actions are visible and reversible; progress is explicit | Every erase, split, recognized shape or paste is one undo unit. Show a short confirmation (“3개 획 지움 · 실행 취소”) and keep the 60-step stack. Keep `NoteTrace` events for stroke count, smoothing, recognition and erase. |
| Discoverability | Tools are visible in a compact toolbar; hidden gestures are taught separately | Labels plus icons in the dock; no long-press-only destructive action. First-run tips should be a dismissible inline hint, not a modal tutorial. Keep content descriptions and test tags for the E2E suite. |

## 4. Top 10 prioritized gaps

“Must-add” means it closes a direct handwriting gap with bounded risk in 0.8.3.
“Must next” is the integration queue immediately after 0.8.3. “Later” means
valuable but dependent on schema, shell work or device testing.

| # | Gap | Acceptance criteria | Map to current files | When |
| ---: | --- | --- | --- | --- |
| 1 | Stroke stabilization | A pure `NoteInk.stabilize(points, strength)` returns normalized points; strength 0 is identity; endpoints are preserved exactly; 1–2 point strokes are unchanged; deterministic; no NaN. Delivered 0.8.3: toggle in note overflow “필기 설정”, default on at strength 0.25, session-only. PEN and HIGHLIGHT. Preview and committed stroke look identical. One undo step per stroke. Unit tests for endpoints, strength 0/1, short strokes and determinism; compile the stroke E2E tests. | `NoteCanvas.kt`, `NoteEditorScreen.kt`, new `NoteInk.kt`, new `NoteInkTest.kt` | **Must-add 0.8.3** |
| 2 | Partial eraser with stroke splitting | `NoteInk.erasePartial(points, trail, radius)` splits PEN/HIGHLIGHT into surviving fragments; drops fragments with fewer than 2 points. Preserve `type`, `color`, `width`, `opacity`, `penStyle`, `layerId`, `createdAt`, `durationMs`. TEXT/STAMP/SIGNATURE/RECT/ELLIPSE/LINE/ARROW still remove whole, as planned. Active layer only. Delivered 0.8.3: “부분 지우개” default on; turning it off keeps whole-object erasing. Undo restores the exact original list. No file-format change. Tests: middle hit → two fragments, endpoint hit → one, no hit → unchanged, highlighter and legacy timestamps. | `NoteCanvas.kt`, `NoteEditorScreen.kt` (`erase`), new `NoteInk.kt`, new `NoteEraserTest.kt` | **Must-add 0.8.3** |
| 3 | Shape recognition / straight-line assist | `NoteInk.recognizeShape(points)` returns LINE, RECT or ELLIPSE only when clear thresholds pass; otherwise null. PEN only, never HIGHLIGHT/TEXT/LASSO; opt-in “도형 자동 인식”, default off. Recognized annotations preserve colour, width, opacity, layer and timestamps; keep the original stroke in undo. No polygon tool in this pass. Unit tests for line, rectangle, ellipse, ambiguous scribble and short stroke. | `NoteCanvas.kt`, `NoteEditorScreen.kt`, new `NoteInk.kt`, new `NoteShapeRecognitionTest.kt` | **Must-add 0.8.3** |
| 4 | Page-grouped annotation panel + hide mode | PDF panel: filter chips All/Ink/Text/Shapes/Redact; page headers; type icon, colour chip, two-line snippet and page number; tap jumps, delete and multi-delete preserved. Hide-all is display-only with a persistent banner and must not change exported PDF. Pure filter tests. Notebook annotation list is not part of the 0.8.3 cut. | `ViewerSheets.kt` (`AnnotationSheet`), `PdfViewModel.kt`, new pure filter file/test; note list later in `NoteEditorScreen.kt` | **Must-add 0.8.3** |
| 5 | Lasso transform: move, resize, rotate, nudge | **0.8.3 delivered with sheet controls:** selection bounds overlay; 1% nudge, ±10% scale, ±90° rotation, copy/paste; A4-aspect-aware rotation; one undo step per action; selection stays selected. Direct drag/corner/rotate handles remain a later gesture-polish item. Tests for translate, proportional scale, rotate, clamping and text/stamp anchors. | `NoteSelectionTransforms.kt`, `NoteSelectionSheet.kt`, `NoteEditorScreen.kt`, `NoteCanvas.kt` | **Delivered 0.8.3; handle gesture polish later** |
| 6 | Selection clipboard and safe paste | **0.8.3 delivered:** session-only, notebook-scoped copy/paste across pages; fresh IDs; active-layer remap; 1.2% offset; `createdAt = now`, `durationMs = 0`; undo one step; source page untouched. Cross-notebook persistence is later. | `NoteClipboard.kt`, `NoteEditorScreen.kt` selection bar/clipboard strip, tests | **Delivered 0.8.3** |
| 7 | Live study-note pane with two-way backlinks | **0.8.4 delivered core:** a real note editor in an 80% bottom pane over the PDF; detached immediate save, export, source-page jump, excerpt append. Fixed height; drag-capture blocks, To/From panel and source rectangles remain later. | `StudyPaneSheet.kt`, `MainActivity.kt`, `NotebookViewModel.kt`, `StudyNoteBuilder.kt` | **Delivered core 0.8.4; resizable/drag later** |
| 8 | Pressure-sensitive fountain ink | **0.8.4 delivered:** pressure captured for stylus, optional `prs` JSON with legacy 1.0, fountain/brush width response, finger fallback, shared preview/export painter; no hardware tuning yet. Tilt and pressure instrumentation remain later. | `InkPressure.kt`, `Models.kt`, `NoteCanvas.kt`, `AnnotationPainter.kt` | **Delivered core 0.8.4; hardware tuning later** |
| 9 | Template studio: orientation and custom media | Add nullable per-note/per-page orientation; old JSON defaults to portrait; renderer/export honor it. Support custom image/PDF template import through SAF into app-private storage, with a thumbnail and apply current/all. Warn when a size change can crop ink. No Flexcil templates/assets. Tests for legacy load, aspect math, export and backup inclusion. | `Notebook.kt`, `PaperBackground.kt`, `NoteEditorScreen.kt`, new template store, `NoteExporter.kt`, backup manifest | **Later 0.8.7** |
| 10 | Note page manager + export presets | **0.8.4 delivered:** page filter, multi-select duplicate/delete, page add, annotation counts, orientation-aware cards; export all/current/selected plus hidden layers, existing filename launcher. Multi-page move remains in overflow; rotate/photo insertion and custom filename later. | `NotePageManager.kt`, `NoteEditorScreen.kt`, `NoteExportOptions.kt`, `NoteExporter.kt`, `MainActivity.kt`, `StudyPaneSheet.kt` | **Delivered core 0.8.4; rotate/photo later** |

Explicitly **not** in the 0.8.3 cut: gesture undo/redo, eraser gesture, pressure
ink, highlighter-only/page erase, live study-note pane, second-document split,
floating popup, template studio, note export presets. They are listed above so
they are not mistaken for shipped behavior.

## 5. What My PDF Writer already does better — and how to keep it

| Advantage | Evidence | Preservation rule |
| --- | --- | --- |
| Offline by construction | Manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`; no account/ads/analytics startup; the app opens the local library. Flexcil declares network/ads/billing/cloud services and launches an account activity. | Do not add network, cloud, account, ads or analytics dependencies. Re-check the **merged** manifest on every release; a transitive SDK must not re-add `INTERNET`. |
| Local backup / restore | `BackupManager` writes a ZIP with manifest/count validation and path-traversal rejection; SAF import/export; optional source PDFs; tested. Flexcil also has manual backup, so this is an advantage of simplicity/privacy, not uniqueness. | Include every new note/template/audio sidecar in the manifest and restore tests. Keep backup versioned and user-initiated; never auto-upload. |
| Local audio replay | `Annotation.createdAt`/`durationMs` plus `AudioReplayController` reveal strokes point by point with seek; recordings live under app-private storage; no paywall. Flexcil's version is broader/premium, so the advantage is local access, not feature count. | Stabilize/split/transform/copy must preserve or deliberately reset timestamps. Add regression tests so ink edits do not break replay. |
| Linked study notes | `StudyNoteBuilder` creates excerpt pages with `sourcePageIndex` and memo; drag/drop appends to the matching notebook; note overflow opens the source PDF page. Flexcil's live pane and To/From panel are richer, but the local link data is a real head start. | Keep `sourceDocumentUri`/`sourcePageIndex` stable through page moves, duplicates and future transforms. Add optional source-rect fields rather than replacing page links. |
| No ads, watermarks or export caps | No ad SDK; PDF/note export is local; no 10-page share cap. Flexcil advertises ads, watermark removal and a 10-page share limit. | Do not add rewarded ads, watermarks or page limits. A user-chosen watermark remains a document style, not a paywall. |
| Layer-aware WYSIWYG export | `NoteLayers.visibleAnnotations` and layer opacity feed both canvas and `NoteExporter`; export uses the same `AnnotationPainter` as the editor. | New eraser/transform/recognition code must remain layer-aware and must not bypass the shared painter. Compare preview and export in tests. |

## 6. IP boundary

**Allowed:** functional concepts, information architecture, interaction patterns
and the general read→capture→note→jump-back workflow. These are ideas, not copied
expression.

**Not allowed, and not present in this repository:**

- Flexcil code, dex files, decompiled sources, layouts, drawables, icons, colour
  tokens or UI copy.
- Its 198 paper templates, cover packs, pen-toolbar skins, stickers, fonts,
  audio, dictionary data, sample PDFs or screenshots.
- The Flexcil name, logo, app icon, store copy or any branding implying
  affiliation.
- Pixel-for-pixel layouts or a copied visual identity. Implement original
  Material 3 components, Korean labels and `PaperBackground` drawings.

Implementation rules: keep the APK and Play screenshots outside the repo; this
document is the only analysis artifact committed from the review; do not extract
paid content or bypass premium/DRM; do not copy reference screenshots into
`docs/`; use permissively licensed data or user-created media only.

## 7. Privacy and no-INTERNET constraints

- `INTERNET` and `ACCESS_NETWORK_STATE` are explicitly removed in the manifest.
  Any new library that adds them must be rejected or stripped and verified in the
  merged manifest.
- No cloud sync, account, telemetry, analytics, crash upload, ads, cloud AI,
  WebView, HTTP client or remote font/template fetch.
- `RECORD_AUDIO` is only for the local audio-note recorder and is requested at
  runtime when recording starts. Keep it that way.
- Translation is an external app/web intent; TTS uses the system service; note
  content, handwriting, excerpts, audio and backups never leave the device unless
  the user explicitly shares/exports them through SAF or the Android share sheet.
- Store new templates, presets, ink and link metadata in app-private files. Backup
  is local and user-initiated. Keep the debug-only `NoteTrace` surface gated by
  `BuildConfig.DEBUG`; do not add production logging of note content.
- Release verification: merged-manifest permission check, backup restore test,
  export WYSIWYG check, and no new `INTERNET` in the arm64 release APK.

## 8. Implementation sequence for 0.8.3+

| Version | Scope | Exit criteria |
| --- | --- | --- |
| **0.8.3** | Stabilization, shape recognition, partial eraser, PDF annotation filter/page grouping/hide mode, lasso transform and session clipboard. | Delivered: `assembleDebug`, 214 JVM tests, `assembleDebugAndroidTest`, `assembleRelease`; no new permission; versionCode 12. On-device stroke/selection E2E still pending an adb device. |
| **0.8.4** | Study-note bottom pane, pressure ink, Template Studio (orientation/tint/cover), note page manager and export presets. | Delivered: debug/tests/instrumentation compile/R8; pane detached save and export; pressure/legacy tests; template/orientation tests; page manager/export tests. Resizable split/drag capture/custom media/hardware tuning remain. |
| **0.8.5** | Study-note bottom pane, linked blocks, source-rect backlinks, To/From links list. | Pane height/undo/close persistence; drag capture; jump-back after page reorder; local-only; backup includes link fields. |
| **0.8.6** | Stylus pressure capture and pressure-aware fountain rendering. | Legacy JSON round-trip; finger fallback; stylus instrumentation; preview/export parity. |
| **0.8.7** | Orientation + custom template/media import; notebook page manager and export presets. | Legacy notebooks load as portrait; orientation export correct; SAF import; backup restore; range/include tests. |
| Later / opt-in | Gesture undo/redo and eraser gesture; second-document split/floating popup; note-content search. Avoid cloud, ads and AI. | Only after conflict, discoverability and privacy review; no change to the no-INTERNET baseline. |

The merge order remains: shared-file owners first, then new-file agents; check
`git status --short` is clean before merging; keep `.termux-build.sh` local-only;
do not push from this review branch.
