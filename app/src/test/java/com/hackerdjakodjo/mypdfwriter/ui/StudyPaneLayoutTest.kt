package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyPaneLayoutTest {

    @Test
    fun clampKeepsFractionsInsideTheSupportedRange() {
        assertEquals(STUDY_PANE_MIN_FRACTION, clampStudyPaneFraction(0.10f), 0.0001f)
        assertEquals(STUDY_PANE_MAX_FRACTION, clampStudyPaneFraction(0.99f), 0.0001f)
        assertEquals(0.62f, clampStudyPaneFraction(0.62f), 0.0001f)
        assertEquals(STUDY_PANE_DEFAULT_FRACTION, clampStudyPaneFraction(Float.NaN), 0.0001f)
    }

    @Test
    fun presetsAreHalfSeventyAndEightyFivePercentInOrder() {
        assertEquals(listOf(0.50f, 0.70f, 0.85f), STUDY_PANE_PRESETS)
        assertTrue(STUDY_PANE_PRESETS.all { it in STUDY_PANE_MIN_FRACTION..STUDY_PANE_MAX_FRACTION })
        assertTrue(STUDY_PANE_PRESETS.zipWithNext().all { (a, b) -> a < b })
        assertEquals("50%", studyPanePresetLabel(0.50f))
        assertEquals("70%", studyPanePresetLabel(0.70f))
        assertEquals("85%", studyPanePresetLabel(0.85f))
    }

    @Test
    fun defaultPaneOpensAtEightyFivePercentWithAPdfStripVisible() {
        assertEquals(0.85f, STUDY_PANE_DEFAULT_FRACTION, 0.0001f)
        assertEquals(STUDY_PANE_MAX_FRACTION, STUDY_PANE_DEFAULT_FRACTION, 0.0001f)
        assertEquals("85%", studyPanePresetLabel(STUDY_PANE_DEFAULT_FRACTION))
        assertTrue(isStudyPanePresetSelected(STUDY_PANE_DEFAULT_FRACTION, 0.85f))
        assertEquals(
            STUDY_PANE_MIN_VISIBLE_PDF_FRACTION,
            visiblePdfFractionForPaneRequest(STUDY_PANE_DEFAULT_FRACTION),
            0.0001f,
        )
    }

    @Test
    fun everyPresetLeavesAReadablePdfStripAndNinetyFivePercentIsRejected() {
        assertTrue(
            STUDY_PANE_PRESETS.all { preset ->
                visiblePdfFractionForPaneRequest(preset) >=
                    STUDY_PANE_MIN_VISIBLE_PDF_FRACTION - 0.0001f
            },
        )
        // A 95% pane would leave only 5% of the PDF content area, so it stays
        // outside the policy even though a caller can pass it in.
        val ninetyFive = visiblePdfFractionForPaneRequest(0.95f)
        assertTrue(ninetyFive < STUDY_PANE_MIN_VISIBLE_PDF_FRACTION)
        assertEquals(0.05f, ninetyFive, 0.0001f)
        assertEquals(
            STUDY_PANE_MIN_VISIBLE_PDF_FRACTION,
            visiblePdfFractionForPaneRequest(Float.NaN),
            0.0001f,
        )
    }

    @Test
    fun draggingUpGrowsAndDraggingDownShrinksThePane() {
        val total = 1000f
        val up = studyPaneFractionAfterDrag(0.70f, -120f, total)
        val down = studyPaneFractionAfterDrag(0.70f, 120f, total)

        assertEquals(0.82f, up, 0.0001f)
        assertEquals(0.58f, down, 0.0001f)
    }

    @Test
    fun dragResultIsClampedAtBothEnds() {
        assertEquals(
            STUDY_PANE_MAX_FRACTION,
            studyPaneFractionAfterDrag(0.70f, -500f, 1000f),
            0.0001f,
        )
        assertEquals(
            STUDY_PANE_MIN_FRACTION,
            studyPaneFractionAfterDrag(0.70f, 500f, 1000f),
            0.0001f,
        )
    }

    @Test
    fun invalidDragMeasurementsLeaveTheCurrentHeightAlone() {
        assertEquals(
            0.70f,
            studyPaneFractionAfterDrag(0.70f, -100f, 0f),
            0.0001f,
        )
        assertEquals(
            0.70f,
            studyPaneFractionAfterDrag(0.70f, Float.NaN, 1000f),
            0.0001f,
        )
        assertEquals(
            0.70f,
            studyPaneFractionAfterDrag(0.70f, -100f, Float.POSITIVE_INFINITY),
            0.0001f,
        )
    }

    @Test
    fun dismissRequiresPullingPastTheMinimumHeight() {
        // Resizing from 70% to the 35% floor is not a dismissal.
        assertFalse(shouldDismissStudyPaneAfterDrag(0.70f, 350f, 1000f))
        // One extra 6% of content height below the floor dismisses.
        assertTrue(shouldDismissStudyPaneAfterDrag(0.70f, 420f, 1000f))
        // Upward drags and invalid totals never dismiss.
        assertFalse(shouldDismissStudyPaneAfterDrag(0.70f, -200f, 1000f))
        assertFalse(shouldDismissStudyPaneAfterDrag(0.70f, 500f, 0f))
        // Already at the floor, a small pull is still just a resize.
        assertFalse(shouldDismissStudyPaneAfterDrag(0.35f, 50f, 1000f))
        assertTrue(shouldDismissStudyPaneAfterDrag(0.35f, 80f, 1000f))
    }

    @Test
    fun presetSelectionUsesTheClampedHeight() {
        assertTrue(isStudyPanePresetSelected(0.50f, 0.50f))
        assertTrue(isStudyPanePresetSelected(0.5004f, 0.50f))
        assertFalse(isStudyPanePresetSelected(0.70f, 0.50f))
        assertFalse(isStudyPanePresetSelected(0.20f, 0.50f))
    }

    @Test
    fun dividerRowAndPresetTargetsUseTheDocumentedSizes() {
        assertEquals(28, STUDY_PANE_DIVIDER_ROW_HEIGHT_DP)
        // The visible pill stays in the 28dp row; TalkBack gets a 48dp target
        // through sizeIn, and the popup rows are a full 48dp tall.
        assertEquals(MIN_TARGET_DP, STUDY_PANE_PRESET_ROW_HEIGHT_DP)
        assertTrue(isAccessibleTarget(STUDY_PANE_PRESET_ROW_HEIGHT_DP))
        assertTrue(STUDY_PANE_DIVIDER_ROW_HEIGHT_DP > 18)
    }

    @Test
    fun dividerLabelShowsTheKoreanNotePercent() {
        assertEquals("노트 70%", studyPaneDividerLabel(0.70f))
        assertEquals("노트 85%", studyPaneDividerLabel(STUDY_PANE_DEFAULT_FRACTION))
        // In-between drag values are shown rounded, not snapped to a preset.
        assertEquals("노트 62%", studyPaneDividerLabel(0.62f))
        assertEquals("노트 85%", studyPaneDividerLabel(Float.NaN))
    }

    @Test
    fun dividerAndPresetRowsHaveTalkBackDescriptions() {
        assertEquals(
            "노트 크기 조절 핸들, 현재 70%, 탭하면 크기 선택",
            studyPaneDividerDescription(0.70f),
        )
        assertEquals("노트 크기 50%", studyPanePresetDescription(0.50f))
        assertEquals("노트 크기 70%", studyPanePresetDescription(0.70f))
        assertEquals("노트 크기 85%", studyPanePresetDescription(0.85f))
    }

    @Test
    fun pdfToolsChipDescriptionTracksVisibility() {
        assertEquals("PDF 도구 숨기기", splitPdfToolsChipDescription(true))
        assertEquals("PDF 도구 표시", splitPdfToolsChipDescription(false))
    }

    @Test
    fun doubleTapTogglesBetweenTheFiftyAndEightyFiveEndpoints() {
        assertEquals(0.85f, studyPanePresetAfterDoubleTap(0.50f), 0.0001f)
        assertEquals(0.50f, studyPanePresetAfterDoubleTap(0.85f), 0.0001f)
        // An in-between value jumps to the farther documented endpoint.
        assertEquals(0.50f, studyPanePresetAfterDoubleTap(0.70f), 0.0001f)
        assertEquals(0.85f, studyPanePresetAfterDoubleTap(0.35f), 0.0001f)
        assertTrue(studyPanePresetAfterDoubleTap(0.62f) in STUDY_PANE_PRESETS)
    }

    @Test
    fun `study pane header row keeps every header control tappable`() {
        assertTrue(
            "header row must keep 48dp controls accessible",
            isAccessibleTarget(STUDY_PANE_HEADER_ROW_HEIGHT_DP),
        )
    }
}
