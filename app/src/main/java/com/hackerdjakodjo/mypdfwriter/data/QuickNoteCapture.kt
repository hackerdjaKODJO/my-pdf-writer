package com.hackerdjakodjo.mypdfwriter.data

import java.util.UUID

/** The two entry points for capturing a note without opening an editor first. */
enum class QuickNoteKind(val label: String) {
    MEMO("빠른 메모"),
    CHECKLIST("체크리스트"),
}

/**
 * Android/Compose-free defaults for one-tap note capture.
 *
 * Every call returns fresh, immutable model objects with independently generated
 * ids. The supplied clock is used for timestamped content; no existing model is
 * modified and no source-document metadata is attached to a quick note.
 */
object QuickNoteCapture {

    private const val CHECKLIST_ITEM_COUNT = 3

    /**
     * Creates the notebook metadata used by the existing create flow.
     *
     * [nowMs] keeps the draft and page APIs on one caller-supplied clock. The
     * current [NotebookDraft] model has no timestamp fields to populate.
     */
    fun draft(
        kind: QuickNoteKind,
        nowMs: Long = System.currentTimeMillis(),
    ): NotebookDraft = NotebookDraft(
        title = kind.label,
        template = templateFor(kind),
        quickKind = kind,
    )

    /** Creates a blank memo page or a lined page with a starter checklist. */
    fun page(
        kind: QuickNoteKind,
        nowMs: Long = System.currentTimeMillis(),
    ): NotePage = NotePage(
        id = newId(),
        template = templateFor(kind),
        layers = listOf(NoteLayer(id = newId())),
        checklists = if (kind == QuickNoteKind.CHECKLIST) {
            listOf(starterChecklist(nowMs))
        } else {
            emptyList()
        },
    )

    private fun templateFor(kind: QuickNoteKind): PaperTemplate = when (kind) {
        QuickNoteKind.MEMO -> PaperTemplate.BLANK
        QuickNoteKind.CHECKLIST -> PaperTemplate.LINED
    }

    private fun starterChecklist(nowMs: Long): ChecklistCard = ChecklistCard(
        id = newId(),
        title = Checklists.DEFAULT_TITLE,
        items = List(CHECKLIST_ITEM_COUNT) { ChecklistItem(id = newId()) },
        createdAt = nowMs,
    )

    private fun newId(): String = UUID.randomUUID().toString()
}
