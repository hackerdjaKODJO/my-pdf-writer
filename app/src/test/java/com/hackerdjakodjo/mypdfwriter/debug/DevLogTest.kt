package com.hackerdjakodjo.mypdfwriter.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset

class DevLogTest {

    private lateinit var dir: File
    private var now: Long = FIXED_NOW

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("devlog-store-test").toFile()
        now = FIXED_NOW
        NoteTrace.enabled = true
        NoteTrace.clear()
        DevLog.enabled = true
    }

    @After
    fun tearDown() {
        DevLog.setStoreForTesting(null)
        DevLog.enabled = true
        NoteTrace.clear()
        dir.deleteRecursively()
    }

    private fun store(
        maxFiles: Int = 7,
        maxFileBytes: Long = 1_000_000L,
        maxMemoryEntries: Int = 1200,
        sessionId: String = "sess-1",
    ): DevLogStore = DevLogStore(
        directory = dir,
        sessionId = sessionId,
        maxFiles = maxFiles,
        maxFileBytes = maxFileBytes,
        maxMemoryEntries = maxMemoryEntries,
        clock = { now },
    )

    private fun dayKey(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).toLocalDate().toString()

    @Test
    fun `json lines escape special characters and remain valid objects`() {
        val log = store(sessionId = "s\"ess")
        val entry = log.record(
            "C/1",
            "ev\tname",
            linkedMapOf(
                "quote" to "a\"b",
                "slash" to "c\\d",
                "line" to "x\ny",
                "tab" to "p\tq",
                "ctrl" to "\u0001",
                "unicode" to "한글",
            ),
        )

        val line = entry.toJsonLine()
        assertTrue(line.startsWith("{"))
        assertTrue(line.endsWith("}"))
        assertTrue(line.contains("\\\""))
        assertTrue(line.contains("\\\\"))
        assertTrue(line.contains("\\n"))
        assertTrue(line.contains("\\t"))
        assertTrue(line.contains("\\u0001"))
        assertTrue(line.contains("\"seq\":1"))
        assertTrue(line.contains("\"ts\":$FIXED_NOW"))
        assertTrue(line.contains("\"iso\":\""))
        assertTrue(line.contains("\"thread\":\""))
        assertTrue(line.contains("\"session\":\""))
        assertTrue(line.contains("\"component\":\""))
        assertTrue(line.contains("\"event\":\""))
        assertTrue(line.contains("\"fields\":{"))
        assertJsonObjectShape(line)
        assertEquals(line, log.exportedJsonl().lineSequence().last())
    }

    @Test
    fun `session_start is emitted once per store and again after clear with files`() {
        val log = store()
        log.record("C", "a")
        log.record("C", "b")

        val snapshot = log.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("session_start", snapshot[0].event)
        assertEquals("DEVLOG", snapshot[0].component)
        assertEquals("sess-1", snapshot[0].fields["session"])
        assertFalse(snapshot[0].fields.containsKey("appVersion"))
        assertEquals(1, snapshot.count { it.event == "session_start" })
        val file = File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl")
        assertEquals(1, file.readLines().count { it.contains("\"event\":\"session_start\"") })

        log.appVersion = "1.2.3"
        log.clear(true)
        assertTrue(log.files().isEmpty())
        assertTrue(log.snapshot().isEmpty())

        log.record("C", "c")
        val after = log.snapshot()
        assertEquals(2, after.size)
        assertEquals("session_start", after[0].event)
        assertEquals("1.2.3", after[0].fields["appVersion"])
    }

    @Test
    fun `record sequences are monotonic and the first event is one`() {
        val log = store()
        repeat(5) { index ->
            assertEquals((index + 1).toLong(), log.record("C", "e$index").sequence)
        }
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L), log.snapshot().map { it.sequence })
    }

    @Test
    fun `memory ring drops oldest entries but preserves insertion order`() {
        val log = store(maxMemoryEntries = 3)
        repeat(5) { index ->
            log.record("C", "e$index", mapOf("i" to index.toString()))
        }

        val snapshot = log.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("2", snapshot[0].fields["i"])
        assertEquals("4", snapshot[2].fields["i"])
        assertTrue(snapshot.zipWithNext().all { (first, second) -> second.sequence == first.sequence + 1 })
        assertEquals(3, log.exportedJsonl().lineSequence().count())
    }

    @Test
    fun `each UTC day gets its own file ordered newest first`() {
        val log = store()
        now = FIXED_NOW
        log.record("C", "day1")
        val firstDay = dayKey(now)
        now = FIXED_NOW + 86_400_000L
        log.record("C", "day2")
        val secondDay = dayKey(now)

        val files = log.files()
        assertEquals(2, files.size)
        assertEquals("devlog-$secondDay.jsonl", files[0].name)
        assertEquals("devlog-$firstDay.jsonl", files[1].name)
        assertEquals("devlog-$secondDay.jsonl", log.activeFile()?.name)
        assertTrue(files[1].readText().contains("\"event\":\"day1\""))
    }

    @Test
    fun `byte cap moves later writes to a part file`() {
        val log = store(maxFileBytes = 1)
        log.record("C", "one")
        log.record("C", "two")

        val base = File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl")
        val part2 = File(dir, "devlog-${dayKey(FIXED_NOW)}-part2.jsonl")
        assertTrue(base.exists())
        assertTrue(part2.exists())
        assertTrue(part2.readText().contains("\"event\":\"two\""))
        assertFalse(part2.readText().contains("\"event\":\"one\""))
        assertEquals(part2.name, log.activeFile()?.name)
    }

    @Test
    fun `file count cap deletes oldest part files`() {
        val log = store(maxFiles = 2, maxFileBytes = 1)
        repeat(4) { index -> log.record("C", "e$index") }

        val files = log.files()
        assertEquals(2, files.size)
        assertTrue(files[0].name.contains("-part4"))
        assertTrue(files[1].name.contains("-part3"))
        assertFalse(File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl").exists())
    }

    @Test
    fun `file count cap deletes the oldest day`() {
        val log = store(maxFiles = 2)
        repeat(3) { index ->
            now = FIXED_NOW + index * 86_400_000L
            log.record("C", "e$index")
        }

        val files = log.files()
        assertEquals(2, files.size)
        assertEquals("devlog-${dayKey(now)}.jsonl", files[0].name)
        assertFalse(File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl").exists())
    }

    @Test
    fun `clear without files keeps the header flag while clear with files resets it`() {
        val log = store()
        log.record("C", "a")
        log.record("C", "b")
        assertEquals(3, log.snapshot().size)

        log.clear(false)
        assertTrue(log.snapshot().isEmpty())
        assertEquals(1, log.files().size)
        log.record("C", "c")
        val afterSoftClear = log.snapshot()
        assertEquals(1, afterSoftClear.size)
        assertEquals("c", afterSoftClear[0].event)
        assertEquals(
            1,
            File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl")
                .readLines()
                .count { it.contains("session_start") },
        )

        log.clear(true)
        assertTrue(log.files().isEmpty())
        log.record("C", "d")
        val afterHardClear = log.snapshot()
        assertEquals(2, afterHardClear.size)
        assertEquals("session_start", afterHardClear[0].event)
        assertEquals("d", afterHardClear[1].event)
    }

    @Test
    fun `text export uses the canonical format and preserves field order`() {
        val log = store(sessionId = "sess-xyz")
        val entry = log.record(
            "CMP",
            "evt",
            linkedMapOf("a" to "1", "b" to "two words"),
        )

        assertTrue(
            entry.toJsonLine().contains("\"fields\":{\"a\":\"1\",\"b\":\"two words\"}"),
        )
        val text = entry.toTextLine()
        assertTrue(text.startsWith("#1 "))
        assertTrue(text.contains("[${entry.thread}]"))
        assertTrue(text.contains("sess-xyz/CMP/evt"))
        assertTrue(text.endsWith("a=1 b=two words"))

        val exported = log.exportedText()
        assertTrue(
            exported.startsWith(
                "# My PDF Writer dev log · session=sess-xyz · entries=2\n",
            ),
        )
        assertTrue(exported.contains(text))
        assertTrue(log.exportedJsonl().contains(entry.toJsonLine()))
    }

    @Test
    fun `a deleted directory is recreated on the next record`() {
        val log = store()
        log.record("C", "before")
        assertTrue(dir.deleteRecursively())
        assertFalse(dir.exists())

        log.record("C", "after")
        assertTrue(dir.exists())
        assertTrue(File(dir, "devlog-${dayKey(FIXED_NOW)}.jsonl").exists())
        assertEquals(3, log.snapshot().size)
    }

    @Test
    fun `io failures never throw and the memory entry is kept`() {
        val log = store()
        assertTrue(dir.deleteRecursively())
        File(dir.path).writeText("not a directory")

        val entry = log.record("C", "survives")
        assertEquals(1L, entry.sequence)
        assertEquals(2, log.snapshot().size)
        assertTrue(log.files().isEmpty())
    }

    @Test
    fun `disabled DevLog is a no-op and enabled DevLog mirrors into NoteTrace`() {
        val log = store()
        DevLog.setStoreForTesting(log)

        DevLog.enabled = false
        DevLog.event("C", "ignored", "k" to "v")
        assertTrue(log.snapshot().isEmpty())
        assertTrue(log.exportedJsonl().isEmpty())
        assertTrue(NoteTrace.snapshot().isEmpty())

        DevLog.enabled = true
        DevLog.event("C", "kept", "k" to "v")
        assertEquals(2, log.snapshot().size)
        assertEquals("kept", log.snapshot().last().event)
        assertEquals("v", log.snapshot().last().fields["k"])
        assertEquals(1, NoteTrace.snapshot().size)
        assertEquals("kept", NoteTrace.snapshot().single().event)
    }

    private fun assertJsonObjectShape(line: String) {
        var depth = 0
        var inString = false
        var escaped = false
        for (ch in line) {
            if (inString) {
                if (escaped) {
                    escaped = false
                } else {
                    when (ch) {
                        '\\' -> escaped = true
                        '"' -> inString = false
                        else -> assertTrue("raw control character in JSON", ch.code >= 0x20)
                    }
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                }
            }
        }
        assertFalse(inString)
        assertEquals(0, depth)
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
