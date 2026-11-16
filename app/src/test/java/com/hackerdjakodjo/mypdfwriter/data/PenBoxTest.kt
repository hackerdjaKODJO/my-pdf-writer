package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PenBoxTest {

    @Test
    fun defaultsCoverTheFourPenStylesAndHighlighter() {
        val presets = defaultPenPresets()

        assertEquals(listOf("볼펜", "만년필", "붓", "연필", "형광펜"), presets.map { it.name })
        assertEquals(
            listOf(PenStyle.BALLPOINT, PenStyle.FOUNTAIN, PenStyle.BRUSH, PenStyle.PENCIL),
            presets.filter { it.tool == Tool.PEN }.map { it.penStyle },
        )
        assertEquals(Tool.HIGHLIGHT, presets.last().tool)
        assertTrue(presets.all { it.width in 0.001f..0.02f })
        assertTrue(presets.all { it.opacity in 0.1f..1f })
    }

    @Test
    fun penPresetJsonRoundTripKeepsStyleAndTool() {
        val preset = PenPreset(
            id = "brush-test",
            name = "굵은 붓",
            color = 0xFF3E2B23.toInt(),
            width = 0.014f,
            opacity = 0.8f,
            penStyle = PenStyle.BRUSH,
            tool = Tool.PEN,
        )

        val restored = PenPreset.fromJson(JSONObject(preset.toJson().toString()))

        assertEquals(preset.id, restored.id)
        assertEquals("굵은 붓", restored.name)
        assertEquals(preset.color, restored.color)
        assertEquals(0.014f, restored.width, 0.00001f)
        assertEquals(0.8f, restored.opacity, 0.00001f)
        assertEquals(PenStyle.BRUSH, restored.penStyle)
        assertEquals(Tool.PEN, restored.tool)
    }

    @Test
    fun legacyPresetWithoutToolDefaultsToPen() {
        val restored = PenPreset.fromJson(
            JSONObject("""{"id":"legacy","name":"볼펜","color":1,"width":0.004,"opacity":1,"style":"BALLPOINT"}"""),
        )
        assertEquals(Tool.PEN, restored.tool)
        assertEquals(PenStyle.BALLPOINT, restored.penStyle)
    }
}
