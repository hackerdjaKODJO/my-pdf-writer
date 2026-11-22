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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Stable grouping for the single 학습 메뉴. Students see only four first-level
 * groups, in this order: 읽기 → 필기 → 담기 → 설정. Everything that used to be a
 * top-level group (내보내기, 기타, 보기·페이지) now lives inside 읽기/설정 or in a
 * small sub-sheet, so the menu stays one calm screen. The order of
 * [pdfMenuTopLevelGroups] is the visible order; every [PdfMenuAction] belongs to
 * exactly one surface, so adding an action cannot silently drop it from the sheet.
 */
internal enum class PdfMenuGroup(val title: String, val expandable: Boolean = false) {
    READ("읽기"),
    WRITE("필기"),
    CAPTURE("담기"),
    SETTINGS("설정"),
    // Sub-sheets, never first-level groups.
    READ_MODE("읽기 모드"),
    AUDIO("오디오"),
    FILE_TOOLS("파일 도구"),
}

internal val pdfMenuTopLevelGroups = listOf(
    PdfMenuGroup.READ,
    PdfMenuGroup.WRITE,
    PdfMenuGroup.CAPTURE,
    PdfMenuGroup.SETTINGS,
)

/** Hard cap for the first-level hub; enforced by unit tests. */
internal const val PDF_HUB_MAX_TOP_LEVEL_GROUPS = 4

/** One capture label everywhere in the PDF UI (hub, selection bar, 더보기). */
internal const val PDF_HUB_CAPTURE_LABEL = "노트에 담기"

/** The unified 읽기 → 위치 entry point; the host opens the one Locations panel. */
internal const val PDF_HUB_READ_GROUP_TITLE = "읽기"
internal const val PDF_HUB_LOCATIONS_LABEL = "위치"

/**
 * Every action reachable from the 학습 메뉴 surface. Actions in the three
 * sub-sheet groups ([PdfMenuGroup.READ_MODE], [PdfMenuGroup.AUDIO],
 * [PdfMenuGroup.FILE_TOOLS]) use a small sub-sheet; [pdfTopBarOwnedActions] stay
 * in the viewer top chrome instead of the menu. 목차/북마크/주석 목록 are folded
 * into the single [LOCATIONS] action because they are three views of one task:
 * "where am I / where do I want to go".
 */
internal enum class PdfMenuAction(val group: PdfMenuGroup, val label: String) {
    // 읽기
    SEARCH(PdfMenuGroup.READ, "검색"),
    LOCATIONS(PdfMenuGroup.READ, PDF_HUB_LOCATIONS_LABEL),
    BOOKMARK_TOGGLE(PdfMenuGroup.READ, "현재 쪽 북마크"),
    READ_MODE(PdfMenuGroup.READ, "읽기 모드"),
    AUDIO(PdfMenuGroup.READ, "오디오"),
    DISPLAY_SETTINGS(PdfMenuGroup.READ, "화면 설정"),
    FIT_SCREEN(PdfMenuGroup.READ, "화면에 맞추기"),

    // 필기
    TOOL_MORE(PdfMenuGroup.WRITE, "주석 도구"),
    MEMO_CARDS(PdfMenuGroup.WRITE, "포스트잇"),
    TOGGLE_ANNOTATIONS(PdfMenuGroup.WRITE, "주석 숨기기/표시"),
    PAGE_NOTES(PdfMenuGroup.WRITE, "페이지 메모"),
    DECORATE(PdfMenuGroup.WRITE, "문서 꾸미기"),

    // 담기
    CAPTURE(PdfMenuGroup.CAPTURE, PDF_HUB_CAPTURE_LABEL),
    EXCERPTS(PdfMenuGroup.CAPTURE, "발췌 목록"),
    COPY_TEXT(PdfMenuGroup.CAPTURE, "텍스트 복사"),

    // 설정
    FILE_TOOLS(PdfMenuGroup.SETTINGS, "파일 도구"),
    AUTO_HIDE(PdfMenuGroup.SETTINGS, "메뉴 자동 숨김"),
    HELP(PdfMenuGroup.SETTINGS, "도움말"),

    // 설정 → 파일 도구 (advanced file operations)
    PAGE_EDIT(PdfMenuGroup.FILE_TOOLS, "쪽 편집"),
    PAGE_MANAGE(PdfMenuGroup.FILE_TOOLS, "쪽 관리"),
    PDF_TOOLS(PdfMenuGroup.FILE_TOOLS, "PDF 도구"),
    EXPORT(PdfMenuGroup.SETTINGS, "PDF 내보내기"),
    PRINT(PdfMenuGroup.FILE_TOOLS, "인쇄"),
    SHARE(PdfMenuGroup.FILE_TOOLS, "공유"),
    DOCUMENT_INFO(PdfMenuGroup.FILE_TOOLS, "문서 정보"),
    PAGE_LOCK(PdfMenuGroup.FILE_TOOLS, "페이지 잠금"),

    // 읽기 → 읽기 모드
    READ_TEXT_MODE(PdfMenuGroup.READ_MODE, "텍스트 모드"),
    READ_CONTINUOUS(PdfMenuGroup.READ_MODE, "연속 보기"),
    READ_SPREAD(PdfMenuGroup.READ_MODE, "스프레드 보기"),
    READ_POPUP(PdfMenuGroup.READ_MODE, "팝업 보기"),
    READ_ALOUD(PdfMenuGroup.READ_MODE, "소리 내어 읽기"),

    // 읽기 → 오디오
    AUDIO_NOTES(PdfMenuGroup.AUDIO, "오디오 노트"),
    AUDIO_REPLAY(PdfMenuGroup.AUDIO, "오디오 재생"),
}

/** Search is the only menu action owned by the top chrome. */
internal val pdfTopBarOwnedActions = setOf(PdfMenuAction.SEARCH)

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

internal val pdfFileToolsActions = listOf(
    PdfMenuAction.PAGE_EDIT,
    PdfMenuAction.PAGE_MANAGE,
    PdfMenuAction.PDF_TOOLS,
    PdfMenuAction.PRINT,
    PdfMenuAction.SHARE,
    PdfMenuAction.DOCUMENT_INFO,
    PdfMenuAction.PAGE_LOCK,
)

internal fun pdfMenuActions(group: PdfMenuGroup): List<PdfMenuAction> =
    PdfMenuAction.entries.filter { it.group == group && it !in pdfTopBarOwnedActions }

/**
 * Two rows have state-dependent wording and one chrome toggle reports its state.
 * Keeping this pure makes the label policy testable without composing the sheet.
 */
internal fun pdfMenuActionLabel(
    action: PdfMenuAction,
    pageLocked: Boolean = false,
    annotationsHidden: Boolean = false,
    autoHideEnabled: Boolean = false,
    bookmarked: Boolean = false,
): String = when (action) {
    PdfMenuAction.PAGE_LOCK -> if (pageLocked) "페이지 잠금 해제" else "페이지 잠금"
    PdfMenuAction.TOGGLE_ANNOTATIONS -> if (annotationsHidden) "주석 표시" else "주석 숨기기"
    PdfMenuAction.AUTO_HIDE -> if (autoHideEnabled) "메뉴 자동 숨김 끄기" else "메뉴 자동 숨김 켜기"
    PdfMenuAction.BOOKMARK_TOGGLE -> if (bookmarked) "현재 쪽 북마크 해제" else "현재 쪽 북마크"
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

/** A labelled group of PDF menu actions. */
data class PdfHubSection(
    val title: String,
    val actions: List<PdfHubAction>,
    val expandable: Boolean = false,
    val initiallyExpanded: Boolean = false,
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

/** The unified Locations action; the host decides what sheet it opens. */
internal fun pdfLocationsAction(onOpen: () -> Unit): PdfHubAction = PdfHubAction(
    label = PDF_HUB_LOCATIONS_LABEL,
    icon = Icons.Outlined.StarBorder,
    onClick = onOpen,
)

internal const val PDF_HUB_NARROW_WIDTH_DP = 320
internal const val PDF_HUB_LARGE_FONT_SCALE = 1.5f

/** Narrow screens and large fonts get two-line rows; every target stays ≥48dp. */
internal data class PdfHubRowLayout(val minHeightDp: Int, val labelMaxLines: Int)

internal fun pdfHubRowLayout(widthDp: Int, fontScale: Float): PdfHubRowLayout {
    val scale = chromeScale(widthDp = widthDp, fontScale = fontScale)
    val twoLines = widthDp <= PDF_HUB_NARROW_WIDTH_DP || fontScale >= PDF_HUB_LARGE_FONT_SCALE
    return PdfHubRowLayout(
        minHeightDp = accessibleTarget(scale.minRowHeightDp),
        labelMaxLines = if (twoLines) 2 else 1,
    )
}

/**
 * The single 학습 메뉴: four fixed first-level groups, no quick-action row and
 * no duplicate navigation entries. Undo/redo/style live in 필기 → 주석 도구, and
 * advanced file operations live in 설정 → 파일 도구, so the first screen stays
 * short enough to read at a glance. Every row dismisses the menu before running
 * its callback so the target sheet appears without stacking a second modal.
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
    val configuration = LocalConfiguration.current
    val rowLayout = pdfHubRowLayout(
        widthDp = configuration.smallestScreenWidthDp,
        fontScale = configuration.fontScale,
    )
    var expandedTitles by remember {
        mutableStateOf(
            sections
                .filter { it.expandable && it.initiallyExpanded }
                .map { it.title }
                .toSet(),
        )
    }
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
                        HubActionRow(
                            action = action,
                            minHeightDp = rowLayout.minHeightDp,
                            labelMaxLines = rowLayout.labelMaxLines,
                            onDismiss = onDismiss,
                        )
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

/** Small sub-sheet for 읽기 모드, 오디오 and 파일 도구, without duplicating the main menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfHubSubSheet(
    title: String,
    subtitle: String,
    actions: List<PdfHubAction>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val rowLayout = pdfHubRowLayout(
        widthDp = configuration.smallestScreenWidthDp,
        fontScale = configuration.fontScale,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "sub_header") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.sectionHeading())
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(10.dp))
                }
            }
            items(actions, key = { it.label }) { action ->
                HubActionRow(
                    action = action,
                    minHeightDp = rowLayout.minHeightDp,
                    labelMaxLines = rowLayout.labelMaxLines,
                    onDismiss = onDismiss,
                )
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
        Text("학습 메뉴", style = MaterialTheme.typography.titleLarge, modifier = Modifier.sectionHeading())
        Text(
            "$docName · ${pageIndex + 1} / $pageCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "읽기·필기·담기·설정 네 묶음입니다. 목차·북마크·주석은 위치에서 한 번에 찾으세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .sizeIn(minHeight = MIN_TARGET_DP.dp)
                .semantics { stateDescription = expandStateDescription(expanded) },
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
                    // State is announced once via the header's stateDescription.
                    contentDescription = null,
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
                modifier = Modifier.sectionHeading(),
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
private fun HubActionRow(
    action: PdfHubAction,
    minHeightDp: Int,
    labelMaxLines: Int,
    onDismiss: () -> Unit,
) {
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
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .sizeIn(minHeight = minHeightDp.dp),
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
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            action.trailing?.let { trailing ->
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = labelMaxLines,
                    overflow = TextOverflow.Ellipsis,
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
