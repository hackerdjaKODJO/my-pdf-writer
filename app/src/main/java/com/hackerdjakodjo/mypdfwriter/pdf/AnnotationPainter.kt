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
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.inkResponseSeries
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
 * callers that want the normal result keep the default of `1f`.
 */
object AnnotationPainter {

    fun draw(
        canvas: Canvas,
        annotations: List<Annotation>,
        width: Float,
        height: Float,
        progress: (Annotation) -> Float = { 1f },
    ) {
        annotations.forEach { drawOne(canvas, it, width, height, progress(it)) }
    }

    fun drawOne(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float = 1f,
    ) {
        if (annotation.points.isEmpty()) return
        val reveal = fraction.coerceIn(0f, 1f)
        if (reveal <= 0f) return
        if (reveal < 1f) {
            drawPartial(canvas, annotation, width, height, reveal)
            return
        }
        when (annotation.type) {
            AnnotationType.PEN -> drawPen(
                canvas,
                annotation,
                width,
                height,
                inkContextFor(annotation, width, height),
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
     * Replay rendering for `0 < fraction < 1`. Strokes expose the first
     * `ceil(points * fraction)` samples, two-point shapes grow their endpoint,
     * and text-like annotations fade in. Fraction `1f` still uses the exact
     * original rendering path above.
     */
    private fun drawPartial(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float,
    ) {
        when (annotation.type) {
            AnnotationType.PEN, AnnotationType.HIGHLIGHT -> {
                // Everything derived from the stroke comes from the full
                // annotation, not the revealed prefix: ink density, outline
                // widths, fountain nib factors and the brush envelope. Replay
                // only slices those arrays so already revealed ink never shifts.
                val ink = inkContextFor(annotation, width, height)
                val points = revealedPoints(annotation.points, fraction)
                val partial = annotation.copy(points = points)
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
                    revealedPoints(annotation.points, fraction)
                }
                drawMarkupLine(canvas, annotation.copy(points = points), width, height)
            }

            AnnotationType.TEXT -> drawText(canvas, annotation.faded(fraction), width, height)
            AnnotationType.STAMP -> drawStamp(canvas, annotation.faded(fraction), width, height)
            // Text-like reveal: fade the whole cover + replacement in together.
            AnnotationType.COVER_TEXT -> drawCoverText(canvas, annotation.faded(fraction), width, height)

            AnnotationType.SIGNATURE -> {
                if (annotation.points.size > 1) {
                    val partial = annotation.copy(points = revealedPoints(annotation.points, fraction))
                    drawSignature(canvas, partial, width, height)
                } else {
                    drawSignature(canvas, annotation.faded(fraction), width, height)
                }
            }
        }
    }

    private fun revealedPoints(points: List<NormalizedPoint>, fraction: Float): List<NormalizedPoint> {
        val count = ceil(points.size * fraction).toInt().coerceIn(1, points.size)
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
     * Replay truncates only [series]; the mean, neutral flag and pressure variation
     * stay those of the complete stroke, so ink density never shifts while revealing.
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
    ): InkContext {
        val series = inkResponseSeries(points, penStyle, isHighlight)
        val variation = PenStrokeGeometry.pressureVariation(points)
        return InkContext(
            mean = meanInkResponse(series),
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
            ),
            variableOutline = PenStrokeGeometry.useVariableWidthOutline(points),
            fountainSmoothed = fountainSmoothedFactors(points, width, height),
            brushEnvelope = brushEnvelope(points),
        )
    }

    /**
     * Identity key for a derived ink context. Points lists are immutable and
     * shared across `Annotation.copy`, so identity (not structural equality)
     * is both correct and O(1) — structural equality on a 10k-point stroke
     * would defeat the purpose of the cache.
     */
    private class InkContextKey(
        val points: List<NormalizedPoint>,
        val penStyle: PenStyle,
        val isHighlight: Boolean,
        val width: Float,
        val height: Float,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InkContextKey) return false
            return points === other.points &&
                penStyle == other.penStyle &&
                isHighlight == other.isHighlight &&
                width == other.width &&
                height == other.height
        }

        override fun hashCode(): Int {
            var hash = System.identityHashCode(points)
            hash = 31 * hash + penStyle.hashCode()
            hash = 31 * hash + isHighlight.hashCode()
            hash = 31 * hash + width.toBits()
            hash = 31 * hash + height.toBits()
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
    @Synchronized
    internal fun clearInkContextCache() = inkContextCache.clear()

    /** Cache-backed entry point; pure-JVM tests use it to assert reuse. */
    internal fun inkContextCached(
        annotation: Annotation,
        width: Float = 1f,
        height: Float = 1f,
    ): InkContext = inkContextFor(annotation, width, height)

    private fun inkContextFor(
        annotation: Annotation,
        width: Float,
        height: Float,
    ): InkContext {
        val key = InkContextKey(
            points = annotation.points,
            penStyle = annotation.penStyle,
            isHighlight = annotation.type == AnnotationType.HIGHLIGHT,
            width = width,
            height = height,
        )
        return synchronized(inkContextCache) {
            inkContextCache.getOrPut(key) {
                inkContext(
                    points = annotation.points,
                    penStyle = annotation.penStyle,
                    isHighlight = annotation.type == AnnotationType.HIGHLIGHT,
                    width = width,
                    height = height,
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

    /** Full-stroke brush thickness envelope, independent of any replay prefix. */
    internal fun brushEnvelope(points: List<NormalizedPoint>): FloatArray {
        val lastIndex = points.lastIndex
        return FloatArray(points.size) { index ->
            val t = if (lastIndex == 0) 0.5f else index.toFloat() / lastIndex
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
     * alpha and constants; only strokes with genuine stylus variation take the
     * new per-segment dynamics.
     */
    private fun drawPen(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        ink: InkContext,
    ) {
        // The decision uses the FULL stroke's flag carried by InkContext, never
        // a replay prefix, and only the default ballpoint uses the generic
        // outline. Fountain/brush/pencil keep their own nib dynamics even when
        // the stylus varied pressure — that is exactly their purpose.
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
                drawPencil(canvas, annotation, width, height, ink.mean, ink.series, ink.neutral)
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
            alpha = scaledInkAlpha(opacityAlpha(annotation), ink.mean.flow, min = 1)
            style = Paint.Style.FILL
        }
        canvas.drawPath(smoothClosedPath(outline, width, height), paint)
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
     * Ballpoint with genuine pressure variation: the nib stays a fixed width, but
     * every short smooth segment uses the local flow instead of the stroke-wide
     * mean. At very light contact some segments give up entirely, leaving only a
     * tiny faint dot where the nib briefly touched the paper.
     */
    private fun drawBallpoint(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        series: List<InkResponse>,
    ) {
        val points = annotation.points
        val paint = basePaint(annotation, width, baseAlpha = 255)
        val baseAlpha = opacityAlpha(annotation, 255)
        if (points.size == 1) {
            val first = points.first()
            paint.alpha = scaledInkAlpha(baseAlpha, series.first().flow)
            val dot = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(first.x * width, first.y * height, paint.strokeWidth / 2f, dot)
            return
        }
        for (index in 1 until points.size) {
            // The segment straddles two samples; averaging their full-stroke flows
            // keeps the fade continuous and replay-stable at the reveal boundary.
            val flow = (series[index - 1].flow + series[index].flow) * 0.5f
            if (PenStrokeGeometry.ballpointSkip(index, flow)) {
                val (startX, startY) = segmentStart(points, index, width, height)
                val trace = Paint(paint).apply {
                    style = Paint.Style.FILL
                    alpha = scaledInkAlpha(
                        baseAlpha,
                        PenStrokeGeometry.ballpointDotAlpha(flow) / 255f,
                        min = 1,
                    )
                }
                canvas.drawCircle(startX, startY, paint.strokeWidth * 0.35f, trace)
                continue
            }
            paint.alpha = scaledInkAlpha(baseAlpha, flow, min = 1)
            canvas.drawPath(segmentPath(points, index, width, height), paint)
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
        val tSpan = (totalSamples - 1).coerceAtLeast(1).toFloat()
        fun halfWidth(index: Int): Float {
            val t = index.toFloat() / tSpan
            return base * smoothed[index] * pressure[index] * 0.5f *
                PenStrokeGeometry.fountainTaper(t, variation)
        }
        val path = ribbonPath(points, width, height) { index -> halfWidth(index) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), response.flow)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            base * smoothed.first() * pressure.first() * 0.5f *
                PenStrokeGeometry.fountainTaper(0f, variation),
            paint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            base * smoothed.last() * pressure.last() * 0.5f *
                PenStrokeGeometry.fountainTaper(1f, variation),
            paint,
        )
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
        val halo = ribbonPath(points, width, height) { index ->
            base * envelope[index] * pressure[index] * 0.80f * response.grain
        }
        canvas.drawPath(halo, haloPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = scaledInkAlpha(opacityAlpha(annotation), response.flow)
            style = Paint.Style.FILL
        }
        val core = ribbonPath(points, width, height) { index ->
            base * envelope[index] * pressure[index] * 0.5f
        }
        canvas.drawPath(core, corePaint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            base * envelope.first() * pressure.first() * 0.5f,
            corePaint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            base * envelope.last() * pressure.last() * 0.5f,
            corePaint,
        )
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
    ): List<NormalizedPoint> {
        if (pass == 1) return points
        val arcs = FloatArray(points.size)
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val dx = (current.x - previous.x) * width
            val dy = (current.y - previous.y) * height
            arcs[index] = arcs[index - 1] + hypot(dx, dy)
        }
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
        response: InkResponse,
        series: List<InkResponse>,
        neutral: Boolean,
    ) {
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
                val jittered = pencilJitter(points, width, height, pass) { response.grain }
                canvas.drawPath(smoothPath(jittered, width, height), paint)
            } else {
                val jittered = pencilJitter(points, width, height, pass) { index ->
                    series[index].grain
                }
                val arcs = FloatArray(points.size)
                for (index in 1 until points.size) {
                    val previous = points[index - 1]
                    val current = points[index]
                    arcs[index] = arcs[index - 1] + hypot(
                        (current.x - previous.x) * width,
                        (current.y - previous.y) * height,
                    )
                }
                for (index in 1 until points.size) {
                    val local = series[index]
                    paint.strokeWidth = pencilStrokeWidth(base, local.width)
                    val wave = PenStrokeGeometry.graphiteJitterByArc(
                        index = index,
                        arcLength = arcs[index],
                        pass = pass,
                        grain = local.grain,
                        legacyPhase = index * 2.3 + 7.1,
                    )
                    val deposit = PenStrokeGeometry.graphiteDeposit(
                        arcs[index], pass, local.grain, wave,
                    )
                    paint.alpha = scaledInkAlpha(
                        alpha,
                        PenStrokeGeometry.pencilSegmentAlpha(pass, local.flow) * deposit,
                        min = 1,
                    )
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
            for (i in 1 until count) lineTo(leftX[i], leftY[i])
            for (i in count - 1 downTo 0) lineTo(rightX[i], rightY[i])
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
            strokeWidth = (annotation.width * width * 1.4f).coerceIn(1.5f, boxHeight * 0.16f)
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
