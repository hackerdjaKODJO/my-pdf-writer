package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfCanvasTopAnchorTest {
    @Test
    fun `page that fits vertically is top anchored`() {
        assertEquals(-200f, pdfTopAnchorOffset(600f, 1f, 1000f), 0.001f)
        assertEquals(0f, 500f + pdfTopAnchorOffset(600f, 1f, 1000f) - 300f, 0.001f)
    }

    @Test
    fun `overflowing page keeps centred pan model`() {
        assertEquals(0f, pdfTopAnchorOffset(1200f, 1f, 1000f), 0.001f)
        assertEquals(0f, pdfTopAnchorOffset(1200f, 2f, 1000f), 0.001f)
    }

    @Test
    fun `anchor approaches zero as zoom grows`() {
        val fit = pdfTopAnchorOffset(600f, 1f, 1000f)
        val zoomed = pdfTopAnchorOffset(600f, 1.5f, 1000f)
        assertEquals(-200f, fit, 0.001f)
        assertEquals(-50f, zoomed, 0.001f)
    }
}
