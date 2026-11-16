package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlin.math.abs

/**
 * Small wrapper around Android's system [TextToSpeech] engine.
 *
 * The app has no INTERNET permission: voice data, if missing, must be installed
 * through the device's own TTS settings. Nothing here talks to a cloud service.
 *
 * All callbacks are delivered on the main thread. [onState] reports the current
 * source page and whether speech is active; [onError] carries a Korean message
 * suitable for display in the sheet.
 *
 * Integration note: apps targeting Android 11+ should declare the
 * `android.intent.action.TTS_SERVICE` intent in the manifest's `<queries>`
 * element so the framework can discover installed TTS engines.
 */
class ReadAloudController(
    context: Context,
    private val pages: List<String>,
    private val onState: (pageIndex: Int, playing: Boolean) -> Unit,
    private val onError: (message: String) -> Unit,
    initialPage: Int = 0,
) {

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 2.0f

        private const val NO_TEXT_MESSAGE = "이 페이지에서 읽을 텍스트를 찾지 못했습니다."
        private const val NO_ENGINE_MESSAGE =
            "기기의 텍스트 음성 변환 엔진을 사용할 수 없습니다. Android 설정에서 TTS 엔진과 음성 데이터를 확인하세요."
        private const val PLAYBACK_ERROR_MESSAGE =
            "음성을 재생하지 못했습니다. 기기의 TTS 설정과 음성 데이터를 확인하세요."
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var initFailed = false
    private var closed = false

    private var currentPage = if (pages.isEmpty()) 0 else initialPage.coerceIn(0, pages.lastIndex)
    private var playing = false
    private var pendingPage: Int? = null

    private var language = ReadAloudLanguage.KOREAN
    private var rate = 1.0f

    /** Guards callbacks from utterances queued before pause/seek/shutdown. */
    private var generation = 0
    private var utterancePrefix = ""
    private var chunkCount = 0

    val currentPageIndex: Int get() = currentPage
    val isPlaying: Boolean get() = playing
    val isInitialized: Boolean get() = initialized
    val speechRate: Float get() = rate
    val currentLanguage: ReadAloudLanguage get() = language

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            if (utteranceId == null) return
            mainHandler.post { handleDone(utteranceId) }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) {
            mainHandler.post { handleUtteranceError(utteranceId) }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            mainHandler.post { handleUtteranceError(utteranceId) }
        }
    }

    init {
        val engine = runCatching {
            TextToSpeech(appContext) { status ->
                mainHandler.post { handleInit(status) }
            }
        }.getOrNull()

        tts = engine
        if (engine == null) {
            initFailed = true
            mainHandler.post {
                if (closed) return@post
                notifyState()
                onError(NO_ENGINE_MESSAGE)
            }
        } else {
            engine.setOnUtteranceProgressListener(listener)
            runCatching {
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
            }
        }
    }

    /**
     * Starts (or restarts) playback at [fromPage]. Pages without text are
     * skipped forward, then backward, so tapping read-aloud on a scanned page
     * still does something useful.
     */
    fun play(fromPage: Int = currentPage) {
        if (closed) return
        if (pages.isEmpty()) {
            fail(NO_TEXT_MESSAGE)
            return
        }
        val target = ReadAloudText.nearestReadablePage(pages, fromPage)
        if (target == null) {
            fail(NO_TEXT_MESSAGE)
            return
        }
        currentPage = target

        if (initFailed) {
            fail(NO_ENGINE_MESSAGE)
            return
        }
        if (!initialized) {
            // The sheet keeps its "preparing" state until handleInit reports the
            // engine is ready (or fails with an error).
            pendingPage = target
            return
        }
        pendingPage = null
        startPage(target)
    }

    fun pause() {
        if (closed) return
        pendingPage = null
        stopSpeech()
        if (playing) {
            playing = false
            notifyState()
        }
    }

    /** Restarts the current page; TTS has no true pause primitive. */
    fun resume() = play(currentPage)

    fun stop() = pause()

    /** Moves to a page. If speaking, playback restarts from that page's text. */
    fun seekTo(page: Int) {
        if (closed) return
        if (pages.isEmpty()) {
            currentPage = 0
            playing = false
            notifyState()
            return
        }

        val safePage = page.coerceIn(0, pages.lastIndex)
        val wasPlaying = playing
        pendingPage = null
        stopSpeech()
        currentPage = safePage

        if (!wasPlaying) {
            playing = false
            notifyState()
            return
        }

        val target = ReadAloudText.nearestReadablePage(pages, safePage)
        if (target == null) {
            playing = false
            notifyState()
            onError(NO_TEXT_MESSAGE)
        } else {
            startPage(target)
        }
    }

    /**
     * Applies 0.5×–2.0× speed. Pass [restartCurrent] from the slider's
     * onValueChangeFinished callback so dragging does not restart audio on every
     * pixel of movement.
     */
    fun setSpeechRate(value: Float, restartCurrent: Boolean = false) {
        if (closed) return
        val clamped = value.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        if (abs(clamped - rate) < 0.0001f) return
        rate = clamped
        tts?.let { runCatching { it.setSpeechRate(rate) } }
        if (restartCurrent && playing) startPage(currentPage)
    }

    fun setLanguage(value: ReadAloudLanguage) {
        if (closed || value == language) return
        language = value

        if (initFailed) {
            fail(NO_ENGINE_MESSAGE)
            return
        }
        if (!initialized) {
            // The pending page is started with the new language by handleInit.
            return
        }
        if (!applyLanguage(value)) {
            playing = false
            stopSpeech()
            notifyState()
            return
        }
        if (playing) {
            startPage(currentPage)
        } else {
            notifyState()
        }
    }

    /** Releases the engine. Safe to call more than once. */
    fun shutdown() {
        if (closed) return
        closed = true
        pendingPage = null
        generation++
        utterancePrefix = ""
        chunkCount = 0
        playing = false

        val engine = tts
        runCatching { engine?.stop() }
        if (engine != null && (initialized || initFailed)) {
            runCatching { engine.shutdown() }
            tts = null
        }
        // If the engine is still initializing, handleInit sees `closed` and
        // performs the shutdown when the framework finishes connecting.
        initialized = false
    }

    private fun handleInit(status: Int) {
        if (closed) {
            runCatching { tts?.shutdown() }
            tts = null
            return
        }
        if (status != TextToSpeech.SUCCESS) {
            initFailed = true
            initialized = false
            playing = false
            pendingPage = null
            runCatching { tts?.shutdown() }
            tts = null
            notifyState()
            onError(NO_ENGINE_MESSAGE)
            return
        }

        initialized = true
        initFailed = false
        tts?.let { engine ->
            runCatching { engine.setSpeechRate(rate) }
        }

        if (!applyLanguage(language)) {
            pendingPage = null
            playing = false
            notifyState()
            return
        }

        val target = pendingPage
        pendingPage = null
        if (target != null) {
            startPage(target)
        } else {
            notifyState()
        }
    }

    private fun startPage(page: Int) {
        if (closed) return
        val engine = tts
        if (engine == null || !initialized) {
            pendingPage = page
            playing = false
            notifyState()
            return
        }

        val chunks = ReadAloudText.chunks(pages.getOrNull(page).orEmpty())
        if (chunks.isEmpty()) {
            fail(NO_TEXT_MESSAGE)
            return
        }
        if (!applyLanguage(language)) {
            playing = false
            notifyState()
            return
        }

        generation++
        val prefix = "rea-$generation-$page-"
        utterancePrefix = prefix
        chunkCount = chunks.size
        currentPage = page
        playing = true

        runCatching { engine.setSpeechRate(rate) }
        notifyState()

        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = runCatching {
                engine.speak(chunk, queueMode, null, "$prefix$index")
            }.getOrDefault(TextToSpeech.ERROR)

            if (result == TextToSpeech.ERROR) {
                stopSpeech()
                playing = false
                notifyState()
                onError(PLAYBACK_ERROR_MESSAGE)
                return
            }
        }
    }

    private fun handleDone(utteranceId: String) {
        if (closed || !playing) return
        if (!utteranceId.startsWith(utterancePrefix)) return
        val chunk = utteranceId.removePrefix(utterancePrefix).toIntOrNull() ?: return
        if (chunk < chunkCount - 1) return

        val next = ReadAloudText.firstReadablePage(pages, currentPage + 1)
        if (next == null) {
            playing = false
            utterancePrefix = ""
            chunkCount = 0
            notifyState()
        } else {
            startPage(next)
        }
    }

    private fun handleUtteranceError(utteranceId: String?) {
        if (closed || !playing) return
        if (utteranceId != null && !utteranceId.startsWith(utterancePrefix)) return
        stopSpeech()
        playing = false
        notifyState()
        onError(PLAYBACK_ERROR_MESSAGE)
    }

    private fun applyLanguage(value: ReadAloudLanguage): Boolean {
        val engine = tts ?: return false
        val result = runCatching {
            engine.setLanguage(Locale.forLanguageTag(value.localeTag))
        }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

        if (result < 0) {
            onError(
                "기기에 ${value.label} 음성 데이터가 없습니다. " +
                    "Android 설정 > 텍스트 음성 변환에서 음성을 설치하세요.",
            )
            return false
        }
        return true
    }

    private fun stopSpeech() {
        generation++
        utterancePrefix = ""
        chunkCount = 0
        runCatching { tts?.stop() }
    }

    private fun fail(message: String) {
        if (closed) return
        playing = false
        pendingPage = null
        notifyState()
        onError(message)
    }

    private fun notifyState() {
        if (!closed) onState(currentPage, playing)
    }
}
