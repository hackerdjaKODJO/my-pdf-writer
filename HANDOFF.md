# PC handoff — My PDF Writer 0.8.30

This document lets another machine continue the work from the same source state.
The Android app source, tests and design notes are in Git; APKs and signing keys
are not.

## Current state

| Field | Value |
| --- | --- |
| Version | `0.8.30` / `versionCode 39` |
| Tag | `v0.8.30` |
| Release commit | `v0.8.30` tag |
| Branch | `feature/starnote` |
| `main` | same commit as `feature/starnote` |
| Package | `com.hackerdjakodjo.mypdfwriter` (release) / `.debug` |
| Remote | `https://github.com/hackerdjaKODJO/my-pdf-writer` |
| JVM tests | **646 passing** |
| On-device tests | Instrumentation APK compiles; not run (no adb device here) |
| Permissions | no `INTERNET`; `RECORD_AUDIO` is runtime-only for audio notes |

The local phone review APK is
`/sdcard/Download/MyPDFWriter-0.8.30-arm64.apk`
(SHA-256 `bfd4917a9cd8025dd2f4f9b1c4d064b5198de2b9c0429af89c612db61213ea8e`).
It is a debug-signed arm64-only APK and is not committed to Git.

## Continue on a PC

```bash
git clone https://github.com/hackerdjaKODJO/my-pdf-writer.git
cd my-pdf-writer
git checkout main
git pull

# Unit tests
./gradlew :app:testDebugUnitTest

# Arm64 debug APK (same review policy as AGENTS.md)
./gradlew :app:assembleDebug -Parm64Only=true
# app/build/outputs/apk/debug/app-arm64-v8a-debug.apk

# Instrumentation-test APK (needs a device to run)
./gradlew :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest

# R8 release build (unsigned unless keystore.properties exists)
./gradlew :app:assembleRelease
```

Requirements: JDK 17, Android SDK Platform 36, Build-Tools 36. Open the project in
Android Studio, or use the Gradle wrapper. The phone-only `.termux-build.sh` is
intentionally local and ignored; do not commit it.

`local.properties` and all keystores are ignored. The real upload key is **not**
on this phone, so release artifacts produced here are unsigned. Sign the release
on the PC/CI with the upload key.

## What is implemented (through 0.8.5)

- Offline PDF reader/annotator and PDF toolbox (organize, OCR, security, forms,
  searchable PDF, export/print/share).
- StarNote-style notebooks: templates, covers, folders, tags, favorites, trash,
  layers, bookmarks, outline, audio notes and point-level audio replay.
- PDF text selection/markup, excerpts, Google Translate handoff, TTS, Reflow,
  continuous/spread/popup reading.
- 0.8.1 UI overhaul (PDF hub/tool dock, home continue/quick actions, note dock).
- 0.8.2 multi-stroke persistence fix, bounded note trace/debug sheet, E2E
  multi-stroke tests.
- 0.8.3 stroke stabilization, opt-in shape recognition, partial eraser, lasso
  transform/clipboard, PDF annotation hide/filter.
- 0.8.4 study-note bottom pane, pressure-sensitive fountain/brush ink, Template
  Studio (A4 orientation/tint/cover), note page manager and export presets.
- 0.8.5 custom image and PDF-first-page backgrounds (backup-included), resizable
  study pane with drop capture and To/From source links, pressure curves and
  two/three-finger undo/redo, note page rotation, photo pages/backgrounds, note
  search.
- 0.8.6 simplified PDF hub (7 primary tools + full expansion), forgiving tap/drag
  text selection with a dedicated dock action, direct PDF top-bar `노트` entry,
  and a focused 85% study pane with a compact 6-tool note dock and larger canvas.
- 0.8.7 reclaims split-screen height: the PDF top/bottom bars disappear while
  the study pane is open; back/page/text-selection/bookmark/tools/hub float over
  the PDF strip. The note pane uses a floating header and a translucent floating
  six-tool dock, so the canvas fills the pane.

Start with:

- `AGENTS.md` — version policy, build/agent rules, release checklist
- `README.md` — features, architecture, build, privacy
- `docs/flexcil-handwriting-review.md` — feature matrix and remaining gaps
- `docs/template-studio.md`
- `docs/ink-pressure-and-gestures.md`
- `docs/note-page-tools.md`
- `docs/testing.md`
- `docs/usage-guide.html` — offline user guide
- `docs/commercial-tool-review.md` — Flexcil/GoodNotes behavioral review
- `docs/masking-cards.md` — study card usage
- `docs/flexcil-apk-feature-ledger.md` — APK static evidence ledger
- `docs/ux-review-0813.md` — UX journey and top friction points
- `docs/e2e-test-cases.md` — case matrix, execution status and device checklist

## Known limitations / next work (0.8.6+)

1. **Physical pressure calibration** — software curves (soft/linear/firm) are
   implemented, but hardware pressure range, dead zone and linearity have not
   been tuned against a real tablet. Add a calibration flow and test with a
   pressure-sensitive stylus.
2. **0.8.8 split divider** — compile/pure tests pass, but the 18dp grip and
   floating toolbar overlap, tap/drag selection and compact handwriting ergonomics
   still need a phone smoke test; instrumentation tests are not executed without adb.
3. **Direct canvas-to-pane drag** — the guaranteed capture paths are the
   selection-bar `패널에 추가` action and dragging the selected-text summary to
   the pane header. Direct drag from canvas text and source-rectangle backlinks
   remain.
4. **Custom PDF templates** — first page only; add page selection for PDF
   imports.
5. **Photo insertion** — implemented as a photo page or current-page background;
   inline movable/resizable photo annotations are not implemented.
6. **Note search** — searches memos, text, stamps and signatures; handwriting
   strokes/OCR/audio are not indexed.
7. **Page rotation** — ±90° only; arbitrary-angle page rotation is not supported.
8. **Resizable study pane** — height is session-only; direct drag-and-drop and
   window/process restoration can be improved.
9. **On-device verification** — no adb device was attached. Install the arm64
   APK and smoke-test SAF template import, photo import, stylus pressure,
   multi-finger undo/redo, pane resize/capture, page rotation and export.
10. **Release signing** — configure `keystore.properties` on the PC/CI; never
   commit it.

## Version policy reminder

Patch releases continue as `0.8.6`, `0.8.7`, … with `versionCode` +1 for each
delivered build. Do not jump to `0.9.0`/`1.0.0` without explicit approval. The
default review artifact is arm64-only; build universal only when requested for
32-bit ARM/x86 compatibility. See `AGENTS.md`.
