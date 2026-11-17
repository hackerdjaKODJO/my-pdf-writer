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
copy the text into a bug report, or `비우기` to clear it. The trace is in-memory
only and is never written to disk or sent over the network.

## 0.8.8 E2E matrix

Current JVM suite: 463 tests, 0 failures. The full case matrix, per-suite
status and on-device manual checklist are in
[`e2e-test-cases.md`](e2e-test-cases.md). Current instrumented classes include:

- `HomeStartE2ETest`
- `NoteCanvasStrokeE2ETest`
- `NoteEditorStrokeE2ETest`
- `StudyPaneE2ETest`
- `PdfCanvasSelectionE2ETest`
- `PdfSplitChromeE2ETest`
- `SearchNavigationTest`, `MaskingCardTest`, `ChecklistCardTest`, `PlaybackSpeedTest`, `TextActionsDictionaryTest`, `HelpCenterContentTest`, `OnboardingStoreTest`, `OnboardingContentTest`, `PdfContextHintsTest`, `ReaderZoomTest`, `ContinuousPinchGateTest`, `ContinuousPinchCompositionTest`, `PdfContinuousTraceTest`, `PdfTextEditTest` (JVM) cover 0.8.9–0.8.19 logic

On this Termux host no adb device/emulator is available, so these compile but
have not been executed. The user-facing guide is
[`usage-guide.html`](usage-guide.html).
