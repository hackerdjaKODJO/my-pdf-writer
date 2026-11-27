package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.triangleVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeGeometryTest {

    @Test
    fun `two drag corners become three explicit triangle vertices`() {
        val vertices = triangleVertices(
            listOf(
                NormalizedPoint(0.8f, 0.1f, pressure = 0.4f, timeMs = 7L),
                NormalizedPoint(0.2f, 0.7f, pressure = 0.8f, timeMs = 19L),
            ),
        )

        assertEquals(3, vertices.size)
        assertEquals(0.5f, vertices[0].x, 0.0001f)
        assertEquals(0.1f, vertices[0].y, 0.0001f)
        assertEquals(0.2f, vertices[1].x, 0.0001f)
        assertEquals(0.7f, vertices[1].y, 0.0001f)
        assertEquals(0.8f, vertices[2].x, 0.0001f)
        assertEquals(0.7f, vertices[2].y, 0.0001f)
        assertEquals(0.4f, vertices[0].pressure, 0.0001f)
        assertEquals(7L, vertices[0].timeMs)
    }

    @Test
    fun `stored triangle vertices survive without rebuilding their orientation`() {
        val original = listOf(
            NormalizedPoint(0.4f, 0.2f),
            NormalizedPoint(0.1f, 0.8f),
            NormalizedPoint(0.9f, 0.6f),
        )

        assertEquals(original, triangleVertices(original))
        assertTrue(triangleVertices(emptyList()).isEmpty())
    }

    @Test
    fun `pdf and note commits store all three triangle vertices`() {
        val drag = listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f))
        val pdf = pdfStrokeAnnotation(
            state = ViewerUiState(pageIndex = 2, tool = Tool.TRIANGLE),
            type = AnnotationType.TRIANGLE,
            points = drag,
            durationMs = 0L,
            nowMs = 123L,
        )
        val note = noteStrokeAnnotation(
            type = AnnotationType.TRIANGLE,
            color = 0xFF000000.toInt(),
            strokeWidth = 0.0001f,
            points = drag,
            opacity = 1f,
            penStyle = PenStyle.BALLPOINT,
            layerId = null,
            durationMs = 0L,
            nowMs = 123L,
        )

        assertEquals(3, pdf.points.size)
        assertEquals(3, note.points.size)
        assertEquals(pdf.points, note.points)
        assertEquals(2, pdf.page)
        assertEquals(123L, pdf.createdAt)
    }

    @Test
    fun `pdf eraser follows all three triangle edges but not the empty center`() {
        val triangle = Annotation(
            type = AnnotationType.TRIANGLE,
            page = 0,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = triangleVertices(
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f)),
            ),
        )
        val vertical = listOf(NormalizedPoint(0.5f, 0.1f), NormalizedPoint(0.5f, 0.9f))
        val inside = listOf(NormalizedPoint(0.49f, 0.5f), NormalizedPoint(0.51f, 0.5f))

        assertTrue(pdfEraserHitsAnnotation(triangle, vertical, pdfEraserRadius(1f, false)))
        assertFalse(pdfEraserHitsAnnotation(triangle, inside, pdfEraserRadius(1f, false)))
    }

    @Test
    fun `triangle lasso selection uses the full three vertex bounds`() {
        val triangle = Annotation(
            id = "triangle",
            type = AnnotationType.TRIANGLE,
            page = 0,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = triangleVertices(
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f)),
            ),
        )
        val around = listOf(
            NormalizedPoint(0.1f, 0.1f),
            NormalizedPoint(0.9f, 0.9f),
        )
        val baseRight = listOf(
            NormalizedPoint(0.70f, 0.70f),
            NormalizedPoint(0.90f, 0.90f),
        )

        assertEquals(setOf("triangle"), noteLassoSelection(listOf(triangle), around, rectangular = true))
        assertEquals(setOf("triangle"), noteLassoSelection(listOf(triangle), baseRight, rectangular = true))
    }

    @Test
    fun `triangle json round trip preserves all vertices`() {
        val original = Annotation(
            type = AnnotationType.TRIANGLE,
            page = 1,
            color = 0xFF336699.toInt(),
            width = 0.004f,
            points = triangleVertices(
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f)),
            ),
        )

        val restored = requireNotNull(Annotation.fromJson(original.toJson()))

        assertEquals(original, restored)
        assertEquals(3, restored.points.size)
        assertEquals(AnnotationType.TRIANGLE, noteAnnotationTypeForTool(Tool.TRIANGLE))
    }
}
