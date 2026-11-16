package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSelectionTransformsTest {

    @Test
    fun `translate moves every point by the requested delta`() {
        val stroke = annotation(
            points = listOf(point(0.20f, 0.30f), point(0.40f, 0.50f)),
        )

        val moved = NoteSelectionTransform.translate(0.05f, -0.02f)
            .applyTo(listOf(stroke))
            .single()

        assertEquals(0.25f, moved.points[0].x, 0.00001f)
        assertEquals(0.28f, moved.points[0].y, 0.00001f)
        assertEquals(0.45f, moved.points[1].x, 0.00001f)
        assertEquals(0.48f, moved.points[1].y, 0.00001f)
        assertEquals(listOf(stroke.points[0].x, stroke.points[1].x), listOf(0.20f, 0.40f))
    }

    @Test
    fun `translate clamps at the page edge without collapsing the stroke`() {
        val stroke = annotation(
            points = listOf(point(0.90f, 0.20f), point(0.95f, 0.30f)),
        )

        val moved = NoteSelectionTransform.translate(0.20f, 0f)
            .applyTo(listOf(stroke))
            .single()

        // The group stops when its right edge reaches 1.0; both samples survive.
        assertEquals(0.95f, moved.points[0].x, 0.00001f)
        assertEquals(1.0f, moved.points[1].x, 0.00001f)
        assertTrue(moved.points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `translate scale and rotate preserve per-point pressure`() {
        val stroke = annotation(
            points = listOf(
                NormalizedPoint(0.20f, 0.20f, 0.30f),
                NormalizedPoint(0.40f, 0.45f, 0.65f),
                NormalizedPoint(0.60f, 0.60f, 1f),
            ),
            penStyle = PenStyle.FOUNTAIN,
        )
        val expected = listOf(0.30f, 0.65f, 1f)

        val moved = NoteSelectionTransform.translate(0.02f, -0.02f)
            .applyTo(listOf(stroke))
            .single()
        val scaled = NoteSelectionTransform.scaleAroundCenter(1.1f)
            .applyTo(listOf(stroke))
            .single()
        val rotated = NoteSelectionTransform.rotateAroundCenter(90f)
            .applyTo(listOf(stroke))
            .single()

        assertEquals(expected, moved.points.map { it.pressure })
        assertEquals(expected, scaled.points.map { it.pressure })
        assertEquals(expected, rotated.points.map { it.pressure })
    }

    @Test
    fun `proportional scale uses the selection centre and scales style`() {
        val stroke = annotation(
            points = listOf(point(0.20f, 0.20f), point(0.60f, 0.60f)),
            text = "content",
            width = 0.004f,
            fontSize = 0.02f,
            layerId = "layer-a",
            opacity = 0.65f,
            penStyle = PenStyle.BRUSH,
        )

        val scaled = NoteSelectionTransform.scaleAroundCenter(2f)
            .applyTo(listOf(stroke))
            .single()

        assertEquals(0f, scaled.points[0].x, 0.00001f)
        assertEquals(0f, scaled.points[0].y, 0.00001f)
        assertEquals(0.80f, scaled.points[1].x, 0.00001f)
        assertEquals(0.80f, scaled.points[1].y, 0.00001f)
        assertEquals(0.008f, scaled.width, 0.000001f)
        assertEquals(0.04f, scaled.fontSize, 0.000001f)
        assertEquals(AnnotationType.PEN, scaled.type)
        assertEquals("layer-a", scaled.layerId)
        assertEquals(stroke.color, scaled.color)
        assertEquals(0.65f, scaled.opacity, 0.00001f)
        assertEquals(PenStyle.BRUSH, scaled.penStyle)
        assertEquals("content", scaled.text)
    }

    @Test
    fun `rotation around centre uses physical A4 aspect ratio`() {
        val line = annotation(
            type = AnnotationType.LINE,
            points = listOf(point(0.40f, 0.50f), point(0.60f, 0.50f)),
        )

        val rotated = NoteSelectionTransform.rotateAroundCenter(90f)
            .applyTo(listOf(line))
            .single()

        assertEquals(0.50f, rotated.points[0].x, 0.0001f)
        assertEquals(0.50f, rotated.points[1].x, 0.0001f)
        assertEquals(0.50f, rotated.points[0].y + 0.1f * NOTE_SELECTION_PAGE_ASPECT, 0.0001f)
        assertEquals(0.50f, rotated.points[1].y - 0.1f * NOTE_SELECTION_PAGE_ASPECT, 0.0001f)
        // A square on paper is not square in normalized coordinates.
        assertTrue(rotated.points[0].y > 0.40f)
    }

    @Test
    fun `text stamp and signature receive the requested text rotation`() {
        listOf(AnnotationType.TEXT, AnnotationType.STAMP, AnnotationType.SIGNATURE).forEach {
            type ->
            val item = annotation(
                type = type,
                points = listOf(point(0.40f, 0.40f)),
                text = "샘플",
                stamp = "확인",
                fontSize = 0.02f,
                textRotation = 45,
            )

            val rotated = NoteSelectionTransform.rotateAroundCenter(90f)
                .applyTo(listOf(item))
                .single()

            assertEquals("$type should rotate", 135, rotated.textRotation)
        }
    }

    @Test
    fun `negative text rotation normalizes into zero to 359`() {
        val text = annotation(
            type = AnnotationType.TEXT,
            points = listOf(point(0.40f, 0.40f)),
            text = "text",
            textRotation = 45,
        )

        val rotated = NoteSelectionTransform.rotateAroundCenter(-90f)
            .applyTo(listOf(text))
            .single()

        assertEquals(315, rotated.textRotation)
    }

    @Test
    fun `rotation and scale keep every point inside the page`() {
        val stroke = annotation(
            points = listOf(point(0.38f, 0.38f), point(0.62f, 0.62f)),
        )
        val transform = NoteSelectionTransform(
            translateX = 0.20f,
            translateY = -0.20f,
            scale = 6f,
            rotationDegrees = 90f,
        )

        val transformed = transform.applyTo(listOf(stroke)).single()

        assertEquals(2, transformed.points.size)
        assertTrue(transformed.points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun `bounds include text and stamp boxes`() {
        val text = annotation(
            type = AnnotationType.TEXT,
            points = listOf(point(0.20f, 0.30f)),
            text = "AB",
            fontSize = 0.04f,
        )
        val stamp = annotation(
            type = AnnotationType.STAMP,
            points = listOf(point(0.50f, 0.50f)),
            stamp = "확인",
            fontSize = 0.04f,
        )

        val textBounds = noteSelectionBounds(listOf(text))
        assertNotNull(textBounds)
        assertEquals(0.20f, textBounds!!.left, 0.00001f)
        assertEquals(0.30f, textBounds.top, 0.00001f)
        assertEquals(0.20f + 2 * 0.04f * 0.56f, textBounds.right, 0.00001f)
        assertEquals(0.30f + 0.04f * 1.3f, textBounds.bottom, 0.00001f)

        val stampBounds = noteSelectionBounds(listOf(stamp))
        assertNotNull(stampBounds)
        assertEquals(0.50f, stampBounds!!.centerX, 0.00001f)
        assertEquals(0.50f, stampBounds.centerY, 0.00001f)
        assertTrue(stampBounds.right > 0.50f && stampBounds.left < 0.50f)
    }

    @Test
    fun `empty selection has no bounds and transforms nothing`() {
        assertNull(noteSelectionBounds(emptyList()))
        assertTrue(
            NoteSelectionTransform.translate(0.1f, 0.1f)
                .applyTo(emptyList())
                .isEmpty(),
        )
        assertTrue(
            NoteSelectionTransform.scaleAroundCenter(2f)
                .applyTo(emptyList())
                .isEmpty(),
        )
        assertTrue(
            NoteSelectionTransform.rotateAroundCenter(90f)
                .applyTo(emptyList())
                .isEmpty(),
        )
    }

    private fun annotation(
        type: AnnotationType = AnnotationType.PEN,
        points: List<NormalizedPoint>,
        text: String = "",
        stamp: String = "",
        width: Float = 0.004f,
        fontSize: Float = 0.02f,
        textRotation: Int = 0,
        layerId: String? = "layer-a",
        opacity: Float = 1f,
        penStyle: PenStyle = PenStyle.BALLPOINT,
    ) = Annotation(
        id = "annotation",
        type = type,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = width,
        points = points,
        text = text,
        fontSize = fontSize,
        textRotation = textRotation,
        stamp = stamp,
        opacity = opacity,
        penStyle = penStyle,
        layerId = layerId,
        createdAt = 1L,
        durationMs = 2L,
    )

    private fun point(x: Float, y: Float) = NormalizedPoint(x, y)
}
