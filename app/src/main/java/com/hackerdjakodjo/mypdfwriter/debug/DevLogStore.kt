package com.hackerdjakodjo.mypdfwriter.debug

import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicLong

private val DEVLOG_UTC: TimeZone = TimeZone.getTimeZone("UTC")
private val DEVLOG_FILE_NAME = Regex("devlog-\\d{4}-\\d{2}-\\d{2}(?:-part\\d+)?\\.jsonl")
private const val MAX_PARTS = 10_000

/**
 * One durable log entry. Fields are always strings so the JSONL representation
 * stays trivial to parse; callers convert counts/booleans before recording.
 *
 * No PDF text, file names, paths, URIs or user data belong in [fields] — only
 * counts, kinds, opaque ids and timings.
 */
data class DevLogEntry(
    val sequence: Long,
    val timestampMs: Long,
    val thread: String,
    val sessionId: String,
    val component: String,
    val event: String,
    val fields: Map<String, String>,
) {
    /** A single valid JSON object on one line (JSONL). */
    fun toJsonLine(): String = buildString {
        append('{')
        append("\"seq\":").append(sequence)
        append(",\"ts\":").append(timestampMs)
        append(",\"iso\":").append(jsonString(isoUtc(timestampMs)))
        append(",\"thread\":").append(jsonString(thread))
        append(",\"session\":").append(jsonString(sessionId))
        append(",\"component\":").append(jsonString(component))
        append(",\"event\":").append(jsonString(event))
        append(",\"fields\":{")
        fields.entries.forEachIndexed { index, (key, value) ->
            if (index > 0) append(',')
            append(jsonString(key)).append(':').append(jsonString(value))
        }
        append("}}")
    }

    /** Human-readable single line for the debug dump. */
    fun toTextLine(): String = buildString {
        append('#').append(sequence).append(' ')
        append(isoUtc(timestampMs)).append(' ')
        append('[').append(thread).append(']').append(' ')
        append(sessionId).append('/').append(component).append('/').append(event)
        fields.entries.forEach { (key, value) ->
            append(' ').append(key).append('=').append(singleLine(value))
        }
    }
}

/**
 * Durable, structured development log that survives process restarts.
 *
 * The store is pure Kotlin: one JSONL file per UTC day under [directory], an
 * append/close per record (no long-lived handle), a bounded in-memory ring for
 * the in-app sheet, and byte/file-count caps for disk. All file IO is best
 * effort — a logging failure must never crash the app.
 *
 * Sequence numbers are per store instance: `record()` returns 1, 2, …; the
 * prepended `session_start` header uses sequence 0.
 */
class DevLogStore(
    private val directory: File,
    val sessionId: String,
    private val maxFiles: Int = 7,
    private val maxFileBytes: Long = 1_000_000L,
    private val maxMemoryEntries: Int = 1200,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Optional version included in the `session_start` header when non-blank. */
    var appVersion: String = ""

    private val sequence = AtomicLong(0)
    private val memory = ArrayDeque<DevLogEntry>()
    private var sessionHeaderWritten = false
    private var activeDay = ""
    private var activePart = 1

    /** Records one event, returning the entry that was added. Never throws. */
    @Synchronized
    fun record(
        component: String,
        event: String,
        fields: Map<String, String> = emptyMap(),
    ): DevLogEntry {
        val now = safeNow()
        val thread = Thread.currentThread().name
        val entry = DevLogEntry(
            sequence = sequence.incrementAndGet(),
            timestampMs = now,
            thread = thread,
            sessionId = sessionId,
            component = component,
            event = event,
            fields = LinkedHashMap(fields),
        )
        val lines = ArrayList<String>(2)
        if (!sessionHeaderWritten) {
            sessionHeaderWritten = true
            val headerFields = LinkedHashMap<String, String>()
            headerFields["session"] = sessionId
            if (appVersion.isNotEmpty()) headerFields["appVersion"] = appVersion
            val header = DevLogEntry(
                sequence = 0L,
                timestampMs = now,
                thread = thread,
                sessionId = sessionId,
                component = "DEVLOG",
                event = "session_start",
                fields = headerFields,
            )
            remember(header)
            lines.add(header.toJsonLine())
        }
        remember(entry)
        lines.add(entry.toJsonLine())
        appendLines(dayKey(now), lines)
        enforceFileCap()
        return entry
    }

    /** A copy of the in-memory ring in insertion order (header first). */
    @Synchronized
    fun snapshot(): List<DevLogEntry> = memory.toList()

    /** All in-memory entries as JSONL, joined by newlines. */
    @Synchronized
    fun exportedJsonl(): String = memory.joinToString("\n") { it.toJsonLine() }

    /** Human-readable dump with a session/entry-count header. */
    @Synchronized
    fun exportedText(): String = buildString {
        append("# My PDF Writer dev log · session=").append(sessionId)
        append(" · entries=").append(memory.size).append('\n')
        memory.forEach { append(it.toTextLine()).append('\n') }
    }

    /** Our log files, newest first (later UTC day, then higher part number). */
    @Synchronized
    fun files(): List<File> = try {
        (directory.listFiles()?.filter { it.isFile && DEVLOG_FILE_NAME.matches(it.name) }
            ?: emptyList())
            .sortedWith(
                compareBy<File> { fileKey(it).first }
                    .thenBy { fileKey(it).second }
                    .reversed(),
            )
    } catch (_: Exception) {
        emptyList()
    }

    /** The file for the current UTC day (or the active part, after rotation). */
    @Synchronized
    fun activeFile(): File? {
        val day = dayKey(safeNow())
        return if (activeDay == day) partFile(day, activePart) else partFile(day, 1)
    }

    /**
     * Clears memory. When [includeFiles] is true, also deletes every log file
     * and resets the session-header flag so the next record starts a new one.
     */
    @Synchronized
    fun clear(includeFiles: Boolean = false) {
        memory.clear()
        if (includeFiles) {
            sessionHeaderWritten = false
            activeDay = ""
            activePart = 1
            files().forEach { safeDelete(it) }
        }
    }

    private fun remember(entry: DevLogEntry) {
        memory.addLast(entry)
        while (memory.size > maxMemoryEntries) memory.removeFirst()
    }

    private fun safeNow(): Long = try {
        clock()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }

    private fun appendLines(day: String, lines: List<String>) {
        if (lines.isEmpty()) return
        try {
            val bytes = (lines.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
            val target = resolveTarget(day, bytes.size)
            directory.mkdirs()
            FileOutputStream(target, true).use { out ->
                out.write(bytes)
                out.flush()
            }
        } catch (_: Exception) {
            // Logging is best effort: swallow IO failures, keep the memory entry.
        }
    }

    private fun resolveTarget(day: String, incomingBytes: Int): File {
        var part = if (activeDay == day) activePart else 1
        while (true) {
            val candidate = partFile(day, part)
            if (!candidate.exists() ||
                candidate.length() + incomingBytes <= maxFileBytes ||
                part >= MAX_PARTS
            ) {
                activeDay = day
                activePart = part
                return candidate
            }
            part++
        }
    }

    private fun partFile(day: String, part: Int): File {
        val suffix = if (part <= 1) "" else "-part$part"
        return File(directory, "devlog-$day$suffix.jsonl")
    }

    private fun enforceFileCap() {
        val all = files()
        if (maxFiles <= 0) {
            all.forEach { safeDelete(it) }
        } else if (all.size > maxFiles) {
            all.drop(maxFiles).forEach { safeDelete(it) }
        }
    }

    private fun safeDelete(file: File) {
        try {
            file.delete()
        } catch (_: Exception) {
            // Best effort cleanup only.
        }
    }

    private fun fileKey(file: File): Pair<String, Int> {
        val stem = file.name.removeSuffix(".jsonl").removePrefix("devlog-")
        val day = stem.substringBefore("-part")
        val part = if (stem.contains("-part")) {
            stem.substringAfter("-part").toIntOrNull() ?: 1
        } else {
            1
        }
        return day to part
    }
}

private fun dayKey(epochMs: Long): String = isoUtc(epochMs).substringBefore('T')

private fun isoUtc(epochMs: Long): String {
    val cal = GregorianCalendar(DEVLOG_UTC)
    cal.timeInMillis = epochMs
    return String.format(
        Locale.US,
        "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ",
        cal.get(GregorianCalendar.YEAR),
        cal.get(GregorianCalendar.MONTH) + 1,
        cal.get(GregorianCalendar.DAY_OF_MONTH),
        cal.get(GregorianCalendar.HOUR_OF_DAY),
        cal.get(GregorianCalendar.MINUTE),
        cal.get(GregorianCalendar.SECOND),
        cal.get(GregorianCalendar.MILLISECOND),
    )
}

private fun singleLine(value: String): String =
    value.replace("\r", "\\r").replace("\n", "\\n")

private fun jsonString(value: String): String = buildString(value.length + 2) {
    append('"')
    for (ch in value) {
        when (ch.code) {
            0x22 -> append("\\\"")
            0x5C -> append("\\\\")
            0x08 -> append("\\b")
            0x0C -> append("\\f")
            0x0A -> append("\\n")
            0x0D -> append("\\r")
            0x09 -> append("\\t")
            else -> if (ch.code < 0x20) {
                append("\\u").append(ch.code.toString(16).padStart(4, '0'))
            } else {
                append(ch)
            }
        }
    }
    append('"')
}
