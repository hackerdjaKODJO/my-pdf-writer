package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A local text excerpt captured from one page of a PDF. */
data class Excerpt(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val text: String,
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("page", pageIndex)
        put("text", text)
        put("title", title)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Excerpt = Excerpt(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            pageIndex = o.optInt("page", 0),
            text = o.optString("text"),
            title = o.optString("title"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )

        fun listToJson(excerpts: List<Excerpt>): JSONArray = JSONArray().apply {
            excerpts.forEach { put(it.toJson()) }
        }

        fun listFromJson(array: JSONArray): List<Excerpt> {
            val result = ArrayList<Excerpt>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(fromJson(it)) }
            }
            return result
        }
    }
}
