package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.HighlightInk
import com.hackerdjakodjo.mypdfwriter.data.InkSettings
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal const val PEN_STUDIO_SELECTED_ART_TAG = "pen_studio_selected_art"

internal val PEN_STUDIO_PALETTE = listOf(
    0xFF1B1C19.toInt(),
    0xFF1F4E79.toInt(),
    0xFFB3261E.toInt(),
    0xFFB4693C.toInt(),
    0xFF2E6B4F.toInt(),
    0xFF6A4C93.toInt(),
    0xFF2C7A7B.toInt(),
    0xFFF2C94C.toInt(),
)

internal val PEN_STUDIO_PALETTE_LABELS = listOf(
    "검정", "파랑", "빨강", "주황", "초록", "보라", "청록", "노랑",
)

internal fun penStudioColorName(color: Int): String =
    PEN_STUDIO_PALETTE_LABELS.getOrNull(PEN_STUDIO_PALETTE.indexOf(color)) ?: "사용자 색상"

internal fun penStudioPressureLabel(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "민감"
    PressureCurve.LINEAR -> "보통"
    PressureCurve.FIRM -> "둔감"
}

internal fun penStudioPressureHint(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "가벼운 필압도 잘 반영해요."
    PressureCurve.LINEAR -> "필압에 고르게 반응해요."
    PressureCurve.FIRM -> "필압을 강하게 눌러야 반응해요."
}

private enum class PenPreviewBackground(val label: String) {
    WHITE("흰 PDF"),
    DARK("어두운 PDF"),
    PAPER("노트 종이"),
}

/**
 * Shared pen/highlighter controls used by both the PDF viewer and note editor.
 * All changes apply to the next stroke; existing annotations keep their stored
 * effective pressure and material values.
 */
@Composable
internal fun PenStudioControls(
    tool: Tool,
    color: Int,
    onColor: (Int) -> Unit,
    strokeWidth: Float,
    onStrokeWidth: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    penStyle: PenStyle,
    onPenStyle: (PenStyle) -> Unit,
    pressureInk: Boolean,
    onPressureInk: (Boolean) -> Unit,
    pressureCurve: PressureCurve,
    onPressureCurve: (PressureCurve) -> Unit,
    highlightWidth: Float,
    onHighlightWidth: (Float) -> Unit,
    highlightOpacity: Float,
    onHighlightOpacity: (Float) -> Unit,
) {
    val isHighlight = tool == Tool.HIGHLIGHT
    val activeWidth = if (isHighlight) HighlightInk.clamp(highlightWidth) else strokeWidth
    val activeOpacity = if (isHighlight) {
        HighlightInk.clampOpacity(highlightOpacity)
    } else {
        opacity
    }

    if (!isHighlight) {
        val styleLabel = penStyleLabel(penStyle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PEN_STUDIO_SELECTED_ART_TAG)
                .semantics { contentDescription = "선택한 펜 $styleLabel" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PenInstrumentArtwork(
                style = penStyle,
                color = color,
                modifier = Modifier.size(72.dp),
                bodyColor = MaterialTheme.colorScheme.onSurface,
                cutoutColor = MaterialTheme.colorScheme.surface,
                showSampleStroke = false,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "선택한 펜 $styleLabel",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    penStyleGlyphSpec(penStyle).hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    PenStudioPreview(
        tool = tool,
        color = color,
        width = activeWidth,
        opacity = activeOpacity,
        penStyle = penStyle,
        pressureCurve = pressureCurve,
        pressureInk = pressureInk,
    )

    Spacer(Modifier.height(16.dp))
    PenStudioSectionLabel("색상")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PEN_STUDIO_PALETTE.forEach { swatch ->
            PenStudioColorDot(
                color = swatch,
                selected = color == swatch,
                onClick = { onColor(swatch) },
            )
        }
    }

    if (!isHighlight) {
        Spacer(Modifier.height(14.dp))
        PenStudioSectionLabel("펜 종류")
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
        val hint = when (penStyle) {
            PenStyle.BALLPOINT -> "볼펜은 굵기가 거의 일정하고 필압에 따라 농도가 달라져요."
            PenStyle.FOUNTAIN -> "만년필은 가벼운 접촉에서 부드럽게 퍼져요."
            PenStyle.BRUSH -> "붓은 필압과 속도에 따라 굵기와 질감이 달라져요."
            PenStyle.PENCIL -> "연필은 가벼운 접촉에서 옅고 거친 질감이 나타나요."
        }
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    } else {
        Text(
            "형광펜은 굵기가 일정하고 필압에 따라 색 농도가 달라져요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    PenStudioSlider(
        label = "굵기",
        valueLabel = "${(activeWidth * 1000).roundToInt()}",
        value = activeWidth,
        range = if (isHighlight) HighlightInk.range else InkSettings.MIN_WIDTH..InkSettings.MAX_WIDTH,
        onValueChange = if (isHighlight) onHighlightWidth else onStrokeWidth,
    )

    Spacer(Modifier.height(10.dp))
    PenStudioSlider(
        label = "불투명도",
        valueLabel = "${(activeOpacity * 100).roundToInt()}%",
        value = activeOpacity,
        range = InkSettings.MIN_OPACITY..InkSettings.MAX_OPACITY,
        onValueChange = if (isHighlight) onHighlightOpacity else onOpacity,
    )

    Spacer(Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(Modifier.height(14.dp))
    PenStudioSectionLabel("스타일러스 필압")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("누르는 힘에 따라 잉크 농도와 펜별 굵기가 달라져요.")
            Text(
                "손가락·마우스에는 필압이 적용되지 않아요.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = pressureInk,
            onCheckedChange = onPressureInk,
            modifier = Modifier.semantics {
                contentDescription = "스타일러스 필압"
                stateDescription = if (pressureInk) "켜짐" else "꺼짐"
            },
        )
    }

    Spacer(Modifier.height(12.dp))
    PenStudioSectionLabel("필압 반응")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PressureCurve.entries.forEach { curve ->
            FilterChip(
                selected = pressureCurve == curve,
                onClick = { onPressureCurve(curve) },
                enabled = pressureInk,
                label = { Text(penStudioPressureLabel(curve)) },
                modifier = Modifier.semantics {
                    contentDescription = "${penStudioPressureLabel(curve)}. ${penStudioPressureHint(curve)}"
                    stateDescription = if (pressureCurve == curve) "선택됨" else "선택 안 됨"
                },
            )
        }
    }
    Text(
        if (pressureInk) penStudioPressureHint(pressureCurve) else "필압을 켜면 조절할 수 있어요.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    PressureCurveGraph(
        curve = pressureCurve,
        enabled = pressureInk,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(top = 8.dp),
    )

    Spacer(Modifier.height(10.dp))
    Text(
        "변경 사항은 다음 획부터 적용돼요. 기존 획은 바뀌지 않아요.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PenStudioPreview(
    tool: Tool,
    color: Int,
    width: Float,
    opacity: Float,
    penStyle: PenStyle,
    pressureCurve: PressureCurve,
    pressureInk: Boolean,
) {
    var background by remember { mutableStateOf(PenPreviewBackground.PAPER) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PenPreviewBackground.entries.forEach { option ->
                FilterChip(
                    selected = background == option,
                    onClick = { background = option },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val bg = when (background) {
            PenPreviewBackground.WHITE -> Color.White
            PenPreviewBackground.DARK -> Color(0xFF202226)
            PenPreviewBackground.PAPER -> Color(0xFFF7F1E3)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                .semantics {
                    contentDescription = "필압 미리보기. ${penStudioPressureLabel(pressureCurve)} 반응."
                },
        ) {
            drawIntoCanvas { canvas ->
                val type = if (tool == Tool.HIGHLIGHT) AnnotationType.HIGHLIGHT else AnnotationType.PEN
                val points = (0..24).map { index ->
                    val t = index / 24f
                    val ramp = (0.20f + 0.80f * sin(t * PI).toFloat()).coerceIn(0f, 1f)
                    NormalizedPoint(
                        x = 0.06f + t * 0.88f,
                        y = 0.52f + sin(t * PI * 1.7).toFloat() * 0.13f,
                        pressure = if (pressureInk) pressureCurve.applyTo(ramp) else 1f,
                    )
                }
                AnnotationPainter.drawOne(
                    canvas = canvas.nativeCanvas,
                    annotation = Annotation(
                        type = type,
                        page = 0,
                        color = color,
                        width = width,
                        points = points,
                        opacity = opacity,
                        penStyle = if (type == AnnotationType.PEN) penStyle else PenStyle.BALLPOINT,
                    ),
                    width = size.width,
                    height = size.height,
                )
            }
        }
        Text(
            "예시 획이에요. 실제 펜과 화면에 따라 다르게 느껴질 수 있어요.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun PressureCurveGraph(
    curve: PressureCurve,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val left = 8f
        val top = 8f
        val right = size.width - 8f
        val bottom = size.height - 22f
        drawLine(axis, Offset(left, top), Offset(left, bottom), 1f)
        drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1f)
        val path = Path()
        for (step in 0..40) {
            val x = step / 40f
            val y = if (enabled) curve.applyTo(x) else x
            val px = left + (right - left) * x
            val py = bottom - (bottom - top) * y
            if (step == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))
    }
}

@Composable
private fun PenStudioSectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(bottom = 6.dp)
            .semantics { heading() },
    )
}

@Composable
private fun PenStudioSlider(
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
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = sliderLabel(label, valueLabel)
                    stateDescription = valueLabel
                },
        )
    }
}

@Composable
private fun PenStudioColorDot(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(MIN_TARGET_DP.dp)
            .clickable(onClickLabel = "색상 선택", onClick = onClick)
            .semantics {
                contentDescription = colorChoiceLabel(penStudioColorName(color), selected)
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        )
    }
}
