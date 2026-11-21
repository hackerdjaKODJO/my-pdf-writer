# E2E coverage expansion — 0.8.39

Three agents created instrumentation tests in parallel, one per area. No device
was attached, so every test was compile-verified with
`:app:assembleDebugAndroidTest`; on-device execution remains the next step.

## Added suites

### PDF (`ui/PdfMenuFlowE2ETest`, `PdfCropAndPanE2ETest`, `PdfSheetBackE2ETest`)
- four first-level 학습 메뉴 groups and no duplicate navigation rows
- single `위치` sheet with tabs, jump and summary export callbacks
- top chrome limited to four controls
- coach card appears, dismisses, and never consumes canvas gestures
- document tab strip: switch, close, hide, overflow, recents and double-tap guard
- crop clip rect identity/asymmetric insets and centre-tap hit-test mapping
- pan reset on document switch with the same page index
- trimmed search query and stale-hit clearing
- Back interception for all `ViewerSheetFlags`, including file tools and 위치

### Notes (`ui/NotePressureE2ETest`, `NoteEraserAndRefineE2ETest`, `NoteMenuAndStrokeE2ETest`)
- pointer-up/memo samples never poison the pressure calibrator
- eraser with point-less TEXT/STAMP annotations does not crash
- eraser preview ellipse matches real erase radii
- `점 간소화` never increases stored point count
- note menu groups `노트·복습·내보내기·도움말` and 10-option ink settings label
- one `노트에 담기` label; translation handoff stays secondary
- six-slot dock with 펜 primary; two quick strokes and undo/redo survive refinement

### Data / Home / Help (`ui/HomeAndHelpE2ETest`, `ui/BackupRestoreE2ETest`, `data/DocumentSwitchE2ETest`)
- home primary action policy and exactly two quick buttons
- onboarding capped to three cards
- four help quick cards, 더보기 reveals all eight legacy topics, quick-action routing
- backup double-entry gate and pending-save flush
- restore discards stale notebook state and does not close a PDF opened meanwhile
- per-doc save queue, rapid double-open, and SAF export snapshot binding

## Totals
- Instrumentation classes: **16** (9 new), `@Test` methods: **85**.
- JVM unit suite unchanged at **879 tests**, 0 failures.
- `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest` pass.

## Running on a device (when one is attached)
```bash
cd ~/my-pdf-writer
./.termux-build.sh :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.hackerdjakodjo.mypdfwriter.debug.test/androidx.test.runner.AndroidJUnitRunner
```
Filter a single class with `-e class com.hackerdjakodjo.mypdfwriter.ui.PdfMenuFlowE2ETest`.
