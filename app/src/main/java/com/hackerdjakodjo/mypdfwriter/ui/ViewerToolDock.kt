package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.Tool
import kotlin.math.roundToInt

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

/** Tools that are drawn by dragging a two-point shape. */
val viewerShapeTools = setOf(Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE)

internal fun isShapeTool(tool: Tool): Boolean = tool in viewerShapeTools

/** Tools whose colour/size/opacity can be adjusted. */
internal fun isStyleTool(tool: Tool): Boolean =
    tool != Tool.VIEW && tool != Tool.ERASER && tool != Tool.REDACT

/** Tools that live behind the single "주석" dock entry. */
internal val dockAnnotationTools: Set<Tool> = viewerShapeTools + setOf(
    Tool.TEXT,
    Tool.UNDERLINE,
    Tool.STRIKEOUT,
    Tool.STAMP,
    Tool.SIGNATURE,
    Tool.REDACT,
)

internal fun isDockAnnotationTool(tool: Tool): Boolean = tool in dockAnnotationTools

/**
 * The annotation slot must name the tool that is actually active. Otherwise
 * choosing 글상자/도형/밑줄 only highlights a generic entry and the user has to
 * remember what is armed.
 */
internal fun dockAnnotationLabel(tool: Tool): String = when (tool) {
    Tool.TEXT -> "글상자"
    Tool.RECT -> "사각형"
    Tool.ARROW -> "화살표"
    Tool.LINE -> "직선"
    Tool.ELLIPSE -> "타원"
    Tool.UNDERLINE -> "밑줄"
    Tool.STRIKEOUT -> "취소선"
    Tool.STAMP -> "스탬프"
    Tool.SIGNATURE -> "서명"
    Tool.REDACT -> "가림"
    else -> "주석"
}

/** Keeps the annotation slot icon aligned with the active extra tool. */
internal fun dockAnnotationIcon(tool: Tool): ImageVector = when (tool) {
    Tool.TEXT -> Icons.Outlined.TextFields
    Tool.ARROW -> Icons.Outlined.NorthEast
    Tool.LINE -> Icons.Outlined.HorizontalRule
    Tool.ELLIPSE -> Icons.Outlined.Circle
    Tool.UNDERLINE -> Icons.Outlined.FormatUnderlined
    Tool.STRIKEOUT -> Icons.Outlined.FormatStrikethrough
    Tool.STAMP -> Icons.Outlined.Approval
    Tool.SIGNATURE -> Icons.Outlined.Draw
    Tool.REDACT -> Icons.Outlined.VisibilityOff
    Tool.RECT -> Icons.Outlined.CropSquare
    else -> Icons.Outlined.MoreHoriz
}

/** Exactly six primary dock slots; everything else is one tap into 주석 도구. */
internal enum class ViewerDockSlot {
    VIEW,
    PEN,
    HIGHLIGHT,
    ERASER,
    TEXT_SELECT,
    ANNOTATION,
}

internal val viewerDockSlots: List<ViewerDockSlot> = ViewerDockSlot.entries.toList()

private data class DockEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Thumb-reach toolbar for the viewer. Six primary slots stay visible; 글상자,
 * shapes and the other annotation tools live in one 주석 도구 sheet. Style
 * controls only appear for tools that actually use them.
 */
@Composable
fun ViewerToolDock(
    state: ViewerUiState,
    onSelectTool: (Tool) -> Unit,
    onTextSelect: () -> Unit,
    onMore: () -> Unit,
    onStyle: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    textSelectMode: Boolean = false,
    compact: Boolean = false,
) {
    val viewLabel = stringResource(R.string.tool_view)
    val penLabel = stringResource(R.string.tool_pen)
    val highlightLabel = stringResource(R.string.tool_highlight)
    val eraserLabel = stringResource(R.string.tool_eraser)
    val entries = viewerDockSlots.map { slot ->
        when (slot) {
            ViewerDockSlot.VIEW -> DockEntry(
                viewLabel,
                Icons.Outlined.Visibility,
                state.tool == Tool.VIEW && !textSelectMode,
            ) { onSelectTool(Tool.VIEW) }

            ViewerDockSlot.PEN -> DockEntry(
                penLabel,
                Icons.Outlined.Edit,
                state.tool == Tool.PEN,
            ) { onSelectTool(Tool.PEN) }

            ViewerDockSlot.HIGHLIGHT -> DockEntry(
                highlightLabel,
                Icons.Outlined.Highlight,
                state.tool == Tool.HIGHLIGHT,
            ) { onSelectTool(Tool.HIGHLIGHT) }

            ViewerDockSlot.ERASER -> DockEntry(
                eraserLabel,
                Icons.Outlined.DeleteSweep,
                state.tool == Tool.ERASER,
            ) { onSelectTool(Tool.ERASER) }

            // Direct text selection entry: this used to be labelled "올가미",
            // which hid the most useful PDF action behind a note-editor term.
            ViewerDockSlot.TEXT_SELECT -> DockEntry(
                "텍스트 선택",
                Icons.AutoMirrored.Outlined.TextSnippet,
                textSelectMode,
                onTextSelect,
            )

            ViewerDockSlot.ANNOTATION -> DockEntry(
                dockAnnotationLabel(state.tool),
                dockAnnotationIcon(state.tool),
                isDockAnnotationTool(state.tool),
                onMore,
            )
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = if (compact) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                DockItem(
                    entry = entry,
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
            }
        }

        // The focused study pane uses a single dense row. Undo/redo and style
        // stay one tap away in the 주석 도구 sheet instead of spending another
        // 48 dp row.
        if (!compact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = stringResource(R.string.undo))
                }
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = stringResource(R.string.redo))
                }
                Spacer(Modifier.weight(1f))
                if (isStyleTool(state.tool)) {
                    StylePill(color = state.color, onStyle = onStyle)
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    entry: DockEntry,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val contentColor = if (entry.selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = entry.label }
            .clickable(onClick = entry.onClick)
            .padding(vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 28.dp else 32.dp)
                .background(
                    color = if (entry.selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                entry.icon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                tint = contentColor,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            entry.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compact) 9.sp else 10.sp,
                lineHeight = if (compact) 11.sp else 12.sp,
            ),
            color = contentColor,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StylePill(color: Int, onStyle: () -> Unit) {
    Surface(
        onClick = onStyle,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .background(Color(color), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Text("스타일", style = MaterialTheme.typography.labelMedium)
            Icon(
                Icons.Outlined.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Floating page pill: ‹ 3 / 12 ›, with the count opening the page sheet. */
@Composable
fun ViewerPageNavigator(
    pageIndex: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = pageIndex > 0,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "이전 페이지",
                    modifier = Modifier.size(20.dp),
                )
            }
            Surface(
                onClick = onOpenPages,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Text(
                    "${pageIndex + 1} / $pageCount",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = pageIndex < pageCount - 1,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "다음 페이지",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private data class ToolOption(
    val tool: Tool,
    val icon: ImageVector,
    val label: String,
    val hint: String? = null,
)

/** Base tools always present in the shared annotation sheet, including split mode. */
internal val annotationBaseTools: List<Tool> = listOf(
    Tool.VIEW,
    Tool.PEN,
    Tool.HIGHLIGHT,
    Tool.ERASER,
)

private val baseToolOptions = listOf(
    ToolOption(Tool.VIEW, Icons.Outlined.Visibility, "보기", "페이지를 넘기고 확대"),
    ToolOption(Tool.PEN, Icons.Outlined.Edit, "펜", "자유롭게 필기"),
    ToolOption(Tool.HIGHLIGHT, Icons.Outlined.Highlight, "형광펜", "끌어서 표시"),
    ToolOption(Tool.ERASER, Icons.Outlined.DeleteSweep, "지우개", "획을 지움"),
)

private val textAndShapeToolOptions = listOf(
    ToolOption(Tool.TEXT, Icons.Outlined.TextFields, "글상자", "페이지를 눌러 입력"),
    ToolOption(Tool.RECT, Icons.Outlined.CropSquare, "사각형", "끌어서 사각형"),
    ToolOption(Tool.ARROW, Icons.Outlined.NorthEast, "화살표", "끌어서 화살표"),
    ToolOption(Tool.LINE, Icons.Outlined.HorizontalRule, "직선", "끌어서 직선"),
    ToolOption(Tool.ELLIPSE, Icons.Outlined.Circle, "타원", "끌어서 타원"),
)

private val markupAndStampToolOptions = listOf(
    ToolOption(Tool.UNDERLINE, Icons.Outlined.FormatUnderlined, "밑줄", "끌어서 밑줄"),
    ToolOption(Tool.STRIKEOUT, Icons.Outlined.FormatStrikethrough, "취소선", "끌어서 취소선"),
    ToolOption(Tool.STAMP, Icons.Outlined.Approval, "스탬프", "페이지를 눌러 배치"),
    ToolOption(Tool.SIGNATURE, Icons.Outlined.Draw, "서명", "페이지를 눌러 배치"),
    ToolOption(Tool.REDACT, Icons.Outlined.VisibilityOff, "가림", "끌어서 검게 가리기"),
)

/**
 * The single annotation-tool sheet shared by the full dock, split mode and
 * selection flows. It owns 글상자 and every shape/markup/stamp tool so the
 * bottom dock can stay at six primary slots.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerToolMoreSheet(
    currentTool: Tool,
    onSelectTool: (Tool) -> Unit,
    onDismiss: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    styleEnabled: Boolean = false,
    onStyle: (() -> Unit)? = null,
    favorites: List<PenPreset> = emptyList(),
    currentPen: PenPreset? = null,
    onApplyFavorite: ((PenPreset) -> Unit)? = null,
    onOpenPenRegistry: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeLabel = if (isDockAnnotationTool(currentTool)) {
        "현재 ${dockAnnotationLabel(currentTool)} 도구가 선택되어 있습니다."
    } else {
        "도구를 고른 뒤 페이지 위에서 사용하세요."
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("주석 도구", style = MaterialTheme.typography.titleLarge)
            Text(
                activeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (currentPen != null && onApplyFavorite != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "즐겨찾는 펜",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                FavoritePenStrip(
                    favorites = favorites,
                    current = currentPen,
                    onApply = onApplyFavorite,
                    onOpenRegistry = { onOpenPenRegistry?.invoke() },
                )
            }
            if (onUndo != null || onRedo != null || (onStyle != null && styleEnabled)) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "빠른 작업",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    onUndo?.let { undo ->
                        AssistChip(
                            onClick = undo,
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
                    }
                    onRedo?.let { redo ->
                        AssistChip(
                            onClick = redo,
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
                    }
                    if (onStyle != null && styleEnabled) {
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
                }
            }
            Spacer(Modifier.height(14.dp))
            ToolOptionSection(
                title = "기본 도구",
                options = baseToolOptions,
                currentTool = currentTool,
                onSelectTool = onSelectTool,
            )
            Spacer(Modifier.height(14.dp))
            ToolOptionSection(
                title = "글상자·도형",
                options = textAndShapeToolOptions,
                currentTool = currentTool,
                onSelectTool = onSelectTool,
            )
            Spacer(Modifier.height(14.dp))
            ToolOptionSection(
                title = "표시·스탬프",
                options = markupAndStampToolOptions,
                currentTool = currentTool,
                onSelectTool = onSelectTool,
            )
        }
    }
}

@Composable
private fun ToolOptionSection(
    title: String,
    options: List<ToolOption>,
    currentTool: Tool,
    onSelectTool: (Tool) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
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
    options.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row.forEach { option ->
                ToolOptionTile(
                    option = option,
                    selected = currentTool == option.tool,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectTool(option.tool) },
                )
            }
            if (row.size == 1) {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ToolOptionTile(
    option: ToolOption,
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
                option.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                option.hint?.let { hint ->
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val styleToolOptions = listOf(
    ToolOption(Tool.PEN, Icons.Outlined.Edit, "펜"),
    ToolOption(Tool.HIGHLIGHT, Icons.Outlined.Highlight, "형광펜"),
    ToolOption(Tool.TEXT, Icons.Outlined.TextFields, "글상자"),
    ToolOption(Tool.RECT, Icons.Outlined.CropSquare, "사각형"),
    ToolOption(Tool.ARROW, Icons.Outlined.NorthEast, "화살표"),
    ToolOption(Tool.LINE, Icons.Outlined.HorizontalRule, "직선"),
    ToolOption(Tool.ELLIPSE, Icons.Outlined.Circle, "타원"),
    ToolOption(Tool.UNDERLINE, Icons.Outlined.FormatUnderlined, "밑줄"),
    ToolOption(Tool.STRIKEOUT, Icons.Outlined.FormatStrikethrough, "취소선"),
    ToolOption(Tool.STAMP, Icons.Outlined.Approval, "스탬프"),
    ToolOption(Tool.SIGNATURE, Icons.Outlined.Draw, "서명"),
    ToolOption(Tool.REDACT, Icons.Outlined.VisibilityOff, "가림"),
)

/** Contextual style sheet: palette, size, opacity and related tools. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerStyleSheet(
    state: ViewerUiState,
    onSelectTool: (Tool) -> Unit,
    onColor: (Int) -> Unit,
    onStrokeWidth: (Float) -> Unit,
    onFontSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("스타일", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${styleToolLabel(state.tool)} 도구의 색상과 크기를 조정합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("완료") }
            }
            Spacer(Modifier.height(10.dp))

            Text("도구", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                styleToolOptions.forEach { option ->
                    FilterChip(
                        selected = state.tool == option.tool,
                        onClick = { onSelectTool(option.tool) },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Icon(
                                option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }

            if (state.tool != Tool.REDACT) {
                Spacer(Modifier.height(16.dp))
                Text("색상", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    annotationPalette.forEach { swatch ->
                        ColorDot(
                            color = swatch,
                            selected = state.color == swatch,
                            onClick = { onColor(swatch) },
                        )
                    }
                }
            }

            when (state.tool) {
                Tool.TEXT -> {
                    Spacer(Modifier.height(16.dp))
                    SettingHeader(
                        label = "글자 크기",
                        value = "${(state.fontSize * 1000).roundToInt()}",
                    )
                    Slider(
                        value = state.fontSize,
                        onValueChange = onFontSize,
                        valueRange = 0.012f..0.05f,
                    )
                }

                Tool.PEN,
                Tool.RECT,
                Tool.ARROW,
                Tool.LINE,
                Tool.ELLIPSE,
                Tool.UNDERLINE,
                Tool.STRIKEOUT,
                -> {
                    Spacer(Modifier.height(16.dp))
                    SettingHeader(
                        label = "굵기",
                        value = "${(state.strokeWidth.coerceIn(0.001f, 0.012f) * 1000).roundToInt()}",
                    )
                    Slider(
                        value = state.strokeWidth.coerceIn(0.001f, 0.012f),
                        onValueChange = onStrokeWidth,
                        valueRange = 0.001f..0.012f,
                    )
                }

                Tool.REDACT -> {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "가림 도구는 선택한 영역을 검게 처리합니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }

                else -> Unit
            }

            if (state.tool != Tool.HIGHLIGHT && state.tool != Tool.REDACT) {
                Spacer(Modifier.height(16.dp))
                SettingHeader(
                    label = stringResource(R.string.opacity),
                    value = stringResource(R.string.opacity_percent, (state.opacity * 100).roundToInt()),
                )
                Slider(
                    value = state.opacity,
                    onValueChange = onOpacity,
                    valueRange = 0.1f..1f,
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "변경 사항은 다음 획부터 적용됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingHeader(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun styleToolLabel(tool: Tool): String = when (tool) {
    Tool.VIEW -> "보기"
    Tool.PEN -> "펜"
    Tool.HIGHLIGHT -> "형광펜"
    Tool.TEXT -> "글상자"
    Tool.RECT -> "사각형"
    Tool.ARROW -> "화살표"
    Tool.UNDERLINE -> "밑줄"
    Tool.STRIKEOUT -> "취소선"
    Tool.ELLIPSE -> "타원"
    Tool.LINE -> "직선"
    Tool.STAMP -> "스탬프"
    Tool.SIGNATURE -> "서명"
    Tool.REDACT -> "가림"
    Tool.ERASER -> "지우개"
    Tool.LASSO -> "올가미"
    Tool.LASSO_RECT -> "사각 올가미"
}

@Composable
internal fun ColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color(color), CircleShape)
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
