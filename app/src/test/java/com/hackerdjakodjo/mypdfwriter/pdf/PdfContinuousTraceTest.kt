package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfContinuousTraceTest {

    @Before
    fun setUp() {
        NoteTrace.enabled = true
        NoteTrace.clear()
    }

    @After
    fun tearDown() {
        NoteTrace.clear()
        NoteTrace.enabled = true
    }

    @Test
    fun `pinch records compact PDF_CONTINUOUS event`() {
        PdfContinuousTrace.pinch(zoom = 1.75f, renderZoom = 2.5f, distance = 320.5f)

        val entry = NoteTrace.snapshot().single()
        assertEquals("PDF_CONTINUOUS", entry.tag)
        assertEquals("pinch", entry.event)
        assertEquals("1.75", entry.fields["zoom"])
        assertEquals("2.5", entry.fields["renderZoom"])
        assertEquals("320.5", entry.fields["distance"])
    }

    @Test
    fun `render records compact PDF_CONTINUOUS event`() {
        PdfContinuousTrace.render(
            page = 3,
            zoom = 1.75f,
            renderZoom = 2.5f,
            scale = 1.5f,
            bitmapWidth = 1240,
            bitmapHeight = 1754,
        )

        val entry = NoteTrace.snapshot().single()
        assertEquals("PDF_CONTINUOUS", entry.tag)
        assertEquals("render", entry.event)
        assertEquals("3", entry.fields["page"])
        assertEquals("1.75", entry.fields["zoom"])
        assertEquals("2.5", entry.fields["renderZoom"])
        assertEquals("1.5", entry.fields["scale"])
        assertEquals("1240x1754", entry.fields["bitmap"])
    }

    @Test
    fun `clear before and after keeps snapshots isolated`() {
        PdfContinuousTrace.pinch(1f, 1f, 10f)
        assertEquals(1, NoteTrace.snapshot().size)

        NoteTrace.clear()
        assertTrue(NoteTrace.snapshot().isEmpty())

        PdfContinuousTrace.render(0, 1f, 1f, 1f, 100, 200)
        val entry = NoteTrace.snapshot().single()
        assertEquals("render", entry.event)
        assertEquals("100x200", entry.fields["bitmap"])
    }

    @Test
    fun `disabled logging does not throw and records nothing`() {
        NoteTrace.enabled = false

        PdfContinuousTrace.pinch(2f, 2f, 15f)
        PdfContinuousTrace.render(1, 2f, 2f, 1f, 50, 60)

        assertTrue(NoteTrace.snapshot().isEmpty())
    }
}
