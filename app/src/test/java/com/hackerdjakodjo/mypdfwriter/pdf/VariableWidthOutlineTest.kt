package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.inkPressureWidthFactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pure geometry tests for the original variable-width outline. The painter cannot
 * be exercised on the JVM, so these cover the ring it feeds: closure, determinism,
 * finite/clamped coordinates and the width steering that makes the outline useful.
 */
class VariableWidthOutlineTest {

    private fun line(count: Int): List<NormalizedPoint> =
        List(count) { NormalizedPoint(0.2f + 0.6f * it / maxOf(1, count - 1), 0.5f) }

    @Test
    fun `outline is a closed finite ring and is deterministic`() {
        val points = line(5)
        val widths = FloatArray(5) { 2f + it * 2f }
        val ring = PenStrokeGeometry.variableWidthOutline(points, widths, 800f, 600f, 8, 0.1f)

        assertTrue("ring covers both tracks", ring.size >= points.size * 2)
        assertEquals("ring closes", ring.first(), ring.last())
        ring.forEach { point ->
            assertTrue("x finite", point.x.isFinite())
            assertTrue("y finite", point.y.isFinite())
            assertTrue("x clamped", point.x in 0f..1f)
            assertTrue("y clamped", point.y in 0f..1f)
            assertEquals("outline points carry neutral pressure", 1f, point.pressure, 0f)
        }
        assertEquals(
            ring,
            PenStrokeGeometry.variableWidthOutline(points, widths, 800f, 600f, 8, 0.1f),
        )
    }

    @Test
    fun `single point becomes a round closed cap`() {
        val ring = PenStrokeGeometry.variableWidthOutline(
            listOf(NormalizedPoint(0.5f, 0.5f)),
            floatArrayOf(20f),
            100f,
            100f,
            12,
            0.1f,
        )
        assertEquals(13, ring.size)
        assertEquals(ring.first(), ring.last())
        ring.forEach { point ->
            assertEquals(
                "constant radius",
                0.2f,
                hypot(point.x - 0.5f, point.y - 0.5f),
                0.02f,
            )
        }
    }

    @Test
    fun `half widths steer the two offset tracks`() {
        val points = line(4)
        val widths = floatArrayOf(2f, 14f, 14f, 2f)
        val ring = PenStrokeGeometry.variableWidthOutline(points, widths, 1000f, 1000f, 6, 0.1f)

        fun spreadNear(x: Float): Float =
            ring.filter { abs(it.x - x) < 0.005f }.maxOf { abs(it.y - 0.5f) }

        assertTrue("thick middle is wider than thin end", spreadNear(0.4f) > spreadNear(0.2f) * 2f)
        assertTrue("far end thins again", spreadNear(0.8f) < spreadNear(0.4f))
    }

    @Test
    fun `degenerate inputs stay finite and clamped`() {
        val points = listOf(
            NormalizedPoint(Float.NaN, 9f, Float.NaN),
            NormalizedPoint(2f, -3f, 1f),
        )
        val ring = PenStrokeGeometry.variableWidthOutline(
            points,
            floatArrayOf(Float.NaN, -5f),
            Float.NaN,
            0f,
            -4,
            Float.NaN,
        )
        assertTrue("degenerate stroke still yields a ring", ring.isNotEmpty())
        ring.forEach { point ->
            assertTrue("x finite", point.x.isFinite())
            assertTrue("y finite", point.y.isFinite())
            assertTrue("x clamped", point.x in 0f..1f)
            assertTrue("y clamped", point.y in 0f..1f)
        }
        assertTrue(
            PenStrokeGeometry.variableWidthOutline(emptyList(), FloatArray(0), 100f, 100f, 8, 0.1f).isEmpty(),
        )
    }

    @Test
    fun `precomputed widths survive replay slicing`() {
        val points = listOf(
            NormalizedPoint(0.2f, 0.5f, 1f),
            NormalizedPoint(0.4f, 0.5f, 0.8f),
            NormalizedPoint(0.6f, 0.5f, 0.3f),
            NormalizedPoint(0.8f, 0.5f, 1f),
        )
        val widths = PenStrokeGeometry.outlineWidthSeries(
            points = points,
            series = points.map { inkPressureWidthFactor(it.pressure) },
            variation = PenStrokeGeometry.pressureVariation(points),
            baseWidth = 1f,
            legacyWidth = 1f,
        )
        val prefixWidths = widths.copyOf(3)
        assertEquals(widths.take(3), prefixWidths.toList())

        val fullRing = PenStrokeGeometry.variableWidthOutline(points, widths, 1000f, 1000f, 8, 0.05f)
        val prefixRing = PenStrokeGeometry.variableWidthOutline(points.take(3), prefixWidths, 1000f, 1000f, 8, 0.05f)
        // The first offset and its width come from the same samples in both frames.
        assertEquals(fullRing.first(), prefixRing.first())
    }
}
