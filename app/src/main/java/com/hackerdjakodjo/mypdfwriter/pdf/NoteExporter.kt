package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.ui.PaperBackground
import com.hackerdjakodjo.mypdfwriter.ui.NoteLayers
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Raster PDF export for local notebooks.
 *
 * Every page is composed with the same paper painter and annotation painter used by
 * the editor, then embedded as a lossless image on an A4 PDF page. Portrait and
 * landscape pages use their own physical page box and matching rendered pixels, so
 * a mixed-orientation notebook exports without stretching either kind of page.
 * Notebooks have no source PDF, so flattening is both simpler and visually safer
 * than reconstructing vector primitives from the normalized annotation points.
 */
object NoteExporter {

    const val RENDER_SCALE = 2f

    /** One page's PDF points and raster pixels at [scale]. */
    data class PageSize(
        val widthPt: Float,
        val heightPt: Float,
        val widthPx: Int,
        val heightPx: Int,
    )

    fun pageSize(
        orientation: PageOrientation = PageOrientation.PORTRAIT,
        scale: Float = RENDER_SCALE,
    ): PageSize = PageSize(
        widthPt = NotePaper.widthPt(orientation),
        heightPt = NotePaper.heightPt(orientation),
        widthPx = (NotePaper.widthPt(orientation) * scale).roundToInt().coerceIn(1, 8192),
        heightPx = (NotePaper.heightPt(orientation) * scale).roundToInt().coerceIn(1, 8192),
    )

    fun renderWidthPx(
        scale: Float = RENDER_SCALE,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
    ): Int = pageSize(orientation, scale).widthPx

    fun renderHeightPx(
        scale: Float = RENDER_SCALE,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
    ): Int = pageSize(orientation, scale).heightPx

    /**
     * Renders one page for thumbnails or sharing. The caller owns the bitmap.
     *
     * [includeHiddenLayers] defaults to the editor's WYSIWYG visibility. Layer
     * opacity is applied in both cases; only the hidden-layer filter changes.
     *
     * Post-it [NotePage.memoCards] are note content, so they are painted after the
     * annotation and checklist layers by default for a WYSIWYG result.
     * [includeMemoCards] is the opt-out for callers that want an ink-only raster;
     * it defaults to true and is deliberately not a persisted export field, so
     * older backups and their JSON stay byte-compatible. [includeChecklists]
     * follows the same rule for durable interactive checklist content.
     */
    fun renderPage(
        page: NotePage,
        width: Int,
        height: Int,
        includeHiddenLayers: Boolean = false,
        backgroundLoader: (String) -> Bitmap? = { null },
        includeMemoCards: Boolean = true,
        includeChecklists: Boolean = true,
    ): Bitmap {
        val safeWidth = width.coerceIn(1, 8192)
        val safeHeight = height.coerceIn(1, 8192)
        val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        PaperBackground.draw(
            canvas = canvas,
            template = page.template,
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat(),
            dark = false,
            tint = page.tint,
        )
        page.customBackground?.let { key ->
            backgroundLoader(key)?.let { background ->
                PaperBackground.drawCustomBackground(
                    canvas = canvas,
                    bitmap = background,
                    width = safeWidth.toFloat(),
                    height = safeHeight.toFloat(),
                )
            }
        }
        val visible = NoteLayers.renderAnnotations(page, includeHiddenLayers)
        AnnotationPainter.draw(
            canvas = canvas,
            annotations = visible,
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat(),
        )
        // Checklists are durable note content. They sit below post-it cards, matching
        // the editor's overlay order, and can be excluded explicitly for ink-only output.
        if (includeChecklists && page.checklists.isNotEmpty()) {
            ChecklistCardPainter.draw(
                canvas = canvas,
                cards = page.checklists,
                width = safeWidth.toFloat(),
                height = safeHeight.toFloat(),
            )
        }
        // Cards sit above ink in the editor, so paint them last. Redaction and every
        // other annotation are still drawn by AnnotationPainter exactly as before.
        NoteCardPainter.draw(
            canvas = canvas,
            cards = NoteCardPainter.visibleMemoCards(page, includeMemoCards),
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat(),
        )
        return bitmap
    }

    /** Exports every page. Kept for the existing callers. */
    suspend fun export(
        notebook: Notebook,
        out: OutputStream,
        backgroundLoader: (String) -> Bitmap? = { null },
        includeMemoCards: Boolean = true,
    ) = export(notebook, out, NoteExportOptions(), backgroundLoader, includeMemoCards)

    /**
     * Exports the pages selected by [options]. When the requested range has no
     * surviving pages, the full notebook is exported rather than an empty PDF.
     *
     * [backgroundLoader] resolves a page's portable custom-template key to an
     * app-private bitmap. It is resolved once per page at export time; hidden-layer
     * filtering and WYSIWYG annotation painting are unchanged. [includeMemoCards]
     * is forwarded to [renderPage] so a caller can request an ink-only export;
     * its default keeps post-its in both full exports and shared raster pages.
     */
    suspend fun export(
        notebook: Notebook,
        out: OutputStream,
        options: NoteExportOptions,
        backgroundLoader: (String) -> Bitmap? = { null },
        includeMemoCards: Boolean = true,
    ) =
        withContext(Dispatchers.IO) {
            val allPages = notebook.pages.ifEmpty { listOf(NotePage(template = PaperTemplate.LINED)) }
            val requestedIndices = options.pageIndices(allPages)
            val pages = if (requestedIndices.isEmpty()) {
                allPages
            } else {
                requestedIndices.map { allPages[it] }
            }

            PDDocument().use { document ->
                pages.forEach { page ->
                    val size = pageSize(page.orientation)
                    val bitmap = renderPage(
                        page,
                        size.widthPx,
                        size.heightPx,
                        options.includeHiddenLayers,
                        backgroundLoader,
                        includeMemoCards,
                        options.includeChecklists,
                    )
                    try {
                        val image = LosslessFactory.createFromImage(document, bitmap)
                        val pdfPage = PDPage(PDRectangle(size.widthPt, size.heightPt))
                        document.addPage(pdfPage)
                        PDPageContentStream(document, pdfPage).use { stream ->
                            stream.drawImage(
                                image,
                                0f,
                                0f,
                                size.widthPt,
                                size.heightPt,
                            )
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                document.save(out)
            }
        }
}
