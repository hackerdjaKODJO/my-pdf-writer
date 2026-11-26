package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Instrumentation coverage for the student-facing note menu (0.8.37) and the
 * stroke/history regressions around it (0.8.35–0.8.38). Labels are the Korean
 * strings a student actually sees; pure helpers pin the vocabulary that Compose
 * cannot inspect when a sheet row is scrolled out of the viewport.
 */
class NoteMenuAndStrokeE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun noteMenuKeepsFourStudentSectionsAndTenInkOptions() {
        assertEquals(
            listOf("노트", "추가", "파일", "설정"),
            NOTE_MENU_SECTION_ORDER.map { it.label },
        )
        assertFalse("legacy 학습 section must be gone", NOTE_MENU_SECTION_ORDER.any { it.label == "학습" })
        assertEquals("카드", NOTE_REVIEW_CARDS_LABEL)
        assertEquals(10, NOTE_INK_SETTINGS.size)
        val supporting = noteInkSettingsSupportingLabel()
        assertTrue("ink settings must announce the real option count: $supporting", supporting.contains("10가지"))
        assertFalse(supporting.contains("8가지"))

        openEmptyEditorMenu()

        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText("노트"))
        assertTrue(composeRule.onAllNodesWithText("노트").fetchSemanticsNodes().isNotEmpty())
        menu.performScrollToNode(hasText("추가"))
        assertTrue(composeRule.onAllNodesWithText("추가").fetchSemanticsNodes().isNotEmpty())
        menu.performScrollToNode(hasText("카드"))
        composeRule.onNodeWithText("카드").assertExists()
        assertFalse(
            "legacy 학습 카드 label must be gone",
            composeRule.onAllNodesWithText("학습 카드").fetchSemanticsNodes().isNotEmpty(),
        )
        menu.performScrollToNode(hasText("파일"))
        assertTrue(composeRule.onAllNodesWithText("파일").fetchSemanticsNodes().isNotEmpty())
        menu.performScrollToNode(hasText("설정"))
        assertTrue(composeRule.onAllNodesWithText("설정").fetchSemanticsNodes().isNotEmpty())
        menu.performScrollToNode(hasText("필기 설정"))
        composeRule.onNodeWithText(supporting).assertExists()
    }

    @Test
    fun captureVocabularyUsesOneNoteLabelAndKeepsHandoffsSecondary() {
        assertEquals("노트에 담기", NOTE_CAPTURE_LABEL)
        assertEquals("노트에 담기", studentCaptureLabel())
        assertEquals(NOTE_CAPTURE_LABEL, NOTE_CAPTURE_VOCABULARY.first())
        assertEquals(listOf("번역", "사전"), STUDENT_HANDOFF_LABELS)
        assertEquals(listOf("노트에 담기", "번역", "사전"), NOTE_CAPTURE_VOCABULARY)
        assertEquals("노트에 담기", PDF_HUB_CAPTURE_LABEL)
        assertEquals("노트에 담기", TextSelectionPrimaryAction.EXCERPT.contentDescription)
        assertFalse(NOTE_CAPTURE_VOCABULARY.contains("발췌로 저장"))
        assertFalse(STUDENT_HANDOFF_LABELS.contains(NOTE_CAPTURE_LABEL))

        composeRule.setContent {
            MyPdfWriterTheme {
                NotebookHomeScreen(
                    notebooks = emptyList(),
                    pdfs = emptyList(),
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
        composeRule.onNodeWithContentDescription("빠른 학습").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("다음").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3. 노트에 담기").assertExists()
    }

    @Test
    fun dockKeepsSixToolsAndMarksPenAsPrimary() {
        assertEquals(
            listOf(Tool.PEN, Tool.HIGHLIGHT, Tool.ERASER, Tool.LASSO, Tool.TEXT, Tool.RECT),
            NOTE_DOCK_TOOLS,
        )
        assertEquals(6, NOTE_DOCK_TOOLS.size)
        assertEquals(Tool.PEN, NOTE_PRIMARY_DOCK_TOOL)
        assertEquals(1, NOTE_DOCK_TOOLS.count(::noteToolIsPrimary))
        NOTE_DOCK_TOOLS.forEach { tool ->
            assertEquals("only 펜 is primary", tool == Tool.PEN, noteToolIsPrimary(tool))
        }
        assertEquals("펜", noteToolLabel(NOTE_PRIMARY_DOCK_TOOL))

        composeRule.setContent { emptyEditor() }
        composeRule.waitForIdle()

        NOTE_DOCK_TOOLS.map(::noteToolLabel).filter { it != "펜" }.forEach { label ->
            assertTrue(
                "dock must expose '$label'",
                composeRule.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        // The primary pen slot also announces its selected state.
        composeRule.onNodeWithContentDescription(noteToolDescription("펜", true)).assertExists()
    }

    @Test
    fun twoQuickStrokesSurviveUndoRedoAfterRefinement() {
        val initial = Notebook(
            id = "e2e-undo-notebook",
            title = "되돌리기 노트",
            pages = listOf(NotePage(id = "undo-page-0", template = PaperTemplate.BLANK)),
        )
        val latest = AtomicReference(initial)

        composeRule.setContent {
            var notebook by remember { mutableStateOf(initial) }
            var pageIndex by remember { mutableIntStateOf(0) }
            NoteEditorScreen(
                notebook = notebook,
                pageIndex = pageIndex,
                onBack = {},
                onChange = { updated ->
                    notebook = updated
                    latest.set(updated)
                },
                onPageIndexChange = { pageIndex = it },
                onExportPdf = {},
            )
        }
        composeRule.waitForIdle()
        enableFingerWriting()

        // No idle wait between strokes: this still exercises the stale-closure
        // regression from 0.8.2 with commit-time refinement switched on.
        drawStroke(yFraction = 0.34f)
        drawStroke(yFraction = 0.60f)
        composeRule.waitForIdle()

        val afterTwo = latest.get().pages[0].annotations
        assertEquals("both strokes must be committed", 2, afterTwo.size)
        assertEquals(2, afterTwo.map { it.id }.toSet().size)
        assertTrue("refined strokes must keep their samples", afterTwo.all { it.points.size >= 2 })

        // Use the always-visible top-bar history actions so the matcher cannot
        // collide with a second copy inside the (modal) note menu sheet.
        composeRule.onNodeWithContentDescription("실행 취소").performClick()
        composeRule.waitForIdle()
        val afterUndo = latest.get().pages[0].annotations
        assertEquals("undo must drop only the most recent stroke", 1, afterUndo.size)
        assertEquals(afterTwo.first().id, afterUndo.first().id)

        composeRule.onNodeWithContentDescription("다시 실행").performClick()
        composeRule.waitForIdle()
        val afterRedo = latest.get().pages[0].annotations
        assertEquals("redo must restore both refined strokes", 2, afterRedo.size)
        assertEquals(
            "redo must restore the same annotations, not fresh ones",
            afterTwo.map { it.id }.toSet(),
            afterRedo.map { it.id }.toSet(),
        )
        assertTrue(afterRedo.all { it.points.size >= 2 })
    }

    @Composable
    private fun emptyEditor() {
        NoteEditorScreen(
            notebook = Notebook(
                id = "e2e-menu-notebook",
                title = "메뉴 노트",
                pages = listOf(NotePage(id = "menu-page-0", template = PaperTemplate.BLANK)),
            ),
            pageIndex = 0,
            onBack = {},
            onChange = {},
            onPageIndexChange = {},
            onExportPdf = {},
        )
    }

    private fun openEmptyEditorMenu() {
        composeRule.setContent { emptyEditor() }
        composeRule.waitForIdle()
        openNoteMenu()
    }

    private fun openNoteMenu() {
        composeRule.onNodeWithContentDescription("노트 메뉴").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("노트 메뉴").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** Instrumentation injects touch only, so finger writing is enabled in the ink sheet. */
    private fun enableFingerWriting() {
        openNoteMenu()
        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText("필기 설정"))
        composeRule.onNodeWithText("필기 설정").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("노트 메뉴").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("손가락 필기").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("손가락 필기").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("닫기").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("손가락 필기").fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun drawStroke(yFraction: Float) {
        composeRule.onNodeWithTag("note_canvas").performTouchInput {
            down(Offset(width * 0.28f, height * yFraction))
            moveTo(Offset(width * 0.45f, height * (yFraction + 0.04f)))
            moveTo(Offset(width * 0.72f, height * yFraction))
            up()
        }
    }
}
