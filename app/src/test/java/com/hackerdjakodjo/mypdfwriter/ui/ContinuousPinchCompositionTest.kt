package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM check that the pointer-count gate and the native transform zoom delta
 * compose correctly: a single finger never changes zoom, and lifting one finger
 * closes the gate again. The Compose wiring in [ContinuousReaderScreen] feeds the
 * same [ContinuousPinchGate] from an Initial-pass observer and applies
 * [gatedPinchZoom] inside `detectTransformGestures`.
 */
class ContinuousPinchCompositionTest {

    private fun assertZoom(expected: Float, actual: Float) =
        assertEquals("zoom", expected, actual, 0.0001f)

    @Test
    fun `single finger never zooms, two fingers do, lifting one stops zoom`() {
        val gate = ContinuousPinchGate()
        var zoom = READER_ZOOM_MIN

        fun onTransformFrame(zoomChange: Float) {
            zoom = gatedPinchZoom(zoom, zoomChange, gate.twoFinger)
        }

        // First finger down: transform gestures still report deltas, but the
        // single-finger delta must be ignored so vertical scrolling is intact.
        gate.onDown(1L)
        onTransformFrame(2f)
        assertZoom(READER_ZOOM_MIN, zoom)

        // Second finger opens the gate; the same native delta now zooms.
        gate.onDown(2L)
        onTransformFrame(2f)
        assertZoom(2.0f, zoom)

        // One finger lifts mid-gesture: no more zoom until a new second finger.
        gate.onUp(1L)
        onTransformFrame(0.5f)
        assertZoom(2.0f, zoom)

        // A fresh two-finger contact continues from the current zoom.
        gate.onDown(3L)
        onTransformFrame(0.5f)
        assertZoom(1.0f, zoom)

        gate.onUp(2L)
        gate.onUp(3L)
        assertEquals(false, gate.twoFinger)
    }
}
