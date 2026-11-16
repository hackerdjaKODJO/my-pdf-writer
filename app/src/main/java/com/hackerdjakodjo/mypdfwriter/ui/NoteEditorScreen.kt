package com.hackerdjakodjo.mypdfwriter.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import com.hackerdjakodjo.mypdfwriter.audio.annotationRevealFraction
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplateAsset
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplatePaths
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplateStore
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotebookStore
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import com.hackerdjakodjo.mypdfwriter.data.PenBoxStore
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.pressureCurveRange
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportPageRange
import com.hackerdjakodjo.mypdfwriter.pdf.noteExportFileName
import kotlinx.coroutines.launch
import java.util.UUID

private const val NOTE_TRACE_TAG = "NoteEditor"

/**
 * Full notebook editor used both full-screen and inside the PDF study pane.
 *
 * [onExportPdf] receives the page range and hidden-layer choice confirmed in the
 * export sheet. Callers own the SAF launcher and pass the options to
 * [com.hackerdjakodjo.mypdfwriter.pdf.NoteExporter].
 *
 * [compact] is used by [StudyPaneSheet]: the study-pane header owns the title and
 * close action, so the editor drops its own top app bar, cell-phone hint strip and
 * tall dock in favour of one tool row plus a slim action row. Every tool, sheet,
 * page manager, export, search, audio, layer, lasso, pressure and template action
 * stays reachable through the compact dock, page pill and overflow sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    notebook: Notebook,
    pageIndex: Int,
    onBack: () -> Unit,
    onChange: (Notebook) -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onExportPdf: (NoteExportOptions) -> Unit,
    onOpenSourcePdf: ((Int) -> Unit)? = null,
    compact: Boolean = false,
) {
    // The parent only echoes notebook changes after recomposition. Keep the newest
    // locally committed notebook here so a second stroke that arrives before that
    // echo is still built on top of the first stroke instead of replacing it.
    var workingNotebook by remember(notebook.id) { mutableStateOf(notebook) }

    val pages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
    val safeIndex = pageIndex.coerceIn(0, pages.lastIndex)
    val page = pages[safeIndex]

    // Page-manager selection is lifted here so the export sheet can offer it.
    // Page ids are stable across inserts, deletes, duplicates and undo.
    var pageSelection by remember(workingNotebook.id) { mutableStateOf<Set<String>>(emptySet()) }
    val selectedPageIds = remember(pageSelection, pages) {
        NotePageManager.validSelection(pages, pageSelection)
    }

    // Adopt a genuinely newer notebook from the parent (for example a rename or a
    // save applied elsewhere) without clobbering edits that are newer locally.
    LaunchedEffect(notebook) {
        val local = workingNotebook
        if (shouldAdoptExternalNotebook(local, notebook)) {
            workingNotebook = notebook
            NoteTrace.event(
                NOTE_TRACE_TAG,
                "parent-sync",
                "adopt" to true,
                "localUpdatedAt" to local.updatedAt,
                "incomingUpdatedAt" to notebook.updatedAt,
                "pages" to notebook.pages.size,
                "annotations" to notebookAnnotationCount(notebook),
            )
        } else if (notebook != local) {
            NoteTrace.event(
                NOTE_TRACE_TAG,
                "parent-sync",
                "adopt" to false,
                "localUpdatedAt" to local.updatedAt,
                "incomingUpdatedAt" to notebook.updatedAt,
                "pages" to local.pages.size,
                "annotations" to notebookAnnotationCount(local),
            )
        }
    }

    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(NOTE_PALETTE.first()) }
    var strokeWidth by remember { mutableStateOf(0.004f) }
    var fontSize by remember { mutableStateOf(0.028f) }
    var opacity by remember { mutableStateOf(1f) }
    var penStyle by remember { mutableStateOf(PenStyle.BALLPOINT) }
    var fingerWriting by remember { mutableStateOf(false) }
    var zoomLock by remember { mutableStateOf(false) }
    var strokeStabilization by remember { mutableStateOf(true) }
    var shapeRecognition by remember { mutableStateOf(false) }
    var partialEraser by remember { mutableStateOf(true) }
    var pressureInk by remember { mutableStateOf(true) }
    var pressureCurve by remember { mutableStateOf(PressureCurve.DEFAULT) }
    var gestureUndo by remember { mutableStateOf(NOTE_GESTURE_UNDO_DEFAULT) }

    val context = LocalContext.current
    val penBoxStore = remember(context) { PenBoxStore(context) }
    val customTemplateStore = remember(context) { CustomTemplateStore(context) }
    var penPresets by remember(penBoxStore) { mutableStateOf(penBoxStore.load()) }
    val pageBackground = remember(page.id, page.customBackground) {
        page.customBackground?.let(customTemplateStore::load)
    }
    var selection by remember(workingNotebook.id, page.id) {
        mutableStateOf<Set<String>>(emptySet())
    }
    val selectedIds = remember(selection, page.annotations) {
        selection.intersect(page.annotations.map { it.id }.toSet())
    }
    val selectedAnnotations = remember(selectedIds, page.annotations) {
        page.annotations.filter { it.id in selectedIds }
    }
    val selectionBounds = remember(selectedAnnotations) {
        noteSelectionBounds(selectedAnnotations, NotePaper.aspect(page.orientation))
    }
    // Session-only and notebook-scoped: page switches keep the clipboard, closing
    // the notebook (or opening another one) discards it.
    var clipboard by remember(workingNotebook.id) { mutableStateOf(NoteClipboard()) }

    var showPenBox by remember { mutableStateOf(false) }
    var showStyle by remember { mutableStateOf(false) }
    var showSelectionSheet by remember { mutableStateOf(false) }

    // A page switch invalidates the selection; close the editor with it.
    LaunchedEffect(page.id) { showSelectionSheet = false }
    var showPageSheet by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var showAudioNotes by remember { mutableStateOf(false) }
    var showAudioReplay by remember { mutableStateOf(false) }
    var replayNote by remember { mutableStateOf<AudioNote?>(null) }
    var replayPositionMs by remember { mutableStateOf(0L) }
    var showLayers by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var activeLayerId by remember(workingNotebook.id, page.id) {
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
    var showTrace by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showStudio by remember { mutableStateOf(false) }
    var showQuickTemplates by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showDeletePage by remember { mutableStateOf(false) }
    var photoImporting by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    var textPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Removing a background keeps its file until the editor closes. Undo can
    // re-reference the same key, so eager deletion would break one-step undo.
    var pendingTemplateCleanup by remember(workingNotebook.id) {
        mutableStateOf<Set<String>>(emptySet())
    }

    val undoStack = remember(workingNotebook.id) { ArrayDeque<Notebook>() }
    val redoStack = remember(workingNotebook.id) { ArrayDeque<Notebook>() }
    var historyVersion by remember(workingNotebook.id) { mutableIntStateOf(0) }

    fun applyChange(next: Notebook) {
        val previous = workingNotebook
        if (next == previous) return
        val saved = next.copy(updatedAt = System.currentTimeMillis())
        undoStack.addLast(previous)
        while (undoStack.size > 60) undoStack.removeFirst()
        redoStack.clear()
        historyVersion++
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "apply",
            "pages" to saved.pages.size,
            "annotationsBefore" to notebookAnnotationCount(previous),
            "annotationsAfter" to notebookAnnotationCount(saved),
        )
        // Publish locally before handing the change to the parent. A second stroke
        // that lands before the parent's recomposition/echo must see this state.
        workingNotebook = saved
        onChange(saved)
    }

    fun updateCurrentPage(transform: (NotePage) -> NotePage) {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val index = pageIndex.coerceIn(0, currentPages.lastIndex)
        val previous = currentPages[index]
        val updated = transform(previous)
        if (updated == previous) return
        val mutable = currentPages.toMutableList()
        mutable[index] = updated
        applyChange(workingNotebook.copy(pages = mutable))
    }

    /**
     * Removes an asset only when no live notebook still points at it. The in-memory
     * working notebook is authoritative for this editor; on-disk copies of other
     * notebooks are checked so a duplicated/shared template is not deleted.
     */
    fun deleteTemplateAssetIfUnused(key: String) {
        val safe = CustomTemplatePaths.sanitizeKey(key) ?: return
        val used = LinkedHashSet<String>()
        used.addAll(CustomTemplatePaths.keysInPages(workingNotebook.pages))
        runCatching { NotebookStore(context).loadAll() }
            .getOrDefault(emptyList())
            .forEach { other ->
                if (other.id != workingNotebook.id) {
                    used.addAll(CustomTemplatePaths.keysInPages(other.pages))
                }
            }
        customTemplateStore.deleteIfUnused(safe, used)
    }

    fun queueTemplateAssetCleanup(keys: Set<String>) {
        val safeKeys = keys.mapNotNullTo(LinkedHashSet()) { CustomTemplatePaths.sanitizeKey(it) }
        if (safeKeys.isNotEmpty()) {
            pendingTemplateCleanup = pendingTemplateCleanup + safeKeys
        }
    }

    fun queueRemovedTemplateAssets(before: Notebook, after: Notebook) {
        queueTemplateAssetCleanup(
            CustomTemplatePaths.keysInPages(before.pages) -
                CustomTemplatePaths.keysInPages(after.pages),
        )
    }

    /**
     * Runs only when this editor is left. The session undo stack is discarded then,
     * so a removed asset can be deleted if no notebook still references it.
     */
    fun flushPendingTemplateCleanup() {
        val keys = pendingTemplateCleanup
        if (keys.isEmpty()) return
        pendingTemplateCleanup = emptySet()
        keys.forEach { deleteTemplateAssetIfUnused(it) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(workingNotebook)
        historyVersion++
        val saved = previous.copy(updatedAt = System.currentTimeMillis())
        workingNotebook = saved
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "undo",
            "pages" to saved.pages.size,
            "annotations" to notebookAnnotationCount(saved),
            "undoDepth" to undoStack.size,
            "redoDepth" to redoStack.size,
        )
        onChange(saved)
        val lastIndex = (previous.pages.size - 1).coerceAtLeast(0)
        if (pageIndex > lastIndex) onPageIndexChange(lastIndex)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(workingNotebook)
        historyVersion++
        val saved = next.copy(updatedAt = System.currentTimeMillis())
        workingNotebook = saved
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "redo",
            "pages" to saved.pages.size,
            "annotations" to notebookAnnotationCount(saved),
            "undoDepth" to undoStack.size,
            "redoDepth" to redoStack.size,
        )
        onChange(saved)
        val lastIndex = (next.pages.size - 1).coerceAtLeast(0)
        if (pageIndex > lastIndex) onPageIndexChange(lastIndex)
    }

    fun addPage() {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val mutable = currentPages.toMutableList()
        val insertAt = index + 1
        val source = currentPages[index]
        mutable.add(
            insertAt,
            NotePage(
                template = source.template,
                orientation = source.orientation,
                tint = source.tint,
                customBackground = source.customBackground,
                customBackgroundName = source.customBackgroundName,
            ),
        )
        applyChange(workingNotebook.copy(pages = mutable))
        onPageIndexChange(insertAt)
    }

    fun duplicatePage() {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
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
        applyChange(workingNotebook.copy(pages = mutable))
        onPageIndexChange(insertAt)
    }

    fun deletePage() {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        if (currentPages.size <= 1) return
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val mutable = currentPages.toMutableList()
        mutable.removeAt(index)
        val before = workingNotebook
        applyChange(workingNotebook.copy(pages = mutable))
        queueRemovedTemplateAssets(before, workingNotebook)
        onPageIndexChange((index - 1).coerceAtLeast(0))
    }

    fun movePage(delta: Int) {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val index = safeIndex.coerceIn(0, currentPages.lastIndex)
        val target = index + delta
        if (target !in currentPages.indices) return
        val mutable = currentPages.toMutableList()
        val moved = mutable.removeAt(index)
        mutable.add(target, moved)
        applyChange(workingNotebook.copy(pages = mutable))
        onPageIndexChange(target)
    }

    fun duplicateSelectedPages() {
        if (selectedPageIds.isEmpty()) return
        val result = NotePageManager.duplicatePages(
            notebook = workingNotebook,
            selectedPageIds = selectedPageIds,
            currentIndex = safeIndex,
        )
        if (result.notebook == workingNotebook) return
        applyChange(result.notebook)
        pageSelection = result.selectedPageIds
        if (result.currentIndex != safeIndex) onPageIndexChange(result.currentIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "page_duplicate",
            "selected" to selectedPageIds.size,
            "pagesAfter" to result.notebook.pages.size,
        )
    }

    fun deleteSelectedPages() {
        if (selectedPageIds.isEmpty()) return
        val before = workingNotebook
        val result = NotePageManager.deletePages(
            notebook = workingNotebook,
            selectedPageIds = selectedPageIds,
            currentIndex = safeIndex,
        )
        if (result.notebook == workingNotebook) return
        applyChange(result.notebook)
        queueRemovedTemplateAssets(before, workingNotebook)
        pageSelection = result.selectedPageIds
        if (result.currentIndex != safeIndex) onPageIndexChange(result.currentIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "page_delete",
            "selected" to selectedPageIds.size,
            "pagesAfter" to result.notebook.pages.size,
        )
    }

    fun applyTemplate(template: PaperTemplate, allPages: Boolean) {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val updatedPages = if (allPages) {
            currentPages.map { it.copy(template = template) }
        } else {
            val index = safeIndex.coerceIn(0, currentPages.lastIndex)
            currentPages.toMutableList().also {
                it[index] = it[index].copy(template = template)
            }
        }
        if (updatedPages == currentPages) return
        applyChange(workingNotebook.copy(pages = updatedPages))
    }

    fun applyStudio(choice: NoteTemplateStudioChoice) {
        val before = workingNotebook
        val next = NoteTemplateStudio.apply(before, safeIndex, choice)
        if (next == before) return
        val keysBefore = CustomTemplatePaths.keysInPages(before.pages)
        val keysAfter = CustomTemplatePaths.keysInPages(next.pages)
        applyChange(next)
        queueTemplateAssetCleanup((keysBefore - keysAfter) + (keysAfter - keysBefore))
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "template_studio",
            "template" to choice.template.name,
            "tint" to choice.tint.name,
            "orientation" to choice.orientation.name,
            "applyAll" to choice.applyToAllPages,
            "coverStyle" to choice.coverStyle.name,
            "customBackground" to (choice.customBackground != null),
            "pages" to next.pages.size,
            "annotations" to notebookAnnotationCount(next),
        )
    }

    /**
     * Rotates the selected pages, or the current page when nothing is selected.
     * Page ids, order, bookmarks, source links and selection survive; [applyChange]
     * commits the whole result as one undo step.
     */
    fun rotatePages(clockwise: Boolean) {
        val result = NotePageManager.rotatePages(
            notebook = workingNotebook,
            selectedPageIds = selectedPageIds,
            currentIndex = safeIndex,
            clockwise = clockwise,
        )
        if (result.notebook == workingNotebook) return
        applyChange(result.notebook)
        pageSelection = result.selectedPageIds
        if (result.currentIndex != safeIndex) onPageIndexChange(result.currentIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "page_rotate",
            "direction" to if (clockwise) "cw" else "ccw",
            "pages" to if (selectedPageIds.isEmpty()) 1 else selectedPageIds.size,
            "pageIndex" to safeIndex,
        )
    }

    /**
     * Applies an imported photo as a new page after [afterPageId]. The asset stays
     * in `files/templates/` and is referenced by its portable key, exactly like a
     * Template Studio background. Returns false when the page cannot be inserted,
     * so the caller can delete the just-imported orphan asset.
     */
    fun insertPhotoPage(asset: CustomTemplateAsset, afterPageId: String): Boolean {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val targetIndex = currentPages.indexOfFirst { it.id == afterPageId }
            .takeIf { it >= 0 }
            ?: safeIndex.coerceIn(0, currentPages.lastIndex)
        val result = NotePageManager.insertPhotoPage(
            notebook = workingNotebook,
            currentIndex = targetIndex,
            selectedPageIds = NotePageManager.validSelection(currentPages, pageSelection),
            assetKey = asset.key,
            displayName = asset.displayName,
        )
        if (result.notebook == workingNotebook) return false
        applyChange(result.notebook)
        pageSelection = result.selectedPageIds
        // Keep the new asset available for one-step undo, then clean it on close
        // only if no live notebook still references the key.
        queueTemplateAssetCleanup(setOf(asset.key))
        if (result.currentIndex != pageIndex) onPageIndexChange(result.currentIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "photo_page",
            "pages" to result.notebook.pages.size,
            "pageIndex" to result.currentIndex,
            "asset" to asset.key,
        )
        return true
    }

    /**
     * Applies an imported photo to [targetPageId] while keeping its orientation,
     * template, tint, annotations and source link. The old background file is kept
     * until the editor closes so one-step undo can restore it.
     */
    fun applyPhotoBackground(asset: CustomTemplateAsset, targetPageId: String): Boolean {
        val currentPages = workingNotebook.pages.ifEmpty { listOf(NotePage()) }
        val targetIndex = currentPages.indexOfFirst { it.id == targetPageId }
        if (targetIndex < 0) {
            photoError = "사진을 적용할 페이지를 찾을 수 없습니다."
            return false
        }
        val previous = currentPages[targetIndex]
        val updated = previous.copy(
            customBackground = asset.key,
            customBackgroundName = CustomTemplatePaths.normalizeName(asset.displayName),
        )
        if (updated == previous) return false
        val mutable = currentPages.toMutableList()
        mutable[targetIndex] = updated
        val before = workingNotebook
        applyChange(workingNotebook.copy(pages = mutable))
        queueRemovedTemplateAssets(before, workingNotebook)
        queueTemplateAssetCleanup(setOf(asset.key))
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "photo_background",
            "pageIndex" to targetIndex,
            "asset" to asset.key,
            "pages" to workingNotebook.pages.size,
        )
        return true
    }

    fun importPhoto(uri: Uri, forNewPage: Boolean, targetPageId: String) {
        if (photoImporting) return
        photoError = null
        photoImporting = true
        scope.launch {
            val imported = runCatching { customTemplateStore.importImage(uri) }
            photoImporting = false
            imported.onSuccess { asset ->
                val applied = if (forNewPage) {
                    insertPhotoPage(asset, targetPageId)
                } else {
                    applyPhotoBackground(asset, targetPageId)
                }
                if (!applied) {
                    customTemplateStore.delete(asset.key)
                } else {
                    snackbarHostState.showSnackbar(
                        if (forNewPage) {
                            "사진 페이지를 추가했습니다 · 실행 취소로 되돌릴 수 있습니다"
                        } else {
                            "현재 페이지 사진 배경을 적용했습니다 · 실행 취소로 되돌릴 수 있습니다"
                        },
                    )
                }
            }.onFailure { error ->
                photoError = error.message ?: "사진을 가져오지 못했습니다."
            }
        }
    }

    val photoPagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { importPhoto(it, forNewPage = true, targetPageId = page.id) } }
    val photoBackgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { importPhoto(it, forNewPage = false, targetPageId = page.id) } }

    fun rename(title: String) {
        val clean = title.trim().ifBlank { "새 노트" }
        if (clean == workingNotebook.title) return
        applyChange(workingNotebook.copy(title = clean))
    }

    fun toggleFavorite() {
        applyChange(workingNotebook.copy(favorite = !workingNotebook.favorite))
    }

    fun addStroke(points: List<NormalizedPoint>, durationMs: Long = 0L) {
        if (points.isEmpty()) return
        val layer = activeLayer ?: return
        if (layer.locked) return
        var type = noteAnnotationTypeForTool(tool) ?: return
        var committedPoints = points
        val safeDuration = durationMs.coerceAtLeast(0L)

        if (tool == Tool.PEN && type == AnnotationType.PEN && shapeRecognition) {
            val recognized = NoteInk.recognizeShape(points)
            NoteTrace.event(
                NOTE_TRACE_TAG,
                "shape",
                "recognized" to (recognized != null),
                "type" to (recognized?.type?.name ?: AnnotationType.PEN.name),
                "pointsBefore" to points.size,
                "pointsAfter" to (recognized?.points?.size ?: points.size),
                "confidence" to (recognized?.confidence ?: 0f),
            )
            if (recognized != null) {
                type = recognized.type
                committedPoints = recognized.points
            }
        }

        if (strokeStabilization &&
            (type == AnnotationType.PEN || type == AnnotationType.HIGHLIGHT)
        ) {
            val stabilized = NoteInk.stabilize(points, NOTE_STABILIZE_STRENGTH)
            NoteTrace.event(
                NOTE_TRACE_TAG,
                "stabilize",
                "tool" to tool.name,
                "before" to points.size,
                "after" to stabilized.size,
                "strength" to NOTE_STABILIZE_STRENGTH,
                "applied" to (stabilized !== points),
            )
            committedPoints = stabilized
        }

        if (type == AnnotationType.PEN || type == AnnotationType.HIGHLIGHT) {
            val pressured = committedPoints.count { it.pressure < 1f }
            NoteTrace.event(
                NOTE_TRACE_TAG,
                "pressure",
                "enabled" to pressureInk,
                "curve" to pressureCurve.name,
                "style" to penStyle.name,
                "points" to committedPoints.size,
                "pressured" to pressured,
                "min" to (committedPoints.minOfOrNull { it.pressure } ?: 1f),
                "max" to (committedPoints.maxOfOrNull { it.pressure } ?: 1f),
            )
        }

        val annotation = Annotation(
            type = type,
            page = 0,
            color = color,
            width = noteStrokeWidth(type, strokeWidth),
            points = committedPoints,
            opacity = if (type == AnnotationType.HIGHLIGHT) 1f else opacity,
            penStyle = if (type == AnnotationType.PEN) penStyle else PenStyle.BALLPOINT,
            layerId = layer.id,
            createdAt = System.currentTimeMillis() - safeDuration,
            durationMs = safeDuration,
        )
        val before = workingNotebook
        val next = noteAppendAnnotation(before, safeIndex, annotation)
        if (next == before) return
        val beforePage = before.pages.getOrNull(safeIndex)
        val afterPage = next.pages.getOrNull(safeIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "stroke",
            "page" to (beforePage?.id ?: page.id),
            "pageIndex" to safeIndex,
            "annotation" to annotation.id,
            "annotationsBefore" to (beforePage?.annotations?.size ?: 0),
            "annotationsAfter" to (afterPage?.annotations?.size ?: 0),
            "points" to committedPoints.size,
            "rawPoints" to points.size,
            "type" to type.name,
            "tool" to tool.name,
        )
        applyChange(next)
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
                            point.copy(
                                x = (point.x + 0.012f).coerceIn(0f, 1f),
                                y = (point.y + 0.012f).coerceIn(0f, 1f),
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

    fun transformSelection(transform: NoteSelectionTransform, kind: String) {
        if (selectedIds.isEmpty() || transform.isIdentity) return
        var changedCount = 0
        updateCurrentPage { pageToEdit ->
            val selected = pageToEdit.annotations.filter { it.id in selectedIds }
            if (selected.isEmpty()) {
                pageToEdit
            } else {
                val transformed = transform.applyTo(
                    selected,
                    pageAspect = NotePaper.aspect(page.orientation),
                )
                if (transformed == selected) {
                    pageToEdit
                } else {
                    val byId = transformed.associateBy { it.id }
                    changedCount = transformed.size
                    pageToEdit.copy(
                        annotations = pageToEdit.annotations.map { byId[it.id] ?: it },
                    )
                }
            }
        }
        if (changedCount == 0) return
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "transform",
            "kind" to kind,
            "count" to changedCount,
            "dx" to transform.translateX,
            "dy" to transform.translateY,
            "scale" to transform.scale,
            "rotation" to transform.rotationDegrees,
            "pageIndex" to safeIndex,
            "annotations" to notebookAnnotationCount(workingNotebook),
        )
    }

    fun copySelection() {
        if (selectedIds.isEmpty()) return
        val currentPage = workingNotebook.pages.getOrNull(safeIndex) ?: return
        val selected = currentPage.annotations.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        clipboard = NoteClipboard.fromSelection(selected, safeIndex)
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "copy",
            "count" to selected.size,
            "clipboard" to clipboard.size,
            "sourcePageIndex" to safeIndex,
        )
    }

    fun pasteClipboard() {
        if (clipboard.isEmpty) return
        val layer = activeLayer ?: page.layers.firstOrNull() ?: return
        if (layer.locked) return
        val pasted = clipboard.paste(targetLayerId = layer.id)
        if (pasted.isEmpty()) return
        val pastedIds = pasted.map { it.id }.toSet()
        updateCurrentPage { pageToEdit ->
            pageToEdit.copy(annotations = pageToEdit.annotations + pasted)
        }
        // Keep the pasted ink selected so follow-up nudges/scales apply to it.
        selection = pastedIds
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "paste",
            "count" to pasted.size,
            "clipboard" to clipboard.size,
            "sourcePageIndex" to clipboard.sourcePageIndex,
            "pageIndex" to safeIndex,
            "offsetX" to NOTE_CLIPBOARD_PASTE_OFFSET,
            "offsetY" to NOTE_CLIPBOARD_PASTE_OFFSET,
            "layer" to layer.name,
            "annotations" to notebookAnnotationCount(workingNotebook),
        )
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
        var removed = 0
        var split = 0
        var segments = 0
        updateCurrentPage { pageToEdit ->
            val result = NoteInk.eraseAnnotations(
                annotations = pageToEdit.annotations,
                trail = trail,
                radius = NOTE_ERASE_RADIUS,
                targetLayerId = layer.id,
                defaultLayerId = pageToEdit.layers.firstOrNull()?.id,
                partial = partialEraser,
            )
            removed = result.removed
            split = result.split
            segments = result.segments
            if (result.changed) {
                pageToEdit.copy(annotations = result.annotations)
            } else {
                pageToEdit
            }
        }
        NoteTrace.event(
            NOTE_TRACE_TAG,
            "erase",
            "partial" to partialEraser,
            "removed" to removed,
            "split" to split,
            "segments" to segments,
            "trailPoints" to trail.size,
            "layer" to layer.name,
        )
    }

    val canUndo = remember(workingNotebook.id, historyVersion) { undoStack.isNotEmpty() }
    val canRedo = remember(workingNotebook.id, historyVersion) { redoStack.isNotEmpty() }
    val fingerMode = if (fingerWriting) "손가락 필기" else "스타일러스 필기 · 손가락 이동"
    val hint = buildString {
        append(fingerMode)
        append(" · ")
        append(noteToolHint(tool))
        append(" · ${activeLayer?.name ?: "레이어"}")
        if (activeLayer?.locked == true) append(" · 잠김")
        append(if (zoomLock) " · 줌 잠금" else " · 줌 가능")
    }
    val compactHint = noteCompactHint(
        tool = tool,
        layerName = activeLayer?.name.orEmpty(),
        layerLocked = activeLayer?.locked == true,
        zoomLock = zoomLock,
    )
    val canvasHint = if (compact) compactHint else hint

    val darkPaper = false

    val leaveEditor: () -> Unit = {
        flushPendingTemplateCleanup()
        onBack()
    }
    // Covers leaving through the source-PDF/study-pane parent, which does not call
    // the top-bar back button. The undo stack dies with this editor.
    val cleanupOnDispose by rememberUpdatedState { flushPendingTemplateCleanup() }
    DisposableEffect(Unit) {
        onDispose { cleanupOnDispose() }
    }

    BackHandler(onBack = leaveEditor)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // The compact editor is hosted below the PDF app bar, so the system-bar
        // insets are already owned outside the pane. Zero here avoids a second
        // status-bar gap above the canvas.
        contentWindowInsets = if (compact) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (!compact) {
                TopAppBar(
                    title = {
                        Text(
                            workingNotebook.title,
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
                        IconButton(onClick = leaveEditor) {
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
                                if (workingNotebook.favorite) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.Star
                                },
                                contentDescription = "즐겨찾기",
                                tint = if (workingNotebook.favorite) {
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
            }
        },
        bottomBar = {
            Column {
                if (selectedIds.isNotEmpty()) {
                    NoteSelectionBar(
                        count = selectedIds.size,
                        onEdit = { showSelectionSheet = true },
                        onColor = { recolorSelection(it) },
                        onDuplicate = { duplicateSelection() },
                        onDelete = { deleteSelection() },
                        onClear = { selection = emptySet() },
                    )
                } else if (clipboard.isNotEmpty) {
                    NoteClipboardBar(
                        count = clipboard.size,
                        sourcePageIndex = clipboard.sourcePageIndex,
                        onPaste = { pasteClipboard() },
                        onEdit = { showSelectionSheet = true },
                        onClear = { clipboard = NoteClipboard() },
                    )
                }
                if (compact) {
                    NoteCompactDock(
                        tool = tool,
                        onTool = { tool = it },
                        color = color,
                        onOpenStyle = { showStyle = true },
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = { undo() },
                        onRedo = { redo() },
                        favorite = workingNotebook.favorite,
                        onToggleFavorite = { toggleFavorite() },
                        onOpenOverflow = { showOverflow = true },
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
                } else {
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
            }
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val wide = maxWidth >= 840.dp && !compact
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
                        pressureEnabled = pressureInk,
                        pressureCurve = pressureCurve,
                        gestureUndoEnabled = gestureUndo,
                        selectionBounds = selectionBounds,
                        annotationProgress = annotationProgress,
                        backgroundBitmap = pageBackground,
                        hint = canvasHint,
                        compact = compact,
                        onAddStroke = { points, durationMs -> addStroke(points, durationMs) },
                        onErase = { erase(it) },
                        onRequestText = { textPoint = it },
                        onLassoSelect = { polygon, rectangular -> selectWithLasso(polygon, rectangular) },
                        onGestureUndo = { undo() },
                        onGestureRedo = { redo() },
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
                        templateStore = customTemplateStore,
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
                    pressureEnabled = pressureInk,
                    pressureCurve = pressureCurve,
                    gestureUndoEnabled = gestureUndo,
                    selectionBounds = selectionBounds,
                    annotationProgress = annotationProgress,
                    backgroundBitmap = pageBackground,
                    hint = canvasHint,
                    compact = compact,
                    onAddStroke = { points, durationMs -> addStroke(points, durationMs) },
                    onErase = { erase(it) },
                    onRequestText = { textPoint = it },
                    onLassoSelect = { polygon, rectangular -> selectWithLasso(polygon, rectangular) },
                    onGestureUndo = { undo() },
                    onGestureRedo = { redo() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showRename) {
        NoteRenameDialog(
            initial = workingNotebook.title,
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                rename(it)
            },
        )
    }

    if (showStudio) {
        NoteTemplateStudioSheet(
            notebook = workingNotebook,
            currentIndex = safeIndex,
            onDismiss = { showStudio = false },
            onApply = { choice ->
                showStudio = false
                applyStudio(choice)
            },
            templateStore = customTemplateStore,
        )
    }

    if (showQuickTemplates) {
        NoteTemplateDialog(
            current = page.template,
            tint = page.tint,
            orientation = page.orientation,
            hasCustomBackground = page.customBackground != null,
            onDismiss = { showQuickTemplates = false },
            onApply = { template, allPages ->
                showQuickTemplates = false
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
            ownerId = workingNotebook.id,
            currentPage = safeIndex,
            onDismiss = { showAudioNotes = false },
        )
    }

    if (showAudioReplay) {
        AudioReplaySheet(
            ownerId = workingNotebook.id,
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
            notebook = workingNotebook,
            currentPage = safeIndex,
            onJump = { onPageIndexChange(it) },
            onBookmarksChange = { applyChange(it) },
            onDismiss = { showOutline = false },
        )
    }

    if (showSearch) {
        NoteSearchSheet(
            notebook = workingNotebook,
            onJump = {
                onPageIndexChange(it)
                showSearch = false
            },
            onDismiss = { showSearch = false },
        )
    }

    if (photoImporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("사진 가져오는 중") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("사진을 앱 저장소로 복사하고 있습니다.")
                }
            },
            confirmButton = {},
        )
    }

    photoError?.let { message ->
        AlertDialog(
            onDismissRequest = { photoError = null },
            title = { Text("사진을 가져오지 못했습니다") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { photoError = null }) {
                    Text("확인")
                }
            },
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

    if (showSelectionSheet) {
        NoteSelectionSheet(
            selectedCount = selectedIds.size,
            clipboardCount = clipboard.size,
            clipboardSourcePage = clipboard.sourcePageIndex.takeIf { clipboard.isNotEmpty },
            onNudge = { dx, dy ->
                transformSelection(NoteSelectionTransform.translate(dx, dy), "nudge")
            },
            onScale = { factor ->
                transformSelection(NoteSelectionTransform.scaleAroundCenter(factor), "scale")
            },
            onRotate = { degrees ->
                transformSelection(
                    NoteSelectionTransform.rotateAroundCenter(degrees.toFloat()),
                    "rotate",
                )
            },
            onCopy = { copySelection() },
            onPaste = { pasteClipboard() },
            onDuplicate = { duplicateSelection() },
            onDelete = {
                deleteSelection()
                showSelectionSheet = false
            },
            onDismiss = { showSelectionSheet = false },
        )
    }

    if (showPageSheet) {
        NotePageManagerSheet(
            pages = pages,
            currentIndex = safeIndex,
            darkPaper = darkPaper,
            templateStore = customTemplateStore,
            selectedPageIds = selectedPageIds,
            onToggleSelection = { pageId ->
                pageSelection = NotePageManager.toggleSelection(pages, selectedPageIds, pageId)
            },
            onClearSelection = { pageSelection = emptySet() },
            onSelect = {
                onPageIndexChange(it)
                showPageSheet = false
            },
            onAdd = {
                showPageSheet = false
                addPage()
            },
            onAddPhoto = {
                showPageSheet = false
                photoPagePicker.launch(arrayOf("image/*"))
            },
            onRotateClockwise = { rotatePages(clockwise = true) },
            onRotateCounterClockwise = { rotatePages(clockwise = false) },
            onDuplicate = { duplicateSelectedPages() },
            onDelete = { deleteSelectedPages() },
            onDismiss = { showPageSheet = false },
        )
    }

    if (showExportOptions) {
        NoteExportOptionsSheet(
            notebookTitle = workingNotebook.title,
            pageCount = pages.size,
            currentIndex = safeIndex,
            selectedPageIds = selectedPageIds,
            onDismiss = { showExportOptions = false },
            onExport = { options ->
                showExportOptions = false
                onExportPdf(options)
            },
        )
    }

    if (showOverflow) {
        NoteOverflowSheet(
            notebookTitle = workingNotebook.title,
            pageIndex = safeIndex,
            pageCount = pages.size,
            templateLabel = page.template.label,
            layerName = activeLayer?.name ?: "레이어",
            layerCount = page.layers.size,
            selectedPageCount = selectedPageIds.size,
            canMoveUp = safeIndex > 0,
            canMoveDown = safeIndex < pages.lastIndex,
            canDeletePage = pages.size > 1,
            fingerWriting = fingerWriting,
            zoomLock = zoomLock,
            strokeStabilization = strokeStabilization,
            shapeRecognition = shapeRecognition,
            partialEraser = partialEraser,
            pressureInk = pressureInk,
            pressureCurve = pressureCurve,
            gestureUndo = gestureUndo,
            onFingerWriting = { fingerWriting = it },
            onZoomLock = { zoomLock = it },
            onStrokeStabilization = { strokeStabilization = it },
            onShapeRecognition = { shapeRecognition = it },
            onPartialEraser = { partialEraser = it },
            onPressureInk = { pressureInk = it },
            onPressureCurve = { pressureCurve = it },
            onGestureUndo = { gestureUndo = it },
            onTemplate = {
                showOverflow = false
                showStudio = true
            },
            onQuickTemplate = {
                showOverflow = false
                showQuickTemplates = true
            },
            onLayers = {
                showOverflow = false
                showLayers = true
            },
            onOutline = {
                showOverflow = false
                showOutline = true
            },
            onSearch = {
                showOverflow = false
                showSearch = true
            },
            onAudioNotes = {
                showOverflow = false
                showAudioNotes = true
            },
            onAudioReplay = {
                showOverflow = false
                showAudioReplay = true
            },
            onOpenSourcePdf = if (
                workingNotebook.sourceDocumentUri.isNotBlank() && onOpenSourcePdf != null
            ) {
                {
                    showOverflow = false
                    val sourcePage = StudyNoteBuilder.sourcePage(workingNotebook, safeIndex) ?: 0
                    onOpenSourcePdf.invoke(sourcePage)
                }
            } else {
                null
            },
            onAddPage = {
                showOverflow = false
                addPage()
            },
            onPhotoPage = {
                showOverflow = false
                photoPagePicker.launch(arrayOf("image/*"))
            },
            onPhotoBackground = {
                showOverflow = false
                photoBackgroundPicker.launch(arrayOf("image/*"))
            },
            onRotateClockwise = {
                showOverflow = false
                rotatePages(clockwise = true)
            },
            onRotateCounterClockwise = {
                showOverflow = false
                rotatePages(clockwise = false)
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
                showExportOptions = true
            },
            onOpenTrace = if (BuildConfig.DEBUG) {
                {
                    showOverflow = false
                    showTrace = true
                }
            } else {
                null
            },
            onRename = {
                showOverflow = false
                showRename = true
            },
            onDismiss = { showOverflow = false },
        )
    }

    if (BuildConfig.DEBUG && showTrace) {
        NoteTraceSheet(onDismiss = { showTrace = false })
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
    pressureEnabled: Boolean,
    pressureCurve: PressureCurve,
    gestureUndoEnabled: Boolean,
    selectionBounds: NoteSelectionBounds?,
    annotationProgress: (Annotation) -> Float,
    backgroundBitmap: android.graphics.Bitmap?,
    hint: String,
    compact: Boolean = false,
    onAddStroke: (List<NormalizedPoint>, Long) -> Unit,
    onErase: (List<NormalizedPoint>) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onLassoSelect: (List<NormalizedPoint>, Boolean) -> Unit,
    onGestureUndo: () -> Unit,
    onGestureRedo: () -> Unit,
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
            pressureEnabled = pressureEnabled,
            pressureCurve = pressureCurve,
            gestureUndoEnabled = gestureUndoEnabled,
            selectionBounds = selectionBounds,
            annotationProgress = annotationProgress,
            backgroundBitmap = backgroundBitmap,
            onAddStroke = onAddStroke,
            onErase = onErase,
            onRequestText = onRequestText,
            onLassoSelect = onLassoSelect,
            onGestureUndo = onGestureUndo,
            onGestureRedo = onGestureRedo,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (compact) NOTE_COMPACT_CANVAS_PADDING_DP.dp else 10.dp,
                    vertical = if (compact) NOTE_COMPACT_CANVAS_PADDING_DP.dp else 8.dp,
                ),
        )
        if (!compact || noteCompactHintVisible(tool)) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(
                    alpha = if (compact) 0.84f else 0.92f,
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (compact) 2.dp else 10.dp),
            ) {
                Text(
                    hint,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 8.dp else 12.dp,
                        vertical = if (compact) 2.dp else 6.dp,
                    ),
                    style = if (compact) {
                        MaterialTheme.typography.labelSmall
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else Int.MAX_VALUE,
                    overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
                )
            }
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

/**
 * Focused-pane variant of the note dock: one primary tool row and one slim row
 * with colour/style, undo/redo, favourite, overflow and the page pill. The study
 * pane header supplies the title and close action, so no editor app bar is shown.
 */
@Composable
private fun NoteCompactDock(
    tool: Tool,
    onTool: (Tool) -> Unit,
    color: Int,
    onOpenStyle: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenOverflow: () -> Unit,
    pageIndex: Int,
    pageCount: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onOpenPages: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        // The study pane already ends above the viewer's navigation-bar-aware
        // tool dock, so adding navigationBarsPadding here would only create a
        // dead strip between the note dock and the PDF controls.
        Column(Modifier.fillMaxWidth()) {
            if (noteIsShapeTool(tool)) {
                NoteToolTray(
                    label = "도형",
                    options = NOTE_SHAPE_TOOLS.map { it to noteShapeLabel(it) },
                    selectedTool = tool,
                    onTool = onTool,
                    compact = true,
                )
            }
            if (noteIsLassoTool(tool)) {
                NoteToolTray(
                    label = "선택",
                    options = NOTE_LASSO_TOOLS.map { it to noteLassoLabel(it) },
                    selectedTool = tool,
                    onTool = onTool,
                    compact = true,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NOTE_COMPACT_TOOL_ROW_HEIGHT_DP.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NOTE_COMPACT_DOCK_TOOLS.forEach { dockTool ->
                    val selected = when (dockTool) {
                        Tool.LASSO -> noteIsLassoTool(tool)
                        Tool.RECT -> noteIsShapeTool(tool)
                        else -> tool == dockTool
                    }
                    NoteCompactDockTool(
                        icon = noteDockToolIcon(dockTool),
                        label = noteCompactToolLabel(dockTool),
                        selected = selected,
                        onClick = {
                            onTool(
                                when (dockTool) {
                                    Tool.LASSO -> if (noteIsLassoTool(tool)) tool else Tool.LASSO
                                    Tool.RECT -> if (noteIsShapeTool(tool)) tool else Tool.RECT
                                    else -> dockTool
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NOTE_COMPACT_ACTION_ROW_HEIGHT_DP.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoteColorDot(
                    color = color,
                    selected = true,
                    onClick = onOpenStyle,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    size = 24.dp,
                )
                NoteCompactActionButton(
                    icon = Icons.Filled.Palette,
                    contentDescription = "스타일",
                    onClick = onOpenStyle,
                )
                NoteCompactActionButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "실행 취소",
                    onClick = onUndo,
                    enabled = canUndo,
                )
                NoteCompactActionButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "다시 실행",
                    onClick = onRedo,
                    enabled = canRedo,
                )
                NoteCompactActionButton(
                    icon = if (favorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "즐겨찾기",
                    onClick = onToggleFavorite,
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                NoteCompactActionButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "더보기",
                    onClick = onOpenOverflow,
                )
                Spacer(Modifier.weight(1f))
                NotePagePill(
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    onPrevious = onPreviousPage,
                    onNext = onNextPage,
                    onOpenPages = onOpenPages,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun NoteCompactDockTool(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
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
                .padding(vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(1.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NoteCompactActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(32.dp),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = if (enabled) {
                tint
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

private fun noteDockToolIcon(tool: Tool): ImageVector = when (tool) {
    Tool.PEN -> Icons.Filled.Edit
    Tool.HIGHLIGHT -> Icons.Filled.Highlight
    Tool.ERASER -> Icons.Filled.AutoFixNormal
    Tool.LASSO, Tool.LASSO_RECT -> Icons.Filled.Gesture
    Tool.TEXT -> Icons.Filled.TextFields
    Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> Icons.Filled.Category
    else -> Icons.Filled.Edit
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
    compact: Boolean = false,
) {
    val rowHeight = if (compact) 30.dp else 36.dp
    val buttonSize = if (compact) 26.dp else 30.dp
    val iconSize = if (compact) 16.dp else 18.dp
    val horizontalPadding = if (compact) 0.dp else 2.dp
    val verticalPadding = if (compact) 4.dp else 8.dp
    Surface(
        onClick = onOpenPages,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.height(rowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = notePagePreviousEnabled(pageIndex),
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "이전 페이지",
                    modifier = Modifier.size(iconSize),
                )
            }
            Text(
                "${pageIndex + 1} / $pageCount",
                modifier = Modifier.padding(
                    horizontal = horizontalPadding,
                    vertical = verticalPadding,
                ),
                style = if (compact) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelMedium
                },
                maxLines = 1,
            )
            IconButton(
                onClick = onNext,
                enabled = notePageNextEnabled(pageIndex, pageCount),
                modifier = Modifier.size(buttonSize),
            ) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "다음 페이지",
                    modifier = Modifier.size(iconSize),
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
    compact: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) 8.dp else 10.dp,
                    vertical = if (compact) 2.dp else 4.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
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
    onEdit: () -> Unit,
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
            TextButton(onClick = onEdit) {
                Text("편집")
            }
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

/**
 * Small always-available paste strip shown when a clipboard exists but nothing is
 * selected. This is what makes copy → switch page → paste work without having to
 * select an unrelated annotation first.
 */
@Composable
private fun NoteClipboardBar(
    count: Int,
    sourcePageIndex: Int,
    onPaste: () -> Unit,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
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
                "클립보드 ${count}개 · ${sourcePageIndex + 1}쪽",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onPaste) {
                Text("붙여넣기")
            }
            TextButton(onClick = onEdit) {
                Text("편집")
            }
            TextButton(onClick = onClear) {
                Text("비우기")
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
private fun NotePageManagerSheet(
    pages: List<NotePage>,
    currentIndex: Int,
    darkPaper: Boolean,
    templateStore: CustomTemplateStore,
    selectedPageIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onAddPhoto: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeCurrent = if (pages.isEmpty()) 0 else currentIndex.coerceIn(0, pages.lastIndex)
    var filter by remember { mutableStateOf(NotePageFilter.ALL) }
    var selectMode by remember { mutableStateOf(selectedPageIds.isNotEmpty()) }
    val entries = remember(pages, filter) { NotePageManager.entries(pages, filter) }
    val annotatedCount = remember(pages) { NotePageManager.annotatedCount(pages) }
    val canDelete = selectedPageIds.isNotEmpty() && selectedPageIds.size < pages.size

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("페이지 관리", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${pages.size}쪽 · 필기 ${annotatedCount}쪽 · 선택 ${selectedPageIds.size}개",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { selectMode = !selectMode },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text(if (selectMode) "완료" else "선택")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("닫기")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = filter == NotePageFilter.ALL,
                    onClick = { filter = NotePageFilter.ALL },
                    label = { Text(NotePageFilter.ALL.label) },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                FilterChip(
                    selected = filter == NotePageFilter.ANNOTATED,
                    onClick = { filter = NotePageFilter.ANNOTATED },
                    label = { Text(NotePageFilter.ANNOTATED.label) },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
            if (filter == NotePageFilter.ANNOTATED && entries.isEmpty()) {
                Text(
                    "아직 필기가 있는 페이지가 없습니다.",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = entries, key = { it.id }) { entry ->
                    NoteManagerPageCard(
                        page = pages[entry.index],
                        number = entry.index,
                        current = entry.index == safeCurrent,
                        checked = entry.id in selectedPageIds,
                        showCheckbox = selectMode,
                        darkPaper = darkPaper,
                        templateStore = templateStore,
                        onOpen = { onSelect(entry.index) },
                        onCheckedChange = { onToggleSelection(entry.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "add-page") {
                    NoteAddPageCard(
                        orientation = pages.getOrNull(safeCurrent)?.orientation
                            ?: PageOrientation.PORTRAIT,
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                if (selectedPageIds.isEmpty()) {
                    "추가·회전은 현재 ${safeCurrent + 1}쪽에 적용됩니다."
                } else {
                    "회전은 선택한 ${selectedPageIds.size}쪽에 한 번에 적용됩니다."
                },
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onAdd,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("페이지 추가")
                }
                TextButton(
                    onClick = onAddPhoto,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("사진 페이지")
                }
                TextButton(
                    onClick = onRotateClockwise,
                    enabled = pages.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("↻ 시계 회전")
                }
                TextButton(
                    onClick = onRotateCounterClockwise,
                    enabled = pages.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("↺ 반시계 회전")
                }
                TextButton(
                    onClick = onDuplicate,
                    enabled = selectedPageIds.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("복제")
                }
                TextButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("삭제")
                }
                TextButton(
                    onClick = onClearSelection,
                    enabled = selectedPageIds.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("선택 해제")
                }
            }
        }
    }
}

@Composable
private fun NoteManagerPageCard(
    page: NotePage,
    number: Int,
    current: Boolean,
    checked: Boolean,
    showCheckbox: Boolean,
    darkPaper: Boolean,
    templateStore: CustomTemplateStore,
    onOpen: () -> Unit,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = remember(page.customBackground) {
        page.customBackground?.let { templateStore.loadThumbnail(it) }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth()) {
            Surface(
                onClick = onOpen,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(
                    width = if (current || checked) 2.dp else 1.dp,
                    color = when {
                        checked -> MaterialTheme.colorScheme.secondary
                        current -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(NotePaper.aspect(page.orientation)),
            ) {
                NotePagePreview(
                    page = page,
                    darkPaper = darkPaper,
                    backgroundBitmap = background,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showCheckbox) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onCheckedChange() },
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${number + 1}쪽" + if (current) " · 현재" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (current) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "필기 ${page.annotations.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            if (page.customBackground != null) {
                "커스텀 배경 · ${page.orientation.label}"
            } else {
                "${page.template.label} · ${page.orientation.label} · ${page.tint.label}"
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteExportOptionsSheet(
    notebookTitle: String,
    pageCount: Int,
    currentIndex: Int,
    selectedPageIds: Set<String>,
    onDismiss: () -> Unit,
    onExport: (NoteExportOptions) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var range by remember { mutableStateOf(NoteExportPageRange.ALL) }
    var includeHiddenLayers by remember { mutableStateOf(false) }
    val selectedAvailable = selectedPageIds.isNotEmpty()
    val safeCurrent = if (pageCount <= 0) 0 else currentIndex.coerceIn(0, pageCount - 1)
    val safeRange = if (range == NoteExportPageRange.SELECTED && !selectedAvailable) {
        NoteExportPageRange.ALL
    } else {
        range
    }
    val fileName = noteExportFileName(notebookTitle)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("PDF로 내보내기", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "페이지 범위와 레이어를 선택하세요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("닫기")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("페이지 범위", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = safeRange == NoteExportPageRange.ALL,
                    onClick = { range = NoteExportPageRange.ALL },
                    label = { Text("전체 ($pageCount)") },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                FilterChip(
                    selected = safeRange == NoteExportPageRange.CURRENT,
                    onClick = { range = NoteExportPageRange.CURRENT },
                    label = { Text("현재 (${safeCurrent + 1})") },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
                FilterChip(
                    selected = safeRange == NoteExportPageRange.SELECTED,
                    onClick = { range = NoteExportPageRange.SELECTED },
                    enabled = selectedAvailable,
                    label = { Text("선택 (${selectedPageIds.size})") },
                    modifier = Modifier.heightIn(min = 44.dp),
                )
            }
            if (!selectedAvailable) {
                Text(
                    "페이지 관리에서 페이지를 선택하면 선택 내보내기를 사용할 수 있습니다.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Surface(
                onClick = { includeHiddenLayers = !includeHiddenLayers },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("숨긴 레이어 포함", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (includeHiddenLayers) {
                                "숨긴 레이어의 필기도 내보냅니다"
                            } else {
                                "화면에 보이는 레이어만 내보냅니다"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = includeHiddenLayers,
                        onCheckedChange = { includeHiddenLayers = it },
                    )
                }
            }
            Text(
                "파일 이름: $fileName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("취소")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onExport(
                            NoteExportOptions(
                                pageRange = safeRange,
                                currentPageIndex = safeCurrent,
                                selectedPageIds = selectedPageIds,
                                includeHiddenLayers = includeHiddenLayers,
                                suggestedFileName = fileName,
                            ),
                        )
                    },
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("내보내기")
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
    selectedPageCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDeletePage: Boolean,
    fingerWriting: Boolean,
    zoomLock: Boolean,
    strokeStabilization: Boolean,
    shapeRecognition: Boolean,
    partialEraser: Boolean,
    pressureInk: Boolean,
    pressureCurve: PressureCurve,
    gestureUndo: Boolean,
    onFingerWriting: (Boolean) -> Unit,
    onZoomLock: (Boolean) -> Unit,
    onStrokeStabilization: (Boolean) -> Unit,
    onShapeRecognition: (Boolean) -> Unit,
    onPartialEraser: (Boolean) -> Unit,
    onPressureInk: (Boolean) -> Unit,
    onPressureCurve: (PressureCurve) -> Unit,
    onGestureUndo: (Boolean) -> Unit,
    onTemplate: () -> Unit,
    onQuickTemplate: () -> Unit,
    onLayers: () -> Unit,
    onOutline: () -> Unit,
    onSearch: () -> Unit,
    onAudioNotes: () -> Unit,
    onAudioReplay: () -> Unit,
    onOpenSourcePdf: (() -> Unit)?,
    onAddPage: () -> Unit,
    onPhotoPage: () -> Unit,
    onPhotoBackground: () -> Unit,
    onRotateClockwise: () -> Unit,
    onRotateCounterClockwise: () -> Unit,
    onDuplicatePage: () -> Unit,
    onMovePageUp: () -> Unit,
    onMovePageDown: () -> Unit,
    onDeletePage: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenTrace: (() -> Unit)?,
    onRename: (() -> Unit)? = null,
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
            if (onRename != null) {
                item {
                    NoteMenuRow(
                        icon = Icons.Filled.Edit,
                        title = "노트 이름 변경",
                        supporting = "이 스터디 노트의 제목 수정",
                        onClick = onRename,
                    )
                }
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Dashboard,
                    title = "템플릿",
                    supporting = "스튜디오 · 종이·방향·색·표지",
                    onClick = onTemplate,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Dashboard,
                    title = "빠른 템플릿",
                    supporting = "현재: $templateLabel",
                    onClick = onQuickTemplate,
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
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Search,
                    title = "노트 검색",
                    supporting = "메모·텍스트·도장·서명에서 찾기",
                    onClick = onSearch,
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
                    icon = Icons.Filled.Image,
                    title = "사진 페이지 추가",
                    supporting = "이미지를 가져와 현재 페이지 뒤에 새 사진 페이지로",
                    onClick = onPhotoPage,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Image,
                    title = "현재 페이지 사진 배경",
                    supporting = "가져온 이미지를 이 페이지의 종이 배경으로",
                    onClick = onPhotoBackground,
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
                    icon = Icons.Filled.Refresh,
                    title = "시계 방향 회전",
                    supporting = if (selectedPageCount > 0) {
                        "선택 ${selectedPageCount}쪽 · 내용과 용지를 함께 90° 회전"
                    } else {
                        "현재 페이지 · 내용과 용지를 함께 90° 회전"
                    },
                    onClick = onRotateClockwise,
                )
            }
            item {
                NoteMenuRow(
                    icon = Icons.Filled.Refresh,
                    title = "반시계 방향 회전",
                    supporting = if (selectedPageCount > 0) {
                        "선택 ${selectedPageCount}쪽 · 실행 취소로 되돌리기"
                    } else {
                        "현재 페이지 · 실행 취소로 되돌리기"
                    },
                    onClick = onRotateCounterClockwise,
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
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Draw,
                    title = "필기 보정",
                    supporting = "획의 미세한 떨림을 부드럽게 정리 (강도 25%)",
                    checked = strokeStabilization,
                    onCheckedChange = onStrokeStabilization,
                )
            }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = "필압 잉크",
                    supporting = if (pressureInk) {
                        "스타일러스 필압으로 붓·만년필 굵기 변화"
                    } else {
                        "모든 점을 압력 1로 기록"
                    },
                    checked = pressureInk,
                    onCheckedChange = onPressureInk,
                )
            }
            item {
                NotePressureCurveRow(
                    curve = pressureCurve,
                    enabled = pressureInk,
                    onCurve = onPressureCurve,
                )
            }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = "제스처 실행 취소",
                    supporting = if (gestureUndo) {
                        "두 손가락 탭 실행 취소 · 세 손가락 탭 다시 실행"
                    } else {
                        "다중 손가락 탭 제스처 사용 안 함"
                    },
                    checked = gestureUndo,
                    onCheckedChange = onGestureUndo,
                )
            }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.Category,
                    title = "도형 자동 인식",
                    supporting = "펜으로 그린 선·사각형·원을 도형으로 변환",
                    checked = shapeRecognition,
                    onCheckedChange = onShapeRecognition,
                )
            }
            item {
                NoteMenuToggleRow(
                    icon = Icons.Filled.AutoFixNormal,
                    title = "부분 지우개",
                    supporting = if (partialEraser) {
                        "닿은 부분만 지우고 획을 나눔 · 텍스트/도장/도형은 통째로"
                    } else {
                        "닿은 주석을 통째로 지움"
                    },
                    checked = partialEraser,
                    onCheckedChange = onPartialEraser,
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

            if (onOpenTrace != null) {
                item { NoteMenuDivider() }
                item { NoteMenuSection("디버그") }
                item {
                    NoteMenuRow(
                        icon = Icons.Filled.BugReport,
                        title = "필기 추적",
                        supporting = "획·저장·주석 수 변화 기록 보기",
                        onClick = onOpenTrace,
                    )
                }
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

private fun pressureCurveLabel(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "부드럽게"
    PressureCurve.LINEAR -> "선형"
    PressureCurve.FIRM -> "강하게"
}

private fun pressureCurveDescription(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "가벼운 필압을 더 굵게 반영"
    PressureCurve.LINEAR -> "필압에 비례해 굵기 변화"
    PressureCurve.FIRM -> "충분히 눌러야 굵어짐"
}

@Composable
private fun NotePressureCurveRow(
    curve: PressureCurve,
    enabled: Boolean,
    onCurve: (PressureCurve) -> Unit,
) {
    // 25% and 75% are a compact, device-neutral illustration only. Real stylus
    // digitizers still need on-device pressure tuning.
    val sample = remember(curve) { pressureCurveRange(curve, 0.25f, 0.75f) }
    val diagnostic = "필압 25→75% = 반영 " +
        "${(sample.effectiveMin * 100).toInt()}→${(sample.effectiveMax * 100).toInt()}%"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Draw,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("필압 반응", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${pressureCurveDescription(curve)} · $diagnostic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 34.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PressureCurve.values().forEach { option ->
                FilterChip(
                    selected = option == curve,
                    enabled = enabled,
                    onClick = { onCurve(option) },
                    label = { Text(pressureCurveLabel(option)) },
                )
            }
        }
        Text(
            if (enabled) {
                "기기별 필압 보정은 아직 필요합니다"
            } else {
                "필압 잉크를 켜면 적용됩니다 · 기기별 보정 필요"
            },
            modifier = Modifier.padding(start = 34.dp, top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun NotePageRail(
    pages: List<NotePage>,
    currentIndex: Int,
    darkPaper: Boolean,
    templateStore: CustomTemplateStore,
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
                        templateStore = templateStore,
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
    templateStore: CustomTemplateStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = remember(page.customBackground) {
        page.customBackground?.let { templateStore.loadThumbnail(it) }
    }
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
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NotePaper.aspect(page.orientation)),
        ) {
            NotePagePreview(
                page = page,
                darkPaper = darkPaper,
                backgroundBitmap = background,
                modifier = Modifier.fillMaxSize(),
            )
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
private fun NoteAddPageCard(
    orientation: PageOrientation,
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
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NotePaper.aspect(orientation)),
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
private fun NotePagePreview(
    page: NotePage,
    darkPaper: Boolean,
    backgroundBitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        drawIntoCanvas { canvas ->
            PaperBackground.draw(
                canvas = canvas.nativeCanvas,
                template = page.template,
                width = size.width,
                height = size.height,
                dark = darkPaper,
                tint = page.tint,
            )
            backgroundBitmap?.let { bitmap ->
                PaperBackground.drawCustomBackground(
                    canvas = canvas.nativeCanvas,
                    bitmap = bitmap,
                    width = size.width,
                    height = size.height,
                )
            }
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
    tint: PaperTint,
    orientation: PageOrientation,
    hasCustomBackground: Boolean,
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
                if (hasCustomBackground) {
                    Text(
                        "이 페이지는 커스텀 배경이 종이를 덮고 있습니다. 배경은 템플릿 스튜디오에서 바꾸거나 제거할 수 있습니다.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
                            tint = tint,
                            orientation = orientation,
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
    tint: PaperTint,
    orientation: PageOrientation,
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
                .aspectRatio(NotePaper.aspect(orientation))
                .clip(RoundedCornerShape(3.dp)),
        ) {
            drawIntoCanvas { canvas ->
                PaperBackground.draw(
                    canvas = canvas.nativeCanvas,
                    template = template,
                    width = size.width,
                    height = size.height,
                    dark = false,
                    tint = tint,
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

internal fun noteToolHint(tool: Tool): String = when (tool) {
    Tool.PEN, Tool.HIGHLIGHT -> "획을 그리세요"
    Tool.ERASER -> "획을 문질러 지우세요"
    Tool.TEXT -> "빈 곳을 눌러 텍스트 입력"
    Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> "끌어서 도형 그리기"
    Tool.LASSO -> "끌어서 자유롭게 선택"
    Tool.LASSO_RECT -> "끌어서 사각형으로 선택"
    else -> "필기"
}

internal fun notebookAnnotationCount(notebook: Notebook): Int =
    notebook.pages.sumOf { it.annotations.size }

/**
 * True when a notebook delivered by the parent is a genuinely newer version that
 * the editor should adopt. Equal timestamps are treated as echoes of local edits
 * and never overwrite the working copy, because two edits can share a millisecond.
 */
internal fun shouldAdoptExternalNotebook(local: Notebook, incoming: Notebook): Boolean =
    incoming.id != local.id || incoming.updatedAt > local.updatedAt

/** Appends one annotation to one page without touching the other pages. */
internal fun noteAppendAnnotation(
    notebook: Notebook,
    pageIndex: Int,
    annotation: Annotation,
): Notebook {
    val pages = notebook.pages.ifEmpty { listOf(NotePage()) }
    val index = pageIndex.coerceIn(0, pages.lastIndex)
    val updated = pages.toMutableList()
    updated[index] = pages[index].copy(annotations = pages[index].annotations + annotation)
    return notebook.copy(pages = updated)
}
