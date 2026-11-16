package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageBookmark
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate

/** One row in the notebook page outline. */
data class OutlinePage(
    val index: Int,
    val template: PaperTemplate,
    val annotationCount: Int,
    val bookmarked: Boolean,
)

/**
 * Pure page-outline and bookmark operations for a [Notebook].
 *
 * Every function returns a new notebook and leaves the input untouched. The
 * bookmark list is canonicalized on every operation: anchors outside the page
 * range are dropped, duplicate page anchors are removed (the first one wins)
 * and the result is sorted by page.
 *
 * Page indices passed to [addBookmark] and [removeBookmark] are clamped to the
 * valid page range. A blank bookmark title falls back to `페이지 N`.
 */
object NotebookOutline {

    /** All pages in reading order with their template, annotation and bookmark state. */
    fun pages(notebook: Notebook): List<OutlinePage> {
        val bookmarkedPages = bookmarks(notebook).mapTo(HashSet()) { it.pageIndex }
        return notebook.pages.mapIndexed { index, page ->
            OutlinePage(
                index = index,
                template = page.template,
                annotationCount = page.annotations.size,
                bookmarked = index in bookmarkedPages,
            )
        }
    }

    /**
     * Adds a bookmark on [pageIndex], clamped to the notebook range.
     *
     * A page can have only one bookmark: adding to an already bookmarked page
     * updates that bookmark's title and keeps its id.
     */
    fun addBookmark(notebook: Notebook, pageIndex: Int, title: String): Notebook {
        if (notebook.pages.isEmpty()) return normalize(notebook)
        val index = pageIndex.coerceIn(0, notebook.pages.lastIndex)
        val base = normalize(notebook)
        val existing = base.bookmarks.firstOrNull { it.pageIndex == index }
        val safeTitle = title.trim().ifBlank { defaultTitle(index) }
        val updated = if (existing == null) {
            base.bookmarks + PageBookmark(pageIndex = index, title = safeTitle)
        } else {
            base.bookmarks.map {
                if (it.id == existing.id) it.copy(title = safeTitle) else it
            }
        }
        return base.copy(bookmarks = updated.sortedBy { it.pageIndex })
    }

    /** Removes the bookmark anchored on [pageIndex], clamped to the notebook range. */
    fun removeBookmark(notebook: Notebook, pageIndex: Int): Notebook {
        if (notebook.pages.isEmpty()) return normalize(notebook)
        val index = pageIndex.coerceIn(0, notebook.pages.lastIndex)
        val base = normalize(notebook)
        return base.copy(bookmarks = base.bookmarks.filterNot { it.pageIndex == index })
    }

    /** Renames the bookmark with [id]. Unknown ids leave existing titles unchanged. */
    fun renameBookmark(notebook: Notebook, id: String, title: String): Notebook {
        val base = normalize(notebook)
        val target = base.bookmarks.firstOrNull { it.id == id } ?: return base
        val safeTitle = title.trim().ifBlank { defaultTitle(target.pageIndex) }
        return base.copy(
            bookmarks = base.bookmarks.map {
                if (it.id == id) it.copy(title = safeTitle) else it
            },
        )
    }

    /**
     * Moves a bookmark one slot earlier ([direction] < 0) or later
     * ([direction] > 0) in page order.
     *
     * Bookmarks are anchored to pages, so moving swaps the page anchors with
     * the adjacent bookmark instead of changing the bookmark list order. A
     * bookmark already at the edge is left in place.
     */
    fun moveBookmark(notebook: Notebook, id: String, direction: Int): Notebook {
        val base = normalize(notebook)
        if (direction == 0) return base
        val sorted = base.bookmarks.sortedBy { it.pageIndex }
        val position = sorted.indexOfFirst { it.id == id }
        if (position < 0) return base
        val target = if (direction < 0) position - 1 else position + 1
        if (target !in sorted.indices) return base
        val moving = sorted[position]
        val other = sorted[target]
        val swapped = sorted.toMutableList()
        swapped[position] = moving.copy(pageIndex = other.pageIndex)
        swapped[target] = other.copy(pageIndex = moving.pageIndex)
        return base.copy(bookmarks = swapped.sortedBy { it.pageIndex })
    }

    /** Bookmarks sorted by page and reduced to one entry per page. */
    fun bookmarks(notebook: Notebook): List<PageBookmark> =
        normalize(notebook).bookmarks.sortedBy { it.pageIndex }

    private fun normalize(notebook: Notebook): Notebook {
        if (notebook.pages.isEmpty()) {
            return if (notebook.bookmarks.isEmpty()) notebook else notebook.copy(bookmarks = emptyList())
        }
        val seenPages = HashSet<Int>()
        val cleaned = notebook.bookmarks
            .filter { it.pageIndex in notebook.pages.indices }
            .sortedBy { it.pageIndex }
            .filter { seenPages.add(it.pageIndex) }
        return if (cleaned == notebook.bookmarks) notebook else notebook.copy(bookmarks = cleaned)
    }

    private fun defaultTitle(pageIndex: Int): String = "페이지 ${pageIndex + 1}"
}
