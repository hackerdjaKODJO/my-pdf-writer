package com.hackerdjakodjo.mypdfwriter.debug

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteTraceTest {

    @Before
    fun setUp() {
        NoteTrace.enabled = true
        NoteTrace.clear()
    }

    @After
    fun tearDown() {
        NoteTrace.clear()
        NoteTrace.enabled = true
    }

    @Test
    fun `keeps only the newest entries when capacity is exceeded`() {
        val overflow = 25
        repeat(NoteTrace.MAX_ENTRIES + overflow) { index ->
            NoteTrace.event("TEST", "bounded", "i" to index)
        }

        val snapshot = NoteTrace.snapshot()
        assertEquals(NoteTrace.MAX_ENTRIES, snapshot.size)
        assertEquals(overflow.toString(), snapshot.first().fields["i"])
        assertEquals((NoteTrace.MAX_ENTRIES + overflow - 1).toString(), snapshot.last().fields["i"])
        assertTrue(
            snapshot.zipWithNext().all { (first, second) -> second.sequence == first.sequence + 1 },
        )
    }

    @Test
    fun `records fields and exports readable lines`() {
        NoteTrace.event(
            "TEST",
            "fields",
            "answer" to 42,
            "flag" to true,
            "missing" to null,
        )

        val entry = NoteTrace.snapshot().single()
        assertEquals("TEST", entry.tag)
        assertEquals("fields", entry.event)
        assertEquals("42", entry.fields["answer"])
        assertEquals("true", entry.fields["flag"])
        assertEquals("null", entry.fields["missing"])
        assertTrue(entry.thread.isNotBlank())
        assertTrue(entry.timestampMs > 0L)

        val line = entry.toLine()
        assertTrue(line.contains("#${entry.sequence}"))
        assertTrue(line.contains("[${entry.thread}]"))
        assertTrue(line.contains("TEST/fields"))
        assertTrue(line.contains("answer=42"))
        assertTrue(line.contains("flag=true"))

        val exported = NoteTrace.export()
        assertTrue(exported.lineSequence().first() == "# My PDF Writer note trace")
        assertTrue(exported.contains("# entries=1"))
        assertTrue(exported.contains(line))
    }

    @Test
    fun `disabled trace drops events and reports an empty export`() {
        NoteTrace.enabled = false

        NoteTrace.event("TEST", "ignored", "value" to 1)

        assertTrue(NoteTrace.snapshot().isEmpty())
        val exported = NoteTrace.export()
        assertTrue(exported.contains("# entries=0"))

        NoteTrace.enabled = true
        NoteTrace.event("TEST", "kept")
        assertEquals(1, NoteTrace.snapshot().size)
    }

    @Test
    fun `clear removes entries without affecting future logging`() {
        NoteTrace.event("TEST", "before", "value" to 1)
        NoteTrace.event("TEST", "before", "value" to 2)
        assertEquals(2, NoteTrace.snapshot().size)

        NoteTrace.clear()

        assertTrue(NoteTrace.snapshot().isEmpty())
        assertTrue(NoteTrace.export().contains("# entries=0"))

        NoteTrace.event("TEST", "after")
        val entry = NoteTrace.snapshot().single()
        assertEquals("after", entry.event)
    }
}
