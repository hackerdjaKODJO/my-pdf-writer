package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure, platform-independent policy helpers for pressure-aware pen ink.
 *
 * Nothing here touches Android `Canvas`, `Paint` or `Path`: it only maps stroke
 * samples and normalized positions to bounded multipliers so the editor preview
 * and the exported PDF make identical decisions. Every helper is deterministic,
 * finite for every input (including NaN/Infinity), and an exact identity for a
 * neutral, uniform-pressure stroke. That last property is what keeps legacy and
 * finger ink byte-for-byte identical to the pre-pressure renderer.
 */
object PenStrokeGeometry {

    /** Legacy pencil constants, shared by the whole-path and per-segment paths. */
    const val PENCIL_WIDTH_FACTOR = 0.62f
    const val PENCIL_CENTER_ALPHA = 0.52f
    const val PENCIL_SIDE_ALPHA = 0.34f
    const val PENCIL_CENTER_GRAIN = 0.0004f
    const val PENCIL_SIDE_GRAIN = 0.0011f

    /** Below this local flow a ballpoint is considered to lack contact. */
    const val BALLPOINT_LIGHT_FLOW = 0.55f

    /** Pressure ranges at or below this are treated as uniform (no nib taper). */
    const val PRESSURE_VARIATION_EPSILON = 0.01f

    /** Fraction of a stroke (from each end) over which a fountain nib tapers. */
    const val FOUNTAIN_TAPER_SPAN = 0.30f

    /** Default fraction of a variable-width stroke used by each end envelope. */
    const val OUTLINE_END_SPAN = 0.28f

    /** Largest number of arc segments accepted for one round outline cap. */
    const val MAX_OUTLINE_CAP_SEGMENTS = 24

    /** Lateral pencil-pass offsets at full grain: centre, side, side. */
    const val GRAPHITE_CENTER_OFFSET = 0f
    const val GRAPHITE_SIDE_OFFSET = 0.55f

    /** How strongly speed may modify a real pressure sample. */
    private const val SIMULATED_SPEED_INFLUENCE = 0.65f

    /**
     * True when a whole stroke carries the legacy neutral pressure (every sample
     * fully pressed). Empty strokes are neutral too; `NormalizedPoint` clamps
     * pressure into 0..1, so `>= 1f` is an exact identity for those samples.
     */
    fun isNeutralPressureStroke(points: List<NormalizedPoint>): Boolean =
        points.all { it.pressure >= 1f }

    /**
     * Normalized pressure range of a stroke, in 0..1. Zero means the samples are
     * uniform (or effectively uniform within [PRESSURE_VARIATION_EPSILON]), which
     * disables effects meant only for genuine stylus variation. Non-finite or
     * out-of-range samples fall back to neutral before the range is measured.
     */
    fun pressureVariation(points: List<NormalizedPoint>): Float {
        if (points.size < 2) return 0f
        var low = 1f
        var high = 0f
        points.forEach { point ->
            val pressure = safeFactor(point.pressure)
            if (pressure < low) low = pressure
            if (pressure > high) high = pressure
        }
        val range = high - low
        return if (range <= PRESSURE_VARIATION_EPSILON) 0f else range.coerceIn(0f, 1f)
    }

    /**
     * Deterministic "missing contact" decision for one ballpoint segment. Never
     * fires for neutral or moderate flow; as flow drops toward zero, gaps become
     * progressively more frequent. The integer hash keeps replay frames and PDF
     * export in lockstep without storing any per-stroke random state.
     */
    fun ballpointSkip(index: Int, flow: Float): Boolean {
        if (index <= 0) return false
        val safe = safeFactor(flow)
        if (safe >= BALLPOINT_LIGHT_FLOW) return false
        val hash = (index.toLong() * 2654435761L) and 0xFFFFFFFFL
        val phase = hash.toFloat() / 4294967295f
        val missing = 1f - safe / BALLPOINT_LIGHT_FLOW
        return phase < 0.08f + 0.55f * missing
    }

    /**
     * Alpha factor (1..255) for the tiny contact dot left at the start of a
     * skipped ballpoint segment. Exactly 255 at neutral flow so the helper can
     * never darken or lighten a uniform stroke; faint traces otherwise.
     */
    fun ballpointDotAlpha(flow: Float): Int {
        val safe = safeFactor(flow)
        if (safe >= 1f) return 255
        return (255f * (0.06f + 0.08f * safe)).roundToInt().coerceIn(1, 254)
    }

    /**
     * Fountain nib taper along normalized stroke position `t` (0 at the start,
     * 1 at the end). Returns exactly 1f for a uniform-pressure stroke, so the
     * legacy ribbon is untouched; varied strokes get a subtle, symmetric 0.60..
     * 1.00 envelope at the two tips only.
     */
    fun fountainTaper(t: Float, variation: Float): Float {
        val range = safeFactor(variation)
        if (range <= PRESSURE_VARIATION_EPSILON) return 1f
        val position = if (t.isFinite()) t.coerceIn(0f, 1f) else 0.5f
        val edge = minOf(position, 1f - position) * 2f
        val ramp = (edge / FOUNTAIN_TAPER_SPAN).coerceIn(0f, 1f)
        val depth = 0.16f + 0.24f * range
        return (1f - depth * (1f - ramp)).coerceIn(0.60f, 1f)
    }

    /**
     * Per-segment graphite darkness for one of the three passes (1 = centre,
     * 0/2 = sides). Reproduces the legacy 0.52/0.34/0.34 constants at neutral
     * flow and fades faster than linearly for genuinely light contact.
     */
    fun pencilSegmentAlpha(pass: Int, flow: Float): Float {
        val base = if (pass == 1) PENCIL_CENTER_ALPHA else PENCIL_SIDE_ALPHA
        val safe = safeFactor(flow)
        if (safe >= 1f) return base
        val eased = safe * safe * (3f - 2f * safe)
        return (base * eased).coerceIn(0f, 1f)
    }

    /**
     * Local graphite width multiplier for one segment. Bounded by the same
     * minimum the pressure model uses, and exactly 1f for a neutral sample.
     */
    fun pencilSegmentWidth(meanWidth: Float): Float {
        val safe = if (meanWidth.isFinite()) meanWidth else 1f
        return safe.coerceIn(MIN_PRESSURE_WIDTH_FACTOR, 1f)
    }

    /**
     * Gate for the filled variable-width outline. It is intentionally strict:
     * legacy/finger ink (all samples fully pressed), single-point dots and tiny
     * quantization noise never qualify, so those strokes keep their original
     * per-style renderer. Only a stroke with two or more samples and a genuine
     * pressure range is redrawn as an outline.
     */
    fun useVariableWidthOutline(points: List<NormalizedPoint>): Boolean {
        if (points.size < 2) return false
        if (isNeutralPressureStroke(points)) return false
        return pressureVariation(points) > 0f
    }

    /**
     * Original speed-aware pressure estimate. `reference` is the stroke's mean
     * local speed: movement at that pace leaves [pressure] untouched, slower
     * movement adds contact and faster movement lifts the nib. Genuine pressure
     * remains the anchor — a full-pressure sample is always exactly 1f, and a
     * missing/non-finite speed or reference falls back to the raw sample.
     */
    fun simulatedPressure(pressure: Float, speed: Float, reference: Float): Float {
        val safe = safeFactor(pressure)
        if (safe >= 1f) return 1f
        if (!speed.isFinite() || !reference.isFinite() || reference <= 0f) return safe
        val ratio = (speed.coerceAtLeast(0f) / reference).coerceIn(0f, 8f)
        // +1 at a standstill, 0 at the reference pace, negative when flicking.
        val direction = (1f - ratio) / (1f + ratio)
        val adjusted = if (direction >= 0f) {
            safe + (1f - safe) * SIMULATED_SPEED_INFLUENCE * direction
        } else {
            safe * (1f + SIMULATED_SPEED_INFLUENCE * direction)
        }
        return adjusted.coerceIn(0f, 1f)
    }

    /**
     * Symmetric tip envelope for a variable-width outline, in 0.60..1. At zero
     * (or noise-level) pressure variation this is exactly 1f for every `t`, so a
     * neutral stroke has no taper. Non-finite `t` lands at the stroke centre.
     */
    fun outlineEndEnvelope(t: Float, variation: Float, span: Float): Float {
        val range = safeFactor(variation)
        if (range <= PRESSURE_VARIATION_EPSILON) return 1f
        val position = if (t.isFinite()) t.coerceIn(0f, 1f) else 0.5f
        val safeSpan = if (span.isFinite()) span.coerceIn(0.02f, 0.5f) else OUTLINE_END_SPAN
        val edge = minOf(position, 1f - position) * 2f
        val u = (edge / safeSpan).coerceIn(0f, 1f)
        val ramp = u * u * (3f - 2f * u)
        val depth = 0.14f + 0.26f * range
        return (1f - depth * (1f - ramp)).coerceIn(0.60f, 1f)
    }

    /**
     * Per-sample half-widths for an outline, in the same unit as [baseWidth].
     * Pass `baseWidth = 1f` to obtain multipliers, then scale by the pixel width
     * at draw time; that is how a context precomputes widths once and lets replay
     * slice them instead of recomputing from a truncated prefix.
     *
     * [series] holds the per-point response width multipliers (never below
     * [MIN_PRESSURE_WIDTH_FACTOR] in normal use), [variation] is the full-stroke
     * pressure range, and [legacyWidth] is the style's neutral width multiplier.
     * For a neutral stroke every entry is exactly `baseWidth * 0.5 * legacyWidth`.
     */
    fun outlineWidthSeries(
        points: List<NormalizedPoint>,
        series: List<Float>,
        variation: Float,
        baseWidth: Float,
        legacyWidth: Float,
    ): FloatArray {
        val count = points.size
        if (count == 0) return FloatArray(0)
        val base = if (baseWidth.isFinite()) baseWidth.coerceAtLeast(0f) else 1f
        val legacy = if (legacyWidth.isFinite() && legacyWidth > 0f) {
            legacyWidth.coerceAtMost(4f)
        } else {
            1f
        }
        val range = safeFactor(variation)

        // Central-difference speed in normalized units; neighbours are clamped so
        // the first and last samples keep a speed and are never dropped.
        val speeds = FloatArray(count)
        for (i in 0 until count) {
            val previous = points[(i - 1).coerceAtLeast(0)]
            val next = points[(i + 1).coerceAtMost(count - 1)]
            val span = hypot((next.x - previous.x).toDouble(), (next.y - previous.y).toDouble())
            speeds[i] = (span * 0.5).toFloat()
        }
        var totalSpeed = 0f
        speeds.forEach { totalSpeed += it }
        val reference = totalSpeed / count.toFloat()

        val floor = (base * 0.5f * MIN_PRESSURE_WIDTH_FACTOR * legacy).coerceAtLeast(0.02f)
        val ceiling = (base * 2f).coerceAtLeast(floor)
        return FloatArray(count) { index ->
            if (range <= PRESSURE_VARIATION_EPSILON) {
                (base * 0.5f * legacy).coerceIn(floor, ceiling)
            } else {
                val effective = simulatedPressure(points[index].pressure, speeds[index], reference)
                val pressureWidth = MIN_PRESSURE_WIDTH_FACTOR + (1f - MIN_PRESSURE_WIDTH_FACTOR) * effective
                val responseWidth = series.getOrNull(index)?.let { value ->
                    if (value.isFinite()) value.coerceIn(MIN_PRESSURE_WIDTH_FACTOR, 1f) else 1f
                } ?: 1f
                val factor = (responseWidth + pressureWidth) * 0.5f
                val t = if (count == 1) 0.5f else index.toFloat() / (count - 1).toFloat()
                val half = base * 0.5f * legacy * factor * outlineEndEnvelope(t, range, OUTLINE_END_SPAN)
                if (half.isFinite()) half.coerceIn(floor, ceiling) else floor
            }
        }
    }

    /**
     * Builds a closed outline ring for a centre line and its per-point half-widths.
     * Two offset tracks are joined by round caps sampled from the local tangent;
     * the last point repeats the first so callers can close a path directly.
     * Coordinates are projected into device pixels with [widthPx]/[heightPx] and
     * returned as clamped normalized points, so the result is finite even for
     * degenerate or non-finite inputs.
     */
    fun variableWidthOutline(
        points: List<NormalizedPoint>,
        halfWidths: FloatArray,
        widthPx: Float,
        heightPx: Float,
        capSegments: Int,
        minHalfWidth: Float,
    ): List<NormalizedPoint> {
        val count = points.size
        if (count == 0) return emptyList()
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        val caps = capSegments.coerceIn(2, MAX_OUTLINE_CAP_SEGMENTS)
        val floor = if (minHalfWidth.isFinite()) minHalfWidth.coerceAtLeast(0.05f) else 0.05f
        val ceiling = w + h

        fun normalize(px: Float, py: Float): NormalizedPoint = NormalizedPoint(
            (px / w).coerceUnit(),
            (py / h).coerceUnit(),
            1f,
        )

        val xs = FloatArray(count) { points[it].x.coerceUnit() * w }
        val ys = FloatArray(count) { points[it].y.coerceUnit() * h }
        val half = FloatArray(count) { index ->
            val value = halfWidths.getOrNull(index) ?: floor
            if (value.isFinite()) value.coerceIn(floor, ceiling) else floor
        }

        if (count == 1) {
            val radius = half[0]
            val circle = List(caps) { step ->
                val angle = 2.0 * PI * step / caps
                normalize(
                    xs[0] + (cos(angle) * radius).toFloat(),
                    ys[0] + (sin(angle) * radius).toFloat(),
                )
            }
            return circle + circle.first()
        }

        val tangentX = FloatArray(count)
        val tangentY = FloatArray(count)
        val leftX = FloatArray(count)
        val leftY = FloatArray(count)
        val rightX = FloatArray(count)
        val rightY = FloatArray(count)
        for (i in 0 until count) {
            val previous = (i - 1).coerceAtLeast(0)
            val next = (i + 1).coerceAtMost(count - 1)
            var tx = xs[next] - xs[previous]
            var ty = ys[next] - ys[previous]
            var length = hypot(tx, ty)
            if (!length.isFinite() || length < 0.0001f) {
                tx = 1f
                ty = 0f
                length = 1f
            }
            tx /= length
            ty /= length
            tangentX[i] = tx
            tangentY[i] = ty
            val nx = -ty
            val ny = tx
            leftX[i] = xs[i] + nx * half[i]
            leftY[i] = ys[i] + ny * half[i]
            rightX[i] = xs[i] - nx * half[i]
            rightY[i] = ys[i] - ny * half[i]
        }

        val ring = ArrayList<NormalizedPoint>(count * 2 + caps * 2)
        for (i in 0 until count) ring.add(normalize(leftX[i], leftY[i]))

        // End cap: +normal -> +tangent -> -normal.
        val end = count - 1
        for (step in 1 until caps) {
            val angle = PI * step / caps
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            val nx = -tangentY[end]
            val ny = tangentX[end]
            ring.add(
                normalize(
                    xs[end] + (nx * c + tangentX[end] * s) * half[end],
                    ys[end] + (ny * c + tangentY[end] * s) * half[end],
                ),
            )
        }
        ring.add(normalize(rightX[end], rightY[end]))

        for (i in end - 1 downTo 0) ring.add(normalize(rightX[i], rightY[i]))

        // Start cap: -normal -> -tangent -> +normal.
        for (step in 1 until caps) {
            val angle = PI * step / caps
            val c = cos(angle).toFloat()
            val s = sin(angle).toFloat()
            val nx = -tangentY[0]
            val ny = tangentX[0]
            ring.add(
                normalize(
                    xs[0] + (-nx * c - tangentX[0] * s) * half[0],
                    ys[0] + (-ny * c - tangentY[0] * s) * half[0],
                ),
            )
        }
        ring.add(normalize(leftX[0], leftY[0]))
        return ring
    }

    /**
     * Deterministic graphite wave for one pencil sample. The legacy
     * index-based sine is kept verbatim whenever [grain] is full, so neutral
     * pencil ink is byte-identical; genuinely light grain switches to a wave
     * seeded by the sample's arc length instead. Pass 1 is the centre track and
     * never wobbles. The returned wave is in -1..1.
     */
    fun graphiteJitterByArc(
        index: Int,
        arcLength: Float,
        pass: Int,
        grain: Float,
        legacyPhase: Double,
    ): Float {
        if (pass == 1) return 0f
        val factor = if (grain.isFinite()) grain.coerceIn(0f, 1f) else 1f
        if (factor >= 1f) return sin(legacyPhase).toFloat()

        val arc = if (arcLength.isFinite()) arcLength.coerceAtLeast(0f) else maxOf(0, index).toFloat()
        var hash = (arc * 4096f).toLong() +
            index.toLong() * 0x9E3779B9L +
            pass.toLong() * 0x85EBCA6BL
        hash = hash xor (hash ushr 27)
        hash *= 0x3C79AC492BA7B653L
        hash = hash xor (hash ushr 33)
        hash *= 0x1C69B3F74AC4AE35L
        hash = hash xor (hash ushr 27)
        val unit = (hash ushr 11).toFloat() / (1L shl 53).toFloat()
        val wave = unit * 2f - 1f
        return (wave * (0.55f + 0.45f * factor)).coerceIn(-1f, 1f)
    }

    /** Fixed lateral offset of one graphite pass, softened by grain. */
    fun graphitePassOffset(pass: Int, grain: Float): Float {
        val base = when {
            pass <= 0 -> -GRAPHITE_SIDE_OFFSET
            pass == 1 -> GRAPHITE_CENTER_OFFSET
            else -> GRAPHITE_SIDE_OFFSET
        }
        val factor = if (grain.isFinite()) grain.coerceIn(0f, 1f) else 1f
        return (base * factor).coerceIn(-GRAPHITE_SIDE_OFFSET, GRAPHITE_SIDE_OFFSET)
    }

    /** Clamps a scalar into 0..1, mapping every non-finite value to neutral. */
    private fun safeFactor(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 1f

    private fun Float.coerceUnit(): Float =
        if (isFinite()) coerceIn(0f, 1f) else 0f
}
