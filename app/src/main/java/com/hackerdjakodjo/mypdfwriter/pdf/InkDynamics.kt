package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import kotlin.math.hypot

/**
 * Speed-aware "pen on paper" dynamics, shared by the note canvas, the PDF
 * overlay and the flattened export.
 *
 * Real writing tools respond to how fast the nib moves, not only to how hard
 * it presses: a brush flicks thin and dry, a fountain nib starves on a fast
 * pull and pools when it dwells, a ballpoint lays less ink on a quick scribble.
 * Every helper here is pure, deterministic, finite for NaN/Infinity, and an
 * EXACT identity for neutral full-pressure ink, so legacy and finger strokes
 * keep rendering byte-for-byte as before.
 *
 * Timed strokes use physical page-point velocity (pixels/second) from central
 * or one-sided differences. Legacy strokes keep the original distance-per-sample
 * fallback. All arrays are derived from the FULL stroke; replay slices them
 * instead of recomputing from a truncated prefix.
 */
object InkDynamics {

    /** Brush width responds strongly to speed: fast flicks thin, slow pulls swell. */
    const val BRUSH_SPEED_INFLUENCE = 0.35f

    /** Fountain nibs starve a little on fast pulls and wet up on slow ones. */
    const val FOUNTAIN_SPEED_INFLUENCE = 0.20f

    /** Graphite thins subtly on fast strokes; darkness already uses depositGain. */
    const val PENCIL_SPEED_INFLUENCE = 0.12f

    /** A ballpoint ball is fixed-size: pressure barely changes its width. */
    const val BALLPOINT_MIN_WIDTH_FACTOR = 0.88f

    /** Shortest timestamp span used for velocity; 1 ms prevents zero-dt spikes. */
    const val MIN_SPEED_DT_MS = 1f

    /**
     * Dwell radius as a fraction of the shorter page side. At 2x A4
     * (1190 x 1684 page points) this is 1.4875 px, preserving the old 1.5 px
     * feel while making screen, export and thumbnail decisions scale-invariant.
     */
    const val DWELL_RADIUS_PAGE_FRACTION = 0.00125f

    /** Dwell duration at which a timed ink pool reaches full strength. */
    const val DWELL_FULL_STRENGTH_MS = 200L

    /** A dwell needs at least this many near-stationary legacy samples to pool ink. */
    const val DWELL_MIN_SAMPLES = 3

    /** Timed dwells need 32 ms (about two 60 Hz intervals), independent of rate. */
    const val DWELL_MIN_DURATION_MS = 32L

    /** Upper bound on pools per stroke so a long dwell cannot flood drawing. */
    const val MAX_DWELL_POOLS = 8

    /** How strongly speed reweights ballpoint darkness (slow = wetter). */
    const val BALLPOINT_FLOW_SPEED_INFLUENCE = 0.30f

    /**
     * Width multiplier for one sample's speed. At the stroke's mean pace the
     * factor is exactly 1f; slower movement swells toward `1 + influence` and
     * faster movement thins toward `1 - influence`. Neutral ink (variation at
     * or below the noise epsilon) and a missing reference are exact identities.
     */
    fun velocityWidthFactor(
        speed: Float,
        reference: Float,
        influence: Float,
        variation: Float,
    ): Float {
        if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
            return 1f
        }
        if (!speed.isFinite() || !reference.isFinite() || reference <= 0f) return 1f
        if (!influence.isFinite() || influence <= 0f) return 1f
        val safeInfluence = influence.coerceAtMost(0.5f)
        val ratio = (speed.coerceAtLeast(0f) / reference).coerceIn(0f, 8f)
        // +1 at a standstill, 0 at the mean pace, negative on a flick.
        val direction = (1f - ratio) / (1f + ratio)
        return (1f + safeInfluence * direction).coerceIn(1f - safeInfluence, 1f + safeInfluence)
    }

    /**
     * Central-difference speed per sample. With valid [timesMs], the result is
     * physical page-point velocity in px/second; endpoints use a one-sided
     * difference. Timestamp spans are clamped to [MIN_SPEED_DT_MS].
     *
     * Timing is deliberately all-or-nothing: a missing, length-mismatched,
     * duplicated or non-monotonic array falls back to the exact legacy
     * distance-per-sample series. An empty stroke yields an empty array.
     */
    fun strokeSpeeds(
        points: List<NormalizedPoint>,
        widthPx: Float,
        heightPx: Float,
        timesMs: List<Long>? = null,
    ): FloatArray {
        val count = points.size
        if (count == 0) return FloatArray(0)
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        val times = timesMs?.takeIf { hasValidPointTimes(points, it) }
        return FloatArray(count) { index ->
            val previous = (index - 1).coerceAtLeast(0)
            val next = (index + 1).coerceAtMost(count - 1)
            val dx = (points[next].x - points[previous].x) * w
            val dy = (points[next].y - points[previous].y) * h
            val span = hypot(dx, dy)
            when {
                !span.isFinite() -> 0f
                times == null -> span * 0.5f
                else -> physicalSpeed(span, times[next], times[previous])
            }
        }
    }

    /**
     * Per-segment pace for deposit policies. This preserves the pencil's legacy
     * segment-distance fallback, but uses px/second when timestamps are valid.
     * Entry zero is unused by segment renderers; keeping it zero also makes the
     * mean over all samples equal to the historical `totalArc / sampleCount`.
     */
    fun strokeSegmentSpeeds(
        points: List<NormalizedPoint>,
        widthPx: Float,
        heightPx: Float,
        timesMs: List<Long>? = null,
    ): FloatArray {
        val count = points.size
        if (count < 2) return FloatArray(count)
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        val times = timesMs?.takeIf { hasValidPointTimes(points, it) }
        return FloatArray(count) { index ->
            if (index == 0) return@FloatArray 0f
            val previous = points[index - 1]
            val current = points[index]
            val dx = (current.x - previous.x) * w
            val dy = (current.y - previous.y) * h
            val span = hypot(dx, dy)
            when {
                !span.isFinite() -> 0f
                times == null -> span
                else -> physicalSpeed(span, times[index], times[index - 1])
            }
        }
    }

    /** True only for a complete, nonnegative, nondecreasing per-point timestamp array. */
    internal fun hasValidPointTimes(
        points: List<NormalizedPoint>,
        timesMs: List<Long>?,
    ): Boolean {
        if (points.size < 2 || timesMs == null || timesMs.size != points.size) return false
        if (timesMs[0] < 0L) return false
        for (index in 1 until timesMs.size) {
            // Equal uptime values are legal (coalesced/batched frames). physicalSpeed
            // clamps their dt to MIN_SPEED_DT_MS instead of discarding the whole stroke.
            if (timesMs[index] < timesMs[index - 1]) return false
        }
        return true
    }

    private fun physicalSpeed(spanPx: Float, endMs: Long, startMs: Long): Float {
        // Double subtraction avoids Long overflow for corrupt extreme timestamps.
        val dtMs = (endMs.toDouble() - startMs.toDouble())
            .coerceAtLeast(MIN_SPEED_DT_MS.toDouble())
        val speed = (spanPx.toDouble() * 1000.0 / dtMs).toFloat()
        return if (speed.isFinite()) speed.coerceAtLeast(0f) else 0f
    }

    /** Mean of [strokeSpeeds]; 0f for an empty stroke. */
    fun speedReference(speeds: FloatArray): Float {
        if (speeds.isEmpty()) return 0f
        var total = 0f
        speeds.forEach { total += if (it.isFinite()) it.coerceAtLeast(0f) else 0f }
        return total / speeds.size
    }

    /**
     * One ink pool where the nib dwelled: [index] is the last sample of the
     * stationary run (the anchor the renderer draws at) and [strength] is 0..1,
     * growing with dwell length. Derived from the full stroke; replay keeps a
     * pool only once its anchor sample has been revealed.
     */
    data class InkPool(val index: Int, val strength: Float)

    /**
     * Finds dwelling-nib runs whose pairwise movement stays within
     * [DWELL_RADIUS_PAGE_FRACTION] of the shorter page side. A real fountain nib
     * or brush pools ink into a darker blob when it rests; a moving stroke never
     * qualifies.
     *
     * Untimed legacy ink requires [DWELL_MIN_SAMPLES] and genuine pressure
     * variation, keeping the bounded sample-count strength formula. Timed ink
     * requires [DWELL_MIN_DURATION_MS] and measures strength from elapsed dwell
     * time, so capture rate does not change the decision. Full-pressure neutral
     * ink never pools, even when timed. At most [MAX_DWELL_POOLS] pools are
     * returned, in stroke order.
     */
    fun dwellPools(
        points: List<NormalizedPoint>,
        widthPx: Float,
        heightPx: Float,
        variation: Float,
        timesMs: List<Long>? = null,
    ): List<InkPool> {
        val times = timesMs?.takeIf { hasValidPointTimes(points, it) }
        if (times == null) {
            if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
                return emptyList()
            }
        } else if (points.all { it.pressure >= 1f }) {
            // Timing must not break the exact full-pressure neutral identity.
            return emptyList()
        }
        if (points.size < if (times == null) DWELL_MIN_SAMPLES else 2) return emptyList()
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        val radius = minOf(w, h) * DWELL_RADIUS_PAGE_FRACTION
        val pools = ArrayList<InkPool>()
        var runStart = 0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val dx = (current.x - previous.x) * w
            val dy = (current.y - previous.y) * h
            val step = hypot(dx, dy)
            val stationary = step.isFinite() && step <= radius
            if (!stationary) {
                addPool(pools, runStart, index - 1, times)
                runStart = index
            }
        }
        addPool(pools, runStart, points.lastIndex, times)
        return pools
    }

    /**
     * The pools a replay frame may draw: only those whose anchor sample has
     * been revealed. Strengths come from the full stroke and never change, so
     * an already visible pool cannot swell or fade while revealing continues.
     */
    fun poolsForReveal(pools: List<InkPool>, revealedSamples: Int): List<InkPool> {
        val count = revealedSamples.coerceAtLeast(0)
        return pools.filter { it.index < count }
    }

    private fun addPool(
        pools: ArrayList<InkPool>,
        runStart: Int,
        runEnd: Int,
        timesMs: List<Long>?,
    ) {
        if (pools.size >= MAX_DWELL_POOLS) return
        val run = runEnd - runStart + 1
        val strength = if (timesMs != null) {
            val durationMs = (timesMs[runEnd].toDouble() - timesMs[runStart].toDouble())
                .coerceAtLeast(0.0)
            if (durationMs < DWELL_MIN_DURATION_MS) return
            (durationMs / DWELL_FULL_STRENGTH_MS.toDouble()).toFloat().coerceIn(0.15f, 1f)
        } else {
            if (run < DWELL_MIN_SAMPLES) return
            ((run - (DWELL_MIN_SAMPLES - 1)).toFloat() / 10f).coerceIn(0.15f, 1f)
        }
        pools.add(InkPool(runEnd, strength))
    }

    /**
     * Speed-weighted ballpoint darkness. A ballpoint lays less ink on a quick
     * scribble and wetter ink on a slow, careful stroke, so slow samples count
     * more toward the stroke's overall flow. With neutral variation this is
     * exactly the plain mean, keeping legacy density unchanged. Bounded 0..1.
     */
    fun ballpointSpeedFlow(
        flows: List<Float>,
        speeds: FloatArray,
        reference: Float,
        variation: Float,
    ): Float {
        if (flows.isEmpty()) return 1f
        val plain = plainMean(flows)
        if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
            return plain
        }
        if (!reference.isFinite() || reference <= 0f || speeds.size < flows.size) return plain
        var weighted = 0f
        var weightTotal = 0f
        flows.forEachIndexed { index, flow ->
            val safeFlow = if (flow.isFinite()) flow.coerceIn(0f, 1f) else 1f
            val speed = speeds[index]
            val ratio = if (speed.isFinite()) {
                (speed.coerceAtLeast(0f) / reference).coerceIn(0f, 8f)
            } else {
                1f
            }
            // Slow samples weigh up to 1+influence, fast ones down to 1-influence.
            val weight = 1f + BALLPOINT_FLOW_SPEED_INFLUENCE * (1f - ratio) / (1f + ratio)
            weighted += safeFlow * weight
            weightTotal += weight
        }
        if (weightTotal <= 0f) return plain
        return (weighted / weightTotal).coerceIn(0f, 1f)
    }

    private fun plainMean(flows: List<Float>): Float {
        var total = 0f
        flows.forEach { total += if (it.isFinite()) it.coerceIn(0f, 1f) else 1f }
        return (total / flows.size).coerceIn(0f, 1f)
    }
}
