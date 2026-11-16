package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure size policy for the PDF study pane. Fractions are measured against the
 * PDF content area (between the viewer app bar and the PDF tool/selection bar),
 * so even the 85% preset leaves a readable strip of the PDF page visible.
 */
internal const val STUDY_PANE_MIN_FRACTION = 0.35f
internal const val STUDY_PANE_MAX_FRACTION = 0.85f
internal const val STUDY_PANE_DEFAULT_FRACTION = 0.70f

/** Extra downward pull below the minimum height that dismisses the pane. */
internal const val STUDY_PANE_DISMISS_OVERSHOOT = 0.06f

/** Preset chips shown in the pane header. */
internal val STUDY_PANE_PRESETS = listOf(0.50f, 0.70f, 0.85f)

internal fun clampStudyPaneFraction(fraction: Float): Float {
    if (!fraction.isFinite()) return STUDY_PANE_DEFAULT_FRACTION
    return fraction.coerceIn(STUDY_PANE_MIN_FRACTION, STUDY_PANE_MAX_FRACTION)
}

/**
 * Converts a vertical drag into a pane fraction. Dragging up grows the pane;
 * the result stays inside the supported size range.
 */
internal fun studyPaneFractionAfterDrag(
    startFraction: Float,
    dragY: Float,
    totalHeightPx: Float,
): Float {
    val start = clampStudyPaneFraction(startFraction)
    if (!dragY.isFinite() || !totalHeightPx.isFinite() || totalHeightPx <= 0f) return start
    return clampStudyPaneFraction(start - dragY / totalHeightPx)
}

/**
 * A normal drag resizes the pane. Once the user has pulled it below the minimum
 * height and keeps dragging for [STUDY_PANE_DISMISS_OVERSHOOT] of the content
 * height, releasing dismisses the pane instead of leaving it at the floor.
 */
internal fun shouldDismissStudyPaneAfterDrag(
    startFraction: Float,
    dragY: Float,
    totalHeightPx: Float,
): Boolean {
    val start = clampStudyPaneFraction(startFraction)
    if (!dragY.isFinite() || dragY <= 0f) return false
    if (!totalHeightPx.isFinite() || totalHeightPx <= 0f) return false
    val rawFraction = start - dragY / totalHeightPx
    return rawFraction < STUDY_PANE_MIN_FRACTION - STUDY_PANE_DISMISS_OVERSHOOT
}

internal fun studyPanePresetLabel(fraction: Float): String =
    "${(clampStudyPaneFraction(fraction) * 100).roundToInt()}%"

internal fun isStudyPanePresetSelected(currentFraction: Float, presetFraction: Float): Boolean =
    abs(clampStudyPaneFraction(currentFraction) - presetFraction) < 0.001f
