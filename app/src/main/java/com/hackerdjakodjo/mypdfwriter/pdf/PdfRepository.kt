package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.hackerdjakodjo.mypdfwriter.data.PageSize
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.RenderedPage
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

class PdfRepository(private val context: Context) {

    private val renderMutex = Mutex()
    private val version = AtomicInteger(0)
    private val pageCache = object : android.util.LruCache<String, RenderedPage>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: RenderedPage): Int = value.bitmap.byteCount
    }

    fun newVersion() {
        version.incrementAndGet()
        pageCache.evictAll()
    }

    suspend fun copyFromUri(uri: Uri, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("문서를 읽을 수 없습니다.")
        target
    }

    suspend fun queryDisplayName(uri: Uri): String = withContext(Dispatchers.IO) {
        var name: String? = null
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        }
        name?.takeIf { it.isNotBlank() } ?: "문서.pdf"
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { it.numberOfPages }
    }

    /** Display size of each source page (after its own /Rotate), used to keep annotations aligned. */
    suspend fun sourcePageSizes(file: File): List<PageSize> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { doc ->
            (0 until doc.numberOfPages).map { index ->
                val page = doc.getPage(index)
                val box = page.cropBox
                if (PageSpec.normalizeRotation(page.rotation) % 180 == 0) {
                    PageSize(box.width, box.height)
                } else {
                    PageSize(box.height, box.width)
                }
            }
        }
    }

    /** Builds a working PDF from the source file according to [specs] (order, deletions, extra rotation, blanks). */
    suspend fun assemble(source: File, specs: List<PageSpec>, target: File) = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        PDDocument.load(source).use { src ->
            PDDocument().use { dst ->
                val blankBox = specs.firstNotNullOfOrNull { spec ->
                    spec.sourcePage?.let { runCatching { src.getPage(it).cropBox }.getOrNull() }
                } ?: PDRectangle.A4
                specs.forEach { spec ->
                    val sourcePage = spec.sourcePage
                    if (sourcePage == null) {
                        dst.addPage(PDPage(blankBox))
                    } else {
                        val original = src.getPage(sourcePage)
                        val imported = dst.importPage(original)
                        imported.rotation = PageSpec.normalizeRotation(original.rotation + spec.extraRotation)
                    }
                }
                if (dst.numberOfPages == 0) dst.addPage(PDPage(blankBox))
                dst.save(target)
            }
        }
        newVersion()
    }

    suspend fun writeBlankPdf(out: OutputStream) = withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.A4))
            doc.save(out)
        }
    }

    suspend fun renderPage(file: File, pageIndex: Int, scale: Float): RenderedPage? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val key = "${version.get()}:${file.lastModified()}:$pageIndex:$scale"
            pageCache.get(key)?.let { return@withLock it }
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                renderer.openPage(pageIndex).use { page ->
                    val widthPt = page.width.toFloat()
                    val heightPt = page.height.toFloat()
                    val targetWidth = (widthPt * scale).roundToInt().coerceIn(320, 2400)
                    val targetHeight = max(1, (targetWidth * heightPt / widthPt).roundToInt())
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val rendered = RenderedPage(bitmap, widthPt, heightPt)
                    pageCache.put(key, rendered)
                    rendered
                }
            } finally {
                runCatching { renderer?.close() }
                runCatching { descriptor?.close() }
            }
        }
    }

    /** Extracts text page by page. Returns empty strings for scanned (image-only) documents. */
    suspend fun extractPagesText(file: File): List<String> = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            val count = doc.numberOfPages
            val pages = ArrayList<String>(count)
            for (i in 1..count) {
                stripper.startPage = i
                stripper.endPage = i
                pages.add(runCatching { stripper.getText(doc) }.getOrDefault(""))
            }
            pages
        }
    }

    fun clearCache() {
        pageCache.evictAll()
    }

    private fun maxCacheBytes(): Int {
        val max = Runtime.getRuntime().maxMemory()
        return (max / 6).toInt().coerceAtLeast(24 * 1024 * 1024)
    }
}
