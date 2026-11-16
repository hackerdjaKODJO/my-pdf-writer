package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook

/** One note page that points back to a page in the source PDF. */
internal data class StudyNoteSourceLink(
    val notePageIndex: Int,
    val sourcePageIndex: Int,
    val memo: String,
    /** Whitespace-collapsed excerpt preview, or the page memo when there is no text. */
    val preview: String,
)

/** Links that share one source PDF page, already ordered by note page. */
internal data class StudyNoteSourceLinkGroup(
    val sourcePageIndex: Int,
    val links: List<StudyNoteSourceLink>,
)

/**
 * Local-only To/From index built from the existing [NotePage.sourcePageIndex]
 * field. No new storage is involved: a note page without a source link is simply
 * absent from the list.
 */
internal object StudyNoteSourceLinks {

    fun fromNotebook(notebook: Notebook): List<StudyNoteSourceLink> =
        notebook.pages.mapIndexedNotNull { index, page ->
            linkOf(index, page)
        }.sortedWith(compareBy({ it.sourcePageIndex }, { it.notePageIndex }))

    /** Groups by PDF page in ascending page order, then note page order. */
    fun grouped(notebook: Notebook): List<StudyNoteSourceLinkGroup> {
        val ordered = fromNotebook(notebook)
        if (ordered.isEmpty()) return emptyList()
        val groups = LinkedHashMap<Int, MutableList<StudyNoteSourceLink>>()
        ordered.forEach { link ->
            groups.getOrPut(link.sourcePageIndex) { ArrayList() }.add(link)
        }
        return groups.map { (sourcePage, links) ->
            StudyNoteSourceLinkGroup(sourcePageIndex = sourcePage, links = links)
        }
    }

    private fun linkOf(index: Int, page: NotePage): StudyNoteSourceLink? {
        val sourcePage = page.sourcePageIndex?.takeIf { it >= 0 } ?: return null
        return StudyNoteSourceLink(
            notePageIndex = index,
            sourcePageIndex = sourcePage,
            memo = page.memo,
            preview = previewOf(page),
        )
    }

    private fun previewOf(page: NotePage): String {
        val text = page.annotations
            .firstOrNull { it.text.isNotBlank() }
            ?.text
            ?: page.memo
        return text.replace(WHITESPACE, " ").trim().take(PREVIEW_LIMIT)
    }

    private const val PREVIEW_LIMIT = 120
    private val WHITESPACE = Regex("\\s+")
}
