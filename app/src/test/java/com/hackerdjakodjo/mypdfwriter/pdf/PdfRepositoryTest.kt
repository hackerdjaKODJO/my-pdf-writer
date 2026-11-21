package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.PageSize
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Pure-JVM coverage for the document-open helpers: identity page-layout detection,
 * single-load document info and the session source-cache reuse decision.
 */
class PdfRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---------------------------------------------------------- identity specs

    @Test
    fun emptyLayoutIsIdentity() {
        assertTrue(isIdentityPageSpecs(emptyList(), 0))
    }

    @Test
    fun sourceOrderWithNoEditsIsIdentity() {
        val pages = listOf(PageSpec(0), PageSpec(1), PageSpec(2))
        assertTrue(isIdentityPageSpecs(pages, 3))
    }

    @Test
    fun normalizedFullTurnIsIdentity() {
        val pages = listOf(PageSpec(0, 360), PageSpec(1, -720))
        assertTrue(isIdentityPageSpecs(pages, 2))
    }

    @Test
    fun pageCountMismatchIsNotIdentity() {
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0)), 2))
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0), PageSpec(1)), 1))
    }

    @Test
    fun reorderedOrDuplicatedSourceIsNotIdentity() {
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(1), PageSpec(0)), 2))
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0), PageSpec(0)), 2))
    }

    @Test
    fun blankPageIsNotIdentity() {
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0), PageSpec(null), PageSpec(2)), 3))
    }

    @Test
    fun extraRotationIsNotIdentity() {
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0), PageSpec(1, 90)), 2))
        assertFalse(isIdentityPageSpecs(listOf(PageSpec(0, 270)), 1))
    }

    // ------------------------------------------------------------ documentInfo

    @Test
    fun documentInfoReportsCountAndDisplaySizesFromOneLoad() {
        val file = temp.newFile("info-${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(200f, 400f)))
            doc.addPage(PDPage(PDRectangle(300f, 500f)))
            doc.addPage(
                PDPage(PDRectangle(400f, 600f)).also { it.rotation = 90 },
            )
            doc.save(file)
        }

        val info = readDocumentInfo(file)

        assertEquals(3, info.pageCount)
        assertEquals(3, info.pageSizes.size)
        assertEquals(PageSize(200f, 400f), info.pageSizes[0])
        assertEquals(PageSize(300f, 500f), info.pageSizes[1])
        // A 90-degree /Rotate swaps the displayed width and height.
        assertEquals(PageSize(600f, 400f), info.pageSizes[2])
    }

    @Test
    fun documentInfoOfEmptyDocumentHasNoSizes() {
        val file = temp.newFile("empty-${System.nanoTime()}.pdf")
        PDDocument().use { it.save(file) }

        val info = readDocumentInfo(file)

        assertEquals(0, info.pageCount)
        assertTrue(info.pageSizes.isEmpty())
    }

    // ------------------------------------------------------------ cache reuse

    @Test
    fun missingOrEmptyCachedSourceIsNotReusable() {
        val absent = File(temp.root, "absent.pdf")
        val empty = temp.newFile("empty-source-${System.nanoTime()}.pdf")

        assertNull(reusableCachedSource(null))
        assertNull(reusableCachedSource(absent))
        assertNull(reusableCachedSource(empty))
    }

    @Test
    fun existingNonEmptyCachedSourceIsReused() {
        val cached = temp.newFile("source-${System.nanoTime()}.pdf")
        cached.writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) // "%PDF"

        assertSame(cached, reusableCachedSource(cached))
    }
}
