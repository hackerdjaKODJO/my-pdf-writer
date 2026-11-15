package com.hackerdjakodjo.mypdfwriter.data

/**
 * Pure geometry helpers for keeping annotations attached to their page content
 * when a page is rotated.
 *
 * Coordinates are normalized to the displayed page area (0..1). When the page
 * turns by 90/270 degrees the display aspect ratio flips, so stroke width and
 * font size need opposite corrections to keep their physical size.
 */
object PageTransforms {

    fun rotate(annotation: Annotation, delta: Int, aspect: Float): Annotation {
        val rotation = PageSpec.normalizeRotation(delta)
        val points = when (rotation) {
            90 -> annotation.points.map { NormalizedPoint(1f - it.y, it.x) }
            180 -> annotation.points.map { NormalizedPoint(1f - it.x, 1f - it.y) }
            270 -> annotation.points.map { NormalizedPoint(it.y, 1f - it.x) }
            else -> annotation.points
        }
        val widthScale = if (rotation == 0 || rotation == 180) 1f else aspect
        val fontScale = if (rotation == 0 || rotation == 180) 1f else 1f / aspect
        return annotation.copy(
            points = points,
            width = (annotation.width * widthScale).coerceIn(0.0005f, 0.1f),
            fontSize = (annotation.fontSize * fontScale).coerceIn(0.008f, 0.12f),
            textRotation = PageSpec.normalizeRotation(annotation.textRotation + rotation),
        )
    }
}
