package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplateStore
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

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = NotebookStore(application)
    private val resolver = application.contentResolver
    private val customTemplateStore = CustomTemplateStore(application)
    private val _notebooks = MutableStateFlow(store.loadAll())
    val notebooks: StateFlow<List<Notebook>> = _notebooks.asStateFlow()

    private val _current = MutableStateFlow<Notebook?>(null)
    val current: StateFlow<Notebook?> = _current.asStateFlow()

    private val _pageIndex = MutableStateFlow(0)
    val pageIndex: StateFlow<Int> = _pageIndex.asStateFlow()

    private var saveJob: Job? = null

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

    fun create(draft: NotebookDraft): Notebook {
        val notebook = Notebook(
            title = draft.title.ifBlank { "새 노트" },
            coverColor = draft.coverColor,
            coverStyle = draft.coverStyle,
            folder = draft.folder.trim(),
            tags = draft.tags,
            pages = listOf(NotePage(template = draft.template)),
        )
        store.save(notebook)
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
        store.save(imported)
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
        store.save(updated)
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
        store.save(notebook)
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
        store.save(updated)
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
        NoteTrace.event(
            "NOTE_VM",
            "save_scheduled",
            "id" to updated.id,
            "pages" to updated.pages.size,
            "total" to noteAnnotationTotal(updated),
            "updatedAt" to updated.updatedAt,
        )
        saveJob = viewModelScope.launch {
            delay(350)
            val startedAt = System.currentTimeMillis()
            store.save(updated)
            NoteTrace.event(
                "NOTE_VM",
                "save_done",
                "id" to updated.id,
                "pages" to updated.pages.size,
                "total" to noteAnnotationTotal(updated),
                "updatedAt" to updated.updatedAt,
                "ms" to (System.currentTimeMillis() - startedAt),
            )
            refresh()
        }
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
        val page = current.pages.getOrNull(index) ?: return
        val copy = page.copy(
            id = java.util.UUID.randomUUID().toString(),
            annotations = page.annotations.map {
                it.copy(id = java.util.UUID.randomUUID().toString())
            },
            // Study data travels with the page but each copy keeps its own ids.
            checklists = Checklists.freshIds(page.checklists),
        )
        val pages = current.pages.toMutableList().also { it.add(index + 1, copy) }
        updateCurrent(current.copy(pages = pages))
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
        store.save(notebook)
        NoteTrace.event(
            "NOTE_VM",
            "save_now_done",
            "id" to notebook.id,
            "pages" to notebook.pages.size,
            "total" to noteAnnotationTotal(notebook),
            "updatedAt" to notebook.updatedAt,
            "ms" to (System.currentTimeMillis() - startedAt),
        )
        refresh()
    }

    private fun findStudyNotebook(source: StudySource): Notebook? =
        StudyNoteBuilder.findStudyNote(_notebooks.value, source)

    private fun mutate(id: String, block: (Notebook) -> Notebook) {
        val original = _current.value?.takeIf { it.id == id }
            ?: store.load(id)
            ?: _notebooks.value.firstOrNull { it.id == id }
            ?: return
        val updated = block(original).normalized().copy(updatedAt = System.currentTimeMillis())
        store.save(updated)
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
