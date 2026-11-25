package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

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
                onErase = { _, _, _ -> },
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

    @Test
    fun memoCardTapsAndMovesDoNotCreateStrokesButOutsideDrawingStillWorks() {
        val card = MemoCard(
            id = "e2e-memo",
            text = "탭 테스트",
            tint = MemoTint.PINK,
            x = 0.3f,
            y = 0.3f,
            w = 0.35f,
            h = 0.22f,
        )
        val state = CanvasStrokeState(
            NotePage(id = "memo-canvas-e2e", template = PaperTemplate.BLANK, memoCards = listOf(card)),
        )
        var opened = 0
        var changes = 0

        composeRule.setContent {
            NoteCanvas(
                page = state.page,
                tool = Tool.PEN,
                color = INK,
                strokeWidth = 0.004f,
                fontSize = 0.028f,
                opacity = 1f,
                penOnly = false,
                zoomLock = false,
                memoCards = state.page.memoCards,
                showMemoCardsOnPage = true,
                onOpenMemoCard = { id -> if (id == card.id) opened++ },
                onMemoCardChange = { changes++ },
                onAddStroke = { points, durationMs ->
                    state.receivedStrokes += points.size
                    state.page = state.page.copy(
                        annotations = state.page.annotations + Annotation(
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
                onErase = { _, _, _ -> },
                onRequestText = {},
                onLassoSelect = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composeRule.waitForIdle()
        val bounds = composeRule.onAllNodesWithTag(NOTE_CANVAS_TAG)
            .fetchSemanticsNodes().first().size
        val viewWidth = bounds.width.toFloat()
        val viewHeight = bounds.height.toFloat()
        val pageWidth = PaperBackground.pageWidthPx()
        val pageHeight = PaperBackground.pageHeightPx()
        val fit = min(viewWidth / pageWidth, viewHeight / pageHeight)
        val rect = memoScreenRect(
            card = card,
            fit = fit,
            pan = Offset.Zero,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )

        // A tap on the card body opens its editor and must not leave a stroke.
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(rect.center.x, rect.center.y))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("card body tap must open the editor", 1, opened)
        assertEquals("card body tap must not create a stroke", 0, state.receivedStrokes.size)

        // Holding and dragging the top-left grip commits exactly one move. The
        // grip is only the left portion of the header: the 48dp "닫기" and ⋮
        // buttons occupy the right ~96dp, so the card centre is NOT draggable.
        val gripX = rect.left + rect.width * 0.1f
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(gripX, rect.top + 10f))
            moveTo(Offset(gripX + 42f, rect.top + 30f))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("one drag must commit one card change", 1, changes)
        assertEquals("card drag must not create a stroke", 0, state.receivedStrokes.size)

        // Drawing outside every card is unchanged.
        drawStroke(yFraction = 0.08f)
        composeRule.waitForIdle()
        assertEquals(1, state.receivedStrokes.size)
        assertEquals(1, state.page.annotations.size)
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
    private class CanvasStrokeState(
        initialPage: NotePage = NotePage(id = "canvas-e2e-page", template = PaperTemplate.BLANK),
    ) {
        var page by mutableStateOf(initialPage)
        val receivedStrokes = mutableListOf<Int>()
    }

    private companion object {
        const val NOTE_CANVAS_TAG = "note_canvas"
        val INK = 0xFF1F4E79.toInt()
    }
}
