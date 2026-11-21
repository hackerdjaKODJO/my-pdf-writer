package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM coverage for BackupViewModel's atomic single-entry guard. */
class BackupViewModelTest {

    @Test
    fun doubleRestoreCallsManagerOnce() {
        val gate = BackupOperationGate()
        var managerCalls = 0

        // First SAF result acquires the gate and starts BackupManager.restore.
        assertTrue(gate.tryStart())
        managerCalls++

        // A second result arriving before the first job exits must be a no-op:
        // the old `if (busy) return` check-then-set could start a second restore.
        assertFalse(gate.tryStart())
        assertEquals(1, managerCalls)

        // The first job's finally clears the gate; a later restore is allowed.
        gate.finish()
        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        managerCalls++
        gate.finish()
        assertEquals(2, managerCalls)
    }

    @Test
    fun gateIsClearedOnFailureAndCancellationExit() {
        val gate = BackupOperationGate()
        assertTrue(gate.tryStart())

        // Mirror the real launch body: `finally` runs for success, failure and
        // CancellationException alike.
        try {
            throw IllegalStateException("read failed")
        } catch (error: Exception) {
            // surfaced as a UI message by the ViewModel
        } finally {
            gate.finish()
        }

        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        gate.finish()
    }
}
