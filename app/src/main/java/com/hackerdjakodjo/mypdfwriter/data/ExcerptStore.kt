package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** One small JSON sidecar of excerpts per PDF document, kept entirely on device. */
class ExcerptStore internal constructor(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, "excerpts").apply { mkdirs() })

    fun load(docId: String): List<Excerpt> =
        readWithTmpRecovery(File(dir, "$docId.json")) {
            Excerpt.listFromJson(JSONArray(it))
                .filter { entry -> entry.text.isNotBlank() }
                .sortedByDescending { entry -> entry.createdAt }
        } ?: emptyList()

    fun save(docId: String, excerpts: List<Excerpt>): Boolean {
        if (docId.isBlank()) return false
        dir.mkdirs()
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        return runCatching {
            tmp.writeText(Excerpt.listToJson(excerpts).toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            load(docId) == excerpts.sortedByDescending { it.createdAt }
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    fun delete(docId: String): Boolean =
        deleteSidecarWithTmp(File(dir, "$docId.json"))
}
