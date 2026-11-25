package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** JVM coverage for backup gates, pre-flush ordering and mutation leases. */
class BackupViewModelTest {

    @Test
    fun doubleRestoreCallsManagerOnce() {
        val gate = BackupOperationGate()
        var managerCalls = 0

        assertTrue(gate.tryStart())
        managerCalls++
        assertFalse(gate.tryStart())
        assertEquals(1, managerCalls)

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
        try {
            throw IllegalStateException("read failed")
        } catch (ignored: Exception) {
            // surfaced as a UI message by the ViewModel
        } finally {
            gate.finish()
        }
        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        gate.finish()
    }

    @Test
    fun successfulFlushRunsBeforeArchiveAndOnlyOnce() {
        val order = mutableListOf<String>()
        val result = withFlushedSaves(
            flush = { order += "flush"; true },
            action = { order += "archive"; "summary" },
        )

        assertEquals("summary", result)
        assertEquals(listOf("flush", "archive"), order)
    }

    @Test
    fun falseFlushAbortsBeforeArchiveTouchesOutput() {
        val order = mutableListOf<String>()
        val failure = runCatching {
            withFlushedSaves(
                flush = { order += "flush"; false },
                action = { order += "archive"; "must-not-run" },
            )
        }.exceptionOrNull()

        assertTrue(failure is BackupFlushException)
        assertEquals(listOf("flush"), order)
    }

    @Test
    fun mutationLeaseStartsBeforeFlushAndLastsThroughOperation() {
        val lease = BackupMutationLease()
        val order = mutableListOf<String>()

        assertTrue(
            lease.begin {
                assertTrue(lease.isActive)
                order += "flush"
                true
            },
        )
        order += "archive"
        assertTrue(lease.isActive)
        order += "host-refresh"
        assertTrue(lease.isActive)

        lease.finish()
        order += "complete"
        assertFalse(lease.isActive)
        assertEquals(listOf("flush", "archive", "host-refresh", "complete"), order)
    }

    @Test
    fun failedMutationLeaseStillRequiresExplicitCompletion() {
        val lease = BackupMutationLease()
        assertFalse(lease.begin { false })
        // The ViewModel's failure callback owns release, so the gate cannot open
        // between the failed pre-flush and retryable error state publication.
        assertTrue(lease.isActive)
        lease.finish()
        assertFalse(lease.isActive)
    }
}
