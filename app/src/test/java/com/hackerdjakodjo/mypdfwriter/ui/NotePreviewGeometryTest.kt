package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WYSIWYG handwriting: ink already drawn behind the finger must not change
 * shape when a new sample arrives or when the stroke is committed.
 */
class NotePreviewGeometryTest {

    private fun stroke(count: Int): List<NormalizedPoint> =
        (0 until count).map { i ->
            val t = i / (count - 1).toFloat()
            NormalizedPoint(
                (0.1f + 0.7f * t + 0.01f * kotlin.math.sin(i * 1.3f)).coerceIn(0f, 1f),
                (0.2f + 0.5f * t + 0.01f * kotlin.math.cos(i * 0.7f)).coerceIn(0f, 1f),
                0.45f + 0.4f * kotlin.math.abs(kotlin.math.sin(i * 0.21f)),
            )
        }

    @Test
    fun `short strokes are previewed verbatim`() {
        val raw = stroke(6)
        assertEquals(raw, notePreviewPoints(raw))
    }

    @Test
    fun `already drawn ink stays stable when a sample arrives`() {
        val raw = stroke(40)
        var previous: List<NormalizedPoint>? = null
        for (k in 12..36) {
            val current = notePreviewPoints(raw.take(k))
            // Comparison window excludes the guarded tail only.
            val stableCount = k - 2 * NOTE_PREVIEW_STABLE_GUARD - 2
            if (previous != null) {
                for (i in 0 until stableCount) {
                    assertEquals(
                        "sample $i moved when stroke grew from ${k - 1} to $k",
                        previous[i].x,
                        current[i].x,
                        0.0001f,
                    )
                    assertEquals(
                        "sample $i y moved when stroke grew from ${k - 1} to $k",
                        previous[i].y,
                        current[i].y,
                        0.0001f,
                    )
                }
            }
            previous = current
        }
    }

    @Test
    fun `preview geometry is finite and bounded`() {
        val preview = notePreviewPoints(stroke(30))
        assertTrue(preview.isNotEmpty())
        preview.forEach { point ->
            assertTrue(point.x in 0f..1f)
            assertTrue(point.y in 0f..1f)
            assertTrue(point.pressure in 0f..1f)
        }
    }

    @Test
    fun `disabled smoothing keeps raw points`() {
        val raw = stroke(30)
        assertEquals(
            raw,
            notePreviewPoints(
                raw,
                stabilizeStrength = 0f,
                smoothHandwriting = false,
                simplifyPoints = false,
            ),
        )
    }
}
