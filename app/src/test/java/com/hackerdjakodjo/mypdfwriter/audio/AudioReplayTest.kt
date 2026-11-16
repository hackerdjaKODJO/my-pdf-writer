package com.hackerdjakodjo.mypdfwriter.audio

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioReplayTest {

    private fun note(startedAt: Long = 1_000_000L) = AudioNote(
        id = "audio-1",
        ownerId = "doc-1",
        pageIndex = 2,
        filePath = "/tmp/audio-1.m4a",
        durationMs = 10_000L,
        createdAt = startedAt,
    )

    private fun annotation(createdAt: Long, durationMs: Long = 0L) = Annotation(
        type = AnnotationType.PEN,
        page = 2,
        color = 0,
        width = 0.004f,
        createdAt = createdAt,
        durationMs = durationMs,
    )

    @Test
    fun cutoffUsesRecordingStartPlusPosition() {
        assertEquals(1_004_500L, audioReplayCutoffMs(note(), 4_500L))
        assertEquals(1_000_000L, audioReplayCutoffMs(note(), -500L))
        assertNull(audioReplayCutoffMs(null, 4_500L))
    }

    @Test
    fun replayRevealsStrokesWhenTheirTimestampIsReached() {
        val active = note()

        assertTrue(annotationVisibleDuringReplay(annotation(900_000L), active, 0L))
        assertFalse(annotationVisibleDuringReplay(annotation(1_003_000L), active, 2_999L))
        assertTrue(annotationVisibleDuringReplay(annotation(1_003_000L), active, 3_000L))
    }

    @Test
    fun legacyAnnotationsAndNoActiveReplayStayVisible() {
        assertTrue(annotationVisibleDuringReplay(annotation(0L), note(), 0L))
        assertTrue(annotationVisibleDuringReplay(annotation(9_999_999L), null, 0L))
    }

    @Test
    fun revealFractionUsesElapsedTimeOverStrokeDuration() {
        val active = note()
        val stroke = annotation(createdAt = 1_002_000L, durationMs = 2_000L)

        assertEquals(1f, annotationRevealFraction(stroke, null, 9_999L), 1e-6f)
        assertEquals(1f, annotationRevealFraction(annotation(0L), active, 0L), 1e-6f)
        assertEquals(0f, annotationRevealFraction(stroke, active, 1_999L), 1e-6f)
        assertEquals(0f, annotationRevealFraction(stroke, active, 2_000L), 1e-6f)
        assertEquals(0.25f, annotationRevealFraction(stroke, active, 2_500L), 1e-6f)
        assertEquals(1f, annotationRevealFraction(stroke, active, 4_000L), 1e-6f)
        assertEquals(1f, annotationRevealFraction(stroke, active, 8_000L), 1e-6f)
    }

    @Test
    fun revealFractionSnapsForAnnotationsWithoutDuration() {
        val active = note()
        val annotation = annotation(createdAt = 1_003_000L)

        assertEquals(0f, annotationRevealFraction(annotation, active, 2_999L), 1e-6f)
        assertEquals(1f, annotationRevealFraction(annotation, active, 3_000L), 1e-6f)
    }
}
