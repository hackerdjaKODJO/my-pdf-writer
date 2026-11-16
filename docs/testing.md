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

Drop `-Parm64Only=true` for a universal APK and install
`app/build/outputs/apk/debug/app-debug.apk` instead.

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
