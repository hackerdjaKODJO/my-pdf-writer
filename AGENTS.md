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

- Current delivered version: **`0.8.0`** — `versionCode = 9`.
- **Next version is `0.8.1` — `versionCode = 10`.**
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
- The debug artifact name is:
  `MyPDFWriter-<version>-arm64.apk` (preferred, arm64-only) and
  `MyPDFWriter-<version>-universal.apk` (all ABIs).
- A version is not “delivered” until `assembleDebug`, `testDebugUnitTest`, and
  (for release candidates) `assembleRelease` pass, the APK is copied to
  Downloads, and the commit is pushed.
- Never commit release binaries, `*.jks`, `*.keystore`, `keystore.properties`, or
  `local.properties`. The release upload key is not stored on this phone.

## Version changelog

| Version | versionCode | Date | Commit | Summary |
| --- | --- | --- | --- | --- |
| `0.8.0` | 9 | 2026-11-16 | `95a4a87` | Point-level audio stroke replay, search-result highlights, local ZIP backup/restore |
| `0.7.0` | 8 | 2026-11-16 | `d7673a8` | Study-note drag & drop, audio timeline playback with whole-stroke reveal |
| `0.6.0` | 7 | 2026-11-16 | `8dfe4bc` | PDF canvas text selection/markup, study notes, 2/4-page spread, popup, local audio notes |
| `0.5.0` | 6 | 2026-11-16 | `6c999ad` | Reading progress/bookmarks, continuous reader, Reflow, system TTS |
| `0.4.1` | 5 | 2026-11-16 | `dcc24e1` | Google Translate handoff and target-language selection |

## Build and test on this phone

The Termux build wrapper (local-only, gitignored) is:

```bash
cd ~/my-pdf-writer
./.termux-build.sh :app:assembleDebug
./.termux-build.sh :app:testDebugUnitTest
./.termux-build.sh :app:assembleRelease
# arm64-only review APK:
./.termux-build.sh :app:assembleDebug -Parm64Only=true
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
- Keep the existing PDF/OCR/bookmark/reflow/TTS/search/backup behavior intact
  when adding features.

## Release checklist

1. Bump PATCH version and versionCode per policy.
2. Run debug build, unit tests, and release build.
3. Verify APK permissions: no `INTERNET`; `RECORD_AUDIO` only for audio.
4. Copy to `~/Downloads` and `/sdcard/Download/` with the versioned filename.
5. Update README and this changelog.
6. Commit, push `feature/starnote`, fast-forward `main`, push `main`.
