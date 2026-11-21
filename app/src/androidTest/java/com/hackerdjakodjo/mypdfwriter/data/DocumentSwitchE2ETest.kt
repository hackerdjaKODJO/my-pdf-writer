package com.hackerdjakodjo.mypdfwriter.data

import com.hackerdjakodjo.mypdfwriter.ui.DocSaveQueue
import com.hackerdjakodjo.mypdfwriter.ui.OpenGenerationGate
import com.hackerdjakodjo.mypdfwriter.ui.PdfExportSnapshot
import com.hackerdjakodjo.mypdfwriter.ui.ViewerUiState
import com.hackerdjakodjo.mypdfwriter.ui.mayPublishOpenResult
import com.hackerdjakodjo.mypdfwriter.ui.mayPublishViewerResult
import com.hackerdjakodjo.mypdfwriter.ui.viewerDocState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Plain-JUnit instrumentation coverage for the document-switch races. Every
 * assertion targets a pure policy extracted from PdfViewModel, so the same
 * behaviour can be exercised on a device without opening a real PDF or SAF
 * picker.
 *
 * - the coalescing save queue is keyed by docId (a doc switch cannot drop the
 *   previous document's last state);
 * - an open generation token lets only the last of two rapid opens publish;
 * - the SAF export payload is captured at launch and is checked against the doc
 *   that is active when the result arrives.
 */
class DocumentSwitchE2ETest {

    private fun state(docId: String, note: String) = DocState(
        docId = docId,
        displayName = "$docId.pdf",
        pages = listOf(PageSpec(0)),
        annotations = emptyList(),
        notes = mapOf(0 to note),
    )

    // ------------------------------------------------------------- save queue

    @Test
    fun saveQueueKeepsTheNewestStateForEachDocId() {
        var queue = DocSaveQueue()
        assertFalse(queue.running)
        assertTrue(queue.pending.isEmpty())

        val (afterA, startA) = queue.offer(state("A", "a1"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(state("B", "b1"))
        assertFalse("a second pending doc must not start another writer", startB)
        queue = afterB
        val (afterA2, startA2) = queue.offer(state("A", "a2"))
        assertFalse(startA2)
        queue = afterA2

        assertEquals(2, queue.pending.size)
        assertEquals("a2", queue.pending["A"]?.notes?.get(0))
        assertEquals("b1", queue.pending["B"]?.notes?.get(0))

        val (afterFirst, first) = queue.take()
        assertEquals("A", first?.docId)
        assertEquals("a2", first?.notes?.get(0))
        queue = afterFirst
        val (afterSecond, second) = queue.take()
        assertEquals("B", second?.docId)
        assertEquals("b1", second?.notes?.get(0))
        queue = afterSecond
        val (idle, nothing) = queue.take()
        assertNull(nothing)
        assertFalse(idle.running)
    }

    @Test
    fun coalescingDropsIntermediateStatesButWritesTheLastOne() {
        var queue = DocSaveQueue()
        val (afterA, startA) = queue.offer(state("A", "a1"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(state("A", "a2"))
        assertFalse(startB)
        queue = afterB
        val (afterC, startC) = queue.offer(state("A", "a3"))
        assertFalse(startC)
        queue = afterC

        assertEquals(1, queue.pending.size)
        val (drained, write) = queue.take()
        assertEquals("a3", write?.notes?.get(0))
        val (idle, empty) = drained.take()
        assertNull(empty)
        assertFalse(idle.running)
    }

    // ------------------------------------------------------------- open races

    @Test
    fun rapidDoubleOpenPublishesOnlyTheLastDocument() {
        val gate = OpenGenerationGate()
        val published = mutableListOf<String>()

        fun finishOpen(docId: String, token: Int, active: Boolean = true) {
            if (mayPublishOpenResult(active, token, gate.current)) published += docId
        }

        val first = gate.begin()
        val second = gate.begin()
        // The first (slow) open finishes after the second one already started.
        finishOpen("first.pdf", first)
        finishOpen("second.pdf", second)
        assertEquals(listOf("second.pdf"), published)

        // close/discard invalidates every open still in flight.
        gate.invalidate()
        val third = gate.begin()
        finishOpen("second.pdf", second)
        finishOpen("third.pdf", third)
        assertEquals(listOf("second.pdf", "third.pdf"), published)

        // A cancelled scope never publishes, even with the newest token.
        finishOpen("cancelled.pdf", third, active = false)
        assertEquals(listOf("second.pdf", "third.pdf"), published)
    }

    // ------------------------------------------------------------ export snapshot

    @Test
    fun safExportSnapshotStaysBoundToTheDocCapturedAtLaunch() {
        val launch = ViewerUiState(
            docId = "doc-a",
            docName = "a.pdf",
            pages = listOf(PageSpec(0), PageSpec(1)),
            pageCount = 2,
            notes = mapOf(0 to "launch note"),
            nightMode = true,
        )
        val captured = viewerDocState(launch)
        val snapshot = PdfExportSnapshot(
            docId = captured.docId,
            docName = captured.displayName,
            source = File("a.pdf"),
            state = captured,
            style = launch.documentStyle,
        )

        // The SAF picker stays open while a different document becomes active.
        val active = ViewerUiState(
            docId = "doc-b",
            docName = "b.pdf",
            pages = listOf(PageSpec(0)),
            pageCount = 1,
            notes = mapOf(0 to "new document note"),
        )
        assertEquals("doc-b", active.docId)
        assertFalse(mayPublishViewerResult(true, active.docId, snapshot.docId))
        assertTrue(mayPublishViewerResult(true, snapshot.docId, snapshot.docId))
        assertFalse(mayPublishViewerResult(false, snapshot.docId, snapshot.docId))

        // The snapshot still carries the launch document's payload, not the doc
        // that happens to be active when the result arrives.
        assertEquals("doc-a", snapshot.docId)
        assertEquals("a.pdf", snapshot.docName)
        assertEquals("launch note", snapshot.state.notes[0])
        assertEquals(2, snapshot.state.pages.size)
        assertTrue(snapshot.state.displayPrefs.nightMode)

        // What a result-time capture of the active viewer would have produced:
        val wrong = viewerDocState(active)
        assertEquals("doc-b", wrong.docId)
        assertEquals("new document note", wrong.notes[0])
    }

    @Test
    fun editingTheViewerAfterCaptureDoesNotMutateTheSnapshot() {
        var viewer = ViewerUiState(
            docId = "doc-a",
            docName = "a.pdf",
            pages = listOf(PageSpec(0)),
            pageCount = 1,
            notes = mapOf(0 to "before"),
        )
        val snapshot = viewerDocState(viewer)

        viewer = viewer.copy(notes = mapOf(0 to "after"), revision = viewer.revision + 1)

        assertEquals("before", snapshot.notes[0])
        assertEquals("after", viewerDocState(viewer).notes[0])
        assertEquals("doc-a", snapshot.docId)
    }
}
