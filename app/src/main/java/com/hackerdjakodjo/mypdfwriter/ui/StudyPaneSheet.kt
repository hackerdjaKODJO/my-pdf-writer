package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import kotlinx.coroutines.delay

/**
 * Phone-first study-note bottom pane. It hosts the real [NoteEditorScreen] in the
 * PDF content area, so handwriting, layers, page navigation, audio and export keep
 * their existing behavior while the PDF stays open above it.
 *
 * The pane is not a modal sheet: the caller gives it a fraction of the PDF content
 * area. The pane canvas fills that whole area, and a compact translucent header
 * floats over its top edge instead of reserving a full-width stack. The header's
 * grip resizes live or dismisses the pane, a tiny size popup exposes the 50/70/85%
 * presets, and the capture action reveals the drop target only when needed. PDF
 * excerpts can also be dropped directly on the header. The compact note dock floats
 * at the bottom, so the writing canvas uses the freed height. The link action opens
 * the local To/From index built from
 * [com.hackerdjakodjo.mypdfwriter.data.NotePage.sourcePageIndex].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPaneSheet(
    notebook: Notebook,
    pageIndex: Int,
    heightFraction: Float,
    onHeightFractionChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    onChange: (Notebook) -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onExportPdf: (NoteExportOptions) -> Unit,
    onOpenSourcePdf: (Int) -> Unit,
    onCaptureExcerpt: (text: String, pageIndex: Int) -> Boolean,
    onJumpToSourcePage: (sourcePageIndex: Int, notePageIndex: Int) -> Unit,
    onOpenHelp: () -> Unit = {},
) {
    var showLinks by remember(notebook.id) { mutableStateOf(false) }
    var showCapture by remember(notebook.id) { mutableStateOf(false) }
    var captureMessage by remember(notebook.id) { mutableStateOf<String?>(null) }
    var paneHeightPx by remember { mutableFloatStateOf(0f) }
    val sourceName = notebook.sourceDocumentName.ifBlank { "원본 PDF" }
    val linkCount = remember(notebook.pages) {
        StudyNoteSourceLinks.fromNotebook(notebook).size
    }
    val handleDropExcerpt: (String, Int) -> Unit = { text, page ->
        val added = onCaptureExcerpt(text, page)
        captureMessage = if (added) {
            "PDF p.${page + 1} 발췌를 추가했습니다"
        } else {
            "발췌를 추가하지 못했습니다"
        }
        showCapture = false
    }

    LaunchedEffect(captureMessage) {
        if (captureMessage != null) {
            delay(2200)
            captureMessage = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { paneHeightPx = it.height.toFloat() },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Box(Modifier.fillMaxSize()) {
                NoteEditorScreen(
                    notebook = notebook,
                    pageIndex = pageIndex,
                    onBack = onDismiss,
                    onChange = onChange,
                    onPageIndexChange = onPageIndexChange,
                    onExportPdf = onExportPdf,
                    onOpenSourcePdf = onOpenSourcePdf,
                    compact = true,
                    compactTopInsetDp = studyPaneFloatingHeaderInsetDp(captureOpen = showCapture),
                    onOpenHelp = onOpenHelp,
                )
                StudyPaneFloatingHeader(
                    notebookTitle = notebook.title,
                    sourceName = sourceName,
                    heightFraction = heightFraction,
                    paneHeightPx = paneHeightPx,
                    captureMessage = captureMessage,
                    linkCount = linkCount,
                    captureOpen = showCapture,
                    onHeightFractionChange = onHeightFractionChange,
                    onToggleCapture = { showCapture = !showCapture },
                    onOpenLinks = { showLinks = true },
                    onClose = onDismiss,
                    onDropExcerpt = handleDropExcerpt,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            start = STUDY_PANE_HEADER_SIDE_MARGIN_DP.dp,
                            end = STUDY_PANE_HEADER_SIDE_MARGIN_DP.dp,
                            top = STUDY_PANE_HEADER_TOP_MARGIN_DP.dp,
                        ),
                )
            }
        }

        if (showLinks) {
            StudyPaneLinksSheet(
                notebook = notebook,
                onDismiss = { showLinks = false },
                onJump = { sourcePage, notePage ->
                    showLinks = false
                    onJumpToSourcePage(sourcePage, notePage)
                },
            )
        }
    }
}

@Composable
private fun StudyPaneFloatingHeader(
    notebookTitle: String,
    sourceName: String,
    heightFraction: Float,
    paneHeightPx: Float,
    captureMessage: String?,
    linkCount: Int,
    captureOpen: Boolean,
    onHeightFractionChange: (Float) -> Unit,
    onToggleCapture: () -> Unit,
    onOpenLinks: () -> Unit,
    onClose: () -> Unit,
    onDropExcerpt: (text: String, pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The pane occupies heightFraction of the PDF content area, so its measured
    // height lets the grip translate pixel drags into content-area fractions.
    val latestFraction by rememberUpdatedState(heightFraction)
    val latestHeight by rememberUpdatedState(paneHeightPx)
    val latestOnFraction by rememberUpdatedState(onHeightFractionChange)
    val latestOnDismiss by rememberUpdatedState(onClose)
    var dragStartFraction by remember { mutableFloatStateOf(0f) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    var dragContentHeightPx by remember { mutableFloatStateOf(0f) }
    var showSizeMenu by remember { mutableStateOf(false) }
    var dropActive by remember { mutableStateOf(false) }
    val dropModifier = rememberStudyNoteDropTarget(
        onDrop = onDropExcerpt,
        onActiveChange = { dropActive = it },
    )
    val subtitle = when {
        captureMessage != null -> captureMessage
        dropActive -> "PDF 발췌를 놓아 추가"
        linkCount > 0 -> "$sourceName · 링크 $linkCount"
        else -> sourceName
    }
    // The grip and the title/source block both resize the pane, so the whole
    // header remains an easy one-thumb drag target without a separate strip.
    val resizeDragModifier = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                dragStartFraction = latestFraction
                dragTotalPx = 0f
                dragContentHeightPx = if (dragStartFraction > 0.01f) {
                    latestHeight / dragStartFraction
                } else {
                    latestHeight
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                dragTotalPx += dragAmount
                latestOnFraction(
                    studyPaneFractionAfterDrag(
                        startFraction = dragStartFraction,
                        dragY = dragTotalPx,
                        totalHeightPx = dragContentHeightPx,
                    ),
                )
            },
            onDragEnd = {
                if (
                    shouldDismissStudyPaneAfterDrag(
                        startFraction = dragStartFraction,
                        dragY = dragTotalPx,
                        totalHeightPx = dragContentHeightPx,
                    )
                ) {
                    latestOnDismiss()
                }
            },
            onDragCancel = { dragTotalPx = 0f },
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .then(dropModifier),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(
                alpha = if (dropActive) 1f else 0.94f,
            ),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(
                width = if (dropActive) 2.dp else 1.dp,
                color = if (dropActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                },
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        (
                            STUDY_PANE_HEADER_ROW_HEIGHT_DP +
                                STUDY_PANE_HEADER_VERTICAL_PADDING_DP * 2
                            ).dp,
                    )
                    .padding(start = 2.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Resize/dismiss grip. The old 16 dp handle strip is gone; the
                // grip lives in the same row as the title to save vertical space.
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = "스터디 패널 높이 조절 · 아래로 밀어 닫기"
                        }
                        .then(resizeDragModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(resizeDragModifier)
                        .padding(end = 4.dp),
                ) {
                    Text(
                        notebookTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (captureMessage != null || dropActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tiny size popup replaces the permanent 50/70/85% chip row.
                Box {
                    Surface(
                        onClick = { showSizeMenu = true },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(
                            studyPanePresetLabel(heightFraction),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(
                        expanded = showSizeMenu,
                        onDismissRequest = { showSizeMenu = false },
                    ) {
                        STUDY_PANE_PRESETS.forEach { preset ->
                            val selected =
                                isStudyPanePresetSelected(heightFraction, preset)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (selected) {
                                            "패널 ${studyPanePresetLabel(preset)} · 현재"
                                        } else {
                                            "패널 ${studyPanePresetLabel(preset)}"
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                onClick = {
                                    showSizeMenu = false
                                    onHeightFractionChange(preset)
                                },
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onToggleCapture,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Notes,
                        contentDescription = if (captureOpen) {
                            "발췌 놓기 닫기"
                        } else {
                            "PDF 발췌 놓기"
                        },
                        modifier = Modifier.size(17.dp),
                        tint = if (captureOpen || dropActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                IconButton(
                    onClick = onOpenLinks,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Link,
                        contentDescription = if (linkCount == 0) {
                            "원본 페이지 링크 없음"
                        } else {
                            "원본 페이지 링크 ${linkCount}개"
                        },
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "스터디 노트 닫기",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // The capture strip is opt-in: even when open it is one compact row, not
        // part of a permanent stacked header. Dropping on the header itself also
        // works, so selection-bar capture stays immediate.
        if (captureOpen) {
            Spacer(Modifier.height(STUDY_PANE_CAPTURE_PANEL_GAP_DP.dp))
            StudyNoteDropTarget(
                onDrop = onDropExcerpt,
                caption = captureMessage ?: "PDF 선택 텍스트를 끌어다 놓기",
                compact = true,
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPaneLinksSheet(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onJump: (sourcePageIndex: Int, notePageIndex: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = remember(notebook.pages, notebook.sourceDocumentName) {
        StudyNoteSourceLinks.grouped(notebook)
    }
    val linkCount = groups.sumOf { it.links.size }
    val sourceName = notebook.sourceDocumentName.ifBlank { "이름 없는 PDF" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "원본 PDF 링크",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${linkCount}개 연결",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "노트 페이지 → 원본 PDF 페이지. 항목을 누르면 패널을 유지한 채 PDF와 노트 페이지가 함께 이동합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "원본 문서: $sourceName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))

            if (groups.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
                        Text(
                            "연결된 원본 페이지가 없습니다",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PDF에서 텍스트를 선택한 뒤 이 패널에 추가하면, 각 발췌 페이지가 " +
                                "원본 PDF 페이지와 연결되어 여기에 표시됩니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groups.forEach { group ->
                        item(key = "source-${group.sourcePageIndex}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "PDF p.${group.sourcePageIndex + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "노트 ${group.links.size}개",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(
                            items = group.links,
                            key = { link ->
                                "link-${link.sourcePageIndex}-${link.notePageIndex}"
                            },
                        ) { link ->
                            StudyPaneLinkRow(
                                link = link,
                                onClick = { onJump(link.sourcePageIndex, link.notePageIndex) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyPaneLinkRow(
    link: StudyNoteSourceLink,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "노트 p.${link.notePageIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        "  →  ",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "PDF p.${link.sourcePageIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (link.preview.isNotBlank()) {
                    Text(
                        link.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                    )
                }
            }
            Text(
                "이동",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
