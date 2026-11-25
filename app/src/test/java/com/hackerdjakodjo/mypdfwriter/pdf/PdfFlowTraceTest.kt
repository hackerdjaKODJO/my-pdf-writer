package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.debug.DevLog
import com.hackerdjakodjo.mypdfwriter.debug.DevLogEntry
import com.hackerdjakodjo.mypdfwriter.debug.DevLogStore
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class PdfFlowTraceTest {

    private lateinit var dir: File
    private lateinit var log: DevLogStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("pdf-flow-trace-test").toFile()
        log = DevLogStore(
            directory = dir,
            sessionId = "flow-session",
            clock = { FIXED_NOW },
        )
        DevLog.setStoreForTesting(log)
        DevLog.enabled = true
        NoteTrace.enabled = true
        NoteTrace.clear()
    }

    @After
    fun tearDown() {
        DevLog.setStoreForTesting(null)
        DevLog.enabled = true
        NoteTrace.clear()
        dir.deleteRecursively()
    }

    private fun events(): List<DevLogEntry> = log.snapshot().drop(1)

    @Test
    fun `open and close helpers use exact event names and canonical fields`() {
        PdfFlowTrace.openStart("content", true)
        PdfFlowTrace.openDone("doc-42", 12, 345L)
        PdfFlowTrace.openFailed("corrupt")
        PdfFlowTrace.close("doc-42", 3)

        val entries = events()
        assertEquals(
            listOf("open_start", "open_done", "open_fail", "close"),
            entries.map { it.event },
        )
        assertTrue(entries.all { it.component == "PDF_FLOW" })
        assertEquals("PDF_FLOW", PdfFlowTrace.COMPONENT)
        assertEquals(
            mapOf("uriKind" to "content", "addToRecents" to "true"),
            entries[0].fields,
        )
        assertEquals(
            mapOf("docId" to "doc-42", "pageCount" to "12", "elapsedMs" to "345"),
            entries[1].fields,
        )
        assertEquals(mapOf("reason" to "corrupt"), entries[2].fields)
        assertEquals(
            mapOf("docId" to "doc-42", "annotationCount" to "3"),
            entries[3].fields,
        )
    }

    @Test
    fun `annotate and history helpers use exact fields including null ids`() {
        PdfFlowTrace.annotate("highlight", "a7", 2, 10)
        PdfFlowTrace.history("undo", "a7", 4)
        PdfFlowTrace.history("clear", null, 0)

        val entries = events()
        assertEquals(listOf("annotate", "history", "history"), entries.map { it.event })
        assertEquals(
            mapOf(
                "type" to "highlight",
                "annotationId" to "a7",
                "page" to "2",
                "total" to "10",
            ),
            entries[0].fields,
        )
        assertEquals(
            mapOf("action" to "undo", "annotationId" to "a7", "remaining" to "4"),
            entries[1].fields,
        )
        assertEquals(
            mapOf("action" to "clear", "annotationId" to "null", "remaining" to "0"),
            entries[2].fields,
        )
    }

    @Test
    fun `page edit search and export helpers use exact event names and fields`() {
        PdfFlowTrace.pageEdit("rotate", 8, 11L)
        PdfFlowTrace.search(4, 2)
        PdfFlowTrace.exportStart()
        PdfFlowTrace.exportDone(2048L, true)

        val entries = events()
        assertEquals(
            listOf("page_edit", "search", "export_start", "export_done"),
            entries.map { it.event },
        )
        assertEquals(
            mapOf("kind" to "rotate", "pageCount" to "8", "revision" to "11"),
            entries[0].fields,
        )
        assertEquals(
            mapOf("queryLength" to "4", "hitCount" to "2"),
            entries[1].fields,
        )
        assertEquals(emptyMap<String, String>(), entries[2].fields)
        assertEquals(
            mapOf("bytes" to "2048", "flattened" to "true"),
            entries[3].fields,
        )
    }

    @Test
    fun `save helpers use exact event names and privacy safe fields`() {
        PdfFlowTrace.saveStart()
        PdfFlowTrace.saveDone("doc-42")
        PdfFlowTrace.saveStart()
        PdfFlowTrace.saveFailed("doc-42", "IOException")

        val entries = events()
        assertEquals(
            listOf("save_start", "save_done", "save_start", "save_fail"),
            entries.map { it.event },
        )
        assertTrue(entries.all { it.component == PdfFlowTrace.COMPONENT })
        assertEquals(emptyMap<String, String>(), entries[0].fields)
        assertEquals(mapOf("docId" to "doc-42"), entries[1].fields)
        assertEquals(emptyMap<String, String>(), entries[2].fields)
        assertEquals(mapOf("docId" to "doc-42", "reason" to "IOException"), entries[3].fields)
    }

    @Test
    fun `save helpers mirror events and reduce exceptions to categories`() {
        val secret = "/private/student/lecture.pdf"
        val reason = PdfFlowTrace.saveFailureReason(IOException(secret))
        PdfFlowTrace.saveStart()
        PdfFlowTrace.saveDone("doc-1")
        PdfFlowTrace.saveFailed("doc-1", reason)

        val mirror = NoteTrace.snapshot()
        assertEquals(3, mirror.size)
        assertEquals(listOf("save_start", "save_done", "save_fail"), mirror.map { it.event })
        assertEquals(events().map { it.event }, mirror.map { it.event })
        assertTrue(mirror.all { it.tag == PdfFlowTrace.COMPONENT })
        assertEquals("IOException", reason)
        assertTrue(!log.exportedJsonl().contains(secret))
        assertEquals(mapOf("docId" to "doc-1", "reason" to "IOException"), events().last().fields)
    }

    @Test
    fun `every helper mirrors the same event into NoteTrace`() {
        PdfFlowTrace.openStart("file", false)
        PdfFlowTrace.openDone("doc-1", 1, 10L)
        PdfFlowTrace.openFailed("password")
        PdfFlowTrace.close("doc-1", 0)
        PdfFlowTrace.annotate("ink", "a1", 0, 1)
        PdfFlowTrace.history("redo", "a1", 1)
        PdfFlowTrace.pageEdit("crop", 1, 2L)
        PdfFlowTrace.search(0, 0)
        PdfFlowTrace.exportStart()
        PdfFlowTrace.exportDone(0L, false)

        val mirror = NoteTrace.snapshot()
        assertEquals(10, mirror.size)
        assertEquals(events().map { it.event }, mirror.map { it.event })
        assertTrue(mirror.all { it.tag == PdfFlowTrace.COMPONENT })
    }

    @Test
    fun `helpers never log user data paths or search text`() {
        PdfFlowTrace.openStart("content", true)
        PdfFlowTrace.openDone("d1", 3, 20L)
        PdfFlowTrace.annotate("note", "a2", 1, 3)
        PdfFlowTrace.search(7, 0)
        PdfFlowTrace.saveStart()
        PdfFlowTrace.saveDone("d1")
        PdfFlowTrace.saveFailed("d1", "IOException")

        val entries = events()
        val secret = "/private/student/lecture.pdf"
        val canonicalKeys = setOf(
            "uriKind", "addToRecents", "docId", "pageCount", "elapsedMs", "reason",
            "annotationCount", "type", "annotationId", "page", "total", "action",
            "remaining", "kind", "revision", "queryLength", "hitCount", "bytes",
            "flattened",
        )
        entries.forEach { entry ->
            assertTrue(entry.fields.keys.all { it in canonicalKeys })
            assertTrue(entry.fields.values.none { it.contains("/") || it.contains("://") })
        }
        assertTrue(log.exportedJsonl().lineSequence().all { it.startsWith("{") })
        assertTrue(!log.exportedJsonl().contains(secret))
    }

    private companion object {
        const val FIXED_NOW = 1_700_000_000_000L
    }
}
