package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end regression coverage for note-stroke loss.
 *
 * The editor is launched with a state-backed [Notebook]. Two finger strokes are
 * injected back-to-back through the real `NoteCanvas` pointer input (the note
 * menu enables finger writing because instrumentation cannot inject a stylus),
 * then the latest notebook emitted through `onChange` is checked and serialized.
 */
class NoteEditorStrokeE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun twoFingerStrokesSurviveEditorStateAndJsonRoundTrip() {
        val initial = Notebook(
            id = "e2e-notebook",
            title = "E2E 노트",
            pages = listOf(
                NotePage(id = "e2e-page-0", template = PaperTemplate.BLANK),
            ),
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
        // No idle wait between the strokes: this exercises both the stale
        // pointerInput callback and the parent's synchronous working notebook.
        drawStroke(yFraction = 0.34f)
        drawStroke(yFraction = 0.60f)
        composeRule.waitForIdle()

        val saved = latest.get()
        assertEquals("the editor must keep a single page", 1, saved.pages.size)
        val savedAnnotations = saved.pages[0].annotations
        assertEquals(
            "both strokes must reach the latest notebook through onChange",
            2,
            savedAnnotations.size,
        )
        assertEquals(
            "both strokes must carry distinct annotation ids",
            2,
            savedAnnotations.map { it.id }.toSet().size,
        )
        assertTrue(savedAnnotations.all { it.type == AnnotationType.PEN })
        assertTrue(savedAnnotations.all { it.points.size >= 2 })

        val restored = Notebook.fromJson(JSONObject(saved.toJson().toString()))
        assertEquals(
            "both strokes must survive the JSON round-trip",
            2,
            restored.pages[0].annotations.size,
        )
        assertEquals(
            savedAnnotations.map { it.id }.toSet(),
            restored.pages[0].annotations.map { it.id }.toSet(),
        )
        assertTrue(restored.pages[0].annotations.all { it.points.size >= 2 })
    }

    /**
     * Instrumentation only injects touch pointers, so finger writing has to be
     * enabled through the note menu (노트 메뉴 -> 필기 설정 -> 손가락 필기).
     */
    private fun enableFingerWriting() {
        composeRule.onNodeWithContentDescription("노트 메뉴").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(SHEET_TITLE).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText(INK_SETTINGS_LABEL))
        composeRule.onNodeWithText(INK_SETTINGS_LABEL).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(SHEET_TITLE).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(FINGER_WRITING_LABEL).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(FINGER_WRITING_LABEL).performClick()
        composeRule.waitForIdle()

        // The toggle keeps the ink sub-sheet open; close it before touching the canvas.
        composeRule.onNodeWithText(CLOSE_LABEL).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(FINGER_WRITING_LABEL).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun drawStroke(yFraction: Float) {
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.28f, height * yFraction))
            moveTo(Offset(width * 0.45f, height * (yFraction + 0.04f)))
            moveTo(Offset(width * 0.72f, height * yFraction))
            up()
        }
    }

    private companion object {
        const val NOTE_CANVAS_TAG = "note_canvas"
        const val SHEET_TITLE = "노트 메뉴"
        const val INK_SETTINGS_LABEL = "필기 설정"
        const val FINGER_WRITING_LABEL = "손가락 필기"
        const val CLOSE_LABEL = "닫기"
    }
}
