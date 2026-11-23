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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import com.hackerdjakodjo.mypdfwriter.data.MaskingCards
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import kotlin.math.roundToInt

private data class MaskingEditorTarget(val pageIndex: Int, val card: MaskingCard?)

private data class PageCard(val pageIndex: Int, val card: MaskingCard)

/**
 * Study manager for the offline masking cards. Lists every card in the notebook with
 * its page number and state, shows aggregate progress, and lets the student create,
 * edit, delete or jump to a card. The on-page tape overlay toggle lives here too so a
 * student can give the pen tools back a clear canvas at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaskingCardsSheet(
    notebook: Notebook,
    currentPageIndex: Int,
    showOnPage: Boolean,
    onToggleShowOnPage: (Boolean) -> Unit,
    onCardChange: (Int, MaskingCard) -> Unit,
    onDeleteCard: (Int, String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(notebook) {
        notebook.pages.flatMapIndexed { index, page ->
            page.maskingCards.map { PageCard(index, it) }
        }
    }
    val score = MaskingCards.score(entries.map { it.card })
    var editorTarget by remember { mutableStateOf<MaskingEditorTarget?>(null) }

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
                        Text("가리기 카드", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "질문을 가리고 정답을 확인하는 오프라인 복습",
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
                            MaskingCards.progressText(score),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { score.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "전체 ${score.total} · 안 봄 ${score.hidden} · 정답 확인 ${score.revealed} · " +
                                "정답 ${score.correct} · 오답 ${score.wrong}",
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
                        Text("페이지에 테이프 표시", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (showOnPage) {
                                "카드가 있는 페이지에 질문/정답 테이프를 겹쳐 표시"
                            } else {
                                "테이프 숨김 · 필기 도구가 페이지를 완전히 사용"
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
                    onClick = { editorTarget = MaskingEditorTarget(currentPageIndex, null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("현재 페이지에 카드 추가")
                }
            }

            item {
                Text(
                    "가리기 카드는 공부용 데이터입니다. 노트 JSON과 로컬 백업에는 저장되지만 " +
                        "PDF 내보내기에는 포함되지 않습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "아직 카드가 없습니다. 위 버튼으로 질문과 정답을 추가해 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { _, entry ->
                MaskingCardRow(
                    pageIndex = entry.pageIndex,
                    card = entry.card,
                    onJump = { onJump(entry.pageIndex) },
                    onEdit = { editorTarget = MaskingEditorTarget(entry.pageIndex, entry.card) },
                    onDelete = { onDeleteCard(entry.pageIndex, entry.card.id) },
                )
            }
        }
    }

    editorTarget?.let { target ->
        MaskingCardEditorDialog(
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
private fun MaskingCardRow(
    pageIndex: Int,
    card: MaskingCard,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
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
                    .background(maskStateColor(card.state)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    card.prompt.ifBlank { "(질문 없음)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    card.answer.ifBlank { "(정답 없음)" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${pageIndex + 1}쪽 · ${maskStateLabel(card.state)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "카드 수정")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "카드 삭제")
            }
        }
    }
}

@Composable
private fun MaskingCardEditorDialog(
    initial: MaskingCard?,
    onDismiss: () -> Unit,
    onSave: (MaskingCard) -> Unit,
) {
    val base = initial ?: MaskingCard()
    var prompt by remember { mutableStateOf(base.prompt) }
    var answer by remember { mutableStateOf(base.answer) }
    var x by remember { mutableStateOf((base.x * 100).roundToInt().toString()) }
    var y by remember { mutableStateOf((base.y * 100).roundToInt().toString()) }
    var w by remember { mutableStateOf((base.w * 100).roundToInt().toString()) }
    var h by remember { mutableStateOf((base.h * 100).roundToInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "가리기 카드 추가" else "가리기 카드 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("질문") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text("정답") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "테이프 위치 (페이지 크기 대비 %)",
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
                            prompt = prompt,
                            answer = answer,
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

private fun maskStateLabel(state: MaskingCardState): String = when (state) {
    MaskingCardState.HIDDEN -> maskingStateLabel(MaskingCardState.HIDDEN)
    MaskingCardState.REVEALED -> maskingStateLabel(MaskingCardState.REVEALED)
    MaskingCardState.CORRECT -> "정답"
    MaskingCardState.WRONG -> "오답"
}

private fun maskStateColor(state: MaskingCardState): Color = when (state) {
    MaskingCardState.HIDDEN -> Color(0xFFC79A2E)
    MaskingCardState.REVEALED -> Color(0xFF42779C)
    MaskingCardState.CORRECT -> Color(0xFF3A8A4E)
    MaskingCardState.WRONG -> Color(0xFFB44A40)
}
