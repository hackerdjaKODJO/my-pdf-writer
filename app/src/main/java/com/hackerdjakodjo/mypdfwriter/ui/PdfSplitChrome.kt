package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

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

/**
 * How much of the floating PDF toolbar fits over the strip. When the note takes
 * more than 80% of the content area (roughly a strip below 120dp on a phone),
 * only the page pill and the separate top-start back button are shown so the
 * source page stays readable.
 */
internal enum class SplitPdfChromeDensity {
    FULL,
    ESSENTIAL,
}

/** Above this pane share the PDF strip is too short for the full tool strip. */
internal const val SPLIT_PDF_ESSENTIAL_PANE_FRACTION = 0.80f

internal fun splitPdfChromeDensity(paneFraction: Float): SplitPdfChromeDensity =
    if (clampStudyPaneFraction(paneFraction) > SPLIT_PDF_ESSENTIAL_PANE_FRACTION) {
        SplitPdfChromeDensity.ESSENTIAL
    } else {
        SplitPdfChromeDensity.FULL
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

/**
 * Markup-first primary actions shared by the full-screen selection bar and the
 * floating split bar. Everything else moves into the selection "더보기" sheet.
 */
internal enum class TextSelectionPrimaryAction(
    val label: String,
    val contentDescription: String,
) {
    HIGHLIGHT("형광펜", "형광펜 표시"),
    UNDERLINE("밑줄", "밑줄 표시"),
    STRIKEOUT("취소선", "취소선 표시"),
    COPY("복사", "선택한 텍스트 복사"),
    EXCERPT("발췌", "발췌로 저장"),
    MORE("더보기", "선택 도구 더보기"),
}

internal fun textSelectionPrimaryIcon(action: TextSelectionPrimaryAction): ImageVector = when (action) {
    TextSelectionPrimaryAction.HIGHLIGHT -> Icons.Outlined.Highlight
    TextSelectionPrimaryAction.UNDERLINE -> Icons.Outlined.FormatUnderlined
    TextSelectionPrimaryAction.STRIKEOUT -> Icons.Outlined.FormatStrikethrough
    TextSelectionPrimaryAction.COPY -> Icons.Outlined.ContentCopy
    TextSelectionPrimaryAction.EXCERPT -> Icons.AutoMirrored.Outlined.Notes
    TextSelectionPrimaryAction.MORE -> Icons.Outlined.MoreHoriz
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
 * Floating top strip over the PDF area: page pill, text-selection toggle,
 * current-page bookmark, annotation-tool sheet and PDF menu. It never reserves
 * layout height; the note pane keeps the rest of the screen. In [compact] mode
 * only the page pill remains (the top-start back button is drawn separately),
 * which keeps a very short PDF strip readable.
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
    compact: Boolean = false,
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
                // The full strip can exceed a narrow screen; keep every tool
                // reachable instead of clipping the trailing icons.
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SplitPagePill(
                pageIndex = pageIndex,
                pageCount = pageCount,
                onPrevious = onPrevious,
                onNext = onNext,
                onOpenPages = onOpenPages,
            )

            if (!compact) {
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
                    contentDescription = "주석",
                    onClick = onOpenTools,
                )

                SplitChromeButton(
                    icon = Icons.Outlined.Menu,
                    contentDescription = "PDF 메뉴",
                    onClick = onOpenHub,
                )
            }
        }
    }
}

/** Previous / page pill / next group, always available in both densities. */
@Composable
private fun SplitPagePill(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPages: () -> Unit,
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
}

/**
 * Resize divider between the PDF strip and the note pane. The whole 28dp row is
 * a vertical drag target; the centered pill also accepts tap (size presets) and
 * double-tap (50% <-> 85%). A trailing chip toggles the PDF tool strip
 * without touching the selection bar. Pure sizing/preset policy lives in
 * [StudyPaneLayout.kt], so this composable only wires gestures to callbacks.
 */
@Composable
internal fun SplitDivider(
    paneFraction: Float,
    totalHeightPx: Float,
    pdfToolsVisible: Boolean,
    onFractionChange: (Float) -> Unit,
    onPresetSelected: (Float) -> Unit,
    onTogglePdfTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragStartFraction by remember { mutableFloatStateOf(paneFraction) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var showSizePopup by remember { mutableStateOf(false) }
    val latestFraction = rememberUpdatedState(paneFraction)
    val latestHeight = rememberUpdatedState(totalHeightPx)
    val latestOnFraction = rememberUpdatedState(onFractionChange)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(STUDY_PANE_DIVIDER_ROW_HEIGHT_DP.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragStartFraction = latestFraction.value
                        dragY = 0f
                        showSizePopup = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount
                        latestOnFraction.value(
                            studyPaneFractionAfterDrag(
                                startFraction = dragStartFraction,
                                dragY = dragY,
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(STUDY_PANE_DIVIDER_ROW_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(50))
                    .semantics { contentDescription = studyPaneDividerDescription(paneFraction) }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showSizePopup = true },
                            onDoubleTap = {
                                showSizePopup = false
                                onPresetSelected(studyPanePresetAfterDoubleTap(paneFraction))
                            },
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Outlined.DragIndicator,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    studyPaneDividerLabel(paneFraction),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Spacer(Modifier.width(12.dp))
            }
        }

        Surface(
            onClick = onTogglePdfTools,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .height(STUDY_PANE_DIVIDER_ROW_HEIGHT_DP.dp)
                .semantics { contentDescription = splitPdfToolsChipDescription(pdfToolsVisible) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            contentColor = if (pdfToolsVisible) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Handyman,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("PDF 도구", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        if (showSizePopup) {
            val density = LocalDensity.current
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(
                    0,
                    with(density) { STUDY_PANE_DIVIDER_ROW_HEIGHT_DP.dp.toPx().roundToInt() },
                ),
                onDismissRequest = { showSizePopup = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    shadowElevation = 8.dp,
                ) {
                    StudyPaneSizePresets(
                        currentFraction = paneFraction,
                        onPresetSelected = { fraction ->
                            showSizePopup = false
                            onPresetSelected(fraction)
                        },
                    )
                }
            }
        }
    }
}

/** Compact preset picker hosted by the divider popup; also testable standalone. */
@Composable
internal fun StudyPaneSizePresets(
    currentFraction: Float,
    onPresetSelected: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(132.dp).padding(vertical = 4.dp)) {
        Text(
            "노트 크기",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        STUDY_PANE_PRESETS.forEach { preset ->
            val selected = isStudyPanePresetSelected(currentFraction, preset)
            Surface(
                onClick = { onPresetSelected(preset) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(STUDY_PANE_PRESET_ROW_HEIGHT_DP.dp)
                    .semantics { contentDescription = studyPanePresetDescription(preset) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(studyPanePresetLabel(preset), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * Floating text-selection bar for split mode. It replaces the bottom dock and
 * overlays the PDF strip instead of adding a permanent row below the page.
 * The same markup-first order as the full-screen bar stays visible; translate,
 * dictionary, pane capture, text edit, share and clear live in the more sheet.
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
    onExcerpt: () -> Unit,
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

            TextSelectionPrimaryAction.entries.forEach { action ->
                SplitChromeButton(
                    icon = textSelectionPrimaryIcon(action),
                    contentDescription = action.contentDescription,
                    onClick = when (action) {
                        TextSelectionPrimaryAction.HIGHLIGHT -> onHighlight
                        TextSelectionPrimaryAction.UNDERLINE -> onUnderline
                        TextSelectionPrimaryAction.STRIKEOUT -> onStrikeout
                        TextSelectionPrimaryAction.COPY -> onCopy
                        TextSelectionPrimaryAction.EXCERPT -> onExcerpt
                        TextSelectionPrimaryAction.MORE -> onMore
                    },
                    enabled = action == TextSelectionPrimaryAction.MORE || hasSelection,
                )
            }
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

/**
 * Secondary text actions for both selection bars. The primary bar keeps the
 * markup-first six; translate, dictionary, pane capture, text edit, share and
 * clear stay one tap behind this sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextSelectionMoreSheet(
    selectedText: String,
    pageIndex: Int,
    hasSelection: Boolean,
    canAddToPane: Boolean,
    onTranslate: () -> Unit,
    onDictionary: () -> Unit,
    onEditText: () -> Unit,
    onShare: () -> Unit,
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
            }
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
                label = "공유",
                icon = Icons.Outlined.Share,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onShare()
                },
            )
            if (canAddToPane) {
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
