package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.input.pointer.PointerType
import com.hackerdjakodjo.mypdfwriter.data.DEFAULT_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PressureCalibrator
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.PressureSmoother
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.EraserGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `out of range stylus pressure is clamped and non finite stays neutral`() {
        assertEquals(0f, effectiveInkPressure(PointerType.Stylus, -3f, true, PressureCurve.SOFT), 0f)
        assertEquals(1f, effectiveInkPressure(PointerType.Stylus, 7f, true, PressureCurve.SOFT), 0f)
        assertEquals(
            PressureCurve.SOFT.applyTo(DEFAULT_INK_PRESSURE),
            effectiveInkPressure(PointerType.Stylus, Float.NaN, true, PressureCurve.SOFT),
            0f,
        )
    }

    @Test
    fun `non finite raw pressure bypasses calibration curve and smoother state`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        calibrator.map(0.2f)
        calibrator.map(0.7f)
        val smoother = PressureSmoother()
        assertEquals(0.5f, effectiveInkPressure(
            PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR, calibrator,
            smoother = smoother, elapsedMs = 8L,
        ), 1e-6f)

        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { raw ->
            assertEquals(1f, effectiveInkPressure(
                PointerType.Stylus, raw, true, PressureCurve.SOFT, calibrator,
                smoother = smoother, elapsedMs = 8L,
            ), 0f)
        }
        assertEquals(0.2f, calibrator.observedMin, 0f)
        assertEquals(0.7f, calibrator.observedMax, 0f)

        val uninterrupted = PressureSmoother().also { it.filter(0.5f, 8L) }
        assertEquals(
            uninterrupted.filter(0.5f, 8L),
            effectiveInkPressure(
                PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR, calibrator,
                smoother = smoother, elapsedMs = 8L,
            ),
            0f,
        )
    }

    @Test
    fun `timed samples preserve relative uptime and duration follows the last point`() {
        val down = NormalizedPoint(0.1f, 0.2f, timeMs = relativePointTimeMs(1_000L, 1_000L))
        val dwell = down.copy(timeMs = relativePointTimeMs(1_132L, 1_000L))
        val move = down.copy(x = 0.3f, timeMs = relativePointTimeMs(1_180L, 1_000L))

        assertEquals(0L, down.timeMs)
        assertEquals(132L, dwell.timeMs)
        assertEquals(180L, move.timeMs)
        assertEquals(180L, inkStrokeDurationMs(listOf(down, dwell, move)))
        assertEquals(0L, inkStrokeDurationMs(listOf(down.copy(timeMs = null))))
        assertEquals(0L, inkStrokeDurationMs(emptyList()))
        assertEquals(0L, relativePointTimeMs(900L, 1_000L))
        assertEquals(132L, relativePointTimeMs(1_120L, 1_000L, minimumTimeMs = 132L))
    }

    @Test
    fun `stylus eraser overrides pen and pressure policy includes the eraser tip`() {
        assertEquals(Tool.ERASER, gestureToolForPointer(Tool.PEN, PointerType.Eraser))
        assertEquals(Tool.ERASER, gestureToolForPointer(Tool.HIGHLIGHT, PointerType.Eraser))
        assertEquals(Tool.PEN, gestureToolForPointer(Tool.PEN, PointerType.Stylus))
        assertEquals(Tool.ERASER, gestureToolForPointer(Tool.ERASER, PointerType.Touch))
        assertTrue(eraserUsesPressure(PointerType.Stylus, true))
        assertTrue(eraserUsesPressure(PointerType.Eraser, true))
        assertFalse(eraserUsesPressure(PointerType.Touch, true))
        assertFalse(eraserUsesPressure(PointerType.Eraser, false))
        assertEquals(0.25f, eraserInputPressure(PointerType.Eraser, 0.25f, true), 0f)
        assertEquals(1f, eraserInputPressure(PointerType.Eraser, Float.NaN, true), 0f)
        assertEquals(1f, eraserInputPressure(PointerType.Touch, 0.25f, true), 0f)
    }

    @Test
    fun `pressure eraser preview uses the shared kernel on both page axes`() {
        val center = NormalizedPoint(0.5f, 0.5f, pressure = 0.25f)
        val radius = EraserGeometry.radiusForPressure(
            EraserGeometry.NORMALIZED_RADIUS,
            center.pressure,
            pressureSensitive = true,
        )
        val portrait = noteEraserPreviewOval(center, 595f, 842f, radius)
        val landscape = noteEraserPreviewOval(center, 842f, 595f, radius)

        assertEquals(radius * 595f * 2f, portrait.right - portrait.left, 1e-3f)
        assertEquals(radius * 842f * 2f, portrait.bottom - portrait.top, 1e-3f)
        assertEquals(radius * 842f * 2f, landscape.right - landscape.left, 1e-3f)
        assertEquals(radius * 595f * 2f, landscape.bottom - landscape.top, 1e-3f)
        assertTrue(radius.isFinite() && radius > 0f)
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

    @Test
    fun `calibration is applied to stylus raw pressure before the curve`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        calibrator.map(0.2f)
        calibrator.map(0.7f)

        // 0.45 is the midpoint of the learned 0.2..0.7 range, so the curve sees
        // 0.5 rather than the uncalibrated 0.45.
        val sampled = effectiveInkPressure(
            PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR, calibrator,
        )
        assertEquals(0.5f, sampled, 1e-6f)

        val uncalibrated = effectiveInkPressure(
            PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR,
        )
        assertEquals(0.45f, uncalibrated, 1e-6f)
        assertTrue(abs(sampled - uncalibrated) > 1e-3f)
    }

    @Test
    fun `calibrators never make neutral pointers or disabled pressure non neutral`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        calibrator.map(0.2f)
        calibrator.map(0.7f)

        val neutralTypes = listOf(
            PointerType.Touch,
            PointerType.Mouse,
            PointerType.Eraser,
            PointerType.Unknown,
        )
        neutralTypes.forEach { type ->
            assertEquals(
                "$type must stay neutral with a calibrator",
                DEFAULT_INK_PRESSURE,
                effectiveInkPressure(type, 0.4f, true, PressureCurve.SOFT, calibrator),
                0f,
            )
        }
        assertEquals(
            DEFAULT_INK_PRESSURE,
            effectiveInkPressure(PointerType.Stylus, 0.4f, false, PressureCurve.FIRM, calibrator),
            0f,
        )
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertEquals(
                "non-finite $value must stay neutral with a calibrator",
                DEFAULT_INK_PRESSURE,
                effectiveInkPressure(PointerType.Stylus, value, true, PressureCurve.FIRM, calibrator),
                0f,
            )
        }
        PressureCurve.entries.forEach { curve ->
            assertEquals(
                "$curve must preserve full pressure with a calibrator",
                1f,
                effectiveInkPressure(PointerType.Stylus, 1f, true, curve, calibrator),
                1e-6f,
            )
        }
    }

    @Test
    fun calibratorIgnoresPointerUpAndNonDrawingSamples() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)

        // Compose reports pointer-up as pressure 0 with pressed = false. Feeding
        // that synthetic sample used to drag the learned minimum toward zero.
        effectiveInkPressure(PointerType.Stylus, 0f, true, PressureCurve.LINEAR, calibrator, pressed = false)
        // An up event may also carry a stale non-zero pressure; still not drawing.
        effectiveInkPressure(PointerType.Stylus, 0.9f, true, PressureCurve.LINEAR, calibrator, pressed = false)
        // Memo taps and navigation gestures are skipped by the canvas entirely, but
        // even if sampled, neutral pointer types must never teach the calibrator.
        listOf(PointerType.Touch, PointerType.Mouse, PointerType.Eraser, PointerType.Unknown)
            .forEach { type ->
                effectiveInkPressure(type, 0f, true, PressureCurve.LINEAR, calibrator, pressed = true)
                effectiveInkPressure(type, 1f, true, PressureCurve.LINEAR, calibrator, pressed = true)
            }

        assertFalse("no range learned from up or non-drawing samples", calibrator.hasRange)

        // The first real pressed stylus sample still starts a fresh range.
        effectiveInkPressure(PointerType.Stylus, 0.3f, true, PressureCurve.LINEAR, calibrator)
        assertTrue(calibrator.hasRange)
        assertEquals(0.3f, calibrator.observedMin, 0f)
        assertEquals(0.3f, calibrator.observedMax, 0f)
    }

    @Test
    fun calibrationRangeSurvivesRepeatedStrokeEnds() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        effectiveInkPressure(PointerType.Stylus, 0.2f, true, PressureCurve.LINEAR, calibrator)
        effectiveInkPressure(PointerType.Stylus, 0.7f, true, PressureCurve.LINEAR, calibrator)
        assertEquals(0.2f, calibrator.observedMin, 0f)
        assertEquals(0.7f, calibrator.observedMax, 0f)

        // Many strokes ending on pointer-up (pressure 0, pressed false) must not
        // move the learned bounds; otherwise later strokes render far too bold.
        repeat(50) {
            effectiveInkPressure(PointerType.Stylus, 0f, true, PressureCurve.LINEAR, calibrator, pressed = false)
        }
        assertEquals(0.2f, calibrator.observedMin, 0f)
        assertEquals(0.7f, calibrator.observedMax, 0f)
        assertEquals(0.5f,
            effectiveInkPressure(PointerType.Stylus, 0.45f, true, PressureCurve.LINEAR, calibrator),
            1e-6f,
        )
    }

    @Test
    fun `neutral pointer samples do not feed the calibrator range`() {
        // The caller only forwards stylus samples to the calibrator, so finger
        // and mouse input must leave its learned range completely untouched.
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        assertFalse(calibrator.hasRange)
        listOf(PointerType.Touch, PointerType.Mouse, PointerType.Eraser, PointerType.Unknown)
            .forEach { type ->
                effectiveInkPressure(type, 0.1f, true, PressureCurve.LINEAR, calibrator)
                effectiveInkPressure(type, 0.9f, true, PressureCurve.LINEAR, calibrator)
            }
        assertFalse(calibrator.hasRange)

        effectiveInkPressure(PointerType.Stylus, 0.3f, true, PressureCurve.LINEAR, calibrator)
        assertEquals(0.3f, calibrator.observedMin, 0f)
        assertEquals(0.3f, calibrator.observedMax, 0f)
    }
}
