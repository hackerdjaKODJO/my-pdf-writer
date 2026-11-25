package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.PageOps
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import kotlin.math.roundToInt

/**
 * Thumbnail grid for reordering and editing the working page list.
 *
 * The screen is deliberately stateless: selection and every mutation are
 * delegated to the caller so it can be backed by [PdfViewModel] (or any other
 * page store) without touching the viewer implementation. Undo/redo default to
 * disabled for standalone callers; the viewer supplies its snapshot history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    docName: String,
    pages: List<PageSpec>,
    selected: Set<Int>,
    loadThumbnail: suspend (Int) -> Bitmap?,
    revision: Int = 0,
    onBack: () -> Unit,
    onSelectionChange: (Set<Int>) -> Unit,
    onRotate: (Set<Int>, Int) -> Unit,
    onDelete: (Set<Int>) -> Unit,
    onDuplicate: (Set<Int>) -> Unit,
    onMove: (Set<Int>, Int) -> Unit,
    onInsertBlank: (Int) -> Unit,
    onExtract: (Set<Int>) -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
) {
    val selection = selected.filter { it in pages.indices }.toSet()
    var pendingDelete by remember { mutableStateOf<Set<Int>>(emptySet()) }

    BackHandler(onBack = onBack)

    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptySet() },
            title = { Text("${pendingDelete.size}개 페이지 삭제") },
            text = {
                Text("선택한 페이지가 문서에서 제거됩니다. 실행 취소로 되돌릴 수 있습니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pendingDelete)
                        pendingDelete = emptySet()
                    },
                    modifier = Modifier.testTag("organize_delete_confirm"),
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptySet() }) { Text("취소") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                title = {
                    Column {
                        Text(
                            "페이지 정리",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${docName.ifBlank { "문서" }} · ${pages.size}개 페이지",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier
                            .size(MIN_TARGET_DP.dp)
                            .testTag("organize_undo"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = stringResource(R.string.undo),
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier
                            .size(MIN_TARGET_DP.dp)
                            .testTag("organize_redo"),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = stringResource(R.string.redo),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            OrganizeBottomBar(
                selection = selection,
                pageCount = pages.size,
                onSelectionChange = onSelectionChange,
                onRotate = onRotate,
                onDelete = { pendingDelete = it },
                onDuplicate = onDuplicate,
                onMove = onMove,
                onInsertBlank = { afterIndex -> onInsertBlank(afterIndex) },
                onExtract = onExtract,
                onMerge = onMerge,
                onSplit = onSplit,
                onExport = onExport,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (pages.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("페이지가 없습니다", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "문서를 다시 열거나 병합해서 페이지를 추가하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = pages.size, key = { index -> index }) { index ->
                    val spec = pages[index]
                    OrganizePageCard(
                        index = index,
                        spec = spec,
                        selected = index in selection,
                        loadThumbnail = loadThumbnail,
                        revision = revision,
                        onClick = {
                            onSelectionChange(
                                if (index in selection) selection - index else selection + index,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrganizePageCard(
    index: Int,
    spec: PageSpec,
    selected: Boolean,
    loadThumbnail: suspend (Int) -> Bitmap?,
    revision: Int,
    onClick: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, index, spec, revision) {
        value = runCatching { loadThumbnail(index) }.getOrNull()
    }
    val bitmap = thumbnail

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "페이지 ${index + 1}"
                stateDescription = if (selected) "선택됨" else "선택되지 않음"
            },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    spec.sourcePage == null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "빈 페이지",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    bitmap != null -> {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                        )
                    }

                    else -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                if (spec.extraRotation != 0) {
                    RotationBadge(
                        degrees = spec.extraRotation,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                }
                SelectionBadge(
                    selected = selected,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}쪽",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                if (spec.sourcePage == null) {
                    Text(
                        "빈칸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationBadge(degrees: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.RotateRight,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                "$degrees°",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            )
        }
    }
}

@Composable
private fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(22.dp)
            .semantics {
                stateDescription = if (selected) "선택됨" else "선택되지 않음"
            },
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun OrganizeBottomBar(
    selection: Set<Int>,
    pageCount: Int,
    onSelectionChange: (Set<Int>) -> Unit,
    onRotate: (Set<Int>, Int) -> Unit,
    onDelete: (Set<Int>) -> Unit,
    onDuplicate: (Set<Int>) -> Unit,
    onMove: (Set<Int>, Int) -> Unit,
    onInsertBlank: (Int) -> Unit,
    onExtract: (Set<Int>) -> Unit,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onExport: () -> Unit,
) {
    val hasSelection = selection.isNotEmpty()
    val allSelected = pageCount > 0 && selection.size == pageCount

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val scale = chromeScale(maxWidth.value.roundToInt(), LocalDensity.current.fontScale)
        // Below 400dp six equal actions leave <48dp per target, so wrap to four.
        val actionsPerRow = if (maxWidth < 400.dp) 4 else 6
        val minActionHeight = scale.minRowHeightDp.dp

        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasSelection) {
                        Text(
                            "${selection.size}개 페이지 선택됨",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            "페이지를 골라 정리하세요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    TextButton(
                        onClick = {
                            onSelectionChange(
                                if (allSelected) emptySet() else PageOps.selectAllIndices(pageCount),
                            )
                        },
                        enabled = pageCount > 0,
                        modifier = Modifier.heightIn(min = MIN_TARGET_DP.dp),
                    ) {
                        Text(if (allSelected) "선택 해제" else "전체 선택")
                    }
                }

                val pageActions = listOf(
                    OrganizeActionSpec(Icons.Outlined.RotateLeft, "왼쪽 회전", hasSelection) {
                        onRotate(selection, -90)
                    },
                    OrganizeActionSpec(Icons.Outlined.RotateRight, "오른쪽 회전", hasSelection) {
                        onRotate(selection, 90)
                    },
                    OrganizeActionSpec(Icons.Outlined.ContentCopy, "복제", hasSelection) {
                        onDuplicate(selection)
                    },
                    OrganizeActionSpec(Icons.Outlined.Delete, "삭제", hasSelection) {
                        onDelete(selection)
                    },
                    OrganizeActionSpec(Icons.Outlined.ArrowUpward, "한 칸 앞으로", selection.any { it > 0 }) {
                        onMove(selection, -1)
                    },
                    OrganizeActionSpec(
                        Icons.Outlined.ArrowDownward,
                        "한 칸 뒤로",
                        selection.any { it < pageCount - 1 },
                    ) { onMove(selection, 1) },
                )
                val documentActions = listOf(
                    OrganizeActionSpec(Icons.Outlined.Add, "빈 페이지 추가", hasSelection) {
                        selection.maxOrNull()?.let(onInsertBlank)
                    },
                    OrganizeActionSpec(Icons.Outlined.OpenInNew, "추출", hasSelection) {
                        onExtract(selection)
                    },
                    OrganizeActionSpec(Icons.Outlined.CallMerge, "병합", pageCount > 0, onMerge),
                    OrganizeActionSpec(Icons.Outlined.CallSplit, "분할", pageCount > 1, onSplit),
                    OrganizeActionSpec(Icons.Outlined.FileDownload, "내보내기", pageCount > 0, onExport),
                )

                listOf(pageActions, documentActions).forEachIndexed { group, actions ->
                    if (group > 0) {
                        HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    actions.chunked(actionsPerRow).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { action ->
                                OrganizeAction(
                                    icon = action.icon,
                                    label = action.label,
                                    enabled = action.enabled,
                                    minHeight = minActionHeight,
                                    modifier = Modifier.weight(1f),
                                    onClick = action.onClick,
                                )
                            }
                            repeat(actionsPerRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

private data class OrganizeActionSpec(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun OrganizeAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    minHeight: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }
    Column(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Clip,
        )
    }
}
