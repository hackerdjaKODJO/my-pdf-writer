package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc

/**
 * Session-only file tabs for the PDF viewer. The entries are intentionally not
 * persisted: they describe the documents opened in this viewer visit, not the
 * library (which is owned by [PdfViewModel.library]). Tabs are keyed by the
 * view model's stable [docId] and carry a URI so switching can reuse the
 * existing `vm.openDocument(uri)` path (and therefore the existing docId-change
 * effect the study pane uses to save itself).
 */
internal data class OpenDocumentTab(
    val docId: String,
    val uri: String,
    val name: String,
)

/**
 * Mutable-in-session tab-bar state. [activeDocId] is the tab whose document is
 * currently loaded; [barVisible] is the user's hide/show choice and survives
 * tab changes but not process death.
 */
internal data class DocumentTabsState(
    val tabs: List<OpenDocumentTab> = emptyList(),
    val activeDocId: String = "",
    val barVisible: Boolean = true,
) {
    val activeTab: OpenDocumentTab? get() = tabs.firstOrNull { it.docId == activeDocId }
}

/** Below two documents a strip would only add chrome; the title still opens recents. */
internal const val MIN_TABS_FOR_STRIP = 2

/** Phone-first cap: the rest of the tabs live behind the `+N` overflow. */
internal const val MAX_VISIBLE_TABS = 3

/** How many recents the small switcher sheet exposes without becoming a library. */
internal const val RECENT_DOCS_LIMIT = 8

internal fun seedDocumentTabs(tab: OpenDocumentTab): DocumentTabsState = DocumentTabsState(
    tabs = listOf(tab),
    activeDocId = tab.docId,
)

/**
 * Adds a document or refreshes an existing entry and makes it active. The list
 * is most-recently-used: the touched tab moves to the front so the next tab is
 * always the one a reader is most likely to want.
 */
internal fun upsertDocumentTab(
    state: DocumentTabsState,
    tab: OpenDocumentTab,
): DocumentTabsState {
    val existing = state.tabs.firstOrNull { it.docId == tab.docId }
    val merged = existing?.copy(name = tab.name, uri = tab.uri) ?: tab
    return state.copy(
        tabs = listOf(merged) + state.tabs.filterNot { it.docId == tab.docId },
        activeDocId = tab.docId,
    )
}

/** Activates an already-open tab; returns null when the id is unknown. */
internal fun activateDocumentTab(
    state: DocumentTabsState,
    docId: String,
): DocumentTabsState? {
    val target = state.tabs.firstOrNull { it.docId == docId } ?: return null
    return upsertDocumentTab(state, target)
}

/**
 * Removes one tab. Closing the active tab promotes its right neighbour (or the
 * previous tab when it was last); closing an inactive tab keeps the active one.
 * An empty result is handled by the caller, which exits the viewer with the
 * unchanged `vm.closeDocument()` path.
 */
internal fun closeDocumentTab(
    state: DocumentTabsState,
    docId: String,
): DocumentTabsState {
    val index = state.tabs.indexOfFirst { it.docId == docId }
    if (index < 0) return state
    val remaining = state.tabs.filterNot { it.docId == docId }
    val nextActive = if (docId == state.activeDocId) {
        (remaining.getOrNull(index) ?: remaining.lastOrNull())?.docId.orEmpty()
    } else {
        state.activeDocId
    }
    return state.copy(tabs = remaining, activeDocId = nextActive)
}

internal fun closeOtherDocumentTabs(state: DocumentTabsState): DocumentTabsState =
    state.copy(tabs = state.tabs.filter { it.docId == state.activeDocId })

internal fun closeAllDocumentTabs(state: DocumentTabsState): DocumentTabsState =
    state.copy(tabs = emptyList(), activeDocId = "")

internal fun setDocumentTabBarVisible(
    state: DocumentTabsState,
    visible: Boolean,
): DocumentTabsState = state.copy(barVisible = visible)

internal fun visibleDocumentTabs(state: DocumentTabsState): List<OpenDocumentTab> =
    state.tabs.take(MAX_VISIBLE_TABS)

internal fun hiddenDocumentTabs(state: DocumentTabsState): List<OpenDocumentTab> =
    state.tabs.drop(MAX_VISIBLE_TABS)

internal fun documentTabOverflowCount(state: DocumentTabsState): Int =
    hiddenDocumentTabs(state).size

internal fun shouldShowDocumentTabStrip(state: DocumentTabsState): Boolean =
    state.barVisible && state.tabs.size >= MIN_TABS_FOR_STRIP

/** Recents first, blank/corrupt entries skipped, bounded to a small switcher. */
internal fun recentDocumentsForSwitcher(
    library: List<RecentDoc>,
    limit: Int = RECENT_DOCS_LIMIT,
): List<RecentDoc> = library
    .filter { it.uri.isNotBlank() }
    .sortedByDescending { it.lastOpenedAt }
    .take(limit.coerceAtLeast(0))

/** Compact, translucent tab strip. An overlay: it never reserves layout height. */
@Composable
internal fun DocumentTabStrip(
    state: DocumentTabsState,
    onSelectTab: (OpenDocumentTab) -> Unit,
    onCloseTab: (OpenDocumentTab) -> Unit,
    onOpenMenu: () -> Unit,
    onHideStrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visibleDocumentTabs(state).forEach { tab ->
                val active = tab.docId == state.activeDocId
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .widthIn(max = 132.dp)
                        .clickable { onSelectTab(tab) }
                        .semantics { contentDescription = stateLabel(tab.name, active) },
                ) {
                    Row(
                        Modifier.padding(start = 9.dp, end = 3.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tab.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.size(4.dp))
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "탭 닫기: ${tab.name}",
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onCloseTab(tab) },
                        )
                    }
                }
            }
            val overflow = documentTabOverflowCount(state)
            if (overflow > 0) {
                Text(
                    "+$overflow",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenMenu() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics { contentDescription = "숨겨진 탭 ${overflow}개" },
                )
            }
            Icon(
                Icons.Outlined.MoreHoriz,
                contentDescription = "탭 메뉴",
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onOpenMenu() },
            )
            Icon(
                Icons.Outlined.VisibilityOff,
                contentDescription = "탭 숨기기",
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onHideStrip() },
            )
        }
    }
}

/** One row of the File Tabs panel / recents sheet. */
@Composable
private fun TabPanelRow(
    label: String,
    trailing: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * GoodReader-style File Tabs panel: every open document plus the tab-management
 * actions. Reached from the strip's `+N` / more button, so hidden tabs remain
 * switchable even when the strip itself is hidden behind the overflow count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentTabOverflowSheet(
    tabs: List<OpenDocumentTab>,
    activeDocId: String,
    tabBarVisible: Boolean,
    autoHideMode: ChromeAutoHideMode,
    onSelectTab: (OpenDocumentTab) -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
    onToggleBar: () -> Unit,
    onToggleAutoHide: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenLocations: () -> Unit,
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
            Text(
                "문서 탭",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "열어 둔 PDF를 탭하면 바로 바뀝니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            tabs.forEach { tab ->
                TabPanelRow(
                    label = tab.name,
                    trailing = if (tab.docId == activeDocId) "현재" else null,
                    icon = Icons.Outlined.Description,
                    contentDescription = "문서 탭 열기: ${tab.name}",
                ) { onSelectTab(tab) }
            }
            TabPanelRow(label = "다른 탭 닫기", enabled = tabs.size > 1) { onCloseOthers() }
            TabPanelRow(label = "모든 탭 닫기", enabled = tabs.isNotEmpty()) { onCloseAll() }
            TabPanelRow(
                label = "탭 표시/숨기기",
                trailing = if (tabBarVisible) "표시" else "숨김",
            ) { onToggleBar() }
            TabPanelRow(
                label = "메뉴 자동 숨김",
                trailing = autoHideMode.label,
            ) { onToggleAutoHide() }
            TabPanelRow(label = "최근 문서") { onOpenRecents() }
            TabPanelRow(label = "위치 · 북마크/목차/주석") { onOpenLocations() }
        }
    }
}

/** Filename tap / `최근 문서` action: a small recents switcher, not the full library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentDocumentsSheet(
    documents: List<RecentDoc>,
    activeUri: String?,
    tabBarVisible: Boolean,
    autoHideMode: ChromeAutoHideMode,
    onOpenDocument: (RecentDoc) -> Unit,
    onToggleTabBar: () -> Unit,
    onToggleAutoHide: () -> Unit,
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
            Text(
                "최근 문서",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "탭하면 현재 PDF 대신 열립니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (documents.isEmpty()) {
                Text(
                    "최근에 연 PDF가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            documents.forEach { doc ->
                TabPanelRow(
                    label = doc.name,
                    trailing = if (doc.uri == activeUri) "현재" else "${doc.pageCount}쪽",
                    icon = Icons.Outlined.Description,
                    contentDescription = "최근 문서 열기: ${doc.name}",
                ) { onOpenDocument(doc) }
            }
            TabPanelRow(
                label = "탭 표시/숨기기",
                trailing = if (tabBarVisible) "표시" else "숨김",
            ) { onToggleTabBar() }
            TabPanelRow(
                label = "메뉴 자동 숨김",
                trailing = autoHideMode.label,
            ) { onToggleAutoHide() }
        }
    }
}

internal val ChromeAutoHideMode.label: String
    get() = when (this) {
        ChromeAutoHideMode.OFF -> "꺼짐"
        ChromeAutoHideMode.ON -> "켜짐"
    }
