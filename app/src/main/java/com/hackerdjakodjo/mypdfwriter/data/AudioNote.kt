package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A timestamp inside one local audio note. Markers point back at the page that
 * was visible when the speaker bookmarked the recording.
 *
 * This first pass stores the marker only; replaying handwriting or strokes at
 * that timestamp is intentionally out of scope.
 */
data class AudioMarker(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val offsetMs: Long,
    val title: String = "",
) {
    fun normalized(): AudioMarker = copy(
        pageIndex = pageIndex.coerceAtLeast(0),
        offsetMs = offsetMs.coerceAtLeast(0L),
        title = title.trim(),
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("page", pageIndex)
        put("offsetMs", offsetMs)
        put("title", title)
    }

    companion object {
        fun fromJson(o: JSONObject): AudioMarker = AudioMarker(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            pageIndex = o.optInt("page", 0),
            offsetMs = o.optLong("offsetMs", 0L),
            title = o.optString("title"),
        ).normalized()
    }
}

/**
 * One microphone recording owned by a notebook or PDF document.
 *
 * [filePath] is an absolute path inside the app's private `files/audio/<ownerId>`
 * directory. Audio never leaves the device. [markers] are page bookmarks into
 * this recording, not separate audio files.
 */
/**
 * Replay treats [AudioNote.createdAt] as the recording START. A stopped note
 * only knows its end time, so backdate it by its duration; otherwise every
 * stroke is already fully revealed at playback position zero.
 */
internal fun audioNoteStartedAt(stoppedAt: Long, durationMs: Long): Long =
    stoppedAt - durationMs.coerceAtLeast(0L)

data class AudioNote(
    val id: String = UUID.randomUUID().toString(),
    val ownerId: String,
    val pageIndex: Int,
    val filePath: String,
    val durationMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val markers: List<AudioMarker> = emptyList(),
) {
    fun normalized(): AudioNote = copy(
        pageIndex = pageIndex.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0L),
        markers = markers.map { it.normalized() }.sortedBy { it.offsetMs },
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("version", 1)
        put("id", id)
        put("ownerId", ownerId)
        put("page", pageIndex)
        put("filePath", filePath)
        put("durationMs", durationMs)
        put("createdAt", createdAt)
        put("markers", JSONArray().apply { markers.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): AudioNote {
            val markersJson = o.optJSONArray("markers") ?: JSONArray()
            val markers = ArrayList<AudioMarker>(markersJson.length())
            for (i in 0 until markersJson.length()) {
                markersJson.optJSONObject(i)?.let { markers.add(AudioMarker.fromJson(it)) }
            }
            return AudioNote(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                ownerId = o.optString("ownerId"),
                pageIndex = o.optInt("page", 0),
                filePath = o.optString("filePath"),
                durationMs = o.optLong("durationMs", 0L),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                markers = markers,
            ).normalized()
        }

        fun listToJson(notes: List<AudioNote>): JSONArray = JSONArray().apply {
            notes.forEach { put(it.toJson()) }
        }

        fun listFromJson(array: JSONArray): List<AudioNote> {
            val result = ArrayList<AudioNote>(array.length())
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { result.add(fromJson(it)) }
            }
            return result
        }
    }
}
