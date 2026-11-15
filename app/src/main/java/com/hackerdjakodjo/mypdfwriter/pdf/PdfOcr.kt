package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

data class OcrPageText(val pageIndex: Int, val text: String)

/**
 * Offline OCR pipeline:
 *  - render each PDF page with Android's PdfRenderer,
 *  - recognize text with the bundled ML Kit model,
 *  - optionally write a searchable PDF with an invisible text layer over the
 *    rendered page. The source file is never uploaded anywhere.
 */
object PdfOcr {

    private const val RENDER_SCALE = 2.2f
    private const val MAX_PIXELS = 2600

    suspend fun recognizeText(
        source: File,
        language: OcrLanguage = OcrLanguage.KOREAN,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<OcrPageText> = withContext(Dispatchers.IO) {
        val result = ArrayList<OcrPageText>()
        openRenderer(source) { renderer ->
            for (index in 0 until renderer.pageCount) {
                renderer.openPage(index).use { page ->
                    val bitmap = render(page)
                    try {
                        val recognized = OcrEngine.recognize(bitmap, language)
                        result.add(OcrPageText(index, recognized.text))
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(index + 1, renderer.pageCount)
            }
        }
        result
    }

    suspend fun createSearchablePdf(
        context: Context,
        source: File,
        out: OutputStream,
        language: OcrLanguage = OcrLanguage.KOREAN,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            val font = loadFont(context, doc) ?: PDType1Font.HELVETICA
            openRenderer(source) { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val bitmap = render(page)
                        try {
                            val recognized = OcrEngine.recognize(bitmap, language)
                            addSearchablePage(doc, font, bitmap, page, recognized)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    onProgress(index + 1, renderer.pageCount)
                }
            }
            doc.save(out)
        }
    }

    private fun addSearchablePage(
        doc: PDDocument,
        font: PDFont,
        bitmap: Bitmap,
        renderedPage: PdfRenderer.Page,
        recognized: OcrResult,
    ) {
        val pageWidth = renderedPage.width.toFloat().coerceAtLeast(1f)
        val pageHeight = renderedPage.height.toFloat().coerceAtLeast(1f)
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        doc.addPage(page)
        val image = JPEGFactory.createFromImage(doc, bitmap, 0.9f)
        PDPageContentStream(doc, page).use { stream ->
            stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
            stream.setRenderingMode(RenderingMode.NEITHER)
            recognized.lines.forEach { line ->
                val text = line.text.replace(Regex("[\\r\\n]+"), " ").trim()
                if (text.isEmpty()) return@forEach
                val box = line.box
                val x = box.left.toFloat() / bitmap.width * pageWidth
                val baseline = pageHeight - box.bottom.toFloat() / bitmap.height * pageHeight
                val textSize = (box.height().toFloat() / bitmap.height * pageHeight * 0.82f)
                    .coerceIn(3f, 72f)
                runCatching {
                    stream.beginText()
                    stream.setFont(font, textSize)
                    stream.newLineAtOffset(x, baseline)
                    stream.showText(text)
                    stream.endText()
                }
            }
        }
    }

    private fun render(page: PdfRenderer.Page): Bitmap {
        val width = (page.width * RENDER_SCALE).toInt().coerceIn(320, MAX_PIXELS)
        val height = (page.height * RENDER_SCALE).toInt().coerceIn(320, MAX_PIXELS)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private suspend fun openRenderer(
        source: File,
        block: suspend (PdfRenderer) -> Unit,
    ) {
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> block(renderer) }
        }
    }

    private fun loadFont(context: Context, doc: PDDocument): PDFont? =
        runCatching {
            context.assets.open("fonts/NanumGothic.ttf").use { input ->
                PDType0Font.load(doc, input, true)
            }
        }.getOrNull()
}
