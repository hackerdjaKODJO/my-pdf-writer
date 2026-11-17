package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for display-preference defaults, clamping and JSON parsing. */
class PdfDisplayPrefsTest {

    @Test
    fun defaultIsDayModeAtFullBrightness() {
        assertEquals(false, DisplayPrefs().nightMode)
        assertEquals(1f, DisplayPrefs().brightness, 0f)
    }

    @Test
    fun clampBrightnessKeepsValuesInsideTheSupportedRange() {
        assertEquals(DisplayPrefs.MIN_BRIGHTNESS, DisplayPrefs.clampBrightness(0.1f), 0f)
        assertEquals(DisplayPrefs.MAX_BRIGHTNESS, DisplayPrefs.clampBrightness(1.8f), 0f)
        assertEquals(0.73f, DisplayPrefs.clampBrightness(0.73f), 0f)
        assertEquals(DisplayPrefs.MIN_BRIGHTNESS, DisplayPrefs.clampBrightness(DisplayPrefs.MIN_BRIGHTNESS), 0f)
    }

    @Test
    fun clampBrightnessReplacesNonFiniteValuesWithTheDefault() {
        assertEquals(DisplayPrefs.DEFAULT_BRIGHTNESS, DisplayPrefs.clampBrightness(Float.NaN), 0f)
        assertEquals(DisplayPrefs.DEFAULT_BRIGHTNESS, DisplayPrefs.clampBrightness(Float.POSITIVE_INFINITY), 0f)
        assertEquals(DisplayPrefs.DEFAULT_BRIGHTNESS, DisplayPrefs.clampBrightness(Float.NEGATIVE_INFINITY), 0f)
    }

    @Test
    fun fromJsonWithoutObjectUsesDefaults() {
        assertEquals(DisplayPrefs(), DisplayPrefs.fromJson(null))
        assertEquals(DisplayPrefs(), DisplayPrefs.fromJson(JSONObject()))
    }

    @Test
    fun fromJsonClampsStoredBrightness() {
        val prefs = DisplayPrefs.fromJson(
            JSONObject().apply {
                put("night", true)
                put("bright", 4.2)
            },
        )
        assertTrue(prefs.nightMode)
        assertEquals(DisplayPrefs.MAX_BRIGHTNESS, prefs.brightness, 0f)
    }

    @Test
    fun jsonRoundTripPreservesBothFields() {
        val original = DisplayPrefs(nightMode = true, brightness = 0.42f)
        val json = original.toJson()

        assertTrue(json.getBoolean("night"))
        assertEquals(0.42, json.getDouble("bright"), 0.0001)
        assertEquals(original, DisplayPrefs.fromJson(json))
    }
}
