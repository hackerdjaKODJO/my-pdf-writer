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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Highlight
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
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.R
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
    val state = stateFlow.value ?: return

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
    var showToolMore by remember { mutableStateOf(false) }
    var showToolStyle by remember { mutableStateOf(false) }
    var showSplitTools by remember { mutableStateOf(false) }
    var showSplitSelectionMore by remember { mutableStateOf(false) }
    var showTextEditDialog by remember { mutableStateOf(false) }
    // Display-only: annotations stay stored and exported; this only hides the canvas layer.
    var annotationsHidden by remember(state.docId) { mutableStateOf(false) }
    var lastShapeTool by remember { mutableStateOf(Tool.RECT) }
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

    /** Remember the last shape so the primary "도형" slot stays a one-tap action. */
    val selectTool: (Tool) -> Unit = { tool ->
        if (isShapeTool(tool)) lastShapeTool = tool
        vm.setTool(tool)
    }

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
        showStyle || showDisplaySettings || showHub || showToolMore || showToolStyle ||
        showSplitTools || showSplitSelectionMore || showTextEditDialog ||
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
    val studyNoteAction = PdfHubAction("스터디 노트", Icons.Outlined.EditNote) {
        showHub = false
        openNote()
    }
    val hubSections = listOf(
        PdfHubSection(
            title = "읽기",
            actions = listOf(
                PdfHubAction("검색", Icons.Outlined.Search, primary = true) { showSearch = true },
                PdfHubAction("목차", Icons.AutoMirrored.Outlined.Toc, primary = true) {
                    vm.loadOutline()
                    showOutline = true
                },
                PdfHubAction(
                    "북마크",
                    Icons.Outlined.StarBorder,
                    trailing = if (bookmarks.isEmpty()) null else "${bookmarks.size}개",
                    primary = true,
                ) { showBookmarks = true },
                PdfHubAction(
                    "텍스트 선택",
                    Icons.AutoMirrored.Outlined.TextSnippet,
                    trailing = if (textSelectMode) "켜짐" else null,
                    onClick = toggleTextSelection,
                ),
                PdfHubAction("텍스트 모드 (Reflow)", Icons.Outlined.AutoStories) { showReflow = true },
                PdfHubAction("연속 보기", Icons.Outlined.ViewAgenda) { showContinuous = true },
                PdfHubAction("스프레드 보기", Icons.AutoMirrored.Outlined.MenuBook) { showSpread = true },
                PdfHubAction("팝업 보기", Icons.Outlined.PictureInPictureAlt) { showPopup = true },
                PdfHubAction("소리 내어 읽기", Icons.Outlined.RecordVoiceOver) { showReadAloud = true },
                PdfHubAction(
                    "화면 설정",
                    Icons.Outlined.DisplaySettings,
                    trailing = if (state.nightMode) "야간" else "주간",
                    primary = true,
                ) { showDisplaySettings = true },
                PdfHubAction(
                    if (state.pageLocked) "페이지 잠금 해제" else "페이지 잠금",
                    if (state.pageLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    trailing = if (state.pageLocked) "켜짐" else null,
                ) { vm.togglePageLock() },
                PdfHubAction("텍스트 복사", Icons.Outlined.ContentCopy) { vm.copyPageText() },
                PdfHubAction(
                    "화면에 맞추기",
                    Icons.Outlined.FitScreen,
                    trailing = "${(state.zoom * 100).roundToInt()}%",
                ) { vm.setZoom(1f) },
            ),
        ),
        PdfHubSection(
            title = "문서",
            actions = listOfNotNull(
                studyNoteAction,
                PdfHubAction("페이지 관리", Icons.Outlined.GridView, primary = true) {
                    selectedPages = emptySet()
                    showOrganize = true
                },
                PdfHubAction("페이지 편집", Icons.Outlined.EditNote) { showPages = true },
                PdfHubAction(
                    "페이지 메모",
                    Icons.AutoMirrored.Outlined.StickyNote2,
                    trailing = if (state.notes.isEmpty()) null else "${state.notes.size}개",
                ) { showNotes = true },
                PdfHubAction(
                    "주석 목록",
                    Icons.AutoMirrored.Outlined.Comment,
                    trailing = if (state.annotations.isEmpty()) null else "${state.annotations.size}개",
                    primary = true,
                ) { showAnnotations = true },
                PdfHubAction(
                    if (annotationsHidden) "주석 표시" else "주석 숨기기",
                    if (annotationsHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    trailing = if (annotationsHidden) "숨김" else "표시 중",
                ) { annotationsHidden = !annotationsHidden },
                PdfHubAction(
                    "발췌·번역",
                    Icons.AutoMirrored.Outlined.Notes,
                    trailing = if (excerpts.isEmpty()) null else "${excerpts.size}개",
                ) { showExcerpts = true },
                PdfHubAction("오디오 노트", Icons.Outlined.Mic) { showAudioNotes = true },
                PdfHubAction("오디오 재생", Icons.Outlined.PlayCircle) { showAudioReplay = true },
                PdfHubAction("문서 정보", Icons.Outlined.Info) {
                    vm.loadMetadata()
                    showProperties = true
                },
                PdfHubAction("문서 꾸미기", Icons.Outlined.Palette) { showStyle = true },
                PdfHubAction("PDF 도구", Icons.Outlined.Handyman) { onOpenTools() },
                onOpenHelp?.let {
                    PdfHubAction("도움말", Icons.AutoMirrored.Outlined.HelpOutline) {
                        showHub = false
                        it()
                    }
                },
            ),
        ),
        PdfHubSection(
            title = "내보내기",
            actions = listOf(
                PdfHubAction("PDF 내보내기", Icons.Outlined.FileDownload, primary = true) {
                    onExportRequest(vm.exportSuggestedName())
                },
                PdfHubAction("인쇄", Icons.Outlined.Print) { vm.printCurrent() },
                PdfHubAction("공유", Icons.Outlined.Share) { vm.shareCurrent() },
            ),
        ),
    )

    // Split/focus chrome: with the study pane open no app bar or dock row may
    // consume height. All PDF controls become floating overlays over the strip.
    val splitMode = studyPane != null
    val chromePolicy = pdfChromePolicy(
        studyPaneOpen = splitMode,
        textSelectMode = textSelectMode,
        hasSelection = selectedWords.isNotEmpty(),
    )

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
                        // Direct note entry, visible as soon as a PDF opens. The host
                        // resolves or creates the study notebook behind this callback.
                        TextButton(
                            onClick = openNote,
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                Icons.Outlined.EditNote,
                                contentDescription = "스터디 노트",
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
                                onTranslate = { TextActions.translate(context, selectedText) },
                                onDictionary = { TextActions.define(context, selectedText) },
                                onExcerpt = { vm.addExcerptFromSelection(state.pageIndex, selectedText) },
                                onAddToPane = if (studyPane != null) {
                                    { appendStudyExcerpt(selectedText, state.pageIndex) }
                                } else {
                                    null
                                },
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
                                onEditText = { showTextEditDialog = true },
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
                                shapeTool = lastShapeTool,
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
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
            val latestFraction = rememberUpdatedState(paneFraction)
            val latestHeight = rememberUpdatedState(splitAreaHeightPx)
            val latestOnFraction = rememberUpdatedState(onStudyPaneFractionChange)
            var dividerDragStart by remember { mutableFloatStateOf(0f) }
            var dividerDragY by remember { mutableFloatStateOf(0f) }
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

                    // The one hint chip is an overlay: it never adds layout height
                    // and its top-center placement stays clear of the floating
                    // page controls and the top-end search stepping chip.
                    Column(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
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
                                .padding(end = 10.dp, top = 10.dp),
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
                        if (chromePolicy.showSplitToolbar) {
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
                                onOpenTools = { showSplitTools = true },
                                onOpenHub = { showHub = true },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp),
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
                                onDictionary = { TextActions.define(context, selectedText) },
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
                                onEditText = { showTextEditDialog = true },
                                onMore = { showSplitSelectionMore = true },
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
                    // The visible boundary is the primary resize affordance:
                    // drag it up for more note, down for more PDF. The floating
                    // pane header still keeps its own drag handle.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dividerDragStart = latestFraction.value
                                        dividerDragY = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dividerDragY += dragAmount
                                        latestOnFraction.value(
                                            studyPaneFractionAfterDrag(
                                                startFraction = dividerDragStart,
                                                dragY = dividerDragY,
                                                totalHeightPx = latestHeight.value,
                                            ),
                                        )
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 2.dp,
                            shadowElevation = 2.dp,
                        ) {
                            Icon(
                                Icons.Outlined.DragIndicator,
                                contentDescription = "PDF와 노트 높이 조절",
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(16.dp)
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
    if (showSplitTools) {
        SplitPdfToolsSheet(
            currentTool = state.tool,
            textSelectMode = textSelectMode,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onSelectTool = selectTool,
            onTextSelect = {
                showSplitTools = false
                toggleTextSelection()
            },
            onUndo = vm::undo,
            onRedo = vm::redo,
            onStyle = {
                showSplitTools = false
                showToolStyle = true
            },
            onDismiss = { showSplitTools = false },
        )
    }
    if (showSplitSelectionMore) {
        SplitSelectionMoreSheet(
            selectedText = selectedText,
            pageIndex = state.pageIndex,
            hasSelection = selectedWords.isNotEmpty(),
            canAddToPane = studyPane != null,
            onTranslate = { TextActions.translate(context, selectedText) },
            onDictionary = { TextActions.define(context, selectedText) },
            onExcerpt = { vm.addExcerptFromSelection(state.pageIndex, selectedText) },
            onEditText = { showTextEditDialog = true },
            onCopy = { copySelection(context, selectedText) },
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
            onDismiss = { showSplitSelectionMore = false },
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
    onTranslate: () -> Unit,
    onDictionary: () -> Unit,
    onExcerpt: () -> Unit,
    onAddToPane: (() -> Unit)?,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikeout: () -> Unit,
    onEditText: () -> Unit,
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
                TextButton(onClick = onDismiss) {
                    Text("텍스트 선택 종료")
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction("복사", Icons.Outlined.ContentCopy, hasSelection, onCopy)
            SelectionAction("Google 번역", Icons.Outlined.Translate, hasSelection, onTranslate)
            SelectionAction("사전", Icons.AutoMirrored.Outlined.MenuBook, hasSelection, onDictionary)
            SelectionAction("발췌", Icons.AutoMirrored.Outlined.Notes, hasSelection, onExcerpt)
            if (onAddToPane != null) {
                SelectionAction("패널에 추가", Icons.Outlined.EditNote, hasSelection, onAddToPane)
            }
            SelectionAction("형광펜", Icons.Outlined.Highlight, hasSelection, onHighlight)
            SelectionAction("밑줄", Icons.Outlined.FormatUnderlined, hasSelection, onUnderline)
            SelectionAction("취소선", Icons.Outlined.FormatStrikethrough, hasSelection, onStrikeout)
            SelectionAction("텍스트 수정", Icons.Outlined.Edit, hasSelection, onEditText)
            SelectionAction("선택 해제", Icons.Outlined.Close, hasSelection, onDismiss)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SelectionAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
            TextButton(onClick = onUnlock, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(PDF_HINT_OCR_ACTION, style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
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
