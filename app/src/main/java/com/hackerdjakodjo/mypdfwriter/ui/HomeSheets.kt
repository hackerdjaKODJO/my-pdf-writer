@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc

/**
 * Single sheet that owns every Home note filter. The sheet edits a draft and
 * only commits it through [onApply], so dismissing with the scrim or back
 * gesture preserves the current library view.
 */
@Composable
internal fun HomeFilterSheet(
    initialScope: NoteScope,
    initialFolder: String?,
    initialTag: String?,
    initialQuery: String,
    folders: List<String>,
    tagCounts: List<Pair<String, Int>>,
    onDismiss: () -> Unit,
    onApply: (scope: NoteScope, folder: String?, tag: String?, query: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var scope by remember(initialScope) { mutableStateOf(initialScope) }
    var folder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var tag by remember(initialTag) { mutableStateOf(initialTag) }
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("필터", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "보기·폴더·태그를 한곳에서 고릅니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FilterSection(title = "보기") {
                NoteScope.entries.forEach { option ->
                    FilterChip(
                        selected = scope == option,
                        onClick = { scope = option },
                        label = { Text(option.label) },
                    )
                }
            }

            FilterSection(title = "폴더") {
                FilterChip(
                    selected = folder == null,
                    onClick = { folder = null },
                    label = { Text("모든 폴더") },
                )
                FilterChip(
                    selected = folder == "",
                    onClick = { folder = "" },
                    label = { Text("미분류") },
                )
                folders.forEach { name ->
                    FilterChip(
                        selected = folder == name,
                        onClick = { folder = name },
                        label = { Text(name) },
                    )
                }
            }

            FilterSection(title = "태그") {
                FilterChip(
                    selected = tag == null,
                    onClick = { tag = null },
                    label = { Text("모든 태그") },
                )
                if (tagCounts.isEmpty()) {
                    Text(
                        "태그 없음",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    tagCounts.forEach { (name, count) ->
                        FilterChip(
                            selected = tag == name,
                            onClick = { tag = name },
                            label = { Text("$name · $count") },
                        )
                    }
                }
            }

            FilterSection(title = "검색") {
                if (query.isBlank()) {
                    Text(
                        "검색어 없음",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    FilterChip(
                        selected = true,
                        onClick = { query = "" },
                        label = { Text("검색 · “$query” 지우기") },
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope = NoteScope.ALL
                        folder = null
                        tag = null
                        query = ""
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("초기화")
                }
                Button(
                    onClick = { onApply(scope, folder, tag, query) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("적용")
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            content()
        }
    }
}

/**
 * Global Home utilities. The sheet is rendered from the shared top bar, so it
 * is reachable from Notes, PDF and Tags without repeating the menu per tab.
 */
@Composable
internal fun HomeSettingsSheet(
    onDismiss: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenTrace: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("설정", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "오프라인 · 파일은 기기 안에서만",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))

            SettingsRow(
                icon = Icons.Outlined.Archive,
                title = "백업/복원",
                body = "기기 안 백업 파일 만들기·복원",
                onClick = onOpenBackup,
            )
            SettingsRow(
                icon = Icons.Outlined.Delete,
                title = "휴지통",
                body = "삭제한 노트 복원·영구 삭제",
                onClick = onOpenTrash,
            )
            SettingsRow(
                icon = Icons.Outlined.LocalOffer,
                title = "태그",
                body = "태그별 노트 모아보기",
                onClick = onOpenTags,
            )
            SettingsRow(
                icon = Icons.Outlined.Dashboard,
                title = "PDF 도구",
                body = "병합·분할·OCR·보안",
                onClick = onOpenTools,
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            SettingsRow(
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                title = "사용법/도움말",
                body = "오프라인 사용 안내",
                onClick = onOpenHelp,
            )
            SettingsRow(
                icon = Icons.Outlined.Info,
                title = "정보",
                body = "버전과 개인정보 안내",
                onClick = onOpenInfo,
            )

            if (BuildConfig.DEBUG && onOpenTrace != null) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                SettingsRow(
                    icon = Icons.Outlined.BugReport,
                    title = "노트 추적",
                    body = "디버그 빌드 전용",
                    onClick = onOpenTrace,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Offline product/version information reachable from the settings sheet. */
@Composable
internal fun HomeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "PDF 가져오기·주석·도구·백업이 모두 기기 안에서 동작하는 오프라인 PDF 작업대입니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "모든 파일과 노트는 기기에만 저장되며 네트워크를 사용하지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}

/** Context sheet for one recent PDF row/card. */
@Composable
internal fun PdfRecentCardSheet(
    doc: RecentDoc,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${doc.pageCount}페이지 · 최근 PDF",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            Surface(
                onClick = {
                    onRemove(doc.uri)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "최근 목록에서 제거",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            "원본 PDF 파일은 삭제되지 않습니다.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
