package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Pure, Compose-independent tracker for a two-finger pinch gesture in the
 * continuous reader.
 *
 * It keeps the first two pointers that go down and ignores any further ones.
 * The gesture becomes [active] as soon as two pointers are tracked; at that
 * moment [startDistance] is frozen and [currentDistance] starts tracking their
 * separation. Lifting *either* tracked pointer clears everything so the next
 * two-finger gesture gets a fresh baseline.
 *
 * The class has no Android/Compose runtime dependencies beyond the [Offset]
 * value type, which makes it usable from plain JVM unit tests.
 */
class ReaderPinchTracker {

    /** The (at most two) tracked pointers, in the order they went down. */
    private val pointers = LinkedHashMap<Int, Offset>()

    private var startDistanceValue = 0f
    private var currentDistanceValue = 0f

    /** True while exactly two pointers are being tracked. */
    val active: Boolean get() = pointers.size >= 2

    /** Separation when the gesture became active; `0` when inactive. */
    val startDistance: Float get() = if (active) startDistanceValue else 0f

    /** Latest separation of the two tracked pointers; `0` when inactive. */
    val currentDistance: Float get() = if (active) currentDistanceValue else 0f

    /** Registers a pointer going down, if it can be one of the first two. */
    fun onDown(pointerId: Int, position: Offset) {
        if (!position.isFinite()) return
        if (pointers.containsKey(pointerId)) return // duplicate id: never counts twice
        if (pointers.size >= 2) return // ignore third and later pointers
        pointers[pointerId] = position
        if (pointers.size == 2) {
            val (a, b) = trackedPointers()
            startDistanceValue = distanceBetween(a, b)
            currentDistanceValue = startDistanceValue
        }
    }

    /** Refreshes the position of an already-tracked pointer. */
    fun onMove(pointerId: Int, position: Offset) {
        if (!position.isFinite()) return
        if (!pointers.containsKey(pointerId)) return // only tracked pointers matter
        pointers[pointerId] = position
        if (active) {
            val (a, b) = trackedPointers()
            currentDistanceValue = distanceBetween(a, b)
        }
    }

    /** A tracked pointer lifting ends the gesture; other pointers are ignored. */
    fun onUp(pointerId: Int) {
        if (pointers.containsKey(pointerId)) clear()
    }

    /** Drops all pointers and distances, restoring the initial state. */
    fun clear() {
        pointers.clear()
        startDistanceValue = 0f
        currentDistanceValue = 0f
    }

    /** The two tracked pointers; call only when [active] is true. */
    private fun trackedPointers(): Pair<Offset, Offset> {
        val values = pointers.values.iterator()
        return values.next() to values.next()
    }
}

/** Rejects `Offset.Unspecified` and any coordinate that is NaN or infinite. */
private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

/**
 * Euclidean distance, computed in [Double] so large float coordinates cannot
 * overflow the subtraction. The result is clamped so it is always finite and
 * non-negative.
 */
private fun distanceBetween(a: Offset, b: Offset): Float {
    val dx = a.x.toDouble() - b.x.toDouble()
    val dy = a.y.toDouble() - b.y.toDouble()
    val distance = hypot(dx, dy)
    if (!distance.isFinite()) return 0f // defensive; inputs are finite doubles
    return distance.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
}
