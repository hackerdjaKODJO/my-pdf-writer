package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextEditTest {

    private fun text(id: String, value: String) = Annotation(
        id = id,
        type = AnnotationType.TEXT,
        page = 0,
        color = 0,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.5f, 0.5f)),
        text = value,
        fontSize = 0.03f,
        layerId = "layer-a",
        createdAt = 1234L,
    )

    @Test
    fun `replacing text keeps identity layer and timing`() {
        val original = text("a", "오타")
        val updated = noteReplaceAnnotationText(listOf(original, text("b", "유지")), "a", "수정")
        assertEquals("수정", updated[0].text)
        assertEquals(original.id, updated[0].id)
        assertEquals(original.layerId, updated[0].layerId)
        assertEquals(original.createdAt, updated[0].createdAt)
        assertEquals(original.fontSize, updated[0].fontSize)
        assertEquals("유지", updated[1].text)
        assertEquals(original, noteReplaceAnnotationText(listOf(original), "missing", "x").single())
    }

    @Test
    fun `edit is offered for exactly one text box`() {
        assertTrue(noteCanEditText(listOf(text("a", "글"))))
        assertFalse(noteCanEditText(emptyList()))
        assertFalse(noteCanEditText(listOf(text("a", "1"), text("b", "2"))))
        assertFalse(
            noteCanEditText(
                listOf(text("a", "1").copy(type = AnnotationType.PEN)),
            ),
        )
    }
}
