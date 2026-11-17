package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.SearchHit

/**
 * Pure rules for stepping through PDF search results.
 *
 * This object deliberately has no Android, Compose or coroutine dependencies so
 * the wrap-around, clamping, query-reset and result-ordering rules are covered by
 * JVM tests. A "hit" here is one row of [ViewerUiState.searchHits], i.e. one page
 * that contains at least one match.
 */
object SearchNavigation {

    /** -1 when there are no hits, otherwise [index] clamped into `0..count-1`. */
    fun clamp(index: Int, count: Int): Int =
        if (count <= 0) -1 else index.coerceIn(0, count - 1)

    /** Next hit with wrap-around. Returns -1 for an empty result set. */
    fun next(index: Int, count: Int): Int =
        if (count <= 0) -1 else (clamp(index, count) + 1) % count

    /** Previous hit with wrap-around. Returns -1 for an empty result set. */
    fun previous(index: Int, count: Int): Int =
        if (count <= 0) -1 else (clamp(index, count) - 1 + count) % count

    /**
     * Index to select after a search query changes.
     *
     * A real query change restarts at the first result (or -1 when there are none).
     * Re-running the same query — for example after the document text is rebuilt —
     * keeps the current position, clamped to the new result count.
     */
    fun indexAfterQueryChange(
        currentIndex: Int,
        previousQuery: String,
        newQuery: String,
        count: Int,
    ): Int {
        if (normalize(previousQuery) != normalize(newQuery)) {
            return if (count <= 0) -1 else 0
        }
        return clamp(currentIndex, count)
    }

    /** Stable reading order for result rows: ascending page, then snippet text. */
    fun ordered(hits: List<SearchHit>): List<SearchHit> =
        hits.sortedWith(compareBy<SearchHit> { it.page }.thenBy { it.snippet })

    /**
     * Builds ordered result rows from one plain-text string per page.
     *
     * Matching is case-insensitive. Each row reports how many occurrences the page
     * contains and a short snippet around the first one. Blank queries produce no
     * hits, and the result is capped so a pathological document cannot flood UI.
     */
    fun hitsForPages(texts: List<String>, query: String): List<SearchHit> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        val hits = ArrayList<SearchHit>()
        for ((index, text) in texts.withIndex()) {
            if (hits.size >= MAX_HITS) break
            if (text.isBlank()) continue
            val flat = text.replace(Regex("\\s+"), " ").trim()
            val lower = flat.lowercase()
            var count = 0
            var from = 0
            while (true) {
                val found = lower.indexOf(needle, from)
                if (found < 0) break
                count++
                from = found + needle.length.coerceAtLeast(1)
            }
            if (count == 0) continue
            val first = lower.indexOf(needle)
            val start = (first - 40).coerceAtLeast(0)
            val end = (first + needle.length + 70).coerceAtMost(flat.length)
            val snippet = buildString {
                if (start > 0) append('…')
                append(flat.substring(start, end))
                if (end < flat.length) append('…')
            }
            hits.add(SearchHit(index, count, snippet))
        }
        return ordered(hits)
    }

    private fun normalize(query: String): String = query.trim().lowercase()

    const val MAX_HITS: Int = 300
}
