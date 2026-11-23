package com.hackerdjakodjo.mypdfwriter.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Functional (not just policy) verification of the offline PDF tool engine.
 * These use PDFBox directly and need no Android device, Bitmap or Context.
 */
class PdfToolsFunctionalTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun writePdf(
        name: String = "src-${System.nanoTime()}.pdf",
        pages: Int = 3,
        boxes: List<PDRectangle> = List(pages) { PDRectangle.A4 },
    ): File {
        val file = temp.newFile(name)
        PDDocument().use { doc ->
            boxes.forEach { box -> doc.addPage(PDPage(box)) }
            doc.save(file)
        }
        return file
    }

    private fun bytesToTemp(bytes: ByteArray, name: String): File =
        temp.newFile(name).also { it.writeBytes(bytes) }

    private fun <T> io(block: suspend () -> T): T = runBlocking { block() }

    private fun pageWidths(file: File): List<Float> = PDDocument.load(file).use { doc ->
        (0 until doc.numberOfPages).map { doc.getPage(it).mediaBox.width }
    }

    @Test
    fun pageCountReportsTheRealNumberOfPages() {
        assertEquals(3, io { PdfTools.pageCount(writePdf(pages = 3)) })
        assertEquals(1, io { PdfTools.pageCount(writePdf(pages = 1)) })
    }

    @Test
    fun mergeConcatenatesBothDocumentsInOrder() {
        val a = writePdf("a.pdf", 2, List(2) { PDRectangle(200f, 400f) })
        val b = writePdf("b.pdf", 3, List(3) { PDRectangle(300f, 500f) })
        val out = ByteArrayOutputStream()
        io { PdfTools.merge(listOf(a, b), out) }
        val merged = bytesToTemp(out.toByteArray(), "merged.pdf")
        assertEquals(5, io { PdfTools.pageCount(merged) })
        assertEquals(listOf(200f, 200f, 300f, 300f, 300f), pageWidths(merged))
    }

    @Test
    fun extractKeepsRequestedOrderAndRejectsBadIndices() {
        val src = writePdf("five.pdf", 5, (1..5).map { PDRectangle(100f * it, 200f) })
        val out = ByteArrayOutputStream()
        io { PdfTools.extract(src, listOf(4, 1, 2), out) }
        val extracted = bytesToTemp(out.toByteArray(), "extracted.pdf")
        assertEquals(3, io { PdfTools.pageCount(extracted) })
        assertEquals(listOf(500f, 200f, 300f), pageWidths(extracted))

        val error = assertThrows(IllegalArgumentException::class.java) {
            io { PdfTools.extract(src, listOf(5), ByteArrayOutputStream()) }
        }
        assertTrue(error.message!!.contains("6"))
    }

    @Test
    fun splitEveryNProducesAllPagesInChunks() {
        val src = writePdf("ten.pdf", 10)
        val dir = temp.newFolder("split")
        val chunks = PdfRange.chunks(10, 3)
        val files = io { PdfTools.split(src, chunks, dir, "part") }
        assertEquals(listOf("part-01.pdf", "part-02.pdf", "part-03.pdf", "part-04.pdf"),
            files.map { it.name })
        assertEquals(listOf(3, 3, 3, 1), files.map { io { PdfTools.pageCount(it) } })
        assertEquals(10, files.sumOf { io { PdfTools.pageCount(it) } })
    }

    @Test
    fun metadataSetAndReadRoundTrip() {
        val src = writePdf()
        val meta = PdfMetadata(
            title = "강의 노트",
            author = "학생",
            subject = "시험",
            keywords = "요약",
            creator = "My PDF Writer",
            producer = "test",
        )
        val out = ByteArrayOutputStream()
        io { PdfTools.setMetadata(src, meta, out) }
        val saved = bytesToTemp(out.toByteArray(), "meta.pdf")
        val read = io { PdfTools.metadata(saved) }
        assertEquals(meta.title, read.title)
        assertEquals(meta.author, read.author)
        assertEquals(meta.subject, read.subject)
        assertEquals(meta.keywords, read.keywords)
        assertEquals(meta.creator, read.creator)
        assertEquals(3, read.pageCount)
        assertFalse(read.encrypted)
    }

    @Test
    fun protectAndUnlockRoundTripWithUserPassword() {
        val src = writePdf("secure.pdf", 2)
        val protectedBytes = ByteArrayOutputStream()
        io { PdfTools.protect(src, "user123", "owner456", out = protectedBytes) }
        val protected = bytesToTemp(protectedBytes.toByteArray(), "protected.pdf")
        assertTrue(io { PdfTools.isEncrypted(protected) })
        assertEquals("암호가 필요합니다",
            assertThrows(IllegalArgumentException::class.java) {
                io { PdfTools.pageCount(protected) }
            }.message)
        assertEquals(2, io { PdfTools.pageCount(protected, "user123") })

        val plainBytes = ByteArrayOutputStream()
        io { PdfTools.unlock(protected, "user123", plainBytes) }
        val unlocked = bytesToTemp(plainBytes.toByteArray(), "unlocked.pdf")
        assertEquals(2, io { PdfTools.pageCount(unlocked) })
        assertFalse(io { PdfTools.isEncrypted(unlocked) })
    }

    @Test
    fun optimizeKeepsPagesAndRemovesMetadata() {
        val src = temp.newFile("opt-src.pdf")
        PDDocument().use { doc ->
            repeat(3) { doc.addPage(PDPage(PDRectangle.A4)) }
            doc.documentInformation.title = "secret"
            doc.save(src)
        }
        val out = ByteArrayOutputStream()
        io { PdfTools.optimize(src, out) }
        val result = bytesToTemp(out.toByteArray(), "optimized.pdf")
        assertEquals(3, io { PdfTools.pageCount(result) })
        assertEquals("", io { PdfTools.metadata(result) }.title)
        assertFalse(io { PdfTools.isEncrypted(result) })
    }

    @Test
    fun rangeParsingAndPreviewAgreeWithExtraction() {
        val spec = "0,2-99,8-"
        val pages = PdfRange.parse(spec, 5)
        assertEquals(listOf(0, 1, 2, 3, 4), pages)
        assertTrue(PdfRange.describe(spec, 5).clamped)

        val src = writePdf("range.pdf", 5)
        val out = ByteArrayOutputStream()
        io { PdfTools.extract(src, pages, out) }
        assertEquals(5, io { PdfTools.pageCount(bytesToTemp(out.toByteArray(), "range-out.pdf")) })
    }
}
