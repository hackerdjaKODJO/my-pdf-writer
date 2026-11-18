package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteWorkingNotebookTest {

    private fun stroke(id: String, x: Float) = Annotation(
        id = id,
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = listOf(NormalizedPoint(x, 0.2f), NormalizedPoint(x + 0.1f, 0.3f)),
    )

    @Test
    fun `two back to back strokes both survive a JSON round trip`() {
        val base = Notebook(id = "nb", pages = listOf(NotePage(id = "page")))

        val afterFirst = noteAppendAnnotation(base, 0, stroke("s1", 0.1f))
        val afterSecond = noteAppendAnnotation(afterFirst, 0, stroke("s2", 0.4f))

        assertEquals(listOf("s1", "s2"), afterSecond.pages[0].annotations.map { it.id })
        assertEquals(2, notebookAnnotationCount(afterSecond))

        val restored = Notebook.fromJson(JSONObject(afterSecond.toJson().toString()))
        assertEquals(listOf("s1", "s2"), restored.pages[0].annotations.map { it.id })
    }

    @Test
    fun `append only touches the requested page`() {
        val base = Notebook(
            id = "nb",
            pages = listOf(
                NotePage(id = "first"),
                NotePage(id = "second"),
            ),
        )

        val updated = noteAppendAnnotation(base, 1, stroke("s2", 0.4f))

        assertEquals(0, updated.pages[0].annotations.size)
        assertEquals(listOf("s2"), updated.pages[1].annotations.map { it.id })
        assertEquals(1, notebookAnnotationCount(updated))
    }

    @Test
    fun `newer parent notebook is adopted`() {
        val local = Notebook(id = "nb", updatedAt = 200L, pages = listOf(NotePage(id = "page")))
        val incoming = local.copy(id = "nb", updatedAt = 201L)

        assertTrue(shouldAdoptExternalNotebook(local, incoming))
    }

    @Test
    fun `older or equal parent snapshot never overwrites local edits`() {
        val local = Notebook(
            id = "nb",
            updatedAt = 200L,
            pages = listOf(NotePage(id = "page", annotations = listOf(stroke("local", 0.1f)))),
        )
        val older = local.copy(
            updatedAt = 199L,
            pages = listOf(NotePage(id = "page")),
        )
        val sameMillisecondEcho = local.copy(
            updatedAt = 200L,
            pages = listOf(NotePage(id = "page")),
        )

        assertFalse(shouldAdoptExternalNotebook(local, older))
        assertFalse(shouldAdoptExternalNotebook(local, sameMillisecondEcho))
    }

    @Test
    fun `a different notebook id is always adopted`() {
        val local = Notebook(id = "nb", updatedAt = 200L)
        val other = Notebook(id = "other", updatedAt = 100L)

        assertTrue(shouldAdoptExternalNotebook(local, other))
    }

    private fun cardPage() = NotePage(
        id = "source-page",
        template = PaperTemplate.GRID,
        orientation = PageOrientation.LANDSCAPE,
        tint = PaperTint.LIGHT_GRAY,
        annotations = listOf(stroke("ink-1", 0.2f)),
        memoCards = listOf(
            MemoCard(
                id = "memo-1",
                text = "첫 메모",
                tint = MemoTint.MINT,
                x = 0.15f,
                y = 0.22f,
                w = 0.4f,
                h = 0.25f,
                tilt = 4f,
                createdAt = 111L,
                updatedAt = 222L,
            ),
            MemoCard(id = "memo-2", text = "둘째 메모", tint = MemoTint.PINK),
        ),
        maskingCards = listOf(
            MaskingCard(id = "study-1", prompt = "질문", answer = "정답", state = MaskingCardState.CORRECT),
        ),
        checklists = listOf(ChecklistCard(id = "check-1", title = "할 일")),
    )

    @Test
    fun `duplicated page refreshes memo and masking ids and keeps content`() {
        val source = cardPage()

        val copy = duplicateNotePage(source)

        assertNotEquals(source.id, copy.id)
        assertNotEquals("ink-1", copy.annotations.single().id)
        assertEquals(2, copy.memoCards.size)
        copy.memoCards.forEachIndexed { index, card ->
            val original = source.memoCards[index]
            assertNotEquals(original.id, card.id)
            assertEquals(original.text, card.text)
            assertEquals(original.tint, card.tint)
            assertEquals(original.x, card.x)
            assertEquals(original.y, card.y)
            assertEquals(original.w, card.w)
            assertEquals(original.h, card.h)
            assertEquals(original.tilt, card.tilt)
            assertEquals(original.createdAt, card.createdAt)
            assertEquals(original.updatedAt, card.updatedAt)
        }
        assertEquals(listOf("첫 메모", "둘째 메모"), copy.memoCards.map { it.text })
        assertEquals(MemoTint.MINT, copy.memoCards.first().tint)
        assertEquals(MemoTint.PINK, copy.memoCards[1].tint)

        assertNotEquals("study-1", copy.maskingCards.single().id)
        assertEquals("질문", copy.maskingCards.single().prompt)
        assertEquals("정답", copy.maskingCards.single().answer)
        assertEquals(MaskingCardState.CORRECT, copy.maskingCards.single().state)
        assertNotEquals("check-1", copy.checklists.single().id)
        assertEquals("할 일", copy.checklists.single().title)

        assertEquals("paper style travels unchanged", PaperTemplate.GRID, copy.template)
        assertEquals(PageOrientation.LANDSCAPE, copy.orientation)
        assertEquals(PaperTint.LIGHT_GRAY, copy.tint)

        assertEquals("source page is untouched", "memo-1", source.memoCards.first().id)
        assertEquals("study-1", source.maskingCards.single().id)
    }

    @Test
    fun `page duplication is one notebook change with one inserted page`() {
        val sourcePage = cardPage()
        val other = NotePage(id = "other-page")
        val notebook = Notebook(id = "nb", pages = listOf(sourcePage, other))

        val updated = duplicatePageInNotebook(notebook, 0)!!

        assertEquals(3, updated.pages.size)
        assertEquals("source-page", updated.pages[0].id)
        assertNotEquals("source-page", updated.pages[1].id)
        assertEquals("other-page", updated.pages[2].id)
        assertEquals("the insert changes only the page list", notebook.copy(pages = updated.pages), updated)
        val sourceMemoIds = sourcePage.memoCards.map { it.id }.toSet()
        assertTrue("memo ids differ from the source", updated.pages[1].memoCards.map { it.id }.none { it in sourceMemoIds })
    }

    @Test
    fun `legacy page without cards duplicates to an empty card set`() {
        val legacy = NotePage(id = "legacy", template = PaperTemplate.LINED)
        val notebook = Notebook(id = "nb", pages = listOf(legacy))

        val copy = duplicateNotePage(legacy)
        val updated = duplicatePageInNotebook(notebook, 0)!!

        assertTrue(copy.memoCards.isEmpty())
        assertTrue(copy.maskingCards.isEmpty())
        assertTrue(copy.checklists.isEmpty())
        assertTrue(updated.pages[1].memoCards.isEmpty())
        assertTrue(updated.pages[1].maskingCards.isEmpty())
        assertEquals("legacy", updated.pages[0].id)
        assertEquals(null, duplicatePageInNotebook(notebook, 9))
    }
}
