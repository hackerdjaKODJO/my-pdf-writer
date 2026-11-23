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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import kotlin.math.roundToInt

internal val NOTE_PALETTE = listOf(
    0xFF1B1C19.toInt(),
    0xFF1F4E79.toInt(),
    0xFFB3261E.toInt(),
    0xFFB4693C.toInt(),
    0xFF2E6B4F.toInt(),
    0xFF6A4C93.toInt(),
    0xFF2C7A7B.toInt(),
    0xFFF2C94C.toInt(),
)

/** Spoken names paired with [NOTE_PALETTE], so every swatch is identifiable. */
internal val NOTE_PALETTE_LABELS = listOf(
    "검정", "파랑", "빨강", "주황", "초록", "보라", "청록", "노랑",
)

internal fun noteColorName(color: Int): String =
    NOTE_PALETTE_LABELS.getOrNull(NOTE_PALETTE.indexOf(color)) ?: "사용자 색상"

/** Spoken name of a style slider, including its current formatted value. */
internal fun noteStyleSliderDescription(label: String, valueLabel: String): String =
    sliderLabel(label, valueLabel)

/** Shared note vocabulary; keep the study-card names unchanged. */
internal const val NOTE_TEXT_TOOL_LABEL = "글상자"
internal const val NOTE_PAGE_MANAGER_LABEL = "페이지"
internal const val NOTE_PEN_PRESETS_LABEL = "내 펜"
internal const val NOTE_MASKING_CARD_LABEL = "가리기 카드"
internal const val NOTE_CHECKLIST_LABEL = "체크리스트"

internal val NOTE_SHAPE_TOOLS = listOf(Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE)
internal val NOTE_LASSO_TOOLS = listOf(Tool.LASSO, Tool.LASSO_RECT)

internal fun noteIsShapeTool(tool: Tool): Boolean = tool in NOTE_SHAPE_TOOLS

internal fun noteIsLassoTool(tool: Tool): Boolean = tool in NOTE_LASSO_TOOLS

internal fun noteStyleShowsColor(tool: Tool): Boolean = when (tool) {
    Tool.ERASER, Tool.LASSO, Tool.LASSO_RECT, Tool.VIEW -> false
    else -> true
}

internal fun noteStyleShowsPenType(tool: Tool): Boolean = tool == Tool.PEN

internal fun noteStyleShowsThickness(tool: Tool): Boolean = when (tool) {
    Tool.PEN,
    Tool.HIGHLIGHT,
    Tool.RECT,
    Tool.ARROW,
    Tool.LINE,
    Tool.ELLIPSE -> true

    else -> false
}

internal fun noteStyleShowsOpacity(tool: Tool): Boolean = when (tool) {
    Tool.PEN,
    Tool.TEXT,
    Tool.RECT,
    Tool.ARROW,
    Tool.LINE,
    Tool.ELLIPSE -> true

    else -> false
}

internal fun noteStyleShowsFontSize(tool: Tool): Boolean = tool == Tool.TEXT

internal fun noteStyleTitle(tool: Tool): String = when (tool) {
    Tool.PEN -> "펜 스타일"
    Tool.HIGHLIGHT -> "형광펜 스타일"
    Tool.TEXT -> "$NOTE_TEXT_TOOL_LABEL 스타일"
    Tool.ERASER -> "지우개"
    Tool.LASSO, Tool.LASSO_RECT -> "선택"
    Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> "도형 스타일"
    else -> "스타일"
}

internal fun notePagePreviousEnabled(pageIndex: Int): Boolean = pageIndex > 0

internal fun notePageNextEnabled(pageIndex: Int, pageCount: Int): Boolean =
    pageIndex in 0 until pageCount - 1

/**
 * Contextual style sheet for the active note tool.
 *
 * The editor keeps one style state for all tools, but only shows the controls the
 * current tool actually uses. Saved pens remain available through [onOpenPenBox]
 * so the existing PenBoxSheet is still one tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteStyleSheet(
    tool: Tool,
    color: Int,
    onColor: (Int) -> Unit,
    strokeWidth: Float,
    onStrokeWidth: (Float) -> Unit,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    penStyle: PenStyle,
    onPenStyle: (PenStyle) -> Unit,
    onOpenPenBox: () -> Unit,
    onDismiss: () -> Unit,
    favorites: List<PenPreset> = emptyList(),
    currentPen: PenPreset? = null,
    onApplyFavorite: ((PenPreset) -> Unit)? = null,
    onOpenPenRegistry: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showsColor = noteStyleShowsColor(tool)
    val showsPenType = noteStyleShowsPenType(tool)
    val showsThickness = noteStyleShowsThickness(tool)
    val showsOpacity = noteStyleShowsOpacity(tool)
    val showsFontSize = noteStyleShowsFontSize(tool)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("스타일", style = MaterialTheme.typography.titleLarge)
                    Text(
                        noteStyleTitle(tool),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenPenBox) {
                    Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(NOTE_PEN_PRESETS_LABEL)
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (favorites.isNotEmpty() && currentPen != null && onApplyFavorite != null) {
                StyleSectionLabel("즐겨찾는 펜")
                FavoritePenStrip(
                    favorites = favorites,
                    current = currentPen,
                    onApply = onApplyFavorite,
                    onOpenRegistry = { onOpenPenRegistry?.invoke() ?: onOpenPenBox() },
                )
                Spacer(Modifier.height(10.dp))
            }

            if (showsColor) {
                StyleSectionLabel("색상")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NOTE_PALETTE.forEach { swatch ->
                        NoteColorDot(
                            color = swatch,
                            selected = color == swatch,
                            onClick = { onColor(swatch) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (showsPenType) {
                StyleSectionLabel("펜 종류")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PenStyle.entries.forEach { style ->
                        PenStyleGlyph(
                            style = style,
                            selected = penStyle == style,
                            color = color,
                            onClick = { onPenStyle(style) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (showsFontSize) {
                NoteStyleSlider(
                    label = "글자 크기",
                    valueLabel = "${(fontSize * 1000).roundToInt()}",
                    value = fontSize,
                    range = 0.012f..0.08f,
                    onValueChange = onFontSize,
                )
            }

            if (showsThickness) {
                NoteStyleSlider(
                    label = "굵기",
                    valueLabel = "${(strokeWidth * 1000).roundToInt()}",
                    value = strokeWidth,
                    range = if (tool == Tool.HIGHLIGHT) {
                        com.hackerdjakodjo.mypdfwriter.data.HighlightInk.range
                    } else {
                        0.001f..0.02f
                    },
                    onValueChange = onStrokeWidth,
                )
            }

            if (showsOpacity) {
                NoteStyleSlider(
                    label = "투명도",
                    valueLabel = "${(opacity * 100).roundToInt()}%",
                    value = opacity,
                    range = 0.1f..1f,
                    onValueChange = onOpacity,
                )
            }

            if (!showsColor && !showsPenType && !showsThickness && !showsOpacity && !showsFontSize) {
                Text(
                    "이 도구에는 색상·굵기 설정이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("완료")
            }
        }
    }
}

@Composable
private fun StyleSectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(bottom = 6.dp).semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoteStyleSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = noteStyleSliderDescription(label, valueLabel)
                    // This Compose version exposes the value as stateDescription
                    // (there is no progressText key); Slider keeps its range info.
                    stateDescription = valueLabel
                },
        )
    }
}

@Composable
internal fun NoteColorDot(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    // The whole 48 dp box is the touch target; only the inner circle is drawn,
    // so the visual dot stays compact while TalkBack gets a full-size control.
    Box(
        modifier = modifier
            .size(MIN_TARGET_DP.dp)
            .clickable(onClickLabel = "색상 선택", onClick = onClick)
            .semantics {
                contentDescription = colorChoiceLabel(noteColorName(color), selected)
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = CircleShape,
                ),
        )
    }
}
