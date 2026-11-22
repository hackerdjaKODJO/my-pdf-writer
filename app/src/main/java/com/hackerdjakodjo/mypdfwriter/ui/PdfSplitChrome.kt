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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
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
    chromeHidden: Boolean = false,
    autoHideMode: ChromeAutoHideMode = ChromeAutoHideMode.OFF,
): PdfChromePolicy = when {
    studyPaneOpen -> PdfChromePolicy(
        showTopAppBar = false,
        showBottomBar = false,
        showHintPill = false,
        // The study pane header already owns a drop target; a second target
        // over the tiny PDF strip only wastes reading space.
        showPageDropTarget = false,
        showSplitToolbar = !textSelectMode,
        showSplitSelectionBar = textSelectMode,
    )

    isChromeImmersive(studyPaneOpen, autoHideMode, chromeHidden) -> PdfChromePolicy(
        // Auto-hide is an overlay state: no bar, hint or drop target may consume
        // height. The floating back/menu controls in ViewerScreen stay reachable.
        showTopAppBar = false,
        showBottomBar = false,
        showHintPill = false,
        showPageDropTarget = false,
        showSplitToolbar = false,
        showSplitSelectionBar = false,
    )

    else -> PdfChromePolicy(
        showTopAppBar = true,
        showBottomBar = true,
        showHintPill = true,
        showPageDropTarget = textSelectMode && hasSelection,
        showSplitToolbar = false,
        showSplitSelectionBar = false,
    )
}

/**
 * GoodReader-style reading chrome modes. [ON] lets a tap on the centre of the
 * page hide/show the bars; [OFF] keeps the normal fixed chrome. The choice is
 * session-only and defaults to ON on phone-sized screens.
 */
internal enum class ChromeAutoHideMode {
    OFF,
    ON,
}

/** 600dp is the phone/large-screen boundary used elsewhere in this codebase. */
internal const val CHROME_AUTO_HIDE_PHONE_WIDTH_DP = 600

internal fun defaultChromeAutoHideMode(smallestScreenWidthDp: Int): ChromeAutoHideMode =
    if (smallestScreenWidthDp < CHROME_AUTO_HIDE_PHONE_WIDTH_DP) {
        ChromeAutoHideMode.ON
    } else {
        ChromeAutoHideMode.OFF
    }

internal fun toggleChromeAutoHideMode(mode: ChromeAutoHideMode): ChromeAutoHideMode =
    when (mode) {
        ChromeAutoHideMode.OFF -> ChromeAutoHideMode.ON
        ChromeAutoHideMode.ON -> ChromeAutoHideMode.OFF
    }

/** True only for the full-screen reader; split chrome already floats. */
internal fun isChromeImmersive(
    studyPaneOpen: Boolean,
    mode: ChromeAutoHideMode,
    chromeHidden: Boolean,
): Boolean = !studyPaneOpen && mode == ChromeAutoHideMode.ON && chromeHidden

/**
 * A canvas tap may toggle chrome only in the safe state: full-screen reader,
 * VIEW tool, no selection or sheet or busy render in progress. Split mode never
 * hijacks canvas taps, so pinch, annotation hit-testing and the floating tool
 * strip keep their current behaviour.
 */
internal fun chromeTapToggleAllowed(
    mode: ChromeAutoHideMode,
    studyPaneOpen: Boolean,
    sheetOpen: Boolean,
    textSelectMode: Boolean,
    hasSelection: Boolean,
    toolIsView: Boolean,
    busy: Boolean,
): Boolean = mode == ChromeAutoHideMode.ON &&
    !studyPaneOpen && !sheetOpen && !textSelectMode && !hasSelection && toolIsView && !busy

internal fun nextChromeHidden(hidden: Boolean, toggleAllowed: Boolean): Boolean =
    if (toggleAllowed) !hidden else hidden

/** Centre band only: the edges stay free for page pills, hints and link taps. */
internal fun isChromeToggleTap(
    x: Float,
    y: Float,
    width: Int,
    height: Int,
): Boolean {
    if (width <= 0 || height <= 0) return false
    return x >= width * 0.25f && x <= width * 0.75f &&
        y >= height * 0.25f && y <= height * 0.75f
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
    EXCERPT(PDF_HUB_CAPTURE_LABEL, PDF_HUB_CAPTURE_LABEL),
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

/**
 * The 더보기 sheet keeps exactly one capture label. 번역/사전 are external
 * handoffs, not study actions, so they sit in a quiet row at the bottom instead
 * of competing with 노트에 담기 for the first tap.
 */
internal enum class TextSelectionSecondaryAction(val label: String) {
    CAPTURE(PDF_HUB_CAPTURE_LABEL),
    EDIT("텍스트 편집"),
    SHARE("공유"),
}

internal val PDF_TEXT_HANDOFF_LABELS = listOf("번역", "사전")

/**
 * All floating split-chrome controls expose a full 48 dp TalkBack target. The
 * drawn circle stays smaller so a short PDF strip is not swamped by chrome.
 */
internal const val SPLIT_CHROME_TARGET_DP = MIN_TARGET_DP

/** Visual diameter of the compact circle drawn inside a 48 dp hit target. */
internal const val SPLIT_CHROME_VISUAL_DP = 36

/**
 * Inventory of the floating PDF strip. The compact (ESSENTIAL) density keeps
 * page navigation and search but drops the four secondary tools; it is a pure
 * list so the "split compact" contract can be checked without a device.
 */
internal fun splitPdfToolbarControls(compact: Boolean): List<String> = buildList {
    add("이전 페이지")
    add("페이지 선택")
    add("다음 페이지")
    add("검색")
    if (!compact) {
        add("텍스트 선택")
        add("현재 페이지 북마크")
        add("주석")
        add("학습 메뉴")
    }
}

/** Tiny translucent back target shown at the PDF-strip edge in split mode. */
@Composable
internal fun SplitPdfBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .sizeIn(
                minWidth = SPLIT_CHROME_TARGET_DP.dp,
                minHeight = SPLIT_CHROME_TARGET_DP.dp,
            )
            .semantics { contentDescription = "PDF 닫기" }
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
        ) {
            Box(
                Modifier.size(SPLIT_CHROME_VISUAL_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/**
 * Floating top strip over the PDF area: page pill, text-selection toggle,
 * current-page bookmark, annotation-tool sheet and 학습 메뉴. It never reserves
 * layout height; the note pane keeps the rest of the screen. In [compact] mode
 * the secondary tools are hidden (the top-start back button is drawn separately),
 * but search stays reachable: the default 85% study pane is ESSENTIAL, so gating
 * search behind `!compact` removed it from the primary study mode.
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
    onOpenSearch: () -> Unit,
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
                // 2 dp padding above/below each 48 dp target keeps the drawn
                // strip compact while every icon remains TalkBack-reachable.
                .height((SPLIT_CHROME_TARGET_DP + 4).dp)
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

            // Search is one tap away in every density, including the ESSENTIAL
            // strip used by the default 85% study pane.
            SplitChromeButton(
                icon = Icons.Outlined.Search,
                contentDescription = "검색",
                onClick = onOpenSearch,
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
                    contentDescription = "학습 메뉴",
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
            modifier = Modifier
                .sizeIn(
                    minWidth = SPLIT_CHROME_TARGET_DP.dp,
                    minHeight = SPLIT_CHROME_TARGET_DP.dp,
                )
                .semantics { contentDescription = "페이지 선택" },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ) {
            Box(
                modifier = Modifier
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
        // The visible pill stays inside the thin divider row, but TalkBack sees
        // a full 48 dp clickable node with an explicit "노트 크기 선택" action.
        // Tap opens the presets and double-tap still jumps to an endpoint.
        Box(
            modifier = Modifier
                .sizeIn(
                    minWidth = SPLIT_CHROME_TARGET_DP.dp,
                    minHeight = SPLIT_CHROME_TARGET_DP.dp,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = studyPaneDividerDescription(paneFraction)
                    onClick(label = "노트 크기 선택") { showSizePopup = true; true }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showSizePopup = true },
                        onDoubleTap = {
                            showSizePopup = false
                            onPresetSelected(studyPanePresetAfterDoubleTap(paneFraction))
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
                        .clip(RoundedCornerShape(50)),
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
        }

        // Same pattern for the tool chip: the drawn chip stays small while the
        // clickable node carries the state-aware label and a 48 dp target.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .sizeIn(
                    minWidth = SPLIT_CHROME_TARGET_DP.dp,
                    minHeight = SPLIT_CHROME_TARGET_DP.dp,
                )
                .clickable(onClick = onTogglePdfTools)
                .semantics { contentDescription = splitPdfToolsChipDescription(pdfToolsVisible) },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                contentColor = if (pdfToolsVisible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Row(
                    modifier = Modifier
                        .height(STUDY_PANE_DIVIDER_ROW_HEIGHT_DP.dp)
                        .padding(horizontal = 12.dp),
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
 * The same markup-first order as the full-screen bar stays visible; 노트에 담기,
 * 텍스트 편집, 공유 and 선택 해제 live in the more sheet, with 번역/사전 as quiet
 * external handoffs.
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
                .height((SPLIT_CHROME_TARGET_DP + 4).dp)
                // The compact bar can exceed the strip width once every action is
                // present; keep the extra tools reachable instead of clipping them.
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = statusModifier
                    .sizeIn(
                        minWidth = SPLIT_CHROME_TARGET_DP.dp,
                        minHeight = SPLIT_CHROME_TARGET_DP.dp,
                    )
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
            .sizeIn(
                minWidth = SPLIT_CHROME_TARGET_DP.dp,
                minHeight = SPLIT_CHROME_TARGET_DP.dp,
            )
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SPLIT_CHROME_VISUAL_DP.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    } else {
                        Color.Transparent
                    },
                ),
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
}

/**
 * Secondary text actions for both selection bars. The primary bar keeps the
 * markup-first six. This sheet has exactly one capture row — [PDF_HUB_CAPTURE_LABEL]
 * — which either adds to the open note pane or saves an excerpt. 번역/사전 remain
 * reachable as quiet external handoffs at the bottom, not as study actions.
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
    onExcerpt: () -> Unit,
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

            // Single capture row: pane capture wins when the pane is open, but
            // the excerpt is still persisted by the host in that callback.
            SplitSheetAction(
                label = TextSelectionSecondaryAction.CAPTURE.label,
                icon = Icons.Outlined.EditNote,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    if (canAddToPane && onAddToPane != null) onAddToPane() else onExcerpt()
                },
            )
            SplitSheetAction(
                label = TextSelectionSecondaryAction.EDIT.label,
                icon = Icons.Outlined.Edit,
                enabled = hasSelection,
                onClick = {
                    onDismiss()
                    onEditText()
                },
            )
            SplitSheetAction(
                label = TextSelectionSecondaryAction.SHARE.label,
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "다른 앱에서 보기",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PDF_TEXT_HANDOFF_LABELS.forEach { label ->
                    TextButton(
                        onClick = {
                            onDismiss()
                            if (label == PDF_TEXT_HANDOFF_LABELS.first()) onTranslate() else onDictionary()
                        },
                        enabled = hasSelection,
                        modifier = Modifier.sizeIn(minHeight = SPLIT_CHROME_TARGET_DP.dp),
                    ) { Text(label) }
                }
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
            .padding(vertical = 2.dp)
            .sizeIn(minHeight = SPLIT_CHROME_TARGET_DP.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
