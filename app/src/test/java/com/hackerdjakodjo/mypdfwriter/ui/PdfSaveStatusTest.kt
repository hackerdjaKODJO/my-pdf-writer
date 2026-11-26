package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure durability transitions and stale-session guards; no Android ViewModel needed. */
class PdfSaveStatusTest {

    private val state = DocState(
        docId = "doc",
        displayName = "document.pdf",
        pages = listOf(PageSpec(0)),
        annotations = emptyList(),
        notes = emptyMap(),
    )

    private fun completion(
        docId: String = "doc",
        viewerGeneration: Long = 1L,
        succeeded: Boolean = true,
        isLatestQueued: Boolean = true,
        reportStatus: Boolean = true,
    ) = PdfSaveCompletion(
        docId = docId,
        viewerGeneration = viewerGeneration,
        state = state,
        succeeded = succeeded,
        isLatestQueued = isLatestQueued,
        reportStatus = reportStatus,
    )

    @Test
    fun enumHasOnlyTheFourPublicDurabilityStates() {
        assertEquals(
            listOf(PdfSaveStatus.IDLE, PdfSaveStatus.SAVING, PdfSaveStatus.SAVED, PdfSaveStatus.FAILED),
            PdfSaveStatus.values().toList(),
        )
        assertEquals(PdfSaveStatus.IDLE, ViewerUiState().saveStatus)
    }

    @Test
    fun queueingAChangedStateAlwaysMeansSaving() {
        assertEquals(PdfSaveStatus.SAVING, ViewerUiState().onPdfSaveQueued().saveStatus)
        assertEquals(
            PdfSaveStatus.SAVING,
            ViewerUiState(saveStatus = PdfSaveStatus.SAVED).onPdfSaveQueued().saveStatus,
        )
        assertEquals(
            PdfSaveStatus.SAVING,
            ViewerUiState(saveStatus = PdfSaveStatus.FAILED).onPdfSaveQueued().saveStatus,
        )
        val alreadySaving = ViewerUiState(saveStatus = PdfSaveStatus.SAVING)
        assertSame(alreadySaving, alreadySaving.onPdfSaveQueued())
    }

    @Test
    fun onlyCurrentLatestSuccessOrFailurePublishesATerminalState() {
        assertEquals(
            PdfSaveStatus.SAVED,
            pdfSaveStatusAfterCompletion(
                current = PdfSaveStatus.SAVING,
                canPublish = true,
                isLatestQueued = true,
                currentStateMatches = true,
                succeeded = true,
            ),
        )
        assertEquals(
            PdfSaveStatus.FAILED,
            pdfSaveStatusAfterCompletion(
                current = PdfSaveStatus.SAVING,
                canPublish = true,
                isLatestQueued = true,
                currentStateMatches = true,
                succeeded = false,
            ),
        )
        assertEquals(
            PdfSaveStatus.SAVING,
            pdfSaveStatusAfterCompletion(
                current = PdfSaveStatus.SAVING,
                canPublish = true,
                isLatestQueued = true,
                currentStateMatches = false,
                succeeded = true,
            ),
        )
    }

    @Test
    fun supersededCompletionLeavesTheNewerResultUntouched() {
        assertEquals(
            PdfSaveStatus.SAVING,
            pdfSaveStatusAfterCompletion(
                current = PdfSaveStatus.SAVING,
                canPublish = true,
                isLatestQueued = false,
                currentStateMatches = true,
                succeeded = true,
            ),
        )
        assertEquals(
            PdfSaveStatus.SAVED,
            pdfSaveStatusAfterCompletion(
                current = PdfSaveStatus.SAVED,
                canPublish = true,
                isLatestQueued = false,
                currentStateMatches = true,
                succeeded = false,
            ),
        )
    }

    @Test
    fun completionGuardRequiresBothDocumentAndViewerGeneration() {
        val current = completion()
        assertTrue(canPublishPdfSaveCompletion("doc", 1L, current))
        assertFalse(canPublishPdfSaveCompletion("other", 1L, current))
        assertFalse(canPublishPdfSaveCompletion(null, 1L, current))
        assertFalse(canPublishPdfSaveCompletion("doc", 2L, current))
    }

    @Test
    fun initialAndStaleCompletionsDoNotChangeTheViewer() {
        val viewer = ViewerUiState(docId = "doc", notes = state.notes)
        assertSame(viewer, viewer.applyPdfSaveCompletion(1L, completion(reportStatus = false)))
        assertSame(viewer, viewer.applyPdfSaveCompletion(1L, completion(docId = "other")))
        assertSame(viewer, viewer.applyPdfSaveCompletion(2L, completion(viewerGeneration = 1L)))
        assertEquals(PdfSaveStatus.IDLE, viewer.saveStatus)
    }

    @Test
    fun savedConfirmationIsTransientButSavingAndFailurePersist() {
        assertTrue(pdfSaveStatusVisible(PdfSaveStatus.SAVING, Long.MAX_VALUE))
        assertTrue(pdfSaveStatusVisible(PdfSaveStatus.FAILED, Long.MAX_VALUE))
        assertTrue(pdfSaveStatusVisible(PdfSaveStatus.SAVED, 0L))
        assertTrue(pdfSaveStatusVisible(PdfSaveStatus.SAVED, PDF_SAVED_STATUS_VISIBLE_MS - 1))
        assertFalse(pdfSaveStatusVisible(PdfSaveStatus.SAVED, PDF_SAVED_STATUS_VISIBLE_MS))
    }
}
