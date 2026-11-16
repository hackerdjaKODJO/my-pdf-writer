package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Normalized point inside a page's displayed area. (0,0) is top-left, (1,1) is bottom-right. */
data class NormalizedPoint(val x: Float, val y: Float)

enum class AnnotationType {
    PEN,
    HIGHLIGHT,
    TEXT,
    RECT,
    ARROW,
    UNDERLINE,
    STRIKEOUT,
    ELLIPSE,
    LINE,
    STAMP,
    SIGNATURE,
    REDACT,
}

enum class PenStyle {
    BALLPOINT,
    FOUNTAIN,
    BRUSH,
    PENCIL,
}

enum class Tool {
    VIEW,
    PEN,
    HIGHLIGHT,
    TEXT,
    RECT,
    ARROW,
    UNDERLINE,
    STRIKEOUT,
    ELLIPSE,
    LINE,
    STAMP,
    SIGNATURE,
    REDACT,
    ERASER,
    LASSO,
    LASSO_RECT,
}

data class Annotation(
    val id: String = UUID.randomUUID().toString(),
    val type: AnnotationType,
    val page: Int,
    val color: Int,
    val width: Float,
    val points: List<NormalizedPoint> = emptyList(),
    val text: String = "",
    val fontSize: Float = 0.024f,
    val textRotation: Int = 0,
    val opacity: Float = 1f,
    val stamp: String = "",
    val penStyle: PenStyle = PenStyle.BALLPOINT,
    val layerId: String? = null,
    /**
     * Wall-clock time when this annotation was committed, used by audio replay
     * to reveal strokes in recording order. Legacy documents load this as `0`,
     * which replay treats as always visible.
     */
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("t", type.name)
        put("p", page)
        put("c", color)
        put("w", width.toDouble())
        val flat = JSONArray()
        points.forEach { flat.put(it.x.toDouble()); flat.put(it.y.toDouble()) }
        put("pts", flat)
        if (text.isNotEmpty()) put("tx", text)
        put("fs", fontSize.toDouble())
        put("tr", textRotation)
        put("ps", penStyle.name)
        layerId?.let { put("layer", it) }
        put("op", opacity.toDouble())
        if (stamp.isNotEmpty()) put("st", stamp)
        put("ct", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Annotation? {
            val type = runCatching { AnnotationType.valueOf(o.optString("t")) }.getOrNull() ?: return null
            val flat = o.optJSONArray("pts") ?: JSONArray()
            val points = ArrayList<NormalizedPoint>(flat.length() / 2)
            var i = 0
            while (i + 1 < flat.length()) {
                points.add(
                    NormalizedPoint(
                        flat.optDouble(i).toFloat(),
                        flat.optDouble(i + 1).toFloat(),
                    ),
                )
                i += 2
            }
            return Annotation(
                id = o.optString("id").ifEmpty { UUID.randomUUID().toString() },
                type = type,
                page = o.optInt("p"),
                color = o.optInt("c"),
                width = o.optDouble("w", 0.004).toFloat(),
                points = points,
                text = o.optString("tx"),
                fontSize = o.optDouble("fs", 0.024).toFloat(),
                textRotation = PageSpec.normalizeRotation(o.optInt("tr")),
                opacity = o.optDouble("op", 1.0).toFloat().coerceIn(0f, 1f),
                stamp = o.optString("st"),
                penStyle = runCatching {
                    PenStyle.valueOf(o.optString("ps"))
                }.getOrDefault(PenStyle.BALLPOINT),
                layerId = o.optString("layer").takeIf { it.isNotBlank() },
                // Older documents have no timestamp; keep them visible in replay.
                createdAt = o.optLong("ct", 0L),
            )
        }
    }
}

/**
 * One page of the working document.
 * [sourcePage] indexes the imported source PDF; null means a blank page.
 * [extraRotation] is clockwise rotation added on top of the source page's own /Rotate.
 */
data class PageSpec(
    val sourcePage: Int?,
    val extraRotation: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (sourcePage == null) put("src", JSONObject.NULL) else put("src", sourcePage)
        put("rot", normalizeRotation(extraRotation))
    }

    companion object {
        fun fromJson(o: JSONObject): PageSpec = PageSpec(
            sourcePage = if (o.isNull("src")) null else o.optInt("src"),
            extraRotation = normalizeRotation(o.optInt("rot")),
        )

        fun normalizeRotation(value: Int): Int = ((value % 360) + 360) % 360
    }
}

data class DocState(
    val docId: String,
    val displayName: String,
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val notes: Map<Int, String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("name", displayName)
        put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
        put("annotations", JSONArray().apply { annotations.forEach { put(it.toJson()) } })
        put("notes", JSONObject().apply { notes.forEach { (k, v) -> put(k.toString(), v) } })
    }

    /** Drops references that no longer exist after the source file changed. */
    fun normalized(sourcePageCount: Int): DocState {
        val valid = pages.mapIndexedNotNull { oldIndex, spec ->
            if (spec.sourcePage == null || spec.sourcePage in 0 until sourcePageCount) {
                oldIndex to spec
            } else {
                null
            }
        }
        if (valid.isEmpty()) {
            val fresh = List(sourcePageCount) { PageSpec(it) }.ifEmpty { listOf(PageSpec(null)) }
            return copy(pages = fresh, annotations = emptyList(), notes = emptyMap())
        }
        val indexMap = HashMap<Int, Int>(valid.size)
        valid.forEachIndexed { newIndex, (oldIndex, _) -> indexMap[oldIndex] = newIndex }
        val specs = valid.map { it.second }
        val annotations = annotations.mapNotNull { annotation ->
            val mapped = indexMap[annotation.page] ?: return@mapNotNull null
            annotation.copy(page = mapped)
        }
        val notes = buildMap {
            notes.forEach { (oldPage, value) -> indexMap[oldPage]?.let { put(it, value) } }
        }
        return copy(pages = specs, annotations = annotations, notes = notes)
    }

    companion object {
        fun fromJson(docId: String, o: JSONObject): DocState {
            val pagesJson = o.optJSONArray("pages") ?: JSONArray()
            val pages = ArrayList<PageSpec>(pagesJson.length())
            for (i in 0 until pagesJson.length()) {
                pagesJson.optJSONObject(i)?.let { pages.add(PageSpec.fromJson(it)) }
            }
            val annsJson = o.optJSONArray("annotations") ?: JSONArray()
            val anns = ArrayList<Annotation>(annsJson.length())
            for (i in 0 until annsJson.length()) {
                annsJson.optJSONObject(i)?.let { Annotation.fromJson(it)?.let(anns::add) }
            }
            val notesJson = o.optJSONObject("notes") ?: JSONObject()
            val notes = LinkedHashMap<Int, String>()
            notesJson.keys().forEach { key ->
                val idx = key.toIntOrNull() ?: return@forEach
                val value = notesJson.optString(key)
                if (value.isNotBlank()) notes[idx] = value
            }
            return DocState(
                docId = docId,
                displayName = o.optString("name"),
                pages = pages,
                annotations = anns,
                notes = notes,
            )
        }
    }
}

data class RecentDoc(
    val uri: String,
    val name: String,
    val pageCount: Int,
    val lastOpenedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("name", name)
        put("pages", pageCount)
        put("at", lastOpenedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): RecentDoc = RecentDoc(
            uri = o.optString("uri"),
            name = o.optString("name"),
            pageCount = o.optInt("pages"),
            lastOpenedAt = o.optLong("at"),
        )
    }
}

data class SearchHit(
    val page: Int,
    val count: Int,
    val snippet: String,
)

/** A rendered page plus its displayed size in PDF points. */
class RenderedPage(
    val bitmap: android.graphics.Bitmap,
    val widthPt: Float,
    val heightPt: Float,
)

/** Display size of a source page in points, after applying the page's own /Rotate. */
data class PageSize(val width: Float, val height: Float)
