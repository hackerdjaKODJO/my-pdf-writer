package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerdjakodjo.mypdfwriter.data.PageOps
import com.hackerdjakodjo.mypdfwriter.data.PageSpec

/**
 * Thumbnail grid for reordering and editing the working page list.
 *
 * The screen is deliberately stateless: selection and every mutation are
 * delegated to the caller so it can be backed by [PdfViewModel] (or any other
 * page store) without touching the viewer implementation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    docName: String,
    pages: List<PageSpec>,
    selected: Set<Int>,
    loadThumbnail: suspend (Int) -> Bitmap?,
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
) {
    val selection = selected.filter { it in pages.indices }.toSet()
    val allSelected = pages.isNotEmpty() && selection.size == pages.size

    BackHandler(onBack = onBack)

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
                            docName.ifBlank { "문서" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${pages.size}쪽",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            onSelectionChange(
                                if (allSelected) emptySet() else PageOps.selectAllIndices(pages.size),
                            )
                        },
                        enabled = pages.isNotEmpty(),
                    ) {
                        Text(if (allSelected) "선택 해제" else "전체 선택")
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
                onRotate = onRotate,
                onDelete = onDelete,
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
    onClick: () -> Unit,
) {
    val thumbnail by produceState<Bitmap?>(initialValue = null, index, spec) {
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
        modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "페이지 ${index + 1} 미리보기",
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
        modifier = modifier.size(22.dp),
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
                    contentDescription = "선택됨",
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
                        "${selection.size}쪽 선택됨",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "페이지를 골라 정리하세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            Row(Modifier.fillMaxWidth()) {
                OrganizeAction(
                    icon = Icons.Outlined.RotateLeft,
                    label = "왼쪽",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { onRotate(selection, -90) }
                OrganizeAction(
                    icon = Icons.Outlined.RotateRight,
                    label = "오른쪽",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { onRotate(selection, 90) }
                OrganizeAction(
                    icon = Icons.Outlined.ContentCopy,
                    label = "복제",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { onDuplicate(selection) }
                OrganizeAction(
                    icon = Icons.Outlined.Delete,
                    label = "삭제",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { onDelete(selection) }
                OrganizeAction(
                    icon = Icons.Outlined.ArrowUpward,
                    label = "앞으로",
                    enabled = selection.any { it > 0 },
                    modifier = Modifier.weight(1f),
                ) { onMove(selection, -1) }
                OrganizeAction(
                    icon = Icons.Outlined.ArrowDownward,
                    label = "뒤로",
                    enabled = selection.any { it < pageCount - 1 },
                    modifier = Modifier.weight(1f),
                ) { onMove(selection, 1) }
            }

            HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

            Row(Modifier.fillMaxWidth()) {
                OrganizeAction(
                    icon = Icons.Outlined.Add,
                    label = "빈 페이지",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { selection.maxOrNull()?.let(onInsertBlank) }
                OrganizeAction(
                    icon = Icons.Outlined.OpenInNew,
                    label = "추출",
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f),
                ) { onExtract(selection) }
                OrganizeAction(
                    icon = Icons.Outlined.CallMerge,
                    label = "병합",
                    enabled = pageCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onMerge,
                )
                OrganizeAction(
                    icon = Icons.Outlined.CallSplit,
                    label = "분할",
                    enabled = pageCount > 1,
                    modifier = Modifier.weight(1f),
                    onClick = onSplit,
                )
                OrganizeAction(
                    icon = Icons.Outlined.FileDownload,
                    label = "내보내기",
                    enabled = pageCount > 0,
                    modifier = Modifier.weight(1f),
                    onClick = onExport,
                )
            }
        }
    }
}

@Composable
private fun OrganizeAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
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
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
