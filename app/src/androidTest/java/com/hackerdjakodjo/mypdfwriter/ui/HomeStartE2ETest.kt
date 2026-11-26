package com.hackerdjakodjo.mypdfwriter.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.hackerdjakodjo.mypdfwriter.MainActivity
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.QuickNoteKind
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Home smoke coverage: the first screen a new user sees must expose the primary
 * actions and a persistent create FAB without a blocking tutorial, offer the
 * optional 60-second prompt, and remember the selected tab when the user opens a
 * document and comes back.
 */
class HomeStartE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun resetOnboarding() {
        context.getSharedPreferences(OnboardingStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun leaveOnboardingUnseen() {
        resetOnboarding()
    }

    @Composable
    private fun home(
        notebooks: List<Notebook> = emptyList(),
        pdfs: List<RecentDoc> = emptyList(),
        libraryMessage: String? = null,
        onDismissLibraryMessage: () -> Unit = {},
        showOnboardingPrompt: Boolean = false,
        onOpenOnboarding: () -> Unit = {},
        onDismissOnboardingPrompt: () -> Unit = {},
        onQuickCapture: (QuickNoteKind) -> Unit = {},
    ) {
        NotebookHomeScreen(
            notebooks = notebooks,
            pdfs = pdfs,
            onOpenNotebook = {},
            onCreateNotebook = {},
            onQuickCapture = onQuickCapture,
            onOpenPdf = {},
            onImportPdf = {},
            onCreatePdf = {},
            onOpenTools = {},
            onToggleFavorite = {},
            onTrash = {},
            onRestore = {},
            onDeleteForever = {},
            onRename = { _, _ -> },
            onMoveFolder = { _, _ -> },
            onSetTags = { _, _ -> },
            onOpenBackup = {},
            libraryMessage = libraryMessage,
            onDismissLibraryMessage = onDismissLibraryMessage,
            showOnboardingPrompt = showOnboardingPrompt,
            onOpenOnboarding = onOpenOnboarding,
            onDismissOnboardingPrompt = onDismissOnboardingPrompt,
        )
    }

    @Test
    fun homeShowsQuickActionsAndNotesSection() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        listOf("내 노트", "PDF 가져오기", "새 노트", "빠른 메모", "체크리스트").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("home must expose '$label'", matches.isNotEmpty())
        }
        composeRule.onNodeWithContentDescription("빠른 학습").assertExists()
        // Tools and backup are demoted to 설정, not shown as home quick actions.
        listOf("PDF 도구", "백업/복원").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("home must not expose '$label' as a quick action", matches.isEmpty())
        }
        composeRule.onNodeWithContentDescription("새로 만들기").assertExists()
        composeRule.onNodeWithContentDescription("검색").assertExists()
        composeRule.onNodeWithContentDescription("홈 설정").assertExists()
        listOf("노트 섹션", "PDF 섹션", "태그 섹션").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertExists()
        }
    }

    @Test
    fun quickCaptureButtonsReportTheirKindWithoutOpeningCreateDialog() {
        val captured = mutableListOf<QuickNoteKind>()
        composeRule.setContent {
            MyPdfWriterTheme { home(onQuickCapture = { captured.add(it) }) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("빠른 메모").performClick()
        composeRule.onNodeWithText("체크리스트").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(QuickNoteKind.MEMO, QuickNoteKind.CHECKLIST), captured)
        composeRule.onNodeWithText("새 노트 만들기").assertDoesNotExist()
    }

    @Test
    fun coldStartShowsOptionalPromptWithoutOpeningTutorial() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription(UiCopy.HOME_ONBOARDING_PROMPT)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(UiCopy.HOME_ONBOARDING_PROMPT).assertIsDisplayed()
            composeRule.onNodeWithContentDescription(OnboardingLabels.SKIP).assertDoesNotExist()
            assertFalse("showing Home must not consume the tutorial", OnboardingStore(context).hasSeen())

            composeRule.onNodeWithContentDescription(UiCopy.HOME_ONBOARDING_PROMPT).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(OnboardingLabels.SKIP).assertIsDisplayed()
            assertTrue("starting the tutorial must mark it seen", OnboardingStore(context).hasSeen())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun dismissingOptionalPromptMarksItSeenWithoutOpeningTutorial() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithContentDescription(UiCopy.HOME_ONBOARDING_PROMPT)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(UiCopy.HOME_ONBOARDING_DISMISS).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(UiCopy.HOME_ONBOARDING_PROMPT).assertDoesNotExist()
            composeRule.onNodeWithContentDescription(OnboardingLabels.SKIP).assertDoesNotExist()
            assertTrue(OnboardingStore(context).hasSeen())
        } finally {
            scenario.close()
        }
    }

    @Test
    fun unseenPromptKeeps48dpTargetsAndOpensTutorialOnlyOnTap() {
        var seen by mutableStateOf(false)
        var tutorialOpen by mutableStateOf(false)
        composeRule.setContent {
            MyPdfWriterTheme {
                home(
                    showOnboardingPrompt = !seen,
                    onOpenOnboarding = {
                        seen = true
                        tutorialOpen = true
                    },
                    onDismissOnboardingPrompt = { seen = true },
                )
                if (tutorialOpen) {
                    OnboardingSheet(
                        onDismiss = { tutorialOpen = false },
                        onComplete = { tutorialOpen = false },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(UiCopy.HOME_ONBOARDING_PROMPT)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(UiCopy.HOME_ONBOARDING_DISMISS)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("처음 사용법").assertDoesNotExist()
        composeRule.onNodeWithText("PDF 가져오기").assertExists()

        composeRule.onNodeWithText(UiCopy.HOME_ONBOARDING_PROMPT).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("처음 사용법").assertIsDisplayed()
        composeRule.onNodeWithText(UiCopy.HOME_ONBOARDING_PROMPT).assertDoesNotExist()
    }

    @Test
    fun topSegmentControlSwitchesSections() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("PDF 섹션").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("내 PDF").assertExists()

        composeRule.onNodeWithContentDescription("태그 섹션").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("노트에 붙인 태그를 한곳에서 찾아봅니다.").assertExists()

        composeRule.onNodeWithContentDescription("노트 섹션").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("내 노트").assertExists()
    }

    @Test
    fun quickStudySheetStepsThroughProgressAndIsReplayable() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("빠른 학습").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/4").assertExists()
        composeRule.onNodeWithText("1. PDF 열기").assertExists()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2/4").assertExists()
        composeRule.onNodeWithText("2. 형광펜으로 표시").assertExists()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3/4").assertExists()
        composeRule.onNodeWithText("3. 노트에 담기").assertExists()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4/4").assertExists()
        composeRule.onNodeWithText("4. 복습하기").assertExists()
        composeRule.onNodeWithText("완료").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/4").assertDoesNotExist()

        // Reopening restarts at step 1/4 instead of remembering the old step.
        composeRule.onNodeWithContentDescription("빠른 학습").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/4").assertExists()
        composeRule.onNodeWithText("건너뛰기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1/4").assertDoesNotExist()
    }

    @Test
    fun searchFieldHasAPersistentLabelAndAnAccessibleClearButton() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("검색").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("노트 제목·태그 검색").assertExists()
        composeRule.onNode(hasSetTextAction()).performTextInput("기말")
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("검색어 지우기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("노트 제목·태그 검색").assertExists()
    }

    @Test
    fun cardMenusAndStarNameTheirSubjectAndState() {
        composeRule.setContent {
            MyPdfWriterTheme {
                home(
                    notebooks = listOf(
                        Notebook(id = "n1", title = "시험 노트", updatedAt = 1L),
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("노트 메뉴: 시험 노트").assertExists()
        composeRule.onNodeWithContentDescription("즐겨찾기").assertExists()
    }

    @Test
    fun settingsSheetKeepsEveryGlobalUtilityReachable() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("홈 설정").performClick()
        composeRule.waitForIdle()

        listOf("백업/복원", "휴지통", "태그", "PDF 도구", "도움말", "정보").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("settings must expose '$label'", matches.isNotEmpty())
        }

        composeRule.onNodeWithText("정보").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(
            "모든 파일과 노트는 기기에만 저장되며 네트워크를 사용하지 않습니다.",
        ).assertExists()
    }

    @Test
    fun filterSheetAppliesAndClearsOneCompactActiveRow() {
        // Filters are intentionally hidden on an empty library; add one note so
        // the sheet under test is actually reachable.
        composeRule.setContent {
            MyPdfWriterTheme { home(notebooks = listOf(Notebook(title = "시험 노트"))) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("필터").performClick()
        composeRule.waitForIdle()
        listOf("노트 범위", "폴더", "태그", "초기화", "적용").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("filter sheet must expose '$label'", matches.isNotEmpty())
        }

        composeRule.onNodeWithText("즐겨찾기").performClick()
        composeRule.onNodeWithText("적용").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("즐겨찾기한 노트가 없어요").assertExists()
        assertTrue(
            composeRule.onAllNodesWithText("즐겨찾기").fetchSemanticsNodes().isNotEmpty(),
        )
        assertTrue(
            composeRule.onAllNodesWithText("모두 지우기").fetchSemanticsNodes().isNotEmpty(),
        )

        composeRule.onNodeWithText("모두 지우기").performClick()
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithText("모두 지우기").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun pdfCardMenuRemovesFromRecentsWithoutDeletingTheSource() {
        composeRule.setContent {
            MyPdfWriterTheme {
                home(
                    pdfs = listOf(
                        RecentDoc(
                            uri = "content://docs/lecture.pdf",
                            name = "lecture.pdf",
                            pageCount = 12,
                            lastOpenedAt = 1L,
                        ),
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("PDF 섹션").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("학습 메뉴: lecture.pdf").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("최근 목록에서 제거").assertExists()
        composeRule.onNodeWithText("원본 PDF 파일은 삭제되지 않습니다.").assertExists()
    }

    @Test
    fun homeTabSurvivesLeavingHomeAndReturning() {
        var showHome by mutableStateOf(true)
        composeRule.setContent {
            MyPdfWriterTheme {
                val holder = rememberSaveableStateHolder()
                if (showHome) {
                    holder.SaveableStateProvider("home") { home() }
                } else {
                    Text("detail")
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("PDF 섹션").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("내 PDF").assertExists()

        composeRule.runOnIdle { showHome = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showHome = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("내 PDF").assertExists()
    }

    @Test
    fun importFailureIsShownAsSnackbarAndThenDismissed() {
        var dismissals = 0
        composeRule.setContent {
            MyPdfWriterTheme {
                home(
                    libraryMessage = "PDF를 열 수 없습니다.",
                    onDismissLibraryMessage = { dismissals++ },
                )
            }
        }
        composeRule.waitUntil("library error snackbar", 4_000L) {
            composeRule.onAllNodesWithText("PDF를 열 수 없습니다.").fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitForIdle()
        assertTrue("snackbar must ask the VM to clear the message", dismissals >= 1)
    }
}
