package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure math behind continuous-reader horizontal panning.
 *
 * The old implementation duplicated the max-pan formula in the gesture observer
 * and the page layout, and only clamped the stored value while a drag frame was
 * being processed. Those tests pin down the shared helper and, in particular,
 * the "zoom out while panned, then cannot scroll back to the left" dead zone.
 */
class ReaderPanTest {

    private fun assertFloat(message: String, expected: Float, actual: Float) =
        assertEquals(message, expected, actual, 0.0001f)

    @Test
    fun `max pan is viewport width times zoom above fit`() {
        assertFloat("fit zoom has no pan", 0f, readerMaxPanPx(1000f, 1f))
        assertFloat("125% zoom", 250f, readerMaxPanPx(1000f, 1.25f))
        assertFloat("200% zoom", 1000f, readerMaxPanPx(1000f, 2f))
        assertFloat("250% zoom", 1500f, readerMaxPanPx(1000f, READER_ZOOM_MAX))
        assertFloat("viewport size scales linearly", 500f, readerMaxPanPx(500f, 2f))
    }

    @Test
    fun `max pan sanitizes bad viewport and zoom values`() {
        assertFloat("NaN viewport", 1f, readerMaxPanPx(Float.NaN, 2f))
        assertFloat("negative viewport", 1f, readerMaxPanPx(-10f, 2f))
        assertFloat("infinite viewport still clamps zoom", 1.5f,
            readerMaxPanPx(Float.POSITIVE_INFINITY, 9f))
    }

    @Test
    fun `clamping after zoom decrease removes the left-scroll dead zone`() {
        // Pan to the right edge at 250%: stored pan = 1500 px for a 1000 px
        // viewport. Pinching back to 200% used to leave that value untouched;
        // the layout hid it by clamping the displayed offset to 1000 px, so a
        // rightward swipe had no visible effect until 500 px were burned off.
        val stalePan = 1500f
        val reclamped = clampReaderPan(stalePan, 2f, 1000f)
        assertFloat("pan must be re-clamped to the new maximum", 1000f, reclamped)

        val afterDrag = panReaderAfterDrag(reclamped, +100f, 2f, 1000f)
        assertFloat("the very first rightward drag must move the page", 900f, afterDrag)
        assertTrue(
            "unclamped math would not move the page at all",
            (stalePan - 100f).coerceIn(0f, 1000f) == 1000f,
        )
    }

    @Test
    fun `dragging right reveals the left side and dragging left reveals the right`() {
        assertFloat("finger right decreases pan", 300f,
            panReaderAfterDrag(500f, +200f, 2f, 1000f))
        assertFloat("finger left increases pan", 700f,
            panReaderAfterDrag(500f, -200f, 2f, 1000f))
    }

    @Test
    fun `pan never leaves the zero to max range across zoom changes`() {
        var pan = 0f
        for (zoomStep in 4..10) {
            val zoom = zoomStep / 4f
            val viewport = 1000f
            pan = clampReaderPan(pan, zoom, viewport)
            val maxPan = readerMaxPanPx(viewport, zoom)
            assertTrue("pan $pan must be within 0..$maxPan", pan in 0f..maxPan)

            for (deltaX in listOf(-800f, -120f, 40f, 250f, 900f)) {
                pan = panReaderAfterDrag(pan, deltaX, zoom, viewport)
                assertTrue(
                    "pan $pan must stay within 0..$maxPan after delta $deltaX",
                    pan in 0f..maxPan,
                )
            }
        }
    }

    @Test
    fun `fit zoom and non finite pan reset the offset`() {
        assertFloat("fit zoom resets pan", 0f, clampReaderPan(1200f, 1f, 1000f))
        assertFloat("NaN pan", 0f, clampReaderPan(Float.NaN, 2f, 1000f))
        assertFloat("Infinity pan", 0f,
            clampReaderPan(Float.POSITIVE_INFINITY, 2f, 1000f))
    }

    @Test
    fun `zoom centred reanchors pan so rightward drag can move the page`() {
        // The actual bug: a fresh zoom left pan at 0 with the page pinned left,
        // so pushing right clamped straight back to 0 (no visible movement).
        val anchored = panAfterZoom(0f, 1f, 2f, 500f, 1000f)
        assertFloat("centred zoom gains left-side headroom", 500f, anchored)
        assertFloat("rightward drag now moves the page", 400f,
            panReaderAfterDrag(anchored, +100f, 2f, 1000f))
    }

    @Test
    fun `zoom at the left edge keeps the page pinned left`() {
        assertFloat("left-edge focal", 0f, panAfterZoom(0f, 1f, 2f, 0f, 1000f))
        assertFloat("left-of-viewport focal", 0f,
            panAfterZoom(0f, 1f, 2f, -100f, 1000f))
    }

    @Test
    fun `zoom out reanchors and clamps to the new range`() {
        assertFloat("panned zoom-out clamps at new max", 500f,
            panAfterZoom(1000f, 2f, 1.5f, 500f, 1000f))
        assertFloat("unpanned zoom-out clamps at zero", 0f,
            panAfterZoom(0f, 2f, 1.5f, 500f, 1000f))
        assertFloat("zoom to fit resets pan", 0f,
            panAfterZoom(500f, 2f, 1f, 500f, 1000f))
    }

    @Test
    fun `non finite focal falls back to viewport centre`() {
        assertFloat("NaN focal", 500f, panAfterZoom(0f, 1f, 2f, Float.NaN, 1000f))
        assertFloat("Infinity focal", 500f,
            panAfterZoom(0f, 1f, 2f, Float.POSITIVE_INFINITY, 1000f))
    }
}
