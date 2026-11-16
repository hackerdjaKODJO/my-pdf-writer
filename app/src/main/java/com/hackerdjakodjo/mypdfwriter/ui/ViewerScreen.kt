package com.hackerdjakodjo.mypdfwriter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLayout
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import com.hackerdjakodjo.mypdfwriter.pdf.TextActions
import kotlin.math.roundToInt

private data class ToolSpec(val tool: Tool, val icon: ImageVector, val labelRes: Int)

private val toolSpecs = listOf(
    ToolSpec(Tool.VIEW, Icons.Outlined.Visibility, R.string.tool_view),
    ToolSpec(Tool.PEN, Icons.Outlined.Edit, R.string.tool_pen),
    ToolSpec(Tool.HIGHLIGHT, Icons.Outlined.Highlight, R.string.tool_highlight),
    ToolSpec(Tool.TEXT, Icons.Outlined.TextFields, R.string.tool_text),
    ToolSpec(Tool.RECT, Icons.Outlined.CropSquare, R.string.tool_rect),
    ToolSpec(Tool.ARROW, Icons.Outlined.NorthEast, R.string.tool_arrow),
    ToolSpec(Tool.LINE, Icons.Outlined.HorizontalRule, R.string.tool_line),
    ToolSpec(Tool.ELLIPSE, Icons.Outlined.Circle, R.string.tool_ellipse),
    ToolSpec(Tool.UNDERLINE, Icons.Outlined.FormatUnderlined, R.string.tool_underline),
    ToolSpec(Tool.STRIKEOUT, Icons.Outlined.FormatStrikethrough, R.string.tool_strikeout),
    ToolSpec(Tool.STAMP, Icons.Outlined.Approval, R.string.tool_stamp),
    ToolSpec(Tool.SIGNATURE, Icons.Outlined.Draw, R.string.tool_signature),
    ToolSpec(Tool.REDACT, Icons.Outlined.VisibilityOff, R.string.tool_redact),
    ToolSpec(Tool.ERASER, Icons.Outlined.DeleteSweep, R.string.tool_eraser),
)

val annotationPalette = listOf(
    0xFF1B1B1B.toInt(),
    0xFFD32F2F.toInt(),
    0xFF1F4E79.toInt(),
    0xFF2E7D32.toInt(),
    0xFFC2703D.toInt(),
    0xFF6A1B9A.toInt(),
    0xFF00838F.toInt(),
    0xFFF9A825.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    vm: PdfViewModel,
    onExportRequest: (String) -> Unit,
    onOpenTools: () -> Unit,
    onCreateStudyNote: (Notebook) -> Unit = {},
) {
    val stateFlow = vm.viewer.collectAsStateWithLifecycle()
    val bitmap by vm.pageBitmap.collectAsStateWithLifecycle()
    val outline by vm.outline.collectAsStateWithLifecycle()
    val metadata by vm.metadata.collectAsStateWithLifecycle()
    val excerpts by vm.excerpts.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val pageTextWords by vm.textWords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val state = stateFlow.value ?: return

    val snackbarHostState = remember { SnackbarHostState() }
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
    var showStudyNote by remember { mutableStateOf(false) }
    var showReflow by remember { mutableStateOf(false) }
    var showReadAloud by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var textTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var stampTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var signatureTarget by remember { mutableStateOf<NormalizedPoint?>(null) }
    var textSelectMode by remember { mutableStateOf(false) }
    var selectedWords by remember { mutableStateOf<List<PdfTextWord>>(emptyList()) }
    val selectedText = remember(selectedWords) { PdfTextLayout.selectionText(selectedWords) }

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
        showStudyNote || showReflow || showReadAloud || showOutline || showProperties || showStyle || showMenu ||
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
            loadPage = { index -> vm.loadReaderPage(index) },
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

    Scaffold(
        topBar = {
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
                            "${stringResource(R.string.page_of, state.pageIndex + 1, state.pageCount)} · 읽음 $progressPercent%",
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
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.search))
                    }
                    IconButton(
                        onClick = {
                            selectedPages = emptySet()
                            showOrganize = true
                        },
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = "페이지 정리")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "더보기")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("페이지 편집") },
                                onClick = {
                                    showMenu = false
                                    showPages = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("페이지 메모") },
                                onClick = {
                                    showMenu = false
                                    showNotes = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (bookmarks.isEmpty()) "북마크"
                                        else "북마크 (${bookmarks.size})",
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.StarBorder, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showBookmarks = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentBookmark != null) "현재 페이지 북마크 해제"
                                        else "현재 페이지 북마크",
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (currentBookmark != null) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    if (currentBookmark != null) {
                                        vm.deleteBookmark(currentBookmark.id)
                                    } else {
                                        vm.addCurrentPageBookmark("${state.pageIndex + 1}쪽")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.annotations.isEmpty()) "주석 목록"
                                        else "주석 목록 (${state.annotations.size})",
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showAnnotations = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (excerpts.isEmpty()) "발췌·번역"
                                        else "발췌·번역 (${excerpts.size})",
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showExcerpts = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("텍스트 모드") },
                                onClick = {
                                    showMenu = false
                                    showReflow = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (textSelectMode) "텍스트 선택 종료" else "텍스트 선택") },
                                leadingIcon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    if (textSelectMode) {
                                        textSelectMode = false
                                        selectedWords = emptyList()
                                    } else {
                                        vm.setTool(Tool.VIEW)
                                        textSelectMode = true
                                        selectedWords = emptyList()
                                        vm.loadTextWords(state.pageIndex)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("소리 내어 읽기") },
                                onClick = {
                                    showMenu = false
                                    showReadAloud = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("연속 보기") },
                                onClick = {
                                    showMenu = false
                                    showContinuous = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("스프레드 보기") },
                                onClick = {
                                    showMenu = false
                                    showSpread = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("팝업으로 보기") },
                                onClick = {
                                    showMenu = false
                                    showPopup = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("오디오 노트") },
                                onClick = {
                                    showMenu = false
                                    showAudioNotes = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("목차") },
                                onClick = {
                                    showMenu = false
                                    vm.loadOutline()
                                    showOutline = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("문서 정보") },
                                onClick = {
                                    showMenu = false
                                    vm.loadMetadata()
                                    showProperties = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("문서 꾸미기 (워터마크·페이지 번호)") },
                                onClick = {
                                    showMenu = false
                                    showStyle = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.nightMode) "주간 모드" else "야간 모드") },
                                onClick = {
                                    showMenu = false
                                    vm.toggleNightMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("현재 페이지 텍스트 복사") },
                                onClick = {
                                    showMenu = false
                                    vm.copyPageText()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("PDF 도구") },
                                onClick = {
                                    showMenu = false
                                    onOpenTools()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("인쇄") },
                                onClick = {
                                    showMenu = false
                                    vm.printCurrent()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("공유") },
                                onClick = {
                                    showMenu = false
                                    vm.shareCurrent()
                                },
                            )
                        }
                    }
                    IconButton(onClick = { onExportRequest(vm.exportSuggestedName()) }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = stringResource(R.string.export))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
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
                            hasSelection = selectedWords.isNotEmpty(),
                            loading = state.textLoading,
                            hasWords = pageTextWords.isNotEmpty(),
                            onCopy = { copySelection(context, selectedText) },
                            onTranslate = { TextActions.translate(context, selectedText) },
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
                        AnnotationToolbar(state, vm)
                    }
                    HorizontalDivider()
                    PageBar(state, vm)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PdfCanvas(
                bitmap = bitmap,
                pageIndex = state.pageIndex,
                displayWidthPt = state.displayWidthPt,
                displayHeightPt = state.displayHeightPt,
                annotations = state.annotations,
                tool = state.tool,
                color = state.color,
                strokeWidth = state.strokeWidth,
                fontSize = state.fontSize,
                opacity = state.opacity,
                zoom = state.zoom,
                documentStyle = state.documentStyle,
                pageCount = state.pageCount,
                nightMode = state.nightMode,
                textWords = pageTextWords,
                textSelectMode = textSelectMode,
                selectedWords = selectedWords,
                onZoomChange = vm::setZoom,
                onAddStroke = vm::addStroke,
                onEraseStroke = vm::eraseStroke,
                onRequestText = { textTarget = it },
                onRequestStamp = { stampTarget = it },
                onRequestSignature = { signatureTarget = it },
                onTextSelection = { selectedWords = it },
            )

            val hintRes = when (state.tool) {
                Tool.VIEW -> R.string.hint_view
                Tool.TEXT -> R.string.hint_text
                Tool.STAMP -> R.string.hint_stamp
                Tool.SIGNATURE -> R.string.hint_signature
                Tool.REDACT -> R.string.hint_redact
                Tool.ERASER -> R.string.hint_eraser
                else -> R.string.hint_draw
            }
            val hintText = when {
                textSelectMode && state.textLoading -> "텍스트를 읽는 중…"
                textSelectMode && pageTextWords.isEmpty() -> "이 페이지에는 선택할 수 있는 텍스트가 없습니다."
                textSelectMode -> "텍스트를 드래그해 선택하세요."
                else -> stringResource(hintRes)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            ) {
                Text(
                    hintText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.busy) {
                BusyOverlay(state.busyLabel)
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
        SearchSheet(state, vm) { showSearch = false }
    }
    if (showAnnotations) {
        AnnotationSheet(state, vm) { showAnnotations = false }
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
            onShare = { text -> TextActions.shareText(context, text) },
            onStudyNote = {
                showExcerpts = false
                showStudyNote = true
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
private fun AnnotationToolbar(state: ViewerUiState, vm: PdfViewModel) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolSpecs.forEach { spec ->
                    ToolChip(
                        icon = spec.icon,
                        label = stringResource(spec.labelRes),
                        selected = state.tool == spec.tool,
                        onClick = { vm.setTool(spec.tool) },
                    )
                }
            }
            IconButton(onClick = vm::undo, enabled = state.canUndo) {
                Icon(Icons.Outlined.Undo, contentDescription = stringResource(R.string.undo))
            }
            IconButton(onClick = vm::redo, enabled = state.canRedo) {
                Icon(Icons.Outlined.Redo, contentDescription = stringResource(R.string.redo))
            }
        }

        if (state.tool != Tool.VIEW && state.tool != Tool.ERASER && state.tool != Tool.REDACT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                annotationPalette.forEach { color ->
                    ColorDot(
                        color = color,
                        selected = state.color == color,
                        onClick = { vm.setColor(color) },
                    )
                }

                when (state.tool) {
                    Tool.TEXT -> {
                        Text(stringResource(R.string.thickness), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.fontSize,
                            onValueChange = vm::setFontSize,
                            valueRange = 0.012f..0.05f,
                            modifier = Modifier.width(120.dp),
                        )
                        OpacityControl(state.opacity, vm::setOpacity)
                    }

                    Tool.HIGHLIGHT -> Unit

                    Tool.STAMP, Tool.SIGNATURE -> {
                        OpacityControl(state.opacity, vm::setOpacity)
                    }

                    else -> {
                        Text(stringResource(R.string.thickness), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.strokeWidth,
                            onValueChange = vm::setStrokeWidth,
                            valueRange = 0.001f..0.012f,
                            modifier = Modifier.width(120.dp),
                        )
                        OpacityControl(state.opacity, vm::setOpacity)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSelectionActionBar(
    text: String,
    hasSelection: Boolean,
    loading: Boolean,
    hasWords: Boolean,
    onCopy: () -> Unit,
    onTranslate: () -> Unit,
    onExcerpt: () -> Unit,
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikeout: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.TextFields,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        hasSelection -> text
                        loading -> "텍스트를 읽는 중…"
                        !hasWords -> "이 페이지에는 선택할 수 있는 텍스트가 없습니다."
                        else -> "텍스트를 드래그해 선택하세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (hasSelection) "${text.length}자 · 선택한 텍스트" else "텍스트 선택 모드",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = if (hasSelection) "선택 해제" else "텍스트 선택 종료",
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction("복사", Icons.Outlined.ContentCopy, hasSelection, onCopy)
            SelectionAction("Google 번역", Icons.Outlined.Translate, hasSelection, onTranslate)
            SelectionAction("발췌", Icons.AutoMirrored.Outlined.Notes, hasSelection, onExcerpt)
            SelectionAction("형광펜", Icons.Outlined.Highlight, hasSelection, onHighlight)
            SelectionAction("밑줄", Icons.Outlined.FormatUnderlined, hasSelection, onUnderline)
            SelectionAction("취소선", Icons.Outlined.FormatStrikethrough, hasSelection, onStrikeout)
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

@Composable
private fun OpacityControl(value: Float, onValueChange: (Float) -> Unit) {
    Text(stringResource(R.string.opacity), style = MaterialTheme.typography.labelMedium)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = 0.1f..1f,
        modifier = Modifier.width(100.dp),
    )
    Text(
        stringResource(R.string.opacity_percent, (value * 100).roundToInt()),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ToolChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

@Composable
internal fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(color), CircleShape)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PageBar(state: ViewerUiState, vm: PdfViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = vm::previousPage, enabled = state.pageIndex > 0) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = stringResource(R.string.back))
        }
        Text(
            stringResource(R.string.page_of, state.pageIndex + 1, state.pageCount),
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(onClick = vm::nextPage, enabled = state.pageIndex < state.pageCount - 1) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = stringResource(R.string.pages))
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { vm.setZoom(state.zoom - 0.5f) }, enabled = state.zoom > 1f) {
            Icon(Icons.Outlined.ZoomOut, contentDescription = stringResource(R.string.zoom_out))
        }
        Text(
            "${(state.zoom * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = { vm.setZoom(state.zoom + 0.5f) }, enabled = state.zoom < 6f) {
            Icon(Icons.Outlined.ZoomIn, contentDescription = stringResource(R.string.zoom_in))
        }
        IconButton(onClick = { vm.setZoom(1f) }) {
            Icon(Icons.Outlined.FitScreen, contentDescription = stringResource(R.string.fit_page))
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
