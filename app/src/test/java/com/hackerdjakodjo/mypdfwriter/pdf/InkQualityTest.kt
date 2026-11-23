package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure "pen on paper" helpers: neutral ink must stay byte-identical. */
class InkQualityTest {

    @Test
    fun stationaryPressureChangesAreEmittedButJitterIsNot() {
        assertTrue(InkQuality.shouldEmitPressureSample(0.2f, 0.5f, false, 32L))
        assertTrue(InkQuality.shouldEmitPressureSample(0.8f, 0.4f, false, 32L))
        assertFalse(InkQuality.shouldEmitPressureSample(0.2f, 0.205f, false, 32L))
        assertFalse(InkQuality.shouldEmitPressureSample(0.2f, 0.5f, false, 2L))
        assertFalse(InkQuality.shouldEmitPressureSample(0.2f, 0.9f, true, 32L))
        assertFalse(InkQuality.shouldEmitPressureSample(0.2f, 0.9f, false, 32L, eps = Float.NaN))
        assertFalse(
            InkQuality.shouldEmitPressureSample(0.2f, 0.9f, false, 32L,
                emitted = InkQuality.MAX_STATIONARY_SAMPLES),
        )
    }

    @Test
    fun depositGainPoolsSlowStrokesAndStarvesFlicks() {
        assertEquals(1f, InkQuality.depositGain(10f, 10f, 0.8f, 0f), 0f)
        assertEquals(1f, InkQuality.depositGain(10f, 10f, 0.8f, 0.5f), 0f)
        val reference = 1f
        val slow = InkQuality.depositGain(0f, reference, 0.8f, 0.5f)
        val medium = InkQuality.depositGain(1f, reference, 0.8f, 0.5f)
        val fast = InkQuality.depositGain(8f, reference, 0.8f, 0.5f)
        assertTrue("slow $slow > medium $medium", slow > medium)
        assertTrue("medium $medium > fast $fast", medium > fast)
        assertTrue(slow <= InkQuality.DEPOSIT_GAIN_MAX)
        assertTrue(fast >= InkQuality.DEPOSIT_GAIN_MIN)
        assertEquals(1f, InkQuality.depositGain(Float.NaN, 1f, 0.8f, 0.5f), 0f)
        assertEquals(1f, InkQuality.depositGain(1f, 0f, 0.8f, 0.5f), 0f)
    }

    @Test
    fun nibEndProfileTapersVariedInkAndKeepsLegacyIntact() {
        assertEquals(1f, InkQuality.nibEndProfile(0f, 0f), 0f)
        assertEquals(1f, InkQuality.nibEndProfile(0.03f, 0f), 0f)
        assertEquals(1f, InkQuality.nibEndProfile(0.5f, 0.8f), 0f)
        val start = InkQuality.nibEndProfile(0f, 0.8f)
        val quarter = InkQuality.nibEndProfile(0.25f, 0.8f)
        assertEquals(InkQuality.NIB_TAPER_MIN, start, 0f)
        assertTrue("quarter $quarter > start $start", quarter > start)
        assertEquals(start, InkQuality.nibEndProfile(1f, 0.8f), 0f)
        assertEquals(quarter, InkQuality.nibEndProfile(0.75f, 0.8f), 0.0001f)
    }

    @Test
    fun grainCellIsScaleInvariantAndSafe() {
        assertEquals(0, InkQuality.grainCell(Float.NaN))
        assertEquals(0, InkQuality.grainCell(0.5f, 0f))
        assertEquals(512, InkQuality.grainCell(1f))
        assertEquals(256, InkQuality.grainCell(0.5f))
        assertEquals(128, InkQuality.grainCell(0.25f))
    }
}
