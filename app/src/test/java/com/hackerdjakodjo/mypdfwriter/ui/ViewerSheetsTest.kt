package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun pageSheetStartsAtTheClampedCurrentPageWithATopAnchor() {
        assertEquals(InitialListScrollTarget(index = 41, offsetPx = 0), pageSheetInitialScrollTarget(41, 100))
        assertEquals(InitialListScrollTarget(index = 0), pageSheetInitialScrollTarget(-5, 10))
        assertEquals(InitialListScrollTarget(index = 9), pageSheetInitialScrollTarget(99, 10))
        assertNull(pageSheetInitialScrollTarget(0, 0))
        assertNull(pageSheetInitialScrollTarget(3, -1))
    }

    @Test
    fun outlineExactCurrentPageWinsAndNearestEntryHandlesGaps() {
        val entries = listOf(
            PdfOutlineEntry("페이지 없음", pageIndex = null, level = 0),
            PdfOutlineEntry("8쪽", pageIndex = 8, level = 0),
            PdfOutlineEntry("2쪽", pageIndex = 2, level = 0),
            PdfOutlineEntry("5쪽", pageIndex = 5, level = 0),
        )

        assertEquals(3, nearestOutlineEntryIndex(entries, currentPage = 5))
        assertEquals(3, nearestOutlineEntryIndex(entries, currentPage = 6))
        assertEquals(1, nearestOutlineEntryIndex(entries, currentPage = 10))
        // 5쪽 is closer to 4 than 2쪽.
        assertEquals(3, nearestOutlineEntryIndex(entries, currentPage = 4))
        // Equal distances keep the earlier outline position.
        assertEquals(
            1,
            nearestOutlineEntryIndex(
                listOf(PdfOutlineEntry("2쪽", pageIndex = 2), PdfOutlineEntry("4쪽", pageIndex = 4)),
                currentPage = 3,
            ),
        )
    }

    @Test
    fun outlineWithoutAUsableCurrentPositionOpensAtTheTop() {
        val entries = listOf(
            PdfOutlineEntry("표지", pageIndex = null, level = 0),
            PdfOutlineEntry("뒤표지", pageIndex = null, level = 0),
        )

        assertNull(nearestOutlineEntryIndex(emptyList(), currentPage = 4))
        assertNull(nearestOutlineEntryIndex(entries, currentPage = 4))
        assertNull(nearestOutlineEntryIndex(entries, currentPage = -1))
        assertNull(locationsOutlineInitialScrollTarget(null, viewportHeightPx = 1000))
    }

    @Test
    fun outlineCurrentPositionUsesAFortyPercentViewportAnchor() {
        assertEquals(0.40f, LOCATIONS_OUTLINE_CURRENT_VIEWPORT_FRACTION, 0.0001f)
        assertEquals(-400, locationsOutlineScrollOffsetPx(1000))
        assertEquals(-150, locationsOutlineScrollOffsetPx(375))
        assertEquals(0, locationsOutlineScrollOffsetPx(0))
        assertEquals(0, locationsOutlineScrollOffsetPx(-20))
        assertEquals(
            InitialListScrollTarget(index = 12, offsetPx = -400),
            locationsOutlineInitialScrollTarget(12, viewportHeightPx = 1000),
        )
    }

    @Test
    fun repeatedEquivalentScrollTargetsDoNotRepositionTheList() {
        val policy = InitialListScrollPolicy()
        val firstOpen = requireNotNull(pageSheetInitialScrollTarget(6, 20))

        assertTrue(policy.shouldApply(firstOpen))
        policy.markApplied(firstOpen)
        // A recomposition rebuilds an equal target, but it is not a new jump.
        assertFalse(policy.shouldApply(pageSheetInitialScrollTarget(6, 20)))
        assertFalse(policy.shouldApply(null))

        val changedPage = requireNotNull(pageSheetInitialScrollTarget(7, 20))
        assertTrue(policy.shouldApply(changedPage))
        policy.markApplied(changedPage)
        assertFalse(policy.shouldApply(changedPage))
    }
}
