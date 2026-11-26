package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeMotionGateTest {
    @Test
    fun subThresholdMovementAccumulatesAgainstLastAcceptedPoint() {
        val gate = StrokeMotionGate(1f)
        gate.reset(0f, 0f)
        assertFalse(gate.accept(0.4f, 0f))
        assertFalse(gate.accept(0.9f, 0f))
        assertTrue(gate.accept(1.2f, 0f))
        assertFalse(gate.accept(1.7f, 0f))
        assertTrue(gate.accept(2.3f, 0f))
    }

    @Test
    fun diagonalAndVerticalMovementUseEuclideanDistance() {
        val gate = StrokeMotionGate(1f)
        gate.reset(0f, 0f)
        assertFalse(gate.accept(0.7f, 0.7f))
        assertTrue(gate.accept(0.8f, 0.8f))
        assertFalse(gate.accept(0.8f, 1.7f))
        assertTrue(gate.accept(0.8f, 1.9f))
    }

    @Test
    fun invalidSamplesNeverMoveTheAcceptedPoint() {
        val gate = StrokeMotionGate(1f)
        gate.reset(0f, 0f)
        assertFalse(gate.accept(Float.NaN, 0f))
        assertFalse(gate.accept(0f, Float.POSITIVE_INFINITY))
        assertTrue(gate.accept(1f, 0f))
    }

    @Test
    fun resetStartsANewStroke() {
        val gate = StrokeMotionGate(1f)
        gate.reset(0f, 0f)
        assertTrue(gate.accept(1f, 0f))
        gate.reset(10f, 10f)
        assertFalse(gate.accept(10.5f, 10f))
        assertTrue(gate.accept(11.2f, 10f))
    }
}
