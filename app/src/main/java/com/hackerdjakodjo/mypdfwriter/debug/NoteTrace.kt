package com.hackerdjakodjo.mypdfwriter.debug

import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded, in-memory trace used while debugging note-editor state changes.
 *
 * The logger is deliberately dependency-free so it can be exercised from JVM
 * tests. It never writes to disk or the network; the debug UI can copy/export
 * the current snapshot for a bug report.
 */
data class NoteTraceEntry(
    val sequence: Long,
    val timestampMs: Long,
    val thread: String,
    val tag: String,
    val event: String,
    val fields: Map<String, String>,
) {
    fun toLine(): String {
        val tail = fields.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        return listOf(
            "#$sequence",
            String.format(Locale.US, "%tT.%tL", timestampMs, timestampMs),
            "[$thread]",
            "$tag/$event",
            tail,
        ).filter { it.isNotBlank() }.joinToString(" ")
    }
}

object NoteTrace {
    const val MAX_ENTRIES = 800

    private val entries = ArrayDeque<NoteTraceEntry>()
    private val sequence = AtomicLong(0)

    @Volatile
    var enabled: Boolean = true

    @Synchronized
    fun event(tag: String, event: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val entry = NoteTraceEntry(
            sequence = sequence.incrementAndGet(),
            timestampMs = now,
            thread = Thread.currentThread().name,
            tag = tag,
            event = event,
            fields = fields.associate { (key, value) -> key to value.toString() },
        )
        entries.addLast(entry)
        while (entries.size > MAX_ENTRIES) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<NoteTraceEntry> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    fun export(): String = buildString {
        appendLine("# My PDF Writer note trace")
        appendLine("# entries=${entries.size}")
        entries.forEach { appendLine(it.toLine()) }
    }
}
