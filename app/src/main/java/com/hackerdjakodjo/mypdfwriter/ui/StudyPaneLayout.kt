package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure size policy for the PDF study pane. Fractions are measured against the
 * PDF content area: between the viewer app bar and the PDF tool/selection bar in
 * full-screen mode, or against the full safe area when the split chrome removes
 * those bars.
 *
 * The pane opens at 85%, which is also the maximum: it leaves a 15% PDF strip
 * visible so the source page stays readable while the note takes most of a
 * phone screen. A 95% pane would leave only 5% of the PDF, which is too thin to
 * keep the page in view, so 95% is intentionally not a preset.
 */
internal const val STUDY_PANE_MIN_FRACTION = 0.35f
internal const val STUDY_PANE_MAX_FRACTION = 0.85f
internal const val STUDY_PANE_DEFAULT_FRACTION = 0.85f

/** Minimum share of the PDF content area that must remain visible above the pane. */
internal const val STUDY_PANE_MIN_VISIBLE_PDF_FRACTION = 0.15f

/** Extra downward pull below the minimum height that dismisses the pane. */
internal const val STUDY_PANE_DISMISS_OVERSHOOT = 0.06f

/** Presets shown in the pane header's tiny size popup. */
internal val STUDY_PANE_PRESETS = listOf(0.50f, 0.70f, 0.85f)

/**
 * Floating header metrics (dp). The pane header is an overlay instead of a
 * full-width stack: one 40 dp row with a grip, title/source, size popup, link and
 * close actions. The whole header is the drop target, so a separate capture
 * toggle and strip are unnecessary.
 */
internal const val STUDY_PANE_HEADER_ROW_HEIGHT_DP = 40
internal const val STUDY_PANE_HEADER_VERTICAL_PADDING_DP = 2
internal const val STUDY_PANE_HEADER_SIDE_MARGIN_DP = 8
internal const val STUDY_PANE_HEADER_TOP_MARGIN_DP = 4

/** Height of the old handle + title + permanent capture strip header. */
internal const val STUDY_PANE_LEGACY_STACKED_HEADER_HEIGHT_DP = 90

internal fun studyPaneFloatingHeaderHeightDp(): Int =
    STUDY_PANE_HEADER_ROW_HEIGHT_DP + STUDY_PANE_HEADER_VERTICAL_PADDING_DP * 2

/**
 * Top inset the compact editor's contextual hint needs so it sits below the
 * floating header instead of behind it.
 */
internal fun studyPaneFloatingHeaderInsetDp(): Int =
    STUDY_PANE_HEADER_TOP_MARGIN_DP + studyPaneFloatingHeaderHeightDp()

internal fun clampStudyPaneFraction(fraction: Float): Float {
    if (!fraction.isFinite()) return STUDY_PANE_DEFAULT_FRACTION
    return fraction.coerceIn(STUDY_PANE_MIN_FRACTION, STUDY_PANE_MAX_FRACTION)
}

/**
 * Visible PDF share for an unclamped pane request. Kept separate from
 * [clampStudyPaneFraction] so the size policy can be checked for rejected
 * values such as 95% without silently clamping them first.
 */
internal fun visiblePdfFractionForPaneRequest(fraction: Float): Float =
    if (!fraction.isFinite()) {
        1f - STUDY_PANE_DEFAULT_FRACTION
    } else {
        (1f - fraction).coerceIn(0f, 1f)
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
