package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfTextLayoutTest {

    private val epsilon = 0.0005f

    @Test
    fun unrotatedGlyphMapsToTopLeftDisplayCoordinates() {
        val rect = PdfTextGeometry.glyphRect(
            baselineX = 10f,
            baselineY = 190f,
            advanceX = 20f,
            advanceY = 0f,
            upX = 0f,
            upY = 10f,
            pageWidth = 100f,
            pageHeight = 200f,
            rotation = 0,
        )
        requireNotNull(rect)
        assertEquals(0.1f, rect.left, epsilon)
        assertEquals(0f, rect.top, epsilon)
        assertEquals(0.3f, rect.right, epsilon)
        assertEquals(0.059f, rect.bottom, epsilon)
    }

    @Test
    fun rotation90SwapsTheDisplayAxes() {
        val rect = glyphRect(rotation = 90)
        requireNotNull(rect)
        assertEquals(0.941f, rect.left, epsilon)
        assertEquals(0.1f, rect.top, epsilon)
        assertEquals(1f, rect.right, epsilon)
        assertEquals(0.3f, rect.bottom, epsilon)
    }

    @Test
    fun rotation180FlipsBothAxes() {
        val rect = glyphRect(rotation = 180)
        requireNotNull(rect)
        assertEquals(0.7f, rect.left, epsilon)
        assertEquals(0.941f, rect.top, epsilon)
        assertEquals(0.9f, rect.right, epsilon)
        assertEquals(1f, rect.bottom, epsilon)
    }

    @Test
    fun rotation270SwapsAndFlipsTheDisplayAxes() {
        val rect = glyphRect(rotation = 270)
        requireNotNull(rect)
        assertEquals(0f, rect.left, epsilon)
        assertEquals(0.7f, rect.top, epsilon)
        assertEquals(0.059f, rect.right, epsilon)
        assertEquals(0.9f, rect.bottom, epsilon)
    }

    @Test
    fun unsupportedRotationIsSkipped() {
        assertNull(glyphRect(rotation = 45))
        assertNull(PdfTextGeometry.mapPoint(40f, 440f, 300f, 500f, 45))
        assertNull(glyphRect(rotation = 0, pageWidth = 0f))
    }

    @Test
    fun pointMappingMatchesPdfBoxDisplayCoordinates() {
        val p0 = PdfTextGeometry.mapPoint(40f, 440f, 300f, 500f, 0)
        requireNotNull(p0)
        assertEquals(0.1333f, p0.first, epsilon)
        assertEquals(0.12f, p0.second, epsilon)

        val p90 = PdfTextGeometry.mapPoint(40f, 440f, 300f, 500f, 90)
        requireNotNull(p90)
        assertEquals(0.88f, p90.first, epsilon)
        assertEquals(0.1333f, p90.second, epsilon)

        val p180 = PdfTextGeometry.mapPoint(40f, 440f, 300f, 500f, 180)
        requireNotNull(p180)
        assertEquals(0.8667f, p180.first, epsilon)
        assertEquals(0.88f, p180.second, epsilon)

        val p270 = PdfTextGeometry.mapPoint(40f, 440f, 300f, 500f, 270)
        requireNotNull(p270)
        assertEquals(0.12f, p270.first, epsilon)
        assertEquals(0.8667f, p270.second, epsilon)
    }

    @Test
    fun selectionTextGroupsVisualLinesInReadingOrder() {
        val hello = word("Hello", 0.1f, 0.1f, 0.2f, 0.12f)
        val world = word("world", 0.21f, 0.101f, 0.3f, 0.121f)
        val second = word("Second", 0.1f, 0.2f, 0.2f, 0.22f)
        val line = word("line", 0.21f, 0.2f, 0.27f, 0.22f)

        assertEquals(
            "Hello world\nSecond line",
            PdfTextLayout.selectionText(listOf(second, world, line, hello)),
        )
    }

    @Test
    fun groupByLineUsesCachedLineOrder() {
        val hello = word("Hello", 0.1f, 0.1f, 0.2f, 0.12f)
        val world = word("world", 0.21f, 0.101f, 0.3f, 0.121f)
        val second = word("Second", 0.1f, 0.2f, 0.2f, 0.22f)
        val line = word("line", 0.21f, 0.2f, 0.27f, 0.22f)
        val lines = listOf(
            PdfTextLine(
                pageIndex = 0,
                text = "Hello world",
                words = listOf(hello, world),
                rect = hello.rect.union(world.rect),
            ),
            PdfTextLine(
                pageIndex = 0,
                text = "Second line",
                words = listOf(second, line),
                rect = second.rect.union(line.rect),
            ),
        )

        val groups = PdfTextLayout.groupByLine(listOf(line, hello, second), lines)
        assertEquals(2, groups.size)
        assertEquals(listOf("Hello"), groups[0].map { it.text })
        assertEquals(listOf("Second", "line"), groups[1].map { it.text })
    }

    private fun glyphRect(rotation: Int, pageWidth: Float = 100f): NormalizedRect? =
        PdfTextGeometry.glyphRect(
            baselineX = 10f,
            baselineY = 190f,
            advanceX = 20f,
            advanceY = 0f,
            upX = 0f,
            upY = 10f,
            pageWidth = pageWidth,
            pageHeight = 200f,
            rotation = rotation,
        )

    private fun word(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): PdfTextWord = PdfTextWord(0, text, NormalizedRect(left, top, right, bottom))
}
