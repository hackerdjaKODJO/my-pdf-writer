package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderZoomTest {

    private fun assertZoom(expected: Float, actual: Float) =
        assertEquals("zoom", expected, actual, 0.0001f)

    @Test
    fun `clamp keeps in range and coerces outside values`() {
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(1.0f))
        assertZoom(1.4f, clampReaderZoom(1.4f))
        assertZoom(READER_ZOOM_MAX, clampReaderZoom(2.5f))
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(0.2f))
        assertZoom(READER_ZOOM_MAX, clampReaderZoom(9f))
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(-1f))
    }

    @Test
    fun `clamp sanitizes non finite values`() {
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(Float.NaN))
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(Float.NEGATIVE_INFINITY))
        assertZoom(READER_ZOOM_MIN, clampReaderZoom(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `step moves by a quarter and stops at the bounds`() {
        assertZoom(1.25f, stepReaderZoom(1.0f, 1))
        assertZoom(1.5f, stepReaderZoom(1.25f, 1))
        assertZoom(1.0f, stepReaderZoom(1.25f, -1))
        assertZoom(READER_ZOOM_MIN, stepReaderZoom(1.0f, -1))
        assertZoom(READER_ZOOM_MAX, stepReaderZoom(2.25f, 1))
        assertZoom(READER_ZOOM_MAX, stepReaderZoom(2.5f, 4))
    }

    @Test
    fun `step handles multiple steps and dirty input`() {
        assertZoom(2.0f, stepReaderZoom(1.0f, 4))
        assertZoom(READER_ZOOM_MAX, stepReaderZoom(1.0f, 10))
        assertZoom(1.65f, stepReaderZoom(1.4f, 1))
        // Non-finite input is treated as fit-to-width before the step applies.
        assertZoom(1.25f, stepReaderZoom(Float.NaN, 1))
    }

    @Test
    fun `double tap toggles between fit and two hundred percent`() {
        assertZoom(READER_ZOOM_DOUBLE_TAP, toggleReaderZoom(1.0f))
        assertZoom(READER_ZOOM_DOUBLE_TAP, toggleReaderZoom(0.5f))
        // Already zoomed (including intermediate steps): go back to fit.
        assertZoom(READER_ZOOM_MIN, toggleReaderZoom(1.25f))
        assertZoom(READER_ZOOM_MIN, toggleReaderZoom(2.0f))
        assertZoom(READER_ZOOM_MIN, toggleReaderZoom(2.5f))
        assertZoom(READER_ZOOM_DOUBLE_TAP, toggleReaderZoom(Float.NaN))
    }

    @Test
    fun `native transform zoom is ignored for a single finger`() {
        // detectTransformGestures also reports one-finger drags as zoomChange=1f.
        // The gate must leave zoom untouched so vertical scrolling still works.
        assertZoom(1.5f, gatedPinchZoom(1.5f, 1f, twoFinger = false))
        assertZoom(1.5f, gatedPinchZoom(1.5f, 2f, twoFinger = false))
        assertZoom(READER_ZOOM_MIN, gatedPinchZoom(Float.NaN, 2f, twoFinger = false))
    }

    @Test
    fun `native transform zoom is applied and clamped for two fingers`() {
        assertZoom(2.0f, gatedPinchZoom(1.0f, 2f, twoFinger = true))
        assertZoom(1.25f, gatedPinchZoom(1.0f, 1.25f, twoFinger = true))
        assertZoom(1.0f, gatedPinchZoom(2.0f, 0.5f, twoFinger = true))
        assertZoom(READER_ZOOM_MAX, gatedPinchZoom(2.0f, 4f, twoFinger = true))
        assertZoom(READER_ZOOM_MIN, gatedPinchZoom(1.0f, 0.25f, twoFinger = true))
    }

    @Test
    fun `native transform zoom rejects invalid deltas`() {
        assertZoom(1.5f, gatedPinchZoom(1.5f, Float.NaN, twoFinger = true))
        assertZoom(1.5f, gatedPinchZoom(1.5f, 0f, twoFinger = true))
        assertZoom(1.5f, gatedPinchZoom(1.5f, -2f, twoFinger = true))
    }

    @Test
    fun `pinch scales the whole document and clamps`() {
        assertZoom(2.0f, zoomFromPinch(1.0f, 100f, 200f))
        assertZoom(1.5f, zoomFromPinch(1.0f, 200f, 300f))
        assertZoom(READER_ZOOM_MAX, zoomFromPinch(2.0f, 100f, 1000f))
        assertZoom(READER_ZOOM_MIN, zoomFromPinch(1.0f, 200f, 20f))
    }

    @Test
    fun `pinch ignores invalid distances and sanitizes start zoom`() {
        assertZoom(1.25f, zoomFromPinch(1.25f, 0f, 500f))
        assertZoom(1.25f, zoomFromPinch(1.25f, -10f, 500f))
        assertZoom(1.25f, zoomFromPinch(1.25f, 100f, Float.NaN))
        assertZoom(1.0f, zoomFromPinch(Float.NaN, 100f, 200f))
    }

    @Test
    fun `render zoom is quantized to quarter steps`() {
        assertZoom(1.0f, readerRenderZoom(1.0f))
        assertZoom(1.25f, readerRenderZoom(1.2f))
        assertZoom(1.5f, readerRenderZoom(1.38f))
        assertZoom(2.5f, readerRenderZoom(9f))
        assertZoom(1.0f, readerRenderZoom(Float.NaN))
    }

    @Test
    fun `render scale grows with zoom but stays bounded`() {
        assertEquals(1.25f, readerRenderScale(1.0f), 0.0001f)
        assertEquals(1.875f, readerRenderScale(1.5f), 0.0001f)
        assertEquals(2.5f, readerRenderScale(2.0f), 0.0001f)
        assertEquals(READER_MAX_RENDER_SCALE, readerRenderScale(2.5f), 0.0001f)
        assertEquals(1.25f, readerRenderScale(Float.NaN), 0.0001f)
    }

    @Test
    fun `every render zoom step produces a distinct scale`() {
        // A pinch only changes the bitmap when the derived scale changes; the
        // render key is (renderZoom, scale), so both must move together.
        val steps = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.25f, 2.5f)
        val scales = steps.map { readerRenderScale(readerRenderZoom(it)) }
        assertEquals("scales must be unique so the cache key changes", scales.size, scales.toSet().size)
        for (i in 1 until scales.size) {
            org.junit.Assert.assertTrue(
                "scale must grow between steps $i and ${i - 1}: $scales",
                scales[i] > scales[i - 1],
            )
        }
    }

    @Test
    fun `label renders a clamped percentage`() {
        assertEquals("100%", readerZoomLabel(1.0f))
        assertEquals("125%", readerZoomLabel(1.25f))
        assertEquals("150%", readerZoomLabel(1.5f))
        assertEquals("250%", readerZoomLabel(2.5f))
        assertEquals("250%", readerZoomLabel(9f))
        assertEquals("100%", readerZoomLabel(Float.NaN))
    }
}
