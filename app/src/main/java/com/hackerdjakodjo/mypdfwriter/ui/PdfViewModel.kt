package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.DocStateStore
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.PdfExporter
import com.hackerdjakodjo.mypdfwriter.pdf.PdfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

data class ViewerUiState(
    val docId: String = "",
    val docName: String = "",
    val pageCount: Int = 0,
    val pageIndex: Int = 0,
    val displayWidthPt: Float = 595f,
    val displayHeightPt: Float = 842f,
    val pages: List<PageSpec> = emptyList(),
    val sourcePageCount: Int = 0,
    val annotations: List<Annotation> = emptyList(),
    val notes: Map<Int, String> = emptyMap(),
    val tool: Tool = Tool.VIEW,
    val color: Int = 0xFF1F4E79.toInt(),
    val strokeWidth: Float = 0.0035f,
    val fontSize: Float = 0.024f,
    val zoom: Float = 1f,
    val busy: Boolean = false,
    val busyLabel: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val message: String? = null,
    val searchQuery: String = "",
    val searchHits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
    val noteDraft: String = "",
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PdfRepository(application)
    private val docStore = DocStateStore(application)
    private val libraryStore = LibraryStore(application)
    private val resolver = application.contentResolver

    private val sourceDir = File(application.filesDir, "sources").apply { mkdirs() }
    private val workingDir = File(application.cacheDir, "working").apply { mkdirs() }

    private val _viewer = MutableStateFlow<ViewerUiState?>(null)
    val viewer: StateFlow<ViewerUiState?> = _viewer.asStateFlow()

    private val _library = MutableStateFlow(libraryStore.load())
    val library: StateFlow<List<RecentDoc>> = _library.asStateFlow()

    private val _pageBitmap = MutableStateFlow<Bitmap?>(null)
    val pageBitmap: StateFlow<Bitmap?> = _pageBitmap.asStateFlow()

    private val _libraryMessage = MutableStateFlow<String?>(null)
    val libraryMessage: StateFlow<String?> = _libraryMessage.asStateFlow()

    private var sourceFile: File? = null
    private var workingFile: File? = null
    private var pageTexts: List<String>? = null
    private var renderJob: Job? = null
    private var searchJob: Job? = null
    private var currentPageTextsKey: String? = null

    private val undoStack = ArrayDeque<List<Annotation>>()
    private val redoStack = ArrayDeque<List<Annotation>>()

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri, addToRecents: Boolean = true) {
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            runCatching {
                runCatching {
                    resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = repo.queryDisplayName(uri)
                val docId = docIdFor(uri)
                val source = File(sourceDir, "$docId.pdf")
                repo.copyFromUri(uri, source)
                val sourceCount = repo.pageCount(source)
                val saved = docStore.load(docId)?.normalized(sourceCount)
                val pages = saved?.pages ?: List(sourceCount) { PageSpec(it) }
                val annotations = saved?.annotations ?: emptyList()
                val notes = saved?.notes ?: emptyMap()
                val working = File(workingDir, "$docId.pdf")
                repo.assemble(source, pages, working)

                sourceFile = source
                workingFile = working
                pageTexts = null
                currentPageTextsKey = null
                undoStack.clear()
                redoStack.clear()

                val displayName = saved?.displayName?.takeIf { it.isNotBlank() } ?: name
                _pageBitmap.value = null
                _viewer.value = ViewerUiState(
                    docId = docId,
                    docName = displayName,
                    pageCount = pages.size,
                    pageIndex = 0,
                    pages = pages,
                    sourcePageCount = sourceCount,
                    annotations = annotations,
                    notes = notes,
                    canUndo = false,
                    canRedo = false,
                    busy = true,
                    busyLabel = text(R.string.rendering_page),
                    noteDraft = notes[0] ?: "",
                )
                persistNow()
                if (addToRecents) {
                    _library.value = libraryStore.upsert(
                        RecentDoc(uri.toString(), displayName, pages.size, System.currentTimeMillis()),
                    )
                }
                renderPage(0)
            }.onFailure { error ->
                _viewer.value = null
                _pageBitmap.value = null
                _libraryMessage.value = text(R.string.error_generic, error.message ?: text(R.string.unsupported_pdf))
            }
        }
    }

    fun createBlankDocument(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                resolver.openOutputStream(uri)?.use { output -> repo.writeBlankPdf(output) }
                    ?: error("출력 파일을 열 수 없습니다.")
            }.onSuccess {
                openDocument(uri)
            }.onFailure { error ->
                _libraryMessage.value = text(R.string.error_generic, error.message ?: "")
            }
        }
    }

    fun closeDocument() {
        renderJob?.cancel()
        searchJob?.cancel()
        persistNow()
        _viewer.value = null
        _pageBitmap.value = null
        pageTexts = null
        sourceFile = null
        workingFile = null
        repo.clearCache()
    }

    // ---------------------------------------------------------------- rendering

    private fun renderPage(index: Int) {
        val file = workingFile ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            val rendered = runCatching { repo.renderPage(file, index, RENDER_SCALE) }.getOrNull()
            if (rendered == null) {
                _viewer.update {
                    it?.copy(busy = false, busyLabel = null, message = text(R.string.unsupported_pdf))
                }
                return@launch
            }
            _pageBitmap.value = rendered.bitmap
            _viewer.update {
                it?.copy(
                    pageIndex = index,
                    displayWidthPt = rendered.widthPt,
                    displayHeightPt = rendered.heightPt,
                    busy = false,
                    busyLabel = null,
                    noteDraft = it.notes[index] ?: "",
                )
            }
        }
    }

    fun goToPage(index: Int) {
        val state = _viewer.value ?: return
        val target = index.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target == state.pageIndex && _pageBitmap.value != null) return
        renderPage(target)
    }

    fun nextPage() = goToPage((_viewer.value?.pageIndex ?: 0) + 1)

    fun previousPage() = goToPage((_viewer.value?.pageIndex ?: 0) - 1)

    // ---------------------------------------------------------------- tools

    fun setTool(tool: Tool) {
        _viewer.update {
            it?.copy(
                tool = tool,
                strokeWidth = when (tool) {
                    Tool.HIGHLIGHT -> 0.02f
                    Tool.PEN -> 0.0035f
                    else -> it.strokeWidth
                },
            )
        }
    }

    fun setColor(color: Int) = _viewer.update { it?.copy(color = color) }

    fun setStrokeWidth(width: Float) = _viewer.update {
        it?.copy(strokeWidth = width.coerceIn(0.001f, 0.03f))
    }

    fun setFontSize(size: Float) = _viewer.update {
        it?.copy(fontSize = size.coerceIn(0.012f, 0.06f))
    }

    fun setZoom(zoom: Float) = _viewer.update { it?.copy(zoom = zoom.coerceIn(1f, 6f)) }

    // ---------------------------------------------------------------- annotations

    fun addStroke(points: List<NormalizedPoint>) {
        val state = _viewer.value ?: return
        if (points.isEmpty() || state.tool == Tool.VIEW || state.tool == Tool.ERASER || state.tool == Tool.TEXT) return
        val type = when (state.tool) {
            Tool.PEN -> AnnotationType.PEN
            Tool.HIGHLIGHT -> AnnotationType.HIGHLIGHT
            Tool.RECT -> AnnotationType.RECT
            Tool.ARROW -> AnnotationType.ARROW
            else -> return
        }
        val usablePoints = if (type == AnnotationType.RECT || type == AnnotationType.ARROW) {
            listOf(points.first(), points.last())
        } else {
            points
        }
        val annotation = Annotation(
            type = type,
            page = state.pageIndex,
            color = state.color,
            width = when (type) {
                AnnotationType.HIGHLIGHT -> 0.02f
                AnnotationType.RECT, AnnotationType.ARROW -> maxOf(state.strokeWidth, 0.0025f)
                else -> state.strokeWidth
            },
            points = usablePoints,
        )
        mutateAnnotations { it + annotation }
    }

    fun addText(point: NormalizedPoint, text: String) {
        val state = _viewer.value ?: return
        if (text.isBlank()) return
        val annotation = Annotation(
            type = AnnotationType.TEXT,
            page = state.pageIndex,
            color = state.color,
            width = state.strokeWidth,
            points = listOf(point),
            text = text,
            fontSize = state.fontSize,
        )
        mutateAnnotations { it + annotation }
    }

    fun eraseStroke(points: List<NormalizedPoint>) {
        val state = _viewer.value ?: return
        if (points.isEmpty()) return
        val page = state.pageIndex
        val remaining = state.annotations.filterNot { annotation ->
            annotation.page == page && isHit(annotation, points)
        }
        if (remaining.size != state.annotations.size) {
            mutateAnnotations { remaining }
        }
    }

    private fun isHit(annotation: Annotation, eraser: List<NormalizedPoint>): Boolean {
        val radius = 0.028f
        fun near(p: NormalizedPoint): Boolean = eraser.any { e ->
            val dx = p.x - e.x
            val dy = p.y - e.y
            dx * dx + dy * dy <= radius * radius
        }
        return when (annotation.type) {
            AnnotationType.PEN, AnnotationType.HIGHLIGHT -> annotation.points.any { near(it) }
            AnnotationType.RECT -> {
                if (annotation.points.size < 2) return false
                val a = annotation.points[0]
                val b = annotation.points[1]
                val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
                val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
                eraser.any { e ->
                    val insideX = e.x in (minX - radius)..(maxX + radius)
                    val insideY = e.y in (minY - radius)..(maxY + radius)
                    val onEdge = (e.x in minX..maxX && (kotlin.math.abs(e.y - minY) <= radius || kotlin.math.abs(e.y - maxY) <= radius)) ||
                        (e.y in minY..maxY && (kotlin.math.abs(e.x - minX) <= radius || kotlin.math.abs(e.x - maxX) <= radius))
                    insideX && insideY && onEdge
                }
            }
            AnnotationType.ARROW -> {
                if (annotation.points.size < 2) return false
                val a = annotation.points[0]
                val b = annotation.points[1]
                eraser.any { e -> distanceToSegment(e, a, b) <= radius }
            }
            AnnotationType.TEXT -> {
                if (annotation.points.isEmpty()) return false
                val p = annotation.points.first()
                val lines = annotation.text.split('\n')
                val width = (lines.maxOfOrNull { it.length } ?: 0) * annotation.fontSize * 0.56f
                val height = lines.size * annotation.fontSize * 1.35f
                eraser.any { e ->
                    e.x in (p.x - radius)..(p.x + width + radius) &&
                        e.y in (p.y - radius)..(p.y + height + radius)
                }
            }
        }
    }

    private fun distanceToSegment(p: NormalizedPoint, a: NormalizedPoint, b: NormalizedPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return kotlin.math.hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return kotlin.math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun mutateAnnotations(block: (List<Annotation>) -> List<Annotation>) {
        val state = _viewer.value ?: return
        undoStack.addLast(state.annotations)
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        _viewer.value = state.copy(
            annotations = block(state.annotations),
            canUndo = true,
            canRedo = false,
        )
        persistNow()
    }

    fun undo() {
        val state = _viewer.value ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state.annotations)
        _viewer.value = state.copy(
            annotations = previous,
            canUndo = undoStack.isNotEmpty(),
            canRedo = true,
        )
        persistNow()
    }

    fun redo() {
        val state = _viewer.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state.annotations)
        _viewer.value = state.copy(
            annotations = next,
            canUndo = true,
            canRedo = redoStack.isNotEmpty(),
        )
        persistNow()
    }

    // ---------------------------------------------------------------- page layout

    fun rotatePage(clockwise: Boolean) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val index = state.pageIndex
        val delta = if (clockwise) 90 else 270
        val aspect = state.displayWidthPt / state.displayHeightPt
        val pages = state.pages.toMutableList().also {
            val spec = it[index]
            it[index] = spec.copy(extraRotation = PageSpec.normalizeRotation(spec.extraRotation + delta))
        }
        val annotations = state.annotations.map { annotation ->
            if (annotation.page == index) rotateAnnotation(annotation, delta, aspect) else annotation
        }
        rebuild(state.copy(pages = pages, annotations = annotations))
    }

    private fun rotateAnnotation(annotation: Annotation, delta: Int, aspect: Float): Annotation {
        val points = when (delta) {
            90 -> annotation.points.map { NormalizedPoint(1f - it.y, it.x) }
            180 -> annotation.points.map { NormalizedPoint(1f - it.x, 1f - it.y) }
            270 -> annotation.points.map { NormalizedPoint(it.y, 1f - it.x) }
            else -> annotation.points
        }
        val widthScale = if (delta == 180) 1f else aspect
        val fontScale = if (delta == 180) 1f else 1f / aspect
        return annotation.copy(
            points = points,
            width = (annotation.width * widthScale).coerceIn(0.0005f, 0.1f),
            fontSize = (annotation.fontSize * fontScale).coerceIn(0.008f, 0.12f),
            textRotation = PageSpec.normalizeRotation(annotation.textRotation + delta),
        )
    }

    fun movePage(index: Int, direction: Int) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val target = index + direction
        if (index !in state.pages.indices || target !in state.pages.indices) return
        val pages = state.pages.toMutableList()
        val swap = pages[index]
        pages[index] = pages[target]
        pages[target] = swap
        val annotations = state.annotations.map {
            when (it.page) {
                index -> it.copy(page = target)
                target -> it.copy(page = index)
                else -> it
            }
        }
        val notes = state.notes.mapKeys { entry ->
            when (entry.key) {
                index -> target
                target -> index
                else -> entry.key
            }
        }
        val current = when (state.pageIndex) {
            index -> target
            target -> index
            else -> state.pageIndex
        }
        rebuild(state.copy(pages = pages, annotations = annotations, notes = notes, pageIndex = current))
    }

    fun deletePage(index: Int) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.size <= 1 || index !in state.pages.indices) return
        val pages = state.pages.toMutableList().also { it.removeAt(index) }
        val annotations = state.annotations
            .filterNot { it.page == index }
            .map { if (it.page > index) it.copy(page = it.page - 1) else it }
        val notes = state.notes.entries
            .filterNot { it.key == index }
            .associate { (key, value) -> (if (key > index) key - 1 else key) to value }
        val current = when {
            state.pageIndex > index -> state.pageIndex - 1
            state.pageIndex == index -> state.pageIndex.coerceAtMost(pages.size - 1)
            else -> state.pageIndex
        }
        rebuild(state.copy(pages = pages, annotations = annotations, notes = notes, pageIndex = current))
    }

    fun insertBlankPage(afterIndex: Int) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val insertAt = (afterIndex + 1).coerceIn(0, state.pages.size)
        val pages = state.pages.toMutableList().also { it.add(insertAt, PageSpec(null)) }
        val annotations = state.annotations.map {
            if (it.page >= insertAt) it.copy(page = it.page + 1) else it
        }
        val notes = state.notes.entries.associate { (key, value) ->
            (if (key >= insertAt) key + 1 else key) to value
        }
        rebuild(state.copy(pages = pages, annotations = annotations, notes = notes, pageIndex = insertAt))
    }

    fun resetPages() {
        val state = _viewer.value ?: return
        if (state.busy) return
        val source = sourceFile ?: return
        viewModelScope.launch {
            val sizes = runCatching { repo.sourcePageSizes(source) }.getOrDefault(emptyList())
            val specs = List(state.sourcePageCount) { PageSpec(it) }

            fun rotated(annotation: Annotation, spec: PageSpec): Annotation {
                val delta = PageSpec.normalizeRotation(-spec.extraRotation)
                if (delta == 0) return annotation
                val size = spec.sourcePage?.let { sizes.getOrNull(it) }
                val baseAspect = if (size != null && size.height > 0f) size.width / size.height else 1f
                val aspect = if (spec.extraRotation % 180 == 0) baseAspect else 1f / baseAspect
                return rotateAnnotation(annotation, delta, aspect)
            }

            val annotations = state.annotations.mapNotNull { annotation ->
                val spec = state.pages.getOrNull(annotation.page) ?: return@mapNotNull null
                val sourcePage = spec.sourcePage ?: return@mapNotNull null
                if (sourcePage !in specs.indices) return@mapNotNull null
                rotated(annotation, spec).copy(page = sourcePage)
            }
            val notes = buildMap {
                state.notes.forEach { (page, value) ->
                    val spec = state.pages.getOrNull(page) ?: return@forEach
                    val sourcePage = spec.sourcePage ?: return@forEach
                    if (sourcePage in specs.indices) put(sourcePage, value)
                }
            }
            rebuild(
                state.copy(
                    pages = specs,
                    annotations = annotations,
                    notes = notes,
                    pageIndex = state.pageIndex.coerceIn(0, (specs.size - 1).coerceAtLeast(0)),
                ),
            )
        }
    }

    private fun rebuild(state: ViewerUiState) {
        val source = sourceFile ?: return
        val working = workingFile ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.building_pages)) }
            runCatching { repo.assemble(source, state.pages, working) }
                .onSuccess {
                    val pageCount = state.pages.size
                    val safeIndex = state.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                    undoStack.clear()
                    redoStack.clear()
                    _viewer.value = state.copy(
                        pageCount = pageCount,
                        pageIndex = safeIndex,
                        busy = true,
                        busyLabel = text(R.string.rendering_page),
                        canUndo = false,
                        canRedo = false,
                    )
                    pageTexts = null
                    currentPageTextsKey = null
                    _pageBitmap.value = null
                    persistNow()
                    renderPage(safeIndex)
                }
                .onFailure { error ->
                    _viewer.update {
                        it?.copy(
                            busy = false,
                            busyLabel = null,
                            message = text(R.string.error_generic, error.message ?: ""),
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------- notes

    fun updateNoteDraft(value: String) = _viewer.update { it?.copy(noteDraft = value) }

    fun saveNote() {
        val state = _viewer.value ?: return
        val notes = state.notes.toMutableMap()
        if (state.noteDraft.isBlank()) notes.remove(state.pageIndex) else notes[state.pageIndex] = state.noteDraft.trim()
        _viewer.value = state.copy(notes = notes, message = text(R.string.saved))
        persistNow()
    }

    // ---------------------------------------------------------------- search

    fun search(query: String) {
        val state = _viewer.value ?: return
        _viewer.update { it?.copy(searchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _viewer.update { it?.copy(searchHits = emptyList(), searching = false, searchFailed = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(220)
            _viewer.update { it?.copy(searching = true, searchFailed = false) }
            val file = workingFile ?: return@launch
            val key = "${state.docId}:${file.lastModified()}:${state.pages.size}"
            if (pageTexts == null || currentPageTextsKey != key) {
                val extracted = runCatching { repo.extractPagesText(file) }.getOrNull()
                if (extracted == null) {
                    _viewer.update { it?.copy(searching = false, searchFailed = true) }
                    return@launch
                }
                pageTexts = extracted
                currentPageTextsKey = key
            }
            val hits = buildHits(pageTexts.orEmpty(), trimmed)
            _viewer.update { it?.copy(searchHits = hits, searching = false) }
        }
    }

    private fun buildHits(texts: List<String>, query: String): List<SearchHit> {
        val needle = query.lowercase()
        val hits = ArrayList<SearchHit>()
        for ((index, text) in texts.withIndex()) {
            if (hits.size >= 300) break
            if (text.isBlank()) continue
            val flat = text.replace(Regex("\\s+"), " ").trim()
            val lower = flat.lowercase()
            var count = 0
            var from = 0
            while (true) {
                val found = lower.indexOf(needle, from)
                if (found < 0) break
                count++
                from = found + needle.length.coerceAtLeast(1)
            }
            if (count == 0) continue
            val first = lower.indexOf(needle)
            val start = (first - 40).coerceAtLeast(0)
            val end = (first + needle.length + 70).coerceAtMost(flat.length)
            val snippet = buildString {
                if (start > 0) append('…')
                append(flat.substring(start, end))
                if (end < flat.length) append('…')
            }
            hits.add(SearchHit(index, count, snippet))
        }
        return hits
    }

    // ---------------------------------------------------------------- export

    fun exportSuggestedName(): String {
        val state = _viewer.value ?: return "My PDF Writer.pdf"
        val base = state.docName.substringBeforeLast('.').ifBlank { "My PDF Writer" }
        val safe = base.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
        return "${safe}_주석.pdf"
    }

    fun exportTo(uri: Uri) {
        val state = _viewer.value ?: return
        val source = sourceFile ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        PdfExporter.export(source, state.pages, state.annotations, output)
                    } ?: error("출력 파일을 열 수 없습니다.")
                }
            }.onSuccess {
                _viewer.update {
                    it?.copy(busy = false, busyLabel = null, message = text(R.string.export_done))
                }
            }.onFailure { error ->
                _viewer.update {
                    it?.copy(
                        busy = false,
                        busyLabel = null,
                        message = text(R.string.export_failed, error.message ?: ""),
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- library

    fun removeRecent(uri: String, deleteAnnotations: Boolean) {
        _library.value = libraryStore.remove(uri)
        if (deleteAnnotations) {
            val id = docIdFor(Uri.parse(uri))
            docStore.delete(id)
            File(sourceDir, "$id.pdf").delete()
        }
    }

    fun clearMessage() = _viewer.update { it?.copy(message = null) }

    fun clearLibraryMessage() {
        _libraryMessage.value = null
    }

    fun dismissError() {
        _viewer.value = null
    }

    // ---------------------------------------------------------------- helpers

    private fun persistNow() {
        val state = _viewer.value ?: return
        val doc = DocState(
            docId = state.docId,
            displayName = state.docName,
            pages = state.pages,
            annotations = state.annotations,
            notes = state.notes,
        )
        viewModelScope.launch(Dispatchers.IO) { docStore.save(doc) }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun docIdFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val RENDER_SCALE = 2.2f
    }
}
