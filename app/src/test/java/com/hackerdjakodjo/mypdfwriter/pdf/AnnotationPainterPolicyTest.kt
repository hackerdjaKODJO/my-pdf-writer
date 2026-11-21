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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

    @Test
    fun `painter constants share the geometry helper source of truth`() {
        assertEquals(PenStrokeGeometry.PENCIL_WIDTH_FACTOR, AnnotationPainter.PENCIL_WIDTH_FACTOR, 0f)
        assertEquals(PenStrokeGeometry.PENCIL_CENTER_ALPHA, AnnotationPainter.PENCIL_CENTER_ALPHA, 0f)
        assertEquals(PenStrokeGeometry.PENCIL_SIDE_ALPHA, AnnotationPainter.PENCIL_SIDE_ALPHA, 0f)
        assertEquals(PenStrokeGeometry.PENCIL_CENTER_GRAIN, AnnotationPainter.PENCIL_CENTER_GRAIN, 0f)
        assertEquals(PenStrokeGeometry.PENCIL_SIDE_GRAIN, AnnotationPainter.PENCIL_SIDE_GRAIN, 0f)
    }

    @Test
    fun `neutral pen strokes switch off every dynamic pen effect`() {
        PenStyle.values().forEach { style ->
            val annotation = stroke(style, List(8) { 1f })
            val variation = PenStrokeGeometry.pressureVariation(annotation.points)
            assertEquals("$style neutral variation", 0f, variation, 0f)
            assertTrue("$style neutral detection", PenStrokeGeometry.isNeutralPressureStroke(annotation.points))
            assertEquals("$style neutral mean", InkResponse(1f, 1f, 1f), AnnotationPainter.annotationInkResponse(annotation))
            AnnotationPainter.annotationInkSeries(annotation).forEach { response ->
                assertEquals("$style neutral sample", InkResponse(1f, 1f, 1f), response)
                assertFalse("$style neutral ballpoint skip", PenStrokeGeometry.ballpointSkip(4, response.flow))
                assertEquals("$style neutral dot alpha", 255, PenStrokeGeometry.ballpointDotAlpha(response.flow))
                assertEquals("$style neutral taper", 1f, PenStrokeGeometry.fountainTaper(0f, variation), 0f)
            }
            assertEquals(
                "$style neutral centre alpha",
                AnnotationPainter.PENCIL_CENTER_ALPHA,
                PenStrokeGeometry.pencilSegmentAlpha(1, 1f),
                0f,
            )
            assertEquals(
                "$style neutral side alpha",
                AnnotationPainter.PENCIL_SIDE_ALPHA,
                PenStrokeGeometry.pencilSegmentAlpha(0, 1f),
                0f,
            )
            assertEquals(
                "$style neutral pencil width",
                10f * AnnotationPainter.PENCIL_WIDTH_FACTOR,
                AnnotationPainter.pencilStrokeWidth(10f, 1f),
                0f,
            )
        }
    }

    @Test
    fun `full-stroke series keeps visible prefix samples stable during replay`() {
        // The last sample of a truncated series loses its right neighbour, which is
        // why the painter derives factors from the full stroke before revealing.
        val full = stroke(PenStyle.BALLPOINT, listOf(1f, 1f, 1f, 0f, 0f, 0f))
        val prefix = stroke(PenStyle.BALLPOINT, listOf(1f, 1f, 1f))
        val fullSeries = AnnotationPainter.annotationInkSeries(full)
        val recomputed = AnnotationPainter.annotationInkSeries(prefix)

        assertEquals(6, fullSeries.size)
        assertEquals(3, recomputed.size)
        assertEquals(fullSeries[0].flow, recomputed[0].flow, 0f)
        assertEquals(fullSeries[1].flow, recomputed[1].flow, 0f)
        assertTrue(
            "truncation changes the boundary sample",
            abs(fullSeries[2].flow - recomputed[2].flow) > 0.01f,
        )
        assertTrue(
            "full response is fainter than a recomputed prefix",
            AnnotationPainter.annotationInkResponse(full).flow <
                AnnotationPainter.annotationInkResponse(prefix).flow,
        )
        assertEquals(
            "replay slices the full series, never a recomputed one",
            fullSeries.take(3),
            AnnotationPainter.annotationInkSeries(full).take(3),
        )
    }

    @Test
    fun `varied strokes expose local flow width and grain for segment rendering`() {
        val pencil = stroke(PenStyle.PENCIL, listOf(1f, 0.85f, 0.6f, 0.3f, 0f, 0.4f))
        val pencilSeries = AnnotationPainter.annotationInkSeries(pencil)
        assertTrue("local flow varies", pencilSeries.map { it.flow }.toSet().size > 1)
        assertTrue("local width varies", pencilSeries.map { it.width }.toSet().size > 1)
        assertTrue("local grain varies", pencilSeries.map { it.grain }.toSet().size > 1)
        assertTrue("variation detected", PenStrokeGeometry.pressureVariation(pencil.points) > 0f)
        assertTrue(
            "pencil segment alphas vary",
            pencilSeries.map { PenStrokeGeometry.pencilSegmentAlpha(0, it.flow) }.toSet().size > 1,
        )

        val ballpoint = stroke(PenStyle.BALLPOINT, listOf(1f, 0.9f, 0.3f, 0f, 0.2f, 1f))
        val lightFlow = AnnotationPainter.annotationInkSeries(ballpoint).minOf { it.flow }
        assertTrue("light ballpoint flow is genuinely light", lightFlow < PenStrokeGeometry.BALLPOINT_LIGHT_FLOW)
        assertTrue(
            "light ballpoint can skip contact",
            (1..32).any { PenStrokeGeometry.ballpointSkip(it, lightFlow) },
        )
    }
}
