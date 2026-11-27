package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.triangleVertices
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Axis-aligned normalized bounds used by the PDF annotation object editor. */
data class AnnotationBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
    val isValid: Boolean
        get() = width.isFinite() && height.isFinite() && width > 0f && height > 0f
}

private fun finitePoint(point: NormalizedPoint): Boolean =
    point.x.isFinite() && point.y.isFinite()

/**
 * Bounds for app-created annotations. Text uses a deterministic estimate that
 * matches the note selection helpers; point annotations use every stored point.
 */
fun annotationBounds(
    annotation: Annotation,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
): AnnotationBounds? {
    if (pageWidth <= 0f || pageHeight <= 0f || !pageWidth.isFinite() || !pageHeight.isFinite()) return null
    if (annotation.points.isEmpty() || annotation.points.any { !finitePoint(it) }) return null

    val textLike = annotation.type == AnnotationType.TEXT ||
        annotation.type == AnnotationType.STAMP ||
        (annotation.type == AnnotationType.SIGNATURE && annotation.points.size == 1)
    val raw = if (textLike) {
        textBounds(annotation, pageWidth, pageHeight) ?: return null
    } else {
        val left = annotation.points.minOf { it.x }
        val top = annotation.points.minOf { it.y }
        val right = annotation.points.maxOf { it.x }
        val bottom = annotation.points.maxOf { it.y }
        AnnotationBounds(left, top, right, bottom)
    }

    return raw
}

private fun textBounds(
    annotation: Annotation,
    pageWidth: Float,
    pageHeight: Float,
): AnnotationBounds? {
    val anchor = annotation.points.firstOrNull() ?: return null
    val aspect = pageHeight / pageWidth
    val fontSize = annotation.fontSize.takeIf { it.isFinite() && it > 0f } ?: 0.024f
    val local = when (annotation.type) {
        AnnotationType.STAMP -> {
            val label = annotation.stamp.ifBlank { "APPROVED" }.replace('\n', ' ').trim()
            val textSize = minOf(fontSize, 0.16f)
            val width = ((label.length * textSize * 0.72f + textSize * 1.2f) * aspect)
                .coerceAtMost(0.72f)
            val height = textSize * 1.9f
            listOf(-width * 0.5f to -height * 0.5f, width * 0.5f to height * 0.5f)
        }
        AnnotationType.SIGNATURE -> {
            val content = annotation.text.ifBlank { annotation.stamp }.ifBlank { " " }
            val textSize = minOf(fontSize, 0.14f)
            val width = (content.length * textSize * 0.58f * aspect).coerceAtMost(0.8f)
            val height = textSize * 1.25f
            listOf(0f to 0f, width to height)
        }
        else -> {
            val lines = annotation.text.ifBlank { " " }.split('\n')
            val longest = lines.maxOf { it.trimEnd().length }.coerceAtLeast(1)
            val width = maxOf(longest * fontSize * 0.56f, fontSize * 0.8f) * aspect
            val height = lines.size * fontSize * 1.3f
            listOf(0f to 0f, width to height)
        }
    }
    val (topLeft, bottomRight) = local
    val corners = listOf(
        topLeft,
        bottomRight.first to topLeft.second,
        bottomRight,
        topLeft.first to bottomRight.second,
    )
    val angle = Math.toRadians(annotation.textRotation.toDouble())
    val cosAngle = cos(angle).toFloat()
    val sinAngle = sin(angle).toFloat()
    val rotated = corners.map { (x, y) ->
        val px = x * pageWidth
        val py = y * pageHeight
        val rx = px * cosAngle - py * sinAngle
        val ry = px * sinAngle + py * cosAngle
        anchor.x + rx / pageWidth to anchor.y + ry / pageHeight
    }
    return AnnotationBounds(
        left = rotated.minOf { it.first },
        top = rotated.minOf { it.second },
        right = rotated.maxOf { it.first },
        bottom = rotated.maxOf { it.second },
    )
}

/** Bounds padded to include visible stroke width and usable transform handles. */
fun annotationSelectionBounds(
    annotation: Annotation,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
): AnnotationBounds? {
    val raw = annotationBounds(annotation, pageWidth, pageHeight) ?: return null
    val padY = maxOf(
        if (annotation.width.isFinite()) annotation.width.coerceAtLeast(0f) * 0.6f else 0f,
        0.002f,
    )
    val padX = padY * pageHeight / pageWidth
    return AnnotationBounds(
        left = raw.left - padX,
        top = raw.top - padY,
        right = raw.right + padX,
        bottom = raw.bottom + padY,
    )
}

/** Shape-aware physical hit test; [slack] is normalized to page height. */
fun annotationHitTest(
    annotation: Annotation,
    point: NormalizedPoint,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
    slack: Float = 0f,
): Boolean {
    if (!finitePoint(point) || annotation.points.isEmpty()) return false
    val slackPx = slack.coerceAtLeast(0f) * pageHeight
    val bounds = annotationBounds(annotation, pageWidth, pageHeight) ?: return false
    if ((annotation.type == AnnotationType.RECT || annotation.type == AnnotationType.ELLIPSE) &&
        annotation.points.size < 2
    ) {
        return false
    }
    val slackX = slackPx / pageWidth
    return when (annotation.type) {
        AnnotationType.REDACT,
        AnnotationType.COVER_TEXT,
        AnnotationType.TEXT,
        AnnotationType.STAMP,
        -> point.x in bounds.left..bounds.right && point.y in bounds.top..bounds.bottom

        AnnotationType.RECT -> point.x in bounds.left - slackX..bounds.right + slackX &&
            point.y in bounds.top - slack..bounds.bottom + slack

        AnnotationType.TRIANGLE -> polygonHitTest(
            point,
            triangleVertices(annotation.points),
            pageWidth,
            pageHeight,
            slackPx,
        )

        AnnotationType.ELLIPSE -> {
            val radiusX = bounds.width * 0.5f + slackX
            val radiusY = bounds.height * 0.5f + slack
            val nx = (point.x - bounds.centerX) / radiusX.coerceAtLeast(0.0001f)
            val ny = (point.y - bounds.centerY) / radiusY.coerceAtLeast(0.0001f)
            nx * nx + ny * ny <= 1f
        }

        else -> polylineHitTest(point, annotation.points, pageWidth, pageHeight, slackPx)
    }
}

private fun polylineHitTest(
    point: NormalizedPoint,
    points: List<NormalizedPoint>,
    pageWidth: Float,
    pageHeight: Float,
    slackPx: Float,
): Boolean {
    if (points.size == 1) {
        return annotationDistanceToSegment(point, points[0], points[0], pageWidth, pageHeight) <= slackPx
    }
    for (index in 1 until points.size) {
        if (
            annotationDistanceToSegment(
                point,
                points[index - 1],
                points[index],
                pageWidth,
                pageHeight,
            ) <= slackPx
        ) {
            return true
        }
    }
    return false
}

private fun polygonHitTest(
    point: NormalizedPoint,
    vertices: List<NormalizedPoint>,
    pageWidth: Float,
    pageHeight: Float,
    slackPx: Float,
): Boolean {
    if (vertices.size < 3) return false
    if (pointInPolygon(point, vertices)) return true
    return polylineHitTest(point, vertices + vertices.first(), pageWidth, pageHeight, slackPx)
}

private fun pointInPolygon(point: NormalizedPoint, vertices: List<NormalizedPoint>): Boolean {
    var inside = false
    var index = 0
    var previous = vertices.lastIndex
    while (index < vertices.size) {
        val current = vertices[index]
        val before = vertices[previous]
        val crosses = (current.y > point.y) != (before.y > point.y)
        if (crosses) {
            val x = before.x + (point.y - before.y) * (current.x - before.x) / (current.y - before.y)
            if (point.x < x) inside = !inside
        }
        previous = index++
    }
    return inside
}

fun translateAnnotation(
    annotation: Annotation,
    dx: Float,
    dy: Float,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
): Annotation {
    if (!dx.isFinite() || !dy.isFinite() || annotation.points.isEmpty()) return annotation
    val translated = annotation.copy(
        points = annotation.points.map { it.copy(x = it.x + dx, y = it.y + dy) },
    )
    return constrainAnnotationToPage(translated, pageWidth, pageHeight)
}

fun resizeAnnotation(
    annotation: Annotation,
    from: AnnotationBounds,
    to: AnnotationBounds,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
): Annotation {
    if (!from.isValid || !to.isValid || annotation.points.isEmpty()) return annotation
    val scaleX = to.width / from.width
    val scaleY = to.height / from.height
    val points = annotation.points.map { point ->
        point.copy(
            x = to.left + (point.x - from.left) * scaleX,
            y = to.top + (point.y - from.top) * scaleY,
        )
    }
    val fontSize = if (
        annotation.type == AnnotationType.TEXT ||
            annotation.type == AnnotationType.STAMP ||
            annotation.type == AnnotationType.SIGNATURE
    ) {
        (annotation.fontSize * maxOf(scaleX, scaleY)).coerceAtLeast(0.001f)
    } else {
        annotation.fontSize
    }
    return constrainAnnotationToPage(
        annotation.copy(points = points, fontSize = fontSize),
        pageWidth,
        pageHeight,
    )
}

fun rotateAnnotation(
    annotation: Annotation,
    angleRadians: Float,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
): Annotation {
    if (!angleRadians.isFinite() || pageWidth <= 0f || pageHeight <= 0f || annotation.points.isEmpty()) return annotation
    val bounds = annotationBounds(annotation, pageWidth, pageHeight) ?: return annotation
    val cosAngle = cos(angleRadians)
    val sinAngle = sin(angleRadians)
    val points = annotation.points.map { point ->
        val px = (point.x - bounds.centerX) * pageWidth
        val py = (point.y - bounds.centerY) * pageHeight
        val rx = px * cosAngle - py * sinAngle
        val ry = px * sinAngle + py * cosAngle
        point.copy(
            x = bounds.centerX + rx / pageWidth,
            y = bounds.centerY + ry / pageHeight,
        )
    }
    val textRotation = if (
        annotation.type == AnnotationType.TEXT ||
            annotation.type == AnnotationType.STAMP ||
            annotation.type == AnnotationType.SIGNATURE
    ) {
        normalizeAnnotationRotation(annotation.textRotation + Math.toDegrees(angleRadians.toDouble()).toInt())
    } else {
        annotation.textRotation
    }
    return constrainAnnotationToPage(
        annotation.copy(points = points, textRotation = textRotation),
        pageWidth,
        pageHeight,
    )
}

private fun constrainAnnotationToPage(
    annotation: Annotation,
    pageWidth: Float,
    pageHeight: Float,
): Annotation {
    if (annotation.points.isEmpty()) return annotation
    val bounds = annotationSelectionBounds(annotation, pageWidth, pageHeight) ?: return annotation
    val shiftX = when {
        bounds.left < 0f -> -bounds.left
        bounds.right > 1f -> 1f - bounds.right
        else -> 0f
    }
    val shiftY = when {
        bounds.top < 0f -> -bounds.top
        bounds.bottom > 1f -> 1f - bounds.bottom
        else -> 0f
    }
    if (shiftX == 0f && shiftY == 0f) return annotation
    return annotation.copy(
        points = annotation.points.map { it.copy(x = it.x + shiftX, y = it.y + shiftY) },
    )
}

fun annotationRotationDegrees(
    start: NormalizedPoint,
    current: NormalizedPoint,
    centerX: Float,
    centerY: Float,
    pageWidth: Float,
    pageHeight: Float,
): Float {
    val startAngle = Math.atan2(
        ((start.y - centerY) * pageHeight).toDouble(),
        ((start.x - centerX) * pageWidth).toDouble(),
    )
    val currentAngle = Math.atan2(
        ((current.y - centerY) * pageHeight).toDouble(),
        ((current.x - centerX) * pageWidth).toDouble(),
    )
    return Math.toDegrees(currentAngle - startAngle).toFloat()
}

fun annotationDistanceToSegment(
    point: NormalizedPoint,
    start: NormalizedPoint,
    end: NormalizedPoint,
    pageWidth: Float,
    pageHeight: Float,
): Float {
    val px = point.x * pageWidth
    val py = point.y * pageHeight
    val ax = start.x * pageWidth
    val ay = start.y * pageHeight
    val bx = end.x * pageWidth
    val by = end.y * pageHeight
    val dx = bx - ax
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0f) return hypot(px - ax, py - ay)
    val t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared
    val clamped = t.coerceIn(0f, 1f)
    return hypot(px - (ax + clamped * dx), py - (ay + clamped * dy))
}

internal fun normalizeAnnotationRotation(rotation: Int): Int {
    val normalized = rotation % 360
    return if (normalized < 0) normalized + 360 else normalized
}
