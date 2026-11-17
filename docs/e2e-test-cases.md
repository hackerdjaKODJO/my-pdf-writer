# E2E test cases — My PDF Writer 0.8.8

The app is intentionally offline. These tests run on a connected Android device
or emulator; the build machine has no adb device attached, so only JVM tests are
executed here. Instrumentation APKs are compiled with
`:app:assembleDebugAndroidTest`.

## Current results

| Suite | Count | Status |
| --- | --- | --- |
| JVM unit tests | 368 | **Passing** on this phone |
| Instrumentation/E2E | 6 classes (see below) | **Compiled**; on-device run pending |
| Debug build | 1 | Passing |
| R8 release build | 1 | Passing (unsigned locally) |

## E2E cases

| ID | Case | Coverage | Status |
| --- | --- | --- | --- |
| HOME-01 | First screen shows `내 노트`, PDF 열기, 새 노트, PDF 도구, 백업/복원 | `HomeStartE2ETest` | Compiled |
| NOTE-01 | Two quick strokes both remain and survive JSON round-trip | `NoteEditorStrokeE2ETest` | Compiled |
| NOTE-02 | Compact study pane shows canvas, close, capture and floating overflow | `StudyPaneE2ETest` | Compiled |
| NOTE-03 | Direct note canvas receives both quick finger strokes | `NoteCanvasStrokeE2ETest` | Compiled |
| PDF-01 | Tap near a text word selects it with finger tolerance | `PdfCanvasSelectionE2ETest` | Compiled |
| PDF-02 | Drag selects every intersecting text word | `PdfCanvasSelectionE2ETest` | Compiled |
| PDF-03 | Split chrome routes back/page/selection/tools/hub controls | `PdfSplitChromeE2ETest` | Compiled |
| PDF-04 | Simple hub shows seven primary tools and expands to every action | JVM `PdfHubSheetTest` | Passing |
| PDF-05 | PDF/note divider clamps to 35–85% and updates the pane | JVM `StudyPaneLayoutTest` + UI manual | Passing preview; device drag pending |
| NOTE-04 | Compact dock/tool targets and floating header metrics | JVM `NoteFloatingChromeTest` | Passing |
| TEMPLATE-01 | Image/PDF-first-page import, background render, backup inclusion | Manual SAF check required | Not run |
| BACKUP-01 | ZIP create/restore with template assets and traversal rejection | JVM `BackupManagerTest` | Passing |
| EXPORT-01 | Note export all/current/selected + hidden layers | JVM `NoteExportOptionsTest` | Passing |
| SEARCH-01 | Search next/previous wraps, clamps, resets on query change | JVM `SearchNavigationTest` | Passing |
| MASK-01 | Masking card legacy JSON, reveal/O-X scoring, duplication | JVM `MaskingCardTest`, `NotePageManagerTest` | Passing |
| DEVICE-01 | Stylus pressure curve and two/three-finger gesture behavior | Manual device required | Not run |

## Run on a device

```bash
./gradlew :app:assembleDebug -Parm64Only=true
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# All instrumentation tests
adb shell am instrument -w \
  com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner

# Or one class
adb shell am instrument -w \
  -e class com.hackerdjakodjo.mypdfwriter.ui.PdfCanvasSelectionE2ETest \
  com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner
```

With a device attached, Gradle can do the install and run:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Manual device checklist

1. Import a text PDF and a scanned/image PDF.
2. Select a word by tapping and a range by dragging; verify highlight, underline,
   strikeout and copy.
3. Open `노트` from the PDF top bar; drag the central divider up/down; resize via
   the header grip; close and reopen the pane.
4. Draw several strokes in the compact pane; check the paper is large and the
   floating dock does not permanently hide the writing area.
5. Add a PDF selection to the pane; open the link panel and jump to the source.
6. Press two fingers to undo and three fingers to redo while not drawing.
7. Create a masking card, tap to reveal, mark O/X, and check the progress/score.
8. Search a PDF, step through results with 다음/이전, and check the page highlight.
9. Import an image/PDF template, export a PDF, then back up and restore it.
8. Test the arm64 APK on a real phone; use the universal build only for a
   32-bit/x86 emulator if ever needed.

## Known blockers

- No adb device or emulator is available in the current Termux environment.
- `pm install` from Termux is denied; use My Files or a PC with adb.
- Stylus pressure and SAF file pickers cannot be meaningfully exercised by the
  JVM-only test suite.
