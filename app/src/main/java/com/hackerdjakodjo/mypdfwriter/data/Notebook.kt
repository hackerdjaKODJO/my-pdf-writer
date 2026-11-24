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

/** Physical paper orientation for one notebook page. Legacy JSON is portrait. */
enum class PageOrientation(val label: String) {
    PORTRAIT("세로"),
    LANDSCAPE("가로"),
}

/**
 * Small, original paper palette. The colour is a fill tint; line work is derived
 * by [com.hackerdjakodjo.mypdfwriter.ui.PaperBackground] so every tint stays legible.
 */
enum class PaperTint(val label: String, val paperColor: Int) {
    WARM("따뜻한 종이", 0xFFFCF8ED.toInt()),
    WHITE("흰 종이", 0xFFFFFFFF.toInt()),
    PALE_YELLOW("연노랑", 0xFFFDF6D8.toInt()),
    LIGHT_GRAY("연회색", 0xFFF2F4F3.toInt()),
}

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
    val tool: Tool = Tool.PEN,
    /** Null keeps legacy presets inheriting the current pressure response. */
    val pressureCurve: PressureCurve? = null,
    /** Null keeps legacy presets inheriting the current pressure switch. */
    val pressureInk: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("width", width.toDouble())
        put("opacity", opacity.toDouble())
        put("style", penStyle.name)
        put("tool", tool.name)
        pressureCurve?.let { put("pc", it.name) }
        pressureInk?.let { put("pi", it) }
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
            tool = runCatching {
                Tool.valueOf(o.optString("tool"))
            }.getOrDefault(Tool.PEN),
            pressureCurve = if (o.has("pc")) {
                runCatching { PressureCurve.valueOf(o.optString("pc")) }.getOrNull()
            } else {
                null
            },
            pressureInk = if (o.has("pi")) o.optBoolean("pi") else null,
        )
    }
}

data class NotePage(
    val id: String = UUID.randomUUID().toString(),
    val template: PaperTemplate = PaperTemplate.LINED,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val tint: PaperTint = PaperTint.WARM,
    /**
     * Portable file name below the app-private `files/templates/` directory, or
     * null/blank for a normal paper page. Never an absolute path or content URI.
     */
    val customBackground: String? = null,
    /** User-visible label for [customBackground]; ignored when the key is absent. */
    val customBackgroundName: String = "",
    val annotations: List<Annotation> = emptyList(),
    val memo: String = "",
    val layers: List<NoteLayer> = listOf(NoteLayer()),
    /**
     * Optional offline masking-tape study cards placed on this page. Legacy JSON
     * without the key loads as an empty list, so old notebooks are unchanged.
     */
    val maskingCards: List<MaskingCard> = emptyList(),
    /**
     * Optional offline interactive checklists placed on this page. Legacy JSON
     * without the key loads as an empty list, so old notebooks are unchanged.
     */
    val checklists: List<ChecklistCard> = emptyList(),
    /**
     * Optional offline post-it cards placed on this page. Legacy JSON without the
     * key loads as an empty list, so older notebooks are unchanged.
     */
    val memoCards: List<MemoCard> = emptyList(),
    /** Zero-based page in the linked source PDF, or null for a plain notebook page. */
    val sourcePageIndex: Int? = null,
) {
    fun normalized(): NotePage {
        val safeLayers = layers.ifEmpty { listOf(NoteLayer()) }
        val defaultLayer = safeLayers.first().id
        val safeBackground = CustomTemplatePaths.sanitizeKey(customBackground)
        return copy(
            sourcePageIndex = sourcePageIndex?.takeIf { it >= 0 },
            customBackground = safeBackground,
            customBackgroundName = if (safeBackground == null) {
                ""
            } else {
                CustomTemplatePaths.normalizeName(customBackgroundName)
            },
            layers = safeLayers,
            maskingCards = maskingCards.map { it.normalized() },
            checklists = checklists.map { it.normalized() },
            memoCards = memoCards.map { it.normalized() },
            annotations = annotations.map {
                if (it.layerId == null) it.copy(layerId = defaultLayer) else it
            },
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("template", template.name)
        put("orientation", orientation.name)
        put("tint", tint.name)
        customBackground?.let {
            put("customBackground", it)
            put("customBackgroundName", customBackgroundName)
        }
        put("memo", memo)
        put("annotations", JSONArray().apply { annotations.forEach { put(it.toJson()) } })
        sourcePageIndex?.let { put("sourcePage", it) }
        put("layers", JSONArray().apply { layers.forEach { put(it.toJson()) } })
        if (maskingCards.isNotEmpty()) {
            put("maskingCards", JSONArray().apply { maskingCards.forEach { put(it.toJson()) } })
        }
        if (checklists.isNotEmpty()) {
            put("checklists", JSONArray().apply { checklists.forEach { put(it.toJson()) } })
        }
        if (memoCards.isNotEmpty()) {
            put("memoCards", JSONArray().apply { memoCards.forEach { put(it.toJson()) } })
        }
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
            val maskingJson = o.optJSONArray("maskingCards") ?: JSONArray()
            val maskingCards = ArrayList<MaskingCard>(maskingJson.length())
            for (i in 0 until maskingJson.length()) {
                maskingJson.optJSONObject(i)?.let { maskingCards.add(MaskingCard.fromJson(it)) }
            }
            val checklistsJson = o.optJSONArray("checklists") ?: JSONArray()
            val checklists = ArrayList<ChecklistCard>(checklistsJson.length())
            for (i in 0 until checklistsJson.length()) {
                checklistsJson.optJSONObject(i)?.let { checklists.add(ChecklistCard.fromJson(it)) }
            }
            val memosJson = o.optJSONArray("memoCards") ?: JSONArray()
            val memoCards = ArrayList<MemoCard>(memosJson.length())
            for (i in 0 until memosJson.length()) {
                memosJson.optJSONObject(i)?.let { memoCards.add(MemoCard.fromJson(it)) }
            }
            return NotePage(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                template = runCatching {
                    PaperTemplate.valueOf(o.optString("template"))
                }.getOrDefault(PaperTemplate.LINED),
                orientation = runCatching {
                    PageOrientation.valueOf(o.optString("orientation"))
                }.getOrDefault(PageOrientation.PORTRAIT),
                tint = runCatching {
                    PaperTint.valueOf(o.optString("tint"))
                }.getOrDefault(PaperTint.WARM),
                customBackground = CustomTemplatePaths.sanitizeKey(
                    o.optString("customBackground"),
                ),
                customBackgroundName = o.optString("customBackgroundName"),
                annotations = annotations,
                memo = o.optString("memo"),
                maskingCards = maskingCards,
                checklists = checklists,
                memoCards = memoCards,
                sourcePageIndex = o.optInt("sourcePage", -1).takeIf { it >= 0 },
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
    /** Display name of the PDF this study notebook was built from, or blank. */
    val sourceDocumentName: String = "",
    /** Best-effort local URI of the source PDF; never required to open the notebook. */
    val sourceDocumentUri: String = "",
    /** Last page the student was writing, so reopening resumes in place. */
    val lastPageIndex: Int = 0,
) {
    val pageCount: Int get() = pages.size

    /** Clamped resume target; legacy notebooks with no saved page open at 0. */
    fun resumePageIndex(): Int = lastPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    fun normalized(): Notebook {
        val safePages = pages.ifEmpty { listOf(NotePage()) }
        return copy(
            title = title.ifBlank { "새 노트" },
            pages = safePages.map { it.normalized() },
            bookmarks = bookmarks.filter { it.pageIndex in safePages.indices },
            tags = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct(),
            sourceDocumentName = sourceDocumentName.trim(),
            sourceDocumentUri = sourceDocumentUri.trim(),
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
        put("sourceDocumentName", sourceDocumentName)
        put("sourceDocumentUri", sourceDocumentUri)
        put("lastPageIndex", lastPageIndex)
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
                sourceDocumentName = o.optString("sourceDocumentName"),
                sourceDocumentUri = o.optString("sourceDocumentUri"),
                lastPageIndex = o.optInt("lastPageIndex", 0),
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
