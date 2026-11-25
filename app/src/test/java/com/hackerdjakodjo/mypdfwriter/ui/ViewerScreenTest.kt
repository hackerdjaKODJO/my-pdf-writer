package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure viewer policies for contextual controls, truthful save/export feedback and
 * progressive menus. System Back must dismiss the topmost viewer layer; closing
 * the document while a sheet is open is the regression covered here.
 */
class ViewerScreenTest {

    @Test
    fun saveStatusUsesOnlyDurableViewModelStates() {
        assertEquals(null, pdfSaveStatusLabel(PdfSaveStatus.IDLE))
        assertEquals("자동 저장 중…", pdfSaveStatusLabel(PdfSaveStatus.SAVING))
        assertEquals("자동 저장됨 · 원본 PDF는 그대로", pdfSaveStatusLabel(PdfSaveStatus.SAVED))
        assertEquals("저장 실패 · 다시 시도", pdfSaveStatusLabel(PdfSaveStatus.FAILED))
        assertFalse(pdfSaveRetryVisible(PdfSaveStatus.IDLE))
        assertFalse(pdfSaveRetryVisible(PdfSaveStatus.SAVING))
        assertFalse(pdfSaveRetryVisible(PdfSaveStatus.SAVED))
        assertTrue(pdfSaveRetryVisible(PdfSaveStatus.FAILED))
        assertEquals("pdf_save_status", PDF_SAVE_STATUS_TAG)
        assertEquals("pdf_retry_save", PDF_RETRY_SAVE_TAG)
    }

    @Test
    fun exportMessagesRouteToExplicitSuccessAndRetryFeedback() {
        assertEquals(
            PdfMessageFeedback.EXPORT_SUCCESS,
            pdfMessageFeedback("PDF를 내보냈습니다", "PDF를 내보냈습니다", "내보내지 못했습니다:"),
        )
        assertEquals(
            PdfMessageFeedback.EXPORT_FAILURE,
            pdfMessageFeedback("내보내지 못했습니다: 디스크가 가득 참", "PDF를 내보냈습니다", "내보내지 못했습니다:"),
        )
        assertEquals(
            PdfMessageFeedback.NONE,
            pdfMessageFeedback("현재 페이지 텍스트를 복사했습니다.", "PDF를 내보냈습니다", "내보내지 못했습니다:"),
        )
        assertEquals(
            PdfMessageFeedback.NONE,
            pdfMessageFeedback(null, "PDF를 내보냈습니다", "내보내지 못했습니다:"),
        )
        assertEquals(
            PdfMessageFeedback.NONE,
            pdfMessageFeedback("다른 메시지", "PDF를 내보냈습니다", ""),
        )
    }

    @Test
    fun contextualActionsFollowTheActiveToolAndHistoryAvailability() {
        assertTrue(viewerContextActions(Tool.VIEW, canUndo = false, canRedo = false).isEmpty())
        assertEquals(
            listOf(ViewerContextAction.STYLE),
            viewerContextActions(Tool.HIGHLIGHT, canUndo = false, canRedo = false),
        )
        assertEquals(
            listOf(ViewerContextAction.UNDO),
            viewerContextActions(Tool.VIEW, canUndo = true, canRedo = false),
        )
        assertEquals(
            listOf(ViewerContextAction.REDO),
            viewerContextActions(Tool.VIEW, canUndo = false, canRedo = true),
        )
    }

    @Test
    fun easyAndExpertMenusBothInterceptBackBeforeDocumentClose() {
        assertTrue(ViewerSheetFlags(showEasyMenu = true).anyOpen)
        assertTrue(ViewerSheetFlags(showHub = true).anyOpen)
        assertFalse(ViewerSheetFlags().anyOpen)
        assertEquals("pdf_easy_menu", PDF_EASY_MENU_TAG)
        assertEquals("pdf_all_tools", PDF_ALL_TOOLS_TAG)
        assertEquals("전체 도구 보기", PDF_EASY_MENU_ALL_TOOLS_LABEL)
        assertEquals(PdfMenuAction.entries.toSet(), pdfEasyMenuExpertActions)
    }

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
            listOf("뒤로", "문서 이름", "검색", UiCopy.PDF_MENU),
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
            idle.copy(showEasyMenu = true),
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
