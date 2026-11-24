# Testing

Unit tests run on the JVM:

```bash
./.termux-build.sh :app:testDebugUnitTest
```

## Android instrumentation (E2E)

The note-stroke regression tests are instrumentation tests:

- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/ui/NoteCanvasStrokeE2ETest.kt`
  draws two quick finger strokes on `NoteCanvas` and asserts both annotations
  stay in the page state.
- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/ui/NoteEditorStrokeE2ETest.kt`
  enables finger writing in the note overflow sheet, draws two strokes, then
  checks the latest `Notebook` from `onChange` and its `toJson()`/`fromJson()`
  round-trip.

Both tests address the canvas through `Modifier.testTag("note_canvas")`, which is
part of the note-stroke fix.

Three more instrumentation classes cover the data/backup/home/help fixes:

- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/ui/HomeAndHelpE2ETest.kt`
  uses `createAndroidComposeRule<ComponentActivity>()` for the home primary
  action (continue vs. import), the two-button quick row, the three-card
  onboarding and the help centre (four ordered quick cards, `더보기` legacy
  topics, quick-action routing). Its plain-JUnit content check rejects stale
  help/onboarding vocabulary.
- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/ui/BackupRestoreE2ETest.kt`
  races two restore results through `BackupOperationGate`, pins the
  flush-before-archive order, and checks `onBackupRestored` ordering and
  `shouldCloseAfterRestore`; it also drives `BackupSheet` while busy and through
  the restore confirmation.
- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/data/DocumentSwitchE2ETest.kt`
  is pure JUnit for per-docId save queues, rapid-double-open generation tokens,
  and SAF export snapshots bound to the document captured when the picker opens.

The HTML guide (`docs/usage-guide.html`) is a repo file rather than an app asset,
so it cannot be read from an on-device `androidTest`. Its `고급`/`스터디` absence
and the `켜져 있는지` (not `꺼져 있는지`) wording are asserted by the JVM-only
`UsageGuideContentTest`; `HomeAndHelpE2ETest` adds `발췌로 저장` to the same
vocabulary guard over the in-app help/onboarding copy derived from the guide.

Build the test APK (no device required):

```bash
./.termux-build.sh :app:assembleDebugAndroidTest
```

Build, install and run on an attached device or emulator:

```bash
./.termux-build.sh :app:assembleDebug -Parm64Only=true
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Normal arm64 phones and the default review build use the arm64 APK only.
Build a universal APK only for a 32-bit ARM or x86 emulator, and install
`app/build/outputs/apk/debug/app-debug.apk` in that case.

When a device is already connected, Gradle can build, install and run in one
step:

```bash
./.termux-build.sh :app:connectedDebugAndroidTest
```

Run only the note-stroke E2E classes with:

```bash
adb shell am instrument -w \
  -e class com.hackerdjakodjo.mypdfwriter.ui.NoteCanvasStrokeE2ETest,com.hackerdjakodjo.mypdfwriter.ui.NoteEditorStrokeE2ETest \
  com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## Note trace debugger

Debug builds expose two local trace viewers:

- Home overflow (`⋮`) → `노트 추적`
- Note editor overflow (`더보기`) → `디버그` → `필기 추적`

The sheet shows `NoteTrace` entries (stroke commit counts, editor apply/undo,
ViewModel update/save timings, and annotation-count decreases). Use `복사` to
copy the text into a bug report, or `비우기` to clear it. The `NoteTrace` ring
itself is in-memory only and is never sent over the network. Since 0.8.88 those
same events are also mirrored into the durable, on-disk developer log described
below, so a trace can still be read after the process exits.

## Durable developer log (0.8.88+)

Debug builds write a structured developer log to disk so a trace survives a
process exit or Activity recreation. `debug/DevLogStore.kt` appends one JSON
object per line (JSONL) to `files/logs/devlog-YYYY-MM-DD.jsonl` in the app's
private files directory: one file per day, with at most 7 daily files retained.
Each file is capped at 1 MB; when the cap is reached the store rolls to a
`-partN` sibling in the same directory. Writes are also bounded in memory by a
1200-entry ring buffer.

Each event is a single JSON line with a session id (8 hex characters), a
monotonically increasing `seq`, the wall-clock time in epoch milliseconds plus
an ISO-8601 UTC timestamp, the emitting thread, a `component`, an `event` name,
and string fields. A `session_start` event is emitted on the first write of a
session and again after `비우기`/clear, so the session id and seq can be used to
order events across a restart. `pdf/PdfFlowTrace.kt` writes typed `PDF_FLOW`
events: `open_start`, `open_done`, `open_fail`, `close`, `annotate`, `history`,
`page_edit`, `search`, `export_start` and `export_done`.

The log is **debug-only**: release builds are no-ops (no file, no ring buffer,
no cost). It is also privacy-safe: only counts, opaque ids, kinds and timings
are recorded — never PDF text, file paths, content URIs or user data. Every
event is mirrored into `NoteTrace` and to logcat with tag `PdfWriterDev`.

Reference the log in any of three ways:

1. Live from logcat (tag `PdfWriterDev`):

   ```bash
   adb logcat -s PdfWriterDev
   ```

2. Pull today's JSONL file directly off a debuggable build:

   ```bash
   adb exec-out run-as com.hackerdjakodjo.mypdfwriter.debug cat files/logs/devlog-$(date +%F).jsonl
   ```

   `run-as` works only for a debuggable debug build. On a non-debuggable release
   build it will not work — use the in-app trace sheet (option 3) there instead.

3. The in-app debug sheet `노트 추적` (Home overflow `⋮` → `노트 추적`), which
   shows the `NoteTrace` mirror of the same events. Use `복사` to paste a report
   or `비우기` to clear it (clearing emits a fresh `session_start`).

E2E tests assert on the disk log, not just the in-memory trace:
`DevLogE2ETest` reads the JSONL file from the app's private storage and checks
the session header fields, that entries survive Activity recreation, and that
no PDF text appears. `PdfFlowTraceE2ETest` drives a real PDF through
open → annotate → page edit → search → export → close and checks that the
`PDF_FLOW` events are on disk in that order, plus `open_fail` for a failed open.

### New test classes for the durable log (0.8.88+)

JVM tests (run with `./.termux-build.sh :app:testDebugUnitTest`):

- `app/src/test/java/com/hackerdjakodjo/mypdfwriter/debug/DevLogTest.kt`
- `app/src/test/java/com/hackerdjakodjo/mypdfwriter/pdf/PdfFlowTraceTest.kt`

Instrumentation tests:

- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/e2e/DevLogE2ETest.kt`
- `app/src/androidTest/java/com/hackerdjakodjo/mypdfwriter/e2e/PdfFlowTraceE2ETest.kt`

Run only these two instrumentation classes with:

```bash
adb shell am instrument -w \
  -e class com.hackerdjakodjo.mypdfwriter.e2e.DevLogE2ETest,com.hackerdjakodjo.mypdfwriter.e2e.PdfFlowTraceE2ETest \
  com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## 0.8.68 E2E matrix

Current JVM suite: 879 tests, 0 failures. Instrumentation suite: 85 cases, compiled. The full case matrix, per-suite
status and on-device manual checklist are in
[`e2e-test-cases.md`](e2e-test-cases.md). Current instrumented classes include:

- `HomeStartE2ETest`
- `NoteCanvasStrokeE2ETest`
- `NoteEditorStrokeE2ETest`
- `StudyPaneE2ETest`
- `PdfCanvasSelectionE2ETest`
- `PdfSplitChromeE2ETest`
- `SearchNavigationTest`, `MaskingCardTest`, `ChecklistCardTest`, `PlaybackSpeedTest`, `TextActionsDictionaryTest`, `HelpCenterContentTest`, `OnboardingStoreTest`, `OnboardingContentTest`, `PdfContextHintsTest`, `ReaderZoomTest`, `ContinuousPinchGateTest`, `ContinuousPinchCompositionTest`, `PdfContinuousTraceTest`, `PdfTextEditTest`, `HomeLibraryPolicyTest`, `ViewerToolDockTest`, `IntentImportPolicyTest`, `PdfDisplayPrefsTest`, `PageLockTest`, `PdfLinksTest`, `HomeFilterStateTest`, `NoteEditorMenuTest`, `ToolsScreenSectionsTest`, `MemoCardTest`, `MemoCardOverlayTest`, `MemoCardsSheetTest`, `NoteCardPainterTest`, `FavoritePensTest`, `FavoritePenStripTest`, `InkResponseTest`, `AnnotationPainterPolicyTest`, `NotePressureCaptureTest`, `AccessibilityPolicyTest`, `AccessibilityLayoutTest`, `PenStrokeGeometryTest`, `PdfRepositoryTest`, `PdfMemoCardLayerTest`, `PdfViewModelMemoTest`, `QuickStudySheetTest` (JVM) cover 0.8.9–0.8.39 logic

On this Termux host no adb device/emulator is available, so these compile but
have not been executed. The user-facing guide is
[`usage-guide.html`](usage-guide.html).
