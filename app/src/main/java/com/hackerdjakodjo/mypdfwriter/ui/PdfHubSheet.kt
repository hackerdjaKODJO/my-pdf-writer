package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One row in the PDF hub. [trailing] is a short state/count label, for example "3개" or "켜짐". */
data class PdfHubAction(
    val label: String,
    val icon: ImageVector,
    val trailing: String? = null,
    /** Primary actions stay visible in the simplified hub. */
    val primary: Boolean = false,
    val onClick: () -> Unit,
)

/** A labelled group of hub actions. */
data class PdfHubSection(
    val title: String,
    val actions: List<PdfHubAction>,
)

/** The simple hub never grows beyond one small screen of primary actions. */
internal const val PDF_HUB_PRIMARY_LIMIT = 7

/** Actions shown before the user expands the hub: primary actions, in section order. */
internal fun hubPrimaryActions(sections: List<PdfHubSection>): List<PdfHubAction> =
    sections.flatMap { it.actions }.filter { it.primary }.take(PDF_HUB_PRIMARY_LIMIT)

/** Every action, used by the expanded groups. */
internal fun hubAllActions(sections: List<PdfHubSection>): List<PdfHubAction> =
    sections.flatMap { it.actions }

/**
 * The PDF hub opens in a simple mode with the small set of actions users need
 * most often. "모든 도구 보기" expands the same sheet into the complete set of
 * grouped tools, so no capability is removed.
 *
 * Every row dismisses the hub before running its callback, so a target sheet or
 * reader mode appears without a second modal sheet stacked on top of the hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHubSheet(
    docName: String,
    pageIndex: Int,
    pageCount: Int,
    sections: List<PdfHubSection>,
    onDismiss: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val primaryActions = hubPrimaryActions(sections)
    val allActions = hubAllActions(sections)
    val activeSections = sections.filter { it.actions.isNotEmpty() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "hub_header") {
                HubHeader(
                    docName = docName,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    expanded = expanded,
                    primaryCount = primaryActions.size,
                    totalCount = allActions.size,
                )
            }

            if (!expanded) {
                item(key = "hub_primary_grid") {
                    HubPrimaryGrid(actions = primaryActions, onDismiss = onDismiss)
                }
                item(key = "hub_toggle_top") {
                    HubExpandToggle(
                        expanded = false,
                        totalCount = allActions.size,
                        onClick = { expanded = true },
                    )
                }
            } else {
                item(key = "hub_toggle_top") {
                    HubExpandToggle(
                        expanded = true,
                        totalCount = allActions.size,
                        onClick = { expanded = false },
                    )
                }
                activeSections.forEach { section ->
                    item(key = "header_${section.title}") {
                        HubSectionHeader(section.title)
                    }
                    items(section.actions, key = { "${section.title}_${it.label}" }) { action ->
                        HubActionRow(action = action, onDismiss = onDismiss)
                    }
                }
                item(key = "hub_toggle_bottom") {
                    HubExpandToggle(
                        expanded = true,
                        totalCount = allActions.size,
                        onClick = { expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun HubHeader(
    docName: String,
    pageIndex: Int,
    pageCount: Int,
    expanded: Boolean,
    primaryCount: Int,
    totalCount: Int,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("PDF 허브", style = MaterialTheme.typography.titleLarge)
        Text(
            "$docName · ${pageIndex + 1} / $pageCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            if (expanded) {
                "모든 도구를 종류별로 모았습니다. 필요한 항목을 선택하세요."
            } else {
                "자주 쓰는 ${primaryCount}개 도구입니다. 전체 ${totalCount}개 도구는 아래에서 펼칠 수 있습니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
    }
}

@Composable
private fun HubPrimaryGrid(actions: List<PdfHubAction>, onDismiss: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        if (actions.isEmpty()) {
            Text(
                "표시할 주요 도구가 없습니다. 모든 도구 보기에서 선택하세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
            return@Column
        }
        actions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { action ->
                    HubPrimaryTile(
                        action = action,
                        modifier = Modifier.weight(1f),
                        onDismiss = onDismiss,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun HubPrimaryTile(
    action: PdfHubAction,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
) {
    Surface(
        onClick = {
            onDismiss()
            action.onClick()
        },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.size(30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                action.trailing?.let { trailing ->
                    Text(
                        trailing,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.size(7.dp))
            Text(
                action.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HubExpandToggle(expanded: Boolean, totalCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (expanded) "간단히 보기" else "모든 도구 보기",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    if (expanded) {
                        "주요 도구만 다시 표시합니다."
                    } else {
                        "읽기·문서·내보내기 전체 ${totalCount}개"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun HubSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun HubActionRow(action: PdfHubAction, onDismiss: () -> Unit) {
    Surface(
        onClick = {
            onDismiss()
            action.onClick()
        },
        shape = RoundedCornerShape(14.dp),
        color = if (action.primary) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                action.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            action.trailing?.let { trailing ->
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
