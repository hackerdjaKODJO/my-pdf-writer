package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import com.hackerdjakodjo.mypdfwriter.pdf.PdfContinuousTrace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // A state object, not a composable Float: page offsets read it inside
    // Modifier.offset { }, so dragging does not recompose every visible page.
    val panX = remember { mutableFloatStateOf(0f) }
    val latestZoom = rememberUpdatedState(zoom)
    val setZoom: (Float) -> Unit = { value ->
        zoom = clampReaderZoom(value)
        if (zoom <= READER_ZOOM_MIN) panX.floatValue = 0f
    }
    val toggleZoom: () -> Unit = { setZoom(toggleReaderZoom(latestZoom.value)) }

    // True only while the companion observer (below) sees 2+ pointers down.
    // detectTransformGestures also fires for one-finger drags, so this gate is
    // what keeps zoom two-finger-only. It also suspends the LazyColumn scroller
    // and the per-page horizontal-pan detectors for the duration of a pinch so
    // they cannot consume the first pointer away from the transform detector.
    var pinchActive by remember { mutableStateOf(false) }

    // Set for the duration of a one-finger pan. The page tap detector reads it
    // at tap time so a horizontal pan cannot also open the page. Reset on the
    // next distinct pointer-down; this avoids consuming the up event and keeps
    // the LazyColumn's native vertical fling intact on diagonal drags.
    val suppressPageTap = remember { mutableStateOf(false) }

    // Debug-only observability: `lastRender` is written from the render
    // coroutine with the *real* bitmap size, so the HUD can tell a computed
    // scale from a page that was really re-rendered at that scale.
    var lastRender by remember { mutableStateOf<ReaderRenderInfo?>(null) }

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
                // One raw Initial-pass observer owns the document-wide
                // pinch zoom and one-finger horizontal pan. It deliberately
                // never consumes single-finger events: the LazyColumn keeps
                // its native vertical scroll/fling, and a diagonal drag can
                // pan horizontally while the list scrolls vertically.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitPointerEventScope {
                                val gate = ContinuousPinchGate()
                                val seen = HashSet<Long>()
                                var lastTracedRenderZoom = readerRenderZoom(latestZoom.value)
                                var pinchWasActive = false
                                var multiTouchGesture = false
                                var panPointerId = -1L
                                var panTotalX = 0f
                                var panStarted = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.forEach { change ->
                                        val id = change.id.value
                                        if (change.pressed) {
                                            if (seen.add(id)) {
                                                gate.onDown(id)
                                                if (seen.size == 1) {
                                                    suppressPageTap.value = false
                                                }
                                            }
                                        } else if (seen.remove(id)) {
                                            gate.onUp(id)
                                        }
                                    }

                                    if (gate.twoFinger) {
                                        multiTouchGesture = true
                                        pinchActive = true
                                        if (!pinchWasActive) {
                                            pinchWasActive = true
                                            lastTracedRenderZoom =
                                                readerRenderZoom(latestZoom.value)
                                            PdfContinuousTrace.pinch(
                                                latestZoom.value,
                                                lastTracedRenderZoom,
                                                event.calculateZoom(),
                                            )
                                        }
                                        val zoomChange = event.calculateZoom()
                                        if (zoomChange.isFinite() && zoomChange > 0f &&
                                            zoomChange != 1f
                                        ) {
                                            val nextZoom = gatedPinchZoom(
                                                latestZoom.value,
                                                zoomChange,
                                                true,
                                            )
                                            setZoom(nextZoom)
                                            val nextRenderZoom = readerRenderZoom(nextZoom)
                                            if (nextRenderZoom != lastTracedRenderZoom) {
                                                lastTracedRenderZoom = nextRenderZoom
                                                PdfContinuousTrace.pinch(
                                                    nextZoom,
                                                    nextRenderZoom,
                                                    zoomChange,
                                                )
                                            }
                                        }
                                        // Consume two-finger events only. This tells
                                        // the LazyColumn scroller and page drag/tap
                                        // detectors to cancel without blocking the
                                        // normal one-finger scroll path.
                                        event.changes.forEach { change ->
                                            if (change.pressed) change.consume()
                                        }
                                    } else {
                                        pinchWasActive = false
                                        pinchActive = false

                                        // One-finger horizontal pan is observed but
                                        // not consumed. Vertical movement continues to
                                        // the LazyColumn, so diagonal drags work.
                                        if (!multiTouchGesture && latestZoom.value >
                                            READER_ZOOM_MIN
                                        ) {
                                            val change = event.changes.firstOrNull {
                                                it.pressed
                                            }
                                            if (change != null) {
                                                val id = change.id.value
                                                if (panPointerId != id) {
                                                    panPointerId = id
                                                    panTotalX = 0f
                                                    panStarted = false
                                                }
                                                val deltaX = change.position.x -
                                                    change.previousPosition.x
                                                if (deltaX != 0f) {
                                                    panTotalX += deltaX
                                                    if (!panStarted &&
                                                        abs(panTotalX) > touchSlop
                                                    ) {
                                                        panStarted = true
                                                        suppressPageTap.value = true
                                                    }
                                                    if (panStarted) {
                                                        val viewportWidth =
                                                            listState.layoutInfo
                                                                .viewportSize.width
                                                                .toFloat()
                                                        val basePageWidth =
                                                            viewportWidth.coerceAtLeast(1f)
                                                        val maxPan = basePageWidth *
                                                            (latestZoom.value -
                                                                READER_ZOOM_MIN)
                                                        panX.floatValue =
                                                            (panX.floatValue - deltaX)
                                                                .coerceIn(0f, maxPan)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        gate.clear()
                                        seen.clear()
                                        pinchActive = false
                                        pinchWasActive = false
                                        multiTouchGesture = false
                                        panPointerId = -1L
                                        panTotalX = 0f
                                        panStarted = false
                                    }
                                }
                            }
                        },
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // The scroller stays enabled: the Initial-pass pinch observer
                        // consumes only two-finger events, so one-finger vertical
                        // scrolling keeps its native fling behavior.
                        // Leave just enough room under the last page for the floating
                        // current-page indicator; the old 18 dp label + gap made pages
                        // look far apart.
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 60.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(count = pageCount, key = { index -> index }) { index ->
                            ReaderPageCard(
                                index = index,
                                zoom = zoom,
                                panX = { panX.floatValue },
                                loadPage = loadPage,
                                onOpenPage = onOpenPage,
                                onToggleZoom = toggleZoom,
                                suppressTap = { suppressPageTap.value },
                                onRendered = { lastRender = it },
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
                    val rendered = lastRender
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 10.dp, top = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                "DEBUG zoom=${readerZoomLabel(zoom)} " +
                                    "render=${readerZoomLabel(renderZoom)} " +
                                    "scale=${"%.2f".format(readerRenderScale(renderZoom))} " +
                                    "pinch=$pinchActive",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                if (rendered == null) {
                                    "bitmap: —"
                                } else {
                                    "bitmap p${rendered.page + 1} " +
                                        "rz=${readerZoomLabel(rendered.renderZoom)} " +
                                        "scale=${"%.2f".format(rendered.scale)} " +
                                        "${rendered.width}x${rendered.height}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
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
    panX: () -> Float,
    loadPage: suspend (Int, Float) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    onToggleZoom: () -> Unit,
    suppressTap: () -> Boolean,
    onRendered: (ReaderRenderInfo) -> Unit,
) {
    var attempt by remember(index) { mutableIntStateOf(0) }
    val renderZoom = readerRenderZoom(zoom)
    val renderScale = readerRenderScale(renderZoom)
    // The loader is a fresh lambda on every parent recomposition. Keep it in an
    // updated state so the render coroutine always calls the current one, and
    // key the render on both the zoom bucket and the derived scale so a change
    // to either can never leave an old bitmap on screen.
    val currentLoadPage by rememberUpdatedState(loadPage)
    val currentOnRendered by rememberUpdatedState(onRendered)
    val page by produceState<ReaderPageState>(
        initialValue = ReaderPageState.Loading,
        index,
        attempt,
        renderZoom,
        renderScale,
    ) {
        // Keep the previous Ready bitmap visible while a new render-scale
        // bitmap is produced. Resetting to Loading here made every zoom step
        // flash a blank/loading page.
        if (value !is ReaderPageState.Ready) value = ReaderPageState.Loading
        val bitmap = try {
            currentLoadPage(index, renderZoom)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (bitmap != null) {
            PdfContinuousTrace.render(
                page = index,
                zoom = zoom,
                renderZoom = renderZoom,
                scale = renderScale,
                bitmapWidth = bitmap.width,
                bitmapHeight = bitmap.height,
            )
            currentOnRendered(
                ReaderRenderInfo(index, renderZoom, renderScale, bitmap.width, bitmap.height),
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

    // A failed page keeps its retry control usable instead of also opening the
    // single-page viewer when the retry chip is tapped.
    val canOpenPage = page !is ReaderPageState.Failed
    val currentSuppressTap by rememberUpdatedState(suppressTap)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        val pageWidth = maxWidth * zoom
        val maxPan = with(LocalDensity.current) {
            (pageWidth - maxWidth).toPx().coerceAtLeast(0f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(index, canOpenPage, onOpenPage, onToggleZoom) {
                    detectTapGestures(
                        onTap = {
                            if (canOpenPage && !currentSuppressTap()) onOpenPage(index)
                        },
                        onDoubleTap = { onToggleZoom() },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(
                // requiredWidth, not width: width is coerced to the parent's
                // max width, which made zoom change the percentage and bitmap
                // while the page stayed fit-to-width. requiredWidth lets the
                // page measure past the viewport; the outer box clips it and
                // the shared pan offset reveals the rest.
                modifier = Modifier
                    .requiredWidth(pageWidth)
                    // Read pan state during layout, not composition: every
                    // pan frame moves all visible pages without recomposing
                    // their bitmaps or semantics.
                    .offset {
                        IntOffset(
                            -panX().coerceIn(0f, maxPan).roundToInt(),
                            0,
                        )
                    }
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

/** Last successful page render, surfaced in the debug HUD. */
private data class ReaderRenderInfo(
    val page: Int,
    val renderZoom: Float,
    val scale: Float,
    val width: Int,
    val height: Int,
)

private const val DEFAULT_PAGE_ASPECT = 0.707f
