package com.hackerdjakodjo.mypdfwriter.data

import kotlin.math.pow

/** Default pressure for fingers, mice, unknown pointers and legacy documents. */
const val DEFAULT_INK_PRESSURE = 1f

/** Lowest stored/rendered pressure. Zero-pressure samples stay visible. */
const val MIN_INK_PRESSURE = 0f

/** Highest accepted stylus pressure. */
const val MAX_INK_PRESSURE = 1f

/**
 * Narrowest a pressure-sensitive stroke may become. This is deliberately well
 * above zero so a light or missing sample can never collapse ink to a hairline
 * or a NaN width.
 */
const val MIN_PRESSURE_WIDTH_FACTOR = 0.35f

/**
 * Keeps a stored or captured pressure inside the supported range. Non-finite
 * values fall back to [fallback] (itself clamped) instead of leaking NaN into
 * geometry or JSON.
 */
fun clampInkPressure(
    value: Float,
    fallback: Float = DEFAULT_INK_PRESSURE,
): Float {
    val safeFallback = if (fallback.isFinite()) {
        fallback.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
    } else {
        DEFAULT_INK_PRESSURE
    }
    if (!value.isFinite()) return safeFallback
    return value.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
}

/**
 * Maps 0..1 stylus pressure to a safe width multiplier for fountain/brush ink.
 * Pressure 1 returns exactly 1, so legacy strokes render byte-for-byte as they
 * did before pressure support. Pressure 0 returns [MIN_PRESSURE_WIDTH_FACTOR].
 */
fun inkPressureWidthFactor(pressure: Float): Float {
    val safe = clampInkPressure(pressure)
    return MIN_PRESSURE_WIDTH_FACTOR + (1f - MIN_PRESSURE_WIDTH_FACTOR) * safe
}

/**
 * Original, device-neutral stylus response presets.
 *
 * A curve maps raw pressure 0..1 to effective pressure 0..1 before the value is
 * stored in `prs`. All presets are exact identities at 0 and 1, so a fully
 * pressed sample, a finger sample and a legacy document (pressure 1) stay
 * byte-for-byte neutral. Between those endpoints:
 *
 *  - [SOFT] lifts light pressure (gamma < 1), making faint contact swell sooner.
 *  - [LINEAR] is the identity and the default.
 *  - [FIRM] compresses light pressure (gamma > 1), requiring a firmer press.
 *
 * These are stable presets, not hardware calibration. Real stylus digitizers
 * report different pressure ranges and linearity, so on-device tuning remains a
 * separate task.
 */
enum class PressureCurve {
    SOFT,
    LINEAR,
    FIRM,
    ;

    /**
     * Maps one raw pressure sample to effective pressure. Out-of-range values are
     * clamped and non-finite values fall back to the neutral pressure instead of
     * leaking NaN into stored points or geometry.
     */
    fun applyTo(rawPressure: Float): Float {
        val safe = clampInkPressure(rawPressure)
        return when (this) {
            SOFT -> safe.pow(SOFT_GAMMA)
            LINEAR -> safe
            FIRM -> safe.pow(FIRM_GAMMA)
        }.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
    }

    companion object {
        /** Default for new sessions; matches the pre-curve 0.8.4 behavior. */
        val DEFAULT = LINEAR

        // Chosen so a light 25% press lands near 41% (SOFT) or 12% (FIRM).
        private const val SOFT_GAMMA = 0.65f
        private const val FIRM_GAMMA = 1.55f
    }
}

/**
 * A concise raw-to-effective pressure summary for the debug trace. It contains
 * ranges only, never page coordinates or stroke content.
 */
data class PressureCurveRange(
    val curve: PressureCurve,
    val rawMin: Float,
    val rawMax: Float,
    val effectiveMin: Float,
    val effectiveMax: Float,
)

/**
 * Builds a safe diagnostic range. Inputs are clamped independently, so an
 * inverted range still yields a finite, ordered summary.
 */
fun pressureCurveRange(
    curve: PressureCurve,
    rawMin: Float,
    rawMax: Float,
): PressureCurveRange {
    val low = clampInkPressure(rawMin)
    val high = clampInkPressure(rawMax)
    val effectiveLow = curve.applyTo(low)
    val effectiveHigh = curve.applyTo(high)
    return PressureCurveRange(
        curve = curve,
        rawMin = minOf(low, high),
        rawMax = maxOf(low, high),
        effectiveMin = minOf(effectiveLow, effectiveHigh),
        effectiveMax = maxOf(effectiveLow, effectiveHigh),
    )
}
