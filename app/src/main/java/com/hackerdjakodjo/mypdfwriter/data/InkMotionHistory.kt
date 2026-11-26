package com.hackerdjakodjo.mypdfwriter.data

import kotlin.math.hypot

/** One timestamped point from a stroke's coalesced MotionEvent history. */
data class InkMotionSample(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val uptimeMs: Long,
)

/**
 * Bounded, stroke-scoped FIFO for coalesced MotionEvent history.
 *
 * Samples must have finite coordinates and pressure, a non-negative uptime and
 * a timestamp strictly newer than the newest accepted sample in this stroke.
 * When full, a new valid sample evicts the oldest one. Reading and predicting
 * never consume or mutate samples. [consumeThrough] removes processed samples,
 * while [clear] resets the stroke.
 *
 * This Android-free helper is intended for one stroke's input thread and is not
 * thread-safe.
 */
class InkMotionHistoryBuffer(private val maxSamples: Int = 128) {
    private val buffer = ArrayDeque<InkMotionSample>()
    private var newestUptimeMs = -1L

    init {
        require(maxSamples > 0) { "maxSamples must be positive" }
    }

    /** Starts a new stroke, forgetting both retained samples and ordering. */
    fun clear() {
        buffer.clear()
        newestUptimeMs = -1L
    }

    /**
     * Retains [sample] if it is valid and strictly newer than the newest sample
     * accepted since [clear]. Returns true when accepted, including when
     * acceptance evicts an old sample.
     */
    fun append(sample: InkMotionSample): Boolean {
        if (!sample.x.isFinite() || !sample.y.isFinite() || !sample.pressure.isFinite()) {
            return false
        }
        if (sample.uptimeMs < 0L) return false
        if (sample.uptimeMs <= newestUptimeMs) return false

        if (buffer.size == maxSamples) buffer.removeFirst()
        buffer.addLast(sample)
        newestUptimeMs = sample.uptimeMs
        return true
    }

    /** Appends in iteration order and returns the number of accepted samples. */
    fun appendAll(samples: Iterable<InkMotionSample>): Int {
        var accepted = 0
        for (sample in samples) {
            if (append(sample)) accepted++
        }
        return accepted
    }

    /** Returns an ordered copy of every retained sample, including maximum uptime. */
    fun snapshot(): List<InkMotionSample> = buffer.toList()

    /**
     * Returns an ordered snapshot of retained samples strictly before [uptimeMs]
     * without consuming them. Calling this repeatedly returns the same samples
     * until [consumeThrough], an eviction or [clear] changes the buffer.
     */
    fun samplesBefore(uptimeMs: Long): List<InkMotionSample> {
        val result = ArrayList<InkMotionSample>()
        for (sample in buffer) {
            if (sample.uptimeMs >= uptimeMs) break
            result.add(sample)
        }
        return result
    }

    /** Removes retained samples whose uptime is at or before [uptimeMs]. */
    fun consumeThrough(uptimeMs: Long) {
        while (buffer.isNotEmpty() && buffer.first().uptimeMs <= uptimeMs) {
            buffer.removeFirst()
        }
    }

    /**
     * Extrapolates a transient point from only the newest two retained samples.
     *
     * The horizon is clamped to 0..12 ms and travel is capped at [maxDistancePx].
     * Non-finite arguments, a non-positive distance limit, stationary or
     * non-finite velocity, coordinate overflow and uptime overflow produce null.
     * Pressure is carried from the newest sample; sub-millisecond horizons are
     * truncated in the returned uptime. The prediction is never retained.
     */
    fun predict(
        horizonMs: Float = 8f,
        maxDistancePx: Float = 12f,
    ): InkMotionSample? {
        if (buffer.size < 2 || !horizonMs.isFinite() || !maxDistancePx.isFinite()) {
            return null
        }
        if (maxDistancePx <= 0f) return null

        val newest = buffer.last()
        val previous = buffer[buffer.size - 2]
        val elapsedMs = (newest.uptimeMs - previous.uptimeMs).toFloat()
        if (!elapsedMs.isFinite() || elapsedMs <= 0f) return null

        val velocityX = (newest.x - previous.x) / elapsedMs
        val velocityY = (newest.y - previous.y) / elapsedMs
        val speed = hypot(velocityX, velocityY)
        if (!velocityX.isFinite() || !velocityY.isFinite() || !speed.isFinite() || speed <= 0f) {
            return null
        }

        val horizon = horizonMs.coerceIn(0f, MAX_PREDICTION_HORIZON_MS)
        // Double arithmetic keeps a very large requested distance from becoming
        // infinity before it can be scaled down to the caller's finite limit.
        val requestedDistance = speed.toDouble() * horizon.toDouble()
        val distanceScale = if (requestedDistance > maxDistancePx) {
            maxDistancePx.toDouble() / requestedDistance
        } else {
            1.0
        }
        val predictedX = (
            newest.x.toDouble() + velocityX.toDouble() * horizon.toDouble() * distanceScale
        ).toFloat()
        val predictedY = (
            newest.y.toDouble() + velocityY.toDouble() * horizon.toDouble() * distanceScale
        ).toFloat()
        if (!predictedX.isFinite() || !predictedY.isFinite()) return null

        val horizonWholeMs = horizon.toLong()
        if (newest.uptimeMs > Long.MAX_VALUE - horizonWholeMs) return null
        return InkMotionSample(
            x = predictedX,
            y = predictedY,
            pressure = newest.pressure,
            uptimeMs = newest.uptimeMs + horizonWholeMs,
        )
    }

    private companion object {
        const val MAX_PREDICTION_HORIZON_MS = 12f
    }
}
