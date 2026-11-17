package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousPinchGateTest {

    @Test
    fun `single pointer is not two finger`() {
        val gate = ContinuousPinchGate()
        assertFalse(gate.twoFinger)

        gate.onDown(1L)
        assertFalse("one pointer must not open the gate", gate.twoFinger)
    }

    @Test
    fun `second pointer opens the gate`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(2L)
        assertTrue(gate.twoFinger)
    }

    @Test
    fun `third pointer keeps the gate open`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(2L)
        gate.onDown(3L)
        assertTrue("three pointers must still be two-finger", gate.twoFinger)
    }

    @Test
    fun `duplicate down ids are safe`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(1L) // duplicate must not count as a second pointer
        assertFalse(gate.twoFinger)

        gate.onDown(2L)
        assertTrue(gate.twoFinger)

        // A duplicate down on an already-active id must not change state.
        gate.onDown(1L)
        assertTrue(gate.twoFinger)
    }

    @Test
    fun `lifting one pointer closes the gate`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(2L)
        assertTrue(gate.twoFinger)

        gate.onUp(1L)
        assertFalse("one remaining pointer must close the gate", gate.twoFinger)

        gate.onUp(2L)
        assertFalse(gate.twoFinger)
    }

    @Test
    fun `unknown up is ignored`() {
        val gate = ContinuousPinchGate()

        // Up on a gate with no pointers must be harmless.
        gate.onUp(99L)
        assertFalse(gate.twoFinger)

        gate.onDown(1L)
        gate.onDown(2L)
        gate.onUp(99L) // unknown id must not drop a real pointer
        assertTrue(gate.twoFinger)
    }

    @Test
    fun `duplicate up ids are safe`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(2L)

        gate.onUp(1L)
        gate.onUp(1L) // second up for the same id is a no-op
        assertFalse(gate.twoFinger)

        gate.onDown(3L)
        assertTrue(gate.twoFinger)
    }

    @Test
    fun `clear resets all pointers`() {
        val gate = ContinuousPinchGate()
        gate.onDown(1L)
        gate.onDown(2L)
        gate.onDown(3L)
        assertTrue(gate.twoFinger)

        gate.clear()
        assertFalse(gate.twoFinger)

        gate.onDown(4L)
        assertFalse("after clear a single pointer must not open the gate", gate.twoFinger)
    }
}
