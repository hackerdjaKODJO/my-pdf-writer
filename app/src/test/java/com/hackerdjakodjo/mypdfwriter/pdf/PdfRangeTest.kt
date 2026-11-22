package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfRangeTest {

    @Test
    fun parse_handlesMixedRangesAndOpenEnd() {
        assertEquals(
            listOf(0, 1, 2, 4, 7, 8, 9),
            PdfRange.parse("1-3,5,8-", 10),
        )
    }

    @Test
    fun parse_clampsPagesOutsideTheDocument() {
        assertEquals(listOf(0, 1, 2, 3, 4), PdfRange.parse("0,2-99", 5))
        assertEquals(listOf(4), PdfRange.parse("8-", 5))
        assertEquals(listOf(4), PdfRange.parse("99", 5))
    }

    @Test
    fun parse_keepsFirstOccurrenceOrderAndIgnoresBlanks() {
        assertEquals(listOf(2, 0, 1), PdfRange.parse(" 3 , 1-2 ,3, ", 5))
        assertEquals(listOf(0, 1, 2), PdfRange.parse(" -3 ", 5))
    }

    @Test
    fun parse_skipsMalformedAndReversedRanges() {
        assertEquals(emptyList<Int>(), PdfRange.parse("", 10))
        assertEquals(emptyList<Int>(), PdfRange.parse(" , , ", 10))
        assertEquals(emptyList<Int>(), PdfRange.parse("5-3", 10))
        assertEquals(emptyList<Int>(), PdfRange.parse("abc,1-3-5,-", 10))
        assertEquals(emptyList<Int>(), PdfRange.parse("2-4", 0))
    }

    @Test
    fun chunks_splitsIntoInclusiveRanges() {
        assertEquals(
            listOf(0..2, 3..5, 6..8, 9..9),
            PdfRange.chunks(10, 3),
        )
        assertEquals(listOf(0..9), PdfRange.chunks(10, 10))
        assertEquals(listOf(0..9), PdfRange.chunks(10, 20))
        assertEquals(listOf(0..0), PdfRange.chunks(1, 1))
        assertEquals(emptyList<IntRange>(), PdfRange.chunks(0, 3))
    }

    @Test
    fun chunks_rejectsNonPositiveSize() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfRange.chunks(10, 0)
        }
    }

    @Test
    fun describeShowsResolvedPagesAndFlagsClamping() {
        val described = PdfRange.describe("1-3,5", 10)
        assertEquals(listOf(0, 1, 2, 4), described.pages)
        assertEquals("1, 2, 3, 5 · 4쪽", described.summary)
        org.junit.Assert.assertFalse(described.clamped)

        val clamped = PdfRange.describe("99", 10)
        assertEquals(listOf(9), clamped.pages)
        org.junit.Assert.assertTrue(clamped.clamped)
        assertEquals("선택된 쪽 없음", PdfRange.describe("", 10).summary)
    }
}
