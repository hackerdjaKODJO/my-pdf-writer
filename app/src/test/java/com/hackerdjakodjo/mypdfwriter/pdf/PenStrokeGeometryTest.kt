package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Pure tests for the pen geometry policies. These helpers decide when a pen may
 * deviate from the legacy renderer, so neutral identity, determinism and bounds
 * are all part of the contract rather than implementation details.
 */
class PenStrokeGeometryTest {

    private fun points(vararg pressures: Float): List<NormalizedPoint> =
        pressures.mapIndexed { index, pressure -> NormalizedPoint(index * 0.1f, 0.2f, pressure) }

    @Test
    fun `neutral detection and variation are exact for uniform strokes`() {
        assertTrue(PenStrokeGeometry.isNeutralPressureStroke(emptyList()))
        assertTrue(PenStrokeGeometry.isNeutralPressureStroke(points(1f, 1f, 1f)))
        assertFalse(PenStrokeGeometry.isNeutralPressureStroke(points(1f, 0.99f, 1f)))
        assertFalse(PenStrokeGeometry.isNeutralPressureStroke(points(0f, 0f)))

        assertEquals(0f, PenStrokeGeometry.pressureVariation(emptyList()), 0f)
        assertEquals(0f, PenStrokeGeometry.pressureVariation(points(1f)), 0f)
        assertEquals(0f, PenStrokeGeometry.pressureVariation(points(1f, 1f, 1f)), 0f)
        // Tiny quantization noise is not a reason to taper a nib.
        assertEquals(0f, PenStrokeGeometry.pressureVariation(points(1f, 0.995f)), 0f)
    }

    @Test
    fun `pressure variation detects real changes and stays bounded and deterministic`() {
        assertEquals(1f, PenStrokeGeometry.pressureVariation(points(1f, 1f, 0f, 0f)), 0f)
        assertEquals(0.5f, PenStrokeGeometry.pressureVariation(points(0.5f, 1f)), 0f)
        // NormalizedPoint clamps out-of-range samples before the range is measured.
        assertEquals(1f, PenStrokeGeometry.pressureVariation(points(4f, -3f)), 0f)

        val samples = listOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 1f)
        samples.forEach { pressure ->
            val variation = PenStrokeGeometry.pressureVariation(points(pressure, 1f))
            assertTrue("bounded for $pressure", variation in 0f..1f)
            assertEquals(
                "deterministic for $pressure",
                variation,
                PenStrokeGeometry.pressureVariation(points(pressure, 1f)),
                0f,
            )
        }
    }

    @Test
    fun `ballpoint skipping only fires for light flow and is deterministic`() {
        (1..64).forEach { index ->
            listOf(1f, 0.8f, PenStrokeGeometry.BALLPOINT_LIGHT_FLOW).forEach { flow ->
                assertFalse("no skip at flow $flow index $index", PenStrokeGeometry.ballpointSkip(index, flow))
            }
        }
        assertFalse(PenStrokeGeometry.ballpointSkip(0, 0f))
        assertFalse(PenStrokeGeometry.ballpointSkip(-3, 0f))

        listOf(0f, 0.2f, 0.5f).forEach { flow ->
            val skipped = (1..64).filter { PenStrokeGeometry.ballpointSkip(it, flow) }
            assertTrue("some gaps at flow $flow", skipped.isNotEmpty())
            assertTrue("some contact at flow $flow", skipped.size < 64)
            skipped.forEach { index ->
                assertEquals(
                    "deterministic gap at $index/$flow",
                    PenStrokeGeometry.ballpointSkip(index, flow),
                    PenStrokeGeometry.ballpointSkip(index, flow),
                )
            }
        }
        val faint = (1..64).count { PenStrokeGeometry.ballpointSkip(it, 0.2f) }
        val moderate = (1..64).count { PenStrokeGeometry.ballpointSkip(it, 0.5f) }
        assertTrue("lighter contact skips at least as often", faint >= moderate)
    }

    @Test
    fun `ballpoint contact dots stay faint and are neutral at full flow`() {
        assertEquals(255, PenStrokeGeometry.ballpointDotAlpha(1f))
        assertTrue(PenStrokeGeometry.ballpointDotAlpha(0f) in 1..254)
        assertTrue(
            PenStrokeGeometry.ballpointDotAlpha(0.2f) <
                PenStrokeGeometry.ballpointDotAlpha(0.5f),
        )
        assertTrue(
            PenStrokeGeometry.ballpointDotAlpha(0.5f) <
                PenStrokeGeometry.ballpointDotAlpha(PenStrokeGeometry.BALLPOINT_LIGHT_FLOW),
        )
        assertEquals(255, PenStrokeGeometry.ballpointDotAlpha(Float.NaN))
    }

    @Test
    fun `fountain taper is neutral at zero variation and subtly rounds varied tips`() {
        listOf(-1f, 0f, 0.25f, 0.5f, 1f, 2f, Float.NaN).forEach { t ->
            assertEquals(1f, PenStrokeGeometry.fountainTaper(t, 0f), 0f)
        }
        val variation = 0.8f
        assertTrue(PenStrokeGeometry.fountainTaper(0f, variation) < 1f)
        assertTrue(PenStrokeGeometry.fountainTaper(0f, variation) >= 0.60f)
        assertTrue(PenStrokeGeometry.fountainTaper(0.5f, variation) == 1f)
        assertEquals(
            PenStrokeGeometry.fountainTaper(0.12f, variation),
            PenStrokeGeometry.fountainTaper(0.88f, variation),
            0f,
        )
        listOf(-4f, 0f, 0.1f, 0.5f, 0.9f, 1f, 7f, Float.NaN).forEach { t ->
            val taper = PenStrokeGeometry.fountainTaper(t, variation)
            assertTrue("taper bounded for $t", taper in 0.60f..1f)
            assertTrue("taper finite for $t", taper.isFinite())
        }
        assertEquals(
            PenStrokeGeometry.fountainTaper(0f, variation),
            PenStrokeGeometry.fountainTaper(0f, variation),
            0f,
        )
    }

    @Test
    fun `pencil segment helpers match legacy constants at neutral pressure`() {
        assertEquals(PenStrokeGeometry.PENCIL_CENTER_ALPHA, PenStrokeGeometry.pencilSegmentAlpha(1, 1f), 0f)
        assertEquals(PenStrokeGeometry.PENCIL_SIDE_ALPHA, PenStrokeGeometry.pencilSegmentAlpha(0, 1f), 0f)
        assertEquals(PenStrokeGeometry.PENCIL_SIDE_ALPHA, PenStrokeGeometry.pencilSegmentAlpha(2, 1f), 0f)
        assertEquals(1f, PenStrokeGeometry.pencilSegmentWidth(1f), 0f)
        assertEquals(0.55f, PenStrokeGeometry.pencilSegmentWidth(0.55f), 0f)
        assertEquals(MIN_PRESSURE_WIDTH_FACTOR, PenStrokeGeometry.pencilSegmentWidth(0.01f), 0f)
        assertEquals(1f, PenStrokeGeometry.pencilSegmentWidth(3f), 0f)
        assertEquals(1f, PenStrokeGeometry.pencilSegmentWidth(Float.NaN), 0f)

        listOf(0, 1, 2, 7).forEach { pass ->
            listOf(0f, 0.2f, 0.5f, 0.9f, 1f, Float.NaN).forEach { flow ->
                val alpha = PenStrokeGeometry.pencilSegmentAlpha(pass, flow)
                assertTrue("alpha bounded for $pass/$flow", alpha in 0f..1f)
                assertTrue("alpha finite for $pass/$flow", alpha.isFinite())
            }
            assertTrue(
                "light graphite is fainter for pass $pass",
                PenStrokeGeometry.pencilSegmentAlpha(pass, 0.25f) <
                    PenStrokeGeometry.pencilSegmentAlpha(pass, 1f),
            )
        }
    }

    @Test
    fun `variable outline gate rejects legacy and noise but accepts real variation`() {
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(emptyList()))
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(points(1f)))
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(points(1f, 1f, 1f)))
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(points(1f, 0.995f, 1f)))
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(points(0.5f, 0.5f)))
        assertFalse(PenStrokeGeometry.useVariableWidthOutline(points(0.5f)))
        assertTrue(PenStrokeGeometry.useVariableWidthOutline(points(1f, 0.4f)))
        assertTrue(PenStrokeGeometry.useVariableWidthOutline(points(0f, 0.5f, 1f)))
        // Out-of-range samples are clamped before the range is measured.
        assertTrue(PenStrokeGeometry.useVariableWidthOutline(points(4f, -3f)))
    }

    @Test
    fun `simulated pressure keeps neutral identity and answers to pace`() {
        listOf(0f, 0.25f, 1f, 4f, Float.NaN, Float.POSITIVE_INFINITY).forEach { speed ->
            listOf(-1f, 0f, 1f, 12f, Float.NaN).forEach { reference ->
                assertEquals("full pressure stays neutral", 1f, PenStrokeGeometry.simulatedPressure(1f, speed, reference), 0f)
            }
        }
        val pressure = 0.4f
        assertEquals(pressure, PenStrokeGeometry.simulatedPressure(pressure, 12f, 12f), 0f)
        assertTrue("slow adds contact", PenStrokeGeometry.simulatedPressure(pressure, 2f, 12f) > pressure)
        assertTrue("fast lifts the nib", PenStrokeGeometry.simulatedPressure(pressure, 48f, 12f) < pressure)
        assertEquals(pressure, PenStrokeGeometry.simulatedPressure(pressure, Float.NaN, 12f), 0f)
        assertEquals(pressure, PenStrokeGeometry.simulatedPressure(pressure, 12f, 0f), 0f)

        listOf(0f, 0.1f, 0.5f, 0.9f).forEach { sample ->
            listOf(0f, 3f, 9f, 90f, Float.NaN).forEach { speed ->
                val estimate = PenStrokeGeometry.simulatedPressure(sample, speed, 9f)
                assertTrue("bounded for $sample/$speed", estimate in 0f..1f)
                assertTrue("finite for $sample/$speed", estimate.isFinite())
                assertEquals(
                    "deterministic for $sample/$speed",
                    estimate,
                    PenStrokeGeometry.simulatedPressure(sample, speed, 9f),
                    0f,
                )
            }
        }
    }

    @Test
    fun `outline end envelope is neutral at zero variation and symmetric otherwise`() {
        listOf(-1f, 0f, 0.25f, 0.5f, 1f, 4f, Float.NaN).forEach { t ->
            listOf(0f, 0.2f, 1f, Float.NaN).forEach { span ->
                assertEquals(1f, PenStrokeGeometry.outlineEndEnvelope(t, 0f, span), 0f)
            }
        }
        val variation = 0.8f
        assertTrue(PenStrokeGeometry.outlineEndEnvelope(0f, variation, 0.3f) < 1f)
        assertEquals(1f, PenStrokeGeometry.outlineEndEnvelope(0.5f, variation, 0.3f), 0f)
        assertEquals(
            PenStrokeGeometry.outlineEndEnvelope(0.12f, variation, 0.3f),
            PenStrokeGeometry.outlineEndEnvelope(0.88f, variation, 0.3f),
            0f,
        )
        listOf(-2f, 0f, 0.1f, 0.5f, 0.9f, 1f, 5f, Float.NaN).forEach { t ->
            val envelope = PenStrokeGeometry.outlineEndEnvelope(t, variation, Float.NaN)
            assertTrue("envelope bounded for $t", envelope in 0.60f..1f)
            assertTrue("envelope finite for $t", envelope.isFinite())
        }
    }

    @Test
    fun `outline width series is a uniform neutral multiplier and stays finite`() {
        val neutral = points(1f, 1f, 1f, 1f)
        val neutralWidths = PenStrokeGeometry.outlineWidthSeries(neutral, List(4) { 1f }, 0f, 10f, 1f)
        assertEquals(4, neutralWidths.size)
        neutralWidths.forEach { assertEquals("neutral half width", 5f, it, 0f) }
        PenStrokeGeometry.outlineWidthSeries(neutral, List(4) { 1f }, 0f, 10f, 0.8f).forEach {
            assertEquals("legacy width multiplier", 4f, it, 0f)
        }
        assertTrue(PenStrokeGeometry.outlineWidthSeries(emptyList(), emptyList(), 0f, 10f, 1f).isEmpty())

        val varied = points(1f, 0.8f, 0.2f, 0.6f)
        val variation = PenStrokeGeometry.pressureVariation(varied)
        val widths = PenStrokeGeometry.outlineWidthSeries(varied, List(4) { 0.8f }, variation, 10f, 1f)
        assertEquals(4, widths.size)
        widths.forEach {
            assertTrue("width positive", it > 0f)
            assertTrue("width bounded", it <= 20f)
            assertTrue("width finite", it.isFinite())
        }
        assertEquals(
            widths.toList(),
            PenStrokeGeometry.outlineWidthSeries(varied, List(4) { 0.8f }, variation, 10f, 1f).toList(),
        )
        // A prefix of the full widths is a slice, never a fresh computation.
        assertEquals(widths.take(2).toList(), widths.copyOf(2).toList())
    }

    @Test
    fun `graphite wave and offsets are exact legacy constants at full grain`() {
        listOf(0, 2, 3, 7).forEach { pass ->
            val phase = 3 * 1.7 + pass * 4.3
            assertEquals("legacy wave", sin(phase).toFloat(), PenStrokeGeometry.graphiteJitterByArc(3, 12.5f, pass, 1f, phase), 0f)
            assertEquals("arc ignored at full grain", sin(phase).toFloat(), PenStrokeGeometry.graphiteJitterByArc(3, 900f, pass, 1f, phase), 0f)
        }
        assertEquals(0f, PenStrokeGeometry.graphiteJitterByArc(3, 1f, 1, 0.4f, 99.0), 0f)
        assertEquals(-PenStrokeGeometry.GRAPHITE_SIDE_OFFSET, PenStrokeGeometry.graphitePassOffset(0, 1f), 0f)
        assertEquals(PenStrokeGeometry.GRAPHITE_CENTER_OFFSET, PenStrokeGeometry.graphitePassOffset(1, 1f), 0f)
        assertEquals(PenStrokeGeometry.GRAPHITE_SIDE_OFFSET, PenStrokeGeometry.graphitePassOffset(2, 1f), 0f)
        assertEquals(-0.275f, PenStrokeGeometry.graphitePassOffset(0, 0.5f), 0f)

        val light = PenStrokeGeometry.graphiteJitterByArc(3, 12.5f, 0, 0.4f, 99.0)
        assertTrue("wave bounded", light in -1f..1f)
        assertTrue("wave finite", light.isFinite())
        assertEquals(light, PenStrokeGeometry.graphiteJitterByArc(3, 12.5f, 0, 0.4f, 99.0), 0f)
        assertTrue(
            "arc length reseeds the wave",
            light != PenStrokeGeometry.graphiteJitterByArc(3, 812.5f, 0, 0.4f, 99.0),
        )
        listOf(0, 2, 7).forEach { pass ->
            listOf(0f, 0.3f, 1f, Float.NaN).forEach { grain ->
                val wave = PenStrokeGeometry.graphiteJitterByArc(2, 4f, pass, grain, 1.0)
                assertTrue("wave bounded for $pass/$grain", wave in -1f..1f)
                assertTrue("wave finite for $pass/$grain", wave.isFinite())
            }
        }
    }

    @Test
    fun `graphite deposit breaks light grain into speckles but keeps legacy identity`() {
        assertEquals(1f, PenStrokeGeometry.graphiteDeposit(12f, 0, 1f, -0.8f), 0f)
        val light = PenStrokeGeometry.graphiteDeposit(12f, 0, 0.3f, -0.8f)
        assertTrue("deposit $light", light in 0.82f..1f)
        assertTrue("speckled", light < 1f)
        assertEquals(light, PenStrokeGeometry.graphiteDeposit(12f, 0, 0.3f, -0.8f), 0f)
        assertEquals(1f, PenStrokeGeometry.graphiteDeposit(12f, 0, 0.3f, Float.NaN), 0f)
    }

    @Test
    fun `a lifted nib cannot be boosted back into ink by slow movement`() {
        assertEquals(0f, PenStrokeGeometry.simulatedPressure(0f, 0f, 12f), 0f)
        assertEquals(0.1f, PenStrokeGeometry.simulatedPressure(0.1f, 0f, 12f), 0f)
        assertTrue(
            "genuine light contact still gains contact when slow",
            PenStrokeGeometry.simulatedPressure(0.4f, 2f, 12f) > 0.4f,
        )
    }
}
