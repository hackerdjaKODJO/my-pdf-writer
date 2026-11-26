package com.hackerdjakodjo.mypdfwriter.pdf

import kotlin.math.hypot

/**
 * Distance gate for one captured stroke. Sub-threshold movement is accumulated
 * against the last accepted point rather than emitted as geometry.
 */
class StrokeMotionGate(private val minDistancePx: Float) {
    init {
        require(minDistancePx.isFinite() && minDistancePx > 0f)
    }

    private var lastX = 0f
    private var lastY = 0f
    private var initialized = false

    fun reset(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        lastX = x
        lastY = y
        initialized = true
    }

    fun accept(x: Float, y: Float): Boolean {
        if (!x.isFinite() || !y.isFinite()) return false
        if (!initialized) {
            reset(x, y)
            return true
        }
        if (hypot(x - lastX, y - lastY) < minDistancePx) return false
        lastX = x
        lastY = y
        return true
    }
}
