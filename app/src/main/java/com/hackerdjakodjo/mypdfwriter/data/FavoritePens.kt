package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray

/**
 * Pure rules for the offline "frequently used pens" registry.
 *
 * Favorites are an ordered list of [PenPreset] ids stored in the very same
 * `pen_box` SharedPreferences file as the presets, so the existing local backup
 * allowlist covers them without any new file or permission. This object has no
 * Android dependency: the store only hands it the raw string and the live preset
 * list, which keeps first-run/empty/stale-id behaviour unit-testable on the JVM.
 */
object FavoritePens {

    /** Hard cap for phone-width favorite strips and the registry section. */
    const val MAX_SLOTS = 6

    /** First-run suggestion: the four classic pen styles (highlighter excluded). */
    internal const val DEFAULT_FAVORITE_COUNT = 4

    /** Stable key inside `pen_box.xml`; already covered by BackupArchive. */
    const val KEY = "favorites_v1"

    /** Ids suggested on first run, before the user ever writes the key. */
    fun defaultIds(presets: List<PenPreset>): List<String> =
        presets.take(DEFAULT_FAVORITE_COUNT).map { it.id }

    /**
     * Decode the stored id list. `null` means the key was never written (first
     * run); a present but invalid/empty value is an intentional clear and stays
     * an empty list.
     */
    fun decode(raw: String?): List<String>? {
        if (raw == null) return null
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }

    /** Encode at most [MAX_SLOTS] ids, preserving user order. */
    fun encode(ids: List<String>): String {
        val array = JSONArray()
        ids.take(MAX_SLOTS).forEach { array.put(it) }
        return array.toString()
    }

    /** Presets for [ids]: unresolved ids and duplicates are dropped, capped at six. */
    fun resolve(ids: List<String>, presets: List<PenPreset>): List<PenPreset> {
        val byId = presets.associateBy { it.id }
        return ids.distinct().mapNotNull { byId[it] }.take(MAX_SLOTS)
    }

    /** Ids that still point at a live preset, in their original order. */
    fun prune(ids: List<String>, presets: List<PenPreset>): List<String> =
        resolve(ids, presets).map { it.id }

    /**
     * Star toggle: remove an already-favorite id, otherwise append it if it is a
     * real preset and there is room. Full strips stay untouched (no silent evict).
     */
    fun toggle(ids: List<String>, presetId: String, presets: List<PenPreset>): List<String> {
        val current = prune(ids, presets).toMutableList()
        return when {
            presetId in current -> current - presetId
            presetId.isEmpty() || presets.none { it.id == presetId } -> current
            current.size >= MAX_SLOTS -> current
            else -> current + presetId
        }
    }

    /** Move one slot to another index; out-of-range moves are ignored. */
    fun move(ids: List<String>, from: Int, to: Int): List<String> {
        if (from !in ids.indices || to !in ids.indices || from == to) return ids
        return ids.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Effective favorites for a stored raw value. A missing key suggests the
     * first four presets; a present key (even empty) is honoured exactly.
     */
    fun fromRaw(raw: String?, presets: List<PenPreset>): List<PenPreset> {
        val ids = decode(raw) ?: defaultIds(presets)
        return resolve(ids, presets)
    }
}

/** True when two presets paint with identical ink settings. */
fun samePenStyle(a: PenPreset, b: PenPreset): Boolean =
    a.color == b.color &&
        a.width == b.width &&
        a.opacity == b.opacity &&
        a.penStyle == b.penStyle &&
        a.tool == b.tool
