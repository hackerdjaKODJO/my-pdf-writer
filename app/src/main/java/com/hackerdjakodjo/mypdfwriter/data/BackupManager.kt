package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Collections
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** What one local backup archive contains. */
data class BackupSummary(
    val createdAt: Long,
    val includeSources: Boolean,
    val fileCount: Int,
)

/** A corrupt, unsafe or incompatible backup. */
class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Local-only ZIP backup and restore.
 *
 * The archive layout is deliberately small and explicit:
 *
 * ```
 * backup.json
 * files/notebooks/<id>.json
 * files/docstates/<docId>.json
 * files/excerpts/<docId>.json
 * files/reading/<docId>.json
 * files/docstyles/<docId>.json
 * files/templates/<assetKey>.png
 * files/audio/<ownerId>/index.json
 * files/audio/<ownerId>/<noteId>.m4a
 * files/sources/<docId>.pdf          (optional)
 * shared_prefs/pen_box.xml
 * shared_prefs/library.xml
 * ```
 *
 * Restore never trusts an entry name. Names with absolute paths, backslashes,
 * drive letters or `..` segments are rejected before anything is written. The
 * archive is first extracted into a private temporary directory, then only the
 * known data directories are replaced. Files outside those directories are not
 * touched. Temporary files are removed in every exit path.
 */
object BackupManager {

    suspend fun create(
        context: Context,
        out: OutputStream,
        includeSources: Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): BackupSummary = withContext(Dispatchers.IO) {
        BackupArchive.create(
            filesDir = context.filesDir,
            prefsDir = File(context.dataDir, "shared_prefs"),
            includeSources = includeSources,
            appVersion = BuildConfig.VERSION_NAME,
            out = out,
            onProgress = onProgress,
        )
    }

    suspend fun restore(
        context: Context,
        input: InputStream,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): BackupSummary = withContext(Dispatchers.IO) {
        val workDir = File(context.cacheDir, "backup-restore-${UUID.randomUUID()}")
        if (!workDir.mkdirs()) throw BackupException("복원용 임시 폴더를 만들 수 없습니다.")
        // A failed preference commit must not discard the on-disk rollback copy:
        // the live directories are only final once preferences were applied.
        var keepWorkDir = false
        try {
            val extracted = BackupArchive.extract(input, workDir, onProgress)
            // Parse preferences before touching live data, so malformed XML
            // cannot leave a half-restored app behind.
            val preferences = readPreferences(extracted.prefsDir)
            installRestoredData(
                filesDir = context.filesDir,
                stagedFilesDir = extracted.dataDir,
                directories = extracted.manifest.directories,
                rollbackDir = File(workDir, "rollback"),
                applyPreferences = { applyPreferences(context, preferences) },
                onRollbackFailed = { keepWorkDir = true },
            )
            BackupSummary(
                createdAt = extracted.manifest.createdAt,
                includeSources = extracted.manifest.includeSources,
                fileCount = extracted.manifest.fileCount,
            )
        } finally {
            if (!keepWorkDir) {
                runCatching { workDir.deleteRecursively() }
            }
        }
    }
}

/**
 * Installs staged directories into [filesDir] and then applies the restored
 * preferences. Preference application happens *after* the data directories are
 * replaced, so a failed commit must roll those directories back before the
 * error is propagated; otherwise new user data would be left paired with a
 * stale preference set and no rollback copy.
 *
 * [onRollbackFailed] tells the caller to keep the rollback directory when the
 * originals could not be fully restored.
 */
internal fun installRestoredData(
    filesDir: File,
    stagedFilesDir: File,
    directories: List<String>,
    rollbackDir: File,
    applyPreferences: () -> Unit,
    onRollbackFailed: () -> Unit = {},
) {
    BackupArchive.replaceDirectories(filesDir, stagedFilesDir, directories, rollbackDir)
    try {
        applyPreferences()
    } catch (prefsError: Throwable) {
        try {
            BackupArchive.rollbackDirectories(filesDir, directories, rollbackDir)
        } catch (rollbackError: Throwable) {
            onRollbackFailed()
            prefsError.addSuppressed(rollbackError)
        }
        throw prefsError
    }
}

/**
 * Platform-independent archive implementation. Kept separate from
 * [BackupManager] so JVM tests can exercise the ZIP and path handling with
 * ordinary temporary directories and no Android [Context].
 */
internal object BackupArchive {

    const val FORMAT_VERSION = 1
    const val MANIFEST_NAME = "backup.json"
    private const val MAX_MANIFEST_BYTES = 1_048_576
    private const val MAX_ENTRY_NAME_LENGTH = 1024
    private const val COPY_BUFFER = 64 * 1024

    val DATA_DIRECTORIES = listOf(
        "notebooks",
        "docstates",
        "excerpts",
        "reading",
        "docstyles",
        "templates",
        "audio",
        "sources",
    )

    val PREFERENCE_FILES = listOf("pen_box.xml", "library.xml")

    data class Manifest(
        val formatVersion: Int,
        val createdAt: Long,
        val includeSources: Boolean,
        val appVersion: String,
        val fileCount: Int,
        val directories: List<String>,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("formatVersion", formatVersion)
            put("createdAt", createdAt)
            put("includeSources", includeSources)
            put("appVersion", appVersion)
            put("fileCount", fileCount)
            put("directories", JSONArray().apply { directories.forEach { put(it) } })
        }
    }

    data class BackupFile(val entryName: String, val source: File)

    data class ExtractedBackup(
        val manifest: Manifest,
        val root: File,
        val dataDir: File,
        val prefsDir: File,
    )

    // ------------------------------------------------------------------ create

    fun create(
        filesDir: File,
        prefsDir: File?,
        includeSources: Boolean,
        appVersion: String,
        out: OutputStream,
        onProgress: (Int, Int) -> Unit,
    ): BackupSummary {
        val createdAt = System.currentTimeMillis()
        val directories = mutableListOf<String>()
        val files = mutableListOf<BackupFile>()

        for (name in DATA_DIRECTORIES) {
            if (name == "sources" && !includeSources) continue
            val root = File(filesDir, name)
            if (!root.isDirectory) continue
            directories += name
            collectFiles(root, name, files)
        }
        PREFERENCE_FILES.forEach { name ->
            val file = prefsDir?.let { File(it, name) }
            if (file != null && file.isFile) {
                files += BackupFile("shared_prefs/$name", file)
            }
        }

        files.sortBy { it.entryName }
        val manifest = Manifest(
            formatVersion = FORMAT_VERSION,
            createdAt = createdAt,
            includeSources = includeSources,
            appVersion = appVersion.take(128),
            fileCount = files.size,
            directories = directories,
        )

        val total = files.size + 1
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            writeBytes(
                zip = zip,
                entryName = MANIFEST_NAME,
                bytes = manifest.toJson().toString(2).toByteArray(Charsets.UTF_8),
                timestamp = createdAt,
            )
            onProgress(1, total)
            files.forEachIndexed { index, file ->
                val entry = ZipEntry(file.entryName).apply { time = createdAt }
                zip.putNextEntry(entry)
                file.source.inputStream().buffered(COPY_BUFFER).use { input ->
                    input.copyTo(zip, COPY_BUFFER)
                }
                zip.closeEntry()
                onProgress(index + 2, total)
            }
            zip.finish()
            zip.flush()
        }
        return BackupSummary(createdAt, includeSources, files.size)
    }

    private fun collectFiles(root: File, topLevel: String, out: MutableList<BackupFile>) {
        val canonicalRoot = root.canonicalFile
        val prefix = canonicalRoot.path + File.separator
        root.walkTopDown().forEach { file ->
            if (!file.isFile || file.name.endsWith(".tmp", ignoreCase = true)) return@forEach
            val canonical = file.canonicalFile
            if (!canonical.path.startsWith(prefix)) return@forEach
            val relative = canonical.relativeTo(canonicalRoot).invariantSeparatorsPath
            val entryName = "files/$topLevel/$relative"
            if (safeEntryPath(entryName) != entryName) {
                throw BackupException("백업할 수 없는 파일 이름입니다: $relative")
            }
            out += BackupFile(entryName, canonical)
        }
    }

    private fun writeBytes(zip: ZipOutputStream, entryName: String, bytes: ByteArray, timestamp: Long) {
        val entry = ZipEntry(entryName).apply { time = timestamp }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    // ----------------------------------------------------------------- extract

    fun extract(
        input: InputStream,
        workDir: File,
        onProgress: (Int, Int) -> Unit,
    ): ExtractedBackup {
        if (!workDir.isDirectory && !workDir.mkdirs()) {
            throw BackupException("복원용 임시 폴더를 만들 수 없습니다.")
        }
        val archive = File(workDir, "backup.zip")
        try {
            FileOutputStream(archive).use { output ->
                input.copyTo(output, COPY_BUFFER)
                output.flush()
                output.fd.sync()
            }
        } catch (error: IOException) {
            throw BackupException("백업 파일을 읽을 수 없습니다.", error)
        }
        if (archive.length() <= 0L) throw BackupException("빈 백업 파일입니다.")

        val root = File(workDir, "extracted").apply { mkdirs() }
        val dataDir = File(root, "files").apply { mkdirs() }
        val prefsDir = File(root, "shared_prefs").apply { mkdirs() }

        try {
            ZipFile(archive).use { zip ->
                val entries = Collections.list(zip.entries())
                val seenFiles = HashSet<String>()
                var manifestEntry: ZipEntry? = null
                val dataEntries = mutableListOf<Pair<String, ZipEntry>>()
                val prefEntries = mutableListOf<Pair<String, ZipEntry>>()

                for (entry in entries) {
                    val path = safeEntryPath(entry.name)
                        ?: throw BackupException("안전하지 않은 경로가 포함되어 있습니다: ${entry.name}")
                    if (entry.isDirectory) continue
                    if (!seenFiles.add(path)) {
                        throw BackupException("같은 경로가 여러 번 들어 있습니다: $path")
                    }
                    when {
                        path == MANIFEST_NAME -> manifestEntry = entry
                        path.startsWith("files/") -> dataEntries += path to entry
                        path.startsWith("shared_prefs/") -> prefEntries += path to entry
                        else -> throw BackupException("백업에 알 수 없는 항목이 있습니다: $path")
                    }
                }

                val manifestZipEntry = manifestEntry
                    ?: throw BackupException("백업 정보(backup.json)가 없습니다.")
                val manifest = parseManifest(readText(zip, manifestZipEntry, MAX_MANIFEST_BYTES))
                if (manifest.formatVersion != FORMAT_VERSION) {
                    throw BackupException("지원하지 않는 백업 형식입니다. (버전 ${manifest.formatVersion})")
                }

                manifest.directories.forEach { name ->
                    if (name !in DATA_DIRECTORIES) {
                        throw BackupException("백업에 알 수 없는 데이터 폴더가 있습니다: $name")
                    }
                    if (name == "sources" && !manifest.includeSources) {
                        throw BackupException("PDF 원본을 포함하지 않은 백업에 sources 폴더가 있습니다.")
                    }
                    File(dataDir, name).mkdirs()
                }

                var fileCount = 0
                dataEntries.forEach { (path, _) ->
                    val parts = path.split('/')
                    if (parts.size < 3) {
                        throw BackupException("잘못된 데이터 경로입니다: $path")
                    }
                    val topLevel = parts[1]
                    if (topLevel !in manifest.directories) {
                        throw BackupException("백업 정보에 없는 폴더의 파일입니다: $path")
                    }
                    fileCount++
                }
                prefEntries.forEach { (path, _) ->
                    val name = path.removePrefix("shared_prefs/")
                    if (name !in PREFERENCE_FILES) {
                        throw BackupException("백업에 알 수 없는 환경설정이 있습니다: $name")
                    }
                    fileCount++
                }
                if (fileCount != manifest.fileCount) {
                    throw BackupException(
                        "백업 파일 수가 맞지 않습니다. (정보 ${manifest.fileCount}개, 실제 ${fileCount}개)",
                    )
                }

                val total = fileCount + 1
                var done = 1
                onProgress(done, total)
                dataEntries.forEach { (path, entry) ->
                    extractEntry(zip, entry, root, path)
                    done++
                    onProgress(done, total)
                }
                prefEntries.forEach { (path, entry) ->
                    extractEntry(zip, entry, root, path)
                    done++
                    onProgress(done, total)
                }
                return ExtractedBackup(manifest, root, dataDir, prefsDir)
            }
        } catch (error: BackupException) {
            throw error
        } catch (error: ZipException) {
            throw BackupException("백업 ZIP 파일이 손상되었습니다.", error)
        } catch (error: IOException) {
            throw BackupException("백업을 풀지 못했습니다: ${error.message ?: "알 수 없는 오류"}", error)
        }
    }

    private fun extractEntry(zip: ZipFile, entry: ZipEntry, root: File, path: String) {
        val canonicalRoot = root.canonicalFile
        val target = File(root, path).canonicalFile
        val prefix = canonicalRoot.path + File.separator
        if (!target.path.startsWith(prefix)) {
            throw BackupException("임시 폴더 밖으로 벗어나는 경로입니다: $path")
        }
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, COPY_BUFFER)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun readText(zip: ZipFile, entry: ZipEntry, limit: Int): String {
        zip.getInputStream(entry).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw BackupException("백업 정보 파일이 너무 큽니다.")
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    // ------------------------------------------------------------- replace data

    /**
     * Replaces only [directories] below [filesDir]. Old directories are moved
     * aside first and restored if any step fails, so a failed restore does not
     * leave a half-replaced notebook or audio tree.
     */
    fun replaceDirectories(
        filesDir: File,
        stagedFilesDir: File,
        directories: List<String>,
        rollbackDir: File,
    ) {
        val installed = mutableListOf<String>()
        val movedAside = mutableListOf<String>()
        try {
            directories.sorted().forEach { name ->
                if (name !in DATA_DIRECTORIES) {
                    throw BackupException("복원할 수 없는 데이터 폴더입니다: $name")
                }
                val target = File(filesDir, name)
                val staged = File(stagedFilesDir, name)
                if (!staged.exists() && !staged.mkdirs()) {
                    throw BackupException("복원할 폴더를 준비하지 못했습니다: $name")
                }
                if (target.exists()) {
                    movePath(target, File(rollbackDir, name))
                    movedAside += name
                }
                movePath(staged, target)
                installed += name
            }
        } catch (error: Throwable) {
            for (name in installed.asReversed()) {
                runCatching { File(filesDir, name).deleteRecursively() }
            }
            for (name in movedAside.asReversed()) {
                val target = File(filesDir, name)
                runCatching { target.deleteRecursively() }
                runCatching { movePath(File(rollbackDir, name), target) }
            }
            throw error
        }
    }

    /**
     * Undoes a successful [replaceDirectories]: removes the installed copies and
     * moves the originals back from [rollbackDir]. Directories that did not exist
     * before the replacement are simply removed. Kept separate from
     * [replaceDirectories]'s internal rollback so a later failure (notably a
     * preference commit) can reverse an already-installed restore.
     */
    fun rollbackDirectories(filesDir: File, directories: List<String>, rollbackDir: File) {
        directories.forEach { name ->
            if (name !in DATA_DIRECTORIES) {
                throw BackupException("복원할 수 없는 데이터 폴더입니다: $name")
            }
            val target = File(filesDir, name)
            if (target.exists() && !target.deleteRecursively()) {
                throw BackupException("복원된 폴더를 정리하지 못했습니다: $name")
            }
            val backup = File(rollbackDir, name)
            if (backup.exists()) {
                movePath(backup, target)
            }
        }
    }

    private fun movePath(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.deleteRecursively()
        if (source.renameTo(target)) return
        if (source.isDirectory) {
            source.copyRecursively(target, overwrite = true)
        } else {
            source.copyTo(target, overwrite = true)
        }
        if (!source.deleteRecursively()) {
            throw BackupException("임시 파일을 정리하지 못했습니다: ${source.name}")
        }
    }

    // --------------------------------------------------------------- manifest

    fun parseManifest(text: String): Manifest {
        val obj = try {
            JSONObject(text)
        } catch (error: Exception) {
            throw BackupException("백업 정보(backup.json)를 읽을 수 없습니다.", error)
        }
        val version = obj.optInt("formatVersion", -1)
        if (version != FORMAT_VERSION) {
            throw BackupException("지원하지 않는 백업 형식입니다. (버전 $version)")
        }
        val createdAt = obj.optLong("createdAt", -1L)
        if (createdAt <= 0L) throw BackupException("백업 생성 시간이 올바르지 않습니다.")
        val includeSources = obj.optBoolean("includeSources", false)
        val appVersion = obj.optString("appVersion", "").trim().take(128)
        val fileCount = obj.optInt("fileCount", -1)
        if (fileCount < 0) throw BackupException("백업 파일 수가 올바르지 않습니다.")
        val array = obj.optJSONArray("directories") ?: JSONArray()
        val directories = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val name = array.optString(index, "").trim()
            if (name.isEmpty()) throw BackupException("백업 폴더 이름이 비어 있습니다.")
            if (name !in DATA_DIRECTORIES) {
                throw BackupException("백업에 알 수 없는 데이터 폴더가 있습니다: $name")
            }
            if (name == "sources" && !includeSources) {
                throw BackupException("PDF 원본을 포함하지 않은 백업에 sources 폴더가 있습니다.")
            }
            if (name in directories) throw BackupException("백업 폴더가 중복되었습니다: $name")
            directories += name
        }
        return Manifest(
            formatVersion = version,
            createdAt = createdAt,
            includeSources = includeSources,
            appVersion = appVersion,
            fileCount = fileCount,
            directories = directories,
        )
    }

    // ---------------------------------------------------------------- path safety

    /**
     * Returns the entry path without a trailing slash, or null when the name is
     * absolute, contains a backslash/NUL/drive prefix, or has `.`/`..`/empty
     * segments. Callers still resolve against the extraction root as a second
     * line of defence.
     */
    fun safeEntryPath(raw: String): String? {
        if (raw.isEmpty() || raw.length > MAX_ENTRY_NAME_LENGTH) return null
        if (raw.indexOf('\u0000') >= 0 || raw.indexOf('\\') >= 0) return null
        if (raw.startsWith('/') || raw.startsWith('\\')) return null
        if (raw.length >= 2 && raw[1] == ':') return null
        val withoutTrailingSlash = raw.trimEnd('/')
        if (withoutTrailingSlash.isEmpty()) return null
        val parts = withoutTrailingSlash.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return withoutTrailingSlash
    }
}

// --------------------------------------------------------------------- prefs

private data class PreferenceValue(val name: String, val value: Any)

private data class RestoredPreferences(val fileName: String, val values: List<PreferenceValue>)

private fun readPreferences(prefsDir: File): List<RestoredPreferences> {
    if (!prefsDir.isDirectory) return emptyList()
    return BackupArchive.PREFERENCE_FILES.mapNotNull { name ->
        val file = File(prefsDir, name)
        if (!file.isFile) return@mapNotNull null
        RestoredPreferences(name, parsePreferencesXml(file))
    }
}

private fun parsePreferencesXml(file: File): List<PreferenceValue> {
    val values = mutableListOf<PreferenceValue>()
    try {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        file.inputStream().buffered().use { input ->
            parser.setInput(input, "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.START_TAG) {
                if (event == XmlPullParser.END_DOCUMENT) {
                    throw BackupException("환경설정 파일이 비어 있습니다: ${file.name}")
                }
                event = parser.next()
            }
            if (parser.name != "map") {
                throw BackupException("환경설정 파일 형식이 올바르지 않습니다: ${file.name}")
            }
            while (true) {
                event = parser.next()
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val key = parser.getAttributeValue(null, "name")
                            ?: throw BackupException("환경설정 이름이 없습니다: ${file.name}")
                        val type = parser.name
                        val value: Any = when (type) {
                            "string" -> parser.nextText()
                            "boolean" -> when (parser.nextText().trim()) {
                                "true" -> true
                                "false" -> false
                                else -> throw BackupException("환경설정 값이 올바르지 않습니다: $key")
                            }
                            "int" -> parser.nextText().trim().toIntOrNull()
                                ?: throw BackupException("환경설정 값이 올바르지 않습니다: $key")
                            "long" -> parser.nextText().trim().toLongOrNull()
                                ?: throw BackupException("환경설정 값이 올바르지 않습니다: $key")
                            "float" -> parser.nextText().trim().toFloatOrNull()
                                ?: throw BackupException("환경설정 값이 올바르지 않습니다: $key")
                            "set" -> readStringSet(parser)
                            else -> throw BackupException("지원하지 않는 환경설정 형식입니다: $type")
                        }
                        values += PreferenceValue(key, value)
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "map") break
                    }
                    XmlPullParser.END_DOCUMENT -> {
                        throw BackupException("환경설정 파일이 완전하지 않습니다: ${file.name}")
                    }
                }
            }
        }
    } catch (error: BackupException) {
        throw error
    } catch (error: Exception) {
        throw BackupException("환경설정 파일을 읽을 수 없습니다: ${file.name}", error)
    }
    return values
}

private fun readStringSet(parser: XmlPullParser): Set<String> {
    val values = LinkedHashSet<String>()
    while (true) {
        when (parser.next()) {
            XmlPullParser.START_TAG -> {
                if (parser.name != "string") {
                    throw BackupException("환경설정 문자열 목록 형식이 올바르지 않습니다.")
                }
                values += parser.nextText()
            }
            XmlPullParser.END_TAG -> return values
            XmlPullParser.END_DOCUMENT -> {
                throw BackupException("환경설정 문자열 목록이 완전하지 않습니다.")
            }
        }
    }
}

private fun applyPreferences(context: Context, restored: List<RestoredPreferences>) {
    restored.forEach { prefsFile ->
        val name = prefsFile.fileName.removeSuffix(".xml")
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        prefsFile.values.forEach { entry ->
            when (val value = entry.value) {
                is String -> editor.putString(entry.name, value)
                is Boolean -> editor.putBoolean(entry.name, value)
                is Int -> editor.putInt(entry.name, value)
                is Long -> editor.putLong(entry.name, value)
                is Float -> editor.putFloat(entry.name, value)
                is Set<*> -> editor.putStringSet(
                    entry.name,
                    value.mapNotNull { it as? String }.toSet(),
                )
                else -> Unit
            }
        }
        if (!editor.commit()) {
            throw BackupException("환경설정을 저장하지 못했습니다: $name")
        }
    }
}
