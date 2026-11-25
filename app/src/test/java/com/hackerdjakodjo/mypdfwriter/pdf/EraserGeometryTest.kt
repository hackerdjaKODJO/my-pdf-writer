package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class EraserGeometryTest {

    @Test
    fun `normalized radius is shared and pressure response is bounded`() {
        val base = EraserGeometry.NORMALIZED_RADIUS
        assertEquals(0.03f, base, 0f)

        listOf(0f, 0.25f, 0.5f, 1f, Float.NaN).forEach { pressure ->
            assertEquals(base, EraserGeometry.radiusForPressure(base, pressure, false), 0f)
        }
        assertEquals(base * 0.6f, EraserGeometry.radiusForPressure(base, -1f, true), 1e-6f)
        assertEquals(base * 0.6f, EraserGeometry.radiusForPressure(base, 0f, true), 1e-6f)
        assertEquals(base * 1.1f, EraserGeometry.radiusForPressure(base, 0.5f, true), 1e-6f)
        assertEquals(base * 1.6f, EraserGeometry.radiusForPressure(base, 1f, true), 1e-6f)
        assertEquals(base * 1.6f, EraserGeometry.radiusForPressure(base, 2f, true), 1e-6f)
    }

    @Test
    fun `densify trail keeps endpoints and interpolates pressure and timestamps`() {
        val first = NormalizedPoint(0.1f, 0.2f, 0.2f, timeMs = 100L)
        val last = NormalizedPoint(0.9f, 0.8f, 0.8f, timeMs = 900L)

        val dense = EraserGeometry.densifyTrail(listOf(first, last), maxStep = 0.05f)

        assertEquals(first, dense.first())
        assertEquals(last, dense.last())
        assertTrue(dense.size > 2)
        dense.zipWithNext().forEach { (a, b) ->
            assertTrue(hypot(b.x - a.x, b.y - a.y) <= 0.05f + 1e-5f)
        }
        dense.forEach { point ->
            assertTrue(point.pressure in 0.2f..0.8f)
            assertTrue(point.timeMs!! in 100L..900L)
        }
        assertEquals(dense, EraserGeometry.densifyTrail(listOf(first, last), maxStep = 0.05f))
    }

    @Test
    fun `densify trail leaves missing timestamps missing`() {
        val dense = EraserGeometry.densifyTrail(
            listOf(
                NormalizedPoint(0.1f, 0.5f, timeMs = 10L),
                NormalizedPoint(0.9f, 0.5f),
            ),
        )
        assertTrue(dense.all { it.timeMs == null })
    }

    @Test
    fun `point segment distance handles interiors endpoints and degenerate segments`() {
        assertEquals(
            0.25f,
            EraserGeometry.pointSegmentDistanceSquared(0.5f, 0.75f, 0f, 0f, 1f, 0f),
            1e-6f,
        )
        assertEquals(
            0.25f,
            EraserGeometry.pointSegmentDistanceSquared(2f, 0f, 0f, 0f, 1f, 0f),
            1e-6f,
        )
        assertEquals(
            0.25f,
            EraserGeometry.pointSegmentDistanceSquared(0.3f, 0.4f, 0f, 0f, 0f, 0f),
            1e-6f,
        )
    }

    @Test
    fun `sparse eraser trail hits a stroke crossing between pointer events`() {
        val stroke = listOf(
            NormalizedPoint(0.5f, 0.1f),
            NormalizedPoint(0.5f, 0.9f),
        )
        val trail = listOf(
            NormalizedPoint(0.1f, 0.5f),
            NormalizedPoint(0.9f, 0.5f),
        )

        assertTrue(EraserGeometry.eraserHitsStroke(stroke, trail, 0.03f))
        assertFalse(
            EraserGeometry.eraserHitsStroke(
                stroke,
                listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.9f, 0.2f)),
                0.03f,
            ),
        )
    }

    @Test
    fun `sparse stroke is hit by a crossing trail and single points use segment distance`() {
        val stroke = listOf(
            NormalizedPoint(0.2f, 0.5f),
            NormalizedPoint(0.8f, 0.5f),
        )
        val trail = listOf(
            NormalizedPoint(0.5f, 0.1f),
            NormalizedPoint(0.5f, 0.9f),
        )
        assertTrue(EraserGeometry.eraserHitsStroke(stroke, trail, 0.03f))
        assertTrue(
            EraserGeometry.eraserHitsStroke(
                listOf(NormalizedPoint(0.5f, 0.5f)),
                trail,
                0.03f,
            ),
        )
        assertFalse(EraserGeometry.eraserHitsStroke(emptyList(), trail, 0.03f))
        assertFalse(EraserGeometry.eraserHitsStroke(stroke, emptyList(), 0.03f))
        assertFalse(EraserGeometry.eraserHitsStroke(stroke, trail, 0f))
    }
}
