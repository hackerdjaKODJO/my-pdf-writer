package com.hackerdjakodjo.mypdfwriter.data

import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Portable identity rules for user-imported background images.
 *
 * A [NotePage] stores only a plain file name below the app-private
 * `files/templates/` directory. It never stores an absolute path, a `content://`
 * URI or a directory component, so a notebook/backup keeps working when Android
 * moves the app's data directory or another device restores the archive.
 */
object CustomTemplatePaths {

    /** Backup/data directory below `Context.filesDir`. */
    const val DIRECTORY = "templates"

    const val FILE_PREFIX = "tpl-"
    const val MAX_KEY_LENGTH = 128
    const val MAX_NAME_LENGTH = 80
    const val DEFAULT_NAME = "커스텀 배경"

    private const val PNG_EXTENSION = ".png"
    private val SAFE_KEY = Regex(
        "^[A-Za-z0-9][A-Za-z0-9._-]*\\.(png|jpg|jpeg|webp)$",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Returns a safe, portable asset file name, or null when [raw] is blank,
     * absolute, nested, a traversal, a drive path, contains control characters or
     * has an extension that is not an accepted background image type.
     */
    fun sanitizeKey(raw: String?): String? {
        val key = raw?.trim().orEmpty()
        if (key.isEmpty() || key.length > MAX_KEY_LENGTH) return null
        if (key.any { it.code < 0x20 || it.code == 0x7f }) return null
        if (key.startsWith('.') || key.contains('/') || key.contains('\\')) return null
        if (key.contains("..") || key.contains(':')) return null
        if (!SAFE_KEY.matches(key)) return null
        return key
    }

    /** Resolves [key] inside [directory] and rejects a canonical escape. */
    fun fileIn(directory: File, key: String): File? {
        val safe = sanitizeKey(key) ?: return null
        val root = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(root, safe).canonicalFile }.getOrNull() ?: return null
        if (file.parentFile != root) return null
        return file
    }

    /** A fresh immutable asset name for a newly imported background. */
    fun newKey(): String = FILE_PREFIX + UUID.randomUUID().toString() + PNG_EXTENSION

    /** Trims a user-visible template name to a safe, non-empty label. */
    fun normalizeName(raw: String?, fallback: String = DEFAULT_NAME): String {
        val clean = raw.orEmpty()
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_NAME_LENGTH)
            .trim()
        return clean.ifBlank { fallback }
    }

    /** Derives a friendly name from a SAF display name without its extension. */
    fun displayNameFrom(raw: String?, fallback: String = DEFAULT_NAME): String {
        val base = raw.orEmpty()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
        return normalizeName(base, fallback)
    }

    /** Asset keys referenced by [pages]. Invalid legacy values are ignored. */
    fun keysInPages(pages: Iterable<NotePage>): Set<String> {
        val keys = LinkedHashSet<String>()
        pages.forEach { page ->
            sanitizeKey(page.customBackground)?.let(keys::add)
        }
        return keys
    }

    /** Asset keys referenced by [notebooks]. */
    fun keysInNotebooks(notebooks: Iterable<Notebook>): Set<String> {
        val keys = LinkedHashSet<String>()
        notebooks.forEach { notebook -> keys.addAll(keysInPages(notebook.pages)) }
        return keys
    }

    /** Normalised lower-case extension without the dot, for diagnostics/tests. */
    fun extensionOf(key: String): String =
        sanitizeKey(key)?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
}

/** File type detected from the bytes, not from an untrusted provider MIME value. */
enum class CustomTemplateFormat { PNG, JPEG, WEBP, PDF }

object CustomTemplateFormats {

    /** True for the image formats accepted as a custom background. */
    fun isImage(format: CustomTemplateFormat?): Boolean = when (format) {
        CustomTemplateFormat.PNG,
        CustomTemplateFormat.JPEG,
        CustomTemplateFormat.WEBP,
        -> true

        else -> false
    }

    /** Detects PNG/JPEG/WebP/PDF from a short file header. */
    fun sniff(header: ByteArray): CustomTemplateFormat? {
        if (header.size >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()
        ) {
            return CustomTemplateFormat.PNG
        }
        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return CustomTemplateFormat.JPEG
        }
        if (header.size >= 12 &&
            header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[8] == 'W'.code.toByte() &&
            header[9] == 'E'.code.toByte() &&
            header[10] == 'B'.code.toByte() &&
            header[11] == 'P'.code.toByte()
        ) {
            return CustomTemplateFormat.WEBP
        }
        if (header.size >= 5 &&
            header[0] == '%'.code.toByte() &&
            header[1] == 'P'.code.toByte() &&
            header[2] == 'D'.code.toByte() &&
            header[3] == 'F'.code.toByte() &&
            header[4] == '-'.code.toByte()
        ) {
            return CustomTemplateFormat.PDF
        }
        return null
    }
}
