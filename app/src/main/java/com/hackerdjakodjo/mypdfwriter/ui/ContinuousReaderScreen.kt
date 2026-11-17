package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * Vertical, read-only PDF page list.
 *
 * [initialPage] and every callback index are zero-based page indices. Callers
 * pass the title, page count, current page and a page renderer; tapping a page
 * is expected to switch to the single-page viewer at the same index.
 *
 * Every visible page is rendered independently through [loadPage] and held only
 * while its lazy item is composed, so leaving this screen releases the page
 * bitmaps. Pages are separated by a small gap; a single tap opens the full
 * editor/viewer while a double tap toggles zoom. Zoom is also available from the
 * compact toolbar controls, and zoomed pages can be panned horizontally without
 * interfering with vertical lazy scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuousReaderScreen(
    title: String,
    pageCount: Int,
    initialPage: Int,
    loadPage: suspend (Int) -> Bitmap?,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    val startIndex = if (pageCount > 0) initialPage.coerceIn(0, pageCount - 1) else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    // The first placement is automatic, but a restored list position should win
    // after a configuration change, so only scroll once per document.
    var initialScrollDone by rememberSaveable(title, startIndex) { mutableStateOf(false) }
    LaunchedEffect(listState, startIndex) {
        if (!initialScrollDone && pageCount > 0) {
            listState.scrollToItem(startIndex)
            initialScrollDone = true
        }
    }

    // Zoom is scoped to this screen and survives rotation; fit-to-width is the
    // default. The stable lambda keeps page tap handlers from being re-registered.
    var zoom by rememberSaveable { mutableFloatStateOf(READER_ZOOM_MIN) }
    val toggleZoom: () -> Unit = remember { { zoom = toggleReaderZoom(zoom) } }

    val currentPage by remember(listState, pageCount) {
        derivedStateOf {
            if (pageCount <= 0) {
                0
            } else {
                val layout = listState.layoutInfo
                val viewportCenter = layout.viewportStartOffset +
                    (layout.viewportEndOffset - layout.viewportStartOffset) / 2
                val centered = layout.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }
                ((centered?.index ?: 0) + 1).coerceIn(1, pageCount)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로",
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            title.ifBlank { "PDF" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "전체 ${pageCount}쪽 · 연속 보기",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { zoom = stepReaderZoom(zoom, -1) },
                            enabled = zoom > READER_ZOOM_MIN,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            readerZoomLabel(zoom),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 44.dp),
                        )
                        IconButton(
                            onClick = { zoom = stepReaderZoom(zoom, 1) },
                            enabled = zoom < READER_ZOOM_MAX,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                        TextButton(
                            onClick = { zoom = READER_ZOOM_MIN },
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("맞춤", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        ) {
            if (pageCount <= 0) {
                EmptyReader(onBack = onBack)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // Leave just enough room under the last page for the floating
                    // current-page indicator; the old 18 dp label + gap made pages
                    // look far apart.
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 8.dp,
                        bottom = 60.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(count = pageCount, key = { index -> index }) { index ->
                        ReaderPageCard(
                            index = index,
                            zoom = zoom,
                            loadPage = loadPage,
                            onOpenPage = onOpenPage,
                            onToggleZoom = toggleZoom,
                        )
                    }
                }

                CurrentPageIndicator(
                    page = currentPage,
                    pageCount = pageCount,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderPageCard(
    index: Int,
    zoom: Float,
    loadPage: suspend (Int) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    onToggleZoom: () -> Unit,
) {
    var attempt by remember(index) { mutableIntStateOf(0) }
    val page by produceState<ReaderPageState>(
        initialValue = ReaderPageState.Loading,
        index,
        attempt,
    ) {
        value = ReaderPageState.Loading
        val bitmap = try {
            loadPage(index)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        value = if (bitmap != null) ReaderPageState.Ready(bitmap) else ReaderPageState.Failed
    }

    val aspectRatio = when (val state = page) {
        is ReaderPageState.Ready -> {
            state.bitmap.width.toFloat() / state.bitmap.height.coerceAtLeast(1).toFloat()
        }

        else -> DEFAULT_PAGE_ASPECT
    }.coerceIn(0.15f, 10f)

    val zoomed = zoom > READER_ZOOM_MIN
    // A failed page keeps its retry control usable instead of also opening the
    // single-page viewer when the retry chip is tapped.
    val canOpenPage = page !is ReaderPageState.Failed

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth * zoom
        val panState = rememberScrollState()
        LaunchedEffect(zoom) { panState.scrollTo(0) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Only horizontal drags are consumed here; vertical drags still
                // reach the parent LazyColumn.
                .then(if (zoomed) Modifier.horizontalScroll(panState) else Modifier)
                .pointerInput(index, canOpenPage, onOpenPage, onToggleZoom) {
                    detectTapGestures(
                        onTap = { if (canOpenPage) onOpenPage(index) },
                        onDoubleTap = { onToggleZoom() },
                    )
                },
            contentAlignment = if (zoomed) Alignment.CenterStart else Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .width(pageWidth)
                    .aspectRatio(aspectRatio),
                shape = RoundedCornerShape(2.dp),
                color = Color.White,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                shadowElevation = 6.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val state = page) {
                        is ReaderPageState.Ready -> {
                            val image = remember(state.bitmap) { state.bitmap.asImageBitmap() }
                            Image(
                                bitmap = image,
                                contentDescription = "${index + 1}쪽 페이지",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        ReaderPageState.Loading -> LoadingPage()
                        ReaderPageState.Failed -> FailedPage(onRetry = { attempt++ })
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingPage() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF3F5F7F),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "페이지를 불러오는 중…",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7075),
        )
    }
}

@Composable
private fun FailedPage(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = Color(0xFFB3261E),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "페이지를 불러오지 못했습니다",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF3C4043),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            color = Color(0xFFE8EEF5),
            contentColor = Color(0xFF1F4E79),
        ) {
            Text(
                "다시 시도",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CurrentPageIndicator(
    page: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
    ) {
        Text(
            "$page / ${pageCount}쪽",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyReader(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "읽을 페이지가 없습니다",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "PDF를 다시 열거나 페이지를 추가한 뒤 이용해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("돌아가기")
        }
    }
}

private sealed interface ReaderPageState {
    data object Loading : ReaderPageState
    data class Ready(val bitmap: Bitmap) : ReaderPageState
    data object Failed : ReaderPageState
}

private const val DEFAULT_PAGE_ASPECT = 0.707f
