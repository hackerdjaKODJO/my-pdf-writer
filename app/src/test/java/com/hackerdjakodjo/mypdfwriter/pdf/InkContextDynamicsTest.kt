package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The combined ink context must carry full-stroke speed dynamics so replay can
 * slice them, and neutral ink must keep every legacy value byte-identical.
 */
class InkContextDynamicsTest {

    private fun stroke(
        style: PenStyle,
        points: List<NormalizedPoint>,
    ) = com.hackerdjakodjo.mypdfwriter.data.Annotation(
        type = com.hackerdjakodjo.mypdfwriter.data.AnnotationType.PEN,
        page = 0,
        color = 0xFF222222.toInt(),
        width = 0.004f,
        points = points,
        penStyle = style,
    )

    private val varied = listOf(
        NormalizedPoint(0.1f, 0.5f, 1f),
        NormalizedPoint(0.2f, 0.5f, 0.7f),
        NormalizedPoint(0.3f, 0.5f, 0.4f),
        NormalizedPoint(0.3f, 0.5f, 0.3f),
        NormalizedPoint(0.3f, 0.5f, 0.2f),
        NormalizedPoint(0.3f, 0.5f, 0.1f),
        NormalizedPoint(0.4f, 0.5f, 0.8f),
        NormalizedPoint(0.5f, 0.5f, 1f),
    )

    @Test
    fun `context carries full-stroke speeds matching the pure helper`() {
        PenStyle.entries.forEach { style ->
            val context = AnnotationPainter.inkContext(varied, style, false, 1000f, 1000f)
            val expected = InkDynamics.strokeSpeeds(varied, 1000f, 1000f)
            assertEquals(expected.toList(), context.speeds.toList())
            assertEquals(InkDynamics.speedReference(expected), context.speedReference, 0f)
        }
    }

    @Test
    fun `fountain and brush contexts expose the dwell pool from the full stroke`() {
        listOf(PenStyle.FOUNTAIN, PenStyle.BRUSH).forEach { style ->
            val context = AnnotationPainter.inkContext(varied, style, false, 1000f, 1000f)
            assertEquals(1, context.pools.size)
            assertEquals(5, context.pools[0].index)
            // Replay keeps the pool only once its anchor is revealed, unchanged.
            assertTrue(InkDynamics.poolsForReveal(context.pools, 3).isEmpty())
            assertEquals(context.pools, InkDynamics.poolsForReveal(context.pools, 6))
        }
    }

    @Test
    fun `ballpoint deposit flow is speed weighted while other styles keep the mean`() {
        val ballpoint = AnnotationPainter.inkContext(varied, PenStyle.BALLPOINT, false, 1000f, 1000f)
        val expected = InkDynamics.ballpointSpeedFlow(
            flows = ballpoint.series.map { it.flow },
            speeds = ballpoint.speeds,
            reference = ballpoint.speedReference,
            variation = ballpoint.variation,
        )
        assertEquals(expected, ballpoint.depositFlow, 0f)
        listOf(PenStyle.FOUNTAIN, PenStyle.BRUSH, PenStyle.PENCIL).forEach { style ->
            val context = AnnotationPainter.inkContext(varied, style, false, 1000f, 1000f)
            assertEquals("$style keeps mean flow", context.mean.flow, context.depositFlow, 0f)
        }
    }

    @Test
    fun `ballpoint outline uses the narrow fixed-ball width band`() {
        val context = AnnotationPainter.inkContext(varied, PenStyle.BALLPOINT, false, 1000f, 1000f)
        val wide = PenStrokeGeometry.outlineWidthSeries(
            points = varied,
            series = context.series.map { it.width },
            variation = context.variation,
            baseWidth = 1f,
            legacyWidth = 1f,
        )
        // The lightest interior sample stays much closer to neutral than the
        // old 0.35..1 band allowed.
        val lightIndex = 5
        assertTrue(
            "narrow ${context.outlineWidths[lightIndex]} vs wide ${wide[lightIndex]}",
            context.outlineWidths[lightIndex] > wide[lightIndex],
        )
    }

    @Test
    fun `neutral context keeps every legacy value and has no pools`() {
        val neutral = List(8) { NormalizedPoint(0.1f * it, 0.5f, 1f) }
        PenStyle.entries.forEach { style ->
            val context = AnnotationPainter.inkContext(neutral, style, false, 1000f, 1000f)
            assertTrue("$style neutral", context.neutral)
            assertEquals("$style variation", 0f, context.variation, 0f)
            assertEquals("$style mean", 1f, context.mean.flow, 0f)
            assertEquals("$style deposit flow", 1f, context.depositFlow, 0f)
            assertTrue("$style no pools", context.pools.isEmpty())
            context.outlineWidths.forEach { assertEquals("$style neutral width", 0.5f, it, 0f) }
        }
    }

    @Test
    fun `replay slicing of speeds equals the full-array prefix`() {
        val context = AnnotationPainter.inkContext(varied, PenStyle.BRUSH, false, 1000f, 1000f)
        val prefix = context.speeds.copyOf(4)
        assertEquals(context.speeds.take(4), prefix.toList())
        // The reference stays the FULL stroke's pace, never the prefix's.
        assertEquals(
            InkDynamics.speedReference(context.speeds),
            context.speedReference,
            0f,
        )
    }
}
