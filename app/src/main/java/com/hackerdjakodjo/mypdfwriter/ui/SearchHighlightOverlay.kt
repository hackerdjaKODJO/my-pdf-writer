package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.hackerdjakodjo.mypdfwriter.pdf.SearchMatchRect
import kotlin.math.min

/**
 * Draws search hits as translucent rounded highlights.
 *
 * The caller is expected to translate/scale [canvas] so that the page occupies
 * `(0, 0) - (width, height)` and to pass only the rects for the page being
 * drawn. Keeping this helper stateless means the viewer and export paths can
 * share it without introducing Compose state or touching PdfCanvas geometry.
 */
object SearchHighlightOverlay {

    /** Default highlight: warm yellow at ~40% opacity. */
    const val DEFAULT_COLOR: Int = 0x66FFD54F

    fun draw(
        canvas: Canvas,
        rects: List<SearchMatchRect>,
        width: Float,
        height: Float,
        color: Int = DEFAULT_COLOR,
    ) {
        if (rects.isEmpty() || width <= 0f || height <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        for (match in rects) {
            val left = match.rect.left * width
            val top = match.rect.top * height
            val right = match.rect.right * width
            val bottom = match.rect.bottom * height
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) continue
            if (right <= left || bottom <= top) continue
            val radius = min(right - left, bottom - top) * CORNER_FRACTION
            canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        }
    }

    private const val CORNER_FRACTION = 0.18f
}
