package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate

private val STUDY_COVER_COLORS = listOf(
    0xFFD8E6F3.toInt(),
    0xFFF6DFC8.toInt(),
    0xFFE2EBCB.toInt(),
    0xFFF3E2EC.toInt(),
    0xFFE8E1F2.toInt(),
    0xFFF5EDC8.toInt(),
    0xFFD9E8E6.toInt(),
    0xFFE9E4D8.toInt(),
)

/**
 * Controlled bottom sheet for the first version of the PDF → study note flow.
 * It is deliberately button-based; dragging excerpts onto a canvas comes later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyNoteSheet(
    source: StudySource,
    excerpts: List<Excerpt>,
    onDismiss: () -> Unit,
    onCreate: (Notebook) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(source.docName) {
        mutableStateOf(StudyNoteBuilder.suggestedTitle(source))
    }
    var coverColor by remember { mutableIntStateOf(StudyNoteBuilder.DEFAULT_COVER_COLOR) }
    var coverStyle by remember { mutableStateOf(CoverStyle.SOLID) }
    var selectedIds by remember(excerpts) {
        mutableStateOf(excerpts.map { it.id }.toSet())
    }

    val selectable = remember(excerpts) { excerpts.filter { it.text.isNotBlank() } }
    val selected = remember(selectable, selectedIds) {
        selectable.filter { it.id in selectedIds }
    }
    val allSelected = selectable.isNotEmpty() && selectable.all { it.id in selectedIds }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "스터디 노트",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${selectable.size}개 발췌",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "PDF에서 모은 발췌를 한 권의 노트로 묶고, 각 발췌의 원본 페이지를 함께 저장합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            StudySourceCard(source)
            Spacer(Modifier.size(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("노트 제목") },
                singleLine = true,
            )
            Spacer(Modifier.size(12.dp))

            StudyCoverPicker(
                title = title,
                coverColor = coverColor,
                coverStyle = coverStyle,
                onCoverColor = { coverColor = it },
                onCoverStyle = { coverStyle = it },
            )

            Spacer(Modifier.size(12.dp))
            HorizontalDivider()
            Spacer(Modifier.size(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "발췌 선택",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${selected.size} / ${selectable.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(
                    onClick = {
                        selectedIds = if (allSelected) {
                            emptySet()
                        } else {
                            selectable.map { it.id }.toSet()
                        }
                    },
                    enabled = selectable.isNotEmpty(),
                ) {
                    Text(if (allSelected) "전체 해제" else "전체 선택")
                }
            }

            if (selectable.isEmpty()) {
                Text(
                    "발췌가 없습니다. PDF에서 텍스트를 발췌한 뒤 다시 열어 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selectable.forEach { excerpt ->
                        StudyExcerptRow(
                            excerpt = excerpt,
                            checked = excerpt.id in selectedIds,
                            onToggle = {
                                selectedIds = if (excerpt.id in selectedIds) {
                                    selectedIds - excerpt.id
                                } else {
                                    selectedIds + excerpt.id
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Button(
                onClick = {
                    onCreate(
                        StudyNoteBuilder.fromExcerpts(
                            title = title,
                            source = source,
                            excerpts = selected,
                            coverColor = coverColor,
                            coverStyle = coverStyle,
                        ),
                    )
                },
                enabled = selected.isNotEmpty() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("스터디 노트 만들기")
            }
            Text(
                "선택한 발췌는 각각 한 페이지가 되고, 페이지 메모에 원본 PDF 페이지가 남습니다. " +
                    "원본 문서는 기기에만 보관됩니다.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun StudySourceCard(source: StudySource) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "원본 PDF",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    source.docName.ifBlank { "이름 없는 PDF" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!source.docUri.isNullOrBlank()) {
                    Text(
                        "기기에 연결된 문서",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyCoverPicker(
    title: String,
    coverColor: Int,
    coverStyle: CoverStyle,
    onCoverColor: (Int) -> Unit,
    onCoverStyle: (CoverStyle) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .width(76.dp)
                .height(104.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                NotebookCover(
                    coverColor = coverColor,
                    coverStyle = coverStyle,
                    template = PaperTemplate.LINED,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    title.ifBlank { "스터디 노트" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF1B1C19),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "표지 스타일",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CoverStyle.entries.forEach { style ->
                    FilterChip(
                        selected = coverStyle == style,
                        onClick = { onCoverStyle(style) },
                        label = { Text(coverStyleLabel(style)) },
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                "표지 색상",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                STUDY_COVER_COLORS.forEach { color ->
                    StudyColorDot(
                        color = color,
                        selected = coverColor == color,
                        onClick = { onCoverColor(color) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyExcerptRow(
    excerpt: Excerpt,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val snippet = remember(excerpt.text) {
        excerpt.text.replace(Regex("\\s+"), " ").trim()
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(10.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "p.${excerpt.pageIndex + 1}",
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
                        )
                    }
                }
                Text(
                    snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StudyColorDot(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

private fun coverStyleLabel(style: CoverStyle): String = when (style) {
    CoverStyle.SOLID -> "단색"
    CoverStyle.GRID -> "모눈"
    CoverStyle.DOTS -> "점"
    CoverStyle.STRIPES -> "줄무늬"
}
