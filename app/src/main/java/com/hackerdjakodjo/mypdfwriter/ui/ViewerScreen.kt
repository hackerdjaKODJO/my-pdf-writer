package com.hackerdjakodjo.mypdfwriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.EditNote
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.PenBoxStore
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
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
import com.hackerdjakodjo.mypdfwriter.pdf.TextActions
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    vm: PdfViewModel,
    onExportRequest: (String) -> Unit,
    onOpenTools: () -> Unit,
    onCreateStudyNote: (Notebook) -> Unit = {},
    /** Optional integration hook used by drag-and-drop capture; must not open the notebook. */
    onAppendStudyExcerpt: (text: String, pageIndex: Int) -> Unit = { _, _ -> },
    /**
     * Optional hub entry point for the live study-note pane. When present, the
     * PDF hub offers "스터디 노트"; the host resolves or creates the notebook
     * without closing the PDF.
     */
    onOpenStudyNote: ((StudySource) -> Unit)? = null,
    /** Optional help hook. When present, the PDF hub offers a "도움말" action. */
    onOpenHelp: (() -> Unit)? = null,
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
) {
    val stateFlow = vm.viewer.collectAsStateWithLifecycle()
    val bitmap by vm.pageBitmap.collectAsStateWithLifecycle()
    val outline by vm.outline.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val excerpts by vm.excerpts.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val pageTextWords by vm.textWords.collectAsStateWithLifecycle()
    val pageLinks by vm.pageLinks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val penBoxStore = remember(context) { PenBoxStore(context) }
    var viewerFavorites by remember(penBoxStore) { mutableStateOf(penBoxStore.loadFavorites()) }
    var showViewerPenBox by remember { mutableStateOf(false) }
    var viewerPenStyle by remember { mutableStateOf(PenStyle.BALLPOINT) }

    fun refreshViewerFavorites() {
        viewerFavorites = penBoxStore.loadFavorites()
    }

    fun applyViewerFavorite(preset: PenPreset) {
        vm.setTool(preset.tool)
        vm.setColor(preset.color)
        vm.setStrokeWidth(preset.width)
        vm.setOpacity(preset.opacity)
        viewerPenStyle = preset.penStyle
    }

    val state = stateFlow.value ?: return

    fun currentViewerPen(): PenPreset = PenPreset(
        id = "current-pen",
        name = "현재 펜",
        color = state.color,
        width = state.strokeWidth,
        opacity = state.opacity,
        penStyle = viewerPenStyle,
        tool = if (state.tool == Tool.HIGHLIGHT) Tool.HIGHLIGHT else Tool.PEN,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val appendStudyExcerpt: (String, Int) -> Unit = { text, page ->
        onAppendStudyExcerpt(text, page)
        scope.launch { snackbarHostState.showSnackbar(STUDY_NOTE_ADDED_MESSAGE) }
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
    var showOutline by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showHub by remember { mutableStateOf(false) }
    var showReadModes by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showToolMore by remember { mutableStateOf(false) }
    var showToolStyle by remember { mutableStateOf(false) }
    var showSelectionMore by remember { mutableStateOf(false) }
    var showTextEditDialog by remember { mutableStateOf(false) }
    // Display-only: annotations stay stored and exported; this only hides the canvas layer.
    var annotationsHidden by remember(state.docId) { mutableStateOf(false) }
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
    var pdfToolsVisible by remember { mutableStateOf(true) }
    val selectedText = remember(selectedWords) { PdfTextLayout.selectionText(selectedWords) }

    // The replacement action lives in the view model, so mirror the local canvas
    // selection into it. This is transient UI state, never persisted or snapshotted.
    LaunchedEffect(selectedWords) { vm.setSelectedWords(selectedWords) }

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
    LaunchedEffect(state.searchQuery, state.pageIndex, state.revision) {
        val query = state.searchQuery.trim()
        if (query.isEmpty()) {
            searchHighlights = emptyList()
            return@LaunchedEffect
        }
        val file = vm.currentWorkingFile()
        if (file == null) {
            searchHighlights = emptyList()
            return@LaunchedEffect
        }
        val lines = runCatching { PdfTextLayout.lines(file) }.getOrDefault(emptyList())
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

    val sheetOpen = showPages || showNotes || showBookmarks || showSearch || showAnnotations ||
        showOrganize || showExcerpts || showContinuous || showSpread || showPopup || showAudioNotes ||
        showAudioReplay || showStudyNote || showReflow || showReadAloud || showOutline || showProperties ||
        showStyle || showDisplaySettings || showHub || showReadModes || showAudioMenu || showToolMore ||
        showToolStyle || showSelectionMore || showTextEditDialog ||
        textTarget != null || stampTarget != null || signatureTarget != null

    BackHandler(enabled = !sheetOpen) {
        if (textSelectMode || selectedWords.isNotEmpty()) {
            textSelectMode = false
            selectedWords = emptyList()
        } else {
            vm.closeDocument()
        }
    }

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
            loadPage = { index, zoom -> vm.loadReaderPage(index, zoom) },
            onBack = { showContinuous = false },
            onOpenPage = { index ->
                vm.goToPage(index)
                showContinuous = false
            },
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
        )
        return
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.clearMessage()
    }

    val progress = if (state.pageCount > 0) {
        (state.pageIndex + 1).toFloat() / state.pageCount
    } else {
        0f
    }
    val progressPercent = (progress * 100).roundToInt()
    val currentBookmark = bookmarks.firstOrNull { it.pageIndex == state.pageIndex }

    val openNote: () -> Unit = {
        val callback = onOpenStudyNote
        if (callback != null) {
            callback(StudySource(state.docName, vm.currentDocumentUri()))
        } else {
            showStudyNote = true
        }
    }
    fun hubAction(action: PdfMenuAction): PdfHubAction = when (action) {
        PdfMenuAction.SEARCH -> PdfHubAction(action.label, Icons.Outlined.Search) {
            showSearch = true
        }

        PdfMenuAction.OUTLINE -> PdfHubAction(action.label, Icons.AutoMirrored.Outlined.Toc) {
            vm.loadOutline()
            showOutline = true
        }

        PdfMenuAction.BOOKMARKS -> PdfHubAction(
            action.label,
            Icons.Outlined.StarBorder,
            trailing = if (bookmarks.isEmpty()) null else "${bookmarks.size}개",
        ) { showBookmarks = true }

        PdfMenuAction.PAGE_NOTES -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.StickyNote2,
            trailing = if (state.notes.isEmpty()) null else "${state.notes.size}개",
        ) { showNotes = true }

        PdfMenuAction.EXCERPTS -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.Notes,
            trailing = if (excerpts.isEmpty()) null else "${excerpts.size}개",
        ) { showExcerpts = true }

        PdfMenuAction.TEXT_SELECT -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.TextSnippet,
            trailing = if (textSelectMode) "켜짐" else null,
            onClick = toggleTextSelection,
        )

        PdfMenuAction.STUDY_NOTE -> PdfHubAction(action.label, Icons.Outlined.EditNote) {
            openNote()
        }

        PdfMenuAction.PAGE_MANAGE -> PdfHubAction(action.label, Icons.Outlined.GridView) {
            selectedPages = emptySet()
            showOrganize = true
        }

        PdfMenuAction.PAGE_EDIT -> PdfHubAction(action.label, Icons.Outlined.EditNote) {
            showPages = true
        }

        PdfMenuAction.ANNOTATION_LIST -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.Comment,
            trailing = if (state.annotations.isEmpty()) null else "${state.annotations.size}개",
        ) { showAnnotations = true }

        PdfMenuAction.TOGGLE_ANNOTATIONS -> PdfHubAction(
            pdfMenuActionLabel(action, annotationsHidden = annotationsHidden),
            if (annotationsHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            trailing = if (annotationsHidden) "숨김" else "표시 중",
        ) { annotationsHidden = !annotationsHidden }

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

        PdfMenuAction.EXPORT -> PdfHubAction(action.label, Icons.Outlined.FileDownload) {
            onExportRequest(vm.exportSuggestedName())
        }

        PdfMenuAction.PRINT -> PdfHubAction(action.label, Icons.Outlined.Print) {
            vm.printCurrent()
        }

        PdfMenuAction.SHARE -> PdfHubAction(action.label, Icons.Outlined.Share) {
            vm.shareCurrent()
        }

        PdfMenuAction.PAGE_LOCK -> PdfHubAction(
            pdfMenuActionLabel(action, pageLocked = state.pageLocked),
            if (state.pageLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
            trailing = if (state.pageLocked) "켜짐" else null,
        ) { vm.togglePageLock() }

        PdfMenuAction.COPY_TEXT -> PdfHubAction(action.label, Icons.Outlined.ContentCopy) {
            vm.copyPageText()
        }

        PdfMenuAction.FIT_SCREEN -> PdfHubAction(
            action.label,
            Icons.Outlined.FitScreen,
            trailing = "${(state.zoom * 100).roundToInt()}%",
        ) { vm.setZoom(1f) }

        PdfMenuAction.DOCUMENT_INFO -> PdfHubAction(action.label, Icons.Outlined.Info) {
            vm.loadMetadata()
            showProperties = true
        }

        PdfMenuAction.DECORATE -> PdfHubAction(action.label, Icons.Outlined.Palette) {
            showStyle = true
        }

        PdfMenuAction.PDF_TOOLS -> PdfHubAction(action.label, Icons.Outlined.Handyman) {
            onOpenTools()
        }

        PdfMenuAction.HELP -> PdfHubAction(
            action.label,
            Icons.AutoMirrored.Outlined.HelpOutline,
            enabled = onOpenHelp != null,
        ) { onOpenHelp?.invoke() }

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

    // Split/focus chrome: with the study pane open no app bar or dock row may
    // consume height. All PDF controls become floating overlays over the strip.
    val splitMode = studyPane != null
    val chromePolicy = pdfChromePolicy(
        studyPaneOpen = splitMode,
        textSelectMode = textSelectMode,
        hasSelection = selectedWords.isNotEmpty(),
    )
    // The pane share decides how much of the floating PDF strip fits. On a very
    // short strip only the page pill remains; the back button stays top-start.
    val pdfChromeDensity = if (splitMode) {
        splitPdfChromeDensity(clampStudyPaneFraction(studyPaneFraction))
    } else {
        SplitPdfChromeDensity.FULL
    }
    val splitToolbarVisible = splitMode && chromePolicy.showSplitToolbar && pdfToolsVisible
    // Hint and search chips share the top edge with the toolbar, so they sit in
    // the next row whenever the strip is drawn.
    val splitTopOverlayOffsetDp = if (splitToolbarVisible) 52 else 10

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
        topBar = {
            if (chromePolicy.showTopAppBar) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                state.docName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.sectionHeading(),
                            )
                            Text(
                                "${stringResource(R.string.page_of, state.pageIndex + 1, state.pageCount)} · 위치 $progressPercent%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = vm::closeDocument) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        // One-tap search: the sheet is also reachable from the
                        // grouped PDF menu, but search is common enough to keep
                        // directly beside the document title.
                        IconButton(onClick = { showSearch = true }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "검색",
                            )
                        }
                        // Direct note entry, visible as soon as a PDF opens. The host
                        // resolves or creates the study notebook behind this callback.
                        TextButton(
                            onClick = openNote,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = "노트 열기",
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("노트", style = MaterialTheme.typography.labelLarge)
                        }
                        IconButton(
                            onClick = {
                                if (currentBookmark != null) {
                                    vm.deleteBookmark(currentBookmark.id)
                                } else {
                                    vm.addCurrentPageBookmark("${state.pageIndex + 1}쪽")
                                }
                            },
                        ) {
                            Icon(
                                if (currentBookmark != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (currentBookmark != null) {
                                    "현재 페이지 북마크 해제"
                                } else {
                                    "현재 페이지 북마크"
                                },
                                tint = if (currentBookmark != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { showHub = true }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "PDF 메뉴")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        bottomBar = {
            if (chromePolicy.showBottomBar) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        if (textSelectMode) {
                            TextSelectionActionBar(
                                text = selectedText,
                                pageIndex = state.pageIndex,
                                hasSelection = selectedWords.isNotEmpty(),
                                loading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                wordCount = pageTextWords.size,
                                onCopy = { copySelection(context, selectedText) },
                                onExcerpt = { vm.addExcerptFromSelection(state.pageIndex, selectedText) },
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
                                onTextSelect = toggleTextSelection,
                                onMore = { showToolMore = true },
                                onStyle = { showToolStyle = true },
                                onUndo = vm::undo,
                                onRedo = vm::redo,
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
                    paneTitle = if (splitMode) "PDF 분할 보기" else "PDF 보기"
                },
        ) {
            // Split mode removes the app bar and bottom dock from layout, so the
            // PDF starts at the top safe edge and the pane gets the full rest of
            // the screen. PDF controls float inside the strip instead.
            val paneFraction = if (studyPane != null) {
                clampStudyPaneFraction(studyPaneFraction)
            } else {
                0f
            }
            var splitAreaHeightPx by remember { mutableFloatStateOf(0f) }
            val showSearchNavigation = !state.searching && state.searchQuery.trim().isNotEmpty() &&
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
                    PdfCanvas(
                        bitmap = bitmap,
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
                        links = pageLinks,
                        textWords = pageTextWords,
                        textSelectMode = textSelectMode,
                        selectedWords = selectedWords,
                        annotationProgress = { annotation ->
                            if (replayNote != null && replayNote?.pageIndex != state.pageIndex) 1f
                            else annotationRevealFraction(annotation, replayNote, replayPositionMs)
                        },
                        onZoomChange = vm::setZoom,
                        onAddStroke = vm::addStroke,
                        onEraseStroke = vm::eraseStroke,
                        onRequestText = { textTarget = it },
                        onRequestStamp = { stampTarget = it },
                        onRequestSignature = { signatureTarget = it },
                        onTextSelection = { selectedWords = it },
                        onRequestSelectionAt = requestSelectionAt,
                        onLinkTapped = handleLinkTap,
                    )

                    // Small, always-visible lock affordance. Bottom-start keeps it
                    // clear of the bottom-center page pill and the top hint/search chips.
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
                        if (annotationsHidden) {
                            HiddenAnnotationsBanner(onShow = { annotationsHidden = false })
                            if (showHintChip) Spacer(Modifier.height(6.dp))
                        }
                        if (showHintChip) {
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
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 74.dp),
                        )
                    }

                    if (splitMode) {
                        SplitPdfBackButton(
                            onBack = vm::closeDocument,
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
                                pageIndex = state.pageIndex,
                                pageCount = state.pageCount,
                                bookmarked = currentBookmark != null,
                                textSelectMode = textSelectMode,
                                textLoading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                onPrevious = vm::previousPage,
                                onNext = vm::nextPage,
                                onOpenPages = { showPages = true },
                                onToggleTextSelection = toggleTextSelection,
                                onToggleBookmark = {
                                    if (currentBookmark != null) {
                                        vm.deleteBookmark(currentBookmark.id)
                                    } else {
                                        vm.addCurrentPageBookmark("${state.pageIndex + 1}쪽")
                                    }
                                },
                                onOpenTools = { showToolMore = true },
                                onOpenHub = { showHub = true },
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
                                hasSelection = selectedWords.isNotEmpty(),
                                loading = state.textLoading,
                                hasWords = pageTextWords.isNotEmpty(),
                                wordCount = pageTextWords.size,
                                onCopy = { copySelection(context, selectedText) },
                                onExcerpt = { vm.addExcerptFromSelection(state.pageIndex, selectedText) },
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
                    } else {
                        ViewerPageNavigator(
                            pageIndex = state.pageIndex,
                            pageCount = state.pageCount,
                            onPrevious = vm::previousPage,
                            onNext = vm::nextPage,
                            onOpenPages = { showPages = true },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 14.dp),
                        )
                    }

                    if (state.busy) {
                        BusyOverlay(state.busyLabel)
                    }
                }

                if (studyPane != null) {
                    // The divider is the primary resize affordance: a 28dp row
                    // with a centered grip/percentage pill and a PDF-tool chip.
                    // The floating pane header keeps its own drag handle and owns
                    // the dismiss overshoot, exactly as before.
                    SplitDivider(
                        paneFraction = paneFraction,
                        totalHeightPx = splitAreaHeightPx,
                        pdfToolsVisible = pdfToolsVisible,
                        onFractionChange = onStudyPaneFractionChange,
                        onPresetSelected = { fraction ->
                            onStudyPaneFractionChange(clampStudyPaneFraction(fraction))
                        },
                        onTogglePdfTools = { pdfToolsVisible = !pdfToolsVisible },
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
            onDropExcerpt = { excerpt ->
                onAppendStudyExcerpt(excerpt.text, excerpt.pageIndex)
                Toast.makeText(context, STUDY_NOTE_ADDED_MESSAGE, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showExcerpts = false },
        )
    }
    if (showStudyNote) {
        StudyNoteSheet(
            source = StudySource(state.docName, vm.currentDocumentUri()),
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
    if (showOutline) {
        OutlineSheet(
            entries = outline.orEmpty(),
            onSelect = { page ->
                vm.goToPage(page)
                showOutline = false
            },
            onDismiss = { showOutline = false },
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
    if (showHub) {
        PdfHubSheet(
            docName = state.docName,
            pageIndex = state.pageIndex,
            pageCount = state.pageCount,
            sections = hubSections,
            onDismiss = { showHub = false },
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
            presets = penBoxStore.load(),
            current = currentViewerPen(),
            onDismiss = { showViewerPenBox = false },
            onApply = { applyViewerFavorite(it) },
            onSaveCurrent = { name ->
                val saved = currentViewerPen().copy(
                    id = UUID.randomUUID().toString(),
                    name = name.trim().ifBlank { "새 펜" },
                )
                penBoxStore.save(penBoxStore.load() + saved)
                refreshViewerFavorites()
            },
            onDelete = { preset ->
                val presets = penBoxStore.load()
                if (presets.size > 1) {
                    penBoxStore.save(presets.filterNot { it.id == preset.id })
                    refreshViewerFavorites()
                }
            },
            onRename = { preset, name ->
                val clean = name.trim().ifBlank { preset.name }
                penBoxStore.save(
                    penBoxStore.load().map { if (it.id == preset.id) it.copy(name = clean) else it },
                )
                refreshViewerFavorites()
            },
            favorites = viewerFavorites,
            onToggleFavorite = { preset ->
                penBoxStore.toggleFavorite(preset.id)
                refreshViewerFavorites()
            },
            onMoveFavorite = { from, to ->
                penBoxStore.moveFavorite(from, to)
                refreshViewerFavorites()
            },
        )
    }

    if (showToolStyle) {
        ViewerStyleSheet(
            state = state,
            onSelectTool = selectTool,
            onColor = vm::setColor,
            onStrokeWidth = vm::setStrokeWidth,
            onFontSize = vm::setFontSize,
            onOpacity = vm::setOpacity,
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
            onEditText = { showTextEditDialog = true },
            onShare = { TextActions.shareText(context, selectedText) },
            onAddToPane = if (studyPane != null) {
                { appendStudyExcerpt(selectedText, state.pageIndex) }
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
private fun TextSelectionActionBar(
    text: String,
    pageIndex: Int,
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
                            Modifier.studyNoteDragSource(text, pageIndex)
                        } else {
                            Modifier
                        },
                    ),
            ) {
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
