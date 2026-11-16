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

data class NotePage(
    val id: String = UUID.randomUUID().toString(),
    val template: PaperTemplate = PaperTemplate.LINED,
    val annotations: List<Annotation> = emptyList(),
    val memo: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("template", template.name)
        put("memo", memo)
        put("annotations", JSONArray().apply { annotations.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): NotePage {
            val annotationsJson = o.optJSONArray("annotations") ?: JSONArray()
            val annotations = ArrayList<Annotation>(annotationsJson.length())
            for (i in 0 until annotationsJson.length()) {
                annotationsJson.optJSONObject(i)?.let { Annotation.fromJson(it)?.let(annotations::add) }
            }
            return NotePage(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                template = runCatching {
                    PaperTemplate.valueOf(o.optString("template"))
                }.getOrDefault(PaperTemplate.LINED),
                annotations = annotations,
                memo = o.optString("memo"),
            )
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
            pages = safePages,
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
