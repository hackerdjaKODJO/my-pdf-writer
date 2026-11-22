package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression for "after zooming, pushing the page right does nothing" in the
 * single-page viewer. Before the fix, double tap RESET zoom to fit (zero pan
 * range), and an off-centre pinch could park pan on its clamp so a rightward
 * drag clamped straight back to the same edge.
 *
 * Movement is sampled with a stationary long press: [onRequestSelectionAt]
 * reports the page coordinate under a fixed screen point, so a rightward page
 * translation makes that normalized x decrease.
 */
class PdfCanvasZoomPanE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setCanvas(zoomState: androidx.compose.runtime.MutableState<Float>) {
        composeRule.setContent {
            PdfCanvas(
                bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
                docId = "zoom-pan-e2e",
                pageIndex = 0,
                displayWidthPt = 595f,
                displayHeightPt = 595f,
                annotations = emptyList(),
                tool = Tool.VIEW,
                color = 0xFF1F4E79.toInt(),
                strokeWidth = 0.0035f,
                fontSize = 0.024f,
                opacity = 1f,
                zoom = zoomState.value,
                cropInsets = CropInsets.NONE,
                onZoomChange = { zoomState.value = it },
                onAddStroke = { _, _ -> },
                onEraseStroke = {},
                onRequestText = {},
                onRequestStamp = {},
                onRequestSignature = {},
                onRequestSelectionAt = { requested = it },
                modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
            )
        }
        composeRule.waitForIdle()
    }

    private var requested: NormalizedPoint? = null

    private fun centrePoint(): NormalizedPoint {
        requested = null
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { longClick(center) }
        composeRule.waitForIdle()
        return requireNotNull(requested) { "long press must reach the page hit-test" }
    }

    private fun dragRight(fraction: Float) {
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(width * fraction, 0f), delayMillis = 16)
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun doubleTapZoomsThenRightDragMovesThePageRight() {
        val zoomState = mutableStateOf(1f)
        setCanvas(zoomState)

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        assertEquals(
            "double tap must zoom in, not reset to fit",
            READER_ZOOM_DOUBLE_TAP,
            zoomState.value,
            0.0001f,
        )

        val before = centrePoint()
        dragRight(0.30f)
        val after = centrePoint()
        assertTrue(
            "rightward drag must move the zoomed page right: $before -> $after",
            before.x - after.x > 0.01f,
        )
    }

    @Test
    fun offCentrePinchThenRightDragStillMovesThePage() {
        val zoomState = mutableStateOf(1f)
        setCanvas(zoomState)

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            val cy = height / 2f
            down(0, Offset(width * 0.08f, cy))
            down(1, Offset(width * 0.15f, cy))
            moveTo(0, Offset(width * 0.04f, cy), delayMillis = 16)
            moveTo(1, Offset(width * 0.35f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        assertTrue("pinch must zoom in", zoomState.value > 1f)

        val afterPinch = centrePoint()
        dragRight(0.25f)
        val afterDrag = centrePoint()
        assertTrue(
            "rightward drag must move the page after an off-centre pinch: " +
                "$afterPinch -> $afterDrag",
            afterPinch.x - afterDrag.x > 0.01f,
        )
    }

    @Test
    fun fitZoomSwipeTurnsPageButZoomedDragDoesNot() {
        var previous = 0
        var next = 0
        val zoomState = mutableStateOf(1f)
        composeRule.setContent {
            PdfCanvas(
                bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
                docId = "swipe-page-e2e",
                pageIndex = 0,
                displayWidthPt = 595f,
                displayHeightPt = 595f,
                annotations = emptyList(),
                tool = Tool.VIEW,
                color = 0xFF1F4E79.toInt(),
                strokeWidth = 0.0035f,
                fontSize = 0.024f,
                opacity = 1f,
                zoom = zoomState.value,
                cropInsets = CropInsets.NONE,
                onZoomChange = { zoomState.value = it },
                onAddStroke = { _, _ -> },
                onEraseStroke = {},
                onRequestText = {},
                onRequestStamp = {},
                onRequestSignature = {},
                onRequestSelectionAt = { requested = it },
                onPreviousPage = { previous++ },
                onNextPage = { next++ },
                modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
            )
        }
        composeRule.waitForIdle()

        fun swipe(dx: Float) {
            composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
                down(center)
                moveTo(center + Offset(dx, 0f), delayMillis = 16)
                up()
            }
            composeRule.waitForIdle()
        }

        val viewport = composeRule.onRoot().fetchSemanticsNode().size.width
        swipe(-viewport * 0.35f)
        assertEquals("swipe left must request the next page", 1, next)
        assertEquals(0, previous)

        swipe(viewport * 0.35f)
        assertEquals("swipe right must request the previous page", 1, previous)

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
        swipe(-viewport * 0.30f)
        assertEquals("zoomed swipe must pan, not turn pages", 1, next)
        assertEquals(1, previous)
    }

    private companion object {
        const val CANVAS_TAG = "canvas_zoom_pan_e2e"
    }
}
