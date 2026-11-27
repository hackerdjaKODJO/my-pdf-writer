package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class AnnotationObjectEditTest {

    @Test
    fun `duplicate inserts after original and regenerates only identity`() {
        val first = annotation("first", page = 0)
        val otherPage = annotation("other", page = 1)
        val last = annotation("last", page = 0)

        val duplicated = duplicateAnnotationIn(listOf(first, otherPage, last), "first", "copy")

        assertEquals(listOf("first", "copy", "other", "last"), duplicated.map { it.id })
        assertEquals(first.type, duplicated[1].type)
        assertEquals(first.points, duplicated[1].points)
        assertEquals(first.createdAt, duplicated[1].createdAt)
        assertNotSame(first, duplicated[1])
        assertSame(first, duplicated[0])
    }

    @Test
    fun `order changes stay inside the selected page`() {
        val annotations = listOf(
            annotation("p0-a", page = 0),
            annotation("p1-a", page = 1),
            annotation("p0-b", page = 0),
            annotation("p0-c", page = 0),
        )

        assertEquals(
            listOf("p0-a", "p1-a", "p0-b", "p0-c"),
            moveAnnotationOrderIn(annotations, "p0-c", forward = true).map { it.id },
        )
        assertEquals(
            listOf("p0-a", "p1-a", "p0-c", "p0-b"),
            moveAnnotationOrderIn(annotations, "p0-c", forward = false).map { it.id },
        )
        assertEquals(
            listOf("p0-a", "p1-a", "p0-c", "p0-b"),
            moveAnnotationOrderIn(annotations, "p0-b", forward = true).map { it.id },
        )
    }

    @Test
    fun `unknown ids and boundary moves are no ops`() {
        val annotations = listOf(annotation("only", page = 0))

        assertSame(annotations, duplicateAnnotationIn(annotations, "missing", "copy"))
        assertSame(annotations, moveAnnotationOrderIn(annotations, "only", forward = true))
        assertSame(annotations, moveAnnotationOrderIn(annotations, "only", forward = false))
    }

    private fun annotation(id: String, page: Int) = Annotation(
        id = id,
        type = AnnotationType.RECT,
        page = page,
        color = 0xFF000000.toInt(),
        width = 0.004f,
        points = listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.8f, 0.8f)),
    )
}
