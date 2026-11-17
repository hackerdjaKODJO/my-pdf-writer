package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNavigationTest {

    @Test
    fun clampKeepsIndexInRangeAndReturnsMinusOneForNoHits() {
        assertEquals(0, SearchNavigation.clamp(0, 3))
        assertEquals(2, SearchNavigation.clamp(2, 3))
        assertEquals(2, SearchNavigation.clamp(99, 3))
        assertEquals(0, SearchNavigation.clamp(-7, 3))
        assertEquals(-1, SearchNavigation.clamp(0, 0))
        assertEquals(-1, SearchNavigation.clamp(4, -2))
    }

    @Test
    fun nextWrapsFromLastToFirst() {
        assertEquals(1, SearchNavigation.next(0, 3))
        assertEquals(2, SearchNavigation.next(1, 3))
        assertEquals(0, SearchNavigation.next(2, 3))
        assertEquals(0, SearchNavigation.next(0, 1))
    }

    @Test
    fun previousWrapsFromFirstToLast() {
        assertEquals(2, SearchNavigation.previous(0, 3))
        assertEquals(0, SearchNavigation.previous(1, 3))
        assertEquals(1, SearchNavigation.previous(2, 3))
        assertEquals(0, SearchNavigation.previous(0, 1))
    }

    @Test
    fun emptyResultsNeverProduceAValidPosition() {
        assertEquals(-1, SearchNavigation.next(0, 0))
        assertEquals(-1, SearchNavigation.previous(0, 0))
        assertEquals(-1, SearchNavigation.next(-1, 0))
        assertTrue(SearchNavigation.hitsForPages(listOf("anything"), "   ").isEmpty())
        assertTrue(SearchNavigation.hitsForPages(emptyList(), "query").isEmpty())
    }

    @Test
    fun outOfRangePositionsAreClampedBeforeStepping() {
        assertEquals(0, SearchNavigation.next(99, 3))
        assertEquals(2, SearchNavigation.previous(-9, 3))
        assertEquals(1, SearchNavigation.next(-1, 3))
    }

    @Test
    fun queryChangeResetsToFirstResultWhileSameQueryKeepsPosition() {
        assertEquals(
            0,
            SearchNavigation.indexAfterQueryChange(
                currentIndex = 2,
                previousQuery = "hello",
                newQuery = "world",
                count = 5,
            ),
        )
        assertEquals(
            2,
            SearchNavigation.indexAfterQueryChange(
                currentIndex = 2,
                previousQuery = "hello",
                newQuery = "hello",
                count = 5,
            ),
        )
        assertEquals(
            -1,
            SearchNavigation.indexAfterQueryChange(
                currentIndex = 2,
                previousQuery = "hello",
                newQuery = "world",
                count = 0,
            ),
        )
        assertEquals(
            2,
            SearchNavigation.indexAfterQueryChange(
                currentIndex = 9,
                previousQuery = "hello",
                newQuery = "HELLO ",
                count = 3,
            ),
        )
    }

    @Test
    fun hitsForPagesAreOrderedByPageAndCountOccurrences() {
        val texts = listOf(
            "No match here",
            "Alpha alpha and ALPHA again",
            "plain text",
            "alpha at the end",
        )

        val hits = SearchNavigation.hitsForPages(texts, "alpha")

        assertEquals(listOf(1, 3), hits.map { it.page })
        assertEquals(3, hits[0].count)
        assertEquals(1, hits[1].count)
        assertTrue(hits[0].snippet.lowercase().contains("alpha"))
        assertTrue(hits[1].snippet.lowercase().contains("alpha"))
    }

    @Test
    fun orderedSortsShuffledHitsByPageWithoutDroppingAny() {
        val shuffled = listOf(
            SearchHit(page = 4, count = 2, snippet = "d"),
            SearchHit(page = 0, count = 1, snippet = "a"),
            SearchHit(page = 2, count = 3, snippet = "c"),
        )

        val ordered = SearchNavigation.ordered(shuffled)

        assertEquals(listOf(0, 2, 4), ordered.map { it.page })
        assertEquals(shuffled.toSet(), ordered.toSet())
    }
}
