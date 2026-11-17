package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Tool

/**
 * Split/focus chrome policy for the PDF viewer. Kept pure so the "which bars
 * are allowed to consume height" decision is testable without a device.
 *
 * When the study pane is open the viewer drops its app bar and bottom bar
 * completely; only compact floating controls remain over the PDF strip.
 */
internal data class PdfChromePolicy(
    val showTopAppBar: Boolean,
    val showBottomBar: Boolean,
    val showHintPill: Boolean,
    val showPageDropTarget: Boolean,
    val showSplitToolbar: Boolean,
    val showSplitSelectionBar: Boolean,
)

internal fun pdfChromePolicy(
    studyPaneOpen: Boolean,
    textSelectMode: Boolean,
    hasSelection: Boolean,
): PdfChromePolicy = if (studyPaneOpen) {
    PdfChromePolicy(
        showTopAppBar = false,
        showBottomBar = false,
        showHintPill = false,
        // The study pane header already owns a drop target; a second target
        // over the tiny PDF strip only wastes reading space.
        showPageDropTarget = false,
        showSplitToolbar = !textSelectMode,
        showSplitSelectionBar = textSelectMode,
    )
} else {
    PdfChromePolicy(
        showTopAppBar = true,
        showBottomBar = true,
        showHintPill = true,
        showPageDropTarget = textSelectMode && hasSelection,
        showSplitToolbar = false,
        showSplitSelectionBar = false,
    )
}

/** Visual/state model for the floating text-selection toggle. */
internal enum class SplitSelectionToggleState {
    IDLE,
    LOADING,
    ACTIVE,
    EMPTY,
}

internal fun splitSelectionToggleState(
    textSelectMode: Boolean,
    loading: Boolean,
    hasWords: Boolean,
): SplitSelectionToggleState = when {
    !textSelectMode -> SplitSelectionToggleState.IDLE
    loading -> SplitSelectionToggleState.LOADING
    !hasWords -> SplitSelectionToggleState.EMPTY
    else -> SplitSelectionToggleState.ACTIVE
}

/** All floating split-chrome targets stay in the 36-40 dp range. */
internal const val SPLIT_CHROME_TARGET_DP = 36

/** Tiny translucent back target shown at the PDF-strip edge in split mode. */
@Composable
internal fun SplitPdfBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onBack,
        modifier = modifier
            .size(SPLIT_CHROME_TARGET_DP.dp)
            .semantics { contentDescription = "PDF 닫기" },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Floating compact toolbar over the PDF strip: page pill, text-selection
 * toggle, current-page bookmark, PDF tool picker and hub. It never reserves
 * layout height; the note pane keeps the rest of the screen.
 */
@Composable
internal fun SplitPdfToolbar(
    pageIndex: Int,
    pageCount: Int,
    bookmarked: Boolean,
    textSelectMode: Boolean,
    textLoading: Boolean,
    hasWords: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPages: () -> Unit,
    onToggleTextSelection: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleState = splitSelectionToggleState(
        textSelectMode = textSelectMode,
        loading = textLoading,
        hasWords = hasWords,
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(SPLIT_CHROME_TARGET_DP.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        RoundedCornerShape(50),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SplitChromeButton(
                    icon = Icons.Outlined.ChevronLeft,
                    contentDescription = "이전 페이지",
                    onClick = onPrevious,
                    enabled = pageIndex > 0,
                )
                Surface(
                    onClick = onOpenPages,
                    modifier = Modifier.semantics { contentDescription = "페이지 선택" },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                ) {
                    Box(
                        modifier = Modifier
                            .height(SPLIT_CHROME_TARGET_DP.dp)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${pageIndex + 1} / $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
                SplitChromeButton(
                    icon = Icons.Outlined.ChevronRight,
                    contentDescription = "다음 페이지",
                    onClick = onNext,
                    enabled = pageIndex < pageCount - 1,
                )
            }

            SplitChromeButton(
                icon = Icons.AutoMirrored.Outlined.TextSnippet,
                contentDescription = when (toggleState) {
                    SplitSelectionToggleState.IDLE -> "텍스트 선택"
                    SplitSelectionToggleState.LOADING -> "텍스트 선택 준비 중"
                    SplitSelectionToggleState.ACTIVE -> "텍스트 선택 끄기"
                    SplitSelectionToggleState.EMPTY -> "선택할 텍스트 없음"
                },
                onClick = onToggleTextSelection,
                selected = toggleState == SplitSelectionToggleState.ACTIVE,
                loading = toggleState == SplitSelectionToggleState.LOADING,
                error = toggleState == SplitSelectionToggleState.EMPTY,
            )

            SplitChromeButton(
                icon = if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = if (bookmarked) {
                    "현재 페이지 북마크 해제"
                } else {
                    "현재 페이지 북마크"
                },
                onClick = onToggleBookmark,
                selected = bookmarked,
                selectedTint = MaterialTheme.colorScheme.primary,
            )

            SplitChromeButton(
                icon = Icons.Outlined.Handyman,
                contentDescription = "PDF 도구",
                onClick = onOpenTools,
            )

            SplitChromeButton(
                icon = Icons.Outlined.Apps,
                contentDescription = "PDF 허브",
                onClick = onOpenHub,
            )
        }
    }
}

/**
 * Floating text-selection bar for split mode. It replaces the bottom dock and
 * overlays the PDF strip instead of adding a permanent row below the page.
 * Highlight/underline/strikeout/text edit/copy/dictionary stay one tap away; more
 * opens the secondary actions (translate, dictionary, excerpt, add to pane, clear).
 */
@Composable
internal fun SplitSelectionActionBar(
    selectedText: String,
    pageIndex: Int,
    hasSelection: Boolean,
    loading: Boolean,
    hasWords: Boolean,
    wordCount: Int,
    onCopy: () -> Unit,
    onDictionary: () -> Unit = {},
    onEditText: () -> Unit = {},
    onHighlight: () -> Unit,
    onUnderline: () -> Unit,
    onStrikeout: () -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = !loading && !hasWords
    val statusText = when {
        loading -> "읽는 중"
        empty -> "없음"
        hasSelection -> "${selectedText.length}자"
        else -> "$wordCount"
    }
    val statusModifier = if (hasSelection) {
        Modifier.studyNoteDragSource(selectedText, pageIndex)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                // The compact bar can exceed the strip width once every action is
                // present; keep the extra tools reachable instead of clipping them.
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = statusModifier
                    .height(SPLIT_CHROME_TARGET_DP.dp)
                    .clip(RoundedCornerShape(50)),
                shape = RoundedCornerShape(50),
                color = if (empty) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (empty) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Outlined.TextSnippet,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }

            SplitChromeButton(
                icon = Icons.Outlined.Highlight,
                contentDescription = "형광펜 표시",
                onClick = onHighlight,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.Outlined.FormatUnderlined,
                contentDescription = "밑줄 표시",
                onClick = onUnderline,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.Outlined.FormatStrikethrough,
                contentDescription = "취소선 표시",
                onClick = onStrikeout,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.Outlined.Edit,
                contentDescription = "선택한 텍스트 수정",
                onClick = onEditText,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.Outlined.ContentCopy,
                contentDescription = "선택한 텍스트 복사",
                onClick = onCopy,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = "사전에서 찾기",
                onClick = onDictionary,
                enabled = hasSelection,
            )
            SplitChromeButton(
                icon = Icons.Outlined.MoreHoriz,
                contentDescription = "선택 도구 더보기",
                onClick = onMore,
            )
            SplitChromeButton(
                icon = Icons.Outlined.Close,
                contentDescription = if (hasSelection) "선택 해제" else "텍스트 선택 종료",
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun SplitChromeButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    selectedTint: Color? = null,
    loading: Boolean = false,
    error: Boolean = false,
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        error -> MaterialTheme.colorScheme.error
        selectedTint != null -> selectedTint
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(SPLIT_CHROME_TARGET_DP.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                } else {
                    Color.Transparent
                },
            )
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = contentColor,
            )
        }
    }
}

/** Tool tile used by the split-mode tool picker. */
private data class SplitToolEntry(
    val tool: Tool,
    val label: String,
    val icon: ImageVector,
)

private val splitToolEntries = listOf(
    SplitToolEntry(Tool.VIEW, "보기", Icons.Outlined.Visibility),
    SplitToolEntry(Tool.PEN, "펜", Icons.Outlined.Edit),
    SplitToolEntry(Tool.HIGHLIGHT, "형광펜", Icons.Outlined.Highlight),
    SplitToolEntry(Tool.ERASER, "지우개", Icons.Outlined.DeleteSweep),
    SplitToolEntry(Tool.TEXT, "글상자", Icons.Outlined.TextFields),
    SplitToolEntry(Tool.RECT, "사각형", Icons.Outlined.CropSquare),
    SplitToolEntry(Tool.ARROW, "화살표", Icons.Outlined.NorthEast),
    SplitToolEntry(Tool.LINE, "직선", Icons.Outlined.HorizontalRule),
    SplitToolEntry(Tool.ELLIPSE, "타원", Icons.Outlined.Circle),
    SplitToolEntry(Tool.UNDERLINE, "밑줄", Icons.Outlined.FormatUnderlined),
    SplitToolEntry(Tool.STRIKEOUT, "취소선", Icons.Outlined.FormatStrikethrough),
    SplitToolEntry(Tool.STAMP, "스탬프", Icons.Outlined.Approval),
    SplitToolEntry(Tool.SIGNATURE, "서명", Icons.Outlined.Draw),
    SplitToolEntry(Tool.REDACT, "가림", Icons.Outlined.VisibilityOff),
)

/**
 * Split-mode replacement for the full PDF tool dock. The dock row is removed
 * with the bottom bar, so this sheet keeps every annotation tool reachable
 * without spending permanent vertical space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitPdfToolsSheet(
    currentTool: Tool,
    textSelectMode: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onSelectTool: (Tool) -> Unit,
    onTextSelect: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStyle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("PDF 도구", style = MaterialTheme.typography.titleLarge)
            Text(
                "노트 패널을 유지한 채 PDF에 주석을 추가합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AssistChip(
                    onClick = onUndo,
                    enabled = canUndo,
                    label = { Text("실행 취소") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                AssistChip(
                    onClick = onRedo,
                    enabled = canRedo,
                    label = { Text("다시 실행") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
                if (isStyleTool(currentTool)) {
                    AssistChip(
                        onClick = onStyle,
                        label = { Text("스타일") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = onTextSelect,
                    label = { Text(if (textSelectMode) "텍스트 선택 끄기" else "텍스트 선택") },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.TextSnippet,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "도구",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(6.dp))
            splitToolEntries.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { entry ->
                        SplitToolTile(
                            entry = entry,
                            selected = currentTool == entry.tool,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onDismiss()
                                onSelectTool(entry.tool)
                            },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SplitToolTile(
    entry: SplitToolEntry,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                entry.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Secondary text actions for the split selection bar. The primary bar keeps
 * highlight/underline/strikeout/text edit/copy/dictionary; translate, dictionary,
 * excerpt, pane capture and clear stay one tap behind this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitSelectionMoreSheet(
    selectedText: String,
    pageIndex: Int,
    hasSelection: Boolean,
    canAddToPane: Boolean,
    onTranslate: () -> Unit,
    onDictionary: () -> Unit,
    onExcerpt: () -> Unit,
    onEditText: () -> Unit,
    onCopy: () -> Unit,
    onAddToPane: (() -> Unit)?,
    onDismissSelection: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "선택한 텍스트",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (hasSelection) {
                    Text(
                        "${selectedText.length}자",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (hasSelection) {
                Text(
                    selectedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            } else {
                Text(
                    "PDF에서 단어를 탭하거나 드래그해 선택하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
            }

            SplitSheetAction(
                label = "Google 번역",
                icon = Icons.Outlined.Translate,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onTranslate()
                },
            )
            SplitSheetAction(
                label = "사전에서 찾기",
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onDictionary()
                },
            )
            SplitSheetAction(
                label = "발췌로 저장",
                icon = Icons.AutoMirrored.Outlined.Notes,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onExcerpt()
                },
            )
            SplitSheetAction(
                label = "텍스트 수정 (화면 덮어쓰기)",
                icon = Icons.Outlined.Edit,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onEditText()
                },
            )
            SplitSheetAction(
                label = "클립보드에 복사",
                icon = Icons.Outlined.ContentCopy,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onCopy()
                },
            )
            if (canAddToPane && onAddToPane != null) {
                SplitSheetAction(
                    label = "노트 패널에 추가",
                    icon = Icons.Outlined.EditNote,
                    enabled = hasSelection,
                    onClick = {
                        onDismiss()
                        onAddToPane()
                    },
                )
                Text(
                    "선택 바를 길게 눌러 PDF p.${pageIndex + 1} 텍스트를 패널로 끌 수도 있습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    onDismiss()
                    onDismissSelection()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (hasSelection) "선택 해제" else "텍스트 선택 종료")
            }
        }
    }
}

@Composable
private fun SplitSheetAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
    }
}
