package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.MIN_PRESSURE_WIDTH_FACTOR
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import kotlin.math.roundToInt

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

    /** Clamps a scalar into 0..1, mapping every non-finite value to neutral. */
    private fun safeFactor(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
}
