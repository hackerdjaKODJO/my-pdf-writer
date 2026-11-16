package com.hackerdjakodjo.mypdfwriter.pdf

/** One search hit rendered as a highlight box on a page. */
data class SearchMatchRect(
    val pageIndex: Int,
    val rect: NormalizedRect,
    val text: String,
)

/**
 * Maps plain-text search queries onto [PdfTextLayout] line geometry.
 *
 * Matching is case-insensitive and whitespace-normalized, so a query typed with
 * extra spaces still finds Korean, English, or mixed-language lines. Each hit is
 * the union of every word it touches. Hits that touch, overlap, or sit within a
 * hair's breadth of each other on the same line are coalesced into one rect so
 * the translucent overlay never stacks paint on the same pixels.
 *
 * Coordinates stay in the same normalized page-display space as
 * [PdfTextWord.rect]; drawing helpers only need to multiply by the rendered page
 * width and height. This file intentionally has no Android dependencies.
 */
object SearchMatchLayout {

    /**
     * Returns every hit for [query] across [lines], in reading order.
     * Blank queries and empty lines produce no results.
     */
    fun matches(lines: List<PdfTextLine>, query: String): List<SearchMatchRect> {
        val needle = normalizeSearchText(query)
        if (needle.isEmpty()) return emptyList()
        val result = ArrayList<SearchMatchRect>()
        for (line in lines) {
            result += matchesInLine(line, needle)
        }
        return result
    }

    /** Returns only the hits belonging to [pageIndex]. */
    fun matchesForPage(
        lines: List<PdfTextLine>,
        pageIndex: Int,
        query: String,
    ): List<SearchMatchRect> {
        val needle = normalizeSearchText(query)
        if (needle.isEmpty()) return emptyList()
        val result = ArrayList<SearchMatchRect>()
        for (line in lines) {
            if (line.pageIndex != pageIndex) continue
            result += matchesInLine(line, needle)
        }
        return result
    }

    private fun matchesInLine(line: PdfTextLine, needle: String): List<SearchMatchRect> {
        if (line.words.isEmpty()) return emptyList()
        val indexed = IndexedLine.from(line.words) ?: return emptyList()

        val segments = ArrayList<Segment>()
        var from = 0
        while (from <= indexed.text.length - needle.length) {
            val start = indexed.text.indexOf(needle, from, ignoreCase = true)
            if (start < 0) break
            val end = start + needle.length
            val rect = unionForRange(line.words, indexed.owners, start, end)
            if (rect != null) {
                segments += Segment(start = start, end = end, rect = rect)
            }
            from = start + needle.length
        }

        if (segments.isEmpty()) return emptyList()
        val merged = ArrayList<Segment>(segments.size)
        for (segment in segments) {
            val previous = merged.lastOrNull()
            if (previous != null && shouldMerge(previous, segment)) {
                merged[merged.lastIndex] = Segment(
                    start = previous.start,
                    end = segment.end,
                    rect = previous.rect.union(segment.rect),
                )
            } else {
                merged += segment
            }
        }
        return merged.map {
            SearchMatchRect(
                pageIndex = line.pageIndex,
                rect = it.rect,
                text = indexed.text.substring(it.start, it.end),
            )
        }
    }

    /** Unions the rects of all words touched by [start, end) in the line text. */
    private fun unionForRange(
        words: List<PdfTextWord>,
        owners: IntArray,
        start: Int,
        end: Int,
    ): NormalizedRect? {
        var union: NormalizedRect? = null
        for (index in start until end) {
            val owner = owners[index]
            if (owner < 0) continue
            val rect = words.getOrNull(owner)?.rect ?: continue
            union = union?.union(rect) ?: rect
        }
        return union
    }

    /**
     * Coalesces hits whose text ranges touch or whose boxes overlap/touch.
     * Separate occurrences of the same word stay separate unless PDF extraction
     * left no visible gap between their boxes.
     */
    private fun shouldMerge(previous: Segment, next: Segment): Boolean {
        if (next.start <= previous.end) return true
        if (previous.rect.intersects(next.rect)) return true
        val gap = maxOf(
            next.rect.left - previous.rect.right,
            previous.rect.left - next.rect.right,
        )
        val tolerance = maxOf(previous.rect.height, next.rect.height) * MERGE_GAP_FRACTION
        return gap <= tolerance
    }

    private const val MERGE_GAP_FRACTION = 0.02f

    private data class Segment(
        val start: Int,
        val end: Int,
        val rect: NormalizedRect,
    )

    /**
     * A line's words joined by single spaces, plus the source word index for
     * every character. Separator spaces carry -1 so a match spanning words can
     * still be mapped back to the words that produced it.
     */
    private class IndexedLine(
        val text: String,
        val owners: IntArray,
    ) {
        companion object {
            fun from(words: List<PdfTextWord>): IndexedLine? {
                val builder = StringBuilder()
                val owners = ArrayList<Int>()
                words.forEachIndexed { index, word ->
                    val normalized = normalizeSearchText(word.text)
                    if (normalized.isEmpty()) return@forEachIndexed
                    if (builder.isNotEmpty()) {
                        builder.append(' ')
                        owners += -1
                    }
                    for (char in normalized) {
                        builder.append(char)
                        owners += index
                    }
                }
                if (builder.isEmpty()) return null
                return IndexedLine(builder.toString(), owners.toIntArray())
            }
        }
    }
}

/**
 * Collapses every run of Unicode whitespace to one space and trims both ends.
 * Keeping this manual (rather than a regex) handles ideographic/NBSP spacing
 * that commonly appears in Korean PDFs.
 */
private fun normalizeSearchText(value: String): String {
    val builder = StringBuilder(value.length)
    var pendingSpace = false
    for (char in value) {
        if (char.isWhitespace()) {
            pendingSpace = builder.isNotEmpty()
        } else {
            if (pendingSpace) builder.append(' ')
            builder.append(char)
            pendingSpace = false
        }
    }
    return builder.toString()
}
