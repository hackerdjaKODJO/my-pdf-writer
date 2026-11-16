package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkPressureTest {

    @Test
    fun `point constructor clamps pressure into the supported range`() {
        assertEquals(0f, NormalizedPoint(0.1f, 0.2f, -3f).pressure, 0f)
        assertEquals(1f, NormalizedPoint(0.1f, 0.2f, 4f).pressure, 0f)
        assertEquals(1f, NormalizedPoint(0.1f, 0.2f, Float.NaN).pressure, 0f)
        assertEquals(0.42f, NormalizedPoint(0.1f, 0.2f, 0.42f).pressure, 0f)
    }

    @Test
    fun `copy preserves or explicitly replaces clamped pressure`() {
        val point = NormalizedPoint(0.1f, 0.2f, 0.4f)

        assertEquals(0.4f, point.copy(x = 0.5f).pressure, 0f)
        assertEquals(1f, point.copy(pressure = 9f).pressure, 0f)
        assertEquals(listOf(0.1f, 0.2f, 0.4f), listOf(point.x, point.y, point.pressure))
    }

    @Test
    fun `width factor is safe finite and monotonic`() {
        val samples = listOf(-1f, 0f, 0.1f, 0.5f, 0.9f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)
        val factors = samples.map { inkPressureWidthFactor(it) }

        factors.forEach { factor ->
            assertTrue("factor must be finite: $factor", factor.isFinite())
            assertTrue("factor must stay visible: $factor", factor >= MIN_PRESSURE_WIDTH_FACTOR)
            assertTrue("factor must stay bounded: $factor", factor <= 1f)
        }
        assertEquals(1f, inkPressureWidthFactor(1f), 0f)
        assertEquals(MIN_PRESSURE_WIDTH_FACTOR, inkPressureWidthFactor(0f), 0f)
        assertTrue(inkPressureWidthFactor(0.8f) > inkPressureWidthFactor(0.2f))
        // Legacy documents use 1f, so the mapping must be an exact identity there.
        assertEquals(1f, inkPressureWidthFactor(clampInkPressure(1f)), 0f)
    }
}
