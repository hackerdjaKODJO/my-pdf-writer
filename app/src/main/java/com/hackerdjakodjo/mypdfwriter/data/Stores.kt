package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the per-document working state (page layout, annotations, notes) as small JSON files. */
class DocStateStore(context: Context) {

    private val dir = File(context.filesDir, "docstates").apply { mkdirs() }

    fun load(docId: String): DocState? {
        val file = File(dir, "$docId.json")
        if (!file.exists()) return null
        return runCatching {
            DocState.fromJson(docId, JSONObject(file.readText()))
        }.getOrNull()
    }

    fun save(state: DocState, dirty: Boolean = false) {
        val target = File(dir, "${state.docId}.json")
        val tmp = File(dir, "${state.docId}.json.tmp")
        runCatching {
            tmp.writeText(state.toJson().toString())
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

/** Recent-document list, kept in SharedPreferences as a compact JSON array. */
class LibraryStore(context: Context) {

    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun load(): List<RecentDoc> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = ArrayList<RecentDoc>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { RecentDoc.fromJson(it) }
                    ?.takeIf { it.uri.isNotBlank() }
                    ?.let(result::add)
            }
            result
        }.getOrDefault(emptyList())
    }

    fun save(docs: List<RecentDoc>) {
        val array = JSONArray()
        docs.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun upsert(doc: RecentDoc): List<RecentDoc> {
        val updated = load().filterNot { it.uri == doc.uri }.toMutableList()
        updated.add(0, doc)
        val capped = updated.take(MAX_RECENTS)
        save(capped)
        return capped
    }

    fun remove(uri: String): List<RecentDoc> {
        val updated = load().filterNot { it.uri == uri }
        save(updated)
        return updated
    }

    private companion object {
        const val KEY = "recents_v1"
        const val MAX_RECENTS = 40
    }
}
