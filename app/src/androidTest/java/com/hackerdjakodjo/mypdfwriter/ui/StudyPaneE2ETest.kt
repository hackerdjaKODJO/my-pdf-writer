package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Rule
import org.junit.Test

/**
 * Compact study-pane smoke coverage: the note canvas must exist while the
 * floating pane header/dock remains reachable and the PDF source strip is not
 * replaced by a full-screen editor.
 */
class StudyPaneE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactPaneExposesCanvasHeaderCaptureAndMore() {
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
        composeRule.onNodeWithContentDescription("스터디 노트 닫기").assertExists()
        composeRule.onNodeWithContentDescription("PDF 발췌 놓기").assertExists()
        composeRule.onNodeWithContentDescription("더보기").assertExists()
    }
}
