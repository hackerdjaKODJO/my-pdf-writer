package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Tool
import kotlin.math.roundToInt

private data class ToolSpec(val tool: Tool, val icon: ImageVector, val labelRes: Int)

private val toolSpecs = listOf(
    ToolSpec(Tool.VIEW, Icons.Outlined.Visibility, R.string.tool_view),
    ToolSpec(Tool.PEN, Icons.Outlined.Edit, R.string.tool_pen),
    ToolSpec(Tool.HIGHLIGHT, Icons.Outlined.Highlight, R.string.tool_highlight),
    ToolSpec(Tool.TEXT, Icons.Outlined.TextFields, R.string.tool_text),
    ToolSpec(Tool.RECT, Icons.Outlined.CropSquare, R.string.tool_rect),
    ToolSpec(Tool.ARROW, Icons.Outlined.NorthEast, R.string.tool_arrow),
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
fun ViewerScreen(vm: PdfViewModel, onExportRequest: (String) -> Unit) {
    val stateFlow = vm.viewer.collectAsStateWithLifecycle()
    val bitmap by vm.pageBitmap.collectAsStateWithLifecycle()
    val state = stateFlow.value ?: return

    val snackbarHostState = remember { SnackbarHostState() }
    var showPages by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var textTarget by remember { mutableStateOf<NormalizedPoint?>(null) }

    val sheetOpen = showPages || showNotes || showSearch || textTarget != null
    BackHandler(enabled = !sheetOpen) { vm.closeDocument() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.clearMessage()
    }

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
                            stringResource(R.string.page_of, state.pageIndex + 1, state.pageCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    IconButton(onClick = { showPages = true }) {
                        Icon(Icons.Outlined.Description, contentDescription = stringResource(R.string.pages))
                    }
                    IconButton(onClick = { showNotes = true }) {
                        Icon(Icons.Outlined.StickyNote2, contentDescription = stringResource(R.string.notes))
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
                    AnnotationToolbar(state, vm)
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
                zoom = state.zoom,
                onZoomChange = vm::setZoom,
                onAddStroke = vm::addStroke,
                onEraseStroke = vm::eraseStroke,
                onRequestText = { textTarget = it },
            )

            val hintRes = when (state.tool) {
                Tool.VIEW -> R.string.hint_view
                Tool.TEXT -> R.string.hint_text
                Tool.ERASER -> R.string.hint_eraser
                else -> R.string.hint_draw
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            ) {
                Text(
                    stringResource(hintRes),
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
    if (showSearch) {
        SearchSheet(state, vm) { showSearch = false }
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

        if (state.tool != Tool.VIEW && state.tool != Tool.ERASER) {
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
                    }

                    Tool.HIGHLIGHT -> Unit

                    else -> {
                        Text(stringResource(R.string.thickness), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.strokeWidth,
                            onValueChange = vm::setStrokeWidth,
                            valueRange = 0.001f..0.012f,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }
        }
    }
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
private fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
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
