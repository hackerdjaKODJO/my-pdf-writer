package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Highlighter width is stored separately so it cannot corrupt the pen width. */
class HighlightInkTest {

    @Test
    fun widthIsClampedToTheHighlighterRange() {
        assertEquals(HighlightInk.MIN_WIDTH, HighlightInk.clamp(0f), 0f)
        assertEquals(HighlightInk.MIN_WIDTH, HighlightInk.clamp(0.002f), 0f)
        assertEquals(HighlightInk.DEFAULT_WIDTH, HighlightInk.clamp(Float.NaN), 0f)
        assertEquals(HighlightInk.MAX_WIDTH, HighlightInk.clamp(0.05f), 0f)
        assertEquals(0.02f, HighlightInk.clamp(0.02f), 0f)
        assertEquals(0.025f, HighlightInk.clamp(0.025f), 0f)
    }

    @Test
    fun sharedSettingsRoundTripKeepsBothWidths() {
        val settings = InkSettings(
            penStyle = PenStyle.BALLPOINT,
            color = 0xFF1B1C19.toInt(),
            width = 0.004f,
            opacity = 1f,
            pressureCurve = PressureCurve.LINEAR,
            highlightWidth = 0.025f,
        )
        val restored = InkSettings.fromRaw(settings.toJson().toString())
        assertEquals(0.004f, restored.width, 0f)
        assertEquals(0.025f, restored.highlightWidth, 0f)
        assertEquals(HighlightInk.DEFAULT_WIDTH,
            InkSettings.fromRaw("""{"width":0.004}""").highlightWidth, 0f)
    }

    @Test
    fun highlightPresetNeverOverwritesThePenWidth() {
        val current = InkSettings(highlightWidth = 0.023f)
        val preset = defaultPenPresets().first { it.tool == Tool.HIGHLIGHT }
        val applied = InkPolicy.fromPreset(preset, current)
        assertEquals(0.023f, applied.width, 0f)
        assertEquals(0.02f, applied.highlightWidth, 0f)
        val currentPen = InkPolicy.currentPen(applied, Tool.HIGHLIGHT)
        assertEquals(0.02f, currentPen.width, 0f)
        assertEquals(PenStyle.BALLPOINT, currentPen.penStyle)
        assertEquals(1f, currentPen.opacity, 0f)
    }
}
