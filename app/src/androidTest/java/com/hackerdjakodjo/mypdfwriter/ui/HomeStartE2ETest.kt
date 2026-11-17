package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithContentDescription("새로 만들기").assertExists()
    }

    @Test
    fun homeTabSurvivesLeavingHomeAndReturning() {
        var showHome by mutableStateOf(true)
        composeRule.setContent {
            MyPdfWriterTheme {
                val holder = rememberSaveableStateHolder()
                if (showHome) {
                    holder.SaveableStateProvider("home") {
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
