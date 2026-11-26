package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for the renderer-collapse bug: a pressure-varied stylus stroke
 * used to bypass fountain/brush/pencil rendering and become a generic ribbon.
 */
class PenRendererSelectionTest {

    private val styles = PenStyle.entries

    @Test
    fun `ballpoint always keeps its continuous natural centerline`() {
        assertEquals(
            PenStrokeGeometry.PenRenderer.BALLPOINT,
            PenStrokeGeometry.selectPenRenderer(PenStyle.BALLPOINT, true),
        )
        assertEquals(
            PenStrokeGeometry.PenRenderer.BALLPOINT,
            PenStrokeGeometry.selectPenRenderer(PenStyle.BALLPOINT, false),
        )
    }

    @Test
    fun `stylus strokes keep their own pen style renderer`() {
        // These are the assertions that would have caught the original bug.
        assertEquals(
            PenStrokeGeometry.PenRenderer.FOUNTAIN,
            PenStrokeGeometry.selectPenRenderer(PenStyle.FOUNTAIN, true),
        )
        assertEquals(
            PenStrokeGeometry.PenRenderer.BRUSH,
            PenStrokeGeometry.selectPenRenderer(PenStyle.BRUSH, true),
        )
        assertEquals(
            PenStrokeGeometry.PenRenderer.PENCIL,
            PenStrokeGeometry.selectPenRenderer(PenStyle.PENCIL, true),
        )
    }

    @Test
    fun `neutral strokes keep the legacy per style renderer`() {
        styles.forEach { style ->
            assertEquals(
                "neutral $style",
                when (style) {
                    PenStyle.BALLPOINT -> PenStrokeGeometry.PenRenderer.BALLPOINT
                    PenStyle.FOUNTAIN -> PenStrokeGeometry.PenRenderer.FOUNTAIN
                    PenStyle.BRUSH -> PenStrokeGeometry.PenRenderer.BRUSH
                    PenStyle.PENCIL -> PenStrokeGeometry.PenRenderer.PENCIL
                },
                PenStrokeGeometry.selectPenRenderer(style, false),
            )
        }
    }
}
