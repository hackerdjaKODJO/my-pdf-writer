@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotebookDraft
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibrarySection(val label: String) {
    NOTES("노트"),
    PDF("PDF"),
    TAGS("태그"),
}

private enum class NoteScope(val label: String) {
    ALL("전체"),
    FAVORITES("즐겨찾기"),
    TRASH("휴지통"),
}

private val CoverPalette: List<Int> = listOf(
    0xFFD8E6F3.toInt(),
    0xFFDCE8DC.toInt(),
    0xFFF6E3D4.toInt(),
    0xFFF3DCE2.toInt(),
    0xFFE3DFF1.toInt(),
    0xFFEDE6D2.toInt(),
    0xFFD7E7E4.toInt(),
    0xFFF3EBCF.toInt(),
)

/**
 * StarNote-like, local-only notebook home. The screen is controlled: it owns
 * only transient UI state (tab, filters, dialogs) and reports every data
 * change through the callbacks above.
 */
@Composable
fun NotebookHomeScreen(
    notebooks: List<Notebook>,
    pdfs: List<RecentDoc>,
    onOpenNotebook: (String) -> Unit,
    onCreateNotebook: (NotebookDraft) -> Unit,
    onOpenPdf: (RecentDoc) -> Unit,
    onImportPdf: () -> Unit,
    onCreatePdf: () -> Unit,
    onOpenTools: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTrash: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onMoveFolder: (String, String) -> Unit,
    onSetTags: (String, List<String>) -> Unit,
    onOpenBackup: () -> Unit = {},
    onOpenTrace: (() -> Unit)? = null,
    onOpenHelp: () -> Unit = {},
) {
    var section by remember { mutableStateOf(LibrarySection.NOTES) }
    var scope by remember { mutableStateOf(NoteScope.ALL) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var gridMode by remember { mutableStateOf(true) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Notebook?>(null) }
    var moveTarget by remember { mutableStateOf<Notebook?>(null) }
    var tagsTarget by remember { mutableStateOf<Notebook?>(null) }
    var deleteTarget by remember { mutableStateOf<Notebook?>(null) }

    val folders = remember(notebooks) {
        notebooks.asSequence()
            .filter { !it.trashed }
            .map { it.folder.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }
    val tagCounts = remember(notebooks) {
        notebooks.asSequence()
            .filter { !it.trashed }
            .flatMap { it.tags.asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }
    val visibleNotebooks = remember(notebooks, scope, selectedFolder, selectedTag, query) {
        val needle = query.trim()
        notebooks.asSequence()
            .filter { notebook ->
                when (scope) {
                    NoteScope.ALL -> !notebook.trashed
                    NoteScope.FAVORITES -> !notebook.trashed && notebook.favorite
                    NoteScope.TRASH -> notebook.trashed
                }
            }
            .filter { notebook ->
                selectedFolder == null || notebook.folder.trim() == selectedFolder
            }
            .filter { notebook ->
                selectedTag == null || notebook.tags.any { it.equals(selectedTag, ignoreCase = true) }
            }
            .filter { notebook ->
                needle.isEmpty() ||
                    notebook.title.contains(needle, ignoreCase = true) ||
                    notebook.tags.any { it.contains(needle, ignoreCase = true) }
            }
            .sortedByDescending { it.updatedAt }
            .toList()
    }
    val visiblePdfs = remember(pdfs, query) {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            pdfs
        } else {
            pdfs.filter { it.name.contains(needle, ignoreCase = true) }
        }
        filtered.sortedByDescending { it.lastOpenedAt }
    }
    val continuation = remember(notebooks, pdfs) {
        selectHomeContinuation(notebooks, pdfs)
    }
    val showStart = scope == NoteScope.ALL &&
        query.isBlank() &&
        selectedFolder == null &&
        selectedTag == null

    val actions = NotebookActions(
        open = onOpenNotebook,
        toggleFavorite = onToggleFavorite,
        rename = { id -> renameTarget = notebooks.firstOrNull { it.id == id } },
        moveFolder = { id -> moveTarget = notebooks.firstOrNull { it.id == id } },
        editTags = { id -> tagsTarget = notebooks.firstOrNull { it.id == id } },
        trash = onTrash,
        restore = onRestore,
        deleteForever = { id -> deleteTarget = notebooks.firstOrNull { it.id == id } },
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 700.dp
        val horizontalPadding = if (expanded) 24.dp else 16.dp
        val subtitle = when (section) {
            LibrarySection.NOTES -> "노트 ${notebooks.count { !it.trashed }}개 · PDF ${pdfs.size}개"
            LibrarySection.PDF -> "최근 PDF ${pdfs.size}개"
            LibrarySection.TAGS -> "태그 ${tagCounts.size}개"
        }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("내 노트", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        searchOpen = !searchOpen
                                        if (!searchOpen) query = ""
                                    },
                                ) {
                                    Icon(
                                        if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                                        contentDescription = if (searchOpen) "검색 닫기" else "검색",
                                    )
                                }
                                Box {
                                    IconButton(onClick = { showOverflow = true }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "더보기")
                                    }
                                    DropdownMenu(
                                        expanded = showOverflow,
                                        onDismissRequest = { showOverflow = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("즐겨찾기") },
                                            leadingIcon = { Icon(Icons.Outlined.StarBorder, contentDescription = null) },
                                            onClick = {
                                                showOverflow = false
                                                section = LibrarySection.NOTES
                                                scope = NoteScope.FAVORITES
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("휴지통") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                            onClick = {
                                                showOverflow = false
                                                section = LibrarySection.NOTES
                                                scope = NoteScope.TRASH
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("사용법/도움말") },
                                            leadingIcon = {
                                                Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
                                            },
                                            onClick = {
                                                showOverflow = false
                                                onOpenHelp()
                                            },
                                        )
                                        if (BuildConfig.DEBUG && onOpenTrace != null) {
                                            HorizontalDivider()
                                            DropdownMenuItem(
                                                text = { Text("노트 추적") },
                                                leadingIcon = {
                                                    Icon(Icons.Outlined.BugReport, contentDescription = null)
                                                },
                                                onClick = {
                                                    showOverflow = false
                                                    onOpenTrace?.invoke()
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                        if (searchOpen) {
                            HomeSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = if (section == LibrarySection.PDF) {
                                    "PDF 이름 검색"
                                } else {
                                    "노트 제목·태그 검색"
                                },
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!expanded) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        LibrarySection.entries.forEach { item ->
                            NavigationBarItem(
                                selected = section == item,
                                onClick = { section = item },
                                icon = { Icon(sectionIcon(item), contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(Modifier.fillMaxSize().padding(innerPadding)) {
                if (expanded) {
                    LibraryRail(
                        selected = section,
                        onSelect = { section = it },
                    )
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (section) {
                        LibrarySection.NOTES -> NotesSection(
                            notebooks = notebooks,
                            visible = visibleNotebooks,
                            scope = scope,
                            onScopeChange = { scope = it },
                            folders = folders,
                            selectedFolder = selectedFolder,
                            onFolderChange = { selectedFolder = it },
                            tagCounts = tagCounts,
                            selectedTag = selectedTag,
                            onTagChange = { selectedTag = it },
                            query = query,
                            gridMode = gridMode,
                            onToggleGrid = { gridMode = !gridMode },
                            showStart = showStart,
                            continuation = continuation,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onOpenBackup = onOpenBackup,
                            onOpenTools = onOpenTools,
                            horizontalPadding = horizontalPadding,
                            actions = actions,
                            onCreate = { showCreate = true },
                        )

                        LibrarySection.PDF -> PdfSection(
                            pdfs = visiblePdfs,
                            query = query,
                            gridMode = gridMode,
                            onToggleGrid = { gridMode = !gridMode },
                            horizontalPadding = horizontalPadding,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onCreatePdf = onCreatePdf,
                            onOpenTools = onOpenTools,
                        )

                        LibrarySection.TAGS -> TagsSection(
                            tagCounts = tagCounts,
                            horizontalPadding = horizontalPadding,
                            onTagSelected = { tag ->
                                selectedTag = tag
                                selectedFolder = null
                                scope = NoteScope.ALL
                                query = ""
                                section = LibrarySection.NOTES
                            },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateNotebookDialog(
            folders = folders,
            onDismiss = { showCreate = false },
            onCreate = { draft ->
                onCreateNotebook(draft)
                showCreate = false
            },
        )
    }

    renameTarget?.let { target ->
        RenameNotebookDialog(
            notebook = target,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                onRename(target.id, title)
                renameTarget = null
            },
        )
    }
    moveTarget?.let { target ->
        MoveNotebookDialog(
            notebook = target,
            folders = folders,
            onDismiss = { moveTarget = null },
            onConfirm = { folder ->
                onMoveFolder(target.id, folder)
                moveTarget = null
            },
        )
    }
    tagsTarget?.let { target ->
        EditTagsDialog(
            notebook = target,
            onDismiss = { tagsTarget = null },
            onConfirm = { tags ->
                onSetTags(target.id, tags)
                tagsTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        DeleteForeverDialog(
            notebook = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteForever(target.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "검색어 지우기")
                }
            }
        },
    )
}

@Composable
private fun LibraryRail(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(10.dp))
        LibrarySection.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(sectionIcon(item), contentDescription = null) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "기기 저장",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

private fun sectionIcon(section: LibrarySection): ImageVector = when (section) {
    LibrarySection.NOTES -> Icons.AutoMirrored.Outlined.MenuBook
    LibrarySection.PDF -> Icons.Outlined.PictureAsPdf
    LibrarySection.TAGS -> Icons.Outlined.LocalOffer
}

@Composable
private fun NotesSection(
    notebooks: List<Notebook>,
    visible: List<Notebook>,
    scope: NoteScope,
    onScopeChange: (NoteScope) -> Unit,
    folders: List<String>,
    selectedFolder: String?,
    onFolderChange: (String?) -> Unit,
    tagCounts: List<Pair<String, Int>>,
    selectedTag: String?,
    onTagChange: (String?) -> Unit,
    query: String,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    showStart: Boolean,
    continuation: HomeContinuation,
    onOpenPdf: (RecentDoc) -> Unit,
    onImportPdf: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenTools: () -> Unit,
    horizontalPadding: Dp,
    actions: NotebookActions,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NotesFilters(
            scope = scope,
            onScopeChange = onScopeChange,
            folders = folders,
            selectedFolder = selectedFolder,
            onFolderChange = onFolderChange,
            tagCounts = tagCounts,
            selectedTag = selectedTag,
            onTagChange = onTagChange,
            gridMode = gridMode,
            onToggleGrid = onToggleGrid,
            horizontalPadding = horizontalPadding,
        )

        if (visible.isEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 10.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStart) {
                    item {
                        HomeStartContent(
                            continuation = continuation,
                            onOpenNotebook = actions.open,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onCreateNote = onCreate,
                            onOpenTools = onOpenTools,
                            onOpenBackup = onOpenBackup,
                        )
                    }
                }
                item {
                    EmptyNotesState(
                        notebooks = notebooks,
                        scope = scope,
                        query = query,
                        selectedFolder = selectedFolder,
                        selectedTag = selectedTag,
                        showCreateAction = !showStart,
                        onCreate = onCreate,
                    )
                }
                item { HomeFooter() }
            }
        } else if (gridMode) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 10.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showStart) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomeStartContent(
                            continuation = continuation,
                            onOpenNotebook = actions.open,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onCreateNote = onCreate,
                            onOpenTools = onOpenTools,
                            onOpenBackup = onOpenBackup,
                        )
                    }
                }
                gridItems(visible, key = { it.id }) { notebook ->
                    NotebookGridCard(notebook = notebook, actions = actions)
                }
                item(span = { GridItemSpan(maxLineSpan) }) { HomeFooter() }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 10.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStart) {
                    item {
                        HomeStartContent(
                            continuation = continuation,
                            onOpenNotebook = actions.open,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onCreateNote = onCreate,
                            onOpenTools = onOpenTools,
                            onOpenBackup = onOpenBackup,
                        )
                    }
                }
                items(visible, key = { it.id }) { notebook ->
                    NotebookListRow(notebook = notebook, actions = actions)
                }
                item { HomeFooter() }
            }
        }
    }
}

@Composable
private fun NotesFilters(
    scope: NoteScope,
    onScopeChange: (NoteScope) -> Unit,
    folders: List<String>,
    selectedFolder: String?,
    onFolderChange: (String?) -> Unit,
    tagCounts: List<Pair<String, Int>>,
    selectedTag: String?,
    onTagChange: (String?) -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    horizontalPadding: Dp,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NoteScope.entries.forEach { item ->
                    FilterChip(
                        selected = scope == item,
                        onClick = { onScopeChange(item) },
                        label = { Text(item.label) },
                        leadingIcon = {
                            Icon(
                                when (item) {
                                    NoteScope.ALL -> Icons.Outlined.Description
                                    NoteScope.FAVORITES -> Icons.Outlined.Star
                                    NoteScope.TRASH -> Icons.Outlined.Delete
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
            }
            IconButton(onClick = onToggleGrid) {
                Icon(
                    if (gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = if (gridMode) "목록으로 보기" else "그리드로 보기",
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = selectedFolder == null,
                onClick = { onFolderChange(null) },
                label = { Text("모든 폴더") },
                leadingIcon = {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            FilterChip(
                selected = selectedFolder == "",
                onClick = { onFolderChange("") },
                label = { Text("미분류") },
                leadingIcon = {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
            folders.forEach { folder ->
                FilterChip(
                    selected = selectedFolder == folder,
                    onClick = { onFolderChange(folder) },
                    label = { Text(folder) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            TagFilterChip(
                tagCounts = tagCounts,
                selectedTag = selectedTag,
                onTagChange = onTagChange,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TagFilterChip(
    tagCounts: List<Pair<String, Int>>,
    selectedTag: String?,
    onTagChange: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedTag != null,
            onClick = { open = true },
            label = { Text(selectedTag ?: "태그") },
            leadingIcon = {
                Icon(Icons.Outlined.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            trailingIcon = if (selectedTag != null) {
                { Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("모든 태그") },
                onClick = {
                    open = false
                    onTagChange(null)
                },
            )
            if (tagCounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("태그 없음") },
                    enabled = false,
                    onClick = { open = false },
                )
            } else {
                tagCounts.forEach { (tag, count) ->
                    DropdownMenuItem(
                        text = { Text("$tag · $count") },
                        onClick = {
                            open = false
                            onTagChange(tag)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotebookGridCard(notebook: Notebook, actions: NotebookActions) {
    val template = notebook.pages.firstOrNull()?.template ?: PaperTemplate.LINED
    Surface(
        onClick = { actions.open(notebook.id) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.84f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                NotebookCover(
                    coverColor = notebook.coverColor,
                    coverStyle = notebook.coverStyle,
                    template = template,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!notebook.trashed) {
                    IconButton(
                        onClick = { actions.toggleFavorite(notebook.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    ) {
                        Icon(
                            if (notebook.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (notebook.favorite) "즐겨찾기 해제" else "즐겨찾기",
                            tint = if (notebook.favorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        notebook.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    NotebookMenuButton(notebook = notebook, actions = actions)
                }
                Text(
                    "${notebook.pageCount}페이지 · ${relativeNoteTime(notebook.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notebook.tags.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        notebook.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (notebook.folder.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            notebook.folder,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (notebook.trashed) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { actions.restore(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("복원")
                        }
                        TextButton(
                            onClick = { actions.deleteForever(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("영구 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotebookListRow(notebook: Notebook, actions: NotebookActions) {
    val template = notebook.pages.firstOrNull()?.template ?: PaperTemplate.LINED
    Surface(
        onClick = { actions.open(notebook.id) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                NotebookCover(
                    coverColor = notebook.coverColor,
                    coverStyle = notebook.coverStyle,
                    template = template,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    notebook.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${notebook.pageCount}페이지 · ${relativeNoteTime(notebook.updatedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notebook.tags.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        notebook.tags.joinToString(" ") { "#$it" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (notebook.folder.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        notebook.folder,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (notebook.trashed) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(
                            onClick = { actions.restore(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("복원")
                        }
                        TextButton(
                            onClick = { actions.deleteForever(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("영구 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (!notebook.trashed) {
                IconButton(onClick = { actions.toggleFavorite(notebook.id) }) {
                    Icon(
                        if (notebook.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (notebook.favorite) "즐겨찾기 해제" else "즐겨찾기",
                        tint = if (notebook.favorite) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            NotebookMenuButton(notebook = notebook, actions = actions)
        }
    }
}

@Composable
private fun NotebookMenuButton(notebook: Notebook, actions: NotebookActions) {
    var open by remember(notebook.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = "노트 메뉴",
                modifier = Modifier.size(19.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (notebook.trashed) {
                DropdownMenuItem(
                    text = { Text("복원") },
                    leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.restore(notebook.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text("영구 삭제", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        open = false
                        actions.deleteForever(notebook.id)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text("이름 변경") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.rename(notebook.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (notebook.favorite) "즐겨찾기 해제" else "즐겨찾기") },
                    leadingIcon = {
                        Icon(
                            if (notebook.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        open = false
                        actions.toggleFavorite(notebook.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text("폴더 이동") },
                    leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.moveFolder(notebook.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text("태그 편집") },
                    leadingIcon = { Icon(Icons.Outlined.LocalOffer, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.editTags(notebook.id)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("휴지통", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        open = false
                        actions.trash(notebook.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyNotesState(
    notebooks: List<Notebook>,
    scope: NoteScope,
    query: String,
    selectedFolder: String?,
    selectedTag: String?,
    showCreateAction: Boolean,
    onCreate: () -> Unit,
) {
    val filtered = query.isNotBlank() || selectedFolder != null || selectedTag != null
    val icon = when {
        filtered -> Icons.Outlined.Search
        scope == NoteScope.TRASH -> Icons.Outlined.Delete
        scope == NoteScope.FAVORITES -> Icons.Outlined.StarBorder
        else -> Icons.AutoMirrored.Outlined.NoteAdd
    }
    val title: String
    val body: String
    val action: String?
    when {
        filtered -> {
            title = "조건에 맞는 노트가 없어요"
            body = "검색어나 폴더·태그 필터를 바꿔 보세요."
            action = null
        }

        scope == NoteScope.TRASH -> {
            title = "휴지통이 비어 있어요"
            body = "삭제한 노트는 여기에서 복원하거나 영구 삭제할 수 있습니다."
            action = null
        }

        scope == NoteScope.FAVORITES -> {
            title = "즐겨찾기한 노트가 없어요"
            body = "노트 카드의 별을 눌러 자주 보는 노트를 모아 두세요."
            action = "새 노트"
        }

        notebooks.none { !it.trashed } -> {
            title = "첫 노트를 만들어 보세요"
            body = "표지와 종이 템플릿을 골라 기기 안에만 저장되는 노트를 만듭니다."
            action = "새 노트"
        }

        else -> {
            title = "노트가 없어요"
            body = "새 노트를 만들어 필기와 아이디어를 정리해 보세요."
            action = "새 노트"
        }
    }
    EmptyState(
        icon = icon,
        title = title,
        body = body,
        actionLabel = action.takeIf { showCreateAction },
        onAction = onCreate,
    )
}

@Composable
private fun PdfSection(
    pdfs: List<RecentDoc>,
    query: String,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    horizontalPadding: Dp,
    onOpenPdf: (RecentDoc) -> Unit,
    onImportPdf: () -> Unit,
    onCreatePdf: () -> Unit,
    onOpenTools: () -> Unit,
) {
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 190.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ImportPdfBanner(onImportPdf = onImportPdf, onCreatePdf = onCreatePdf)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ToolsEntry(onClick = onOpenTools)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PdfHeader(
                    count = pdfs.size,
                    filtered = query.isNotBlank(),
                    gridMode = gridMode,
                    onToggleGrid = onToggleGrid,
                )
            }
            if (pdfs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPdfState(query = query, onImportPdf = onImportPdf)
                }
            } else {
                gridItems(pdfs, key = { it.uri }) { doc ->
                    PdfGridCard(doc = doc, onClick = { onOpenPdf(doc) })
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { HomeFooter() }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ImportPdfBanner(onImportPdf = onImportPdf, onCreatePdf = onCreatePdf) }
            item { ToolsEntry(onClick = onOpenTools) }
            item {
                PdfHeader(
                    count = pdfs.size,
                    filtered = query.isNotBlank(),
                    gridMode = gridMode,
                    onToggleGrid = onToggleGrid,
                )
            }
            if (pdfs.isEmpty()) {
                item { EmptyPdfState(query = query, onImportPdf = onImportPdf) }
            } else {
                items(pdfs, key = { it.uri }) { doc ->
                    PdfListRow(doc = doc, onClick = { onOpenPdf(doc) })
                }
            }
            item { HomeFooter() }
        }
    }
}

@Composable
private fun ImportPdfBanner(onImportPdf: () -> Unit, onCreatePdf: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.64f)),
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
                    Text("PDF 가져오기", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "기기에서 PDF를 열어 주석과 필기를 더하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportPdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF 가져오기")
                }
                OutlinedButton(onClick = onCreatePdf, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("빈 PDF")
                }
            }
        }
    }
}

@Composable
private fun PdfHeader(
    count: Int,
    filtered: Boolean,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("최근 PDF", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            if (filtered) "검색 ${count}개" else "${count}개",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onToggleGrid) {
            Icon(
                if (gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = if (gridMode) "목록으로 보기" else "그리드로 보기",
            )
        }
    }
}

@Composable
private fun PdfGridCard(doc: RecentDoc, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                doc.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${doc.pageCount}페이지 · ${relativeNoteTime(doc.lastOpenedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PdfListRow(doc: RecentDoc, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${doc.pageCount}페이지 · ${relativeNoteTime(doc.lastOpenedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPdfState(query: String, onImportPdf: () -> Unit) {
    val filtered = query.isNotBlank()
    EmptyState(
        icon = if (filtered) Icons.Outlined.Search else Icons.Outlined.PictureAsPdf,
        title = if (filtered) "검색 결과가 없어요" else "아직 PDF가 없어요",
        body = if (filtered) {
            "다른 이름으로 검색해 보세요."
        } else {
            "기기에서 PDF를 가져오면 최근 문서와 주석 작업을 이곳에서 이어갈 수 있습니다."
        },
        actionLabel = if (filtered) null else "PDF 가져오기",
        onAction = onImportPdf,
    )
}

@Composable
private fun TagsSection(
    tagCounts: List<Pair<String, Int>>,
    horizontalPadding: Dp,
    onTagSelected: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 10.dp,
            bottom = 24.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 4.dp)) {
                Text("태그", style = MaterialTheme.typography.titleLarge)
                Text(
                    "노트에 붙인 태그를 한곳에서 찾아봅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (tagCounts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Outlined.LocalOffer,
                    title = "태그가 없어요",
                    body = "노트를 만들거나 태그를 편집하면 이곳에 태그가 모입니다.",
                )
            }
        } else {
            gridItems(tagCounts, key = { it.first }) { (tag, count) ->
                TagCard(tag = tag, count = count, onClick = { onTagSelected(tag) })
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HomeFooter() }
    }
}

@Composable
private fun TagCard(tag: String, count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    tag,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "노트 ${count}개",
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

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                FilledTonalButton(onClick = onAction) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun HomeFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "모든 작업은 기기 안에서만",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreateNotebookDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onCreate: (NotebookDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var coverColor by remember { mutableStateOf(CoverPalette.first()) }
    var coverStyle by remember { mutableStateOf(CoverStyle.SOLID) }
    var template by remember { mutableStateOf(PaperTemplate.LINED) }
    var folder by remember { mutableStateOf("") }
    var tagsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 노트") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("노트 이름") },
                    placeholder = { Text("새 노트") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("표지 색", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                CoverPalette.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { color ->
                            CoverSwatch(
                                color = color,
                                selected = coverColor == color,
                                onClick = { coverColor = color },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text("표지 무늬", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    CoverStyle.entries.forEach { style ->
                        FilterChip(
                            selected = coverStyle == style,
                            onClick = { coverStyle = style },
                            label = { Text(coverStyleLabel(style)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("종이 템플릿", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    PaperTemplate.entries.forEach { paper ->
                        FilterChip(
                            selected = template == paper,
                            onClick = { template = paper },
                            label = { Text(paper.label) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("폴더 (선택)") },
                    placeholder = { Text("예: 강의 노트") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folders.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        folders.forEach { existing ->
                            AssistChip(
                                onClick = { folder = existing },
                                label = { Text(existing) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("태그 (선택)") },
                    placeholder = { Text("시험, 정리, 아이디어") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "쉼표로 구분해 입력하세요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        NotebookDraft(
                            title = title.trim().ifBlank { "새 노트" },
                            coverColor = coverColor,
                            coverStyle = coverStyle,
                            template = template,
                            folder = folder.trim(),
                            tags = parseTagInput(tagsText),
                        ),
                    )
                },
            ) {
                Text("만들기")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun CoverSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(color)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = "선택된 표지 색",
                tint = if (swatch.luminance() > 0.55f) Color(0xFF1B1C19) else Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun coverStyleLabel(style: CoverStyle): String = when (style) {
    CoverStyle.SOLID -> "단색"
    CoverStyle.GRID -> "모눈"
    CoverStyle.DOTS -> "점"
    CoverStyle.STRIPES -> "줄무늬"
}

@Composable
private fun RenameNotebookDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(notebook.id) { mutableStateOf(notebook.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이름 변경") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("노트 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun MoveNotebookDialog(
    notebook: Notebook,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var folder by remember(notebook.id) { mutableStateOf(notebook.folder) }
    val choices = remember(folders, notebook.folder) {
        (folders + notebook.folder).asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("폴더 이동") },
        text = {
            Column {
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("폴더") },
                    placeholder = { Text("비워 두면 미분류") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (choices.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "기존 폴더",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AssistChip(
                            onClick = { folder = "" },
                            label = { Text("미분류") },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                        choices.forEach { choice ->
                            AssistChip(
                                onClick = { folder = choice },
                                label = { Text(choice) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(folder.trim()) }) { Text("이동") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun EditTagsDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    var text by remember(notebook.id) { mutableStateOf(notebook.tags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("태그 편집") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("태그") },
                    placeholder = { Text("시험, 정리, 아이디어") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "쉼표로 구분해 입력하세요. 비워 두면 모든 태그가 제거됩니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(parseTagInput(text)) }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun DeleteForeverDialog(
    notebook: Notebook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("영구 삭제") },
        text = {
            Text(
                "\"${notebook.title}\" 노트를 기기에서 완전히 삭제합니다. 이 작업은 되돌릴 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("영구 삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private class NotebookActions(
    val open: (String) -> Unit,
    val toggleFavorite: (String) -> Unit,
    val rename: (String) -> Unit,
    val moveFolder: (String) -> Unit,
    val editTags: (String) -> Unit,
    val trash: (String) -> Unit,
    val restore: (String) -> Unit,
    val deleteForever: (String) -> Unit,
)

/** Pure formatter shared by tests. Negative clock skew is treated as "just now". */
internal fun relativeNoteTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - millis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> "방금"
        diff < 3_600_000L -> "${diff / 60_000L}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000L}시간 전"
        diff < 604_800_000L -> "${diff / 86_400_000L}일 전"
        else -> SimpleDateFormat("yyyy. M. d.", Locale.KOREAN).format(Date(millis))
    }
}

/** Splits comma/semicolon/newline separated tags, trims leading '#', drops blanks, dedupes. */
internal fun parseTagInput(raw: String): List<String> =
    raw.split(',', ';', '\n')
        .asSequence()
        .map { it.trim().trimStart('#').trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()
