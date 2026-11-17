package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.roundToInt

/** Smallest continuous-reader zoom: one page fits the viewport width. */
const val READER_ZOOM_MIN = 1.0f

/** Largest continuous-reader zoom, kept modest so page bitmaps stay usable. */
const val READER_ZOOM_MAX = 2.5f

/** Increment used by the toolbar −/+ buttons. */
const val READER_ZOOM_STEP = 0.25f

/** Zoom reached by double-tapping a page. */
const val READER_ZOOM_DOUBLE_TAP = 2.0f

/**
 * Clamps a zoom factor into the supported range. Non-finite values fall back to
 * [READER_ZOOM_MIN] so a bad saved value can never break the reader layout.
 */
fun clampReaderZoom(value: Float): Float {
    if (!value.isFinite()) return READER_ZOOM_MIN
    return value.coerceIn(READER_ZOOM_MIN, READER_ZOOM_MAX)
}

/** Applies [steps] button increments to [current] and clamps the result. */
fun stepReaderZoom(current: Float, steps: Int): Float =
    clampReaderZoom(clampReaderZoom(current) + steps * READER_ZOOM_STEP)

/**
 * Double-tap toggle: fit the page when already zoomed, otherwise jump straight
 * to [READER_ZOOM_DOUBLE_TAP] for readable text.
 */
fun toggleReaderZoom(current: Float): Float =
    if (clampReaderZoom(current) > READER_ZOOM_MIN) {
        READER_ZOOM_MIN
    } else {
        READER_ZOOM_DOUBLE_TAP
    }

/** Short percent label for the toolbar, e.g. `100%`, `125%` or `250%`. */
fun readerZoomLabel(zoom: Float): String =
    "${(clampReaderZoom(zoom) * 100f).roundToInt()}%"
