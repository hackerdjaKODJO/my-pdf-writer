package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONArray

/** SharedPreferences-backed pen box. Keeps the user's saved pen presets locally. */
class PenBoxStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<PenPreset> {
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrBlank()) return defaultPenPresets()
        val parsed = runCatching {
            val array = JSONArray(raw)
            val result = ArrayList<PenPreset>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(PenPreset.fromJson(it)) }
            }
            result
        }.getOrDefault(emptyList())
        return parsed.ifEmpty { defaultPenPresets() }
    }

    fun save(presets: List<PenPreset>) {
        val safe = presets.ifEmpty { defaultPenPresets() }
        val array = JSONArray()
        safe.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY, array.toString()).commit()
        // A deleted preset must not leave a dangling favorite id behind. When the
        // key is absent we deliberately do not create it: first-run defaults apply.
        pruneFavoritesIfPresent(safe)
    }

    /** Raw favorites value, or null when the key was never written (first run). */
    fun favoritesRaw(): String? = prefs.getString(FavoritePens.KEY, null)

    /** Ordered favorite presets, pruned and capped. Missing key => first four pens. */
    fun loadFavorites(): List<PenPreset> = FavoritePens.fromRaw(favoritesRaw(), load())

    fun saveFavorites(ids: List<String>) {
        prefs.edit().putString(FavoritePens.KEY, FavoritePens.encode(ids)).commit()
    }

    fun toggleFavorite(presetId: String) {
        val presets = load()
        saveFavorites(FavoritePens.toggle(currentIds(presets), presetId, presets))
    }

    fun moveFavorite(from: Int, to: Int) {
        val presets = load()
        saveFavorites(FavoritePens.move(currentIds(presets), from, to))
    }

    fun isFavorite(presetId: String): Boolean = loadFavorites().any { it.id == presetId }

    private fun currentIds(presets: List<PenPreset>): List<String> {
        val ids = FavoritePens.decode(favoritesRaw()) ?: FavoritePens.defaultIds(presets)
        return FavoritePens.prune(ids, presets)
    }

    private fun pruneFavoritesIfPresent(presets: List<PenPreset>) {
        val ids = FavoritePens.decode(favoritesRaw()) ?: return
        val pruned = FavoritePens.prune(ids, presets)
        if (pruned != ids) saveFavorites(pruned)
    }

    internal companion object {
        const val PREFS = "pen_box"
        const val KEY = "presets_v1"
    }
}

/**
 * Sensible first-run pens. These are original presets for the four ink styles and
 * the highlighter; the fixed ids make reloading deterministic.
 */
fun defaultPenPresets(): List<PenPreset> = listOf(
    PenPreset(
        id = "pen-box-ballpoint",
        name = "볼펜",
        color = 0xFF1B1C19.toInt(),
        width = 0.004f,
        opacity = 1f,
        penStyle = PenStyle.BALLPOINT,
        tool = Tool.PEN,
    ),
    PenPreset(
        id = "pen-box-fountain",
        name = "만년필",
        color = 0xFF1F4E79.toInt(),
        width = 0.0055f,
        opacity = 1f,
        penStyle = PenStyle.FOUNTAIN,
        tool = Tool.PEN,
    ),
    PenPreset(
        id = "pen-box-brush",
        name = "붓",
        color = 0xFF3E2B23.toInt(),
        width = 0.012f,
        opacity = 0.95f,
        penStyle = PenStyle.BRUSH,
        tool = Tool.PEN,
    ),
    PenPreset(
        id = "pen-box-pencil",
        name = "연필",
        color = 0xFF4B4B4B.toInt(),
        width = 0.0035f,
        opacity = 0.85f,
        penStyle = PenStyle.PENCIL,
        tool = Tool.PEN,
    ),
    PenPreset(
        id = "pen-box-highlighter",
        name = "형광펜",
        color = 0xFFF2C94C.toInt(),
        width = 0.02f,
        opacity = 1f,
        penStyle = PenStyle.BALLPOINT,
        tool = Tool.HIGHLIGHT,
    ),
)
