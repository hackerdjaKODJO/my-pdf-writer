package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home smoke coverage: the first screen a new user sees must expose the four
 * primary actions without opening a menu or knowing the data model.
 */
class HomeStartE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeShowsQuickActionsAndNotesSection() {
        composeRule.setContent {
            MyPdfWriterTheme {
                NotebookHomeScreen(
                    notebooks = emptyList<Notebook>(),
                    pdfs = emptyList<RecentDoc>(),
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
        }
        composeRule.waitForIdle()

        listOf("내 노트", "PDF 열기", "새 노트", "PDF 도구", "백업/복원").forEach { label ->
            val matches = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("home must expose '$label'", matches.isNotEmpty())
        }
    }
}
