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
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.roundToInt

/** Page count and per-page display sizes obtained from a single PDDocument load. */
data class DocumentInfo(val pageCount: Int, val pageSizes: List<PageSize>)

/**
 * True when [pages] is exactly the source document: same page count, every page
 * references its own index, no extra rotation and no blank page. Such a layout
 * needs no assemble() pass; the source file can be used as the working file.
 *
 * Rotation is compared after normalization, so a full 360-degree turn is also
 * treated as identity (it produces the same rendered page).
 */
fun isIdentityPageSpecs(pages: List<PageSpec>, sourceCount: Int): Boolean {
    if (pages.size != sourceCount) return false
    for (index in pages.indices) {
        val spec = pages[index]
        if (spec.sourcePage != index) return false
        if (PageSpec.normalizeRotation(spec.extraRotation) != 0) return false
    }
    return true
}

/**
 * Pure cache-reuse decision used by [PdfRepository.sourceFileFor]: a previously
 * copied source is reusable only while it still exists on disk and has bytes.
 * A missing or truncated copy is treated as a cache miss and copied again.
 */
fun reusableCachedSource(cached: File?): File? =
    cached?.takeIf { it.exists() && it.length() > 0L }

private val PDF_HEADER = "%PDF-".toByteArray(Charsets.US_ASCII)

/**
 * Pure disk-fallback decision used by [PdfRepository.sourceFileFor]: a source
 * restored to disk (e.g. by a backup that included the originals) is reusable
 * only when it has bytes, carries a PDF header and parses as a document with at
 * least one page. A missing, truncated or unparseable file is a miss, never a
 * crash — every IO/parse failure is contained here.
 */
fun reusableRestoredSource(file: File?): File? {
    val candidate = file?.takeIf { it.exists() && it.length() > 0L } ?: return null
    val header = ByteArray(PDF_HEADER.size)
    val read = runCatching { candidate.inputStream().use { it.read(header) } }.getOrNull() ?: 0
    if (read < header.size || !header.contentEquals(PDF_HEADER)) return null
    return runCatching {
        if (readDocumentInfo(candidate).pageCount >= 1) candidate else null
    }.getOrNull()
}

/**
 * Blocking implementation shared by [PdfRepository.documentInfo]. Kept as a
 * top-level function so the count/size extraction can be exercised by plain JVM
 * tests without an Android [Context]. The PDDocument is loaded exactly once.
 */
fun readDocumentInfo(file: File): DocumentInfo = PDDocument.load(file).use { doc ->
    val count = doc.numberOfPages
    // A malformed page box must not cost us the page count; the old two-call
    // flow also let size extraction fail soft while pageCount() still succeeded.
    val sizes = runCatching {
        (0 until count).map { index ->
            val page = doc.getPage(index)
            val box = page.cropBox
            if (PageSpec.normalizeRotation(page.rotation) % 180 == 0) {
                PageSize(box.width, box.height)
            } else {
                PageSize(box.height, box.width)
            }
        }
    }.getOrDefault(emptyList())
    DocumentInfo(count, sizes)
}

class PdfRepository(private val context: Context) {

    /**
     * Session-only map from a URI string to the file we copied from it. It lives
     * only as long as this repository (one ViewModel / app process): reopening the
     * same URI reuses the bytes instead of copying them again, and nothing is
     * persisted. Page edits never touch these files — assemble() always writes a
     * separate working copy — so the cached source stays pristine. Stale entries
     * are dropped when the file is gone/empty or when any step of opening fails.
     */
    private val sourceCache = HashMap<String, File>()

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
        val parent = target.absoluteFile.parentFile
        parent?.mkdirs()
        // Copy through a same-directory temp file: a failed/interrupted read must
        // not truncate a valid source that a backup may have restored at [target],
        // and the rename is atomic once all bytes have arrived.
        val temp = File.createTempFile("src-", ".part", parent)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: error("문서를 읽을 수 없습니다.")
            if (!temp.renameTo(target)) {
                // Some filesystems refuse an atomic replace; stream the complete
                // temp file into place instead (target has not been touched yet).
                temp.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            target
        } finally {
            runCatching { if (temp.exists()) temp.delete() }
        }
    }

    /**
     * Returns the local source file for [uri], reusing this session's copy when
     * available. Otherwise the live URI is copied first, so a replaced original
     * always refreshes [target] and a stale file is never served. Only when that
     * copy fails (e.g. the original was deleted after a backup restore) do we fall
     * back to [target] itself, and only if it is a valid on-disk PDF. An
     * unrecoverable copy failure drops the cache entry and throws as before.
     */
    suspend fun sourceFileFor(uri: Uri, target: File): File = withContext(Dispatchers.IO) {
        val key = uri.toString()
        val cached = synchronized(sourceCache) { reusableCachedSource(sourceCache[key]) }
        if (cached != null) return@withContext cached

        val copied = runCatching { copyFromUri(uri, target) }
        val result = copied.getOrNull() ?: run {
            // Original unreadable (deleted/moved/permission revoked). A backup
            // with sources may have restored [target]; reuse it only after full
            // validation so a truncated/garbage file can never be served.
            reusableRestoredSource(target) ?: run {
                synchronized(sourceCache) { sourceCache.remove(key) }
                copied.getOrThrow() // rethrow the original copy error
            }
        }
        synchronized(sourceCache) { sourceCache[key] = result }
        result
    }

    /** Drops the session-only source copy for a URI (e.g. after a failed open or a delete). */
    fun invalidateSource(uriString: String) {
        synchronized(sourceCache) { sourceCache.remove(uriString) }
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

    /** Single PDDocument load providing both the page count and source page sizes. */
    suspend fun documentInfo(file: File): DocumentInfo = withContext(Dispatchers.IO) {
        readDocumentInfo(file)
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { it.numberOfPages }
    }

    /** Display size of each source page (after its own /Rotate), used to keep annotations aligned. */
    suspend fun sourcePageSizes(file: File): List<PageSize> = documentInfo(file).pageSizes

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
        // A non-finite or non-positive scale would produce an invalid bitmap and
        // a poisoned cache entry, so normalize it before it reaches either.
        val safeScale = if (scale.isFinite() && scale > 0f) scale else 1f
        // The scale is part of the identity: the same page at two zoom-driven
        // scales must never share a cached bitmap. Format it so equal floats
        // always produce an equal key.
        val scaleKey = String.format(Locale.US, "%.4f", safeScale)
        val key = "${version.get()}:${file.lastModified()}:$pageIndex:$scaleKey"
        // LruCache is already synchronized. Checking it outside the render mutex
        // keeps cache hits from queueing behind unrelated page renders.
        pageCache.get(key)?.let { return@withContext it }
        renderMutex.withLock {
            var descriptor: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(descriptor)
                if (pageIndex !in 0 until renderer.pageCount) return@withLock null
                renderer.openPage(pageIndex).use { page ->
                    val widthPt = page.width.toFloat()
                    val heightPt = page.height.toFloat()
                    val targetWidth = (widthPt * safeScale).roundToInt().coerceIn(320, 2400)
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
