package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType

/**
 * Categories shown as filter chips in the PDF annotation panel.
 *
 * This is presentation grouping only; the stored [AnnotationType] is never
 * changed and annotations are never merged or rewritten.
 */
enum class AnnotationFilter(val label: String) {
    ALL("전체"),
    INK("필기"),
    TEXT("텍스트"),
    SHAPES("도형"),
    REDACT("가림"),
}

/**
 * Pure, display-only helpers for the PDF annotation panel.
 *
 * The filter never mutates annotations and never drops them from the document;
 * it only produces the rows the sheet should render.
 */
object PdfAnnotationFilter {

    /** Map every stored type to exactly one panel category. */
    fun categoryOf(type: AnnotationType): AnnotationFilter = when (type) {
        AnnotationType.PEN,
        AnnotationType.HIGHLIGHT,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.SIGNATURE,
        -> AnnotationFilter.INK

        AnnotationType.TEXT,
        AnnotationType.STAMP,
        AnnotationType.COVER_TEXT,
        -> AnnotationFilter.TEXT

        AnnotationType.RECT,
        AnnotationType.ELLIPSE,
        AnnotationType.LINE,
        AnnotationType.ARROW,
        -> AnnotationFilter.SHAPES

        AnnotationType.REDACT -> AnnotationFilter.REDACT
    }

    fun matches(type: AnnotationType, filter: AnnotationFilter): Boolean =
        filter == AnnotationFilter.ALL || categoryOf(type) == filter

    /**
     * Sanitized rows for the panel.
     *
     * Removes rows that cannot be shown safely (blank ids, duplicate ids, pages
     * outside `0 until pageCount`), keeps only [filter] matches, then orders by
     * ascending page and stored [AnnotationType] order. Kotlin's stable sort
     * leaves the document's natural order intact inside one page/type group, so
     * repeated calls over the same input are deterministic.
     */
    fun filter(
        annotations: List<Annotation>,
        filter: AnnotationFilter = AnnotationFilter.ALL,
        pageCount: Int = Int.MAX_VALUE,
    ): List<Annotation> {
        if (annotations.isEmpty()) return emptyList()
        val limit = pageCount.coerceAtLeast(0)
        if (limit == 0) return emptyList()

        val seenIds = HashSet<String>(annotations.size)
        return annotations
            .asSequence()
            .filter { it.id.isNotBlank() && it.page >= 0 && it.page < limit }
            .filter { seenIds.add(it.id) }
            .filter { matches(it.type, filter) }
            .sortedWith(compareBy<Annotation>({ it.page }, { it.type.ordinal }))
            .toList()
    }
}
