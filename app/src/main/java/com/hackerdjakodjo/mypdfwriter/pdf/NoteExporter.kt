package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.ui.PaperBackground
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
     */
    fun renderPage(
        page: NotePage,
        width: Int,
        height: Int,
        includeHiddenLayers: Boolean = false,
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
        val layers = page.layers.associateBy { it.id }
        val visible = page.annotations.filter { annotation ->
            val layer = layers[annotation.layerId] ?: page.layers.firstOrNull()
            includeHiddenLayers || layer?.visible != false
        }.map { annotation ->
            val layer = layers[annotation.layerId] ?: page.layers.firstOrNull()
            if (layer != null && layer.opacity < 1f) {
                annotation.copy(opacity = (annotation.opacity * layer.opacity).coerceIn(0f, 1f))
            } else {
                annotation
            }
        }
        AnnotationPainter.draw(
            canvas = canvas,
            annotations = visible,
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat(),
        )
        return bitmap
    }

    /** Exports every page. Kept for the existing callers. */
    suspend fun export(notebook: Notebook, out: OutputStream) =
        export(notebook, out, NoteExportOptions())

    /**
     * Exports the pages selected by [options]. When the requested range has no
     * surviving pages, the full notebook is exported rather than an empty PDF.
     */
    suspend fun export(
        notebook: Notebook,
        out: OutputStream,
        options: NoteExportOptions,
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
