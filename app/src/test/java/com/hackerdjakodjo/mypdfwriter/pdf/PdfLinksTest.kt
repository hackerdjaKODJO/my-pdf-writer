package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Tests link extraction against a small PDF generated locally with PDFBox. */
class PdfLinksTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageBox = PDRectangle(200f, 400f)
    private val linkRect = PDRectangle(20f, 300f, 60f, 60f) // left/bottom + width/height

    private fun writePdf(
        pageCount: Int = 3,
        rotation: Int = 0,
        configure: (PDDocument, List<PDPage>) -> Unit = { _, _ -> },
    ): File {
        val file = temp.newFile("links-${System.nanoTime()}.pdf")
        PDDocument().use { document ->
            val pages = (0 until pageCount).map {
                PDPage(pageBox).also { page -> page.rotation = rotation }
            }
            pages.forEach(document::addPage)
            configure(document, pages)
            document.save(file)
        }
        return file
    }

    private fun internalLink(rect: PDRectangle, destinationPage: PDPage): PDAnnotationLink {
        val destination = PDPageFitDestination().apply { page = destinationPage }
        return PDAnnotationLink().apply {
            rectangle = rect
            this.destination = destination
        }
    }

    private fun uriLink(rect: PDRectangle, uri: String): PDAnnotationLink =
        PDAnnotationLink().apply {
            rectangle = rect
            action = PDActionURI().apply { setURI(uri) }
        }

    @Test
    fun internalDestinationResolvesToZeroBasedPage() {
        val file = writePdf { document, pages ->
            pages[0].annotations = listOf(internalLink(linkRect, pages[2]))
        }

        val links = PdfLinks.linksForFile(file, 0)

        assertEquals(1, links.size)
        assertEquals(2, links[0].targetPage)
        assertNull(links[0].uri)
        assertTrue(links[0].isInternal)
    }

    @Test
    fun goToActionDestinationIsAlsoResolved() {
        val file = writePdf { document, pages ->
            val destination = PDPageFitDestination().apply { page = pages[1] }
            val link = PDAnnotationLink().apply {
                rectangle = linkRect
                action = PDActionGoTo().apply { this.destination = destination }
            }
            pages[0].annotations = listOf(link)
        }

        val links = PdfLinks.linksForFile(file, 0)

        assertEquals(1, links.size)
        assertEquals(1, links[0].targetPage)
    }

    @Test
    fun rectangleMapsToNormalizedDisplayCoordinates() {
        val file = writePdf { document, pages ->
            pages[0].annotations = listOf(internalLink(linkRect, pages[1]))
        }

        val rect = PdfLinks.linksForFile(file, 0).single().rect

        assertEquals(0.1f, rect.left, 0.001f)
        assertEquals(0.4f, rect.right, 0.001f)
        assertEquals(0.1f, rect.top, 0.001f)
        assertEquals(0.25f, rect.bottom, 0.001f)
    }

    @Test
    fun rotatedPageRectangleIsMappedThroughPageRotation() {
        val file = writePdf(rotation = 90) { document, pages ->
            pages[0].annotations = listOf(internalLink(linkRect, pages[1]))
        }

        val rect = PdfLinks.linksForFile(file, 0).single().rect

        // /Rotate 90 maps (x, y) -> (y / height, x / width).
        assertEquals(0.75f, rect.left, 0.001f)
        assertEquals(0.9f, rect.right, 0.001f)
        assertEquals(0.1f, rect.top, 0.001f)
        assertEquals(0.4f, rect.bottom, 0.001f)
    }

    @Test
    fun uriLinkIsSurfacedForClipboardCopyOnly() {
        val file = writePdf { document, pages ->
            pages[0].annotations = listOf(uriLink(linkRect, "https://example.com/guide"))
        }

        val link = PdfLinks.linksForFile(file, 0).single()

        assertFalse(link.isInternal)
        assertNull(link.targetPage)
        assertEquals("https://example.com/guide", link.uri)
    }

    @Test
    fun hitTestFindsTopmostLinkAndIgnoresBlankArea() {
        val top = PdfLink(NormalizedRect(0.1f, 0.1f, 0.4f, 0.25f), uri = "top")
        val bottom = PdfLink(NormalizedRect(0.1f, 0.1f, 0.4f, 0.25f), targetPage = 1)

        assertEquals("top", PdfLinks.linkAt(listOf(bottom, top), 0.2f, 0.2f)?.uri)
        assertNull(PdfLinks.linkAt(listOf(bottom, top), 0.8f, 0.8f))
    }

    @Test
    fun outOfRangePageYieldsNoLinks() {
        val file = writePdf { document, pages ->
            pages[0].annotations = listOf(internalLink(linkRect, pages[1]))
        }
        assertTrue(PdfLinks.linksForFile(file, 99).isEmpty())
        assertTrue(PdfLinks.linksForFile(file, -1).isEmpty())
    }

    @Test
    fun extraRotationIsAppliedToTheLinkRectangle() {
        val file = writePdf { document, pages ->
            pages[0].annotations = listOf(internalLink(linkRect, pages[1]))
        }

        val rect = PdfLinks.linksForFile(file, 0, extraRotation = 90).single().rect

        // Same rectangle shown on a page rotated another 90 degrees.
        assertEquals(0.75f, rect.left, 0.001f)
        assertEquals(0.9f, rect.right, 0.001f)
        assertEquals(0.1f, rect.top, 0.001f)
        assertEquals(0.4f, rect.bottom, 0.001f)
    }

    @Test
    fun remapToDocumentFollowsPageLayoutAndDropsRemovedTargets() {
        val links = listOf(
            PdfLink(NormalizedRect(0.1f, 0.1f, 0.4f, 0.25f), targetPage = 2),
            PdfLink(NormalizedRect(0.1f, 0.1f, 0.4f, 0.25f), uri = "https://example.com"),
        )
        // Working layout: source 0, source 2, blank. Source 1 was deleted.
        val pages = listOf(PageSpec(0), PageSpec(2), PageSpec(null))

        val mapped = PdfLinks.remapToDocument(links, pages)

        assertEquals(2, mapped.size)
        assertEquals(1, mapped[0].targetPage)
        assertEquals("https://example.com", mapped[1].uri)
    }
}
