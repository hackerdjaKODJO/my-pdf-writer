package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Flattens the annotation layer into the PDF.
 *
 * Each annotated page is rendered at 2x with the exact same painter used on screen and
 * embedded as a lossless image. This guarantees Korean text, highlighter alpha and pen
 * smoothing look identical in every PDF viewer. The image is placed with a rotation
 * matrix derived from the page's /Rotate so the overlay follows the page orientation.
 */
object PdfExporter {

    private const val RENDER_SCALE = 2.0f

    suspend fun export(
        source: File,
        specs: List<PageSpec>,
        annotations: List<Annotation>,
        out: OutputStream,
    ) = withContext(Dispatchers.IO) {
        PDDocument.load(source).use { src ->
            PDDocument().use { doc ->
                val blankBox = specs.firstNotNullOfOrNull { spec ->
                    spec.sourcePage?.let { runCatching { src.getPage(it).cropBox }.getOrNull() }
                } ?: PDRectangle.A4

                specs.forEach { spec ->
                    if (spec.sourcePage == null) {
                        doc.addPage(PDPage(blankBox))
                    } else {
                        val original = src.getPage(spec.sourcePage)
                        val imported = doc.importPage(original)
                        imported.rotation = PageSpec.normalizeRotation(original.rotation + spec.extraRotation)
                    }
                }
                if (doc.numberOfPages == 0) doc.addPage(PDPage(blankBox))

                val byPage = annotations.groupBy { it.page }
                for ((pageIndex, pageAnnotations) in byPage) {
                    if (pageIndex !in 0 until doc.numberOfPages) continue
                    if (pageAnnotations.isEmpty()) continue
                    drawPageOverlay(doc, doc.getPage(pageIndex), pageAnnotations)
                }

                doc.save(out)
            }
        }
    }

    private fun drawPageOverlay(doc: PDDocument, page: PDPage, annotations: List<Annotation>) {
        val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4
        val pageWidth = box.width
        val pageHeight = box.height
        val rotation = PageSpec.normalizeRotation(page.rotation)
        val displayWidth = if (rotation % 180 == 0) pageWidth else pageHeight
        val displayHeight = if (rotation % 180 == 0) pageHeight else pageWidth

        val bitmapWidth = (displayWidth * RENDER_SCALE).roundToInt().coerceIn(64, 4096)
        val bitmapHeight = (displayHeight * RENDER_SCALE).roundToInt().coerceIn(64, 4096)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        AnnotationPainter.draw(canvas, annotations, bitmapWidth.toFloat(), bitmapHeight.toFloat())
        val image = LosslessFactory.createFromImage(doc, bitmap)
        bitmap.recycle()

        PDPageContentStream(doc, page, AppendMode.APPEND, true, true).use { stream ->
            stream.saveGraphicsState()
            val matrix = when (rotation) {
                90 -> Matrix(0f, 1f, -1f, 0f, pageWidth, 0f)
                180 -> Matrix(-1f, 0f, 0f, -1f, pageWidth, pageHeight)
                270 -> Matrix(0f, -1f, 1f, 0f, 0f, pageHeight)
                else -> Matrix(1f, 0f, 0f, 1f, 0f, 0f)
            }
            stream.transform(matrix)
            stream.drawImage(image, 0f, 0f, displayWidth, displayHeight)
            stream.restoreGraphicsState()
        }
    }
}
