package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState

/**
 * Small pure policies shared by the UX-consistency pass. Keeping these out of
 * composables makes the cross-screen rules unit-testable and stops the same
 * decision drifting between the PDF reader, the note editor and the home hub.
 */

/** Back clears a transient selection before it leaves a work surface, like the PDF viewer. */
internal enum class NoteBackStep { CLEAR_SELECTION, LEAVE_EDITOR }

internal fun noteBackStep(hasSelection: Boolean): NoteBackStep =
    if (hasSelection) NoteBackStep.CLEAR_SELECTION else NoteBackStep.LEAVE_EDITOR

/** System Back closes the smallest scope first: pane, then selection, then document. */
internal enum class ViewerBackTarget { CLOSE_STUDY_PANE, CLEAR_SELECTION, CLOSE_DOCUMENT }

internal fun viewerBackTarget(hasStudyPane: Boolean, hasSelection: Boolean): ViewerBackTarget =
    when {
        hasStudyPane -> ViewerBackTarget.CLOSE_STUDY_PANE
        hasSelection -> ViewerBackTarget.CLEAR_SELECTION
        else -> ViewerBackTarget.CLOSE_DOCUMENT
    }

/** One canonical talk-back state name for the four masking-card states. */
internal fun maskingStateLabel(state: MaskingCardState): String = when (state) {
    MaskingCardState.HIDDEN -> "안 봄"
    MaskingCardState.REVEALED -> "정답 확인"
    MaskingCardState.CORRECT -> "정답"
    MaskingCardState.WRONG -> "오답"
}

/** The same bookmark toggle is named identically in the hub, sheet and split chrome. */
internal const val BOOKMARK_TOGGLE_LABEL = "현재 페이지 북마크"
internal const val BOOKMARK_TOGGLE_REMOVE_LABEL = "현재 페이지 북마크 해제"

/** Multiple PDFs shared at once: open the first and tell the student why. */
internal const val MULTI_IMPORT_NOTICE = "여러 PDF는 한 번에 한 개씩 열 수 있습니다."

/** Import actions that carry a PDF URI. SEND_MULTIPLE must not be silently ignored. */
internal val pdfImportActions = setOf(
    "android.intent.action.VIEW",
    "android.intent.action.SEND",
    "android.intent.action.SEND_MULTIPLE",
)

/** Whether more than one URI came with an import intent. */
internal fun isMultiImport(uris: Int): Boolean = uris > 1
