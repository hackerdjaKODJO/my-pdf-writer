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

    private fun annotation(createdAt: Long) = Annotation(
        type = AnnotationType.PEN,
        page = 2,
        color = 0,
        width = 0.004f,
        createdAt = createdAt,
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
}
