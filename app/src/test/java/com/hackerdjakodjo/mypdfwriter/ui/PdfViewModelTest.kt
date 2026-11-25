package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.pdf.EraserGeometry
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

    private fun saveRequest(
        name: String,
        docId: String = "doc",
        viewerGeneration: Long = 1L,
        reportStatus: Boolean = true,
    ) = DocSaveRequest(
        state = doc(name, docId),
        viewerGeneration = viewerGeneration,
        reportStatus = reportStatus,
    )

    private fun completion(
        request: DocSaveRequest,
        succeeded: Boolean = true,
        isLatestQueued: Boolean = true,
    ) = PdfSaveCompletion(
        docId = request.state.docId,
        viewerGeneration = request.viewerGeneration,
        state = request.state,
        succeeded = succeeded,
        isLatestQueued = isLatestQueued,
        reportStatus = request.reportStatus,
    )

    private fun viewerFor(request: DocSaveRequest) = ViewerUiState(
        docId = request.state.docId,
        docName = request.state.displayName,
        pageCount = request.state.pages.size,
        pages = request.state.pages,
        notes = request.state.notes,
    )

    @Test
    fun pdfStrokeCommitPropagatesCompleteTimestampsAndReplayTimes() {
        val state = ViewerUiState(pageIndex = 2, color = 0xFF123456.toInt())
        val points = listOf(
            NormalizedPoint(0.1f, 0.2f, timeMs = 100L),
            NormalizedPoint(0.3f, 0.4f, timeMs = 200L),
            NormalizedPoint(0.5f, 0.6f, timeMs = 300L),
        )

        val stroke = pdfStrokeAnnotation(state, AnnotationType.PEN, points, 500L, nowMs = 2000L)
        val line = pdfStrokeAnnotation(state, AnnotationType.LINE, points, 500L, nowMs = 2000L)
        val partial = pdfStrokeAnnotation(
            state,
            AnnotationType.PEN,
            listOf(points.first(), points.last().copy(timeMs = null)),
            500L,
            nowMs = 2000L,
        )

        assertEquals(listOf(100L, 200L, 300L), stroke.timesMs)
        assertEquals(1500L, stroke.createdAt)
        assertEquals(500L, stroke.durationMs)
        assertEquals(2, stroke.page)
        assertEquals(listOf(points.first(), points.last()), line.points)
        assertEquals(listOf(100L, 300L), line.timesMs)
        assertNull(partial.timesMs)
    }

    // ---------------------------------------------------------------- eraser geometry

    @Test
    fun pdfAndNoteErasersUseTheSamePressureAwareRadius() {
        assertEquals(EraserGeometry.NORMALIZED_RADIUS, NOTE_ERASE_RADIUS, 0f)
        listOf(0f, 0.5f, 1f).forEach { pressure ->
            listOf(false, true).forEach { sensitive ->
                assertEquals(
                    EraserGeometry.radiusForPressure(NOTE_ERASE_RADIUS, pressure, sensitive),
                    pdfEraserRadius(pressure, sensitive),
                    0f,
                )
            }
        }
        assertEquals(NOTE_ERASE_RADIUS, pdfEraserRadius(0f, false), 0f)
    }

    @Test
    fun pdfPolylineEraserDetectsCrossingsBetweenSparseSamples() {
        val stroke = listOf(
            NormalizedPoint(0.5f, 0.1f),
            NormalizedPoint(0.5f, 0.9f),
        )
        val trail = listOf(
            NormalizedPoint(0.1f, 0.5f),
            NormalizedPoint(0.9f, 0.5f),
        )
        val types = listOf(
            AnnotationType.PEN,
            AnnotationType.HIGHLIGHT,
            AnnotationType.LINE,
            AnnotationType.ARROW,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.SIGNATURE,
        )
        types.forEach { type ->
            val annotation = annotation("crossing-$type", 0).copy(type = type, points = stroke)
            assertTrue(type.name, pdfEraserHitsAnnotation(annotation, trail, pdfEraserRadius(1f, false)))
            assertFalse(
                type.name,
                pdfEraserHitsAnnotation(
                    annotation,
                    listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.9f, 0.2f)),
                    pdfEraserRadius(1f, false),
                ),
            )
        }
    }

    @Test
    fun pdfShapeEraserUsesBorderSegmentsRatherThanOnlyCornerSamples() {
        val rect = annotation("rect", 0).copy(
            type = AnnotationType.RECT,
            points = listOf(NormalizedPoint(0.3f, 0.4f), NormalizedPoint(0.7f, 0.6f)),
        )
        val crossing = listOf(NormalizedPoint(0.5f, 0.1f), NormalizedPoint(0.5f, 0.9f))
        val throughInside = listOf(NormalizedPoint(0.1f, 0.5f), NormalizedPoint(0.9f, 0.5f))

        assertTrue(pdfEraserHitsAnnotation(rect, crossing, pdfEraserRadius(1f, false)))
        assertFalse(pdfEraserHitsAnnotation(rect, throughInside, pdfEraserRadius(1f, false)))
    }

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
    fun undoOfAPageEditRestoresItsOriginalNotesAndCards() {
        val before = listOf(PageSpec(0), PageSpec(1), PageSpec(2))
        val notesBefore = mapOf(1 to "b", 2 to "c")
        val cardsBefore = listOf(card("c", 1))
        val state = ViewerUiState(
            docId = "doc",
            pages = before.dropLast(1),
            pageCount = 2,
            pageIndex = 0,
            notes = mapOf(1 to "c"),
            memoCards = emptyList(),
        )
        val snapshot = DocSnapshot(
            pages = before,
            annotations = emptyList(),
            pageIndex = 1,
            notes = notesBefore,
            memoCards = cardsBefore,
            bookmarks = listOf(com.hackerdjakodjo.mypdfwriter.data.PdfBookmark(pageIndex = 1, title = "b")),
        )
        val restored = restoreSnapshotInto(state, snapshot, true, false)
        assertEquals(notesBefore, restored.notes)
        assertEquals(cardsBefore, restored.memoCards)
        assertEquals("b", restored.noteDraft)
        assertEquals(3, restored.pageCount)
        assertEquals(1, snapshot.bookmarks?.size)
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

        val (afterA, startA) = queue.offer(saveRequest("a"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(saveRequest("b"))
        assertFalse(startB)
        queue = afterB
        val (afterC, startC) = queue.offer(saveRequest("c"))
        assertFalse(startC)
        queue = afterC

        val (drained, firstWrite) = queue.take()
        assertEquals("c", firstWrite?.state?.notes?.get(0))
        assertTrue(drained.isLatest(firstWrite!!))
        queue = drained
        val (idle, secondWrite) = queue.take()
        assertNull(secondWrite)
        assertFalse(idle.running)
        queue = idle

        // A later edit starts a fresh single writer after the previous drain.
        val (again, restart) = queue.offer(saveRequest("d"))
        assertTrue(restart)
        val (_, lastWrite) = again.take()
        assertEquals("d", lastWrite?.state?.notes?.get(0))
    }

    @Test
    fun offerKeepsPendingStatesForDistinctDocIds() {
        var queue = DocSaveQueue()
        val (afterA, startA) = queue.offer(saveRequest("a", docId = "A"))
        assertTrue(startA)
        queue = afterA
        val (afterB, startB) = queue.offer(saveRequest("b", docId = "B"))
        assertFalse(startB)
        queue = afterB
        // A later edit to A overwrites only A's entry, never B's.
        val (afterA2, startA2) = queue.offer(saveRequest("a2", docId = "A"))
        assertFalse(startA2)
        queue = afterA2
        assertEquals(2, queue.pending.size)

        // The drain loop takes exactly one document per iteration.
        val (afterFirst, first) = queue.take()
        assertEquals("A", first?.state?.docId)
        assertEquals("a2", first?.state?.notes?.get(0))
        assertEquals(1, afterFirst.pending.size)
        assertTrue(afterFirst.isLatest(first!!))
        queue = afterFirst

        val (afterSecond, second) = queue.take()
        assertEquals("B", second?.state?.docId)
        assertEquals("b", second?.state?.notes?.get(0))
        assertTrue(afterSecond.pending.isEmpty())
        queue = afterSecond

        val (idle, nothing) = queue.take()
        assertNull(nothing)
        assertFalse(idle.running)
    }

    @Test
    fun clearingPendingKeepsSequenceMonotonicForInFlightResults() {
        var queue = DocSaveQueue()
        val (offered, _) = queue.offer(saveRequest("old"))
        queue = offered
        val (takenQueue, oldInFlight) = queue.take()
        queue = takenQueue

        val cleared = DocSaveQueue(nextSequence = queue.nextSequence)
        val (reopened, _) = cleared.offer(saveRequest("fresh", viewerGeneration = 2L))
        assertFalse(reopened.isLatest(oldInFlight!!))
        assertEquals(queue.nextSequence + 1L, reopened.nextSequence)
    }

    // ---------------------------------------------------------------- save status

    @Test
    fun saveStatusEnumAndFreshViewerStartIdle() {
        assertEquals(
            listOf(PdfSaveStatus.IDLE, PdfSaveStatus.SAVING, PdfSaveStatus.SAVED, PdfSaveStatus.FAILED),
            PdfSaveStatus.values().toList(),
        )
        assertEquals(PdfSaveStatus.IDLE, ViewerUiState(docId = "fresh").saveStatus)
    }

    @Test
    fun changedStateShowsSavingAndOnlyVerifiedSuccessShowsSaved() {
        var queue = DocSaveQueue()
        val request = saveRequest("changed")
        val (offered, _) = queue.offer(request)
        queue = offered
        var viewer = viewerFor(request).onPdfSaveQueued()

        // Enqueueing and taking the request are not IO success.
        assertEquals(PdfSaveStatus.SAVING, viewer.saveStatus)
        val (takenQueue, taken) = queue.take()
        queue = takenQueue
        assertTrue(queue.isLatest(taken!!))
        assertEquals(PdfSaveStatus.SAVING, viewer.saveStatus)

        viewer = viewer.applyPdfSaveCompletion(1L, completion(taken))!!
        assertEquals(PdfSaveStatus.SAVED, viewer.saveStatus)
    }

    @Test
    fun coalescedWritesStaySavingUntilTheNewestStateIsDurable() {
        var queue = DocSaveQueue()
        val first = saveRequest("first")
        val (afterFirstOffer, _) = queue.offer(first)
        queue = afterFirstOffer
        val (afterFirstTake, inFlight) = queue.take()
        queue = afterFirstTake

        val latest = saveRequest("latest")
        val (afterLatestOffer, _) = queue.offer(latest)
        queue = afterLatestOffer
        var viewer = viewerFor(latest).onPdfSaveQueued()

        // The first write really succeeded, but a newer state is already queued.
        val firstWrite = inFlight!!
        viewer = viewer.applyPdfSaveCompletion(
            1L,
            completion(firstWrite, isLatestQueued = queue.isLatest(firstWrite)),
        )!!
        assertEquals(PdfSaveStatus.SAVING, viewer.saveStatus)

        val (afterLatestTake, latestWrite) = queue.take()
        queue = afterLatestTake
        val newestWrite = latestWrite!!
        viewer = viewer.applyPdfSaveCompletion(
            1L,
            completion(newestWrite, isLatestQueued = queue.isLatest(newestWrite)),
        )!!
        assertEquals(PdfSaveStatus.SAVED, viewer.saveStatus)
    }

    @Test
    fun failedCoalescedAttemptDoesNotOverrideTheNewestSave() {
        var queue = DocSaveQueue()
        val (afterFirstOffer, _) = queue.offer(saveRequest("first"))
        queue = afterFirstOffer
        val (afterFirstTake, inFlight) = queue.take()
        queue = afterFirstTake
        val latest = saveRequest("latest")
        val (afterLatestOffer, _) = queue.offer(latest)
        queue = afterLatestOffer
        var viewer = viewerFor(latest).onPdfSaveQueued()

        val firstWrite = inFlight!!
        viewer = viewer.applyPdfSaveCompletion(
            1L,
            completion(firstWrite, succeeded = false, isLatestQueued = queue.isLatest(firstWrite)),
        )!!
        assertEquals(PdfSaveStatus.SAVING, viewer.saveStatus)

        val (afterLatestTake, latestWrite) = queue.take()
        queue = afterLatestTake
        val newestWrite = latestWrite!!
        viewer = viewer.applyPdfSaveCompletion(
            1L,
            completion(newestWrite, isLatestQueued = queue.isLatest(newestWrite)),
        )!!
        assertEquals(PdfSaveStatus.SAVED, viewer.saveStatus)
    }

    @Test
    fun latestWriteFailureShowsFailedAndRetryReturnsToSavingThenSaved() {
        val request = saveRequest("changed")
        var viewer = viewerFor(request).onPdfSaveQueued()
        viewer = viewer.applyPdfSaveCompletion(1L, completion(request, succeeded = false))!!
        assertEquals(PdfSaveStatus.FAILED, viewer.saveStatus)

        // retrySave() re-offers this same current state and forces a new visible cycle.
        viewer = viewer.onPdfSaveQueued()
        assertEquals(PdfSaveStatus.SAVING, viewer.saveStatus)
        viewer = viewer.applyPdfSaveCompletion(1L, completion(request))!!
        assertEquals(PdfSaveStatus.SAVED, viewer.saveStatus)
    }

    @Test
    fun staleDocumentAndSameIdReopenCompletionsCannotRelabelTheViewer() {
        val oldRequest = saveRequest("old", docId = "same", viewerGeneration = 1L)
        val otherViewer = ViewerUiState(docId = "other", saveStatus = PdfSaveStatus.SAVING)
        assertSame(
            otherViewer,
            otherViewer.applyPdfSaveCompletion(2L, completion(oldRequest)),
        )

        val reopenedViewer = ViewerUiState(docId = "same", saveStatus = PdfSaveStatus.IDLE)
        assertSame(
            reopenedViewer,
            reopenedViewer.applyPdfSaveCompletion(2L, completion(oldRequest)),
        )
        val noViewer: ViewerUiState? = null
        assertNull(noViewer.applyPdfSaveCompletion(2L, completion(oldRequest)))
    }

    @Test
    fun freshOpenIgnoresInitialSidecarSuccessAndOldCompletion() {
        val initial = saveRequest("loaded", reportStatus = false)
        val fresh = viewerFor(initial)
        assertSame(fresh, fresh.applyPdfSaveCompletion(1L, completion(initial)))

        val stale = saveRequest("old edit", viewerGeneration = 1L)
        assertSame(fresh, fresh.applyPdfSaveCompletion(2L, completion(stale)))
        assertEquals(PdfSaveStatus.IDLE, fresh.saveStatus)
    }

    @Test
    fun mismatchedCurrentStateNeverClaimsSaved() {
        val request = saveRequest("captured")
        val viewer = ViewerUiState(docId = "doc", notes = mapOf(0 to "newer")).onPdfSaveQueued()

        val updated = viewer.applyPdfSaveCompletion(1L, completion(request))!!

        assertEquals(PdfSaveStatus.SAVING, updated.saveStatus)
    }

    @Test
    fun olderCompletionCannotDragAnAlreadySavedNewerStateBackToSaving() {
        val viewer = ViewerUiState(docId = "doc", saveStatus = PdfSaveStatus.SAVED)
        val stale = saveRequest("older")

        val updated = viewer.applyPdfSaveCompletion(
            1L,
            completion(stale, isLatestQueued = false),
        )

        assertSame(viewer, updated)
        assertEquals(PdfSaveStatus.SAVED, updated?.saveStatus)
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
