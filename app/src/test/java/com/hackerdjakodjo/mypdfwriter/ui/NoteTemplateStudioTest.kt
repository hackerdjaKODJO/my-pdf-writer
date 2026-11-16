package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NoteTemplateStudioTest {

    private fun fullPageInk() = listOf(
        Annotation(
            type = AnnotationType.PEN,
            page = 0,
            color = 0xFF1B1C19.toInt(),
            width = 0.004f,
            points = listOf(NormalizedPoint(0.05f, 0.05f), NormalizedPoint(0.95f, 0.95f)),
        ),
    )

    private fun choice(
        applyAll: Boolean,
        orientation: PageOrientation = PageOrientation.LANDSCAPE,
    ) = NoteTemplateStudioChoice(
        template = PaperTemplate.GRID,
        tint = PaperTint.PALE_YELLOW,
        orientation = orientation,
        applyToAllPages = applyAll,
        coverColor = 0xFFDCE8DC.toInt(),
        coverStyle = CoverStyle.STRIPES,
    )

    private val notebook = Notebook(
        title = "스튜디오",
        coverColor = 0xFFD8E6F3.toInt(),
        pages = listOf(
            NotePage(id = "p0", template = PaperTemplate.LINED, annotations = fullPageInk()),
            NotePage(id = "p1", template = PaperTemplate.DOTTED),
        ),
    )

    @Test
    fun applyCurrentChangesOnePageAndTheCover() {
        val result = NoteTemplateStudio.apply(notebook, currentIndex = 0, choice = choice(false))

        assertEquals(PageOrientation.LANDSCAPE, result.pages[0].orientation)
        assertEquals(PaperTemplate.GRID, result.pages[0].template)
        assertEquals(PaperTint.PALE_YELLOW, result.pages[0].tint)
        assertEquals("p1", result.pages[1].id)
        assertEquals(PaperTemplate.DOTTED, result.pages[1].template)
        assertEquals(PageOrientation.PORTRAIT, result.pages[1].orientation)
        assertEquals(PaperTint.WARM, result.pages[1].tint)
        assertEquals(0xFFDCE8DC.toInt(), result.coverColor)
        assertEquals(CoverStyle.STRIPES, result.coverStyle)
    }

    @Test
    fun applyAllChangesEveryPageAndKeepsPageIds() {
        val result = NoteTemplateStudio.apply(notebook, currentIndex = 0, choice = choice(true))

        assertEquals(listOf("p0", "p1"), result.pages.map { it.id })
        result.pages.forEach { page ->
            assertEquals(PageOrientation.LANDSCAPE, page.orientation)
            assertEquals(PaperTemplate.GRID, page.template)
            assertEquals(PaperTint.PALE_YELLOW, page.tint)
        }
        assertEquals(1, result.pages[0].annotations.size)
        assertFalse(result.pages[1].annotations.isNotEmpty())
    }

    @Test
    fun cropWarningCountsOnlyPagesThatChangeOrientation() {
        val pending = choice(applyAll = true)

        assertEquals(1, NoteTemplateStudio.cropRiskCount(notebook, 0, pending))
        assertEquals(
            0,
            NoteTemplateStudio.cropRiskCount(
                notebook,
                1,
                pending.copy(applyToAllPages = false),
            ),
        )
        assertEquals(
            0,
            NoteTemplateStudio.cropRiskCount(
                notebook,
                0,
                pending.copy(orientation = PageOrientation.PORTRAIT),
            ),
        )
        assertEquals(1, NoteTemplateStudio.targetIndices(notebook.pages, 0, false).size)
        assertEquals(2, NoteTemplateStudio.targetIndices(notebook.pages, 1, true).size)
    }

    @Test
    fun reorientsExistingInkReportsOnlyInkPagesThatMove() {
        val pending = choice(applyAll = false)

        assertEquals(true, NoteTemplateStudio.reorientsExistingInk(notebook, 0, pending))
        assertEquals(
            false,
            NoteTemplateStudio.reorientsExistingInk(notebook, 1, pending),
        )
    }
}
