package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
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
 *
 * Pages containing a REDACT annotation take a second pass: the fully composed page is
 * rasterized and the original page content is replaced by that bitmap, so hidden text
 * is not left underneath the blackout in the exported file.
 */
object PdfExporter {

    private const val RENDER_SCALE = 2.0f

    suspend fun export(
        source: File,
        specs: List<PageSpec>,
        annotations: List<Annotation>,
        out: OutputStream,
        style: DocumentStyle = DocumentStyle(),
        pageCount: Int = specs.size,
    ) = withContext(Dispatchers.IO) {
        val redacted = annotations.asSequence()
            .filter { it.type == AnnotationType.REDACT }
            .map { it.page }
            .filter { it in specs.indices }
            .toSet()

        if (redacted.isEmpty()) {
            writePdf(source, specs, annotations, out, style, pageCount)
            return@withContext
        }

        val temp = File.createTempFile("mypdf-export-", ".pdf", source.parentFile)
        try {
            temp.outputStream().use { stream ->
                writePdf(source, specs, annotations, stream, style, pageCount)
            }
            rasterizeRedactedPages(temp, redacted, out)
        } finally {
            temp.delete()
        }
    }

    private fun writePdf(
        source: File,
        specs: List<PageSpec>,
        annotations: List<Annotation>,
        out: OutputStream,
        style: DocumentStyle,
        pageCount: Int,
    ) {
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
                for (pageIndex in 0 until doc.numberOfPages) {
                    val pageAnnotations = byPage[pageIndex].orEmpty()
                    if (pageAnnotations.isEmpty() && style.isDefault) continue
                    drawPageOverlay(
                        doc = doc,
                        page = doc.getPage(pageIndex),
                        annotations = pageAnnotations,
                        style = style,
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                    )
                }

                doc.save(out)
            }
        }
    }

    private fun drawPageOverlay(
        doc: PDDocument,
        page: PDPage,
        annotations: List<Annotation>,
        style: DocumentStyle,
        pageIndex: Int,
        pageCount: Int,
    ) {
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
        DocumentOverlayPainter.draw(
            canvas = canvas,
            style = style,
            pageIndex = pageIndex,
            pageCount = pageCount,
            width = bitmapWidth.toFloat(),
            height = bitmapHeight.toFloat(),
        )
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

    /**
     * Replaces redacted pages with an image of the fully composed page. This is the
     * only way to guarantee the hidden text is gone while keeping the rest of the
     * document as vector content.
     */
    private fun rasterizeRedactedPages(source: File, redacted: Set<Int>, out: OutputStream) {
        PDDocument.load(source).use { src ->
            PDDocument().use { dst ->
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (index in 0 until src.numberOfPages) {
                            if (index !in redacted) {
                                dst.importPage(src.getPage(index))
                                continue
                            }
                            renderer.openPage(index).use { page ->
                                val scale = 2f
                                val width = (page.width * scale).coerceIn(320f, 2400f).toInt()
                                val height = (page.height * scale).coerceIn(320f, 3200f).toInt()
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageWidth = width / scale
                                val pageHeight = height / scale

                                val newPage = PDPage(PDRectangle(pageWidth, pageHeight))
                                dst.addPage(newPage)
                                val image = JPEGFactory.createFromImage(dst, bitmap, 0.9f)
                                PDPageContentStream(dst, newPage).use { stream ->
                                    stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                                }
                                bitmap.recycle()
                            }
                        }
                    }
                }
                dst.save(out)
            }
        }
    }
}
