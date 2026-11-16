package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.Context
import android.content.Intent

/**
 * Hands text to another installed app. Nothing is uploaded by this application:
 * translation and sharing happen only inside whichever external app the user picks.
 */
object TextActions {

    fun translate(context: Context, text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val process = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = MIME_TEXT
            putExtra(Intent.EXTRA_PROCESS_TEXT, value)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        val hasHandler = runCatching {
            process.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
        val target = if (hasHandler) process else sendIntent(value)
        launchChooser(
            context = context,
            target = target,
            title = if (hasHandler) "번역 앱 선택" else "텍스트 공유",
        )
    }

    fun shareText(context: Context, text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        launchChooser(context, sendIntent(value), "텍스트 공유")
    }

    private fun sendIntent(text: String): Intent = Intent(Intent.ACTION_SEND).apply {
        type = MIME_TEXT
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun launchChooser(context: Context, target: Intent, title: String) {
        val chooser = Intent.createChooser(target, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    private const val MIME_TEXT = "text/plain"
}
