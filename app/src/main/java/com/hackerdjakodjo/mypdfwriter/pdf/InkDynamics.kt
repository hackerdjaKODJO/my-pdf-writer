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
 * EXACT identity for neutral (uniform/full pressure) ink, so legacy and finger
 * strokes keep rendering byte-for-byte as before.
 *
 * Speeds are spatial (normalized page units scaled to pixels), matching the
 * existing [PenStrokeGeometry.simulatedPressure] convention: samples arrive at
 * a roughly uniform pointer-event rate, so distance per sample stands in for
 * velocity. All arrays are derived from the FULL stroke; replay slices them
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

    /** Consecutive samples closer than this (page px) count as a dwelling nib. */
    const val DWELL_RADIUS_PX = 1.5f

    /** A dwell needs at least this many near-stationary samples to pool ink. */
    const val DWELL_MIN_SAMPLES = 3

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
     * Central-difference speed per sample in page pixels. Neighbour indices are
     * clamped so the first and last samples keep a speed and are never dropped.
     * An empty stroke yields an empty array.
     */
    fun strokeSpeeds(
        points: List<NormalizedPoint>,
        widthPx: Float,
        heightPx: Float,
    ): FloatArray {
        val count = points.size
        if (count == 0) return FloatArray(0)
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        return FloatArray(count) { index ->
            val previous = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(count - 1)]
            val dx = (next.x - previous.x) * w
            val dy = (next.y - previous.y) * h
            val span = hypot(dx, dy)
            if (span.isFinite()) span * 0.5f else 0f
        }
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
     * Finds dwelling-nib runs: [DWELL_MIN_SAMPLES] or more consecutive samples
     * whose pairwise movement stays under [DWELL_RADIUS_PX]. A real fountain
     * nib or brush pools ink into a darker blob when it rests; a moving stroke
     * never qualifies. Neutral ink returns an empty list so legacy strokes are
     * untouched. At most [MAX_DWELL_POOLS] pools are returned, in stroke order.
     */
    fun dwellPools(
        points: List<NormalizedPoint>,
        widthPx: Float,
        heightPx: Float,
        variation: Float,
    ): List<InkPool> {
        if (!variation.isFinite() || variation <= PenStrokeGeometry.PRESSURE_VARIATION_EPSILON) {
            return emptyList()
        }
        if (points.size < DWELL_MIN_SAMPLES) return emptyList()
        val w = if (widthPx.isFinite() && widthPx > 0f) widthPx else 1f
        val h = if (heightPx.isFinite() && heightPx > 0f) heightPx else 1f
        val pools = ArrayList<InkPool>()
        var runStart = 0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val dx = (current.x - previous.x) * w
            val dy = (current.y - previous.y) * h
            val step = hypot(dx, dy)
            val stationary = step.isFinite() && step <= DWELL_RADIUS_PX
            if (!stationary) {
                addPool(pools, runStart, index - 1)
                runStart = index
            }
        }
        addPool(pools, runStart, points.lastIndex)
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

    private fun addPool(pools: ArrayList<InkPool>, runStart: Int, runEnd: Int) {
        if (pools.size >= MAX_DWELL_POOLS) return
        val run = runEnd - runStart + 1
        if (run < DWELL_MIN_SAMPLES) return
        val strength = ((run - (DWELL_MIN_SAMPLES - 1)).toFloat() / 10f).coerceIn(0.15f, 1f)
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
