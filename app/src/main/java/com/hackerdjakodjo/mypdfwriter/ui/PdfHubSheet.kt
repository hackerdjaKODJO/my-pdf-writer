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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

/**
 * Stable grouping for the single PDF menu. The order of [pdfMenuTopLevelGroups]
 * is the visible order; every [PdfMenuAction] belongs to exactly one group, so
 * adding an action cannot silently drop it from the sheet.
 */
internal enum class PdfMenuGroup(val title: String, val expandable: Boolean = false) {
    READING("읽기"),
    PAGES("페이지"),
    VIEW("보기"),
    EXPORT("내보내기"),
    ADVANCED("고급", expandable = true),
    READ_MODE("읽기 모드"),
    AUDIO("오디오"),
}

internal val pdfMenuTopLevelGroups = listOf(
    PdfMenuGroup.READING,
    PdfMenuGroup.PAGES,
    PdfMenuGroup.VIEW,
    PdfMenuGroup.EXPORT,
    PdfMenuGroup.ADVANCED,
)

/** The advanced group is collapsed on open so the menu stays one calm screen. */
internal const val PDF_HUB_ADVANCED_DEFAULT_EXPANDED = false

/**
 * Every PDF action reachable from the menu. Actions in [PdfMenuGroup.READ_MODE]
 * and [PdfMenuGroup.AUDIO] are rendered by their small sub-sheet.
 */
internal enum class PdfMenuAction(val group: PdfMenuGroup, val label: String) {
    SEARCH(PdfMenuGroup.READING, "검색"),
    OUTLINE(PdfMenuGroup.READING, "목차"),
    BOOKMARKS(PdfMenuGroup.READING, "북마크 목록"),
    PAGE_NOTES(PdfMenuGroup.READING, "페이지 메모"),
    MEMO_CARDS(PdfMenuGroup.READING, "메모 카드"),
    EXCERPTS(PdfMenuGroup.READING, "발췌·번역"),
    TEXT_SELECT(PdfMenuGroup.READING, "텍스트 선택"),
    STUDY_NOTE(PdfMenuGroup.READING, "스터디 노트"),

    PAGE_MANAGE(PdfMenuGroup.PAGES, "페이지 관리"),
    PAGE_EDIT(PdfMenuGroup.PAGES, "페이지 편집"),
    ANNOTATION_LIST(PdfMenuGroup.PAGES, "주석 목록"),
    TOGGLE_ANNOTATIONS(PdfMenuGroup.PAGES, "주석 숨기기/표시"),

    DISPLAY_SETTINGS(PdfMenuGroup.VIEW, "화면 설정"),
    READ_MODE(PdfMenuGroup.VIEW, "읽기 모드"),
    AUDIO(PdfMenuGroup.VIEW, "오디오"),

    EXPORT(PdfMenuGroup.EXPORT, "PDF 내보내기"),
    PRINT(PdfMenuGroup.EXPORT, "인쇄"),
    SHARE(PdfMenuGroup.EXPORT, "공유"),

    PAGE_LOCK(PdfMenuGroup.ADVANCED, "페이지 잠금"),
    COPY_TEXT(PdfMenuGroup.ADVANCED, "텍스트 복사"),
    FIT_SCREEN(PdfMenuGroup.ADVANCED, "화면에 맞추기"),
    DOCUMENT_INFO(PdfMenuGroup.ADVANCED, "문서 정보"),
    DECORATE(PdfMenuGroup.ADVANCED, "문서 꾸미기"),
    PDF_TOOLS(PdfMenuGroup.ADVANCED, "PDF 도구"),
    HELP(PdfMenuGroup.ADVANCED, "도움말"),

    READ_TEXT_MODE(PdfMenuGroup.READ_MODE, "텍스트 모드"),
    READ_CONTINUOUS(PdfMenuGroup.READ_MODE, "연속 보기"),
    READ_SPREAD(PdfMenuGroup.READ_MODE, "스프레드 보기"),
    READ_POPUP(PdfMenuGroup.READ_MODE, "팝업 보기"),
    READ_ALOUD(PdfMenuGroup.READ_MODE, "소리 내어 읽기"),

    AUDIO_NOTES(PdfMenuGroup.AUDIO, "오디오 노트"),
    AUDIO_REPLAY(PdfMenuGroup.AUDIO, "오디오 재생"),
}

internal val pdfReadModeActions = listOf(
    PdfMenuAction.READ_TEXT_MODE,
    PdfMenuAction.READ_CONTINUOUS,
    PdfMenuAction.READ_SPREAD,
    PdfMenuAction.READ_POPUP,
    PdfMenuAction.READ_ALOUD,
)

internal val pdfAudioActions = listOf(
    PdfMenuAction.AUDIO_NOTES,
    PdfMenuAction.AUDIO_REPLAY,
)

internal fun pdfMenuActions(group: PdfMenuGroup): List<PdfMenuAction> =
    PdfMenuAction.entries.filter { it.group == group }

/**
 * Two rows have state-dependent wording. Keeping this pure makes the label
 * policy testable without composing the sheet.
 */
internal fun pdfMenuActionLabel(
    action: PdfMenuAction,
    pageLocked: Boolean = false,
    annotationsHidden: Boolean = false,
): String = when (action) {
    PdfMenuAction.PAGE_LOCK -> if (pageLocked) "페이지 잠금 해제" else "페이지 잠금"
    PdfMenuAction.TOGGLE_ANNOTATIONS -> if (annotationsHidden) "주석 표시" else "주석 숨기기"
    else -> action.label
}

/** One row in the PDF menu. [trailing] is a short state/count label. */
data class PdfHubAction(
    val label: String,
    val icon: ImageVector,
    val trailing: String? = null,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** A labelled group of PDF menu actions. Expandable groups start collapsed. */
data class PdfHubSection(
    val title: String,
    val actions: List<PdfHubAction>,
    val expandable: Boolean = false,
    val initiallyExpanded: Boolean = PDF_HUB_ADVANCED_DEFAULT_EXPANDED,
)

/** Every action, including groups the user has not expanded yet. */
internal fun hubAllActions(sections: List<PdfHubSection>): List<PdfHubAction> =
    sections.flatMap { it.actions }

/** Actions currently reachable in the sheet; collapsed groups are excluded. */
internal fun hubVisibleActions(
    sections: List<PdfHubSection>,
    expandedTitles: Set<String>,
): List<PdfHubAction> = sections
    .filter { !it.expandable || it.title in expandedTitles }
    .flatMap { it.actions }

/**
 * The single PDF menu: always grouped, with the advanced group collapsed by
 * default instead of a separate simple/expanded mode.
 *
 * Every row dismisses the menu before running its callback, so a target sheet,
 * reader mode or sub-menu appears without stacking a second modal on the menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHubSheet(
    docName: String,
    pageIndex: Int,
    pageCount: Int,
    sections: List<PdfHubSection>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var expandedTitles by remember {
        mutableStateOf(
            sections
                .filter { it.expandable && it.initiallyExpanded }
                .map { it.title }
                .toSet(),
        )
    }
    val visibleSections = sections.filter {
        !it.expandable || it.title in expandedTitles
    }
    val activeSections = visibleSections.filter { it.actions.isNotEmpty() }

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
                )
            }

            sections.filter { it.actions.isNotEmpty() || it.expandable }.forEach { section ->
                val expanded = section.title in expandedTitles
                item(key = "header_${section.title}") {
                    HubSectionHeader(
                        section = section,
                        expanded = expanded,
                        onToggle = {
                            expandedTitles = if (expanded) {
                                expandedTitles - section.title
                            } else {
                                expandedTitles + section.title
                            }
                        },
                    )
                }
                if (!section.expandable || expanded) {
                    items(section.actions, key = { "${section.title}_${it.label}" }) { action ->
                        HubActionRow(action = action, onDismiss = onDismiss)
                    }
                }
            }

            if (activeSections.isEmpty()) {
                item(key = "hub_empty") {
                    Text(
                        "표시할 도구가 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** Small sub-sheet for 읽기 모드 and 오디오, without duplicating the main menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHubSubSheet(
    title: String,
    subtitle: String,
    actions: List<PdfHubAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
            }
            actions.forEach { action ->
                HubActionRow(action = action, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun HubHeader(
    docName: String,
    pageIndex: Int,
    pageCount: Int,
) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("PDF 메뉴", style = MaterialTheme.typography.titleLarge)
        Text(
            "$docName · ${pageIndex + 1} / $pageCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "읽기·페이지·보기·내보내기로 묶었습니다. 자주 쓰지 않는 도구는 고급에서 펼치세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
    }
}

@Composable
private fun HubSectionHeader(
    section: PdfHubSection,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (section.expandable) {
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${section.actions.size}개",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (expanded) "접기" else "펼치기",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "${section.title} 접기" else "${section.title} 펼치기",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                section.title,
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
}

@Composable
private fun HubActionRow(action: PdfHubAction, onDismiss: () -> Unit) {
    Surface(
        onClick = {
            onDismiss()
            action.onClick()
        },
        enabled = action.enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
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
                        tint = if (action.enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
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
