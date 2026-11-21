package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * End-to-end gesture check for the text-selection canvas path. The test uses a
 * square page so normalized word rectangles map predictably onto the fitted
 * canvas, then verifies both the one-tap and rectangle-drag routes.
 */
class PdfCanvasSelectionE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapSelectsTheWordUnderTheFinger() {
        val word = word("tap", 0.35f, 0.40f, 0.65f, 0.60f)
        var selected: List<PdfTextWord> = emptyList()
        setCanvas(listOf(word), onSelection = { selected = it })

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(word), selected)
    }

    @Test
    fun dragSelectsEveryIntersectingWordWithoutLeavingSelectionMode() {
        val first = word("first", 0.30f, 0.40f, 0.48f, 0.58f)
        val second = word("second", 0.52f, 0.40f, 0.70f, 0.58f)
        var selected: List<PdfTextWord> = emptyList()
        setCanvas(listOf(first, second), onSelection = { selected = it })

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            val side = min(width, height).toFloat()
            val left = (width - side) / 2f
            val top = (height - side) / 2f
            down(Offset(left + side * 0.20f, top + side * 0.20f))
            moveTo(Offset(left + side * 0.80f, top + side * 0.80f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(first, second), selected)
    }

    @Test
    fun longPressInViewModeRequestsSelectionAtThePressedWord() {
        val word = word("long", 0.35f, 0.40f, 0.65f, 0.60f)
        var requested: NormalizedPoint? = null
        setCanvas(
            words = listOf(word),
            onSelection = {},
            textSelectMode = false,
            onRequestSelectionAt = { requested = it },
        )

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            longClick(center)
        }
        composeRule.waitForIdle()

        val point = requireNotNull(requested)
        assertEquals(0.5f, point.x, 0.02f)
        assertEquals(0.5f, point.y, 0.02f)
    }

    @Test
    fun longPressInSelectionModeNeverRequestsSelection() {
        val word = word("text", 0.35f, 0.40f, 0.65f, 0.60f)
        var requests = 0
        var selected: List<PdfTextWord> = emptyList()
        setCanvas(
            words = listOf(word),
            onSelection = { selected = it },
            onRequestSelectionAt = { requests++ },
        )

        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            longClick(center)
        }
        composeRule.waitForIdle()

        assertEquals(0, requests)
        assertEquals(listOf(word), selected)
    }

    private fun setCanvas(
        words: List<PdfTextWord>,
        onSelection: (List<PdfTextWord>) -> Unit,
        textSelectMode: Boolean = true,
        onRequestSelectionAt: (NormalizedPoint) -> Unit = {},
    ) {
        composeRule.setContent {
            PdfCanvas(
                bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
                docId = "e2e-doc",
                pageIndex = 0,
                displayWidthPt = 595f,
                displayHeightPt = 595f,
                annotations = emptyList(),
                tool = Tool.VIEW,
                color = 0xFF1F4E79.toInt(),
                strokeWidth = 0.0035f,
                fontSize = 0.024f,
                opacity = 1f,
                zoom = 1f,
                textWords = words,
                textSelectMode = textSelectMode,
                onZoomChange = {},
                onAddStroke = { _, _ -> },
                onEraseStroke = {},
                onRequestText = {},
                onRequestStamp = {},
                onRequestSignature = {},
                onTextSelection = onSelection,
                onRequestSelectionAt = onRequestSelectionAt,
                modifier = Modifier.fillMaxSize().testTag(CANVAS_TAG),
            )
        }
        composeRule.waitForIdle()
    }

    private fun word(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): PdfTextWord = PdfTextWord(
        pageIndex = 0,
        text = text,
        rect = NormalizedRect(left, top, right, bottom),
    )

    private companion object {
        const val CANVAS_TAG = "pdf_canvas_selection_e2e"
    }
}
