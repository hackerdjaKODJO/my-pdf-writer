package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Pure coverage for the write serialization and restore-discard policy. */
class NotebookSaveRaceTest {

    @Test
    fun concurrentWritesAreSerializedByOneGate() {
        val active = AtomicInteger(0)
        val overlaps = AtomicInteger(0)
        val writes = AtomicInteger(0)
        // Mimic NotebookStore: one fixed tmp file per id, written then published.
        // Two overlapping writes would truncate each other's temp file.
        val gate = NotebookWriteGate { notebook ->
            if (active.incrementAndGet() != 1) overlaps.incrementAndGet()
            try {
                val tmp = StringBuilder(notebook.id)
                Thread.sleep(1)
                assertTrue("tmp must contain a full id: $tmp", tmp.startsWith("nb-"))
                writes.incrementAndGet()
            } finally {
                active.decrementAndGet()
            }
        }

        val threads = (1..16).map { index ->
            Thread { repeat(10) { gate.writeLocked(Notebook(id = "nb-$index")) } }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }

        assertEquals("writes must never overlap", 0, overlaps.get())
        assertEquals(160, writes.get())
        assertEquals(0, active.get())
    }

    @Test
    fun synchronousFlushSharesTheDebouncedWritersLock() {
        val active = AtomicInteger(0)
        val overlaps = AtomicInteger(0)
        val gate = NotebookWriteGate { _ ->
            if (active.incrementAndGet() != 1) overlaps.incrementAndGet()
            try {
                Thread.sleep(1)
            } finally {
                active.decrementAndGet()
            }
        }

        val flusher = Thread { gate.locked { gate.writeLocked(Notebook(id = "flushed")) } }
        val debounced = Thread { gate.writeLocked(Notebook(id = "debounced")) }
        flusher.start()
        debounced.start()
        flusher.join(10_000)
        debounced.join(10_000)

        assertEquals(0, overlaps.get())
    }

    @Test
    fun discardClearsLiveStateAndInvalidatesPendingSave() {
        val discarded = discardNotebookState(7L)

        assertNull(discarded.current)
        assertEquals(0, discarded.pageIndex)
        assertEquals(8L, discarded.saveGeneration)
        assertFalse(isLatestSaveGeneration(7L, discarded.saveGeneration))
        assertTrue(isLatestSaveGeneration(discarded.saveGeneration, discarded.saveGeneration))
    }

    @Test
    fun backupFlushesPendingSavesBeforeArchive() {
        val order = mutableListOf<String>()

        val summary = withFlushedSaves(
            { order += "flush" },
            {
                order += "archive"
                "summary"
            },
        )

        assertEquals("summary", summary)
        assertEquals(listOf("flush", "archive"), order)
    }
}
