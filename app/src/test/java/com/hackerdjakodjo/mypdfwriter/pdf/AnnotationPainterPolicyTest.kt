package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.InkResponse
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.inkPressureWidthFactor
import com.hackerdjakodjo.mypdfwriter.data.inkResponseSeries
import com.hackerdjakodjo.mypdfwriter.data.meanInkFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Pure policy tests for the per-style ink response integration. Android `Canvas`
 * drawing cannot run on the JVM unit-test source set, so the deterministic half of
 * the painter — stroke-wide response derivation and alpha/width scaling — is covered
 * here. Neutral values must reproduce the pre-pressure painter exactly.
 */
class AnnotationPainterPolicyTest {

    private fun stroke(
        style: PenStyle,
        pressures: List<Float>,
        type: AnnotationType = AnnotationType.PEN,
    ): Annotation = Annotation(
        type = type,
        page = 0,
        color = 0xFF222222.toInt(),
        width = 0.004f,
        points = pressures.mapIndexed { index, pressure ->
            NormalizedPoint(0.1f * index, 0.2f, pressure)
        },
        penStyle = style,
    )

    @Test
    fun `annotation response is exact neutral for legacy finger and empty strokes`() {
        val neutral = InkResponse(1f, 1f, 1f)
        val pressures = List(6) { 1f }
        PenStyle.values().forEach { style ->
            assertEquals(
                "$style pen neutral",
                neutral,
                AnnotationPainter.annotationInkResponse(stroke(style, pressures)),
            )
            assertEquals(
                "$style highlight neutral",
                neutral,
                AnnotationPainter.annotationInkResponse(stroke(style, pressures, AnnotationType.HIGHLIGHT)),
            )
            assertEquals(
                "$style empty neutral",
                neutral,
                AnnotationPainter.annotationInkResponse(stroke(style, emptyList())),
            )
        }
    }

    @Test
    fun `annotation response honors the highlight profile over the pen style`() {
        val light = listOf(0f, 0f, 0f)
        val points = stroke(PenStyle.BALLPOINT, light).points

        PenStyle.values().forEach { style ->
            val highlight = AnnotationPainter.annotationInkResponse(
                stroke(style, light, AnnotationType.HIGHLIGHT),
            )
            assertEquals(
                "highlight overrides $style flow",
                meanInkFlow(points, PenStyle.BALLPOINT, true),
                highlight.flow,
                1e-6f,
            )
            assertEquals(
                "highlight overrides $style response",
                AnnotationPainter.annotationInkResponse(stroke(PenStyle.BALLPOINT, light, AnnotationType.HIGHLIGHT)),
                highlight,
            )
        }

        val penFlow = AnnotationPainter.annotationInkResponse(stroke(PenStyle.BALLPOINT, light)).flow
        val highlightFlow = AnnotationPainter.annotationInkResponse(
            stroke(PenStyle.BALLPOINT, light, AnnotationType.HIGHLIGHT),
        ).flow
        assertTrue("highlight is a lighter overlay than a pen", highlightFlow > penFlow)
    }

    @Test
    fun `response factors come from the full stroke so replay keeps ink density`() {
        // First three samples are full pressure; the rest fade to zero. A replay
        // truncated to the first three would look fully inked if it recomputed
        // factors from the visible prefix.
        val mixed = listOf(1f, 1f, 1f, 0f, 0f, 0f)
        val prefix = mixed.take(3)

        val ballpoint = stroke(PenStyle.BALLPOINT, mixed)
        assertEquals(
            1f,
            AnnotationPainter.annotationInkResponse(stroke(PenStyle.BALLPOINT, prefix)).flow,
            0f,
        )
        assertTrue(
            "full stroke is fainter than its neutral prefix",
            AnnotationPainter.annotationInkResponse(ballpoint).flow < 1f,
        )

        val brush = stroke(PenStyle.BRUSH, mixed)
        assertEquals(1f, AnnotationPainter.annotationInkResponse(stroke(PenStyle.BRUSH, prefix)).grain, 0f)
        assertTrue(
            "full brush stroke is drier than its neutral prefix",
            AnnotationPainter.annotationInkResponse(brush).grain < 1f,
        )

        val pencil = stroke(PenStyle.PENCIL, mixed)
        assertEquals(1f, AnnotationPainter.annotationInkResponse(stroke(PenStyle.PENCIL, prefix)).width, 0f)
        assertTrue(
            "full pencil stroke is thinner than its neutral prefix",
            AnnotationPainter.annotationInkResponse(pencil).width < 1f,
        )
    }

    @Test
    fun `alpha and width policies are exact identities at neutral pressure`() {
        assertEquals(200, AnnotationPainter.scaledInkAlpha(200, 1f))
        assertEquals(100, AnnotationPainter.scaledInkAlpha(200, 0.5f))
        // The old halo used (opacityAlpha * 0.20f).roundToInt().coerceIn(0, 255).
        assertEquals((128 * 0.20f).roundToInt(), AnnotationPainter.brushHaloAlpha(128, 1f, 1f))

        assertEquals(10f * AnnotationPainter.PENCIL_WIDTH_FACTOR, AnnotationPainter.pencilStrokeWidth(10f, 1f), 0f)
        assertEquals(
            (200 * AnnotationPainter.PENCIL_CENTER_ALPHA).roundToInt(),
            AnnotationPainter.pencilPassAlpha(200, 1, 1f),
        )
        assertEquals(
            (200 * AnnotationPainter.PENCIL_SIDE_ALPHA).roundToInt(),
            AnnotationPainter.pencilPassAlpha(200, 0, 1f),
        )
        assertEquals(AnnotationPainter.PENCIL_CENTER_GRAIN, AnnotationPainter.pencilPassGrain(1, 1f), 0f)
        assertEquals(AnnotationPainter.PENCIL_SIDE_GRAIN, AnnotationPainter.pencilPassGrain(0, 1f), 0f)
    }

    @Test
    fun `alpha and width policies fade thin or dry at light pressure`() {
        assertTrue(AnnotationPainter.scaledInkAlpha(200, 0.45f) < 200)
        assertTrue(
            AnnotationPainter.brushHaloAlpha(128, 0.4f, 0.5f) <
                AnnotationPainter.brushHaloAlpha(128, 1f, 1f),
        )
        assertTrue(
            AnnotationPainter.pencilStrokeWidth(10f, 0.55f) <
                AnnotationPainter.pencilStrokeWidth(10f, 1f),
        )
        assertTrue(
            AnnotationPainter.pencilPassAlpha(200, 1, 0.35f) <
                AnnotationPainter.pencilPassAlpha(200, 1, 1f),
        )
        assertTrue(
            AnnotationPainter.pencilPassGrain(0, 0.4f) < AnnotationPainter.pencilPassGrain(0, 1f),
        )
    }

    @Test
    fun `fountain and brush widths match the legacy pressure curve at every pressure`() {
        val pressures = listOf(-2f, 0f, 0.07f, 0.25f, 0.5f, 0.9f, 1f, 3f, Float.NaN)
        val points = pressures.map { NormalizedPoint(0.1f, 0.2f, it) }
        val sizes = setOf(1, 2, pressures.size)

        sizes.forEach { size ->
            val sample = points.take(size)
            val raw = sample.map { inkPressureWidthFactor(it.pressure) }
            val expected = if (size < 3) {
                raw
            } else {
                raw.indices.map { index ->
                    val previous = raw[(index - 1).coerceAtLeast(0)]
                    val current = raw[index]
                    val next = raw[(index + 1).coerceAtMost(raw.lastIndex)]
                    (previous + current * 2f + next) * 0.25f
                }
            }
            listOf(PenStyle.FOUNTAIN, PenStyle.BRUSH).forEach { style ->
                val widths = inkResponseSeries(sample, style, false).map { it.width }
                assertEquals("$style/$size width count", expected.size, widths.size)
                expected.indices.forEach { index ->
                    assertEquals("$style/$size width[$index]", expected[index], widths[index], 0f)
                }
            }
        }
    }
}
