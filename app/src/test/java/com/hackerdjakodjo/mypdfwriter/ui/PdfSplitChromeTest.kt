package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSplitChromeTest {

    @Test
    fun fullScreenKeepsTheAppBarDockAndHintPill() {
        val policy = pdfChromePolicy(
            studyPaneOpen = false,
            textSelectMode = false,
            hasSelection = false,
        )

        assertTrue(policy.showTopAppBar)
        assertTrue(policy.showBottomBar)
        assertTrue(policy.showHintPill)
        assertFalse(policy.showPageDropTarget)
        assertFalse(policy.showSplitToolbar)
        assertFalse(policy.showSplitSelectionBar)
    }

    @Test
    fun fullScreenSelectionStillUsesTheExistingDropTarget() {
        val policy = pdfChromePolicy(
            studyPaneOpen = false,
            textSelectMode = true,
            hasSelection = true,
        )

        assertTrue(policy.showTopAppBar)
        assertTrue(policy.showBottomBar)
        assertTrue(policy.showHintPill)
        assertTrue(policy.showPageDropTarget)
        assertFalse(policy.showSplitSelectionBar)
    }

    @Test
    fun splitModeRemovesBarsHintAndDuplicateDropTarget() {
        val policy = pdfChromePolicy(
            studyPaneOpen = true,
            textSelectMode = false,
            hasSelection = false,
        )

        assertFalse(policy.showTopAppBar)
        assertFalse(policy.showBottomBar)
        assertFalse(policy.showHintPill)
        assertFalse(policy.showPageDropTarget)
        assertTrue(policy.showSplitToolbar)
        assertFalse(policy.showSplitSelectionBar)
    }

    @Test
    fun splitSelectionReplacesTheToolbarWithoutRestoringBars() {
        val policy = pdfChromePolicy(
            studyPaneOpen = true,
            textSelectMode = true,
            hasSelection = true,
        )

        assertFalse(policy.showTopAppBar)
        assertFalse(policy.showBottomBar)
        assertFalse(policy.showHintPill)
        assertFalse(policy.showPageDropTarget)
        assertFalse(policy.showSplitToolbar)
        assertTrue(policy.showSplitSelectionBar)
    }

    @Test
    fun selectionToggleCoversIdleLoadingActiveAndEmpty() {
        assertEquals(
            SplitSelectionToggleState.IDLE,
            splitSelectionToggleState(textSelectMode = false, loading = true, hasWords = true),
        )
        assertEquals(
            SplitSelectionToggleState.LOADING,
            splitSelectionToggleState(textSelectMode = true, loading = true, hasWords = false),
        )
        assertEquals(
            SplitSelectionToggleState.ACTIVE,
            splitSelectionToggleState(textSelectMode = true, loading = false, hasWords = true),
        )
        assertEquals(
            SplitSelectionToggleState.EMPTY,
            splitSelectionToggleState(textSelectMode = true, loading = false, hasWords = false),
        )
    }

    @Test
    fun selectionPrimaryActionsAreMarkupFirstAndCappedAtSix() {
        assertEquals(
            listOf("형광펜", "밑줄", "취소선", "복사", "발췌", "더보기"),
            TextSelectionPrimaryAction.entries.map { it.label },
        )
        assertEquals(6, TextSelectionPrimaryAction.entries.size)
    }

    @Test
    fun floatingTargetsStayInTheCompactRange() {
        assertTrue(SPLIT_CHROME_TARGET_DP in 36..40)
    }

    @Test
    fun splitPaneKeepsTheDocumentedPdfStrip() {
        val paneFraction = clampStudyPaneFraction(STUDY_PANE_DEFAULT_FRACTION)
        assertEquals(0.15f, 1f - paneFraction, 0.0001f)
        assertEquals(0.85f, paneFraction, 0.0001f)
    }
}
