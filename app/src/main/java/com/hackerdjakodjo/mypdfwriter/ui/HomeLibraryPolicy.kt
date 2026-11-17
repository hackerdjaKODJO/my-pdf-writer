package com.hackerdjakodjo.mypdfwriter.ui

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
