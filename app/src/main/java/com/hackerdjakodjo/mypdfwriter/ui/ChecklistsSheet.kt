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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.ChecklistItem
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import kotlin.math.roundToInt

private data class ChecklistEditorTarget(val pageIndex: Int, val card: ChecklistCard?)

private data class PageChecklist(val pageIndex: Int, val card: ChecklistCard)

/**
 * Study manager for the offline interactive checklists. Lists every card in the
 * notebook with its page number and checked/total progress, shows aggregate progress,
 * and lets the student create, edit, delete or jump to a card. The on-page overlay
 * toggle lives here too so a student can give the pen tools back a clear canvas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsSheet(
    notebook: Notebook,
    currentPageIndex: Int,
    showOnPage: Boolean,
    onToggleShowOnPage: (Boolean) -> Unit,
    onCardChange: (Int, ChecklistCard) -> Unit,
    onDeleteCard: (Int, String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(notebook) {
        notebook.pages.flatMapIndexed { index, page ->
            page.checklists.map { PageChecklist(index, it) }
        }
    }
    val progress = Checklists.progress(entries.map { it.card })
    var editorTarget by remember { mutableStateOf<ChecklistEditorTarget?>(null) }

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
                        Text("체크리스트", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "항목을 하나씩 완료하는 오프라인 학습 도구",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            Checklists.progressText(progress),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "전체 ${progress.total}개 항목 · 카드 ${entries.size}개",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                                "카드가 있는 페이지에 체크리스트를 겹쳐 표시"
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
                    onClick = { editorTarget = ChecklistEditorTarget(currentPageIndex, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("현재 페이지에 체크리스트 추가")
                }
            }

            item {
                Text(
                    "체크리스트는 공부용 데이터입니다. 노트 JSON과 로컬 백업에는 저장되지만 " +
                        "PDF 내보내기에는 포함되지 않습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "아직 체크리스트가 없습니다. 위 버튼으로 항목을 추가해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { _, entry ->
                ChecklistRow(
                    pageIndex = entry.pageIndex,
                    card = entry.card,
                    onJump = { onJump(entry.pageIndex) },
                    onEdit = { editorTarget = ChecklistEditorTarget(entry.pageIndex, entry.card) },
                    onDelete = { onDeleteCard(entry.pageIndex, entry.card.id) },
                )
            }
        }
    }

    editorTarget?.let { target ->
        ChecklistEditorDialog(
            initial = target.card,
            onDismiss = { editorTarget = null },
            onSave = { saved ->
                onCardChange(target.pageIndex, saved)
                editorTarget = null
            },
        )
    }
}

@Composable
private fun ChecklistRow(
    pageIndex: Int,
    card: ChecklistCard,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = Checklists.progress(card)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF3A7D5D)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Checklists.progressText(progress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${pageIndex + 1}쪽",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "체크리스트 수정")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "체크리스트 삭제")
            }
        }
    }
}

@Composable
private fun ChecklistEditorDialog(
    initial: ChecklistCard?,
    onDismiss: () -> Unit,
    onSave: (ChecklistCard) -> Unit,
) {
    val base = initial ?: ChecklistCard()
    var title by remember { mutableStateOf(base.title) }
    val items = remember {
        mutableStateListOf<ChecklistItem>().apply { addAll(base.items) }
    }
    var x by remember { mutableStateOf((base.x * 100).roundToInt().toString()) }
    var y by remember { mutableStateOf((base.y * 100).roundToInt().toString()) }
    var w by remember { mutableStateOf((base.w * 100).roundToInt().toString()) }
    var h by remember { mutableStateOf((base.h * 100).roundToInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "체크리스트 추가" else "체크리스트 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("항목", style = MaterialTheme.typography.labelMedium)
                items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.done,
                            onCheckedChange = { checked ->
                                items[index] = item.copy(done = checked)
                            },
                        )
                        OutlinedTextField(
                            value = item.text,
                            onValueChange = { text ->
                                items[index] = item.copy(text = text)
                            },
                            label = { Text("항목 ${index + 1}") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { items.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "항목 삭제")
                        }
                    }
                }
                TextButton(
                    onClick = { if (items.size < Checklists.MAX_ITEMS) items.add(ChecklistItem()) },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(4.dp))
                    Text("항목 추가")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "카드 위치 (페이지 크기 대비 %)",
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlacementField("X", x, Modifier.weight(1f)) { x = it }
                    PlacementField("Y", y, Modifier.weight(1f)) { y = it }
                    PlacementField("W", w, Modifier.weight(1f)) { w = it }
                    PlacementField("H", h, Modifier.weight(1f)) { h = it }
                }
                Text(
                    "0~100 사이 값. 비워 두면 기본 위치를 사용합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        base.copy(
                            title = title,
                            items = items.toList(),
                            x = percent(x, base.x),
                            y = percent(y, base.y),
                            w = percent(w, base.w),
                            h = percent(h, base.h),
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
private fun PlacementField(
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

private fun percent(text: String, fallback: Float): Float =
    text.trim().toFloatOrNull()?.div(100f) ?: fallback
