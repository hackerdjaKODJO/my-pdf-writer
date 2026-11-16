package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.audio.annotationRevealFraction
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PenBoxStore
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val NOTE_PALETTE = listOf(
    0xFF1B1C19.toInt(),
    0xFF1F4E79.toInt(),
    0xFFB3261E.toInt(),
    0xFFB4693C.toInt(),
    0xFF2E6B4F.toInt(),
    0xFF6A4C93.toInt(),
    0xFF2C7A7B.toInt(),
    0xFFF2C94C.toInt(),
)

private data class NoteToolOption(val tool: Tool, val label: String)

private val NOTE_TOOLS = listOf(
    NoteToolOption(Tool.PEN, "펜"),
    NoteToolOption(Tool.HIGHLIGHT, "형광펜"),
    NoteToolOption(Tool.ERASER, "지우개"),
    NoteToolOption(Tool.LASSO, "올가미"),
    NoteToolOption(Tool.LASSO_RECT, "사각 올가미"),
    NoteToolOption(Tool.TEXT, "텍스트"),
    NoteToolOption(Tool.RECT, "사각형"),
    NoteToolOption(Tool.ARROW, "화살표"),
    NoteToolOption(Tool.LINE, "선"),
    NoteToolOption(Tool.ELLIPSE, "원"),
)

private const val NOTE_ERASE_RADIUS = 0.03f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    notebook: Notebook,
    pageIndex: Int,
    onBack: () -> Unit,
    onChange: (Notebook) -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onExportPdf: () -> Unit,
    onOpenSourcePdf: ((Int) -> Unit)? = null,
) {
    val pages = notebook.pages.ifEmpty { listOf(NotePage()) }
    val safeIndex = pageIndex.coerceIn(0, pages.lastIndex)
    val page = pages[safeIndex]

    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(NOTE_PALETTE.first()) }
    var strokeWidth by remember { mutableStateOf(0.004f) }
    var fontSize by remember { mutableStateOf(0.028f) }
    var opacity by remember { mutableStateOf(1f) }
    var penStyle by remember { mutableStateOf(PenStyle.BALLPOINT) }
    var fingerWriting by remember { mutableStateOf(false) }
    var zoomLock by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val penBoxStore = remember(context) { PenBoxStore(context) }
    var penPresets by remember(penBoxStore) { mutableStateOf(penBoxStore.load()) }
    var selection by remember(notebook.id, page.id) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedIds = remember(selection, page.annotations) {
        selection.intersect(page.annotations.map { it.id }.toSet())
    }

    var showPenBox by remember { mutableStateOf(false) }
    var showAudioNotes by remember { mutableStateOf(false) }
    var showAudioReplay by remember { mutableStateOf(false) }
    var replayNote by remember { mutableStateOf<AudioNote?>(null) }
    var replayPositionMs by remember { mutableStateOf(0L) }
    var showLayers by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var activeLayerId by remember(notebook.id, page.id) {
        mutableStateOf(page.layers.firstOrNull()?.id.orEmpty())
    }
    val activeLayer = page.layers.firstOrNull { it.id == activeLayerId && !it.locked && it.visible }
        ?: page.layers.firstOrNull { !it.locked && it.visible }
        ?: page.layers.firstOrNull()
    val visibleAnnotations = NoteLayers.visibleAnnotations(page).map { annotation ->
        val layer = page.layers.firstOrNull { it.id == annotation.layerId }
            ?: page.layers.firstOrNull()
        if (layer != null && layer.opacity < 1f) {
            annotation.copy(opacity = (annotation.opacity * layer.opacity).coerceIn(0f, 1f))
        } else {
            annotation
        }
    }
    val visiblePage = page.copy(annotations = visibleAnnotations)
    val annotationProgress: (Annotation) -> Float = { annotation ->
        annotationRevealFraction(annotation, replayNote, replayPositionMs)
    }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var showDeletePage by remember { mutableStateOf(false) }
    var textPoint by remember { mutableStateOf<NormalizedPoint?>(null) }

    val undoStack = remember(notebook.id) { ArrayDeque<Notebook>() }
    val redoStack = remember(notebook.id) { ArrayDeque<Notebook>() }
    var historyVersion by remember(notebook.id) { mutableIntStateOf(0) }

    fun applyChange(next: Notebook) {
        if (next == notebook) return
        undoStack.addLast(notebook)
        while (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        historyVersion++
        onChange(next.copy(updatedAt = System.currentTimeMillis()))
    }

    fun updateCurrentPage(transform: (NotePage) -> NotePage) {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val index = pageIndex.coerceIn(0, currentPages.lastIndex)
        val previous = currentPages[index]
        val updated = transform(previous)
        if (updated == previous) return
        val mutable = currentPages.toMutableList()
        mutable[index] = updated
        applyChange(notebook.copy(pages = mutable))
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(notebook)
        historyVersion++
        onChange(previous.copy(updatedAt = System.currentTimeMillis()))
        val lastIndex = (previous.pages.size - 1).coerceAtLeast(0)
        if (pageIndex > lastIndex) onPageIndexChange(lastIndex)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(notebook)
        historyVersion++
        onChange(next.copy(updatedAt = System.currentTimeMillis()))
        val lastIndex = (next.pages.size - 1).coerceAtLeast(0)
        if (pageIndex > lastIndex) onPageIndexChange(lastIndex)
    }

    fun addPage() {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val mutable = currentPages.toMutableList()
        val insertAt = index + 1
        mutable.add(insertAt, NotePage(template = currentPages[index].template))
        applyChange(notebook.copy(pages = mutable))
        onPageIndexChange(insertAt)
    }

    fun duplicatePage() {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val source = currentPages[index]
        val duplicate = source.copy(
            id = UUID.randomUUID().toString(),
            annotations = source.annotations.map {
                it.copy(id = UUID.randomUUID().toString(), page = 0)
            },
        )
        val mutable = currentPages.toMutableList()
        val insertAt = index + 1
        mutable.add(insertAt, duplicate)
        applyChange(notebook.copy(pages = mutable))
        onPageIndexChange(insertAt)
    }

    fun deletePage() {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        if (currentPages.size <= 1) return
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val mutable = currentPages.toMutableList()
        mutable.removeAt(index)
        applyChange(notebook.copy(pages = mutable))
        onPageIndexChange((index - 1).coerceAtLeast(0))
    }

    fun movePage(delta: Int) {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val target = index + delta
        if (target !in currentPages.indices) return
        val mutable = currentPages.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(target, moved)
        applyChange(notebook.copy(pages = mutable))
        onPageIndexChange(target)
    }

    fun applyTemplate(template: PaperTemplate, allPages: Boolean) {
        val currentPages = notebook.pages.ifEmpty { listOf(NotePage()) }
        val updatedPages = if (allPages) {
            currentPages.map { it.copy(template = template) }
        } else {
            val index = safeIndex.coerceIn(0, currentPages.lastIndex)
            currentPages.toMutableList().also {
                it[index] = it[index].copy(template = template)
            }
        }
        if (updatedPages == currentPages) return
        applyChange(notebook.copy(pages = updatedPages))
    }

    fun rename(title: String) {
        val clean = title.trim().ifBlank { "새 노트" }
        if (clean == notebook.title) return
        applyChange(notebook.copy(title = clean))
    }

    fun toggleFavorite() {
        applyChange(notebook.copy(favorite = !notebook.favorite))
    }

    fun addStroke(points: List<NormalizedPoint>, durationMs: Long = 0L) {
        if (points.isEmpty()) return
        val layer = activeLayer ?: return
        if (layer.locked) return
        val type = noteAnnotationTypeForTool(tool) ?: return
        val safeDuration = durationMs.coerceAtLeast(0L)
        val annotation = Annotation(
            type = type,
            page = 0,
            color = color,
            width = noteStrokeWidth(type, strokeWidth),
            points = points,
            opacity = if (type == AnnotationType.HIGHLIGHT) 1f else opacity,
            penStyle = if (type == AnnotationType.PEN) penStyle else PenStyle.BALLPOINT,
            layerId = layer.id,
            createdAt = System.currentTimeMillis() - safeDuration,
            durationMs = safeDuration,
        )
        updateCurrentPage { it.copy(annotations = it.annotations + annotation) }
    }

    fun selectWithLasso(polygon: List<NormalizedPoint>, rectangular: Boolean) {
        selection = noteLassoSelection(NoteLayers.visibleAnnotations(page), polygon, rectangular)
    }

    fun recolorSelection(newColor: Int) {
        if (selectedIds.isEmpty()) return
        color = newColor
        updateCurrentPage { pageToEdit ->
            pageToEdit.copy(
                annotations = pageToEdit.annotations.map {
                    if (it.id in selectedIds) it.copy(color = newColor) else it
                },
            )
        }
    }

    fun duplicateSelection() {
        if (selectedIds.isEmpty()) return
        val duplicatedIds = mutableSetOf<String>()
        updateCurrentPage { pageToEdit ->
            val copies = pageToEdit.annotations
                .filter { it.id in selectedIds }
                .map { original ->
                    val duplicate = original.copy(
                        id = UUID.randomUUID().toString(),
                        page = 0,
                        points = original.points.map { point ->
                            NormalizedPoint(
                                (point.x + 0.012f).coerceIn(0f, 1f),
                                (point.y + 0.012f).coerceIn(0f, 1f),
                            )
                        },
                    )
                    duplicatedIds.add(duplicate.id)
                    duplicate
                }
            if (copies.isEmpty()) {
                pageToEdit
            } else {
                pageToEdit.copy(annotations = pageToEdit.annotations + copies)
            }
        }
        if (duplicatedIds.isNotEmpty()) selection = duplicatedIds
    }

    fun deleteSelection() {
        if (selectedIds.isEmpty()) return
        updateCurrentPage { pageToEdit ->
            pageToEdit.copy(
                annotations = pageToEdit.annotations.filterNot { it.id in selectedIds },
            )
        }
        selection = emptySet()
    }

    fun applyPenPreset(preset: PenPreset) {
        tool = preset.tool
        color = preset.color
        strokeWidth = preset.width
        opacity = preset.opacity
        penStyle = preset.penStyle
        showPenBox = false
    }

    fun saveCurrentPen(name: String) {
        val savedTool = if (tool == Tool.HIGHLIGHT) Tool.HIGHLIGHT else Tool.PEN
        val saved = PenPreset(
            name = name,
            color = color,
            width = if (savedTool == Tool.HIGHLIGHT) {
                noteStrokeWidth(AnnotationType.HIGHLIGHT, strokeWidth)
            } else {
                strokeWidth
            },
            opacity = opacity,
            penStyle = penStyle,
            tool = savedTool,
        )
        val updated = penPresets + saved
        penPresets = updated
        penBoxStore.save(updated)
    }

    fun deletePenPreset(preset: PenPreset) {
        if (penPresets.size <= 1) return
        val updated = penPresets.filterNot { it.id == preset.id }
        penPresets = updated
        penBoxStore.save(updated)
    }

    fun renamePenPreset(preset: PenPreset, name: String) {
        val clean = name.trim().ifBlank { preset.name }
        if (clean == preset.name) return
        val updated = penPresets.map { if (it.id == preset.id) it.copy(name = clean) else it }
        penPresets = updated
        penBoxStore.save(updated)
    }

    fun erase(trail: List<NormalizedPoint>) {
        if (trail.isEmpty()) return
        val layer = activeLayer ?: return
        if (layer.locked) return
        updateCurrentPage { pageToEdit ->
            val defaultLayerId = pageToEdit.layers.firstOrNull()?.id
            val kept = pageToEdit.annotations.filterNot { annotation ->
                val annotationLayer = annotation.layerId ?: defaultLayerId
                annotationLayer == layer.id && noteAnnotationHit(annotation, trail)
            }
            if (kept.size == pageToEdit.annotations.size) {
                pageToEdit
            } else {
                pageToEdit.copy(annotations = kept)
            }
        }
    }

    val canUndo = remember(notebook.id, historyVersion) { undoStack.isNotEmpty() }
    val canRedo = remember(notebook.id, historyVersion) { redoStack.isNotEmpty() }
    val fingerMode = if (fingerWriting) "손가락 필기" else "스타일러스 필기 · 손가락 이동"
    val hint = buildString {
        append(fingerMode)
        append(" · ")
        append(noteToolHint(tool))
        append(" · ${activeLayer?.name ?: "레이어"}")
        if (activeLayer?.locked == true) append(" · 잠김")
        append(if (zoomLock) " · 줌 잠금" else " · 줌 가능")
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showRename = true }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    ) {
                        Text(
                            notebook.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${safeIndex + 1} / ${pages.size} · ${page.template.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    TextButton(onClick = { showPenBox = true }) {
                        Text("필통")
                    }
                    IconButton(onClick = { toggleFavorite() }) {
                        Icon(
                            if (notebook.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = "즐겨찾기",
                            tint = if (notebook.favorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("PDF로 내보내기") },
                                onClick = {
                                    showMenu = false
                                    onExportPdf()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("템플릿 변경") },
                                onClick = {
                                    showMenu = false
                                    showTemplates = true
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
                                text = { Text("오디오 재생") },
                                onClick = {
                                    showMenu = false
                                    showAudioReplay = true
                                },
                            )
                            if (notebook.sourceDocumentUri.isNotBlank() && onOpenSourcePdf != null) {
                                DropdownMenuItem(
                                    text = { Text("원본 PDF 열기") },
                                    onClick = {
                                        showMenu = false
                                        val page = StudyNoteBuilder.sourcePage(notebook, safeIndex) ?: 0
                                        onOpenSourcePdf.invoke(page)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("레이어") },
                                onClick = {
                                    showMenu = false
                                    showLayers = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("아웃라인·북마크") },
                                onClick = {
                                    showMenu = false
                                    showOutline = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("페이지 추가") },
                                onClick = {
                                    showMenu = false
                                    addPage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("페이지 복제") },
                                onClick = {
                                    showMenu = false
                                    duplicatePage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("페이지 위로 이동") },
                                enabled = safeIndex > 0,
                                onClick = {
                                    showMenu = false
                                    movePage(-1)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("페이지 아래로 이동") },
                                enabled = safeIndex < pages.lastIndex,
                                onClick = {
                                    showMenu = false
                                    movePage(1)
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("페이지 삭제") },
                                enabled = pages.size > 1,
                                onClick = {
                                    showMenu = false
                                    showDeletePage = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Column {
                if (selectedIds.isNotEmpty()) {
                    NoteSelectionBar(
                        count = selectedIds.size,
                        onColor = { recolorSelection(it) },
                        onDuplicate = { duplicateSelection() },
                        onDelete = { deleteSelection() },
                        onClear = { selection = emptySet() },
                    )
                }
                NoteToolbar(
                    tool = tool,
                    onTool = { tool = it },
                    color = color,
                    onColor = { color = it },
                    strokeWidth = strokeWidth,
                    onStrokeWidth = { strokeWidth = it },
                    fontSize = fontSize,
                    onFontSize = { fontSize = it },
                    opacity = opacity,
                    onOpacity = { opacity = it },
                    penStyle = penStyle,
                    onPenStyle = { penStyle = it },
                    fingerWriting = fingerWriting,
                    onFingerWriting = { fingerWriting = it },
                    zoomLock = zoomLock,
                    onZoomLock = { zoomLock = it },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = { undo() },
                    onRedo = { redo() },
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 840.dp
            val darkPaper = false
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NoteCanvasPane(
                        page = visiblePage,
                        tool = tool,
                        color = color,
                        strokeWidth = strokeWidth,
                        fontSize = fontSize,
                        opacity = opacity,
                        penStyle = penStyle,
                        penOnly = !fingerWriting,
                        zoomLock = zoomLock,
                        annotationProgress = annotationProgress,
                        hint = hint,
                        onAddStroke = { points, durationMs -> addStroke(points, durationMs) },
                        onErase = { erase(it) },
                        onRequestText = { textPoint = it },
                        onLassoSelect = { polygon, rectangular -> selectWithLasso(polygon, rectangular) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    NotePageRail(
                        pages = pages,
                        currentIndex = safeIndex,
                        darkPaper = darkPaper,
                        onSelect = onPageIndexChange,
                        onAdd = { addPage() },
                        modifier = Modifier.width(154.dp).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    NoteCanvasPane(
                        page = visiblePage,
                        tool = tool,
                        color = color,
                        strokeWidth = strokeWidth,
                        fontSize = fontSize,
                        opacity = opacity,
                        penStyle = penStyle,
                        penOnly = !fingerWriting,
                        zoomLock = zoomLock,
                        annotationProgress = annotationProgress,
                        hint = hint,
                        onAddStroke = { points, durationMs -> addStroke(points, durationMs) },
                        onErase = { erase(it) },
                        onRequestText = { textPoint = it },
                        onLassoSelect = { polygon, rectangular -> selectWithLasso(polygon, rectangular) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    NotePageStrip(
                        pages = pages,
                        currentIndex = safeIndex,
                        darkPaper = darkPaper,
                        onSelect = onPageIndexChange,
                        onAdd = { addPage() },
                    )
                }
            }
        }
    }

    if (showRename) {
        NoteRenameDialog(
            initial = notebook.title,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                rename(it)
            },
        )
    }

    if (showTemplates) {
        NoteTemplateDialog(
            current = page.template,
            onDismiss = { showTemplates = false },
            onApply = { template, allPages ->
                showTemplates = false
                applyTemplate(template, allPages)
            },
        )
    }

    if (showDeletePage) {
        AlertDialog(
            onDismissRequest = { showDeletePage = false },
            title = { Text("페이지 삭제") },
            text = { Text("이 페이지를 삭제할까요? 삭제한 페이지는 실행 취소로 되돌릴 수 있습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePage = false
                        deletePage()
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePage = false }) {
                    Text("취소")
                }
            },
        )
    }

    val point = textPoint
    if (point != null) {
        NoteTextDialog(
            onDismiss = { textPoint = null },
            onConfirm = { value ->
                textPoint = null
                if (value.isNotBlank()) {
                    val annotation = Annotation(
                        type = AnnotationType.TEXT,
                        page = 0,
                        color = color,
                        width = maxOf(strokeWidth, 0.0025f),
                        points = listOf(point),
                        text = value,
                        fontSize = fontSize,
                        opacity = opacity,
                        layerId = activeLayer?.id,
                    )
                    updateCurrentPage { it.copy(annotations = it.annotations + annotation) }
                }
            },
        )
    }

    if (showPenBox) {
        val currentTool = if (tool == Tool.HIGHLIGHT) Tool.HIGHLIGHT else Tool.PEN
        PenBoxSheet(
            presets = penPresets,
            current = PenPreset(
                id = "current-pen",
                name = "현재 펜",
                color = color,
                width = if (currentTool == Tool.HIGHLIGHT) {
                    noteStrokeWidth(AnnotationType.HIGHLIGHT, strokeWidth)
                } else {
                    strokeWidth
                },
                opacity = if (currentTool == Tool.HIGHLIGHT) 1f else opacity,
                penStyle = if (currentTool == Tool.PEN) penStyle else PenStyle.BALLPOINT,
                tool = currentTool,
            ),
            onDismiss = { showPenBox = false },
            onApply = { applyPenPreset(it) },
            onSaveCurrent = { saveCurrentPen(it) },
            onDelete = { deletePenPreset(it) },
            onRename = { preset, name -> renamePenPreset(preset, name) },
        )
    }

    if (showAudioNotes) {
        AudioNotesSheet(
            ownerId = notebook.id,
            currentPage = safeIndex,
            onDismiss = { showAudioNotes = false },
        )
    }

    if (showAudioReplay) {
        AudioReplaySheet(
            ownerId = notebook.id,
            currentPage = safeIndex,
            onPlaybackState = { note, positionMs, _ ->
                replayNote = note
                replayPositionMs = positionMs
                if (note != null && note.pageIndex != safeIndex) onPageIndexChange(note.pageIndex)
            },
            onDismiss = {
                replayNote = null
                replayPositionMs = 0L
                showAudioReplay = false
            },
        )
    }

    if (showLayers) {
        NoteLayersSheet(
            page = page,
            activeLayerId = activeLayer?.id.orEmpty(),
            onChange = { updated ->
                updateCurrentPage { updated }
                if (updated.layers.none { it.id == activeLayerId }) {
                    activeLayerId = updated.layers.firstOrNull()?.id.orEmpty()
                }
            },
            onActiveChange = { activeLayerId = it },
            onDismiss = { showLayers = false },
        )
    }

    if (showOutline) {
        NotebookOutlineSheet(
            notebook = notebook,
            currentPage = safeIndex,
            onJump = { onPageIndexChange(it) },
            onBookmarksChange = { applyChange(it) },
            onDismiss = { showOutline = false },
        )
    }
}

@Composable
private fun NoteCanvasPane(
    page: NotePage,
    tool: Tool,
    color: Int,
    strokeWidth: Float,
    fontSize: Float,
    opacity: Float,
    penStyle: PenStyle,
    penOnly: Boolean,
    zoomLock: Boolean,
    annotationProgress: (Annotation) -> Float,
    hint: String,
    onAddStroke: (List<NormalizedPoint>, Long) -> Unit,
    onErase: (List<NormalizedPoint>) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onLassoSelect: (List<NormalizedPoint>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        NoteCanvas(
            page = page,
            tool = tool,
            color = color,
            strokeWidth = strokeWidth,
            fontSize = fontSize,
            opacity = opacity,
            penStyle = penStyle,
            penOnly = penOnly,
            zoomLock = zoomLock,
            annotationProgress = annotationProgress,
            onAddStroke = onAddStroke,
            onErase = onErase,
            onRequestText = onRequestText,
            onLassoSelect = onLassoSelect,
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
        ) {
            Text(
                hint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteToolbar(
    tool: Tool,
    onTool: (Tool) -> Unit,
    color: Int,
    onColor: (Int) -> Unit,
    strokeWidth: Float,
    onStrokeWidth: (Float) -> Unit,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    penStyle: PenStyle,
    onPenStyle: (PenStyle) -> Unit,
    fingerWriting: Boolean,
    onFingerWriting: (Boolean) -> Unit,
    zoomLock: Boolean,
    onZoomLock: (Boolean) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOTE_TOOLS.forEach { option ->
                    NoteToolChip(
                        label = option.label,
                        selected = tool == option.tool,
                        onClick = { onTool(option.tool) },
                    )
                }
            }

            if (tool == Tool.PEN) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "펜 종류",
                        modifier = Modifier.padding(end = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PenStyle.entries.forEach { style ->
                        NoteToolChip(
                            label = penStyleLabel(style),
                            selected = penStyle == style,
                            onClick = { onPenStyle(style) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOTE_PALETTE.forEach { swatch ->
                    NoteColorDot(
                        color = swatch,
                        selected = color == swatch,
                        onClick = { onColor(swatch) },
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "실행 취소")
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "다시 실행")
                }
                FilterChip(
                    selected = fingerWriting,
                    onClick = { onFingerWriting(!fingerWriting) },
                    label = { Text("손가락 필기") },
                )
                FilterChip(
                    selected = zoomLock,
                    onClick = { onZoomLock(!zoomLock) },
                    label = { Text("줌 잠금") },
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tool == Tool.TEXT) {
                    NoteSlider(
                        label = "글자 크기",
                        valueLabel = "${(fontSize * 1000).roundToInt()}",
                        value = fontSize,
                        range = 0.012f..0.08f,
                        onValueChange = onFontSize,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    NoteSlider(
                        label = "굵기",
                        valueLabel = "${(strokeWidth * 1000).roundToInt()}",
                        value = strokeWidth,
                        range = 0.001f..0.02f,
                        onValueChange = onStrokeWidth,
                        modifier = Modifier.weight(1f),
                    )
                }
                NoteSlider(
                    label = "투명도",
                    valueLabel = "${(opacity * 100).roundToInt()}%",
                    value = opacity,
                    range = 0.1f..1f,
                    onValueChange = onOpacity,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NoteSelectionBar(
    count: Int,
    onColor: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${count}개 선택",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            NOTE_PALETTE.forEach { swatch ->
                NoteColorDot(
                    color = swatch,
                    selected = false,
                    onClick = { onColor(swatch) },
                )
            }
            TextButton(onClick = onDuplicate) {
                Text("복제")
            }
            TextButton(onClick = onDelete) {
                Text("삭제")
            }
            TextButton(onClick = onClear) {
                Text("선택 해제")
            }
        }
    }
}

@Composable
private fun NoteToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun NoteColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun NoteSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NotePageRail(
    pages: List<NotePage>,
    currentIndex: Int,
    darkPaper: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "페이지",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "페이지 추가",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(bottom = 6.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                    NotePageThumbnail(
                        page = item,
                        number = index,
                        selected = index == currentIndex,
                        darkPaper = darkPaper,
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotePageStrip(
    pages: List<NotePage>,
    currentIndex: Int,
    darkPaper: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(pages, key = { _, item -> item.id }) { index, item ->
                    NotePageThumbnail(
                        page = item,
                        number = index,
                        selected = index == currentIndex,
                        darkPaper = darkPaper,
                        onClick = { onSelect(index) },
                        modifier = Modifier.width(64.dp),
                    )
                }
                item {
                    NoteAddPageCard(onClick = onAdd)
                }
            }
        }
    }
}

@Composable
private fun NotePageThumbnail(
    page: NotePage,
    number: Int,
    selected: Boolean,
    darkPaper: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().aspectRatio(PaperBackground.A4_ASPECT),
        ) {
            NotePagePreview(page = page, darkPaper = darkPaper, modifier = Modifier.fillMaxSize())
        }
        Text(
            "${number + 1}",
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun NoteAddPageCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PaperBackground.A4_ASPECT),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "페이지 추가",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "추가",
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotePagePreview(page: NotePage, darkPaper: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            PaperBackground.draw(
                canvas = canvas.nativeCanvas,
                template = page.template,
                width = size.width,
                height = size.height,
                dark = darkPaper,
            )
            AnnotationPainter.draw(
                canvas = canvas.nativeCanvas,
                annotations = page.annotations,
                width = size.width,
                height = size.height,
            )
        }
    }
}

@Composable
private fun NoteTemplateDialog(
    current: PaperTemplate,
    onDismiss: () -> Unit,
    onApply: (PaperTemplate, Boolean) -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    var allPages by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("템플릿 변경") },
        text = {
            Column {
                Text(
                    "현재 페이지: ${current.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(310.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PaperTemplate.entries) { template ->
                        NoteTemplateCard(
                            template = template,
                            selected = selected == template,
                            onClick = { selected = template },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allPages, onCheckedChange = { allPages = it })
                    Text("모든 페이지에 적용", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(selected, allPages) }) {
                Text("적용")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@Composable
private fun NoteTemplateCard(
    template: PaperTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(7.dp),
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PaperBackground.A4_ASPECT)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            drawIntoCanvas { canvas ->
                PaperBackground.draw(
                    canvas = canvas.nativeCanvas,
                    template = template,
                    width = size.width,
                    height = size.height,
                    dark = false,
                )
            }
        }
        Text(
            template.label,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoteRenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("노트 이름") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("제목") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@Composable
private fun NoteTextDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("텍스트 입력") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("내용") },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

private fun noteToolHint(tool: Tool): String = when (tool) {
    Tool.PEN, Tool.HIGHLIGHT -> "획을 그리세요"
    Tool.ERASER -> "획을 문질러 지우세요"
    Tool.TEXT -> "빈 곳을 눌러 텍스트 입력"
    Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> "끌어서 도형 그리기"
    Tool.LASSO -> "끌어서 자유롭게 선택"
    Tool.LASSO_RECT -> "끌어서 사각형으로 선택"
    else -> "필기"
}

private fun noteAnnotationHit(annotation: Annotation, trail: List<NormalizedPoint>): Boolean {
    if (trail.isEmpty() || annotation.points.isEmpty()) return false
    return trail.any { eraser ->
        when (annotation.type) {
            AnnotationType.PEN,
            AnnotationType.HIGHLIGHT,
            AnnotationType.SIGNATURE,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.LINE,
            AnnotationType.ARROW -> distanceToPolyline(eraser, annotation.points) <= NOTE_ERASE_RADIUS

            AnnotationType.RECT,
            AnnotationType.ELLIPSE -> annotation.points.size >= 2 &&
                distanceToRectBorder(eraser, annotation.points[0], annotation.points[1]) <= NOTE_ERASE_RADIUS

            AnnotationType.TEXT,
            AnnotationType.STAMP -> distance(eraser, annotation.points[0]) <= NOTE_ERASE_RADIUS * 2.4f

            AnnotationType.REDACT -> annotation.points.size >= 2 &&
                insideRect(eraser, annotation.points[0], annotation.points[1])
        }
    }
}

private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

private fun distanceToPolyline(point: NormalizedPoint, points: List<NormalizedPoint>): Float {
    if (points.isEmpty()) return Float.MAX_VALUE
    if (points.size == 1) return distance(point, points[0])
    var best = Float.MAX_VALUE
    for (i in 1 until points.size) {
        best = min(
            best,
            distanceToSegment(point, points[i - 1], points[i]),
        )
    }
    return best
}

private fun distanceToSegment(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSq = dx * dx + dy * dy
    val t = if (lengthSq <= 0.0000001f) {
        0f
    } else {
        (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
    }
    val x = a.x + t * dx
    val y = a.y + t * dy
    val ex = point.x - x
    val ey = point.y - y
    return sqrt(ex * ex + ey * ey)
}

private fun distanceToRectBorder(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Float {
    val left = minOf(a.x, b.x)
    val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    val corners = listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
    )
    var best = Float.MAX_VALUE
    for (i in corners.indices) {
        best = min(best, distanceToSegment(point, corners[i], corners[(i + 1) % corners.size]))
    }
    return best
}

private fun insideRect(
    point: NormalizedPoint,
    a: NormalizedPoint,
    b: NormalizedPoint,
): Boolean {
    val left = minOf(a.x, b.x)
    val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y)
    val bottom = maxOf(a.y, b.y)
    return point.x in left..right && point.y in top..bottom
}
