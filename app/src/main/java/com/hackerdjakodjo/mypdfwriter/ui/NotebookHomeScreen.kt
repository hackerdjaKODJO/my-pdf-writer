@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotebookDraft
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class LibrarySection(val label: String) {
    NOTES("노트"),
    PDF("PDF"),
    TAGS("태그"),
}

/** Compact top control that replaces the old bottom tab bar / side rail. */
internal fun homeSectionLabels(): List<String> = LibrarySection.entries.map { it.label }

/**
 * Home has exactly one primary action. A returning student resumes the last
 * document (`이어서 읽기`); a new student starts with a PDF (`PDF 가져오기`).
 * The decision is pure so the JVM tests can pin the policy without Compose.
 */
internal enum class HomePrimaryAction(val label: String) {
    CONTINUE("이어서 읽기"),
    IMPORT("PDF 가져오기"),
}

internal fun homePrimaryAction(hasContent: Boolean): HomePrimaryAction =
    if (hasContent) HomePrimaryAction.CONTINUE else HomePrimaryAction.IMPORT

/** The secondary create action that is always present beside the primary one. */
internal const val HOME_NEW_NOTE_LABEL = "새 노트"

/** Optional first-run entry: visible until the student starts or dismisses it. */
internal fun showHomeOnboardingPrompt(hasSeenOnboarding: Boolean): Boolean = !hasSeenOnboarding

/**
 * Home quick buttons collapse to two slots: the primary action (resume for a
 * returning student, import for a new one) and 새 노트. Tools, backup and empty
 * PDF creation live in 홈 설정 instead of competing for this row.
 */
internal fun homeQuickActionLabels(hasContent: Boolean): List<String> = listOf(
    homePrimaryAction(hasContent).label,
    HOME_NEW_NOTE_LABEL,
)

/** Both quick labels are allowed two lines; 48 dp targets are set by the UI. */
internal fun homeQuickLabelMaxLines(
    availableWidthDp: Int,
    label: String,
    fontScale: Float = 1f,
): Int = studentLabelMaxLines(availableWidthDp, label.length * 12, fontScale)

/** Which document the `이어서 읽기` action opens: the most recently used one. */
internal enum class HomeResumeTarget { PDF, NOTEBOOK, IMPORT }

internal fun homeResumeTarget(continuation: HomeContinuation): HomeResumeTarget {
    val pdf = continuation.lastPdf
    val notebook = continuation.lastNotebook
    return when {
        pdf == null && notebook == null -> HomeResumeTarget.IMPORT
        notebook == null -> HomeResumeTarget.PDF
        pdf == null -> HomeResumeTarget.NOTEBOOK
        pdf.lastOpenedAt >= notebook.updatedAt -> HomeResumeTarget.PDF
        else -> HomeResumeTarget.NOTEBOOK
    }
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

/** Spoken colour names for the cover swatches, in [CoverPalette] order. */
private val CoverColorNames = listOf(
    "하늘색", "민트색", "살구색", "분홍색", "라벤더색", "모래색", "청록색", "연두색",
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
    showOnboardingPrompt: Boolean = false,
    onOpenOnboarding: () -> Unit = {},
    onDismissOnboardingPrompt: () -> Unit = {},
    onRemovePdfRecent: (String) -> Unit = {},
    libraryMessage: String? = null,
    onDismissLibraryMessage: () -> Unit = {},
) {
    var section by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { name ->
                runCatching { LibrarySection.valueOf(name) }.getOrNull()
                    ?: LibrarySection.NOTES
            },
        ),
    ) { mutableStateOf(LibrarySection.NOTES) }
    var scope by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { name ->
                runCatching { NoteScope.valueOf(name) }.getOrNull()
                    ?: NoteScope.ALL
            },
        ),
    ) { mutableStateOf(NoteScope.ALL) }
    var sort by rememberSaveable(
        stateSaver = Saver(
            save = { it.name },
            restore = { name ->
                runCatching { LibrarySort.valueOf(name) }.getOrNull()
                    ?: LibrarySort.RECENTLY_MODIFIED
            },
        ),
    ) { mutableStateOf(LibrarySort.RECENTLY_MODIFIED) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var selectedFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showQuickStudy by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
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
    val visibleNotebooks = remember(notebooks, scope, selectedFolder, selectedTag, query, sort) {
        val needle = query.trim()
        val filtered = notebooks.asSequence()
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
            .toList()
        sortNotebooks(filtered, sort)
    }
    val visiblePdfs = remember(pdfs, query, sort) {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            pdfs
        } else {
            pdfs.filter { it.name.contains(needle, ignoreCase = true) }
        }
        sortPdfs(filtered, sort)
    }
    val continuation = remember(notebooks, pdfs) {
        selectHomeContinuation(notebooks, pdfs)
    }
    val onContinue: () -> Unit = {
        when (homeResumeTarget(continuation)) {
            HomeResumeTarget.PDF -> continuation.lastPdf?.let(onOpenPdf)
            HomeResumeTarget.NOTEBOOK ->
                continuation.lastNotebook?.let { onOpenNotebook(it.id) }
            HomeResumeTarget.IMPORT -> onImportPdf()
        }
        Unit
    }
    val filterState = HomeFilterState(
        scope = scope,
        folder = selectedFolder,
        tag = selectedTag,
        query = query,
    )
    val showStart = section == LibrarySection.NOTES && filterState.showsHomeStart()

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
        val scale = chromeScale(maxWidth.value.roundToInt(), LocalDensity.current.fontScale)
        val subtitle = when (section) {
            LibrarySection.NOTES -> "노트 ${notebooks.count { !it.trashed }}개 · PDF ${pdfs.size}개"
            LibrarySection.PDF -> "최근 PDF ${pdfs.size}개"
            LibrarySection.TAGS -> "태그 ${tagCounts.size}개"
        }

        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(libraryMessage) {
            if (libraryMessage != null) {
                snackbarHostState.showSnackbar(libraryMessage)
                onDismissLibraryMessage()
            }
        }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                HomeCreateFab(
                    onNewNote = { showCreate = true },
                    onImportPdf = onImportPdf,
                )
            },
            topBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        when (section) {
                                            LibrarySection.NOTES -> "내 노트"
                                            LibrarySection.PDF -> "내 PDF"
                                            LibrarySection.TAGS -> "태그"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = scale.titleMaxLines,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                    if (scale.showSubtitle) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
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
                                IconButton(onClick = onOpenHelp) {
                                    Icon(
                                        Icons.Outlined.HelpOutline,
                                        contentDescription = "도움말",
                                    )
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(
                                        Icons.Outlined.Settings,
                                        contentDescription = "홈 설정",
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                        )
                        HomeSectionSegments(
                            selected = section,
                            onSelect = { section = it },
                        )
                        if (searchOpen) {
                            HomeSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                label = when (section) {
                                    LibrarySection.PDF -> "PDF 이름 검색"
                                    LibrarySection.TAGS -> "태그 검색"
                                    LibrarySection.NOTES -> "노트 제목·태그 검색"
                                },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (showOnboardingPrompt) {
                    HomeOnboardingPrompt(
                        onOpen = onOpenOnboarding,
                        onDismiss = onDismissOnboardingPrompt,
                        horizontalPadding = horizontalPadding,
                    )
                }
                if (showStart && continuation.hasContent) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp),
                    ) {
                        HomeContinuationCards(
                            continuation = continuation,
                            onOpenNotebook = onOpenNotebook,
                            onOpenPdf = onOpenPdf,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        LibrarySection.NOTES -> NotesSection(
                            notebooks = notebooks,
                            visible = visibleNotebooks,
                            filterState = filterState,
                            sort = sort,
                            onSortChange = { sort = it },
                            gridMode = gridMode,
                            onToggleGrid = { gridMode = !gridMode },
                            onOpenFilters = { showFilters = true },
                            onRemoveFilter = { kind ->
                                val updated = filterState.without(kind)
                                scope = updated.scope
                                selectedFolder = updated.folder
                                selectedTag = updated.tag
                                query = updated.query
                            },
                            onClearAllFilters = {
                                val updated = filterState.cleared()
                                scope = updated.scope
                                selectedFolder = updated.folder
                                selectedTag = updated.tag
                                query = updated.query
                            },
                            showStart = showStart,
                            hasContent = continuation.hasContent,
                            onImportPdf = onImportPdf,
                            onContinue = onContinue,
                            onOpenQuickStudy = { showQuickStudy = true },
                            horizontalPadding = horizontalPadding,
                            actions = actions,
                            onClearFilters = {
                                // Same semantics as the filter chip row: clear the
                                // scope too, so "필터 초기화" in Trash really resets.
                                val updated = filterState.cleared()
                                scope = updated.scope
                                selectedFolder = updated.folder
                                selectedTag = updated.tag
                                query = updated.query
                            },
                            onCreate = { showCreate = true },
                        )

                        LibrarySection.PDF -> PdfSection(
                            pdfs = visiblePdfs,
                            hasAnyPdf = pdfs.isNotEmpty(),
                            query = query,
                            onClearQuery = { query = "" },
                            sort = sort,
                            onSortChange = { sort = it },
                            gridMode = gridMode,
                            onToggleGrid = { gridMode = !gridMode },
                            horizontalPadding = horizontalPadding,
                            onOpenPdf = onOpenPdf,
                            onImportPdf = onImportPdf,
                            onCreatePdf = onCreatePdf,
                            onRemovePdfRecent = onRemovePdfRecent,
                        )

                        LibrarySection.TAGS -> TagsSection(
                            tagCounts = tagCounts,
                            query = query,
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

    if (showFilters) {
        HomeFilterSheet(
            initialScope = scope,
            initialFolder = selectedFolder,
            initialTag = selectedTag,
            initialQuery = query,
            folders = folders,
            tagCounts = tagCounts,
            onDismiss = { showFilters = false },
            onApply = { newScope, newFolder, newTag, newQuery ->
                scope = newScope
                selectedFolder = newFolder
                selectedTag = newTag
                query = newQuery
                showFilters = false
            },
        )
    }

    if (showSettings) {
        HomeSettingsSheet(
            onDismiss = { showSettings = false },
            onCreatePdf = {
                showSettings = false
                onCreatePdf()
            },
            onOpenBackup = {
                showSettings = false
                onOpenBackup()
            },
            onOpenTrash = {
                showSettings = false
                searchOpen = false
                query = ""
                selectedFolder = null
                selectedTag = null
                scope = NoteScope.TRASH
                section = LibrarySection.NOTES
            },
            onOpenTags = {
                showSettings = false
                searchOpen = false
                query = ""
                section = LibrarySection.TAGS
            },
            onOpenTools = {
                showSettings = false
                onOpenTools()
            },
            onOpenHelp = {
                showSettings = false
                onOpenHelp()
            },
            onOpenInfo = {
                showSettings = false
                showInfo = true
            },
            onOpenTrace = onOpenTrace?.let { trace ->
                {
                    showSettings = false
                    trace()
                }
            },
        )
    }

    if (showInfo) {
        HomeInfoDialog(onDismiss = { showInfo = false })
    }

    if (showQuickStudy) {
        QuickStudySheet(
            onDismiss = { showQuickStudy = false },
            onOpenPdf = { continuation.lastPdf?.let(onOpenPdf) ?: onImportPdf() },
            onImportPdf = onImportPdf,
            hasRecentPdf = continuation.lastPdf != null,
        )
    }
}

/** Small, dismissible first-run entry; it never blocks the Home actions. */
@Composable
private fun HomeOnboardingPrompt(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    horizontalPadding: Dp,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(start = 12.dp).size(18.dp),
            )
            TextButton(
                onClick = onOpen,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MIN_TARGET_DP.dp)
                    .semantics { contentDescription = UiCopy.HOME_ONBOARDING_PROMPT },
            ) {
                Text(
                    UiCopy.HOME_ONBOARDING_PROMPT,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = STUDENT_LABEL_MAX_LINES,
                    overflow = TextOverflow.Clip,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(MIN_TARGET_DP.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = UiCopy.HOME_ONBOARDING_DISMISS,
                )
            }
        }
    }
}

@Composable
private fun HomeCreateFab(
    onNewNote: () -> Unit,
    onImportPdf: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FloatingActionButton(
            onClick = { open = true },
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "새로 만들기")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("새 노트") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
                },
                onClick = {
                    open = false
                    onNewNote()
                },
            )
            DropdownMenuItem(
                text = { Text("PDF 가져오기") },
                leadingIcon = {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                },
                onClick = {
                    open = false
                    onImportPdf()
                },
            )
            // 빈 PDF moved to 홈 설정; the FAB stays a two-item creation menu.
        }
    }
}

@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "검색어 지우기")
                }
            }
        },
    )
}

/**
 * The former bottom tabs (and wide-screen rail) are now this compact top
 * segmented control. Labels wrap to two lines and the row scrolls horizontally,
 * so it stays usable at 320dp and with large fonts.
 */
@Composable
private fun HomeSectionSegments(
    selected: LibrarySection,
    onSelect: (LibrarySection) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibrarySection.entries.forEach { item ->
                val isSelected = selected == item
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .selectable(selected = isSelected, onClick = { onSelect(item) })
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.semantics {
                            contentDescription = "${item.label} 섹션"
                        },
                    )
                }
            }
        }
    }
}

/**
 * Home start: one primary action plus exactly one secondary create button. The
 * primary slot resumes a returning student and imports for a new student; the
 * quick-study entry stays reachable but is de-emphasised so it never competes
 * with the primary action.
 */
@Composable
private fun StudentHomeStart(
    primary: HomePrimaryAction,
    onPrimary: () -> Unit,
    onCreateNote: () -> Unit,
    onOpenQuickStudy: () -> Unit,
) {
    val labels = homeQuickActionLabels(primary == HomePrimaryAction.CONTINUE)
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val cardWidthDp = ((maxWidth - 8.dp) / 2).value.roundToInt()
            val labelWidthDp = (cardWidthDp - 24).coerceAtLeast(48)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomePrimaryActionButton(
                    label = labels[0],
                    primary = true,
                    icon = if (primary == HomePrimaryAction.CONTINUE) {
                        Icons.Outlined.ChevronRight
                    } else {
                        Icons.Outlined.PictureAsPdf
                    },
                    labelMaxLines = homeQuickLabelMaxLines(labelWidthDp, labels[0], fontScale),
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f),
                )
                HomePrimaryActionButton(
                    label = labels[1],
                    primary = false,
                    icon = Icons.AutoMirrored.Outlined.NoteAdd,
                    labelMaxLines = homeQuickLabelMaxLines(labelWidthDp, labels[1], fontScale),
                    onClick = onCreateNote,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onOpenQuickStudy,
            modifier = Modifier
                .heightIn(min = MIN_TARGET_DP.dp)
                .semantics { contentDescription = "빠른 학습" },
        ) {
            Text(
                "빠른 학습 · 네 단계로 시작하기",
                style = MaterialTheme.typography.labelMedium,
                maxLines = STUDENT_LABEL_MAX_LINES,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun HomePrimaryActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    labelMaxLines: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (primary) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (primary) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        },
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics { contentDescription = label },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (primary) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NotesSection(
    notebooks: List<Notebook>,
    visible: List<Notebook>,
    filterState: HomeFilterState,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (HomeFilterKind) -> Unit,
    onClearAllFilters: () -> Unit,
    showStart: Boolean,
    hasContent: Boolean,
    onImportPdf: () -> Unit,
    onContinue: () -> Unit,
    onOpenQuickStudy: () -> Unit,
    horizontalPadding: Dp,
    actions: NotebookActions,
    onClearFilters: () -> Unit,
    onCreate: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (homeShowsLibraryControls(visible.size, filterState.hasActiveFilters)) {
            NotesFilters(
                filterState = filterState,
                sort = sort,
                onSortChange = onSortChange,
                gridMode = gridMode,
                onToggleGrid = onToggleGrid,
                onOpenFilters = onOpenFilters,
                onRemoveFilter = onRemoveFilter,
                onClearAllFilters = onClearAllFilters,
                horizontalPadding = horizontalPadding,
            )
        }

        if (visible.isEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 10.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStart) {
                    item {
                        StudentHomeStart(
                            primary = homePrimaryAction(hasContent),
                            onPrimary = if (hasContent) onContinue else onImportPdf,
                            onCreateNote = onCreate,
                            onOpenQuickStudy = onOpenQuickStudy,
                        )
                    }
                }
                item {
                    EmptyNotesState(
                        notebooks = notebooks,
                        filterState = filterState,
                        showCreateAction = !showStart,
                        onClearFilters = onClearFilters,
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
                    bottom = 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showStart) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StudentHomeStart(
                            primary = homePrimaryAction(hasContent),
                            onPrimary = if (hasContent) onContinue else onImportPdf,
                            onCreateNote = onCreate,
                            onOpenQuickStudy = onOpenQuickStudy,
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
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStart) {
                    item {
                        StudentHomeStart(
                            primary = homePrimaryAction(hasContent),
                            onPrimary = if (hasContent) onContinue else onImportPdf,
                            onCreateNote = onCreate,
                            onOpenQuickStudy = onOpenQuickStudy,
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
    filterState: HomeFilterState,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    onOpenFilters: () -> Unit,
    onRemoveFilter: (HomeFilterKind) -> Unit,
    onClearAllFilters: () -> Unit,
    horizontalPadding: Dp,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = horizontalPadding, end = horizontalPadding, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterButton(activeCount = filterState.activeCount, onClick = onOpenFilters)
            SortMenuButton(sort = sort, onSortChange = onSortChange)
            ViewModeButton(gridMode = gridMode, onToggleGrid = onToggleGrid)
        }
        if (filterState.hasActiveFilters) {
            ActiveFilterRow(
                chips = homeFilterChips(filterState),
                onRemove = onRemoveFilter,
                onClearAll = onClearAllFilters,
                horizontalPadding = horizontalPadding,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = {
            if (activeCount > 0) {
                Badge { Text(activeCount.toString()) }
            }
        },
    ) {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(4.dp))
            Text("필터", maxLines = 1)
        }
    }
}

/** Compact sort control shared by the Notes and PDF sections. */
@Composable
private fun SortMenuButton(
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "정렬: ${sort.label}",
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text("정렬", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ViewModeButton(gridMode: Boolean, onToggleGrid: () -> Unit) {
    OutlinedButton(
        onClick = onToggleGrid,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Icon(
            if (gridMode) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
            contentDescription = if (gridMode) "목록으로 보기" else "그리드로 보기",
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("보기", maxLines = 1)
    }
}

@Composable
private fun ActiveFilterRow(
    chips: List<HomeFilterChip>,
    onRemove: (HomeFilterKind) -> Unit,
    onClearAll: () -> Unit,
    horizontalPadding: Dp,
) {
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPadding, end = horizontalPadding, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { chip ->
                AssistChip(
                    onClick = { onRemove(chip.kind) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    label = {
                        Text(
                            chip.label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }
        }
        TextButton(
            onClick = onClearAll,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("모두 지우기", maxLines = 2, style = MaterialTheme.typography.labelMedium)
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
                            .padding(4.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                    ) {
                        Icon(
                            if (notebook.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = favoriteToggleDescription(notebook.favorite),
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
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("복원")
                        }
                        TextButton(
                            onClick = { actions.deleteForever(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
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
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("복원")
                        }
                        TextButton(
                            onClick = { actions.deleteForever(notebook.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text("영구 삭제", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (!notebook.trashed) {
                IconButton(
                    onClick = { actions.toggleFavorite(notebook.id) },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        if (notebook.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = favoriteToggleDescription(notebook.favorite),
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
        IconButton(onClick = { open = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = menuLabel("노트 메뉴", notebook.title),
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
    filterState: HomeFilterState,
    showCreateAction: Boolean,
    onClearFilters: () -> Unit,
    onCreate: () -> Unit,
) {
    val scope = filterState.scope
    val filtered = filterState.query.isNotBlank() ||
        filterState.folder != null ||
        filterState.tag != null
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
            action = "필터 초기화"
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
        actionLabel = if (filtered) action else action.takeIf { showCreateAction },
        onAction = if (filtered) onClearFilters else onCreate,
    )
}

@Composable
private fun PdfSection(
    pdfs: List<RecentDoc>,
    hasAnyPdf: Boolean,
    query: String,
    onClearQuery: () -> Unit,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
    horizontalPadding: Dp,
    onOpenPdf: (RecentDoc) -> Unit,
    onImportPdf: () -> Unit,
    onCreatePdf: () -> Unit,
    onRemovePdfRecent: (String) -> Unit,
) {
    var menuTarget by remember { mutableStateOf<RecentDoc?>(null) }
    val queryChips = if (query.isBlank()) {
        emptyList()
    } else {
        homeFilterChips(HomeFilterState(query = query))
    }
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 190.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 10.dp,
                bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pdfs.isNotEmpty() || query.isNotBlank()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PdfHeader(
                        count = pdfs.size,
                        filtered = query.isNotBlank(),
                        sort = sort,
                        onSortChange = onSortChange,
                        gridMode = gridMode,
                        onToggleGrid = onToggleGrid,
                    )
                }
            }
            if (queryChips.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ActiveFilterRow(
                        chips = queryChips,
                        onRemove = { onClearQuery() },
                        onClearAll = onClearQuery,
                        horizontalPadding = 0.dp,
                    )
                }
            }
            if (pdfs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPdfState(
                        query = query,
                        hasAnyPdf = hasAnyPdf,
                        onImportPdf = onImportPdf,
                        onClearQuery = onClearQuery,
                    )
                }
            } else {
                gridItems(pdfs, key = { it.uri }) { doc ->
                    PdfGridCard(
                        doc = doc,
                        onClick = { onOpenPdf(doc) },
                        onMenu = { menuTarget = doc },
                    )
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
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (pdfs.isNotEmpty() || query.isNotBlank()) {
                item {
                    PdfHeader(
                        count = pdfs.size,
                        filtered = query.isNotBlank(),
                        sort = sort,
                        onSortChange = onSortChange,
                        gridMode = gridMode,
                        onToggleGrid = onToggleGrid,
                    )
                }
            }
            if (queryChips.isNotEmpty()) {
                item {
                    ActiveFilterRow(
                        chips = queryChips,
                        onRemove = { onClearQuery() },
                        onClearAll = onClearQuery,
                        horizontalPadding = 0.dp,
                    )
                }
            }
            if (pdfs.isEmpty()) {
                item {
                    EmptyPdfState(
                        query = query,
                        hasAnyPdf = hasAnyPdf,
                        onImportPdf = onImportPdf,
                        onClearQuery = onClearQuery,
                    )
                }
            } else {
                items(pdfs, key = { it.uri }) { doc ->
                    PdfListRow(
                        doc = doc,
                        onClick = { onOpenPdf(doc) },
                        onMenu = { menuTarget = doc },
                    )
                }
            }
            item { HomeFooter() }
        }
    }

    menuTarget?.let { doc ->
        PdfRecentCardSheet(
            doc = doc,
            onDismiss = { menuTarget = null },
            onRemove = { uri ->
                onRemovePdfRecent(uri)
                menuTarget = null
            },
        )
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
                Button(
                    onClick = onImportPdf,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF 가져오기", maxLines = 2)
                }
                OutlinedButton(
                    onClick = onCreatePdf,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("빈 PDF", maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun PdfHeader(
    count: Int,
    filtered: Boolean,
    sort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    gridMode: Boolean,
    onToggleGrid: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "최근 PDF",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                if (filtered) "검색 ${count}개" else "${count}개",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // PDF 도구 lives in 홈 설정; the PDF header keeps only view controls.
        SortMenuButton(sort = sort, onSortChange = onSortChange)
        ViewModeButton(gridMode = gridMode, onToggleGrid = onToggleGrid)
    }
}

@Composable
private fun PdfGridCard(doc: RecentDoc, onClick: () -> Unit, onMenu: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = menuLabel(UiCopy.RECENT_PDF_MENU, doc.name),
                        modifier = Modifier.size(19.dp),
                    )
                }
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
private fun PdfListRow(doc: RecentDoc, onClick: () -> Unit, onMenu: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onMenu),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
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
            IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = menuLabel(UiCopy.RECENT_PDF_MENU, doc.name),
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyPdfState(
    query: String,
    hasAnyPdf: Boolean,
    onImportPdf: () -> Unit,
    onClearQuery: () -> Unit,
) {
    val filtered = pdfEmptyStateIsFiltered(hasAnyPdf, query)
    EmptyState(
        icon = if (filtered) Icons.Outlined.Search else Icons.Outlined.PictureAsPdf,
        title = if (filtered) "검색 결과가 없어요" else "아직 PDF가 없어요",
        body = if (filtered) {
            "다른 이름으로 검색해 보세요."
        } else {
            "기기에서 PDF를 가져오면 최근 PDF와 주석 작업을 이곳에서 이어갈 수 있습니다."
        },
        actionLabel = if (filtered) "검색 지우기" else "PDF 가져오기",
        onAction = if (filtered) onClearQuery else onImportPdf,
    )
}

@Composable
private fun TagsSection(
    tagCounts: List<Pair<String, Int>>,
    query: String,
    horizontalPadding: Dp,
    onTagSelected: (String) -> Unit,
) {
    val visibleTags = remember(tagCounts, query) { filterTagCounts(tagCounts, query) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = horizontalPadding,
            end = horizontalPadding,
            top = 10.dp,
            bottom = 96.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 4.dp)) {
                Text(
                    "태그",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    "노트에 붙인 태그를 한곳에서 찾아봅니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (visibleTags.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = if (query.isBlank()) Icons.Outlined.LocalOffer else Icons.Outlined.Search,
                    title = if (query.isBlank()) "태그가 없어요" else "검색 결과가 없어요",
                    body = if (query.isBlank()) {
                        "노트를 만들거나 태그를 편집하면 이곳에 태그가 모입니다."
                    } else {
                        "다른 태그 이름으로 검색해 보세요."
                    },
                )
            }
        } else {
            gridItems(visibleTags, key = { it.first }) { (tag, count) ->
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
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
    var showAdvanced by remember { mutableStateOf(false) }

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
                                name = CoverColorNames[CoverPalette.indexOf(color)],
                                selected = coverColor == color,
                                onClick = { coverColor = color },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "고급 옵션 접기" else "고급 옵션 더 보기")
                }
                if (showAdvanced) {
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
                                modifier = Modifier.heightIn(min = 48.dp),
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
                                modifier = Modifier.heightIn(min = 48.dp),
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
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("취소") }
        },
    )
}

@Composable
private fun CoverSwatch(
    color: Int,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatch = Color(color)
    Box(
        modifier = Modifier
            .size(48.dp)
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
            .clickable(onClick = onClick)
            .semantics { contentDescription = colorChoiceLabel(name, selected) },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
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
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("취소") }
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
            TextButton(
                onClick = { onConfirm(folder.trim()) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("이동") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("취소") }
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
            TextButton(
                onClick = { onConfirm(parseTagInput(text)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("취소") }
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
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("영구 삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("취소") }
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
