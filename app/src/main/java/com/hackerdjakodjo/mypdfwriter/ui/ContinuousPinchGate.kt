package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Pure, dependency-free gate that reports whether a two-finger gesture is in
 * progress by counting distinct pointer ids that are currently down.
 *
 * This is intentionally minimal: it does not track positions or distances and
 * has no Android/Compose dependencies, so it is usable from plain JVM unit
 * tests. It is meant to gate Compose's `detectTransformGestures` so that
 * single-finger vertical scrolling is preserved and zoom is applied only once
 * a second pointer goes down.
 *
 * Duplicate ids never count more than once, and an [onUp] for an unknown id is
 * ignored. Every pointer that goes down must eventually be lifted (or [clear]
 * called) to bring the count back down.
 */
class ContinuousPinchGate {

    /** Distinct ids of pointers currently considered down. */
    private val activePointers = mutableSetOf<Long>()

    /** True while two or more distinct pointers are down. */
    val twoFinger: Boolean get() = activePointers.size >= 2

    /** Registers a pointer going down; duplicate ids are ignored. */
    fun onDown(pointerId: Long) {
        activePointers.add(pointerId)
    }

    /** Removes a pointer that went up; unknown ids are ignored. */
    fun onUp(pointerId: Long) {
        activePointers.remove(pointerId)
    }

    /** Drops all tracked pointers, restoring the initial state. */
    fun clear() {
        activePointers.clear()
    }
}
