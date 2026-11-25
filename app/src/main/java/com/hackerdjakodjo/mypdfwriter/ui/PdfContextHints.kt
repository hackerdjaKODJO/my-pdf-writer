package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool

/**
 * The single contextual hint allowed over the PDF page at a time.
 *
 * This is deliberately a pure, Compose-free policy: the viewer derives one
 * value from its state and the UI only decides where to draw it. Keeping the
 * decision here makes the first-use guidance testable on the JVM and prevents
 * two overlapping hints from being composed at once.
 */
internal enum class PdfContextHint {
    /** Nothing to teach right now (selection is active, words are absent, ...). */
    NONE,

    /** VIEW mode: long-press selects a word; the dock toggle selects a range. */
    VIEW_SELECTION_HINT,

    /** Word geometry is still being extracted. */
    LOADING,

    /** No extractable words: scanned/empty page, offer the local OCR tools. */
    NO_TEXT,

    /** Words are available in selection mode; the existing action bar explains it. */
    SELECTED_HINT,

    /** Non-view drawing/stamp tool keeps its existing one-line tool hint. */
    TOOL_HINT,
}

internal data class PdfContextHintState(
    val tool: Tool = Tool.VIEW,
    val textSelectMode: Boolean = false,
    val hasSelection: Boolean = false,
    val textLoading: Boolean = false,
    val hasWords: Boolean = false,
    val dismissed: Boolean = false,
)

/**
 * Resolves viewer state into exactly one hint. An active selection and an
 * explicit dismissal always win; selection mode then reports LOADING / NO_TEXT /
 * SELECTED_HINT, while VIEW only teaches gesture selection once words exist.
 */
internal fun pdfContextHint(state: PdfContextHintState): PdfContextHint = when {
    state.dismissed || state.hasSelection -> PdfContextHint.NONE
    state.textSelectMode -> when {
        state.textLoading -> PdfContextHint.LOADING
        !state.hasWords -> PdfContextHint.NO_TEXT
        else -> PdfContextHint.SELECTED_HINT
    }
    state.tool == Tool.VIEW -> if (state.hasWords) {
        PdfContextHint.VIEW_SELECTION_HINT
    } else {
        PdfContextHint.NONE
    }
    else -> PdfContextHint.TOOL_HINT
}

internal const val PDF_HINT_VIEW_TEXT = "길게 눌러 단어 선택 · 하단 텍스트 선택으로 범위 선택"
internal const val PDF_HINT_PAGE_NAVIGATION_TEXT = "위쪽 페이지 이동"
internal const val PDF_HINT_VIEW_ZOOM_TEXT = "보기 도구에서 두 손가락·더블 탭으로 확대"
internal const val PDF_HINT_STYLE_UNDO_TEXT = "스타일·실행 취소는 도구 독 위 버튼에"
internal const val PDF_HINT_LOADING_TEXT = "텍스트 위치를 읽는 중…"
internal const val PDF_HINT_NO_TEXT = "선택할 텍스트가 없습니다 · OCR로 텍스트를 추가하세요"
internal const val PDF_HINT_SELECTED_TEXT = "단어를 탭하거나 드래그해 선택하세요"
internal const val PDF_HINT_OCR_ACTION = "OCR 도구"
internal const val SEARCH_EMPTY_OCR_TIP = "검색어를 줄이거나 OCR로 텍스트를 추가하세요"

/** Visible label for the PDF search field, not just its placeholder. */
internal const val PDF_SEARCH_FIELD_LABEL = "검색어"

/** Spoken reason for markup actions while no text is selected. */
internal const val PDF_SELECTION_REQUIRED_REASON = "선택한 텍스트 없음"

/** Local, offline strings; [toolHint] comes from the existing tool-hint resource. */
internal fun pdfContextHintText(hint: PdfContextHint, toolHint: String = ""): String = when (hint) {
    PdfContextHint.VIEW_SELECTION_HINT -> PDF_HINT_VIEW_TEXT
    PdfContextHint.LOADING -> PDF_HINT_LOADING_TEXT
    PdfContextHint.NO_TEXT -> PDF_HINT_NO_TEXT
    PdfContextHint.SELECTED_HINT -> PDF_HINT_SELECTED_TEXT
    PdfContextHint.TOOL_HINT -> toolHint
    PdfContextHint.NONE -> ""
}

/**
 * Minimum height (dp) of the PDF strip in split mode before an overlay hint is
 * allowed. The default 85% study pane leaves roughly 15% of the screen, which
 * is too small for a chip plus the floating page controls.
 */
internal const val PDF_HINT_MIN_SPLIT_STRIP_DP = 140f

/**
 * Decides whether the chip is drawn at all. The selection action bar already
 * narrates LOADING and SELECTED_HINT, so those are suppressed while it is
 * visible; NO_TEXT is the exception because only the chip carries the OCR
 * shortcut. Search result stepping owns the top-end corner while it is shown.
 */
internal fun pdfContextHintChipVisible(
    hint: PdfContextHint,
    selectionBarVisible: Boolean,
    searchNavigationVisible: Boolean,
    splitMode: Boolean,
    pdfStripHeightDp: Float,
): Boolean {
    if (hint == PdfContextHint.NONE || searchNavigationVisible) return false
    if (selectionBarVisible && hint != PdfContextHint.NO_TEXT) return false
    if (splitMode && (!pdfStripHeightDp.isFinite() || pdfStripHeightDp < PDF_HINT_MIN_SPLIT_STRIP_DP)) {
        return false
    }
    return true
}
