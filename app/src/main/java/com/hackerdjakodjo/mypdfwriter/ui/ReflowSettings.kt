package com.hackerdjakodjo.mypdfwriter.ui

import java.util.Locale
import kotlin.math.roundToInt

/** Reader colour themes. Labels are Korean because the reader is a Korean-first surface. */
enum class ReflowTheme(val label: String) {
    PAPER("종이"),
    SEPIA("세피아"),
    DARK("다크"),
}

/**
 * Typography choices for [ReflowReaderScreen].
 *
 * This is deliberately a plain value type: the screen owns it in Compose state,
 * while tests exercise the clamping and formatting rules on the JVM.
 */
data class ReflowSettings(
    val fontSizeSp: Float = DEFAULT_FONT_SIZE_SP,
    val lineHeightScale: Float = DEFAULT_LINE_HEIGHT_SCALE,
    val theme: ReflowTheme = ReflowTheme.PAPER,
) {
    fun normalized(): ReflowSettings = copy(
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP),
        lineHeightScale = lineHeightScale.coerceIn(MIN_LINE_HEIGHT_SCALE, MAX_LINE_HEIGHT_SCALE),
    )

    /** Absolute line height in sp for Compose's [androidx.compose.ui.text.TextStyle]. */
    val lineHeightSp: Float
        get() {
            val size = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            val scale = lineHeightScale.coerceIn(MIN_LINE_HEIGHT_SCALE, MAX_LINE_HEIGHT_SCALE)
            return size * scale
        }

    companion object {
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 30f
        const val DEFAULT_FONT_SIZE_SP = 18f

        const val MIN_LINE_HEIGHT_SCALE = 1.2f
        const val MAX_LINE_HEIGHT_SCALE = 2.2f
        const val DEFAULT_LINE_HEIGHT_SCALE = 1.7f

        /** Number of intermediate stops between the slider endpoints. */
        const val FONT_SIZE_STEPS = 15
        const val LINE_HEIGHT_STEPS = 9
    }
}

/** One-based page counter used by the top bar, jump dialog and read-aloud sheet. */
fun formatPageCounter(pageIndex: Int, pageCount: Int): String {
    if (pageCount <= 0) return "0 / 0쪽"
    val safeIndex = pageIndex.coerceIn(0, pageCount - 1)
    return "${safeIndex + 1} / ${pageCount}쪽"
}

/** Clamp a page move so previous/next buttons never escape the document. */
fun stepPageIndex(current: Int, delta: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    return (current + delta).coerceIn(0, pageCount - 1)
}

fun formatFontSize(sizeSp: Float): String {
    val clamped = sizeSp.coerceIn(ReflowSettings.MIN_FONT_SIZE_SP, ReflowSettings.MAX_FONT_SIZE_SP)
    return "${clamped.roundToInt()}sp"
}

fun formatLineHeight(scale: Float): String {
    val clamped = scale.coerceIn(
        ReflowSettings.MIN_LINE_HEIGHT_SCALE,
        ReflowSettings.MAX_LINE_HEIGHT_SCALE,
    )
    return String.format(Locale.ROOT, "%.1f×", clamped)
}

fun formatSpeechRate(rate: Float): String {
    val clamped = rate.coerceIn(0.5f, 2.0f)
    return String.format(Locale.ROOT, "%.1f×", clamped)
}
