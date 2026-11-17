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
    fun `label renders a clamped percentage`() {
        assertEquals("100%", readerZoomLabel(1.0f))
        assertEquals("125%", readerZoomLabel(1.25f))
        assertEquals("150%", readerZoomLabel(1.5f))
        assertEquals("250%", readerZoomLabel(2.5f))
        assertEquals("250%", readerZoomLabel(9f))
        assertEquals("100%", readerZoomLabel(Float.NaN))
    }
}
