package com.hackerdjakodjo.mypdfwriter.data

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
