package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
 * area. The handle resizes live, preset chips set 50/70/85%, and pulling below the
 * minimum height dismisses it. The header capture strip accepts the same in-app
 * drag payload as the PDF selection bar, and the link action opens the local
 * To/From index built from [com.hackerdjakodjo.mypdfwriter.data.NotePage.sourcePageIndex].
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
) {
    var showLinks by remember(notebook.id) { mutableStateOf(false) }
    var captureMessage by remember(notebook.id) { mutableStateOf<String?>(null) }
    var paneHeightPx by remember { mutableFloatStateOf(0f) }
    val sourceName = notebook.sourceDocumentName.ifBlank { "원본 PDF" }
    val linkCount = remember(notebook.pages) {
        StudyNoteSourceLinks.fromNotebook(notebook).size
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
            Column(Modifier.fillMaxSize()) {
                StudyPaneHeader(
                    notebookTitle = notebook.title,
                    sourceName = sourceName,
                    heightFraction = heightFraction,
                    paneHeightPx = paneHeightPx,
                    captureMessage = captureMessage,
                    linkCount = linkCount,
                    onHeightFractionChange = onHeightFractionChange,
                    onOpenLinks = { showLinks = true },
                    onClose = onDismiss,
                    onDropExcerpt = { text, page ->
                        val added = onCaptureExcerpt(text, page)
                        captureMessage = if (added) {
                            "PDF p.${page + 1} 발췌를 추가했습니다"
                        } else {
                            "발췌를 추가하지 못했습니다"
                        }
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    NoteEditorScreen(
                        notebook = notebook,
                        pageIndex = pageIndex,
                        onBack = onDismiss,
                        onChange = onChange,
                        onPageIndexChange = onPageIndexChange,
                        onExportPdf = onExportPdf,
                        onOpenSourcePdf = onOpenSourcePdf,
                    )
                }
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
private fun StudyPaneHeader(
    notebookTitle: String,
    sourceName: String,
    heightFraction: Float,
    paneHeightPx: Float,
    captureMessage: String?,
    linkCount: Int,
    onHeightFractionChange: (Float) -> Unit,
    onOpenLinks: () -> Unit,
    onClose: () -> Unit,
    onDropExcerpt: (text: String, pageIndex: Int) -> Unit,
) {
    // The pane occupies heightFraction of the PDF content area, so its measured
    // height lets the handle translate pixel drags into content-area fractions.
    val latestFraction by rememberUpdatedState(heightFraction)
    val latestHeight by rememberUpdatedState(paneHeightPx)
    val latestOnFraction by rememberUpdatedState(onHeightFractionChange)
    val latestOnDismiss by rememberUpdatedState(onClose)
    var dragStartFraction by remember { mutableFloatStateOf(0f) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    var dragContentHeightPx by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .pointerInput(Unit) {
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
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(50),
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 14.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EditNote,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "PDF 위 스터디 노트",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    "$notebookTitle · $sourceName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onOpenLinks,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = if (linkCount == 0) {
                        "원본 페이지 링크 없음"
                    } else {
                        "원본 페이지 링크 ${linkCount}개"
                    },
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "스터디 노트 닫기",
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StudyNoteDropTarget(
                onDrop = onDropExcerpt,
                caption = captureMessage ?: "PDF 선택 텍스트를 끌어다 놓기",
                compact = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                STUDY_PANE_PRESETS.forEach { preset ->
                    StudyPaneSizeChip(
                        fraction = preset,
                        selected = isStudyPanePresetSelected(heightFraction, preset),
                        onClick = { onHeightFractionChange(preset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyPaneSizeChip(
    fraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            studyPanePresetLabel(fraction),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
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
