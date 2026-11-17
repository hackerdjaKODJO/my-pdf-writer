package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.util.Log
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
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
 * editor/viewer while a double tap toggles zoom. Pinch zoom and horizontal
 * dragging apply to the entire continuous list, not just one page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuousReaderScreen(
    title: String,
    pageCount: Int,
    initialPage: Int,
    loadPage: suspend (Int, Float) -> Bitmap?,
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

    // Zoom and horizontal pan are screen-wide: every page uses the same values,
    // so a pinch on one page enlarges the whole continuous document.
    var zoom by rememberSaveable { mutableFloatStateOf(READER_ZOOM_MIN) }
    var panX by remember { mutableFloatStateOf(0f) }
    val latestZoom = rememberUpdatedState(zoom)
    val setZoom: (Float) -> Unit = { value ->
        zoom = clampReaderZoom(value)
        if (zoom <= READER_ZOOM_MIN) panX = 0f
    }
    val toggleZoom: () -> Unit = { setZoom(toggleReaderZoom(latestZoom.value)) }

    // Pure tracker that owns all pinch-distance math. This screen only feeds it
    // pointer ids/positions and applies the resulting document-wide zoom.
    val pinchTracker = remember { ReaderPinchTracker() }

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
                            "전체 ${pageCount}쪽 · 연속 보기" +
                                if (zoom > READER_ZOOM_MIN) " · ${readerZoomLabel(zoom)}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                // The pinch detector lives on a Box that wraps ONLY the page list,
                // not the whole screen or the page indicator. The LazyColumn keeps
                // its own one-finger vertical scrolling; this handler only steps in
                // once a second tracked pointer joins the gesture.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // Ids already fed to the tracker during the current
                            // gesture. Local to this input handler so a fresh
                            // gesture starts clean and recomposition cannot feed
                            // the same id twice.
                            val seenIds = HashSet<Int>()
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                if (seenIds.add(firstDown.id.value.toInt())) {
                                    pinchTracker.onDown(firstDown.id.value.toInt(), firstDown.position)
                                }
                                var startZoom = READER_ZOOM_MIN
                                var baselineCaptured = false
                                var firstFrame = true
                                var lastLoggedRenderZoom = readerRenderZoom(latestZoom.value)
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val changes = event.changes

                                        // onDown only for brand-new pointers. On the
                                        // first frame every pressed pointer is new: a
                                        // second finger can share the down event that
                                        // awaitFirstDown consumed, so its down flag is
                                        // already gone by the time we see it here.
                                        changes.forEach { change ->
                                            val isNew = if (firstFrame) {
                                                change.pressed && seenIds.add(change.id.value.toInt())
                                            } else {
                                                change.pressed &&
                                                    change.changedToDownIgnoreConsumed() &&
                                                    seenIds.add(change.id.value.toInt())
                                            }
                                            if (isNew) {
                                                pinchTracker.onDown(change.id.value.toInt(), change.position)
                                            }
                                        }
                                        firstFrame = false
                                        // Moves and lifts are meaningful only for
                                        // pointers the tracker actually knows.
                                        changes.forEach { change ->
                                            if (change.pressed && change.id.value.toInt() in seenIds) {
                                                pinchTracker.onMove(change.id.value.toInt(), change.position)
                                            }
                                        }
                                        changes.forEach { change ->
                                            if (!change.pressed && change.id.value.toInt() in seenIds) {
                                                pinchTracker.onUp(change.id.value.toInt())
                                                seenIds.remove(change.id.value.toInt())
                                            }
                                        }
                                        // A lifted finger resets the tracker; the next
                                        // two-finger contact needs a fresh zoom baseline.
                                        if (!pinchTracker.active) baselineCaptured = false

                                        if (pinchTracker.active) {
                                            // Freeze the document-wide zoom exactly
                                            // once, when the pinch first activates.
                                            if (!baselineCaptured) {
                                                startZoom = latestZoom.value
                                                baselineCaptured = true
                                            }
                                            val nextZoom = zoomFromPinch(
                                                startZoom,
                                                pinchTracker.startDistance,
                                                pinchTracker.currentDistance,
                                            )
                                            setZoom(nextZoom)
                                            val nextRenderZoom = readerRenderZoom(nextZoom)
                                            if (BuildConfig.DEBUG && nextRenderZoom != lastLoggedRenderZoom) {
                                                lastLoggedRenderZoom = nextRenderZoom
                                                Log.d(
                                                    "PdfContinuousPinch",
                                                    "zoom=${readerZoomLabel(nextZoom)} " +
                                                        "renderZoom=${readerZoomLabel(nextRenderZoom)} " +
                                                        "dist=${pinchTracker.currentDistance}",
                                                )
                                            }
                                            // Two-finger events only. A single finger
                                            // must stay unconsumed or vertical scroll
                                            // in the LazyColumn stops working.
                                            changes.forEach { change ->
                                                if (change.pressed && change.id.value.toInt() in seenIds) {
                                                    change.consume()
                                                }
                                            }
                                        }

                                        if (changes.none { it.pressed }) break
                                    }
                                } finally {
                                    pinchTracker.clear()
                                    seenIds.clear()
                                }
                            }
                        },
                ) {
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
                                panX = panX,
                                loadPage = loadPage,
                                onOpenPage = onOpenPage,
                                onToggleZoom = toggleZoom,
                                onPanChange = { panX = it },
                            )
                        }
                    }
                }

                CurrentPageIndicator(
                    page = currentPage,
                    pageCount = pageCount,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )

                if (BuildConfig.DEBUG) {
                    val renderZoom = readerRenderZoom(zoom)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 10.dp, top = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Text(
                            "DEBUG zoom=${readerZoomLabel(zoom)} " +
                                "render=${readerZoomLabel(renderZoom)} " +
                                "scale=${"%.2f".format(readerRenderScale(renderZoom))}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPageCard(
    index: Int,
    zoom: Float,
    panX: Float,
    loadPage: suspend (Int, Float) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    onPanChange: (Float) -> Unit,
) {
    var attempt by remember(index) { mutableIntStateOf(0) }
    val renderZoom = readerRenderZoom(zoom)
    val renderScale = readerRenderScale(renderZoom)
    val page by produceState<ReaderPageState>(
        initialValue = ReaderPageState.Loading,
        index,
        attempt,
        renderZoom,
    ) {
        value = ReaderPageState.Loading
        val bitmap = try {
            loadPage(index, renderZoom)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (BuildConfig.DEBUG && bitmap != null) {
            Log.d(
                "PdfContinuousRender",
                "page=$index zoom=${readerZoomLabel(zoom)} " +
                    "renderZoom=${readerZoomLabel(renderZoom)} scale=${renderScale}x " +
                    "bitmap=${bitmap.width}x${bitmap.height}",
            )
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
    val latestPan by rememberUpdatedState(panX)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        val pageWidth = maxWidth * zoom
        val maxPan = with(LocalDensity.current) {
            (pageWidth - maxWidth).toPx().coerceAtLeast(0f)
        }
        val currentPan = panX.coerceIn(0f, maxPan)
        val panOffset = with(LocalDensity.current) { currentPan.toDp() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (zoomed) {
                        Modifier.pointerInput(index, maxPan, onPanChange) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                onPanChange((latestPan - dragAmount).coerceIn(0f, maxPan))
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .pointerInput(index, canOpenPage, onOpenPage, onToggleZoom) {
                    detectTapGestures(
                        onTap = { if (canOpenPage) onOpenPage(index) },
                        onDoubleTap = { onToggleZoom() },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(
                modifier = Modifier
                    .width(pageWidth)
                    .offset(x = -panOffset)
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
