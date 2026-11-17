package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Checklists
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplatePaths
import com.hackerdjakodjo.mypdfwriter.data.MaskingCards
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import java.util.UUID

/** Page-manager filter chips. */
enum class NotePageFilter(val label: String) {
    ALL("전체"),
    ANNOTATED("필기 있음"),
}

/** One card in the notebook page manager. [index] is always the live page order. */
data class NotePageEntry(
    val index: Int,
    val id: String,
    val template: PaperTemplate,
    val orientation: PageOrientation,
    val tint: PaperTint,
    val annotationCount: Int,
)

/**
 * Result of a multi-page operation. [currentIndex] points at the same logical
 * page as before whenever that page survived; otherwise it falls back to the
 * nearest following page and then to the last survivor.
 */
data class NotePageManagerResult(
    val notebook: Notebook,
    val currentIndex: Int,
    val selectedPageIds: Set<String>,
)

/**
 * Pure page filtering, selection and multi-page operations for notebooks.
 *
 * Page identity is the stable [NotePage.id]. Selection survives filters and
 * sheet dismissal, but ids that no longer belong to the notebook are ignored.
 * Every operation returns a new notebook and leaves its input untouched so the
 * editor can commit the whole result as one undo step.
 */
object NotePageManager {

    fun annotatedCount(pages: List<NotePage>): Int = pages.count { it.annotations.isNotEmpty() }

    /** Visible cards for [filter], in page order. Annotation counts include hidden layers. */
    fun entries(
        pages: List<NotePage>,
        filter: NotePageFilter = NotePageFilter.ALL,
    ): List<NotePageEntry> = pages.mapIndexedNotNull { index, page ->
        if (filter == NotePageFilter.ANNOTATED && page.annotations.isEmpty()) {
            null
        } else {
            NotePageEntry(
                index = index,
                id = page.id,
                template = page.template,
                orientation = page.orientation,
                tint = page.tint,
                annotationCount = page.annotations.size,
            )
        }
    }

    /** Selected ids that still exist, in page order (not selection order). */
    fun validSelection(pages: List<NotePage>, selectedPageIds: Set<String>): Set<String> {
        val valid = LinkedHashSet<String>(selectedPageIds.size)
        pages.forEach { page ->
            if (page.id in selectedPageIds) valid.add(page.id)
        }
        return valid
    }

    /** Toggles one known page id and keeps only ids that still exist. */
    fun toggleSelection(
        pages: List<NotePage>,
        selectedPageIds: Set<String>,
        pageId: String,
    ): Set<String> {
        val valid = validSelection(pages, selectedPageIds)
        if (pages.none { it.id == pageId }) return valid
        val toggled = if (pageId in valid) valid - pageId else valid + pageId
        return validSelection(pages, toggled)
    }

    /**
     * Inserts a duplicate directly after every selected page, in page order.
     *
     * Existing pages and annotations keep their ids. Copies receive fresh page,
     * annotation and masking-card ids while keeping templates, memos, source links,
     * layers, layer visibility/opacity, card content/geometry/state and annotation
     * timing. Bookmarks that follow an
     * inserted copy are remapped to the same logical page; no bookmark is copied.
     */
    fun duplicatePages(
        notebook: Notebook,
        selectedPageIds: Set<String>,
        currentIndex: Int,
    ): NotePageManagerResult {
        val pages = notebook.pages
        if (pages.isEmpty()) {
            return NotePageManagerResult(notebook, 0, emptySet())
        }
        val selected = validSelection(pages, selectedPageIds)
        val safeCurrent = currentIndex.coerceIn(0, pages.lastIndex)
        if (selected.isEmpty()) {
            return NotePageManagerResult(notebook, safeCurrent, selected)
        }

        val output = ArrayList<NotePage>(pages.size + selected.size)
        val oldToNew = IntArray(pages.size)
        pages.forEachIndexed { oldIndex, page ->
            oldToNew[oldIndex] = output.size
            output.add(page)
            if (page.id in selected) output.add(duplicatePage(page))
        }
        val bookmarks = notebook.bookmarks.mapNotNull { bookmark ->
            val mapped = oldToNew.getOrNull(bookmark.pageIndex) ?: return@mapNotNull null
            bookmark.copy(pageIndex = mapped)
        }
        return NotePageManagerResult(
            notebook = notebook.copy(pages = output, bookmarks = bookmarks),
            currentIndex = oldToNew[safeCurrent],
            selectedPageIds = selected,
        )
    }

    /**
     * Rotates selected pages, or the current page when the selection is empty.
     *
     * Every target keeps its page id, annotation ids and source link. Page order,
     * bookmarks and selection are unchanged, and the current index still points at
     * the same logical page. The caller commits the returned notebook once, so the
     * whole rotation is one undo step.
     */
    fun rotatePages(
        notebook: Notebook,
        selectedPageIds: Set<String>,
        currentIndex: Int,
        clockwise: Boolean,
    ): NotePageManagerResult {
        val pages = notebook.pages
        if (pages.isEmpty()) {
            return NotePageManagerResult(notebook, 0, emptySet())
        }
        val selected = validSelection(pages, selectedPageIds)
        val safeCurrent = currentIndex.coerceIn(0, pages.lastIndex)
        val targets = if (selected.isEmpty()) {
            setOf(pages[safeCurrent].id)
        } else {
            selected
        }
        val rotated = pages.map { page ->
            if (page.id in targets) NotePaper.rotatePage(page, clockwise) else page
        }
        return NotePageManagerResult(
            notebook = notebook.copy(pages = rotated),
            currentIndex = safeCurrent,
            selectedPageIds = selected,
        )
    }

    /**
     * Inserts a fresh photo page after [currentIndex]. The imported asset key is
     * the same portable `files/templates/` key used by Template Studio; the page
     * inherits the current page's A4 orientation, template and tint, starts empty
     * (one default layer, no annotations/source link), and becomes current.
     *
     * Bookmarks that follow the insertion move with their original page. The
     * existing selection is preserved because no referenced page id changes.
     * An invalid asset key is rejected instead of storing an unreadable page.
     */
    fun insertPhotoPage(
        notebook: Notebook,
        currentIndex: Int,
        selectedPageIds: Set<String>,
        assetKey: String,
        displayName: String,
    ): NotePageManagerResult {
        val pages = notebook.pages
        if (pages.isEmpty()) {
            return NotePageManagerResult(notebook, 0, emptySet())
        }
        val safeKey = CustomTemplatePaths.sanitizeKey(assetKey)
            ?: return NotePageManagerResult(
                notebook = notebook,
                currentIndex = currentIndex.coerceIn(0, pages.lastIndex),
                selectedPageIds = validSelection(pages, selectedPageIds),
            )
        val safeCurrent = currentIndex.coerceIn(0, pages.lastIndex)
        val source = pages[safeCurrent]
        val insertAt = safeCurrent + 1
        val photoPage = NotePage(
            template = source.template,
            orientation = source.orientation,
            tint = source.tint,
            customBackground = safeKey,
            customBackgroundName = CustomTemplatePaths.normalizeName(displayName),
        )
        val output = ArrayList<NotePage>(pages.size + 1)
        pages.forEachIndexed { index, page ->
            output.add(page)
            if (index == safeCurrent) output.add(photoPage)
        }
        val bookmarks = notebook.bookmarks.map { bookmark ->
            if (bookmark.pageIndex >= insertAt) {
                bookmark.copy(pageIndex = bookmark.pageIndex + 1)
            } else {
                bookmark
            }
        }
        return NotePageManagerResult(
            notebook = notebook.copy(pages = output, bookmarks = bookmarks),
            currentIndex = insertAt,
            selectedPageIds = validSelection(output, selectedPageIds),
        )
    }

    /**
     * Removes selected pages and remaps the current page, page bookmarks and
     * the remaining selection. The operation is refused when it would remove
     * every page, so a notebook always keeps at least one page.
     */
    fun deletePages(
        notebook: Notebook,
        selectedPageIds: Set<String>,
        currentIndex: Int,
    ): NotePageManagerResult {
        val pages = notebook.pages
        if (pages.isEmpty()) {
            return NotePageManagerResult(notebook, 0, emptySet())
        }
        val selected = validSelection(pages, selectedPageIds)
        val safeCurrent = currentIndex.coerceIn(0, pages.lastIndex)
        if (selected.isEmpty() || selected.size >= pages.size) {
            return NotePageManagerResult(notebook, safeCurrent, selected)
        }

        val keptIndices = pages.indices.filter { pages[it].id !in selected }
        val oldToNew = IntArray(pages.size) { -1 }
        keptIndices.forEachIndexed { newIndex, oldIndex -> oldToNew[oldIndex] = newIndex }
        val newCurrent = if (oldToNew[safeCurrent] >= 0) {
            oldToNew[safeCurrent]
        } else {
            keptIndices.count { it < safeCurrent }
        }.coerceIn(0, keptIndices.lastIndex)
        val bookmarks = notebook.bookmarks.mapNotNull { bookmark ->
            val mapped = oldToNew.getOrNull(bookmark.pageIndex)?.takeIf { it >= 0 }
                ?: return@mapNotNull null
            bookmark.copy(pageIndex = mapped)
        }
        return NotePageManagerResult(
            notebook = notebook.copy(
                pages = keptIndices.map { pages[it] },
                bookmarks = bookmarks,
            ),
            currentIndex = newCurrent,
            selectedPageIds = emptySet(),
        )
    }

    private fun duplicatePage(page: NotePage): NotePage = page.copy(
        id = UUID.randomUUID().toString(),
        annotations = page.annotations.map {
            it.copy(id = UUID.randomUUID().toString(), page = 0)
        },
        // Cards travel with the page like annotations, but a duplicate must be an
        // independent study record (fresh ids, same prompt/answer/rect/state).
        maskingCards = MaskingCards.freshIds(page.maskingCards),
        // A duplicated page owns independent checklist records and item ids.
        checklists = Checklists.freshIds(page.checklists),
    )
}
