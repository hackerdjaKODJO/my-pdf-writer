package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A4 portrait width/height ratio, duplicated here because this file must stay free
 * of Android/Compose imports. Keep it in sync with `PaperBackground.A4_ASPECT`.
 */
const val NOTE_SELECTION_PAGE_ASPECT = 595f / 842f

private const val MIN_SELECTION_SCALE = 0.05f
private const val MAX_SELECTION_SCALE = 20f
private const val MIN_STROKE_WIDTH = 0.0001f
private const val MIN_FONT_SIZE = 0.002f

/** Axis-aligned bounds of a lasso selection in normalized page coordinates. */
data class NoteSelectionBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * One immutable selection edit: a nudge, a proportional scale, a rotation, or any
 * combination of the three.
 *
 * Order is scale, then rotation, then translation, always around the selection
 * centre. Rotation converts normalized coordinates to physical page coordinates
 * first, so a 90° turn on an A4 page keeps the visual geometry square instead of
 * stretching it with the non-square normalized axes. Every resulting point is
 * clamped to 0..1 without reordering the stroke samples.
 *
 * Only [Annotation.points], [Annotation.width], [Annotation.fontSize] and
 * [Annotation.textRotation] change. Type, layer, colour, opacity, pen style,
 * timestamps, text and stamp content are copied unchanged.
 */
data class NoteSelectionTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
) {
    val isIdentity: Boolean
        get() = translateX == 0f &&
            translateY == 0f &&
            scale == 1f &&
            rotationDegrees == 0f

    fun applyTo(
        annotations: List<Annotation>,
        pageAspect: Float = NOTE_SELECTION_PAGE_ASPECT,
    ): List<Annotation> {
        if (annotations.isEmpty() || isIdentity) return annotations
        if (!translateX.isFinite() || !translateY.isFinite() ||
            !scale.isFinite() || !rotationDegrees.isFinite()
        ) {
            return annotations
        }

        val bounds = noteSelectionBounds(annotations, pageAspect) ?: return annotations
        val aspect = pageAspect.takeIf { it.isFinite() && it > 0f } ?: NOTE_SELECTION_PAGE_ASPECT
        val factor = if (scale > 0f) scale.coerceIn(MIN_SELECTION_SCALE, MAX_SELECTION_SCALE) else 1f
        val degrees = rotationDegrees.roundToInt()
        val radians = degrees * PI.toFloat() / 180f
        val cosA = cos(radians)
        val sinA = sin(radians)
        val centerX = bounds.centerX
        val centerY = bounds.centerY

        val provisional = annotations.map { annotation ->
            val transformedPoints = annotation.points.map { point ->
                val scaledX = centerX + (point.x - centerX) * factor
                val scaledY = centerY + (point.y - centerY) * factor
                val rotated = rotateOffset(
                    dx = scaledX - centerX,
                    dy = scaledY - centerY,
                    cosA = cosA,
                    sinA = sinA,
                    aspect = aspect,
                )
                point.copy(
                    x = clampUnit(centerX + rotated.x),
                    y = clampUnit(centerY + rotated.y),
                )
            }
            annotation.copy(
                points = transformedPoints,
                width = if (factor == 1f) {
                    annotation.width
                } else {
                    max(annotation.width * factor, MIN_STROKE_WIDTH)
                },
                fontSize = if (factor == 1f) {
                    annotation.fontSize
                } else {
                    max(annotation.fontSize * factor, MIN_FONT_SIZE)
                },
                textRotation = if (degrees != 0 && annotation.type.hasTextRotation()) {
                    PageSpec.normalizeRotation(annotation.textRotation + degrees)
                } else {
                    annotation.textRotation
                },
            )
        }

        // Translate the whole group as far as the page edge allows. This keeps the
        // selected shape intact instead of collapsing edge points onto each other.
        val movedBounds = pointBounds(provisional)
        val dx = if (movedBounds == null) {
            0f
        } else {
            translateX.coerceIn(-movedBounds.left, 1f - movedBounds.right)
        }
        val dy = if (movedBounds == null) {
            0f
        } else {
            translateY.coerceIn(-movedBounds.top, 1f - movedBounds.bottom)
        }
        if (dx == 0f && dy == 0f) return provisional

        return provisional.map { annotation ->
            annotation.copy(
                points = annotation.points.map { point ->
                    point.copy(
                        x = clampUnit(point.x + dx),
                        y = clampUnit(point.y + dy),
                    )
                },
            )
        }
    }

    companion object {
        fun translate(dx: Float, dy: Float): NoteSelectionTransform =
            NoteSelectionTransform(translateX = dx, translateY = dy)

        fun scaleAroundCenter(factor: Float): NoteSelectionTransform =
            NoteSelectionTransform(scale = factor)

        fun rotateAroundCenter(degrees: Float): NoteSelectionTransform =
            NoteSelectionTransform(rotationDegrees = degrees)
    }
}

/**
 * Bounds of the selected annotations. Strokes, lines and shapes use their point
 * extent; TEXT, STAMP and text signatures include the approximate visible box used
 * by lasso hit-testing. A rotated text box contributes the corners of its rotated
 * outline so the selection centre matches what the painter draws.
 */
fun noteSelectionBounds(
    annotations: List<Annotation>,
    pageAspect: Float = NOTE_SELECTION_PAGE_ASPECT,
): NoteSelectionBounds? {
    if (annotations.isEmpty()) return null
    val aspect = pageAspect.takeIf { it.isFinite() && it > 0f } ?: NOTE_SELECTION_PAGE_ASPECT
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY

    fun include(bounds: NoteSelectionBounds) {
        left = min(left, bounds.left)
        top = min(top, bounds.top)
        right = max(right, bounds.right)
        bottom = max(bottom, bounds.bottom)
    }

    annotations.forEach { annotation ->
        val bounds = when {
            annotation.points.isEmpty() -> null
            annotation.type == AnnotationType.TEXT -> textBounds(annotation, aspect)
            annotation.type == AnnotationType.STAMP -> stampBounds(annotation, aspect)
            annotation.type == AnnotationType.SIGNATURE && annotation.points.size == 1 ->
                signatureTextBounds(annotation, aspect)

            else -> strokeBounds(annotation.points)
        }
        if (bounds != null) include(bounds)
    }

    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
        return null
    }
    return NoteSelectionBounds(left = left, top = top, right = right, bottom = bottom)
}

private fun AnnotationType.hasTextRotation(): Boolean = when (this) {
    AnnotationType.TEXT,
    AnnotationType.STAMP,
    AnnotationType.SIGNATURE -> true

    else -> false
}

private fun strokeBounds(points: List<NormalizedPoint>): NoteSelectionBounds? {
    if (points.isEmpty()) return null
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    points.forEach { point ->
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    return NoteSelectionBounds(left, top, right, bottom)
}

private fun pointBounds(annotations: List<Annotation>): NoteSelectionBounds? =
    strokeBounds(annotations.flatMap { it.points })

private fun textBounds(annotation: Annotation, aspect: Float): NoteSelectionBounds? {
    val anchor = annotation.points.firstOrNull() ?: return null
    val textSize = annotation.fontSize.coerceAtLeast(MIN_FONT_SIZE)
    val lines = annotation.text.ifBlank { " " }.split('\n')
    val longest = lines.maxOfOrNull { it.trimEnd().length }?.coerceAtLeast(1) ?: 1
    val width = max(longest * textSize * 0.56f, textSize * 0.8f)
    val height = textSize * 1.3f * lines.size
    return rotatedBoxBounds(
        anchor = anchor,
        localLeft = 0f,
        localTop = 0f,
        localRight = width,
        localBottom = height,
        rotationDegrees = annotation.textRotation,
        aspect = aspect,
    )
}

private fun stampBounds(annotation: Annotation, aspect: Float): NoteSelectionBounds? {
    val anchor = annotation.points.firstOrNull() ?: return null
    val textSize = annotation.fontSize.coerceAtLeast(MIN_FONT_SIZE)
    val label = annotation.stamp.ifBlank { "APPROVED" }.replace('\n', ' ').trim()
    val width = (label.length * textSize * 0.72f + textSize * 1.2f)
        .coerceAtLeast(textSize * 1.6f)
    val height = textSize * 1.9f
    return rotatedBoxBounds(
        anchor = anchor,
        localLeft = -width / 2f,
        localTop = -height / 2f,
        localRight = width / 2f,
        localBottom = height / 2f,
        rotationDegrees = annotation.textRotation,
        aspect = aspect,
    )
}

private fun signatureTextBounds(annotation: Annotation, aspect: Float): NoteSelectionBounds? {
    val anchor = annotation.points.firstOrNull() ?: return null
    val textSize = annotation.fontSize.coerceAtLeast(MIN_FONT_SIZE)
    val text = annotation.text.ifBlank { annotation.stamp }.ifBlank { " " }
    val width = (text.length * textSize * 0.58f).coerceAtLeast(textSize)
    val height = textSize * 1.25f
    return rotatedBoxBounds(
        anchor = anchor,
        localLeft = 0f,
        localTop = 0f,
        localRight = width,
        localBottom = height,
        rotationDegrees = annotation.textRotation,
        aspect = aspect,
    )
}

private fun rotatedBoxBounds(
    anchor: NormalizedPoint,
    localLeft: Float,
    localTop: Float,
    localRight: Float,
    localBottom: Float,
    rotationDegrees: Int,
    aspect: Float,
): NoteSelectionBounds {
    val radians = rotationDegrees * PI.toFloat() / 180f
    val cosA = cos(radians)
    val sinA = sin(radians)
    val corners = listOf(
        rotateOffset(localLeft, localTop, cosA, sinA, aspect),
        rotateOffset(localRight, localTop, cosA, sinA, aspect),
        rotateOffset(localRight, localBottom, cosA, sinA, aspect),
        rotateOffset(localLeft, localBottom, cosA, sinA, aspect),
    )
    val left = corners.minOf { anchor.x + it.x }
    val top = corners.minOf { anchor.y + it.y }
    val right = corners.maxOf { anchor.x + it.x }
    val bottom = corners.maxOf { anchor.y + it.y }
    return NoteSelectionBounds(left, top, right, bottom)
}

/**
 * Rotates a normalized offset as a physical page offset. [aspect] is page width ÷
 * page height, so x is compressed by the page shape and y is stretched back.
 */
private fun rotateOffset(
    dx: Float,
    dy: Float,
    cosA: Float,
    sinA: Float,
    aspect: Float,
): NormalizedPoint = NormalizedPoint(
    x = dx * cosA - dy * (sinA / aspect),
    y = dx * (aspect * sinA) + dy * cosA,
)

private fun clampUnit(value: Float): Float = when {
    !value.isFinite() -> 0f
    value <= 0f -> 0f
    value >= 1f -> 1f
    else -> value
}
