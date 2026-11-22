package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageOpsTest {

    private fun edit(
        pages: List<PageSpec>,
        annotations: List<Annotation> = emptyList(),
        notes: Map<Int, String> = emptyMap(),
        currentPage: Int = 0,
        bookmarks: List<PdfBookmark> = emptyList(),
    ) = PageEdit(pages, annotations, notes, currentPage, bookmarks)

    private fun bookmark(id: String, page: Int) =
        PdfBookmark(id = id, pageIndex = page, title = "$id title", createdAt = 10L)

    private fun annotation(
        id: String,
        page: Int,
        x: Float = 0.25f,
        y: Float = 0.4f,
        width: Float = 0.004f,
        fontSize: Float = 0.02f,
    ) = Annotation(
        id = id,
        type = AnnotationType.TEXT,
        page = page,
        color = 0xFF1F4E79.toInt(),
        width = width,
        points = listOf(NormalizedPoint(x, y)),
        text = "note",
        fontSize = fontSize,
    )

    private fun pages(vararg sourcePages: Int?): List<PageSpec> = sourcePages.map { PageSpec(it) }

    // ------------------------------------------------------------------ rotate

    @Test
    fun rotate_normalizesPageRotationAndTransformsAnnotations() {
        val original = edit(
            pages = listOf(PageSpec(0, 270), PageSpec(1, 90)),
            annotations = listOf(
                annotation("a", page = 0),
                annotation("b", page = 1),
            ),
            notes = mapOf(0 to "first"),
            currentPage = 1,
        )
        var callbackPage = -1
        var callbackSpec: PageSpec? = null

        val result = PageOps.rotate(original, setOf(0), 90) { index, spec ->
            callbackPage = index
            callbackSpec = spec
            0.5f
        }

        assertEquals(0, callbackPage)
        assertEquals(PageSpec(0, 270), callbackSpec)
        assertEquals(0, result.pages[0].extraRotation)
        assertEquals(90, result.pages[1].extraRotation)
        assertEquals(90, result.annotations[0].textRotation)
        assertEquals(0.6f, result.annotations[0].points[0].x, 1e-6f)
        assertEquals(0.25f, result.annotations[0].points[0].y, 1e-6f)
        assertEquals(0.002f, result.annotations[0].width, 1e-6f)
        assertEquals(0.04f, result.annotations[0].fontSize, 1e-6f)
        // The unselected page keeps its annotation untouched.
        assertEquals(original.annotations[1], result.annotations[1])
        assertEquals(1, result.currentPage)
        assertEquals(mapOf(0 to "first"), result.notes)
    }

    @Test
    fun rotate_negativeDeltaWrapsToCounterClockwiseRotation() {
        val original = edit(listOf(PageSpec(0, 0)), listOf(annotation("a", page = 0)))

        val result = PageOps.rotate(original, setOf(0), -90) { _, _ -> 1f }

        assertEquals(270, result.pages[0].extraRotation)
        assertEquals(270, result.annotations[0].textRotation)
        assertEquals(0.4f, result.annotations[0].points[0].x, 1e-6f)
        assertEquals(0.75f, result.annotations[0].points[0].y, 1e-6f)
    }

    @Test
    fun rotate_multipleTurnsStayNormalized() {
        var state = edit(listOf(PageSpec(0, 270)))

        state = PageOps.rotate(state, setOf(0), 90) { _, _ -> 1f }
        assertEquals(0, state.pages[0].extraRotation)
        state = PageOps.rotate(state, setOf(0), 180) { _, _ -> 1f }
        assertEquals(180, state.pages[0].extraRotation)
        state = PageOps.rotate(state, setOf(0), 270) { _, _ -> 1f }
        assertEquals(90, state.pages[0].extraRotation)
    }

    @Test
    fun rotate_ignoresOutOfRangeIndicesAndZeroDelta() {
        val original = edit(listOf(PageSpec(0)), listOf(annotation("a", page = 0)))

        assertEquals(original, PageOps.rotate(original, setOf(4), 90) { _, _ -> 1f })
        assertEquals(original, PageOps.rotate(original, setOf(0), 0) { _, _ -> 1f })
    }

    // ------------------------------------------------------------------ delete

    @Test
    fun delete_removesPageDataAndRemapsLaterIndices() {
        val original = edit(
            pages = pages(10, 11, 12, 13),
            annotations = listOf(
                annotation("a", page = 0),
                annotation("b", page = 1),
                annotation("c", page = 2),
                annotation("d", page = 3),
            ),
            notes = mapOf(0 to "p0", 1 to "p1", 2 to "p2", 3 to "p3"),
            currentPage = 2,
        )

        val result = PageOps.delete(original, setOf(1))

        assertEquals(listOf(PageSpec(10), PageSpec(12), PageSpec(13)), result.pages)
        assertEquals(listOf("a", "c", "d"), result.annotations.map { it.id })
        assertEquals(listOf(0, 1, 2), result.annotations.map { it.page })
        assertEquals(mapOf(0 to "p0", 1 to "p2", 2 to "p3"), result.notes)
        assertEquals(1, result.currentPage)
    }

    @Test
    fun delete_currentPage_landsOnFollowingPage() {
        val original = edit(pages = pages(0, 1, 2, 3), currentPage = 2)

        val result = PageOps.delete(original, setOf(2))

        assertEquals(listOf(PageSpec(0), PageSpec(1), PageSpec(3)), result.pages)
        assertEquals(2, result.currentPage)
    }

    @Test
    fun delete_currentLastPage_landsOnLastSurvivor() {
        val original = edit(pages = pages(0, 1, 2), currentPage = 2)

        val result = PageOps.delete(original, setOf(2))

        assertEquals(2, result.pages.size)
        assertEquals(1, result.currentPage)
    }

    @Test
    fun delete_everything_keepsOneBlankPage() {
        val original = edit(
            pages = pages(0, 1, 2),
            annotations = listOf(annotation("a", page = 0), annotation("b", page = 2)),
            notes = mapOf(0 to "x", 2 to "y"),
            currentPage = 1,
        )

        val result = PageOps.delete(original, setOf(0, 1, 2))

        assertEquals(listOf(PageSpec(null)), result.pages)
        assertTrue(result.annotations.isEmpty())
        assertTrue(result.notes.isEmpty())
        assertEquals(0, result.currentPage)
    }

    @Test
    fun delete_ignoresUnknownIndices() {
        val original = edit(pages = pages(0, 1), currentPage = 1)

        assertEquals(original, PageOps.delete(original, setOf(5, -2)))
    }

    // --------------------------------------------------------------- duplicate

    @Test
    fun duplicate_insertsCopiesAfterEachSelectedPage() {
        val original = edit(
            pages = pages(10, 11, 12),
            annotations = listOf(
                annotation("a", page = 0),
                annotation("b", page = 1),
                annotation("c", page = 2),
            ),
            notes = mapOf(0 to "p0", 2 to "p2"),
            currentPage = 1,
        )

        val result = PageOps.duplicate(original, setOf(0, 2))

        assertEquals(
            listOf(PageSpec(10), PageSpec(10), PageSpec(11), PageSpec(12), PageSpec(12)),
            result.pages,
        )
        assertEquals(listOf("a", "b", "c"), result.annotations.take(3).map { it.id })
        assertEquals(listOf(0, 2, 3), result.annotations.take(3).map { it.page })
        val copies = result.annotations.drop(3)
        assertEquals(2, copies.size)
        assertEquals(listOf(1, 4), copies.map { it.page })
        assertNotEquals("a", copies[0].id)
        assertNotEquals("c", copies[1].id)
        assertEquals(5, result.annotations.map { it.id }.toSet().size)
        assertEquals("note", copies[0].text)
        assertEquals(0.004f, copies[0].width, 1e-6f)
        assertEquals(mapOf(0 to "p0", 1 to "p0", 3 to "p2", 4 to "p2"), result.notes)
        // Page 1 (source 11) moved to index 2 because a copy followed page 0.
        assertEquals(2, result.currentPage)
    }

    @Test
    fun duplicate_nothingSelected_isIdentity() {
        val original = edit(pages = pages(0, 1), currentPage = 0)

        assertEquals(original, PageOps.duplicate(original, emptySet()))
    }

    // -------------------------------------------------------------------- move

    @Test
    fun move_groupEarlier_movesTogetherAndRemapsData() {
        val original = edit(
            pages = pages(0, 1, 2, 3),
            annotations = listOf(
                annotation("a", page = 0),
                annotation("b", page = 1),
                annotation("c", page = 2),
                annotation("d", page = 3),
            ),
            notes = mapOf(1 to "p1", 2 to "p2"),
            currentPage = 1,
        )

        val result = PageOps.move(original, setOf(1, 2), -1)

        assertEquals(
            listOf(PageSpec(1), PageSpec(2), PageSpec(0), PageSpec(3)),
            result.pages,
        )
        assertEquals(listOf(2, 0, 1, 3), result.annotations.map { it.page })
        assertEquals(mapOf(0 to "p1", 1 to "p2"), result.notes)
        assertEquals(0, result.currentPage)
    }

    @Test
    fun move_groupLater_movesTogether() {
        val original = edit(
            pages = pages(0, 1, 2, 3),
            annotations = listOf(annotation("b", page = 1), annotation("c", page = 2)),
            notes = mapOf(1 to "p1", 2 to "p2"),
            currentPage = 2,
        )

        val result = PageOps.move(original, setOf(1, 2), 1)

        assertEquals(
            listOf(PageSpec(0), PageSpec(3), PageSpec(1), PageSpec(2)),
            result.pages,
        )
        assertEquals(listOf(2, 3), result.annotations.map { it.page })
        assertEquals(mapOf(2 to "p1", 3 to "p2"), result.notes)
        assertEquals(3, result.currentPage)
    }

    @Test
    fun move_atBoundaries_doesNothing() {
        val first = edit(pages = pages(0, 1, 2), currentPage = 0)
        val last = edit(pages = pages(0, 1, 2), currentPage = 2)

        assertEquals(first, PageOps.move(first, setOf(0), -1))
        assertEquals(last, PageOps.move(last, setOf(2), 1))
    }

    @Test
    fun move_skipsOverUnselectedNeighbour() {
        val original = edit(pages = pages(0, 1, 2, 3), currentPage = 2)

        val result = PageOps.move(original, setOf(0, 2), -1)

        assertEquals(
            listOf(PageSpec(0), PageSpec(2), PageSpec(1), PageSpec(3)),
            result.pages,
        )
        assertEquals(1, result.currentPage)
    }

    @Test
    fun move_ignoresUnknownIndices() {
        val original = edit(pages = pages(0, 1), currentPage = 0)

        assertEquals(original, PageOps.move(original, setOf(8), 1))
    }

    // ------------------------------------------------------------- insert blank

    @Test
    fun insertBlank_shiftsAnnotationsNotesAndCurrentPage() {
        val original = edit(
            pages = pages(10, 11, 12),
            annotations = listOf(
                annotation("a", page = 0),
                annotation("b", page = 1),
                annotation("c", page = 2),
            ),
            notes = mapOf(0 to "p0", 1 to "p1", 2 to "p2"),
            currentPage = 2,
        )

        val result = PageOps.insertBlank(original, afterIndex = 0, count = 2)

        assertEquals(
            listOf(PageSpec(10), PageSpec(null), PageSpec(null), PageSpec(11), PageSpec(12)),
            result.pages,
        )
        assertEquals(listOf(0, 3, 4), result.annotations.map { it.page })
        assertEquals(mapOf(0 to "p0", 3 to "p1", 4 to "p2"), result.notes)
        assertEquals(4, result.currentPage)
    }

    @Test
    fun insertBlank_atBeginningMovesExistingContentDown() {
        val original = edit(
            pages = pages(10, 11),
            annotations = listOf(annotation("a", page = 0)),
            notes = mapOf(0 to "p0"),
            currentPage = 0,
        )

        val result = PageOps.insertBlank(original, afterIndex = -1)

        assertEquals(listOf(PageSpec(null), PageSpec(10), PageSpec(11)), result.pages)
        assertEquals(1, result.annotations[0].page)
        assertEquals(mapOf(1 to "p0"), result.notes)
        assertEquals(1, result.currentPage)
    }

    @Test
    fun insertBlank_clampsAfterIndexAndHandlesZeroCount() {
        val original = edit(
            pages = pages(10, 11),
            annotations = listOf(annotation("a", page = 1)),
            currentPage = 1,
        )

        val atEnd = PageOps.insertBlank(original, afterIndex = 99)
        assertEquals(listOf(PageSpec(10), PageSpec(11), PageSpec(null)), atEnd.pages)
        assertEquals(listOf(1), atEnd.annotations.map { it.page })
        assertEquals(1, atEnd.currentPage)

        assertEquals(original, PageOps.insertBlank(original, afterIndex = 0, count = 0))
    }

    @Test
    fun insertBlank_intoEmptyDocumentCreatesFirstPage() {
        val result = PageOps.insertBlank(edit(emptyList()), afterIndex = -1)

        assertEquals(listOf(PageSpec(null)), result.pages)
        assertEquals(0, result.currentPage)
    }

    // ------------------------------------------------------------- bookmarks

    @Test
    fun delete_earlierPageMovesBookmarkDownLikeAnnotations() {
        val original = edit(
            pages = pages(0, 1, 2),
            annotations = listOf(annotation("ink", page = 2)),
            bookmarks = listOf(bookmark("b", 2)),
            currentPage = 2,
        )

        val result = PageOps.delete(original, setOf(0))

        assertEquals(1, result.annotations.single().page)
        assertEquals(1, result.bookmarks.single().pageIndex)
        assertEquals("b", result.bookmarks.single().id)
        assertEquals("b title", result.bookmarks.single().title)
    }

    @Test
    fun delete_removesBookmarksOnDeletedPagesAndRemapsSurvivors() {
        val original = edit(
            pages = pages(0, 1, 2, 3),
            bookmarks = listOf(
                bookmark("a", 0),
                bookmark("b", 1),
                bookmark("c", 2),
                bookmark("d", 3),
            ),
        )

        val result = PageOps.delete(original, setOf(1))

        assertEquals(listOf("a", "c", "d"), result.bookmarks.map { it.id })
        assertEquals(listOf(0, 1, 2), result.bookmarks.map { it.pageIndex })
        assertEquals("c title", result.bookmarks[1].title)
    }

    @Test
    fun delete_everything_dropsAllBookmarks() {
        val original = edit(
            pages = pages(0, 1, 2),
            bookmarks = listOf(bookmark("a", 0), bookmark("c", 2)),
        )

        val result = PageOps.delete(original, setOf(0, 1, 2))

        assertTrue(result.bookmarks.isEmpty())
    }

    @Test
    fun duplicate_copiesBookmarksWithFreshIdsAndShiftsOriginals() {
        val original = edit(
            pages = pages(0, 1, 2),
            bookmarks = listOf(bookmark("a", 0), bookmark("c", 2)),
        )

        val result = PageOps.duplicate(original, setOf(0))

        assertEquals(listOf("a", "c"), result.bookmarks.take(2).map { it.id })
        assertEquals(listOf(0, 3), result.bookmarks.take(2).map { it.pageIndex })
        val copies = result.bookmarks.drop(2)
        assertEquals(1, copies.size)
        assertEquals(1, copies[0].pageIndex)
        assertEquals("a title", copies[0].title)
        assertNotEquals("a", copies[0].id)
        assertEquals(3, result.bookmarks.map { it.id }.toSet().size)
    }

    @Test
    fun move_remapsBookmarksWithTheirPages() {
        val original = edit(
            pages = pages(0, 1, 2, 3),
            bookmarks = listOf(bookmark("b", 1), bookmark("c", 2)),
        )

        val result = PageOps.move(original, setOf(1, 2), -1)

        assertEquals(mapOf("b" to 0, "c" to 1), result.bookmarks.associate { it.id to it.pageIndex })
    }

    @Test
    fun insertBlank_shiftsBookmarksAfterInsertion() {
        val original = edit(
            pages = pages(0, 1, 2),
            bookmarks = listOf(bookmark("a", 0), bookmark("c", 2)),
        )

        val result = PageOps.insertBlank(original, afterIndex = 0, count = 2)

        assertEquals(mapOf("a" to 0, "c" to 4), result.bookmarks.associate { it.id to it.pageIndex })
    }

    @Test
    fun rotate_keepsBookmarksOnTheirPages() {
        val original = edit(
            pages = listOf(PageSpec(0)),
            bookmarks = listOf(bookmark("a", 0)),
        )

        val result = PageOps.rotate(original, setOf(0), 90) { _, _ -> 1f }

        assertEquals(original.bookmarks, result.bookmarks)
    }

    // ------------------------------------------------------------- select all

    @Test
    fun selectAllIndices_handlesCounts() {
        assertEquals(setOf(0, 1, 2, 3), PageOps.selectAllIndices(4))
        assertEquals(emptySet<Int>(), PageOps.selectAllIndices(0))
        assertEquals(emptySet<Int>(), PageOps.selectAllIndices(-3))
    }
}
