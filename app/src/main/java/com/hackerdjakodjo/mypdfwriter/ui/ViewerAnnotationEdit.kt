package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.InkSettings
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationSummary
import kotlin.math.roundToInt

internal const val PDF_ANNOTATION_STYLE_TAG = "pdf_annotation_edit"
internal const val PDF_ANNOTATION_DUPLICATE_TAG = "pdf_annotation_duplicate"
internal const val PDF_ANNOTATION_FORWARD_TAG = "pdf_annotation_forward"
internal const val PDF_ANNOTATION_BACKWARD_TAG = "pdf_annotation_backward"
internal const val PDF_ANNOTATION_DELETE_TAG = "pdf_annotation_delete"
internal const val PDF_ANNOTATION_EDIT_DONE_TAG = "pdf_annotation_edit_done"

@Composable
internal fun ViewerAnnotationSelectionBar(
    annotation: Annotation,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnnotationEditAction("편집", Icons.Outlined.Edit, PDF_ANNOTATION_STYLE_TAG, onEdit)
            AnnotationEditAction("복제", Icons.Outlined.ContentCopy, PDF_ANNOTATION_DUPLICATE_TAG, onDuplicate)
            AnnotationEditAction("앞으로", Icons.AutoMirrored.Outlined.Redo, PDF_ANNOTATION_FORWARD_TAG, onForward)
            AnnotationEditAction("뒤로", Icons.AutoMirrored.Outlined.Undo, PDF_ANNOTATION_BACKWARD_TAG, onBackward)
            AnnotationEditAction("삭제", Icons.Outlined.Delete, PDF_ANNOTATION_DELETE_TAG, onDelete)
            AnnotationEditAction("완료", Icons.Outlined.Check, PDF_ANNOTATION_EDIT_DONE_TAG, onDone)
        }
    }
}

@Composable
private fun AnnotationEditAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .sizeIn(minWidth = MIN_TARGET_DP.dp, minHeight = MIN_TARGET_DP.dp)
            .testTag(tag)
            .semantics { contentDescription = label },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ViewerAnnotationStyleSheet(
    annotation: Annotation,
    onDismiss: () -> Unit,
    onApply: (Annotation) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(annotation.id) { mutableStateOf(annotation) }
    val textLike = annotation.type == AnnotationType.TEXT ||
        annotation.type == AnnotationType.STAMP ||
        annotation.type == AnnotationType.SIGNATURE ||
        annotation.type == AnnotationType.COVER_TEXT
    val showWidth = annotation.type != AnnotationType.TEXT &&
        annotation.type != AnnotationType.COVER_TEXT &&
        annotation.type != AnnotationType.REDACT
    val showColor = annotation.type != AnnotationType.REDACT
    val showOpacity = annotation.type != AnnotationType.REDACT
    val showRotation = textLike && annotation.type != AnnotationType.COVER_TEXT

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("주석 편집", style = MaterialTheme.typography.titleLarge)
                    Text(
                        AnnotationSummary.typeLabel(annotation.type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.sizeIn(minWidth = MIN_TARGET_DP.dp, minHeight = MIN_TARGET_DP.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (textLike) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = if (annotation.type == AnnotationType.STAMP) draft.stamp else draft.text,
                    onValueChange = { value ->
                        draft = if (annotation.type == AnnotationType.STAMP) {
                            draft.copy(stamp = value)
                        } else {
                            draft.copy(text = value)
                        }
                    },
                    label = { Text("내용") },
                    singleLine = annotation.type != AnnotationType.TEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showColor) {
                Spacer(Modifier.height(16.dp))
                Text("색상", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    annotationPalette.forEach { color ->
                        val selected = draft.color == color
                        Surface(
                            shape = CircleShape,
                            color = Color(color),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(selected = selected, onClick = { draft = draft.copy(color = color) })
                                .semantics {
                                    contentDescription = annotationColorName(color)
                                    this.selected = selected
                                },
                        ) {
                            if (selected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = if (annotationColorLuminance(color) > 0.5f) Color.Black else Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "가림은 검정색과 완전한 불투명도로 고정됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (showWidth) {
                Spacer(Modifier.height(16.dp))
                val widthValue = "${(draft.width * 1000).roundToInt()}"
                SettingHeader("굵기", widthValue)
                Slider(
                    value = draft.width.coerceIn(InkSettings.MIN_WIDTH, InkSettings.MAX_WIDTH),
                    onValueChange = { draft = draft.copy(width = it) },
                    valueRange = InkSettings.MIN_WIDTH..InkSettings.MAX_WIDTH,
                    modifier = Modifier.semantics {
                        contentDescription = sliderLabel("굵기", widthValue)
                        stateDescription = widthValue
                    },
                )
            }

            if (showOpacity) {
                Spacer(Modifier.height(16.dp))
                val opacityValue = "${(draft.opacity * 100).roundToInt()}%"
                SettingHeader("불투명도", opacityValue)
                Slider(
                    value = draft.opacity.coerceIn(0.05f, 1f),
                    onValueChange = { draft = draft.copy(opacity = it) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.semantics {
                        contentDescription = sliderLabel("불투명도", opacityValue)
                        stateDescription = opacityValue
                    },
                )
            }

            if (textLike) {
                Spacer(Modifier.height(16.dp))
                val fontSizeValue = "${(draft.fontSize * 1000).roundToInt()}"
                SettingHeader("글자 크기", fontSizeValue)
                Slider(
                    value = draft.fontSize.coerceIn(0.012f, 0.06f),
                    onValueChange = { draft = draft.copy(fontSize = it) },
                    valueRange = 0.012f..0.06f,
                    modifier = Modifier.semantics {
                        contentDescription = sliderLabel("글자 크기", fontSizeValue)
                        stateDescription = fontSizeValue
                    },
                )
                if (showRotation) {
                    Spacer(Modifier.height(12.dp))
                    Text("회전", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 90, 180, 270).forEach { rotation ->
                            FilterChip(
                                selected = draft.textRotation == rotation,
                                onClick = { draft = draft.copy(textRotation = rotation) },
                                label = { Text("${rotation}°") },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onApply(draft)
                    onDismiss()
                },
                enabled = !textLike || (
                    if (annotation.type == AnnotationType.STAMP) draft.stamp else draft.text
                    ).isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MIN_TARGET_DP.dp),
            ) {
                Text("적용")
            }
        }
    }
}

private fun annotationColorLuminance(color: Int): Float {
    val r = ((color shr 16) and 0xff) / 255f
    val g = ((color shr 8) and 0xff) / 255f
    val b = (color and 0xff) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}
