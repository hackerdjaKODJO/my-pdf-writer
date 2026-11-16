package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
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
 * the editor, then embedded as a lossless image on an A4 PDF page. Notebooks have no
 * source PDF, so flattening is both simpler and visually safer than reconstructing
 * vector primitives from the normalized annotation points.
 */
object NoteExporter {

    const val RENDER_SCALE = 2f

    fun renderWidthPx(scale: Float = RENDER_SCALE): Int =
        (PaperBackground.A4_WIDTH_PT * scale).roundToInt().coerceIn(1, 8192)

    fun renderHeightPx(scale: Float = RENDER_SCALE): Int =
        (PaperBackground.A4_HEIGHT_PT * scale).roundToInt().coerceIn(1, 8192)

    /** Renders one page for thumbnails or sharing. The caller owns the bitmap. */
    fun renderPage(page: NotePage, width: Int, height: Int): Bitmap {
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
        )
        AnnotationPainter.draw(
            canvas = canvas,
            annotations = page.annotations,
            width = safeWidth.toFloat(),
            height = safeHeight.toFloat(),
        )
        return bitmap
    }

    suspend fun export(notebook: Notebook, out: OutputStream) =
        withContext(Dispatchers.IO) {
            val pages = notebook.pages.ifEmpty { listOf(NotePage(template = PaperTemplate.LINED)) }
            val width = renderWidthPx()
            val height = renderHeightPx()

            PDDocument().use { document ->
                pages.forEach { page ->
                    val bitmap = renderPage(page, width, height)
                    try {
                        val image = LosslessFactory.createFromImage(document, bitmap)
                        val pdfPage = PDPage(
                            PDRectangle(PaperBackground.A4_WIDTH_PT, PaperBackground.A4_HEIGHT_PT),
                        )
                        document.addPage(pdfPage)
                        PDPageContentStream(document, pdfPage).use { stream ->
                            stream.drawImage(
                                image,
                                0f,
                                0f,
                                PaperBackground.A4_WIDTH_PT,
                                PaperBackground.A4_HEIGHT_PT,
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
