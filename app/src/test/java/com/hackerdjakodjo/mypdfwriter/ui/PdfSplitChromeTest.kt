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
    fun captureHasOneLabelAndHandoffsStaySecondary() {
        assertEquals(PDF_HUB_CAPTURE_LABEL, "노트에 담기")
        assertEquals(
            PDF_HUB_CAPTURE_LABEL,
            TextSelectionPrimaryAction.EXCERPT.label,
        )
        assertEquals(
            listOf(PDF_HUB_CAPTURE_LABEL, "텍스트 편집", "공유"),
            TextSelectionSecondaryAction.entries.map { it.label },
        )
        assertEquals(1, TextSelectionSecondaryAction.entries.count { it.label == PDF_HUB_CAPTURE_LABEL })
        assertEquals(listOf("번역", "사전"), PDF_TEXT_HANDOFF_LABELS)
        assertFalse("발췌로 저장 duplicate is gone", "발췌로 저장" in TextSelectionSecondaryAction.entries.map { it.label })
    }

    @Test
    fun compactSplitStripKeepsEveryStudyAction() {
        assertEquals(
            listOf("검색", "텍스트 선택", "주석"),
            splitPdfToolbarControls(compact = true),
        )
        assertEquals(5, splitPdfToolbarControls(compact = false).size)
        listOf("텍스트 선택", "주석", "현재 페이지 북마크", "학습 메뉴").forEach {
            assertTrue(it in splitPdfToolbarControls(compact = false))
        }
        // The two actions that justify the study pane must survive the default
        // 85% pane; only secondary chrome collapses.
        listOf("검색", "텍스트 선택", "주석").forEach {
            assertTrue("$it survives the default 85% pane", it in splitPdfToolbarControls(compact = true))
        }
        listOf("현재 페이지 북마크", "학습 메뉴").forEach {
            assertFalse("compact strip drops secondary $it", it in splitPdfToolbarControls(compact = true))
        }
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

    @Test
    fun autoHideDefaultsOnForPhonesAndOffForLargeScreens() {
        assertEquals(ChromeAutoHideMode.ON, defaultChromeAutoHideMode(360))
        assertEquals(ChromeAutoHideMode.ON, defaultChromeAutoHideMode(599))
        assertEquals(ChromeAutoHideMode.OFF, defaultChromeAutoHideMode(600))
        assertEquals(ChromeAutoHideMode.OFF, defaultChromeAutoHideMode(840))
        assertEquals(600, CHROME_AUTO_HIDE_PHONE_WIDTH_DP)
        assertEquals(ChromeAutoHideMode.OFF, toggleChromeAutoHideMode(ChromeAutoHideMode.ON))
        assertEquals(
            ChromeAutoHideMode.ON,
            toggleChromeAutoHideMode(toggleChromeAutoHideMode(ChromeAutoHideMode.ON)),
        )
    }

    @Test
    fun canvasTapToggleIsRestrictedToTheSafeFullScreenViewState() {
        val safe = chromeTapToggleAllowed(
            mode = ChromeAutoHideMode.ON,
            studyPaneOpen = false,
            sheetOpen = false,
            textSelectMode = false,
            hasSelection = false,
            toolIsView = true,
            busy = false,
        )
        assertTrue(safe)
        assertFalse("hide mode must not hijack taps", chromeTapToggleAllowed(
            ChromeAutoHideMode.OFF, false, false, false, false, true, false,
        ))
        assertFalse("split chrome floats already", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, true, false, false, false, true, false,
        ))
        assertFalse("sheets own Back/taps", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, false, true, false, false, true, false,
        ))
        assertFalse("selection gesture wins", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, false, false, true, false, true, false,
        ))
        assertFalse("selected words win", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, false, false, false, true, true, false,
        ))
        assertFalse("drawing tool wins", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, false, false, false, false, false, false,
        ))
        assertFalse("busy render wins", chromeTapToggleAllowed(
            ChromeAutoHideMode.ON, false, false, false, false, true, true,
        ))
    }

    @Test
    fun hidingChromeIsANoopWhenTheToggleIsNotAllowed() {
        assertTrue(nextChromeHidden(hidden = false, toggleAllowed = true))
        assertFalse(nextChromeHidden(hidden = true, toggleAllowed = true))
        assertFalse(nextChromeHidden(hidden = false, toggleAllowed = false))
        assertTrue(nextChromeHidden(hidden = true, toggleAllowed = false))
    }

    @Test
    fun onlyTheCentreBandOfTheCanvasTogglesChrome() {
        assertTrue(isChromeToggleTap(200f, 300f, 400, 600))
        assertTrue(isChromeToggleTap(100f, 150f, 400, 600))
        assertTrue(isChromeToggleTap(300f, 450f, 400, 600))
        assertFalse(isChromeToggleTap(10f, 300f, 400, 600))
        assertFalse(isChromeToggleTap(390f, 300f, 400, 600))
        assertFalse(isChromeToggleTap(200f, 10f, 400, 600))
        assertFalse(isChromeToggleTap(200f, 590f, 400, 600))
        assertFalse(isChromeToggleTap(0f, 0f, 0, 0))
        assertFalse(isChromeToggleTap(0f, 0f, -2, 10))
    }

    @Test
    fun immersiveChromeDropsBarsWithoutChangingSplitOrDefaultPolicy() {
        val immersive = pdfChromePolicy(
            studyPaneOpen = false,
            textSelectMode = true,
            hasSelection = true,
            chromeHidden = true,
            autoHideMode = ChromeAutoHideMode.ON,
        )
        assertFalse(immersive.showTopAppBar)
        assertFalse(immersive.showBottomBar)
        assertFalse(immersive.showHintPill)
        assertFalse(immersive.showPageDropTarget)
        assertFalse(immersive.showSplitToolbar)
        assertFalse(immersive.showSplitSelectionBar)
        assertTrue(isChromeImmersive(false, ChromeAutoHideMode.ON, true))

        // Hidden state never applies to split mode or to the OFF mode.
        assertFalse(isChromeImmersive(true, ChromeAutoHideMode.ON, true))
        assertFalse(isChromeImmersive(false, ChromeAutoHideMode.OFF, true))
        val split = pdfChromePolicy(
            studyPaneOpen = true,
            textSelectMode = false,
            hasSelection = false,
            chromeHidden = true,
            autoHideMode = ChromeAutoHideMode.ON,
        )
        assertTrue(split.showSplitToolbar)
        assertFalse(split.showTopAppBar)

        // Default arguments keep the pre-existing full-screen behaviour.
        val full = pdfChromePolicy(false, false, false)
        assertTrue(full.showTopAppBar)
        assertTrue(full.showBottomBar)
        assertTrue(full.showHintPill)
    }
}
