package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.AudioNoteStore
import com.hackerdjakodjo.mypdfwriter.data.DocState
import com.hackerdjakodjo.mypdfwriter.data.DocStateStore
import com.hackerdjakodjo.mypdfwriter.data.DisplayPrefs
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyleStore
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.ExcerptStore
import com.hackerdjakodjo.mypdfwriter.data.HighlightInk
import com.hackerdjakodjo.mypdfwriter.data.InkSettings
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenSettingsRepository
import com.hackerdjakodjo.mypdfwriter.data.PenSettingsWriteResult
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageEdit
import com.hackerdjakodjo.mypdfwriter.data.PageOps
import com.hackerdjakodjo.mypdfwriter.data.PageSize
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.PageTransforms
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.data.PdfReadingState
import com.hackerdjakodjo.mypdfwriter.data.PdfReadingStore
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.triangleVertices
import com.hackerdjakodjo.mypdfwriter.debug.DevLog
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.EraserGeometry
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import com.hackerdjakodjo.mypdfwriter.pdf.PdfExporter
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFlowTrace
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
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTools
import com.hackerdjakodjo.mypdfwriter.pdf.isIdentityPageSpecs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

data class ViewerUiState(
    val docId: String = "",
    val docName: String = "",
    /** SHA-256 of the original source bytes; empty only before a document is ready. */
    val contentFingerprint: String = "",
    /** True when this URI now holds different bytes and old sidecars were isolated. */
    val sourceReplaced: Boolean = false,
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
    val penStyle: PenStyle = InkSettings.DEFAULT.penStyle,
    val color: Int = InkSettings.DEFAULT.color,
    val strokeWidth: Float = InkSettings.DEFAULT.width,
    val highlightWidth: Float = HighlightInk.DEFAULT_WIDTH,
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
    /** View-only crop margins; never change the source PDF or exported pages. */
    val cropInsets: CropInsets = CropInsets.NONE,
    /** When true, even pages mirror the left/right crop insets. */
    val cropOddEven: Boolean = false,
    /** Session-only page lock: blocks page navigation until explicitly unlocked. */
    val pageLocked: Boolean = false,
    val revision: Int = 0,
    val textLoading: Boolean = false,
    /**
     * Transient words currently selected on the canvas. Not persisted and not part
     * of the undo snapshot; the UI mirrors this into `setSelectedWords`.
     */
    val selectedWords: List<PdfTextWord> = emptyList(),
    /** Shared with the note editor: stylus pressure capture can be turned off. */
    val pressureInk: Boolean = InkSettings.DEFAULT.pressureInk,
    /** Shared pressure response used by new PDF strokes and Pen Studio preview. */
    val pressureCurve: PressureCurve = InkSettings.DEFAULT.pressureCurve,
    /** Highlighter opacity is independent from pen opacity. */
    val highlightOpacity: Float = InkSettings.DEFAULT.highlightOpacity,
    /** Latest auto-save durability for this viewer session; enqueue alone is not success. */
    val saveStatus: PdfSaveStatus = PdfSaveStatus.IDLE,
)

/** PDF-only projection of the process-wide shared ink state. */
internal fun ViewerUiState.withInkSettings(ink: InkSettings): ViewerUiState = copy(
    penStyle = ink.penStyle,
    color = ink.color,
    strokeWidth = ink.width,
    opacity = ink.opacity,
    pressureCurve = ink.pressureCurve,
    highlightWidth = ink.highlightWidth,
    pressureInk = ink.pressureInk,
    highlightOpacity = ink.highlightOpacity,
)

/**
 * One document-bound PDF capture. [sourcePage] is always the original source
 * ordinal, never the current viewer index (which can move after page edits).
 */
data class PdfStudyCapture(
    val text: String,
    val sourcePage: Int,
    val rect: NormalizedRect?,
    val excerptId: String?,
    val source: StudySource,
    val rotation: Int = 0,
)

/** Exact union of usable selected words on one viewer page, without cover padding. */
internal fun selectedWordsUnionRect(
    words: List<PdfTextWord>,
    pageIndex: Int,
): NormalizedRect? {
    val usable = words.filter { word ->
        word.pageIndex == pageIndex && word.text.isNotBlank() &&
            word.rect.width > 0f && word.rect.height > 0f &&
            listOf(word.rect.left, word.rect.top, word.rect.right, word.rect.bottom)
                .all { it.isFinite() }
    }
    if (usable.isEmpty()) return null
    val union = usable.drop(1).fold(usable.first().rect) { acc, word -> acc.union(word.rect) }
    return NormalizedRect(
        left = union.left.coerceIn(0f, 1f),
        top = union.top.coerceIn(0f, 1f),
        right = union.right.coerceIn(0f, 1f),
        bottom = union.bottom.coerceIn(0f, 1f),
    ).takeIf { it.width > 0f && it.height > 0f }
}

/** Pure provenance builder shared by selection, drag and excerpt capture. */
internal fun buildPdfStudyCapture(
    state: ViewerUiState,
    pageIndex: Int,
    text: String,
    words: List<PdfTextWord> = emptyList(),
    excerptId: String? = null,
): PdfStudyCapture? {
    val value = text.trim()
    val spec = state.pages.getOrNull(pageIndex) ?: return null
    val sourcePage = spec.sourcePage?.takeIf { it >= 0 } ?: return null
    if (value.isEmpty() || state.docId.isBlank() || state.contentFingerprint.isBlank()) return null
    return PdfStudyCapture(
        text = value,
        sourcePage = sourcePage,
        rect = selectedWordsUnionRect(words, pageIndex),
        excerptId = excerptId,
        source = StudySource(
            docName = state.docName,
            docUri = null,
            docId = state.docId,
            contentFingerprint = state.contentFingerprint,
        ),
        rotation = PageSpec.normalizeRotation(
            (words.firstOrNull { it.pageIndex == pageIndex }?.rotation ?: 0) +
                spec.extraRotation,
        ),
    )
}

/**
 * Pure half of [PdfViewModel.validatePdfStudyCapture]. A capture is accepted only
 * for the exact active source bytes and an original source page that still
 * exists after page edits. The caller owns publishing [onRejected] to the UI.
 */
internal fun validatePdfStudyCaptureAgainst(
    state: ViewerUiState,
    capture: PdfStudyCapture,
    currentSource: StudySource?,
    onRejected: (String) -> Unit,
): PdfStudyCapture? {
    if (currentSource == null || capture.text.isBlank() || capture.sourcePage < 0) return null
    if (capture.source.docId != currentSource.docId ||
        capture.source.contentFingerprint != currentSource.contentFingerprint
    ) {
        onRejected("다른 문서 또는 바뀐 PDF의 발췌는 추가할 수 없습니다.")
        return null
    }
    if (state.pages.none { it.sourcePage == capture.sourcePage }) {
        onRejected("원본 PDF에서 해당 페이지를 찾지 못했습니다.")
        return null
    }
    return capture.copy(source = currentSource)
}

/** Resolves an original source ordinal after insert/delete/move/duplicate/rotate. */
internal fun resolveOriginalSourcePage(
    pages: List<PageSpec>,
    sourcePage: Int,
): StudyLinkResolution {
    if (sourcePage < 0 || pages.isEmpty()) return StudyLinkResolution.PageMissing
    val matches = pages.indices.filter { pages[it].sourcePage == sourcePage }
    return when (matches.size) {
        0 -> StudyLinkResolution.PageMissing
        1 -> StudyLinkResolution.Found(
            viewPageIndex = matches.first(),
            rect = null,
            ambiguous = false,
        )
        else -> StudyLinkResolution.Found(
            viewPageIndex = matches.first(),
            rect = null,
            ambiguous = true,
        )
    }
}

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
 * Undo payload for page layout and ink. Page operations capture notes, memo cards
 * and bookmarks too, because their page indices were remapped by the edit. Legacy
 * snapshots without those fields keep the current values for ink-only undo.
 */
internal data class DocSnapshot(
    val pages: List<PageSpec>,
    val annotations: List<Annotation>,
    val pageIndex: Int,
    // Null means "legacy snapshot, keep the current values" (ink-only undo).
    // A page-layout edit captures all three so undo cannot mis-associate them.
    val notes: Map<Int, String>? = null,
    val memoCards: List<MemoCard>? = null,
    val bookmarks: List<PdfBookmark>? = null,
)

/**
 * Applies a snapshot, restoring captured notes/cards when the page layout changed
 * and keeping current ones for ink-only undo. The note draft is re-derived from
 * the notes at the restored page.
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
        notes = snapshot.notes ?: state.notes,
        memoCards = snapshot.memoCards ?: state.memoCards,
        pageIndex = page,
        pageCount = snapshot.pages.size,
        noteDraft = (snapshot.notes ?: state.notes)[page] ?: "",
        canUndo = canUndo,
        canRedo = canRedo,
    )
}

/**
 * One captured state and the viewer session that queued it. The generation guards
 * same-id reopen races; [reportStatus] is false for the initial sidecar write so
 * a fresh document remains IDLE until its state actually changes.
 */
internal data class DocSaveRequest(
    val state: DocState,
    val viewerGeneration: Long,
    val reportStatus: Boolean,
    val sequence: Long = 0L,
)

/**
 * Pure state of the coalescing doc-state writer. [pending] holds the newest
 * captured state *per document*, keyed by docId; [running] is true while the
 * single IO drain loop owns the queue. Keying by document means switching docs
 * before a drain cannot drop the previous document's last state.
 */
internal data class DocSaveQueue(
    val pending: Map<String, DocSaveRequest> = emptyMap(),
    val running: Boolean = false,
    val nextSequence: Long = 0L,
    private val latestSequences: Map<String, Long> = emptyMap(),
) {
    /** Returns the updated queue and whether a writer job must be started. */
    fun offer(request: DocSaveRequest): Pair<DocSaveQueue, Boolean> {
        val sequence = nextSequence + 1L
        val queued = request.copy(sequence = sequence)
        return copy(
            pending = pending + (queued.state.docId to queued),
            running = true,
            nextSequence = sequence,
            latestSequences = latestSequences + (queued.state.docId to sequence),
        ) to !running
    }

    /** Returns the updated queue and one state to write, or null when drained. */
    fun take(): Pair<DocSaveQueue, DocSaveRequest?> = takeFor(null)

    /**
     * Drains one document without disturbing another document's pending state.
     * Used by the synchronous same-document reopen barrier.
     */
    fun takeFor(docId: String?): Pair<DocSaveQueue, DocSaveRequest?> {
        if (pending.isEmpty()) return copy(running = false) to null
        val entry = if (docId == null) {
            pending.entries.first()
        } else {
            pending.entries.firstOrNull { it.key == docId }
        }
        if (entry == null) return this to null
        val remaining = pending - entry.key
        return copy(pending = remaining, running = remaining.isNotEmpty()) to entry.value
    }

    fun hasPending(docId: String): Boolean = pending.containsKey(docId)

    /** False once a newer request for this document has superseded [request]. */
    fun isLatest(request: DocSaveRequest): Boolean = latestSequences[request.state.docId] == request.sequence
}

/** One debounced/immediate reading write, retained until a reopen barrier drains it. */
internal data class PendingReadingWrite(
    val docId: String,
    val state: PdfReadingState,
)

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

/**
 * Monotonic token for document opens. Each [begin] supersedes every earlier
 * open; [invalidate] drops all in-flight opens (used by close/discard). Pure so
 * the rapid-double-open policy is JVM-testable without an Android ViewModel.
 */
internal class OpenGenerationGate {
    private var generation: Int = 0

    val current: Int get() = generation

    fun begin(): Int = ++generation

    fun invalidate() {
        generation++
    }

    fun isCurrent(token: Int): Boolean = token == generation
}

/** True only while work tagged with [token] is still the active document open. */
/** A failed import must not close an unrelated document already on screen. */
internal fun keepViewerOnFailedOpen(openDocId: String?, openUri: String?): Boolean =
    openDocId != null || openUri != null

internal fun mayPublishOpenResult(
    isActive: Boolean,
    token: Int?,
    currentToken: Int,
): Boolean = isActive && (token == null || token == currentToken)

/**
 * Remembers the fingerprint last associated with a URI-derived docId. Kept apart
 * from DocState so a changed source can be detected before that state is loaded.
 */
private class SourceIdentityStore(context: Context) {
    private val dir = File(context.filesDir, "source-identities").apply { mkdirs() }

    fun load(docId: String): String? {
        if (docId.isBlank()) return null
        val file = File(dir, "$docId.json")
        if (!file.exists()) return null
        return runCatching {
            JSONObject(file.readText()).optString("fingerprint").takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun save(docId: String, fingerprint: String) {
        if (docId.isBlank() || fingerprint.isBlank()) return
        dir.mkdirs()
        val target = File(dir, "$docId.json")
        val tmp = File(dir, "$docId.json.tmp")
        tmp.writeText(JSONObject().put("docId", docId).put("fingerprint", fingerprint).toString())
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}

/**
 * Moves stale canonical/tmp sidecars aside before a changed source gets a fresh
 * neutral state. Failure to preserve a backup never causes old bytes to be
 * adopted; the caller still ignores them.
 */
internal fun quarantineReplacedSidecars(
    filesDir: File,
    docId: String,
    previousFingerprint: String,
    nowMs: Long = System.currentTimeMillis(),
): Boolean {
    val suffix = "replaced-${previousFingerprint.take(12)}-$nowMs"
    var allIsolated = true
    listOf("docstates", "excerpts", "reading").forEach { directory ->
        listOf("$docId.json", "$docId.json.tmp").forEach { name ->
            val source = File(File(filesDir, directory), name)
            if (!source.exists()) return@forEach
            val backup = File(source.parentFile, "$name.$suffix")
            val isolated = runCatching {
                if (source.renameTo(backup)) {
                    true
                } else {
                    source.copyTo(backup, overwrite = true)
                    source.delete()
                }
            }.getOrDefault(false)
            if (!isolated || source.exists() || !backup.isFile) allIsolated = false
        }
    }
    return allIsolated
}

/** Immutable export payload captured at SAF-launch time, bound to one document. */
data class PdfExportSnapshot(
    val docId: String,
    val docName: String,
    val source: File,
    val state: DocState,
    val style: DocumentStyle,
)

/** Captures the whole DocState on the Main thread before handing it to a writer. */
internal fun viewerDocState(state: ViewerUiState): DocState = DocState(
    docId = state.docId,
    displayName = state.docName,
    pages = state.pages,
    annotations = state.annotations,
    notes = state.notes,
    displayPrefs = DisplayPrefs(
        nightMode = state.nightMode,
        brightness = state.brightness,
        cropLeft = state.cropInsets.left,
        cropTop = state.cropInsets.top,
        cropRight = state.cropInsets.right,
        cropBottom = state.cropInsets.bottom,
        cropOddEven = state.cropOddEven,
        zoom = state.zoom,
    ),
    memoCards = state.memoCards,
)

/** Drops annotations on pages that were not extracted before remapping indexes. */
internal fun remapExtractedAnnotations(
    annotations: List<Annotation>,
    ordered: List<Int>,
): List<Annotation> = annotations
    .filter { it.page in ordered }
    .map { it.copy(page = ordered.indexOf(it.page)) }

/** Cache key for parsed link targets of one source page. */
internal fun linkCacheKey(
    docId: String,
    source: File,
    sourcePage: Int,
    extraRotation: Int,
): String = "${docId}:${source.absolutePath}:${source.lastModified()}:$sourcePage:$extraRotation"

private const val LINK_CACHE_MAX = 48

/** Pure commit builder shared by PDF stroke persistence and JVM regressions. */
internal fun pdfStrokeAnnotation(
    state: ViewerUiState,
    type: AnnotationType,
    points: List<NormalizedPoint>,
    durationMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Annotation {
    val shapedPoints = if (type == AnnotationType.TRIANGLE) triangleVertices(points) else points
    val usablePoints = if (isTwoPointType(type)) {
        listOf(shapedPoints.first(), shapedPoints.last())
    } else {
        shapedPoints
    }
    val safeDuration = durationMs.coerceAtLeast(0L)
    return Annotation(
        type = type,
        page = state.pageIndex,
        color = state.color,
        width = when (type) {
            AnnotationType.HIGHLIGHT -> HighlightInk.clamp(state.highlightWidth)
            AnnotationType.RECT,
            AnnotationType.TRIANGLE,
            AnnotationType.ARROW,
            AnnotationType.LINE,
            AnnotationType.ELLIPSE,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.SQUIGGLY -> maxOf(state.strokeWidth, 0.0025f)
            else -> state.strokeWidth
        },
        points = usablePoints,
        timesMs = pointTimesMs(usablePoints),
        penStyle = if (type == AnnotationType.PEN) state.penStyle else PenStyle.BALLPOINT,
        opacity = when (type) {
            AnnotationType.HIGHLIGHT -> HighlightInk.clampOpacity(state.highlightOpacity)
            AnnotationType.REDACT -> 1f
            else -> state.opacity
        },
        createdAt = nowMs - safeDuration,
        durationMs = safeDuration,
    )
}

private fun isTwoPointType(type: AnnotationType): Boolean = when (type) {
    AnnotationType.RECT,
    AnnotationType.ARROW,
    AnnotationType.LINE,
    AnnotationType.ELLIPSE,
    AnnotationType.UNDERLINE,
    AnnotationType.STRIKEOUT,
    AnnotationType.SQUIGGLY,
    AnnotationType.REDACT,
    AnnotationType.COVER_TEXT -> true
    else -> false
}

/** Duplicates one annotation immediately after its original, preserving page order. */
internal fun duplicateAnnotationIn(
    annotations: List<Annotation>,
    id: String,
    newId: String,
): List<Annotation> {
    val index = annotations.indexOfFirst { it.id == id }
    if (index < 0) return annotations
    return annotations.toMutableList().apply {
        add(index + 1, annotations[index].copy(id = newId))
    }
}

/** Moves one annotation one step inside its own page's draw order. */
internal fun moveAnnotationOrderIn(
    annotations: List<Annotation>,
    id: String,
    forward: Boolean,
): List<Annotation> {
    val selected = annotations.firstOrNull { it.id == id } ?: return annotations
    val pageAnnotations = annotations.filter { it.page == selected.page }
    val oldIndex = pageAnnotations.indexOfFirst { it.id == id }
    val newIndex = (oldIndex + if (forward) 1 else -1).coerceIn(0, pageAnnotations.lastIndex)
    if (oldIndex == newIndex) return annotations
    val reordered = pageAnnotations.toMutableList().apply {
        val item = removeAt(oldIndex)
        add(newIndex, item)
    }
    var pageCursor = 0
    return annotations.map { annotation ->
        if (annotation.page == selected.page) reordered[pageCursor++] else annotation
    }
}

/** PDF eraser preview and hit testing share this exact normalized radius. */
internal fun pdfEraserRadius(pressure: Float, pressureSensitive: Boolean): Float =
    EraserGeometry.radiusForPressure(EraserGeometry.NORMALIZED_RADIUS, pressure, pressureSensitive)

/** Pure PDF annotation hit policy, extracted so JVM tests cover the real geometry. */
internal fun pdfEraserHitsAnnotation(
    annotation: Annotation,
    trail: List<NormalizedPoint>,
    radius: Float,
): Boolean {
    if (trail.isEmpty() || radius <= 0f || !radius.isFinite()) return false
    val eraser = EraserGeometry.densifyTrail(trail)
    return when (annotation.type) {
        AnnotationType.PEN,
        AnnotationType.HIGHLIGHT,
        AnnotationType.ARROW,
        AnnotationType.LINE,
        AnnotationType.UNDERLINE,
        AnnotationType.STRIKEOUT,
        AnnotationType.SQUIGGLY -> EraserGeometry.eraserHitsStroke(annotation.points, eraser, radius)

        AnnotationType.RECT -> {
            if (annotation.points.size < 2) return false
            EraserGeometry.eraserHitsStroke(
                pdfRectangleBorder(annotation.points[0], annotation.points[1]),
                eraser,
                radius,
            )
        }

        AnnotationType.ELLIPSE -> {
            if (annotation.points.size < 2) return false
            EraserGeometry.eraserHitsStroke(
                pdfEllipseBorder(annotation.points[0], annotation.points[1]),
                eraser,
                radius,
            )
        }

        AnnotationType.TRIANGLE -> {
            val border = triangleVertices(annotation.points)
            if (border.size < 3) return false
            EraserGeometry.eraserHitsStroke(border + border.first(), eraser, radius)
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
                EraserGeometry.eraserHitsStroke(annotation.points, eraser, radius)
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
            val minX = minOf(a.x, b.x)
            val maxX = maxOf(a.x, b.x)
            val minY = minOf(a.y, b.y)
            val maxY = maxOf(a.y, b.y)
            eraser.any { e ->
                e.x in (minX - radius)..(maxX + radius) &&
                    e.y in (minY - radius)..(maxY + radius)
            }
        }
    }
}

private fun pdfRectangleBorder(a: NormalizedPoint, b: NormalizedPoint): List<NormalizedPoint> {
    val left = minOf(a.x, b.x)
    val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    return listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
        NormalizedPoint(left, top),
    )
}

private fun pdfEllipseBorder(a: NormalizedPoint, b: NormalizedPoint): List<NormalizedPoint> {
    val cx = (a.x + b.x) * 0.5f
    val cy = (a.y + b.y) * 0.5f
    val rx = abs(a.x - b.x) * 0.5f
    val ry = abs(a.y - b.y) * 0.5f
    if (rx <= 1e-6f || ry <= 1e-6f) return listOf(a, b)
    val samples = 64
    return List(samples + 1) { index ->
        val angle = 2.0 * PI * index / samples
        NormalizedPoint(
            cx + rx * cos(angle).toFloat(),
            cy + ry * sin(angle).toFloat(),
        )
    }
}

class PdfViewModel(application: Application) : AndroidViewModel(application) {
    /** Parsed link targets survive page revisits; a full PDF parse per page is expensive. */
    private val pageLinkCache =
        LinkedHashMap<String, List<com.hackerdjakodjo.mypdfwriter.pdf.PdfLink>>()

    private val repo = PdfRepository(application)
    private val docStore = DocStateStore(application)
    private val docStyleStore = DocumentStyleStore(application)
    private val libraryStore = LibraryStore(application)
    private val excerptStore = ExcerptStore(application)
    private val readingStore = PdfReadingStore(application)
    private val sourceIdentityStore = SourceIdentityStore(application)
    private val inkRepository = PenSettingsRepository.get(application)
    private val resolver = application.contentResolver

    private val sourceDir = File(application.filesDir, "sources").apply { mkdirs() }
    private val workingDir = File(application.cacheDir, "working").apply { mkdirs() }

    private val _viewer = MutableStateFlow<ViewerUiState?>(null)
    val viewer: StateFlow<ViewerUiState?> = _viewer.asStateFlow()

    init {
        viewModelScope.launch {
            inkRepository.state.collect { snapshot ->
                _viewer.update { it?.withInkSettings(snapshot.ink) }
            }
        }
    }

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
    private var openJob: Job? = null
    private val openGate = OpenGenerationGate()
    private var searchJob: Job? = null
    private var readingSaveJob: Job? = null
    private var pendingReadingWrite: PendingReadingWrite? = null
    private val failedReadingWrites = mutableMapOf<String, PendingReadingWrite>()
    private var textLayoutJob: Job? = null
    private var linkLoadJob: Job? = null
    private var textLayoutLines: List<PdfTextLine> = emptyList()
    private var textLayoutKey: String? = null
    private var textLayoutLoadingKey: String? = null
    private var currentPageTextsKey: String? = null

    private var docSaveJob: Job? = null
    private var docSaveQueue = DocSaveQueue()
    private val docSaveLock = Any()
    /**
     * Documents whose newest attempted write failed. The async writer records its
     * result before waiting for Main, so a Main-thread flush cannot mistake an
     * in-flight failure for a drained queue. A later successful write clears it.
     */
    private val failedDocSaveDocs = mutableSetOf<String>()
    /** A replaced source could not be isolated; backups must stay blocked. */
    @Volatile
    private var sourceIsolationFailure = false
    /** Serialises the two docstate writers (drain + pre-backup flush). */
    private val docWriteLock = Any()
    /** Excerpt writes use the same lock order (write, then queue), never Main callbacks. */
    private val excerptWriteLock = Any()
    private val excerptQueueLock = Any()
    private val pendingExcerptWrites = LinkedHashMap<String, List<Excerpt>>()
    private val failedExcerptWrites = mutableMapOf<String, List<Excerpt>>()
    private var excerptSaveJob: Job? = null
    /** Changes only when the active viewer session changes, not on a failed open. */
    private var viewerSaveGeneration = 0L
    /** Main-thread baseline used to distinguish real changes from no-op persistence. */
    private var lastQueuedDocState: DocState? = null

    private val undoStack = ArrayDeque<DocSnapshot>()
    private val redoStack = ArrayDeque<DocSnapshot>()

    /** One pre-erase snapshot for a timestamped, densified pointer-up trail. */
    private var pointEraserUndoSnapshot: DocSnapshot? = null
    private var pointEraserUndoChanged = false
    private var pointEraserPageIndex: Int? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val finishPointEraserUndo = Runnable { finishPointEraserBatch() }

    // ---------------------------------------------------------------- opening

    fun openDocument(uri: Uri, addToRecents: Boolean = true) {
        // Capture the page request synchronously so a stale open cannot clear a
        // newer one. A same-document reopen is a disk barrier: every previously
        // queued doc/reading write for this docId must be canonical before load().
        val requestedPage = pendingInitialPage
        pendingInitialPage = null
        val reopeningDocId = docIdFor(uri)
        saveReadingNow()
        if (_viewer.value?.docId == reopeningDocId) {
            // Supersede Main-thread completions from the previous session. Without
            // this, an old writer can publish after the barrier and enqueue again
            // while the new open is already reading the sidecar.
            viewerSaveGeneration++
            persistNow()
        }
        flushPdfSavesNow(reopeningDocId)
        flushReadingSaveNow(reopeningDocId)
        flushExcerptSavesNow(reopeningDocId)
        searchJob?.cancel()
        textLayoutJob?.cancel()
        linkLoadJob?.cancel()
        openJob?.cancel()
        val token = openGate.begin()
        val openStartedAt = System.currentTimeMillis()
        // Only the URI *scheme* is logged — never the path, file name or query.
        PdfFlowTrace.openStart(
            uriKind = uri.scheme ?: "none",
            addToRecents = addToRecents,
        )
        openJob = viewModelScope.launch {
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            runCatching {
                runCatching {
                    resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val name = repo.queryDisplayName(uri)
                val docId = docIdFor(uri)
                // Always refresh the URI copy, then hash it off Main. The stored
                // fingerprint is checked before any annotation-bearing sidecar loads.
                val source = repo.sourceFileFor(uri, File(sourceDir, "$docId.pdf"))
                val fingerprint = repo.fingerprint(source)
                val previousFingerprint = sourceIdentityStore.load(docId)
                val sourceReplaced = previousFingerprint != null && previousFingerprint != fingerprint
                if (sourceReplaced) {
                    val isolated = quarantineReplacedSidecars(
                        getApplication<Application>().filesDir,
                        docId,
                        previousFingerprint!!,
                    )
                    if (!isolated) {
                        sourceIsolationFailure = true
                        // Fail closed: never publish a new fingerprint while stale
                        // canonical sidecars can still be reloaded on the next open.
                        _viewer.value = null
                        currentUriString = null
                        _pageBitmap.value = null
                        error("이전 PDF의 주석과 발췌를 안전하게 보관하지 못했습니다. 저장 공간을 확인해 주세요.")
                    }
                    DevLog.event(
                        PdfFlowTrace.COMPONENT,
                        "source_replaced",
                        "docId" to docId,
                        "sidecars" to "isolated",
                    )
                    sourceIsolationFailure = false
                }
                // One PDDocument load replaces the old pageCount() + sourcePageSizes() pair.
                val info = repo.documentInfo(source)
                val sourceCount = info.pageCount
                val sizes = info.pageSizes
                sourceIdentityStore.save(docId, fingerprint)
                val style = docStyleStore.load(docId)
                val saved = if (sourceReplaced) null else docStore.load(docId)?.normalized(sourceCount)
                val pages = saved?.pages ?: List(sourceCount) { PageSpec(it) }
                val annotations = saved?.annotations ?: emptyList()
                val notes = saved?.notes ?: emptyMap()
                val memoCards = saved?.memoCards ?: emptyList()
                val displayPrefs = saved?.displayPrefs ?: DisplayPrefs()
                val reading = if (sourceReplaced) {
                    PdfReadingState()
                } else {
                    (readingStore.load(docId) ?: PdfReadingState()).normalized(pages.size)
                }
                val excerpts = if (sourceReplaced) emptyList() else excerptStore.load(docId)
                // An unchanged layout needs no load+save pass: render the source directly.
                // Cache keys already include lastModified, so reopening an unchanged
                // document must not evict bitmaps for every other open document.
                val working: File
                if (isIdentityPageSpecs(pages, sourceCount)) {
                    working = source
                } else {
                    working = File(workingDir, "$docId.pdf")
                    repo.assemble(source, pages, working)
                }

                // Every shared write below is guarded: a superseded open must not
                // overwrite sourceFile/workingFile/currentUriString/_viewer.
                if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
                sourceFile = source
                workingFile = working
                currentUriString = uri.toString()
                sourceSizes = sizes
                resetTextLayout()
                _excerpts.value = excerpts
                _bookmarks.value = reading.bookmarks
                pageTexts = null
                currentPageTextsKey = null
                _outline.value = null
                _metadata.value = null
                undoStack.clear()
                redoStack.clear()
                mainHandler.removeCallbacks(finishPointEraserUndo)
                clearPointEraserUndo()

                val displayName = saved?.displayName?.takeIf { it.isNotBlank() } ?: name
                val restoredPage = (requestedPage ?: reading.lastPage)
                    .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                _pageBitmap.value = null
                _pageLinks.value = emptyList()
                val ink = inkRepository.snapshot.ink
                val openedState = ViewerUiState(
                    docId = docId,
                    penStyle = ink.penStyle,
                    color = ink.color,
                    strokeWidth = ink.width,
                    highlightWidth = ink.highlightWidth,
                    opacity = ink.opacity,
                    pressureInk = ink.pressureInk,
                    pressureCurve = ink.pressureCurve,
                    highlightOpacity = ink.highlightOpacity,
                    docName = displayName,
                    contentFingerprint = fingerprint,
                    sourceReplaced = sourceReplaced,
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
                    message = if (sourceReplaced) {
                        "원본 PDF가 바뀌었습니다. 이전 주석과 발췌는 새 문서에 적용하지 않고 보관했습니다."
                    } else {
                        null
                    },
                    documentStyle = style,
                    nightMode = displayPrefs.nightMode,
                    brightness = displayPrefs.brightness,
                    cropInsets = displayPrefs.cropInsets(),
                    cropOddEven = displayPrefs.cropOddEven,
                    zoom = DisplayPrefs.clampZoom(displayPrefs.zoom),
                )
                // A fresh viewer is IDLE. Its initial sidecar write is durability
                // work, but not a user-visible change to an already-loaded document.
                viewerSaveGeneration++
                lastQueuedDocState = viewerDocState(openedState)
                _viewer.value = openedState
                persistNow()
                if (addToRecents) {
                    _library.value = libraryStore.upsert(
                        RecentDoc(uri.toString(), displayName, pages.size, System.currentTimeMillis()),
                    )
                }
                renderPage(restoredPage, token)
                PdfFlowTrace.openDone(docId, pages.size, System.currentTimeMillis() - openStartedAt)
            }.onFailure { error ->
                if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
                // Only the exception type is logged; its message can contain a path.
                PdfFlowTrace.openFailed(error::class.java.simpleName)
                // A bad/truncated copy must not poison later opens in this session.
                repo.invalidateSource(uri.toString())
                if (keepViewerOnFailedOpen(_viewer.value?.docId, currentUriString)) {
                    // Another document is open and unaffected: report the failure
                    // but do not bounce the student to Home or clear their state.
                    _libraryMessage.value = text(
                        R.string.error_generic,
                        error.message ?: text(R.string.unsupported_pdf),
                    )
                    return@launch
                }
                _viewer.value = null
                lastQueuedDocState = null
                _pageBitmap.value = null
                _pageLinks.value = emptyList()
                _excerpts.value = emptyList()
                _bookmarks.value = emptyList()
                currentUriString = null
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

    /** Stable identity of the document currently rendered by the viewer. */
    fun currentStudySource(): StudySource? {
        val state = _viewer.value ?: return null
        if (state.docId.isBlank() || state.contentFingerprint.isBlank()) return null
        return StudySource(
            docName = state.docName,
            docUri = currentUriString,
            docId = state.docId,
            contentFingerprint = state.contentFingerprint,
        )
    }

    /** Checks document/fingerprint/page identity without moving the viewer. */
    fun resolveStudyLink(link: StudySourceLink): StudyLinkResolution {
        val state = _viewer.value ?: return StudyLinkResolution.SourceUnavailable
        val source = currentStudySource() ?: return StudyLinkResolution.SourceUnavailable
        return com.hackerdjakodjo.mypdfwriter.ui.resolveStudyLink(
            link = link,
            currentDocId = source.docId,
            currentFingerprint = source.contentFingerprint,
            pages = state.pages,
        )
    }

    /**
     * Explicit source jump. Page lock is intentionally bypassed, but a wrong,
     * changed, missing or ambiguous source never silently clamps to another page.
     */
    fun jumpToStudyLink(link: StudySourceLink): StudyLinkResolution {
        val resolution = resolveStudyLink(link)
        if (resolution is StudyLinkResolution.Found && !resolution.ambiguous) {
            if (PageLockGate.allowsNavigation(
                    _viewer.value?.pageLocked == true,
                    explicitSourceJump = true,
                )
            ) {
                goToPageUnlocked(resolution.viewPageIndex)
            }
        }
        DevLog.event(
            PdfFlowTrace.COMPONENT,
            "study_link_jump",
            "result" to resolution::class.java.simpleName,
            "ambiguous" to (resolution is StudyLinkResolution.Found && resolution.ambiguous),
        )
        return resolution
    }

    /** Maps a persisted excerpt's original source ordinal through current page ops. */
    fun jumpToExcerpt(excerpt: Excerpt): StudyLinkResolution {
        val source = currentStudySource() ?: return StudyLinkResolution.SourceUnavailable
        return jumpToStudyLink(
            StudySourceLink(
                uri = source.docUri.orEmpty(),
                docId = source.docId,
                sourcePage = excerpt.pageIndex,
                fingerprint = source.contentFingerprint,
                rect = null,
                excerptId = excerpt.id,
                rotation = 0,
            ),
        )
    }

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
        mainHandler.removeCallbacks(finishPointEraserUndo)
        clearPointEraserUndo()
        AnnotationPainter.clearInkContextCache()
        openJob?.cancel()
        openGate.invalidate()
        renderJob?.cancel()
        searchJob?.cancel()
        linkLoadJob?.cancel()
        val closedSaveOk = flushAllSavesNow()
        if (!closedSaveOk) {
            _libraryMessage.value = "PDF의 최종 변경을 저장하지 못했습니다. 저장 공간을 확인한 뒤 다시 시도해 주세요."
            return
        }
        val closingState = _viewer.value
        if (closingState != null) {
            PdfFlowTrace.close(closingState.docId, closingState.annotations.size)
        }
        viewerSaveGeneration++
        lastQueuedDocState = null
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

    /**
     * Drops the current document without writing its in-memory state back to disk.
     * Used after a local backup restore replaces sidecars on disk: persisting the
     * stale viewer here would immediately overwrite the restored annotations.
     */
    fun discardDocument() {
        mainHandler.removeCallbacks(finishPointEraserUndo)
        clearPointEraserUndo()
        AnnotationPainter.clearInkContextCache()
        openJob?.cancel()
        openGate.invalidate()
        renderJob?.cancel()
        searchJob?.cancel()
        linkLoadJob?.cancel()
        viewerSaveGeneration++
        lastQueuedDocState = null
        sourceIsolationFailure = false
        synchronized(docSaveLock) {
            // Keep the sequence monotonic across discard so an old in-flight IO
            // result can never collide with a reopened document's first request.
            docSaveQueue = DocSaveQueue(nextSequence = docSaveQueue.nextSequence)
            failedDocSaveDocs.clear()
        }
        docSaveJob?.cancel()
        docSaveJob = null
        // A pending reading-save must not land after a restore replaces the
        // sidecar with the backed-up last page/bookmarks.
        readingSaveJob?.cancel()
        readingSaveJob = null
        pendingReadingWrite = null
        failedReadingWrites.clear()
        synchronized(excerptQueueLock) {
            pendingExcerptWrites.clear()
            failedExcerptWrites.clear()
        }
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

    private fun renderPage(index: Int, openToken: Int? = null) {
        val file = workingFile ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            if (!mayPublishOpenResult(isActive, openToken, openGate.current)) return@launch
            val docId = _viewer.value?.docId ?: return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.rendering_page)) }
            val rendered = try {
                repo.renderPage(file, index, RENDER_SCALE)
            } catch (error: Throwable) {
                rethrowIfCancelled(error)
                null
            }
            // A superseded render (page flip, document swap or a newer open) must not publish.
            if (!mayPublishViewerResult(isActive, _viewer.value?.docId, docId)) return@launch
            if (!mayPublishOpenResult(isActive, openToken, openGate.current)) return@launch
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
    fun jumpToSourcePage(index: Int): StudyLinkResolution {
        val state = _viewer.value ?: return StudyLinkResolution.SourceUnavailable
        if (!PageLockGate.allowsNavigation(state.pageLocked, explicitSourceJump = true)) {
            return StudyLinkResolution.SourceUnavailable
        }
        val resolution = resolveOriginalSourcePage(state.pages, index)
        if (resolution is StudyLinkResolution.Found && !resolution.ambiguous) {
            goToPageUnlocked(resolution.viewPageIndex)
        }
        return resolution
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
            val key = linkCacheKey(docId, source, sourcePage, spec.extraRotation)
            val raw = withContext(Dispatchers.IO) {
                synchronized(pageLinkCache) { pageLinkCache[key] }
                    ?: PdfLinks.linksForFile(source, sourcePage, spec.extraRotation)
                        .also { parsed ->
                            synchronized(pageLinkCache) {
                                if (pageLinkCache.size >= LINK_CACHE_MAX) pageLinkCache.clear()
                                pageLinkCache[key] = parsed
                            }
                        }
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

    /** View-only crop margins. Saved with the document; never changes the file. */
    fun setCrop(insets: CropInsets, oddEven: Boolean) {
        val state = _viewer.value ?: return
        val clamped = PdfCrop.clampInsets(insets)
        if (state.cropInsets == clamped && state.cropOddEven == oddEven) return
        _viewer.value = state.copy(cropInsets = clamped, cropOddEven = oddEven)
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
        val token = openGate.current
        viewModelScope.launch {
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            val file = buildExportFile()
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
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
        val token = openGate.current
        viewModelScope.launch {
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            val file = buildExportFile()
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
            _viewer.update { it?.copy(busy = false, busyLabel = null) }
            if (file != null) {
                PdfShare.printPdf(getApplication(), file, state.docName.substringBeforeLast('.'))
            } else {
                _viewer.update { it?.copy(message = "인쇄할 PDF를 만들지 못했습니다.") }
            }
        }
    }

    // ---------------------------------------------------------------- tools

    /** One atomic shared-ink update; PDF state is only a projection of the repository. */
    private fun updateInk(transform: (InkSettings) -> InkSettings) {
        val result = inkRepository.updateInk(transform)
        if (result is PenSettingsWriteResult.Applied) {
            // Keep immediate setter→annotation callers synchronous even before the
            // StateFlow collector runs on the next Main-loop turn.
            _viewer.update { it?.withInkSettings(result.snapshot.ink) }
        }
    }

    /** Nib style for new PDF pen annotations. */
    fun setPenStyle(style: PenStyle) = updateInk { it.copy(penStyle = style) }

    fun applyInkPreset(preset: PenPreset) {
        val result = inkRepository.applyPreset(preset)
        if (result is PenSettingsWriteResult.Applied) {
            _viewer.update { it?.withInkSettings(result.snapshot.ink) }
            setTool(preset.tool)
        }
    }

    fun setTool(tool: Tool) {
        _viewer.update { it?.copy(tool = tool) }
    }

    fun setColor(color: Int) = updateInk { it.copy(color = color) }

    fun setStrokeWidth(width: Float) = updateInk {
        it.copy(width = InkSettings.clampWidth(width))
    }

    /** Width used by the highlighter, stored separately from the pen width. */
    fun setHighlightWidth(width: Float) = updateInk {
        it.copy(highlightWidth = HighlightInk.clamp(width))
    }

    fun setFontSize(size: Float) = _viewer.update {
        it?.copy(fontSize = size.coerceIn(0.012f, 0.06f))
    }

    fun setOpacity(opacity: Float) = updateInk {
        it.copy(opacity = InkSettings.clampOpacity(opacity))
    }

    fun setPressureInk(enabled: Boolean) = updateInk { it.copy(pressureInk = enabled) }

    fun setPressureCurve(curve: PressureCurve) = updateInk { it.copy(pressureCurve = curve) }

    fun setHighlightOpacity(opacity: Float) = updateInk {
        it.copy(highlightOpacity = HighlightInk.clampOpacity(opacity))
    }

    fun setZoom(zoom: Float) {
        _viewer.update { it?.copy(zoom = DisplayPrefs.clampZoom(zoom)) }
        // Zoom is part of DisplayPrefs, which the viewer promises to restore.
        persistNow()
    }

    // ---------------------------------------------------------------- annotations

    fun addStroke(points: List<NormalizedPoint>, durationMs: Long = 0L) {
        val state = _viewer.value ?: return
        if (points.isEmpty() || state.tool == Tool.VIEW || state.tool == Tool.ERASER || state.tool == Tool.TEXT) return
        val type = when (state.tool) {
            Tool.PEN -> AnnotationType.PEN
            Tool.HIGHLIGHT -> AnnotationType.HIGHLIGHT
            Tool.RECT -> AnnotationType.RECT
            Tool.TRIANGLE -> AnnotationType.TRIANGLE
            Tool.ARROW -> AnnotationType.ARROW
            Tool.UNDERLINE -> AnnotationType.UNDERLINE
            Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
            Tool.SQUIGGLY -> AnnotationType.SQUIGGLY
            Tool.ELLIPSE -> AnnotationType.ELLIPSE
            Tool.LINE -> AnnotationType.LINE
            Tool.REDACT -> AnnotationType.REDACT
            else -> return
        }
        val annotation = pdfStrokeAnnotation(state, type, points, durationMs)
        mutateAnnotations { it + annotation }
        PdfFlowTrace.annotate(
            type = type.name,
            annotationId = annotation.id,
            page = annotation.page,
            total = state.annotations.size + 1,
        )
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
            timesMs = pointTimesMs(listOf(point)),
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
            timesMs = pointTimesMs(listOf(point)),
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
            timesMs = pointTimesMs(listOf(point)),
            text = signature,
            fontSize = maxOf(state.fontSize, 0.03f),
            opacity = state.opacity,
        )
        mutateAnnotations { it + annotation }
    }

    fun deleteAnnotation(id: String) {
        val state = _viewer.value ?: return
        val removed = state.annotations.firstOrNull { it.id == id } ?: return
        mutateAnnotations { annotations -> annotations.filterNot { it.id == id } }
        PdfFlowTrace.annotationEdit(
            kind = "delete",
            annotationId = id,
            type = removed.type.name,
            page = removed.page,
            total = state.annotations.size - 1,
        )
    }

    /** Replaces one app-created annotation as a single undoable object edit. */
    fun replaceAnnotation(updated: Annotation, kind: String = "transform") {
        val state = _viewer.value ?: return
        val existing = state.annotations.firstOrNull { it.id == updated.id } ?: return
        if (existing.page != updated.page || updated.points.any { !it.x.isFinite() || !it.y.isFinite() }) return
        if (existing == updated) return
        mutateAnnotations { annotations ->
            annotations.map { if (it.id == updated.id) updated else it }
        }
        PdfFlowTrace.annotationEdit(
            kind = kind,
            annotationId = updated.id,
            type = updated.type.name,
            page = updated.page,
            total = state.annotations.size,
        )
    }

    /** Duplicates an annotation immediately after its original in draw order. */
    fun duplicateAnnotation(id: String) {
        val state = _viewer.value ?: return
        val index = state.annotations.indexOfFirst { it.id == id }
        if (index < 0) return
        val original = state.annotations[index]
        val duplicate = original.copy(id = UUID.randomUUID().toString())
        mutateAnnotations { annotations ->
            duplicateAnnotationIn(annotations, id, duplicate.id)
        }
        PdfFlowTrace.annotationEdit(
            kind = "duplicate",
            annotationId = duplicate.id,
            type = duplicate.type.name,
            page = duplicate.page,
            total = state.annotations.size + 1,
        )
    }

    /** Moves an annotation one step within its page's draw order. */
    fun moveAnnotationOrder(id: String, forward: Boolean) {
        val state = _viewer.value ?: return
        val selected = state.annotations.firstOrNull { it.id == id } ?: return
        if (moveAnnotationOrderIn(state.annotations, id, forward) == state.annotations) return
        mutateAnnotations { annotations ->
            moveAnnotationOrderIn(annotations, id, forward)
        }
        PdfFlowTrace.annotationEdit(
            kind = if (forward) "forward" else "backward",
            annotationId = id,
            type = selected.type.name,
            page = selected.page,
            total = state.annotations.size,
        )
    }

    fun annotationsForPage(page: Int): List<Annotation> =
        _viewer.value?.annotations?.filter { it.page == page }.orEmpty()

    /** Legacy trail callback retained for existing canvas call sites and method references. */
    fun eraseStroke(points: List<NormalizedPoint>) = eraseStroke(points, 1f, false)

    /** Whole-stroke PDF erase with continuous, pressure-aware hit geometry. */
    fun eraseStroke(
        points: List<NormalizedPoint>,
        pressure: Float,
        pressureSensitive: Boolean,
    ) {
        val state = _viewer.value ?: return
        if (points.isEmpty()) return
        val page = state.pageIndex
        val radius = pdfEraserRadius(pressure, pressureSensitive)
        val remaining = state.annotations.filterNot { annotation ->
            annotation.page == page && pdfEraserHitsAnnotation(annotation, points, radius)
        }
        if (remaining.size != state.annotations.size) {
            mutateAnnotations { remaining }
        }
    }

    /** Pointer-event entry point used by pressure-aware input integration. */
    fun eraseAt(
        point: NormalizedPoint,
        pressure: Float = 1f,
        pressureSensitive: Boolean = false,
    ) {
        val state = _viewer.value ?: return
        if (pointEraserPageIndex != null && pointEraserPageIndex != state.pageIndex) {
            mainHandler.removeCallbacks(finishPointEraserUndo)
            commitPointEraserUndo()
        }
        if (pointEraserUndoSnapshot == null) {
            pointEraserUndoSnapshot = snapshot(state)
            pointEraserUndoChanged = false
            pointEraserPageIndex = state.pageIndex
        }
        val radius = pdfEraserRadius(pressure, pressureSensitive)
        val remaining = state.annotations.filterNot { annotation ->
            annotation.page == state.pageIndex && pdfEraserHitsAnnotation(annotation, listOf(point), radius)
        }
        if (remaining.size != state.annotations.size) {
            if (!pointEraserUndoChanged) redoStack.clear()
            pointEraserUndoChanged = true
            _viewer.value = state.copy(
                annotations = remaining,
                canUndo = true,
                canRedo = false,
            )
        }
        // The canvas feeds every densified sample synchronously on pointer-up.
        // Finalizing after the current Main-looper message keeps that whole swipe
        // in one undo step without requiring a second gesture-end callback.
        mainHandler.removeCallbacks(finishPointEraserUndo)
        mainHandler.post(finishPointEraserUndo)
    }

    /** Keeps the existing `vm::eraseStroke` method-reference call site compiling. */
    fun eraseStroke(
        point: NormalizedPoint,
        pressure: Float,
        pressureSensitive: Boolean,
    ) = eraseAt(point, pressure, pressureSensitive)

    private fun snapshot(state: ViewerUiState, bookmarks: List<PdfBookmark> = _bookmarks.value) = DocSnapshot(
        pages = state.pages,
        annotations = state.annotations,
        pageIndex = state.pageIndex,
        notes = state.notes,
        memoCards = state.memoCards,
        bookmarks = bookmarks,
    )

    private fun finishPointEraserBatch() {
        val changed = pointEraserUndoChanged
        commitPointEraserUndo()
        if (changed) persistNow()
    }

    private fun commitPointEraserUndo() {
        mainHandler.removeCallbacks(finishPointEraserUndo)
        val before = pointEraserUndoSnapshot
        val changed = pointEraserUndoChanged
        clearPointEraserUndo()
        if (changed && before != null) {
            undoStack.addLast(before)
            if (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    private fun clearPointEraserUndo() {
        pointEraserUndoSnapshot = null
        pointEraserUndoChanged = false
        pointEraserPageIndex = null
    }

    private fun pushHistory(state: ViewerUiState) {
        commitPointEraserUndo()
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
            // Page edits remap notes/cards/bookmarks; undo must roll all of them
            // back together or page 2 ends up showing page 4's note.
            snapshot.bookmarks?.let { restoredBookmarks ->
                if (_bookmarks.value != restoredBookmarks) {
                    _bookmarks.value = restoredBookmarks
                    saveReadingNow()
                }
            }
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
        commitPointEraserUndo()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot(state))
        restoreSnapshot(state, previous)
        PdfFlowTrace.history("undo", previous.annotations.lastOrNull()?.id, _viewer.value?.annotations?.size ?: 0)
    }

    fun redo() {
        val state = _viewer.value ?: return
        commitPointEraserUndo()
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot(state))
        restoreSnapshot(state, next)
        PdfFlowTrace.history("redo", next.annotations.lastOrNull()?.id, _viewer.value?.annotations?.size ?: 0)
    }

    // ---------------------------------------------------------------- page layout

    /**
     * Runs a [PageOps] operation with temporary marker annotations, then reads the
     * markers back to remap memo cards with the *same* page permutation PageOps used
     * for ink. This keeps delete/move/duplicate/insert in sync without copying their
     * index rules, and duplicated cards get fresh ids just like duplicated strokes.
     * Bookmarks ride along inside [PageEdit] itself, so PageOps remaps them with the
     * identical old-to-new source-page map it uses for annotations.
     */
    private fun pageEditWithMemos(
        state: ViewerUiState,
        block: (PageEdit) -> PageEdit,
    ): Pair<PageEdit, List<MemoCard>> {
        if (state.memoCards.isEmpty()) {
            return block(
                PageEdit(state.pages, state.annotations, state.notes, state.pageIndex, _bookmarks.value),
            ) to emptyList()
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
            PageEdit(state.pages, state.annotations + markers, state.notes, state.pageIndex, _bookmarks.value),
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
        applyPageEdit(state, edit, "페이지를 회전하는 중…", memos, kind = "rotate")
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
        applyPageEdit(state, edit, "페이지를 이동하는 중…", memos, kind = "move")
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
        applyPageEdit(state, edit, "페이지를 삭제하는 중…", memos, kind = "delete")
    }

    fun duplicatePages(indices: Set<Int>) {
        val state = _viewer.value ?: return
        if (state.busy || state.pages.isEmpty()) return
        val (edit, memos) = pageEditWithMemos(state) { base ->
            PageOps.duplicate(base, indices)
        }
        applyPageEdit(state, edit, "페이지를 복제하는 중…", memos, kind = "duplicate")
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
            kind = "insert",
        )
    }

    fun extractPages(indices: Set<Int>, uri: Uri) {
        val state = _viewer.value ?: return
        val source = sourceFile ?: return
        val ordered = indices.filter { it in state.pages.indices }.sorted()
        if (ordered.isEmpty()) return
        val token = openGate.current
        viewModelScope.launch {
            if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
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
                if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
                _viewer.update { it?.copy(busy = false, busyLabel = null, message = "페이지 ${ordered.size}장을 추출했습니다.") }
            }.onFailure { error ->
                if (!mayPublishOpenResult(isActive, token, openGate.current)) return@launch
                _viewer.update { it?.copy(busy = false, busyLabel = null, message = "추출하지 못했습니다: ${error.message}") }
            }
        }
    }

    private fun applyPageEdit(
        state: ViewerUiState,
        edit: PageEdit,
        label: String,
        memoCards: List<MemoCard> = state.memoCards,
        kind: String = "edit",
    ) {
        if (edit.pages == state.pages &&
            edit.annotations == state.annotations &&
            edit.notes == state.notes &&
            edit.bookmarks == _bookmarks.value &&
            memoCards == state.memoCards &&
            edit.currentPage == state.pageIndex
        ) {
            return
        }
        PdfFlowTrace.pageEdit(kind, edit.pages.size, state.revision.toLong())
        pushHistory(state)
        // Publish the remapped bookmarks together with the page edit, then flush
        // the reading sidecar so close/reopen cannot restore a stale page index.
        _bookmarks.value = edit.bookmarks
        saveReadingNow()
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
            // Bookmarks follow the same source page as annotations/notes/cards.
            // Ones on inserted blank pages have no source and are dropped.
            val bookmarks = _bookmarks.value.mapNotNull { bookmark ->
                val sourcePage = state.pages.getOrNull(bookmark.pageIndex)?.sourcePage
                    ?: return@mapNotNull null
                if (sourcePage !in specs.indices) return@mapNotNull null
                bookmark.copy(pageIndex = sourcePage)
            }.distinctBy { it.pageIndex }
            PdfFlowTrace.pageEdit("reset", specs.size, state.revision.toLong())
            pushHistory(state)
            _bookmarks.value = bookmarks
            saveReadingNow()
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
        val generation = viewerSaveGeneration
        viewModelScope.launch {
            _viewer.update { current ->
                if (current?.docId == state.docId && viewerSaveGeneration == generation) {
                    current.copy(busy = true, busyLabel = text(R.string.building_pages))
                } else {
                    current
                }
            }
            runCatching { repo.assemble(source, state.pages, working) }
                .onSuccess {
                    if (_viewer.value?.docId != state.docId || viewerSaveGeneration != generation) return@onSuccess
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
                        // A save may have completed while assemble was off Main.
                        saveStatus = _viewer.value?.saveStatus ?: state.saveStatus,
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
                    if (_viewer.value?.docId != state.docId || viewerSaveGeneration != generation) return@onFailure
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
        _viewer.value = state.copy(notes = notes)
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
            val capture = buildPdfStudyCapture(state, state.pageIndex, excerptText)
                ?.copy(source = currentStudySource() ?: return@launch)
            if (capture == null) {
                _viewer.update { it?.copy(message = "원본 PDF 페이지를 확인하지 못했습니다.") }
                return@launch
            }
            val excerpt = Excerpt(
                pageIndex = capture.sourcePage,
                text = excerptText,
                title = title.trim(),
            )
            val updated = (listOf(excerpt) + _excerpts.value).sortedByDescending { it.createdAt }
            _excerpts.value = updated
            queueExcerptSave(state.docId, updated)
            _viewer.update { it?.copy(message = "발췌를 저장했습니다.") }
        }
    }

    fun deleteExcerpt(id: String) {
        val state = _viewer.value ?: return
        val updated = _excerpts.value.filterNot { it.id == id }
        if (updated.size == _excerpts.value.size) return
        _excerpts.value = updated
        queueExcerptSave(state.docId, updated)
        _viewer.update { it?.copy(message = "발췌를 삭제했습니다.") }
    }

    fun updateExcerptTitle(id: String, title: String) {
        val state = _viewer.value ?: return
        val updated = _excerpts.value.map { excerpt ->
            if (excerpt.id == id) excerpt.copy(title = title.trim()) else excerpt
        }
        if (updated == _excerpts.value) return
        _excerpts.value = updated
        queueExcerptSave(state.docId, updated)
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
    fun loadTextWords(
        pageIndex: Int = _viewer.value?.pageIndex ?: 0,
        announce: Boolean = true,
    ) {
        val state = _viewer.value ?: return
        val file = workingFile ?: return
        val key = textLayoutKeyFor(state, file, pageIndex)

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
        val generation = viewerSaveGeneration
        _textWords.value = emptyList()
        // Flip the loading state before launching so the selection UI never
        // shows a false "no text" empty state while extraction is starting.
        _viewer.update { it?.copy(textLoading = true) }
        textLayoutJob = viewModelScope.launch {
            val lines = try {
                withContext(Dispatchers.IO) { PdfTextLayout.lines(file, pageIndex) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (!isCurrentViewerSession(state, generation)) return@launch

            textLayoutLoadingKey = null
            if (lines == null) {
                textLayoutKey = null
                textLayoutLines = emptyList()
                _textWords.value = emptyList()
                _viewer.update {
                    it?.copy(
                        textLoading = false,
                        message = if (announce) "텍스트 레이아웃을 읽지 못했습니다." else it.message,
                    )
                }
            } else {
                textLayoutKey = key
                textLayoutLines = lines
                publishTextWords(_viewer.value?.pageIndex ?: pageIndex)
                _viewer.update {
                    it?.copy(
                        textLoading = false,
                        message = if (announce && lines.isEmpty()) {
                            "이 PDF에는 선택할 수 있는 텍스트가 없습니다."
                        } else {
                            it.message
                        },
                    )
                }
            }
        }
    }

    /** Passive current-page warm-up for direct VIEW-mode text selection. */
    fun preloadTextWords(pageIndex: Int = _viewer.value?.pageIndex ?: 0) =
        loadTextWords(pageIndex, announce = false)

    private fun publishTextWords(pageIndex: Int) {
        _textWords.value = textLayoutLines
            .filter { it.pageIndex == pageIndex }
            .flatMap { it.words }
    }

    private fun textLayoutKeyFor(state: ViewerUiState, file: File, pageIndex: Int): String =
        "${state.docId}:${state.contentFingerprint}:${file.absolutePath}:${file.lastModified()}:${state.pages.size}:${state.revision}:$pageIndex"

    private fun resetTextLayout() {
        PdfTextLayout.clearLineCache()
        synchronized(pageLinkCache) { pageLinkCache.clear() }
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
            type != AnnotationType.STRIKEOUT &&
            type != AnnotationType.SQUIGGLY
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

                AnnotationType.UNDERLINE,
                AnnotationType.SQUIGGLY -> if (vertical) {
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
        added.forEachIndexed { index, annotation ->
            PdfFlowTrace.annotate(
                type = annotation.type.name,
                annotationId = annotation.id,
                page = annotation.page,
                total = state.annotations.size + index + 1,
            )
        }
        _viewer.update {
            it?.copy(
                message = when (type) {
                    AnnotationType.HIGHLIGHT -> "선택한 텍스트에 형광펜을 표시했습니다."
                    AnnotationType.UNDERLINE -> "선택한 텍스트에 밑줄을 표시했습니다."
                    AnnotationType.SQUIGGLY -> "선택한 텍스트에 물결선을 표시했습니다."
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

    /** Selection provenance without creating a persisted excerpt (used at drag start). */
    fun studyCaptureForSelection(pageIndex: Int, text: String): PdfStudyCapture? {
        val state = _viewer.value ?: return null
        val source = currentStudySource() ?: return null
        return buildPdfStudyCapture(state, pageIndex, text, state.selectedWords)?.copy(source = source)
    }

    /** Adds a selection capture and returns its persisted excerpt identity. */
    fun addExcerptFromSelection(pageIndex: Int, text: String): PdfStudyCapture? =
        studyCaptureForSelection(pageIndex, text)?.let(::addPdfStudyCapture)

    /**
     * Validates a drag/drop capture against the active source bytes and the
     * current source-page layout without persisting it. A valid capture is
     * rebound to [currentStudySource], so stale display names or URIs cannot
     * become the saved provenance.
     */
    fun validatePdfStudyCapture(capture: PdfStudyCapture): PdfStudyCapture? {
        val state = _viewer.value ?: return null
        val source = currentStudySource() ?: return null
        return validatePdfStudyCaptureAgainst(state, capture, source) { message ->
            _viewer.update { it?.copy(message = message) }
        }
    }

    /**
     * Accepts a drag/drop capture only when it still belongs to the active source
     * bytes. The saved Excerpt keeps the original source ordinal, not a viewer index.
     */
    fun addPdfStudyCapture(capture: PdfStudyCapture): PdfStudyCapture? {
        val validated = validatePdfStudyCapture(capture) ?: return null
        val docId = validated.source.docId ?: return null
        val excerpt = Excerpt(pageIndex = validated.sourcePage, text = validated.text.trim())
        val updated = (listOf(excerpt) + _excerpts.value).sortedByDescending { it.createdAt }
        _excerpts.value = updated
        queueExcerptSave(docId, updated)
        _viewer.update { it?.copy(message = "선택한 텍스트를 노트에 담았습니다.") }
        return validated.copy(excerptId = excerpt.id)
    }

    /** Rebuilds a document-bound capture for an already persisted excerpt. */
    fun studyCaptureForExcerpt(excerpt: Excerpt): PdfStudyCapture? {
        val state = _viewer.value ?: return null
        val source = currentStudySource() ?: return null
        if (excerpt.pageIndex < 0 || state.pages.none { it.sourcePage == excerpt.pageIndex }) return null
        return PdfStudyCapture(
            text = excerpt.text,
            sourcePage = excerpt.pageIndex,
            rect = null,
            excerptId = excerpt.id,
            source = source,
            rotation = 0,
        )
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
        // A new query must not keep showing the previous query's hits while the
        // debounce runs; otherwise the sheet and page highlights disagree.
        _viewer.update {
            it?.copy(
                searchQuery = query,
                searchHits = emptyList(),
                currentSearchHit = -1,
                searching = false,
                searchFailed = false,
            )
        }
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
            val generation = viewerSaveGeneration
            delay(220)
            if (!isCurrentViewerSession(state, generation)) return@launch
            _viewer.update { it?.copy(searching = true, searchFailed = false) }
            val file = workingFile ?: return@launch
            val key = "${state.docId}:${state.contentFingerprint}:${file.lastModified()}:${state.pages.size}"
            val texts = pageTexts?.takeIf { currentPageTextsKey == key } ?: try {
                repo.extractPagesText(file)
            } catch (error: Throwable) {
                rethrowIfCancelled(error)
                null
            }
            if (texts == null) {
                if (isCurrentViewerSession(state, generation)) {
                    _viewer.update { it?.copy(searching = false, searchFailed = true) }
                }
                return@launch
            }
            pageTexts = texts
            currentPageTextsKey = key
            val hits = SearchNavigation.hitsForPages(texts, trimmed)
            if (!isCurrentViewerSession(state, generation)) return@launch
            // Only the query *length* is logged — the query itself may be user data.
            PdfFlowTrace.search(trimmed.length, hits.size)
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

    /**
     * Captures the active document at SAF-launch time. The picker can stay open
     * while the user switches documents, so the export must carry its own payload
     * instead of reading the active viewer when the result arrives.
     */
    fun captureExportSnapshot(): PdfExportSnapshot? {
        val state = _viewer.value ?: return null
        val source = sourceFile ?: return null
        return PdfExportSnapshot(
            docId = state.docId,
            docName = state.docName,
            source = source,
            state = viewerDocState(state),
            style = state.documentStyle,
        )
    }

    fun exportTo(snapshot: PdfExportSnapshot, uri: Uri) {
        viewModelScope.launch {
            // A document switch while the picker was open must not move the busy
            // indicator or success/failure message onto the newly active document.
            if (!mayPublishViewerResult(isActive, _viewer.value?.docId, snapshot.docId)) return@launch
            _viewer.update { it?.copy(busy = true, busyLabel = text(R.string.preparing_export)) }
            PdfFlowTrace.exportStart()
            var exportedBytes = 0L
            runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { output ->
                        PdfExporter.export(
                            source = snapshot.source,
                            specs = snapshot.state.pages,
                            annotations = snapshot.state.annotations,
                            out = output,
                            style = snapshot.style,
                            pageCount = snapshot.state.pages.size,
                        )
                    } ?: error("출력 파일을 열 수 없습니다.")
                    // Read back only the byte count for the dev log. Never log the URI.
                    exportedBytes = runCatching {
                        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
                    }.getOrDefault(0L)
                }
            }.onSuccess {
                if (!mayPublishViewerResult(isActive, _viewer.value?.docId, snapshot.docId)) return@launch
                PdfFlowTrace.exportDone(exportedBytes, flattened = true)
                _viewer.update {
                    it?.copy(busy = false, busyLabel = null, message = text(R.string.export_done))
                }
            }.onFailure { error ->
                if (!mayPublishViewerResult(isActive, _viewer.value?.docId, snapshot.docId)) return@launch
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
            // A just-closed document may still have an in-flight docstate write
            // that would resurrect its JSON after deletion.
            flushPdfSavesNow()
            readingSaveJob?.cancel()
            readingSaveJob = null
            AudioNoteStore(getApplication()).audioDirectory(id).deleteRecursively()
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

    /** One-shot notice for import intents (e.g. several PDFs shared at once). */
    fun postLibraryMessage(message: String) {
        _libraryMessage.value = message
    }

    fun reloadLibrary() {
        _library.value = libraryStore.load()
    }

    fun dismissError() {
        viewerSaveGeneration++
        lastQueuedDocState = null
        _viewer.value = null
    }

    // ---------------------------------------------------------------- helpers

    /** Coalesces excerpt writes per document without dropping a previous doc's last state. */
    private fun queueExcerptSave(docId: String, excerpts: List<Excerpt>) {
        val startWriter: Boolean
        synchronized(excerptQueueLock) {
            pendingExcerptWrites[docId] = excerpts
            failedExcerptWrites.remove(docId)
            startWriter = excerptSaveJob?.isActive != true
        }
        if (startWriter) excerptSaveJob = viewModelScope.launch(Dispatchers.IO) { drainExcerptSaves() }
    }

    private suspend fun drainExcerptSaves() {
        while (true) {
            synchronized(excerptWriteLock) {
                val entry = synchronized(excerptQueueLock) {
                    pendingExcerptWrites.entries.firstOrNull()
                } ?: return
                val saved = excerptStore.save(entry.key, entry.value)
                synchronized(excerptQueueLock) {
                    if (pendingExcerptWrites[entry.key] === entry.value) {
                        pendingExcerptWrites.remove(entry.key)
                    }
                    if (saved) {
                        failedExcerptWrites.remove(entry.key)
                    } else if (pendingExcerptWrites[entry.key] == null) {
                        failedExcerptWrites[entry.key] = entry.value
                    }
                }
            }
        }
    }

    /** Synchronous target-document barrier; unrelated excerpt writes remain queued. */
    private fun flushExcerptSavesNow(docId: String): Boolean {
        synchronized(excerptWriteLock) {
            while (true) {
                val next = synchronized(excerptQueueLock) { pendingExcerptWrites.remove(docId) }
                if (next == null) break
                if (!excerptStore.save(docId, next)) {
                    synchronized(excerptQueueLock) { failedExcerptWrites[docId] = next }
                    return false
                }
            }
            val failed = synchronized(excerptQueueLock) { failedExcerptWrites[docId] } ?: return true
            val saved = excerptStore.save(docId, failed)
            synchronized(excerptQueueLock) {
                if (saved) failedExcerptWrites.remove(docId) else failedExcerptWrites[docId] = failed
            }
            return saved
        }
    }

    private data class DocSaveWriteResult(
        val succeeded: Boolean,
        val failureReason: String? = null,
    )

    private fun persistNow(forceSaveStatus: Boolean = false) {
        val state = _viewer.value ?: return
        // Capture the whole DocState on the Main thread before handing it to the writer.
        val doc = viewerDocState(state)
        val generation = viewerSaveGeneration
        val publishSaving = forceSaveStatus || doc != lastQueuedDocState
        val request = DocSaveRequest(
            state = doc,
            viewerGeneration = generation,
            // Initial/no-op writes may still create or refresh the sidecar, but a
            // fresh viewer stays IDLE and a no-op cannot clear a real failure.
            reportStatus = publishSaving || state.saveStatus == PdfSaveStatus.SAVING,
        )
        val startWriter: Boolean
        synchronized(docSaveLock) {
            val (updated, shouldStart) = docSaveQueue.offer(request)
            docSaveQueue = updated
            startWriter = shouldStart
        }
        lastQueuedDocState = doc
        if (publishSaving && state.saveStatus != PdfSaveStatus.SAVING) {
            PdfFlowTrace.saveStart()
            _viewer.update { current ->
                current?.takeIf { it.docId == doc.docId && viewerSaveGeneration == generation }
                    ?.onPdfSaveQueued()
            }
        }
        // One drain loop owns the tmp file; never cancel it mid-write. Close/error
        // paths keep calling persistNow(), so their final state is always written.
        if (startWriter) docSaveJob = viewModelScope.launch(Dispatchers.IO) { drainDocSaves() }
    }

    /** Re-offers a failed document's current state; no viewer or no failure is a no-op. */
    fun retrySave() {
        val state = _viewer.value ?: return
        if (state.saveStatus != PdfSaveStatus.FAILED) return
        persistNow(forceSaveStatus = true)
    }

    /**
     * [DocStateStore.save] intentionally contains IO failures so callers cannot
     * crash. Read the sidecar back before claiming durability; a missing or
     * mismatched result is a failed write, not success.
     */
    private fun writeDocState(request: DocSaveRequest): DocSaveWriteResult {
        val reason = try {
            docStore.save(request.state)
            if (docStore.load(request.state.docId) == request.state) null else "io_unverified"
        } catch (error: Throwable) {
            rethrowIfCancelled(error)
            PdfFlowTrace.saveFailureReason(error)
        }
        val result = DocSaveWriteResult(succeeded = reason == null, failureReason = reason)
        // Writes are serialised by docWriteLock, so a success here is newer
        // than any previously recorded failure for this document.
        synchronized(docSaveLock) {
            if (result.succeeded) {
                failedDocSaveDocs.remove(request.state.docId)
            } else {
                failedDocSaveDocs.add(request.state.docId)
            }
        }
        return result
    }

    /** Called on Main after IO, or by the Main-thread synchronous backup barrier. */
    private fun publishDocSaveCompletion(request: DocSaveRequest, result: DocSaveWriteResult) {
        val isLatest = synchronized(docSaveLock) { docSaveQueue.isLatest(request) }
        val completion = PdfSaveCompletion(
            docId = request.state.docId,
            viewerGeneration = request.viewerGeneration,
            state = request.state,
            succeeded = result.succeeded,
            isLatestQueued = isLatest,
            reportStatus = request.reportStatus,
        )
        val current = _viewer.value
        val canPublish = current != null &&
            canPublishPdfSaveCompletion(current.docId, viewerSaveGeneration, completion)
        // Defensive against a Main-thread state change that landed between capture
        // and publication: queue that newest state instead of showing a stale result.
        if (current != null && canPublish && isLatest && viewerDocState(current) != request.state) {
            persistNow(forceSaveStatus = true)
            return
        }
        if (isLatest && request.reportStatus) {
            if (result.succeeded) {
                PdfFlowTrace.saveDone(request.state.docId)
            } else {
                PdfFlowTrace.saveFailed(
                    request.state.docId,
                    result.failureReason ?: "io_unverified",
                )
            }
        }
        if (!canPublish) return
        val active = current ?: return
        val updated = active.applyPdfSaveCompletion(viewerSaveGeneration, completion) ?: return
        if (updated != active) _viewer.value = updated
    }

    /**
     * Synchronous Main-thread barrier used before creating a backup. The normal
     * writer is asynchronous, so a ZIP created immediately after a stroke could
     * miss the newest annotation state. Draining the queue here makes the archive
     * see it. Each write holds the same lock as the async writer, so a backup can
     * never capture a half-written tmp.
     */
    fun flushPdfSavesNow(docId: String? = null) {
        drainDocSavesNow(docId)
    }

    /**
     * Synchronously flushes the active document's current docstate, reading state
     * and newest queued excerpts. Must be called on Main. Unlike the legacy
     * docstate-only barrier, failures are reported to the caller; one failed queue
     * does not prevent the remaining queues from being drained.
     */
    fun flushAllSavesNow(): Boolean {
        if (sourceIsolationFailure) return false
        val docId = _viewer.value?.docId?.takeIf { it.isNotBlank() } ?: return true
        // Include state that has not reached a debounce yet, then drain all three
        // queues without holding one store's lock while entering another store.
        persistNow(forceSaveStatus = _viewer.value?.saveStatus == PdfSaveStatus.FAILED)
        saveReadingNow()
        var allSucceeded = drainDocSavesNow(docId)
        allSucceeded = allSucceeded && synchronized(docSaveLock) {
            docId !in failedDocSaveDocs
        }
        try {
            allSucceeded = flushReadingSaveNow(docId) && allSucceeded
        } catch (error: Throwable) {
            rethrowIfCancelled(error)
            allSucceeded = false
        }
        try {
            allSucceeded = flushExcerptSavesNow(docId) && allSucceeded
        } catch (error: Throwable) {
            rethrowIfCancelled(error)
            allSucceeded = false
        }
        return allSucceeded
    }

    /** Drains queued docstates and reports whether every write attempted here succeeded. */
    private fun drainDocSavesNow(docId: String?): Boolean {
        var allSucceeded = true
        synchronized(docWriteLock) {
            while (true) {
                val next: DocSaveRequest?
                synchronized(docSaveLock) {
                    val (updated, request) = docSaveQueue.takeFor(docId)
                    docSaveQueue = updated
                    next = request
                }
                if (next == null) return allSucceeded
                val result = writeDocState(next)
                allSucceeded = allSucceeded && result.succeeded
                publishDocSaveCompletion(next, result)
            }
        }
    }

    /** Writes the newest pending state in a loop until nothing remains queued. */
    private suspend fun drainDocSaves() {
        while (true) {
            // Never wait for Main while holding the write lock: a Main-thread
            // backup flush would otherwise deadlock against this publication.
            val written = synchronized(docWriteLock) {
                val request = synchronized(docSaveLock) {
                    val (updated, next) = docSaveQueue.take()
                    docSaveQueue = updated
                    next
                }
                request?.let { it to writeDocState(it) }
            }
            if (written == null) return
            val (request, result) = written
            if (!currentCoroutineContext().isActive) return
            withContext(Dispatchers.Main) {
                if (currentCoroutineContext().isActive) publishDocSaveCompletion(request, result)
            }
        }
    }

    /** Saves the page the user landed on after a short pause so page flipping stays cheap. */
    private fun scheduleReadingSave(pageIndex: Int) {
        val state = _viewer.value ?: return
        queueReadingSave(PendingReadingWrite(state.docId, readingStateFor(state, pageIndex)), delayed = true)
    }

    private fun readingStateFor(state: ViewerUiState, pageIndex: Int = state.pageIndex): PdfReadingState =
        PdfReadingState(
            lastPage = pageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
            bookmarks = _bookmarks.value,
        ).normalized(state.pageCount)

    private fun queueReadingSave(request: PendingReadingWrite, delayed: Boolean) {
        readingSaveJob?.cancel()
        pendingReadingWrite = request
        val queued = request
        readingSaveJob = viewModelScope.launch {
            if (delayed) delay(READING_SAVE_DELAY_MS)
            val saved = withContext(Dispatchers.IO) { readingStore.save(queued.docId, queued.state) }
            if (saved) {
                failedReadingWrites.remove(queued.docId)
            } else {
                failedReadingWrites[queued.docId] = queued
            }
            if (pendingReadingWrite === queued) pendingReadingWrite = null
        }
    }

    /** Flushes progress and bookmarks without debounce (bookmark edits, document close). */
    private fun saveReadingNow() {
        val state = _viewer.value ?: return
        queueReadingSave(PendingReadingWrite(state.docId, readingStateFor(state)), delayed = false)
    }

    /**
     * Synchronous target-document reading barrier. The store itself serialises IO
     * and never calls Main, so cancelling/joining a coroutine here cannot invert
     * the Main/IO lock order used by docstate writes.
     */
    private fun flushReadingSaveNow(docId: String): Boolean {
        val pending = pendingReadingWrite?.takeIf { it.docId == docId }
            ?: failedReadingWrites[docId]
            ?: return true
        readingSaveJob?.cancel()
        readingSaveJob = null
        pendingReadingWrite = null
        val saved = readingStore.save(pending.docId, pending.state)
        if (saved) failedReadingWrites.remove(docId) else failedReadingWrites[docId] = pending
        return saved
    }

    private fun isCurrentViewerSession(state: ViewerUiState, generation: Long): Boolean =
        viewerSaveGeneration == generation &&
            _viewer.value?.docId == state.docId &&
            _viewer.value?.contentFingerprint == state.contentFingerprint

    private fun text(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun docIdFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(finishPointEraserUndo)
        clearPointEraserUndo()
        super.onCleared()
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
