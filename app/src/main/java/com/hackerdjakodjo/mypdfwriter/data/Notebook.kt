package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PaperTemplate(val label: String, val category: String) {
    BLANK("백지", "기본"),
    LINED("줄노트", "기본"),
    GRID("모눈", "기본"),
    DOTTED("점노트", "기본"),
    CORNELL("코넬 노트", "공부"),
    CHECKLIST("체크리스트", "공부"),
    FOUR_QUADRANT("4분면", "공부"),
    READING("독서 노트", "공부"),
    MEETING("회의록", "업무"),
    PLANNER("주간 플래너", "계획"),
    MUSIC("오선지", "기타"),
}

enum class CoverStyle { SOLID, GRID, DOTS, STRIPES }

data class NoteLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "레이어 1",
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("visible", visible)
        put("locked", locked)
        put("opacity", opacity.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject): NoteLayer = NoteLayer(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name").ifBlank { "레이어" },
            visible = o.optBoolean("visible", true),
            locked = o.optBoolean("locked", false),
            opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1f),
        )
    }
}

data class PageBookmark(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int = 0,
    val title: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("page", pageIndex)
        put("title", title)
    }

    companion object {
        fun fromJson(o: JSONObject): PageBookmark = PageBookmark(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            pageIndex = o.optInt("page"),
            title = o.optString("title"),
        )
    }
}

data class PenPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "펜",
    val color: Int = 0xFF1B1C19.toInt(),
    val width: Float = 0.004f,
    val opacity: Float = 1f,
    val penStyle: PenStyle = PenStyle.BALLPOINT,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("width", width.toDouble())
        put("opacity", opacity.toDouble())
        put("style", penStyle.name)
    }

    companion object {
        fun fromJson(o: JSONObject): PenPreset = PenPreset(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = o.optString("name").ifBlank { "펜" },
            color = o.optInt("color", 0xFF1B1C19.toInt()),
            width = o.optDouble("width", 0.004).toFloat(),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            penStyle = runCatching {
                PenStyle.valueOf(o.optString("style"))
            }.getOrDefault(PenStyle.BALLPOINT),
        )
    }
}

data class NotePage(
    val id: String = UUID.randomUUID().toString(),
    val template: PaperTemplate = PaperTemplate.LINED,
    val annotations: List<Annotation> = emptyList(),
    val memo: String = "",
    val layers: List<NoteLayer> = listOf(NoteLayer()),
) {
    fun normalized(): NotePage {
        val safeLayers = layers.ifEmpty { listOf(NoteLayer()) }
        val defaultLayer = safeLayers.first().id
        return copy(
            layers = safeLayers,
            annotations = annotations.map {
                if (it.layerId == null) it.copy(layerId = defaultLayer) else it
            },
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("template", template.name)
        put("memo", memo)
        put("annotations", JSONArray().apply { annotations.forEach { put(it.toJson()) } })
        put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): NotePage {
            val annotationsJson = o.optJSONArray("annotations") ?: JSONArray()
            val annotations = ArrayList<Annotation>(annotationsJson.length())
            for (i in 0 until annotationsJson.length()) {
                annotationsJson.optJSONObject(i)?.let { Annotation.fromJson(it)?.let(annotations::add) }
            }
            val layersJson = o.optJSONArray("layers") ?: JSONArray()
            val layers = ArrayList<NoteLayer>(layersJson.length())
            for (i in 0 until layersJson.length()) {
                layersJson.optJSONObject(i)?.let { layers.add(NoteLayer.fromJson(it)) }
            }
            return NotePage(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                template = runCatching {
                    PaperTemplate.valueOf(o.optString("template"))
                }.getOrDefault(PaperTemplate.LINED),
                annotations = annotations,
                memo = o.optString("memo"),
                layers = layers.ifEmpty { listOf(NoteLayer()) },
            ).normalized()
        }
    }
}

/**
 * A local notebook. PDFs stay in the existing RecentDoc flow; a notebook is a
 * sequence of template pages with the same annotation primitives used by the
 * PDF viewer.
 */
data class Notebook(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "새 노트",
    val coverColor: Int = 0xFFD8E6F3.toInt(),
    val coverStyle: CoverStyle = CoverStyle.SOLID,
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val pages: List<NotePage> = listOf(NotePage()),
    val bookmarks: List<PageBookmark> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val trashed: Boolean = false,
) {
    val pageCount: Int get() = pages.size

    fun normalized(): Notebook {
        val safePages = pages.ifEmpty { listOf(NotePage()) }
        return copy(
            title = title.ifBlank { "새 노트" },
            pages = safePages.map { it.normalized() },
            bookmarks = bookmarks.filter { it.pageIndex in safePages.indices },
            tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("title", title)
        put("coverColor", coverColor)
        put("coverStyle", coverStyle.name)
        put("folder", folder)
        put("tags", JSONArray().apply { tags.forEach { put(it) } })
        put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
        put("bookmarks", JSONArray().apply { bookmarks.forEach { put(it.toJson()) } })
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("favorite", favorite)
        put("trashed", trashed)
    }

    companion object {
        fun fromJson(o: JSONObject): Notebook {
            val pagesJson = o.optJSONArray("pages") ?: JSONArray()
            val pages = ArrayList<NotePage>(pagesJson.length())
            for (i in 0 until pagesJson.length()) {
                pagesJson.optJSONObject(i)?.let { pages.add(NotePage.fromJson(it)) }
            }
            val tagsJson = o.optJSONArray("tags") ?: JSONArray()
            val tags = ArrayList<String>(tagsJson.length())
            for (i in 0 until tagsJson.length()) {
                tagsJson.optString(i).takeIf { it.isNotBlank() }?.let(tags::add)
            }
            val bookmarksJson = o.optJSONArray("bookmarks") ?: JSONArray()
            val bookmarks = ArrayList<PageBookmark>(bookmarksJson.length())
            for (i in 0 until bookmarksJson.length()) {
                bookmarksJson.optJSONObject(i)?.let { bookmarks.add(PageBookmark.fromJson(it)) }
            }
            return Notebook(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title").ifBlank { "새 노트" },
                coverColor = o.optInt("coverColor", 0xFFD8E6F3.toInt()),
                coverStyle = runCatching {
                    CoverStyle.valueOf(o.optString("coverStyle"))
                }.getOrDefault(CoverStyle.SOLID),
                folder = o.optString("folder"),
                tags = tags,
                pages = pages.ifEmpty { listOf(NotePage()) },
                bookmarks = bookmarks,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                favorite = o.optBoolean("favorite"),
                trashed = o.optBoolean("trashed"),
            ).normalized()
        }
    }
}

data class NotebookDraft(
    val title: String = "새 노트",
    val coverColor: Int = 0xFFD8E6F3.toInt(),
    val coverStyle: CoverStyle = CoverStyle.SOLID,
    val template: PaperTemplate = PaperTemplate.LINED,
    val folder: String = "",
    val tags: List<String> = emptyList(),
)
