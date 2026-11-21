package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.ui.PdfAnnotationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationSummaryTest {

    @Test
    fun rowsGroupByPageAndCarryTypeColorAndSnippet() {
        val input = listOf(
            annotation(AnnotationType.HIGHLIGHT, page = 2, id = "h2", color = 0xFFFDD835.toInt(), text = " wichtig\n  text "),
            annotation(AnnotationType.PEN, page = 0, id = "p0", color = 0xFF1E88E5.toInt()),
            annotation(AnnotationType.REDACT, page = 2, id = "r2", color = 0xFF000000.toInt()),
        )

        val rows = AnnotationSummary.rows(input, pageCount = 5)

        assertEquals(listOf(0, 2, 2), rows.map { it.page })
        assertEquals("필기", rows[0].let { AnnotationSummary.typeLabel(it.type) })
        assertEquals("파랑", rows[0].colorName)
        assertEquals("필기 획", rows[0].snippet)
        assertEquals("형광펜", rows[1].let { AnnotationSummary.typeLabel(it.type) })
        assertEquals("노랑", rows[1].colorName)
        assertEquals("wichtig text", rows[1].snippet)
        assertEquals("가림", rows[2].let { AnnotationSummary.typeLabel(it.type) })
        assertEquals("검정", rows[2].colorName)
    }

    @Test
    fun markdownHasOnePageHeadingPerPageAndFourFieldsPerRow() {
        val input = listOf(
            annotation(AnnotationType.TEXT, page = 1, id = "t1", color = 0xFFE53935.toInt(), text = "memo"),
            annotation(AnnotationType.RECT, page = 1, id = "b1", color = 0xFF43A047.toInt()),
            annotation(AnnotationType.STAMP, page = 0, id = "s0", color = 0xFFFB8C00.toInt(), stamp = "승인"),
        )

        val lines = AnnotationSummary.lines(input, pageCount = 3)

        assertEquals("# 주석 요약", lines.first())
        assertEquals(listOf("## 1쪽", "## 2쪽"), lines.filter { it.startsWith("## ") })
        val rows = lines.filter { it.startsWith("- ") }
        assertEquals(3, rows.size)
        assertTrue(rows[0].contains("도장"))
        assertTrue(rows[0].contains("1쪽"))
        assertTrue(rows[0].contains("주황"))
        assertTrue(rows[0].contains("승인"))
        assertTrue(rows[1].contains("글상자"))
        assertTrue(rows[1].contains("2쪽"))
        assertTrue(rows[1].contains("빨강"))
        assertTrue(rows[1].contains("memo"))
        assertTrue(rows[2].contains("사각형"))
        assertTrue(rows[2].contains("2쪽"))
        assertTrue(rows[2].contains("초록"))
    }

    @Test
    fun markdownIsDeterministicForTheSameRowsInAnyInputOrder() {
        val a = annotation(AnnotationType.PEN, page = 1, id = "a", text = "A")
        val b = annotation(AnnotationType.HIGHLIGHT, page = 0, id = "b", text = "B")
        val c = annotation(AnnotationType.RECT, page = 1, id = "c", text = "C")
        val d = annotation(AnnotationType.TEXT, page = 0, id = "d", text = "D")

        val first = AnnotationSummary.buildMarkdown(listOf(a, b, c, d), pageCount = 4)
        val shuffled = AnnotationSummary.buildMarkdown(listOf(d, c, b, a), pageCount = 4)
        val rebuilt = AnnotationSummary.buildMarkdown(listOf(b, a, d, c), pageCount = 4)

        assertEquals(first, shuffled)
        assertEquals(first, rebuilt)
        assertEquals(first, AnnotationSummary.buildMarkdown(listOf(a, b, c, d), pageCount = 4))
        assertTrue(first.endsWith("\n"))
        assertTrue("exactly one trailing newline", !first.endsWith("\n\n"))
    }

    @Test
    fun rowsFollowTheLocationsPanelPageTypeOrderWithoutAnIdTiebreak() {
        // Ids deliberately sort the opposite way to the document order inside
        // one page/type group. The export used to follow those random ids while
        // the Locations panel kept its stable page/type order.
        val rect = annotation(AnnotationType.RECT, page = 1, id = "a-rect", text = "first")
        val pen1 = annotation(AnnotationType.PEN, page = 1, id = "z-pen", text = "second")
        val earlierPage = annotation(AnnotationType.RECT, page = 0, id = "c-rect", text = "third")
        val pen2 = annotation(AnnotationType.PEN, page = 1, id = "a-pen", text = "fourth")
        val input = listOf(rect, pen1, earlierPage, pen2)

        val rows = AnnotationSummary.rows(input, pageCount = 3)

        assertEquals(listOf("third", "second", "fourth", "first"), rows.map { it.snippet })
        assertEquals(
            "one comparator shared with the Locations panel",
            PdfAnnotationFilter.filter(input, pageCount = 3).map { it.page to it.type },
            rows.map { it.page to it.type },
        )
    }

    @Test
    fun sanitizerDropsBlanksDuplicatesAndOutOfRangePages() {
        val valid = annotation(AnnotationType.PEN, page = 0, id = "valid")
        val input = listOf(
            valid,
            annotation(AnnotationType.TEXT, page = 0, id = ""),
            annotation(AnnotationType.TEXT, page = 0, id = "valid"),
            annotation(AnnotationType.TEXT, page = -1, id = "negative"),
            annotation(AnnotationType.TEXT, page = 4, id = "past-end"),
        )

        val rows = AnnotationSummary.rows(input, pageCount = 4)

        assertEquals(1, rows.size)
        assertEquals("필기 획", rows[0].snippet)
        assertTrue(AnnotationSummary.lines(emptyList(), pageCount = 4).contains("주석이 없습니다."))
        assertTrue(AnnotationSummary.rows(input, pageCount = 0).isEmpty())
    }

    @Test
    fun colorNameUsesNearestPaletteColourDeterministically() {
        assertEquals("빨강", AnnotationSummary.colorName(0xFFFF0000.toInt()))
        assertEquals("검정", AnnotationSummary.colorName(0xFF000000.toInt()))
        assertEquals("하양", AnnotationSummary.colorName(0xFFFFFFFF.toInt()))
        // Alpha must not change the name; equal distances keep palette order.
        assertEquals(
            AnnotationSummary.colorName(0xFF1E88E5.toInt()),
            AnnotationSummary.colorName(0x801E88E5.toInt()),
        )
    }

    private fun annotation(
        type: AnnotationType,
        page: Int,
        id: String,
        color: Int = 0xFF1E88E5.toInt(),
        text: String = "",
        stamp: String = "",
    ): Annotation = Annotation(
        id = id,
        type = type,
        page = page,
        color = color,
        width = 0.0035f,
        text = text,
        stamp = stamp,
    )
}
