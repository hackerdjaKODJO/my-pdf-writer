package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the pure policies behind PdfViewModel's undo, save and
 * cancellation fixes. No Android ViewModel instance is needed.
 */
class PdfViewModelTest {

    private fun annotation(id: String, page: Int) = Annotation(
        id = id,
        type = AnnotationType.PEN,
        page = page,
        color = 0,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.5f, 0.5f)),
    )

    private fun card(id: String, page: Int) = MemoCard(
        id = id,
        page = page,
        text = "카드 $id",
        tint = MemoTint.YELLOW,
    )

    private fun doc(name: String, docId: String = "doc") = DocState(
        docId = docId,
        displayName = "$name.pdf",
        pages = listOf(PageSpec(0)),
        annotations = emptyList(),
        notes = mapOf(0 to name),
    )

    // ---------------------------------------------------------------- undo

    @Test
    fun undoSnapshotKeepsCurrentNotesAndMemoCardsAndRebuildsDraft() {
        val pages = listOf(PageSpec(0), PageSpec(1))
        val state = ViewerUiState(
            docId = "doc",
            pages = pages,
            pageCount = 2,
            pageIndex = 0,
            notes = mapOf(1 to "나중에 쓴 메모"),
            memoCards = listOf(card("c", 1)),
            noteDraft = "outdated draft",
        )
        // A snapshot taken before the note/card edits has no note or card payload.
        val snapshot = DocSnapshot(
            pages = pages,
            annotations = emptyList(),
            pageIndex = 1,
        )

        val restored = restoreSnapshotInto(state, snapshot, canUndo = true, canRedo = false)

        assertTrue(restored.annotations.isEmpty())
        assertEquals(state.notes, restored.notes)
        assertEquals(state.memoCards, restored.memoCards)
        assertEquals("나중에 쓴 메모", restored.noteDraft)
        assertEquals(1, restored.pageIndex)
        assertEquals(2, restored.pageCount)
        assertTrue(restored.canUndo)
        assertFalse(restored.canRedo)
    }

    @Test
    fun undoToAPageWithoutANoteClearsTheDraft() {
        val pages = listOf(PageSpec(0))
        val state = ViewerUiState(
            pages = pages,
            pageCount = 1,
            notes = mapOf(1 to "다른 페이지 메모"),
            noteDraft = "stale",
        )
        val snapshot = DocSnapshot(pages, emptyList(), pageIndex = 0)

        assertEquals("", restoreSnapshotInto(state, snapshot, false, true).noteDraft)
        assertTrue(restoreSnapshotInto(state, snapshot, false, true).canRedo)
    }

    // ---------------------------------------------------------------- save coalescing

    @Test
    fun coalescingWriterSkipsIntermediateStatesButAlwaysWritesTheNewest() {
        var queue = DocSaveQueue()
        assertFalse(queue.running)
        assertTrue(queue.pending.isEmpty())

        val (afterA, startA) = queue.offer(doc("a"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(doc("b"))
        assertFalse(startB)
        queue = afterB
        val (afterC, startC) = queue.offer(doc("c"))
        assertFalse(startC)
        queue = afterC

        val (drained, firstWrite) = queue.take()
        assertEquals("c", firstWrite?.notes?.get(0))
        queue = drained
        val (idle, secondWrite) = queue.take()
        assertNull(secondWrite)
        assertFalse(idle.running)
        queue = idle

        // A later edit starts a fresh single writer after the previous drain.
        val (again, restart) = queue.offer(doc("d"))
        assertTrue(restart)
        val (_, lastWrite) = again.take()
        assertEquals("d", lastWrite?.notes?.get(0))
    }

    @Test
    fun offerKeepsPendingStatesForDistinctDocIds() {
        var queue = DocSaveQueue()
        val (afterA, startA) = queue.offer(doc("a", docId = "A"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(doc("b", docId = "B"))
        assertFalse(startB)
        queue = afterB
        // A later edit to A overwrites only A's entry, never B's.
        val (afterA2, startA2) = queue.offer(doc("a2", docId = "A"))
        assertFalse(startA2)
        queue = afterA2
        assertEquals(2, queue.pending.size)

        // The drain loop takes exactly one document per iteration.
        val (afterFirst, first) = queue.take()
        assertEquals("A", first?.docId)
        assertEquals("a2", first?.notes?.get(0))
        assertEquals(1, afterFirst.pending.size)
        queue = afterFirst

        val (afterSecond, second) = queue.take()
        assertEquals("B", second?.docId)
        assertEquals("b", second?.notes?.get(0))
        assertTrue(afterSecond.pending.isEmpty())
        queue = afterSecond

        val (idle, nothing) = queue.take()
        assertNull(nothing)
        assertFalse(idle.running)
    }

    // ---------------------------------------------------------------- open generation

    @Test
    fun rapidDoubleOpenPublishesOnlyLastDocument() {
        val gate = OpenGenerationGate()
        val published = mutableListOf<String>()

        fun finishOpen(docId: String, token: Int, isActive: Boolean = true) {
            if (mayPublishOpenResult(isActive, token, gate.current)) published += docId
        }

        val first = gate.begin()
        val second = gate.begin()
        // The first (slow) open finishes after the second one started.
        finishOpen("first.pdf", first)
        finishOpen("second.pdf", second)
        assertEquals(listOf("second.pdf"), published)

        // close/discard invalidates everything still in flight.
        gate.invalidate()
        val third = gate.begin()
        finishOpen("second.pdf", second)
        finishOpen("third.pdf", third)
        assertEquals(listOf("second.pdf", "third.pdf"), published)

        // A cancelled scope never publishes, even with the newest token.
        finishOpen("cancelled.pdf", third, isActive = false)
        assertEquals(listOf("second.pdf", "third.pdf"), published)
    }

    // ---------------------------------------------------------------- cancellation + doc guard

    @Test
    fun cancellationIsRethrownInsteadOfBecomingAnError() {
        val cancelled = CancellationException("page flipped")
        val thrown = assertThrows(CancellationException::class.java) { rethrowIfCancelled(cancelled) }
        assertSame(cancelled, thrown)
    }

    @Test
    fun ordinaryFailuresAreSwallowedByTheRethrowPolicy() {
        rethrowIfCancelled(IllegalStateException("bad pdf"))
    }

    @Test
    fun staleDocumentResultsAreNotPublished() {
        assertTrue(mayPublishViewerResult(isActive = true, currentDocId = "doc", docId = "doc"))
        assertFalse(mayPublishViewerResult(isActive = true, currentDocId = "other", docId = "doc"))
        assertFalse(mayPublishViewerResult(isActive = true, currentDocId = null, docId = "doc"))
        assertFalse(mayPublishViewerResult(isActive = false, currentDocId = "doc", docId = "doc"))
    }

    // ---------------------------------------------------------------- extraction

    @Test
    fun extractionDropsAnnotationsOnPagesThatWereNotExtracted() {
        val annotations = listOf(
            annotation("a", 1),
            annotation("stale", 3),
            annotation("b", 5),
            annotation("c", 9),
        )
        val ordered = listOf(1, 5, 9) // page 3 is a stale legacy page: filtered out

        val result = remapExtractedAnnotations(annotations, ordered)

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.page })
    }

    @Test
    fun extractionHandlesAnEmptySelection() {
        assertTrue(remapExtractedAnnotations(listOf(annotation("a", 0)), emptyList()).isEmpty())
    }

    @Test
    fun linkCacheKeyDistinguishesDocumentsPagesAndLayouts() {
        val file = java.io.File("/tmp/lecture.pdf")
        val base = linkCacheKey("doc1", file, 3, 0)
        assertEquals(base, linkCacheKey("doc1", file, 3, 0))
        assertTrue(base != linkCacheKey("doc2", file, 3, 0))
        assertTrue(base != linkCacheKey("doc1", file, 4, 0))
        assertTrue(base != linkCacheKey("doc1", file, 3, 90))
    }
}
