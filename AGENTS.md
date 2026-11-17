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

- Current delivered version: **`0.8.24`** — `versionCode = 33`.
- **Next version is `0.8.25` — `versionCode = 34`.**
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
