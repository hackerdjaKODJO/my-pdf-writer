package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageBookmark
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePageRotationTest {

    private fun page(
        id: String,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        sourcePage: Int? = null,
    ) = NotePage(
        id = id,
        template = PaperTemplate.GRID,
        orientation = orientation,
        tint = PaperTint.WARM,
        sourcePageIndex = sourcePage,
        layers = listOf(NoteLayer(id = "layer-$id")),
        annotations = listOf(
            Annotation(
                id = "a-$id",
                type = AnnotationType.PEN,
                page = 0,
                color = 0,
                width = 0.004f,
                points = listOf(NormalizedPoint(0.2f, 0.3f, 0.4f)),
                layerId = "layer-$id",
            ),
        ),
    )

    private fun notebook(vararg pages: NotePage, bookmarks: List<PageBookmark> = emptyList()) =
        Notebook(id = "notebook", pages = pages.toList(), bookmarks = bookmarks)

    @Test
    fun rotationAppliesToSelectedPagesOnlyInOneResult() {
        val source = notebook(
            page("p0", sourcePage = 3),
            page("p1"),
            page("p2", sourcePage = 8),
            bookmarks = listOf(
                PageBookmark(id = "b0", pageIndex = 0, title = "first"),
                PageBookmark(id = "b2", pageIndex = 2, title = "last"),
            ),
        )

        val result = NotePageManager.rotatePages(
            notebook = source,
            selectedPageIds = setOf("p0", "p2"),
            currentIndex = 2,
            clockwise = true,
        )

        assertEquals(listOf("p0", "p1", "p2"), result.notebook.pages.map { it.id })
        assertEquals(PageOrientation.LANDSCAPE, result.notebook.pages[0].orientation)
        assertEquals(PageOrientation.PORTRAIT, result.notebook.pages[1].orientation)
        assertEquals(PageOrientation.LANDSCAPE, result.notebook.pages[2].orientation)
        assertEquals(3, result.notebook.pages[0].sourcePageIndex)
        assertEquals(8, result.notebook.pages[2].sourcePageIndex)
        assertEquals("a-p0", result.notebook.pages[0].annotations.single().id)
        assertEquals("layer-p0", result.notebook.pages[0].layers.single().id)
        assertEquals(2, result.currentIndex)
        assertEquals(setOf("p0", "p2"), result.selectedPageIds)
        assertEquals(source.bookmarks, result.notebook.bookmarks)
        assertEquals(1f - 0.3f, result.notebook.pages[0].annotations.single().points[0].x, 0.000001f)
        assertEquals(0.4f, result.notebook.pages[0].annotations.single().points[0].pressure, 0f)
    }

    @Test
    fun rotationWithoutSelectionRotatesOnlyTheCurrentPage() {
        val source = notebook(
            page("p0"),
            page("p1"),
        )

        val result = NotePageManager.rotatePages(
            notebook = source,
            selectedPageIds = emptySet(),
            currentIndex = 1,
            clockwise = false,
        )

        assertEquals(PageOrientation.PORTRAIT, result.notebook.pages[0].orientation)
        assertEquals(PageOrientation.LANDSCAPE, result.notebook.pages[1].orientation)
        assertEquals(1, result.currentIndex)
        assertTrue(result.selectedPageIds.isEmpty())
    }

    @Test
    fun rotationIgnoresUnknownSelectionIdsAndEmptyNotebooks() {
        val source = notebook(page("p0"))
        val result = NotePageManager.rotatePages(
            notebook = source,
            selectedPageIds = setOf("gone"),
            currentIndex = 4,
            clockwise = true,
        )
        assertEquals(PageOrientation.LANDSCAPE, result.notebook.pages.single().orientation)
        assertEquals(0, result.currentIndex)
        assertTrue(result.selectedPageIds.isEmpty())

        val empty = Notebook(id = "empty", pages = emptyList())
        val safe = NotePageManager.rotatePages(empty, setOf("gone"), 3, true)
        assertEquals(empty, safe.notebook)
        assertEquals(0, safe.currentIndex)
    }

    @Test
    fun photoPageInsertsAfterCurrentAndKeepsPortableAssetReference() {
        val first = page("p0", PageOrientation.LANDSCAPE)
        val second = page("p1")
        val third = page("p2")
        val source = notebook(
            first,
            second,
            third,
            bookmarks = listOf(
                PageBookmark(id = "b0", pageIndex = 0, title = "first"),
                PageBookmark(id = "b2", pageIndex = 2, title = "third"),
            ),
        )

        val result = NotePageManager.insertPhotoPage(
            notebook = source,
            currentIndex = 0,
            selectedPageIds = setOf("p1"),
            assetKey = "tpl-photo.png",
            displayName = " My   Photo ",
        )

        assertEquals(listOf("p0", null, "p1", "p2"), result.notebook.pages.map {
            it.id.takeIf { id -> id in setOf("p0", "p1", "p2") }
        })
        assertEquals(4, result.notebook.pages.size)
        val photo = result.notebook.pages[1]
        assertNotEquals("p0", photo.id)
        assertEquals(PageOrientation.LANDSCAPE, photo.orientation)
        assertEquals(PaperTemplate.GRID, photo.template)
        assertEquals(PaperTint.WARM, photo.tint)
        assertEquals("tpl-photo.png", photo.customBackground)
        assertEquals("My Photo", photo.customBackgroundName)
        assertTrue(photo.annotations.isEmpty())
        assertEquals(1, photo.layers.size)
        assertNull(photo.sourcePageIndex)
        assertEquals(1, result.currentIndex)
        assertEquals(setOf("p1"), result.selectedPageIds)
        assertEquals(listOf("b0" to 0, "b2" to 3), result.notebook.bookmarks.map { it.id to it.pageIndex })
    }

    @Test
    fun photoPageRejectsMissingOrUnsafeAssets() {
        val source = notebook(page("p0"))

        val bad = NotePageManager.insertPhotoPage(
            notebook = source,
            currentIndex = 0,
            selectedPageIds = emptySet(),
            assetKey = "../escape.png",
            displayName = "bad",
        )
        assertEquals(source, bad.notebook)
        assertEquals(0, bad.currentIndex)

        val empty = NotePageManager.insertPhotoPage(
            notebook = Notebook(id = "empty", pages = emptyList()),
            currentIndex = 0,
            selectedPageIds = emptySet(),
            assetKey = "tpl-ok.png",
            displayName = "ok",
        )
        assertEquals(0, empty.currentIndex)
        assertTrue(empty.notebook.pages.isEmpty())
    }

    @Test
    fun rotationRoundTripReturnsSamePhysicalInkNinetyDegreeOrientation() {
        val source = notebook(page("p0"))
        val clockwise = NotePageManager.rotatePages(source, setOf("p0"), 0, true).notebook
        val restored = NotePageManager.rotatePages(clockwise, setOf("p0"), 0, false).notebook
        val annotation = restored.pages.single().annotations.single()

        assertEquals(PageOrientation.PORTRAIT, restored.pages.single().orientation)
        assertEquals(0.2f, annotation.points.single().x, 0.000001f)
        assertEquals(0.3f, annotation.points.single().y, 0.000001f)
        assertEquals(NotePaper.PORTRAIT_ASPECT, NotePaper.aspect(restored.pages.single().orientation), 0.0001f)
    }
}
