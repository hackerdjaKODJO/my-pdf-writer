package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.InkResponse
import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.inkResponseSeries
import com.hackerdjakodjo.mypdfwriter.data.revealPointCount
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Paints the annotation layer with the platform canvas.
 *
 * The same code is used for the on-screen overlay and for export, so the exported PDF
 * is pixel-faithful to what the student sees (including Korean text, stamps, signatures
 * and redaction blackouts). [progress] lets replay reveal each annotation gradually;
 * callers that want the normal result keep the default of `1f`. Growing pen
 * previews may opt into `livePreview` to suppress final-length-dependent end
 * profiles; committed replay and export keep the default `false`. Global
 * stroke means and speed references remain full-stroke in this bounded patch.
 */
/**
 * Non-throwing stamp border width. A very long stamp (or a tiny thumbnail)
 * shrinks the text box below 1.5px; `coerceIn(1.5f, smaller)` used to throw and
 * abort the whole annotation layer / PDF export.
 */
internal fun stampBorderWidth(
    annotationWidth: Float,
    widthPx: Float,
    boxHeight: Float,
): Float {
    val width = if (annotationWidth.isFinite()) annotationWidth.coerceAtLeast(0f) else 0f
    val canvas = if (widthPx.isFinite()) widthPx.coerceAtLeast(1f) else 1f
    val max = if (boxHeight.isFinite()) boxHeight * 0.16f else 0f
    return (width * canvas * 1.4f)
        .coerceAtLeast(1.5f)
        .coerceAtMost(maxOf(1.5f, max))
}

object AnnotationPainter {

    fun draw(
        canvas: Canvas,
        annotations: List<Annotation>,
        width: Float,
        height: Float,
        progress: (Annotation) -> Float = { 1f },
        livePreview: Boolean = false,
    ) {
        annotations.forEach {
            drawOne(canvas, it, width, height, progress(it), livePreview = livePreview)
        }
    }

    fun drawOne(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float = 1f,
        livePreview: Boolean = false,
    ) {
        if (annotation.points.isEmpty()) return
        val reveal = fraction.coerceIn(0f, 1f)
        if (reveal <= 0f) return
        if (reveal < 1f) {
            drawPartial(canvas, annotation, width, height, reveal, livePreview)
            return
        }
        when (annotation.type) {
            AnnotationType.PEN -> drawPen(
                canvas,
                annotation,
                width,
                height,
                inkContextFor(annotation, width, height, livePreview),
            )
            AnnotationType.HIGHLIGHT -> drawStroke(
                canvas,
                annotation,
                width,
                height,
                baseAlpha = 96,
                response = annotationInkResponse(annotation),
            )
            AnnotationType.RECT -> drawRect(canvas, annotation, width, height)
            AnnotationType.ARROW -> drawArrow(canvas, annotation, width, height)
            AnnotationType.TEXT -> drawText(canvas, annotation, width, height)
            AnnotationType.UNDERLINE, AnnotationType.STRIKEOUT -> drawMarkupLine(canvas, annotation, width, height)
            AnnotationType.ELLIPSE -> drawEllipse(canvas, annotation, width, height)
            AnnotationType.LINE -> drawStraightLine(canvas, annotation, width, height)
            AnnotationType.STAMP -> drawStamp(canvas, annotation, width, height)
            AnnotationType.SIGNATURE -> drawSignature(canvas, annotation, width, height)
            AnnotationType.REDACT -> drawRedact(canvas, annotation, width, height)
            AnnotationType.COVER_TEXT -> drawCoverText(canvas, annotation, width, height)
        }
    }

    /**
     * Replay rendering for `0 < fraction < 1`. Strokes use the annotation's
     * timing-aware reveal count, two-point shapes grow their endpoint, and
     * text-like annotations fade in. Fraction `1f` still uses the exact
     * original rendering path above.
     */
    private fun drawPartial(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float,
        livePreview: Boolean,
    ) {
        when (annotation.type) {
            AnnotationType.PEN, AnnotationType.HIGHLIGHT -> {
                // Everything derived from the stroke comes from the full
                // annotation, not the revealed prefix: ink density, outline
                // widths, fountain nib factors and the brush envelope. Replay
                // only slices those arrays so already revealed ink never shifts.
                val points = revealedPoints(annotation, fraction)
                if (points.isEmpty()) return
                val ink = inkContextFor(annotation, width, height, livePreview)
                val partial = annotation.copy(
                    points = points,
                    timesMs = annotation.timesMs?.take(points.size),
                )
                if (annotation.type == AnnotationType.PEN) {
                    drawPen(
                        canvas,
                        partial,
                        width,
                        height,
                        ink.copy(
                            series = ink.series.take(points.size),
                            outlineWidths = ink.outlineWidths.copyOf(points.size),
                            fountainSmoothed = ink.fountainSmoothed.copyOf(points.size),
                            brushEnvelope = ink.brushEnvelope.copyOf(points.size),
                            speeds = ink.speeds.copyOf(points.size),
                            arcLengths = ink.arcLengths.copyOf(points.size),
                            segmentSpeeds = ink.segmentSpeeds.copyOf(points.size),
                            pools = InkDynamics.poolsForReveal(ink.pools, points.size),
                        ),
                    )
                } else {
                    drawStroke(canvas, partial, width, height, baseAlpha = 96, response = ink.mean)
                }
            }

            AnnotationType.RECT,
            AnnotationType.ELLIPSE,
            AnnotationType.ARROW,
            AnnotationType.LINE,
            AnnotationType.REDACT -> {
                val points = revealedEndpoints(annotation.points, fraction) ?: return
                val partial = annotation.copy(points = points)
                when (annotation.type) {
                    AnnotationType.RECT -> drawRect(canvas, partial, width, height)
                    AnnotationType.ELLIPSE -> drawEllipse(canvas, partial, width, height)
                    AnnotationType.ARROW -> drawArrow(canvas, partial, width, height)
                    AnnotationType.LINE -> drawStraightLine(canvas, partial, width, height)
                    AnnotationType.REDACT -> drawRedact(canvas, partial, width, height)
                    else -> Unit
                }
            }

            AnnotationType.UNDERLINE, AnnotationType.STRIKEOUT -> {
                val points = if (annotation.points.size == 2) {
                    revealedEndpoints(annotation.points, fraction) ?: return
                } else {
                    revealedPoints(annotation, fraction)
                }
                if (points.isEmpty()) return
                val partial = annotation.copy(
                    points = points,
                    timesMs = annotation.timesMs?.take(points.size),
                )
                drawMarkupLine(canvas, partial, width, height)
            }

            AnnotationType.TEXT -> drawText(canvas, annotation.faded(fraction), width, height)
            AnnotationType.STAMP -> drawStamp(canvas, annotation.faded(fraction), width, height)
            // Text-like reveal: fade the whole cover + replacement in together.
            AnnotationType.COVER_TEXT -> drawCoverText(canvas, annotation.faded(fraction), width, height)

            AnnotationType.SIGNATURE -> {
                if (annotation.points.size > 1) {
                    val points = revealedPoints(annotation, fraction)
                    if (points.isEmpty()) return
                    val partial = annotation.copy(
                        points = points,
                        timesMs = annotation.timesMs?.take(points.size),
                    )
                    drawSignature(canvas, partial, width, height)
                } else {
                    drawSignature(canvas, annotation.faded(fraction), width, height)
                }
            }
        }
    }

    /**
     * Timed replay delegates the count to the annotation so nonuniform capture
     * timing is honored. The model's null-timing implementation is the legacy
     * nearest-sample round used by [revealedPoints].
     */
    internal fun revealedPoints(
        annotation: Annotation,
        fraction: Float,
    ): List<NormalizedPoint> {
        val points = annotation.points
        if (points.isEmpty()) return emptyList()
        val count = annotation.revealPointCount(fraction.coerceIn(0f, 1f))
            .coerceIn(0, points.size)
        return if (count <= 0) {
            emptyList()
        } else if (count >= points.size) {
            points
        } else {
            points.subList(0, count).toList()
        }
    }

    internal fun revealedPoints(
        points: List<NormalizedPoint>,
        fraction: Float,
    ): List<NormalizedPoint> {
        // ceil(0.3f * 10) can be 4 because 0.3f is 0.30000001f; rounding to
        // nearest keeps replay aligned with the audio timeline.
        val count = (points.size * fraction + 0.5f).toInt().coerceIn(1, points.size)
        return if (count >= points.size) points else points.subList(0, count).toList()
    }

    /** Interpolates the end of a two-point shape from its start point. */
    private fun revealedEndpoints(
        points: List<NormalizedPoint>,
        fraction: Float,
    ): List<NormalizedPoint>? {
        if (points.size < 2) return null
        val start = points.first()
        val end = points.last()
        val t = fraction.coerceIn(0f, 1f)
        return listOf(
            start,
            end.copy(
                x = start.x + (end.x - start.x) * t,
                y = start.y + (end.y - start.y) * t,
            ),
        )
    }

    private fun Annotation.faded(fraction: Float): Annotation =
        copy(opacity = (opacity * fraction.coerceIn(0f, 1f)).coerceIn(0f, 1f))

    fun drawEraserPreview(canvas: Canvas, point: NormalizedPoint, radiusPx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(190, 70, 70, 70)
        }
        canvas.drawCircle(point.x, point.y, radiusPx, paint)
        paint.color = Color.argb(40, 70, 70, 70)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(point.x, point.y, radiusPx, paint)
    }

    private fun opacityAlpha(annotation: Annotation, baseAlpha: Int = 255): Int =
        (baseAlpha * annotation.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

    private fun basePaint(annotation: Annotation, width: Float, baseAlpha: Int = 255): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation, baseAlpha)
            style = Paint.Style.STROKE
            strokeWidth = (annotation.width * width).coerceAtLeast(1.1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    /**
     * Everything the pen renderers need, derived once from the **full** annotation.
     * Replay truncates per-point arrays; the mean, neutral flag, variation, total
     * length and references stay those of the complete stroke, so ink density
     * and dynamics never shift while revealing.
     */
    internal data class InkContext(
        val mean: InkResponse,
        val series: List<InkResponse>,
        val neutral: Boolean,
        val variation: Float,
        /** Full stroke length, kept when [series] is truncated for replay. */
        val totalSamples: Int,
        /**
         * Half-width multipliers computed once from the full stroke. Replay
         * slices this array; it is never recomputed from a truncated prefix.
         */
        val outlineWidths: FloatArray,
        /** Full-stroke result of [PenStrokeGeometry.useVariableWidthOutline]. */
        val variableOutline: Boolean,
        /**
         * Fountain nib factors, neighbour-averaged once over the full stroke.
         * Replay slices this array; recomputing it from a prefix retapers ink
         * that was already visible.
         */
        val fountainSmoothed: FloatArray,
        /**
         * Brush thickness envelope from the full stroke. Replay slices this
         * array; an envelope built from a truncated prefix swells/shrinks the
         * already revealed wash on every frame.
         */
        val brushEnvelope: FloatArray,
        /**
         * Central-difference speed per sample from the full stroke: page px per
          * sample for legacy input, page px/second when timestamps are valid.
         * Replay slices this array; speed dynamics never re-derive from a
         * truncated prefix.
         */
        val speeds: FloatArray,
        /** Mean of [speeds]; the pace at which speed dynamics are neutral. */
        val speedReference: Float,
        /** Full-stroke cumulative page-point arc length, sliced during replay. */
        val arcLengths: FloatArray,
        /** Last full-stroke arc length; never replaced by a replay-prefix total. */
        val totalArcLength: Float,
        /** Full-stroke segment pace used by pencil deposit (px/s when timed). */
        val segmentSpeeds: FloatArray,
        /** Full-stroke mean of [segmentSpeeds], never a prefix-local reference. */
        val segmentSpeedReference: Float,
        /** Whether this context uses valid physical px/second timestamps. */
        val timed: Boolean,
        /** Suppresses final-length-dependent end profiles on a growing preview. */
        val livePreview: Boolean,
        /**
         * Dwelling-nib ink pools from the full stroke. Replay keeps a pool
         * only once its anchor sample is revealed; its strength never changes.
         */
        val pools: List<InkDynamics.InkPool>,
        /**
         * Stroke-wide darkness for the ballpoint outline: speed-weighted so a
         * slow careful stroke reads wetter than a quick scribble. Equal to
         * `mean.flow` for every other style and for neutral ink.
         */
        val depositFlow: Float,
    )

    /**
     * Derives the whole pen context from one [inkResponseSeries] pass. The mean
     * (width/grain/flow), neutral flag and variation all describe the full stroke
     * before replay truncates [series], so ink density never shifts while a stroke
     * is revealed and a legacy uniform stroke stays byte-identical.
     */
    internal fun inkContext(
        points: List<NormalizedPoint>,
        penStyle: PenStyle,
        isHighlight: Boolean = false,
        width: Float = 1f,
        height: Float = 1f,
        timesMs: List<Long>? = null,
        livePreview: Boolean = false,
    ): InkContext {
        val series = inkResponseSeries(points, penStyle, isHighlight)
        val variation = PenStrokeGeometry.pressureVariation(points)
        val mean = meanInkResponse(series)
        val speeds = InkDynamics.strokeSpeeds(points, width, height, timesMs)
        val speedReference = InkDynamics.speedReference(speeds)
        val arcLengths = PenStrokeGeometry.strokeArcLengths(points, width, height)
        val segmentSpeeds = InkDynamics.strokeSegmentSpeeds(points, width, height, timesMs)
        val segmentSpeedReference = InkDynamics.speedReference(segmentSpeeds)
        // A ballpoint's fixed-size ball barely changes width; its pressure
        // identity is ink flow. Every other style keeps the original band.
        val minWidthFactor = if (penStyle == PenStyle.BALLPOINT && !isHighlight) {
            InkDynamics.BALLPOINT_MIN_WIDTH_FACTOR
        } else {
            MIN_PRESSURE_WIDTH_FACTOR
        }
        return InkContext(
            mean = mean,
            series = series,
            neutral = PenStrokeGeometry.isNeutralPressureStroke(points),
            variation = variation,
            totalSamples = points.size,
            outlineWidths = PenStrokeGeometry.outlineWidthSeries(
                points = points,
                series = series.map { it.width },
                variation = variation,
                baseWidth = 1f,
                legacyWidth = 1f,
                minWidthFactor = minWidthFactor,
                widthPx = width,
                heightPx = height,
                timesMs = timesMs,
                livePreview = livePreview,
            ),
            variableOutline = PenStrokeGeometry.useVariableWidthOutline(points),
            fountainSmoothed = fountainSmoothedFactors(points, width, height),
            brushEnvelope = brushEnvelope(
                points,
                livePreview,
                arcLengths,
                arcLengths.lastOrNull() ?: 0f,
                useArcLength = variation > PenStrokeGeometry.PRESSURE_VARIATION_EPSILON,
            ),
            speeds = speeds,
            speedReference = speedReference,
            arcLengths = arcLengths,
            totalArcLength = arcLengths.lastOrNull() ?: 0f,
            segmentSpeeds = segmentSpeeds,
            segmentSpeedReference = segmentSpeedReference,
            timed = InkDynamics.hasValidPointTimes(points, timesMs),
            livePreview = livePreview,
            pools = InkDynamics.dwellPools(points, width, height, variation, timesMs),
            depositFlow = if (penStyle == PenStyle.BALLPOINT && !isHighlight) {
                InkDynamics.ballpointSpeedFlow(
                    flows = series.map { it.flow },
                    speeds = speeds,
                    reference = speedReference,
                    variation = variation,
                )
            } else {
                mean.flow
            },
        )
    }

    /**
     * Identity key for a derived ink context. Points and timing lists are
     * immutable and shared across `Annotation.copy`, so identity (not structural
     * equality) is both correct and O(1) — structural equality on a 10k-point
     * stroke would defeat the purpose of the cache.
     */
    private class InkContextKey(
        val points: List<NormalizedPoint>,
        val penStyle: PenStyle,
        val isHighlight: Boolean,
        val width: Float,
        val height: Float,
        val timesMs: List<Long>?,
        val livePreview: Boolean,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InkContextKey) return false
            return points === other.points &&
                penStyle == other.penStyle &&
                isHighlight == other.isHighlight &&
                width == other.width &&
                height == other.height &&
                timesMs === other.timesMs &&
                livePreview == other.livePreview
        }

        override fun hashCode(): Int {
            var hash = System.identityHashCode(points)
            hash = 31 * hash + penStyle.hashCode()
            hash = 31 * hash + isHighlight.hashCode()
            hash = 31 * hash + width.toBits()
            hash = 31 * hash + height.toBits()
            hash = 31 * hash + System.identityHashCode(timesMs)
            hash = 31 * hash + livePreview.hashCode()
            return hash
        }
    }

    /** Bounded, thread-safe cache; the hot draw loop reuses unchanged ink. */
    private const val INK_CONTEXT_CACHE_MAX = 64
    private val inkContextCache = object :
        LinkedHashMap<InkContextKey, InkContext>(INK_CONTEXT_CACHE_MAX, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<InkContextKey, InkContext>?,
        ): Boolean = size > INK_CONTEXT_CACHE_MAX
    }

    /** Test/document-switch hook: drops all derived ink contexts. */
    internal fun clearInkContextCache() = synchronized(inkContextCache) {
        inkContextCache.clear()
    }

    /** Cache-backed entry point; pure-JVM tests use it to assert reuse. */
    internal fun inkContextCached(
        annotation: Annotation,
        width: Float = 1f,
        height: Float = 1f,
        livePreview: Boolean = false,
    ): InkContext = inkContextFor(annotation, width, height, livePreview)

    private fun inkContextFor(
        annotation: Annotation,
        width: Float,
        height: Float,
        livePreview: Boolean = false,
    ): InkContext {
        val key = InkContextKey(
            points = annotation.points,
            penStyle = annotation.penStyle,
            isHighlight = annotation.type == AnnotationType.HIGHLIGHT,
            width = width,
            height = height,
            timesMs = annotation.timesMs,
            livePreview = livePreview,
        )
        return synchronized(inkContextCache) {
            inkContextCache.getOrPut(key) {
                inkContext(
                    points = annotation.points,
                    penStyle = annotation.penStyle,
                    isHighlight = annotation.type == AnnotationType.HIGHLIGHT,
                    width = width,
                    height = height,
                    timesMs = annotation.timesMs,
                    livePreview = livePreview,
                )
            }
        }
    }

    /**
     * Fountain nib response over the whole stroke, including the neighbour
     * averaging that removes single-point spikes. Pure/JVM-safe: replay keeps
     * the prefix of this array instead of recomputing it from fewer points.
     */
    internal fun fountainSmoothedFactors(
        points: List<NormalizedPoint>,
        width: Float,
        height: Float,
    ): FloatArray {
        val nibAngle = (PI / 4.0).toFloat()
        val factors = FloatArray(points.size) { index ->
            val prev = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(points.lastIndex)]
            val dx = (next.x - prev.x) * width
            val dy = (next.y - prev.y) * height
            if (abs(dx) + abs(dy) < 0.0001f) {
                0.72f
            } else {
                0.30f + 0.95f * abs(sin(atan2(dy, dx) - nibAngle))
            }
        }
        return FloatArray(points.size) { index ->
            val a = factors[(index - 1).coerceAtLeast(0)]
            val b = factors[index]
            val c = factors[(index + 1).coerceAtMost(points.lastIndex)]
            (a + b + b + c) / 4f
        }
    }

    /**
     * Full-stroke brush thickness envelope, independent of any replay prefix.
     * A growing live preview defers this final-length-dependent envelope; the
     * committed stroke, replay and export restore the original wash shape.
     */
    internal fun brushEnvelope(
        points: List<NormalizedPoint>,
        livePreview: Boolean = false,
        arcLengths: FloatArray? = null,
        totalArcLength: Float? = null,
        useArcLength: Boolean = arcLengths != null,
    ): FloatArray {
        if (livePreview) return FloatArray(points.size) { 1f }
        val lastIndex = points.lastIndex
        val physicalArc = useArcLength && arcLengths != null && arcLengths.size == points.size &&
            (totalArcLength ?: 0f).isFinite() && (totalArcLength ?: 0f) > 0f
        return FloatArray(points.size) { index ->
            val t = if (physicalArc) {
                (arcLengths!![index] / totalArcLength!!).coerceIn(0f, 1f)
            } else {
                if (lastIndex == 0) 0.5f else index.toFloat() / lastIndex
            }
            0.38f + 0.72f * sin(PI * t).pow(0.6).toFloat()
        }
    }

    /** Per-point response from the full annotation, shared by drawing and replay. */
    internal fun annotationInkSeries(annotation: Annotation): List<InkResponse> =
        inkResponseSeries(
            annotation.points,
            annotation.penStyle,
            annotation.type == AnnotationType.HIGHLIGHT,
        )

    /**
     * Dispatches a PEN stroke. Uniform-pressure ink keeps the exact legacy path,
     * alpha and constants. Pressure-varied ballpoint keeps the same midpoint
     * centerline with local flow; fountain/brush/pencil keep their nib dynamics.
     */
    private fun drawPen(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        // Renderer choice is style-only. The full-stroke flag remains in the API
        // for compatibility, but ballpoint no longer switches to an outline.
        when (
            PenStrokeGeometry.selectPenRenderer(
                annotation.penStyle,
                ink.variableOutline,
            )
        ) {
            PenStrokeGeometry.PenRenderer.PRESSURE_OUTLINE -> {
                drawVariableWidthOutline(canvas, annotation, width, height, ink)
            }
            PenStrokeGeometry.PenRenderer.BALLPOINT -> {
                if (ink.neutral) {
                    drawStroke(canvas, annotation, width, height, baseAlpha = 255, response = ink.mean)
                } else {
                    drawBallpoint(canvas, annotation, width, height, ink.series)
                }
            }
            PenStrokeGeometry.PenRenderer.FOUNTAIN ->
                drawFountain(canvas, annotation, width, height, ink)
            PenStrokeGeometry.PenRenderer.BRUSH ->
                drawBrush(canvas, annotation, width, height, ink)
            PenStrokeGeometry.PenRenderer.PENCIL ->
                drawPencil(canvas, annotation, width, height, ink)
        }
    }

    /**
     * Original filled outline: two offset tracks plus round caps are generated by
     * [PenStrokeGeometry.variableWidthOutline] from the precomputed half-widths,
     * then smoothed with the same quadratic style as the legacy centre line. Only
     * colour, opacity and flow alpha come from the original pen semantics; neutral
     * strokes never reach this path.
     */
    private fun drawVariableWidthOutline(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        val base = (annotation.width * width).coerceAtLeast(1.1f)
        val halfWidths = FloatArray(ink.outlineWidths.size) { index ->
            (ink.outlineWidths[index] * base).coerceAtLeast(PenStrokeGeometry.MIN_INK_HALF_WIDTH_PX)
        }
        val ring = PenStrokeGeometry.variableWidthOutline(
            points = annotation.points,
            halfWidths = halfWidths,
            widthPx = width,
            heightPx = height,
            capSegments = 10,
            minHalfWidth = PenStrokeGeometry.MIN_INK_HALF_WIDTH_PX,
        )
        if (ring.size < 3) return
        val outline = if (ring.size > 1 && ring.first() == ring.last()) ring.dropLast(1) else ring
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), ink.depositFlow, min = 1)
            style = Paint.Style.FILL
        }
        canvas.drawPath(smoothClosedPath(outline, width, height), paint)
        // A real ballpoint leaves a slightly heavier deposit where the ball
        // first touches paper. Only varied ink gets the blob; the outline path
        // is never reached by neutral strokes.
        if (ink.variation > PenStrokeGeometry.PRESSURE_VARIATION_EPSILON &&
            annotation.points.isNotEmpty()
        ) {
            val first = annotation.points.first()
            val contact = Paint(paint).apply {
                alpha = scaledInkAlpha(
                    opacityAlpha(annotation),
                    ink.series.first().flow * BALLPOINT_CONTACT_GAIN,
                    min = 1,
                )
            }
            canvas.drawCircle(
                first.x * width,
                first.y * height,
                halfWidths.first() * 0.9f,
                contact,
            )
        }
    }

    /** Slightly heavier ink at the ballpoint's first contact with paper. */
    private const val BALLPOINT_CONTACT_GAIN = 1.12f

    /**
     * Darker ink blobs where the nib dwelled (fountain/brush). Pool anchors and
     * strengths come from the full stroke; replay draws a pool only once its
     * anchor sample is revealed, so already visible ink never shifts.
     */
    private fun drawDwellPools(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
        halfWidthAt: (Int) -> Float,
    ) {
        if (ink.pools.isEmpty()) return
        val baseAlpha = opacityAlpha(annotation)
        ink.pools.forEach { pool ->
            val point = annotation.points.getOrNull(pool.index) ?: return@forEach
            val flow = ink.series.getOrNull(pool.index)?.flow ?: ink.mean.flow
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = annotation.color
                alpha = scaledInkAlpha(baseAlpha, 0.30f * pool.strength * flow, min = 1)
                style = Paint.Style.FILL
            }
            val radius = (halfWidthAt(pool.index) * (1f + 0.35f * pool.strength))
                .coerceAtLeast(PenStrokeGeometry.MIN_INK_HALF_WIDTH_PX)
            canvas.drawCircle(point.x * width, point.y * height, radius, paint)
        }
    }

    /** Quadratic smoothing around a closed ring, starting at a segment midpoint. */
    private fun smoothClosedPath(
        points: List<NormalizedPoint>,
        width: Float,
        height: Float,
    ): Path {
        val path = Path()
        val count = points.size
        if (count < 3) return path
        val last = points[count - 1]
        val first = points[0]
        path.moveTo((last.x + first.x) * 0.5f * width, (last.y + first.y) * 0.5f * height)
        for (index in 0 until count) {
            val current = points[index]
            val next = points[(index + 1) % count]
            path.quadTo(
                current.x * width,
                current.y * height,
                (current.x + next.x) * 0.5f * width,
                (current.y + next.y) * 0.5f * height,
            )
        }
        path.close()
        return path
    }

    /**
     * Stroke-wide response derived from the **full** annotation points. Drawing and
     * replay both call this before any truncation so revealing a stroke cannot change
     * its ink density. Flow, width and grain are all averaged from the one smoothed
     * response series, matching `meanInkFlow` without a second O(N) pass.
     *
     * `InkResponse(1f, 1f, 1f)` is returned for empty strokes and for every uniform
     * full-pressure stroke, which keeps legacy/finger ink byte-identical. Highlight
     * annotations use the highlight profile regardless of their underlying pen style.
     */
    internal fun annotationInkResponse(annotation: Annotation): InkResponse =
        meanInkResponse(annotationInkSeries(annotation))

    /**
     * Stroke-wide mean from a single already-computed [series]. Width and grain are
     * averaged in sample order; flow reproduces `meanInkFlow` exactly (including
     * its 0..1 clamp) so neutral and legacy rendering stay bit-identical. This is
     * the only place that folds a series into a per-stroke response.
     */
    internal fun meanInkResponse(series: List<InkResponse>): InkResponse {
        if (series.isEmpty()) return InkResponse(1f, 1f, 1f)
        var width = 0f
        var grain = 0f
        var flow = 0f
        series.forEach {
            width += it.width
            grain += it.grain
            flow += it.flow
        }
        val count = series.size.toFloat()
        return InkResponse(
            width = width / count,
            flow = (flow / count).coerceIn(0f, 1f),
            grain = grain / count,
        )
    }

    /** Scales an existing alpha; neutral factors leave it untouched. */
    internal fun scaledInkAlpha(base: Int, factor: Float, min: Int = 0): Int =
        (base * factor).roundToInt().coerceIn(min, 255)

    /** Brush wash density: the legacy 0.20 halo, thinned by flow and grain. */
    internal fun brushHaloAlpha(baseAlpha: Int, flow: Float, grain: Float): Int =
        scaledInkAlpha(baseAlpha, 0.20f * flow * grain)

    // Aliases keep the established painter API while the geometry helpers own the
    // single source of truth for the legacy constants.
    internal const val PENCIL_WIDTH_FACTOR = PenStrokeGeometry.PENCIL_WIDTH_FACTOR
    internal const val PENCIL_CENTER_ALPHA = PenStrokeGeometry.PENCIL_CENTER_ALPHA
    internal const val PENCIL_SIDE_ALPHA = PenStrokeGeometry.PENCIL_SIDE_ALPHA
    internal const val PENCIL_CENTER_GRAIN = PenStrokeGeometry.PENCIL_CENTER_GRAIN
    internal const val PENCIL_SIDE_GRAIN = PenStrokeGeometry.PENCIL_SIDE_GRAIN

    /** Pencil base stroke width; neutral mean width reproduces the old 0.62 factor. */
    internal fun pencilStrokeWidth(base: Float, meanWidth: Float): Float =
        base * PENCIL_WIDTH_FACTOR * PenStrokeGeometry.pencilSegmentWidth(meanWidth)

    /** One of the three graphite passes; neutral flow reproduces 0.52/0.34/0.34. */
    internal fun pencilPassAlpha(baseAlpha: Int, pass: Int, flow: Float): Int {
        val factor = if (pass == 1) PENCIL_CENTER_ALPHA else PENCIL_SIDE_ALPHA
        return scaledInkAlpha(baseAlpha, factor * flow, min = 1)
    }

    /** Jitter amplitude for a graphite pass; neutral grain keeps the old constants. */
    internal fun pencilPassGrain(pass: Int, grain: Float): Float =
        (if (pass == 1) PENCIL_CENTER_GRAIN else PENCIL_SIDE_GRAIN) * grain

    /**
     * Constant-width single path: the highlighter at every pressure, and a
     * ballpoint pen whose samples are all neutral. Pressure only changes darkness
     * here via `meanInkFlow` of the full annotation, so light highlighter contact
     * paints a fainter overlay and neutral ink stays exactly as before.
     */
    private fun drawStroke(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        baseAlpha: Int,
        response: InkResponse,
    ) {
        val paint = basePaint(annotation, width, baseAlpha)
        paint.alpha = scaledInkAlpha(opacityAlpha(annotation, baseAlpha), response.flow)
        val points = annotation.points
        if (points.size == 1) {
            val p = points.first()
            val dot = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(p.x * width, p.y * height, paint.strokeWidth / 2f, dot)
            return
        }
        val path = smoothPath(points, width, height)
        canvas.drawPath(path, paint)
    }

    private fun smoothPath(points: List<NormalizedPoint>, width: Float, height: Float): Path {
        val path = Path()
        val first = points.first()
        path.moveTo(first.x * width, first.y * height)
        if (points.size == 2) {
            path.lineTo(points[1].x * width, points[1].y * height)
            return path
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val current = points[i]
            val midX = (prev.x + current.x) / 2f * width
            val midY = (prev.y + current.y) / 2f * height
            path.quadTo(prev.x * width, prev.y * height, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * width, last.y * height)
        return path
    }

    /** Where the smoothing path for segment `index` (1..lastIndex) begins. */
    private fun segmentStart(
        points: List<NormalizedPoint>,
        index: Int,
        width: Float,
        height: Float,
    ): Pair<Float, Float> {
        val previous = points[index - 1]
        if (index == 1) return previous.x * width to previous.y * height
        val before = points[index - 2]
        return ((before.x + previous.x) / 2f * width) to ((before.y + previous.y) / 2f * height)
    }

    /**
     * One piece of [smoothPath], split at the midpoints the legacy quadratic
     * smoothing already uses. Drawing all pieces in order reproduces that path,
     * but lets each piece carry its own alpha, width or jitter.
     */
    private fun segmentPath(
        points: List<NormalizedPoint>,
        index: Int,
        width: Float,
        height: Float,
    ): Path {
        val (startX, startY) = segmentStart(points, index, width, height)
        val previous = points[index - 1]
        val current = points[index]
        val midX = (previous.x + current.x) / 2f * width
        val midY = (previous.y + current.y) / 2f * height
        return Path().apply {
            moveTo(startX, startY)
            quadTo(previous.x * width, previous.y * height, midX, midY)
            // The legacy path finishes with one straight line to the endpoint.
            if (index == points.lastIndex) lineTo(current.x * width, current.y * height)
        }
    }

    /**
     * Ballpoint with genuine pressure variation: the nib stays a fixed width and
     * each midpoint segment uses local flow. Interior caps are butt-shaped so the
     * centerline stays continuous without per-sample blobs; only the two stroke
     * terminals receive round caps.
     */
    private fun drawBallpoint(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        series: List<InkResponse>,
    ) {
        val points = annotation.points
        val paint = basePaint(annotation, width, baseAlpha = 255).apply {
            // Interior pieces meet exactly at midpoint boundaries. Round caps are
            // reserved for the two stroke terminals, avoiding a blob per sample.
            strokeCap = Paint.Cap.BUTT
        }
        val baseAlpha = opacityAlpha(annotation, 255)
        if (points.size == 1) {
            val first = points.first()
            paint.alpha = scaledInkAlpha(baseAlpha, series.first().flow, min = 1)
            val dot = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(first.x * width, first.y * height, paint.strokeWidth / 2f, dot)
            return
        }
        for (index in 1 until points.size) {
            val flow = (series[index - 1].flow + series[index].flow) * 0.5f
            paint.alpha = scaledInkAlpha(baseAlpha, flow, min = 1)
            canvas.drawPath(segmentPath(points, index, width, height), paint)
        }
        val startFlow = series.first().flow
        val endFlow = series.last().flow
        listOf(
            points.first() to startFlow,
            points.last() to endFlow,
        ).forEach { (point, flow) ->
            val cap = Paint(paint).apply {
                style = Paint.Style.FILL
                alpha = scaledInkAlpha(baseAlpha, flow, min = 1)
            }
            canvas.drawCircle(point.x * width, point.y * height, paint.strokeWidth / 2f, cap)
        }
    }

    /**
     * Calligraphic pen: a filled ribbon whose width follows the angle between the
     * movement direction and a fixed nib angle. Strokes along the nib stay thin,
     * strokes across it swell, which produces the familiar italic-nib feel without
     * needing a custom font or texture.
     */
    private fun drawFountain(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1.1f)
        val pressure = ink.series.map { it.width }
        val response = ink.mean
        val variation = ink.variation
        val totalSamples = ink.totalSamples
        if (points.size == 1) {
            drawPenDot(
                canvas,
                annotation,
                points.first(),
                width,
                height,
                base * 0.52f * pressure.first(),
                flow = response.flow,
            )
            return
        }
        // Precomputed once from the full stroke (nib factors plus neighbour
        // averaging). Replay slices the same array, so revealing a prefix can
        // never re-smooth the ink that was already visible.
        val smoothed = ink.fountainSmoothed
        // A real nib lifts off at the start and end of a stroke, but only when the
        // stylus actually varied pressure. `fountainTaper` is exactly 1f for neutral
        // ink, so legacy strokes keep the original ribbon and round end caps.
        // Normalize by the full stroke length so revealing a prefix cannot retaper
        // ink that was already visible.
        fun halfWidth(index: Int): Float {
            val t = inkEndPosition(ink, index, totalSamples)
            // A fountain nib starves on a fast pull and wets up on a slow one;
            // the factor is exactly 1f for neutral ink and at the mean pace.
            val speed = InkDynamics.velocityWidthFactor(
                speed = ink.speeds.getOrElse(index) { 0f },
                reference = ink.speedReference,
                influence = InkDynamics.FOUNTAIN_SPEED_INFLUENCE,
                variation = variation,
            )
            // A live stroke has no committed end yet. Keep its already-dry nib
            // width independent of the growing final-length taper; replay and
            // export use both full-stroke end profiles.
            val taper = if (ink.livePreview) 1f else PenStrokeGeometry.fountainTaper(t, variation)
            val tip = if (ink.livePreview) 1f else InkQuality.nibEndProfile(t, variation)
            return base * smoothed[index] * pressure[index] * 0.5f * taper * tip * speed
        }
        val path = ribbonPath(
            points,
            width,
            height,
            smooth = variation > PenStrokeGeometry.PRESSURE_VARIATION_EPSILON,
        ) { index -> halfWidth(index) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), response.flow)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            halfWidth(0),
            paint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            halfWidth(points.lastIndex),
            paint,
        )
        drawDwellPools(canvas, annotation, width, height, ink, ::halfWidth)
    }

    /**
     * Soft brush: a broad translucent pass under a tapered ink core. The envelope
     * thins the ends and swells the middle while the halo keeps the edge fuzzy.
     */
    private fun drawBrush(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1.1f)
        val pressure = ink.series.map { it.width }
        val response = ink.mean
        if (points.size == 1) {
            drawPenDot(
                canvas,
                annotation,
                points.first(),
                width,
                height,
                base * 0.58f * pressure.first(),
                flow = response.flow,
            )
            return
        }
        // Full-stroke envelope, sliced by replay; a prefix-local envelope would
        // change the thickness of every already revealed point on each frame.
        val envelope = ink.brushEnvelope
        // Light pressure thins and fades the wash as well as the core, giving a
        // dry-brush look; at neutral flow/grain this is the old 0.20 halo verbatim.
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = brushHaloAlpha(opacityAlpha(annotation), response.flow, response.grain)
            style = Paint.Style.FILL
        }
        val halo = ribbonPath(
            points,
            width,
            height,
            smooth = ink.variation > PenStrokeGeometry.PRESSURE_VARIATION_EPSILON,
        ) { index ->
            base * envelope[index] * pressure[index] * 0.80f * response.grain
        }
        canvas.drawPath(halo, haloPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), response.flow)
            style = Paint.Style.FILL
        }
        // A fast brush flick leaves a thin dry core inside the unchanged halo;
        // a slow pull swells the core wet. Neutral ink keeps the old 0.5 core.
        fun coreHalfWidth(index: Int): Float {
            val speed = InkDynamics.velocityWidthFactor(
                speed = ink.speeds.getOrElse(index) { 0f },
                reference = ink.speedReference,
                influence = InkDynamics.BRUSH_SPEED_INFLUENCE,
                variation = ink.variation,
            )
            return base * envelope[index] * pressure[index] * 0.5f * speed
        }
        val core = ribbonPath(
            points,
            width,
            height,
            smooth = ink.variation > PenStrokeGeometry.PRESSURE_VARIATION_EPSILON,
        ) { index -> coreHalfWidth(index) }
        canvas.drawPath(core, corePaint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            coreHalfWidth(0),
            corePaint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            coreHalfWidth(points.lastIndex),
            corePaint,
        )
        drawDwellPools(canvas, annotation, width, height, ink, ::coreHalfWidth)
    }

    /**
     * Deterministic graphite wobble. The centre pass (1) stays on the exact stroke
     * line. [grainAt] supplies the per-point grain factor: full grain reproduces the
     * legacy index-sine wave verbatim, while genuinely light grain uses the arc-length
     * seeded wave from [PenStrokeGeometry.graphiteJitterByArc] instead.
     */
    private fun pencilJitter(
        points: List<NormalizedPoint>,
        width: Float,
        height: Float,
        pass: Int,
        grainAt: (Int) -> Float,
        arcLengths: FloatArray? = null,
    ): List<NormalizedPoint> {
        if (pass == 1) return points
        val arcs = arcLengths ?: PenStrokeGeometry.strokeArcLengths(points, width, height)
        return points.mapIndexed { index, point ->
            val grain = grainAt(index)
            val wave = PenStrokeGeometry.graphiteJitterByArc(
                index = index,
                arcLength = arcs[index],
                pass = pass,
                grain = grain,
                legacyPhase = index * 1.7 + pass * 4.3,
            )
            val amplitude = pencilPassGrain(pass, grain)
            point.copy(
                x = (point.x + wave * amplitude).coerceIn(0f, 1f),
                y = (point.y + wave * amplitude * 0.65f).coerceIn(0f, 1f),
            )
        }
    }

    /**
     * One varied-pencil segment's complete draw decision. Keeping this pure lets
     * JVM regressions compare a replay prefix with the full-stroke plan without
     * an Android Canvas. Arc phase, speed reference and tip position always come
     * from the full [ink] context, never from the visible prefix.
     */
    internal data class PencilSegmentPlan(
        val width: Float,
        val alpha: Int,
        val arcLength: Float,
        val deposit: Float,
        val depositGain: Float,
        val tip: Float,
        val speedFactor: Float,
    )

    internal fun inkEndPosition(ink: InkContext, index: Int, totalSamples: Int): Float =
        if (ink.totalArcLength > 0f) {
            ((ink.arcLengths.getOrNull(index) ?: 0f) / ink.totalArcLength).coerceIn(0f, 1f)
        } else {
            index.toFloat() / (totalSamples - 1).coerceAtLeast(1).toFloat()
        }

    internal fun pencilSegmentPlan(
        ink: InkContext,
        index: Int,
        pass: Int,
        base: Float,
        baseAlpha: Int,
        pressure: Float,
    ): PencilSegmentPlan {
        val totalSamples = ink.totalSamples.coerceAtLeast(2)
        val safeIndex = index.coerceIn(1, totalSamples - 1)
        val local = ink.series.getOrNull(safeIndex) ?: ink.mean
        val t = inkEndPosition(ink, safeIndex, totalSamples)
        val tip = if (ink.livePreview) 1f else InkQuality.nibEndProfile(t, ink.variation)
        val speedFactor = InkDynamics.velocityWidthFactor(
            speed = ink.speeds.getOrElse(safeIndex) { 0f },
            reference = ink.speedReference,
            influence = InkDynamics.PENCIL_SPEED_INFLUENCE,
            variation = ink.variation,
        )
        val arc = ink.arcLengths.getOrNull(safeIndex) ?: ink.totalArcLength
        val previousArc = ink.arcLengths.getOrNull(safeIndex - 1) ?: 0f
        val segmentSpeed = ink.segmentSpeeds.getOrNull(safeIndex)
            ?: (arc - previousArc).coerceAtLeast(0f)
        val wave = PenStrokeGeometry.graphiteJitterByArc(
            index = safeIndex,
            arcLength = arc,
            pass = pass,
            grain = local.grain,
            legacyPhase = safeIndex * 2.3 + 7.1,
        )
        val deposit = PenStrokeGeometry.graphiteDeposit(arc, pass, local.grain, wave)
        // Timed deposit uses the same central/one-sided physical velocity and
        // full-stroke reference as width. Untimed input keeps the historical
        // per-segment distance reference exactly.
        val depositSpeed = if (ink.timed) {
            ink.speeds.getOrElse(safeIndex) { 0f }
        } else {
            segmentSpeed
        }
        val depositReference = if (ink.timed) ink.speedReference else ink.segmentSpeedReference
        val gain = InkQuality.depositGain(
            speed = depositSpeed,
            reference = depositReference,
            pressure = pressure,
            variation = ink.variation,
        )
        return PencilSegmentPlan(
            width = pencilStrokeWidth(base, local.width) * tip * speedFactor,
            alpha = scaledInkAlpha(
                baseAlpha,
                PenStrokeGeometry.pencilSegmentAlpha(pass, local.flow) * deposit * gain,
                min = 1,
            ),
            arcLength = arc,
            deposit = deposit,
            depositGain = gain,
            tip = tip,
            speedFactor = speedFactor,
        )
    }

    internal fun pencilSegmentPlans(
        ink: InkContext,
        points: List<NormalizedPoint>,
        pass: Int,
        base: Float,
        baseAlpha: Int,
        revealedSamples: Int = ink.totalSamples,
    ): List<PencilSegmentPlan> {
        if (ink.totalSamples < 2) return emptyList()
        val count = revealedSamples.coerceIn(1, ink.totalSamples)
        return (1 until count).map { index ->
            pencilSegmentPlan(
                ink = ink,
                index = index,
                pass = pass,
                base = base,
                baseAlpha = baseAlpha,
                pressure = points.getOrNull(index)?.pressure ?: 1f,
            )
        }
    }

    /**
     * Pencil: three thin, slightly jittered passes at low alpha. Uniform-pressure
     * ink keeps the exact old three whole-path passes (width 0.62, alphas
     * 0.52/0.34/0.34 and the legacy grain constants). Varied ink is drawn segment
     * by segment, with local width, darkness and jitter taken from the full-stroke
     * response series.
     */
    private fun drawPencil(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        val response = ink.mean
        val series = ink.series
        val neutral = ink.neutral
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1f)
        if (points.size == 1) {
            val local = if (neutral) response else series.first()
            drawPenDot(
                canvas,
                annotation,
                points.first(),
                width,
                height,
                base * 0.34f * local.width,
                flow = local.flow,
            )
            return
        }
        val alpha = opacityAlpha(annotation)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            style = Paint.Style.STROKE
            strokeWidth = pencilStrokeWidth(base, response.width)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val offsets = floatArrayOf(
            PenStrokeGeometry.graphitePassOffset(0, response.grain),
            PenStrokeGeometry.graphitePassOffset(1, response.grain),
            PenStrokeGeometry.graphitePassOffset(2, response.grain),
        )
        offsets.forEachIndexed { pass, offset ->
            canvas.save()
            canvas.translate(offset, offset * 0.35f)
            if (neutral) {
                paint.strokeWidth = pencilStrokeWidth(base, response.width)
                paint.alpha = pencilPassAlpha(alpha, pass, response.flow)
                val jittered = pencilJitter(points, width, height, pass, grainAt = { response.grain })
                canvas.drawPath(smoothPath(jittered, width, height), paint)
            } else {
                val jittered = pencilJitter(
                    points,
                    width,
                    height,
                    pass,
                    grainAt = { index -> series[index].grain },
                    arcLengths = ink.arcLengths,
                )
                for (index in 1 until points.size) {
                    val plan = pencilSegmentPlan(
                        ink = ink,
                        index = index,
                        pass = pass,
                        base = base,
                        baseAlpha = alpha,
                        pressure = points[index].pressure,
                    )
                    paint.strokeWidth = plan.width
                    paint.alpha = plan.alpha
                    canvas.drawPath(segmentPath(jittered, index, width, height), paint)
                }
            }
            canvas.restore()
        }
    }

    /** Builds a closed ribbon around a centre line using per-vertex half-widths. */
    private fun ribbonPath(
        points: List<NormalizedPoint>,
        width: Float,
        height: Float,
        smooth: Boolean,
        halfWidthAt: (Int) -> Float,
    ): Path {
        val count = points.size
        val xs = FloatArray(count) { points[it].x * width }
        val ys = FloatArray(count) { points[it].y * height }
        val leftX = FloatArray(count)
        val leftY = FloatArray(count)
        val rightX = FloatArray(count)
        val rightY = FloatArray(count)
        for (i in 0 until count) {
            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 1).coerceAtMost(count - 1)
            var dx = xs[next] - xs[prev]
            var dy = ys[next] - ys[prev]
            var length = hypot(dx, dy)
            if (length < 0.0001f) {
                dx = 1f
                dy = 0f
                length = 1f
            }
            val nx = -dy / length
            val ny = dx / length
            val half = halfWidthAt(i).coerceAtLeast(PenStrokeGeometry.MIN_INK_HALF_WIDTH_PX)
            leftX[i] = xs[i] + nx * half
            leftY[i] = ys[i] + ny * half
            rightX[i] = xs[i] - nx * half
            rightY[i] = ys[i] - ny * half
        }
        return Path().apply {
            moveTo(leftX[0], leftY[0])
            if (smooth) {
                for (i in 1 until count) {
                    val midX = (leftX[i - 1] + leftX[i]) * 0.5f
                    val midY = (leftY[i - 1] + leftY[i]) * 0.5f
                    quadTo(leftX[i - 1], leftY[i - 1], midX, midY)
                }
                lineTo(leftX[count - 1], leftY[count - 1])
                lineTo(rightX[count - 1], rightY[count - 1])
                for (i in count - 2 downTo 0) {
                    val midX = (rightX[i + 1] + rightX[i]) * 0.5f
                    val midY = (rightY[i + 1] + rightY[i]) * 0.5f
                    quadTo(rightX[i + 1], rightY[i + 1], midX, midY)
                }
                lineTo(rightX[0], rightY[0])
            } else {
                for (i in 1 until count) lineTo(leftX[i], leftY[i])
                for (i in count - 1 downTo 0) lineTo(rightX[i], rightY[i])
            }
            close()
        }
    }

    private fun drawPenDot(
        canvas: Canvas,
        annotation: Annotation,
        point: NormalizedPoint,
        width: Float,
        height: Float,
        radius: Float,
        flow: Float = 1f,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), flow)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(point.x * width, point.y * height, radius, paint)
    }

    private fun drawRect(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = basePaint(annotation, width)
        canvas.drawRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            paint,
        )
    }

    private fun drawEllipse(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = basePaint(annotation, width)
        val rect = RectF(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
        )
        canvas.drawOval(rect, paint)
    }

    /**
     * UNDERLINE / STRIKEOUT share the same geometry; the semantic type is preserved
     * so the annotation list can label them differently.
     */
    private fun drawMarkupLine(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val points = annotation.points
        if (points.size < 2) return
        val paint = basePaint(annotation, width)
        if (points.size == 2) {
            canvas.drawLine(
                points[0].x * width,
                points[0].y * height,
                points[1].x * width,
                points[1].y * height,
                paint,
            )
            return
        }
        val path = Path().apply {
            moveTo(points[0].x * width, points[0].y * height)
            for (i in 1 until points.size) {
                lineTo(points[i].x * width, points[i].y * height)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawStraightLine(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points.first()
        val b = annotation.points.last()
        canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, basePaint(annotation, width))
    }

    private fun drawArrow(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val x1 = a.x * width
        val y1 = a.y * height
        val x2 = b.x * width
        val y2 = b.y * height
        val paint = basePaint(annotation, width)
        canvas.drawLine(x1, y1, x2, y2, paint)

        val angle = atan2(y2 - y1, x2 - x1)
        val length = hypot(x2 - x1, y2 - y1).coerceAtLeast(1f)
        val head = (paint.strokeWidth * 4.2f).coerceAtMost(length * 0.45f)
        val spread = (Math.PI / 7.0).toFloat()
        val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(x2, y2)
            lineTo(x2 - head * cos(angle - spread), y2 - head * sin(angle - spread))
            lineTo(x2 - head * cos(angle + spread), y2 - head * sin(angle + spread))
            close()
        }
        canvas.drawPath(path, headPaint)
    }

    private fun drawText(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.text.isBlank()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            textSize = (annotation.fontSize * height).coerceAtLeast(8f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val lineHeight = paint.textSize * 1.3f
        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())
        var baseline = paint.textSize
        annotation.text.split('\n').forEach { line ->
            canvas.drawText(line, 0f, baseline, paint)
            baseline += lineHeight
        }
        canvas.restore()
    }

    private fun drawStamp(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val label = annotation.stamp.ifBlank { "APPROVED" }.replace('\n', ' ').trim()
        if (label.isEmpty()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            style = Paint.Style.FILL
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }

        var textSize = (annotation.fontSize * height).coerceAtLeast(10f)
        textSize = min(textSize, height * 0.16f)
        paint.textSize = textSize
        var textWidth = paint.measureText(label)
        val maxTextWidth = width * 0.72f
        if (textWidth > maxTextWidth && textWidth > 0f) {
            textSize *= maxTextWidth / textWidth
            paint.textSize = textSize
            textWidth = paint.measureText(label)
        }

        val boxWidth = textWidth + textSize * 1.2f
        val boxHeight = textSize * 1.9f
        val radius = boxHeight * 0.24f

        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())

        val fill = Paint(paint).apply {
            style = Paint.Style.FILL
            alpha = opacityAlpha(annotation, baseAlpha = 36)
        }
        canvas.drawRoundRect(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f, radius, radius, fill)

        val border = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = stampBorderWidth(annotation.width, width, boxHeight)
            alpha = opacityAlpha(annotation)
        }
        canvas.drawRoundRect(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f, radius, radius, border)

        paint.alpha = opacityAlpha(annotation)
        val baseline = -(paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, 0f, baseline, paint)
        canvas.restore()
    }

    private fun drawSignature(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size > 1) {
            val paint = basePaint(annotation, width).apply {
                strokeWidth = (annotation.width * width * 1.4f).coerceAtLeast(1.6f)
            }
            canvas.drawPath(smoothPath(annotation.points, width, height), paint)
            return
        }
        val signature = annotation.text.ifBlank { annotation.stamp }
        if (signature.isBlank()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            typeface = Typeface.create("cursive", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        var textSize = (annotation.fontSize * height).coerceAtLeast(12f)
        textSize = min(textSize, height * 0.14f)
        paint.textSize = textSize
        var textWidth = paint.measureText(signature)
        val maxTextWidth = width * 0.8f
        if (textWidth > maxTextWidth && textWidth > 0f) {
            textSize *= maxTextWidth / textWidth
            paint.textSize = textSize
            textWidth = paint.measureText(signature)
        }

        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())
        val baseline = textSize
        canvas.drawText(signature, 0f, baseline, paint)
        val underline = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (annotation.width * width * 1.2f).coerceAtLeast(1.2f)
            alpha = opacityAlpha(annotation, baseAlpha = 190)
        }
        val underlineY = baseline + textSize * 0.22f
        canvas.drawLine(0f, underlineY, textWidth, underlineY, underline)
        canvas.restore()
    }

    /**
     * The blackout must stay fully opaque: the raster export flattens it into the page,
     * so a translucent fill would leak the underlying text.
     */
    private fun drawRedact(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 255
        }
        canvas.drawRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            paint,
        )
    }

    private val COVER_TEXT_ON_LIGHT = 0xFF1A1A1A.toInt()
    private val COVER_TEXT_ON_DARK = 0xFFFFFFFF.toInt()

    /**
     * Visual text replacement: an opaque paper-coloured rectangle followed by the
     * replacement text in a contrasting colour. This is a pure overlay drawn by the
     * same painter in the editor and in flattened export; it never modifies PDF text
     * bytes, and the covered original words remain in the content stream.
     *
     * The rectangle is axis-aligned in display coordinates (the geometry produced by
     * `PdfTextEdit`), so no text rotation is applied. The replacement font shrinks to
     * fit the cover when it is longer than the original selection.
     */
    private fun drawCoverText(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2 || annotation.text.isBlank()) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val left = minOf(a.x, b.x) * width
        val right = maxOf(a.x, b.x) * width
        val top = minOf(a.y, b.y) * height
        val bottom = maxOf(a.y, b.y) * height
        if (right <= left || bottom <= top) return

        val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, top, right, bottom, cover)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightColor(annotation.color)) COVER_TEXT_ON_LIGHT else COVER_TEXT_ON_DARK
            // Keep the replacement in step with the cover during replay fades.
            alpha = opacityAlpha(annotation)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val boxWidth = right - left
        val boxHeight = bottom - top
        val padding = min(boxWidth, boxHeight) * COVER_PADDING_FRACTION
        val maxWidth = (boxWidth - padding * 2f).coerceAtLeast(1f)
        val maxHeight = (boxHeight - padding * 2f).coerceAtLeast(1f)
        val lines = annotation.text.replace('\r', '\n').split('\n')

        textPaint.textSize = (annotation.fontSize * height).coerceAtLeast(8f)
        val longest = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val proportionalHeight = textPaint.textSize * LINE_HEIGHT_FACTOR * lines.size
        val scale = min(
            if (longest > 0f) (maxWidth / longest).coerceAtMost(1f) else 1f,
            (maxHeight / proportionalHeight).coerceAtMost(1f),
        )
        textPaint.textSize = (textPaint.textSize * scale).coerceAtLeast(6f)

        val lineHeight = textPaint.textSize * LINE_HEIGHT_FACTOR
        val blockHeight = lineHeight * lines.size
        var baseline = top + padding + (maxHeight - blockHeight) / 2f - textPaint.ascent()
        lines.forEach { line ->
            canvas.drawText(line, left + padding, baseline, textPaint)
            baseline += lineHeight
        }
    }

    /** Perceived brightness of the cover colour, used to pick readable text ink. */
    private fun isLightColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299f * r + 0.587f * g + 0.114f * b) > 150f
    }

    private const val COVER_PADDING_FRACTION = 0.12f
    private const val LINE_HEIGHT_FACTOR = 1.22f
}
