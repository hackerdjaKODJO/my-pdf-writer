package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `calibrator maps a narrow stylus range across the full response`() {
        val calibrator = PressureCalibrator(alpha = 0.5f, deadZone = 0f)
        // A pen that only ever reports 0.2 at rest and 0.7 at full press.
        repeat(24) {
            calibrator.map(0.2f)
            calibrator.map(0.7f)
        }

        assertTrue(calibrator.hasRange)
        assertEquals(0.2f, calibrator.observedMin, 0.01f)
        assertEquals(0.7f, calibrator.observedMax, 0.01f)
        assertEquals(0f, calibrator.map(0.2f), 0.01f)
        assertEquals(1f, calibrator.map(0.7f), 0.01f)
        assertEquals(0.5f, calibrator.map(0.45f), 0.01f)
    }

    @Test
    fun `calibrator tracks extremes with an ewma and ignores in range samples`() {
        val calibrator = PressureCalibrator(alpha = 0.5f, deadZone = 0f)
        assertFalse(calibrator.hasRange)
        assertEquals(Float.POSITIVE_INFINITY, calibrator.observedMin, 0f)
        assertEquals(Float.NEGATIVE_INFINITY, calibrator.observedMax, 0f)

        calibrator.map(0.4f)
        assertTrue(calibrator.hasRange)
        assertEquals(0.4f, calibrator.observedMin, 0f)
        assertEquals(0.4f, calibrator.observedMax, 0f)

        // A new low moves only the minimum, by alpha of the distance.
        calibrator.map(0.2f)
        assertEquals(0.3f, calibrator.observedMin, 1e-6f)
        assertEquals(0.4f, calibrator.observedMax, 0f)

        // A new high moves only the maximum, by the same alpha.
        calibrator.map(0.8f)
        assertEquals(0.3f, calibrator.observedMin, 0f)
        assertEquals(0.6f, calibrator.observedMax, 1e-6f)

        // Samples inside the learned range leave both bounds untouched.
        calibrator.map(0.45f)
        assertEquals(0.3f, calibrator.observedMin, 0f)
        assertEquals(0.6f, calibrator.observedMax, 0f)
    }

    @Test
    fun `calibrator flattens the faintest band but keeps full pressure exact`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0.1f)
        calibrator.map(0.3f)
        calibrator.map(0.7f)

        // Range 0.3..0.7; the bottom 10% is dead zone, the rest is rescaled.
        assertEquals(0f, calibrator.map(0.30f), 1e-6f)
        assertEquals(0f, calibrator.map(0.32f), 1e-6f)
        assertEquals(0f, calibrator.map(0.34f), 1e-6f)
        assertEquals(0.5f, calibrator.map(0.52f), 1e-6f)
        assertEquals(1f, calibrator.map(0.70f), 1e-6f)
        assertEquals(1f, calibrator.map(1f), 0f)
    }

    @Test
    fun `calibrator is identity for narrow ranges full presses and non finite samples`() {
        val narrow = PressureCalibrator(alpha = 1f, deadZone = 0f)
        repeat(10) {
            narrow.map(0.50f)
            narrow.map(0.52f)
        }
        assertTrue(narrow.hasRange)
        assertTrue(narrow.observedMax - narrow.observedMin <= 0.05f)
        assertEquals(0.50f, narrow.map(0.50f), 0f)
        assertEquals(0.51f, narrow.map(0.51f), 0f)
        assertEquals(0.52f, narrow.map(0.52f), 0f)

        val calibrator = PressureCalibrator()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1f, 2f).forEach {
            assertEquals("neutral for $it", 1f, calibrator.map(it), 0f)
        }
        // Neutral samples never seed or pollute the learned range.
        assertFalse(calibrator.hasRange)
    }

    @Test
    fun `calibrator reset forgets the learned range`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        calibrator.map(0.2f)
        calibrator.map(0.6f)
        assertEquals(0.2f, calibrator.observedMin, 0f)
        assertEquals(0.6f, calibrator.observedMax, 0f)
        assertTrue(calibrator.hasRange)

        calibrator.reset()
        assertEquals(Float.POSITIVE_INFINITY, calibrator.observedMin, 0f)
        assertEquals(Float.NEGATIVE_INFINITY, calibrator.observedMax, 0f)
        assertFalse(calibrator.hasRange)

        calibrator.map(0.8f)
        assertEquals(0.8f, calibrator.observedMin, 0f)
        assertEquals(0.8f, calibrator.observedMax, 0f)
    }

    @Test
    fun `calibrator output stays finite and bounded for wild input`() {
        val calibrator = PressureCalibrator(alpha = 0.3f, deadZone = 0.02f)
        val wild = listOf(
            -2f, 0f, 0.1f, 0.5f, 0.9f, 1f, 4f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )
        wild.forEach { value ->
            val mapped = calibrator.map(value)
            assertTrue("finite for $value", mapped.isFinite())
            assertTrue("bounded for $value: $mapped", mapped in 0f..1f)
        }
    }
}
