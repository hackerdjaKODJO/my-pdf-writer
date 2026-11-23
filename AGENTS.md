# AGENTS.md — My PDF Writer

This file is the operating contract for human and AI agents working on this
repository. Read it before changing code.

## Project identity

- App: **My PDF Writer** — offline PDF reader/annotator/workbench + StarNote-like
  notebooks.
- Package: `com.hackerdjakodjo.mypdfwriter` (release) /
  `com.hackerdjakodjo.mypdfwriter.debug` (debug).
- Repo: `https://github.com/hackerdjaKODJO/my-pdf-writer`
- Main branch: `main`; feature branch for the current workbench line:
  `feature/starnote`.
- The app is **offline**: do not add `INTERNET`, cloud sync, accounts, ads,
  analytics, or a cloud AI SDK. `RECORD_AUDIO` is only for the local audio-note
  recorder and must be requested at runtime when recording starts.
- Do not copy StarNote, Flexcil, All PDF Reader, MyReader, PF Brief, or other
  third-party code/assets/fonts/templates/covers/branding. Behavioral/UX
  references are documented in `docs/starnote-reverse-engineering.md`,
  `docs/flexcil-analysis.md`, and `docs/all-pdf-reader-analysis.md`.

## Versioning policy (required)

Use a **three-part semantic version: `MAJOR.MINOR.PATCH`**. The version string
must always have two decimal points.

- Current delivered version: **`0.8.68`** — `versionCode = 77`.
- **Next version is `0.8.69` — `versionCode = 78`.**
- For every subsequent app delivery, increment the **PATCH** component by one:
  `0.8.1`, `0.8.2`, `0.8.3`, … Do **not** jump to `0.9.0` or `1.0.0` unless the
  user explicitly approves a new minor/major milestone.
- `versionCode` is a monotonically increasing integer and must be incremented by
  exactly 1 for every build delivered to the phone/Play Console. Never reuse a
  versionCode.
- A patch release may contain one or more user-visible fixes/features; do not
  skip a patch number because a change is small.
- Update all of these in the same commit:
  1. `app/build.gradle.kts` — `versionCode` and `versionName`
  2. `README.md` — displayed version and build-size/test notes when relevant
  3. this `AGENTS.md` — changelog row below
- **Default APK policy: arm64-only.** Build and publish the smaller
  `MyPDFWriter-<version>-arm64.apk` (only `lib/arm64-v8a`). Do not build or copy a
  universal APK unless the user explicitly asks for 32-bit ARM/x86 emulator
  compatibility. The universal APK has the same code/features; it only adds
  duplicate native libraries for other CPU architectures.
- A version is not “delivered” until `assembleDebug`, `testDebugUnitTest`, and
  (for release candidates) `assembleRelease` pass, the APK is copied to
  Downloads, and the commit is pushed.
- Never commit release binaries, `*.jks`, `*.keystore`, `keystore.properties`, or
  `local.properties`. The release upload key is not stored on this phone.

## Version changelog

| Version | versionCode | Date | Commit | Summary |
| --- | --- | --- | --- | --- |
| `0.8.68` | 77 | 2026-11-23 | `v0.8.68` | Five-agent full code review: fix undo snapshot notes/cards/bookmarks, docstate writer serialization + tmp recovery, SEND_MULTIPLE recents, PDF pressureInk sharing, note highlight presets, 4 stale E2E tests, bounded stationary samples and several lifecycle/IO fixes; 4 JVM tests |
| `0.8.67` | 76 | 2026-11-23 | `v0.8.67` | Four-agent paper-feel pressure pass: stationary-nib pressure samples, shared PDF calibration, speed-aware ink deposit and nib-point terminals, pressure-aware pen swatches, persisted pressureInk, TalkBack curve state, truthful help; 5 JVM tests |
| `0.8.66` | 75 | 2026-11-23 | `v0.8.66` | Four-agent intuitive-UI overhaul: study pane keeps text-select/annotate tools, finger-writing one-tap toggle, numeric page jump, page vocabulary unified, Back closes pane first, Tools source-first, truthful gestures and visible drag affordance; 3 JVM tests |
| `0.8.65` | 74 | 2026-11-23 | `v0.8.65` | Fix overlapping page-turn and zoom pills in paged and continuous PDF viewers: shared measured bottom area stacks them on narrow phones, side-by-side on tablets; 1 JVM test |
| `0.8.64` | 73 | 2026-11-23 | `v0.8.64` | Four-agent commercial UX/quality audit: consistent Back/selection, bookmark wording and dismiss labels, scrollable sheets, 48dp studio, readable dock labels, SEND_MULTIPLE + corrupt-PDF handling, failed-open keeps current doc, TalkBack long-press action and masking-card states, cancellable photo import; 6 JVM tests |
| `0.8.63` | 72 | 2026-11-23 | `v0.8.63` | Three-agent PDF text-selection performance pass: dedupe per-frame selection publishes, debounce ViewModel mirror, isolate selection overlay into its own Canvas, prefilter gesture words; 5 JVM + 1 E2E tests |
| `0.8.62` | 71 | 2026-11-23 | `v0.8.62` | Three-agent pen-illustration pass: realistic ballpoint/fountain/brush/pencil drawings (clip, clicker, gold nib, breather hole, ferrule, bristles, graphite, eraser) in both PDF and note pickers; pure geometry spec; 7 JVM + 1 E2E tests |
| `0.8.61` | 70 | 2026-11-23 | `v0.8.61` | Four-agent commercial UX/accessibility pass: surface silent import errors as Home snackbar, ACTION_VIEW PDFs join recents for resume, persisted per-document zoom, debounced notebook page resume, page-aware TalkBack pane titles, 48dp overlay targets + spoken states; 7 JVM + 2 E2E tests |
| `0.8.60` | 69 | 2026-11-23 | `v0.8.60` | Three-agent long-press dock review: pen/highlight long press opens that tool's style sheet in PDF + note; highlighter width stored separately so pen width is never corrupted; 5 JVM + 3 E2E tests |
| `0.8.59` | 68 | 2026-11-23 | `v0.8.59` | Three-agent unified pen settings: shared InkSettingsStore (pdf+note), graphic pen-style glyphs in both style sheets, four default pen styles guaranteed, 12 JVM tests |
| `0.8.58` | 67 | 2026-11-23 | `v0.8.58` | Three-agent PDF tools review: 8 JVM functional tests + 5 ToolsScreen E2E tests; fix text output "열기", permission-only PDF gating, header-only "no text" detection, range clamp preview; 5 JVM regressions |
| `0.8.57` | 66 | 2026-11-23 | `v0.8.57` | Two-agent UI consistency + accessibility pass: page pill TalkBack action fix, expandable-header state, page-row/slider/sheet semantics, 48dp sheet targets, dock/color/help vocabulary aligned; 4 JVM tests |
| `0.8.56` | 65 | 2026-11-23 | `v0.8.56` | Three-agent PDF editing review: run heavy tools off Main, prefer edited working file in Tools handoff, select/unlock password-protected PDFs, clear stale tool outputs, parse link targets once per page revisit, preview resolved page ranges; 4 JVM tests |
| `0.8.55` | 64 | 2026-11-23 | `v0.8.55` | Three-agent full note review: fix audio replay start time, bookmark remap on single-page delete/move, locked-layer selection gate, study-card pen hit-test, card rect clamping, opacity undo flood, multi-page delete confirmation; text-box editing and card-content search; 9 JVM tests |
| `0.8.54` | 63 | 2026-11-23 | `v0.8.54` | Three-agent latent-bug review: flush PDF saves before backup/restore, keep study-pane page on capture, preserve lastPageIndex across editor saves, cancel stale reading saves, durable preference writes and restore fsync, fix stamp crash/replay off-by-one/cache monitor mismatch/stale-zoom swipe; 5 JVM tests |
| `0.8.53` | 62 | 2026-11-22 | `v0.8.53` | Three-agent simplicity review vs Flexcil/GoodNotes: remove redundant tool picker, duplicate dismiss/hub/menu entries, two-tier ink settings, hide filters on empty library, collapse create-dialog options; 3 JVM tests |
| `0.8.52` | 61 | 2026-11-22 | `v0.8.52` | Open-source ink review (three agents, perfect-freehand/libmypaint): full-press calibration + bootstrap, lifted-nib contact gate, deterministic paper tooth, layered sheet shadow, granular graphite, one ink-width floor; 7 JVM tests |
| `0.8.51` | 60 | 2026-11-22 | `v0.8.51` | Four-agent UX benchmark against Flexcil/GoodNotes (official sites): make 노트에 담기 really open the note, one-tap note search, notebook last-page resume, clearer menu labels, benchmark doc; 1 JVM regression |
| `0.8.50` | 59 | 2026-11-22 | `v0.8.50` | Three-agent performance pass: cache derived ink contexts per annotation, cache PDF text layouts, merge saves into the in-memory library instead of rescanning, defer cold-start library parse, skip cache-hit mutex/redundant version bump/per-entry fsync, hoist per-frame page filters, disable release tracing; 5 JVM regressions |
| `0.8.49` | 58 | 2026-11-22 | `v0.8.49` | Four-agent UX pass: one-tap add-page pill and note help, truthful first-run card, filtered PDF empty-state action, single-channel study-note feedback, consistent filter reset, 48dp overlay targets + state labels, help/guide corrected and new viewer features documented; 2 JVM + 2 test guard updates |
| `0.8.48` | 57 | 2026-11-22 | `v0.8.48` | Commercial ink feel: pen-style renderers restored for stylus strokes (no more generic outline), WYSIWYG note preview shares commit geometry, PDF captures stylus pressure and pen style in preview+commit; 7 JVM regressions |
| `0.8.47` | 56 | 2026-11-22 | `v0.8.47` | Play closed-beta delivery bundling the user-first PDF viewer UX pass (horizontal swipe page turn at fit zoom, one-tap ± zoom pill, Home help button, export promoted to the first-level settings row, stale help/guide copy corrected, quick cards relabelled as disclosures) with the v0.8.43–0.8.45 zoom/pan fixes (re-clamp pan on every zoom/viewport change, pivot-preserving zoom, 25% headroom band, double-tap fit↔200% toggle) across both continuous and single-page viewers; 3 JVM + 2 E2E regressions |
| `0.8.46` | 55 | 2026-11-22 | `v0.8.46` | UX pass from three user-journey agents: horizontal swipe page turning in both viewers at fit zoom, one-tap ± zoom pill, Home help button, export promoted to the first-level settings row, stale help/guide paths corrected, quick cards relabelled as disclosures; 3 JVM + 2 E2E regressions |
| `0.8.45` | 54 | 2026-11-22 | `v0.8.45` | Fix zoom-then-pan in BOTH viewers: 25% headroom band keeps an edge-focal zoom from pinning pan at a clamp edge (continuous), single-page double tap now toggles fit↔200% and pinch/double-tap pan keeps a reserved band; 3 JVM + 4 E2E regressions |
| `0.8.44` | 53 | 2026-11-22 | `v0.8.44` | Fix continuous-reader right pan after zoom: pivot-preserving zoom re-anchors pan around the pinch/double-tap focal so the page has headroom both ways; add pan/zoomPan debug traces + HUD, 4 JVM + 2 E2E regressions |
| `0.8.43` | 52 | 2026-11-22 | `v0.8.43` | Fix continuous-reader left pan after zoom: re-clamp pan on every zoom/viewport change (no invisible dead zone), remove sticky multi-touch latch so the retained finger can pan, unify max-pan math, add 6 JVM + 3 E2E regressions |
| `0.8.42` | 51 | 2026-11-22 | — | Full-flow E2E (104/104 pass): real PDF open/annotate/page-ops/search/export/reopen, backup+restore, notebook disk round-trip; fix bookmark remap and restored-source reuse |
| `0.8.41` | 50 | 2026-11-22 | — | PC emulator E2E run (86/86 pass): fix 15 flaky cases, release backup gate on throwing flush with regression test |
| `0.8.40` | 49 | 2026-11-22 | — | Play closed-beta release build from latest main |
| `0.8.39` | 48 | 2026-11-22 | `v0.8.39` | E2E expansion: 48 new instrumentation cases across PDF, notes, data/backup/help |
| `0.8.38` | 47 | 2026-11-22 | `v0.8.38` | Latent bug pass: save/open/export races, pressure calibration, crop clip, backup/notebook concurrency, replay parity |
| `0.8.37` | 46 | 2026-11-22 | `v0.8.37` | Multi-app review: 4-group PDF hub, unified 위치, 4-button chrome, coach card, one primary home action, 60-second help |
| `0.8.36` | 45 | 2026-11-21 | `v0.8.36` | GoodReader parity: Locations panel, annotation summary export, document tabs/recents, auto-hide chrome, view-only crop margins |
| `0.8.35` | 44 | 2026-11-21 | `v0.8.35` | Original Kotlin ink refinement: variable-width outline, lazy brush/RDP/Chaikin, arc graphite, pressure range calibration |
| `0.8.34` | 43 | 2026-11-21 | `v0.8.34` | Latent bug/performance pass: undo/save/search races, split search, back handling, backup rollback, IO offloading, ink dedupe |
| `0.8.33` | 42 | 2026-11-21 | `v0.8.33` | Student-first redesign: Home segments + quick study, 6-slot PDF study dock, 학습 메뉴, single-row note dock, clearer vocabulary |
| `0.8.32` | 41 | 2026-11-21 | `v0.8.32` | Faster PDF open (single documentInfo, identity skip assemble, source cache), PDF post-it cards, obvious card close |
| `0.8.31` | 40 | 2026-11-21 | `v0.8.31` | Realistic pen dynamics: ballpoint dry skipping, fountain nib taper, per-point pencil graphite, neutral legacy compatibility |
| `0.8.30` | 39 | 2026-11-21 | `v0.8.30` | Accessibility pass: 48dp targets, state labels, accessible split divider, large-font/320dp layout policy, headings/paneTitle, contrast |
| `0.8.29` | 38 | 2026-11-18 | `v0.8.29` | Per-style ink response profiles (flow/grain/width), full-stroke replay stability, active-stylus pressure sampling |
| `0.8.28` | 37 | 2026-11-18 | `v0.8.28` | Split divider with visible percentage/presets, top PDF chrome with density policy, note toolbox hide/restore toggle |
| `0.8.27` | 36 | 2026-11-18 | `v0.8.27` | Samsung Notes post-it memo cards with export; Flexcil-style favorite pen registry and one-tap slots in note/PDF |
| `0.8.26` | 35 | 2026-11-18 | `v0.8.26` | Whole-app menu redesign: Home filter/settings sheets, grouped PDF menu, 6-slot reader dock, markup-first selection bar, note menu reduced to focused sub-sheets |
| `0.8.25` | 34 | 2026-11-17 | `v0.8.25` | GoodReader iOS survey applied offline: persisted display prefs + brightness, page lock, internal PDF links, ACTION_SEND PDF import, Home sort menu |
| `0.8.24` | 33 | 2026-11-17 | `v0.8.24` | QA follow-up: empty-PDF search state, scope-preserving filter reset, tag clear accessibility, defensive saved-enum restore, shape icon/label sync |
| `0.8.23` | 32 | 2026-11-17 | `v0.8.23` | Flexcil-pattern menu pass: persistent create FAB, saveable home tab/filter state, PDF recents first, tag search, PDF menu rename and unique primary set, active dock tool labels, Tools system-back |
| `0.8.22` | 31 | 2026-11-17 | `v0.8.22` | Edge-to-edge zoom panning to both page edges; stable old bitmap during zoom re-render (no flicker) |
| `0.8.21` | 30 | 2026-11-17 | `v0.8.21` | Layout-phase horizontal pan offset (no per-frame recomposition); diagonal drag pans X while LazyColumn scrolls Y; tap suppression |
| `0.8.20` | 29 | 2026-11-17 | `v0.8.20` | Continuous page actually grows past viewport (`requiredWidth`); raw Initial-pass pinch tracking avoids awaitEachGesture down-consumption |
| `0.8.19` | 28 | 2026-11-17 | `v0.8.19` | Initial-pass calculateZoom pinch with pointer-count gate; native transform replaced |
| `0.8.18` | 27 | 2026-11-17 | `v0.8.18` | Initial-pass pinch tracking, stale-lambda/render/cache key fixes, PDF_CONTINUOUS trace |
| `0.8.17` | 26 | 2026-11-17 | `v0.8.17` | Continuous pinch re-renders page bitmaps at zoom-dependent scale; debug render logs |
| `0.8.16` | 25 | 2026-11-17 | `v0.8.16` | Reliable continuous pinch: tracked pointer ids, outer-box handler, single-finger scroll preserved |
| `0.8.15` | 24 | 2026-11-17 | `v0.8.15` | Continuous reader pinch zoom with document-wide zoom/pan shared across pages |
| `0.8.14` | 23 | 2026-11-17 | `v0.8.14` | Compact/zoomable continuous reader and visual selected-text replacement |
| `0.8.13` | 22 | 2026-11-17 | `v0.8.13` | UX review, first-run onboarding, contextual PDF hints and search empty state |
| `0.8.12` | 21 | 2026-11-17 | `v0.8.12` | Flexcil APK resource evidence: interactive checklists, audio playback speed, external dictionary handoff |
| `0.8.11` | 20 | 2026-11-17 | `v0.8.11` | Always copy selected text to clipboard before Google Translate; ACTION_PROCESS_TEXT handoff |
| `0.8.10` | 19 | 2026-11-17 | `v0.8.10` | In-app help center, long-press PDF word selection, verified by repeated build/test cycles |
| `0.8.9` | 18 | 2026-11-17 | `v0.8.9` | Commercial-quality review; offline masking study cards and PDF search next/previous navigation |
| `0.8.8` | 17 | 2026-11-17 | `v0.8.8` | Draggable PDF/note boundary divider for direct split-height adjustment |
| `0.8.7` | 16 | 2026-11-17 | `v0.8.7` | Split PDF/note space reclamation: floating PDF chrome and compact floating note header/dock |
| `0.8.6` | 15 | 2026-11-17 | `v0.8.6` | Simplified PDF hub, restored tap/drag text selection, direct note entry, focused 85% study pane with compact note dock |
| `0.8.5` | 14 | 2026-11-16 | `v0.8.5` | Custom image/PDF templates, resizable study pane with drop capture/source links, pressure curves/gestures, page rotation/photo pages/note search |
| `0.8.4` | 13 | 2026-11-16 | `v0.8.4` | PDF study-note pane, stylus pressure ink, template studio (orientation/tint/cover), note page manager/export presets |
| `0.8.3` | 12 | 2026-11-16 | `v0.8.3` | Flexcil review: stroke stabilization, smart shapes, partial eraser, lasso transform/clipboard, PDF annotation hide/filter |
| `0.8.2` | 11 | 2026-11-16 | `v0.8.2` | Fix multi-stroke note loss, note trace debugger, multi-stroke E2E regression tests |
| `0.8.1` | 10 | 2026-11-16 | `v0.8.1` | PDF viewer hub/tool dock, home continue/quick actions, note editor style dock |
| `0.8.0` | 9 | 2026-11-16 | `95a4a87` | Point-level audio stroke replay, search-result highlights, local ZIP backup/restore |
| `0.7.0` | 8 | 2026-11-16 | `d7673a8` | Study-note drag & drop, audio timeline playback with whole-stroke reveal |
| `0.6.0` | 7 | 2026-11-16 | `8dfe4bc` | PDF canvas text selection/markup, study notes, 2/4-page spread, popup, local audio notes |
| `0.5.0` | 6 | 2026-11-16 | `6c999ad` | Reading progress/bookmarks, continuous reader, Reflow, system TTS |
| `0.4.1` | 5 | 2026-11-16 | `dcc24e1` | Google Translate handoff and target-language selection |

## Build and test on this phone

The Termux build wrapper (local-only, gitignored) is:

```bash
cd ~/my-pdf-writer
# Default review build: small arm64-only APK
./.termux-build.sh :app:assembleDebug -Parm64Only=true
./.termux-build.sh :app:testDebugUnitTest
./.termux-build.sh :app:assembleDebugAndroidTest
./.termux-build.sh :app:assembleRelease
# Universal APK ONLY when the user asks for 32-bit/x86 compatibility.
# It is the same app plus duplicate native libs for other ABIs.
./.termux-build.sh :app:assembleDebug
```

The phone SDK lives at `~/android-sdk`; the setup is documented in
`~/android-sdk/TERMUX-SETUP.md`. Do not edit the SDK or `local.properties`.
Release R8 builds are unsigned locally; download/sign with the real upload key
outside this phone.

## Agent/worktree rules

- Independent features use separate git worktrees/branches and are merged into
  `feature/starnote` (then fast-forwarded to `main`) only after their build and
  unit tests pass.
- Merge order: shared-file owners first (Viewer/PdfViewModel/PdfCanvas/Models),
  then new-file-only agents.
- Before merging a worktree, check `git -C <worktree> status --short` is clean.
- Remove merged worktrees and delete merged `work/*` branches after the push.
- Every feature change must include a test when it is pure logic, and a build
  verification note in the final report.

## Code conventions

- Kotlin + Jetpack Compose, single `:app` module; no Navigation/Room/Hilt.
- PDFBox-Android for PDF operations; Android `PdfRenderer` for page bitmaps;
  Compose Canvas + `AnnotationPainter` for ink.
- Note data: `Notebook`/`NotePage` JSON under `files/notebooks/`; PDF page state
  under `files/docstates/`; excerpts/reading/audio/styles have their own
  sidecars.
- `Annotation` coordinates are normalized 0..1; keep `createdAt` and
  `durationMs` for audio replay.
- New UI strings in Korean literals for new screens; existing UI uses
  `strings.xml`.
- Note persistence changes should use the synchronous working-notebook pattern in
  `NoteEditorScreen` and emit `NoteTrace` events. Stroke loss has E2E coverage in
  `app/src/androidTest/.../Note*StrokeE2ETest.kt`; compile it with
  `assembleDebugAndroidTest` when a change affects note input.
- Keep the existing PDF/OCR/bookmark/reflow/TTS/search/backup behavior intact
  when adding features.

## Release checklist

1. Bump PATCH version and versionCode per policy.
2. Run debug build, unit tests, Android-test compilation, and release build.
3. Verify APK permissions: no `INTERNET`; `RECORD_AUDIO` only for audio.
4. Copy the arm64 APK to `~/Downloads` and `/sdcard/Download/` with the
   versioned filename. Do not copy a universal APK unless explicitly requested.
5. Update README and this changelog.
6. Commit, push `feature/starnote`, fast-forward `main`, push `main`.
