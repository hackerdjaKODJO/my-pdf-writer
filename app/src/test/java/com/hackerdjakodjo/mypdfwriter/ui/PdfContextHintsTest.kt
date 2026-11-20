package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfContextHintsTest {

    @Test
    fun viewWithWordsTeachesTapDragAndLongPress() {
        assertEquals(
            PdfContextHint.TAP_DRAG_LONG_PRESS,
            pdfContextHint(
                PdfContextHintState(tool = Tool.VIEW, hasWords = true),
            ),
        )
    }

    @Test
    fun viewWithoutWordsOrWithSelectionStaysQuiet() {
        assertEquals(
            PdfContextHint.NONE,
            pdfContextHint(
                PdfContextHintState(tool = Tool.VIEW, hasWords = false),
            ),
        )
        assertEquals(
            PdfContextHint.NONE,
            pdfContextHint(
                PdfContextHintState(tool = Tool.VIEW, hasWords = true, hasSelection = true),
            ),
        )
    }

    @Test
    fun selectionModeReportsLoadingBeforeEmptyOrReady() {
        assertEquals(
            PdfContextHint.LOADING,
            pdfContextHint(
                PdfContextHintState(
                    tool = Tool.VIEW,
                    textSelectMode = true,
                    textLoading = true,
                    hasWords = false,
                ),
            ),
        )
        assertEquals(
            PdfContextHint.LOADING,
            pdfContextHint(
                PdfContextHintState(
                    tool = Tool.VIEW,
                    textSelectMode = true,
                    textLoading = true,
                    hasWords = true,
                ),
            ),
        )
    }

    @Test
    fun scannedOrEmptyPageReportsNoText() {
        assertEquals(
            PdfContextHint.NO_TEXT,
            pdfContextHint(
                PdfContextHintState(
                    tool = Tool.VIEW,
                    textSelectMode = true,
                    hasWords = false,
                ),
            ),
        )
    }

    @Test
    fun selectionModeWithWordsUsesTheExistingActionBarHint() {
        assertEquals(
            PdfContextHint.SELECTED_HINT,
            pdfContextHint(
                PdfContextHintState(
                    tool = Tool.VIEW,
                    textSelectMode = true,
                    hasWords = true,
                ),
            ),
        )
    }

    @Test
    fun startingASelectionNeverLeavesACompetingHint() {
        for (hintState in listOf(
            PdfContextHintState(tool = Tool.VIEW, hasWords = true, hasSelection = true),
            PdfContextHintState(tool = Tool.VIEW, textSelectMode = true, textLoading = true, hasSelection = true),
            PdfContextHintState(tool = Tool.VIEW, textSelectMode = true, hasWords = false, hasSelection = true),
            PdfContextHintState(tool = Tool.VIEW, textSelectMode = true, hasWords = true, hasSelection = true),
        )) {
            assertEquals(PdfContextHint.NONE, pdfContextHint(hintState))
        }
    }

    @Test
    fun dismissedHintStaysHiddenUntilStateChanges() {
        assertEquals(
            PdfContextHint.NONE,
            pdfContextHint(
                PdfContextHintState(tool = Tool.VIEW, hasWords = true, dismissed = true),
            ),
        )
        assertEquals(
            PdfContextHint.NONE,
            pdfContextHint(
                PdfContextHintState(
                    tool = Tool.VIEW,
                    textSelectMode = true,
                    hasWords = false,
                    dismissed = true,
                ),
            ),
        )
    }

    @Test
    fun otherToolsKeepTheirExistingHint() {
        for (tool in listOf(Tool.PEN, Tool.HIGHLIGHT, Tool.TEXT, Tool.RECT, Tool.STAMP, Tool.ERASER)) {
            assertEquals(PdfContextHint.TOOL_HINT, pdfContextHint(PdfContextHintState(tool = tool)))
        }
    }

    @Test
    fun hintTextsStayShortAndCarryTheOcrShortcut() {
        assertTrue(pdfContextHintText(PdfContextHint.TAP_DRAG_LONG_PRESS).contains("탭"))
        assertTrue(pdfContextHintText(PdfContextHint.TAP_DRAG_LONG_PRESS).contains("드래그"))
        assertTrue(pdfContextHintText(PdfContextHint.TAP_DRAG_LONG_PRESS).contains("길게"))
        assertTrue(pdfContextHintText(PdfContextHint.LOADING).contains("읽는 중"))
        assertTrue(pdfContextHintText(PdfContextHint.NO_TEXT).contains("OCR"))
        assertTrue(pdfContextHintText(PdfContextHint.SELECTED_HINT).contains("탭"))
        assertEquals("도구 안내", pdfContextHintText(PdfContextHint.TOOL_HINT, "도구 안내"))
        assertEquals("", pdfContextHintText(PdfContextHint.NONE))
        assertEquals("OCR 도구", PDF_HINT_OCR_ACTION)
        assertTrue(SEARCH_EMPTY_OCR_TIP.contains("OCR"))
        assertTrue(SEARCH_EMPTY_OCR_TIP.contains("검색어를 줄이거나"))
    }

    @Test
    fun accessibilityLabelsExplainTheSearchFieldAndDisabledMarkup() {
        assertEquals("검색어", PDF_SEARCH_FIELD_LABEL)
        assertEquals(
            "형광펜 표시 (선택한 텍스트 없음)",
            disabledReasonLabel("형광펜 표시", PDF_SELECTION_REQUIRED_REASON),
        )
        assertEquals("굵기 4", sliderLabel("굵기", "4"))
    }

    @Test
    fun fullScreenShowsOnlyHintsNotAlreadyInTheActionBar() {
        fun visible(hint: PdfContextHint, selectionBar: Boolean, searchNav: Boolean = false) =
            pdfContextHintChipVisible(
                hint = hint,
                selectionBarVisible = selectionBar,
                searchNavigationVisible = searchNav,
                splitMode = false,
                pdfStripHeightDp = Float.POSITIVE_INFINITY,
            )

        assertTrue(visible(PdfContextHint.TAP_DRAG_LONG_PRESS, selectionBar = false))
        assertTrue(visible(PdfContextHint.TOOL_HINT, selectionBar = false))
        // NO_TEXT keeps its OCR shortcut even while the selection bar is up.
        assertTrue(visible(PdfContextHint.NO_TEXT, selectionBar = true))
        assertFalse(visible(PdfContextHint.SELECTED_HINT, selectionBar = true))
        assertFalse(visible(PdfContextHint.LOADING, selectionBar = true))
        assertFalse(visible(PdfContextHint.NONE, selectionBar = false))
        assertFalse(visible(PdfContextHint.TAP_DRAG_LONG_PRESS, selectionBar = false, searchNav = true))
    }

    @Test
    fun splitModeSuppressesHintsOnATinyStrip() {
        fun visible(stripDp: Float) = pdfContextHintChipVisible(
            hint = PdfContextHint.TAP_DRAG_LONG_PRESS,
            selectionBarVisible = false,
            searchNavigationVisible = false,
            splitMode = true,
            pdfStripHeightDp = stripDp,
        )

        // Default 85% pane on a ~700dp screen leaves ~105dp: no chip.
        assertFalse(visible(105f))
        assertFalse(visible(PDF_HINT_MIN_SPLIT_STRIP_DP - 1f))
        assertFalse(visible(Float.NaN))
        assertTrue(visible(PDF_HINT_MIN_SPLIT_STRIP_DP))
        assertTrue(visible(220f))
    }
}
