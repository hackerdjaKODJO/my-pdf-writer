package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** One small JSON file per notebook; writes are verified before reporting success. */
class NotebookStore private constructor(
    private val dir: File,
    private val io: NotebookStoreIo,
) {

    constructor(context: Context) : this(
        File(context.filesDir, "notebooks").apply { mkdirs() },
        FileNotebookStoreIo,
    )

    internal constructor(dir: File) : this(dir, FileNotebookStoreIo)

    /** JVM-testable file-backed constructor with IO fault injection. */
    internal constructor(dir: File, io: NotebookStoreIo) : this(dir, io)

    fun loadAll(): List<Notebook> {
        val ids = dir.listFiles { file -> file.isFile && sidecarId(file) != null }
            ?.mapNotNull(::sidecarId)
            ?.toSet()
            ?: return emptyList()
        return ids.mapNotNull(::load).sortedByDescending { it.updatedAt }
    }

    fun load(id: String): Notebook? = readWithTmpRecovery(File(dir, "$id.json")) {
        Notebook.fromJson(JSONObject(it))
    }

    /**
     * Writes and fsyncs a complete tmp, publishes it, then reads the canonical
     * file back and compares normalized JSON. The tmp is deliberately retained
     * until that verification succeeds, so every failure leaves a recovery
     * source instead of silently claiming that the edit is durable.
     */
    fun save(notebook: Notebook): Boolean = runCatching {
        if (notebook.id.isBlank() || File(notebook.id).name != notebook.id || dir.isFile) {
            return@runCatching false
        }
        if (!dir.isDirectory && !dir.mkdirs()) return@runCatching false
        val normalized = notebook.normalized()
        val expected = normalized.toJson().toString()
        val target = File(dir, "${normalized.id}.json")
        val tmp = File(target.path + ".tmp")
        io.writeTmp(tmp, expected)
        io.publish(tmp, target)
        val restored = Notebook.fromJson(JSONObject(io.readCanonical(target))).normalized()
        if (restored.toJson().toString() != expected) return@runCatching false
        if (!tmp.delete()) return@runCatching false
        true
    }.getOrDefault(false)

    /** Removes the recovery source first, then the verified canonical sidecar. */
    fun delete(id: String): Boolean = deleteSidecarWithTmp(File(dir, "$id.json"))
}

/** Small IO seam so verification and tmp-retention failures are JVM-testable. */
internal interface NotebookStoreIo {
    fun writeTmp(tmp: File, content: String)
    fun publish(tmp: File, target: File)
    fun readCanonical(target: File): String
}

internal object FileNotebookStoreIo : NotebookStoreIo {
    override fun writeTmp(tmp: File, content: String) {
        tmp.parentFile?.mkdirs()
        FileOutputStream(tmp).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    /**
     * Copy rather than rename so the fsynced tmp remains available if canonical
     * readback fails. A torn canonical copy is invalid JSON and therefore falls
     * back to the complete tmp on the next load.
     */
    override fun publish(tmp: File, target: File) {
        tmp.copyTo(target, overwrite = true)
        FileOutputStream(target).use { output -> output.fd.sync() }
    }

    override fun readCanonical(target: File): String {
        if (!target.isFile) throw IOException("Canonical notebook is missing")
        return target.readText()
    }
}
