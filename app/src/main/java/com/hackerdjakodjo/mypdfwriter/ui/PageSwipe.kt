package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.abs

/**
 * Pure policy for horizontal page-swipe gestures in the PDF viewers.
 *
 * A swipe only turns pages at fit zoom; a zoomed page uses the same horizontal
 * drag for panning instead. Keeping the decision math here makes it unit
 * testable and guarantees both viewers use the same thresholds.
 */
internal object PageSwipe {
    /** Never turn a page on a drag shorter than 64 dp. */
    const val MIN_DRAG_DP = 64f

    /** Or shorter than 18% of the viewport width, whichever is larger. */
    const val WIDTH_FRACTION = 0.18f

    /** Horizontal travel must dominate vertical travel by this factor. */
    const val AXIS_DOMINANCE = 1.5f

    fun thresholdPx(viewportWidthPx: Float, density: Float): Float =
        maxOf(MIN_DRAG_DP * density.coerceAtLeast(0.1f), viewportWidthPx * WIDTH_FRACTION)

    /**
     * 0 = no page turn, +1 = next page (finger moved left),
     * -1 = previous page (finger moved right).
     */
    fun direction(totalX: Float, totalY: Float, touchSlopPx: Float): Int {
        if (!totalX.isFinite() || !totalY.isFinite()) return 0
        if (abs(totalX) <= touchSlopPx) return 0
        if (abs(totalX) <= abs(totalY) * AXIS_DOMINANCE) return 0
        return if (totalX < 0f) 1 else -1
    }

    /** 0-based page target, clamped to the document. */
    fun target(currentPage: Int, direction: Int, pageCount: Int): Int {
        if (pageCount <= 1 || direction == 0) return currentPage.coerceIn(0, pageCount - 1)
        return (currentPage + direction).coerceIn(0, pageCount - 1)
    }
}
