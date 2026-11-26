package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.DocState

/** Durability of the active document's latest auto-save, never of a queued write. */
enum class PdfSaveStatus {
    /** No user-visible document change is awaiting a result in this viewer session. */
    IDLE,

    /** A changed state is queued or in flight; the latest state is not durable yet. */
    SAVING,

    /** The latest queued state for this viewer session was read back successfully. */
    SAVED,

    /** The latest write for this viewer session failed; [com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel.retrySave] can re-offer it. */
    FAILED,
}

/** One completed IO attempt, bound to the viewer session that queued it. */
internal data class PdfSaveCompletion(
    val docId: String,
    val viewerGeneration: Long,
    val state: DocState,
    val succeeded: Boolean,
    val isLatestQueued: Boolean,
    val reportStatus: Boolean,
)

/** A same-id reopen is a new viewer session, so its older writes are stale too. */
internal fun canPublishPdfSaveCompletion(
    currentDocId: String?,
    currentViewerGeneration: Long,
    completion: PdfSaveCompletion,
): Boolean = currentDocId == completion.docId &&
    currentViewerGeneration == completion.viewerGeneration

/**
 * A superseded write is not a user-visible result: coalescing keeps the indicator
 * saving until the newest state is durable. Only a current, latest attempt may
 * publish a terminal state.
 */
internal fun pdfSaveStatusAfterCompletion(
    current: PdfSaveStatus,
    canPublish: Boolean,
    isLatestQueued: Boolean,
    currentStateMatches: Boolean,
    succeeded: Boolean,
): PdfSaveStatus = when {
    !canPublish -> current
    // A newer request owns the result. In particular, an older completion must
    // not drag an already-published SAVED state back to SAVING.
    !isLatestQueued -> current
    !currentStateMatches -> PdfSaveStatus.SAVING
    succeeded -> PdfSaveStatus.SAVED
    else -> PdfSaveStatus.FAILED
}

internal fun ViewerUiState.onPdfSaveQueued(): ViewerUiState =
    if (saveStatus == PdfSaveStatus.SAVING) this else copy(saveStatus = PdfSaveStatus.SAVING)

/** A successful auto-save is a transient confirmation, not permanent chrome. */
internal const val PDF_SAVED_STATUS_VISIBLE_MS = 2_500L

internal fun pdfSaveStatusVisible(status: PdfSaveStatus, savedAgeMs: Long): Boolean = when (status) {
    PdfSaveStatus.SAVED -> savedAgeMs in 0 until PDF_SAVED_STATUS_VISIBLE_MS
    else -> true
}

/** Applies an IO result without letting a stale document relabel the active viewer. */
internal fun ViewerUiState?.applyPdfSaveCompletion(
    currentViewerGeneration: Long,
    completion: PdfSaveCompletion,
): ViewerUiState? {
    if (this == null || !completion.reportStatus) return this
    if (!canPublishPdfSaveCompletion(docId, currentViewerGeneration, completion)) return this
    val status = pdfSaveStatusAfterCompletion(
        current = saveStatus,
        canPublish = true,
        isLatestQueued = completion.isLatestQueued,
        currentStateMatches = viewerDocState(this) == completion.state,
        succeeded = completion.succeeded,
    )
    return if (status == saveStatus) this else copy(saveStatus = status)
}
