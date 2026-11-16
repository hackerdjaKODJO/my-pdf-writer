package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class NoteInkTest {

    // ---------------------------------------------------------------- stabilize

    @Test
    fun `stabilize preserves endpoints count and distinct samples`() {
        val points = listOf(
            point(0.10f, 0.50f),
            point(0.15f, 0.53f),
            point(0.20f, 0.47f),
            point(0.25f, 0.52f),
            point(0.30f, 0.50f),
        )

        val stabilized = NoteInk.stabilize(points, 0.25f)

        assertEquals(points.size, stabilized.size)
        assertEquals(points.first(), stabilized.first())
        assertEquals(points.last(), stabilized.last())
        assertEquals(points.toSet().size, stabilized.toSet().size)
        assertNotEquals(points, stabilized)
    }

    @Test
    fun `stabilize with zero strength keeps the stroke untouched`() {
        val points = listOf(
            point(0.1f, 0.5f),
            point(0.2f, 0.7f),
            point(0.3f, 0.4f),
        )

        assertEquals(points, NoteInk.stabilize(points, 0f))
        assertEquals(points, stabilizeStroke(points, -1f))
    }

    @Test
    fun `stabilize gently reduces alternating jitter`() {
        val points = (0..10).map { index ->
            val x = 0.1f + index * 0.05f
            val jitter = if (index % 2 == 0) -0.01f else 0.01f
            point(x, 0.5f + jitter)
        }

        val stabilized = NoteInk.stabilize(points, 0.25f)
        val before = points.drop(1).dropLast(1).maxOf { abs(it.y - 0.5f) }
        val after = stabilized.drop(1).dropLast(1).maxOf { abs(it.y - 0.5f) }

        assertTrue(after < before)
        assertTrue(after > 0f)
    }

    @Test
    fun `stabilize interpolates pressure and keeps endpoints exact`() {
        val points = listOf(
            NormalizedPoint(0.10f, 0.50f, 0.25f),
            NormalizedPoint(0.15f, 0.53f, 0.40f),
            NormalizedPoint(0.20f, 0.47f, 0.55f),
            NormalizedPoint(0.25f, 0.52f, 0.70f),
            NormalizedPoint(0.30f, 0.50f, 0.85f),
        )

        val stabilized = NoteInk.stabilize(points, 0.25f)

        assertEquals(points.size, stabilized.size)
        assertEquals(points.first().pressure, stabilized.first().pressure, 0f)
        assertEquals(points.last().pressure, stabilized.last().pressure, 0f)
        stabilized.forEach { point ->
            assertTrue(point.pressure in 0f..1f)
            assertTrue(point.pressure < 1f)
        }
        // Interior pressures are blended, not reset to the neutral value.
        assertNotEquals(1f, stabilized[2].pressure)
    }

    @Test
    fun `stabilize keeps a constant stylus pressure constant`() {
        val points = listOf(
            NormalizedPoint(0.10f, 0.50f, 0.6f),
            NormalizedPoint(0.20f, 0.60f, 0.6f),
            NormalizedPoint(0.30f, 0.50f, 0.6f),
        )

        val stabilized = NoteInk.stabilize(points, 1f)

        stabilized.forEach { assertEquals(0.6f, it.pressure, 1e-6f) }
    }

    // ---------------------------------------------------------- shape recognition

    @Test
    fun `recognizes a rough line`() {
        val points = (0..24).map { index ->
            val t = index / 24f
            point(
                x = 0.15f + 0.70f * t,
                y = 0.42f + 0.003f * sin(t * PI.toFloat() * 4f),
            )
        }

        val shape = NoteInk.recognizeShape(points)

        assertEquals(AnnotationType.LINE, shape?.type)
        assertEquals(2, shape?.points?.size)
        assertEquals(0.15f, shape!!.points.first().x, 0.02f)
        assertEquals(0.85f, shape.points.last().x, 0.02f)
        assertTrue(shape.confidence > 0.5f)
    }

    @Test
    fun `recognizes a rough rectangle`() {
        val shape = NoteInk.recognizeShape(rectanglePoints())

        assertEquals(AnnotationType.RECT, shape?.type)
        assertEquals(2, shape?.points?.size)
        assertEquals(0.20f, shape!!.points[0].x, 0.01f)
        assertEquals(0.25f, shape.points[0].y, 0.01f)
        assertEquals(0.70f, shape.points[1].x, 0.01f)
        assertEquals(0.55f, shape.points[1].y, 0.01f)
    }

    @Test
    fun `recognizes a rough ellipse`() {
        val shape = NoteInk.recognizeShape(ellipsePoints())

        assertEquals(AnnotationType.ELLIPSE, shape?.type)
        assertEquals(2, shape?.points?.size)
        assertEquals(0.25f, shape!!.points[0].x, 0.01f)
        assertEquals(0.30f, shape.points[0].y, 0.01f)
        assertEquals(0.75f, shape.points[1].x, 0.01f)
        assertEquals(0.70f, shape.points[1].y, 0.01f)
        assertTrue(shape.confidence > 0.6f)
    }

    @Test
    fun `rejects a squiggle`() {
        val points = (0..80).map { index ->
            val t = index / 80f
            point(
                x = 0.20f + 0.60f * t,
                y = 0.50f + 0.08f * sin(t * PI.toFloat() * 6f),
            )
        }

        assertNull(NoteInk.recognizeShape(points))
    }

    @Test
    fun `rejects strokes with too few samples`() {
        val points = listOf(
            point(0.2f, 0.2f),
            point(0.5f, 0.2f),
            point(0.8f, 0.2f),
            point(0.8f, 0.8f),
        )

        assertNull(NoteInk.recognizeShape(points))
    }

    // ------------------------------------------------------------- partial erase

    @Test
    fun `partial erase splits a pen stroke and preserves metadata`() {
        val original = annotation(
            id = "pen-original",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
            color = 0xFF123456.toInt(),
            width = 0.007f,
            opacity = 0.42f,
            penStyle = PenStyle.FOUNTAIN,
            layerId = "layer-a",
            createdAt = 1234L,
            durationMs = 5678L,
        )

        val result = NoteInk.eraseAnnotations(
            annotations = listOf(original),
            trail = listOf(point(0.50f, 0.50f)),
            radius = 0.05f,
            targetLayerId = "layer-a",
            defaultLayerId = "layer-a",
            partial = true,
        )

        assertEquals(0, result.removed)
        assertEquals(1, result.split)
        assertEquals(2, result.segments)
        assertEquals(2, result.annotations.size)
        assertTrue(result.annotations.none { it.id == original.id })
        assertEquals(2, result.annotations.map { it.id }.toSet().size)
        assertTrue(result.annotations[0].points.last().x < 0.50f)
        assertTrue(result.annotations[1].points.first().x > 0.50f)

        result.annotations.forEach { segment ->
            assertEquals(AnnotationType.PEN, segment.type)
            assertEquals(original.color, segment.color)
            assertEquals(original.width, segment.width, 0f)
            assertEquals(original.opacity, segment.opacity, 0f)
            assertEquals(original.penStyle, segment.penStyle)
            assertEquals(original.layerId, segment.layerId)
            assertEquals(original.createdAt, segment.createdAt)
            assertEquals(original.durationMs, segment.durationMs)
        }
    }

    @Test
    fun `partial erase preserves interpolated pressure on surviving runs`() {
        val points = (0..10).map { index ->
            NormalizedPoint(0.10f + index * 0.08f, 0.50f, 0.4f)
        }
        val original = annotation(
            id = "pressure-original",
            type = AnnotationType.PEN,
            points = points,
            penStyle = PenStyle.BRUSH,
        )

        val result = NoteInk.erasePartial(
            annotations = listOf(original),
            trail = listOf(point(0.50f, 0.50f)),
            radius = 0.05f,
        )

        assertEquals(2, result.segments)
        result.annotations.forEach { segment ->
            segment.points.forEach { assertEquals(0.4f, it.pressure, 1e-6f) }
        }
    }

    @Test
    fun `partial erase splits every supported polyline type`() {
        val types = listOf(
            AnnotationType.PEN,
            AnnotationType.HIGHLIGHT,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.LINE,
            AnnotationType.ARROW,
            AnnotationType.SIGNATURE,
        )

        types.forEach { type ->
            val original = annotation(
                id = "original-$type",
                type = type,
                points = horizontalStroke(),
            )
            val result = NoteInk.erasePartial(
                annotations = listOf(original),
                trail = listOf(point(0.50f, 0.50f)),
                radius = 0.05f,
            )

            assertEquals("$type should split", 1, result.split)
            assertEquals("$type should survive in two runs", 2, result.segments)
            assertEquals(2, result.annotations.size)
            assertTrue(result.annotations.all { it.type == type })
            assertEquals(2, result.annotations.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `partial erase leaves a missed annotation untouched`() {
        val original = annotation(
            id = "untouched",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
        )

        val result = NoteInk.eraseAnnotations(
            annotations = listOf(original),
            trail = listOf(point(0.50f, 0.90f)),
            radius = 0.03f,
            targetLayerId = "layer-a",
            defaultLayerId = "layer-a",
            partial = true,
        )

        assertFalse(result.changed)
        assertEquals(0, result.removed)
        assertEquals(0, result.split)
        assertEquals(listOf(original), result.annotations)
    }

    @Test
    fun `partial erase removes a fully covered stroke`() {
        val original = annotation(
            id = "covered",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
        )

        val result = NoteInk.erasePartial(
            annotations = listOf(original),
            trail = listOf(point(0.05f, 0.50f), point(0.95f, 0.50f)),
            radius = 0.04f,
        )

        assertEquals(1, result.removed)
        assertEquals(0, result.split)
        assertTrue(result.annotations.isEmpty())
    }

    @Test
    fun `whole eraser removes a hit stroke without splitting`() {
        val original = annotation(
            id = "whole",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
        )

        val result = NoteInk.eraseAnnotations(
            annotations = listOf(original),
            trail = listOf(point(0.50f, 0.50f)),
            radius = 0.05f,
            targetLayerId = "layer-a",
            defaultLayerId = "layer-a",
            partial = false,
        )

        assertEquals(1, result.removed)
        assertEquals(0, result.split)
        assertTrue(result.annotations.isEmpty())
    }

    @Test
    fun `atomic text stamp signature shape and redact annotations are removed whole`() {
        val annotations = listOf(
            annotation("text", AnnotationType.TEXT, listOf(point(0.50f, 0.50f)), text = "메모"),
            annotation("stamp", AnnotationType.STAMP, listOf(point(0.50f, 0.50f)), stamp = "확인"),
            annotation(
                "signature",
                AnnotationType.SIGNATURE,
                listOf(point(0.50f, 0.50f)),
                text = "서명",
            ),
            annotation(
                "rect",
                AnnotationType.RECT,
                listOf(point(0.30f, 0.40f), point(0.70f, 0.60f)),
            ),
            annotation(
                "ellipse",
                AnnotationType.ELLIPSE,
                listOf(point(0.30f, 0.40f), point(0.70f, 0.60f)),
            ),
            annotation(
                "redact",
                AnnotationType.REDACT,
                listOf(point(0.30f, 0.40f), point(0.70f, 0.60f)),
            ),
        )

        val result = NoteInk.erasePartial(
            annotations = annotations,
            trail = listOf(
                point(0.50f, 0.50f),
                point(0.30f, 0.50f),
                point(0.50f, 0.50f),
            ),
            radius = 0.03f,
        )

        assertEquals(annotations.size, result.removed)
        assertEquals(0, result.split)
        assertTrue(result.annotations.isEmpty())
    }

    @Test
    fun `multiple cuts create unique ids and preserve drawing order`() {
        val original = annotation(
            id = "source",
            type = AnnotationType.PEN,
            points = horizontalStroke(samples = 21),
        )

        val result = NoteInk.erasePartial(
            annotations = listOf(original),
            trail = listOf(
                point(0.30f, 0.50f),
                point(0.30f, 0.70f),
                point(0.50f, 0.70f),
                point(0.50f, 0.50f),
                point(0.50f, 0.70f),
                point(0.70f, 0.70f),
                point(0.70f, 0.50f),
            ),
            radius = 0.035f,
        )

        assertEquals(1, result.split)
        assertEquals(4, result.segments)
        assertEquals(4, result.annotations.size)
        assertEquals(4, result.annotations.map { it.id }.toSet().size)
        assertTrue(result.annotations.none { it.id == original.id })
        assertTrue(
            result.annotations.zipWithNext().all { (first, second) ->
                first.points.last().x < second.points.first().x
            },
        )
    }

    @Test
    fun `erase respects the active layer and the default layer fallback`() {
        val active = annotation(
            id = "active",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
            layerId = "layer-a",
        )
        val other = annotation(
            id = "other",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
            layerId = "layer-b",
        )
        val legacy = annotation(
            id = "legacy",
            type = AnnotationType.PEN,
            points = horizontalStroke(),
            layerId = null,
        )

        val result = NoteInk.erasePartial(
            annotations = listOf(active, other, legacy),
            trail = listOf(point(0.50f, 0.50f)),
            radius = 0.05f,
            targetLayerId = "layer-a",
            defaultLayerId = "layer-a",
        )

        assertEquals(2, result.split)
        assertEquals(4, result.segments)
        assertTrue(result.annotations.any { it.id == "other" })
        assertTrue(result.annotations.none { it.id == "legacy" })
        assertTrue(result.annotations.none { it.id == "active" })
        assertEquals(
            "other",
            result.annotations.first { it.layerId == "layer-b" }.id,
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun annotation(
        id: String,
        type: AnnotationType,
        points: List<NormalizedPoint>,
        color: Int = 0xFF1B1C19.toInt(),
        width: Float = 0.004f,
        opacity: Float = 1f,
        penStyle: PenStyle = PenStyle.BALLPOINT,
        layerId: String? = "layer-a",
        createdAt: Long = 100L,
        durationMs: Long = 200L,
        text: String = "",
        stamp: String = "",
    ) = Annotation(
        id = id,
        type = type,
        page = 0,
        color = color,
        width = width,
        points = points,
        text = text,
        stamp = stamp,
        opacity = opacity,
        penStyle = penStyle,
        layerId = layerId,
        createdAt = createdAt,
        durationMs = durationMs,
    )

    private fun point(x: Float, y: Float): NormalizedPoint = NormalizedPoint(x, y)

    private fun horizontalStroke(samples: Int = 11): List<NormalizedPoint> =
        (0 until samples).map { index ->
            val t = index / (samples - 1).toFloat()
            point(0.10f + 0.80f * t, 0.50f)
        }

    private fun rectanglePoints(
        left: Float = 0.20f,
        top: Float = 0.25f,
        right: Float = 0.70f,
        bottom: Float = 0.55f,
        perSide: Int = 9,
    ): List<NormalizedPoint> {
        val points = ArrayList<NormalizedPoint>()
        for (index in 0 until perSide) {
            val t = index / perSide.toFloat()
            points.add(point(left + (right - left) * t, top))
        }
        for (index in 0 until perSide) {
            val t = index / perSide.toFloat()
            points.add(point(right, top + (bottom - top) * t))
        }
        for (index in 0 until perSide) {
            val t = index / perSide.toFloat()
            points.add(point(right - (right - left) * t, bottom))
        }
        for (index in 0 until perSide) {
            val t = index / perSide.toFloat()
            points.add(point(left, bottom - (bottom - top) * t))
        }
        points.add(points.first())
        return points
    }

    private fun ellipsePoints(
        centerX: Float = 0.50f,
        centerY: Float = 0.50f,
        radiusX: Float = 0.25f,
        radiusY: Float = 0.20f,
        samples: Int = 72,
    ): List<NormalizedPoint> = (0 until samples).map { index ->
        val angle = 2f * PI.toFloat() * index / samples
        val wobble = 1f + 0.03f * sin(angle * 3f)
        point(
            centerX + radiusX * wobble * cos(angle),
            centerY + radiusY * (2f - wobble) * sin(angle),
        )
    }
}
