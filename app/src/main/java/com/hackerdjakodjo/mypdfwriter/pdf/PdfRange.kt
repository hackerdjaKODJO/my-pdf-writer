package com.hackerdjakodjo.mypdfwriter.pdf

/**
 * Pure, side-effect-free page range helpers.
 *
 * Page expressions are 1-based (`1-3,5,8-`); results are zero-based page indices.
 * Out-of-range numbers are clamped to the nearest valid page, so the parser never
 * produces an index that cannot be used on the document.
 */
object PdfRange {

    fun parse(spec: String, pageCount: Int): List<Int> {
        if (pageCount <= 0 || spec.isBlank()) return emptyList()

        val pages = LinkedHashSet<Int>()
        for (rawToken in spec.split(',')) {
            val token = rawToken.trim()
            if (token.isEmpty()) continue

            val dash = token.indexOf('-')
            if (dash < 0) {
                val page = token.toIntOrNull() ?: continue
                pages.add(page.coerceIn(1, pageCount) - 1)
                continue
            }

            val startText = token.substring(0, dash).trim()
            val endText = token.substring(dash + 1).trim()
            if (startText.isEmpty() && endText.isEmpty()) continue

            val start = startText.toIntOrNull()
                ?: if (startText.isEmpty()) 1 else continue
            val end = endText.toIntOrNull()
                ?: if (endText.isEmpty()) pageCount else continue

            val first = start.coerceIn(1, pageCount)
            val last = end.coerceIn(1, pageCount)
            if (first > last) continue
            for (page in first..last) pages.add(page - 1)
        }
        return pages.toList()
    }

    fun chunks(pageCount: Int, every: Int): List<IntRange> {
        require(every > 0) { "묶음 크기는 1 이상이어야 합니다" }
        if (pageCount <= 0) return emptyList()

        val result = ArrayList<IntRange>()
        var start = 0
        while (start < pageCount) {
            val remaining = pageCount - start
            val end = if (every >= remaining) pageCount - 1 else start + every - 1
            result.add(start..end)
            start = end + 1
        }
        return result
    }
}
