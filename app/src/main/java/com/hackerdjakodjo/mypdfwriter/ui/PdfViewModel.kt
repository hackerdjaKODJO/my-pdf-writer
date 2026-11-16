package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.DocStateStore
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyleStore
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.ExcerptStore
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageEdit
import com.hackerdjakodjo.mypdfwriter.data.PageOps
import com.hackerdjakodjo.mypdfwriter.data.PageSize
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.PageTransforms
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.data.PdfReadingState
import com.hackerdjakodjo.mypdfwriter.data.PdfReadingStore
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.PdfExporter
import com.hackerdjakodjo.mypdfwriter.pdf.PdfMetadata
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import com.hackerdjakodjo.mypdfwriter.pdf.PdfRepository
import com.hackerdjakodjo.mypdfwriter.pdf.PdfShare
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLayout
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLine
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTools
import kotlinx.coroutines.CancellationException
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
    val opacity: Float = 1f,
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
    val documentStyle: DocumentStyle = DocumentStyle(),
    val nightMode: Boolean = false,
    val revision: Int = 0,
    val textLoading: Boolean = false,
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PdfRepository(application)
    private val docStore = DocStateStore(application)
    private val docStyleStore = DocumentStyleStore(application)
    private val libraryStore = LibraryStore(application)
    private val excerptStore = ExcerptStore(application)
    private val readingStore = PdfReadingStore(application)
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

    private val _outline = MutableStateFlow<List<PdfOutlineEntry>?>(null)
    val outline: StateFlow<List<PdfOutlineEntry>?> = _outline.asStateFlow()

    private val _metadata = MutableStateFlow<PdfMetadata?>(null)
    val metadata: StateFlow<PdfMetadata?> = _metadata.asStateFlow()

    private val _excerpts = MutableStateFlow<List<Excerpt>>(emptyList())
    val excerpts: StateFlow<List<Excerpt>> = _excerpts.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<PdfBookmark>>(emptyList())
    val bookmarks: StateFlow<List<PdfBookmark>> = _bookmarks.asStateFlow()

    private val _textWords = MutableStateFlow<List<PdfTextWord>>(emptyList())
    /** Selectable words for the currently displayed page. */
    val textWords: StateFlow<List<PdfTextWord>> = _textWords.asStateFlow()

    private var sourceFile: File? = null
    private var workingFile: File? = null
    private var sourceSizes: List<PageSize> = emptyList()
    private var pageTexts: List<String>? = null
    private var renderJob: Job? = null
    private var searchJob: Job? = null
    private var readingSaveJob: Job? = null
    private var textLayoutJob: Job? = null
    private var textLayoutLines: List<PdfTextLine> = emptyList()
    private var textLayoutKey: String? = null
    private var textLayoutLoadingKey: String? = null
    private var currentPageTextsKey: String? = null

    private data class DocSnapshot(
        val pages: List<PageSpec>,
        val annotations: List<Annotation>,
        val notes: Map<Int, String>,
        val pageIndex: Int,
    )

    private val undoStack = ArrayDeque<DocSnapshot>()
    private val redoStack = ArrayDeque<DocSnapshot>()

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri, addToRecents: Boolean = true) {
        saveReadingNow()
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
                val sizes = runCatching { repo.sourcePageSizes(source) }.getOrDefault(emptyList())
                val style = docStyleStore.load(docId)
                val saved = docStore.load(docId)?.normalized(sourceCount)
                val pages = saved?.pages ?: List(sourceCount) { PageSpec(it) }
                val annotations = saved?.annotations ?: emptyList()
                val notes = saved?.notes ?: emptyMap()
                val reading = (readingStore.load(docId) ?: PdfReadingState()).normalized(pages.size)
                val working = File(workingDir, "$docId.pdf")
                repo.assemble(source, pages, working)

                sourceFile = source
                workingFile = working
                sourceSizes = sizes
                resetTextLayout()
                _excerpts.value = excerptStore.load(docId)
                _bookmarks.value = reading.bookmarks
                pageTexts = null
                currentPageTextsKey = null
                _outline.value = null
                _metadata.value = null
                undoStack.clear()
                redoStack.clear()

                val displayName = saved?.displayName?.takeIf { it.isNotBlank() } ?: name
                val restoredPage = reading.lastPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _pageBitmap.value = null
                _viewer.value = ViewerUiState(
                    docId = docId,
                    docName = displayName,
                    pageCount = pages.size,
                    pageIndex = restoredPage,
                    pages = pages,
                    sourcePageCount = sourceCount,
                    annotations = annotations,
                    notes = notes,
                    canUndo = false,
                    canRedo = false,
                    busy = true,
                    busyLabel = text(R.string.rendering_page),
                    noteDraft = notes[restoredPage] ?: "",
                    documentStyle = style,
                )
                persistNow()
                if (addToRecents) {
                    _library.value = libraryStore.upsert(
                        RecentDoc(uri.toString(), displayName, pages.size, System.currentTimeMillis()),
                    )
                }
                renderPage(restoredPage)
            }.onFailure { error ->
                _viewer.value = null
                _pageBitmap.value = null
                _excerpts.value = emptyList()
                _bookmarks.value = emptyList()
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
        saveReadingNow()
        persistNow()
        _viewer.value = null
        _pageBitmap.value = null
        _outline.value = null
        _metadata.value = null
        _excerpts.value = emptyList()
        _bookmarks.value = emptyList()
        resetTextLayout()
        pageTexts = null
        sourceSizes = emptyList()
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
            scheduleReadingSave(index)
        }
    }

    fun goToPage(index: Int) {
        val state = _viewer.value ?: return
        val target = index.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target == state.pageIndex && _pageBitmap.value != null) return
        renderPage(target)
        scheduleReadingSave(target)
    }

    fun nextPage() = goToPage((_viewer.value?.pageIndex ?: 0) + 1)

    fun previousPage() = goToPage((_viewer.value?.pageIndex ?: 0) - 1)

    suspend fun loadThumbnail(index: Int): Bitmap? {
        val file = workingFile ?: return null
        return runCatching { repo.renderPage(file, index, 0.55f)?.bitmap }.getOrNull()
    }

    /** Full-width page image for the continuous reader. */
    suspend fun loadReaderPage(index: Int): Bitmap? {
        val file = workingFile ?: return null
        return runCatching { repo.renderPage(file, index, 1.25f)?.bitmap }.getOrNull()
    }

    fun currentSourceFile(): File? = sourceFile

    fun currentWorkingFile(): File? = workingFile

    fun toggleNightMode() = _viewer.update { it?.copy(nightMode = !it.nightMode) }

    fun setDocumentStyle(style: DocumentStyle) {
        val state = _viewer.value ?: return
        docStyleStore.save(state.docId, style)
        _viewer.value = state.copy(documentStyle = style, message = "문서 스타일을 적용했습니다.")
    }

    fun loadOutline() {
        val state = _viewer.value ?: return
        val source = sourceFile ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = "목차를 읽는 중…") }
            val entries = runCatching { PdfTools.outline(source) }.getOrDefault(emptyList())
            val mapped = entries.map { entry ->
                val current = entry.pageIndex?.let { sourcePage ->
                    state.pages.indexOfFirst { it.sourcePage == sourcePage }
                }?.takeIf { it >= 0 }
                entry.copy(pageIndex = current)
            }
            _outline.value = mapped
            _viewer.update { it?.copy(busy = false, busyLabel = null) }
        }
    }

    fun loadMetadata() {
        val source = sourceFile ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = "문서 정보를 읽는 중…") }
            _metadata.value = runCatching { PdfTools.metadata(source) }.getOrNull()
            _viewer.update { it?.copy(busy = false, busyLabel = null) }
        }
    }

    fun copyPageText() {
        val state = _viewer.value ?: return
        val file = workingFile ?: return
        viewModelScope.launch {
            val texts = pageTexts ?: runCatching { repo.extractPagesText(file) }.getOrDefault(emptyList())
            pageTexts = texts
            val current = texts.getOrNull(state.pageIndex).orEmpty().trim()
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    state.docName,
                    current.ifBlank { "이 페이지에서 복사할 텍스트를 찾지 못했습니다." },
                ),
            )
            _viewer.update {
                it?.copy(message = if (current.isBlank()) "이 페이지에는 텍스트가 없습니다." else "현재 페이지 텍스트를 복사했습니다.")
            }
        }
    }

    suspend fun buildExportFile(): File? {
        val state = _viewer.value ?: return null
        val source = sourceFile ?: return null
        val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
        val name = PdfShare.safeName("${state.docName.substringBeforeLast('.')}_주석.pdf", ".pdf")
        val target = File(dir, name)
        return withContext(Dispatchers.IO) {
            runCatching {
                target.outputStream().use { output ->
                    PdfExporter.export(
                        source = source,
                        specs = state.pages,
                        annotations = state.annotations,
                        out = output,
                        style = state.documentStyle,
                        pageCount = state.pageCount,
                    )
                }
                target
            }.getOrNull()
        }
    }

    fun shareCurrent() {
        val state = _viewer.value ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            val file = buildExportFile()
            _viewer.update { it?.copy(busy = false, busyLabel = null) }
            if (file != null) {
                PdfShare.sharePdf(getApplication(), file, state.docName)
            } else {
                _viewer.update { it?.copy(message = "공유할 PDF를 만들지 못했습니다.") }
            }
        }
    }

    fun printCurrent() {
        val state = _viewer.value ?: return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            val file = buildExportFile()
            _viewer.update { it?.copy(busy = false, busyLabel = null) }
            if (file != null) {
                PdfShare.printPdf(getApplication(), file, state.docName.substringBeforeLast('.'))
            } else {
                _viewer.update { it?.copy(message = "인쇄할 PDF를 만들지 못했습니다.") }
            }
        }
    }

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

    fun setOpacity(opacity: Float) = _viewer.update {
        it?.copy(opacity = opacity.coerceIn(0.1f, 1f))
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
            Tool.UNDERLINE -> AnnotationType.UNDERLINE
            Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
            Tool.ELLIPSE -> AnnotationType.ELLIPSE
            Tool.LINE -> AnnotationType.LINE
            Tool.REDACT -> AnnotationType.REDACT
            else -> return
        }
        val usablePoints = if (isTwoPointType(type)) {
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
                AnnotationType.RECT,
                AnnotationType.ARROW,
                AnnotationType.LINE,
                AnnotationType.ELLIPSE,
                AnnotationType.UNDERLINE,
                AnnotationType.STRIKEOUT -> maxOf(state.strokeWidth, 0.0025f)
                else -> state.strokeWidth
            },
            points = usablePoints,
            opacity = when (type) {
                AnnotationType.HIGHLIGHT, AnnotationType.REDACT -> 1f
                else -> state.opacity
            },
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
            opacity = state.opacity,
        )
        mutateAnnotations { it + annotation }
    }

    fun addStamp(point: NormalizedPoint, text: String) {
        val state = _viewer.value ?: return
        val label = text.trim()
        if (label.isEmpty()) return
        val annotation = Annotation(
            type = AnnotationType.STAMP,
            page = state.pageIndex,
            color = state.color,
            width = maxOf(state.strokeWidth, 0.003f),
            points = listOf(point),
            fontSize = maxOf(state.fontSize, 0.03f),
            opacity = state.opacity,
            stamp = label,
        )
        mutateAnnotations { it + annotation }
    }

    fun addSignature(point: NormalizedPoint, text: String) {
        val state = _viewer.value ?: return
        val signature = text.trim()
        if (signature.isEmpty()) return
        val annotation = Annotation(
            type = AnnotationType.SIGNATURE,
            page = state.pageIndex,
            color = state.color,
            width = maxOf(state.strokeWidth, 0.0025f),
            points = listOf(point),
            text = signature,
            fontSize = maxOf(state.fontSize, 0.03f),
            opacity = state.opacity,
        )
        mutateAnnotations { it + annotation }
    }

    fun deleteAnnotation(id: String) {
        val state = _viewer.value ?: return
        if (state.annotations.none { it.id == id }) return
        mutateAnnotations { annotations -> annotations.filterNot { it.id == id } }
    }

    fun annotationsForPage(page: Int): List<Annotation> =
        _viewer.value?.annotations?.filter { it.page == page }.orEmpty()

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
            AnnotationType.ARROW,
            AnnotationType.LINE,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT -> nearPolyline(annotation, eraser, radius)
            AnnotationType.ELLIPSE -> {
                if (annotation.points.size < 2) return false
                nearEllipse(annotation.points[0], annotation.points[1], eraser, radius)
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
            AnnotationType.STAMP -> {
                if (annotation.points.isEmpty()) return false
                val p = annotation.points.first()
                val label = annotation.stamp.ifBlank { "APPROVED" }
                val width = (label.length * annotation.fontSize * 0.66f + annotation.fontSize * 1.2f)
                    .coerceAtMost(0.85f)
                val height = annotation.fontSize * 1.9f
                eraser.any { e ->
                    e.x in (p.x - width / 2f - radius)..(p.x + width / 2f + radius) &&
                        e.y in (p.y - height / 2f - radius)..(p.y + height / 2f + radius)
                }
            }
            AnnotationType.SIGNATURE -> {
                if (annotation.points.isEmpty()) return false
                if (annotation.points.size > 1) {
                    nearPolyline(annotation, eraser, radius)
                } else {
                    val p = annotation.points.first()
                    val label = annotation.text.ifBlank { annotation.stamp }
                    val width = (label.length * annotation.fontSize * 0.52f).coerceAtMost(0.85f)
                    val height = annotation.fontSize * 1.5f
                    eraser.any { e ->
                        e.x in (p.x - radius)..(p.x + width + radius) &&
                            e.y in (p.y - radius)..(p.y + height + radius)
                    }
                }
            }
            AnnotationType.REDACT -> {
                if (annotation.points.size < 2) return false
                val a = annotation.points[0]
                val b = annotation.points[1]
                val minX = minOf(a.x, b.x); val maxX = maxOf(a.x, b.x)
                val minY = minOf(a.y, b.y); val maxY = maxOf(a.y, b.y)
                eraser.any { e ->
                    e.x in (minX - radius)..(maxX + radius) &&
                        e.y in (minY - radius)..(maxY + radius)
                }
            }
        }
    }

    private fun nearPolyline(
        annotation: Annotation,
        eraser: List<NormalizedPoint>,
        radius: Float,
    ): Boolean {
        val points = annotation.points
        if (points.size < 2) return false
        return points.zipWithNext().any { (a, b) ->
            eraser.any { e -> distanceToSegment(e, a, b) <= radius }
        }
    }

    private fun nearEllipse(
        a: NormalizedPoint,
        b: NormalizedPoint,
        eraser: List<NormalizedPoint>,
        radius: Float,
    ): Boolean {
        val cx = (a.x + b.x) / 2f
        val cy = (a.y + b.y) / 2f
        val rx = kotlin.math.abs(a.x - b.x) / 2f
        val ry = kotlin.math.abs(a.y - b.y) / 2f
        if (rx <= 0f || ry <= 0f) return false
        val samples = 48
        return eraser.any { e ->
            if (e.x !in (cx - rx - radius)..(cx + rx + radius) ||
                e.y !in (cy - ry - radius)..(cy + ry + radius)
            ) {
                return@any false
            }
            (0 until samples).any { i ->
                val angle = i * (2.0 * Math.PI / samples)
                val dx = e.x - (cx + rx * kotlin.math.cos(angle).toFloat())
                val dy = e.y - (cy + ry * kotlin.math.sin(angle).toFloat())
                dx * dx + dy * dy <= radius * radius
            }
        }
    }

    private fun isTwoPointType(type: AnnotationType): Boolean = when (type) {
        AnnotationType.RECT,
        AnnotationType.ARROW,
        AnnotationType.LINE,
        AnnotationType.ELLIPSE,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.REDACT -> true
        else -> false
    }

    private fun distanceToSegment(p: NormalizedPoint, a: NormalizedPoint, b: NormalizedPoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return kotlin.math.hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return kotlin.math.hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    private fun snapshot(state: ViewerUiState) = DocSnapshot(
        pages = state.pages,
        annotations = state.annotations,
        notes = state.notes,
        pageIndex = state.pageIndex,
    )

    private fun pushHistory(state: ViewerUiState) {
        undoStack.addLast(snapshot(state))
        if (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        _viewer.value = state.copy(canUndo = true, canRedo = false)
    }

    private fun restoreSnapshot(state: ViewerUiState, snapshot: DocSnapshot) {
        val pagesChanged = snapshot.pages != state.pages
        val restored = state.copy(
            pages = snapshot.pages,
            annotations = snapshot.annotations,
            notes = snapshot.notes,
            pageIndex = snapshot.pageIndex.coerceIn(0, (snapshot.pages.size - 1).coerceAtLeast(0)),
            pageCount = snapshot.pages.size,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
        if (pagesChanged) {
            rebuild(restored)
        } else {
            _viewer.value = restored
            persistNow()
            if (snapshot.pageIndex != state.pageIndex) renderPage(restored.pageIndex)
        }
    }

    private fun mutateAnnotations(block: (List<Annotation>) -> List<Annotation>) {
        val state = _viewer.value ?: return
        pushHistory(state)
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
        redoStack.addLast(snapshot(state))
        restoreSnapshot(state, previous)
    }

    fun redo() {
        val state = _viewer.value ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot(state))
        restoreSnapshot(state, next)
    }

    // ---------------------------------------------------------------- page layout

    fun rotatePage(clockwise: Boolean) {
        val state = _viewer.value ?: return
        rotatePages(setOf(state.pageIndex), if (clockwise) 90 else 270)
    }

    fun rotatePages(indices: Set<Int>, delta: Int) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val edit = PageOps.rotate(
            edit = PageEdit(state.pages, state.annotations, state.notes, state.pageIndex),
            indices = indices,
            delta = delta,
            aspectForPage = ::aspectForPage,
        )
        applyPageEdit(state, edit, "페이지를 회전하는 중…")
    }

    fun movePage(index: Int, direction: Int) {
        movePages(setOf(index), direction)
    }

    fun movePages(indices: Set<Int>, direction: Int) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val edit = PageOps.move(
            PageEdit(state.pages, state.annotations, state.notes, state.pageIndex),
            indices,
            direction,
        )
        applyPageEdit(state, edit, "페이지를 이동하는 중…")
    }

    fun deletePage(index: Int) {
        deletePages(setOf(index))
    }

    fun deletePages(indices: Set<Int>) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.size <= 1) return
        val edit = PageOps.delete(
            PageEdit(state.pages, state.annotations, state.notes, state.pageIndex),
            indices,
        )
        applyPageEdit(state, edit, "페이지를 삭제하는 중…")
    }

    fun duplicatePages(indices: Set<Int>) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val edit = PageOps.duplicate(
            PageEdit(state.pages, state.annotations, state.notes, state.pageIndex),
            indices,
        )
        applyPageEdit(state, edit, "페이지를 복제하는 중…")
    }

    fun insertBlankPage(afterIndex: Int) {
        insertBlankPages(afterIndex)
    }

    fun insertBlankPages(afterIndex: Int, count: Int = 1) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val edit = PageOps.insertBlank(
            PageEdit(state.pages, state.annotations, state.notes, state.pageIndex),
            afterIndex,
            count,
        ).copy(currentPage = (afterIndex + 1).coerceIn(0, state.pages.size + count - 1))
        applyPageEdit(state, edit, "빈 페이지를 추가하는 중…")
    }

    fun extractPages(indices: Set<Int>, uri: Uri) {
        val state = _viewer.value ?: return
        val source = sourceFile ?: return
        val ordered = indices.filter { it in state.pages.indices }.sorted()
        if (ordered.isEmpty()) return
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = "페이지를 추출하는 중…") }
            runCatching {
                val specs = ordered.map { state.pages[it] }
                val annotations = state.annotations
                    .filter { it.page in indices }
                    .map { it.copy(page = ordered.indexOf(it.page)) }
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        PdfExporter.export(
                            source = source,
                            specs = specs,
                            annotations = annotations,
                            out = output,
                            style = state.documentStyle,
                            pageCount = specs.size,
                        )
                    } ?: error("출력 파일을 열 수 없습니다.")
                }
            }.onSuccess {
                _viewer.update { it?.copy(busy = false, busyLabel = null, message = "페이지 ${ordered.size}장을 추출했습니다.") }
            }.onFailure { error ->
                _viewer.update { it?.copy(busy = false, busyLabel = null, message = "추출하지 못했습니다: ${error.message}") }
            }
        }
    }

    private fun applyPageEdit(state: ViewerUiState, edit: PageEdit, label: String) {
        if (edit.pages == state.pages &&
            edit.annotations == state.annotations &&
            edit.notes == state.notes &&
            edit.currentPage == state.pageIndex
        ) {
            return
        }
        pushHistory(state)
        rebuild(
            state.copy(
                pages = edit.pages,
                annotations = edit.annotations,
                notes = edit.notes,
                pageIndex = edit.currentPage,
                pageCount = edit.pages.size,
                busyLabel = label,
            ),
        )
    }

    private fun aspectForPage(@Suppress("UNUSED_PARAMETER") index: Int, spec: PageSpec): Float {
        val size = spec.sourcePage?.let { sourceSizes.getOrNull(it) } ?: return 1f
        val base = if (size.height > 0f) size.width / size.height else 1f
        return if (spec.extraRotation % 180 == 0) base else 1f / base
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
                return PageTransforms.rotate(annotation, delta, aspect)
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
            pushHistory(state)
            rebuild(
                state.copy(
                    pages = specs,
                    annotations = annotations,
                    notes = notes,
                    pageIndex = state.pageIndex.coerceIn(0, (specs.size - 1).coerceAtLeast(0)),
                    pageCount = specs.size,
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
                    _viewer.value = state.copy(
                        pageCount = pageCount,
                        pageIndex = safeIndex,
                        busy = true,
                        busyLabel = text(R.string.rendering_page),
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty(),
                        revision = state.revision + 1,
                    )
                    pageTexts = null
                    currentPageTextsKey = null
                    resetTextLayout()
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

    // ---------------------------------------------------------------- excerpts

    fun addCurrentPageExcerpt(title: String = "") {
        val state = _viewer.value ?: return
        val file = workingFile ?: return
        viewModelScope.launch {
            val texts = pageTexts ?: runCatching { repo.extractPagesText(file) }.getOrNull()
            if (texts == null) {
                _viewer.update { it?.copy(message = "페이지 텍스트를 읽지 못했습니다.") }
                return@launch
            }
            pageTexts = texts
            val excerptText = texts.getOrNull(state.pageIndex).orEmpty().trim()
            if (excerptText.isBlank()) {
                _viewer.update { it?.copy(message = "이 페이지에는 발췌할 텍스트가 없습니다.") }
                return@launch
            }
            if (_viewer.value?.docId != state.docId) return@launch
            val excerpt = Excerpt(
                pageIndex = state.pageIndex,
                text = excerptText,
                title = title.trim(),
            )
            val updated = (listOf(excerpt) + _excerpts.value).sortedByDescending { it.createdAt }
            _excerpts.value = updated
            withContext(Dispatchers.IO) { excerptStore.save(state.docId, updated) }
            _viewer.update { it?.copy(message = "발췌를 저장했습니다.") }
        }
    }

    fun deleteExcerpt(id: String) {
        val state = _viewer.value ?: return
        val updated = _excerpts.value.filterNot { it.id == id }
        if (updated.size == _excerpts.value.size) return
        _excerpts.value = updated
        viewModelScope.launch(Dispatchers.IO) { excerptStore.save(state.docId, updated) }
        _viewer.update { it?.copy(message = "발췌를 삭제했습니다.") }
    }

    fun updateExcerptTitle(id: String, title: String) {
        val state = _viewer.value ?: return
        val updated = _excerpts.value.map { excerpt ->
            if (excerpt.id == id) excerpt.copy(title = title.trim()) else excerpt
        }
        if (updated == _excerpts.value) return
        _excerpts.value = updated
        viewModelScope.launch(Dispatchers.IO) { excerptStore.save(state.docId, updated) }
    }

    /** Current page text for the excerpt sheet; extracts once and reuses the page-text cache. */
    suspend fun currentPageText(): String {
        val state = _viewer.value ?: return ""
        val file = workingFile ?: return ""
        val texts = pageTexts ?: runCatching { repo.extractPagesText(file) }.getOrNull() ?: return ""
        pageTexts = texts
        return texts.getOrNull(state.pageIndex).orEmpty().trim()
    }

    // ---------------------------------------------------------------- text selection

    /**
     * Loads word geometry for the working PDF on first use, then serves any page from
     * the per-document cache. PDFBox extraction stays off the main thread.
     */
    fun loadTextWords(pageIndex: Int = _viewer.value?.pageIndex ?: 0) {
        val state = _viewer.value ?: return
        val file = workingFile ?: return
        val key = textLayoutKeyFor(state, file)

        if (key == textLayoutKey) {
            publishTextWords(pageIndex)
            return
        }
        if (key == textLayoutLoadingKey && textLayoutJob?.isActive == true) {
            _textWords.value = emptyList()
            return
        }

        textLayoutJob?.cancel()
        textLayoutLoadingKey = key
        _textWords.value = emptyList()
        textLayoutJob = viewModelScope.launch {
            _viewer.update { it?.copy(textLoading = true) }
            val lines = try {
                withContext(Dispatchers.IO) { PdfTextLayout.lines(file) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (_viewer.value?.docId != state.docId) return@launch

            textLayoutLoadingKey = null
            if (lines == null) {
                textLayoutKey = null
                textLayoutLines = emptyList()
                _textWords.value = emptyList()
                _viewer.update {
                    it?.copy(textLoading = false, message = "텍스트 레이아웃을 읽지 못했습니다.")
                }
            } else {
                textLayoutKey = key
                textLayoutLines = lines
                publishTextWords(_viewer.value?.pageIndex ?: pageIndex)
                _viewer.update {
                    it?.copy(
                        textLoading = false,
                        message = if (lines.isEmpty()) {
                            "이 PDF에는 선택할 수 있는 텍스트가 없습니다."
                        } else {
                            it.message
                        },
                    )
                }
            }
        }
    }

    private fun publishTextWords(pageIndex: Int) {
        _textWords.value = textLayoutLines
            .filter { it.pageIndex == pageIndex }
            .flatMap { it.words }
    }

    private fun textLayoutKeyFor(state: ViewerUiState, file: File): String =
        "${state.docId}:${file.absolutePath}:${file.lastModified()}:${state.pages.size}:${state.revision}"

    private fun resetTextLayout() {
        textLayoutJob?.cancel()
        textLayoutJob = null
        textLayoutLines = emptyList()
        textLayoutKey = null
        textLayoutLoadingKey = null
        _textWords.value = emptyList()
        _viewer.update { it?.copy(textLoading = false) }
    }

    /** Adds one markup annotation per selected visual line. */
    fun addTextMarkup(words: List<PdfTextWord>, type: AnnotationType, color: Int) {
        val state = _viewer.value ?: return
        if (words.isEmpty()) return
        if (type != AnnotationType.HIGHLIGHT &&
            type != AnnotationType.UNDERLINE &&
            type != AnnotationType.STRIKEOUT
        ) {
            return
        }

        val pageWidth = state.displayWidthPt.takeIf { it > 1f } ?: 595f
        val pageHeight = state.displayHeightPt.takeIf { it > 1f } ?: 842f
        val heightToWidth = pageHeight / pageWidth
        val groups = PdfTextLayout.groupByLine(
            words.filter { it.text.isNotBlank() && it.pageIndex in 0 until state.pageCount },
            textLayoutLines,
        )

        val added = groups.mapNotNull { group ->
            val first = group.firstOrNull() ?: return@mapNotNull null
            val rect = group.drop(1).fold(first.rect) { acc, word -> acc.union(word.rect) }
            if (rect.width <= 0f || rect.height <= 0f) return@mapNotNull null

            val vertical = first.rotation % 180 != 0 ||
                (group.size > 1 && rect.height > rect.width * 1.5f)
            val normalizedThickness = if (vertical) {
                rect.width
            } else {
                rect.height * heightToWidth
            }
            val centerX = (rect.left + rect.right) / 2f
            val centerY = (rect.top + rect.bottom) / 2f
            val points = when (type) {
                AnnotationType.HIGHLIGHT -> if (vertical) {
                    listOf(
                        NormalizedPoint(centerX, rect.top.coerceIn(0f, 1f)),
                        NormalizedPoint(centerX, rect.bottom.coerceIn(0f, 1f)),
                    )
                } else {
                    listOf(
                        NormalizedPoint(rect.left.coerceIn(0f, 1f), centerY),
                        NormalizedPoint(rect.right.coerceIn(0f, 1f), centerY),
                    )
                }

                AnnotationType.UNDERLINE -> if (vertical) {
                    // With /Rotate 90 the glyph "up" direction is to the right, so the
                    // underline sits on the left edge; /Rotate 270 is the mirror case.
                    val x = if (first.rotation % 360 == 90) {
                        (rect.left + rect.width * 0.1f).coerceIn(0f, 1f)
                    } else {
                        (rect.right - rect.width * 0.1f).coerceIn(0f, 1f)
                    }
                    listOf(
                        NormalizedPoint(x, rect.top.coerceIn(0f, 1f)),
                        NormalizedPoint(x, rect.bottom.coerceIn(0f, 1f)),
                    )
                } else {
                    val y = if (first.rotation % 360 == 180) {
                        (rect.top + rect.height * 0.1f).coerceIn(0f, 1f)
                    } else {
                        (rect.bottom - rect.height * 0.1f).coerceIn(0f, 1f)
                    }
                    listOf(
                        NormalizedPoint(rect.left.coerceIn(0f, 1f), y),
                        NormalizedPoint(rect.right.coerceIn(0f, 1f), y),
                    )
                }

                else -> if (vertical) {
                    listOf(
                        NormalizedPoint(centerX, rect.top.coerceIn(0f, 1f)),
                        NormalizedPoint(centerX, rect.bottom.coerceIn(0f, 1f)),
                    )
                } else {
                    listOf(
                        NormalizedPoint(rect.left.coerceIn(0f, 1f), centerY),
                        NormalizedPoint(rect.right.coerceIn(0f, 1f), centerY),
                    )
                }
            }
            Annotation(
                type = type,
                page = first.pageIndex,
                color = color,
                width = when (type) {
                    AnnotationType.HIGHLIGHT -> normalizedThickness.coerceIn(0.012f, 0.06f)
                    else -> maxOf(0.0025f, normalizedThickness * 0.12f).coerceAtMost(0.008f)
                },
                points = points,
                fontSize = if (vertical) {
                    rect.width.coerceIn(0.008f, 0.08f)
                } else {
                    rect.height.coerceIn(0.008f, 0.08f)
                },
                opacity = 1f,
            )
        }
        if (added.isEmpty()) return

        mutateAnnotations { it + added }
        _viewer.update {
            it?.copy(
                message = when (type) {
                    AnnotationType.HIGHLIGHT -> "선택한 텍스트에 형광펜을 표시했습니다."
                    AnnotationType.UNDERLINE -> "선택한 텍스트에 밑줄을 표시했습니다."
                    else -> "선택한 텍스트에 취소선을 표시했습니다."
                },
            )
        }
    }

    fun addExcerptFromSelection(pageIndex: Int, text: String) {
        val state = _viewer.value ?: return
        val value = text.trim()
        if (value.isEmpty() || pageIndex !in 0 until state.pageCount) {
            _viewer.update { it?.copy(message = "발췌할 텍스트가 없습니다.") }
            return
        }
        val excerpt = Excerpt(pageIndex = pageIndex, text = value)
        val updated = (listOf(excerpt) + _excerpts.value).sortedByDescending { it.createdAt }
        _excerpts.value = updated
        viewModelScope.launch(Dispatchers.IO) { excerptStore.save(state.docId, updated) }
        _viewer.update { it?.copy(message = "선택한 텍스트를 발췌로 저장했습니다.") }
    }

    // ---------------------------------------------------------------- reading

    fun addCurrentPageBookmark(title: String) {
        val state = _viewer.value ?: return
        if (_bookmarks.value.any { it.pageIndex == state.pageIndex }) {
            _viewer.update { it?.copy(message = "이미 북마크한 페이지입니다.") }
            return
        }
        val label = title.trim().ifBlank { "${state.pageIndex + 1}쪽" }
        val bookmark = PdfBookmark(pageIndex = state.pageIndex, title = label)
        _bookmarks.value = PdfReadingState(bookmarks = _bookmarks.value)
            .withBookmark(bookmark)
            .bookmarks
        saveReadingNow()
        _viewer.update { it?.copy(message = "북마크를 추가했습니다.") }
    }

    fun deleteBookmark(id: String) {
        val updated = _bookmarks.value.filterNot { it.id == id }
        if (updated.size == _bookmarks.value.size) return
        _bookmarks.value = updated
        saveReadingNow()
        _viewer.update { it?.copy(message = "북마크를 삭제했습니다.") }
    }

    fun renameBookmark(id: String, title: String) {
        val label = title.trim()
        if (label.isEmpty()) return
        val current = _bookmarks.value.firstOrNull { it.id == id } ?: return
        if (current.title == label) return
        _bookmarks.value = PdfReadingState(bookmarks = _bookmarks.value)
            .withRenamedBookmark(id, label)
            .bookmarks
        saveReadingNow()
        _viewer.update { it?.copy(message = "북마크 이름을 바꿨습니다.") }
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
                        PdfExporter.export(
                            source = source,
                            specs = state.pages,
                            annotations = state.annotations,
                            out = output,
                            style = state.documentStyle,
                            pageCount = state.pageCount,
                        )
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
            docStyleStore.delete(id)
            excerptStore.delete(id)
            readingStore.delete(id)
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

    /** Saves the page the user landed on after a short pause so page flipping stays cheap. */
    private fun scheduleReadingSave(pageIndex: Int) {
        val state = _viewer.value ?: return
        val reading = PdfReadingState(
            lastPage = pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
            bookmarks = _bookmarks.value,
        ).normalized(state.pageCount)
        readingSaveJob?.cancel()
        readingSaveJob = viewModelScope.launch {
            delay(READING_SAVE_DELAY_MS)
            withContext(Dispatchers.IO) { readingStore.save(state.docId, reading) }
        }
    }

    /** Flushes progress and bookmarks without debounce (bookmark edits, document close). */
    private fun saveReadingNow() {
        readingSaveJob?.cancel()
        readingSaveJob = null
        val state = _viewer.value ?: return
        val reading = PdfReadingState(
            lastPage = state.pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
            bookmarks = _bookmarks.value,
        ).normalized(state.pageCount)
        viewModelScope.launch(Dispatchers.IO) { readingStore.save(state.docId, reading) }
    }

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun docIdFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val RENDER_SCALE = 2.2f
        const val READING_SAVE_DELAY_MS = 450L
    }
}
