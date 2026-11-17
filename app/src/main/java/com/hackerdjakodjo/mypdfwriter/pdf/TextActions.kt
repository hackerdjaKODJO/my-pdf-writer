package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

enum class TranslateTarget(val code: String, val label: String) {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    CHINESE("zh-CN", "中文"),
}

/**
 * Hands text to Google Translate. The app itself still has no INTERNET
 * permission: the Google Translate app or the browser performs the network
 * request under its own privacy policy.
 */
object TextActions {

    private const val MIME_TEXT = "text/plain"
    private const val TRANSLATE_PACKAGE = "com.google.android.apps.translate"
    private const val MAX_URL_TEXT = 1800

    /**
     * Turns a PDF text selection into a single-line dictionary query. PDF text
     * frequently wraps across lines; collapse every whitespace run into one
     * space and trim the edges without altering the selected words themselves.
     * Pure and device-independent so it can be unit tested.
     */
    internal fun dictionaryQuery(text: String): String =
        text.replace(Regex("""\s+"""), " ").trim()

    fun translate(
        context: Context,
        text: String,
        target: TranslateTarget = TranslateTarget.KOREAN,
    ) {
        val value = text.trim()
        if (value.isEmpty()) return

        // Google Translate's web/app deep link can ignore the URL `text=`
        // parameter. Always put the exact selection on the clipboard so the user
        // can paste it even when the deep link opens without preloaded text.
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Google 번역", value))
        Toast.makeText(
            context,
            "번역할 문구를 복사했습니다. Google 번역에 붙여넣으세요.",
            Toast.LENGTH_SHORT,
        ).show()

        val includeText = value.length <= MAX_URL_TEXT
        val url = buildString {
            append("https://translate.google.com/?sl=auto&tl=")
            append(target.code)
            if (includeText) {
                append("&text=")
                append(Uri.encode(value))
            }
            append("&op=translate")
        }

        // ACTION_PROCESS_TEXT is what the system text-selection menu uses; when
        // available it opens Translate with this text already filled in.
        val processIntent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_PROCESS_TEXT, value)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            setPackage(TRANSLATE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = Uri.parse(url)
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Intent.EXTRA_TEXT, value)
            setPackage(TRANSLATE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Intent.EXTRA_TEXT, value)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            context.startActivity(processIntent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(appIntent)
                true
            }.getOrElse {
                runCatching {
                    context.startActivity(browserIntent)
                    true
                }.getOrDefault(false)
            }
        }
        if (!opened) {
            shareText(context, value)
        }
    }

    /**
     * Hands the exact selected text to whichever dictionary/notes app the user
     * chooses via [Intent.createChooser]. The app itself ships no dictionary
     * data, hardcodes no package and needs no INTERNET permission: the chosen
     * app looks the word up under its own privacy policy. If no installed app
     * can process the text, copy the query to the clipboard and tell the user
     * briefly in Korean.
     */
    fun define(context: Context, text: String) {
        val query = dictionaryQuery(text)
        if (query.isEmpty()) return

        val processText = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_PROCESS_TEXT, query)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(processText, "사전에서 찾기")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val opened = runCatching { context.startActivity(chooser) }.isSuccess
        if (!opened) copyQueryToClipboard(context, query)
    }

    private fun copyQueryToClipboard(context: Context, query: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(context, "사전 앱이 없고 클립보드도 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("사전 검색어", query))
        Toast.makeText(context, "사전 앱이 없어 검색어를 복사했습니다.", Toast.LENGTH_SHORT).show()
    }

    fun shareText(context: Context, text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_TEXT, value)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(send, "텍스트 공유")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
