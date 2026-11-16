package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReadingTest {

    @Test
    fun bookmarkJsonRoundTripKeepsFields() {
        val bookmark = PdfBookmark(
            id = "bookmark-1",
            pageIndex = 12,
            title = "3장 정의",
            createdAt = 1_760_000_000_000L,
        )

        val restored = PdfBookmark.fromJson(JSONObject(bookmark.toJson().toString()))

        assertEquals(bookmark, restored)
    }

    @Test
    fun missingIdGetsFreshIdAndDefaultsAreSafe() {
        val restored = PdfBookmark.fromJson(JSONObject("""{"page":3,"title":"복습"}"""))

        assertTrue(restored.id.isNotBlank())
        assertEquals(3, restored.pageIndex)
        assertEquals("복습", restored.title)
        assertTrue(restored.createdAt > 0L)
    }

    @Test
    fun readingStateJsonRoundTripPreservesProgressAndOrder() {
        val state = PdfReadingState(
            lastPage = 9,
            bookmarks = listOf(
                PdfBookmark(id = "a", pageIndex = 2, title = "개요", createdAt = 10L),
                PdfBookmark(id = "b", pageIndex = 9, title = "정리", createdAt = 20L),
            ),
        )

        val restored = PdfReadingState.fromJson(JSONObject(state.toJson().toString()))

        assertEquals(state, restored)
    }

    @Test
    fun normalizedClampsProgressAndDropsDuplicatesAndInvalidPages() {
        val state = PdfReadingState(
            lastPage = 40,
            bookmarks = listOf(
                PdfBookmark(id = "a", pageIndex = 0, title = "첫째", createdAt = 10L),
                PdfBookmark(id = "b", pageIndex = 0, title = "중복", createdAt = 20L),
                PdfBookmark(id = "c", pageIndex = 8, title = "범위 밖", createdAt = 30L),
                PdfBookmark(id = "d", pageIndex = 4, title = "마지막", createdAt = 40L),
            ),
        )

        val normalized = state.normalized(pageCount = 5)

        assertEquals(4, normalized.lastPage)
        assertEquals(listOf("a", "d"), normalized.bookmarks.map { it.id })
        assertEquals(0, state.normalized(pageCount = 0).lastPage)
        assertTrue(state.normalized(pageCount = 0).bookmarks.isEmpty())
    }

    @Test
    fun bookmarkEditsAreStoreIndependent() {
        val first = PdfBookmark(id = "a", pageIndex = 1, title = "1쪽", createdAt = 10L)
        val replacement = PdfBookmark(id = "b", pageIndex = 1, title = "새 제목", createdAt = 20L)
        val other = PdfBookmark(id = "c", pageIndex = 5, title = "5쪽", createdAt = 30L)
        val state = PdfReadingState(bookmarks = listOf(first, other))

        val added = state.withBookmark(replacement)
        assertEquals(listOf("b", "c"), added.bookmarks.map { it.id })
        assertNotNull(added.bookmarkAt(1))
        assertEquals("새 제목", added.bookmarkAt(1)?.title)

        val renamed = added.withRenamedBookmark("c", "정리 노트")
        assertEquals("정리 노트", renamed.bookmarkAt(5)?.title)
        assertEquals("새 제목", renamed.bookmarkAt(1)?.title)

        val removed = renamed.withoutBookmark("b")
        assertEquals(listOf("c"), removed.bookmarks.map { it.id })
    }
}
