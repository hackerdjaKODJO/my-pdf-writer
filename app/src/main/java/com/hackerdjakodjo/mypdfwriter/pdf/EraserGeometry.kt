package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.clampInkPressure
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Shared, deterministic eraser geometry for PDF annotations and notebook ink.
 *
 * Coordinates and radii are normalized to the page. The same radius drives the
 * visible eraser preview and hit testing; pressure may scale it only when the
 * active pointer is a pressure-sensitive stylus.
 */
object EraserGeometry {
    const val NORMALIZED_RADIUS = 0.03f

    private const val DEFAULT_MAX_STEP = 0.004f
    private const val MIN_PRESSURE_RADIUS_FACTOR = 0.6f
    private const val PRESSURE_RADIUS_FACTOR_SPAN = 1f
    private const val MAX_DENSIFY_STEPS_PER_SEGMENT = 4096

    /**
     * Returns [baseRadius] unchanged for fingers/mice or when pressure response is
     * disabled. A pressure-sensitive stylus maps clamped pressure 0..1 to a
     * bounded 0.6..1.6 radius multiplier.
     */
    fun radiusForPressure(
        baseRadius: Float,
        pressure: Float,
        pressureSensitive: Boolean,
    ): Float {
        val safeBase = if (baseRadius.isFinite()) baseRadius.coerceAtLeast(0f) else 0f
        if (!pressureSensitive || safeBase == 0f) return safeBase
        val safePressure = clampInkPressure(pressure)
        val factor = MIN_PRESSURE_RADIUS_FACTOR + PRESSURE_RADIUS_FACTOR_SPAN * safePressure
        return safeBase * factor
    }

    /**
     * Inserts samples along fast pointer trails so a swipe is a continuous
     * capsule instead of disconnected circles. Original samples, pressure and
     * timestamps are retained; inserted timestamps are linearly interpolated
     * only when both neighbours have one.
     */
    fun densifyTrail(
        points: List<NormalizedPoint>,
        maxStep: Float = DEFAULT_MAX_STEP,
    ): List<NormalizedPoint> {
        if (points.size < 2) return points
        val safeStep = if (maxStep.isFinite() && maxStep > 0f) {
            maxStep
        } else {
            DEFAULT_MAX_STEP
        }
        val samples = points.map(::sanitizePoint)
        val output = ArrayList<NormalizedPoint>()
        for (index in 0 until samples.lastIndex) {
            val a = samples[index]
            val b = samples[index + 1]
            val length = hypot(b.x - a.x, b.y - a.y)
            val steps = if (length <= 1e-12f) {
                1
            } else {
                ceil(length / safeStep).toInt().coerceIn(1, MAX_DENSIFY_STEPS_PER_SEGMENT)
            }
            for (step in 0 until steps) {
                val fraction = step.toFloat() / steps
                output.add(interpolate(a, b, fraction))
            }
        }
        output.add(samples.last())
        return output
    }

    /** Squared distance from a point to a finite line segment. */
    fun pointSegmentDistanceSquared(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        if (!px.isFinite() || !py.isFinite() || !ax.isFinite() || !ay.isFinite() ||
            !bx.isFinite() || !by.isFinite()
        ) {
            return Float.MAX_VALUE
        }
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val fraction = if (lengthSquared <= 1e-20f) {
            0f
        } else {
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        }
        val ex = px - (ax + fraction * dx)
        val ey = py - (ay + fraction * dy)
        return ex * ex + ey * ey
    }

    /**
     * Tests a stroke against the eraser trail capsule. The trail is densified to
     * at most half a radius per step, then every probe is tested against every
     * stroke segment, so crossings between sparse pointer events cannot be lost.
     */
    fun eraserHitsStroke(
        stroke: List<NormalizedPoint>,
        trail: List<NormalizedPoint>,
        radius: Float,
    ): Boolean {
        if (stroke.isEmpty() || trail.isEmpty() || !radius.isFinite() || radius <= 0f) {
            return false
        }
        val probes = densifyTrail(
            trail,
            maxStep = min(DEFAULT_MAX_STEP, radius * 0.5f).coerceAtLeast(0.00001f),
        )
        val radiusSquared = radius * radius
        val samples = stroke.map(::sanitizePoint)
        if (samples.size == 1) {
            val point = samples.first()
            if (probes.size == 1) {
                val probe = probes.first()
                val dx = point.x - probe.x
                val dy = point.y - probe.y
                return dx * dx + dy * dy <= radiusSquared
            }
            for (index in 1 until probes.size) {
                val a = probes[index - 1]
                val b = probes[index]
                if (pointSegmentDistanceSquared(point.x, point.y, a.x, a.y, b.x, b.y) <= radiusSquared) {
                    return true
                }
            }
            return false
        }
        for (index in 1 until samples.size) {
            val a = samples[index - 1]
            val b = samples[index]
            for (probe in probes) {
                if (pointSegmentDistanceSquared(probe.x, probe.y, a.x, a.y, b.x, b.y) <= radiusSquared) {
                    return true
                }
            }
        }
        return false
    }

    private fun sanitizePoint(point: NormalizedPoint): NormalizedPoint = point.copy(
        x = if (point.x.isFinite()) point.x.coerceIn(0f, 1f) else 0f,
        y = if (point.y.isFinite()) point.y.coerceIn(0f, 1f) else 0f,
        pressure = clampInkPressure(point.pressure),
    )

    private fun interpolate(
        a: NormalizedPoint,
        b: NormalizedPoint,
        fraction: Float,
    ): NormalizedPoint {
        val aTime = a.timeMs
        val bTime = b.timeMs
        return a.copy(
            x = a.x + (b.x - a.x) * fraction,
            y = a.y + (b.y - a.y) * fraction,
            pressure = clampInkPressure(a.pressure + (b.pressure - a.pressure) * fraction),
            timeMs = if (aTime != null && bTime != null) {
                val delta = ((bTime.toDouble() - aTime.toDouble()) * fraction).roundToLong()
                aTime + delta
            } else {
                null
            },
        )
    }
}
