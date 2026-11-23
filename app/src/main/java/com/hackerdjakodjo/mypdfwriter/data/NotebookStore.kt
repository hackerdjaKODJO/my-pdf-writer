package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One small JSON file per notebook; atomic writes keep notes safe on crash. */
class NotebookStore(context: Context) {

    private val dir = File(context.filesDir, "notebooks").apply { mkdirs() }

    fun loadAll(): List<Notebook> {
        val ids = dir.listFiles { file -> file.isFile && sidecarId(file) != null }
            ?.mapNotNull(::sidecarId)
            ?.toSet()
            ?: return emptyList()
        return ids.mapNotNull(::load).sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Notebook? = readWithTmpRecovery(File(dir, "$id.json")) {
        Notebook.fromJson(JSONObject(it))
    }

    fun save(notebook: Notebook) {
        val target = File(dir, "${notebook.id}.json")
        val tmp = File(dir, "${notebook.id}.json.tmp")
        runCatching {
            tmp.writeText(notebook.normalized().toJson().toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }
}
