package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for note-stroke loss in [NoteCanvas].
 *
 * The stroke callback deliberately closes over the page snapshot of the current
 * composition, the same way `NoteEditorScreen` used to. If the `pointerInput`
 * coroutine keeps the callback captured when it was launched, the second stroke
 * rebuilds the page from the stale (empty) snapshot and the first stroke is lost.
 */
class NoteCanvasStrokeE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun twoQuickFingerStrokesBothRemainOnThePage() {
        val state = CanvasStrokeState()

        composeRule.setContent {
            // Plain composition value: this is the stale closure under test.
            val pageSnapshot = state.page
            NoteCanvas(
                page = pageSnapshot,
                tool = Tool.PEN,
                color = INK,
                strokeWidth = 0.004f,
                fontSize = 0.028f,
                opacity = 1f,
                penOnly = false,
                zoomLock = false,
                onAddStroke = { points, durationMs ->
                    state.receivedStrokes += points.size
                    state.page = pageSnapshot.copy(
                        annotations = pageSnapshot.annotations + Annotation(
                            type = AnnotationType.PEN,
                            page = 0,
                            color = INK,
                            width = 0.004f,
                            points = points,
                            createdAt = System.currentTimeMillis(),
                            durationMs = durationMs.coerceAtLeast(0L),
                        ),
                    )
                },
                onErase = {},
                onRequestText = {},
                onLassoSelect = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.waitForIdle()
        drawStroke(yFraction = 0.32f)
        composeRule.waitForIdle()
        drawStroke(yFraction = 0.58f)
        composeRule.waitForIdle()

        assertEquals(
            "both strokes must be offered to the add-stroke callback",
            2,
            state.receivedStrokes.size,
        )
        assertEquals(
            "both strokes must remain in the page state",
            2,
            state.page.annotations.size,
        )
        assertEquals(
            "each retained stroke must be a distinct annotation",
            2,
            state.page.annotations.map { it.id }.toSet().size,
        )
        assertTrue(state.page.annotations.all { it.points.size >= 2 })
    }

    private fun drawStroke(yFraction: Float) {
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.28f, height * yFraction))
            moveTo(Offset(width * 0.45f, height * (yFraction + 0.04f)))
            moveTo(Offset(width * 0.72f, height * yFraction))
            up()
        }
    }

    /** Mirrors the editor's state-backed annotation page. */
    private class CanvasStrokeState {
        var page by mutableStateOf(
            NotePage(id = "canvas-e2e-page", template = PaperTemplate.BLANK),
        )
        val receivedStrokes = mutableListOf<Int>()
    }

    private companion object {
        const val NOTE_CANVAS_TAG = "note_canvas"
        val INK = 0xFF1F4E79.toInt()
    }
}
