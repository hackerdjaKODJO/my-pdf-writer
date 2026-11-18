package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import kotlin.math.roundToInt

/** Compose view of the portable ARGB fill stored in [MemoTint]. */
private val MemoTint.bg: Color get() = Color(background)

/** One card plus the page it lives on. Kept pure so the sheet list is JVM-tested. */
internal data class MemoPageCard(val pageIndex: Int, val card: MemoCard)

/** Page-ordered snapshot of every memo card in the notebook. */
internal fun memoCardEntries(notebook: Notebook): List<MemoPageCard> =
    notebook.pages.flatMapIndexed { index, page ->
        page.memoCards.map { MemoPageCard(index, it) }
    }

/** Parses an X/Y/W/H percent field; a blank or malformed field keeps its old value. */
internal fun memoPercent(text: String, fallback: Float): Float =
    text.trim().toFloatOrNull()?.div(100f) ?: fallback

/** Short placement summary shown under a card row. */
internal fun memoPlacementLabel(card: MemoCard): String =
    "X ${(card.x * 100).roundToInt()}% · Y ${(card.y * 100).roundToInt()}% · " +
        "W ${(card.w * 100).roundToInt()}% · H ${(card.h * 100).roundToInt()}%"

private data class MemoEditorTarget(val pageIndex: Int, val card: MemoCard?)

/**
 * Manager sheet for the offline post-it cards. Lists every card across the
 * notebook, highlights the current page, lets the student jump to a card, toggle
 * the on-page overlay, and add, edit or delete a card (delete asks for confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoCardsSheet(
    notebook: Notebook,
    currentPageIndex: Int,
    showOnPage: Boolean,
    onToggleShowOnPage: (Boolean) -> Unit,
    onCardChange: (Int, MemoCard) -> Unit,
    onDeleteCard: (Int, String) -> Unit,
    onJump: (Int) -> Unit,
    editCardId: String? = null,
    onEditHandled: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(notebook) { memoCardEntries(notebook) }
    var editorTarget by remember { mutableStateOf<MemoEditorTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<MemoPageCard?>(null) }

    // Tapping a card on the canvas asks the sheet to open that card directly.
    LaunchedEffect(editCardId, notebook) {
        if (editCardId != null && editorTarget == null && pendingDelete == null) {
            entries.firstOrNull { it.card.id == editCardId }?.let { entry ->
                editorTarget = MemoEditorTarget(entry.pageIndex, entry.card)
            }
            onEditHandled()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(NOTE_MEMO_CARD_LABEL, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "페이지에 붙여 둔 짧은 메모를 한곳에서 관리",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("페이지에 카드 표시", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (showOnPage) {
                                "카드가 있는 페이지에 메모를 겹쳐 표시"
                            } else {
                                "카드 숨김 · 필기 도구가 페이지를 완전히 사용"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showOnPage, onCheckedChange = onToggleShowOnPage)
                }
            }

            item {
                Button(
                    onClick = { editorTarget = MemoEditorTarget(currentPageIndex, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("현재 페이지에 카드 추가")
                }
            }

            item {
                Text(
                    "메모 카드는 노트 JSON과 로컬 백업에 저장되며 PDF 내보내기에도 기본적으로 포함됩니다. 페이지에서 숨기기는 보기 전용입니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "아직 카드가 없습니다. 위 버튼으로 첫 메모를 붙여 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { _, entry ->
                MemoCardRow(
                    entry = entry,
                    current = entry.pageIndex == currentPageIndex,
                    onJump = { onJump(entry.pageIndex) },
                    onEdit = { editorTarget = MemoEditorTarget(entry.pageIndex, entry.card) },
                    onDelete = { pendingDelete = entry },
                )
            }
        }
    }

    editorTarget?.let { target ->
        MemoCardEditorDialog(
            initial = target.card,
            onDismiss = { editorTarget = null },
            onSave = { saved ->
                onCardChange(target.pageIndex, saved)
                editorTarget = null
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("메모 카드 삭제") },
            text = { Text("이 카드의 메모를 삭제합니다. 실행 취소로 되돌릴 수 있습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCard(entry.pageIndex, entry.card.id)
                        pendingDelete = null
                    },
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun MemoCardRow(
    entry: MemoPageCard,
    current: Boolean,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onJump),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(entry.card.tint.bg),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.card.text.ifBlank { "(빈 메모)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    memoPlacementLabel(entry.card),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${entry.pageIndex + 1}쪽${if (current) " · 현재" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "메모 수정")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "메모 삭제")
            }
        }
    }
}

@Composable
private fun MemoCardEditorDialog(
    initial: MemoCard?,
    onDismiss: () -> Unit,
    onSave: (MemoCard) -> Unit,
) {
    val base = initial ?: MemoCard()
    var text by remember { mutableStateOf(base.text) }
    var tint by remember { mutableStateOf(base.tint) }
    var x by remember { mutableStateOf((base.x * 100).roundToInt().toString()) }
    var y by remember { mutableStateOf((base.y * 100).roundToInt().toString()) }
    var w by remember { mutableStateOf((base.w * 100).roundToInt().toString()) }
    var h by remember { mutableStateOf((base.h * 100).roundToInt().toString()) }
    var tilt by remember { mutableStateOf(base.tilt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "메모 카드 추가" else "메모 카드 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= MemoCard.MAX_TEXT_LENGTH) text = value
                    },
                    label = { Text("메모 내용") },
                    supportingText = { Text("${text.length} / ${MemoCard.MAX_TEXT_LENGTH}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Spacer(Modifier.height(10.dp))
                Text("배경 색상", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MemoTint.entries.forEach { option ->
                        FilterChip(
                            selected = tint == option,
                            onClick = { tint = option },
                            label = { Text(option.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("카드 위치와 크기 (페이지 크기 대비 %)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoPlacementField("X", x, Modifier.weight(1f)) { x = it }
                    MemoPlacementField("Y", y, Modifier.weight(1f)) { y = it }
                    MemoPlacementField("W", w, Modifier.weight(1f)) { w = it }
                    MemoPlacementField("H", h, Modifier.weight(1f)) { h = it }
                }
                Text(
                    "0~100 사이 값. 비워 두면 이전 값을 사용합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "기울기 ${tilt.roundToInt()}°",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = tilt,
                    valueRange = -MemoCard.MAX_TILT..MemoCard.MAX_TILT,
                    onValueChange = { tilt = it.roundToInt().toFloat() },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        base.copy(
                            text = text,
                            tint = tint,
                            x = memoPercent(x, base.x),
                            y = memoPercent(y, base.y),
                            w = memoPercent(w, base.w),
                            h = memoPercent(h, base.h),
                            tilt = tilt,
                            updatedAt = System.currentTimeMillis(),
                        ).normalized(),
                    )
                },
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun MemoPlacementField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.height(64.dp),
    )
}
