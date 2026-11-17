package com.hackerdjakodjo.mypdfwriter.audio

import kotlin.math.abs

/**
 * Supported offline playback rates for local audio replay.
 *
 * Only the six rates exposed by the playback-speed control are valid. Any other
 * value (including an out-of-range or non-finite number) is snapped back to the
 * nearest supported rate, so a bad value can never reach `MediaPlayer`. This is
 * a pure helper with no Android dependency: the same rules drive the UI chips,
 * the controller state, and the unit tests.
 */
object PlaybackSpeed {
    /** Neutral rate used whenever a new replay session starts. */
    const val DEFAULT: Float = 1.0f

    /** Rates shown in the compact chip row, in ascending order. */
    val ALLOWED: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    private const val EPSILON = 1e-4f

    /** True when [value] is one of the supported rates (within float tolerance). */
    fun isValid(value: Float): Boolean =
        value.isFinite() && ALLOWED.any { abs(it - value) <= EPSILON }

    /**
     * Validates and clamps [value] to a supported rate.
     *
     * Non-finite input falls back to [DEFAULT]; finite input is first clamped to
     * the supported range and then snapped to the nearest allowed rate.
     */
    fun normalize(value: Float): Float {
        if (!value.isFinite()) return DEFAULT
        val clamped = value.coerceIn(ALLOWED.first(), ALLOWED.last())
        var best = ALLOWED.first()
        var bestDelta = Float.MAX_VALUE
        for (candidate in ALLOWED) {
            val delta = abs(candidate - clamped)
            if (delta < bestDelta) {
                best = candidate
                bestDelta = delta
            }
        }
        return best
    }

    /** Compact user-facing label such as `0.5x`, `1x`, or `2x`. */
    fun label(value: Float): String {
        val speed = normalize(value)
        val whole = speed.toInt()
        val number = if (speed == whole.toFloat()) {
            whole.toString()
        } else {
            speed.toString().trimEnd('0').trimEnd('.')
        }
        return "${number}x"
    }

    /** Restores the neutral rate. */
    fun reset(): Float = DEFAULT
}
