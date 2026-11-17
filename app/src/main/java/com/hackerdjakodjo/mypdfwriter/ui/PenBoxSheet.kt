package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

internal fun penStyleLabel(style: PenStyle): String = when (style) {
    PenStyle.BALLPOINT -> "볼펜"
    PenStyle.FOUNTAIN -> "만년필"
    PenStyle.BRUSH -> "붓"
    PenStyle.PENCIL -> "연필"
}

/**
 * Bottom sheet for saved pens. Applying a preset updates the active tool, colour,
 * width, opacity and pen style in one tap. Presets stay local in PenBoxStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenBoxSheet(
    presets: List<PenPreset>,
    current: PenPreset,
    onDismiss: () -> Unit,
    onApply: (PenPreset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDelete: (PenPreset) -> Unit,
    onRename: (PenPreset, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PenPreset?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(NOTE_PEN_PRESETS_LABEL, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "저장한 펜을 눌러 바로 사용하세요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PenBoxPreview(preset = current, modifier = Modifier.width(88.dp).height(36.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets, key = { it.id }) { preset ->
                    val active = preset.color == current.color &&
                        preset.width == current.width &&
                        preset.opacity == current.opacity &&
                        preset.penStyle == current.penStyle &&
                        preset.tool == current.tool
                    PenBoxRow(
                        preset = preset,
                        active = active,
                        canDelete = presets.size > 1,
                        onApply = { onApply(preset) },
                        onRename = { renameTarget = preset },
                        onDelete = { onDelete(preset) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { saveDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("현재 펜을 내 펜에 저장")
            }
        }
    }

    if (saveDialog) {
        PenNameDialog(
            title = "내 펜에 저장",
            initial = "내 펜 ${presets.size + 1}",
            confirmLabel = "저장",
            onDismiss = { saveDialog = false },
            onConfirm = { name ->
                saveDialog = false
                onSaveCurrent(name)
            },
        )
    }

    val target = renameTarget
    if (target != null) {
        PenNameDialog(
            title = "펜 이름 변경",
            initial = target.name,
            confirmLabel = "변경",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRename(target, name)
            },
        )
    }
}

@Composable
private fun PenBoxRow(
    preset: PenPreset,
    active: Boolean,
    canDelete: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = if (active) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(Color(preset.color))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        if (preset.tool == Tool.HIGHLIGHT) {
                            append("형광펜")
                        } else {
                            append(penStyleLabel(preset.penStyle))
                        }
                        append(" · ")
                        append(preset.width.times(1000).roundToInt())
                        append(" · ")
                        append((preset.opacity * 100).roundToInt())
                        append("%")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PenBoxPreview(
                preset = preset,
                modifier = Modifier.width(76.dp).height(30.dp),
            )
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "이름 변경",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "펜 삭제",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun PenBoxPreview(preset: PenPreset, modifier: Modifier = Modifier) {
    val type = if (preset.tool == Tool.HIGHLIGHT) AnnotationType.HIGHLIGHT else AnnotationType.PEN
    val points = remember {
        (0..10).map { index ->
            val t = index / 10f
            NormalizedPoint(
                x = 0.06f + t * 0.88f,
                y = 0.5f + sin(t * PI * 1.6).toFloat() * 0.16f,
            )
        }
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            AnnotationPainter.drawOne(
                canvas = canvas.nativeCanvas,
                annotation = Annotation(
                    type = type,
                    page = 0,
                    color = preset.color,
                    width = noteStrokeWidth(type, preset.width),
                    points = points,
                    opacity = preset.opacity,
                    penStyle = preset.penStyle,
                ),
                width = size.width,
                height = size.height,
            )
        }
    }
}

@Composable
private fun PenNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim().ifBlank { initial }) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
