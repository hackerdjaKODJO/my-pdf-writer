package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc

/** User-selectable Home ordering. Labels are shown verbatim in the sort menu. */
internal enum class LibrarySort(val label: String) {
    RECENTLY_MODIFIED("최근 수정순"),
    NAME("이름순"),
    RECENTLY_OPENED("최근 열기순"),
    PAGE_COUNT("페이지 수순"),
}

/**
 * Filters the tag overview by a case-insensitive tag-name query. Keeping this
 * out of the composable makes the home search behavior testable without a
 * device and prevents the tag tab from showing a live field that filters
 * nothing.
 */
internal fun filterTagCounts(
    tagCounts: List<Pair<String, Int>>,
    query: String,
): List<Pair<String, Int>> {
    val needle = query.trim()
    if (needle.isEmpty()) return tagCounts
    return tagCounts.filter { (tag, _) -> tag.contains(needle, ignoreCase = true) }
}

/** True only when a PDF search is hiding documents that actually exist. */
internal fun pdfEmptyStateIsFiltered(hasAnyPdf: Boolean, query: String): Boolean =
    hasAnyPdf && query.isNotBlank()

/**
 * Returns a sorted copy of [items]. Notes have no separate "last opened"
 * timestamp, so both time-based options use [Notebook.updatedAt], which is also
 * touched when a note is opened. Ties keep the incoming order (stable sort).
 */
internal fun sortNotebooks(items: List<Notebook>, sort: LibrarySort): List<Notebook> =
    when (sort) {
        LibrarySort.RECENTLY_MODIFIED,
        LibrarySort.RECENTLY_OPENED,
        -> items.sortedByDescending { it.updatedAt }

        LibrarySort.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

        LibrarySort.PAGE_COUNT -> items.sortedByDescending { it.pageCount }
    }

/**
 * Returns a sorted copy of [items]. PDFs have no separate "last modified"
 * timestamp, so both time-based options use [RecentDoc.lastOpenedAt]. Ties keep
 * the incoming order (stable sort).
 */
internal fun sortPdfs(items: List<RecentDoc>, sort: LibrarySort): List<RecentDoc> =
    when (sort) {
        LibrarySort.RECENTLY_MODIFIED,
        LibrarySort.RECENTLY_OPENED,
        -> items.sortedByDescending { it.lastOpenedAt }

        LibrarySort.NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        LibrarySort.PAGE_COUNT -> items.sortedByDescending { it.pageCount }
    }
