package com.hackerdjakodjo.mypdfwriter.pdf

/** Languages exposed by the system TTS read-aloud controls. */
enum class ReadAloudLanguage(val label: String, val localeTag: String) {
    KOREAN("한국어", "ko-KR"),
    ENGLISH("English", "en-US"),
}

/**
 * Pure text helpers for read-aloud playback.
 *
 * Keeping chunking and page skipping here means the Android [android.speech.tts.TextToSpeech]
 * wrapper stays small and the rules can be unit-tested without a device.
 */
object ReadAloudText {

    /**
     * TextToSpeech rejects inputs above its engine limit (commonly 4000 chars).
     * Stay comfortably below that and keep sentence/paragraph boundaries where
     * possible so playback sounds natural.
     */
    const val DEFAULT_CHUNK_LIMIT = 3000

    fun chunks(text: String, limit: Int = DEFAULT_CHUNK_LIMIT): List<String> {
        require(limit > 0) { "limit must be positive" }
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()

        val result = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val remaining = normalized.length - start
            if (remaining <= limit) {
                val tail = normalized.substring(start).trim()
                if (tail.isNotEmpty()) result += tail
                break
            }

            val window = normalized.substring(start, start + limit)
            var cut = preferredCut(window)
            if (cut <= 0) cut = safeHardCut(window)
            if (cut <= 0) cut = limit

            val piece = normalized.substring(start, start + cut).trim()
            if (piece.isNotEmpty()) result += piece
            start += cut
            while (start < normalized.length && normalized[start].isWhitespace()) start++
        }
        return result
    }

    /** First page at or after [from] that has readable text, or null. */
    fun firstReadablePage(pages: List<String>, from: Int): Int? {
        if (pages.isEmpty() || from > pages.lastIndex) return null
        val start = from.coerceAtLeast(0)
        return (start..pages.lastIndex).firstOrNull { pages[it].isNotBlank() }
    }

    /** Last readable page at or before [from] (used by the skip-back button). */
    fun previousReadablePage(pages: List<String>, from: Int): Int? {
        if (pages.isEmpty() || from < 0) return null
        val start = from.coerceAtMost(pages.lastIndex)
        return (start downTo 0).firstOrNull { pages[it].isNotBlank() }
    }

    /**
     * Nearest readable page, preferring forward. This makes "read this page"
     * work when the current page is a scanned/blank page.
     */
    fun nearestReadablePage(pages: List<String>, from: Int): Int? {
        firstReadablePage(pages, from)?.let { return it }
        if (pages.isEmpty()) return null
        val start = from.coerceIn(0, pages.lastIndex)
        return (start downTo 0).firstOrNull { pages[it].isNotBlank() }
    }

    private fun preferredCut(window: String): Int {
        val candidates = intArrayOf(
            window.lastIndexOf('\n'),
            window.lastIndexOf(". "),
            window.lastIndexOf("! "),
            window.lastIndexOf("? "),
            window.lastIndexOf('。'),
            window.lastIndexOf('！'),
            window.lastIndexOf('？'),
            window.lastIndexOf("; "),
            window.lastIndexOf(", "),
            window.lastIndexOf(' '),
        )
        val best = candidates.max()
        // Only use a boundary when it is not pathologically early; otherwise a
        // long unbroken run would produce dozens of one-word chunks.
        return if (best >= window.length / 2) best + 1 else -1
    }

    private fun safeHardCut(window: String): Int {
        if (window.isEmpty()) return 0
        return if (window[window.lastIndex].isHighSurrogate()) window.lastIndex else window.length
    }
}
