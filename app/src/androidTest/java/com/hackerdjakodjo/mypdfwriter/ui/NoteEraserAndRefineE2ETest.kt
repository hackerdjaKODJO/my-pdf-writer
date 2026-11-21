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
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * Instrumentation coverage for the 0.8.38 eraser and commit-time refinement
 * fixes: TEXT/STAMP annotations without geometry, a preview that matches the
 * committed erase region, and "점 간소화" never growing a short stroke.
 */
class NoteEraserAndRefineE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun eraserWithPointlessTextAndStampKeepsOtherStrokesIntact() {
        val state = EraserCanvasState(
            NotePage(
                id = "eraser-canvas-e2e",
                template = PaperTemplate.BLANK,
                annotations = testAnnotations(),
            ),
        )
        var eraseCalls = 0

        composeRule.setContent {
            NoteCanvas(
                page = state.page,
                tool = Tool.ERASER,
                color = INK,
                strokeWidth = 0.004f,
                fontSize = 0.028f,
                opacity = 1f,
                penOnly = false,
                zoomLock = false,
                onAddStroke = { _, _ -> },
                onErase = { trail ->
                    eraseCalls++
                    val result = NoteInk.eraseAnnotations(
                        annotations = state.page.annotations,
                        trail = trail,
                        radius = NOTE_ERASE_RADIUS,
                        partial = true,
                    )
                    state.page = state.page.copy(annotations = result.annotations)
                },
                onRequestText = {},
                onLassoSelect = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composeRule.waitForIdle()

        eraseThrough(0.27f, 0.30f, 0.33f, 0.30f)
        composeRule.waitForIdle()

        assertEquals("the eraser trail must be committed", 1, eraseCalls)
        assertEquals(3, state.page.annotations.size)
        val ids = state.page.annotations.map { it.id }.toSet()
        assertTrue("pointless TEXT must survive without crashing", EMPTY_TEXT_ID in ids)
        assertTrue("pointless STAMP must survive without crashing", EMPTY_STAMP_ID in ids)
        assertTrue("strokes away from the trail must be untouched", FAR_PEN_ID in ids)
        assertFalse("anchored TEXT under the trail is erased", ANCHORED_TEXT_ID in ids)
        assertEquals(
            2,
            state.page.annotations.first { it.id == FAR_PEN_ID }.points.size,
        )
    }

    @Test
    fun eraserHitTestTreatsEmptyTextAndStampAsMisses() {
        val result = NoteInk.eraseAnnotations(
            annotations = testAnnotations(),
            trail = listOf(
                NormalizedPoint(0.27f, 0.30f),
                NormalizedPoint(0.33f, 0.30f),
            ),
            radius = NOTE_ERASE_RADIUS,
            partial = true,
        )

        val ids = result.annotations.map { it.id }.toSet()
        assertEquals("only the anchored TEXT is hit", 1, result.removed)
        assertFalse(ids.contains(ANCHORED_TEXT_ID))
        assertTrue(ids.containsAll(listOf(EMPTY_TEXT_ID, EMPTY_STAMP_ID, FAR_PEN_ID)))
        assertEquals(0.03f, NOTE_ERASE_RADIUS, 0f)
    }

    @Test
    fun eraserPreviewRadiiMatchTheNormalizedEraseRegion() {
        val pageWidth = 595f
        val pageHeight = 842f // portrait: an old W-based circle was too short here
        val center = NormalizedPoint(0.5f, 0.25f)

        val oval = noteEraserPreviewOval(center, pageWidth, pageHeight)

        val radiusX = NOTE_ERASE_RADIUS * pageWidth
        val radiusY = NOTE_ERASE_RADIUS * pageHeight
        assertEquals(center.x * pageWidth - radiusX, oval.left, 1e-5f)
        assertEquals(center.x * pageWidth + radiusX, oval.right, 1e-5f)
        assertEquals(center.y * pageHeight - radiusY, oval.top, 1e-5f)
        assertEquals(center.y * pageHeight + radiusY, oval.bottom, 1e-5f)
        assertEquals(2f * radiusX, oval.right - oval.left, 1e-3f)
        assertEquals(2f * radiusY, oval.bottom - oval.top, 1e-3f)
        assertTrue("portrait preview must be an ellipse: $oval", oval.bottom - oval.top > oval.right - oval.left)

        val landscape = noteEraserPreviewOval(center, 842f, 595f)
        assertEquals(2f * NOTE_ERASE_RADIUS * 842f, landscape.right - landscape.left, 1e-3f)
        assertEquals(2f * NOTE_ERASE_RADIUS * 595f, landscape.bottom - landscape.top, 1e-3f)
        assertTrue(landscape.right - landscape.left > landscape.bottom - landscape.top)
    }

    @Test
    fun simplifyPointsNeverIncreasesShortStrokePointCount() {
        val shortStroke = listOf(
            NormalizedPoint(0.10f, 0.50f),
            NormalizedPoint(0.50f, 0.52f),
            NormalizedPoint(0.90f, 0.50f),
        )
        val smoothOnly = noteRefineForCommit(shortStroke, smoothHandwriting = true, simplifyPoints = false)
        val smoothAndSimplified = noteRefineForCommit(shortStroke, smoothHandwriting = true, simplifyPoints = true)
        assertTrue(
            "simplify ${smoothAndSimplified.size} must not exceed smooth-only ${smoothOnly.size}",
            smoothAndSimplified.size <= smoothOnly.size,
        )
        // RDP is the final stage, so corner rounding cannot re-add removed points.
        assertEquals(
            refineStroke(shortStroke, NOTE_LAZY_BRUSH_RADIUS, NOTE_SIMPLIFY_TOLERANCE, 1),
            smoothAndSimplified,
        )
        assertTrue(smoothAndSimplified.isNotEmpty())
        assertEquals(shortStroke.first(), smoothAndSimplified.first())
        assertEquals(shortStroke.last(), smoothAndSimplified.last())
    }

    @Test
    fun simplifyCommitCallbackNeverGrowsTheStoredStroke() {
        val state = RefineCanvasState()
        composeRule.setContent {
            NoteCanvas(
                page = NotePage(id = "refine-canvas-e2e", template = PaperTemplate.BLANK),
                tool = Tool.PEN,
                color = INK,
                strokeWidth = 0.004f,
                fontSize = 0.028f,
                opacity = 1f,
                penOnly = false,
                zoomLock = false,
                onAddStroke = { points, durationMs ->
                    // Mirrors the editor's commit-time refinement with 점 간소화 on.
                    state.rawCounts += points.size
                    val committed = noteRefineForCommit(
                        points = points,
                        smoothHandwriting = false,
                        simplifyPoints = true,
                    )
                    state.committedCounts += committed.size
                    state.durations += durationMs.coerceAtLeast(0L)
                },
                onErase = {},
                onRequestText = {},
                onLassoSelect = { _, _ -> },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composeRule.waitForIdle()

        drawShortStroke(yFraction = 0.30f)
        drawShortStroke(yFraction = 0.62f)
        composeRule.waitForIdle()

        assertEquals(2, state.rawCounts.size)
        assertEquals(2, state.committedCounts.size)
        state.rawCounts.indices.forEach { index ->
            assertTrue(state.committedCounts[index] <= state.rawCounts[index])
            assertTrue(state.committedCounts[index] >= 2)
        }
    }

    /** Erases through two normalized page coordinates using real touch input. */
    private fun eraseThrough(x1: Float, y1: Float, x2: Float, y2: Float) {
        val bounds = composeRule.onAllNodesWithTag(NOTE_CANVAS_TAG)
            .fetchSemanticsNodes().first().size
        val viewWidth = bounds.width.toFloat()
        val viewHeight = bounds.height.toFloat()
        val pageWidth = PaperBackground.pageWidthPx()
        val pageHeight = PaperBackground.pageHeightPx()
        val fit = min(viewWidth / pageWidth, viewHeight / pageHeight)
        fun screen(x: Float, y: Float) = Offset(
            viewWidth / 2f + (x - 0.5f) * pageWidth * fit,
            viewHeight / 2f + (y - 0.5f) * pageHeight * fit,
        )
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(screen(x1, y1))
            moveTo(screen(x2, y2))
            up()
        }
    }

    private fun drawShortStroke(yFraction: Float) {
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.32f, height * yFraction))
            moveTo(Offset(width * 0.45f, height * yFraction))
            moveTo(Offset(width * 0.58f, height * yFraction))
            up()
        }
    }

    private class EraserCanvasState(initialPage: NotePage) {
        var page by mutableStateOf(initialPage)
    }

    private class RefineCanvasState {
        val rawCounts = mutableListOf<Int>()
        val committedCounts = mutableListOf<Int>()
        val durations = mutableListOf<Long>()
    }

    private companion object {
        const val NOTE_CANVAS_TAG = "note_canvas"
        const val INK = 0xFF1F4E79.toInt()
        const val EMPTY_TEXT_ID = "text-without-points"
        const val EMPTY_STAMP_ID = "stamp-without-points"
        const val ANCHORED_TEXT_ID = "text-with-anchor"
        const val FAR_PEN_ID = "pen-far-away"

        fun testAnnotations(): List<Annotation> = listOf(
            Annotation(
                id = EMPTY_TEXT_ID,
                type = AnnotationType.TEXT,
                page = 0,
                color = INK,
                width = 0.01f,
                text = "", // blank text keeps the renderer from needing an anchor
                points = emptyList(),
            ),
            Annotation(
                id = EMPTY_STAMP_ID,
                type = AnnotationType.STAMP,
                page = 0,
                color = INK,
                width = 0.01f,
                stamp = "",
                points = emptyList(),
            ),
            Annotation(
                id = ANCHORED_TEXT_ID,
                type = AnnotationType.TEXT,
                page = 0,
                color = INK,
                width = 0.01f,
                text = "지워지는 글상자",
                points = listOf(NormalizedPoint(0.30f, 0.30f)),
            ),
            Annotation(
                id = FAR_PEN_ID,
                type = AnnotationType.PEN,
                page = 0,
                color = INK,
                width = 0.004f,
                points = listOf(
                    NormalizedPoint(0.75f, 0.70f),
                    NormalizedPoint(0.85f, 0.75f),
                ),
            ),
        )
    }
}
