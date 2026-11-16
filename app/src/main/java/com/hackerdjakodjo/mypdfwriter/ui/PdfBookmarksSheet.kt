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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfBookmarksSheet(
    bookmarks: List<PdfBookmark>,
    currentPage: Int,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PdfBookmark?>(null) }
    val sorted = remember(bookmarks) {
        bookmarks.sortedWith(compareBy<PdfBookmark>({ it.pageIndex }, { it.createdAt }))
    }
    val currentBookmark = bookmarks.firstOrNull { it.pageIndex == currentPage }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "북마크",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${bookmarks.size}개",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "다시 찾고 싶은 페이지를 저장하고 한 번에 이동하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = { showAddDialog = true },
                enabled = currentBookmark == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (currentBookmark == null) "현재 ${currentPage + 1}쪽 북마크 추가"
                    else "현재 ${currentPage + 1}쪽은 북마크됨",
                )
            }
            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(10.dp))

            if (sorted.isEmpty()) {
                Text(
                    "아직 북마크가 없습니다. 현재 페이지를 북마크해 두면 나중에 바로 돌아올 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(sorted, key = { it.id }) { bookmark ->
                        BookmarkRow(
                            bookmark = bookmark,
                            current = bookmark.pageIndex == currentPage,
                            onJump = { onJump(bookmark.pageIndex) },
                            onRename = { renameTarget = bookmark },
                            onDelete = { onDelete(bookmark.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(20.dp))
        }
    }

    if (showAddDialog) {
        BookmarkTitleDialog(
            dialogTitle = "현재 페이지 북마크",
            initialTitle = "${currentPage + 1}쪽 북마크",
            confirmLabel = "추가",
            onDismiss = { showAddDialog = false },
            onConfirm = { title ->
                showAddDialog = false
                onAdd(title)
            },
        )
    }

    renameTarget?.let { bookmark ->
        BookmarkTitleDialog(
            dialogTitle = "북마크 이름 변경",
            initialTitle = bookmark.title.ifBlank { "${bookmark.pageIndex + 1}쪽 북마크" },
            confirmLabel = "저장",
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                renameTarget = null
                onRename(bookmark.id, title)
            },
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: PdfBookmark,
    current: Boolean,
    onJump: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val date = remember(bookmark.createdAt) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(bookmark.createdAt))
    }

    Surface(
        onClick = onJump,
        shape = RoundedCornerShape(12.dp),
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${bookmark.pageIndex + 1}쪽",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.title.ifBlank { "${bookmark.pageIndex + 1}쪽 북마크" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Outlined.Edit, contentDescription = "북마크 이름 변경")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "북마크 삭제",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun BookmarkTitleDialog(
    dialogTitle: String,
    initialTitle: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("제목") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
