package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom page bar replaces the floating zoom/page pills: it is a layout row
 * below the PDF, shown only when the full-screen reader has chrome visible.
 */
class ViewerBottomPageBarTest {

    @Test
    fun `bar shows only in the full screen reader with chrome visible`() {
        assertTrue(viewerBottomPageBarVisible(splitMode = false, immersive = false))
        assertFalse("split keeps its floating toolbar", viewerBottomPageBarVisible(true, false))
        assertFalse("immersive hides every bar", viewerBottomPageBarVisible(false, true))
        assertFalse(viewerBottomPageBarVisible(true, true))
    }

    @Test
    fun `bar height leaves room for the 48dp navigator targets`() {
        assertTrue(VIEWER_BOTTOM_PAGE_BAR_HEIGHT_DP >= MIN_TARGET_DP)
    }

    @Test
    fun `continuous scroll target steps one page and clamps at both ends`() {
        // currentPage is 1-based; the target is a 0-based list index.
        assertEquals(3, continuousReaderScrollTarget(3, 1, 10))
        assertEquals(1, continuousReaderScrollTarget(3, -1, 10))
        assertEquals(0, continuousReaderScrollTarget(1, -1, 10))
        assertEquals(9, continuousReaderScrollTarget(10, 1, 10))
        assertEquals(0, continuousReaderScrollTarget(1, 1, 1))
        assertEquals(0, continuousReaderScrollTarget(1, -1, 1))
    }

    @Test
    fun `continuous scroll target is safe for empty and dirty input`() {
        assertEquals(0, continuousReaderScrollTarget(1, 1, 0))
        assertEquals(0, continuousReaderScrollTarget(0, -1, 0))
        // An out-of-range current page is clamped before stepping.
        assertEquals(8, continuousReaderScrollTarget(99, -1, 10))
        assertEquals(1, continuousReaderScrollTarget(-4, 1, 10))
    }
}
