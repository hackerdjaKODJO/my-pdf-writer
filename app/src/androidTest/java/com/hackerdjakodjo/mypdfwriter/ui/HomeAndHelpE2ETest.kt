package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home/help E2E coverage for the student-first policy: one primary action chosen
 * from whether the library has content, a two-button quick row, a short
 * onboarding, and a help centre whose four quick cards route to real destinations
 * while all eight legacy topics stay behind 더보기.
 *
 * The Compose rules need a device; the content checks are plain JUnit and run in
 * the same instrumentation APK without a device-specific dependency.
 */
class HomeAndHelpE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderHome(
        notebooks: List<Notebook> = emptyList(),
        pdfs: List<RecentDoc> = emptyList(),
        onImportPdf: () -> Unit = {},
        onOpenPdf: (RecentDoc) -> Unit = {},
    ) {
        composeRule.setContent {
            MyPdfWriterTheme {
                NotebookHomeScreen(
                    notebooks = notebooks,
                    pdfs = pdfs,
                    onOpenNotebook = {},
                    onCreateNotebook = {},
                    onOpenPdf = onOpenPdf,
                    onImportPdf = onImportPdf,
                    onCreatePdf = {},
                    onOpenTools = {},
                    onToggleFavorite = {},
                    onTrash = {},
                    onRestore = {},
                    onDeleteForever = {},
                    onRename = { _, _ -> },
                    onMoveFolder = { _, _ -> },
                    onSetTags = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderHelp(
        onQuickAction: ((QuickDestination) -> Unit)? = null,
        initialTopic: HelpTopicKey? = null,
    ) {
        composeRule.setContent {
            MyPdfWriterTheme {
                HelpCenterSheet(
                    onDismiss = {},
                    onQuickAction = onQuickAction,
                    initialTopic = initialTopic,
                )
            }
        }
        val expectedText = initialTopic?.let { key ->
            helpTopics().first { it.key == key }.title
        } ?: "1 PDF 열기"
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(expectedText).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    // ------------------------------------------------------------------ home

    @Test
    fun returningStudentGetsContinuePrimaryAndExactlyTwoQuickButtons() {
        val doc = RecentDoc(
            uri = "content://docs/lecture.pdf",
            name = "lecture.pdf",
            pageCount = 12,
            lastOpenedAt = 400L,
        )
        var opened: RecentDoc? = null
        renderHome(pdfs = listOf(doc), onOpenPdf = { opened = it })

        assertEquals(listOf("이어서 읽기", "새 노트"), homeQuickActionLabels(hasContent = true))
        assertEquals(HomePrimaryAction.CONTINUE, homePrimaryAction(true))
        composeRule.onNodeWithText("이어서 읽기: lecture.pdf").assertExists()
        composeRule.onNodeWithContentDescription("빠른 학습").assertExists()

        // The quick row is exactly two buttons: resume/create. Import, tools and
        // backup stay out of this row and live in the FAB/설정 instead.
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("이어서 읽기").fetchSemanticsNodes().size,
        )
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("새 노트").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithContentDescription("PDF 가져오기").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("이어서 읽기").performClick()
        composeRule.waitForIdle()
        assertEquals(doc.uri, opened?.uri)
    }

    @Test
    fun newStudentGetsImportPrimaryAndExactlyTwoQuickButtons() {
        var imports = 0
        renderHome(onImportPdf = { imports++ })

        assertEquals(listOf("PDF 가져오기", "새 노트"), homeQuickActionLabels(hasContent = false))
        assertEquals(HomePrimaryAction.IMPORT, homePrimaryAction(false))
        composeRule.onNodeWithContentDescription("PDF 가져오기").assertExists()
        composeRule.onNodeWithContentDescription("새 노트").assertExists()
        assertEquals(
            0,
            composeRule.onAllNodesWithContentDescription("이어서 읽기").fetchSemanticsNodes().size,
        )
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("PDF 가져오기").fetchSemanticsNodes().size,
        )
        assertEquals(
            1,
            composeRule.onAllNodesWithContentDescription("새 노트").fetchSemanticsNodes().size,
        )

        composeRule.onNodeWithContentDescription("PDF 가져오기").performClick()
        composeRule.waitForIdle()
        assertEquals(1, imports)
    }

    @Test
    fun onboardingIsCappedAtThreeCardsAndStepsThroughToStart() {
        assertEquals(3, ONBOARDING_CARD_LIMIT)
        assertEquals(ONBOARDING_CARD_LIMIT, onboardingCards().size)
        assertTrue(onboardingCards().size <= ONBOARDING_CARD_LIMIT)

        var visible by mutableStateOf(true)
        var completions = 0
        composeRule.setContent {
            MyPdfWriterTheme {
                if (visible) {
                    OnboardingSheet(
                        onDismiss = { visible = false },
                        onComplete = {
                            completions++
                            visible = false
                        },
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("1 / 3").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 / 3").assertExists()
        composeRule.onNodeWithText("4 / 3").assertDoesNotExist()
        composeRule.onNodeWithText("건너뛰기").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("다음").assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2 / 3").assertExists()
        composeRule.onNodeWithText("이전").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 / 3").assertExists()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3 / 3").assertExists()
        composeRule.onNodeWithText("4 / 3").assertDoesNotExist()
        composeRule.onNodeWithText("시작").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("시작").performClick()
        composeRule.waitForIdle()

        assertEquals(1, completions)
    }

    @Test
    fun onboardingFinalActionCanHandOffToPdfImport() {
        var visible by mutableStateOf(true)
        var imports = 0
        var completions = 0
        composeRule.setContent {
            MyPdfWriterTheme {
                if (visible) {
                    OnboardingSheet(
                        onDismiss = { visible = false },
                        onComplete = {
                            completions++
                            visible = false
                        },
                        onStartImport = {
                            imports++
                            visible = false
                        },
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("3 / 3").assertExists()
        composeRule.onNodeWithText("시작").assertDoesNotExist()
        composeRule.onNodeWithText("PDF 가져오기").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("PDF 가져오기").performClick()
        composeRule.waitForIdle()

        assertEquals(1, imports)
        assertEquals(0, completions)
    }

    // ------------------------------------------------------------------ help

    @Test
    fun helpCenterShowsFourQuickCardsInDocumentedOrder() {
        renderHelp()

        val labels = listOf("1 PDF 열기", "2 표시하며 읽기", "3 노트에 담기", "4 복습하기")
        assertEquals(labels, quickCards().map { it.label })
        labels.forEach { composeRule.onNodeWithText(it).assertExists() }

        // Visual order, not just presence: each card sits below the previous one.
        val tops = labels.map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top }
        assertTrue("quick cards must stack in order: $tops", tops.zipWithNext().all { it.first < it.second })

        // Legacy topics are hidden before 더보기.
        composeRule.onNodeWithText("더보기").assertExists()
        composeRule.onNodeWithText("문제 해결").assertDoesNotExist()
    }

    @Test
    fun showMoreRevealsAllEightLegacyTopicsAndCanCollapse() {
        renderHelp()

        val chips = helpTopics().map { it.chip }
        assertEquals(8, chips.size)
        assertEquals(8, visibleLegacyTopics(true).size)
        assertTrue(visibleLegacyTopics(false).isEmpty())
        chips.forEach { chip ->
            assertTrue("'$chip' must be hidden before 더보기", !hasText(chip))
        }

        composeRule.onNodeWithText("더보기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("접기").assertExists()
        chips.forEach { chip ->
            assertTrue("'$chip' must be revealed by 더보기", hasText(chip))
        }

        composeRule.onNodeWithText("접기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("더보기").assertExists()
        chips.forEach { chip ->
            assertTrue("'$chip' must hide again after 접기", !hasText(chip))
        }
    }

    @Test
    fun contextualTopicOpenRevealsSelectsAndScrollsToTheTopic() {
        renderHelp(initialTopic = HelpTopicKey.OPEN_PDF)

        assertTrue(visibleLegacyTopics(true).isNotEmpty())
        val headings = composeRule.onAllNodesWithText("PDF 열기/읽기")
            .fetchSemanticsNodes()
        assertTrue("the requested topic heading must be composed", headings.isNotEmpty())
        composeRule.onAllNodesWithText("PDF 열기/읽기").onLast().assertIsDisplayed()
        composeRule.onNodeWithText(PDF_HINT_PAGE_NAVIGATION_TEXT, substring = true).assertExists()
        composeRule.onNodeWithText(PDF_HINT_VIEW_ZOOM_TEXT, substring = true).assertExists()
    }

    @Test
    fun contextualWriteHelpExplainsNoteOnlyUndoAndPdfActionLocation() {
        renderHelp(initialTopic = HelpTopicKey.WRITE_TOOLS)

        composeRule.onNodeWithText(
            "노트 편집기에서만 두 손가락 탭은 실행 취소",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithText(
            "PDF에서는 실행 취소·다시 실행 버튼",
            substring = true,
        ).assertExists()
        composeRule.onNodeWithText(PDF_HINT_STYLE_UNDO_TEXT, substring = true).assertExists()
    }

    @Test
    fun quickActionCardsRouteTheirDestinationToTheHost() {
        val routes = mutableListOf<QuickDestination>()
        renderHelp(onQuickAction = { routes += it })

        quickCards().forEach { card ->
            composeRule.onNodeWithText(card.label).performClick()
            composeRule.waitForIdle()
        }
        assertEquals(quickCards().map { it.destination }, routes)
    }

    @Test
    fun quickCardWithoutHostCallbackRevealsItsLegacyTopic() {
        renderHelp()

        composeRule.onNodeWithText("3 노트에 담기").performClick()
        composeRule.waitForIdle()

        assertTrue(hasText("학습 노트 + 높이"))
    }

    @Test
    fun exportSuccessResourceNamesTheNewPdfWithNotes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            "주석을 포함한 새 PDF를 저장했습니다.",
            context.getString(R.string.export_done_with_notes),
        )
    }

    // ------------------------------------------------------- pure content (no device APIs)

    @Test
    fun inAppGuidanceHasNoStaleVocabularyOrHideStateContradiction() {
        val corpus = (
            helpTopics().map { it.allText() } +
                onboardingCards().map { "${it.title}\n${it.body}" }
            ).joinToString("\n")

        listOf(
            "고급", "스터디", "발췌로 저장", "꺼져 있는지",
            "5분 빠른 시작", "4단계 빠른 시작", "PDF 상단의 노트",
            "홈의 백업/복원", "홈의 백업·복원", "하단 페이지 바",
            "설정 → 파일 도구에 페이지 잠금·문서 정보·PDF 내보내기가",
        ).forEach { stale ->
            assertFalse("in-app guidance must not use stale '$stale'", corpus.contains(stale))
        }
        listOf(
            "60초 빠른 시작", "하단 도구 독의 노트", "홈 설정의 백업/복원",
            "위쪽 페이지 이동", "스와이프",
            "보기 도구에서 두 손가락·더블 탭으로 확대", "노트 편집기에서만 두 손가락 탭",
        ).forEach { current ->
            assertTrue("in-app guidance must mention '$current'", corpus.contains(current))
        }
        assertTrue("the hide-state hint must read 켜져 있는지", corpus.contains("켜져 있는지"))
    }

    private fun hasText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}
