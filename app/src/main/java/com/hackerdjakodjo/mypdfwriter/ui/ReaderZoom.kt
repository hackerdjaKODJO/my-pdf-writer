package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.roundToInt

/** Smallest continuous-reader zoom: one page fits the viewport width. */
const val READER_ZOOM_MIN = 1.0f

/** Largest continuous-reader zoom, kept modest so page bitmaps stay usable. */
const val READER_ZOOM_MAX = 2.5f

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

/**
 * Applies a native `detectTransformGestures` zoom delta only while two fingers
 * are down. A single-finger delta is ignored so vertical scrolling (and the
 * separate horizontal pan) stay independent of zoom. The result is clamped to
 * the supported reader range.
 */
fun gatedPinchZoom(current: Float, zoomChange: Float, twoFinger: Boolean): Float {
    if (!twoFinger || !zoomChange.isFinite() || zoomChange <= 0f) {
        return clampReaderZoom(current)
    }
    return clampReaderZoom(current * zoomChange)
}

/**
 * Converts a two-finger pinch into a zoom factor. The whole continuous reader
 * uses the returned value, so every page stays at the same zoom level.
 */
fun zoomFromPinch(
    startZoom: Float,
    startDistance: Float,
    currentDistance: Float,
): Float {
    if (!startZoom.isFinite()) return READER_ZOOM_MIN
    val base = clampReaderZoom(startZoom)
    if (!startDistance.isFinite() || startDistance <= 0f || !currentDistance.isFinite()) {
        return base
    }
    return clampReaderZoom(base * currentDistance / startDistance)
}

/**
 * Largest horizontal pan in pixels for a viewport width and zoom.
 *
 * The zoomed page is left-aligned and overflows to the right, so pan 0 shows the
 * left edge and [readerMaxPanPx] shows the right edge. Keeping this math here
 * (instead of duplicating it in the gesture observer and the page layout) makes
 * it impossible for the stored pan and the displayed offset to disagree.
 */
fun readerMaxPanPx(viewportWidthPx: Float, zoom: Float): Float {
    val width = if (viewportWidthPx.isFinite()) viewportWidthPx.coerceAtLeast(1f) else 1f
    return (width * (clampReaderZoom(zoom) - READER_ZOOM_MIN)).coerceAtLeast(0f)
}

/**
 * Re-clamps a stored pan after a zoom or viewport change.
 *
 * A zoom decrease shrinks the available pan range. Without this clamp the
 * stored value stays at the old (larger) maximum while the layout silently
 * clamps it for display, so the first rightward drags produce no visible
 * movement — the page appears stuck and the left side is unreachable.
 */
fun clampReaderPan(panPx: Float, zoom: Float, viewportWidthPx: Float): Float {
    if (!panPx.isFinite()) return 0f
    return panPx.coerceIn(0f, readerMaxPanPx(viewportWidthPx, zoom))
}

/**
 * Applies one pointer frame of horizontal panning. A positive [deltaX] means
 * the finger moved right, which reveals the LEFT part of the zoomed page.
 */
fun panReaderAfterDrag(
    panPx: Float,
    deltaX: Float,
    zoom: Float,
    viewportWidthPx: Float,
): Float = clampReaderPan(panPx - deltaX, zoom, viewportWidthPx)

/**
 * Minimum share of the pan range kept reachable in BOTH directions after a
 * zoom change. A focal near the viewport edge otherwise leaves pan at 0 (or at
 * maxPan), making one drag direction a complete no-op.
 */
const val READER_PAN_HEADROOM_FRACTION = 0.25f

/**
 * Focal-preserving pan with a guaranteed headroom band.
 *
 * A zoom anchored near the left edge used to produce panX ≈ 0, and because the
 * continuous reader clamps pan to [0, maxPan] with the page left-aligned, a
 * rightward drag immediately clamped back to 0 — the page did not move at all.
 * Keeping the result inside [25%, 75%] of the range preserves the focal for
 * ordinary centred zooms while leaving travel in both directions.
 */
fun readerPanWithHeadroom(
    panPx: Float,
    oldZoom: Float,
    newZoom: Float,
    focalX: Float,
    viewportWidthPx: Float,
): Float {
    val maxPan = readerMaxPanPx(viewportWidthPx, newZoom)
    if (maxPan <= 0f) return 0f
    val focalPan = panAfterZoom(panPx, oldZoom, newZoom, focalX, viewportWidthPx)
    val reserve = maxPan * READER_PAN_HEADROOM_FRACTION
    return focalPan.coerceIn(reserve, maxPan - reserve)
}

/**
 * Re-anchors the horizontal pan when zoom changes so the page content under
 * [focalX] stays under the same screen position.
 *
 * Without this, zooming always left [panPx] at 0: the enlarged page is pinned to
 * the viewport's left edge, so a rightward drag produced a negative pan that
 * clamped straight back to 0 and the page could not be moved right at all.
 *
 * Derivation: with page left edge at -pan and content fraction t,
 * `screenX = t * W * zoom - pan`. Holding t fixed at the focal point gives
 * `panAfter = panBefore * (z1/z0) + focalX * (z1/z0 - 1)`.
 */
fun panAfterZoom(
    panPx: Float,
    oldZoom: Float,
    newZoom: Float,
    focalX: Float,
    viewportWidthPx: Float,
): Float {
    val z0 = clampReaderZoom(oldZoom)
    val z1 = clampReaderZoom(newZoom)
    val width = if (viewportWidthPx.isFinite()) viewportWidthPx.coerceAtLeast(1f) else 1f
    val focus = if (focalX.isFinite()) focalX.coerceIn(0f, width) else width / 2f
    val before = clampReaderPan(panPx, z0, width)
    val ratio = z1 / z0
    return clampReaderPan(before * ratio + focus * (ratio - 1f), z1, width)
}

/**
 * Pan to seed when a saved reader zoom is restored (page starts fit, centred
 * focal). Kept pure so the headroom invariant is covered by JVM tests instead
 * of living only inside a LaunchedEffect. Without the headroom step this seed
 * can plant pan at a clamp edge, making the first rightward drag a no-op.
 */
fun readerRestoredPan(zoom: Float, viewportWidthPx: Float): Float =
    readerPanWithHeadroom(
        panPx = 0f,
        oldZoom = READER_ZOOM_MIN,
        newZoom = zoom,
        focalX = viewportWidthPx / 2f,
        viewportWidthPx = viewportWidthPx,
    )

/** Rendering scale used when a continuous page fits the viewport width. */
const val READER_BASE_RENDER_SCALE = 1.25f

/** Upper bound so a 250% pinch does not allocate an oversized bitmap. */
const val READER_MAX_RENDER_SCALE = 3.0f

/**
 * Quantized zoom used for bitmap re-rendering. Continuous pinch values are
 * rounded to the nearest quarter step, so dragging does not re-render on every
 * pointer frame but the actual page bitmap does grow with the zoom level.
 */
fun readerRenderZoom(zoom: Float): Float {
    val clamped = clampReaderZoom(zoom)
    return ((clamped * 4f).roundToInt() / 4f).coerceIn(READER_ZOOM_MIN, READER_ZOOM_MAX)
}

/** Actual PDF render scale for a given display zoom. */
fun readerRenderScale(renderZoom: Float): Float =
    (READER_BASE_RENDER_SCALE * clampReaderZoom(renderZoom))
        .coerceAtMost(READER_MAX_RENDER_SCALE)

/** Short percent label, e.g. `100%`, `125%` or `250%`. */
fun readerZoomLabel(zoom: Float): String =
    "${(clampReaderZoom(zoom) * 100f).roundToInt()}%"
