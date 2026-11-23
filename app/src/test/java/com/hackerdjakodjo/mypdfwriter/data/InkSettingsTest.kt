package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared active-pen settings used by both the PDF viewer and the note editor. */
class InkSettingsTest {

    @Test
    fun defaultsMatchTheFirstBallpointPreset() {
        val first = defaultPenPresets().first()
        assertEquals(PenStyle.BALLPOINT, InkSettings.DEFAULT.penStyle)
        assertEquals(first.color, InkSettings.DEFAULT.color)
        assertEquals(first.width, InkSettings.DEFAULT.width, 0f)
        assertEquals(first.opacity, InkSettings.DEFAULT.opacity, 0f)
        assertEquals(PressureCurve.DEFAULT, InkSettings.DEFAULT.pressureCurve)
    }

    @Test
    fun missingAndMalformedInputUsesDefaults() {
        assertEquals(InkSettings.DEFAULT, InkSettings.fromRaw(null))
        assertEquals(InkSettings.DEFAULT, InkSettings.fromRaw(""))
        assertEquals(InkSettings.DEFAULT, InkSettings.fromRaw("not-json"))
        assertEquals(InkSettings.DEFAULT, InkSettings.fromJson(null))
        assertEquals(InkSettings.DEFAULT, InkSettings.fromJson(org.json.JSONObject()))
    }

    @Test
    fun widthAndOpacityAreAlwaysClamped() {
        assertEquals(InkSettings.MIN_WIDTH, InkSettings.clampWidth(0f), 0f)
        assertEquals(InkSettings.MAX_WIDTH, InkSettings.clampWidth(1f), 0f)
        assertEquals(0.012f, InkSettings.clampWidth(0.012f), 0f)
        assertEquals(InkSettings.DEFAULT_WIDTH, InkSettings.clampWidth(Float.NaN), 0f)
        assertEquals(InkSettings.MIN_OPACITY, InkSettings.clampOpacity(0f), 0f)
        assertEquals(InkSettings.MAX_OPACITY, InkSettings.clampOpacity(2f), 0f)
        assertEquals(0.85f, InkSettings.clampOpacity(0.85f), 0f)
        assertEquals(InkSettings.DEFAULT_OPACITY, InkSettings.clampOpacity(Float.NaN), 0f)
    }

    @Test
    fun jsonRoundTripKeepsAllFiveFields() {
        val settings = InkSettings(
            PenStyle.FOUNTAIN,
            0xFF1F4E79.toInt(),
            0.0055f,
            0.9f,
            PressureCurve.FIRM,
        )
        val json = settings.toJson()
        assertEquals("FOUNTAIN", json.getString("style"))
        assertEquals("FIRM", json.getString("curve"))
        assertEquals(settings, InkSettings.fromRaw(json.toString()))
    }

    @Test
    fun applyingAPresetKeepsTheLivePressureCurve() {
        val preset = defaultPenPresets().first { it.id == "pen-box-pencil" }
        val current = InkSettings(
            PenStyle.BRUSH,
            0xFF3E2B23.toInt(),
            0.012f,
            0.95f,
            PressureCurve.FIRM,
        )
        val applied = InkPolicy.fromPreset(preset, current)
        assertEquals(PenStyle.PENCIL, applied.penStyle)
        assertEquals(0xFF4B4B4B.toInt(), applied.color)
        assertEquals(0.0035f, applied.width, 0f)
        assertEquals(0.85f, applied.opacity, 0f)
        assertEquals(PressureCurve.FIRM, applied.pressureCurve)
    }

    @Test
    fun currentPenIsOneSharedBuilderForBothSurfaces() {
        val settings = InkSettings(PenStyle.BRUSH, 0xFF3E2B23.toInt(), 0.01f, 0.8f)
        val pen = InkPolicy.currentPen(settings, Tool.PEN)
        assertEquals(InkPolicy.CURRENT_PEN_ID, pen.id)
        assertEquals(InkPolicy.CURRENT_PEN_NAME, pen.name)
        assertEquals(PenStyle.BRUSH, pen.penStyle)
        val highlight = InkPolicy.currentPen(settings, Tool.HIGHLIGHT)
        assertEquals(PenStyle.BALLPOINT, highlight.penStyle)
        assertEquals(1f, highlight.opacity, 0f)
        assertTrue(highlight.width >= 0.018f)
    }

    @Test
    fun backupKeyContractIsUnchanged() {
        assertEquals("pen_box", PenBoxStore.PREFS)
        assertEquals("ink_v1", InkSettings.KEY)
        assertTrue(InkSettings.KEY != PenBoxStore.KEY)
        assertTrue(InkSettings.KEY != FavoritePens.KEY)
        assertTrue("pen_box.xml" in BackupArchive.PREFERENCE_FILES)
    }

    @Test
    fun requiredPenStylesAreAlwaysRestored() {
        val custom = PenPreset(id = "custom", name = "내 펜")
        val presets = ensureRequiredPresets(listOf(custom) + defaultPenPresets().drop(1))
        assertEquals("custom", presets.first().id)
        REQUIRED_PRESET_IDS.forEach { id ->
            assertTrue("missing $id", presets.any { it.id == id })
        }
        assertEquals(
            REQUIRED_PRESET_IDS.size,
            presets.map { it.id }.toSet().intersect(REQUIRED_PRESET_IDS.toSet()).size,
        )
        assertTrue(isRequiredPreset("pen-box-ballpoint"))
        assertTrue(!isRequiredPreset("custom"))
    }
}
