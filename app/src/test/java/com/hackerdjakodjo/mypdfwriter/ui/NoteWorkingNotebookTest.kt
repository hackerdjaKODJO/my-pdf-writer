package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
