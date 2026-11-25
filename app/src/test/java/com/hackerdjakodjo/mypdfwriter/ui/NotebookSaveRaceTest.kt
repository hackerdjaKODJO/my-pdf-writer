package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun closeFailureRetainsCurrentAndRetrySucceeds() {
        var failWrite = true
        val session = NotebookSaveSession({ failWrite.not() }, { 123L })
        val notebook = Notebook(
            id = "live",
            pages = listOf(NotePage(), NotePage()),
            lastPageIndex = 1,
        )
        session.open(notebook)

        assertFalse(session.close())
        assertEquals(notebook.normalized(), session.current.value)
        assertEquals(1, session.pageIndex.value)
        assertTrue(session.saveError.value != null)

        failWrite = false
        assertTrue(session.retrySave())
        assertNull(session.saveError.value)
        assertTrue(session.close())
        assertNull(session.current.value)
        assertEquals(0, session.pageIndex.value)
    }

    @Test
    fun detachedFailureReturnsNullAndBackupFlushPropagatesFailure() {
        var failWrite = true
        val session = NotebookSaveSession({ failWrite.not() }, { 456L })
        val detached = Notebook(id = "detached", pages = listOf(NotePage(), NotePage()))

        assertNull(session.trySaveDetached(detached))
        assertFalse(session.flushPendingSavesBeforeBackup())
        var archived = false
        assertThrows(NotebookSaveException::class.java) {
            withFlushedSaves(
                { session.requireBackupFlush() },
                { archived = true },
            )
        }
        assertFalse(archived)

        failWrite = false
        assertTrue(session.retrySave())
        assertNull(session.saveError.value)
        assertEquals(detached.normalized().copy(updatedAt = 456L), session.notebooks.value.single())
        assertTrue(session.flushPendingSavesBeforeBackup())
    }

    @Test
    fun detachedPageNavigationPersistsClampedLastPageWithoutReordering() {
        val session = NotebookSaveSession({ true }, { 789L })
        val notebook = Notebook(
            id = "pane",
            pages = listOf(NotePage(), NotePage(), NotePage()),
            updatedAt = 42L,
        )

        val saved = session.setDetachedPageIndex(notebook, 99)

        assertTrue(saved != null)
        assertEquals(2, saved?.lastPageIndex)
        assertEquals(42L, saved?.updatedAt)
        assertEquals(saved, session.notebooks.value.single())
    }

    @Test
    fun differentUriWithSameNameNeverMerges() {
        val wrongDocument = Notebook(
            id = "wrong",
            sourceDocumentName = "lecture.pdf",
            sourceDocumentUri = "content://docs/a",
        )
        val rightDocument = Notebook(
            id = "right",
            sourceDocumentName = "lecture.pdf",
            sourceDocumentUri = "content://docs/b",
        )
        val source = StudySource("lecture.pdf", "content://docs/b")

        assertEquals(
            "right",
            findStudyNotebook(listOf(wrongDocument, rightDocument), source)?.id,
        )
        assertNull(findStudyNotebook(listOf(wrongDocument), source))
    }

    @Test
    fun studyAppendResultReportsTheActualTargetPage() {
        val source = StudySource("lecture.pdf", "content://docs/lecture")
        val existing = Notebook(
            id = "target",
            pages = listOf(NotePage(), NotePage()),
            sourceDocumentUri = source.docUri.orEmpty(),
        )
        var persisted: Notebook? = null

        val result = appendStudyExcerptResult(
            source = source,
            text = "새 발췌",
            pageIndex = 7,
            findExisting = { existing },
            save = {
                persisted = it
                true
            },
        )

        assertTrue(result.saved)
        assertEquals("target", result.notebookId)
        assertEquals(2, result.pageIndex)
        assertEquals(7, result.notebook.pages[result.pageIndex].sourcePageIndex)
        assertEquals(result.notebook, persisted)
    }

    @Test
    fun studyAppendResultReportsUnverifiedSaveAsFailure() {
        val result = appendStudyExcerptResult(
            source = StudySource("lecture.pdf", "content://docs/lecture"),
            text = "저장 실패",
            pageIndex = 3,
            findExisting = { null },
            save = { false },
        )

        assertFalse(result.saved)
        assertEquals(0, result.pageIndex)
        assertTrue(result.error != null)
    }
}
