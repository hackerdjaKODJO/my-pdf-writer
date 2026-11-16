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
            STUDY_PANE_DEFAULT_FRACTION,
            studyPaneFractionAfterDrag(0.70f, -100f, 0f),
            0.0001f,
        )
        assertEquals(
            STUDY_PANE_DEFAULT_FRACTION,
            studyPaneFractionAfterDrag(0.70f, Float.NaN, 1000f),
            0.0001f,
        )
        assertEquals(
            STUDY_PANE_DEFAULT_FRACTION,
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
}
