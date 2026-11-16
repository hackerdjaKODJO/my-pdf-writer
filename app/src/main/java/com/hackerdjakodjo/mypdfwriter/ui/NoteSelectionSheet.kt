package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val NOTE_SELECTION_NUDGE_STEP = 0.01f
internal const val NOTE_SELECTION_SCALE_SHRINK = 0.9f
internal const val NOTE_SELECTION_SCALE_GROW = 1.1f
internal const val NOTE_SELECTION_ROTATE_STEP = 90

/**
 * Lasso selection editor.
 *
 * The sheet stays open after transforms so several nudges, scales or rotations can
 * be applied; each call creates one undo step in the editor. Copy and paste are
 * session-only: the clipboard survives page switches inside this editor, but not
 * closing the notebook.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteSelectionSheet(
    selectedCount: Int,
    clipboardCount: Int,
    clipboardSourcePage: Int?,
    onNudge: (Float, Float) -> Unit,
    onScale: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasSelection = selectedCount > 0
    val hasClipboard = clipboardCount > 0
    val clipboardLabel = when {
        !hasClipboard -> "클립보드 비어 있음"
        clipboardSourcePage != null -> "클립보드 ${clipboardCount}개 · ${clipboardSourcePage + 1}쪽"
        else -> "클립보드 ${clipboardCount}개"
    }

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
                    Text("선택 편집", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${selectedCount}개 선택 · $clipboardLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            SelectionSectionLabel("이동")
            Text(
                "1%씩 이동 · 페이지 밖으로는 나가지 않습니다",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NudgeButton(
                    label = "왼쪽",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = hasSelection,
                    onClick = { onNudge(-NOTE_SELECTION_NUDGE_STEP, 0f) },
                    modifier = Modifier.weight(1f),
                )
                NudgeButton(
                    label = "위",
                    icon = {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = hasSelection,
                    onClick = { onNudge(0f, -NOTE_SELECTION_NUDGE_STEP) },
                    modifier = Modifier.weight(1f),
                )
                NudgeButton(
                    label = "아래",
                    icon = {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = hasSelection,
                    onClick = { onNudge(0f, NOTE_SELECTION_NUDGE_STEP) },
                    modifier = Modifier.weight(1f),
                )
                NudgeButton(
                    label = "오른쪽",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    enabled = hasSelection,
                    onClick = { onNudge(NOTE_SELECTION_NUDGE_STEP, 0f) },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            SelectionSectionLabel("크기")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onScale(NOTE_SELECTION_SCALE_SHRINK) },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("− 10%")
                }
                Text(
                    "선택 중심",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onScale(NOTE_SELECTION_SCALE_GROW) },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ 10%")
                }
            }

            Spacer(Modifier.height(16.dp))
            SelectionSectionLabel("회전")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onRotate(-NOTE_SELECTION_ROTATE_STEP) },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("− 90°")
                }
                Text(
                    "A4 비율 보정",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onRotate(NOTE_SELECTION_ROTATE_STEP) },
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+ 90°")
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("복사")
                }
                OutlinedButton(
                    onClick = onPaste,
                    enabled = hasClipboard,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("붙여넣기")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onDuplicate,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("복제")
                }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = hasSelection,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("삭제")
                }
            }

            Spacer(Modifier.height(16.dp))
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
private fun SelectionSectionLabel(label: String) {
    Text(
        label,
        modifier = Modifier.padding(bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NudgeButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(
            horizontal = 4.dp,
            vertical = 8.dp,
        ),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
