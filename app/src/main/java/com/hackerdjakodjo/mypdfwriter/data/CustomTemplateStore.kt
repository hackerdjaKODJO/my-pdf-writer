package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** A user-visible import failure. [message] is safe to show in the studio sheet. */
class CustomTemplateException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** One imported background that already lives in app-private storage. */
data class CustomTemplateAsset(
    val key: String,
    val displayName: String,
    val byteSize: Long,
)

/**
 * App-private store for original, user-imported notebook backgrounds.
 *
 * Import accepts JPEG/PNG/WebP images and the first page of a PDF. The bytes are
 * copied into `files/templates/`, the file type is detected from its magic number
 * (not only from the provider's MIME claim), the page is normalised to a bounded
 * PNG (max [MAX_RENDER_EDGE] px) and the temporary content-URI copy is deleted.
 * The notebook therefore keeps only a portable file name, never a temporary
 * `content://` reference.
 *
 * The same directory is the `files/templates/` backup root, so local ZIP backup
 * and restore include these assets without a second manifest format.
 */
class CustomTemplateStore(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val assetsDir = File(appContext.filesDir, CustomTemplatePaths.DIRECTORY)

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount / 1024).coerceAtLeast(1)
    }

    /** Import a supported image and return its portable asset key. */
    suspend fun importImage(uri: Uri): CustomTemplateAsset = withContext(Dispatchers.IO) {
        val temp = copyToTemp(uri, MAX_IMAGE_BYTES)
        try {
            val format = sniff(temp)
            if (!CustomTemplateFormats.isImage(format)) {
                throw CustomTemplateException("JPEG, PNG, WebP 이미지만 가져올 수 있습니다.")
            }
            val bounds = decodeBounds(temp)
            val decoded = decodeScaled(temp, bounds, MAX_RENDER_EDGE)
                ?: throw CustomTemplateException("이미지를 읽을 수 없습니다.")
            val oriented = runCatching { applyExifRotation(temp, decoded) }
                .getOrDefault(decoded)
            if (oriented !== decoded) decoded.recycle()
            val scaled = scaleDownTo(oriented, MAX_RENDER_EDGE)
            if (scaled !== oriented) oriented.recycle()
            try {
                CustomTemplateAsset(
                    key = writeAsset(scaled),
                    displayName = displayName(uri, pdf = false),
                    byteSize = 0L,
                ).withFileSize()
            } finally {
                scaled.recycle()
            }
        } finally {
            temp.delete()
        }
    }

    /** Render and store page 1 of a PDF as a normalised PNG background. */
    suspend fun importPdfFirstPage(uri: Uri): CustomTemplateAsset = withContext(Dispatchers.IO) {
        val temp = copyToTemp(uri, MAX_PDF_BYTES)
        try {
            if (sniff(temp) != CustomTemplateFormat.PDF) {
                throw CustomTemplateException("PDF 파일만 가져올 수 있습니다.")
            }
            val bitmap = renderFirstPdfPage(temp)
                ?: throw CustomTemplateException("PDF 첫 페이지를 읽을 수 없습니다.")
            try {
                CustomTemplateAsset(
                    key = writeAsset(bitmap),
                    displayName = displayName(uri, pdf = true),
                    byteSize = 0L,
                ).withFileSize()
            } finally {
                bitmap.recycle()
            }
        } finally {
            temp.delete()
        }
    }

    /**
     * Loads a full-size asset for editor/export painting. Returns null for a
     * missing, invalid or unreadable file; the page then falls back to its paper.
     * The returned bitmap is owned by this store's bounded cache and must not be
     * recycled by callers.
     */
    fun load(key: String): Bitmap? = loadAt(key, MAX_RENDER_EDGE)

    /** Smaller decode for rails, manager cards and the studio preview. */
    fun loadThumbnail(key: String, maxEdge: Int = THUMBNAIL_EDGE): Bitmap? =
        loadAt(key, maxEdge.coerceIn(64, MAX_RENDER_EDGE))

    private fun loadAt(key: String, maxEdge: Int): Bitmap? {
        val file = CustomTemplatePaths.fileIn(assetsDir, key) ?: return null
        if (!file.isFile || file.length() <= 0L) return null
        val cacheKey = cacheKey(key, file, maxEdge)
        synchronized(cache) { cache.get(cacheKey) }?.let { cached ->
            if (!cached.isRecycled) return cached
            synchronized(cache) { cache.remove(cacheKey) }
        }
        val bitmap = runCatching {
            val bounds = decodeBounds(file)
            val decoded = decodeScaled(file, bounds, maxEdge)
            if (decoded == null) {
                null
            } else {
                val scaled = scaleDownTo(decoded, maxEdge)
                if (scaled !== decoded) decoded.recycle()
                scaled
            }
        }.getOrNull() ?: return null
        synchronized(cache) { cache.put(cacheKey, bitmap) }
        return bitmap
    }

    /** Delete one asset file and its cached bitmaps. */
    fun delete(key: String): Boolean {
        val file = CustomTemplatePaths.fileIn(assetsDir, key) ?: return false
        evict(key)
        return runCatching { file.delete() }.getOrDefault(false)
    }

    /** Delete [key] only when no caller-supplied page still references it. */
    fun deleteIfUnused(key: String, usedKeys: Set<String>): Boolean {
        val safe = CustomTemplatePaths.sanitizeKey(key) ?: return false
        val used = usedKeys.mapNotNullTo(LinkedHashSet()) { CustomTemplatePaths.sanitizeKey(it) }
        return if (safe in used) false else delete(safe)
    }

    private fun CustomTemplateAsset.withFileSize(): CustomTemplateAsset {
        val file = CustomTemplatePaths.fileIn(assetsDir, key)
        return copy(byteSize = file?.length() ?: 0L)
    }

    private fun cacheKey(key: String, file: File, maxEdge: Int): String =
        "$key@$maxEdge@${file.length()}@${file.lastModified()}"

    private fun evict(key: String) {
        val safe = CustomTemplatePaths.sanitizeKey(key) ?: return
        val prefix = "$safe@"
        val keys = synchronized(cache) { cache.snapshot().keys.toList() }
        keys.forEach { cached ->
            if (cached.startsWith(prefix)) synchronized(cache) { cache.remove(cached) }
        }
    }

    // ----------------------------------------------------------------- import

    private fun copyToTemp(uri: Uri, maxBytes: Long): File {
        if (!assetsDir.isDirectory && !assetsDir.mkdirs()) {
            throw CustomTemplateException("템플릿 저장 폴더를 만들 수 없습니다.")
        }
        val temp = File(assetsDir, ".import-${UUID.randomUUID()}.tmp")
        try {
            val input = try {
                resolver.openInputStream(uri)
            } catch (error: SecurityException) {
                throw CustomTemplateException("선택한 파일을 읽을 권한이 없습니다.", error)
            } ?: throw CustomTemplateException("선택한 파일을 열 수 없습니다.")
            input.use { source ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            throw CustomTemplateException(
                                "파일이 너무 큽니다. 최대 ${maxBytes / (1024 * 1024)}MB까지 가져올 수 있습니다.",
                            )
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            if (temp.length() <= 0L) throw CustomTemplateException("빈 파일은 가져올 수 없습니다.")
            return temp
        } catch (error: CustomTemplateException) {
            temp.delete()
            throw error
        } catch (error: Exception) {
            temp.delete()
            throw CustomTemplateException("파일을 읽을 수 없습니다.", error)
        }
    }

    private fun sniff(file: File): CustomTemplateFormat? {
        val header = ByteArray(16)
        val read = runCatching {
            FileInputStream(file).use { input -> input.read(header) }
        }.getOrDefault(-1)
        if (read <= 0) return null
        return CustomTemplateFormats.sniff(header.copyOf(read))
    }

    private data class ImageBounds(val width: Int, val height: Int)

    private fun decodeBounds(file: File): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0) {
            throw CustomTemplateException("이미지 크기를 확인할 수 없습니다.")
        }
        if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE ||
            width.toLong() * height.toLong() > MAX_SOURCE_PIXELS
        ) {
            throw CustomTemplateException("이미지 해상도가 너무 큽니다.")
        }
        return ImageBounds(width, height)
    }

    private fun decodeScaled(file: File, bounds: ImageBounds, maxEdge: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.width, bounds.height, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = max(width, height)
        while (longest / sample > maxEdge * 2) sample *= 2
        return sample
    }

    private fun scaleDownTo(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceIn(1, maxEdge)
        val height = (bitmap.height * scale).roundToInt().coerceIn(1, maxEdge)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** Applies the common EXIF rotations so portrait photos do not import sideways. */
    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun renderFirstPdfPage(file: File): Bitmap? {
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: Exception) {
            throw CustomTemplateException("PDF 파일을 열 수 없습니다.", error)
        }
        descriptor.use { fd ->
            try {
                PdfRenderer(fd).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        if (page.width <= 0 || page.height <= 0) return null
                        val scale = MAX_RENDER_EDGE.toFloat() /
                            max(page.width, page.height).toFloat()
                        val width = (page.width * scale).roundToInt().coerceIn(1, MAX_RENDER_EDGE)
                        val height = (page.height * scale).roundToInt().coerceIn(1, MAX_RENDER_EDGE)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bitmap
                    }
                }
            } catch (error: Exception) {
                throw CustomTemplateException("PDF 첫 페이지를 그릴 수 없습니다.", error)
            }
        }
    }

    private fun writeAsset(bitmap: Bitmap): String {
        if (!assetsDir.isDirectory && !assetsDir.mkdirs()) {
            throw CustomTemplateException("템플릿 저장 폴더를 만들 수 없습니다.")
        }
        val key = CustomTemplatePaths.newKey()
        val target = CustomTemplatePaths.fileIn(assetsDir, key)
            ?: throw CustomTemplateException("템플릿 파일 이름을 만들 수 없습니다.")
        val temp = File(assetsDir, ".write-${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw CustomTemplateException("배경 이미지를 저장할 수 없습니다.")
                }
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                if (!temp.delete()) temp.deleteOnExit()
            }
            if (!target.isFile || target.length() <= 0L) {
                throw CustomTemplateException("배경 이미지를 저장할 수 없습니다.")
            }
            return key
        } catch (error: CustomTemplateException) {
            temp.delete()
            target.delete()
            throw error
        } catch (error: Exception) {
            temp.delete()
            target.delete()
            throw CustomTemplateException("배경 이미지를 저장할 수 없습니다.", error)
        }
    }

    private fun displayName(uri: Uri, pdf: Boolean): String {
        val raw = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
        val base = CustomTemplatePaths.displayNameFrom(raw)
        return if (pdf) "$base · 1쪽" else base
    }

    private fun cacheSizeKb(): Int {
        val eighth = Runtime.getRuntime().maxMemory() / 1024L / 8L
        return eighth.coerceIn(8L * 1024L, 32L * 1024L).toInt()
    }

    private companion object {
        const val COPY_BUFFER = 64 * 1024
        const val MAX_IMAGE_BYTES = 20L * 1024L * 1024L
        const val MAX_PDF_BYTES = 60L * 1024L * 1024L
        const val MAX_SOURCE_EDGE = 20_000
        const val MAX_SOURCE_PIXELS = 120_000_000L

        /** Enough for the 2.5x A4 editor sheet, and cheap to restore from backup. */
        const val MAX_RENDER_EDGE = 2480

        /** Rail/manager/studio previews use a smaller decode and cache entry. */
        const val THUMBNAIL_EDGE = 512
    }
}
