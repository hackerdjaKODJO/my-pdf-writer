package com.hackerdjakodjo.mypdfwriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenSettingsRepository
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.audio.annotationRevealFraction
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.PdfLink
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLayout
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import com.hackerdjakodjo.mypdfwriter.pdf.SearchMatchLayout
import com.hackerdjakodjo.mypdfwriter.pdf.SearchMatchRect
import com.hackerdjakodjo.mypdfwriter.pdf.SummarySafWriter
import com.hackerdjakodjo.mypdfwriter.pdf.TextActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** Debounce for the transient selection mirror into the view model. */
private const val SELECTION_MIRROR_SETTLE_MS = 120L

/**
 * Every viewer sheet/dialog that must intercept system Back before the document
 * itself closes. Kept as a pure value so the predicate is unit-testable — omitting
 * a single flag (as happened with the memo-card and pen-box sheets) makes Back
 * close the whole PDF while that sheet is still open.
 *
 * The unified `위치` sheet lives here too: opening it must not let Back close
 * the whole document while it is on screen. The easy PDF 메뉴 and its expert
 * hub are separate flags so Back always closes the visible layer first.
 */
internal data class ViewerSheetFlags(
    val showPages: Boolean = false,
    val showNotes: Boolean = false,
    val showBookmarks: Boolean = false,
    val showSearch: Boolean = false,
    val showAnnotations: Boolean = false,
    val showOrganize: Boolean = false,
    val showExcerpts: Boolean = false,
    val showContinuous: Boolean = false,
    val showSpread: Boolean = false,
    val showPopup: Boolean = false,
    val showAudioNotes: Boolean = false,
    val showAudioReplay: Boolean = false,
    val showStudyNote: Boolean = false,
    val showReflow: Boolean = false,
    val showReadAloud: Boolean = false,
    val showProperties: Boolean = false,
    val showStyle: Boolean = false,
    val showDisplaySettings: Boolean = false,
    val showEasyMenu: Boolean = false,
    val showHub: Boolean = false,
    val showReadModes: Boolean = false,
    val showAudioMenu: Boolean = false,
    val showToolMore: Boolean = false,
    val showToolStyle: Boolean = false,
    val showSelectionMore: Boolean = false,
    val showTextEditDialog: Boolean = false,
    val showMemoCardsSheet: Boolean = false,
    val showViewerPenBox: Boolean = false,
    val showRecentDocs: Boolean = false,
    val showTabOverflow: Boolean = false,
    val showLocations: Boolean = false,
    val showFileTools: Boolean = false,
    val textTargetOpen: Boolean = false,
    val stampTargetOpen: Boolean = false,
    val signatureTargetOpen: Boolean = false,
) {
    val anyOpen: Boolean get() = showPages || showNotes || showBookmarks || showSearch ||
        showAnnotations || showOrganize || showExcerpts || showContinuous || showSpread ||
        showPopup || showAudioNotes || showAudioReplay || showStudyNote || showReflow ||
        showReadAloud || showProperties || showStyle || showDisplaySettings ||
        showEasyMenu || showHub || showReadModes || showAudioMenu || showToolMore || showToolStyle ||
        showSelectionMore || showTextEditDialog || showMemoCardsSheet || showViewerPenBox ||
        showRecentDocs || showTabOverflow || showLocations || showFileTools ||
        textTargetOpen || stampTargetOpen || signatureTargetOpen
}

/**
 * The full-screen top chrome has exactly four controls, in this order. The
 * bookmark button, note entry and auto-hide toggle moved into 학습 메뉴 (and the
 * existing document-tab sheets) so the bar no longer competes for reading space.
 */
internal enum class PdfTopChromeControl(val label: String) {
    BACK("뒤로"),
    TITLE("문서 이름"),
    SEARCH("검색"),
    HUB(UiCopy.PDF_MENU),
}

internal val pdfTopChromeControls = listOf(
    PdfTopChromeControl.BACK,
    PdfTopChromeControl.TITLE,
    PdfTopChromeControl.SEARCH,
    PdfTopChromeControl.HUB,
)

internal const val PDF_TOP_CHROME_MAX_CONTROLS = 4

/**
 * Session-only first-open coach. The three steps mirror the student flow
 * (open → mark → capture) and stay in process memory: one card per visit, not
 * per document, and no persistence.
 */
internal data class PdfCoachState(
    val shownThisSession: Boolean = false,
    val dismissed: Boolean = false,
)

internal fun shouldShowPdfCoach(state: PdfCoachState): Boolean =
    !state.shownThisSession && !state.dismissed

internal fun dismissPdfCoach(state: PdfCoachState): PdfCoachState =
    state.copy(shownThisSession = true, dismissed = true)

/**
 * Query used by the on-page highlight overlay. It must be normalized exactly
 * like `PdfViewModel.search` does, otherwise a padded query highlights matches
 * that the search result list (and its navigation chip) never reports.
 */
internal fun viewerHighlightQuery(rawQuery: String): String = rawQuery.trim()

internal const val PDF_SAVE_STATUS_TAG = "pdf_save_status"
internal const val PDF_RETRY_SAVE_TAG = "pdf_retry_save"

/** Truthful save copy comes only from the view model's durable save status. */
internal fun pdfSaveStatusLabel(status: PdfSaveStatus): String? = when (status) {
    PdfSaveStatus.IDLE -> null
    PdfSaveStatus.SAVING -> "자동 저장 중…"
    PdfSaveStatus.SAVED -> "자동 저장됨 · 원본 PDF는 그대로"
    PdfSaveStatus.FAILED -> "저장 실패 · 다시 시도"
}

internal fun pdfSaveRetryVisible(status: PdfSaveStatus): Boolean = status == PdfSaveStatus.FAILED

internal enum class PdfMessageFeedback { NONE, EXPORT_SUCCESS, EXPORT_FAILURE }

/** Keeps snackbar routing testable without guessing from unrelated messages. */
internal fun pdfMessageFeedback(
    message: String?,
    exportDoneMessage: String,
    exportFailurePrefix: String,
): PdfMessageFeedback = when {
    message.isNullOrBlank() -> PdfMessageFeedback.NONE
    message == exportDoneMessage -> PdfMessageFeedback.EXPORT_SUCCESS
    exportFailurePrefix.isNotBlank() && message.startsWith(exportFailurePrefix) ->
        PdfMessageFeedback.EXPORT_FAILURE
    else -> PdfMessageFeedback.NONE
}

internal object PdfViewerCoachSession {
    var shown: Boolean = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    vm: PdfViewModel,
    onExportRequest: (String) -> Unit,
    onOpenTools: () -> Unit,
    onCreateStudyNote: (Notebook) -> Unit = {},
    /** Optional integration hook used by drag-and-drop capture; must not open the notebook. */
    onAppendStudyExcerpt: (PdfStudyCapture) -> Boolean = { true },
    /**
     * Optional hub entry point for the live study-note pane. When present, the
     * PDF hub offers "스터디 노트"; the host resolves or creates the notebook
     * without closing the PDF.
     */
    onOpenStudyNote: ((StudySource) -> Unit)? = null,
    /** Optional help hook. When present, the PDF hub offers a "도움말" action. */
    onOpenHelp: (() -> Unit)? = null,
    /** Opens the help center directly on a task-specific topic when supported. */
    onOpenHelpTopic: ((HelpTopicKey) -> Unit)? = null,
    /**
     * Height of the embedded study pane as a fraction of the PDF content area.
     * Ignored when [studyPane] is null.
     */
    studyPaneFraction: Float = 0f,
    /** Called continuously while the draggable PDF/note divider is moved. */
    onStudyPaneFractionChange: (Float) -> Unit = {},
    /**
     * Live study-note pane content hosted below the PDF canvas and above the PDF
     * tool/selection bar. The host owns the notebook and height state.
     */
    studyPane: (@Composable () -> Unit)? = null,
    /** Called by system Back so the pane closes before the whole PDF. */
    onCloseStudyPane: (() -> Unit)? = null,
) {
    val stateFlow = vm.viewer.collectAsStateWithLifecycle()
    val bitmap by vm.pageBitmap.collectAsStateWithLifecycle()
    val outline by vm.outline.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val excerpts by vm.excerpts.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val recentDocs by vm.library.collectAsStateWithLifecycle()
    val pageTextWords by vm.textWords.collectAsStateWithLifecycle()
    val pageLinks by vm.pageLinks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val accessibilityScale = chromeScale(widthDp = 360, fontScale = LocalConfiguration.current.fontScale)
    val penRepository = remember(context) { PenSettingsRepository.get(context) }
    val penSnapshot by penRepository.state.collectAsStateWithLifecycle()
    val viewerFavorites = penSnapshot.favorites
    var showViewerPenBox by remember { mutableStateOf(false) }

    fun applyViewerFavorite(preset: PenPreset) {
        vm.applyInkPreset(preset)
    }

    val state = stateFlow.value ?: return

    fun currentViewerPen(): PenPreset = com.hackerdjakodjo.mypdfwriter.data.InkPolicy.currentPen(
        penSnapshot.ink,
        if (state.tool == Tool.HIGHLIGHT) Tool.HIGHLIGHT else Tool.PEN,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appendStudyExcerpt: (PdfStudyCapture) -> Boolean = { capture ->
        val added = onAppendStudyExcerpt(capture)
        if (added) {
            scope.launch { snackbarHostState.showSnackbar(STUDY_NOTE_ADDED_MESSAGE) }
        }
        added
    }
    var showPages by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showOrganize by remember { mutableStateOf(false) }
    var showExcerpts by remember { mutableStateOf(false) }
    var showContinuous by remember { mutableStateOf(false) }
    var showSpread by remember { mutableStateOf(false) }
    var showPopup by remember { mutableStateOf(false) }
    var showAudioNotes by remember { mutableStateOf(false) }
    var showAudioReplay by remember { mutableStateOf(false) }
    var replayNote by remember { mutableStateOf<AudioNote?>(null) }
    var replayPositionMs by remember { mutableStateOf(0L) }
    var showStudyNote by remember { mutableStateOf(false) }
    var showReflow by remember { mutableStateOf(false) }
    var showReadAloud by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showEasyMenu by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    var showReadModes by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showToolMore by remember { mutableStateOf(false) }
    var showToolStyle by remember { mutableStateOf(false) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var showTextEditDialog by remember { mutableStateOf(false) }
    // Display-only: annotations stay stored and exported; this only hides the canvas layer.
    var annotationsHidden by remember(state.docId) { mutableStateOf(false) }
    // PDF post-it cards persist in DocState; visibility is a view-only toggle.
    var showMemoCardsOnPage by remember(state.docId) { mutableStateOf(true) }
    var showMemoCardsSheet by remember { mutableStateOf(false) }
    var memoEditCardId by remember { mutableStateOf<String?>(null) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var textTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var stampTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var signatureTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var textSelectMode by remember { mutableStateOf(false) }
    var selectedWords by remember { mutableStateOf<List<PdfTextWord>>(emptyList()) }
    var searchHighlights by remember { mutableStateOf<List<SearchMatchRect>>(emptyList()) }
    // Explicit dismissal of the one contextual hint chip; resets on document,
    // page, tool or selection-mode changes so guidance is not lost forever.
    var hintDismissed by remember { mutableStateOf(false) }
    // Divider chip: the floating PDF tool strip starts visible and can be hidden
    // without affecting the selection bar or any document callback.

    // GoodReader-style session chrome: file tabs, the recents switcher and the
    // auto-hide menu mode. None of this is persisted; a new visit starts fresh.
    var tabsState by remember { mutableStateOf(DocumentTabsState()) }
    var showRecentDocs by remember { mutableStateOf(false) }
    var showTabOverflow by remember { mutableStateOf(false) }
    var showLocations by remember { mutableStateOf(false) }
    var showFileTools by remember { mutableStateOf(false) }
    // SAF returns after the Activity is recreated; keep the text across that
    // recreation and clear it on every outcome (including a cancelled picker).
    var pendingSummary by rememberSaveable { mutableStateOf("") }
    val smallestScreenWidthDp = LocalConfiguration.current.smallestScreenWidthDp
    var autoHideMode by remember {
        mutableStateOf(defaultChromeAutoHideMode(smallestScreenWidthDp))
    }
    var chromeHidden by remember { mutableStateOf(false) }
    val selectedText = remember(selectedWords) { PdfTextLayout.selectionText(selectedWords) }
    val openPdfHelp: (HelpTopicKey) -> Unit = { topic ->
        if (onOpenHelpTopic != null) {
            onOpenHelpTopic(topic)
        } else {
            onOpenHelp?.invoke()
        }
    }
    // Progressive disclosure is session-only: every PDF 메뉴 entry starts easy.
    val openPdfMenu: () -> Unit = {
        chromeHidden = false
        showEasyMenu = true
        showHub = false
    }

    // The replacement action lives in the view model, so mirror the local canvas
    // selection into it. This is transient UI state, never persisted or snapshotted.
    // Dragging changes the selection on nearly every touch frame, but the view
    // model only needs the settled value (for the text-replace action). Publishing
    // every frame emitted a new ViewerUiState and forced a SECOND whole-screen
    // recomposition per tick. Clearing is still immediate so no action can use a
    // stale selection.
    LaunchedEffect(selectedWords) {
        if (selectedWords.isEmpty()) {
            vm.setSelectedWords(selectedWords)
        } else {
            delay(SELECTION_MIRROR_SETTLE_MS)
            vm.setSelectedWords(selectedWords)
        }
    }

    /** Enter/leave the text-selection (lasso) mode used by both the dock and the hub. */
    val toggleTextSelection: () -> Unit = {
        if (textSelectMode) {
            textSelectMode = false
            selectedWords = emptyList()
        } else {
            vm.setTool(Tool.VIEW)
            textSelectMode = true
            selectedWords = emptyList()
            vm.loadTextWords(state.pageIndex)
        }
    }

    /**
     * Long press in VIEW mode enters selection mode. When this page's word geometry
     * is already on screen, the word under the press is selected immediately;
     * otherwise selection mode opens with the normal loading hint. Drawing and
     * active selection gestures use different canvas paths and never call this.
     */
    val requestSelectionAt: (NormalizedPoint) -> Unit = { point ->
        if (!textSelectMode) {
            vm.setTool(Tool.VIEW)
            textSelectMode = true
            selectedWords = emptyList()
            val pageWords = pageTextWords.filter {
                it.pageIndex == state.pageIndex && it.text.isNotBlank()
            }
            if (!state.textLoading && pageWords.isNotEmpty()) {
                selectedWords = listOfNotNull(
                    PdfTextLayout.wordAt(pageWords, point.x, point.y),
                )
            } else {
                vm.loadTextWords(state.pageIndex)
            }
        }
    }

    /**
     * VIEW-mode PDF link taps. Internal destinations use the normal (lock-gated)
     * navigation path; external URIs are copy-only and never open a browser.
     */
    val handleLinkTap: (PdfLink) -> Unit = { link ->
        when {
            link.targetPage != null -> vm.goToPage(link.targetPage)
            link.uri != null -> {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("PDF 링크", link.uri))
                Toast.makeText(context, "링크 주소를 복사했습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val selectTool: (Tool) -> Unit = { tool -> vm.setTool(tool) }

    LaunchedEffect(state.docId) {
        textSelectMode = false
        selectedWords = emptyList()
    }
    // Seeds the first tab and follows every document switch/rename. The study
    // pane still saves on the existing docId change; this only tracks the tab.
    LaunchedEffect(state.docId, state.docName) {
        val uri = vm.currentDocumentUri()
        if (!uri.isNullOrBlank()) {
            tabsState = upsertDocumentTab(
                tabsState,
                OpenDocumentTab(state.docId, uri, state.docName),
            )
        }
    }
    LaunchedEffect(state.pageIndex, state.revision) {
        selectedWords = emptyList()
        if (textSelectMode) vm.loadTextWords(state.pageIndex)
    }
    LaunchedEffect(state.tool) {
        if (textSelectMode && state.tool != Tool.VIEW) {
            textSelectMode = false
            selectedWords = emptyList()
        }
    }
    LaunchedEffect(state.docId, state.pageIndex, state.tool, textSelectMode) {
        hintDismissed = false
    }
    // An active annotation tool or text selection needs its action bar; never
    // leave it stranded behind hidden chrome.
    LaunchedEffect(textSelectMode, state.tool) {
        if (textSelectMode || state.tool != Tool.VIEW) chromeHidden = false
    }
    LaunchedEffect(state.searchQuery, state.pageIndex, state.revision) {
        val query = viewerHighlightQuery(state.searchQuery)
        if (query.isEmpty()) {
            searchHighlights = emptyList()
            return@LaunchedEffect
        }
        val file = vm.currentWorkingFile()
        if (file == null) {
            searchHighlights = emptyList()
            return@LaunchedEffect
        }
        val lines = withContext(Dispatchers.IO) {
            try {
                PdfTextLayout.lines(file)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }
        searchHighlights = SearchMatchLayout.matchesForPage(lines, state.pageIndex, query)
    }

    val extractPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null && selectedPages.isNotEmpty()) {
            vm.extractPages(selectedPages, uri)
            selectedPages = emptySet()
        }
    }

    val exportSummary = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (SummarySafWriter.outcomeForResult(uri != null, pendingSummary) ==
            SummarySafWriter.Outcome.WRITE && uri != null
        ) {
            val written = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    SummarySafWriter.write(out, pendingSummary)
                } ?: false
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (written) "주석 요약을 저장했습니다" else "주석 요약을 저장하지 못했습니다",
                Toast.LENGTH_SHORT,
            ).show()
        }
        // Cancelled picker, blank payload, failed write or success: never carry
        // the pending text into the next export request.
        pendingSummary = ""
    }

    val sheetOpen = ViewerSheetFlags(
        showPages = showPages,
        showNotes = showNotes,
        showBookmarks = showBookmarks,
        showSearch = showSearch,
        showAnnotations = showAnnotations,
        showOrganize = showOrganize,
        showExcerpts = showExcerpts,
        showContinuous = showContinuous,
        showSpread = showSpread,
        showPopup = showPopup,
        showAudioNotes = showAudioNotes,
        showAudioReplay = showAudioReplay,
        showStudyNote = showStudyNote,
        showReflow = showReflow,
        showReadAloud = showReadAloud,
        showProperties = showProperties,
        showStyle = showStyle,
        showDisplaySettings = showDisplaySettings,
        showEasyMenu = showEasyMenu,
        showHub = showHub,
        showReadModes = showReadModes,
        showAudioMenu = showAudioMenu,
        showToolMore = showToolMore,
        showToolStyle = showToolStyle,
        showSelectionMore = showSelectionMore,
        showTextEditDialog = showTextEditDialog,
        showMemoCardsSheet = showMemoCardsSheet,
        showViewerPenBox = showViewerPenBox,
        showRecentDocs = showRecentDocs,
        showTabOverflow = showTabOverflow,
        showLocations = showLocations,
        showFileTools = showFileTools,
        textTargetOpen = textTarget != null,
        stampTargetOpen = stampTarget != null,
        signatureTargetOpen = signatureTarget != null,
    ).anyOpen

    val handleViewerBack: () -> Unit = {
        when (viewerBackTarget(
            hasStudyPane = studyPane != null && onCloseStudyPane != null,
            hasSelection = textSelectMode || selectedWords.isNotEmpty(),
        )) {
            ViewerBackTarget.CLOSE_STUDY_PANE -> onCloseStudyPane?.invoke()
            ViewerBackTarget.CLEAR_SELECTION -> {
                textSelectMode = false
                selectedWords = emptyList()
            }
            ViewerBackTarget.CLOSE_DOCUMENT -> vm.closeDocument()
        }
    }
    BackHandler(enabled = !sheetOpen, onBack = handleViewerBack)

    if (showReflow) {
        val source = vm.currentWorkingFile() ?: vm.currentSourceFile()
        if (source == null) {
            showReflow = false
        } else {
            ReflowReaderScreen(
                sourceFile = source,
                docName = state.docName,
                initialPage = state.pageIndex,
                brightness = state.brightness,
                onBack = { showReflow = false },
                onOpenPage = { page ->
                    vm.goToPage(page)
                    showReflow = false
                },
            )
            return
        }
    }

    if (showSpread) {
        SpreadReaderScreen(
            title = state.docName,
            pageCount = state.pageCount,
            initialPage = state.pageIndex,
            brightness = state.brightness,
            cropInsets = state.cropInsets,
            cropOddEven = state.cropOddEven,
            loadPage = { index -> vm.loadReaderPage(index) },
            onBack = { showSpread = false },
            onOpenPage = { index ->
                vm.goToPage(index)
                showSpread = false
            },
        )
        return
    }

    if (showContinuous) {
        ContinuousReaderScreen(
            title = state.docName,
            pageCount = state.pageCount,
            initialPage = state.pageIndex,
            brightness = state.brightness,
            cropInsets = state.cropInsets,
            cropOddEven = state.cropOddEven,
            loadPage = { index, zoom -> vm.loadReaderPage(index, zoom) },
            onBack = { showContinuous = false },
            onOpenPage = { index ->
                vm.goToPage(index)
                showContinuous = false
            },
            // Swiping at fit zoom turns the page and keeps the ViewModel in sync,
            // so leaving continuous view returns to the page you were reading.
            onPageChange = { index -> vm.goToPage(index) },
        )
        return
    }

    if (showOrganize) {
        OrganizeScreen(
            docName = state.docName,
            pages = state.pages,
            selected = selectedPages,
            loadThumbnail = { index -> vm.loadThumbnail(index) },
            revision = state.revision,
            onBack = { showOrganize = false },
            onSelectionChange = { selectedPages = it },
            onRotate = { indices, delta ->
                vm.rotatePages(indices, delta)
                selectedPages = emptySet()
            },
            onDelete = { indices ->
                vm.deletePages(indices)
                selectedPages = emptySet()
            },
            onDuplicate = { indices ->
                vm.duplicatePages(indices)
                selectedPages = emptySet()
            },
            onMove = { indices, direction ->
                vm.movePages(indices, direction)
                selectedPages = emptySet()
            },
            onInsertBlank = { afterIndex ->
                vm.insertBlankPages(afterIndex)
                selectedPages = emptySet()
            },
            onExtract = { indices ->
                selectedPages = indices
                extractPdf.launch("extract.pdf")
            },
            onMerge = onOpenTools,
            onSplit = onOpenTools,
            onExport = { onExportRequest(vm.exportSuggestedName()) },
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = vm::undo,
            onRedo = vm::redo,
        )
        return
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val feedback = pdfMessageFeedback(
            message = message,
            exportDoneMessage = context.getString(R.string.export_done),
            exportFailurePrefix = context.getString(R.string.export_failed, "").trimEnd(),
        )
        val result = when (feedback) {
            PdfMessageFeedback.EXPORT_SUCCESS -> snackbarHostState.showSnackbar(
                message = context.getString(R.string.export_done_with_notes),
                actionLabel = "공유",
                duration = SnackbarDuration.Long,
            )
            PdfMessageFeedback.EXPORT_FAILURE -> snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "다시 시도",
                duration = SnackbarDuration.Long,
            )
            PdfMessageFeedback.NONE -> snackbarHostState.showSnackbar(message)
        }
        if (result == SnackbarResult.ActionPerformed) {
            when (feedback) {
                PdfMessageFeedback.EXPORT_SUCCESS -> vm.shareCurrent()
                PdfMessageFeedback.EXPORT_FAILURE -> onExportRequest(vm.exportSuggestedName())
                PdfMessageFeedback.NONE -> Unit
            }
        }
        vm.clearMessage()
    }

    val currentBookmark = bookmarks.firstOrNull { it.pageIndex == state.pageIndex }

    val openNote: () -> Unit = {
        val callback = onOpenStudyNote
        val source = vm.currentStudySource()
        if (callback != null && source != null) {
            callback(source)
        } else {
            showStudyNote = true
        }
    }
    // One capture path for the hub, the selection bar and drag-and-drop: persist
    // the excerpt, hand it to the live pane when there is one, then open the note.
    val captureToNote: () -> Unit = {
        if (selectedText.isNotBlank()) {
            val capture = vm.addExcerptFromSelection(state.pageIndex, selectedText)
            if (capture != null && studyPane != null) onAppendStudyExcerpt(capture)
            selectedWords = emptyList()
            textSelectMode = false
        }
        openNote()
    }
    fun hubAction(action: PdfMenuAction): PdfHubAction = when (action) {
        // Kept exhaustive even though the menu no longer lists SEARCH: the
        // top bar owns one-tap search. See pdfTopBarOwnedActions.
        PdfMenuAction.SEARCH -> PdfHubAction(action.label, Icons.Outlined.Search) {
            showSearch = true
        }

        // 읽기: one 위치 entry replaces the three duplicate navigation rows.
        PdfMenuAction.LOCATIONS -> PdfHubAction(action.label, Icons.Outlined.StarBorder) {
            vm.loadOutline()
            showLocations = true
        }

        PdfMenuAction.BOOKMARK_TOGGLE -> PdfHubAction(
            pdfMenuActionLabel(action, bookmarked = currentBookmark != null),
            if (currentBookmark != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
            trailing = if (currentBookmark != null) "저장됨" else null,
            kind = PdfHubActionKind.TOGGLE,
        ) {
            if (currentBookmark != null) {
                vm.deleteBookmark(currentBookmark.id)
            } else {
                vm.addCurrentPageBookmark("${state.pageIndex + 1}쪽")
            }
        }

        PdfMenuAction.PAGE_NOTES -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.StickyNote2,
            trailing = if (state.notes.isEmpty()) null else "${state.notes.size}개",
        ) { showNotes = true }

        PdfMenuAction.DISPLAY_SETTINGS -> PdfHubAction(
            action.label,
            Icons.Outlined.DisplaySettings,
            trailing = if (state.nightMode) "야간" else "주간",
        ) { showDisplaySettings = true }

        PdfMenuAction.READ_MODE -> PdfHubAction(action.label, Icons.Outlined.AutoStories) {
            showReadModes = true
        }

        PdfMenuAction.AUDIO -> PdfHubAction(action.label, Icons.AutoMirrored.Outlined.VolumeUp) {
            showAudioMenu = true
        }

        PdfMenuAction.FIT_SCREEN -> PdfHubAction(
            action.label,
            Icons.Outlined.FitScreen,
            trailing = "${(state.zoom * 100).roundToInt()}%",
            kind = PdfHubActionKind.COMMAND,
        ) { vm.setZoom(1f) }

        // 필기: annotation tools, post-its and visibility stay one tap away.
        PdfMenuAction.TOOL_MORE -> PdfHubAction(action.label, Icons.Outlined.Handyman) {
            showToolMore = true
        }

        PdfMenuAction.MEMO_CARDS -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.StickyNote2,
            trailing = if (state.memoCards.isEmpty()) null else "${state.memoCards.size}개",
        ) {
            memoEditCardId = null
            showMemoCardsSheet = true
        }

        PdfMenuAction.TOGGLE_ANNOTATIONS -> PdfHubAction(
            pdfMenuActionLabel(action, annotationsHidden = annotationsHidden),
            if (annotationsHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            trailing = if (annotationsHidden) "숨김" else "표시 중",
            kind = PdfHubActionKind.TOGGLE,
        ) { annotationsHidden = !annotationsHidden }

        PdfMenuAction.DECORATE -> PdfHubAction(action.label, Icons.Outlined.Palette) {
            showStyle = true
        }

        // 담기: one label, one capture path, one list of what was captured.
        PdfMenuAction.CAPTURE -> PdfHubAction(
            action.label,
            Icons.Outlined.EditNote,
            kind = PdfHubActionKind.COMMAND,
        ) {
            captureToNote()
        }

        PdfMenuAction.EXCERPTS -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.Notes,
            trailing = if (excerpts.isEmpty()) null else "${excerpts.size}개",
        ) { showExcerpts = true }

        PdfMenuAction.COPY_TEXT -> PdfHubAction(
            action.label,
            Icons.Outlined.ContentCopy,
            kind = PdfHubActionKind.COMMAND,
        ) {
            vm.copyPageText()
        }

        // 설정: the advanced file operations live one level deeper.
        PdfMenuAction.FILE_TOOLS -> PdfHubAction(action.label, Icons.Outlined.Handyman) {
            showFileTools = true
        }

        PdfMenuAction.AUTO_HIDE -> PdfHubAction(
            pdfMenuActionLabel(action, autoHideEnabled = autoHideMode == ChromeAutoHideMode.ON),
            if (autoHideMode == ChromeAutoHideMode.ON) {
                Icons.Outlined.VisibilityOff
            } else {
                Icons.Outlined.Visibility
            },
            trailing = autoHideMode.label,
            kind = PdfHubActionKind.TOGGLE,
        ) { autoHideMode = toggleChromeAutoHideMode(autoHideMode) }

        PdfMenuAction.HELP -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.HelpOutline,
            enabled = onOpenHelp != null || onOpenHelpTopic != null,
        ) { openPdfHelp(HelpTopicKey.QUICK_START) }

        PdfMenuAction.PAGE_EDIT -> PdfHubAction(action.label, Icons.Outlined.EditNote) {
            showPages = true
        }

        PdfMenuAction.PAGE_MANAGE -> PdfHubAction(action.label, Icons.Outlined.GridView) {
            selectedPages = emptySet()
            showOrganize = true
        }

        PdfMenuAction.PDF_TOOLS -> PdfHubAction(action.label, Icons.Outlined.Handyman) {
            onOpenTools()
        }

        PdfMenuAction.EXPORT -> PdfHubAction(
            action.label,
            Icons.Outlined.FileDownload,
            kind = PdfHubActionKind.COMMAND,
        ) {
            onExportRequest(vm.exportSuggestedName())
        }

        PdfMenuAction.PRINT -> PdfHubAction(
            action.label,
            Icons.Outlined.Print,
            kind = PdfHubActionKind.COMMAND,
        ) {
            vm.printCurrent()
        }

        PdfMenuAction.SHARE -> PdfHubAction(
            action.label,
            Icons.Outlined.Share,
            kind = PdfHubActionKind.COMMAND,
        ) {
            vm.shareCurrent()
        }

        PdfMenuAction.DOCUMENT_INFO -> PdfHubAction(action.label, Icons.Outlined.Info) {
            vm.loadMetadata()
            showProperties = true
        }

        PdfMenuAction.PAGE_LOCK -> PdfHubAction(
            pdfMenuActionLabel(action, pageLocked = state.pageLocked),
            if (state.pageLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            trailing = if (state.pageLocked) "켜짐" else null,
            kind = PdfHubActionKind.TOGGLE,
        ) { vm.togglePageLock() }

        PdfMenuAction.READ_TEXT_MODE -> PdfHubAction(action.label, Icons.Outlined.AutoStories) {
            showReflow = true
        }

        PdfMenuAction.READ_CONTINUOUS -> PdfHubAction(action.label, Icons.Outlined.ViewAgenda) {
            showContinuous = true
        }

        PdfMenuAction.READ_SPREAD -> PdfHubAction(action.label, Icons.AutoMirrored.Outlined.MenuBook) {
            showSpread = true
        }

        PdfMenuAction.READ_POPUP -> PdfHubAction(action.label, Icons.Outlined.PictureInPictureAlt) {
            showPopup = true
        }

        PdfMenuAction.READ_ALOUD -> PdfHubAction(action.label, Icons.Outlined.RecordVoiceOver) {
            showReadAloud = true
        }

        PdfMenuAction.AUDIO_NOTES -> PdfHubAction(action.label, Icons.Outlined.Mic) {
            showAudioNotes = true
        }

        PdfMenuAction.AUDIO_REPLAY -> PdfHubAction(action.label, Icons.Outlined.PlayCircle) {
            showAudioReplay = true
        }
    }
    val hubSections = pdfMenuTopLevelGroups.map { group ->
        PdfHubSection(
            title = group.title,
            actions = pdfMenuActions(group).map { hubAction(it) },
            expandable = group.expandable,
        )
    }
    val readModeSheetActions = pdfReadModeActions.map { hubAction(it) }
    val audioSheetActions = pdfAudioActions.map { hubAction(it) }
    val fileToolsSheetActions = pdfFileToolsActions.map { hubAction(it) }

    // Split/focus chrome: with the study pane open no app bar or dock row may
    // consume height. All PDF controls become floating overlays over the strip.
    val splitMode = studyPane != null
    val immersive = isChromeImmersive(splitMode, autoHideMode, chromeHidden)
    val chromePolicy = pdfChromePolicy(
        studyPaneOpen = splitMode,
        textSelectMode = textSelectMode,
        hasSelection = selectedWords.isNotEmpty(),
        chromeHidden = chromeHidden,
        autoHideMode = autoHideMode,
    )
    val density = LocalDensity.current
    val statusBarTopDp = with(density) {
        WindowInsets.statusBars.getTop(density).toDp()
    }
    val pdfTopInset = statusBarTopDp +
        if (chromePolicy.showTopAppBar) (PDF_COMPACT_TOP_BAR_HEIGHT_DP + 4).dp else 0.dp
    val chromeTapAllowed = chromeTapToggleAllowed(
        mode = autoHideMode,
        studyPaneOpen = splitMode,
        sheetOpen = sheetOpen,
        textSelectMode = textSelectMode,
        hasSelection = selectedWords.isNotEmpty(),
        toolIsView = state.tool == Tool.VIEW,
        busy = state.busy,
    )
    val showTabStrip = shouldShowDocumentTabStrip(tabsState) && !immersive &&
        !(splitMode && chromePolicy.showSplitSelectionBar)

    val openRecentDocs: () -> Unit = {
        chromeHidden = false
        showTabOverflow = false
        showRecentDocs = true
    }
    val openTabOverflow: () -> Unit = {
        chromeHidden = false
        showRecentDocs = false
        showTabOverflow = true
    }
    val hideTabStrip: () -> Unit = {
        tabsState = setDocumentTabBarVisible(tabsState, false)
    }
    val switchToTab: (OpenDocumentTab) -> Unit = { tab ->
        showRecentDocs = false
        showTabOverflow = false
        chromeHidden = false
        if (tab.docId != state.docId) {
            activateDocumentTab(tabsState, tab.docId)?.let { tabsState = it }
            vm.openDocument(Uri.parse(tab.uri))
        }
    }
    val closeTab: (OpenDocumentTab) -> Unit = { tab ->
        val before = tabsState
        val after = closeDocumentTab(before, tab.docId)
        tabsState = after
        if (tab.docId == before.activeDocId) {
            val next = after.activeTab
            if (next != null) {
                vm.openDocument(Uri.parse(next.uri))
            } else {
                // Last tab: unchanged exit path back to the library.
                vm.closeDocument()
            }
        }
    }
    // The pane share decides how much of the floating PDF strip fits. On a very
    // short strip only the page pill remains; the back button stays top-start.
    val pdfChromeDensity = if (splitMode) {
        splitPdfChromeDensity(clampStudyPaneFraction(studyPaneFraction))
    } else {
        SplitPdfChromeDensity.FULL
    }
    val splitToolbarVisible = splitMode && chromePolicy.showSplitToolbar
    // Hint and search chips share the top edge with the toolbar, so they sit in
    // the next row whenever the strip is drawn.
    val splitTopOverlayOffsetDp = if (splitToolbarVisible) {
        52f
    } else {
        statusBarTopDp.value + if (chromePolicy.showTopAppBar) {
            PDF_COMPACT_TOP_BAR_HEIGHT_DP + 6f
        } else {
            6f
        }
    }

    // One state, one hint. The chip only draws what this pure policy allows, so
    // the selection action bar and the tool hint can never be duplicated.
    val contextHint = pdfContextHint(
        PdfContextHintState(
            tool = state.tool,
            textSelectMode = textSelectMode,
            hasSelection = selectedWords.isNotEmpty(),
            textLoading = state.textLoading,
            hasWords = pageTextWords.isNotEmpty(),
            dismissed = hintDismissed,
        ),
    )
    val toolHintRes = when (state.tool) {
        Tool.VIEW -> R.string.hint_view
        Tool.TEXT -> R.string.hint_text
        Tool.STAMP -> R.string.hint_stamp
        Tool.SIGNATURE -> R.string.hint_signature
        Tool.REDACT -> R.string.hint_redact
        Tool.ERASER -> R.string.hint_eraser
        else -> R.string.hint_draw
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (chromePolicy.showBottomBar) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        PdfSaveStatusRow(
                            status = state.saveStatus,
                            onRetry = vm::retrySave,
                        )
                        ViewerContextActionRow(
                            state = state,
                            onStyle = { showToolStyle = true },
                            onUndo = vm::undo,
                            onRedo = vm::redo,
                        )
                        if (textSelectMode) {
                            TextSelectionActionBar(
                                text = selectedText,
                                pageIndex = state.pageIndex,
                                capture = vm.studyCaptureForSelection(state.pageIndex, selectedText),
                                hasSelection = selectedWords.isNotEmpty(),
                                loading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                wordCount = pageTextWords.size,
                                onCopy = { copySelection(context, selectedText) },
                                onExcerpt = captureToNote,
                                onHighlight = {
                                    vm.addTextMarkup(
                                        selectedWords,
                                        AnnotationType.HIGHLIGHT,
                                        0xFFFFD54F.toInt(),
                                    )
                                    selectedWords = emptyList()
                                },
                                onUnderline = {
                                    vm.addTextMarkup(selectedWords, AnnotationType.UNDERLINE, state.color)
                                    selectedWords = emptyList()
                                },
                                onStrikeout = {
                                    vm.addTextMarkup(selectedWords, AnnotationType.STRIKEOUT, state.color)
                                    selectedWords = emptyList()
                                },
                                onMore = { showSelectionMore = true },
                                onDismiss = {
                                    if (selectedWords.isNotEmpty()) {
                                        selectedWords = emptyList()
                                    } else {
                                        textSelectMode = false
                                        selectedWords = emptyList()
                                    }
                                },
                            )
                        } else {
                            ViewerToolDock(
                                state = state,
                                onSelectTool = selectTool,
                                // Long press a writing tool opens its own style sheet.
                                onLongPressTool = { tool ->
                                    selectTool(tool)
                                    showToolStyle = true
                                },
                                onTextSelect = toggleTextSelection,
                                onNote = openNote,
                                textSelectMode = textSelectMode,
                                compact = studyPane != null,
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                // TalkBack announces the pane name instead of an unlabelled
                // full-screen canvas when the viewer opens.
                .semantics {
                    // Announce where the student is when the reader gains focus.
                    paneTitle = buildString {
                        append(if (splitMode) "PDF 분할 보기" else "PDF 보기")
                        append(", ${state.pageIndex + 1}/${state.pageCount} 페이지")
                    }
                },
        ) {
            if (chromePolicy.showTopAppBar) {
                PdfCompactTopBar(
                    title = state.docName,
                    pageIndex = state.pageIndex,
                    pageCount = state.pageCount,
                    onBack = handleViewerBack,
                    onPrevious = vm::previousPage,
                    onNext = vm::nextPage,
                    onOpenPages = { showPages = true },
                    onTitleClick = { openRecentDocs() },
                    onSearch = { showSearch = true },
                    onOpenMenu = openPdfMenu,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f),
                )
            }
            // Split mode removes the app bar and bottom dock from layout, so the
            // PDF starts at the top safe edge and the pane gets the full rest of
            // the screen. PDF controls float inside the strip instead.
            val paneFraction = if (studyPane != null) {
                clampStudyPaneFraction(studyPaneFraction)
            } else {
                0f
            }
            var splitAreaHeightPx by remember { mutableFloatStateOf(0f) }
            val showSearchNavigation = !state.searching &&
                viewerHighlightQuery(state.searchQuery).isNotEmpty() &&
                state.searchHits.isNotEmpty()
            val pdfStripHeightDp = if (splitMode) {
                (maxHeight * (1f - paneFraction)).value
            } else {
                Float.POSITIVE_INFINITY
            }
            val showHintChip = pdfContextHintChipVisible(
                hint = contextHint,
                // Both the full-screen and the floating selection bar carry the
                // same status text while selection mode is active.
                selectionBarVisible = textSelectMode,
                searchNavigationVisible = showSearchNavigation,
                splitMode = splitMode,
                pdfStripHeightDp = pdfStripHeightDp,
            )
            // The one-time coach replaces the contextual hint; they never stack.
            val showTopHint = showHintChip && !immersive
            Column(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { splitAreaHeightPx = it.height.toFloat() },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight((1f - paneFraction).coerceAtLeast(0.10f)),
                ) {
                    // Below the canvas in z-order: drawing, pinch, selection and
                    // link taps are consumed by PdfCanvas first, so this only sees
                    // a plain VIEW tap in the centre band. Never used in split mode.
                    if (chromeTapAllowed) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(chromeHidden) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            if (isChromeToggleTap(
                                                    offset.x,
                                                    offset.y,
                                                    size.width,
                                                    size.height,
                                                )
                                            ) {
                                                chromeHidden = nextChromeHidden(chromeHidden, true)
                                            }
                                        },
                                        // Double tap toggles fit <-> 200% in the canvas.
                                        onDoubleTap = {},
                                    )
                                }
                                .semantics { contentDescription = "화면 탭하여 메뉴 표시/숨기기" },
                        )
                    }

                    PdfCanvas(
                        modifier = Modifier.padding(top = pdfTopInset),
                        bitmap = bitmap,
                        docId = state.docId,
                        pageIndex = state.pageIndex,
                        displayWidthPt = state.displayWidthPt,
                        displayHeightPt = state.displayHeightPt,
                        annotations = if (annotationsHidden) emptyList() else state.annotations,
                        tool = state.tool,
                        color = state.color,
                        strokeWidth = state.strokeWidth,
                        fontSize = state.fontSize,
                        opacity = state.opacity,
                        zoom = state.zoom,
                        documentStyle = state.documentStyle,
                        searchHighlights = searchHighlights,
                        pageCount = state.pageCount,
                        nightMode = state.nightMode,
                        brightness = state.brightness,
                        cropInsets = state.cropInsets,
                        cropOddEven = state.cropOddEven,
                        links = pageLinks,
                        textWords = pageTextWords,
                        textSelectMode = textSelectMode,
                        selectedWords = selectedWords,
                        annotationProgress = { annotation ->
                            if (replayNote != null && replayNote?.pageIndex != state.pageIndex) 1f
                            else annotationRevealFraction(annotation, replayNote, replayPositionMs)
                        },
                        memoCards = remember(state.memoCards, state.pageIndex) {
                            pdfMemoCardsForPage(state.memoCards, state.pageIndex)
                        },
                        showMemoCardsOnPage = showMemoCardsOnPage,
                        onOpenMemoCard = { id ->
                            memoEditCardId = id
                            showMemoCardsSheet = true
                        },
                        onMemoCardChange = vm::upsertMemoCard,
                        onHideMemoCardOverlay = { showMemoCardsOnPage = false },
                        onDeleteMemoCard = vm::deleteMemoCard,
                        penStyle = state.penStyle,
                        pressureEnabled = state.pressureInk,
                        pressureCurve = state.pressureCurve,
                        highlightWidth = state.highlightWidth,
                        highlightOpacity = state.highlightOpacity,
                        onZoomChange = vm::setZoom,
                        onAddStroke = vm::addStroke,
                        onEraseStroke = vm::eraseStroke,
                        onRequestText = { textTarget = it },
                        onRequestStamp = { stampTarget = it },
                        onRequestSignature = { signatureTarget = it },
                        onTextSelection = { selectedWords = it },
                        onRequestSelectionAt = requestSelectionAt,
                        onLinkTapped = handleLinkTap,
                        onPreviousPage = vm::previousPage,
                        onNextPage = vm::nextPage,
                    )

                    // Small, always-visible lock affordance. Bottom-start keeps it
                    // clear of the top hint/search chips; page navigation now lives
                    // in its own row below the canvas.
                    if (state.pageLocked) {
                        PageLockBadge(
                            onUnlock = vm::togglePageLock,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 10.dp, bottom = 12.dp),
                        )
                    }

                    // The one hint chip is an overlay: it never adds layout height.
                    // In split mode it drops one row below the floating tool strip
                    // so the two never cover each other.
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = splitTopOverlayOffsetDp.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Full-screen reader: the strip sits above the hint chip.
                        // Split mode places it at the strip's bottom instead, where
                        // it cannot cover the floating page/toolbar row.
                        if (showTabStrip && !splitMode) {
                            DocumentTabStrip(
                                state = tabsState,
                                onSelectTab = switchToTab,
                                onCloseTab = closeTab,
                                onOpenMenu = openTabOverflow,
                                onHideStrip = hideTabStrip,
                                modifier = Modifier.widthIn(
                                    max = if (showSearchNavigation) 240.dp else 320.dp,
                                ),
                            )
                            if (showTopHint) Spacer(Modifier.height(6.dp))
                        }
                        if (annotationsHidden) {
                            HiddenAnnotationsBanner(onShow = { annotationsHidden = false })
                            if (showTopHint) Spacer(Modifier.height(6.dp))
                        }
                        if (showTopHint) {
                            PdfContextHintChip(
                                text = pdfContextHintText(contextHint, stringResource(toolHintRes)),
                                loading = contextHint == PdfContextHint.LOADING,
                                ocrAction = contextHint == PdfContextHint.NO_TEXT,
                                subtle = splitMode,
                                onOcr = onOpenTools,
                                onDismiss = { hintDismissed = true },
                                modifier = Modifier.padding(
                                    start = if (splitMode) 48.dp else 0.dp,
                                    end = if (splitMode) 8.dp else 0.dp,
                                ),
                            )
                        }
                    }

                    if (immersive) {
                        // Auto-hidden chrome never traps the reader: exit stays a
                        // top-start tap and the menu stays a top-end tap away.
                        SplitPdfBackButton(
                            onBack = handleViewerBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 6.dp),
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 8.dp, top = 6.dp)
                                .size(SPLIT_CHROME_TARGET_DP.dp)
                                .clickable { chromeHidden = false }
                                .semantics { contentDescription = "메뉴 표시" },
                        ) {
                            Box(
                                Modifier.size(SPLIT_CHROME_VISUAL_DP.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }
                    }

                    // One-tap stepping over result pages. It is absent until a query
                    // has real hits, so the idle viewer and the tool dock stay calm,
                    // and its small top-end footprint never covers canvas gestures.
                    if (showSearchNavigation) {
                        val currentHit =
                            SearchNavigation.clamp(state.currentSearchHit, state.searchHits.size).coerceAtLeast(0)
                        SearchNextChip(
                            current = currentHit,
                            total = state.searchHits.size,
                            onNext = vm::nextSearchHit,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 10.dp, top = splitTopOverlayOffsetDp.dp),
                        )
                    }

                    if (chromePolicy.showPageDropTarget && selectedText.isNotBlank()) {
                        StudyNoteDropTarget(
                            onDrop = appendStudyExcerpt,
                            caption = "선택한 텍스트를 여기로 끌어다 놓으세요",
                            expectedDocId = state.docId,
                            expectedFingerprint = state.contentFingerprint,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 74.dp),
                        )
                    }

                    if (splitMode) {
                        SplitPdfBackButton(
                            onBack = handleViewerBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 6.dp),
                        )
                        if (splitToolbarVisible) {
                            // Top-center floating strip: symmetric side padding
                            // keeps it clear of the top-start back button and the
                            // top-end search chip while staying centered. It is an
                            // overlay and reserves no layout height.
                            SplitPdfToolbar(
                                bookmarked = currentBookmark != null,
                                textSelectMode = textSelectMode,
                                textLoading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                onToggleTextSelection = toggleTextSelection,
                                onOpenSearch = { showSearch = true },
                                onToggleBookmark = {
                                    if (currentBookmark != null) {
                                        vm.deleteBookmark(currentBookmark.id)
                                    } else {
                                        vm.addCurrentPageBookmark("${state.pageIndex + 1}쪽")
                                    }
                                },
                                onOpenTools = { showToolMore = true },
                                onOpenHub = openPdfMenu,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 6.dp, start = 48.dp, end = 48.dp),
                                compact = pdfChromeDensity == SplitPdfChromeDensity.ESSENTIAL,
                            )
                        }
                        if (chromePolicy.showSplitSelectionBar) {
                            SplitSelectionActionBar(
                                selectedText = selectedText,
                                pageIndex = state.pageIndex,
                                capture = vm.studyCaptureForSelection(state.pageIndex, selectedText),
                                hasSelection = selectedWords.isNotEmpty(),
                                loading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                wordCount = pageTextWords.size,
                                onCopy = { copySelection(context, selectedText) },
                                onExcerpt = captureToNote,
                                onHighlight = {
                                    vm.addTextMarkup(
                                        selectedWords,
                                        AnnotationType.HIGHLIGHT,
                                        0xFFFFD54F.toInt(),
                                    )
                                    selectedWords = emptyList()
                                },
                                onUnderline = {
                                    vm.addTextMarkup(selectedWords, AnnotationType.UNDERLINE, state.color)
                                    selectedWords = emptyList()
                                },
                                onStrikeout = {
                                    vm.addTextMarkup(selectedWords, AnnotationType.STRIKEOUT, state.color)
                                    selectedWords = emptyList()
                                },
                                onMore = { showSelectionMore = true },
                                onDismiss = {
                                    if (selectedWords.isNotEmpty()) {
                                        selectedWords = emptyList()
                                    } else {
                                        textSelectMode = false
                                        selectedWords = emptyList()
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 6.dp)
                                    .padding(bottom = 6.dp),
                            )
                        }
                        // The strip floats at the bottom of the PDF sample so the
                        // top-center page/toolbar row stays untouched. During text
                        // selection the selection bar owns that edge.
                        if (showTabStrip && !chromePolicy.showSplitSelectionBar) {
                            DocumentTabStrip(
                                state = tabsState,
                                onSelectTab = switchToTab,
                                onCloseTab = closeTab,
                                onOpenMenu = openTabOverflow,
                                onHideStrip = hideTabStrip,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                            )
                        }
                    }

                    if (state.busy) {
                        BusyOverlay(state.busyLabel)
                    }
                }

                if (studyPane != null) {
                    // Only the slim resize grip lives between PDF and note. Page
                    // navigation and PDF controls stay in their own PDF chrome;
                    // neither reserves vertical space here.
                    SplitDivider(
                        paneFraction = paneFraction,
                        totalHeightPx = splitAreaHeightPx,
                        onFractionChange = onStudyPaneFractionChange,
                        onPresetSelected = { fraction ->
                            onStudyPaneFractionChange(clampStudyPaneFraction(fraction))
                        },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(paneFraction.coerceAtLeast(0.10f)),
                    ) {
                        studyPane()
                    }
                }
            }
        }
    }

    if (showPages) {
        PageSheet(state, vm) { showPages = false }
    }
    if (showNotes) {
        NoteSheet(state, vm) { showNotes = false }
    }
    if (showMemoCardsSheet) {
        PdfMemoCardsSheet(
            cards = state.memoCards,
            currentPage = state.pageIndex,
            pageCount = state.pageCount,
            showOnPage = showMemoCardsOnPage,
            onToggleShowOnPage = { showMemoCardsOnPage = it },
            onCardChange = vm::upsertMemoCard,
            onDeleteCard = vm::deleteMemoCard,
            onJump = { page ->
                vm.goToPage(page)
                showMemoCardsSheet = false
            },
            editCardId = memoEditCardId,
            onEditHandled = { memoEditCardId = null },
            onDismiss = {
                memoEditCardId = null
                showMemoCardsSheet = false
            },
        )
    }
    if (showReadAloud) {
        vm.currentWorkingFile()?.let { working ->
            ReadAloudLoaderSheet(
                workingFile = working,
                initialPage = state.pageIndex,
                onPageChange = { page -> vm.goToPage(page) },
                onDismiss = { showReadAloud = false },
            )
        } ?: run { showReadAloud = false }
    }
    if (showBookmarks) {
        PdfBookmarksSheet(
            bookmarks = bookmarks,
            currentPage = state.pageIndex,
            onAdd = { title -> vm.addCurrentPageBookmark(title) },
            onDelete = { id -> vm.deleteBookmark(id) },
            onRename = { id, title -> vm.renameBookmark(id, title) },
            onJump = { page ->
                vm.goToPage(page)
                showBookmarks = false
            },
            onDismiss = { showBookmarks = false },
        )
    }
    if (showSearch) {
        SearchSheet(
            state = state,
            vm = vm,
            onDismiss = { showSearch = false },
            onOpenOcr = {
                showSearch = false
                onOpenTools()
            },
        )
    }
    if (showAnnotations) {
        AnnotationSheet(state, vm, annotationsHidden = annotationsHidden) { showAnnotations = false }
    }
    if (showExcerpts) {
        ExcerptSheet(
            excerpts = excerpts,
            currentPage = state.pageIndex,
            onAdd = { title -> vm.addCurrentPageExcerpt(title) },
            onDelete = { id -> vm.deleteExcerpt(id) },
            onJump = { page ->
                vm.goToPage(page)
                showExcerpts = false
            },
            onTranslate = { text, target -> TextActions.translate(context, text, target) },
            onDictionary = { text -> TextActions.define(context, text) },
            onShare = { text -> TextActions.shareText(context, text) },
            onStudyNote = {
                showExcerpts = false
                showStudyNote = true
            },
            onDropExcerpt = appendStudyExcerpt,
            captureForExcerpt = vm::studyCaptureForExcerpt,
            docId = state.docId,
            fingerprint = state.contentFingerprint,
            onDismiss = { showExcerpts = false },
        )
    }
    if (showStudyNote) {
        StudyNoteSheet(
            source = vm.currentStudySource() ?: StudySource(state.docName, vm.currentDocumentUri()),
            excerpts = excerpts,
            onDismiss = { showStudyNote = false },
            onCreate = { notebook ->
                showStudyNote = false
                onCreateStudyNote(notebook)
            },
        )
    }
    if (showAudioNotes) {
        AudioNotesSheet(
            ownerId = state.docId,
            currentPage = state.pageIndex,
            onDismiss = { showAudioNotes = false },
        )
    }
    if (showAudioReplay) {
        AudioReplaySheet(
            ownerId = state.docId,
            currentPage = state.pageIndex,
            onPlaybackState = { note, positionMs, _ ->
                replayNote = note
                replayPositionMs = positionMs
                if (note != null && note.pageIndex != state.pageIndex) {
                    vm.goToPage(note.pageIndex)
                }
            },
            onDismiss = {
                replayNote = null
                replayPositionMs = 0L
                showAudioReplay = false
            },
        )
    }
    if (showPopup) {
        PopupPageDialog(
            title = state.docName,
            bitmap = bitmap,
            pageIndex = state.pageIndex,
            pageCount = state.pageCount,
            onPrevious = { vm.previousPage() },
            onNext = { vm.nextPage() },
            onDismiss = { showPopup = false },
        )
    }
    if (showProperties) {
        PropertiesSheet(
            metadata = metadata,
            fileSize = vm.currentSourceFile()?.length() ?: 0L,
            pageCount = state.pageCount,
            onDismiss = { showProperties = false },
            onEdit = {
                showProperties = false
                onOpenTools()
            },
        )
    }
    if (showStyle) {
        DocumentStyleDialog(
            initial = state.documentStyle,
            pageCount = state.pageCount,
            onDismiss = { showStyle = false },
            onApply = {
                vm.setDocumentStyle(it)
                showStyle = false
            },
        )
    }
    if (showDisplaySettings) {
        DisplaySettingsSheet(state = state, vm = vm, onDismiss = { showDisplaySettings = false })
    }
    if (showEasyMenu) {
        PdfEasyMenuSheet(
            docName = state.docName,
            pageIndex = state.pageIndex,
            pageCount = state.pageCount,
            bookmarked = currentBookmark != null,
            taskEnabled = { task ->
                task != PdfEasyTask.HELP || onOpenHelp != null || onOpenHelpTopic != null
            },
            onTask = { task -> hubAction(task.menuAction).onClick() },
            onOpenExpert = {
                showEasyMenu = false
                showHub = true
            },
            onDismiss = { showEasyMenu = false },
        )
    }
    if (showHub) {
        PdfHubSheet(
            docName = state.docName,
            pageIndex = state.pageIndex,
            pageCount = state.pageCount,
            sections = hubSections,
            onDismiss = { showHub = false },
        )
    }
    if (showLocations) {
        LocationsSheet(
            bookmarks = bookmarks,
            outline = outline.orEmpty(),
            annotations = state.annotations,
            pageCount = state.pageCount,
            currentPage = state.pageIndex,
            onJumpToPage = { page -> vm.goToPage(page) },
            onExportSummary = { markdown ->
                pendingSummary = markdown
                val safeName = state.docName.replace(Regex("[^0-9A-Za-z가-힣._-]"), "_")
                exportSummary.launch("PDF-주석요약-$safeName.md")
            },
            onDismiss = { showLocations = false },
            // Full managers stay reachable without adding duplicate nav rows
            // to the hub: the unified panel hands off to the existing sheets.
            onManageBookmarks = {
                showLocations = false
                showBookmarks = true
            },
            onManageAnnotations = {
                showLocations = false
                showAnnotations = true
            },
        )
    }
    if (showRecentDocs) {
        RecentDocumentsSheet(
            documents = recentDocumentsForSwitcher(recentDocs),
            activeUri = vm.currentDocumentUri(),
            tabBarVisible = tabsState.barVisible,
            autoHideMode = autoHideMode,
            onOpenDocument = { doc ->
                showRecentDocs = false
                chromeHidden = false
                vm.openDocument(Uri.parse(doc.uri))
            },
            onToggleTabBar = {
                tabsState = setDocumentTabBarVisible(tabsState, !tabsState.barVisible)
            },
            onToggleAutoHide = { autoHideMode = toggleChromeAutoHideMode(autoHideMode) },
            onDismiss = { showRecentDocs = false },
        )
    }
    if (showTabOverflow) {
        DocumentTabOverflowSheet(
            tabs = tabsState.tabs,
            activeDocId = tabsState.activeDocId,
            tabBarVisible = tabsState.barVisible,
            autoHideMode = autoHideMode,
            onSelectTab = switchToTab,
            onCloseOthers = { tabsState = closeOtherDocumentTabs(tabsState) },
            onCloseAll = {
                showTabOverflow = false
                tabsState = closeAllDocumentTabs(tabsState)
                vm.closeDocument()
            },
            onToggleBar = {
                tabsState = setDocumentTabBarVisible(tabsState, !tabsState.barVisible)
            },
            onToggleAutoHide = { autoHideMode = toggleChromeAutoHideMode(autoHideMode) },
            onOpenRecents = openRecentDocs,
            onOpenLocations = {
                showTabOverflow = false
                vm.loadOutline()
                showLocations = true
            },
            onDismiss = { showTabOverflow = false },
        )
    }
    if (showReadModes) {
        PdfHubSubSheet(
            title = "읽기 모드",
            subtitle = "텍스트·연속·스프레드·팝업 보기와 소리 내어 읽기",
            actions = readModeSheetActions,
            onDismiss = { showReadModes = false },
        )
    }
    if (showAudioMenu) {
        PdfHubSubSheet(
            title = "오디오",
            subtitle = "현재 PDF 페이지에 연결된 오디오 노트",
            actions = audioSheetActions,
            onDismiss = { showAudioMenu = false },
        )
    }
    if (showFileTools) {
        PdfHubSubSheet(
            title = "파일 도구",
            subtitle = "페이지 이동·페이지 편집, PDF 도구, 인쇄·공유, 문서 정보·페이지 잠금 · 내보내기는 설정에서",
            actions = fileToolsSheetActions,
            onDismiss = { showFileTools = false },
        )
    }
    if (showToolMore) {
        ViewerToolMoreSheet(
            currentTool = state.tool,
            onSelectTool = { tool ->
                selectTool(tool)
                showToolMore = false
            },
            onDismiss = { showToolMore = false },
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = vm::undo,
            onRedo = vm::redo,
            styleEnabled = isStyleTool(state.tool),
            onStyle = {
                showToolMore = false
                showToolStyle = true
            },
            favorites = viewerFavorites,
            currentPen = currentViewerPen(),
            onApplyFavorite = { applyViewerFavorite(it) },
            onOpenPenRegistry = {
                showToolMore = false
                showViewerPenBox = true
            },
        )
    }
    if (showViewerPenBox) {
        PenBoxSheet(
            presets = penSnapshot.presets,
            current = currentViewerPen(),
            onDismiss = { showViewerPenBox = false },
            onApply = { applyViewerFavorite(it) },
            onSaveCurrent = { name ->
                val saved = currentViewerPen().copy(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { "새 펜" },
                )
                penRepository.updatePresets { it + saved }
            },
            onDelete = { preset ->
                penRepository.updatePresets { presets ->
                    if (presets.size > 1) presets.filterNot { it.id == preset.id } else presets
                }
            },
            onRename = { preset, name ->
                val clean = name.trim().ifBlank { preset.name }
                penRepository.updatePresets { presets ->
                    presets.map { if (it.id == preset.id) it.copy(name = clean) else it }
                }
            },
            favorites = viewerFavorites,
            onToggleFavorite = { preset -> penRepository.toggleFavorite(preset.id) },
            onMoveFavorite = { from, to -> penRepository.moveFavorite(from, to) },
        )
    }

    if (showToolStyle) {
        ViewerStyleSheet(
            state = state,
            onColor = vm::setColor,
            onStrokeWidth = vm::setStrokeWidth,
            onFontSize = vm::setFontSize,
            onOpacity = vm::setOpacity,
            onPenStyle = vm::setPenStyle,
            onHighlightWidth = vm::setHighlightWidth,
            onPressureInk = vm::setPressureInk,
            onPressureCurve = vm::setPressureCurve,
            onHighlightOpacity = vm::setHighlightOpacity,
            favorites = viewerFavorites,
            currentPen = currentViewerPen(),
            onApplyFavorite = { applyViewerFavorite(it) },
            onOpenPenRegistry = {
                showToolStyle = false
                showViewerPenBox = true
            },
            onDismiss = { showToolStyle = false },
        )
    }
    if (showSelectionMore) {
        TextSelectionMoreSheet(
            selectedText = selectedText,
            pageIndex = state.pageIndex,
            hasSelection = selectedWords.isNotEmpty(),
            canAddToPane = studyPane != null,
            onTranslate = { TextActions.translate(context, selectedText) },
            onDictionary = { TextActions.define(context, selectedText) },
            onExcerpt = captureToNote,
            onEditText = { showTextEditDialog = true },
            onShare = { TextActions.shareText(context, selectedText) },
            onAddToPane = if (studyPane != null) {
                {
                    val capture = vm.addExcerptFromSelection(state.pageIndex, selectedText)
                    if (capture != null) appendStudyExcerpt(capture)
                }
            } else {
                null
            },
            onDismissSelection = {
                if (selectedWords.isNotEmpty()) {
                    selectedWords = emptyList()
                } else {
                    textSelectMode = false
                    selectedWords = emptyList()
                }
            },
            onDismiss = { showSelectionMore = false },
        )
    }

    if (showTextEditDialog) {
        TextEditDialog(
            initialText = selectedText,
            onDismiss = { showTextEditDialog = false },
            onConfirm = { replacement ->
                // Visual overlay only: the view model writes one COVER_TEXT annotation,
                // clears the selection and leaves the source PDF bytes untouched.
                vm.replaceSelectedText(replacement)
                selectedWords = emptyList()
                showTextEditDialog = false
            },
        )
    }

    textTarget?.let { point ->
        TextAnnotationDialog(
            point = point,
            initialText = "",
            onDismiss = { textTarget = null },
            onConfirm = { text ->
                vm.addText(point, text)
                textTarget = null
            },
        )
    }

    stampTarget?.let { point ->
        StampDialog(
            initialColor = state.color,
            onDismiss = { stampTarget = null },
            onConfirm = { text, color ->
                vm.setColor(color)
                vm.addStamp(point, text)
                stampTarget = null
            },
        )
    }

    signatureTarget?.let { point ->
        SignatureDialog(
            initialColor = state.color,
            onDismiss = { signatureTarget = null },
            onConfirm = { text, color ->
                vm.setColor(color)
                vm.addSignature(point, text)
                signatureTarget = null
            },
        )
    }
}

@Composable
internal fun PdfSaveStatusRow(
    status: PdfSaveStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var savedAgeMs by remember(status) { mutableStateOf(0L) }
    LaunchedEffect(status) {
        if (status == PdfSaveStatus.SAVED) {
            while (savedAgeMs < PDF_SAVED_STATUS_VISIBLE_MS) {
                delay(100L)
                savedAgeMs += 100L
            }
        }
    }
    if (!pdfSaveStatusVisible(status, savedAgeMs)) return
    val label = pdfSaveStatusLabel(status) ?: return
    val failed = pdfSaveRetryVisible(status)
    Surface(
        color = if (failed) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp)
                .padding(start = 12.dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (status) {
                PdfSaveStatus.SAVING -> CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                )
                PdfSaveStatus.FAILED -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                PdfSaveStatus.SAVED -> Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                PdfSaveStatus.IDLE -> Unit
            }
            Spacer(Modifier.width(7.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (failed) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag(PDF_SAVE_STATUS_TAG)
                    .semantics {
                        contentDescription = label
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            if (failed) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = MIN_TARGET_DP.dp)
                        .testTag(PDF_RETRY_SAVE_TAG),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text("다시 시도", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun TextSelectionActionBar(
    text: String,
    pageIndex: Int,
    capture: PdfStudyCapture?,
    hasSelection: Boolean,
    loading: Boolean,
    hasWords: Boolean,
    wordCount: Int,
    onCopy: () -> Unit,
    onExcerpt: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikeout: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val empty = !loading && !hasWords
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = if (empty) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.TextSnippet,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (empty) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (hasSelection) {
                            Modifier.studyNoteDragSource(capture)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (hasSelection) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.DragIndicator,
                            contentDescription = "스터디 노트로 끌기",
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Text(
                    when {
                        hasSelection -> text
                        loading -> "텍스트 위치를 읽는 중…"
                        empty -> "이 페이지에는 선택할 수 있는 텍스트가 없습니다."
                        else -> "단어를 탭하거나 드래그해 선택하세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            hasSelection -> "${text.length}자 · 선택한 텍스트"
                            loading -> "텍스트 선택 준비 중"
                            empty -> "텍스트 선택 모드 · 종료할 수 있습니다"
                            else -> "텍스트 선택 중 · 단어 ${wordCount}개"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (empty) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (hasSelection) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Outlined.DragIndicator,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "길게 눌러 끌기",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = if (hasSelection) "선택 해제" else "텍스트 선택 종료",
                )
            }
        }
        if (empty) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "스캔 이미지 PDF는 텍스트가 없을 수 있습니다. 계속 읽으려면 종료하세요.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(
                        minWidth = MIN_TARGET_DP.dp,
                        minHeight = MIN_TARGET_DP.dp,
                    ),
                ) {
                    Text("텍스트 선택 종료")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextSelectionPrimaryAction.entries.forEach { action ->
                val enabled = action == TextSelectionPrimaryAction.MORE || hasSelection
                SelectionAction(
                    action = action,
                    enabled = enabled,
                    // A disabled markup action still speaks what it is and why it
                    // cannot be used, instead of being an unlabelled grey tile.
                    description = if (enabled) {
                        action.contentDescription
                    } else {
                        disabledReasonLabel(
                            action.contentDescription,
                            PDF_SELECTION_REQUIRED_REASON,
                        )
                    },
                    onClick = when (action) {
                        TextSelectionPrimaryAction.HIGHLIGHT -> onHighlight
                        TextSelectionPrimaryAction.UNDERLINE -> onUnderline
                        TextSelectionPrimaryAction.STRIKEOUT -> onStrikeout
                        TextSelectionPrimaryAction.COPY -> onCopy
                        TextSelectionPrimaryAction.EXCERPT -> onExcerpt
                        TextSelectionPrimaryAction.MORE -> onMore
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SelectionAction(
    action: TextSelectionPrimaryAction,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .sizeIn(
                minWidth = MIN_TARGET_DP.dp,
                minHeight = MIN_TARGET_DP.dp,
            )
            .semantics { contentDescription = description },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                textSelectionPrimaryIcon(action),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun copySelection(context: Context, text: String) {
    val value = text.trim()
    if (value.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "클립보드를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("선택한 텍스트", value))
    Toast.makeText(context, "선택한 텍스트를 복사했습니다.", Toast.LENGTH_SHORT).show()
}

/** Compact lock badge with an immediate unlock button, drawn over the PDF canvas. */
@Composable
private fun PageLockBadge(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = "페이지 잠김",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text("페이지 잠금", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onUnlock,
                modifier = Modifier.sizeIn(
                    minWidth = MIN_TARGET_DP.dp,
                    minHeight = MIN_TARGET_DP.dp,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("해제", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Persistent reminder that annotation hiding is a canvas display mode. The whole
 * banner is a one-tap restore target; tapping it never changes stored data.
 */
@Composable
private fun HiddenAnnotationsBanner(onShow: () -> Unit) {
    Surface(
        onClick = onShow,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 5.dp, bottom = 5.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.VisibilityOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("주석 숨김", style = MaterialTheme.typography.labelMedium)
                Text(
                    "화면에서만 가림 · 데이터 유지",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "표시",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Session-only, one-time coach for the first PDF open. Three steps in the order
 * students actually work: open the PDF, mark it up, capture it into a note. It is
 * a small self-dismissing card — never a modal or a full-screen pointer sink — so
 * every page gesture outside the card keeps its existing behaviour.
 */
@Composable
private fun PdfCoachCard(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onDismiss,
        modifier = modifier
            .sizeIn(minHeight = MIN_TARGET_DP.dp)
            .semantics { contentDescription = PDF_COACH_DISMISS_DESCRIPTION },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "① 열기 ② 표시 ③ 노트에 담기",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                )
                Text(
                    "탭해서 닫기",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** TalkBack label for the one-time coach; also used by Compose tests. */
internal const val PDF_COACH_DISMISS_DESCRIPTION = "첫 PDF 안내, 탭하여 닫기"

/**
 * Single compact, translucent first-use hint over the page. It carries only a
 * dismiss control and — for scanned/empty pages — a direct local OCR shortcut;
 * all other PDF tools stay reachable through the unchanged dock/hub.
 */
@Composable
private fun PdfContextHintChip(
    text: String,
    loading: Boolean,
    ocrAction: Boolean,
    subtle: Boolean,
    onOcr: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (subtle) 0.80f else 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(start = 12.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ocrAction) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = onOcr,
                    modifier = Modifier.sizeIn(
                        minWidth = MIN_TARGET_DP.dp,
                        minHeight = MIN_TARGET_DP.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(PDF_HINT_OCR_ACTION, style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(
                    minWidth = MIN_TARGET_DP.dp,
                    minHeight = MIN_TARGET_DP.dp,
                ),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "힌트 닫기",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun BusyOverlay(label: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.16f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(label ?: stringResource(R.string.rendering_page), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Compact result-stepping chip; only composed while a query has real hits. */
@Composable
private fun SearchNextChip(
    current: Int,
    total: Int,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onNext,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.search_result_counter, current + 1, total),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = stringResource(R.string.search_next_result),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
