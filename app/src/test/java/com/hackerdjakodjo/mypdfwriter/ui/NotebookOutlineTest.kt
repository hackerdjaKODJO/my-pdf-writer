package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageBookmark
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookOutlineTest {

    private fun notebook(
        pageCount: Int = 3,
        bookmarks: List<PageBookmark> = emptyList(),
    ): Notebook = Notebook(
        title = "테스트 노트",
        pages = List(pageCount) { index ->
            NotePage(
                template = PaperTemplate.entries[index % PaperTemplate.entries.size],
                annotations = List(index) {
                    Annotation(
                        type = AnnotationType.PEN,
                        page = index,
                        color = 0xFF1F4E79.toInt(),
                        width = 0.004f,
                    )
                },
            )
        },
        bookmarks = bookmarks,
    )

    @Test
    fun pagesExposeTemplateAnnotationCountAndBookmarkState() {
        val bookmarked = PageBookmark(pageIndex = 1, title = "복습")
        val outline = NotebookOutline.pages(notebook(pageCount = 3, bookmarks = listOf(bookmarked)))

        assertEquals(3, outline.size)
        assertEquals(listOf(0, 1, 2), outline.map { it.index })
        assertEquals(PaperTemplate.entries[0], outline[0].template)
        assertEquals(0, outline[0].annotationCount)
        assertEquals(2, outline[2].annotationCount)
        assertFalse(outline[0].bookmarked)
        assertTrue(outline[1].bookmarked)
        assertFalse(outline[2].bookmarked)
    }

    @Test
    fun addBookmarkClampsPageIndexAndUsesDefaultTitle() {
        var note = notebook(pageCount = 3)

        note = NotebookOutline.addBookmark(note, -10, "  시작  ")
        assertEquals(0, note.bookmarks.single().pageIndex)
        assertEquals("시작", note.bookmarks.single().title)

        note = NotebookOutline.addBookmark(note, 99, "   ")
        val last = note.bookmarks.last()
        assertEquals(2, last.pageIndex)
        assertEquals("페이지 3", last.title)
    }

    @Test
    fun addBookmarkUpdatesExistingPageInsteadOfAddingAnother() {
        val existing = PageBookmark(pageIndex = 1, title = "이전 제목")
        var note = notebook(pageCount = 3, bookmarks = listOf(existing))

        note = NotebookOutline.addBookmark(note, 1, "새 제목")

        assertEquals(1, note.bookmarks.size)
        assertEquals(existing.id, note.bookmarks.single().id)
        assertEquals("새 제목", note.bookmarks.single().title)
    }

    @Test
    fun addBookmarkDropsDuplicateAnchors() {
        val first = PageBookmark(pageIndex = 1, title = "첫 번째")
        val second = PageBookmark(pageIndex = 1, title = "두 번째")
        val note = notebook(pageCount = 3, bookmarks = listOf(first, second))

        val updated = NotebookOutline.addBookmark(note, 1, "하나만")

        assertEquals(1, updated.bookmarks.size)
        assertEquals(first.id, updated.bookmarks.single().id)
        assertEquals("하나만", updated.bookmarks.single().title)
    }

    @Test
    fun removeBookmarkClampsIndexAndDropsOutOfRangeAnchors() {
        val note = notebook(
            pageCount = 3,
            bookmarks = listOf(
                PageBookmark(pageIndex = 1, title = "남길 것"),
                PageBookmark(pageIndex = 8, title = "범위 밖"),
            ),
        )

        val kept = NotebookOutline.removeBookmark(note, -5)
        assertEquals(listOf(1), kept.bookmarks.map { it.pageIndex })

        val removed = NotebookOutline.removeBookmark(note, 1)
        assertTrue(removed.bookmarks.isEmpty())
    }

    @Test
    fun renameBookmarkTrimsTitleAndKeepsId() {
        val bookmark = PageBookmark(id = "bookmark-1", pageIndex = 2, title = "이전")
        val note = notebook(pageCount = 3, bookmarks = listOf(bookmark))

        val renamed = NotebookOutline.renameBookmark(note, "bookmark-1", "  새 이름  ")

        val updated = renamed.bookmarks.single()
        assertEquals("bookmark-1", updated.id)
        assertEquals("새 이름", updated.title)
        assertEquals(2, updated.pageIndex)
    }

    @Test
    fun renameBookmarkFallsBackToPageTitleWhenBlank() {
        val bookmark = PageBookmark(id = "bookmark-1", pageIndex = 2, title = "이전")
        val note = notebook(pageCount = 3, bookmarks = listOf(bookmark))

        val renamed = NotebookOutline.renameBookmark(note, "bookmark-1", "   ")

        assertEquals("페이지 3", renamed.bookmarks.single().title)
    }

    @Test
    fun renameUnknownBookmarkLeavesTitlesUnchanged() {
        val bookmark = PageBookmark(id = "bookmark-1", pageIndex = 1, title = "그대로")
        val note = notebook(pageCount = 3, bookmarks = listOf(bookmark))

        val renamed = NotebookOutline.renameBookmark(note, "missing", "바뀜")

        assertEquals("그대로", renamed.bookmarks.single().title)
    }

    @Test
    fun moveBookmarkSwapsPageAnchorsWithNeighbor() {
        val a = PageBookmark(id = "a", pageIndex = 0, title = "A")
        val b = PageBookmark(id = "b", pageIndex = 2, title = "B")
        val c = PageBookmark(id = "c", pageIndex = 4, title = "C")
        val note = notebook(pageCount = 5, bookmarks = listOf(c, a, b))

        val movedDown = NotebookOutline.moveBookmark(note, "b", 1)
        assertEquals(listOf("a", "c", "b"), movedDown.bookmarks.map { it.id })
        assertEquals(listOf(0, 2, 4), movedDown.bookmarks.map { it.pageIndex })
        assertEquals(2, movedDown.bookmarks.first { it.id == "c" }.pageIndex)
        assertEquals(4, movedDown.bookmarks.first { it.id == "b" }.pageIndex)

        val movedUp = NotebookOutline.moveBookmark(note, "c", -1)
        assertEquals(listOf("a", "c", "b"), movedUp.bookmarks.map { it.id })
    }

    @Test
    fun moveBookmarkAtEdgeOrWithZeroDirectionIsNoOp() {
        val a = PageBookmark(id = "a", pageIndex = 0, title = "A")
        val b = PageBookmark(id = "b", pageIndex = 2, title = "B")
        val note = notebook(pageCount = 3, bookmarks = listOf(a, b))

        val topUp = NotebookOutline.moveBookmark(note, "a", -1)
        val bottomDown = NotebookOutline.moveBookmark(note, "b", 1)
        val zero = NotebookOutline.moveBookmark(note, "a", 0)
        val unknown = NotebookOutline.moveBookmark(note, "missing", 1)

        assertEquals(listOf("a", "b"), topUp.bookmarks.map { it.id })
        assertEquals(listOf("a", "b"), bottomDown.bookmarks.map { it.id })
        assertEquals(listOf("a", "b"), zero.bookmarks.map { it.id })
        assertEquals(listOf("a", "b"), unknown.bookmarks.map { it.id })
    }

    @Test
    fun bookmarksAreSortedByPageAndDeduplicated() {
        val duplicate = PageBookmark(id = "dup", pageIndex = 2, title = "중복")
        val first = PageBookmark(id = "first", pageIndex = 2, title = "첫 번째")
        val low = PageBookmark(id = "low", pageIndex = 0, title = "앞")
        val outside = PageBookmark(id = "outside", pageIndex = 9, title = "범위 밖")
        val note = notebook(pageCount = 3, bookmarks = listOf(duplicate, low, first, outside))

        val sorted = NotebookOutline.bookmarks(note)

        assertEquals(listOf("low", "dup"), sorted.map { it.id })
        assertEquals(listOf(0, 2), sorted.map { it.pageIndex })
    }

    @Test
    fun operationsOnPageLessNotebookDropBookmarks() {
        val empty = Notebook(
            pages = emptyList(),
            bookmarks = listOf(PageBookmark(pageIndex = 0, title = "없는 페이지")),
        )

        assertTrue(NotebookOutline.pages(empty).isEmpty())
        assertTrue(NotebookOutline.bookmarks(empty).isEmpty())
        assertTrue(NotebookOutline.addBookmark(empty, 0, "새 북마크").bookmarks.isEmpty())
        assertTrue(NotebookOutline.removeBookmark(empty, 0).bookmarks.isEmpty())
    }
}
