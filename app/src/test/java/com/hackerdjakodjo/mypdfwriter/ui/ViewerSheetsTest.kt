package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun pageJumpAcceptsOnlyNumbersInsideTheDocument() {
        assertEquals(0, parsePageJump("1", 12))
        assertEquals(41, parsePageJump(" 42 ", 100))
        assertEquals(11, parsePageJump("12", 12))
        org.junit.Assert.assertNull(parsePageJump("0", 12))
        org.junit.Assert.assertNull(parsePageJump("13", 12))
        org.junit.Assert.assertNull(parsePageJump("abc", 12))
        org.junit.Assert.assertNull(parsePageJump("", 12))
        org.junit.Assert.assertNull(parsePageJump("3", 0))
    }

    @Test
    fun displaySettingsStartWithTheScreenRowAndCollapseCropMargins() {
        assertFalse("여백 자르기 starts collapsed", PDF_DISPLAY_CROP_DEFAULT_EXPANDED)
        assertFalse(displayCropSectionVisible(PDF_DISPLAY_CROP_DEFAULT_EXPANDED))
        assertTrue(displayCropSectionVisible(true))
        // The primary row owns both day/night and brightness; crop is separate.
        assertEquals(listOf("주간", "야간", "밝기"), DISPLAY_PRIMARY_CONTROLS)
        assertTrue("홀수·짝수 and 초기화 live inside crop", CROP_SECTION_CONTROLS.containsAll(listOf("홀수·짝수", "여백 초기화")))
    }
}
