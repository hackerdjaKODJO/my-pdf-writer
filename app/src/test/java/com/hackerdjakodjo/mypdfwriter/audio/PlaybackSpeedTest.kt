package com.hackerdjakodjo.mypdfwriter.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun allowedRatesAreTheSixFlexcilStepsInOrder() {
        assertEquals(
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
            PlaybackSpeed.ALLOWED,
        )
        assertTrue(PlaybackSpeed.ALLOWED == PlaybackSpeed.ALLOWED.sorted())
    }

    @Test
    fun defaultIsOneAndResetRestoresIt() {
        assertEquals(1.0f, PlaybackSpeed.DEFAULT, 1e-6f)
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.reset(), 1e-6f)
        assertEquals(1.0f, PlaybackSpeed.normalize(PlaybackSpeed.reset()), 1e-6f)
    }

    @Test
    fun isValidAcceptsOnlyAllowedRates() {
        PlaybackSpeed.ALLOWED.forEach { assertTrue(PlaybackSpeed.isValid(it)) }
        assertFalse(PlaybackSpeed.isValid(0.9f))
        assertFalse(PlaybackSpeed.isValid(3.0f))
        assertFalse(PlaybackSpeed.isValid(Float.NaN))
        assertFalse(PlaybackSpeed.isValid(Float.POSITIVE_INFINITY))
    }

    @Test
    fun normalizeClampsOutOfRangeAndSnapsToNearestAllowedRate() {
        assertEquals(0.5f, PlaybackSpeed.normalize(0.1f), 1e-6f)
        assertEquals(0.5f, PlaybackSpeed.normalize(-4f), 1e-6f)
        assertEquals(2.0f, PlaybackSpeed.normalize(9f), 1e-6f)
        assertEquals(1.0f, PlaybackSpeed.normalize(1.1f), 1e-6f)
        assertEquals(1.25f, PlaybackSpeed.normalize(1.2f), 1e-6f)
        assertEquals(1.5f, PlaybackSpeed.normalize(1.4f), 1e-6f)
        assertEquals(0.75f, PlaybackSpeed.normalize(0.74f), 1e-6f)
    }

    @Test
    fun normalizeHandlesNonFiniteInputWithoutCrashing() {
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.normalize(Float.NaN), 1e-6f)
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.normalize(Float.POSITIVE_INFINITY), 1e-6f)
        assertEquals(PlaybackSpeed.DEFAULT, PlaybackSpeed.normalize(Float.NEGATIVE_INFINITY), 1e-6f)
    }

    @Test
    fun normalizeIsIdempotentForEveryAllowedRate() {
        PlaybackSpeed.ALLOWED.forEach { speed ->
            assertEquals(speed, PlaybackSpeed.normalize(speed), 1e-6f)
            assertEquals(speed, PlaybackSpeed.normalize(PlaybackSpeed.normalize(speed)), 1e-6f)
        }
    }

    @Test
    fun labelUsesCompactFormAndNormalizesFirst() {
        assertEquals("0.5x", PlaybackSpeed.label(0.5f))
        assertEquals("0.75x", PlaybackSpeed.label(0.75f))
        assertEquals("1x", PlaybackSpeed.label(1.0f))
        assertEquals("1.25x", PlaybackSpeed.label(1.25f))
        assertEquals("1.5x", PlaybackSpeed.label(1.5f))
        assertEquals("2x", PlaybackSpeed.label(2.0f))
        assertEquals("1x", PlaybackSpeed.label(1.1f))
        assertEquals("1x", PlaybackSpeed.label(Float.NaN))
    }
}
