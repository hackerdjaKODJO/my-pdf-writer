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
internal const val STUDY_PANE_HEADER_ROW_HEIGHT_DP = MIN_TARGET_DP
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

/** Height of the resize divider row between the PDF strip and the note pane. */
internal const val STUDY_PANE_DIVIDER_ROW_HEIGHT_DP = 28

/** Full 48dp TalkBack target for the size-popup rows; the inline pill fits the 28dp row. */
internal const val STUDY_PANE_PRESET_ROW_HEIGHT_DP = MIN_TARGET_DP

/** Midpoint between the 50% and 85% presets, used by divider double-tap. */
private const val STUDY_PANE_DOUBLE_TAP_MIDPOINT_FRACTION = 0.675f

/** Visible label on the centered divider pill, e.g. `노트 70%`. */
internal fun studyPaneDividerLabel(fraction: Float): String =
    "노트 ${studyPanePresetLabel(fraction)}"

/** TalkBack description for the draggable/tappable divider pill. */
internal fun studyPaneDividerDescription(fraction: Float): String =
    "노트 크기 조절 핸들, 현재 ${studyPanePresetLabel(fraction)}, 탭하면 크기 선택"

/** TalkBack description for one preset row of the compact size popup. */
internal fun studyPanePresetDescription(fraction: Float): String =
    "노트 크기 ${studyPanePresetLabel(fraction)}"

/** TalkBack description for the divider's PDF-tool visibility chip. */
internal fun splitPdfToolsChipDescription(visible: Boolean): String =
    if (visible) "PDF 도구 숨기기" else "PDF 도구 표시"

/**
 * Double-tapping the pill jumps between the smallest and largest preset. From an
 * in-between value the farther preset wins, so the gesture always lands on one
 * of the two documented endpoints.
 */
internal fun studyPanePresetAfterDoubleTap(currentFraction: Float): Float =
    if (clampStudyPaneFraction(currentFraction) <= STUDY_PANE_DOUBLE_TAP_MIDPOINT_FRACTION) {
        STUDY_PANE_PRESETS.last()
    } else {
        STUDY_PANE_PRESETS.first()
    }
