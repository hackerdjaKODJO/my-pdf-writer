package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * One small JSON index plus the `.m4a` files for every recording owned by one
 * notebook or PDF document:
 *
 * ```
 * files/audio/<ownerId>/index.json
 * files/audio/<ownerId>/<noteId>.m4a
 * ```
 *
 * Everything stays in the app's private storage. The store only moves bytes; it
 * never uploads audio and does not request a network permission.
 *
 * Integration note: the manifest must declare `RECORD_AUDIO` separately before
 * [com.hackerdjakodjo.mypdfwriter.audio.AudioNoteController] can start a real
 * microphone recording.
 */
class AudioNoteStore(context: Context) {

    private val root = File(context.filesDir, "audio").apply { mkdirs() }

    /** Directory that holds one owner's index and audio files. */
    fun audioDirectory(ownerId: String): File =
        File(root, audioOwnerDirectoryName(ownerId))

    @Synchronized
    fun load(ownerId: String): List<AudioNote> {
        val dir = audioDirectory(ownerId)
        val index = File(dir, INDEX_NAME)
        val tmp = File(dir, TMP_INDEX_NAME)
        val notes = readIndex(index) ?: readIndex(tmp) ?: return emptyList()
        return notes
            .asSequence()
            .filter { it.filePath.isNotBlank() }
            .map { it.copy(ownerId = ownerId) }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    /**
     * Writes the whole owner index atomically: a temporary file is synced and
     * renamed over `index.json`, with a copy fallback for filesystems that do
     * not replace on rename.
     *
     * @return true when the index was written.
     */
    @Synchronized
    fun save(ownerId: String, notes: List<AudioNote>): Boolean {
        val dir = audioDirectory(ownerId).apply { mkdirs() }
        val clean = notes
            .asSequence()
            .filter { it.filePath.isNotBlank() }
            .map { it.normalized().copy(ownerId = ownerId) }
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .toList()

        val tmp = File(dir, TMP_INDEX_NAME)
        return runCatching {
            FileOutputStream(tmp).use { out ->
                out.write(AudioNote.listToJson(clean).toString().toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            val target = File(dir, INDEX_NAME)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrElse {
            tmp.delete()
            false
        }
    }

    /**
     * Inserts or replaces [note], then returns the new sorted list. Returns null
     * when the index could not be written.
     */
    @Synchronized
    fun upsert(ownerId: String, note: AudioNote): List<AudioNote>? {
        val clean = note.normalized().copy(ownerId = ownerId)
        val updated = (load(ownerId).filterNot { it.id == clean.id } + clean)
            .sortedByDescending { it.createdAt }
        return if (save(ownerId, updated)) updated else null
    }

    /**
     * Removes one note from the index. The audio file is deleted only after the
     * new index is durable, so a failed index write keeps both in place.
     */
    @Synchronized
    fun delete(ownerId: String, noteId: String, deleteAudio: Boolean = true): Boolean {
        val current = load(ownerId)
        val target = current.firstOrNull { it.id == noteId }
        val remaining = current.filterNot { it.id == noteId }
        if (!save(ownerId, remaining)) return false
        if (deleteAudio && target != null) {
            deleteAudioFile(ownerId, target.filePath)
        }
        return true
    }

    /**
     * Reserves a new recording path. The file itself is created by
     * [com.hackerdjakodjo.mypdfwriter.audio.AudioNoteController] when recording
     * starts; a name collision gets a numeric suffix.
     */
    fun createRecordingFile(
        ownerId: String,
        noteId: String = UUID.randomUUID().toString(),
    ): File {
        val dir = audioDirectory(ownerId).apply { mkdirs() }
        val base = audioFileName(noteId)
        var candidate = File(dir, base)
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(dir, "${base.removeSuffix(".m4a")}-$suffix.m4a")
            suffix++
        }
        return candidate
    }

    /**
     * Deletes a recording only if it is a direct file below this owner's audio
     * directory. This keeps a malformed or copied index from deleting arbitrary
     * files.
     */
    fun deleteAudioFile(ownerId: String, filePath: String): Boolean {
        if (filePath.isBlank()) return false
        return runCatching {
            val dir = audioDirectory(ownerId).apply { mkdirs() }.canonicalFile
            val file = File(filePath).canonicalFile
            if (file == dir || file.name == INDEX_NAME || file.name == TMP_INDEX_NAME) {
                return@runCatching false
            }
            if (file.parentFile != dir) return@runCatching false
            if (!file.exists()) return@runCatching true
            file.delete()
        }.getOrDefault(false)
    }

    private fun readIndex(file: File): List<AudioNote>? {
        if (!file.isFile) return null
        return runCatching {
            val text = file.readText().trim()
            val array = if (text.startsWith("{")) {
                JSONObject(text).optJSONArray("notes") ?: JSONArray()
            } else {
                JSONArray(text)
            }
            AudioNote.listFromJson(array)
        }.getOrNull()
    }

    private companion object {
        const val INDEX_NAME = "index.json"
        const val TMP_INDEX_NAME = "index.json.tmp"
    }
}

/**
 * Keeps an owner id safely inside one directory name while preserving ordinary
 * UUIDs unchanged. Slashes, NULs and other path syntax become `_`.
 */
internal fun audioOwnerDirectoryName(ownerId: String): String {
    val trimmed = ownerId.trim()
    val safe = buildString(trimmed.length.coerceAtMost(MAX_COMPONENT)) {
        for (ch in trimmed) {
            if (length >= MAX_COMPONENT) break
            append(
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') ch else '_',
            )
        }
    }
    return when {
        safe.isBlank() -> "default"
        safe == "." || safe == ".." -> "_$safe"
        else -> safe
    }
}

/** A stable, traversal-free `.m4a` name for one note id. */
internal fun audioFileName(noteId: String): String {
    val trimmed = noteId.trim()
    val safe = buildString(trimmed.length.coerceAtMost(MAX_COMPONENT)) {
        for (ch in trimmed) {
            if (length >= MAX_COMPONENT) break
            append(
                if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') ch else '_',
            )
        }
    }
    val base = safe.ifBlank { "recording" }.let {
        if (it == "." || it == "..") "_$it" else it
    }
    return "$base.m4a"
}

private const val MAX_COMPONENT = 96
