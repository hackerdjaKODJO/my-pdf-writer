package com.hackerdjakodjo.mypdfwriter.pdf

import kotlin.math.max
import kotlin.math.min

/**
 * View-only crop margins, stored as fractions of the *original* page (0..1).
 *
 * The four insets shave blank edges off the displayed page: [left] and [right]
 * are removed from the horizontal extent, [top] and [bottom] from the vertical
 * one. Nothing about the PDF file, its page box or any stored annotation is
 * modified — these values only drive a display transform and the inverse
 * hit-test mapping in the readers. All four are zero by default, which is an
 * exact identity transform.
 */
data class CropInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        /** Convenience identity value, safe to share because this is immutable. */
        val NONE: CropInsets = CropInsets()

        /**
         * At least this much of each axis always remains visible. Without a
         * floor, adversarial JSON (or a settings slider at both extremes)
         * could collapse the page to zero pixels and break the inverse map.
         */
        const val MIN_VISIBLE_FRACTION: Float = 0.1f
    }
}

/** A point in one of the two normalized crop coordinate spaces. Pure/JVM-safe. */
data class CropPoint(val x: Float, val y: Float)

/**
 * Integral pixel window inside a rendered bitmap that corresponds to the
 * visible crop rectangle. Used by the image-based continuous/spread readers so
 * they can draw exactly the kept sub-rectangle without copying the bitmap.
 */
data class CropPixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Clean-room, view-only crop math shared by every PDF surface.
 *
 * Coordinate spaces:
 *  - **page** : normalized 0..1 over the original page, where annotation,
 *    text, link and memo geometry is already stored.
 *  - **display**: normalized 0..1 over the remaining visible rectangle after
 *    the margins are removed.
 *
 * Every function here is pure, offline and deterministic; none of them reads
 * or writes a PDF, a bitmap or a preference store.
 */
object PdfCrop {

    /** Largest inset allowed on one side so the opposite axis cannot vanish. */
    private const val MAX_SIDE = 1f - CropInsets.MIN_VISIBLE_FRACTION

    /**
     * Sanitizes raw insets: non-finite or negative values become 0, each side
     * is capped, and an over-large pair is scaled down proportionally so the
     * visible width and height are always at least [CropInsets.MIN_VISIBLE_FRACTION].
     * Idempotent and safe to call on every render/hit-test pass.
     */
    fun clampInsets(insets: CropInsets): CropInsets {
        fun side(value: Float): Float =
            if (value.isFinite()) value.coerceIn(0f, MAX_SIDE) else 0f

        var l = side(insets.left)
        var t = side(insets.top)
        var r = side(insets.right)
        var b = side(insets.bottom)
        val horizontal = l + r
        if (horizontal > MAX_SIDE) {
            val scale = MAX_SIDE / horizontal
            l *= scale
            r *= scale
        }
        val vertical = t + b
        if (vertical > MAX_SIDE) {
            val scale = MAX_SIDE / vertical
            t *= scale
            b *= scale
        }
        return CropInsets(l, t, r, b)
    }

    /** Visible portion of the page in page-normalized coordinates. */
    fun visibleRect(insets: CropInsets): NormalizedRect {
        val c = clampInsets(insets)
        return NormalizedRect(c.left, c.top, 1f - c.right, 1f - c.bottom)
    }

    /** True when the crop does nothing; callers can keep their legacy fast path. */
    fun isIdentity(insets: CropInsets): Boolean {
        val c = clampInsets(insets)
        return c.left == 0f && c.top == 0f && c.right == 0f && c.bottom == 0f
    }

    /**
     * Display point (0..1 over the visible area) to original page point.
     * Deliberately affine and unclamped: when the user pans/zooms past the
     * visible area the result may leave 0..1, which is exactly what ink
     * hit-testing needs so live strokes do not stick to the crop edge.
     */
    fun displayToPage(x: Float, y: Float, insets: CropInsets): CropPoint {
        val v = visibleRect(insets)
        return CropPoint(v.left + x * v.width, v.top + y * v.height)
    }

    /** Inverse of [displayToPage]: original page point to display point. */
    fun pageToDisplay(x: Float, y: Float, insets: CropInsets): CropPoint {
        val v = visibleRect(insets)
        return CropPoint(
            (x - v.left) / v.width,
            (y - v.top) / v.height,
        )
    }

    /**
     * Maps a rectangle stored in page coordinates into display coordinates.
     * The crop map is monotonic because [visibleRect] always has a positive
     * span, so mapping both opposite corners is enough. Used to keep markup,
     * search and link rectangles aligned with the cropped page image.
     */
    fun applyCropToRect(rect: NormalizedRect, insets: CropInsets): NormalizedRect {
        val a = pageToDisplay(rect.left, rect.top, insets)
        val b = pageToDisplay(rect.right, rect.bottom, insets)
        return NormalizedRect(
            min(a.x, b.x),
            min(a.y, b.y),
            max(a.x, b.x),
            max(a.y, b.y),
        )
    }

    /**
     * Odd/even margin policy for facing-page layouts.
     *
     * Zero-based [pageIndex] is used: page 0 is the first (odd, 1-based) page.
     * With [oddEvenEnabled] off every page keeps the same insets. When it is
     * on, even-numbered pages mirror the left/right pair so a bound book's
     * inner and outer margins stay balanced; top/bottom are unchanged.
     */
    fun cropForPage(
        insets: CropInsets,
        pageIndex: Int,
        oddEvenEnabled: Boolean,
    ): CropInsets {
        val safe = clampInsets(insets)
        if (!oddEvenEnabled || pageIndex < 0) return safe
        val isEvenPage = ((pageIndex + 1) and 1) == 0
        return if (isEvenPage) safe.copy(left = safe.right, right = safe.left) else safe
    }

    /** Converts a visible page rectangle into an integral bitmap crop window. */
    fun cropPixelRect(
        visible: NormalizedRect,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ): CropPixelRect {
        val w = bitmapWidth.coerceAtLeast(1)
        val h = bitmapHeight.coerceAtLeast(1)
        val left = (visible.left * w).toInt().coerceIn(0, w - 1)
        val top = (visible.top * h).toInt().coerceIn(0, h - 1)
        val right = (visible.right * w).toInt().coerceIn(left + 1, w)
        val bottom = (visible.bottom * h).toInt().coerceIn(top + 1, h)
        return CropPixelRect(left, top, right - left, bottom - top)
    }
}
