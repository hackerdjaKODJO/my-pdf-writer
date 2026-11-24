package com.hackerdjakodjo.mypdfwriter.debug

import android.content.Context
import android.util.Log
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import java.io.File
import java.security.SecureRandom
import java.util.Locale

/**
 * Debug-only durable log facade.
 *
 * [install] is called once from `Application.onCreate`; after that [event]
 * writes to the on-disk [DevLogStore], mirrors the same logical event into
 * [NoteTrace] (so the existing in-app debug sheet stays useful) and emits a
 * logcat line. Release builds default to disabled; callers never need to
 * check the flag themselves.
 *
 * Only counts, kinds, opaque ids and timings may be logged — never PDF text,
 * file names, paths, URIs, annotation text, search queries or user data.
 */
object DevLog {
    const val TAG = "PdfWriterDev"
    const val MAX_MEMORY_ENTRIES = 1200
    const val MAX_LOG_FILES = 7
    const val MAX_FILE_BYTES = 1_000_000L

    // Debug-only; disabled in release avoids disk IO and eager field work.
    @Volatile
    var enabled: Boolean = BuildConfig.DEBUG

    private val random = SecureRandom()

    /** Eight random hex characters, stable for the lifetime of the process. */
    val sessionId: String = String.format(Locale.US, "%08x", random.nextInt())

    @Volatile
    private var store: DevLogStore? = null
    private var versionOverride: String = ""

    /** Installs the store under `filesDir/logs`. Idempotent; no-op if disabled. */
    @Synchronized
    fun install(context: Context) {
        if (!enabled || store != null) return
        val logsDir = File(context.applicationContext.filesDir, "logs")
        val newStore = DevLogStore(
            directory = logsDir,
            sessionId = sessionId,
            maxFiles = MAX_LOG_FILES,
            maxFileBytes = MAX_FILE_BYTES,
            maxMemoryEntries = MAX_MEMORY_ENTRIES,
        )
        newStore.appVersion = if (versionOverride.isEmpty()) {
            BuildConfig.VERSION_NAME
        } else {
            versionOverride
        }
        store = newStore
    }

    /** Records an event and mirrors it into [NoteTrace] and logcat. */
    fun event(component: String, event: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        val current = store ?: return
        val stringFields = LinkedHashMap<String, String>()
        fields.forEach { (key, value) -> stringFields[key] = value.toString() }
        val entry = try {
            current.record(component, event, stringFields)
        } catch (_: Exception) {
            null // record() is best effort already; never let tracing crash.
        }
        NoteTrace.event(component, event, *fields)
        try {
            Log.d(TAG, buildString {
                if (entry != null) append('#').append(entry.sequence).append(' ')
                append(component).append('/').append(event)
                stringFields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
            })
        } catch (_: Exception) {
            // android.util.Log is stubbed in local JVM unit tests and throws.
        }
    }

    /** Current in-memory entries (safe on an uninstalled logger). */
    fun snapshot(): List<DevLogEntry> = store?.snapshot() ?: emptyList()

    /** JSONL dump of current in-memory entries. */
    fun exportedJsonl(): String = store?.exportedJsonl() ?: ""

    /** Human-readable dump of current in-memory entries. */
    fun exportedText(): String = store?.exportedText() ?: ""

    /** Durable log files, newest first. */
    fun files(): List<File> = store?.files() ?: emptyList()

    /** Clears memory; with [includeFiles] also deletes files and the header. */
    fun clear(includeFiles: Boolean = false) {
        store?.clear(includeFiles)
    }

    /** Overrides the version written into the next `session_start` header. */
    fun setAppVersion(version: String) {
        versionOverride = version
        store?.appVersion = version
    }

    /** Replaces the backing store; used by JVM tests to avoid an Android Context. */
    internal fun setStoreForTesting(newStore: DevLogStore?) {
        store = newStore
    }
}
