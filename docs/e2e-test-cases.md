# E2E test cases — My PDF Writer 0.8.38

The app is intentionally offline. These tests run on a connected Android device
or emulator; the build machine has no adb device attached, so only JVM tests are
executed here. Instrumentation APKs are compiled with
`:app:assembleDebugAndroidTest`.

## Current results

| Suite | Count | Status |
| --- | --- | --- |
| JVM unit tests | 879 | **Passing** on this phone |
| Instrumentation/E2E | 10 classes (see below) | **Compiled**; on-device run pending |
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
| PDF-03 | Split chrome routes back/page/selection/tools/PDF menu controls | `PdfSplitChromeE2ETest` | Compiled |
| PDF-04 | Simple hub shows seven primary tools and expands to every action | JVM `PdfHubSheetTest` | Passing |
| PDF-05 | PDF/note divider clamps to 35–85% and updates the pane | JVM `StudyPaneLayoutTest` + UI manual | Passing preview; device drag pending |
| NOTE-04 | Compact dock/tool targets and floating header metrics | JVM `NoteFloatingChromeTest` | Passing |
| TEMPLATE-01 | Image/PDF-first-page import, background render, backup inclusion | Manual SAF check required | Not run |
| BACKUP-01 | ZIP create/restore with template assets and traversal rejection | JVM `BackupManagerTest` | Passing |
| EXPORT-01 | Note export all/current/selected + hidden layers | JVM `NoteExportOptionsTest` | Passing |
| SEARCH-01 | Search next/previous wraps, clamps, resets on query change | JVM `SearchNavigationTest` | Passing |
| MASK-01 | Masking card legacy JSON, reveal/O-X scoring, duplication | JVM `MaskingCardTest`, `NotePageManagerTest` | Passing |
| CHECK-01 | Checklist JSON, item toggles, progress, fresh ids | JVM `ChecklistCardTest` | Passing |
| AUDIO-01 | Playback speed rates, clamping, labels | JVM `PlaybackSpeedTest` | Passing |
| DICT-01 | Dictionary query normalization and fallback | JVM `TextActionsDictionaryTest` | Passing |
| UX-01 | Onboarding topic order/one-time flag | JVM `OnboardingStoreTest`, `OnboardingContentTest` | Passing |
| UX-02 | PDF hint state policy and no duplicate chips | JVM `PdfContextHintsTest` | Passing |
| READ-01 | Continuous zoom clamp/pinch/toggle and label; pinch pointer tracking | JVM `ReaderZoomTest`, `ReaderPinchTrackerTest` | Passing |
| READ-02 | Two-finger pinch changes document-wide zoom label | Instrumentation `ReaderPinchE2ETest` | Compiled |
| READ-05 | Pointer-count gate + gated zoom composition | JVM `ContinuousPinchGateTest`, `ContinuousPinchCompositionTest` | Passing |
| READ-06 | Zoom enlarges page layout past viewport, not only the % label | Instrumentation `ReaderPinchE2ETest` (bounds assertion) | Compiled |
| READ-07 | Horizontal pan moves the enlarged page, returns to the left edge, and does not open it | Instrumentation `ReaderPinchE2ETest` | Compiled |
| READ-08 | Previous bitmap stays visible while next zoom render is pending | Instrumentation `ReaderPinchE2ETest` | Compiled |
| HOME-09 | Persistent create FAB is present on Home | Instrumentation `HomeStartE2ETest` | Compiled |
| HOME-10 | Tag search filters the tag overview; empty-PDF search keeps import copy | JVM `HomeLibraryPolicyTest` | Passing |
| HOME-11 | Home tab survives leaving Home and returning | Instrumentation `HomeStartE2ETest` | Compiled |
| UI-11 | Viewer dock names the active extra tool/shape | JVM `ViewerToolDockTest` | Passing |
| READ-03 | Render zoom quantization and bitmap scale | JVM `ReaderZoomTest` | Passing |
| READ-04 | Pinch actually re-renders page bitmaps at higher zoom | Instrumentation `ReaderPinchE2ETest` | Compiled |
| TRACE-01 | PDF continuous trace events/fields | JVM `PdfContinuousTraceTest` | Passing |
| EDIT-01 | Selected-text replacement geometry/JSON/filter | JVM `PdfTextEditTest`, `AnnotationJsonTest` | Passing |
| HELP-01 | Help topics are complete, offline and ordered | JVM `HelpCenterContentTest` | Passing |
| PDF-06 | Long press in VIEW mode enters selection and reports a point; long press in selection mode is inert | JVM `PdfTextLayoutTest` + instrumentation `PdfCanvasSelectionE2ETest` | Compiled |
| DEVICE-01 | Stylus pressure curve and two/three-finger gesture behavior | Manual device required | Not run |
| GR-01 | Persisted display prefs, brightness clamp and legacy JSON | JVM `DocStateTest`, `PdfDisplayPrefsTest` | Passing |
| GR-02 | Page lock gates ordinary navigation but keeps source jumps | JVM `PageLockTest` | Passing |
| GR-03 | Internal PDF links extract/remap; external URIs are copy-only | JVM `PdfLinksTest` | Passing |
| GR-04 | ACTION_SEND URI precedence and duplicate-carrier guard | JVM `IntentImportPolicyTest` | Passing |
| GR-05 | Home sort keys, ties and case-insensitive names | JVM `HomeLibraryPolicyTest` | Passing |
| UI-12 | Home filter sheet state, active chips, reset/apply | JVM `HomeFilterStateTest` + E2E | Passing |
| UI-13 | Reader dock has six primary slots; annotation tools remain reachable | JVM `ViewerToolDockTest` | Passing |
| UI-14 | PDF menu groups every action; advanced section is reachable | JVM `PdfHubSheetTest` | Passing |
| UI-15 | Note menu exposes focused page/study/audio/writing sub-sheets | JVM `NoteEditorMenuTest` | Passing |
| UI-16 | PDF tools are grouped without losing items | JVM `ToolsScreenSectionsTest` | Passing |
| MEMO-01 | Memo card JSON, clamping, fresh ids and legacy pages | JVM `MemoCardTest`, `NotePageManagerTest` | Passing |
| MEMO-02 | Memo card screen rect, drag/resize helper and pointer gating | JVM `MemoCardOverlayTest` + `NoteCanvasStrokeE2ETest` | Compiled |
| MEMO-03 | Memo card list/editor/delete and export painter | JVM `MemoCardsSheetTest`, `NoteCardPainterTest` | Passing |
| PEN-01 | Favorite pen defaults, clear semantics, cap/prune/reorder | JVM `PenBoxTest`, `FavoritePenStripTest` | Passing |
| SPLIT-01 | Divider percentage, presets and 28dp handle | JVM `StudyPaneLayoutTest` + E2E | Compiled |
| SPLIT-02 | PDF chrome density and top toolbar callbacks | JVM `PdfSplitChromeTest` + E2E | Compiled |
| SPLIT-03 | Note tools hide/restore pill and header drop target | JVM `NoteFloatingChromeTest` + E2E | Compiled |
| INK-01 | Per-style pressure width/flow/grain profiles and legacy identity | JVM `InkResponseTest` | Passing |
| INK-02 | Painter flow/wash/graphite policy and replay stability | JVM `AnnotationPainterPolicyTest` | Passing |
| INK-03 | Active stylus pressure sampling and neutral fallback | JVM `NotePressureCaptureTest` | Passing |
| A11Y-01 | 48dp targets and state labels | JVM `AccessibilityPolicyTest`, E2E smoke | Compiled |
| A11Y-02 | 320dp / 200% font layout decisions | JVM `AccessibilityLayoutTest` | Passing |
| A11Y-03 | Split divider accessible size control | JVM `StudyPaneLayoutTest` + E2E | Compiled |
| A11Y-04 | Home/note/PDF labels and selected states | JVM + E2E smoke | Compiled |
| PEN-02 | Ballpoint skip, fountain taper, pencil segment dynamics | JVM `PenStrokeGeometryTest`, `AnnotationPainterPolicyTest` | Passing |
| PDF-OPEN | Single documentInfo, identity layout skip, source cache | JVM `PdfRepositoryTest` | Passing |
| PDF-MEMO | PDF post-its persist, render, move/resize and close | JVM `PdfMemoCardLayerTest`, `PdfViewModelMemoTest` | Passing |
| STUDUX-01 | Home segments, quick actions and Quick Study steps | JVM `QuickStudySheetTest`, `HomeStartTest` | Passing |
| STUDUX-02 | PDF six-slot dock and 학습 메뉴 migration audit | JVM `ViewerToolDockTest`, `PdfHubSheetTest` | Passing |
| STUDUX-03 | Note single-row dock and top-bar no-duplicate rule | JVM `NoteEditorControlsTest` | Passing |
| BUG-01 | Undo/save/search races and cancellation policies | JVM `PdfViewModelTest` | Passing |
| BUG-02 | Viewer back handling, split search and memo edit requests | JVM + E2E | Compiled |
| BUG-03 | Backup rollback and intent carrier clearing | JVM `BackupManagerTest`, `IntentImportPolicyTest` | Passing |
| INK-04 | Variable-width outline, lazy brush/RDP/Chaikin and pressure calibration | JVM `VariableWidthOutlineTest`, `NoteStrokeRefineTest`, `InkPressureTest` | Passing |
| GR-05 | Locations panel, annotation summary, tabs, auto-hide and crop | JVM `LocationsSheetTest`, `AnnotationSummaryTest`, `DocumentTabsTest`, `PdfCropTest` | Passing |
| UX-06 | Four-group hub, unified 위치, 4-button chrome, coach card, quick help | JVM `PdfHubSheetTest`, `ViewerScreenTest`, `HelpCenterContentTest`, `NotebookHomeFormatTest` | Passing |
| BUG-07 | Save/open/export races, pressure calibration, crop clip, backup concurrency | JVM `PdfViewModelTest`, `IntentImportPolicyTest`, `NotePressureCaptureTest`, `PdfCropTest`, `NotebookSaveRaceTest` | Passing |
| HOME-12 | Returning student sees `이어서 읽기`, new student sees `PDF 가져오기`; exactly two quick buttons; onboarding capped at 3 cards | Instrumentation `HomeAndHelpE2ETest` (UI + pure) | Compiled |
| HELP-02 | Four quick cards in documented order; `더보기` reveals all 8 legacy topics; quick callbacks route to their destinations | Instrumentation `HomeAndHelpE2ETest` | Compiled |
| BACKUP-02 | Two simultaneous restore results start one manager call; backup flushes pending notebook saves; restore discards stale notebook state before refresh and keeps a PDF opened during restore | Instrumentation `BackupRestoreE2ETest` (pure + sheet UI) | Compiled |
| DOC-02 | Save queue keeps newest state per docId; rapid double open publishes only the last document; SAF export snapshot stays bound to the launch document | Instrumentation `DocumentSwitchE2ETest` (pure JUnit) | Compiled |
| GUIDE-01 | No stale `고급`/`스터디`/`발췌로 저장` and no `켜져 있는지` contradiction | JVM `UsageGuideContentTest` (HTML) + pure in-app content check in `HomeAndHelpE2ETest` | Passing / Compiled |

## New data/home/help instrumentation classes

- `ui/HomeAndHelpE2ETest.kt` drives `NotebookHomeScreen`, `OnboardingSheet` and
  `HelpCenterSheet` with `createAndroidComposeRule<ComponentActivity>()`. It pins
  the single primary action (continue vs. import) and two-button quick row,
  steps the three-card walkthrough, checks quick-card order/routing, and toggles
  `더보기`/`접기` over the eight legacy topics. Its plain-JUnit content check
  guards the in-app guidance vocabulary.
- `ui/BackupRestoreE2ETest.kt` races two SAF restore results through
  `BackupOperationGate`, verifies `withFlushedSaves` runs before archiving and
  through the notebook write gate, and checks `onBackupRestored` ordering plus
  `shouldCloseAfterRestore` for a document opened mid-restore. Sheet UI tests
  assert both actions are disabled while busy and that backup/restore callbacks
  route correctly (including the confirm dialog).
- `data/DocumentSwitchE2ETest.kt` is pure JUnit: per-docId save-queue coalescing,
  `OpenGenerationGate` for rapid opens, and `PdfExportSnapshot`/`viewerDocState`
  immutability with the active-document publication guard.

`docs/usage-guide.html` is a repository file, not an app asset, so an on-device
`androidTest` cannot read it. The HTML itself is checked by the JVM-only
`UsageGuideContentTest` (`고급`/`스터디` absent, `켜져 있는지` present and
`꺼져 있는지` absent). `GUIDE-01` adds `발췌로 저장` to the same vocabulary guard
over the in-app help/onboarding copy that is derived from the guide.

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
