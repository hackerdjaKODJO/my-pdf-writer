package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
