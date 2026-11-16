package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** One small JSON sidecar of excerpts per PDF document, kept entirely on device. */
class ExcerptStore(context: Context) {

    private val dir = File(context.filesDir, "excerpts").apply { mkdirs() }

    fun load(docId: String): List<Excerpt> {
        val file = File(dir, "$docId.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            Excerpt.listFromJson(JSONArray(file.readText()))
                .filter { it.text.isNotBlank() }
                .sortedByDescending { it.createdAt }
        }.getOrDefault(emptyList())
    }

    fun save(docId: String, excerpts: List<Excerpt>) {
        dir.mkdirs()
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        runCatching {
            tmp.writeText(Excerpt.listToJson(excerpts).toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun delete(docId: String) {
        File(dir, "$docId.json").delete()
    }
}
