package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfAnnotationFilterTest {

    @Test
    fun allFilterReturnsEveryTypeInPageThenTypeOrder() {
        val input = listOf(
            annotation(AnnotationType.TEXT, page = 1, id = "p1-text"),
            annotation(AnnotationType.REDACT, page = 0, id = "p0-redact"),
            annotation(AnnotationType.PEN, page = 1, id = "p1-pen"),
            annotation(AnnotationType.HIGHLIGHT, page = 0, id = "p0-highlight"),
            annotation(AnnotationType.PEN, page = 0, id = "p0-pen"),
        )

        val rows = PdfAnnotationFilter.filter(input, AnnotationFilter.ALL, pageCount = 10)

        assertEquals(
            listOf("p0-pen", "p0-highlight", "p0-redact", "p1-pen", "p1-text"),
            rows.map { it.id },
        )
        assertTrue(rows.all { PdfAnnotationFilter.matches(it.type, AnnotationFilter.ALL) })
    }

    @Test
    fun inkCategoryContainsOnlyFreehandMarkupAndSignature() {
        val rows = PdfAnnotationFilter.filter(allTypes(), AnnotationFilter.INK)

        assertEquals(
            listOf(
                AnnotationType.PEN,
                AnnotationType.HIGHLIGHT,
                AnnotationType.UNDERLINE,
                AnnotationType.STRIKEOUT,
                AnnotationType.SIGNATURE,
            ),
            rows.map { it.type },
        )
    }

    @Test
    fun textCategoryContainsTextAndStamp() {
        val rows = PdfAnnotationFilter.filter(allTypes(), AnnotationFilter.TEXT)

        assertEquals(
            listOf(AnnotationType.TEXT, AnnotationType.STAMP, AnnotationType.COVER_TEXT),
            rows.map { it.type },
        )
    }

    @Test
    fun shapesCategoryContainsRectEllipseLineAndArrow() {
        val rows = PdfAnnotationFilter.filter(allTypes(), AnnotationFilter.SHAPES)

        assertEquals(
            listOf(
                AnnotationType.RECT,
                AnnotationType.ARROW,
                AnnotationType.ELLIPSE,
                AnnotationType.LINE,
            ),
            rows.map { it.type },
        )
    }

    @Test
    fun redactCategoryContainsOnlyRedactionMarks() {
        val rows = PdfAnnotationFilter.filter(allTypes(), AnnotationFilter.REDACT)

        assertEquals(listOf(AnnotationType.REDACT), rows.map { it.type })
    }

    @Test
    fun samePageAndTypeKeepsDocumentOrderAndRepeatsDeterministically() {
        val input = listOf(
            annotation(AnnotationType.PEN, page = 0, id = "second"),
            annotation(AnnotationType.PEN, page = 0, id = "first"),
            annotation(AnnotationType.HIGHLIGHT, page = 0, id = "third"),
        )

        val first = PdfAnnotationFilter.filter(input, AnnotationFilter.INK, pageCount = 3)
        val second = PdfAnnotationFilter.filter(input, AnnotationFilter.INK, pageCount = 3)

        assertEquals(listOf("second", "first", "third"), first.map { it.id })
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun sanitizerDropsBlankIdsDuplicateIdsAndOutOfRangePages() {
        val input = listOf(
            annotation(AnnotationType.PEN, page = 0, id = "valid"),
            annotation(AnnotationType.PEN, page = 0, id = ""),
            annotation(AnnotationType.PEN, page = 0, id = "valid"),
            annotation(AnnotationType.PEN, page = -1, id = "negative"),
            annotation(AnnotationType.PEN, page = 3, id = "past-end"),
        )

        val rows = PdfAnnotationFilter.filter(input, AnnotationFilter.ALL, pageCount = 3)

        assertEquals(listOf("valid"), rows.map { it.id })
    }

    @Test
    fun emptyInputAndEmptyDocumentReturnEmptyRows() {
        assertTrue(PdfAnnotationFilter.filter(emptyList(), AnnotationFilter.ALL).isEmpty())
        assertTrue(
            PdfAnnotationFilter.filter(
                listOf(annotation(AnnotationType.PEN, page = 0)),
                AnnotationFilter.ALL,
                pageCount = 0,
            ).isEmpty(),
        )
    }

    @Test
    fun countsUseTheSameSanitizedRowsAsTheFilter() {
        val input = listOf(
            annotation(AnnotationType.PEN, page = 0, id = "pen"),
            annotation(AnnotationType.HIGHLIGHT, page = 1, id = "hl"),
            annotation(AnnotationType.TEXT, page = 1, id = "text"),
            annotation(AnnotationType.RECT, page = 2, id = "rect"),
            annotation(AnnotationType.REDACT, page = 2, id = "redact"),
            annotation(AnnotationType.PEN, page = 0, id = "pen"), // duplicate
            annotation(AnnotationType.PEN, page = 9, id = "past-end"),
            annotation(AnnotationType.PEN, page = 0, id = ""),
        )

        val counts = PdfAnnotationFilter.counts(input, pageCount = 3)

        assertEquals(5, counts[AnnotationFilter.ALL])
        assertEquals(2, counts[AnnotationFilter.INK])
        assertEquals(1, counts[AnnotationFilter.TEXT])
        assertEquals(1, counts[AnnotationFilter.SHAPES])
        assertEquals(1, counts[AnnotationFilter.REDACT])
        AnnotationFilter.entries.forEach { option ->
            assertEquals(
                "count must match the filter output for ${option.label}",
                PdfAnnotationFilter.filter(input, option, pageCount = 3).size,
                counts[option],
            )
        }
        assertTrue(PdfAnnotationFilter.counts(emptyList()).all { it.value == 0 })
    }

    private fun allTypes(): List<Annotation> = AnnotationType.entries.mapIndexed { index, type ->
        annotation(type, page = index / 4, id = type.name.lowercase())
    }

    private fun annotation(
        type: AnnotationType,
        page: Int = 0,
        id: String = type.name.lowercase() + "-$page",
    ): Annotation = Annotation(
        id = id,
        type = type,
        page = page,
        color = 0xFF1F4E79.toInt(),
        width = 0.0035f,
    )
}
