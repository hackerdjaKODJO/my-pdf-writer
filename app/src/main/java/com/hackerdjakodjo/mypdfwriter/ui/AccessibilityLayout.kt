package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Pure layout policy for screen chrome under narrow widths and large fonts.
 *
 * Large text trades secondary chrome (subtitles and dock labels) for extra
 * header lines and taller rows; narrow screens also drop inline actions and
 * collapse quick actions to two columns. This is deliberately framework-free
 * so the same decisions can be tested on the JVM and reused by any screen.
 */
data class ChromeScale(
    val showSubtitle: Boolean,
    val titleMaxLines: Int,
    val inlineActionCount: Int,
    val showDockLabels: Boolean,
    val quickActionColumns: Int,
    val minRowHeightDp: Int,
)

/**
 * Resolves the chrome scale for [widthDp] and [fontScale].
 *
 * Width is clamped to at least 320 dp (the smallest supported phone) and font
 * scale to 1f..3f. A width below 360 dp is "narrow"; a scale at or above 1.5f
 * is "large" and at or above 2f is "huge".
 */
fun chromeScale(widthDp: Int, fontScale: Float): ChromeScale {
    val width = maxOf(320, widthDp)
    // coerceIn leaves NaN untouched; normalise it to the compact (1f) default.
    val scale = if (fontScale.isNaN()) 1f else fontScale.coerceIn(1f, 3f)
    val narrow = width < 360
    val large = scale >= 1.5f
    val huge = scale >= 2f
    return ChromeScale(
        showSubtitle = !large,
        titleMaxLines = if (large) 2 else 1,
        inlineActionCount = when {
            narrow && huge -> 1
            large || narrow -> 2
            else -> 3
        },
        showDockLabels = !large,
        quickActionColumns = if (narrow || large) 2 else 4,
        minRowHeightDp = if (large) 56 else 48,
    )
}
