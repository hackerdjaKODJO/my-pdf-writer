package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One small JSON sidecar of reading progress and bookmarks per PDF document. */
class PdfReadingStore(context: Context) {

    private val dir = File(context.filesDir, "reading").apply { mkdirs() }

    @Synchronized
    fun load(docId: String): PdfReadingState? {
        if (docId.isBlank()) return null
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        return read(target) ?: read(tmp)
    }

    @Synchronized
    fun save(docId: String, state: PdfReadingState): Boolean {
        if (docId.isBlank()) return false
        dir.mkdirs()
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        return runCatching {
            tmp.writeText(state.toJson().toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            load(docId) == state
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    @Synchronized
    fun delete(docId: String) {
        if (docId.isBlank()) return
        File(dir, "$docId.json").delete()
        File(dir, "$docId.json.tmp").delete()
    }

    private fun read(file: File): PdfReadingState? {
        if (!file.exists()) return null
        return runCatching { PdfReadingState.fromJson(JSONObject(file.readText())) }.getOrNull()
    }
}
