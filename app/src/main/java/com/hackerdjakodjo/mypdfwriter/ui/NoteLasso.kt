package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import kotlin.math.max
import kotlin.math.min

/**
 * Pure lasso hit-testing over page annotations.
 *
 * The page canvas works in normalized page coordinates, so the same test is used
 * for a free-hand polygon and for a two-corner rectangular selection. Closed
 * shapes are selected when their bounds meet the lasso; strokes are selected when
 * a sampled point lies inside or any segment crosses the lasso edge. Text, stamps
 * and signatures use their visible text box, which is enough for object selection
 * without measuring a bitmap.
 */
fun noteLassoSelection(
    annotations: List<Annotation>,
    polygon: List<NormalizedPoint>,
    rectangular: Boolean,
): Set<String> {
    val outline = if (rectangular) rectangleOutline(polygon) else polygon
    if (outline.size < 3) return emptySet()
    return annotations.asSequence()
        .filter { it.points.isNotEmpty() && annotationHitsLasso(it, outline) }
        .map { it.id }
        .toSet()
}

private data class NoteBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(point: NormalizedPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    fun intersects(other: NoteBox): Boolean =
        left <= other.right && right >= other.left && top <= other.bottom && bottom >= other.top

    fun corners(): List<NormalizedPoint> = listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
    )
}

private fun rectangleOutline(points: List<NormalizedPoint>): List<NormalizedPoint> {
    if (points.size < 2) return emptyList()
    val a = points.first()
    val b = points.last()
    val left = min(a.x, b.x)
    val right = max(a.x, b.x)
    val top = min(a.y, b.y)
    val bottom = max(a.y, b.y)
    if (right - left < 0.0001f || bottom - top < 0.0001f) return emptyList()
    return listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
    )
}

private fun annotationHitsLasso(annotation: Annotation, outline: List<NormalizedPoint>): Boolean =
    when (annotation.type) {
        AnnotationType.PEN,
        AnnotationType.HIGHLIGHT,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.LINE,
        AnnotationType.ARROW -> polylineHitsLasso(annotation.points, outline)

        AnnotationType.SIGNATURE ->
            if (annotation.points.size > 1) {
                polylineHitsLasso(annotation.points, outline)
            } else {
                boxHitsLasso(signatureBox(annotation), outline)
            }

        AnnotationType.TEXT -> boxHitsLasso(textBox(annotation), outline)
        AnnotationType.STAMP -> boxHitsLasso(stampBox(annotation), outline)
        AnnotationType.RECT,
        AnnotationType.ELLIPSE,
        AnnotationType.REDACT -> {
            if (annotation.points.size >= 2) {
                val a = annotation.points[0]
                val b = annotation.points[1]
                boxHitsLasso(
                    NoteBox(
                        left = min(a.x, b.x),
                        top = min(a.y, b.y),
                        right = max(a.x, b.x),
                        bottom = max(a.y, b.y),
                    ),
                    outline,
                )
            } else {
                false
            }
        }
    }

private fun textBox(annotation: Annotation): NoteBox {
    val anchor = annotation.points.first()
    val lines = annotation.text.ifBlank { " " }.split('\n')
    val longest = lines.maxOfOrNull { it.trimEnd().length }?.coerceAtLeast(1) ?: 1
    val charWidth = annotation.fontSize * 0.56f
    val lineHeight = annotation.fontSize * 1.3f
    val width = (longest * charWidth).coerceAtLeast(annotation.fontSize * 0.8f)
    val height = lineHeight * lines.size
    return NoteBox(
        left = anchor.x,
        top = anchor.y,
        right = anchor.x + width,
        bottom = anchor.y + height,
    )
}

private fun stampBox(annotation: Annotation): NoteBox {
    val anchor = annotation.points.first()
    val label = annotation.stamp.ifBlank { "APPROVED" }.replace('\n', ' ').trim()
    val textSize = annotation.fontSize.coerceAtLeast(0.01f)
    val width = (label.length * textSize * 0.72f + textSize * 1.2f)
        .coerceAtLeast(textSize * 1.6f)
    val height = textSize * 1.9f
    return NoteBox(
        left = anchor.x - width / 2f,
        top = anchor.y - height / 2f,
        right = anchor.x + width / 2f,
        bottom = anchor.y + height / 2f,
    )
}

private fun signatureBox(annotation: Annotation): NoteBox {
    val anchor = annotation.points.first()
    val text = annotation.text.ifBlank { annotation.stamp }.ifBlank { " " }
    val textSize = annotation.fontSize.coerceAtLeast(0.01f)
    val width = (text.length * textSize * 0.58f).coerceAtLeast(textSize)
    return NoteBox(
        left = anchor.x,
        top = anchor.y,
        right = anchor.x + width,
        bottom = anchor.y + textSize * 1.25f,
    )
}

private fun polylineHitsLasso(
    points: List<NormalizedPoint>,
    outline: List<NormalizedPoint>,
): Boolean {
    if (points.any { pointInPolygon(it, outline) }) return true
    if (points.size == 1) return false
    for (i in 1 until points.size) {
        if (segmentHitsPolygon(points[i - 1], points[i], outline)) return true
    }
    return false
}

private fun segmentHitsPolygon(
    a: NormalizedPoint,
    b: NormalizedPoint,
    outline: List<NormalizedPoint>,
): Boolean {
    for (i in outline.indices) {
        if (segmentsIntersect(a, b, outline[i], outline[(i + 1) % outline.size])) return true
    }
    return false
}

private fun boxHitsLasso(box: NoteBox, outline: List<NormalizedPoint>): Boolean {
    if (box.corners().any { pointInPolygon(it, outline) }) return true
    if (outline.any { box.contains(it) }) return true
    for (i in outline.indices) {
        val a = outline[i]
        val b = outline[(i + 1) % outline.size]
        if (segmentIntersectsBox(a, b, box)) return true
    }
    return false
}

private fun segmentIntersectsBox(
    a: NormalizedPoint,
    b: NormalizedPoint,
    box: NoteBox,
): Boolean {
    if (box.contains(a) || box.contains(b)) return true
    val corners = box.corners()
    for (i in corners.indices) {
        if (segmentsIntersect(a, b, corners[i], corners[(i + 1) % corners.size])) return true
    }
    return false
}

/**
 * Ray casting with an inclusive boundary. The polygon is implicitly closed from
 * the last point back to the first point.
 */
private fun pointInPolygon(point: NormalizedPoint, polygon: List<NormalizedPoint>): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var previous = polygon.last()
    for (current in polygon) {
        if (pointOnSegment(point, previous, current)) return true
        val crosses =
            (current.y > point.y) != (previous.y > point.y) &&
                point.x <
                (previous.x - current.x) * (point.y - current.y) /
                (previous.y - current.y) + current.x
        if (crosses) inside = !inside
        previous = current
    }
    return inside
}

private fun pointOnSegment(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Boolean {
    val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
    if (kotlin.math.abs(cross) > 0.00001f) return false
    return point.x in (min(a.x, b.x) - 0.00001f)..(max(a.x, b.x) + 0.00001f) &&
        point.y in (min(a.y, b.y) - 0.00001f)..(max(a.y, b.y) + 0.00001f)
}

private fun segmentsIntersect(
    a: NormalizedPoint,
    b: NormalizedPoint,
    c: NormalizedPoint,
    d: NormalizedPoint,
): Boolean {
    val o1 = orientation(a, b, c)
    val o2 = orientation(a, b, d)
    val o3 = orientation(c, d, a)
    val o4 = orientation(c, d, b)
    if (o1 * o2 < 0f && o3 * o4 < 0f) return true
    if (kotlin.math.abs(o1) <= 0.00001f && pointOnSegment(c, a, b)) return true
    if (kotlin.math.abs(o2) <= 0.00001f && pointOnSegment(d, a, b)) return true
    if (kotlin.math.abs(o3) <= 0.00001f && pointOnSegment(a, c, d)) return true
    if (kotlin.math.abs(o4) <= 0.00001f && pointOnSegment(b, c, d)) return true
    return false
}

private fun orientation(
    a: NormalizedPoint,
    b: NormalizedPoint,
    c: NormalizedPoint,
): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
