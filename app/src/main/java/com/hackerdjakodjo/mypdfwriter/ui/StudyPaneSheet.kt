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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
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
 * area. The pane canvas fills that whole area. The only boundary affordance is the
 * slim divider owned by the PDF viewer; no file-name/header row reserves note
 * space. The compact note dock floats at the bottom. System Back closes the pane.
 * PDF excerpts can still be dropped anywhere on the pane.
 */
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
    onCaptureExcerpt: (PdfStudyCapture) -> Boolean,
    onJumpToSourcePage: (sourcePageIndex: Int, notePageIndex: Int) -> Unit,
    onOpenHelp: () -> Unit = {},
    sourceDocId: String? = null,
    sourceFingerprint: String? = null,
) {
    var showLinks by remember(notebook.id) { mutableStateOf(false) }
    val dropModifier = rememberStudyNoteDropTarget(
        onDrop = onCaptureExcerpt,
        onActiveChange = {},
        expectedDocId = sourceDocId,
        expectedFingerprint = sourceFingerprint,
    )

    Box(Modifier.fillMaxSize().then(dropModifier)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            NoteEditorScreen(
                notebook = notebook,
                pageIndex = pageIndex,
                onBack = onDismiss,
                onChange = onChange,
                onPageIndexChange = onPageIndexChange,
                onExportPdf = onExportPdf,
                onOpenSourcePdf = onOpenSourcePdf,
                onOpenSourceLinks = { showLinks = true },
                compact = true,
                compactTopInsetDp = STUDY_PANE_TOP_INSET_DP,
                compactShowTools = true,
                onOpenHelp = onOpenHelp,
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
