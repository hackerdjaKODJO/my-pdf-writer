package com.hackerdjakodjo.mypdfwriter.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Physical A4 paper math and the original orientation policy used by notebooks.
 *
 * Policy — "A4 re-cut, never stretch":
 *  - Every page is a real A4 sheet: 595 x 842 pt in portrait, 842 x 595 pt in
 *    landscape. Orientation changes the sheet, not the handwriting.
 *  - Annotation points are converted through physical points, so strokes, shapes
 *    and text keep their real size. They are never scaled non-uniformly and never
 *    rotated by the app.
 *  - Ink is shifted as one group only when that keeps it inside the new sheet. If
 *    the ink is physically larger than the new sheet, the overflow is centred and
 *    clipped by the page in the editor and in raster PDF export. Undo restores it.
 *  - Stroke width (relative to page width) and text size (relative to page height)
 *    are converted as well, so a pen line stays the same thickness on screen.
 *
 * Pure Kotlin so the policy can be unit-tested without Android or Compose.
 */
object NotePaper {

    const val PORTRAIT_WIDTH_PT = 595f
    const val PORTRAIT_HEIGHT_PT = 842f
    const val LANDSCAPE_WIDTH_PT = PORTRAIT_HEIGHT_PT
    const val LANDSCAPE_HEIGHT_PT = PORTRAIT_WIDTH_PT
    const val PORTRAIT_ASPECT = PORTRAIT_WIDTH_PT / PORTRAIT_HEIGHT_PT

    private const val MIN_NORMALIZED = 0.0001f
    private const val EPSILON = 0.0001f

    fun widthPt(orientation: PageOrientation): Float = when (orientation) {
        PageOrientation.PORTRAIT -> PORTRAIT_WIDTH_PT
        PageOrientation.LANDSCAPE -> LANDSCAPE_WIDTH_PT
    }

    fun heightPt(orientation: PageOrientation): Float = when (orientation) {
        PageOrientation.PORTRAIT -> PORTRAIT_HEIGHT_PT
        PageOrientation.LANDSCAPE -> LANDSCAPE_HEIGHT_PT
    }

    fun aspect(orientation: PageOrientation): Float = widthPt(orientation) / heightPt(orientation)

    /**
     * Re-cuts [page] onto [target] paper without stretching or rotating its ink.
     *
     * The returned page has the new orientation. Points may fall slightly outside
     * 0..1 when the old ink is physically larger than the new sheet; renderers clip
     * them, and undo is the supported way to recover a cropped orientation change.
     */
    fun reorient(page: NotePage, target: PageOrientation): NotePage {
        if (page.orientation == target) return page
        if (page.annotations.isEmpty()) return page.copy(orientation = target)

        val oldWidth = widthPt(page.orientation)
        val oldHeight = heightPt(page.orientation)
        val newWidth = widthPt(target)
        val newHeight = heightPt(target)
        val bounds = inkBounds(page, oldWidth, oldHeight) ?: return page.copy(orientation = target)

        val shiftX = fitTranslation(bounds.left, bounds.right, newWidth)
        val shiftY = fitTranslation(bounds.top, bounds.bottom, newHeight)
        val widthFactor = oldWidth / newWidth
        val heightFactor = oldHeight / newHeight

        val moved = page.annotations.map { annotation ->
            annotation.copy(
                points = annotation.points.map { point ->
                    NormalizedPoint(
                        x = (point.x * oldWidth + shiftX) / newWidth,
                        y = (point.y * oldHeight + shiftY) / newHeight,
                        pressure = point.pressure,
                    )
                },
                width = (annotation.width * widthFactor).coerceAtLeast(MIN_NORMALIZED),
                fontSize = (annotation.fontSize * heightFactor).coerceAtLeast(MIN_NORMALIZED),
            )
        }
        return page.copy(orientation = target, annotations = moved)
    }

    /**
     * Rotates the whole physical page content by 90 degrees and swaps A4
     * portrait/landscape. This is deliberately different from [reorient], which
     * only re-cuts the sheet and keeps handwriting upright.
     *
     * - Points rotate about the page centre: clockwise `(x, y) -> (1-y, x)`,
     *   counter-clockwise `(x, y) -> (y, 1-x)`. A4's swapped sides make the same
     *   normalized formula valid in both directions.
     * - Per-point pressure, ids, timestamps, layer ids, opacity, stamps and text
     *   fields survive unchanged. Stroke width and font size are converted so a
     *   pen line and a text glyph keep their physical size on the new sheet.
     * - Text rotation follows the sheet, so a label stays attached to the same
     *   piece of writing instead of becoming upright while its line turns.
     * - The custom-background key and label are preserved. The image itself is
     *   not rotated or duplicated; [com.hackerdjakodjo.mypdfwriter.ui.PaperBackground]
     *   re-applies its cover crop under the new orientation. A portrait photo on a
     *   landscape page therefore crops the top/bottom more tightly, and rotating
     *   back restores the original crop.
     *
     * The page id and notebook-level bookmarks are not touched. Rotating clockwise
     * then counter-clockwise is the identity for normal annotation values.
     */
    fun rotatePage(page: NotePage, clockwise: Boolean = true): NotePage {
        val target = if (page.orientation == PageOrientation.PORTRAIT) {
            PageOrientation.LANDSCAPE
        } else {
            PageOrientation.PORTRAIT
        }
        val oldWidth = widthPt(page.orientation)
        val oldHeight = heightPt(page.orientation)
        val newWidth = widthPt(target)
        val newHeight = heightPt(target)
        val widthFactor = oldWidth / newWidth
        val heightFactor = oldHeight / newHeight
        val degrees = if (clockwise) 90 else 270

        val rotated = page.annotations.map { annotation ->
            val points = annotation.points.map { point ->
                if (clockwise) {
                    NormalizedPoint(
                        x = 1f - point.y,
                        y = point.x,
                        pressure = point.pressure,
                    )
                } else {
                    NormalizedPoint(
                        x = point.y,
                        y = 1f - point.x,
                        pressure = point.pressure,
                    )
                }
            }
            annotation.copy(
                points = points,
                width = annotation.width * widthFactor,
                fontSize = annotation.fontSize * heightFactor,
                textRotation = PageSpec.normalizeRotation(annotation.textRotation + degrees),
            )
        }
        return page.copy(orientation = target, annotations = rotated)
    }

    /**
     * True when re-cutting [page] onto [target] would put ink outside the sheet.
     * Used by the template studio to warn before a destructive orientation change.
     */
    fun wouldCrop(page: NotePage, target: PageOrientation): Boolean {
        if (page.orientation == target || page.annotations.isEmpty()) return false
        val bounds = inkBounds(
            page = page,
            width = widthPt(page.orientation),
            height = heightPt(page.orientation),
        ) ?: return false
        return bounds.right - bounds.left > widthPt(target) + EPSILON ||
            bounds.bottom - bounds.top > heightPt(target) + EPSILON
    }

    fun hasInk(page: NotePage): Boolean = page.annotations.any { it.points.isNotEmpty() }

    /**
     * Translation for one axis. Keep the group's original placement when it already
     * fits; otherwise move it just enough to fit. If the group is larger than the
     * sheet, centre the overflow so both edges crop equally.
     */
    internal fun fitTranslation(min: Float, max: Float, newSize: Float): Float {
        if (!min.isFinite() || !max.isFinite() || !newSize.isFinite() || newSize <= 0f) {
            return 0f
        }
        val span = max - min
        if (span <= 0f) return 0f
        if (span > newSize) return (newSize - span) / 2f - min
        val lower = -min
        val upper = newSize - max
        return when {
            lower > 0f -> lower
            upper < 0f -> upper
            else -> 0f
        }
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    )

    /**
     * Conservative physical ink bounds. Stroke half-width and approximate text
     * boxes are included so the studio warning does not miss text that starts on
     * the page but extends past its edge.
     */
    private fun inkBounds(page: NotePage, width: Float, height: Float): Bounds? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var found = false

        fun include(bounds: Bounds) {
            left = minOf(left, bounds.left)
            top = minOf(top, bounds.top)
            right = maxOf(right, bounds.right)
            bottom = maxOf(bottom, bounds.bottom)
            found = true
        }

        page.annotations.forEach { annotation ->
            annotationBounds(annotation, width, height)?.let(::include)
        }
        return if (found && left.isFinite() && top.isFinite() &&
            right.isFinite() && bottom.isFinite()
        ) {
            Bounds(left, top, right, bottom)
        } else {
            null
        }
    }

    private fun annotationBounds(
        annotation: Annotation,
        width: Float,
        height: Float,
    ): Bounds? {
        if (annotation.points.isEmpty()) return null
        val strokePad = (annotation.width * width * 0.5f).coerceAtLeast(0.5f)
        val textSize = (annotation.fontSize * height).coerceAtLeast(0f)
        return when (annotation.type) {
            AnnotationType.TEXT -> textLikeBounds(
                anchor = annotation.points.first(),
                width = textWidth(annotation.text, textSize),
                height = textSize * 1.3f * annotation.text.lines().size.coerceAtLeast(1),
                rotation = annotation.textRotation,
                pageWidth = width,
                pageHeight = height,
            )

            AnnotationType.STAMP -> {
                val label = annotation.stamp.ifBlank { "APPROVED" }
                val boxWidth = textWidth(label, textSize) + textSize * 1.2f
                val boxHeight = textSize * 1.9f
                rotatedBounds(
                    anchor = annotation.points.first(),
                    localLeft = -boxWidth / 2f,
                    localTop = -boxHeight / 2f,
                    localRight = boxWidth / 2f,
                    localBottom = boxHeight / 2f,
                    rotation = annotation.textRotation,
                    pageWidth = width,
                    pageHeight = height,
                )
            }

            AnnotationType.SIGNATURE -> {
                if (annotation.points.size > 1) {
                    pointBounds(annotation.points, strokePad, width, height)
                } else {
                    val text = annotation.text.ifBlank { annotation.stamp }
                    textLikeBounds(
                        anchor = annotation.points.first(),
                        width = textWidth(text, textSize),
                        height = textSize * 1.3f,
                        rotation = annotation.textRotation,
                        pageWidth = width,
                        pageHeight = height,
                    )
                }
            }

            else -> pointBounds(annotation.points, strokePad, width, height)
        }
    }

    private fun pointBounds(
        points: List<NormalizedPoint>,
        pad: Float,
        width: Float,
        height: Float,
    ): Bounds? {
        val first = points.firstOrNull() ?: return null
        var left = first.x * width
        var top = first.y * height
        var right = left
        var bottom = top
        points.drop(1).forEach { point ->
            val x = point.x * width
            val y = point.y * height
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }
        return Bounds(left - pad, top - pad, right + pad, bottom + pad)
    }

    private fun textLikeBounds(
        anchor: NormalizedPoint,
        width: Float,
        height: Float,
        rotation: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): Bounds = rotatedBounds(
        anchor = anchor,
        localLeft = 0f,
        localTop = 0f,
        localRight = width.coerceAtLeast(1f),
        localBottom = height.coerceAtLeast(1f),
        rotation = rotation,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
    )

    private fun rotatedBounds(
        anchor: NormalizedPoint,
        localLeft: Float,
        localTop: Float,
        localRight: Float,
        localBottom: Float,
        rotation: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): Bounds {
        val radians = rotation * PI.toFloat() / 180f
        val cosA = cos(radians)
        val sinA = sin(radians)
        val anchorX = anchor.x * pageWidth
        val anchorY = anchor.y * pageHeight
        val corners = listOf(
            localLeft to localTop,
            localRight to localTop,
            localRight to localBottom,
            localLeft to localBottom,
        ).map { (dx, dy) ->
            (anchorX + dx * cosA - dy * sinA) to (anchorY + dx * sinA + dy * cosA)
        }
        return Bounds(
            left = corners.minOf { it.first },
            top = corners.minOf { it.second },
            right = corners.maxOf { it.first },
            bottom = corners.maxOf { it.second },
        )
    }

    /** Conservative text width: CJK is one em, Latin about 0.6 em. */
    private fun textWidth(text: String, textSize: Float): Float {
        if (text.isEmpty() || textSize <= 0f) return 0f
        var longest = 0f
        text.lines().forEach { line ->
            var lineWidth = 0f
            line.forEach { ch ->
                lineWidth += when {
                    ch == '\t' -> 0.9f
                    ch.code < 0x20 -> 0f
                    ch.code < 0x80 -> 0.6f
                    else -> 1.0f
                }
            }
            if (lineWidth > longest) longest = lineWidth
        }
        return longest * textSize
    }
}
