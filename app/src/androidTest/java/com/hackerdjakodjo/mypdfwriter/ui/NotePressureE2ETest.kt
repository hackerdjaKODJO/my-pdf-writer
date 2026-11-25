package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DEFAULT_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCalibrator
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.inkPressureWidthFactor
import com.hackerdjakodjo.mypdfwriter.data.inkWidthFactor
import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * Instrumentation coverage for the 0.8.35–0.8.38 pressure fixes.
 *
 * Compose touch injection cannot produce a stylus pointer-up with pressure 0, so
 * the calibrator's UP/non-drawing gate is exercised through the same pure capture
 * helper [effectiveInkPressure] the real `NoteCanvas` calls. The Compose tests
 * then draw several strokes through the real canvas and check that non-stylus
 * samples stay neutral and keep the rendered ink width stable.
 */
class NotePressureE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pointerUpAndMemoSamplesDoNotDriftLearnedRangeOrInkWidth() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)

        // Compose reports pointer-up as pressure 0 with pressed = false; some
        // digitizers leave a stale non-zero value on the last event. Neither may
        // teach the session calibrator, or the learned minimum collapses to zero.
        repeat(12) {
            effectiveInkPressure(
                PointerType.Stylus, 0f, true, PressureCurve.LINEAR, calibrator, pressed = false,
            )
            effectiveInkPressure(
                PointerType.Stylus, 0.95f, true, PressureCurve.LINEAR, calibrator, pressed = false,
            )
        }
        // Memo taps, navigation and palm rejection use neutral pointer types.
        listOf(PointerType.Touch, PointerType.Mouse, PointerType.Eraser, PointerType.Unknown)
            .forEach { type ->
                effectiveInkPressure(type, 0f, true, PressureCurve.LINEAR, calibrator)
                effectiveInkPressure(type, 1f, true, PressureCurve.LINEAR, calibrator)
            }
        assertFalse("UP and non-drawing samples must not start a range", calibrator.hasRange)

        // Several strokes with a stylus reporting only 0.2..0.7, each ending on UP.
        repeat(6) {
            listOf(0.2f, 0.45f, 0.7f).forEach { raw ->
                effectiveInkPressure(PointerType.Stylus, raw, true, PressureCurve.LINEAR, calibrator)
            }
            effectiveInkPressure(
                PointerType.Stylus, 0f, true, PressureCurve.LINEAR, calibrator, pressed = false,
            )
            assertEquals("stroke end must not pull the minimum", 0.2f, calibrator.observedMin, 0f)
            assertEquals("stroke end must not pull the maximum", 0.7f, calibrator.observedMax, 0f)
        }

        val mappedMid = effectiveInkPressure(
            PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR, calibrator,
        )
        assertEquals("0.45 is the midpoint of the learned 0.2..0.7 range", 0.5f, mappedMid, 1e-6f)
        val stableWidth = inkWidthFactor(PenStyle.FOUNTAIN, false, mappedMid)
        assertEquals(inkPressureWidthFactor(0.5f), stableWidth, 1e-6f)
        // Had the UP drift been learned (0..0.7), the same sample would map lower
        // and the fountain nib would render visibly thicker.
        val drifted = 0.45f / 0.7f
        assertNotEquals(
            "learned range must keep ink width from drifting",
            stableWidth,
            inkWidthFactor(PenStyle.FOUNTAIN, false, drifted),
            1e-3f,
        )
        assertTrue(stableWidth in MIN_PRESSURE_WIDTH_FACTOR..1f)
    }

    @Test
    fun repeatedStrokesAfterMemoTapKeepNeutralPressureAndStableWidth() {
        val card = MemoCard(
            id = "pressure-memo",
            text = "필압 메모",
            tint = MemoTint.PINK,
            x = 0.3f,
            y = 0.3f,
            w = 0.35f,
            h = 0.22f,
        )
        val state = PressureCanvasState(
            NotePage(id = "pressure-canvas-e2e", template = PaperTemplate.BLANK, memoCards = listOf(card)),
        )

        composeRule.setContent {
            NoteCanvas(
                page = state.page,
                tool = Tool.PEN,
                color = INK,
                strokeWidth = 0.004f,
                fontSize = 0.028f,
                opacity = 1f,
                penStyle = PenStyle.FOUNTAIN,
                penOnly = false,
                zoomLock = false,
                pressureEnabled = true,
                memoCards = state.page.memoCards,
                showMemoCardsOnPage = true,
                onOpenMemoCard = {},
                onMemoCardChange = {},
                onAddStroke = { points, durationMs ->
                    state.strokes += points
                    state.page = state.page.copy(
                        annotations = state.page.annotations + Annotation(
                            type = AnnotationType.PEN,
                            page = 0,
                            color = INK,
                            width = 0.004f,
                            points = points,
                            penStyle = PenStyle.FOUNTAIN,
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

        // A memo tap is a non-drawing sample: the overlay owns it, so no stroke and
        // no pressure sample can reach the calibrator.
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
        composeRule.onNodeWithTag(NOTE_CANVAS_TAG).performTouchInput {
            down(Offset(rect.center.x, rect.center.y))
            up()
        }
        composeRule.waitForIdle()
        assertEquals("memo tap must not create a stroke", 0, state.strokes.size)

        drawStroke(yFraction = 0.08f)
        drawStroke(yFraction = 0.70f)
        drawStroke(yFraction = 0.86f)
        composeRule.waitForIdle()

        assertEquals("three finger strokes must all be committed", 3, state.strokes.size)
        assertEquals(3, state.page.annotations.size)
        val neutralWidth = inkWidthFactor(PenStyle.FOUNTAIN, false, DEFAULT_INK_PRESSURE)
        state.strokes.forEachIndexed { index, points ->
            assertTrue("stroke $index must have samples", points.size >= 2)
            assertTrue(
                "touch samples must store the neutral pressure",
                points.all { it.pressure == DEFAULT_INK_PRESSURE },
            )
            assertEquals(
                "non-stylus strokes must render with the same stable width",
                neutralWidth,
                inkWidthFactor(PenStyle.FOUNTAIN, false, points.first().pressure),
                0f,
            )
        }
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
    private class PressureCanvasState(
        initialPage: NotePage,
    ) {
        var page by mutableStateOf(initialPage)
        val strokes = mutableListOf<List<NormalizedPoint>>()
    }

    private companion object {
        const val NOTE_CANVAS_TAG = "note_canvas"
        const val INK = 0xFF1F4E79.toInt()
    }
}
