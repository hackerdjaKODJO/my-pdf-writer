package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.pdf.TranslateTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcerptSheet(
    excerpts: List<Excerpt>,
    currentPage: Int,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onJump: (Int) -> Unit,
    onTranslate: (String, TranslateTarget) -> Unit,
    onDictionary: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Optional integration hook; the viewer supplies it to open [StudyNoteSheet]. */
    onStudyNote: (() -> Unit)? = null,
    /** Optional drag-and-drop hook; rows become draggable when it is supplied. */
    onDropExcerpt: ((PdfStudyCapture) -> Boolean)? = null,
    captureForExcerpt: (Excerpt) -> PdfStudyCapture? = { null },
    docId: String? = null,
    fingerprint: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showTitleDialog by remember { mutableStateOf(false) }
    var translateTarget by remember { mutableStateOf(TranslateTarget.KOREAN) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "노트에 담기",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${excerpts.size}개",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "현재 ${currentPage + 1}쪽의 텍스트를 노트에 담고, 다른 앱에서 바로 열 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("번역 대상", style = MaterialTheme.typography.labelMedium)
                TranslateTarget.entries.forEach { target ->
                    FilterChip(
                        selected = translateTarget == target,
                        onClick = { translateTarget = target },
                        label = { Text(target.label) },
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Button(onClick = { showTitleDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("현재 페이지 담기")
            }
            if (onStudyNote != null) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onStudyNote, modifier = Modifier.fillMaxWidth()) {
                    Text("PDF → 노트에 담기")
                }
            }
            val dropHandler = onDropExcerpt
            if (dropHandler != null) {
                Spacer(Modifier.size(8.dp))
                StudyNoteDropTarget(
                    onDrop = { capture -> dropHandler(capture) },
                    caption = "발췌 카드를 길게 눌러 여기로 끌어다 놓으세요",
                    expectedDocId = docId,
                    expectedFingerprint = fingerprint,
                )
            }
            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(10.dp))

            if (excerpts.isEmpty()) {
                Text(
                    "아직 담은 내용이 없습니다. 현재 페이지의 텍스트를 노트에 담아 보세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(excerpts, key = { it.id }) { excerpt ->
                        val capture = captureForExcerpt(excerpt)
                        ExcerptRow(
                            excerpt = excerpt,
                            draggable = onDropExcerpt != null && capture != null,
                            capture = capture,
                            onJump = { onJump(excerpt.pageIndex) },
                            onTranslate = { onTranslate(excerpt.text, translateTarget) },
                            onDictionary = { onDictionary(excerpt.text) },
                            onShare = { onShare(excerpt.text) },
                            onDelete = { onDelete(excerpt.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.size(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "번역은 Google 번역 앱 또는 브라우저로 텍스트를 넘깁니다. Google 앱이 네트워크를 사용하며 " +
                        "그 처리는 Google의 정책을 따릅니다. My PDF Writer 자체는 발췌를 서버에 올리지 않고 " +
                        "인터넷 권한도 사용하지 않습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showTitleDialog) {
        ExcerptTitleDialog(
            currentPage = currentPage,
            onDismiss = { showTitleDialog = false },
            onConfirm = { title ->
                showTitleDialog = false
                onAdd(title)
            },
        )
    }
}

@Composable
private fun ExcerptRow(
    excerpt: Excerpt,
    draggable: Boolean,
    capture: PdfStudyCapture?,
    onJump: () -> Unit,
    onTranslate: () -> Unit,
    onDictionary: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val snippet = remember(excerpt.text) {
        excerpt.text.replace(Regex("\\s+"), " ").trim()
    }
    val date = remember(excerpt.createdAt) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(excerpt.createdAt))
    }

    Surface(
        onClick = onJump,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (draggable && capture != null) {
                    Modifier.studyNoteDragSource(capture)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${excerpt.pageIndex + 1}쪽",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (excerpt.title.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        excerpt.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (draggable) {
                    Icon(
                        Icons.Outlined.DragIndicator,
                        contentDescription = "스터디 노트로 끌기",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                snippet,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onTranslate) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Google 번역")
                }
                TextButton(onClick = onDictionary) {
                    Icon(
                        Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("사전")
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("공유")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "발췌 삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExcerptTitleDialog(
    currentPage: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf("${currentPage + 1}쪽 발췌") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        title = { Text("현재 페이지 발췌") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "${currentPage + 1}쪽의 텍스트를 노트에 담습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제목 (선택)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }) { Text("발췌") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
