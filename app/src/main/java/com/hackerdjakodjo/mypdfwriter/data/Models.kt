package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Normalized point inside a page's displayed area. (0,0) is top-left, (1,1) is
 * bottom-right.
 *
 * [pressure] is the stylus pressure sample at this point in 0..1; 1 is the
 * neutral value used by fingers, mice, legacy files and recognized shapes.
 * The constructor clamps it so no path can store NaN or an out-of-range value.
 * This is deliberately a regular class rather than a data class: Kotlin cannot
 * clamp a data-class primary-constructor value without exposing the raw one.
 */
class NormalizedPoint(
    val x: Float,
    val y: Float,
    pressure: Float = DEFAULT_INK_PRESSURE,
) {
    val pressure: Float = clampInkPressure(pressure)

    fun copy(
        x: Float = this.x,
        y: Float = this.y,
        pressure: Float = this.pressure,
    ): NormalizedPoint = NormalizedPoint(x, y, pressure)

    operator fun component1(): Float = x
    operator fun component2(): Float = y
    operator fun component3(): Float = pressure

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NormalizedPoint) return false
        return x == other.x && y == other.y && pressure == other.pressure
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + pressure.hashCode()
        return result
    }

    override fun toString(): String =
        "NormalizedPoint(x=$x, y=$y, pressure=$pressure)"
}

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
    /**
     * Opaque paper-like cover over selected source text plus replacement text.
     *
     * Visual overlay only: the source PDF bytes and its text content stream are
     * never rewritten. The stored geometry is an axis-aligned two-point rectangle
     * in display coordinates ([points]), [text] is the replacement and [fontSize]
     * is normalized to the page height. Flattened export rasterizes this overlay
     * into the page image; the original words remain in the PDF content stream.
     */
    COVER_TEXT,
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
     * Wall-clock time when this annotation was committed. For recorded strokes
     * this is the pointer-down time (commit time minus [durationMs]), used by
     * audio replay to reveal strokes in recording order. Legacy documents load
     * this as `0`, which replay treats as always visible.
     */
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Time spent drawing the stroke in milliseconds. Replay spreads the point
     * reveal across this window. Shapes, text and stamps use `0`, so they appear
     * whole once their [createdAt] is reached. Legacy documents default to `0`.
     */
    val durationMs: Long = 0L,
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
        // Opt-in parallel array: legacy files and uniform-pressure strokes keep
        // the exact old size because `prs` is omitted entirely when it adds no
        // information.
        if (points.any { it.pressure != DEFAULT_INK_PRESSURE }) {
            val pressures = JSONArray()
            points.forEach { pressures.put(it.pressure.toDouble()) }
            put("prs", pressures)
        }
        if (text.isNotEmpty()) put("tx", text)
        put("fs", fontSize.toDouble())
        put("tr", textRotation)
        put("ps", penStyle.name)
        layerId?.let { put("layer", it) }
        put("op", opacity.toDouble())
        if (stamp.isNotEmpty()) put("st", stamp)
        put("ct", createdAt)
        put("dur", durationMs)
    }

    companion object {
        fun fromJson(o: JSONObject): Annotation? {
            val type = runCatching { AnnotationType.valueOf(o.optString("t")) }.getOrNull() ?: return null
            val flat = o.optJSONArray("pts") ?: JSONArray()
            val pressures = o.optJSONArray("prs")
            val points = ArrayList<NormalizedPoint>(flat.length() / 2)
            var i = 0
            var pointIndex = 0
            while (i + 1 < flat.length()) {
                val pressure = if (pressures != null) {
                    clampInkPressure(
                        pressures.optDouble(pointIndex, DEFAULT_INK_PRESSURE.toDouble()).toFloat(),
                    )
                } else {
                    DEFAULT_INK_PRESSURE
                }
                points.add(
                    NormalizedPoint(
                        flat.optDouble(i).toFloat(),
                        flat.optDouble(i + 1).toFloat(),
                        pressure,
                    ),
                )
                i += 2
                pointIndex++
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
                durationMs = o.optLong("dur", 0L).coerceAtLeast(0L),
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

/**
 * Per-document display preferences persisted alongside annotations and notes.
 *
 * Legacy documents have no `display` object and load with these defaults, so
 * adding this field never changes the JSON of older files on read. Brightness is
 * a screen dimming factor in 0.4..1.0 (1.0 = no dimming); values outside that
 * range are clamped when read or set.
 */
data class DisplayPrefs(
    val nightMode: Boolean = false,
    val brightness: Float = DEFAULT_BRIGHTNESS,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("night", nightMode)
        put("bright", brightness.toDouble())
    }

    companion object {
        const val MIN_BRIGHTNESS = 0.4f
        const val MAX_BRIGHTNESS = 1f
        const val DEFAULT_BRIGHTNESS = 1f

        fun clampBrightness(value: Float): Float {
            if (!value.isFinite()) return DEFAULT_BRIGHTNESS
            return value.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS)
        }

        fun fromJson(o: JSONObject?): DisplayPrefs {
            if (o == null) return DisplayPrefs()
            return DisplayPrefs(
                nightMode = o.optBoolean("night", false),
                brightness = clampBrightness(
                    o.optDouble("bright", DEFAULT_BRIGHTNESS.toDouble()).toFloat(),
                ),
            )
        }
    }
}

data class DocState(
    val docId: String,
    val displayName: String,
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val notes: Map<Int, String>,
    val displayPrefs: DisplayPrefs = DisplayPrefs(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("name", displayName)
        put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
        put("annotations", JSONArray().apply { annotations.forEach { put(it.toJson()) } })
        put("notes", JSONObject().apply { notes.forEach { (k, v) -> put(k.toString(), v) } })
        // Omit the default so legacy/default documents keep the exact old JSON shape.
        if (displayPrefs != DisplayPrefs()) put("display", displayPrefs.toJson())
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
                displayPrefs = DisplayPrefs.fromJson(o.optJSONObject("display")),
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
