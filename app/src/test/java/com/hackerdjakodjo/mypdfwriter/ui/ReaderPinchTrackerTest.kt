package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPinchTrackerTest {

    private fun assertFloat(expected: Float, actual: Float, message: String = "") {
        assertEquals(message, expected, actual, 0.0001f)
    }

    @Test
    fun `inactive until two pointers go down`() {
        val tracker = ReaderPinchTracker()
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)
        assertFloat(0f, tracker.currentDistance)

        tracker.onDown(1, Offset(0f, 0f))
        assertFalse("one pointer must not activate", tracker.active)
        assertFloat(0f, tracker.startDistance)
        assertFloat(0f, tracker.currentDistance)

        tracker.onDown(2, Offset(3f, 4f))
        assertTrue(tracker.active)
    }

    @Test
    fun `startDistance is the separation when the gesture activates`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onMove(1, Offset(10f, 0f)) // latest position of first pointer is used
        tracker.onDown(2, Offset(10f, 5f))

        assertTrue(tracker.active)
        assertFloat(5f, tracker.startDistance)
        assertFloat(5f, tracker.currentDistance)
    }

    @Test
    fun `moving either pointer updates only currentDistance`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(2, Offset(3f, 4f))
        assertFloat(5f, tracker.startDistance)

        tracker.onMove(1, Offset(-3f, 4f)) // distance from (-3,4) to (3,4) = 6
        assertFloat(5f, tracker.startDistance, "baseline must stay frozen")
        assertFloat(6f, tracker.currentDistance)

        tracker.onMove(2, Offset(3f, 12f)) // distance from (-3,4) to (3,12) = 10
        assertFloat(5f, tracker.startDistance)
        assertFloat(10f, tracker.currentDistance)
    }

    @Test
    fun `third and later pointers are ignored`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(2, Offset(10f, 0f))
        assertFloat(10f, tracker.startDistance)

        tracker.onDown(3, Offset(1000f, 1000f))
        tracker.onMove(3, Offset(-1000f, -1000f))
        assertFloat(10f, tracker.startDistance)
        assertFloat(10f, tracker.currentDistance, "third pointer must not affect distance")

        // A non-tracked pointer lifting must not end the gesture either.
        tracker.onUp(3)
        assertTrue(tracker.active)
        assertFloat(10f, tracker.currentDistance)
    }

    @Test
    fun `lifting any tracked pointer resets for a fresh two-finger gesture`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(2, Offset(10f, 0f))
        tracker.onMove(2, Offset(20f, 0f))

        tracker.onUp(1)
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)
        assertFloat(0f, tracker.currentDistance)

        // New gesture gets a new baseline, not the old one.
        tracker.onDown(5, Offset(0f, 0f))
        tracker.onDown(6, Offset(4f, 0f))
        assertTrue(tracker.active)
        assertFloat(4f, tracker.startDistance)
        assertFloat(4f, tracker.currentDistance)
    }

    @Test
    fun `lifting the second tracked pointer also resets`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(2, Offset(10f, 0f))
        tracker.onUp(2)
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)
        assertFloat(0f, tracker.currentDistance)
    }

    @Test
    fun `duplicate pointer ids are handled safely`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(1, Offset(50f, 50f)) // duplicate: must not count as a second pointer
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)

        tracker.onDown(2, Offset(3f, 4f))
        assertTrue(tracker.active)
        assertFloat(5f, tracker.startDistance, "duplicate must not change the baseline")

        // A duplicate down on a tracked id after activation must not alter state.
        tracker.onDown(1, Offset(-100f, -100f))
        assertFloat(5f, tracker.startDistance)
        assertFloat(5f, tracker.currentDistance)
    }

    @Test
    fun `clear resets all state`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onDown(2, Offset(10f, 0f))
        tracker.onMove(2, Offset(30f, 0f))
        assertTrue(tracker.active)

        tracker.clear()
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)
        assertFloat(0f, tracker.currentDistance)

        // After clear, a single pointer is treated as a brand-new gesture.
        tracker.onDown(9, Offset(0f, 0f))
        assertFalse(tracker.active)
        assertFloat(0f, tracker.startDistance)
    }

    @Test
    fun `non-finite positions are ignored`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(Float.NaN, 0f))
        tracker.onDown(2, Offset(Float.POSITIVE_INFINITY, 0f))
        assertFalse(tracker.active)

        tracker.onDown(3, Offset(0f, 0f))
        tracker.onMove(3, Offset(Float.NaN, 5f)) // bad move must not corrupt stored position
        tracker.onDown(4, Offset(3f, 4f))
        assertTrue(tracker.active)
        assertFloat(5f, tracker.startDistance)

        tracker.onMove(4, Offset(Float.NEGATIVE_INFINITY, 0f))
        assertFloat(5f, tracker.currentDistance, "bad move must not change the distance")
    }

    @Test
    fun `large finite coordinates keep distances finite and non-negative`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(Float.MAX_VALUE, Float.MAX_VALUE))
        tracker.onDown(2, Offset(-Float.MAX_VALUE, -Float.MAX_VALUE))
        assertTrue(tracker.active)
        assertTrue("startDistance must be finite", tracker.startDistance.isFinite())
        assertTrue("startDistance must be non-negative", tracker.startDistance >= 0f)
        assertEquals(Float.MAX_VALUE, tracker.startDistance)
        assertTrue("currentDistance must be finite", tracker.currentDistance.isFinite())
    }

    @Test
    fun `moves for unknown pointers are ignored`() {
        val tracker = ReaderPinchTracker()
        tracker.onDown(1, Offset(0f, 0f))
        tracker.onMove(99, Offset(100f, 100f))
        assertFalse(tracker.active)

        tracker.onDown(2, Offset(3f, 4f))
        assertFloat(5f, tracker.startDistance)
    }
}
