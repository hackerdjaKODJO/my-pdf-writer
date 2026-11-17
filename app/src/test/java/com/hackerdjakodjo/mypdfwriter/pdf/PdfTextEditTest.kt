package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure geometry for the visual text-replacement overlay. These tests document that
 * the helper only computes a cover rectangle/font size; it never touches PDF bytes.
 */
class PdfTextEditTest {

    private val epsilon = 0.0001f

    @Test
    fun emptyOrUnusableSelectionReturnsNull() {
        assertNull(PdfTextEdit.geometry(emptyList()))
        assertNull(
            PdfTextEdit.geometry(
                listOf(
                    word(" ", 0.1f, 0.1f, 0.2f, 0.12f),
                    word("", 0.2f, 0.1f, 0.3f, 0.12f),
                ),
            ),
        )
        assertNull(PdfTextEdit.geometry(listOf(word("x", 0.2f, 0.2f, 0.2f, 0.2f))))
    }

    @Test
    fun geometryUnionsWordsAndAddsSymmetricPadding() {
        val geometry = PdfTextEdit.geometry(
            listOf(
                word("가", 0.10f, 0.20f, 0.14f, 0.24f),
                word("나", 0.16f, 0.20f, 0.20f, 0.24f),
            ),
        )
        requireNotNull(geometry)
        assertEquals(0, geometry.page)
        assertEquals(0.10f - 0.04f * 0.22f, geometry.rect.left, epsilon)
        assertEquals(0.20f - 0.04f * 0.14f, geometry.rect.top, epsilon)
        assertEquals(0.20f + 0.04f * 0.22f, geometry.rect.right, epsilon)
        assertEquals(0.24f + 0.04f * 0.14f, geometry.rect.bottom, epsilon)
        assertTrue(geometry.rect.width > 0.10f)
        assertTrue(geometry.rect.height > 0.04f)
        assertEquals(0.04f * 0.86f, geometry.fontSize, epsilon)
    }

    @Test
    fun paddingIsClampedToThePage() {
        val geometry = PdfTextEdit.geometry(listOf(word("A", 0f, 0f, 1f, 0.02f)))
        requireNotNull(geometry)
        assertEquals(0f, geometry.rect.left, epsilon)
        assertEquals(0f, geometry.rect.top, epsilon)
        assertEquals(1f, geometry.rect.right, epsilon)
        assertEquals(0.02f + 0.02f * 0.14f, geometry.rect.bottom, epsilon)
    }

    @Test
    fun wordsFromOtherPagesAreIgnored() {
        val geometry = PdfTextEdit.geometry(
            listOf(
                word("한", 0.10f, 0.10f, 0.14f, 0.14f, page = 3),
                word("다른 쪽", 0.60f, 0.60f, 0.90f, 0.64f, page = 4),
            ),
        )
        requireNotNull(geometry)
        assertEquals(3, geometry.page)
        assertTrue(geometry.rect.right < 0.2f)
    }

    @Test
    fun thinnerDimensionDrivesFontSizeForRotatedPageText() {
        // A vertical glyph run is tall and narrow; its "line height" is the width.
        val vertical = PdfTextEdit.geometry(listOf(word("세", 0.10f, 0.10f, 0.13f, 0.40f)))
        val horizontal = PdfTextEdit.geometry(listOf(word("세", 0.10f, 0.10f, 0.40f, 0.13f)))
        assertNotNull(vertical)
        assertNotNull(horizontal)
        assertEquals(horizontal!!.fontSize, vertical!!.fontSize, epsilon)
        assertEquals(0.03f * 0.86f, vertical.fontSize, epsilon)
    }

    @Test
    fun fontSizeIsClampedToAReadableRange() {
        val tiny = PdfTextEdit.geometry(listOf(word("·", 0.10f, 0.10f, 0.1005f, 0.1005f)))
        val huge = PdfTextEdit.geometry(listOf(word("W", 0.10f, 0.10f, 0.90f, 0.60f)))
        requireNotNull(tiny)
        requireNotNull(huge)
        assertEquals(0.012f, tiny.fontSize, epsilon)
        assertEquals(0.06f, huge.fontSize, epsilon)
    }

    private fun word(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        page: Int = 0,
    ): PdfTextWord = PdfTextWord(
        pageIndex = page,
        text = text,
        rect = NormalizedRect(left, top, right, bottom),
    )
}
