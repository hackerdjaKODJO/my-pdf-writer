package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Small per-document sidecar for watermark/page-number settings. */
class DocumentStyleStore(context: Context) {

    private val dir = File(context.filesDir, "docstyles").apply { mkdirs() }

    fun load(docId: String): DocumentStyle {
        val file = File(dir, "$docId.json")
        if (!file.exists()) return DocumentStyle()
        return runCatching { DocumentStyle.fromJson(JSONObject(file.readText())) }
            .getOrDefault(DocumentStyle())
    }

    fun save(docId: String, style: DocumentStyle) {
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        runCatching {
            tmp.writeText(style.toJson().toString())
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
