package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExporter
import com.hackerdjakodjo.mypdfwriter.pdf.noteExportFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = NotebookStore(application)
    private val resolver = application.contentResolver
    private val customTemplateStore = CustomTemplateStore(application)
    // Parsing every notebook used to block the first frame. Publish an empty
    // library immediately and load it off Main; open() already falls back to a
    // single-file read when the list has not arrived yet.
    private val _notebooks = MutableStateFlow(emptyList<Notebook>())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _notebooks.value = store.loadAll()
        }
    }

    private val _current = MutableStateFlow<Notebook?>(null)
    val current: StateFlow<Notebook?> = _current.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private var saveJob: Job? = null

    /**
     * Guards every notebook JSON write. The debounced writer holds this on IO;
     * [saveNow] takes it on the calling thread so a close/backup flush can never
     * interleave with a cancelled in-flight write of the same file. All six direct
     * write sites funnel through [saveLocked] too, so no write can bypass it.
     */
    private val saveLock = NotebookWriteGate(store::save)

    private fun saveLocked(notebook: Notebook) = saveLock.writeLocked(notebook)

    /** Monotonic id of the newest scheduled write; older jobs may not publish. */
    private var saveGeneration = 0L

    fun open(id: String) {
        val notebook = store.load(id)
            ?: _notebooks.value.firstOrNull { it.id == id }
            ?: return
        _current.value = notebook
        _pageIndex.value = 0
    }

    fun close() {
        saveNow()
        _current.value = null
        _pageIndex.value = 0
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
        _current.value = discarded.current
        _pageIndex.value = discarded.pageIndex
        NoteTrace.event(
            "NOTE_VM",
            "discard_current",
            "generation" to saveGeneration,
        )
    }

    fun create(draft: NotebookDraft): Notebook {
        val notebook = Notebook(
            title = draft.title.ifBlank { "새 노트" },
            coverColor = draft.coverColor,
            coverStyle = draft.coverStyle,
            folder = draft.folder.trim(),
            tags = draft.tags,
            pages = listOf(NotePage(template = draft.template)),
        )
        saveLocked(notebook)
        refresh()
        open(notebook.id)
        return notebook
    }

    /**
     * Persists a notebook produced elsewhere (for example by [StudyNoteBuilder]),
     * refreshes the library and opens it in the editor.
     */
    fun importNotebook(notebook: Notebook): Notebook {
        saveNow()
        val imported = notebook.normalized().copy(updatedAt = System.currentTimeMillis())
        saveLocked(imported)
        refresh()
        open(imported.id)
        return imported
    }

    /**
     * Captures one drag-and-drop selection into the study notebook linked to
     * [source]. An existing notebook is matched by its source URI first and by its
     * source document name or study-note title second. The notebook is saved and
     * refreshed but deliberately not opened, so the PDF reader stays in place.
     */
    fun appendStudyExcerpt(source: StudySource, text: String, pageIndex: Int): Notebook {
        val trimmed = text.trim()
        val excerpt = Excerpt(
            pageIndex = pageIndex.coerceAtLeast(0),
            text = trimmed,
        )
        val existing = findStudyNotebook(source)
        val updated = if (existing != null) {
            StudyNoteBuilder.appendExcerpt(existing, source, excerpt)
        } else {
            StudyNoteBuilder.fromExcerpts(
                title = StudyNoteBuilder.suggestedTitle(source),
                source = source,
                excerpts = listOf(excerpt),
            )
        }
        if (trimmed.isBlank()) return updated
        saveLocked(updated)
        if (_current.value?.id == updated.id) _current.value = updated
        refresh()
        return updated
    }

    /**
     * Resolves the study notebook linked to [source] without opening it. The PDF
     * study pane edits this notebook detached from the full-screen editor.
     */
    fun studyNoteFor(source: StudySource): Notebook? {
        val match = StudyNoteBuilder.findStudyNote(_notebooks.value, source) ?: return null
        return store.load(match.id) ?: match
    }

    /**
     * Creates and saves a blank study notebook linked to [source] without making
     * it current. Used by the PDF hub when the document has no study note yet.
     */
    fun createBlankStudyNote(source: StudySource): Notebook {
        val notebook = StudyNoteBuilder.blankStudyNote(source)
        saveLocked(notebook)
        refresh()
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
     * Saves a notebook edited in a detached surface (the PDF bottom pane).
     * Unlike [updateCurrent] or [importNotebook] this never sets [_current] and
     * never changes the full-screen editor's page. The save is immediate so a
     * close, back press or drag-dismiss cannot lose the last stroke.
     */
    fun saveDetached(notebook: Notebook): Notebook {
        val updated = notebook.normalized().copy(updatedAt = System.currentTimeMillis())
        val startedAt = System.currentTimeMillis()
        saveLocked(updated)
        NoteTrace.event(
            "NOTE_VM",
            "save_detached",
            "id" to updated.id,
            "pages" to updated.pages.size,
            "total" to noteAnnotationTotal(updated),
            "updatedAt" to updated.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        refresh()
        return updated
    }

    fun updateCurrent(notebook: Notebook) {
        val previous = _current.value
        NoteTrace.event(
            "NOTE_VM",
            "update_in",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "ann" to noteAnnotationCounts(notebook),
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
        )

        val updated = notebook.normalized().copy(updatedAt = System.currentTimeMillis())
        if (previous != null && previous.id == updated.id) {
            logAnnotationDecreases(previous, updated)
        }
        _current.value = updated
        _pageIndex.value = _pageIndex.value.coerceIn(0, (updated.pages.size - 1).coerceAtLeast(0))
        _current.value?.let { live ->
            NoteTrace.event(
                "NOTE_VM",
                "update_live",
                "id" to live.id,
                "pages" to live.pages.size,
                "ann" to noteAnnotationCounts(live),
                "total" to noteAnnotationTotal(live),
                "updatedAt" to live.updatedAt,
            )
        }

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
            delay(350)
            if (!isLatestSaveGeneration(generation, saveGeneration)) return@launch
            // Serialization + disk IO + library scan all leave Main. Only the
            // StateFlow publish below runs on the Main dispatcher.
            val loaded = withContext(Dispatchers.IO) {
                persistSnapshot(updated)
            }
            // A newer stroke (or saveNow) may have superseded this job while it
            // was writing; its stale library must never hide that newer state.
            if (isLatestSaveGeneration(generation, saveGeneration)) {
                _notebooks.value = loaded
            } else {
                NoteTrace.event(
                    "NOTE_VM",
                    "save_superseded",
                    "id" to updated.id,
                    "generation" to generation,
                    "latest" to saveGeneration,
                )
            }
        }
    }

    /**
     * Writes one snapshot and rescans the library under [saveLock]. Called on IO
     * by the debounced writer and on the caller thread by [saveNow]; the lock
     * keeps a cancelled older write from clobbering a newer one on disk.
     */
    private fun persistSnapshot(notebook: Notebook): List<Notebook> = saveLock.locked {
        val startedAt = System.currentTimeMillis()
        saveLocked(notebook)
        // Only the edited notebook changed: merge it into the in-memory library
        // instead of re-reading and re-parsing every notebook on every save.
        val loaded = mergeNotebookIntoLibrary(_notebooks.value, notebook)
        NoteTrace.event(
            "NOTE_VM",
            "save_done",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        loaded
    }

    fun setPageIndex(index: Int) {
        val current = _current.value ?: return
        _pageIndex.value = index.coerceIn(0, (current.pages.size - 1).coerceAtLeast(0))
    }

    fun addPage(template: PaperTemplate? = null) {
        val current = _current.value ?: return
        val previous = current.pages.lastOrNull()
        val pageTemplate = template ?: previous?.template ?: PaperTemplate.LINED
        val pages = current.pages + NotePage(
            template = pageTemplate,
            orientation = previous?.orientation ?: PageOrientation.PORTRAIT,
            tint = previous?.tint ?: PaperTint.WARM,
        )
        updateCurrent(current.copy(pages = pages))
        _pageIndex.value = pages.lastIndex
    }

    fun duplicatePage(index: Int) {
        val current = _current.value ?: return
        val updated = duplicatePageInNotebook(current, index) ?: return
        updateCurrent(updated)
        _pageIndex.value = index + 1
    }

    fun deletePage(index: Int) {
        val current = _current.value ?: return
        if (current.pages.size <= 1 || index !in current.pages.indices) return
        val pages = current.pages.toMutableList().also { it.removeAt(index) }
        updateCurrent(current.copy(pages = pages))
        _pageIndex.value = _pageIndex.value.coerceIn(0, pages.lastIndex)
    }

    fun setFavorite(id: String, favorite: Boolean) = mutate(id) { it.copy(favorite = favorite) }

    fun toggleFavorite(id: String) {
        val notebook = store.load(id) ?: _notebooks.value.firstOrNull { it.id == id } ?: return
        setFavorite(id, !notebook.favorite)
    }

    fun trash(id: String) = mutate(id) { it.copy(trashed = true) }

    fun restore(id: String) = mutate(id) { it.copy(trashed = false) }

    fun deleteForever(id: String) {
        store.delete(id)
        if (_current.value?.id == id) {
            _current.value = null
            _pageIndex.value = 0
        }
        refresh()
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

    fun exportSuggestedName(): String = suggestedExportName(_current.value?.title)

    fun exportSuggestedName(notebook: Notebook): String = suggestedExportName(notebook.title)

    fun exportCurrent(uri: Uri, options: NoteExportOptions = NoteExportOptions()) {
        _current.value?.let { exportNotebook(it, uri, options) }
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
        _notebooks.value = store.loadAll()
    }

    fun saveNow() {
        saveJob?.cancel()
        // Invalidate any debounced job that already finished its IO and is about
        // to publish, so its older library cannot overwrite this flush.
        saveGeneration = nextSaveGeneration(saveGeneration)
        val notebook = _current.value
        if (notebook == null) {
            NoteTrace.event("NOTE_VM", "save_now_skip", "reason" to "no_current")
            return
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
        // Close/back/backup callers are synchronous and cannot await a job, so the
        // flush completes on the caller thread under the same lock the IO writer
        // uses. This is runBlocking-free and still guarantees the bytes are on
        // disk before the screen closes.
        val loaded = saveLock.locked {
            saveLocked(notebook)
            mergeNotebookIntoLibrary(_notebooks.value, notebook)
        }
        NoteTrace.event(
            "NOTE_VM",
            "save_now_done",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        _notebooks.value = loaded
    }

    private fun findStudyNotebook(source: StudySource): Notebook? =
        StudyNoteBuilder.findStudyNote(_notebooks.value, source)

    private fun mutate(id: String, block: (Notebook) -> Notebook) {
        val original = _current.value?.takeIf { it.id == id }
            ?: store.load(id)
            ?: _notebooks.value.firstOrNull { it.id == id }
            ?: return
        val updated = block(original).normalized().copy(updatedAt = System.currentTimeMillis())
        saveLocked(updated)
        if (_current.value?.id == id) _current.value = updated
        refresh()
    }
}

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
 * writes each notebook through a fixed `<id>.json.tmp` file and renames it into
 * place, so two overlapping saves of the same id would truncate each other's tmp
 * file and could leave a torn snapshot on disk. [writeLocked] is the one funnel
 * all VM write sites use; [locked] also covers the load-all scan that publishes
 * the library, so a flush cannot observe a half-finished write.
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
