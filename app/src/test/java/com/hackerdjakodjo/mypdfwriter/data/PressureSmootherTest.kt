package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Temporal pressure low-pass: digitizer noise must not shimmer the ink, and
 * neutral/full-press identity must survive end to end.
 */
class PressureSmootherTest {

    @Test
    fun `uniform neutral pressure stays exactly neutral`() {
        val smoother = PressureSmoother()
        repeat(12) {
            assertEquals(1f, smoother.filter(1f), 0f)
        }
    }

    @Test
    fun `first sample of a stroke passes through exactly`() {
        val smoother = PressureSmoother()
        assertEquals(0.42f, smoother.filter(0.42f), 0f)
        smoother.reset()
        assertEquals(0.77f, smoother.filter(0.77f), 0f)
    }

    @Test
    fun `full press snaps back to neutral immediately`() {
        val smoother = PressureSmoother()
        smoother.filter(0.3f)
        smoother.filter(0.4f)
        assertEquals(1f, smoother.filter(1f), 0f)
        // The next light sample eases down from neutral, never above it.
        val next = smoother.filter(0.5f)
        assertTrue("next $next", next in 0.5f..1f)
    }

    @Test
    fun `sensor jitter is reduced, not amplified`() {
        val smoother = PressureSmoother()
        // A steady 0.5 press with alternating ±0.05 digitizer noise.
        val raw = List(40) { index -> 0.5f + if (index % 2 == 0) 0.05f else -0.05f }
        val filtered = raw.map { smoother.filter(it) }
        // Skip the first sample (exact by contract) when measuring spread.
        val rawSpread = raw.drop(1).maxOrNull()!! - raw.drop(1).minOrNull()!!
        val outSpread = filtered.drop(1).maxOrNull()!! - filtered.drop(1).minOrNull()!!
        assertTrue("output spread $outSpread < raw spread $rawSpread", outSpread < rawSpread * 0.5f)
        filtered.forEach { assertTrue("bounded $it", it in 0f..1f) }
    }

    @Test
    fun `pressing harder responds faster than lifting`() {
        val press = PressureSmoother()
        press.filter(0.2f)
        val pressStep = press.filter(0.8f) - 0.2f
        val lift = PressureSmoother()
        lift.filter(0.8f)
        val liftStep = 0.8f - lift.filter(0.2f)
        assertTrue("attack step $pressStep > release step $liftStep", pressStep > liftStep)
    }

    @Test
    fun `converges toward a steady press`() {
        val smoother = PressureSmoother()
        smoother.filter(0.1f)
        var last = 0.1f
        repeat(20) {
            last = smoother.filter(0.7f)
        }
        assertTrue("converged $last", abs(last - 0.7f) < 0.01f)
    }

    @Test
    fun `non-finite input is neutral and never teaches the filter`() {
        val smoother = PressureSmoother()
        assertEquals(1f, smoother.filter(Float.NaN), 0f)
        assertEquals(1f, smoother.filter(Float.POSITIVE_INFINITY), 0f)
        // The filter still has no level: the next finite sample is exact.
        assertEquals(0.35f, smoother.filter(0.35f), 0f)
    }

    @Test
    fun `out of range samples are clamped`() {
        val smoother = PressureSmoother()
        assertEquals(0f, smoother.filter(-2f), 0f)
        smoother.reset()
        assertEquals(1f, smoother.filter(9f), 0f)
    }

    @Test
    fun `reset starts a fresh stroke`() {
        val smoother = PressureSmoother()
        smoother.filter(0.9f)
        smoother.filter(0.9f)
        smoother.reset()
        assertEquals(0.2f, smoother.filter(0.2f), 0f)
    }

    @Test
    fun `deterministic for the same sample sequence`() {
        val raw = listOf(0.2f, 0.5f, 0.4f, 0.9f, 0.3f, 1f, 0.6f)
        val a = PressureSmoother()
        val b = PressureSmoother()
        raw.forEach { assertEquals(a.filter(it), b.filter(it), 0f) }
    }
}
