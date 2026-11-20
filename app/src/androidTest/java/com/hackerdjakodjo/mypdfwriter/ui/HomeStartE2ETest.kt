package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home smoke coverage: the first screen a new user sees must expose the primary
 * actions and a persistent create FAB, and must remember the selected tab when
 * the user opens a document and comes back.
 */
class HomeStartE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun home(
        notebooks: List<Notebook> = emptyList(),
        pdfs: List<RecentDoc> = emptyList(),
    ) {
        NotebookHomeScreen(
            notebooks = notebooks,
            pdfs = pdfs,
            onOpenNotebook = {},
            onCreateNotebook = {},
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
        )
    }

    @Test
    fun homeShowsQuickActionsAndNotesSection() {
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        listOf("내 노트", "PDF 가져오기", "새 노트", "PDF 도구", "백업/복원").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("home must expose '$label'", matches.isNotEmpty())
        }
        composeRule.onNodeWithContentDescription("새로 만들기").assertExists()
        composeRule.onNodeWithContentDescription("검색").assertExists()
        composeRule.onNodeWithContentDescription("홈 설정").assertExists()
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
        composeRule.onNodeWithText("노트 제목·태그 검색").performTextInput("기말")
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

        listOf("백업/복원", "휴지통", "태그", "PDF 도구", "사용법/도움말", "정보").forEach { label ->
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
        composeRule.setContent {
            MyPdfWriterTheme { home() }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("필터").performClick()
        composeRule.waitForIdle()
        listOf("보기", "폴더", "태그", "초기화", "적용").forEach { label ->
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

        composeRule.onNodeWithText("PDF").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("PDF 메뉴: lecture.pdf").performClick()
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

        composeRule.onNodeWithText("PDF").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("내 PDF").assertExists()

        composeRule.runOnIdle { showHome = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showHome = true }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("내 PDF").assertExists()
    }
}
