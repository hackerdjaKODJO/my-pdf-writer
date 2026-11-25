package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Clean-room checks for the commit-time stroke refinement pipeline:
 * lazy brush (trailing filter), RDP (point reduction) and Chaikin (corner
 * rounding). All helpers are pure so there is no Compose/Android dependency.
 */
class NoteStrokeRefineTest {

    @Test
    fun `refinement constants stay in normalized page units`() {
        assertEquals(0.004f, NOTE_LAZY_BRUSH_RADIUS, 0f)
        assertEquals(0.0008f, NOTE_SIMPLIFY_TOLERANCE, 0f)
    }

    @Test
    fun `lazy brush keeps endpoints count pressure and determinism`() {
        val points = spikeStroke()

        val refined = lazyBrush(points, NOTE_LAZY_BRUSH_RADIUS)

        assertEquals(points.size, refined.size)
        assertEquals(points.first(), refined.first())
        assertEquals(points.last(), refined.last())
        points.indices.forEach { index ->
            assertEquals(points[index].pressure, refined[index].pressure, 1e-6f)
        }
        assertEquals(refined, NoteInk.lazyBrush(points, NOTE_LAZY_BRUSH_RADIUS))
        assertNotEquals(points, refined)
    }

    @Test
    fun `lazy brush damps isolated pointer spikes but keeps the baseline`() {
        val points = spikeStroke()

        val refined = lazyBrush(points, NOTE_LAZY_BRUSH_RADIUS)

        val before = points.maxOf { abs(it.y - 0.5f) }
        val after = refined.maxOf { abs(it.y - 0.5f) }
        assertTrue("spike was $before, refined $after", after < before)
        assertEquals(0.5f, refined.first().y, 1e-6f)
        assertEquals(0.5f, refined.last().y, 1e-6f)
        assertTrue(refined.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `lazy brush with non positive radius leaves the input untouched`() {
        val points = spikeStroke()

        assertSame(points, lazyBrush(points, 0f))
        assertSame(points, lazyBrush(points, -1f))
        assertSame(points, lazyBrush(points, Float.NaN))
        assertSame(points, NoteInk.lazyBrush(points, 0f))
    }

    @Test
    fun `lazy brush never propagates non finite coordinates`() {
        val points = listOf(
            NormalizedPoint(Float.NaN, 0.5f),
            point(0.2f, Float.POSITIVE_INFINITY),
            point(0.4f, 0.5f),
            point(Float.NEGATIVE_INFINITY, 0.6f),
            point(0.8f, 0.5f),
        )

        val refined = lazyBrush(points, NOTE_LAZY_BRUSH_RADIUS)

        assertEquals(points.size, refined.size)
        refined.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
            assertTrue(it.x in 0f..1f && it.y in 0f..1f)
            assertTrue(it.pressure in 0f..1f)
        }
    }

    @Test
    fun `rdp collapses a collinear stroke to its two endpoints`() {
        val points = (0..20).map { index ->
            point(0.10f + index * 0.04f, 0.5f, 0.2f + index * 0.03f)
        }

        val simplified = simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE)

        assertEquals(2, simplified.size)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertEquals(2, NoteInk.simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE).size)
    }

    @Test
    fun `rdp preserves sharp corners endpoints and pressure`() {
        val points = listOf(
            point(0.20f, 0.50f, 0.2f),
            point(0.30f, 0.50f, 0.3f),
            point(0.40f, 0.50f, 0.4f),
            point(0.50f, 0.80f, 0.5f),
            point(0.50f, 0.60f, 0.6f),
            point(0.60f, 0.50f, 0.7f),
            point(0.70f, 0.50f, 0.8f),
            point(0.80f, 0.50f, 0.9f),
        )

        val simplified = simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE)

        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
        assertTrue("corner must survive: $simplified", simplified.any { it.y > 0.7f })
        simplified.forEach { assertTrue(it.pressure in 0f..1f) }
        assertEquals(simplified, simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE))
    }

    @Test
    fun `rdp with non positive tolerance leaves the input untouched`() {
        val points = (0..8).map { index -> point(0.1f + index * 0.01f, 0.5f) }

        assertSame(points, simplifyRdp(points, 0f))
        assertSame(points, simplifyRdp(points, -0.01f))
        assertSame(points, simplifyRdp(points, Float.NaN))
    }

    @Test
    fun `rdp never emits non finite coordinates`() {
        val points = listOf(
            point(Float.NaN, 0.5f),
            point(0.3f, 0.5f),
            point(0.5f, Float.NaN),
            point(0.7f, 0.5f),
            point(0.9f, 0.5f),
        )

        simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE).forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
        }
    }

    @Test
    fun `chaikin preserves endpoints interpolates pressure and doubles samples`() {
        val points = listOf(
            point(0.10f, 0.50f, 0.0f),
            point(0.30f, 0.70f, 0.4f),
            point(0.50f, 0.50f, 0.8f),
            point(0.70f, 0.30f, 1.0f),
            point(0.90f, 0.50f, 0.2f),
        )

        val rounded = chaikin(points, 1)

        assertEquals(points.size * 2, rounded.size)
        assertEquals(points.first(), rounded.first())
        assertEquals(points.last(), rounded.last())
        assertEquals(points[0].x + (points[1].x - points[0].x) * 0.25f, rounded[1].x, 1e-6f)
        assertEquals(points[0].y + (points[1].y - points[0].y) * 0.75f, rounded[2].y, 1e-6f)
        rounded.forEach { assertTrue(it.pressure in 0f..1f) }
        assertEquals(rounded, NoteInk.chaikin(points, 1))
        assertEquals(points.size * 4, chaikin(points, 2).size)
        assertEquals(points.size * 16, chaikin(points, 99).size)
    }

    @Test
    fun `chaikin with zero iterations leaves the input untouched`() {
        val points = listOf(point(0.1f, 0.5f), point(0.9f, 0.5f))

        assertSame(points, chaikin(points, 0))
        assertSame(points, chaikin(points, -3))
        assertSame(points, NoteInk.chaikin(points, 0))
    }

    @Test
    fun `chaikin never emits non finite coordinates`() {
        val points = listOf(
            point(0.1f, Float.NaN),
            point(0.5f, 0.5f),
            point(Float.NaN, 0.9f),
        )

        chaikin(points, 1).forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
            assertTrue(it.pressure in 0f..1f)
        }
    }

    @Test
    fun `refine stroke with all neutral settings is the identity`() {
        val points = spikeStroke()

        assertSame(points, refineStroke(points, 0f, 0f, 0))
        assertSame(points, NoteInk.refineStroke(points, 0f, 0f, 0))
    }

    @Test
    fun `refine stroke runs each stage only when its setting is positive`() {
        val points = spikeStroke()

        assertEquals(chaikin(points, 1), refineStroke(points, 0f, 0f, 1))
        assertEquals(simplifyRdp(points, NOTE_SIMPLIFY_TOLERANCE),
            refineStroke(points, 0f, NOTE_SIMPLIFY_TOLERANCE, 0))
        assertEquals(lazyBrush(points, NOTE_LAZY_BRUSH_RADIUS),
            refineStroke(points, NOTE_LAZY_BRUSH_RADIUS, 0f, 0))
    }

    @Test
    fun `simplify never increases the stored point count`() {
        val points = spikeStroke()

        val smoothOnly = refineStroke(points, NOTE_LAZY_BRUSH_RADIUS, 0f, 1)
        val smoothAndSimplify = refineStroke(points, NOTE_LAZY_BRUSH_RADIUS, NOTE_SIMPLIFY_TOLERANCE, 1)
        assertTrue(
            "simplify ${smoothAndSimplify.size} must not exceed smooth-only ${smoothOnly.size}",
            smoothAndSimplify.size <= smoothOnly.size,
        )

        // A short stroke used to grow when the toggle was turned on: Chaikin ran
        // after RDP and re-added points the reduction had just removed.
        val shortStroke = listOf(point(0.1f, 0.5f), point(0.5f, 0.5f), point(0.9f, 0.5f))
        val shortSmooth = refineStroke(shortStroke, 0f, 0f, 1)
        val shortSimplified = refineStroke(shortStroke, 0f, NOTE_SIMPLIFY_TOLERANCE, 1)
        assertTrue(shortSimplified.size <= shortSmooth.size)

        // Corner rounding must be applied before reduction, so RDP is the last
        // stage and can only drop points.
        assertEquals(
            simplifyRdp(chaikin(points, 1), NOTE_SIMPLIFY_TOLERANCE),
            refineStroke(points, 0f, NOTE_SIMPLIFY_TOLERANCE, 1),
        )
    }

    @Test
    fun `refine stroke is deterministic endpoint preserving and finite`() {
        val points = spikeStroke()

        val refined = refineStroke(points)

        assertEquals(refined, refineStroke(points))
        assertEquals(points.first(), refined.first())
        assertEquals(points.last(), refined.last())
        assertTrue(refined.isNotEmpty())
        refined.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
            assertTrue(it.x in 0f..1f && it.y in 0f..1f)
            assertTrue(it.pressure in 0f..1f)
        }
    }

    @Test
    fun `refine stroke survives corrupt input samples`() {
        val points = listOf(
            point(0.1f, 0.5f),
            point(Float.NaN, 0.4f),
            point(0.5f, Float.POSITIVE_INFINITY),
            point(0.7f, 0.6f),
            point(0.9f, 0.5f),
        )

        val refined = refineStroke(points)

        assertTrue(refined.isNotEmpty())
        refined.forEach {
            assertTrue(it.x.isFinite() && it.y.isFinite())
            assertTrue(it.pressure in 0f..1f)
        }
    }

    @Test
    fun `commit helper honours session toggles and skips recognized shapes`() {
        val points = spikeStroke()

        val twoPointStroke = listOf(points.first(), points.last())
        val singlePointStroke = listOf(points.first())
        assertSame(points, noteRefineForCommit(points, false, false))
        assertSame(points, noteRefineForCommit(points, true, true, skipRefinement = true))
        assertEquals(chaikin(twoPointStroke, 1), noteRefineForCommit(twoPointStroke, true, true))
        assertSame(singlePointStroke, noteRefineForCommit(singlePointStroke, true, true))

        assertEquals(
            refineStroke(points, NOTE_LAZY_BRUSH_RADIUS, 0f, 1),
            noteRefineForCommit(points, true, false),
        )
        assertEquals(
            refineStroke(points, 0f, NOTE_SIMPLIFY_TOLERANCE, 0),
            noteRefineForCommit(points, false, true),
        )
        assertEquals(
            refineStroke(points),
            noteRefineForCommit(points, true, true),
        )
    }

    @Test
    fun `stabilization preserves pressure extrema and original timestamps`() {
        val points = listOf(
            point(0.1f, 0.5f, 0.8f, 0L),
            point(0.3f, 0.5f, 0.7f, 10L),
            point(0.5f, 0.6f, 0.2f, 20L),
            point(0.7f, 0.4f, 0.6f, 30L),
            point(0.9f, 0.5f, 0.9f, 40L),
        )

        val stabilized = NoteInk.stabilize(points, 1f)

        assertEquals(points.first(), stabilized.first())
        assertEquals(points.last(), stabilized.last())
        assertEquals(points.map { it.timeMs }, stabilized.map { it.timeMs })
        assertEquals(points.minOf { it.pressure }, stabilized.minOf { it.pressure }, 0f)
        assertEquals(points.maxOf { it.pressure }, stabilized.maxOf { it.pressure }, 0f)
    }

    @Test
    fun `chaikin preserves pressure extrema and interpolates timestamps`() {
        val points = listOf(
            point(0.1f, 0.5f, 0.6f, 0L),
            point(0.3f, 0.5f, 0.1f, 10L),
            point(0.5f, 0.5f, 0.9f, 20L),
            point(0.7f, 0.5f, 0.4f, 30L),
            point(0.9f, 0.5f, 0.7f, 40L),
        )

        val rounded = chaikin(points, 1)

        assertEquals(points.first(), rounded.first())
        assertEquals(points.last(), rounded.last())
        assertEquals(0.1f, rounded.minOf { it.pressure }, 0f)
        assertEquals(0.9f, rounded.maxOf { it.pressure }, 0f)
        assertTrue(rounded.all { it.timeMs != null })
        val times = rounded.map { it.timeMs!! }
        assertEquals(times, times.sorted())
        assertTrue(rounded[1].timeMs!! in 0L..10L)
    }

    @Test
    fun `rdp keeps pressure extrema with their original timestamps`() {
        val points = listOf(
            point(0.1f, 0.5f, 0.8f, 0L),
            point(0.2f, 0.5f, 0.7f, 10L),
            point(0.4f, 0.5f, 0.2f, 20L),
            point(0.6f, 0.5f, 0.6f, 30L),
            point(0.8f, 0.5f, 0.9f, 40L),
            point(0.9f, 0.5f, 0.5f, 50L),
        )

        val simplified = simplifyRdp(points, 0.01f)

        assertEquals(listOf(0L, 20L, 40L, 50L), simplified.map { it.timeMs })
        assertEquals(0.2f, simplified.minOf { it.pressure }, 0f)
        assertEquals(0.9f, simplified.maxOf { it.pressure }, 0f)
    }

    @Test
    fun `full preparation preserves endpoints pressure extrema and complete timestamps`() {
        val points = spikeStroke()

        val prepared = prepareNoteStroke(points, NoteStrokePreparation())

        assertEquals(points.first(), prepared.first())
        assertEquals(points.last(), prepared.last())
        assertEquals(points.minOf { it.pressure }, prepared.minOf { it.pressure }, 0f)
        assertEquals(points.maxOf { it.pressure }, prepared.maxOf { it.pressure }, 0f)
        assertTrue(prepared.all { it.timeMs != null })
        val times = prepared.map { it.timeMs!! }
        assertEquals(times, times.sorted())
    }

    @Test
    fun `commit helper applies refinement exactly once`() {
        val points = spikeStroke()

        val once = noteRefineForCommit(points, true, true)
        val twice = noteRefineForCommit(once, true, true)

        assertEquals(refineStroke(points), once)
        assertNotEquals(once, twice)
        assertEquals(points.first(), once.first())
        assertEquals(points.last(), once.last())
    }

    private fun point(x: Float, y: Float, pressure: Float = 1f, timeMs: Long? = null): NormalizedPoint =
        NormalizedPoint(x, y, pressure, timeMs = timeMs)

    /** Dense horizontal stroke with occasional isolated vertical spikes. */
    private fun spikeStroke(): List<NormalizedPoint> = (0..100).map { index ->
        val x = 0.1f + index * 0.008f
        val spike = if (index % 10 == 5) 0.004f else 0f
        point(x, 0.5f + spike, 0.3f + index * 0.006f, index * 10L)
    }
}
