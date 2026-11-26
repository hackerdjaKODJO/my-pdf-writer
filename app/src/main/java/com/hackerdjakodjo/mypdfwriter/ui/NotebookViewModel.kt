package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.data.AudioNoteStore
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplateStore
import com.hackerdjakodjo.mypdfwriter.data.MaskingCards
import com.hackerdjakodjo.mypdfwriter.data.Memos
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotebookDraft
import com.hackerdjakodjo.mypdfwriter.data.NotebookStore
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import com.hackerdjakodjo.mypdfwriter.data.QuickNoteCapture
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExporter
import com.hackerdjakodjo.mypdfwriter.pdf.noteExportFileName
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal const val NOTE_SAVE_FAILURE = "노트를 저장하지 못했습니다. 다시 시도해 주세요."

/** Typed failure used by the synchronous backup barrier. */
class NotebookSaveException(
    message: String = NOTE_SAVE_FAILURE,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Truthful outcome of one study capture, including the appended notebook page. */
data class StudyAppendResult(
    val notebookId: String,
    val pageIndex: Int,
    val saved: Boolean,
    val notebook: Notebook,
    val error: String? = null,
)

/**
 * Replaces (or inserts) one notebook in the loaded library and keeps the same
 * descending `updatedAt` order as [NotebookStore.loadAll]. Avoids a full
 * directory scan + JSON parse on every settled stroke.
 */
internal fun mergeNotebookIntoLibrary(
    library: List<Notebook>,
    updated: Notebook,
): List<Notebook> {
    val merged = library.filterNot { it.id == updated.id }.toMutableList()
    merged.add(updated)
    return merged.sortedByDescending { it.updatedAt }
}

/**
 * The ViewModel owns the current page index; the editor's working notebook may
 * still hold an older value. Keep the newer page when merging an editor save so
 * a "swipe then write" flow does not lose the resume position.
 */
internal fun mergeResumePage(incoming: Notebook, previous: Notebook?): Notebook {
    if (previous == null || previous.id != incoming.id) return incoming
    return incoming.copy(lastPageIndex = previous.lastPageIndex)
}

/** URI/docId-aware lookup supplied by the link-model integration. */
internal fun findStudyNotebook(notebooks: List<Notebook>, source: StudySource): Notebook? =
    notebooks.firstOrNull { !it.trashed && studySourcesMatch(it, source) }

/**
 * Capture is stricter than opening: when the active bytes have a fingerprint, a
 * legacy URI-only notebook must not silently absorb a replacement PDF's content.
 */
internal fun findStudyNotebookForCapture(
    notebooks: List<Notebook>,
    source: StudySource,
): Notebook? {
    val fingerprint = source.contentFingerprint?.trim()?.takeIf { it.isNotEmpty() }
        ?: return findStudyNotebook(notebooks, source)
    return notebooks.firstOrNull { notebook ->
        !notebook.trashed && studySourcesMatch(notebook, source) &&
            notebook.pages.any { page ->
                page.sourceLink?.normalized()?.fingerprint == fingerprint
            }
    }
}

/** Pure capture policy shared by the ViewModel and JVM result tests. */
internal fun appendStudyExcerptResult(
    source: StudySource,
    text: String,
    pageIndex: Int,
    findExisting: () -> Notebook?,
    save: (Notebook) -> Boolean,
): StudyAppendResult = appendStudyExcerptResult(
    capture = StudyCapture(
        text = text,
        source = source,
        sourcePageIndex = pageIndex,
    ),
    findExisting = findExisting,
    save = save,
)

/** Full-provenance capture policy used after the viewer revalidates identity/page. */
internal fun appendStudyExcerptResult(
    capture: StudyCapture,
    findExisting: () -> Notebook?,
    save: (Notebook) -> Boolean,
): StudyAppendResult {
    val trimmed = capture.text.trim()
    val excerpt = Excerpt(
        id = capture.excerptId ?: java.util.UUID.randomUUID().toString(),
        pageIndex = capture.sourcePageIndex.coerceAtLeast(0),
        text = trimmed,
    )
    val existing = findExisting()
    val updated = if (existing != null) {
        StudyNoteBuilder.appendCapture(existing, capture)
    } else {
        StudyNoteBuilder.fromCapture(
            title = StudyNoteBuilder.suggestedTitle(capture.source),
            capture = capture,
        )
    }
    if (trimmed.isBlank()) {
        return StudyAppendResult(
            notebookId = updated.id,
            pageIndex = -1,
            saved = false,
            notebook = updated,
        )
    }
    val saved = save(updated)
    return StudyAppendResult(
        notebookId = updated.id,
        pageIndex = updated.pages.lastIndex,
        saved = saved,
        notebook = updated,
        error = if (saved) null else NOTE_SAVE_FAILURE,
    )
}

private const val PAGE_SAVE_DELAY_MS = 350L

/**
 * Android-free save state used by [NotebookViewModel]. Keeping the failure,
 * retry and close policy here makes the lifecycle guarantees JVM-testable while
 * the ViewModel remains responsible for coroutines, tracing and the write gate.
 */
internal class NotebookSaveSession(
    private val write: (Notebook) -> Boolean,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _notebooks = MutableStateFlow(emptyList<Notebook>())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    private val _current = MutableStateFlow<Notebook?>(null)
    val current: StateFlow<Notebook?> = _current.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private var pendingRetry: (() -> Boolean)? = null

    fun setLibrary(notebooks: List<Notebook>) {
        _notebooks.value = notebooks
    }

    fun open(notebook: Notebook) {
        val normalized = notebook.normalized()
        _current.value = normalized
        _pageIndex.value = normalized.resumePageIndex()
    }

    fun updateCurrent(
        notebook: Notebook,
        touchTimestamp: Boolean = true,
        preserveResumePage: Boolean = true,
    ): Notebook {
        val prepared = prepare(notebook, touchTimestamp)
        val updated = if (preserveResumePage) mergeResumePage(prepared, _current.value) else prepared
        _current.value = updated
        _pageIndex.value = _pageIndex.value.coerceIn(0, (updated.pages.size - 1).coerceAtLeast(0))
        return updated
    }

    fun setPageIndex(index: Int): Notebook? {
        val current = _current.value ?: return null
        val safe = index.coerceIn(0, (current.pages.size - 1).coerceAtLeast(0))
        if (safe == current.lastPageIndex && _pageIndex.value == safe) return current
        _pageIndex.value = safe
        return updateCurrent(
            current.copy(lastPageIndex = safe),
            touchTimestamp = false,
            preserveResumePage = false,
        )
    }

    fun saveSnapshot(notebook: Notebook): Boolean =
        writeSnapshot(notebook) { saveSnapshot(notebook) }

    fun saveNow(): Boolean {
        val notebook = _current.value ?: return true
        return writeSnapshot(notebook) { saveNow() }
    }

    fun close(): Boolean {
        if (!saveNow()) return false
        clearCurrent()
        return true
    }

    fun clearCurrent() {
        _current.value = null
        _pageIndex.value = 0
    }

    fun trySaveDetached(
        notebook: Notebook,
        preserveResumePage: Boolean = true,
        touchTimestamp: Boolean = true,
    ): Notebook? {
        val prepared = prepare(notebook, touchTimestamp)
        val updated = if (preserveResumePage) mergeResumePage(prepared, _current.value) else prepared
        return if (writeSnapshot(updated) { saveSnapshot(updated) }) updated else null
    }

    fun setDetachedPageIndex(notebook: Notebook, index: Int): Notebook? {
        val safe = index.coerceIn(0, (notebook.pages.size - 1).coerceAtLeast(0))
        return trySaveDetached(
            notebook.copy(lastPageIndex = safe),
            preserveResumePage = false,
            touchTimestamp = false,
        )
    }

    /** Retries the newest failed snapshot, or flushes the live editor. */
    fun retrySave(): Boolean {
        val retry = pendingRetry
        return if (retry != null) retry() else saveNow()
    }

    /** Backup must drain detached failures as well as the live editor. */
    fun flushPendingSavesBeforeBackup(): Boolean {
        val retry = pendingRetry
        if (retry != null && !retry()) return false
        return saveNow()
    }

    fun requireBackupFlush() {
        if (!flushPendingSavesBeforeBackup()) {
            throw NotebookSaveException(_saveError.value ?: NOTE_SAVE_FAILURE)
        }
    }

    fun publishSaveSuccess(notebook: Notebook, library: List<Notebook>? = null) {
        _notebooks.value = library ?: mergeNotebookIntoLibrary(_notebooks.value, notebook)
        clearSaveError()
    }

    fun publishSaveFailure(notebook: Notebook) {
        reportSaveFailure(NOTE_SAVE_FAILURE) { saveSnapshot(notebook) }
    }

    fun reportSaveFailure(message: String, retry: () -> Boolean) {
        _saveError.value = message
        pendingRetry = retry
    }

    fun clearSaveError() {
        _saveError.value = null
        pendingRetry = null
    }

    fun discardCurrent() {
        _current.value = null
        _pageIndex.value = 0
        // A restore invalidates every pre-restore snapshot, including a detached
        // pane failure that retrySave might otherwise write over restored data.
        clearSaveError()
    }

    fun removeFromLibrary(id: String) {
        _notebooks.value = _notebooks.value.filterNot { it.id == id }
    }

    private fun prepare(notebook: Notebook, touchTimestamp: Boolean): Notebook {
        val normalized = notebook.normalized()
        return if (touchTimestamp) normalized.copy(updatedAt = nowMs()) else normalized
    }

    private fun writeSnapshot(notebook: Notebook, retry: () -> Boolean): Boolean {
        val snapshot = notebook.normalized()
        val saved = runCatching { write(snapshot) }.getOrDefault(false)
        if (!saved) {
            reportSaveFailure(NOTE_SAVE_FAILURE, retry)
            return false
        }
        publishSaveSuccess(snapshot)
        return true
    }
}

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = NotebookStore(application)
    private val resolver = application.contentResolver
    private val customTemplateStore = CustomTemplateStore(application)

    /**
     * Guards every notebook JSON write and every load-all scan. The debounced
     * writer holds this on IO; [saveNow] takes it on the calling thread so a
     * close/backup flush can never interleave with an in-flight write.
     */
    private val saveLock = NotebookWriteGate { store.save(it) }
    private val saveSession = NotebookSaveSession(::saveLocked)

    val notebooks: StateFlow<List<Notebook>> = saveSession.notebooks
    val current: StateFlow<Notebook?> = saveSession.current
    val pageIndex: StateFlow<Int> = saveSession.pageIndex
    val saveError: StateFlow<String?> = saveSession.saveError

    private var saveJob: Job? = null

    /** Monotonic id of the newest scheduled write; older jobs may not publish. */
    @Volatile
    private var saveGeneration = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = saveLock.locked { store.loadAll() }
            saveSession.setLibrary(loaded)
        }
    }

    private fun saveLocked(notebook: Notebook): Boolean = saveLock.locked { store.save(notebook) }

    fun open(id: String) {
        // Reopening the same document must observe its pending final write. A
        // failed flush retains the live notebook instead of replacing it with
        // stale disk data.
        if (current.value != null && !saveNow()) return
        val notebook = saveLock.locked {
            store.load(id) ?: notebooks.value.firstOrNull { it.id == id }
        } ?: return
        saveSession.open(notebook)
    }

    fun close(): Boolean {
        if (!saveNow()) return false
        saveSession.clearCurrent()
        return true
    }

    /**
     * Drops the live editor notebook after an external restore or merge without
     * writing it back over the freshly restored files. Any debounced write is
     * cancelled and its generation invalidated, so a job already past its delay
     * can neither write nor publish a stale snapshot. Unlike [close] this never
     * calls [saveNow]; the caller keeps control of what reaches disk.
     */
    fun discardCurrent() {
        saveJob?.cancel()
        saveJob = null
        val discarded = discardNotebookState(saveGeneration)
        saveGeneration = discarded.saveGeneration
        saveSession.discardCurrent()
        // Wait for any writer that already passed its generation check. Restore
        // callers may replace files as soon as this method returns.
        saveLock.locked { }
        NoteTrace.event(
            "NOTE_VM",
            "discard_current",
            "generation" to saveGeneration,
        )
    }

    fun create(draft: NotebookDraft): Notebook? {
        val notebook = Notebook(
            title = draft.title.ifBlank { "새 노트" },
            coverColor = draft.coverColor,
            coverStyle = draft.coverStyle,
            folder = draft.folder.trim(),
            tags = draft.tags,
            pages = listOf(
                draft.quickKind?.let { QuickNoteCapture.page(it) }
                    ?: NotePage(template = draft.template),
            ),
        )
        if (!saveSession.saveSnapshot(notebook)) return null
        open(notebook.id)
        return notebook
    }

    /** Persists a notebook produced elsewhere and opens it only after verification. */
    fun importNotebook(notebook: Notebook): Notebook? {
        if (!saveNow()) return null
        val imported = notebook.normalized().copy(updatedAt = System.currentTimeMillis())
        if (!saveSession.saveSnapshot(imported)) return null
        open(imported.id)
        return imported
    }

    /**
     * Captures one selection into the URI/docId-matched study notebook. The
     * canonical library is read under the write gate so a detached pane cannot
     * append to a stale snapshot and overwrite restored data.
     */
    fun appendStudyExcerptResult(source: StudySource, text: String, pageIndex: Int): StudyAppendResult =
        saveLock.locked {
            val result = appendStudyExcerptResult(
                source = source,
                text = text,
                pageIndex = pageIndex,
                findExisting = { findStudyNotebook(store.loadAll(), source) },
                save = ::saveLocked,
            )
            if (result.saved) {
                if (current.value?.id == result.notebookId) {
                    saveSession.updateCurrent(result.notebook, touchTimestamp = false)
                }
                saveSession.publishSaveSuccess(result.notebook)
            } else if (result.error != null) {
                saveSession.reportSaveFailure(result.error) {
                    appendStudyExcerptResult(source, text, pageIndex).saved
                }
            }
            result
        }

    /** Full-provenance append after PdfViewModel has revalidated the active bytes/page. */
    fun appendStudyCaptureResult(capture: PdfStudyCapture): StudyAppendResult =
        saveLock.locked {
            val result = appendStudyExcerptResult(
                capture = StudyCapture(
                    text = capture.text,
                    source = capture.source,
                    sourcePageIndex = capture.sourcePage,
                    rect = capture.rect,
                    excerptId = capture.excerptId,
                    rotation = capture.rotation,
                ),
                findExisting = {
                    findStudyNotebookForCapture(store.loadAll(), capture.source)
                },
                save = ::saveLocked,
            )
            if (result.saved) {
                if (current.value?.id == result.notebookId) {
                    saveSession.updateCurrent(result.notebook, touchTimestamp = false)
                }
                saveSession.publishSaveSuccess(result.notebook)
            } else if (result.error != null) {
                saveSession.reportSaveFailure(result.error) {
                    appendStudyCaptureResult(capture).saved
                }
            }
            result
        }

    /** Compatibility wrapper; callers that need truthfulness use the result API. */
    fun appendStudyExcerpt(source: StudySource, text: String, pageIndex: Int): Notebook =
        appendStudyExcerptResult(source, text, pageIndex).notebook

    /** Resolves a canonical, URI/docId-matched study notebook without opening it. */
    fun studyNoteFor(source: StudySource): Notebook? = saveLock.locked {
        findStudyNotebook(store.loadAll(), source)
    }

    /** Creates and verifies a blank study notebook without making it current. */
    fun createBlankStudyNote(source: StudySource): Notebook? {
        val notebook = StudyNoteBuilder.blankStudyNote(source)
        if (!saveSession.saveSnapshot(notebook)) return null
        NoteTrace.event(
            "NOTE_VM",
            "create_detached",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "source" to notebook.sourceDocumentName,
        )
        return notebook
    }

    /**
     * Compatibility wrapper for detached saves. A failure is exposed through
     * [saveError]; new callers should use [trySaveDetached] and get null.
     */
    fun saveDetached(notebook: Notebook): Notebook =
        trySaveDetached(notebook) ?: notebook

    /** Returns null unless canonical readback verified the detached snapshot. */
    fun trySaveDetached(notebook: Notebook): Notebook? {
        val startedAt = System.currentTimeMillis()
        val updated = saveSession.trySaveDetached(notebook)
        NoteTrace.event(
            "NOTE_VM",
            if (updated == null) "save_detached_failed" else "save_detached",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to updated?.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        return updated
    }

    /** Persists detached-pane navigation without changing Home ordering. */
    fun setDetachedPageIndex(notebook: Notebook, index: Int): Notebook? {
        val updated = saveSession.setDetachedPageIndex(notebook, index)
        NoteTrace.event(
            "NOTE_VM",
            if (updated == null) "save_detached_page_failed" else "save_detached_page",
            "id" to notebook.id,
            "page" to index,
            "lastPageIndex" to updated?.lastPageIndex,
        )
        return updated
    }

    fun updateCurrent(notebook: Notebook) {
        val previous = current.value
        NoteTrace.event(
            "NOTE_VM",
            "update_in",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "ann" to noteAnnotationCounts(notebook),
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
        )

        val updated = saveSession.updateCurrent(notebook)
        if (previous != null && previous.id == updated.id) {
            logAnnotationDecreases(previous, updated)
        }
        NoteTrace.event(
            "NOTE_VM",
            "update_live",
            "id" to updated.id,
            "pages" to updated.pages.size,
            "ann" to noteAnnotationCounts(updated),
            "total" to noteAnnotationTotal(updated),
            "updatedAt" to updated.updatedAt,
        )

        // This newer snapshot supersedes any older failed retry.
        saveSession.clearSaveError()
        saveJob?.cancel()
        val generation = nextSaveGeneration(saveGeneration)
        saveGeneration = generation
        NoteTrace.event(
            "NOTE_VM",
            "save_scheduled",
            "id" to updated.id,
            "pages" to updated.pages.size,
            "total" to noteAnnotationTotal(updated),
            "updatedAt" to updated.updatedAt,
            "generation" to generation,
        )
        saveJob = viewModelScope.launch {
            delay(PAGE_SAVE_DELAY_MS)
            if (!isLatestSaveGeneration(generation, saveGeneration)) return@launch
            val result = withContext(Dispatchers.IO) { persistSnapshot(updated, generation) }
            publishSnapshotWrite(updated, result, generation)
        }
    }

    /**
     * Writes one snapshot under [saveLock]. The generation is checked only after
     * acquiring the lock, closing the race where a cancelled job could otherwise
     * wait behind deletion and then write its stale snapshot afterwards.
     */
    private fun persistSnapshot(
        notebook: Notebook,
        generation: Long? = null,
    ): SnapshotWriteResult = saveLock.locked {
        if (generation != null && !isLatestSaveGeneration(generation, saveGeneration)) {
            return@locked SnapshotWriteResult(saved = false, superseded = true)
        }
        val saved = saveLocked(notebook)
        if (!saved) return@locked SnapshotWriteResult(saved = false)
        if (generation == null || isLatestSaveGeneration(generation, saveGeneration)) {
            // Publish while still holding the write gate so a detached save
            // cannot land between disk publication and the library update.
            saveSession.publishSaveSuccess(notebook)
        }
        SnapshotWriteResult(saved = true)
    }

    private fun publishSnapshotWrite(
        notebook: Notebook,
        result: SnapshotWriteResult,
        generation: Long,
    ) {
        when {
            result.superseded -> NoteTrace.event(
                "NOTE_VM",
                "save_superseded",
                "id" to notebook.id,
                "generation" to generation,
                "latest" to saveGeneration,
            )
            !result.saved -> {
                if (isLatestSaveGeneration(generation, saveGeneration)) {
                    saveSession.publishSaveFailure(notebook)
                    NoteTrace.event("NOTE_VM", "save_failed", "id" to notebook.id)
                }
            }
            isLatestSaveGeneration(generation, saveGeneration) -> {
                NoteTrace.event(
                    "NOTE_VM",
                    "save_done",
                    "id" to notebook.id,
                    "pages" to notebook.pages.size,
                    "total" to noteAnnotationTotal(notebook),
                    "updatedAt" to notebook.updatedAt,
                )
            }
        }
    }

    /** Persists a page-only change without touching updatedAt or Home ordering. */
    private fun schedulePageOnlySave(notebook: Notebook) {
        saveSession.clearSaveError()
        saveJob?.cancel()
        val generation = nextSaveGeneration(saveGeneration)
        saveGeneration = generation
        saveJob = viewModelScope.launch {
            delay(PAGE_SAVE_DELAY_MS)
            if (!isLatestSaveGeneration(generation, saveGeneration)) return@launch
            val result = withContext(Dispatchers.IO) { persistSnapshot(notebook, generation) }
            publishSnapshotWrite(notebook, result, generation)
        }
    }

    fun setPageIndex(index: Int) {
        val updated = saveSession.setPageIndex(index) ?: return
        // PDF resume is debounce-saved on page change; notebooks must behave the
        // same, otherwise process death loses the page a student was reading.
        schedulePageOnlySave(updated)
    }

    fun addPage(template: PaperTemplate? = null) {
        val current = current.value ?: return
        val previous = current.pages.lastOrNull()
        val pageTemplate = template ?: previous?.template ?: PaperTemplate.LINED
        val pages = current.pages + NotePage(
            template = pageTemplate,
            orientation = previous?.orientation ?: PageOrientation.PORTRAIT,
            tint = previous?.tint ?: PaperTint.WARM,
        )
        updateCurrent(current.copy(pages = pages))
        setPageIndex(pages.lastIndex)
    }

    fun duplicatePage(index: Int) {
        val current = current.value ?: return
        val updated = duplicatePageInNotebook(current, index) ?: return
        updateCurrent(updated)
        setPageIndex(index + 1)
    }

    fun deletePage(index: Int) {
        val current = current.value ?: return
        if (current.pages.size <= 1 || index !in current.pages.indices) return
        val pages = current.pages.toMutableList().also { it.removeAt(index) }
        updateCurrent(current.copy(pages = pages))
        setPageIndex(pageIndex.value.coerceIn(0, pages.lastIndex))
    }

    fun setFavorite(id: String, favorite: Boolean) = mutate(id) { it.copy(favorite = favorite) }

    fun toggleFavorite(id: String) {
        val notebook = saveLock.locked { store.load(id) }
            ?: notebooks.value.firstOrNull { it.id == id } ?: return
        setFavorite(id, !notebook.favorite)
    }

    fun trash(id: String) = mutate(id) { it.copy(trashed = true) }

    fun restore(id: String) = mutate(id) { it.copy(trashed = false) }

    /**
     * Permanent deletion is serialized with writes. Owner audio is removed only
     * after both canonical and tmp notebook sidecars are verified absent.
     */
    fun deleteForever(id: String): Boolean {
        saveJob?.cancel()
        saveJob = null
        saveGeneration = nextSaveGeneration(saveGeneration)
        return saveLock.locked {
            if (!store.delete(id)) return@locked false
            if (current.value?.id == id) saveSession.discardCurrent()
            saveSession.removeFromLibrary(id)
            val audioDir = AudioNoteStore(getApplication()).audioDirectory(id)
            val removed = audioDir.deleteRecursively() && !audioDir.exists()
            NoteTrace.event(
                "NOTE_VM",
                if (removed) "delete_done" else "delete_audio_failed",
                "id" to id,
            )
            removed
        }
    }

    fun rename(id: String, title: String) = mutate(id) {
        it.copy(title = title.trim().ifBlank { it.title })
    }

    fun moveFolder(id: String, folder: String) = mutate(id) {
        it.copy(folder = folder.trim())
    }

    fun setTags(id: String, tags: List<String>) = mutate(id) {
        it.copy(tags = tags.map(String::trim).filter(String::isNotBlank).distinct())
    }

    fun exportSuggestedName(): String = suggestedExportName(current.value?.title)

    fun exportSuggestedName(notebook: Notebook): String = suggestedExportName(notebook.title)

    fun exportCurrent(uri: Uri, options: NoteExportOptions = NoteExportOptions()) {
        current.value?.let { exportNotebook(it, uri, options) }
    }

    /** Exports a detached pane notebook through the same launcher as the editor. */
    fun exportDetached(
        notebook: Notebook,
        uri: Uri,
        options: NoteExportOptions = NoteExportOptions(),
    ) {
        exportNotebook(notebook, uri, options)
    }

    private fun exportNotebook(notebook: Notebook, uri: Uri, options: NoteExportOptions) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { stream ->
                        NoteExporter.export(notebook, stream, options, customTemplateStore::load)
                    } ?: error("출력 파일을 열 수 없습니다.")
                }
            }
            Toast.makeText(
                getApplication(),
                if (result.isSuccess) "PDF로 내보냈습니다." else "내보내지 못했습니다: ${result.exceptionOrNull()?.message}",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun refresh() {
        saveSession.setLibrary(saveLock.locked { store.loadAll() })
    }

    /** Synchronous verified flush used by close, Back and backup barriers. */
    fun saveNow(): Boolean {
        saveJob?.cancel()
        saveJob = null
        // Invalidate any debounced job that already finished its IO and is about
        // to publish, so its older library cannot overwrite this flush.
        saveGeneration = nextSaveGeneration(saveGeneration)
        val notebook = current.value
        if (notebook == null) {
            NoteTrace.event("NOTE_VM", "save_now_skip", "reason" to "no_current")
            return true
        }
        NoteTrace.event(
            "NOTE_VM",
            "save_now_start",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
        )
        val startedAt = System.currentTimeMillis()
        val saved = saveSession.saveNow()
        NoteTrace.event(
            "NOTE_VM",
            if (saved) "save_now_done" else "save_now_failed",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        return saved
    }

    fun retrySave(): Boolean {
        val saved = saveSession.retrySave()
        NoteTrace.event("NOTE_VM", if (saved) "retry_save_done" else "retry_save_failed")
        return saved
    }

    /** Boolean API for hosts that want to render their own backup failure. */
    fun flushPendingSavesBeforeBackup(): Boolean = saveSession.flushPendingSavesBeforeBackup()

    /** Unit-returning barrier that propagates a typed failure to BackupViewModel. */
    fun requireBackupFlush() = saveSession.requireBackupFlush()

    private fun mutate(id: String, block: (Notebook) -> Notebook) {
        val original = current.value?.takeIf { it.id == id }
            ?: saveLock.locked { store.load(id) }
            ?: notebooks.value.firstOrNull { it.id == id }
            ?: return
        val updated = block(original).normalized().copy(updatedAt = System.currentTimeMillis())
        if (current.value?.id == id) {
            saveSession.updateCurrent(updated, touchTimestamp = false)
        }
        saveSession.saveSnapshot(updated)
    }
}

private data class SnapshotWriteResult(
    val saved: Boolean,
    val superseded: Boolean = false,
)

private fun suggestedExportName(title: String?): String = noteExportFileName(title)

private fun noteAnnotationCounts(notebook: Notebook): String =
    notebook.pages.joinToString(",") { it.annotations.size.toString() }

private fun noteAnnotationTotal(notebook: Notebook): Int =
    notebook.pages.sumOf { it.annotations.size }

/**
 * Pure save-ordering policy behind [NotebookViewModel.updateCurrent] and
 * [NotebookViewModel.saveNow]. Every new update supersedes the pending debounced
 * write; a job whose generation is no longer the newest must neither write after
 * nor publish over a newer snapshot. Keeping this JVM-pure lets the ordering that
 * protects multi-stroke edits be tested without an Android application.
 */
internal fun nextSaveGeneration(current: Long): Long = current + 1L

internal fun isLatestSaveGeneration(candidate: Long, latest: Long): Boolean = candidate == latest

/**
 * The single re-entrant lock behind every notebook JSON write. [NotebookStore]
 * writes each notebook through a fixed `<id>.json.tmp` file, so two overlapping
 * saves of the same id would truncate each other's tmp file. [locked] is the one
 * funnel all VM write/load sites use; [writeLocked] remains as a compatibility
 * helper for serialization tests and adapters.
 */
internal class NotebookWriteGate(private val write: (Notebook) -> Unit) {
    private val lock = Any()

    fun writeLocked(notebook: Notebook) {
        synchronized(lock) { write(notebook) }
    }

    fun <T> locked(block: () -> T): T = synchronized(lock, block)
}

/** Pure outcome of dropping the live editor notebook after an external change. */
internal data class DiscardedNotebookState(
    val current: Notebook?,
    val pageIndex: Int,
    val saveGeneration: Long,
)

/**
 * A discard clears the in-memory notebook and its page without persisting, and
 * advances the generation so any in-flight debounced write is ignored. Kept pure
 * so the restore/merge policy is verifiable without an Android application.
 */
internal fun discardNotebookState(saveGeneration: Long): DiscardedNotebookState =
    DiscardedNotebookState(
        current = null,
        pageIndex = 0,
        saveGeneration = nextSaveGeneration(saveGeneration),
    )

/**
 * Pure page duplication behind [NotebookViewModel.duplicatePage]. Kept free of
 * Android state so the fan-out of study and memo ids stays JVM-tested.
 *
 * The original page and its cards are untouched; the copy owns fresh page,
 * annotation, checklist, masking-card and memo-card ids while content, tints,
 * geometry, study state and timestamps travel with it in order.
 */
internal fun duplicateNotePage(page: NotePage): NotePage = page.copy(
    id = java.util.UUID.randomUUID().toString(),
    annotations = page.annotations.map {
        it.copy(id = java.util.UUID.randomUUID().toString())
    },
    // Study data and post-its travel with the page, but each copy keeps its own
    // ids so an edit on one page can never change the other.
    checklists = Checklists.freshIds(page.checklists),
    maskingCards = MaskingCards.freshIds(page.maskingCards),
    memoCards = Memos.freshIds(page.memoCards),
)

/**
 * Inserts one duplicate directly after [index] and returns the whole notebook as
 * a single new value, so the caller commits one change. Returns null when the
 * index is out of range.
 */
internal fun duplicatePageInNotebook(notebook: Notebook, index: Int): Notebook? {
    val page = notebook.pages.getOrNull(index) ?: return null
    val copy = duplicateNotePage(page)
    val pages = notebook.pages.toMutableList().also { it.add(index + 1, copy) }
    return notebook.copy(pages = pages)
}

/**
 * Debug-only diagnostic: an incoming update may legitimately remove annotations
 * through the eraser or undo, so this only records the counts and never blocks
 * the update. Matching by page id keeps page inserts/deletes from looking like
 * annotation loss.
 */
private fun logAnnotationDecreases(previous: Notebook, target: Notebook) {
    val previousCounts = previous.pages.associate { it.id to it.annotations.size }
    target.pages.forEachIndexed { index, page ->
        val before = previousCounts[page.id] ?: return@forEachIndexed
        val after = page.annotations.size
        if (after < before) {
            NoteTrace.event(
                "NOTE_WARN",
                "annotation_decrease",
                "id" to target.id,
                "page" to page.id,
                "index" to index,
                "from" to before,
                "to" to after,
                "delta" to (after - before),
            )
        }
    }
}
