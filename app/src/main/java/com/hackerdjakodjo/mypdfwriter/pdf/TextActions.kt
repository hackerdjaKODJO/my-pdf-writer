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

    fun translate(
        context: Context,
        text: String,
        target: TranslateTarget = TranslateTarget.KOREAN,
    ) {
        val value = text.trim()
        if (value.isEmpty()) return

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
        if (!includeText) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Google 번역", value))
            Toast.makeText(
                context,
                "텍스트가 길어 클립보드에 복사했습니다. Google 번역에 붙여넣으세요.",
                Toast.LENGTH_LONG,
            ).show()
        }

        val uri = Uri.parse(url)
        val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(TRANSLATE_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val opened = runCatching {
            context.startActivity(appIntent)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(browserIntent)
                true
            }.getOrDefault(false)
        }
        if (!opened) {
            shareText(context, value)
        }
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
