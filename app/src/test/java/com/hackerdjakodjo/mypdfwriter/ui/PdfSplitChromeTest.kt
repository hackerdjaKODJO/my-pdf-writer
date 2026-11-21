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
            listOf("형광펜", "밑줄", "취소선", "복사", "노트에 담기", "더보기"),
            TextSelectionPrimaryAction.entries.map { it.label },
        )
        assertEquals(6, TextSelectionPrimaryAction.entries.size)
        // Identifiers stay unchanged across the label rename.
        assertEquals(
            listOf("HIGHLIGHT", "UNDERLINE", "STRIKEOUT", "COPY", "EXCERPT", "MORE"),
            TextSelectionPrimaryAction.entries.map { it.name },
        )
    }

    @Test
    fun selectionPrimaryDescriptionsFollowTheNewLabels() {
        assertEquals("형광펜 표시", TextSelectionPrimaryAction.HIGHLIGHT.contentDescription)
        assertEquals("밑줄 표시", TextSelectionPrimaryAction.UNDERLINE.contentDescription)
        assertEquals("취소선 표시", TextSelectionPrimaryAction.STRIKEOUT.contentDescription)
        assertEquals("선택한 텍스트 복사", TextSelectionPrimaryAction.COPY.contentDescription)
        assertEquals("노트에 담기", TextSelectionPrimaryAction.EXCERPT.contentDescription)
        assertEquals("선택 도구 더보기", TextSelectionPrimaryAction.MORE.contentDescription)
    }

    @Test
    fun floatingTargetsMeetTheMinimumTouchTarget() {
        assertEquals(MIN_TARGET_DP, SPLIT_CHROME_TARGET_DP)
        assertTrue(isAccessibleTarget(SPLIT_CHROME_TARGET_DP))
        assertEquals(SPLIT_CHROME_TARGET_DP, accessibleTarget(SPLIT_CHROME_TARGET_DP))
        // The drawn circle stays compact; only the hit target grows to 48 dp.
        assertTrue(SPLIT_CHROME_VISUAL_DP < SPLIT_CHROME_TARGET_DP)
    }

    @Test
    fun splitPaneKeepsTheDocumentedPdfStrip() {
        val paneFraction = clampStudyPaneFraction(STUDY_PANE_DEFAULT_FRACTION)
        assertEquals(0.15f, 1f - paneFraction, 0.0001f)
        assertEquals(0.85f, paneFraction, 0.0001f)
    }

    @Test
    fun normalPanesShowTheFullPdfStrip() {
        assertEquals(SplitPdfChromeDensity.FULL, splitPdfChromeDensity(0.35f))
        assertEquals(SplitPdfChromeDensity.FULL, splitPdfChromeDensity(0.50f))
        assertEquals(SplitPdfChromeDensity.FULL, splitPdfChromeDensity(0.70f))
        // The threshold is exclusive: exactly 80% still fits the full strip.
        assertEquals(SplitPdfChromeDensity.FULL, splitPdfChromeDensity(0.80f))
        assertEquals(0.80f, SPLIT_PDF_ESSENTIAL_PANE_FRACTION, 0.0001f)
    }

    @Test
    fun shortStripsKeepOnlyEssentialPdfControls() {
        assertEquals(SplitPdfChromeDensity.ESSENTIAL, splitPdfChromeDensity(0.81f))
        assertEquals(SplitPdfChromeDensity.ESSENTIAL, splitPdfChromeDensity(0.85f))
        // Invalid values resolve through the default 85% pane.
        assertEquals(SplitPdfChromeDensity.ESSENTIAL, splitPdfChromeDensity(Float.NaN))
        assertEquals(SplitPdfChromeDensity.ESSENTIAL, splitPdfChromeDensity(2f))
    }
}
