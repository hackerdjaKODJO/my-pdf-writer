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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.sqrt

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
    var showStyle by remember { mutableStateOf(false) }
    var showPageSheet by remember { mutableStateOf(false) }
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
        if (replayNote != null && replayNote?.pageIndex != safeIndex) 1f
        else annotationRevealFraction(annotation, replayNote, replayPositionMs)
    }
    var showOverflow by remember { mutableStateOf(false) }
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

    val darkPaper = false

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        notebook.title,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClickLabel = "제목 편집") { showRename = true }
                            .padding(horizontal = 2.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { undo() }, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "실행 취소")
                    }
                    IconButton(onClick = { redo() }, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "다시 실행")
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
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
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
                NoteDock(
                    tool = tool,
                    onTool = { tool = it },
                    color = color,
                    onOpenStyle = { showStyle = true },
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = { undo() },
                    onRedo = { redo() },
                    pageIndex = safeIndex,
                    pageCount = pages.size,
                    onPreviousPage = {
                        if (notePagePreviousEnabled(safeIndex)) onPageIndexChange(safeIndex - 1)
                    },
                    onNextPage = {
                        if (notePageNextEnabled(safeIndex, pages.size)) onPageIndexChange(safeIndex + 1)
                    },
                    onOpenPages = { showPageSheet = true },
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
                    modifier = Modifier.fillMaxSize(),
                )
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

    if (showStyle) {
        NoteStyleSheet(
            tool = tool,
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
            onOpenPenBox = {
                showStyle = false
                showPenBox = true
            },
            onDismiss = { showStyle = false },
        )
    }

    if (showPageSheet) {
        NotePageSheet(
            pages = pages,
            currentIndex = safeIndex,
            darkPaper = darkPaper,
            onSelect = {
                onPageIndexChange(it)
                showPageSheet = false
            },
            onAdd = {
                showPageSheet = false
                addPage()
            },
            onDismiss = { showPageSheet = false },
        )
    }

    if (showOverflow) {
        NoteOverflowSheet(
            notebookTitle = notebook.title,
            pageIndex = safeIndex,
            pageCount = pages.size,
            templateLabel = page.template.label,
            layerName = activeLayer?.name ?: "레이어",
            layerCount = page.layers.size,
            canMoveUp = safeIndex > 0,
            canMoveDown = safeIndex < pages.lastIndex,
            canDeletePage = pages.size > 1,
            fingerWriting = fingerWriting,
            zoomLock = zoomLock,
            onFingerWriting = { fingerWriting = it },
            onZoomLock = { zoomLock = it },
            onTemplate = {
                showOverflow = false
                showTemplates = true
            },
            onLayers = {
                showOverflow = false
                showLayers = true
            },
            onOutline = {
                showOverflow = false
                showOutline = true
            },
            onAudioNotes = {
                showOverflow = false
                showAudioNotes = true
            },
            onAudioReplay = {
                showOverflow = false
                showAudioReplay = true
            },
            onOpenSourcePdf = if (notebook.sourceDocumentUri.isNotBlank() && onOpenSourcePdf != null) {
                {
                    showOverflow = false
                    val sourcePage = StudyNoteBuilder.sourcePage(notebook, safeIndex) ?: 0
                    onOpenSourcePdf.invoke(sourcePage)
                }
            } else {
                null
            },
            onAddPage = {
                showOverflow = false
                addPage()
            },
            onDuplicatePage = {
                showOverflow = false
                duplicatePage()
            },
            onMovePageUp = {
                showOverflow = false
                movePage(-1)
            },
            onMovePageDown = {
                showOverflow = false
                movePage(1)
            },
            onDeletePage = {
                showOverflow = false
                showDeletePage = true
            },
            onExportPdf = {
                showOverflow = false
                onExportPdf()
            },
            onDismiss = { showOverflow = false },
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
private fun NoteDock(
    tool: Tool,
    onTool: (Tool) -> Unit,
    color: Int,
    onOpenStyle: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    pageIndex: Int,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenPages: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            if (noteIsShapeTool(tool)) {
                NoteToolTray(
                    label = "도형",
                    options = NOTE_SHAPE_TOOLS.map { it to noteShapeLabel(it) },
                    selectedTool = tool,
                    onTool = onTool,
                )
            }
            if (noteIsLassoTool(tool)) {
                NoteToolTray(
                    label = "선택",
                    options = NOTE_LASSO_TOOLS.map { it to noteLassoLabel(it) },
                    selectedTool = tool,
                    onTool = onTool,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NoteDockTool(
                        icon = Icons.Filled.Edit,
                        label = "펜",
                        selected = tool == Tool.PEN,
                        onClick = { onTool(Tool.PEN) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteDockTool(
                        icon = Icons.Filled.Highlight,
                        label = "형광펜",
                        selected = tool == Tool.HIGHLIGHT,
                        onClick = { onTool(Tool.HIGHLIGHT) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteDockTool(
                        icon = Icons.Filled.AutoFixNormal,
                        label = "지우개",
                        selected = tool == Tool.ERASER,
                        onClick = { onTool(Tool.ERASER) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteDockTool(
                        icon = Icons.Filled.Gesture,
                        label = "올가미",
                        selected = noteIsLassoTool(tool),
                        onClick = {
                            onTool(if (noteIsLassoTool(tool)) tool else Tool.LASSO)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    NoteDockTool(
                        icon = Icons.Filled.TextFields,
                        label = "텍스트",
                        selected = tool == Tool.TEXT,
                        onClick = { onTool(Tool.TEXT) },
                        modifier = Modifier.weight(1f),
                    )
                    NoteDockTool(
                        icon = Icons.Filled.Category,
                        label = "도형",
                        selected = noteIsShapeTool(tool),
                        onClick = {
                            onTool(if (noteIsShapeTool(tool)) tool else Tool.RECT)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteColorDot(
                    color = color,
                    selected = true,
                    onClick = onOpenStyle,
                )
                NoteDockStyleButton(onClick = onOpenStyle)
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "실행 취소",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "다시 실행",
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                NotePagePill(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    onPrevious = onPreviousPage,
                    onNext = onNextPage,
                    onOpenPages = onOpenPages,
                )
            }
        }
    }
}

@Composable
private fun NoteDockTool(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
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
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NoteDockStyleButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("스타일", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NotePagePill(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPages: () -> Unit,
) {
    Surface(
        onClick = onOpenPages,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = notePagePreviousEnabled(pageIndex),
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "이전 페이지",
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                "${pageIndex + 1} / $pageCount",
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            IconButton(
                onClick = onNext,
                enabled = notePageNextEnabled(pageIndex, pageCount),
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "다음 페이지",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NoteToolTray(
    label: String,
    options: List<Pair<Tool, String>>,
    selectedTool: Tool,
    onTool: (Tool) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            options.forEach { (option, text) ->
                NoteToolChip(
                    label = text,
                    selected = selectedTool == option,
                    onClick = { onTool(option) },
                )
            }
        }
    }
}

private fun noteShapeLabel(tool: Tool): String = when (tool) {
    Tool.RECT -> "사각형"
    Tool.ARROW -> "화살표"
    Tool.LINE -> "선"
    Tool.ELLIPSE -> "원"
    else -> "도형"
}

private fun noteLassoLabel(tool: Tool): String =
    if (tool == Tool.LASSO_RECT) "사각 올가미" else "자유 올가미"

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePageSheet(
    pages: List<NotePage>,
    currentIndex: Int,
    darkPaper: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeCurrent = if (pages.isEmpty()) 0 else currentIndex.coerceIn(0, pages.lastIndex)
    val templateLabel = pages.getOrNull(safeCurrent)?.template?.label.orEmpty()
    val pageLabel = if (pages.isEmpty()) "0 / 0" else "${safeCurrent + 1} / ${pages.size}"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("페이지", style = MaterialTheme.typography.titleLarge)
                    Text(
                        pageLabel + if (templateLabel.isBlank()) "" else " · $templateLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = pages.size, key = { pages[it].id }) { index ->
                    NotePageThumbnail(
                        page = pages[index],
                        number = index,
                        selected = index == safeCurrent,
                        darkPaper = darkPaper,
                        onClick = { onSelect(index) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "add-page") {
                    NoteAddPageCard(
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteOverflowSheet(
    notebookTitle: String,
    pageIndex: Int,
    pageCount: Int,
    templateLabel: String,
    layerName: String,
    layerCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDeletePage: Boolean,
    fingerWriting: Boolean,
    zoomLock: Boolean,
    onFingerWriting: (Boolean) -> Unit,
    onZoomLock: (Boolean) -> Unit,
    onTemplate: () -> Unit,
    onLayers: () -> Unit,
    onOutline: () -> Unit,
    onAudioNotes: () -> Unit,
    onAudioReplay: () -> Unit,
    onOpenSourcePdf: (() -> Unit)?,
    onAddPage: () -> Unit,
    onDuplicatePage: () -> Unit,
    onMovePageUp: () -> Unit,
    onMovePageDown: () -> Unit,
    onDeletePage: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("노트 메뉴", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "$notebookTitle · ${pageIndex + 1} / $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            item { NoteMenuSection("문서") }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Dashboard,
                    title = "템플릿",
                    supporting = "현재: $templateLabel",
                    onClick = onTemplate,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Layers,
                    title = "레이어",
                    supporting = "${layerCount}개 · $layerName",
                    onClick = onLayers,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Bookmarks,
                    title = "아웃라인/북마크",
                    supporting = "페이지 목록과 책갈피",
                    onClick = onOutline,
                )
            }
            if (onOpenSourcePdf != null) {
                item {
                    NoteMenuRow(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "원본 PDF 열기",
                        supporting = "연결된 PDF에서 열기",
                        onClick = onOpenSourcePdf,
                    )
                }
            }

            item { NoteMenuDivider() }
            item { NoteMenuSection("오디오") }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Mic,
                    title = "오디오 노트",
                    supporting = "이 페이지에 녹음 추가·재생",
                    onClick = onAudioNotes,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "오디오 재생",
                    supporting = "녹음과 필기를 함께 재생",
                    onClick = onAudioReplay,
                )
            }

            item { NoteMenuDivider() }
            item { NoteMenuSection("페이지") }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Add,
                    title = "페이지 추가",
                    supporting = "현재 페이지 뒤에 추가",
                    onClick = onAddPage,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.ContentCopy,
                    title = "페이지 복제",
                    supporting = "필기까지 함께 복사",
                    onClick = onDuplicatePage,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.ArrowUpward,
                    title = "페이지 위로 이동",
                    enabled = canMoveUp,
                    onClick = onMovePageUp,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.ArrowDownward,
                    title = "페이지 아래로 이동",
                    enabled = canMoveDown,
                    onClick = onMovePageDown,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Delete,
                    title = "페이지 삭제",
                    enabled = canDeletePage,
                    destructive = true,
                    onClick = onDeletePage,
                )
            }

            item { NoteMenuDivider() }
            item { NoteMenuSection("필기 설정") }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Draw,
                    title = "손가락 필기",
                    supporting = if (fingerWriting) {
                        "손가락으로 그리고 두 손가락으로 이동"
                    } else {
                        "스타일러스로 그리고 손가락으로 이동"
                    },
                    checked = fingerWriting,
                    onCheckedChange = onFingerWriting,
                )
            }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Lock,
                    title = "줌 잠금",
                    supporting = "필기 중 페이지 확대·이동 잠금",
                    checked = zoomLock,
                    onCheckedChange = onZoomLock,
                )
            }

            item { NoteMenuDivider() }
            item { NoteMenuSection("내보내기") }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.FileDownload,
                    title = "PDF로 내보내기",
                    supporting = "현재 노트를 PDF 파일로 저장",
                    onClick = onExportPdf,
                )
            }
        }
    }
}

@Composable
private fun NoteMenuSection(label: String) {
    Text(
        label,
        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NoteMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun NoteMenuRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val iconColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
                if (!supporting.isNullOrBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteMenuToggleRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
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
private fun NoteAddPageCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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
