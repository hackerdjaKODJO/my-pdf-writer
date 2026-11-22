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
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
    fun `combined ink context derives mean from the single computed series`() {
        val pressures = listOf(1f, 0.82f, 0.46f, 0.1f, 0f, 0.31f, 1f)
        PenStyle.values().forEach { style ->
            val annotation = stroke(style, pressures)
            val context = AnnotationPainter.inkContext(annotation.points, style)

            assertEquals("sample count", annotation.points.size, context.series.size)
            assertEquals("one series shared", AnnotationPainter.annotationInkSeries(annotation), context.series)
            assertEquals("mean folded from that series", AnnotationPainter.meanInkResponse(context.series), context.mean)

            // Byte-identical to the old two-pass derivation, including flow.
            assertEquals("old response", AnnotationPainter.annotationInkResponse(annotation), context.mean)
            assertEquals("meanInkFlow parity", meanInkFlow(annotation.points, style, false), context.mean.flow, 0f)

            // Replay-relevant fields describe the full stroke, never a prefix.
            assertEquals(annotation.points.size, context.totalSamples)
            assertEquals(PenStrokeGeometry.isNeutralPressureStroke(annotation.points), context.neutral)
            assertEquals(PenStrokeGeometry.pressureVariation(annotation.points), context.variation, 0f)
            assertTrue("varied stroke", !context.neutral && context.variation > 0f)
        }
    }

    @Test
    fun `combined ink context keeps neutral legacy response byte identical`() {
        val points = List(9) { NormalizedPoint(0.1f * it, 0.2f, 1f) }
        val context = AnnotationPainter.inkContext(points, PenStyle.FOUNTAIN, isHighlight = false)

        assertEquals(InkResponse(1f, 1f, 1f), context.mean)
        assertTrue(context.neutral)
        assertEquals(0f, context.variation, 0f)
        context.series.forEach { assertEquals(InkResponse(1f, 1f, 1f), it) }
        assertEquals(InkResponse(1f, 1f, 1f), AnnotationPainter.meanInkResponse(emptyList()))
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

    @Test
    fun `ink context precomputes outline widths and replay slices the full array`() {
        val annotation = stroke(PenStyle.FOUNTAIN, listOf(1f, 1f, 0.3f, 0f, 0.7f, 1f))
        val context = AnnotationPainter.inkContext(annotation.points, annotation.penStyle)
        assertEquals(annotation.points.size, context.outlineWidths.size)
        assertTrue("full stroke enables the outline", context.variableOutline)
        assertTrue("full stroke varies", context.variation > 0f)

        val prefix = 2
        val sliced = context.outlineWidths.copyOf(prefix)
        assertEquals("slice equals prefix of precomputed widths", context.outlineWidths.take(prefix), sliced.toList())

        // A freshly derived prefix loses the right neighbour and the end envelope,
        // so it cannot stand in for the sliced full-stroke widths.
        val recomputed = AnnotationPainter.inkContext(annotation.points.take(prefix), annotation.penStyle)
        assertFalse("neutral prefix cannot enable the outline", recomputed.variableOutline)
        assertTrue("replaying must not recompute widths", abs(sliced[1] - recomputed.outlineWidths[1]) > 0.001f)

        val neutral = AnnotationPainter.inkContext(List(5) { NormalizedPoint(0.1f * it, 0.2f, 1f) }, PenStyle.PENCIL)
        assertFalse("neutral stroke keeps its per-style path", neutral.variableOutline)
        neutral.outlineWidths.forEach { assertEquals("neutral half width", 0.5f, it, 0f) }
    }

    @Test
    fun `replay slices full stroke envelopes instead of recomputing from a prefix`() {
        // A zigzag so the nib factors change with direction and the brush
        // envelope changes with stroke length; a straight line cannot expose
        // either bug.
        val points = listOf(
            NormalizedPoint(0.10f, 0.10f, 1f),
            NormalizedPoint(0.20f, 0.40f, 0.8f),
            NormalizedPoint(0.30f, 0.12f, 0.5f),
            NormalizedPoint(0.45f, 0.45f, 0.2f),
            NormalizedPoint(0.60f, 0.10f, 0.6f),
            NormalizedPoint(0.75f, 0.40f, 0.9f),
            NormalizedPoint(0.90f, 0.15f, 1f),
            NormalizedPoint(0.95f, 0.55f, 0.7f),
        )
        val width = 1000f
        val height = 2000f
        val context = AnnotationPainter.inkContext(
            points,
            PenStyle.FOUNTAIN,
            width = width,
            height = height,
        )

        assertEquals(points.size, context.fountainSmoothed.size)
        assertEquals(points.size, context.brushEnvelope.size)
        assertEquals(
            AnnotationPainter.fountainSmoothedFactors(points, width, height).toList(),
            context.fountainSmoothed.toList(),
        )
        assertEquals(
            AnnotationPainter.brushEnvelope(points).toList(),
            context.brushEnvelope.toList(),
        )

        val shown = 3
        val slicedFountain = context.fountainSmoothed.copyOf(shown)
        val slicedBrush = context.brushEnvelope.copyOf(shown)
        assertEquals("reveal slices the full nib factors", context.fountainSmoothed.take(shown), slicedFountain.toList())
        assertEquals("reveal slices the full brush envelope", context.brushEnvelope.take(shown), slicedBrush.toList())

        // A prefix-local recomputation retapers the nib (the last revealed
        // point loses its right neighbour) and reswells the brush (a short
        // stroke has a different envelope), so it cannot stand in for a slice.
        val prefix = points.take(shown)
        assertFalse(
            "fountain reveal must not recompute from the prefix",
            slicedFountain.toList() ==
                AnnotationPainter.fountainSmoothedFactors(prefix, width, height).toList(),
        )
        assertFalse(
            "brush reveal must not recompute from the prefix",
            slicedBrush.toList() == AnnotationPainter.brushEnvelope(prefix).toList(),
        )
    }

    @Test
    fun `graphite policies keep the legacy pencil constants at neutral grain`() {
        val neutral = InkResponse(1f, 1f, 1f)
        assertEquals(-0.55f, PenStrokeGeometry.graphitePassOffset(0, neutral.grain), 0f)
        assertEquals(0f, PenStrokeGeometry.graphitePassOffset(1, neutral.grain), 0f)
        assertEquals(0.55f, PenStrokeGeometry.graphitePassOffset(2, neutral.grain), 0f)
        val phase = 2 * 1.7 + 0 * 4.3
        assertEquals(
            AnnotationPainter.PENCIL_SIDE_GRAIN * kotlin.math.sin(phase).toFloat(),
            AnnotationPainter.PENCIL_SIDE_GRAIN *
                PenStrokeGeometry.graphiteJitterByArc(2, 40f, 0, neutral.grain, phase),
            0f,
        )
        val light = InkResponse(1f, 1f, 0.4f)
        assertTrue(
            "light grain tightens the side passes",
            PenStrokeGeometry.graphitePassOffset(0, light.grain) > -0.55f,
        )
        assertTrue(
            "light grain reseeds the side wobble",
            PenStrokeGeometry.graphiteJitterByArc(2, 40f, 0, light.grain, phase) != kotlin.math.sin(phase).toFloat(),
        )
    }

    @Test
    fun `ink context is reused for unchanged strokes and recomputed when geometry changes`() {
        AnnotationPainter.clearInkContextCache()
        val pen = stroke(PenStyle.FOUNTAIN, listOf(1f, 0.4f, 0.8f, 0.5f))
        val first = AnnotationPainter.inkContextCached(pen, 1000f, 1400f)
        val second = AnnotationPainter.inkContextCached(pen.copy(width = 0.01f), 1000f, 1400f)
        assertSame("unchanged geometry must reuse the derived context", first, second)
        assertTrue(first.outlineWidths.contentEquals(second.outlineWidths))

        val resized = AnnotationPainter.inkContextCached(pen, 1200f, 1400f)
        assertNotSame("a different viewport changes nib factors", first, resized)
        assertSame(
            "copying an annotation shares its immutable points",
            first,
            AnnotationPainter.inkContextCached(pen.copy(opacity = 0.5f), 1000f, 1400f),
        )

        AnnotationPainter.clearInkContextCache()
        assertNotSame("clearing the cache forces a fresh derivation", first,
            AnnotationPainter.inkContextCached(pen, 1000f, 1400f))
    }

    @Test
    fun `ink context cache stays bounded`() {
        AnnotationPainter.clearInkContextCache()
        val keys = (0..80).map { i ->
            Annotation(
                type = AnnotationType.PEN,
                page = 0,
                color = 0,
                width = 0.004f,
                points = listOf(
                    NormalizedPoint(0.1f, 0.2f + i * 0.001f, 0.7f),
                    NormalizedPoint(0.3f, 0.4f, 0.4f),
                ),
                penStyle = PenStyle.BALLPOINT,
            )
        }
        keys.forEach { AnnotationPainter.inkContextCached(it, 1000f, 1400f) }
        val afterEviction = AnnotationPainter.inkContextCached(keys.first(), 1000f, 1400f)
        val fresh = AnnotationPainter.clearInkContextCache().let {
            AnnotationPainter.inkContext(keys.first().points, PenStyle.BALLPOINT, width = 1000f, height = 1400f)
        }
        // The oldest entry is evicted, so a fresh object is returned; the math
        // itself is unchanged.
        assertNotSame(afterEviction, fresh)
        assertTrue(afterEviction.outlineWidths.contentEquals(fresh.outlineWidths))
    }
}
