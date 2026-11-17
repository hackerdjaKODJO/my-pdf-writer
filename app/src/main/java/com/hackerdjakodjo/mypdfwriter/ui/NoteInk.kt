package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.clampInkPressure
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Default eraser radius in normalized page units. */
const val NOTE_ERASE_RADIUS = 0.03f

/** Gentle default stabilization strength used by "필기 보정". */
const val NOTE_STABILIZE_STRENGTH = 0.25f

/** Minimum sample count before automatic shape recognition is attempted. */
const val NOTE_MIN_SHAPE_POINTS = 8

/**
 * A freehand stroke that can be replaced by a cleaner geometric annotation.
 *
 * [points] are ready to store in an [Annotation] of [type]: two endpoints for a
 * LINE, and the opposite bounding-box corners for RECT/ELLIPSE.
 */
data class RecognizedShape(
    val type: AnnotationType,
    val points: List<NormalizedPoint>,
    val confidence: Float,
)

/** Result of applying the note eraser to one page. */
data class NoteEraseResult(
    val annotations: List<Annotation>,
    /** Annotations removed as a whole. */
    val removed: Int,
    /** Annotations that were only partially hit and replaced by surviving runs. */
    val split: Int,
    /** Total number of new annotations created by splitting. */
    val segments: Int,
) {
    val changed: Boolean get() = removed > 0 || split > 0
}

/**
 * Pure, offline ink helpers for the note editor.
 *
 * Everything here works on the normalized 0..1 page coordinate system and has no
 * Android/Compose dependency, so the behavior can be unit-tested on the JVM.
 */
object NoteInk {

    /**
     * Softens a freehand polyline while keeping its first and last samples exact.
     *
     * Each interior point is blended toward a 1:2:1 average of its neighbours. The
     * blend factor is half of [strength], so even strength 1.0 stays a local filter
     * rather than collapsing the stroke. Distinct input points are not merged.
     */
    fun stabilize(points: List<NormalizedPoint>, strength: Float): List<NormalizedPoint> {
        if (points.size < 3) return points
        if (!strength.isFinite()) return points
        val amount = strength.coerceIn(0f, 1f)
        if (amount <= 0f) return points
        val blend = amount * 0.5f
        val output = ArrayList<NormalizedPoint>(points.size)
        output.add(points.first())
        for (index in 1 until points.lastIndex) {
            val previous = points[index - 1]
            val current = points[index]
            val next = points[index + 1]
            val averageX = (previous.x + current.x * 2f + next.x) * 0.25f
            val averageY = (previous.y + current.y * 2f + next.y) * 0.25f
            val averagePressure =
                (previous.pressure + current.pressure * 2f + next.pressure) * 0.25f
            output.add(
                current.copy(
                    x = current.x + (averageX - current.x) * blend,
                    y = current.y + (averageY - current.y) * blend,
                    pressure = clampInkPressure(
                        current.pressure + (averagePressure - current.pressure) * blend,
                    ),
                ),
            )
        }
        output.add(points.last())
        return output
    }

    /** Explicitly named alias for callers that prefer the verb "stabilizeStroke". */
    fun stabilizeStroke(points: List<NormalizedPoint>, strength: Float): List<NormalizedPoint> =
        stabilize(points, strength)

    /**
     * Recognizes a rough LINE, RECT or ELLIPSE drawn in one PEN stroke.
     *
     * The recognizer is intentionally conservative: it needs [NOTE_MIN_SHAPE_POINTS]
     * samples, a stroke large enough to be intentional, and either a straightness
     * score (LINE) or a closed-path border/radial coverage score (RECT/ELLIPSE).
     * Squiggles, letters and open round shapes are rejected.
     */
    fun recognizeShape(points: List<NormalizedPoint>): RecognizedShape? {
        val samples = dedupeConsecutive(points)
        if (samples.size < NOTE_MIN_SHAPE_POINTS) return null

        val bounds = Bounds.of(samples)
        val diagonal = bounds.diagonal
        val pathLength = pathLength(samples)
        if (diagonal < 0.04f || pathLength < 0.08f) return null

        val closedDistance = distance(samples.first(), samples.last())
        val closed = closedDistance <= max(0.018f, diagonal * 0.22f)

        if (!closed) return recognizeLine(samples, bounds, pathLength)

        val rectangle = recognizeRectangle(samples, bounds, diagonal)
        val ellipse = recognizeEllipse(samples, bounds, diagonal)
        return when {
            rectangle != null && ellipse != null ->
                if (rectangle.confidence >= ellipse.confidence) rectangle else ellipse
            rectangle != null -> rectangle
            ellipse != null -> ellipse
            else -> null
        }
    }

    /**
     * Erases [trail] from a page's annotations.
     *
     * When [partial] is true, freehand-like polylines are split into surviving
     * segments. TEXT, STAMP, text signatures, RECT, ELLIPSE and REDACT are always
     * removed whole. A drawn SIGNATURE polyline (more than one point) follows the
     * stroke path and can be split. Only annotations on [targetLayerId] are
     * considered; pass null to erase regardless of layer. [defaultLayerId] mirrors
     * the editor's legacy behavior for annotations with no explicit layer.
     */
    fun eraseAnnotations(
        annotations: List<Annotation>,
        trail: List<NormalizedPoint>,
        radius: Float = NOTE_ERASE_RADIUS,
        targetLayerId: String? = null,
        defaultLayerId: String? = null,
        partial: Boolean = true,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): NoteEraseResult {
        if (trail.isEmpty() || annotations.isEmpty()) {
            return NoteEraseResult(annotations, removed = 0, split = 0, segments = 0)
        }
        val safeRadius = radius.coerceAtLeast(0.0001f)
        val probes = eraserProbes(trail, safeRadius)

        var removed = 0
        var split = 0
        var segments = 0
        val output = ArrayList<Annotation>(annotations.size)

        annotations.forEach { annotation ->
            val annotationLayer = annotation.layerId ?: defaultLayerId
            if (targetLayerId != null && annotationLayer != targetLayerId) {
                output.add(annotation)
                return@forEach
            }
            if (!noteAnnotationHit(annotation, probes, safeRadius)) {
                output.add(annotation)
                return@forEach
            }
            if (!partial || !isSplittable(annotation)) {
                removed++
                return@forEach
            }

            val runs = splitPolyline(annotation.points, probes, safeRadius)
            if (runs.isEmpty()) {
                removed++
                return@forEach
            }
            if (runs.size == 1 && runs.first() == annotation.points) {
                // Defensive: the hit test agreed but the split produced no cut.
                output.add(annotation)
                return@forEach
            }

            val replacement = runs.map { run ->
                val points = when (annotation.type) {
                    AnnotationType.LINE, AnnotationType.ARROW ->
                        listOf(run.first(), run.last())
                    else -> run
                }
                annotation.copy(id = idFactory(), points = points)
            }
            output.addAll(replacement)
            split++
            segments += replacement.size
        }

        return NoteEraseResult(
            annotations = output,
            removed = removed,
            split = split,
            segments = segments,
        )
    }

    /** Convenience alias for callers that want partial erase explicitly. */
    fun erasePartial(
        annotations: List<Annotation>,
        trail: List<NormalizedPoint>,
        radius: Float = NOTE_ERASE_RADIUS,
        targetLayerId: String? = null,
        defaultLayerId: String? = null,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): NoteEraseResult = eraseAnnotations(
        annotations = annotations,
        trail = trail,
        radius = radius,
        targetLayerId = targetLayerId,
        defaultLayerId = defaultLayerId,
        partial = true,
        idFactory = idFactory,
    )

    private val PARTIAL_SPLIT_TYPES = setOf(
        AnnotationType.PEN,
        AnnotationType.HIGHLIGHT,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.LINE,
        AnnotationType.ARROW,
        AnnotationType.SIGNATURE,
    )

    /**
     * Drawn (polyline) signatures can be split like other strokes. Text signatures
     * have a single anchor point or a stamp-like text payload and are removed whole.
     */
    private fun isSplittable(annotation: Annotation): Boolean =
        annotation.type in PARTIAL_SPLIT_TYPES &&
            (annotation.type != AnnotationType.SIGNATURE || annotation.points.size > 1)

    private fun recognizeLine(
        samples: List<NormalizedPoint>,
        bounds: Bounds,
        pathLength: Float,
    ): RecognizedShape? {
        val first = samples.first()
        val last = samples.last()
        val chordX = last.x - first.x
        val chordY = last.y - first.y
        val chord = hypot(chordX, chordY)
        if (chord < 0.02f) return null
        val efficiency = chord / pathLength.coerceAtLeast(0.0001f)
        if (efficiency < 0.9f) return null

        var meanX = 0f
        var meanY = 0f
        samples.forEach { point ->
            meanX += point.x
            meanY += point.y
        }
        meanX /= samples.size
        meanY /= samples.size

        var xx = 0f
        var xy = 0f
        var yy = 0f
        samples.forEach { point ->
            val dx = point.x - meanX
            val dy = point.y - meanY
            xx += dx * dx
            xy += dx * dy
            yy += dy * dy
        }
        val angle = 0.5f * atan2(2f * xy, xx - yy)
        var axisX = cos(angle)
        var axisY = sin(angle)
        if (axisX * chordX + axisY * chordY < 0f) {
            axisX = -axisX
            axisY = -axisY
        }

        var minProjection = Float.MAX_VALUE
        var maxProjection = -Float.MAX_VALUE
        var maxResidual = 0f
        var sumResidual = 0f
        samples.forEach { point ->
            val dx = point.x - meanX
            val dy = point.y - meanY
            val projection = dx * axisX + dy * axisY
            val residual = abs(-dx * axisY + dy * axisX)
            minProjection = min(minProjection, projection)
            maxProjection = max(maxProjection, projection)
            maxResidual = max(maxResidual, residual)
            sumResidual += residual
        }

        val span = maxProjection - minProjection
        val diagonal = bounds.diagonal
        if (span < diagonal * 0.8f) return null
        val maxTolerance = max(0.015f, diagonal * 0.075f)
        val meanTolerance = max(0.008f, diagonal * 0.025f)
        if (maxResidual > maxTolerance) return null
        if (sumResidual / samples.size > meanTolerance) return null

        val start = clampPoint(
            NormalizedPoint(
                meanX + axisX * minProjection,
                meanY + axisY * minProjection,
            ),
        )
        val end = clampPoint(
            NormalizedPoint(
                meanX + axisX * maxProjection,
                meanY + axisY * maxProjection,
            ),
        )
        if (distance(start, end) < 0.02f) return null

        val straightness = 1f - maxResidual / maxTolerance
        val confidence = (0.6f + 0.4f * efficiency) * straightness.coerceIn(0.2f, 1f)
        return RecognizedShape(
            type = AnnotationType.LINE,
            points = listOf(start, end),
            confidence = confidence.coerceIn(0f, 1f),
        )
    }

    private fun recognizeRectangle(
        samples: List<NormalizedPoint>,
        bounds: Bounds,
        diagonal: Float,
    ): RecognizedShape? {
        if (bounds.width < diagonal * 0.15f || bounds.height < diagonal * 0.15f) return null
        val tolerance = max(0.012f, diagonal * 0.085f)
        val binsPerSide = 4
        val sideBins = Array(4) { BooleanArray(binsPerSide) }
        var nearBorder = 0
        var sumBorderDistance = 0f

        samples.forEach { point ->
            val leftDistance = abs(point.x - bounds.left)
            val rightDistance = abs(point.x - bounds.right)
            val topDistance = abs(point.y - bounds.top)
            val bottomDistance = abs(point.y - bounds.bottom)
            val nearest = minOf(leftDistance, rightDistance, topDistance, bottomDistance)
            sumBorderDistance += nearest
            if (nearest <= tolerance) nearBorder++

            if (leftDistance <= tolerance) {
                markBin(sideBins[0], (point.y - bounds.top) / bounds.height, binsPerSide)
            }
            if (rightDistance <= tolerance) {
                markBin(sideBins[1], (point.y - bounds.top) / bounds.height, binsPerSide)
            }
            if (topDistance <= tolerance) {
                markBin(sideBins[2], (point.x - bounds.left) / bounds.width, binsPerSide)
            }
            if (bottomDistance <= tolerance) {
                markBin(sideBins[3], (point.x - bounds.left) / bounds.width, binsPerSide)
            }
        }

        val nearFraction = nearBorder.toFloat() / samples.size
        if (nearFraction < 0.82f) return null
        if (sumBorderDistance / samples.size > diagonal * 0.055f) return null

        val coverages = sideBins.map { bins -> bins.count { it }.toFloat() / binsPerSide }
        val minimumCoverage = coverages.minOrNull() ?: 0f
        val averageCoverage = coverages.average().toFloat()
        if (minimumCoverage < 0.25f || averageCoverage < 0.65f) return null

        val confidence = (nearFraction * 0.55f + averageCoverage * 0.45f).coerceIn(0f, 1f)
        return RecognizedShape(
            type = AnnotationType.RECT,
            points = listOf(
                NormalizedPoint(bounds.left, bounds.top),
                NormalizedPoint(bounds.right, bounds.bottom),
            ),
            confidence = confidence,
        )
    }

    private fun recognizeEllipse(
        samples: List<NormalizedPoint>,
        bounds: Bounds,
        diagonal: Float,
    ): RecognizedShape? {
        if (bounds.width < diagonal * 0.15f || bounds.height < diagonal * 0.15f) return null
        val radiusX = bounds.width / 2f
        val radiusY = bounds.height / 2f
        val residualTolerance = 0.18f
        val angularBins = BooleanArray(16)
        var good = 0
        var sumResidual = 0f

        samples.forEach { point ->
            val normalizedX = (point.x - bounds.centerX) / radiusX
            val normalizedY = (point.y - bounds.centerY) / radiusY
            val radial = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
            val residual = abs(radial - 1f)
            sumResidual += residual
            if (residual <= residualTolerance) good++

            val angle = atan2(normalizedY, normalizedX)
            val bin = (((angle + PI) / (2f * PI) * angularBins.size).toInt())
                .coerceIn(0, angularBins.lastIndex)
            angularBins[bin] = true
        }

        val goodFraction = good.toFloat() / samples.size
        if (goodFraction < 0.82f) return null
        if (sumResidual / samples.size > 0.11f) return null
        val angularCoverage = angularBins.count { it }.toFloat() / angularBins.size
        if (angularCoverage < 0.75f) return null

        val confidence = (goodFraction * 0.65f + angularCoverage * 0.35f).coerceIn(0f, 1f)
        return RecognizedShape(
            type = AnnotationType.ELLIPSE,
            points = listOf(
                NormalizedPoint(bounds.left, bounds.top),
                NormalizedPoint(bounds.right, bounds.bottom),
            ),
            confidence = confidence,
        )
    }

    private fun markBin(bins: BooleanArray, fraction: Float, count: Int) {
        val index = (fraction.coerceIn(0f, 1f) * count).toInt().coerceIn(0, count - 1)
        bins[index] = true
    }

    private fun clampPoint(point: NormalizedPoint): NormalizedPoint = point.copy(
        x = point.x.coerceIn(0f, 1f),
        y = point.y.coerceIn(0f, 1f),
    )

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        val diagonal: Float get() = hypot(width, height)

        companion object {
            fun of(points: List<NormalizedPoint>): Bounds {
                var left = Float.MAX_VALUE
                var top = Float.MAX_VALUE
                var right = -Float.MAX_VALUE
                var bottom = -Float.MAX_VALUE
                points.forEach { point ->
                    left = min(left, point.x)
                    top = min(top, point.y)
                    right = max(right, point.x)
                    bottom = max(bottom, point.y)
                }
                return Bounds(left, top, right, bottom)
            }
        }
    }
}

/** Top-level alias matching the original feature plan. */
fun stabilizeStroke(points: List<NormalizedPoint>, strength: Float): List<NormalizedPoint> =
    NoteInk.stabilize(points, strength)

/** Top-level alias for shape recognition. */
fun recognizeShape(points: List<NormalizedPoint>): RecognizedShape? =
    NoteInk.recognizeShape(points)

private fun dedupeConsecutive(points: List<NormalizedPoint>): List<NormalizedPoint> {
    if (points.size < 2) return points
    val output = ArrayList<NormalizedPoint>(points.size)
    points.forEach { point ->
        if (output.isEmpty() || !samePoint(output.last(), point)) output.add(point)
    }
    return output
}

private fun pathLength(points: List<NormalizedPoint>): Float {
    var length = 0f
    for (index in 1 until points.size) {
        length += distance(points[index - 1], points[index])
    }
    return length
}

private fun samePoint(a: NormalizedPoint, b: NormalizedPoint): Boolean =
    abs(a.x - b.x) <= 1e-6f && abs(a.y - b.y) <= 1e-6f

private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToSegment(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq <= 1e-12f) {
        0f
    } else {
        (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
    }
    val x = a.x + t * dx
    val y = a.y + t * dy
    val ex = point.x - x
    val ey = point.y - y
    return sqrt(ex * ex + ey * ey)
}

private fun distanceToPolyline(point: NormalizedPoint, points: List<NormalizedPoint>): Float {
    if (points.isEmpty()) return Float.MAX_VALUE
    if (points.size == 1) return distance(point, points[0])
    var best = Float.MAX_VALUE
    for (index in 1 until points.size) {
        best = min(best, distanceToSegment(point, points[index - 1], points[index]))
    }
    return best
}

private fun distanceToRectBorder(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Float {
    val left = min(a.x, b.x)
    val right = max(a.x, b.x)
    val top = min(a.y, b.y)
    val bottom = max(a.y, b.y)
    val corners = listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
    )
    var best = Float.MAX_VALUE
    for (index in corners.indices) {
        best = min(best, distanceToSegment(point, corners[index], corners[(index + 1) % corners.size]))
    }
    return best
}

private fun insideRect(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Boolean {
    val left = min(a.x, b.x)
    val right = max(a.x, b.x)
    val top = min(a.y, b.y)
    val bottom = max(a.y, b.y)
    return point.x in left..right && point.y in top..bottom
}

/**
 * Densifies the eraser trail so fast swipes still produce a continuous capsule.
 * Samples are at most half a radius apart.
 */
private fun eraserProbes(trail: List<NormalizedPoint>, radius: Float): List<NormalizedPoint> {
    if (trail.size < 2) return trail
    val targetStep = max(radius * 0.5f, 0.001f)
    val probes = ArrayList<NormalizedPoint>()
    for (index in 0 until trail.lastIndex) {
        val a = trail[index]
        val b = trail[index + 1]
        val length = distance(a, b)
        val steps = ceil(length / targetStep).toInt().coerceIn(1, 128)
        for (step in 0 until steps) {
            val t = step.toFloat() / steps
            probes.add(
                a.copy(
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    pressure = clampInkPressure(
                        a.pressure + (b.pressure - a.pressure) * t,
                    ),
                ),
            )
        }
    }
    probes.add(trail.last())
    return probes
}

private fun noteAnnotationHit(
    annotation: Annotation,
    probes: List<NormalizedPoint>,
    radius: Float,
): Boolean {
    if (probes.isEmpty() || annotation.points.isEmpty()) return false
    return when (annotation.type) {
        AnnotationType.PEN,
        AnnotationType.HIGHLIGHT,
        AnnotationType.SIGNATURE,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.LINE,
        AnnotationType.ARROW -> probes.any {
            distanceToPolyline(it, annotation.points) <= radius
        }

        AnnotationType.RECT,
        AnnotationType.ELLIPSE -> annotation.points.size >= 2 && probes.any {
            distanceToRectBorder(it, annotation.points[0], annotation.points[1]) <= radius
        }

        AnnotationType.TEXT,
        AnnotationType.STAMP -> distanceToPolyline(annotation.points[0], probes) <= radius * 2.4f

        AnnotationType.REDACT,
        AnnotationType.COVER_TEXT -> annotation.points.size >= 2 && probes.any {
            insideRect(it, annotation.points[0], annotation.points[1])
        }
    }
}

/**
 * Splits a polyline into runs outside the eraser capsules.
 *
 * Each original segment is intersected with every eraser probe circle; the
 * resulting parameter intervals are merged and the surviving pieces are joined
 * back into continuous runs. Cut boundaries are inserted as interpolated points,
 * so surviving segments end at the eraser edge instead of at a far-away sample.
 */
private fun splitPolyline(
    points: List<NormalizedPoint>,
    probes: List<NormalizedPoint>,
    radius: Float,
): List<List<NormalizedPoint>> {
    if (points.size < 2 || probes.isEmpty()) return emptyList()
    val runs = ArrayList<List<NormalizedPoint>>()
    var current = ArrayList<NormalizedPoint>()

    fun flush() {
        if (current.size >= 2) runs.add(current.toList())
        current = ArrayList()
    }

    fun append(start: NormalizedPoint, end: NormalizedPoint) {
        if (samePoint(start, end)) return
        if (current.isNotEmpty() && !samePoint(current.last(), start)) flush()
        if (current.isEmpty()) current.add(start)
        if (!samePoint(current.last(), end)) current.add(end)
    }

    for (index in 0 until points.lastIndex) {
        val a = points[index]
        val b = points[index + 1]
        val intervals = cutIntervals(a, b, probes, radius)
        var cursor = 0f
        intervals.forEach { interval ->
            if (interval.start > cursor + 1e-6f) {
                append(
                    interpolate(a, b, cursor),
                    interpolate(a, b, interval.start),
                )
            }
            if (interval.endInclusive > interval.start + 1e-6f) flush()
            cursor = max(cursor, interval.endInclusive)
        }
        if (cursor < 1f - 1e-6f) {
            append(
                interpolate(a, b, cursor),
                interpolate(a, b, 1f),
            )
        }
    }
    flush()
    return runs.filter { run ->
        run.size >= 2 && run.any { point -> !samePoint(point, run.first()) }
    }
}

private fun cutIntervals(
    a: NormalizedPoint,
    b: NormalizedPoint,
    probes: List<NormalizedPoint>,
    radius: Float,
): List<ClosedFloatingPointRange<Float>> {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSq = dx * dx + dy * dy
    if (lengthSq <= 1e-12f) {
        return if (probes.any { distance(a, it) <= radius }) listOf(0f..1f) else emptyList()
    }

    val intervals = ArrayList<ClosedFloatingPointRange<Float>>()
    val radiusSq = radius * radius
    probes.forEach { probe ->
        val fx = a.x - probe.x
        val fy = a.y - probe.y
        val quadraticA = lengthSq
        val quadraticB = 2f * (fx * dx + fy * dy)
        val quadraticC = fx * fx + fy * fy - radiusSq
        val discriminant = quadraticB * quadraticB - 4f * quadraticA * quadraticC
        if (discriminant < 0f) return@forEach
        val root = sqrt(discriminant)
        val first = (-quadraticB - root) / (2f * quadraticA)
        val second = (-quadraticB + root) / (2f * quadraticA)
        val start = max(0f, min(first, second))
        val end = min(1f, max(first, second))
        if (start <= end + 1e-6f) intervals.add(start..end)
    }
    if (intervals.isEmpty()) return emptyList()

    intervals.sortBy { it.start }
    val merged = ArrayList<ClosedFloatingPointRange<Float>>()
    intervals.forEach { interval ->
        val previous = merged.lastOrNull()
        if (previous == null || interval.start > previous.endInclusive + 1e-5f) {
            merged.add(interval)
        } else {
            merged[merged.lastIndex] = previous.start..max(previous.endInclusive, interval.endInclusive)
        }
    }
    return merged
}

private fun interpolate(a: NormalizedPoint, b: NormalizedPoint, t: Float): NormalizedPoint =
    a.copy(
        x = a.x + (b.x - a.x) * t,
        y = a.y + (b.y - a.y) * t,
        pressure = clampInkPressure(
            a.pressure + (b.pressure - a.pressure) * t,
        ),
    )
