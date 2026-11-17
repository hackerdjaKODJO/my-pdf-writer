package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import java.io.File

/**
 * One tappable link annotation on a PDF page, in normalized display coordinates.
 *
 * Exactly one of [targetPage] (zero-based internal page) or [uri] (external link)
 * is set. External links are never opened: the reader only copies the URI.
 */
data class PdfLink(
    val rect: NormalizedRect,
    val targetPage: Int? = null,
    val uri: String? = null,
) {
    val isInternal: Boolean get() = targetPage != null
}

/**
 * Offline extraction of PDF link annotations.
 *
 * Limitations: mapping uses the crop-box size and the page's `/Rotate` with
 * right-angle rotations only. Pages with a non-zero crop-box origin, non-90°
 * rotation or user-unit scaling may place rectangles slightly differently from
 * the rasterizer. To avoid wrong jumps, only internal page destinations and
 * simple URI links are surfaced; named actions and remote go-to actions are
 * ignored. Links are extracted from the source PDF because PDFBox's
 * `importPage` does not remap internal destination references.
 */
object PdfLinks {

    /**
     * Reads link annotations of one source page.
     *
     * PDFBox's `importPage` does not remap `/Dest` page references, so links are
     * always extracted from the original source PDF; the caller remaps the
     * returned zero-based source page numbers to the working document's pages.
     * [extraRotation] is the page-layout rotation added on top of `/Rotate`.
     */
    fun linksForFile(file: File, pageIndex: Int, extraRotation: Int = 0): List<PdfLink> {
        if (pageIndex < 0) return emptyList()
        return runCatching {
            PDDocument.load(file).use { document ->
                if (pageIndex >= document.numberOfPages) return@use emptyList<PdfLink>()
                val page = document.getPage(pageIndex)
                val box = page.cropBox ?: return@use emptyList<PdfLink>()
                if (box.width <= 0f || box.height <= 0f) return@use emptyList<PdfLink>()
                val rotation = ((page.rotation + extraRotation) % 360 + 360) % 360
                if (rotation % 90 != 0) return@use emptyList<PdfLink>()

                val result = ArrayList<PdfLink>()
                val annotations = runCatching { page.annotations }.getOrDefault(emptyList())
                for (annotation in annotations) {
                    if (annotation !is PDAnnotationLink) continue
                    val rect = annotation.rectangle ?: continue
                    val displayRect = normalizedRect(rect, box.width, box.height, rotation) ?: continue
                    val target = resolveInternalPage(document, annotation)
                    when {
                        target != null -> result += PdfLink(displayRect, targetPage = target)
                        else -> {
                            val uri = (annotation.action as? PDActionURI)?.uri?.trim()
                            if (!uri.isNullOrEmpty()) result += PdfLink(displayRect, uri = uri)
                        }
                    }
                }
                result
            }
        }.getOrDefault(emptyList())
    }

    /** Topmost link under a normalized display point, or null for a blank spot. */
    fun linkAt(links: List<PdfLink>, x: Float, y: Float): PdfLink? =
        links.lastOrNull { it.rect.contains(x, y) }

    /**
     * Maps source-page targets through the working document's page layout. Links
     * pointing at source pages that were deleted are dropped; URI links pass
     * through unchanged.
     */
    fun remapToDocument(links: List<PdfLink>, pages: List<PageSpec>): List<PdfLink> =
        links.mapNotNull { link ->
            val target = link.targetPage ?: return@mapNotNull link
            val index = pages.indexOfFirst { it.sourcePage == target }
            if (index >= 0) link.copy(targetPage = index) else null
        }

    private fun normalizedRect(
        rect: PDRectangle,
        pageWidth: Float,
        pageHeight: Float,
        rotation: Int,
    ): NormalizedRect? {
        val points = listOf(
            rect.lowerLeftX to rect.lowerLeftY,
            rect.upperRightX to rect.lowerLeftY,
            rect.lowerLeftX to rect.upperRightY,
            rect.upperRightX to rect.upperRightY,
        ).mapNotNull { (x, y) ->
            PdfTextGeometry.mapPoint(x, y, pageWidth, pageHeight, rotation)
        }
        if (points.size != 4) return null
        val left = points.minOf { it.first }.coerceIn(0f, 1f)
        val right = points.maxOf { it.first }.coerceIn(0f, 1f)
        val top = points.minOf { it.second }.coerceIn(0f, 1f)
        val bottom = points.maxOf { it.second }.coerceIn(0f, 1f)
        if (right - left < 0.001f || bottom - top < 0.001f) return null
        // Fully off-page rectangles cannot be tapped.
        if (right <= 0f || left >= 1f || bottom <= 0f || top >= 1f) return null
        return NormalizedRect(left, top, right, bottom)
    }

    private fun resolveInternalPage(document: PDDocument, link: PDAnnotationLink): Int? {
        val destination: PDDestination? = runCatching { link.destination }.getOrNull()
            ?: (link.action as? PDActionGoTo)?.let { runCatching { it.destination }.getOrNull() }
        if (destination !is PDPageDestination) return null

        // A page dictionary is the most reliable form: match it against the
        // document's page tree to get the exact zero-based index.
        val page = runCatching { destination.page }.getOrNull()
        if (page != null) {
            val index = runCatching { document.pages.indexOf(page) }.getOrNull() ?: -1
            if (index in 0 until document.numberOfPages) return index
        }

        // Integer destinations are zero-based page indices in the PDF spec.
        val index = runCatching { destination.retrievePageNumber() }.getOrNull() ?: -1
        return index.takeIf { it in 0 until document.numberOfPages }
    }
}
