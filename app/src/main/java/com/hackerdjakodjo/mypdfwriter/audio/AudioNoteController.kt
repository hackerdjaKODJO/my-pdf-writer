package com.hackerdjakodjo.mypdfwriter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File

/** Immutable snapshot reported to the audio-notes UI. */
data class AudioNoteControllerState(
    val recording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recordingFilePath: String? = null,
    val playingNoteId: String? = null,
    val playing: Boolean = false,
    val preparing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/** Result of a successfully stopped microphone recording. */
data class AudioRecording(
    val filePath: String,
    val durationMs: Long,
)

/**
 * Small owner of one [MediaRecorder] and one [MediaPlayer].
 *
 * Recordings are mono AAC in an MPEG-4 `.m4a` container written to the path the
 * caller received from
 * [com.hackerdjakodjo.mypdfwriter.data.AudioNoteStore.createRecordingFile].
 * Playback reads that same local file. Nothing here uses the network.
 *
 * Call the public methods on the main thread. [onState] and [onError] are
 * delivered on the main thread. The class deliberately does not run a
 * foreground service: keeping the screen on while the sheet is open is enough
 * for this pass, and longer background recordings remain future work.
 *
 * Integration note: add `android.permission.RECORD_AUDIO` to the manifest and
 * request it from the UI. This class reports a Korean error instead of crashing
 * when the permission is missing.
 */
class AudioNoteController(
    context: Context,
    private val onState: (AudioNoteControllerState) -> Unit = {},
    private val onError: (String) -> Unit = {},
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recorder: MediaRecorder? = null
    private var recorderFile: File? = null
    private var recordStartedAt = 0L
    private var recording = false

    private var player: MediaPlayer? = null
    private var playerNoteId: String? = null
    private var playerPath: String? = null
    private var playerPosition = 0L
    private var playerDuration = 0L
    private var playing = false
    private var preparing = false

    /** Invalidates listeners and prepared callbacks from an older player. */
    private var playerGeneration = 0
    private var closed = false

    private val ticker = object : Runnable {
        override fun run() {
            if (closed) return
            if (recording) {
                notifyState()
                mainHandler.postDelayed(this, TICK_MS)
            } else if (playing && player != null) {
                notifyState()
                mainHandler.postDelayed(this, TICK_MS)
            }
        }
    }

    /**
     * Starts a new recording. Returns false when another recording is active or
     * when the recorder cannot start; [onError] carries the Korean reason.
     */
    fun startRecording(outputFile: File): Boolean {
        if (closed || recording) return false
        if (playing || preparing) {
            onError("재생 중에는 녹음할 수 없습니다. 먼저 재생을 중지하세요.")
            return false
        }

        // A paused player is no longer producing sound; release it so the mic
        // session is unambiguous.
        releasePlayer()
        playerNoteId = null
        playerPath = null
        playerPosition = 0L
        playerDuration = 0L
        mainHandler.removeCallbacks(ticker)

        outputFile.parentFile?.mkdirs()
        val newRecorder = try {
            createRecorder()
        } catch (error: Exception) {
            onError("녹음을 시작하지 못했습니다. 마이크 권한과 다른 앱의 녹음 사용을 확인하세요.")
            notifyState()
            return false
        }
        return try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioChannels(1)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.setAudioEncodingBitRate(128_000)
            newRecorder.setOutputFile(outputFile.absolutePath)
            newRecorder.prepare()
            newRecorder.start()

            recorder = newRecorder
            recorderFile = outputFile
            recordStartedAt = SystemClock.elapsedRealtime()
            recording = true
            notifyState()
            mainHandler.removeCallbacks(ticker)
            mainHandler.post(ticker)
            true
        } catch (error: Exception) {
            runCatching { newRecorder.reset() }
            runCatching { newRecorder.release() }
            runCatching { outputFile.delete() }
            onError("녹음을 시작하지 못했습니다. 마이크 권한과 다른 앱의 녹음 사용을 확인하세요.")
            notifyState()
            false
        }
    }

    /**
     * Stops and finalizes the current recording. Returns null when there was no
     * recording or Android could not finalize the file; a failed file is
     * deleted so the index never points at broken audio.
     */
    fun stopRecording(): AudioRecording? {
        if (closed || !recording) return null
        val file = recorderFile
        val elapsed = elapsedRecordingMs()
        val active = recorder

        recording = false
        recorder = null
        recorderFile = null
        recordStartedAt = 0L
        mainHandler.removeCallbacks(ticker)

        if (file == null) {
            runCatching { active?.reset() }
            runCatching { active?.release() }
            notifyState()
            return null
        }

        return try {
            active?.stop()
            active?.reset()
            active?.release()
            notifyState()
            AudioRecording(
                filePath = file.absolutePath,
                durationMs = elapsed.coerceAtLeast(1L),
            )
        } catch (error: Exception) {
            runCatching { active?.reset() }
            runCatching { active?.release() }
            runCatching { file.delete() }
            onError("녹음을 저장하지 못했습니다. 너무 짧은 녹음이거나 마이크가 중단되었을 수 있습니다.")
            notifyState()
            null
        }
    }

    /**
     * Starts (or restarts) playback of [noteId] at [startMs]. [durationHintMs]
     * lets the UI show the stored note length before the player has prepared.
     */
    fun play(
        noteId: String,
        filePath: String,
        startMs: Long = 0L,
        durationHintMs: Long = 0L,
    ): Boolean {
        if (closed) return false
        if (recording) {
            onError("녹음 중에는 재생할 수 없습니다. 먼저 녹음을 중지하세요.")
            return false
        }

        val file = File(filePath)
        if (!file.isFile || file.length() <= 0L) {
            onError("녹음 파일을 찾지 못했습니다. 파일이 이동되었거나 삭제되었을 수 있습니다.")
            return false
        }

        releasePlayer()
        playerGeneration++
        val generation = playerGeneration
        playerNoteId = noteId
        playerPath = file.absolutePath
        playerPosition = startMs.coerceAtLeast(0L)
        playerDuration = durationHintMs.coerceAtLeast(0L)
        preparing = true
        playing = false
        notifyState()

        val newPlayer = try {
            MediaPlayer()
        } catch (error: Exception) {
            preparing = false
            playing = false
            playerNoteId = null
            playerPath = null
            playerPosition = 0L
            playerDuration = 0L
            onError(PLAYBACK_ERROR)
            notifyState()
            return false
        }
        return try {
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            newPlayer.setDataSource(file.absolutePath)
            newPlayer.setOnPreparedListener { prepared ->
                if (closed || generation != playerGeneration || prepared !== player) {
                    runCatching { prepared.release() }
                    return@setOnPreparedListener
                }
                preparing = false
                val actualDuration = runCatching { prepared.duration.toLong() }
                    .getOrDefault(0L)
                    .coerceAtLeast(0L)
                if (actualDuration > 0L) playerDuration = actualDuration
                val target = startMs.coerceIn(
                    0L,
                    playerDuration.coerceAtLeast(0L),
                )
                runCatching { prepared.seekTo(target.toInt()) }
                playing = runCatching {
                    prepared.start()
                    true
                }.getOrElse {
                    preparing = false
                    playing = false
                    releasePlayer()
                    playerNoteId = null
                    playerPath = null
                    playerPosition = 0L
                    playerDuration = 0L
                    onError(PLAYBACK_ERROR)
                    notifyState()
                    false
                }
                if (playing) {
                    notifyState()
                    mainHandler.removeCallbacks(ticker)
                    mainHandler.post(ticker)
                }
            }
            newPlayer.setOnCompletionListener { completed ->
                if (closed || generation != playerGeneration) return@setOnCompletionListener
                playerPosition = runCatching { completed.duration.toLong() }
                    .getOrDefault(playerDuration)
                    .coerceAtLeast(0L)
                playing = false
                preparing = false
                mainHandler.removeCallbacks(ticker)
                releasePlayer()
                playerNoteId = null
                playerPath = null
                playerPosition = 0L
                notifyState()
            }
            newPlayer.setOnErrorListener { _, _, _ ->
                if (closed || generation != playerGeneration) {
                    return@setOnErrorListener true
                }
                releasePlayer()
                playerNoteId = null
                playerPath = null
                playerPosition = 0L
                playerDuration = 0L
                onError(PLAYBACK_ERROR)
                notifyState()
                true
            }
            player = newPlayer
            newPlayer.prepareAsync()
            true
        } catch (error: Exception) {
            runCatching { newPlayer.reset() }
            runCatching { newPlayer.release() }
            if (player === newPlayer) player = null
            preparing = false
            playing = false
            playerNoteId = null
            playerPath = null
            playerPosition = 0L
            playerDuration = 0L
            onError(PLAYBACK_ERROR)
            notifyState()
            false
        }
    }

    fun pause() {
        if (closed || recording || !playing) return
        val active = player ?: return
        val paused = runCatching {
            active.pause()
            true
        }.getOrElse {
            onError(PLAYBACK_ERROR)
            false
        }
        playing = false
        playerPosition = runCatching { active.currentPosition.toLong() }
            .getOrDefault(playerPosition)
            .coerceAtLeast(0L)
        mainHandler.removeCallbacks(ticker)
        if (!paused) {
            releasePlayer()
            playerNoteId = null
            playerPath = null
            playerPosition = 0L
            playerDuration = 0L
        }
        notifyState()
    }

    /** Resumes a paused player; after completion this is a no-op. */
    fun resume() {
        if (closed || recording || playing || preparing) return
        val active = player ?: return
        playing = runCatching {
            active.start()
            true
        }.getOrElse {
            onError(PLAYBACK_ERROR)
            false
        }
        if (playing) {
            notifyState()
            mainHandler.removeCallbacks(ticker)
            mainHandler.post(ticker)
        } else {
            releasePlayer()
            playerNoteId = null
            playerPath = null
            playerPosition = 0L
            playerDuration = 0L
            notifyState()
        }
    }

    /** Releases playback resources and clears the active-note state. */
    fun stopPlayback() {
        if (closed) return
        if (playing || preparing || player != null) {
            releasePlayer()
        }
        playerNoteId = null
        playerPath = null
        playerPosition = 0L
        playerDuration = 0L
        mainHandler.removeCallbacks(ticker)
        notifyState()
    }

    /**
     * Releases the recorder and player. An in-progress recording is discarded
     * because it was never finalized or indexed. Safe to call more than once.
     */
    fun release() {
        if (closed) return
        closed = true
        mainHandler.removeCallbacks(ticker)
        playerGeneration++

        if (recording) {
            recording = false
            val active = recorder
            val file = recorderFile
            recorder = null
            recorderFile = null
            recordStartedAt = 0L
            runCatching { active?.stop() }
            runCatching { active?.reset() }
            runCatching { active?.release() }
            runCatching { file?.delete() }
        } else {
            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            recorder = null
            recorderFile = null
        }

        releasePlayer()
        playerNoteId = null
        playerPath = null
        playerPosition = 0L
        playerDuration = 0L
        playing = false
        preparing = false
    }

    /** Alias for [release] so callers can use the lifecycle spelling. */
    fun close() = release()

    private fun elapsedRecordingMs(): Long {
        if (!recording) return 0L
        return (SystemClock.elapsedRealtime() - recordStartedAt).coerceAtLeast(0L)
    }

    private fun releasePlayer() {
        playerGeneration++
        val active = player
        player = null
        playing = false
        preparing = false
        runCatching { active?.setOnPreparedListener(null) }
        runCatching { active?.setOnCompletionListener(null) }
        runCatching { active?.setOnErrorListener(null) }
        runCatching { active?.reset() }
        runCatching { active?.release() }
        mainHandler.removeCallbacks(ticker)
    }

    private fun notifyState() {
        if (closed) return
        val livePosition = player?.let {
            runCatching { it.currentPosition.toLong() }.getOrDefault(playerPosition)
        } ?: playerPosition
        val liveDuration = player?.let {
            runCatching { it.duration.toLong() }.getOrDefault(0L)
        } ?: 0L
        if (liveDuration > 0L) playerDuration = liveDuration
        playerPosition = livePosition.coerceAtLeast(0L)

        onState(
            AudioNoteControllerState(
                recording = recording,
                recordingElapsedMs = if (recording) elapsedRecordingMs() else 0L,
                recordingFilePath = recorderFile?.absolutePath,
                playingNoteId = playerNoteId,
                playing = playing,
                preparing = preparing,
                positionMs = playerPosition,
                durationMs = playerDuration,
            ),
        )
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(appContext)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val TICK_MS = 200L
        const val PLAYBACK_ERROR =
            "녹음 파일을 재생하지 못했습니다. 파일이 손상되었거나 지원되지 않는 형식일 수 있습니다."
    }
}
