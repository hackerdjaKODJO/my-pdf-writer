package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compact study-pane smoke coverage: the note canvas must exist while the
 * floating pane header/dock remains reachable, the duplicate capture toggle is
 * gone (the header is the drop target), and the note menu owns favourite plus the
 * study-card sub-sheet.
 */
class StudyPaneE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactPaneUsesSingleStyleSlotAndMenuOwnedFavorite() {
        val notebook = Notebook(
            title = "E2E Study",
            pages = listOf(NotePage(id = "study-page", template = PaperTemplate.LINED)),
        )

        composeRule.setContent {
            MyPdfWriterTheme {
                StudyPaneSheet(
                    notebook = notebook,
                    pageIndex = 0,
                    heightFraction = 0.85f,
                    onHeightFractionChange = {},
                    onDismiss = {},
                    onChange = {},
                    onPageIndexChange = {},
                    onExportPdf = { _: NoteExportOptions -> },
                    onOpenSourcePdf = {},
                    onCaptureExcerpt = { _, _ -> true },
                    onJumpToSourcePage = { _, _ -> },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("note_canvas").assertExists()
        composeRule.onNodeWithText("E2E Study").assertExists()
        composeRule.onNodeWithContentDescription("스터디 노트 닫기").assertExists()
        composeRule.onNodeWithContentDescription("원본 페이지 링크 없음").assertExists()

        // The header itself is the drop target; the redundant toggle is gone.
        composeRule.onNodeWithContentDescription("PDF 발췌 놓기").assertDoesNotExist()

        // One style control, no compact favourite, and the corrected text label.
        composeRule.onNodeWithContentDescription("스타일").assertExists()
        composeRule.onNodeWithContentDescription("즐겨찾기").assertDoesNotExist()
        composeRule.onNodeWithText("글상자").assertExists()

        composeRule.onNodeWithContentDescription("노트 메뉴").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("노트 메뉴").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Favourite moved into the menu header for compact panes.
        composeRule.onNodeWithContentDescription("즐겨찾기").assertExists()
        composeRule.onNodeWithText("페이지").assertExists()

        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText("학습 카드"))
        composeRule.onNodeWithText("학습 카드").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("가림 카드").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("가림 카드").assertExists()
        composeRule.onNodeWithText("체크리스트").assertExists()
    }
}
