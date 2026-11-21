package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search rows must stay distinct even when several hits land on one page. */
class ViewerSheetsTest {

    @Test
    fun repeatedPagesGetUniqueStableKeys() {
        val hits = listOf(
            SearchHit(page = 3, count = 2, snippet = "first"),
            SearchHit(page = 3, count = 1, snippet = "second"),
            SearchHit(page = 3, count = 4, snippet = "third"),
            SearchHit(page = 7, count = 1, snippet = "fourth"),
        )

        val rows = keyedSearchHits(hits)

        assertEquals(hits.size, rows.map { it.key }.toSet().size)
        assertEquals(listOf("search-hit-0", "search-hit-1", "search-hit-2", "search-hit-3"), rows.map { it.key })
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.index })
        assertEquals(hits, rows.map { it.hit })
        // Rebuilding rows for the same result list keeps the same keys/indices.
        assertEquals(rows, keyedSearchHits(hits))
    }

    @Test
    fun emptyResultsProduceNoRows() {
        assertTrue(keyedSearchHits(emptyList()).isEmpty())
    }
}
