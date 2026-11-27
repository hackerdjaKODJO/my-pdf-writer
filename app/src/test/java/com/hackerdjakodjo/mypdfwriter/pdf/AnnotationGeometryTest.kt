package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.triangleVertices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationGeometryTest {

    @Test
    fun `point shapes use every vertex`() {
        val triangle = Annotation(
            type = AnnotationType.TRIANGLE,
            page = 0,
            color = 0,
            width = 0.01f,
            points = triangleVertices(
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f)),
            ),
        )

        val bounds = requireNotNull(annotationBounds(triangle, 600f, 800f))

        assertEquals(0.2f, bounds.left, 0.0001f)
        assertEquals(0.2f, bounds.top, 0.0001f)
        assertEquals(0.8f, bounds.right, 0.0001f)
        assertEquals(0.8f, bounds.bottom, 0.0001f)
    }

    @Test
    fun `horizontal line selection bounds stay usable`() {
        val line = Annotation(
            type = AnnotationType.LINE,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.2f, 0.5f), NormalizedPoint(0.8f, 0.5f)),
        )

        val bounds = requireNotNull(annotationSelectionBounds(line, 600f, 800f))

        assertTrue(bounds.isValid)
        assertTrue(bounds.height >= 0.004f)
    }

    @Test
    fun `text bounds include all lines and rotation`() {
        val text = Annotation(
            type = AnnotationType.TEXT,
            page = 0,
            color = 0,
            width = 0f,
            points = listOf(NormalizedPoint(0.5f, 0.5f)),
            text = "가나다\n라",
            fontSize = 0.02f,
            textRotation = 90,
        )

        val bounds = requireNotNull(annotationBounds(text, 600f, 800f))

        assertTrue(bounds.left < 0.5f)
        assertEquals(0.5f, bounds.top, 0.0001f)
        assertTrue(bounds.bottom > 0.5f)
        assertTrue(bounds.width > bounds.height)
    }

    @Test
    fun `stamp bounds are centered and text width respects page aspect`() {
        val stamp = Annotation(
            type = AnnotationType.STAMP,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.5f, 0.5f)),
            stamp = "APPROVED",
            fontSize = 0.02f,
        )
        val stampBounds = requireNotNull(annotationBounds(stamp, 600f, 800f))
        assertTrue(stampBounds.left < 0.5f)
        assertTrue(stampBounds.right > 0.5f)
        assertTrue(stampBounds.top < 0.5f)
        assertTrue(stampBounds.bottom > 0.5f)

        val text = Annotation(
            type = AnnotationType.TEXT,
            page = 0,
            color = 0,
            width = 0f,
            points = listOf(NormalizedPoint(0.2f, 0.2f)),
            text = "가나다",
            fontSize = 0.02f,
        )
        val textBounds = requireNotNull(annotationBounds(text, 600f, 800f))
        assertEquals(3f * 0.02f * 0.56f * 800f / 600f, textBounds.width, 0.0001f)
    }

    @Test
    fun `move preserves timing pressure and annotation identity fields`() {
        val original = Annotation(
            id = "ink",
            type = AnnotationType.PEN,
            page = 2,
            color = 0xFF123456.toInt(),
            width = 0.004f,
            points = listOf(
                NormalizedPoint(0.2f, 0.3f, pressure = 0.4f, timeMs = 3L),
                NormalizedPoint(0.4f, 0.5f, pressure = 0.8f, timeMs = 9L),
            ),
            timesMs = listOf(3L, 9L),
            createdAt = 123L,
        )

        val moved = translateAnnotation(original, 0.1f, -0.05f)

        assertEquals("ink", moved.id)
        assertEquals(2, moved.page)
        assertEquals(listOf(3L, 9L), moved.timesMs)
        assertEquals(123L, moved.createdAt)
        assertEquals(0.4f, moved.points[0].pressure, 0f)
        assertEquals(3L, moved.points[0].timeMs)
        assertEquals(0.3f, moved.points[0].x, 0.0001f)
        assertEquals(0.25f, moved.points[0].y, 0.0001f)
    }

    @Test
    fun `resize maps every point from old to new bounds`() {
        val original = Annotation(
            type = AnnotationType.RECT,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.6f, 0.7f)),
        )
        val from = requireNotNull(annotationBounds(original))
        val to = AnnotationBounds(0.1f, 0.2f, 0.8f, 0.9f)

        val resized = resizeAnnotation(original, from, to)

        assertEquals(0.1f, resized.points[0].x, 0.0001f)
        assertEquals(0.2f, resized.points[0].y, 0.0001f)
        assertEquals(0.8f, resized.points[1].x, 0.0001f)
        assertEquals(0.9f, resized.points[1].y, 0.0001f)
    }

    @Test
    fun `rotation uses page aspect and updates text rotation`() {
        val line = Annotation(
            type = AnnotationType.LINE,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.25f, 0.5f), NormalizedPoint(0.75f, 0.5f)),
        )
        val rotated = rotateAnnotation(line, Math.toRadians(90.0).toFloat(), 600f, 800f)
        val dx = rotated.points[1].x - rotated.points[0].x
        val dy = rotated.points[1].y - rotated.points[0].y

        assertTrue(kotlin.math.abs(dx) < 0.0001f)
        assertTrue(dy > 0f)

        val text = Annotation(
            type = AnnotationType.TEXT,
            page = 0,
            color = 0,
            width = 0f,
            points = listOf(NormalizedPoint(0.5f, 0.5f)),
            text = "회전",
            textRotation = 10,
        )
        assertEquals(100, rotateAnnotation(text, Math.toRadians(90.0).toFloat()).textRotation)
    }

    @Test
    fun `whole object translation stays recoverable inside the page`() {
        val original = Annotation(
            type = AnnotationType.RECT,
            page = 0,
            color = 0,
            width = 0.01f,
            points = listOf(NormalizedPoint(0.7f, 0.7f), NormalizedPoint(0.9f, 0.9f)),
        )

        val moved = translateAnnotation(original, 1f, 1f, 600f, 800f)
        val bounds = requireNotNull(annotationSelectionBounds(moved, 600f, 800f))

        assertTrue(bounds.left >= 0f)
        assertTrue(bounds.top >= 0f)
        assertTrue(bounds.right <= 1.0001f)
        assertTrue(bounds.bottom <= 1.0001f)
    }

    @Test
    fun `invalid geometry fails safely`() {
        val empty = Annotation(type = AnnotationType.PEN, page = 0, color = 0, width = 0.004f)
        val invalid = empty.copy(points = listOf(NormalizedPoint(Float.NaN, 0.5f)))

        assertNull(annotationBounds(empty))
        assertNull(annotationBounds(invalid))
        assertSame(empty, translateAnnotation(empty, 0.1f, 0.1f))
        assertSame(invalid, rotateAnnotation(invalid, 1f))
        assertFalse(annotationHitTest(empty, NormalizedPoint(0.5f, 0.5f)))
        val malformedRect = empty.copy(
            type = AnnotationType.RECT,
            points = listOf(NormalizedPoint(0.5f, 0.5f)),
        )
        assertFalse(annotationHitTest(malformedRect, NormalizedPoint(0.5f, 0.5f)))
    }

    @Test
    fun `triangle interior and malformed rectangle hit testing are safe`() {
        val triangle = Annotation(
            type = AnnotationType.TRIANGLE,
            page = 0,
            color = 0,
            width = 0.004f,
            points = triangleVertices(
                listOf(NormalizedPoint(0.2f, 0.8f), NormalizedPoint(0.8f, 0.2f)),
            ),
        )
        assertTrue(annotationHitTest(triangle, NormalizedPoint(0.5f, 0.65f)))

        val malformedRect = Annotation(
            type = AnnotationType.RECT,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.5f, 0.5f)),
        )
        assertFalse(annotationHitTest(malformedRect, NormalizedPoint(0.5f, 0.5f)))
    }

    @Test
    fun `hit testing and rotation angle use normalized page coordinates`() {
        val annotation = Annotation(
            type = AnnotationType.RECT,
            page = 0,
            color = 0,
            width = 0.01f,
            points = listOf(NormalizedPoint(0.2f, 0.2f), NormalizedPoint(0.8f, 0.8f)),
        )

        assertTrue(annotationHitTest(annotation, NormalizedPoint(0.5f, 0.5f), slack = 0.01f))
        assertTrue(annotationHitTest(annotation, NormalizedPoint(0.2f, 0.5f), slack = 0.01f))
        assertFalse(annotationHitTest(annotation, NormalizedPoint(0.95f, 0.95f)))
        assertEquals(
            90f,
            annotationRotationDegrees(
                start = NormalizedPoint(0.8f, 0.5f),
                current = NormalizedPoint(0.5f, 0.8f),
                centerX = 0.5f,
                centerY = 0.5f,
                pageWidth = 600f,
                pageHeight = 600f,
            ),
            0.001f,
        )
        assertNotNull(annotationBounds(annotation))
    }
}
