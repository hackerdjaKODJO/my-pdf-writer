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

    @Test
    fun `default pressure curve is linear`() {
        assertEquals(PressureCurve.LINEAR, PressureCurve.DEFAULT)
    }

    @Test
    fun `pressure curves preserve neutral and clamped endpoints`() {
        PressureCurve.values().forEach { curve ->
            assertEquals("${curve.name} at 0", 0f, curve.applyTo(0f), 0f)
            assertEquals("${curve.name} at 1", 1f, curve.applyTo(1f), 0f)
            assertEquals("${curve.name} below 0", 0f, curve.applyTo(-2f), 0f)
            assertEquals("${curve.name} above 1", 1f, curve.applyTo(3f), 0f)
            // Non-finite samples fall back to neutral rather than leaking NaN.
            assertEquals("${curve.name} NaN", 1f, curve.applyTo(Float.NaN), 0f)
            assertEquals(
                "${curve.name} +inf",
                1f,
                curve.applyTo(Float.POSITIVE_INFINITY),
                0f,
            )
            assertEquals(
                "${curve.name} -inf",
                1f,
                curve.applyTo(Float.NEGATIVE_INFINITY),
                0f,
            )
        }
    }

    @Test
    fun `soft linear firm ordering is stable between the endpoints`() {
        listOf(0.01f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 0.99f).forEach { raw ->
            val soft = PressureCurve.SOFT.applyTo(raw)
            val linear = PressureCurve.LINEAR.applyTo(raw)
            val firm = PressureCurve.FIRM.applyTo(raw)
            assertTrue("SOFT >= LINEAR at $raw", soft >= linear)
            assertTrue("LINEAR >= FIRM at $raw", linear >= firm)
            assertTrue("curve output stays bounded at $raw", soft <= 1f && firm >= 0f)
        }
    }

    @Test
    fun `curves always keep a visible non zero width`() {
        val rawSamples = listOf(
            -5f, 0f, 0.01f, 0.25f, 0.5f, 0.75f, 0.99f, 1f, 4f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )
        PressureCurve.values().forEach { curve ->
            rawSamples.forEach { raw ->
                val effective = curve.applyTo(raw)
                val factor = inkPressureWidthFactor(effective)
                assertTrue("$curve/$raw effective finite", effective.isFinite())
                assertTrue("$curve/$raw width finite", factor.isFinite())
                assertTrue(
                    "$curve/$raw width must stay visible: $factor",
                    factor >= MIN_PRESSURE_WIDTH_FACTOR,
                )
                assertTrue("$curve/$raw width bounded", factor <= 1f)
            }
        }
    }

    @Test
    fun `pressure curve keeps legacy neutral pressure exactly neutral`() {
        PressureCurve.values().forEach { curve ->
            assertEquals(1f, curve.applyTo(DEFAULT_INK_PRESSURE), 0f)
            assertEquals(1f, inkPressureWidthFactor(curve.applyTo(DEFAULT_INK_PRESSURE)), 0f)
        }
    }

    @Test
    fun `diagnostic range is finite ordered and uses the selected curve`() {
        PressureCurve.values().forEach { curve ->
            val range = pressureCurveRange(curve, 0.2f, 0.8f)
            assertEquals(curve, range.curve)
            assertTrue(range.rawMin <= range.rawMax)
            assertTrue(range.effectiveMin <= range.effectiveMax)
            assertTrue(range.effectiveMin.isFinite() && range.effectiveMax.isFinite())
            assertEquals(curve.applyTo(0.2f), range.effectiveMin, 0f)
            assertEquals(curve.applyTo(0.8f), range.effectiveMax, 0f)
        }

        val inverted = pressureCurveRange(PressureCurve.LINEAR, 0.9f, 0.1f)
        assertEquals(0.1f, inverted.rawMin, 0f)
        assertEquals(0.9f, inverted.rawMax, 0f)

        val nonFinite = pressureCurveRange(PressureCurve.FIRM, Float.NaN, 0.5f)
        assertTrue(nonFinite.rawMin.isFinite() && nonFinite.rawMax.isFinite())
        assertTrue(nonFinite.effectiveMin.isFinite() && nonFinite.effectiveMax.isFinite())
    }
}
