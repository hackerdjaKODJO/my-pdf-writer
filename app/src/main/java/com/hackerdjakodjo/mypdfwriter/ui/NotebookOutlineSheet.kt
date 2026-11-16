package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageBookmark
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate

private data class BookmarkEditorState(
    val pageIndex: Int,
    val bookmarkId: String?,
    val initialTitle: String,
)

/**
 * Controlled notebook outline sheet with page and bookmark tabs.
 *
 * The sheet never owns the notebook: every bookmark edit is returned through
 * [onBookmarksChange] and page selection through [onJump]. Jumping closes the
 * sheet so the editor can show the selected page immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookOutlineSheet(
    notebook: Notebook,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onBookmarksChange: (Notebook) -> Unit,
    onDismiss: () -> Unit,
) {
    val pages = remember(notebook) { NotebookOutline.pages(notebook) }
    val bookmarks = remember(notebook) { NotebookOutline.bookmarks(notebook) }
    val safeCurrent = if (pages.isEmpty()) -1 else currentPage.coerceIn(0, pages.lastIndex)
    val currentBookmark = bookmarks.firstOrNull { it.pageIndex == safeCurrent }

    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<BookmarkEditorState?>(null) }

    fun jumpTo(index: Int) {
        onJump(index)
        onDismiss()
    }

    fun openEditor(pageIndex: Int) {
        val bookmark = bookmarks.firstOrNull { it.pageIndex == pageIndex }
        editor = BookmarkEditorState(
            pageIndex = pageIndex,
            bookmarkId = bookmark?.id,
            initialTitle = bookmark?.title ?: "페이지 ${pageIndex + 1}",
        )
    }

    fun toggleBookmark(pageIndex: Int) {
        val existing = bookmarks.firstOrNull { it.pageIndex == pageIndex }
        val updated = if (existing == null) {
            NotebookOutline.addBookmark(notebook, pageIndex, "페이지 ${pageIndex + 1}")
        } else {
            NotebookOutline.removeBookmark(notebook, pageIndex)
        }
        onBookmarksChange(updated)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("노트 개요", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "전체 ${pages.size}쪽 · 북마크 ${bookmarks.size}개",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { if (safeCurrent >= 0) openEditor(safeCurrent) },
                    enabled = safeCurrent >= 0,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (currentBookmark == null) "북마크 추가" else "북마크 편집")
                }
            }

            Spacer(Modifier.height(4.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("페이지") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("북마크") },
                )
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                0 -> OutlinePagesTab(
                    pages = pages,
                    currentPage = safeCurrent,
                    onJump = ::jumpTo,
                    onToggleBookmark = ::toggleBookmark,
                    onEditBookmark = ::openEditor,
                )

                else -> OutlineBookmarksTab(
                    bookmarks = bookmarks,
                    pages = pages,
                    onJump = ::jumpTo,
                    onEdit = { bookmark ->
                        editor = BookmarkEditorState(
                            pageIndex = bookmark.pageIndex,
                            bookmarkId = bookmark.id,
                            initialTitle = bookmark.title,
                        )
                    },
                    onDelete = { bookmark ->
                        onBookmarksChange(
                            NotebookOutline.removeBookmark(notebook, bookmark.pageIndex),
                        )
                    },
                    onMove = { bookmark, direction ->
                        onBookmarksChange(NotebookOutline.moveBookmark(notebook, bookmark.id, direction))
                    },
                )
            }
        }
    }

    val pendingEditor = editor
    if (pendingEditor != null) {
        BookmarkTitleDialog(
            state = pendingEditor,
            onDismiss = { editor = null },
            onConfirm = { title ->
                val updated = if (pendingEditor.bookmarkId == null) {
                    NotebookOutline.addBookmark(notebook, pendingEditor.pageIndex, title)
                } else {
                    NotebookOutline.renameBookmark(notebook, pendingEditor.bookmarkId, title)
                }
                editor = null
                onBookmarksChange(updated)
            },
        )
    }
}

@Composable
private fun OutlinePagesTab(
    pages: List<OutlinePage>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onEditBookmark: (Int) -> Unit,
) {
    if (pages.isEmpty()) {
        EmptyOutlineMessage(
            title = "페이지가 없습니다.",
            body = "노트에 페이지를 추가하면 이곳에 표시됩니다.",
        )
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        items(pages, key = { it.index }) { page ->
            OutlinePageRow(
                page = page,
                current = page.index == currentPage,
                onJump = { onJump(page.index) },
                onToggleBookmark = { onToggleBookmark(page.index) },
                onEditBookmark = { onEditBookmark(page.index) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlinePageRow(
    page: OutlinePage,
    current: Boolean,
    onJump: () -> Unit,
    onToggleBookmark: () -> Unit,
    onEditBookmark: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = shape,
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onJump,
                onLongClick = onEditBookmark,
            ),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (current) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("${page.index + 1}", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        page.template.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (current) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "현재",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    "주석 ${page.annotationCount}개",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onToggleBookmark,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (page.bookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (page.bookmarked) "북마크 해제" else "북마크 추가",
                    tint = if (page.bookmarked) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "페이지 메뉴",
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (page.bookmarked) "북마크 이름 변경" else "북마크 추가") },
                        leadingIcon = {
                            Icon(
                                if (page.bookmarked) Icons.Outlined.Edit else Icons.Outlined.Add,
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onEditBookmark()
                        },
                    )
                    if (page.bookmarked) {
                        DropdownMenuItem(
                            text = { Text("북마크 삭제") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onToggleBookmark()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineBookmarksTab(
    bookmarks: List<PageBookmark>,
    pages: List<OutlinePage>,
    onJump: (Int) -> Unit,
    onEdit: (PageBookmark) -> Unit,
    onDelete: (PageBookmark) -> Unit,
    onMove: (PageBookmark, Int) -> Unit,
) {
    if (bookmarks.isEmpty()) {
        EmptyOutlineMessage(
            title = "북마크가 없습니다.",
            body = "페이지 탭에서 별을 누르거나 ‘북마크 추가’로 현재 페이지를 저장하세요.",
        )
        return
    }

    val templateByPage = pages.associate { it.index to it.template }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 430.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 4.dp),
    ) {
        itemsIndexed(bookmarks, key = { _, bookmark -> bookmark.id }) { index, bookmark ->
            OutlineBookmarkRow(
                bookmark = bookmark,
                template = templateByPage[bookmark.pageIndex],
                canMoveUp = index > 0,
                canMoveDown = index < bookmarks.lastIndex,
                onJump = { onJump(bookmark.pageIndex) },
                onEdit = { onEdit(bookmark) },
                onDelete = { onDelete(bookmark) },
                onMoveUp = { onMove(bookmark, -1) },
                onMoveDown = { onMove(bookmark, 1) },
            )
        }
    }
}

@Composable
private fun OutlineBookmarkRow(
    bookmark: PageBookmark,
    template: PaperTemplate?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        onClick = onJump,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    bookmark.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("${bookmark.pageIndex + 1}쪽")
                        if (template != null) append(" · ${template.label}")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlineBookmarkAction(Icons.Outlined.ArrowUpward, "북마크 위로", canMoveUp, onMoveUp)
            OutlineBookmarkAction(Icons.Outlined.ArrowDownward, "북마크 아래로", canMoveDown, onMoveDown)
            OutlineBookmarkAction(Icons.Outlined.Edit, "북마크 이름 변경", true, onEdit)
            OutlineBookmarkAction(
                icon = Icons.Outlined.Delete,
                label = "북마크 삭제",
                enabled = true,
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun OutlineBookmarkAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyOutlineMessage(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookmarkTitleDialog(
    state: BookmarkEditorState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(state) { mutableStateOf(state.initialTitle) }
    val renaming = state.bookmarkId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (renaming) "북마크 이름 변경" else "북마크 추가") },
        text = {
            Column {
                Text(
                    "${state.pageIndex + 1}쪽에 저장합니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("제목") },
                    placeholder = { Text("예: 3장 요약") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text(if (renaming) "저장" else "추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
