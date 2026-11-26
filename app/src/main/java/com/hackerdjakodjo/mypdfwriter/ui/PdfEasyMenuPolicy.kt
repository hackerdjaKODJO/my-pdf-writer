package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Session-only progressive disclosure for PDF 메뉴. The easy sheet intentionally
 * contains exactly six everyday tasks; every existing power action stays in the
 * expert hub reached from its footer. Nothing here is persisted.
 */
internal enum class PdfEasyTask(
    val label: String,
    val menuAction: PdfMenuAction,
    val helpTopic: HelpTopicKey?,
) {
    STUDY_NOTE("학습 노트", PdfMenuAction.CAPTURE, HelpTopicKey.STUDY_NOTE),
    PAGE_MEMO("페이지 메모", PdfMenuAction.PAGE_NOTES, HelpTopicKey.WRITE_TOOLS),
    BOOKMARK("현재 페이지 북마크", PdfMenuAction.BOOKMARK_TOGGLE, HelpTopicKey.OPEN_PDF),
    EXPORT("PDF 내보내기", PdfMenuAction.EXPORT, HelpTopicKey.BACKUP),
    SHARE("공유", PdfMenuAction.SHARE, null),
    HELP("도움말", PdfMenuAction.HELP, HelpTopicKey.QUICK_START),
}

/** State-aware row label; bookmark toggling is not one-way. */
internal fun pdfEasyTaskLabel(task: PdfEasyTask, bookmarked: Boolean): String =
    if (task == PdfEasyTask.BOOKMARK && bookmarked) {
        "현재 페이지 북마크 해제"
    } else {
        task.label
    }

/** The one ordered source of truth for the six easy-menu rows. */
internal val pdfEasyMenuTasks: List<PdfEasyTask> = PdfEasyTask.entries.toList()

internal const val PDF_EASY_MENU_ALL_TOOLS_LABEL = "전체 도구 보기"

/**
 * Complete expert inventory, including the sub-sheets and the one action owned by
 * the top bar. Keeping this derived from the existing hub lists makes a dropped
 * power action fail the policy test instead of disappearing during the handoff.
 */
internal val pdfEasyMenuExpertActions: Set<PdfMenuAction> = buildSet {
    addAll(pdfMenuTopLevelGroups.flatMap { pdfMenuActions(it) })
    addAll(pdfReadModeActions)
    addAll(pdfAudioActions)
    addAll(pdfFileToolsActions)
    addAll(pdfTopBarOwnedActions)
}
