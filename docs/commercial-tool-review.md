# Commercial note/PDF tool review — My PDF Writer 0.8.8

> Documentation-only review on `work/commercial-review`, against `main` at
> `6917140` (v0.8.8 / versionCode 17). Prepared 2026-11-17. No app code, tests,
> assets, manifests or build files were changed.


> **0.8.12 implementation update:** the three P1 items below (interactive note checklists, audio playback speed, and external dictionary handoff) were implemented after this evidence/review snapshot with original code and no copied Flexcil assets.

## 1. Scope, evidence and boundaries

**Offline-only comparison.** This is a product-quality review of the *current*
My PDF Writer against the **behavioral patterns** of Flexcil and GoodNotes-style
commercial note/PDF tools. It is not a benchmark, not a compatibility target and
not a request to add network features.

**Evidence used, and its limits:**

- `docs/flexcil-analysis.md` and `docs/flexcil-handwriting-review.md` — the only
  Flexcil analysis permitted here. It is **static** (manifest, resources, assets,
  public Play material for Flexcil 1.5.1.4). Its findings are *inferred surface
  existence*, not proof of runtime behavior, reachability or quality. **No new
  reverse engineering was performed**: no APK inspection, decompilation,
  instrumentation, Frida, network, account or purchase flow for any third party.
- GoodNotes-style patterns are taken from **public, vendor-authored material**
  only: the Goodnotes "Information for AI Assistants" page (verified Sept 2026),
  pricing/plan page, product blog posts (2023, 2024) and App Store listing. These
  are documented product claims, independently *not* runtime observations.
- My PDF Writer source at `6917140`: 105 Kotlin files under `app/src/main/java`,
  `AndroidManifest.xml`, `docs/usage-guide.html`, `docs/ux-overhaul.md`,
  `AGENTS.md`, and the delivered status recorded in the two reviews above.
  Claims about our app are grounded in this tree; e.g. `ViewerUiState` exposes
  `searchHits` but **no `currentHitIndex`/next/previous field**, and the only
  "가림" (masking) hits are the `Tool.REDACT` black-box tool and a static
  `CHECKLIST` paper template — **there are no masking-tape study cards, flashcards
  or O/X scoring in 0.8.8**. Likewise there is no search next/previous navigation.

**IP boundary.** Reuse interaction ideas and information architecture only. Do
not reuse either app's code, dex/resources, layouts, icons, colours, UI copy,
templates/covers/stickers/audio/fonts/sample PDFs/screenshots, names, logos or
branding; no pixel-for-pixel layouts. Keep all implementation original Material 3
with our own Korean labels and code-drawn paper. No premium bypass, DRM
circumvention or extraction of paid content; the APK and Play screenshots stay
outside the repository.

**How to read the tables.** The matrix compares *behavioral patterns*, not
feature-count checklists: a "gap" is a missing or shallow interaction pattern,
not a demand to reproduce a competitor's exact UI. "Current" always means the
0.8.8 code in this worktree; planned work in sibling agent branches is reported
as a target in §4/§6 and never as delivered here. Priorities assume the fixed
offline/no-ads/no-cloud constraint and the phone-first form factor.

## 2. Feature matrix

Priority key: **P0** preserve/verify, **P1** next delivery, **P2** near queue,
**P3** later/conditional, **—** no action (strength).

| Row | Commercial pattern (behavioral, documented) | Current My PDF Writer (0.8.8, code-verified) | Gap / quality issue | Pri |
| --- | --- | --- | --- | --- |
| Handwriting / pens / pressure | Goodnotes: natural ink, fountain/ballpoint/brush, iOS handwriting sensitivity, Smart Ink; Flexcil: fountain/laser/highlighter, stabilization, up to 30 pens | `PenStyle` ballpoint/fountain/brush/pencil; 8-colour palette, width/opacity; stylus pressure with soft/linear/firm curves (`InkPressure.kt`); stabilization on by default | No device pressure calibration (hardware range varies), no tilt, no scribble-to-erase, fixed stabilization strength | P2 |
| Eraser / lasso / transform | Goodnotes: Scribble to Erase, Circle to Lasso, lasso resize/align/cut/copy/paste; Flexcil: stroke/partial/pressure/highlighter-only/page erase, selectable lasso types | Default partial eraser with whole-object fallback; free/rect lasso; selection sheet nudge, scale, ±90° rotate, session clipboard (`NoteInk.kt`, `NoteSelectionTransforms.kt`) | No gesture erase/lasso; no highlighter-only/page erase; no selectable-type filter; arbitrary-angle rotation not represented | P2 |
| Shapes | Goodnotes shape tool with recognition, snapping/fill; Flexcil straight line, polygon side count | Manual RECT/ARROW/LINE/ELLIPSE plus opt-in LINE/RECT/ELLIPSE recognition from PEN | No polygons, fill or snapping; recognition conservative and axis-aligned | P3 |
| Templates / covers / media | Goodnotes dynamic templates (size/colour), covers, marketplace, washi tape/stickers/images; Flexcil 198 A4 PDFs, cover/template editor, Files/Gallery import | 11 original templates, four tints, cover studio, custom image and PDF-**first-page** backgrounds via SAF, photo pages (`NotePaper.kt`, `NoteTemplateStudio*`) | Custom PDF import is first-page only; no washi tape/stickers, no dynamic page sizing; **no masking study cards** | P1/P2 |
| Notebooks / pages / layers | Goodnotes unlimited notebooks, pages; layers are experimental on iPad/Mac; Flexcil page manager with All/Annotated filter | Notebook JSON; per-page `NoteLayer` with visible/locked/opacity; page manager filter, multi-select duplicate/delete/rotate, photo pages | Layers apply to notes, not PDF pages; no cross-notebook merge/extract | P2 / — |
| PDF annotation / markup | Goodnotes import, highlight, mark up, search annotations; Flexcil editable imported annotations, grouped annotation panel with All/Ink/Link/Masking filters, hide mode | PEN/HIGHLIGHT/TEXT/shapes/STAMP/SIGNATURE/REDACT on PDF; page-grouped panel with filters; hide mode display-only; flattened WYSIWYG export | No editable-original export path; annotation content is not searchable | P1/P2 |
| Text selection / links | Goodnotes search across handwriting/typed/PDF, internal links between pages; Flexcil gesture selection, copy/dictionary/translate/bookmark, drag-to-note with backlinks | Tap/drag word selection + floating bar copy/highlight/underline/strike/translate/excerpt; To/From source links from `NotePage.sourcePageIndex` (`StudyNoteSourceLinks.kt`) | Only text-layer PDFs; no external dictionary; captured blocks link at page level, not source rect; no in-PDF hyperlinks | P1/P2 |
| Split / popup / study pane | Flexcil split screen, resizable popup, popup↔split swap, bottom study pane; Goodnotes presentation mode / multi-window on larger screens | Resizable 35–85% bottom study pane with draggable centre divider, floating PDF/note chrome, read-only 2/4-page spread | No second-document split on phone, no floating popup, no popup↔split swap; tablet-first complexity | P2 |
| Masking / checklist / flashcards | Flexcil masking tape: cover answer, O/X, score, side-panel filter; interactive checklist; Goodnotes **Study Sets** flashcards (active recall, spaced repetition), tape | Static `CHECKLIST` paper template and `Tool.REDACT` black-box only; no masking cards, O/X marking, score or flashcards | Distinctive study loop absent — largest single feature gap | P1 |
| Search | Goodnotes handwriting/OCR + typed + PDF + folder-title search; Flexcil title/main-text/text-box search, page-grouped results | PDF full-text search with page hits/snippets and on-page highlight; note search over memo/text/stamp/signature; home title/tag search. `ViewerUiState` has `searchHits` only | **No next/previous hit navigation or current-index indicator**; no handwriting search; no cross-document search | P1/P3 |
| Organization / folders / tags | Goodnotes multi-colour folders, bookmarks; Flexcil folders/favourites/recents/trash/search; **no Flexcil tags** | Notes/PDF/Tags tabs, folders, tags, favourites, trash, grid/list, sort (`NotebookHomeScreen.kt`) | PDF-side organization thinner than notes; no bulk multi-select move. Tags are our differentiator, not a parity item | P2 / — |
| Backup / export | Flexcil local `.flex` backup, folder export, cloud sync, ads/watermark, 10-page share cap; Goodnotes cloud + Google Drive/OneDrive/Dropbox and paid watermark-free export | Local ZIP backup/restore with manifest validation and path-traversal rejection; SAF; PDF flatten export; note export presets; **no caps/watermark** | Backup ZIP is unencrypted (guide warns); no folder export; no editable-original export | P1/P2 |
| Audio replay | Goodnotes audio synced to writing, unlimited recording (paid), transcription; Flexcil 6 h, 0.5×–2× speed, multi-doc sync, premium | Local recording + point-by-point stroke reveal with seek from `createdAt`/`durationMs` (`AudioReplayController.kt`); free, no paywall | No playback speed control, no transcription, no multi-doc sync; hardware tuning pending | P2/P3 |
| Privacy / offline | Flexcil declares network/ads/billing/cloud services and an account launcher; Goodnotes syncs to cloud and ships AI; both offer some offline access | `INTERNET` and `ACCESS_NETWORK_STATE` removed in manifest; `RECORD_AUDIO` runtime-only for local recorder; translation/TTS via external intents; no accounts/ads/analytics/cloud | None relative to our product goal — this is the core strength | **P0 preserve** |

## 3. Quality dimensions beyond features

Scores are approximate, evidence-based judgements of the 0.8.8 tree, not
benchmarks. "Unverified" means no adb device was available (see `usage-guide.html`).

| Dimension | Score | Why |
| --- | --- | --- |
| Discoverability | 7/10 | Seven-primary-tool PDF hub + "모든 도구 보기", contextual style/selection sheets, Korean labels; but many capabilities still sit behind overflow sheets and a few gestures (two/three-finger tap) are hard to find |
| One-hand reach | 8/10 | Bottom docks, floating page pill and compact floating PDF/note controls, thumb-reachable sheets; 85% default pane leaves a thin PDF strip and small overlay targets |
| Latency / responsiveness | 7/10* | PdfRenderer bitmaps, shared AnnotationPainter for ink, stabilization; no on-device profiling, and large single-file notebook JSON may grow — measurement needed |
| Data safety | 7/10 | Backup manifest/count validation and path-traversal rejection; synchronous working-notebook save + `NoteTrace`; multi-stroke loss fixed in 0.8.2; undo stack. Backup is unencrypted and there is no auto-save/version history |
| Error states | 6/10 | `searchFailed`/`busy` states, image-PDF selection warning, hide-annotations banner, troubleshooting section; sparse validation/retry feedback on import/export failures |
| Onboarding / help | 7/10 | Offline single-file `usage-guide.html`, "빠른 5분 튜토리얼", FAQ; no in-app first-run inline hints, help lives outside the app |
| Accessibility | 5/10 | Material 3, content descriptions/test tags, dark theme; no TalkBack audit, font-scale audit or verified 48 dp target coverage |
| Release polish | 7/10* | Strict version policy, changelog, debug/unit/androidTest/unsigned-release builds, ~352 JVM tests, arm64-only APK; no real-device smoke test for touch/pressure/overlap |

\* Unverified on hardware. Rough overall: **~6.8/10** — feature breadth is strong;
the gaps are depth in the study loop, error/accessibility polish and on-device proof.

## 4. Top 10 prioritized gaps (acceptance criteria + file mapping)

Items 1–2 are the 0.8.9 candidates owned by other agents. **They are not present
in the reviewed 0.8.8 tree**; criteria below are targets, not shipped behavior.
All items stay within no-INTERNET/no-ads/no-cloud and use original assets only.

| # | Gap | Acceptance criteria | Map to current files | Pri |
| --- | --- | --- | --- | --- |
| 1 | Masking-tape study cards | Per-page card stores prompt/answer/revealed/O-X/score; tap-to-reveal; O/X marking and score recompute; render, PDF export and ZIP backup stay sane; legacy notebooks load unchanged; pure logic tests | `data/Notebook.kt`, `ui/NoteEditorScreen.kt`, `ui/NoteCanvas.kt`, new `ui/MaskingStudy*.kt`, tests | P1 (0.8.9) |
| 2 | PDF search next/previous | `ViewerUiState` gains current hit index; next/prev actions with wrap, page jump and "current" highlight; selection/annotation behavior preserved; pure index tests | `ui/PdfViewModel.kt`, `ui/ViewerScreen.kt`, `ui/ViewerSheets.kt`, `ui/PdfCanvas.kt`, `ui/PdfAnnotationFilter.kt` if needed, tests | P1 (0.8.9) |
| 3 | Annotation search + jump from results | Search annotation/snippet text and jump to page; page-grouped results reuse filter chips; no change to hidden/exported data | `ui/ViewerSheets.kt`, `ui/PdfViewModel.kt`, `ui/PdfAnnotationFilter.kt`, tests | P2 |
| 4 | Scribble-to-erase and circle-to-lasso (opt-in) | Gestures strictly opt-in and disabled during writing; must not conflict with pan, pinch, lasso or palm rejection; one undo step each; unit + E2E coverage | `ui/NoteGestures.kt`, `ui/NoteCanvas.kt`, `ui/NoteInk.kt`, `Note*StrokeE2ETest.kt` | P2 |
| 5 | Highlighter-only/page erase + lasso type filter | Distinct modes with safe whole-stroke default; page clear asks then is undoable; lasso can include/exclude pen/highlighter/text/image; tests per mode | `ui/NoteCanvas.kt`, `ui/NoteEditorScreen.kt`, `ui/NoteLasso.kt`, `ui/NoteInk.kt` | P2 |
| 6 | Source-rect link chips and bidirectional jump | Captured block stores source doc/page/normalized rect; chip tap jumps back; page reordering remaps ids like annotations; To/From panel unchanged | `data/Excerpt.kt`, `ui/StudyNoteBuilder.kt`, `ui/StudyNoteSourceLinks.kt`, `ui/NoteCanvas.kt` | P2 |
| 7 | Editable-original note export + folder export | Export preset offers flattened vs original layers; optional folder bundle in local ZIP; no watermark/page cap; include all sidecars in backup manifest | `pdf/NoteExporter.kt`, `pdf/PdfExporter.kt`, `data/BackupManager.kt`, `ui/DocumentStyleDialog.kt` | P2 |
| 8 | Audio playback speed + multi-doc sync | 0.5×–2× speed control and optional multi-document sync, all local; timestamps/durations preserved; replay regression tests | `audio/AudioReplayController.kt`, `audio/AudioNoteController.kt`, `ui/AudioReplaySheet.kt`, `data/Models.kt` | P3 |
| 9 | On-device handwriting search | Recognizer runs locally, never via cloud/AI SDK; indexes note ink with confidence and page result; opt-in and testable; reject any dependency that adds INTERNET | new local recognizer, `data/NoteSearch.kt`, `ui/NoteSearchSheet.kt`, `ui/NoteCanvas.kt` | P3/conditional |
| 10 | Accessibility pass | TalkBack labels on all dock/sheet controls, verified ≥48 dp targets, font-scale and dark-theme checks, keyboard/focus order; no third-party fonts | `ui/ViewerToolDock.kt`, `ui/NoteCompactLayout.kt`, `ui/ViewerSheets.kt`, `res/values/strings.xml` | P2 |

Deferred explicitly (do not schedule): accounts, cloud sync, ads/rewarded unlocks,
watermarks, export caps, web viewer/AI upload, licensed sticker/planner
marketplace, three-finger popup pinch, second-document floating popup and 4-up
phone spreads — each conflicts with the offline/IP position or is tablet-first
complexity with low phone return.

## 5. What My PDF Writer already does better

| Advantage | Evidence | Keep by |
| --- | --- | --- |
| Offline by construction | `INTERNET`/`ACCESS_NETWORK_STATE` removed in manifest; no account/ads/analytics startup; opens local library | Re-check the **merged** manifest each release; reject transitive SDKs that re-add `INTERNET` |
| No ads, accounts, cloud or paywall | Flexcil declares ad/billing/cloud SDKs and caps; Goodnotes gates audio/files by plan; we export locally with no watermark or 10-page cap | Never add rewarded ads, watermarks, page limits or sync |
| Local ZIP backup/restore | `BackupManager` manifest/count validation and path-traversal rejection, SAF, source-PDF option | Include every new card/template/audio sidecar in manifest and restore tests |
| Local audio replay | `Annotation.createdAt`/`durationMs` + `AudioReplayController` point-by-point reveal with seek, free | Preserve/reset timestamps deliberately in every ink edit; keep replay tests |
| Study links | `StudyNoteBuilder` + `StudyNoteSourceLinks` To/From index from `sourcePageIndex`; jump stays local | Keep source ids stable through moves/duplicates/transforms |
| Open source + tests | Public repo, ~352 JVM tests, E2E stroke coverage, offline HTML guide, arm64 release | Keep tests/build gates and the guide updated for each patch |
| Original code-drawn paper | 11 templates via `PaperBackground` and cover studio — no bundled third-party content | Draw new templates/cards in Compose; never import peer assets |

## 6. Recommended 0.8.9 / 0.8.10 / 0.9 sequence

- **0.8.9 (versionCode 18)** — offline-safe, high-return only: masking-tape study
  cards (#1) and PDF search next/previous (#2), if aligned with the
  `masking-study` and `reader-convenience` agents. Merge shared-file owners first,
  verify clean worktrees, run debug + unit + androidTest compile + release, keep
  no new permissions, then bump version/docs/changelog. **Neither feature exists
  in the reviewed 0.8.8 tree; do not report it as implemented until merged.**
- **0.8.10** — annotation search (#3), opt-in gesture erase/lasso (#4), eraser
  modes and lasso type filter (#5), source-rect links (#6), export/folder bundle
  (#7), and the accessibility pass (#10). Still strictly offline; on-device smoke
  test for pressure, touch and pane overlap is an exit criterion.
- **0.9 (conditional)** — per `AGENTS.md`, **do not jump to a minor version
  without explicit user approval.** Only if approved: local handwriting search
  (#9), audio speed/multi-doc (#8), and a carefully scoped second-document split.
  Re-evaluate each item against no-INTERNET/no-ads/no-cloud and IP rules; no peer
  assets, cloud AI, sync or marketplace.

## 7. Sources and limitations

- Internal: `docs/flexcil-analysis.md`, `docs/flexcil-handwriting-review.md`,
  `docs/ux-overhaul.md`, `docs/usage-guide.html`, `AGENTS.md`, and 0.8.8 source at
  `6917140`.
- Public: Goodnotes official "Information for AI Assistants" (verified
  Sept 2026), pricing/plan page, product blog (2023-08-09, 2024-09-23) and App
  Store listing — paraphrased feature descriptions only.
- Limitations: static/inferred Flexcil evidence; vendor-claimed Goodnotes
  features; no runtime testing of any third-party app; scores are judgements;
  our latency and on-device ergonomics remain unverified without an adb device.
