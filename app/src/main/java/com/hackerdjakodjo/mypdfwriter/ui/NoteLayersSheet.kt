package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import kotlin.math.roundToInt

/**
 * Controlled layer panel for one notebook page.
 *
 * The caller owns [page] and [activeLayerId]. Every mutation is expressed through
 * the pure [NoteLayers] operations, so the editor can put the returned page on its
 * own undo stack. Layers are visual grouping only; this sheet never creates PDF
 * pages or network state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteLayersSheet(
    page: NotePage,
    activeLayerId: String,
    onChange: (NotePage) -> Unit,
    onActiveChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // NotePage normally carries at least one layer; keep the panel usable even if
    // a caller hands us an empty list.
    val fallbackLayer = remember(page.id) { NoteLayer(name = "레이어 1") }
    val layers = page.layers.ifEmpty { listOf(fallbackLayer) }
    val active = NoteLayers.activeLayer(page, activeLayerId) ?: layers.first()
    val selected = layers.firstOrNull { it.id == active.id } ?: layers.first()
    val selectedIndex = layers.indexOfFirst { it.id == selected.id }
    // One slider drag must be one undo step, not dozens of frames.
    var draftOpacity by remember(selected.id) { mutableFloatStateOf(selected.opacity) }
    val defaultLayerId = layers.first().id

    var renameLayerId by remember { mutableStateOf<String?>(null) }
    var deleteLayerId by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun addLayer() {
        val updated = NoteLayers.addLayer(page, nextLayerName(layers))
        onChange(updated)
        updated.layers.lastOrNull()?.let { onActiveChange(it.id) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("레이어", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${layers.size}개 · 위쪽 레이어가 더 앞에 그려집니다",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("완료") }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "레이어는 필기를 묶어 보여주는 시각적 그룹입니다. " +
                            "PDF 페이지가 따로 만들어지지는 않습니다.",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            items(layers.asReversed(), key = { it.id }) { layer ->
                LayerRow(
                    layer = layer,
                    selected = layer.id == selected.id,
                    annotationCount = annotationCount(page, layer.id, defaultLayerId),
                    onSelect = { onActiveChange(layer.id) },
                    onToggleVisible = {
                        onChange(NoteLayers.setVisible(page, layer.id, !layer.visible))
                    },
                    onToggleLock = {
                        onChange(NoteLayers.setLocked(page, layer.id, !layer.locked))
                    },
                )
            }

            item {
                OutlinedButton(
                    onClick = ::addLayer,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("레이어 추가")
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    "선택한 레이어 · ${selected.name}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("불투명도", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(draftOpacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = draftOpacity,
                    onValueChange = { draftOpacity = it },
                    onValueChangeFinished = {
                        onChange(NoteLayers.setOpacity(page, selected.id, draftOpacity))
                    },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onChange(NoteLayers.moveLayer(page, selected.id, 1))
                        },
                        enabled = selectedIndex < layers.lastIndex,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("위로")
                    }
                    OutlinedButton(
                        onClick = {
                            onChange(NoteLayers.moveLayer(page, selected.id, -1))
                        },
                        enabled = selectedIndex > 0,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("아래로")
                    }
                }
                Row {
                    TextButton(
                        onClick = { renameLayerId = selected.id },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("이름 변경")
                    }
                    TextButton(
                        onClick = { deleteLayerId = selected.id },
                        enabled = layers.size > 1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("삭제")
                    }
                }
                Text(
                    "잠긴 레이어도 화면에는 표시됩니다. 삭제해도 필기는 다른 레이어로 이동합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val renameTarget = renameLayerId?.let { id -> layers.firstOrNull { it.id == id } }
    if (renameTarget != null) {
        LayerNameDialog(
            initialName = renameTarget.name,
            onDismiss = { renameLayerId = null },
            onConfirm = { name ->
                onChange(NoteLayers.renameLayer(page, renameTarget.id, name))
                renameLayerId = null
            },
        )
    }

    val deleteTarget = deleteLayerId?.let { id -> layers.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        val fallback = fallbackLayerFor(layers, deleteTarget.id)
        LayerDeleteDialog(
            layer = deleteTarget,
            annotationCount = annotationCount(page, deleteTarget.id, defaultLayerId),
            fallbackName = fallback?.name ?: "다른 레이어",
            onDismiss = { deleteLayerId = null },
            onConfirm = {
                val updated = NoteLayers.deleteLayer(page, deleteTarget.id)
                if (updated != page) {
                    onChange(updated)
                    if (deleteTarget.id == active.id) {
                        fallback?.let { onActiveChange(it.id) }
                    }
                }
                deleteLayerId = null
            },
        )
    }
}

@Composable
private fun LayerRow(
    layer: NoteLayer,
    selected: Boolean,
    annotationCount: Int,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    onToggleLock: () -> Unit,
) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    layer.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (layer.visible) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                val opacity = (layer.opacity * 100).roundToInt()
                val details = if (opacity < 100) "필기 ${annotationCount}개 · $opacity%" else "필기 ${annotationCount}개"
                Text(
                    details,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleVisible) {
                Icon(
                    imageVector = if (layer.visible) {
                        Icons.Outlined.Visibility
                    } else {
                        Icons.Outlined.VisibilityOff
                    },
                    contentDescription = if (layer.visible) "레이어 숨기기" else "레이어 표시",
                    tint = if (layer.visible) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            IconButton(onClick = onToggleLock) {
                Icon(
                    imageVector = if (layer.locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                    contentDescription = if (layer.locked) "레이어 잠금 해제" else "레이어 잠금",
                    tint = if (layer.locked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun LayerNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("레이어 이름 변경") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("레이어 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun LayerDeleteDialog(
    layer: NoteLayer,
    annotationCount: Int,
    fallbackName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("레이어 삭제") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("‘${layer.name}’ 레이어를 삭제할까요?")
                Text(
                    if (annotationCount > 0) {
                        "이 레이어의 필기 ${annotationCount}개는 ‘$fallbackName’ 레이어로 이동합니다. " +
                            "필기는 삭제되지 않습니다."
                    } else {
                        "이 레이어에는 필기가 없습니다. 빈 레이어만 삭제됩니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private fun annotationCount(page: NotePage, layerId: String, defaultLayerId: String): Int =
    page.annotations.count { (it.layerId ?: defaultLayerId) == layerId }

/** Mirrors [NoteLayers.deleteLayer]: nearest lower layer, otherwise the next one. */
private fun fallbackLayerFor(layers: List<NoteLayer>, id: String): NoteLayer? {
    if (layers.size <= 1) return null
    val index = layers.indexOfFirst { it.id == id }
    if (index < 0) return null
    return if (index > 0) layers[index - 1] else layers[index + 1]
}

private fun nextLayerName(layers: List<NoteLayer>): String {
    val used = layers.mapTo(HashSet()) { it.name }
    var number = layers.size + 1
    while ("레이어 $number" in used) number++
    return "레이어 $number"
}
