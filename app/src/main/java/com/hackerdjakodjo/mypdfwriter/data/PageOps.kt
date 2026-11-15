package com.hackerdjakodjo.mypdfwriter.data

import java.util.UUID

/**
 * Immutable page-editing state used by [PageOps].
 *
 * [currentPage] is the zero-based page index the viewer should show after the
 * operation. All page-indexed data (annotations and notes) uses the same
 * zero-based indexing as [pages].
 */
data class PageEdit(
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val notes: Map<Int, String>,
    val currentPage: Int,
)

/**
 * Pure page-list operations shared by the viewer and the organize screen.
 *
 * The functions never mutate their input and keep annotations, notes and the
 * current page attached to the same logical page unless the operation
 * explicitly creates or removes a page.
 */
object PageOps {

    fun rotate(
        edit: PageEdit,
        indices: Set<Int>,
        delta: Int,
        aspectForPage: (Int, PageSpec) -> Float,
    ): PageEdit {
        if (edit.pages.isEmpty()) return edit.copy(currentPage = 0)
        val selected = validIndices(indices, edit.pages.size)
        val rotation = PageSpec.normalizeRotation(delta)
        if (selected.isEmpty() || rotation == 0) return edit

        val pages = edit.pages.toMutableList()
        val aspectByPage = HashMap<Int, Float>(selected.size)
        selected.forEach { index ->
            val before = pages[index]
            // The callback describes the page as currently displayed, i.e.
            // before this rotation is applied.
            aspectByPage[index] = aspectForPage(index, before)
            pages[index] = before.copy(
                extraRotation = PageSpec.normalizeRotation(before.extraRotation + rotation),
            )
        }

        val annotations = edit.annotations.map { annotation ->
            val aspect = aspectByPage[annotation.page]
            if (aspect != null) PageTransforms.rotate(annotation, rotation, aspect) else annotation
        }
        return edit.copy(
            pages = pages,
            annotations = annotations,
            currentPage = edit.currentPage.coerceIn(0, pages.lastIndex),
        )
    }

    /**
     * Removes [indices]. If every page is removed, a single blank page is kept
     * so the document always has at least one page.
     */
    fun delete(edit: PageEdit, indices: Set<Int>): PageEdit {
        if (edit.pages.isEmpty()) return edit.copy(currentPage = 0)
        val removed = validIndices(indices, edit.pages.size).toHashSet()
        if (removed.isEmpty()) return edit

        val kept = edit.pages.indices.filter { it !in removed }
        if (kept.isEmpty()) {
            return PageEdit(
                pages = listOf(PageSpec(null)),
                annotations = emptyList(),
                notes = emptyMap(),
                currentPage = 0,
            )
        }

        val oldToNew = IntArray(edit.pages.size) { -1 }
        kept.forEachIndexed { newIndex, oldIndex -> oldToNew[oldIndex] = newIndex }
        val safeCurrent = edit.currentPage.coerceIn(0, edit.pages.lastIndex)
        val newCurrent = if (oldToNew[safeCurrent] >= 0) {
            oldToNew[safeCurrent]
        } else {
            // The current page was removed: show the next surviving page, or
            // the last page when the removed page was at the end.
            kept.count { it < safeCurrent }
        }.coerceIn(0, kept.lastIndex)

        val annotations = edit.annotations.mapNotNull { annotation ->
            val mapped = oldToNew.getOrNull(annotation.page)?.takeIf { it >= 0 }
                ?: return@mapNotNull null
            annotation.copy(page = mapped)
        }
        val notes = buildMap {
            edit.notes.forEach { (page, value) ->
                val mapped = oldToNew.getOrNull(page)?.takeIf { it >= 0 } ?: return@forEach
                put(mapped, value)
            }
        }
        return PageEdit(
            pages = kept.map { edit.pages[it] },
            annotations = annotations,
            notes = notes,
            currentPage = newCurrent,
        )
    }

    /**
     * Inserts a copy of every selected page directly after that page. Each
     * copied annotation receives a fresh id, while original annotations keep
     * their ids.
     */
    fun duplicate(edit: PageEdit, indices: Set<Int>): PageEdit {
        if (edit.pages.isEmpty()) return edit.copy(currentPage = 0)
        val selected = validIndices(indices, edit.pages.size)
        if (selected.isEmpty()) return edit

        val selectedSet = selected.toHashSet()
        val oldToNew = IntArray(edit.pages.size) { -1 }
        val copyOfOld = HashMap<Int, Int>(selected.size)
        val pages = ArrayList<PageSpec>(edit.pages.size + selected.size)
        edit.pages.forEachIndexed { oldIndex, spec ->
            oldToNew[oldIndex] = pages.size
            pages.add(spec)
            if (oldIndex in selectedSet) {
                copyOfOld[oldIndex] = pages.size
                pages.add(spec)
            }
        }

        val originalAnnotations = ArrayList<Annotation>(edit.annotations.size)
        val copiedAnnotations = ArrayList<Annotation>(edit.annotations.size)
        edit.annotations.forEach { annotation ->
            val mapped = oldToNew.getOrNull(annotation.page)?.takeIf { it >= 0 }
            if (mapped != null) {
                originalAnnotations.add(annotation.copy(page = mapped))
                val copyPage = copyOfOld[annotation.page]
                if (copyPage != null) {
                    copiedAnnotations.add(
                        annotation.copy(
                            id = UUID.randomUUID().toString(),
                            page = copyPage,
                        ),
                    )
                }
            }
        }

        val notes = buildMap {
            edit.notes.forEach { (page, value) ->
                val mapped = oldToNew.getOrNull(page)?.takeIf { it >= 0 } ?: return@forEach
                put(mapped, value)
                copyOfOld[page]?.let { put(it, value) }
            }
        }
        val safeCurrent = edit.currentPage.coerceIn(0, edit.pages.lastIndex)
        return PageEdit(
            pages = pages,
            annotations = originalAnnotations + copiedAnnotations,
            notes = notes,
            currentPage = oldToNew[safeCurrent].coerceIn(0, pages.lastIndex),
        )
    }

    /**
     * Moves the selected pages one slot earlier ([direction] < 0) or later
     * ([direction] > 0) when the group has room to move. Selections already at
     * the edge are left in place.
     */
    fun move(edit: PageEdit, indices: Set<Int>, direction: Int): PageEdit {
        if (edit.pages.isEmpty() || direction == 0) {
            return edit.copy(currentPage = edit.currentPage.coerceIn(0, edit.pages.lastIndex.coerceAtLeast(0)))
        }
        val selected = validIndices(indices, edit.pages.size).toHashSet()
        if (selected.isEmpty()) return edit

        val pages = edit.pages.toMutableList()
        // occupant[position] = original index of the page currently there.
        val occupant = MutableList(edit.pages.size) { it }

        fun swap(a: Int, b: Int) {
            val page = pages[a]
            pages[a] = pages[b]
            pages[b] = page
            val who = occupant[a]
            occupant[a] = occupant[b]
            occupant[b] = who
        }

        if (direction < 0) {
            // Ascending order means each selected page is still at its
            // original position when processed, and the group shifts together.
            for (position in 0 until edit.pages.size) {
                if (position !in selected || position == 0) continue
                if (occupant[position - 1] in selected) continue
                swap(position, position - 1)
            }
        } else {
            for (position in edit.pages.lastIndex downTo 0) {
                if (position !in selected || position == edit.pages.lastIndex) continue
                if (occupant[position + 1] in selected) continue
                swap(position, position + 1)
            }
        }

        val oldToNew = IntArray(edit.pages.size)
        occupant.forEachIndexed { newIndex, oldIndex -> oldToNew[oldIndex] = newIndex }
        val annotations = edit.annotations.mapNotNull { annotation ->
            val mapped = oldToNew.getOrNull(annotation.page)?.takeIf { it >= 0 }
                ?: return@mapNotNull null
            annotation.copy(page = mapped)
        }
        val notes = buildMap {
            edit.notes.forEach { (page, value) ->
                val mapped = oldToNew.getOrNull(page)?.takeIf { it >= 0 } ?: return@forEach
                put(mapped, value)
            }
        }
        val safeCurrent = edit.currentPage.coerceIn(0, edit.pages.lastIndex)
        return PageEdit(
            pages = pages,
            annotations = annotations,
            notes = notes,
            currentPage = oldToNew[safeCurrent].coerceIn(0, pages.lastIndex),
        )
    }

    /**
     * Inserts [count] blank pages after [afterIndex] (use -1 to insert at the
     * beginning). Existing annotations, notes and the current page shift to
     * keep pointing at the same content.
     */
    fun insertBlank(edit: PageEdit, afterIndex: Int, count: Int = 1): PageEdit {
        val insertCount = count.coerceAtLeast(0)
        if (insertCount == 0) {
            return edit.copy(currentPage = edit.currentPage.coerceIn(0, edit.pages.lastIndex.coerceAtLeast(0)))
        }
        val insertAt = (afterIndex + 1).coerceIn(0, edit.pages.size)
        val pages = ArrayList<PageSpec>(edit.pages.size + insertCount)
        pages.addAll(edit.pages.subList(0, insertAt))
        repeat(insertCount) { pages.add(PageSpec(null)) }
        pages.addAll(edit.pages.subList(insertAt, edit.pages.size))

        val annotations = edit.annotations.map { annotation ->
            if (annotation.page >= insertAt) annotation.copy(page = annotation.page + insertCount) else annotation
        }
        val notes = buildMap {
            edit.notes.forEach { (page, value) ->
                put(if (page >= insertAt) page + insertCount else page, value)
            }
        }
        val current = if (edit.pages.isEmpty()) {
            0
        } else if (edit.currentPage >= insertAt) {
            edit.currentPage + insertCount
        } else {
            edit.currentPage
        }
        return PageEdit(
            pages = pages,
            annotations = annotations,
            notes = notes,
            currentPage = current.coerceIn(0, pages.lastIndex),
        )
    }

    fun selectAllIndices(pageCount: Int): Set<Int> =
        if (pageCount <= 0) emptySet() else (0 until pageCount).toSet()

    private fun validIndices(indices: Set<Int>, pageCount: Int): List<Int> =
        indices.asSequence()
            .filter { it in 0 until pageCount }
            .distinct()
            .sorted()
            .toList()
}
