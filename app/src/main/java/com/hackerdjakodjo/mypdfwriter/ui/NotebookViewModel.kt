package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotebookDraft
import com.hackerdjakodjo.mypdfwriter.data.NotebookStore
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotebookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = NotebookStore(application)
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

    fun updateCurrent(notebook: Notebook) {
        val updated = notebook.normalized().copy(updatedAt = System.currentTimeMillis())
        _current.value = updated
        _pageIndex.value = _pageIndex.value.coerceIn(0, (updated.pages.size - 1).coerceAtLeast(0))
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            store.save(updated)
            refresh()
        }
    }

    fun setPageIndex(index: Int) {
        val current = _current.value ?: return
        _pageIndex.value = index.coerceIn(0, (current.pages.size - 1).coerceAtLeast(0))
    }

    fun addPage(template: PaperTemplate? = null) {
        val current = _current.value ?: return
        val pageTemplate = template ?: current.pages.lastOrNull()?.template ?: PaperTemplate.LINED
        val pages = current.pages + NotePage(template = pageTemplate)
        updateCurrent(current.copy(pages = pages))
        _pageIndex.value = pages.lastIndex
    }

    fun duplicatePage(index: Int) {
        val current = _current.value ?: return
        val page = current.pages.getOrNull(index) ?: return
        val pages = current.pages.toMutableList().also {
            it.add(index + 1, page.copy(id = java.util.UUID.randomUUID().toString()))
        }
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

    fun refresh() {
        _notebooks.value = store.loadAll()
    }

    fun saveNow() {
        saveJob?.cancel()
        _current.value?.let {
            store.save(it)
            refresh()
        }
    }

    private fun mutate(id: String, block: (Notebook) -> Notebook) {
        val original = store.load(id)
            ?: _notebooks.value.firstOrNull { it.id == id }
            ?: return
        val updated = block(original).normalized().copy(updatedAt = System.currentTimeMillis())
        store.save(updated)
        if (_current.value?.id == id) _current.value = updated
        refresh()
    }
}
