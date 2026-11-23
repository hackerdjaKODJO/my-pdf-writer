package com.hackerdjakodjo.mypdfwriter.pdf

import kotlin.math.abs

/**
 * Pure helpers for the remaining "pen on real paper" fidelity details.
 *
 * Every helper is deterministic, finite for NaN/Infinity and an EXACT identity
 * for neutral (uniform/full pressure) ink, so legacy annotations and finger
 * strokes keep rendering byte-for-byte as before.
 */
object InkQuality {

    /** Pressure delta below which a stationary nib change is just sensor noise. */
    const val PRESSURE_EPSILON = 0.01f

    /** Stationary samples must not be emitted faster than this (ms). */
    const val MIN_STATIONARY_SAMPLE_MS = 32L
    /** A long dwell must not grow the stroke without bound. */
    const val MAX_STATIONARY_SAMPLES = 24

    const val DEPOSIT_INFLUENCE = 0.22f
    const val DEPOSIT_GAIN_MIN = 0.85f
    const val DEPOSIT_GAIN_MAX = 1.25f

    /** Fraction of each stroke end collapsed into a nib point. */
    const val NIB_TAPER_SPAN = 0.08f
    const val NIB_TAPER_MIN = 0.08f

    /**
     * A pressurised nib that dwells in place must still lay down ink: a fountain
     * or brush grows darker/wider when the student leans on it. Position changes
     * are handled by the normal capture path, so this decides only the stationary
     * case. Jitter below [eps] is ignored to avoid flooding the stroke.
     */
    fun shouldEmitPressureSample(
        previousPressure: Float,
        nextPressure: Float,
        positionChanged: Boolean,
        elapsedMs: Long = MIN_STATIONARY_SAMPLE_MS,
        eps: Float = PRESSURE_EPSILON,
        emitted: Int = 0,
    ): Boolean {
        if (positionChanged) return false
        if (emitted >= MAX_STATIONARY_SAMPLES) return false
        if (elapsedMs < MIN_STATIONARY_SAMPLE_MS) return false
        val previous = clampPressure(previousPressure)
        val next = clampPressure(nextPressure)
        return abs(next - previous) >= eps
    }

    /**
     * Slow strokes pool ink, fast flicks starve it. [speed] and [reference] can
     * be in any unit as long as they match; a non-positive reference disables
     * the effect. Returns exactly 1f for neutral ink.
     */
    fun depositGain(
        speed: Float,
        reference: Float,
        pressure: Float,
        variation: Float,
    ): Float {
        if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
            return 1f
        }
        if (!speed.isFinite() || !reference.isFinite() || reference <= 0f) return 1f
        val ratio = (speed.coerceAtLeast(0f) / reference).coerceIn(0f, 8f)
        // +1 at a standstill, 0 at the stroke's mean pace, negative on a flick.
        val direction = (1f - ratio) / (1f + ratio)
        val gain = 1f + DEPOSIT_INFLUENCE * direction * clampPressure(pressure)
        return gain.coerceIn(DEPOSIT_GAIN_MIN, DEPOSIT_GAIN_MAX)
    }

    /**
     * Collapses the first and last [span] of a varied stroke into a handwriting
     * point instead of a blunt round cap. Exactly 1f for neutral ink, so old
     * finger/legacy strokes are unchanged.
     */
    fun nibEndProfile(
        t: Float,
        variation: Float,
        span: Float = NIB_TAPER_SPAN,
        floor: Float = NIB_TAPER_MIN,
    ): Float {
        if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
            return 1f
        }
        val position = if (t.isFinite()) t.coerceIn(0f, 1f) else 0.5f
        val safeSpan = if (span.isFinite()) span.coerceIn(0.02f, 0.25f) else NIB_TAPER_SPAN
        val edge = minOf(position, 1f - position)
        val ramp = (edge / safeSpan).coerceIn(0f, 1f)
        val smooth = ramp * ramp * (3f - 2f * ramp)
        val safeFloor = if (floor.isFinite()) floor.coerceIn(0.02f, 0.5f) else NIB_TAPER_MIN
        return (safeFloor + (1f - safeFloor) * smooth).coerceIn(safeFloor, 1f)
    }

    /** Stable cell of a scale-invariant normalized arc length. */
    fun grainCell(normalizedArc: Float, cellSize: Float = 1f / 512f): Int {
        if (!normalizedArc.isFinite() || !cellSize.isFinite() || cellSize <= 0f) return 0
        return (normalizedArc / cellSize).toInt()
    }

    private fun clampPressure(value: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
}
