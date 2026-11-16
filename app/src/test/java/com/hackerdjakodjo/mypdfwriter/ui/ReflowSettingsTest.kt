package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReflowSettingsTest {

    @Test
    fun defaultsProduceComfortableLineHeight() {
        val settings = ReflowSettings()
        assertEquals(18f, settings.fontSizeSp, 0.001f)
        assertEquals(1.7f, settings.lineHeightScale, 0.001f)
        assertEquals(30.6f, settings.lineHeightSp, 0.001f)
        assertEquals(ReflowTheme.PAPER, settings.theme)
    }

    @Test
    fun normalizedClampsExtremeSliderValues() {
        val small = ReflowSettings(fontSizeSp = 2f, lineHeightScale = 0.2f).normalized()
        assertEquals(14f, small.fontSizeSp, 0.001f)
        assertEquals(1.2f, small.lineHeightScale, 0.001f)

        val large = ReflowSettings(fontSizeSp = 80f, lineHeightScale = 9f).normalized()
        assertEquals(30f, large.fontSizeSp, 0.001f)
        assertEquals(2.2f, large.lineHeightScale, 0.001f)
    }

    @Test
    fun lineHeightUsesClampedValues() {
        val settings = ReflowSettings(fontSizeSp = 20f, lineHeightScale = 2f)
        assertEquals(40f, settings.lineHeightSp, 0.001f)
    }

    @Test
    fun pageCounterIsOneBasedAndClamped() {
        assertEquals("0 / 0쪽", formatPageCounter(0, 0))
        assertEquals("1 / 1쪽", formatPageCounter(0, 1))
        assertEquals("3 / 12쪽", formatPageCounter(2, 12))
        assertEquals("12 / 12쪽", formatPageCounter(99, 12))
        assertEquals("1 / 12쪽", formatPageCounter(-4, 12))
    }

    @Test
    fun pageStepsStayInsideTheDocument() {
        assertEquals(0, stepPageIndex(0, -1, 10))
        assertEquals(1, stepPageIndex(0, 1, 10))
        assertEquals(9, stepPageIndex(9, 1, 10))
        assertEquals(4, stepPageIndex(5, -1, 10))
        assertEquals(0, stepPageIndex(5, 1, 0))
    }

    @Test
    fun labelsUseDotDecimalSeparator() {
        assertEquals("18sp", formatFontSize(18f))
        assertEquals("14sp", formatFontSize(1f))
        assertEquals("30sp", formatFontSize(44f))
        assertEquals("1.7×", formatLineHeight(1.66f))
        assertEquals("2.2×", formatLineHeight(4f))
        assertEquals("1.0×", formatSpeechRate(1f))
        assertEquals("0.5×", formatSpeechRate(0f))
        assertEquals("2.0×", formatSpeechRate(3f))
    }
}
