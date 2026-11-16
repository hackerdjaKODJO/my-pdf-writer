package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A user-created bookmark on one page of a PDF document. */
data class PdfBookmark(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("page", pageIndex)
        put("title", title)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): PdfBookmark = PdfBookmark(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            pageIndex = o.optInt("page", 0).coerceAtLeast(0),
            title = o.optString("title"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        )

        fun listToJson(bookmarks: List<PdfBookmark>): JSONArray = JSONArray().apply {
            bookmarks.forEach { put(it.toJson()) }
        }

        fun listFromJson(array: JSONArray): List<PdfBookmark> {
            val result = ArrayList<PdfBookmark>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(fromJson(it)) }
            }
            return result
        }
    }
}

/** Reading progress and page bookmarks for one PDF, persisted in a small local sidecar. */
data class PdfReadingState(
    val lastPage: Int = 0,
    val bookmarks: List<PdfBookmark> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("lastPage", lastPage)
        put("bookmarks", PdfBookmark.listToJson(bookmarks))
    }

    /** Keeps progress and bookmarks inside the document and one bookmark per page. */
    fun normalized(pageCount: Int): PdfReadingState {
        if (pageCount <= 0) return copy(lastPage = 0, bookmarks = emptyList())
        return copy(
            lastPage = lastPage.coerceIn(0, pageCount - 1),
            bookmarks = bookmarks
                .filter { it.pageIndex in 0 until pageCount }
                .distinctBy { it.pageIndex },
        )
    }

    fun bookmarkAt(pageIndex: Int): PdfBookmark? =
        bookmarks.firstOrNull { it.pageIndex == pageIndex }

    /** Adds a bookmark, replacing any existing bookmark on the same page in place. */
    fun withBookmark(bookmark: PdfBookmark): PdfReadingState {
        val index = bookmarks.indexOfFirst { it.pageIndex == bookmark.pageIndex }
        if (index < 0) return copy(bookmarks = bookmarks + bookmark)
        return copy(
            bookmarks = bookmarks.toMutableList().also { it[index] = bookmark },
        )
    }

    fun withoutBookmark(id: String): PdfReadingState =
        copy(bookmarks = bookmarks.filterNot { it.id == id })

    fun withRenamedBookmark(id: String, title: String): PdfReadingState = copy(
        bookmarks = bookmarks.map { if (it.id == id) it.copy(title = title) else it },
    )

    companion object {
        fun fromJson(o: JSONObject): PdfReadingState = PdfReadingState(
            lastPage = o.optInt("lastPage", 0).coerceAtLeast(0),
            bookmarks = PdfBookmark.listFromJson(o.optJSONArray("bookmarks") ?: JSONArray()),
        )
    }
}
