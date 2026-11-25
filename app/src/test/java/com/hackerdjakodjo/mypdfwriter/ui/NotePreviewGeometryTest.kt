package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** WYSIWYG note ink: final preview and commit use one pure preparation pipeline. */
class NotePreviewGeometryTest {

    private fun stroke(count: Int): List<NormalizedPoint> =
        (0 until count).map { i ->
            val t = i / (count - 1).toFloat()
            NormalizedPoint(
                (0.1f + 0.7f * t + 0.01f * kotlin.math.sin(i * 1.3f)).coerceIn(0f, 1f),
                (0.2f + 0.5f * t + 0.01f * kotlin.math.cos(i * 0.7f)).coerceIn(0f, 1f),
                0.45f + 0.4f * kotlin.math.abs(kotlin.math.sin(i * 0.21f)),
                timeMs = 100L + i * 17L,
            )
        }

    @Test
    fun `preview equals commit preparation for every short and long count`() {
        val counts = listOf(3, 4, 6, 7, 8, 40)
        for (count in counts) {
            val raw = stroke(count)
            for (stabilize in listOf(0f, NOTE_STABILIZE_STRENGTH)) {
                for (smooth in listOf(false, true)) {
                    for (simplify in listOf(false, true)) {
                        val policy = NoteStrokePreparation(
                            stabilizeStrength = stabilize,
                            smoothHandwriting = smooth,
                            simplifyPoints = simplify,
                        )
                        val preview = notePreviewPoints(
                            raw,
                            stabilizeStrength = stabilize,
                            smoothHandwriting = smooth,
                            simplifyPoints = simplify,
                        )
                        assertEquals(
                            "count=$count stabilize=$stabilize smooth=$smooth simplify=$simplify",
                            prepareNoteStroke(raw, policy),
                            preview,
                        )
                        if (stabilize == 0f) {
                            assertEquals(
                                noteRefineForCommit(raw, smooth, simplify),
                                preview,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `three to seven point strokes no longer bypass preparation`() {
        for (count in 3..7) {
            val raw = stroke(count)
            assertNotEquals(
                "stabilization must run for $count points",
                raw,
                prepareNoteStroke(raw, NoteStrokePreparation(stabilizeStrength = 0.25f)),
            )
            assertNotEquals(
                "refinement must run for $count points",
                raw,
                prepareNoteStroke(
                    raw,
                    NoteStrokePreparation(stabilizeStrength = 0f, smoothHandwriting = true),
                ),
            )
        }
    }

    @Test
    fun `prepared preview keeps exact endpoints and finite normalized geometry`() {
        val raw = stroke(40)
        val preview = notePreviewPoints(raw)
        assertEquals(raw.first(), preview.first())
        assertEquals(raw.last(), preview.last())
        assertTrue(preview.isNotEmpty())
        preview.forEach { point ->
            assertTrue(point.x in 0f..1f)
            assertTrue(point.y in 0f..1f)
            assertTrue(point.pressure in 0f..1f)
        }
    }

    @Test
    fun `disabled preparation keeps raw points`() {
        val raw = stroke(30)
        assertSame(
            raw,
            notePreviewPoints(
                raw,
                stabilizeStrength = 0f,
                smoothHandwriting = false,
                simplifyPoints = false,
            ),
        )
    }

    @Test
    fun `shape width floor is identical for preview and commit`() {
        val shapeTypes = listOf(
            AnnotationType.RECT,
            AnnotationType.ARROW,
            AnnotationType.LINE,
            AnnotationType.ELLIPSE,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.REDACT,
        )
        assertEquals(0.0025f, NOTE_SHAPE_MIN_WIDTH, 0f)
        shapeTypes.forEach { type ->
            assertEquals(
                "$type preview floor",
                maxOf(0.001f, NOTE_SHAPE_MIN_WIDTH),
                noteCommittedStrokeWidth(type, 0.001f),
                0f,
            )
            assertEquals(
                "$type must keep a wider chosen stroke",
                0.006f,
                noteCommittedStrokeWidth(type, 0.006f),
                0f,
            )
        }
        assertEquals(
            maxOf(0.001f, 0.018f),
            noteCommittedStrokeWidth(AnnotationType.HIGHLIGHT, 0.001f),
            0f,
        )
    }

    @Test
    fun noteStrokeCommitPropagatesCompleteTimestampsAndReplayTimes() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.2f, timeMs = 100L),
            NormalizedPoint(0.3f, 0.4f, timeMs = 200L),
            NormalizedPoint(0.5f, 0.6f, timeMs = 300L),
        )
        val stroke = noteStrokeAnnotation(
            type = AnnotationType.PEN,
            color = 0xFF123456.toInt(),
            strokeWidth = 0.004f,
            points = points,
            opacity = 0.8f,
            penStyle = PenStyle.FOUNTAIN,
            layerId = "layer-a",
            durationMs = 500L,
            nowMs = 2000L,
        )
        val partial = noteStrokeAnnotation(
            type = AnnotationType.PEN,
            color = 0,
            strokeWidth = 0.004f,
            points = listOf(points.first(), points.last().copy(timeMs = null)),
            opacity = 1f,
            penStyle = PenStyle.BALLPOINT,
            layerId = null,
            durationMs = 0L,
            nowMs = 2000L,
        )

        assertEquals(listOf(100L, 200L, 300L), stroke.timesMs)
        assertEquals(1500L, stroke.createdAt)
        assertEquals(500L, stroke.durationMs)
        assertEquals("layer-a", stroke.layerId)
        assertEquals(PenStyle.FOUNTAIN, stroke.penStyle)
        assertNull(partial.timesMs)
    }

    @Test
    fun `recognized shapes are the documented commit only exception`() {
        val raw = listOf(
            NormalizedPoint(0.2f, 0.2f),
            NormalizedPoint(0.4f, 0.2f),
            NormalizedPoint(0.6f, 0.2f),
            NormalizedPoint(0.8f, 0.2f),
            NormalizedPoint(0.8f, 0.4f),
            NormalizedPoint(0.8f, 0.6f),
            NormalizedPoint(0.8f, 0.8f),
            NormalizedPoint(0.6f, 0.8f),
            NormalizedPoint(0.4f, 0.8f),
            NormalizedPoint(0.2f, 0.8f),
            NormalizedPoint(0.2f, 0.6f),
            NormalizedPoint(0.2f, 0.4f),
            NormalizedPoint(0.2f, 0.2f),
        )
        val shape = NoteInk.recognizeShape(raw)!!
        assertSame(shape.points, noteRefineForCommit(shape.points, true, true, skipRefinement = true))
        assertNotEquals(raw, notePreviewPoints(raw))
    }
}
