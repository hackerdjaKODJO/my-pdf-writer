package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.input.pointer.PointerType
import com.hackerdjakodjo.mypdfwriter.data.DEFAULT_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Pure coverage for the per-sample pressure gate used by the note canvas. */
class NotePressureCaptureTest {

    @Test
    fun `stylus sample is clamped and gets the selected curve exactly once`() {
        val raw = 0.25f
        val expected = PressureCurve.FIRM.applyTo(raw)

        val sampled = effectiveInkPressure(PointerType.Stylus, raw, true, PressureCurve.FIRM)

        assertEquals(expected, sampled, 1e-6f)
        // The capture step must not square the curve; rendering never re-applies it.
        assertTrue(abs(PressureCurve.FIRM.applyTo(sampled) - sampled) > 1e-3f)
    }

    @Test
    fun `out of range and non finite stylus pressure is clamped before the curve`() {
        assertEquals(0f, effectiveInkPressure(PointerType.Stylus, -3f, true, PressureCurve.SOFT), 0f)
        assertEquals(1f, effectiveInkPressure(PointerType.Stylus, 7f, true, PressureCurve.SOFT), 0f)
        assertEquals(
            PressureCurve.SOFT.applyTo(DEFAULT_INK_PRESSURE),
            effectiveInkPressure(PointerType.Stylus, Float.NaN, true, PressureCurve.SOFT),
            0f,
        )
    }

    @Test
    fun `finger mouse eraser and unknown pointers always store neutral pressure`() {
        val neutralTypes = listOf(
            PointerType.Touch,
            PointerType.Mouse,
            PointerType.Eraser,
            PointerType.Unknown,
        )
        neutralTypes.forEach { type ->
            assertEquals(
                "$type must stay neutral",
                DEFAULT_INK_PRESSURE,
                effectiveInkPressure(type, 0.2f, true, PressureCurve.SOFT),
                0f,
            )
            assertEquals(
                "$type must stay neutral for every curve",
                DEFAULT_INK_PRESSURE,
                effectiveInkPressure(type, 0.8f, true, PressureCurve.FIRM),
                0f,
            )
        }
    }

    @Test
    fun `disabled pressure is neutral even for a stylus`() {
        assertEquals(
            DEFAULT_INK_PRESSURE,
            effectiveInkPressure(PointerType.Stylus, 0.1f, false, PressureCurve.FIRM),
            0f,
        )
    }

    @Test
    fun `current pointer type decides each sample rather than a down latched type`() {
        // A finger may start the stroke (finger writing enabled) and a stylus may
        // join mid-stroke. Sampling from each event's own type must flip from the
        // neutral value to real pressure on the very next move.
        val fingerSample = effectiveInkPressure(PointerType.Touch, 0.3f, true, PressureCurve.SOFT)
        val stylusSample = effectiveInkPressure(PointerType.Stylus, 0.3f, true, PressureCurve.SOFT)

        assertEquals(DEFAULT_INK_PRESSURE, fingerSample, 0f)
        assertEquals(PressureCurve.SOFT.applyTo(0.3f), stylusSample, 1e-6f)
        assertTrue(abs(stylusSample - fingerSample) > 1e-3f)
    }

    @Test
    fun `curve presets keep light pressure distinct and full pressure neutral`() {
        val light = 0.25f
        assertTrue(effectiveInkPressure(PointerType.Stylus, light, true, PressureCurve.SOFT) > light)
        assertTrue(effectiveInkPressure(PointerType.Stylus, light, true, PressureCurve.FIRM) < light)
        PressureCurve.entries.forEach { curve ->
            assertEquals(
                "$curve must preserve full pressure",
                1f,
                effectiveInkPressure(PointerType.Stylus, 1f, true, curve),
                1e-6f,
            )
        }
    }
}
