package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteClipboardTest {

    @Test
    fun `paste creates fresh ids remaps page and layer and resets replay timing`() {
        val source = annotation(
            id = "source",
            type = AnnotationType.TEXT,
            page = 4,
            points = listOf(point(0.20f, 0.30f)),
            text = "복사할 내용",
            fontSize = 0.03f,
            textRotation = 15,
            layerId = "source-layer",
            createdAt = 10L,
            durationMs = 900L,
        )
        val clipboard = NoteClipboard.fromSelection(listOf(source), sourcePageIndex = 4)
        var nextId = 0

        val pasted = clipboard.paste(
            targetLayerId = "target-layer",
            offsetX = 0.01f,
            offsetY = 0.02f,
            now = 777L,
            idFactory = { "fresh-${nextId++}" },
        ).single()

        assertEquals("fresh-0", pasted.id)
        assertNotEquals(source.id, pasted.id)
        assertEquals(0, pasted.page)
        assertEquals("target-layer", pasted.layerId)
        assertEquals(0.21f, pasted.points[0].x, 0.00001f)
        assertEquals(0.32f, pasted.points[0].y, 0.00001f)
        assertEquals(777L, pasted.createdAt)
        assertEquals(0L, pasted.durationMs)
        assertEquals("복사할 내용", pasted.text)
        assertEquals(0.03f, pasted.fontSize, 0.00001f)
        assertEquals(15, pasted.textRotation)
        assertEquals(source.color, pasted.color)
        assertEquals(source.width, pasted.width, 0.00001f)
        assertEquals(source.opacity, pasted.opacity, 0.00001f)
        assertEquals(PenStyle.BRUSH, pasted.penStyle)

        // The source page data is untouched.
        assertEquals(4, source.page)
        assertEquals("source-layer", source.layerId)
        assertEquals(10L, source.createdAt)
        assertEquals(900L, source.durationMs)
    }

    @Test
    fun `pasting twice produces distinct ids with the same content`() {
        val clipboard = NoteClipboard.fromSelection(
            listOf(
                annotation(
                    id = "source",
                    points = listOf(point(0.20f, 0.20f), point(0.30f, 0.30f)),
                ),
            ),
            sourcePageIndex = 0,
        )
        var nextId = 0

        val first = clipboard.paste(targetLayerId = "layer") { "id-${nextId++}" }
        val second = clipboard.paste(targetLayerId = "layer") { "id-${nextId++}" }

        assertEquals(listOf("id-0"), first.map { it.id })
        assertEquals(listOf("id-1"), second.map { it.id })
        assertNotEquals(first.single().id, second.single().id)
        assertEquals(first.single().points, second.single().points)
    }

    @Test
    fun `paste preserves per-point pressure`() {
        val clipboard = NoteClipboard.fromSelection(
            listOf(
                annotation(
                    id = "pressure-source",
                    points = listOf(
                        NormalizedPoint(0.20f, 0.20f, 0.25f),
                        NormalizedPoint(0.30f, 0.30f, 0.80f),
                    ),
                ),
            ),
            sourcePageIndex = 0,
        )

        val pasted = clipboard.paste(targetLayerId = "layer").single()

        assertEquals(listOf(0.25f, 0.80f), pasted.points.map { it.pressure })
        assertEquals(PenStyle.BRUSH, pasted.penStyle)
    }

    @Test
    fun `paste clamps near the page edge`() {
        val clipboard = NoteClipboard.fromSelection(
            listOf(
                annotation(
                    points = listOf(point(0.98f, 0.99f), point(0.995f, 0.999f)),
                ),
            ),
            sourcePageIndex = 1,
        )

        val pasted = clipboard.paste(
            targetLayerId = "layer",
            offsetX = 0.10f,
            offsetY = 0.10f,
        ).single()

        assertTrue(pasted.points.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(1f, pasted.points[0].x, 0.00001f)
        assertEquals(1f, pasted.points[0].y, 0.00001f)
    }

    @Test
    fun `empty clipboard pastes nothing`() {
        val clipboard = NoteClipboard()

        assertTrue(clipboard.isEmpty)
        assertEquals(0, clipboard.size)
        assertTrue(clipboard.paste(targetLayerId = "layer").isEmpty())
        assertTrue(NoteClipboard.fromSelection(emptyList(), 2).paste("layer").isEmpty())
    }

    private fun annotation(
        id: String = "annotation",
        type: AnnotationType = AnnotationType.PEN,
        page: Int = 0,
        points: List<NormalizedPoint>,
        text: String = "",
        fontSize: Float = 0.02f,
        textRotation: Int = 0,
        layerId: String? = "layer-a",
        createdAt: Long = 1L,
        durationMs: Long = 2L,
    ) = Annotation(
        id = id,
        type = type,
        page = page,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = points,
        text = text,
        fontSize = fontSize,
        textRotation = textRotation,
        opacity = 0.8f,
        penStyle = PenStyle.BRUSH,
        layerId = layerId,
        createdAt = createdAt,
        durationMs = durationMs,
    )

    private fun point(x: Float, y: Float) = NormalizedPoint(x, y)
}
