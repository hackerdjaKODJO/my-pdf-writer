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
import com.hackerdjakodjo.mypdfwriter.data.DisplayPrefs
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyleStore
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.ExcerptStore
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
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
import com.hackerdjakodjo.mypdfwriter.pdf.PdfLink
import com.hackerdjakodjo.mypdfwriter.pdf.PdfLinks
import com.hackerdjakodjo.mypdfwriter.pdf.PdfMetadata
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import com.hackerdjakodjo.mypdfwriter.pdf.PdfRepository
import com.hackerdjakodjo.mypdfwriter.pdf.PdfShare
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextEdit
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLayout
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLine
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTools
import com.hackerdjakodjo.mypdfwriter.pdf.isIdentityPageSpecs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

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
    /** Post-it cards pinned to pages; [MemoCard.page] is the zero-based page index. */
    val memoCards: List<MemoCard> = emptyList(),
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
    /** Index into [searchHits]; -1 when there are no results. */
    val currentSearchHit: Int = -1,
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
    val noteDraft: String = "",
    val documentStyle: DocumentStyle = DocumentStyle(),
    val nightMode: Boolean = false,
    /** Screen dimming factor in 0.4..1.0, persisted per document. */
    val brightness: Float = DisplayPrefs.DEFAULT_BRIGHTNESS,
    /** Session-only page lock: blocks page navigation until explicitly unlocked. */
    val pageLocked: Boolean = false,
    val revision: Int = 0,
    val textLoading: Boolean = false,
    /**
     * Transient words currently selected on the canvas. Not persisted and not part
     * of the undo snapshot; the UI mirrors this into `setSelectedWords`.
     */
    val selectedWords: List<PdfTextWord> = emptyList(),
)

/** Pure memo-card list transitions; JVM-tested without an Android ViewModel. */
internal fun memoCardsOnPage(cards: List<MemoCard>, page: Int): List<MemoCard> =
    if (page < 0) emptyList() else cards.filter { it.page == page }

internal fun upsertMemoCardIn(cards: List<MemoCard>, card: MemoCard): List<MemoCard> {
    val safe = card.normalized()
    val replaced = cards.any { it.id == safe.id }
    return if (replaced) {
        cards.map { if (it.id == safe.id) safe else it }
    } else {
        cards + safe
    }
}

internal fun deleteMemoCardFrom(cards: List<MemoCard>, id: String): List<MemoCard> =
    cards.filterNot { it.id == id }

/**
 * Undo payload for page layout and ink only. Notes and memo cards are deliberately
 * absent: undoing an annotation must not roll back a later note/card edit.
 */
internal data class DocSnapshot(
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val pageIndex: Int,
)

/**
 * Applies a snapshot while keeping the *current* notes and memo cards. The note
 * draft is re-derived from those notes at the restored page, so undo/redo never
 * leaves a draft that no longer matches the page being shown.
 */
internal fun restoreSnapshotInto(
    state: ViewerUiState,
    snapshot: DocSnapshot,
    canUndo: Boolean,
    canRedo: Boolean,
): ViewerUiState {
    val page = snapshot.pageIndex.coerceIn(0, (snapshot.pages.size - 1).coerceAtLeast(0))
    return state.copy(
        pages = snapshot.pages,
        annotations = snapshot.annotations,
        notes = state.notes,
        memoCards = state.memoCards,
        pageIndex = page,
        pageCount = snapshot.pages.size,
        noteDraft = state.notes[page] ?: "",
        canUndo = canUndo,
        canRedo = canRedo,
    )
}

/**
 * Pure state of the coalescing doc-state writer. [pending] is the newest captured
 * state not yet written; [running] is true while the single IO drain loop owns it.
 */
internal data class DocSaveQueue(
    val pending: DocState? = null,
    val running: Boolean = false,
) {
    /** Returns the updated queue and whether a writer job must be started. */
    fun offer(state: DocState): Pair<DocSaveQueue, Boolean> =
        copy(pending = state, running = true) to !running

    /** Returns the updated queue and the state to write, or null when drained. */
    fun take(): Pair<DocSaveQueue, DocState?> =
        if (pending == null) {
            copy(running = false) to null
        } else {
            copy(pending = null, running = true) to pending
        }
}

/** Cancellation is control flow, not a failure: never let it become a UI error. */
internal fun rethrowIfCancelled(error: Throwable) {
    if (error is CancellationException) throw error
}

/** Guards background publication against stale work after a document swap. */
internal fun mayPublishViewerResult(
    isActive: Boolean,
    currentDocId: String?,
    docId: String,
): Boolean = isActive && currentDocId == docId

/** Drops annotations on pages that were not extracted before remapping indexes. */
internal fun remapExtractedAnnotations(
    annotations: List<Annotation>,
    ordered: List<Int>,
): List<Annotation> = annotations
    .filter { it.page in ordered }
    .map { it.copy(page = ordered.indexOf(it.page)) }

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

    private val _pageLinks = MutableStateFlow<List<PdfLink>>(emptyList())
    /** Link annotations (internal page jumps and copy-only URIs) for the current page. */
    val pageLinks: StateFlow<List<PdfLink>> = _pageLinks.asStateFlow()

    private var sourceFile: File? = null
    private var workingFile: File? = null
    private var currentUriString: String? = null
    private var pendingInitialPage: Int? = null
    private var sourceSizes: List<PageSize> = emptyList()
    private var pageTexts: List<String>? = null
    private var renderJob: Job? = null
    private var searchJob: Job? = null
    private var readingSaveJob: Job? = null
    private var textLayoutJob: Job? = null
    private var linkLoadJob: Job? = null
    private var textLayoutLines: List<PdfTextLine> = emptyList()
    private var textLayoutKey: String? = null
    private var textLayoutLoadingKey: String? = null
    private var currentPageTextsKey: String? = null

    private var docSaveJob: Job? = null
    private var docSaveQueue = DocSaveQueue()
    private val docSaveLock = Any()

    private val undoStack = ArrayDeque<DocSnapshot>()
    private val redoStack = ArrayDeque<DocSnapshot>()

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri, addToRecents: Boolean = true) {
        saveReadingNow()
        searchJob?.cancel()
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            runCatching {
                runCatching {
                    resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = repo.queryDisplayName(uri)
                val docId = docIdFor(uri)
                // Reuse this session's copy when possible; page edits never touch it.
                val source = repo.sourceFileFor(uri, File(sourceDir, "$docId.pdf"))
                // One PDDocument load replaces the old pageCount() + sourcePageSizes() pair.
                val info = repo.documentInfo(source)
                val sourceCount = info.pageCount
                val sizes = info.pageSizes
                val style = docStyleStore.load(docId)
                val saved = docStore.load(docId)?.normalized(sourceCount)
                val pages = saved?.pages ?: List(sourceCount) { PageSpec(it) }
                val annotations = saved?.annotations ?: emptyList()
                val notes = saved?.notes ?: emptyMap()
                val memoCards = saved?.memoCards ?: emptyList()
                val displayPrefs = saved?.displayPrefs ?: DisplayPrefs()
                val reading = (readingStore.load(docId) ?: PdfReadingState()).normalized(pages.size)
                // An unchanged layout needs no load+save pass: render the source directly.
                // newVersion() keeps the per-open bitmap-cache invalidation assemble() used to do.
                val working: File
                if (isIdentityPageSpecs(pages, sourceCount)) {
                    repo.newVersion()
                    working = source
                } else {
                    working = File(workingDir, "$docId.pdf")
                    repo.assemble(source, pages, working)
                }

                sourceFile = source
                workingFile = working
                currentUriString = uri.toString()
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
                val requestedPage = pendingInitialPage
                pendingInitialPage = null
                val restoredPage = (requestedPage ?: reading.lastPage)
                    .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _pageBitmap.value = null
                _pageLinks.value = emptyList()
                _viewer.value = ViewerUiState(
                    docId = docId,
                    docName = displayName,
                    pageCount = pages.size,
                    pageIndex = restoredPage,
                    pages = pages,
                    sourcePageCount = sourceCount,
                    annotations = annotations,
                    notes = notes,
                    memoCards = memoCards,
                    canUndo = false,
                    canRedo = false,
                    busy = true,
                    busyLabel = text(R.string.rendering_page),
                    noteDraft = notes[restoredPage] ?: "",
                    documentStyle = style,
                    nightMode = displayPrefs.nightMode,
                    brightness = displayPrefs.brightness,
                )
                persistNow()
                if (addToRecents) {
                    _library.value = libraryStore.upsert(
                        RecentDoc(uri.toString(), displayName, pages.size, System.currentTimeMillis()),
                    )
                }
                renderPage(restoredPage)
            }.onFailure { error ->
                // A bad/truncated copy must not poison later opens in this session.
                repo.invalidateSource(uri.toString())
                _viewer.value = null
                _pageBitmap.value = null
                _pageLinks.value = emptyList()
                _excerpts.value = emptyList()
                _bookmarks.value = emptyList()
                currentUriString = null
                pendingInitialPage = null
                _libraryMessage.value = text(R.string.error_generic, error.message ?: text(R.string.unsupported_pdf))
            }
        }
    }

    /** Opens a document at a requested page, used by study-note source links. */
    fun openDocumentAt(uri: Uri, pageIndex: Int) {
        pendingInitialPage = pageIndex.coerceAtLeast(0)
        openDocument(uri)
    }

    fun currentDocumentUri(): String? = currentUriString

    fun createBlankDocument(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                resolver.openOutputStream(uri)?.use { output -> repo.writeBlankPdf(output) }
                    ?: error("출력 파일을 열 수 없습니다.")
            }.onSuccess {
                // The URI now holds different bytes; drop any session copy of the old file.
                repo.invalidateSource(uri.toString())
                openDocument(uri)
            }.onFailure { error ->
                _libraryMessage.value = text(R.string.error_generic, error.message ?: "")
            }
        }
    }

    fun closeDocument() {
        renderJob?.cancel()
        searchJob?.cancel()
        linkLoadJob?.cancel()
        saveReadingNow()
        persistNow()
        _viewer.value = null
        _pageBitmap.value = null
        _pageLinks.value = emptyList()
        _outline.value = null
        _metadata.value = null
        _excerpts.value = emptyList()
        _bookmarks.value = emptyList()
        resetTextLayout()
        pageTexts = null
        sourceSizes = emptyList()
        sourceFile = null
        workingFile = null
        currentUriString = null
        pendingInitialPage = null
        repo.clearCache()
    }

    // ---------------------------------------------------------------- rendering

    private fun renderPage(index: Int) {
        val file = workingFile ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            val docId = _viewer.value?.docId ?: return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            val rendered = try {
                repo.renderPage(file, index, RENDER_SCALE)
            } catch (error: Throwable) {
                rethrowIfCancelled(error)
                null
            }
            // A superseded render (page flip or document swap) must not publish.
            if (!mayPublishViewerResult(isActive, _viewer.value?.docId, docId)) return@launch
            if (rendered == null) {
                _viewer.update {
                    it?.copy(busy = false, busyLabel = null, message = text(R.string.unsupported_pdf))
                }
                return@launch
            }
            _pageBitmap.value = rendered.bitmap
            loadPageLinks(index)
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
        if (!PageLockGate.allowsNavigation(state.pageLocked)) return
        goToPageUnlocked(index)
    }

    /** Shared implementation for navigation that has already passed the lock gate. */
    private fun goToPageUnlocked(index: Int) {
        val state = _viewer.value ?: return
        val target = index.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
        if (target == state.pageIndex && _pageBitmap.value != null) return
        renderPage(target)
        scheduleReadingSave(target)
    }

    fun nextPage() = goToPage((_viewer.value?.pageIndex ?: 0) + 1)

    fun previousPage() = goToPage((_viewer.value?.pageIndex ?: 0) - 1)

    /**
     * Study-note source links keep working while the page is locked: they are an
     * explicit "take me to the cited page" action, not incidental page stepping.
     */
    fun jumpToSourcePage(index: Int) {
        val state = _viewer.value ?: return
        if (!PageLockGate.allowsNavigation(state.pageLocked, explicitSourceJump = true)) return
        goToPageUnlocked(index)
    }

    fun togglePageLock() {
        val state = _viewer.value ?: return
        _viewer.value = state.copy(
            pageLocked = !state.pageLocked,
            message = if (state.pageLocked) "페이지 잠금을 해제했습니다." else "페이지를 잠갔습니다. 페이지를 넘기려면 잠금을 해제하세요.",
        )
    }

    suspend fun loadThumbnail(index: Int): Bitmap? {
        val file = workingFile ?: return null
        return runCatching { repo.renderPage(file, index, 0.55f)?.bitmap }.getOrNull()
    }

    /** Continuous-reader page image. The render scale follows the document zoom. */
    suspend fun loadReaderPage(index: Int, zoom: Float = 1f): Bitmap? {
        val file = workingFile ?: return null
        val scale = readerRenderScale(readerRenderZoom(zoom))
        return runCatching { repo.renderPage(file, index, scale)?.bitmap }.getOrNull()
    }

    fun currentSourceFile(): File? = sourceFile

    fun currentWorkingFile(): File? = workingFile

    /** Extracts tap targets for the displayed page off the main thread. */
    private fun loadPageLinks(pageIndex: Int) {
        val source = sourceFile ?: return
        val state = _viewer.value ?: return
        val spec = state.pages.getOrNull(pageIndex) ?: return
        val sourcePage = spec.sourcePage ?: return
        val docId = state.docId
        linkLoadJob?.cancel()
        linkLoadJob = viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                PdfLinks.linksForFile(source, sourcePage, spec.extraRotation)
            }
            val mapped = PdfLinks.remapToDocument(raw, state.pages)
            if (_viewer.value?.docId == docId && _viewer.value?.pageIndex == pageIndex) {
                _pageLinks.value = mapped
            }
        }
    }

    /** Toggles day/night and persists it with the document so it survives close/reopen. */
    fun toggleNightMode() {
        val state = _viewer.value ?: return
        _viewer.value = state.copy(nightMode = !state.nightMode)
        persistNow()
    }

    /** Screen dimming factor in 0.4..1.0. Clamped by [DisplayPrefs] and persisted. */
    fun setBrightness(value: Float) {
        val state = _viewer.value ?: return
        val clamped = DisplayPrefs.clampBrightness(value)
        if (state.brightness == clamped) return
        _viewer.value = state.copy(brightness = clamped)
        persistNow()
    }

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

    /**
     * Builds the flattened annotated PDF. Memo cards are viewer/document data and are
     * intentionally not drawn here yet: `PdfExporter` owns the overlay painter and is
     * outside this feature's file ownership, so PDF card export is deferred rather
     * than risking a regression in the existing annotation export path.
     */
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

    fun addStroke(points: List<NormalizedPoint>, durationMs: Long = 0L) {
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
        val safeDuration = durationMs.coerceAtLeast(0L)
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
            createdAt = System.currentTimeMillis() - safeDuration,
            durationMs = safeDuration,
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
            AnnotationType.REDACT,
            AnnotationType.COVER_TEXT -> {
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
        AnnotationType.REDACT,
        AnnotationType.COVER_TEXT -> true
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
        val restored = restoreSnapshotInto(
            state = state,
            snapshot = snapshot,
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

    // ---------------------------------------------------------------- memo cards

    /**
     * One memo edit is one undo step: snapshot the current document, publish the new
     * card list and flush the sidecar immediately, exactly like ink mutations.
     */
    private fun mutateMemoCards(block: (List<MemoCard>) -> List<MemoCard>) {
        val state = _viewer.value ?: return
        pushHistory(state)
        _viewer.value = state.copy(
            memoCards = block(state.memoCards),
            canUndo = true,
            canRedo = false,
        )
        persistNow()
    }

    /** Adds or replaces a card by id. Geometry/text are normalized before storage. */
    fun upsertMemoCard(card: MemoCard) {
        val safe = card.normalized()
        mutateMemoCards { cards -> upsertMemoCardIn(cards, safe) }
    }

    /** Deletes a card by id; unknown ids are a no-op (no undo step, no write). */
    fun deleteMemoCard(id: String) {
        val state = _viewer.value ?: return
        if (state.memoCards.none { it.id == id }) return
        mutateMemoCards { cards -> deleteMemoCardFrom(cards, id) }
    }

    /** Cards pinned to one document page, in insertion order. */
    fun memoCardsForPage(page: Int): List<MemoCard> = memoCardsOnPage(_viewer.value?.memoCards.orEmpty(), page)

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

    /**
     * Runs a [PageOps] operation with temporary marker annotations, then reads the
     * markers back to remap memo cards with the *same* page permutation PageOps used
     * for ink. This keeps delete/move/duplicate/insert in sync without copying their
     * index rules, and duplicated cards get fresh ids just like duplicated strokes.
     */
    private fun pageEditWithMemos(
        state: ViewerUiState,
        block: (PageEdit) -> PageEdit,
    ): Pair<PageEdit, List<MemoCard>> {
        if (state.memoCards.isEmpty()) {
            return block(PageEdit(state.pages, state.annotations, state.notes, state.pageIndex)) to emptyList()
        }
        // A per-call marker makes a collision with real user text impossible.
        val marker = "$MEMO_SENTINEL_MARKER${UUID.randomUUID()}:"
        val markerId = "$MEMO_SENTINEL_ID${UUID.randomUUID()}-"
        val markers = state.pages.indices.map { page ->
            Annotation(
                id = "$markerId$page",
                type = AnnotationType.PEN,
                page = page,
                color = 0,
                width = 0f,
                text = "$marker$page",
            )
        }
        val edit = block(
            PageEdit(state.pages, state.annotations + markers, state.notes, state.pageIndex),
        )
        val cleanAnnotations = edit.annotations.filterNot { it.text.startsWith(marker) }
        val mapped = ArrayList<MemoCard>()
        edit.annotations.forEach { sentinel ->
            if (!sentinel.text.startsWith(marker)) return@forEach
            val oldPage = sentinel.text.removePrefix(marker).toIntOrNull() ?: return@forEach
            val copied = !sentinel.id.startsWith(markerId)
            state.memoCards.filter { it.page == oldPage }.forEach { card ->
                mapped.add(
                    card.copy(
                        id = if (copied) UUID.randomUUID().toString() else card.id,
                        page = sentinel.page,
                    ).normalized(),
                )
            }
        }
        return edit.copy(annotations = cleanAnnotations) to mapped
    }

    fun rotatePage(clockwise: Boolean) {
        val state = _viewer.value ?: return
        rotatePages(setOf(state.pageIndex), if (clockwise) 90 else 270)
    }

    fun rotatePages(indices: Set<Int>, delta: Int) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.rotate(
                edit = base,
                indices = indices,
                delta = delta,
                aspectForPage = ::aspectForPage,
            )
        }
        applyPageEdit(state, edit, "페이지를 회전하는 중…", memos)
    }

    fun movePage(index: Int, direction: Int) {
        movePages(setOf(index), direction)
    }

    fun movePages(indices: Set<Int>, direction: Int) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.move(base, indices, direction)
        }
        applyPageEdit(state, edit, "페이지를 이동하는 중…", memos)
    }

    fun deletePage(index: Int) {
        deletePages(setOf(index))
    }

    fun deletePages(indices: Set<Int>) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.size <= 1) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.delete(base, indices)
        }
        applyPageEdit(state, edit, "페이지를 삭제하는 중…", memos)
    }

    fun duplicatePages(indices: Set<Int>) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.duplicate(base, indices)
        }
        applyPageEdit(state, edit, "페이지를 복제하는 중…", memos)
    }

    fun insertBlankPage(afterIndex: Int) {
        insertBlankPages(afterIndex)
    }

    fun insertBlankPages(afterIndex: Int, count: Int = 1) {
        val state = _viewer.value ?: return
        if (state.busy) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.insertBlank(base, afterIndex, count)
        }
        applyPageEdit(
            state,
            edit.copy(currentPage = (afterIndex + 1).coerceIn(0, state.pages.size + count - 1)),
            "빈 페이지를 추가하는 중…",
            memos,
        )
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
                val annotations = remapExtractedAnnotations(state.annotations, ordered)
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

    private fun applyPageEdit(
        state: ViewerUiState,
        edit: PageEdit,
        label: String,
        memoCards: List<MemoCard> = state.memoCards,
    ) {
        if (edit.pages == state.pages &&
            edit.annotations == state.annotations &&
            edit.notes == state.notes &&
            memoCards == state.memoCards &&
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
                memoCards = memoCards,
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
        viewModelScope.launch {
            // Reuse the sizes loaded at open time instead of loading the PDF again.
            val sizes = sourceSizes
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
            val memoCards = state.memoCards.mapNotNull { card ->
                val sourcePage = state.pages.getOrNull(card.page)?.sourcePage ?: return@mapNotNull null
                if (sourcePage !in specs.indices) return@mapNotNull null
                card.copy(page = sourcePage).normalized()
            }
            pushHistory(state)
            rebuild(
                state.copy(
                    pages = specs,
                    annotations = annotations,
                    notes = notes,
                    memoCards = memoCards,
                    pageIndex = state.pageIndex.coerceIn(0, (specs.size - 1).coerceAtLeast(0)),
                    pageCount = specs.size,
                ),
            )
        }
    }

    private fun rebuild(state: ViewerUiState) {
        val source = sourceFile ?: return
        // Always assemble into a distinct working path: an identity open may have
        // pointed workingFile at the source, and assembling onto it would corrupt it.
        val working = File(workingDir, "${state.docId}.pdf")
        viewModelScope.launch {
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.building_pages)) }
            runCatching { repo.assemble(source, state.pages, working) }
                .onSuccess {
                    workingFile = working
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
                    _pageLinks.value = emptyList()
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
            _viewer.update { it?.copy(textLoading = false) }
            publishTextWords(pageIndex)
            return
        }
        if (key == textLayoutLoadingKey && textLayoutJob?.isActive == true) {
            _textWords.value = emptyList()
            _viewer.update { it?.copy(textLoading = true) }
            return
        }

        textLayoutJob?.cancel()
        textLayoutLoadingKey = key
        _textWords.value = emptyList()
        // Flip the loading state before launching so the selection UI never
        // shows a false "no text" empty state while extraction is starting.
        _viewer.update { it?.copy(textLoading = true) }
        textLayoutJob = viewModelScope.launch {
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

    /** Mirrors the canvas selection into the view model for text-replacement actions. */
    fun setSelectedWords(words: List<PdfTextWord>) {
        if (_viewer.value?.selectedWords == words) return
        _viewer.update { it?.copy(selectedWords = words) }
    }

    /**
     * Adds one `COVER_TEXT` annotation as a single undo step: an opaque cover over the
     * selected source words and [replacement] drawn on top, then clears the selection.
     *
     * This is a **visual overlay only**. The source PDF bytes and its text content
     * stream are never rewritten; the covered words are merely hidden on screen and
     * under the overlay in a flattened export, where they remain extractable.
     */
    fun replaceSelectedText(replacement: String) {
        val state = _viewer.value ?: return
        val value = replacement.trim()
        if (value.isEmpty()) {
            _viewer.update { it?.copy(message = "바꿀 텍스트를 입력하세요.") }
            return
        }
        val geometry = PdfTextEdit.geometry(state.selectedWords)
        if (geometry == null || geometry.page !in 0 until state.pageCount) {
            _viewer.update { it?.copy(message = "바꿀 텍스트를 먼저 선택하세요.") }
            return
        }

        val annotation = Annotation(
            type = AnnotationType.COVER_TEXT,
            page = geometry.page,
            // Paper-like default cover; the painter derives readable ink from it.
            color = COVER_PAPER_COLOR,
            width = 0f,
            points = listOf(
                NormalizedPoint(geometry.rect.left, geometry.rect.top),
                NormalizedPoint(geometry.rect.right, geometry.rect.bottom),
            ),
            text = value,
            fontSize = geometry.fontSize,
            opacity = 1f,
            createdAt = System.currentTimeMillis(),
        )
        mutateAnnotations { it + annotation }
        _viewer.update {
            it?.copy(
                selectedWords = emptyList(),
                message = "선택한 텍스트를 시각적으로 바꿨습니다. 원본 PDF는 그대로입니다.",
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
        val trimmed = query.trim()
        _viewer.update { it?.copy(searchQuery = query) }
        searchJob?.cancel()
        if (trimmed.isEmpty()) {
            _viewer.update {
                it?.copy(
                    searchHits = emptyList(),
                    currentSearchHit = -1,
                    searching = false,
                    searchFailed = false,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(220)
            if (!mayPublishViewerResult(isActive, _viewer.value?.docId, state.docId)) return@launch
            _viewer.update { it?.copy(searching = true, searchFailed = false) }
            val file = workingFile ?: return@launch
            val key = "${state.docId}:${file.lastModified()}:${state.pages.size}"
            val texts = pageTexts?.takeIf { currentPageTextsKey == key } ?: try {
                repo.extractPagesText(file)
            } catch (error: Throwable) {
                rethrowIfCancelled(error)
                null
            }
            if (texts == null) {
                if (mayPublishViewerResult(isActive, _viewer.value?.docId, state.docId)) {
                    _viewer.update { it?.copy(searching = false, searchFailed = true) }
                }
                return@launch
            }
            pageTexts = texts
            currentPageTextsKey = key
            val hits = SearchNavigation.hitsForPages(texts, trimmed)
            if (!mayPublishViewerResult(isActive, _viewer.value?.docId, state.docId)) return@launch
            _viewer.update { current ->
                current?.copy(
                    searchHits = hits,
                    searching = false,
                    // A successful search always clears an earlier failure.
                    searchFailed = false,
                    // A new query restarts at the first result; a repeat search keeps
                    // the reader's place, clamped to the new list.
                    currentSearchHit = SearchNavigation.indexAfterQueryChange(
                        currentIndex = state.currentSearchHit,
                        previousQuery = state.searchQuery,
                        newQuery = trimmed,
                        count = hits.size,
                    ),
                )
            }
        }
    }

    /** Selects a result row and jumps to its page; the highlight follows the page change. */
    fun goToSearchHit(index: Int) {
        val state = _viewer.value ?: return
        if (!PageLockGate.allowsNavigation(state.pageLocked)) return
        val safe = SearchNavigation.clamp(index, state.searchHits.size)
        if (safe < 0) return
        _viewer.update { it?.copy(currentSearchHit = safe) }
        goToPage(state.searchHits[safe].page)
    }

    /** Wraps through result pages in either direction without leaving search mode. */
    fun moveSearchHit(delta: Int) {
        val state = _viewer.value ?: return
        if (!PageLockGate.allowsNavigation(state.pageLocked)) return
        val count = state.searchHits.size
        if (count == 0) return
        val target = when {
            delta > 0 -> SearchNavigation.next(state.currentSearchHit, count)
            delta < 0 -> SearchNavigation.previous(state.currentSearchHit, count)
            else -> SearchNavigation.clamp(state.currentSearchHit, count)
        }
        _viewer.update { it?.copy(currentSearchHit = target) }
        goToPage(state.searchHits[target].page)
    }

    fun nextSearchHit() = moveSearchHit(1)

    fun previousSearchHit() = moveSearchHit(-1)

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
            repo.invalidateSource(uri)
        }
    }

    fun clearMessage() = _viewer.update { it?.copy(message = null) }

    fun clearLibraryMessage() {
        _libraryMessage.value = null
    }

    fun reloadLibrary() {
        _library.value = libraryStore.load()
    }

    fun dismissError() {
        _viewer.value = null
    }

    // ---------------------------------------------------------------- helpers

    private fun persistNow() {
        val state = _viewer.value ?: return
        // Capture the whole DocState on the Main thread before handing it to the writer.
        val doc = DocState(
            docId = state.docId,
            displayName = state.docName,
            pages = state.pages,
            annotations = state.annotations,
            notes = state.notes,
            displayPrefs = DisplayPrefs(
                nightMode = state.nightMode,
                brightness = state.brightness,
            ),
            memoCards = state.memoCards,
        )
        val startWriter: Boolean
        synchronized(docSaveLock) {
            val (updated, shouldStart) = docSaveQueue.offer(doc)
            docSaveQueue = updated
            startWriter = shouldStart
        }
        // One drain loop owns the tmp file; never cancel it mid-write. Close/error
        // paths keep calling persistNow(), so their final state is always written.
        if (startWriter) docSaveJob = viewModelScope.launch(Dispatchers.IO) { drainDocSaves() }
    }

    /** Writes the newest pending state in a loop until nothing remains queued. */
    private fun drainDocSaves() {
        while (true) {
            val next: DocState?
            synchronized(docSaveLock) {
                val (updated, state) = docSaveQueue.take()
                docSaveQueue = updated
                next = state
            }
            if (next == null) return
            docStore.save(next)
        }
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
        /** Opaque paper-like cover for visual text replacement. */
        const val COVER_PAPER_COLOR = -1 // 0xFFFFFFFF, opaque white
        /** Temporary markers used to reuse PageOps' page mapping for memo cards. */
        const val MEMO_SENTINEL_MARKER = "pdfmemo-page:"
        const val MEMO_SENTINEL_ID = "pdfmemo-sentinel-"
    }
}
