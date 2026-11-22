package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageBookmark
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePageManagerTest {

    private fun annotation(id: String, layerId: String) = Annotation(
        id = id,
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.4f, 0.5f)),
        layerId = layerId,
    )

    private fun page(id: String, template: PaperTemplate, annotationIds: List<String>, layerId: String? = null) =
        NotePage(
            id = id,
            template = template,
            layers = listOf(NoteLayer(id = layerId ?: "layer-$id")),
            annotations = annotationIds.map { annotation(it, layerId ?: "layer-$id") },
        )

    private fun notebook(vararg pages: NotePage, bookmarks: List<PageBookmark> = emptyList()) =
        Notebook(id = "notebook", pages = pages.toList(), bookmarks = bookmarks)

    private fun memo(id: String) = MemoCard(
        id = id,
        text = "복습 메모",
        tint = MemoTint.SKY,
        x = 0.18f,
        y = 0.24f,
        w = 0.36f,
        h = 0.22f,
        tilt = -3f,
        createdAt = 111L,
        updatedAt = 222L,
    )

    @Test
    fun entriesExposeAllAndAnnotatedPagesInOrder() {
        val pages = listOf(
            page("p0", PaperTemplate.LINED, emptyList()),
            page("p1", PaperTemplate.GRID, listOf("a", "b")),
            page("p2", PaperTemplate.DOTTED, listOf("c")),
        )

        val all = NotePageManager.entries(pages, NotePageFilter.ALL)
        assertEquals(listOf(0, 1, 2), all.map { it.index })
        assertEquals(listOf("p0", "p1", "p2"), all.map { it.id })
        assertEquals(listOf(0, 2, 1), all.map { it.annotationCount })
        assertEquals(PaperTemplate.GRID, all[1].template)
        assertEquals(2, NotePageManager.annotatedCount(pages))

        val annotated = NotePageManager.entries(pages, NotePageFilter.ANNOTATED)
        assertEquals(listOf(1, 2), annotated.map { it.index })
        assertEquals(listOf("p1", "p2"), annotated.map { it.id })
    }

    @Test
    fun annotatedFilterCountsAnnotationsOnHiddenLayers() {
        val hidden = page("hidden", PaperTemplate.LINED, listOf("ink"), layerId = "hidden-layer")
            .copy(layers = listOf(NoteLayer(id = "hidden-layer", visible = false)))

        val entries = NotePageManager.entries(listOf(hidden), NotePageFilter.ANNOTATED)

        assertEquals(1, entries.size)
        assertEquals(0, entries.single().index)
        assertEquals(1, entries.single().annotationCount)
    }

    @Test
    fun selectionIgnoresUnknownIdsAndKeepsPageOrder() {
        val pages = listOf(
            page("p0", PaperTemplate.LINED, emptyList()),
            page("p1", PaperTemplate.LINED, emptyList()),
            page("p2", PaperTemplate.LINED, emptyList()),
        )

        val selected = NotePageManager.toggleSelection(
            pages,
            selectedPageIds = setOf("p2", "missing"),
            pageId = "p0",
        )

        assertEquals(listOf("p0", "p2"), selected.toList())
        assertEquals(setOf("p2"), NotePageManager.validSelection(pages, setOf("p2", "gone")))

        val toggledOff = NotePageManager.toggleSelection(pages, selected, "p2")
        assertEquals(setOf("p0"), toggledOff)

        val unknown = NotePageManager.toggleSelection(pages, selected, "not-a-page")
        assertEquals(selected, unknown)
    }

    @Test
    fun duplicateInsertsCopiesAfterEachSelectedPageAndKeepsIds() {
        val first = page("p0", PaperTemplate.LINED, listOf("a"))
        val middle = page("p1", PaperTemplate.GRID, listOf("b"))
        val last = page("p2", PaperTemplate.DOTTED, listOf("c"))
        val source = notebook(
            first,
            middle,
            last,
            bookmarks = listOf(
                PageBookmark(id = "b0", pageIndex = 0, title = "첫"),
                PageBookmark(id = "b2", pageIndex = 2, title = "끝"),
            ),
        )

        val result = NotePageManager.duplicatePages(
            notebook = source,
            selectedPageIds = setOf("p0", "p2"),
            currentIndex = 2,
        )

        assertEquals(5, result.notebook.pages.size)
        assertEquals(listOf("p0", null, "p1", "p2", null), result.notebook.pages.map { page ->
            page.id.takeIf { it in setOf("p0", "p1", "p2") }
        })
        assertEquals(listOf("p0", "p1", "p2"), result.notebook.pages.filter { it.id in setOf("p0", "p1", "p2") }.map { it.id })
        assertEquals("the copied page keeps the template", first.template, result.notebook.pages[1].template)
        assertEquals("the copied page keeps the source layer", first.layers, result.notebook.pages[1].layers)
        assertNotEquals("the copied page gets a fresh id", "p0", result.notebook.pages[1].id)
        assertNotEquals("the copied annotation gets a fresh id", "a", result.notebook.pages[1].annotations.single().id)
        assertEquals(0, result.notebook.pages[1].annotations.single().page)
        assertEquals(setOf("p0", "p2"), result.selectedPageIds)
        assertEquals("current page still points at p2", 3, result.currentIndex)
        assertEquals(listOf("b0" to 0, "b2" to 3), result.notebook.bookmarks.map { it.id to it.pageIndex })

        val copyOfMiddle = NotePageManager.duplicatePages(
            notebook = source,
            selectedPageIds = setOf("p1"),
            currentIndex = 1,
        )
        assertEquals(listOf("p0", "p1", "copy", "p2"), copyOfMiddle.notebook.pages.map {
            it.id.takeIf { id -> id in setOf("p0", "p1", "p2") } ?: "copy"
        })
        assertSame(middle.layers, copyOfMiddle.notebook.pages[2].layers)
    }

    @Test
    fun duplicatePagesGivesCardsFreshIdsAndKeepsStudyData() {
        val card = MaskingCard(
            id = "study-card",
            prompt = "질문",
            answer = "정답",
            x = 0.2f,
            y = 0.3f,
            w = 0.4f,
            h = 0.1f,
            state = MaskingCardState.CORRECT,
            createdAt = 123L,
        )
        val sourcePage = page("p0", PaperTemplate.LINED, emptyList())
            .copy(maskingCards = listOf(card))
        val source = notebook(sourcePage)

        val result = NotePageManager.duplicatePages(source, setOf("p0"), currentIndex = 0)
        val original = result.notebook.pages[0]
        val copy = result.notebook.pages[1]

        assertEquals("study-card", original.maskingCards.single().id)
        assertEquals(1, copy.maskingCards.size)
        assertNotEquals("study-card", copy.maskingCards.single().id)
        assertEquals("질문", copy.maskingCards.single().prompt)
        assertEquals("정답", copy.maskingCards.single().answer)
        assertEquals(MaskingCardState.CORRECT, copy.maskingCards.single().state)
        assertEquals(0.2f, copy.maskingCards.single().x)
        assertEquals(0.4f, copy.maskingCards.single().w)
        assertEquals(123L, copy.maskingCards.single().createdAt)
        assertEquals("the source notebook is untouched", 1, source.pages[0].maskingCards.size)
    }

    @Test
    fun duplicatePagesGivesMemoCardsFreshIdsAndKeepsContent() {
        val sourcePage = page("p0", PaperTemplate.LINED, emptyList()).copy(
            memoCards = listOf(memo("memo-1"), memo("memo-2").copy(text = "둘째")),
        )
        val source = notebook(sourcePage)

        val result = NotePageManager.duplicatePages(source, setOf("p0"), currentIndex = 0)
        val original = result.notebook.pages[0]
        val copy = result.notebook.pages[1]

        assertEquals(listOf("memo-1", "memo-2"), original.memoCards.map { it.id })
        assertEquals(2, copy.memoCards.size)
        assertEquals(
            "no memo id is shared with the source or with another copy",
            4,
            (original.memoCards.map { it.id } + copy.memoCards.map { it.id }).toSet().size,
        )
        copy.memoCards.forEachIndexed { index, card ->
            val src = sourcePage.memoCards[index]
            assertNotEquals(src.id, card.id)
            assertEquals(src.text, card.text)
            assertEquals(src.tint, card.tint)
            assertEquals(src.x, card.x)
            assertEquals(src.y, card.y)
            assertEquals(src.w, card.w)
            assertEquals(src.h, card.h)
            assertEquals(src.tilt, card.tilt)
            assertEquals(src.createdAt, card.createdAt)
            assertEquals(src.updatedAt, card.updatedAt)
        }
        assertEquals("card order travels with the page", listOf("복습 메모", "둘째"), copy.memoCards.map { it.text })
        assertEquals(MemoTint.SKY, copy.memoCards.first().tint)
        assertEquals("the source notebook is untouched", 2, source.pages[0].memoCards.size)
        assertEquals("one commit adds exactly one page", source.pages.size + 1, result.notebook.pages.size)
    }

    @Test
    fun duplicatePagesRefreshesMaskingAndMemoIdsTogether() {
        val sourcePage = page("p0", PaperTemplate.LINED, emptyList()).copy(
            maskingCards = listOf(MaskingCard(id = "study-1", prompt = "질문", answer = "정답")),
            memoCards = listOf(memo("memo-1")),
        )

        val result = NotePageManager.duplicatePages(notebook(sourcePage), setOf("p0"), currentIndex = 0)
        val copy = result.notebook.pages[1]

        assertNotEquals("study-1", copy.maskingCards.single().id)
        assertEquals("질문", copy.maskingCards.single().prompt)
        assertEquals("정답", copy.maskingCards.single().answer)
        assertNotEquals("memo-1", copy.memoCards.single().id)
        assertEquals("복습 메모", copy.memoCards.single().text)
        assertEquals(MemoTint.SKY, copy.memoCards.single().tint)
    }

    @Test
    fun duplicatePagesOnLegacyPageWithoutCardsIsSafe() {
        val legacy = NotePage(id = "p0")

        val result = NotePageManager.duplicatePages(notebook(legacy), setOf("p0"), currentIndex = 0)

        val copy = result.notebook.pages[1]
        assertNotEquals("p0", copy.id)
        assertTrue(copy.memoCards.isEmpty())
        assertTrue(copy.maskingCards.isEmpty())
        assertTrue(copy.checklists.isEmpty())
        assertEquals("p0", result.notebook.pages[0].id)
    }

    @Test
    fun duplicateCarriesOrientationAndTint() {
        val sourcePage = NotePage(
            id = "p0",
            template = PaperTemplate.GRID,
            orientation = PageOrientation.LANDSCAPE,
            tint = PaperTint.LIGHT_GRAY,
            layers = listOf(NoteLayer(id = "layer-p0")),
        )
        val source = notebook(sourcePage)

        val result = NotePageManager.duplicatePages(
            notebook = source,
            selectedPageIds = setOf("p0"),
            currentIndex = 0,
        )

        val copy = result.notebook.pages[1]
        assertNotEquals("p0", copy.id)
        assertEquals(PaperTemplate.GRID, copy.template)
        assertEquals(PageOrientation.LANDSCAPE, copy.orientation)
        assertEquals(PaperTint.LIGHT_GRAY, copy.tint)
        assertEquals(sourcePage.layers, copy.layers)
    }

    @Test
    fun pageEntriesExposePaperOrientationAndTint() {
        val entries = NotePageManager.entries(
            listOf(
                NotePage(
                    id = "p0",
                    template = PaperTemplate.DOTTED,
                    orientation = PageOrientation.LANDSCAPE,
                    tint = PaperTint.PALE_YELLOW,
                ),
            ),
        )

        assertEquals(PageOrientation.LANDSCAPE, entries.single().orientation)
        assertEquals(PaperTint.PALE_YELLOW, entries.single().tint)
    }

    @Test
    fun duplicateWithNoSelectionIsIdentity() {
        val source = notebook(page("p0", PaperTemplate.LINED, emptyList()))

        val result = NotePageManager.duplicatePages(source, emptySet(), currentIndex = 9)

        assertEquals(source, result.notebook)
        assertEquals(0, result.currentIndex)
        assertTrue(result.selectedPageIds.isEmpty())
    }

    @Test
    fun deleteRemapsCurrentPageBookmarksAndSelection() {
        val source = notebook(
            page("p0", PaperTemplate.LINED, listOf("a")),
            page("p1", PaperTemplate.GRID, listOf("b")),
            page("p2", PaperTemplate.DOTTED, listOf("c")),
            page("p3", PaperTemplate.BLANK, listOf("d")),
            bookmarks = listOf(
                PageBookmark(id = "b0", pageIndex = 0, title = "첫"),
                PageBookmark(id = "b1", pageIndex = 1, title = "삭제"),
                PageBookmark(id = "b3", pageIndex = 3, title = "끝"),
            ),
        )

        val result = NotePageManager.deletePages(
            notebook = source,
            selectedPageIds = setOf("p1", "p2"),
            currentIndex = 2,
        )

        assertEquals(listOf("p0", "p3"), result.notebook.pages.map { it.id })
        assertEquals(1, result.currentIndex)
        assertEquals(listOf("b0" to 0, "b3" to 1), result.notebook.bookmarks.map { it.id to it.pageIndex })
        assertTrue(result.selectedPageIds.isEmpty())
    }

    @Test
    fun deleteNeverRemovesTheLastPage() {
        val source = notebook(
            page("p0", PaperTemplate.LINED, emptyList()),
            page("p1", PaperTemplate.GRID, emptyList()),
        )

        val result = NotePageManager.deletePages(
            notebook = source,
            selectedPageIds = setOf("p0", "p1"),
            currentIndex = 1,
        )

        assertEquals(source, result.notebook)
        assertEquals(1, result.currentIndex)
        assertEquals(setOf("p0", "p1"), result.selectedPageIds)
    }

    @Test
    fun deleteOnEmptyNotebookIsSafe() {
        val source = Notebook(id = "empty", pages = emptyList())

        val duplicate = NotePageManager.duplicatePages(source, setOf("gone"), currentIndex = 4)
        val deleted = NotePageManager.deletePages(source, setOf("gone"), currentIndex = 4)

        assertEquals(source, duplicate.notebook)
        assertEquals(0, duplicate.currentIndex)
        assertEquals(source, deleted.notebook)
        assertEquals(0, deleted.currentIndex)
    }

    @Test
    fun deleteConfirmationNamesTheNumberOfPages() {
        assertTrue("single page keeps the original copy", noteDeleteConfirmMessage(1).startsWith("이 페이지"))
        assertTrue("multi page names the count", noteDeleteConfirmMessage(3).contains("3쪽"))
        assertTrue(noteDeleteConfirmMessage(0).startsWith("이 페이지"))
    }

    @Test
    fun singlePageDeleteAndMoveKeepBookmarksOnTheirLogicalPages() {
        val pages = listOf(NotePage(), NotePage(), NotePage())
        val notebook = Notebook(
            pages = pages,
            bookmarks = listOf(
                PageBookmark(pageIndex = 0, title = "A"),
                PageBookmark(pageIndex = 2, title = "C"),
            ),
        )
        val (afterDelete, newIndex) = NotePageManager.deletePage(notebook, 1)
        assertEquals(2, afterDelete.pages.size)
        assertEquals(0, newIndex)
        assertEquals(
            listOf(0, 1),
            afterDelete.bookmarks.map { it.pageIndex },
        )

        val (afterMove, movedIndex) = NotePageManager.movePage(notebook, 0, 2)
        assertEquals(2, movedIndex)
        assertEquals(
            listOf(2, 1),
            afterMove.bookmarks.map { it.pageIndex },
        )
    }
}
