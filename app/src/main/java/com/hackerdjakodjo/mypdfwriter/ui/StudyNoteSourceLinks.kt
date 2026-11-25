package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import com.hackerdjakodjo.mypdfwriter.data.effectiveStudyLink

/** One note page that points back to a page in the source PDF. */
internal data class StudyNoteSourceLink(
    val notePageIndex: Int,
    val sourcePageIndex: Int,
    val memo: String,
    /** Whitespace-collapsed excerpt preview, or the page memo when there is no text. */
    val preview: String,
    /** Containing notebook identity; stable across page reordering. */
    val noteId: String,
    /** Note-page identity; use this rather than the viewer index as a list key. */
    val pageId: String,
    /** Effective durable locator, including legacy root-URI fallback. */
    val sourceLink: StudySourceLink,
) {
    val stableKey: String get() = pageId
}

/** Links that share one source PDF page, already ordered by note page. */
internal data class StudyNoteSourceLinkGroup(
    val sourcePageIndex: Int,
    val links: List<StudyNoteSourceLink>,
)

/**
 * Local-only To/From index built from each page's [effectiveStudyLink]. Legacy
 * root URI/page links remain visible, while orphan legacy pages without a root
 * URI are omitted. Explicit per-page links survive a missing notebook root URI.
 */
internal object StudyNoteSourceLinks {

    fun fromNotebook(notebook: Notebook): List<StudyNoteSourceLink> =
        notebook.pages.mapIndexedNotNull { index, page ->
            linkOf(notebook, index, page)
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

    private fun linkOf(notebook: Notebook, index: Int, page: NotePage): StudyNoteSourceLink? {
        val sourceLink = page.effectiveStudyLink(notebook) ?: return null
        return StudyNoteSourceLink(
            notePageIndex = index,
            sourcePageIndex = sourceLink.sourcePage,
            memo = page.memo,
            preview = previewOf(page),
            noteId = notebook.id,
            pageId = page.id,
            sourceLink = sourceLink,
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
