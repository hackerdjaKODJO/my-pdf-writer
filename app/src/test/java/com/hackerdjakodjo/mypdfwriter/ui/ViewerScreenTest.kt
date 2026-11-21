package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * System Back must dismiss the topmost viewer layer. Closing the document while
 * a sheet is open is the regression covered here, with special attention to the
 * sheets introduced by the simplified student menus.
 */
class ViewerScreenTest {

    @Test
    fun idleViewerLetsBackCloseTheDocument() {
        assertFalse(ViewerSheetFlags().anyOpen)
    }

    @Test
    fun unifiedLocationAndFileToolsSheetInterceptBack() {
        assertTrue(ViewerSheetFlags(showLocations = true).anyOpen)
        assertTrue(ViewerSheetFlags(showFileTools = true).anyOpen)
    }

    @Test
    fun memoCardsSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showMemoCardsSheet = true).anyOpen)
    }

    @Test
    fun penBoxSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showViewerPenBox = true).anyOpen)
    }

    @Test
    fun recentDocumentsSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showRecentDocs = true).anyOpen)
    }

    @Test
    fun tabOverflowSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showTabOverflow = true).anyOpen)
    }

    @Test
    fun topChromeHasExactlyFourControls() {
        assertEquals(PDF_TOP_CHROME_MAX_CONTROLS, 4)
        assertEquals(
            listOf("뒤로", "문서 이름", "검색", "학습 메뉴"),
            pdfTopChromeControls.map { it.label },
        )
        val labels = pdfTopChromeControls.map { it.label }.toSet()
        assertFalse("북마크 이동", "북마크" in labels)
        assertFalse("노트 바로가기 이동", "노트" in labels)
        assertFalse("자동 숨김 이동", "메뉴 자동 숨김" in labels)
    }

    @Test
    fun coachShowsOnceThenStaysDismissedForTheSession() {
        val firstOpen = PdfCoachState()
        assertTrue(shouldShowPdfCoach(firstOpen))

        val dismissed = dismissPdfCoach(firstOpen)
        assertFalse(shouldShowPdfCoach(dismissed))
        // Opening another document in the same visit starts from the session flag.
        assertFalse(shouldShowPdfCoach(PdfCoachState(shownThisSession = true)))
        // A brand-new visit gets the card again.
        assertTrue(shouldShowPdfCoach(PdfCoachState(shownThisSession = false)))
        assertEquals(PdfCoachState(true, true), dismissed)
    }

    @Test
    fun highlightQueryIsTrimmedLikeTheViewModelSearch() {
        assertEquals("주석", viewerHighlightQuery("  주석  "))
        // Only the ends are trimmed — exactly like PdfViewModel.search, which
        // does query.trim() and leaves internal spacing to the matcher.
        assertEquals("hello  world", viewerHighlightQuery("\n\t hello  world \n"))
        assertEquals("", viewerHighlightQuery("   "))
        // The overlay and the search result list must agree on what is a query.
        assertEquals(viewerHighlightQuery(" annotated "), "annotated")
    }

    @Test
    fun canvasStateKeyChangesOnDocumentSwitchEvenAtTheSamePage() {
        val first = CanvasPageKey(docId = "doc-a", pageIndex = 3)
        assertEquals(first, CanvasPageKey("doc-a", 3))
        assertFalse("same page in another document resets pan", first == CanvasPageKey("doc-b", 3))
        assertFalse("different page resets pan", first == CanvasPageKey("doc-a", 4))
        assertEquals(setOf(first, CanvasPageKey("doc-b", 3), CanvasPageKey("doc-a", 4)).size, 3)
    }

    @Test
    fun everyViewerLayerInterceptsBack() {
        val idle = ViewerSheetFlags()
        val layers = listOf(
            idle.copy(showPages = true),
            idle.copy(showNotes = true),
            idle.copy(showBookmarks = true),
            idle.copy(showSearch = true),
            idle.copy(showAnnotations = true),
            idle.copy(showOrganize = true),
            idle.copy(showExcerpts = true),
            idle.copy(showContinuous = true),
            idle.copy(showSpread = true),
            idle.copy(showPopup = true),
            idle.copy(showAudioNotes = true),
            idle.copy(showAudioReplay = true),
            idle.copy(showStudyNote = true),
            idle.copy(showReflow = true),
            idle.copy(showReadAloud = true),
            idle.copy(showProperties = true),
            idle.copy(showStyle = true),
            idle.copy(showDisplaySettings = true),
            idle.copy(showHub = true),
            idle.copy(showReadModes = true),
            idle.copy(showAudioMenu = true),
            idle.copy(showFileTools = true),
            idle.copy(showToolMore = true),
            idle.copy(showToolStyle = true),
            idle.copy(showSelectionMore = true),
            idle.copy(showTextEditDialog = true),
            idle.copy(showMemoCardsSheet = true),
            idle.copy(showViewerPenBox = true),
            idle.copy(showRecentDocs = true),
            idle.copy(showTabOverflow = true),
            idle.copy(showLocations = true),
            idle.copy(textTargetOpen = true),
            idle.copy(stampTargetOpen = true),
            idle.copy(signatureTargetOpen = true),
        )

        layers.forEachIndexed { index, flags ->
            assertTrue("viewer layer #$index must intercept Back", flags.anyOpen)
        }
    }
}
