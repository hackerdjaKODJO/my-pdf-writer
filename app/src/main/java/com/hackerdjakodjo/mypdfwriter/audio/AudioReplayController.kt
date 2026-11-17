package com.hackerdjakodjo.mypdfwriter.audio

import android.content.Context
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AudioNote

/**
 * Playback snapshot for one [AudioNote] during stroke replay.
 *
 * [note] is null when nothing is selected. A paused note keeps its id and
 * [positionMs], so the viewer can keep the reveal cutoff frozen until playback
 * resumes or the user explicitly stops.
 */
data class AudioReplayState(
    val note: AudioNote? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    /** Rate applied to the active playback; session-scoped, default 1.0x. */
    val playbackSpeed: Float = PlaybackSpeed.DEFAULT,
) {
    /**
     * Absolute wall-clock timestamp up to which annotations should be visible,
     * or null when replay is not showing a note.
     */
    val revealCutoffMs: Long? get() = audioReplayCutoffMs(note, positionMs)
}

/**
 * Latest annotation timestamp that may be visible at [positionMs] inside
 * [note]. Returns null when there is no active replay note.
 *
 * The cutoff is the recording start plus elapsed playback, matching the
 * timestamps written by [Annotation.createdAt].
 */
fun audioReplayCutoffMs(note: AudioNote?, positionMs: Long): Long? =
    note?.let { it.createdAt + positionMs.coerceAtLeast(0L) }

/**
 * Whether [annotation] should be drawn while replaying [note] at [positionMs].
 *
 * Annotations with no timestamp (`createdAt <= 0`, from documents saved before
 * timestamps existed) stay visible so replay never hides legacy ink. Passing a
 * null [note] disables filtering entirely.
 */
fun annotationVisibleDuringReplay(
    annotation: Annotation,
    note: AudioNote?,
    positionMs: Long,
): Boolean {
    val cutoff = audioReplayCutoffMs(note, positionMs) ?: return true
    if (annotation.createdAt <= 0L) return true
    return annotation.createdAt <= cutoff
}

/**
 * How much of [annotation] should be drawn while replaying [note] at
 * [positionMs], expressed as a fraction from 0 to 1.
 *
 * A null [note] and annotations without a recorded timestamp (`createdAt <= 0`)
 * are fully visible. An annotation that has not started yet is hidden, and a
 * stroke whose [Annotation.durationMs] is known reveals point by point across
 * that window. Duration-less annotations snap to fully visible when their
 * timestamp is reached.
 */
fun annotationRevealFraction(
    annotation: Annotation,
    note: AudioNote?,
    positionMs: Long,
): Float {
    val cutoff = audioReplayCutoffMs(note, positionMs) ?: return 1f
    if (annotation.createdAt <= 0L) return 1f
    val elapsed = cutoff - annotation.createdAt
    if (elapsed < 0L) return 0f
    if (annotation.durationMs <= 0L) return 1f
    return (elapsed.toFloat() / annotation.durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Thin wrapper around [AudioNoteController] for replay.
 *
 * It resolves the active note id from the player callback, falls back to the
 * stored note duration while the player prepares, and reports a single
 * [AudioReplayState] stream. [AudioNoteController] already ticks every 200 ms
 * while playing, so callers receive position updates at that interval without
 * adding another timer.
 *
 * Call methods on the main thread. [onState] and [onError] are delivered on the
 * main thread.
 */
class AudioReplayController(
    context: Context,
    private val onState: (AudioReplayState) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    private var notes: List<AudioNote> = emptyList()
    private var lastRawState = AudioNoteControllerState()

    private val player = AudioNoteController(
        context = context,
        onState = ::handlePlayerState,
        onError = onError,
    )

    /** Keeps note metadata available when a player callback only reports an id. */
    fun updateNotes(notes: List<AudioNote>) {
        this.notes = notes.toList()
    }

    /**
     * Applies [speed] to the active note immediately and remembers it for the
     * rest of this replay session. The player reports the applied value back
     * through [AudioReplayState.playbackSpeed]; [positionMs] and the reveal
     * cutoff still come from the real `MediaPlayer` position, so changing speed
     * cannot desync annotation reveal.
     */
    fun setPlaybackSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    /**
     * Starts [note] from [startMs]. A previous note is replaced. Returns false
     * when the local file is missing or the player cannot start.
     */
    fun play(note: AudioNote, startMs: Long = 0L): Boolean {
        // Keep the latest metadata for this id so callbacks resolve the same note.
        updateNotes(notes.filterNot { it.id == note.id } + note)
        return player.play(
            noteId = note.id,
            filePath = note.filePath,
            startMs = startMs.coerceAtLeast(0L),
            durationHintMs = note.durationMs,
        )
    }

    /**
     * Pause/play toggle for [note]. Paused playback resumes at its saved
     * position; finished or stopped playback starts again from zero.
     */
    fun toggle(note: AudioNote) {
        val active = lastRawState.playingNoteId == note.id
        when {
            active && lastRawState.preparing -> Unit
            active && lastRawState.playing -> player.pause()
            active -> player.resume()
            else -> play(note)
        }
    }

    /** Pauses the active note without clearing its reveal cutoff. */
    fun pause() = player.pause()

    /** Resumes a paused note. */
    fun resume() = player.resume()

    /**
     * Restarts [note] at [positionMs], clamped to its known duration. This is
     * used by the progress slider and page markers.
     */
    fun seekTo(note: AudioNote, positionMs: Long): Boolean {
        val target = if (note.durationMs > 0L) {
            positionMs.coerceIn(0L, note.durationMs)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        return play(note, target)
    }

    /** Stops playback and reports a cleared state (`note == null`). */
    fun stop() = player.stopPlayback()

    /** Releases both recorder and player. Safe to call more than once. */
    fun release() = player.release()

    private fun handlePlayerState(raw: AudioNoteControllerState) {
        lastRawState = raw
        val note = raw.playingNoteId?.let { id -> notes.firstOrNull { it.id == id } }
        val duration = when {
            raw.durationMs > 0L -> raw.durationMs
            note != null -> note.durationMs
            else -> 0L
        }
        onState(
            AudioReplayState(
                note = note,
                positionMs = if (note != null) raw.positionMs.coerceAtLeast(0L) else 0L,
                durationMs = duration.coerceAtLeast(0L),
                playing = note != null && raw.playing,
                preparing = note != null && raw.preparing,
                playbackSpeed = raw.playbackSpeed,
            ),
        )
    }
}
