package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.pdf.PdfMetadata
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    entries: List<PdfOutlineEntry>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("목차", style = MaterialTheme.typography.titleLarge)
            Text(
                "${entries.size}개 항목",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text(
                    "이 PDF에는 목차가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = entry.pageIndex != null) {
                                    entry.pageIndex?.let(onSelect)
                                }
                                .padding(start = (entry.level.coerceIn(0, 6) * 16).dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                entry.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (entry.pageIndex != null) {
                                Text(
                                    "${entry.pageIndex + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesSheet(
    metadata: PdfMetadata?,
    fileSize: Long,
    pageCount: Int,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(0.dp))
                Text(" 문서 정보", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                "${pageCount}쪽 · ${formatBytes(fileSize)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            PropertyRow("제목", metadata?.title.orEmpty().ifBlank { "—" })
            PropertyRow("작성자", metadata?.author.orEmpty().ifBlank { "—" })
            PropertyRow("주제", metadata?.subject.orEmpty().ifBlank { "—" })
            PropertyRow("키워드", metadata?.keywords.orEmpty().ifBlank { "—" })
            PropertyRow("보안", if (metadata?.encrypted == true) "암호 보호됨" else "암호 없음")
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Text("문서 정보 편집")
            }
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
