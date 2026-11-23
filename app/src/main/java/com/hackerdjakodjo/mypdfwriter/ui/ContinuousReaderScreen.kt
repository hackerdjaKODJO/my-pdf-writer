package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import com.hackerdjakodjo.mypdfwriter.BuildConfig
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.CropPixelRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfContinuousTrace
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
    /** Screen dimming factor in 0.4..1.0; 1f draws no scrim. */
    brightness: Float = 1f,
    /**
     * View-only crop margins in normalized page fractions. The reader draws
     * only the kept sub-rectangle of every rendered bitmap; the original page,
     * file and exported PDF stay untouched. Defaults keep the uncropped path.
     */
    cropInsets: CropInsets = CropInsets.NONE,
    /** Mirror left/right margins on even (1-based) pages. */
    cropOddEven: Boolean = false,
    loadPage: suspend (Int, Float) -> Bitmap?,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    /** 0-based page selected by a horizontal swipe at fit zoom. */
    onPageChange: (Int) -> Unit = {},
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
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val scope = rememberCoroutineScope()
    var showPageJump by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val viewportWidthPx = listState.layoutInfo.viewportSize.width.toFloat()
    val setZoom: (Float, Float, String) -> Unit = { value, focalX, reason ->
        val zoomBefore = latestZoom.value
        val zoomAfter = clampReaderZoom(value)
        val width = listState.layoutInfo.viewportSize.width.toFloat()
        val panBefore = panX.floatValue
        // Re-anchor the pan around the zoom focal point. Previously zoom always
        // left panX = 0 with the enlarged page pinned to the viewport's left
        // edge, so a rightward drag became a negative pan and clamped back to
        // 0: the page could not be moved right at all.
        panX.floatValue = readerPanWithHeadroom(
            panBefore, zoomBefore, zoomAfter, focalX, width,
        )
        zoom = zoomAfter
        PdfContinuousTrace.zoomPan(
            reason, zoomBefore, zoomAfter, panBefore, panX.floatValue,
            readerMaxPanPx(width, zoomAfter), focalX,
        )
    }
    val toggleZoom: (Float) -> Unit = { focalX ->
        setZoom(toggleReaderZoom(latestZoom.value), focalX, "toggle")
    }
    // Zoom survives recreation but pan does not. Seed a centered pan once the
    // viewport is known, then only re-clamp when the viewport shrinks.
    val restorePanSeeded = remember { mutableStateOf(false) }
    LaunchedEffect(viewportWidthPx) {
        if (viewportWidthPx <= 0f) return@LaunchedEffect
        val currentZoom = latestZoom.value
        panX.floatValue = clampReaderPan(panX.floatValue, currentZoom, viewportWidthPx)
        if (!restorePanSeeded.value) {
            restorePanSeeded.value = true
            if (currentZoom > READER_ZOOM_MIN) {
                val seeded = panAfterZoom(
                    0f, READER_ZOOM_MIN, currentZoom,
                    viewportWidthPx / 2f, viewportWidthPx,
                )
                panX.floatValue = seeded
                PdfContinuousTrace.zoomPan(
                    "restore", READER_ZOOM_MIN, currentZoom, 0f, seeded,
                    readerMaxPanPx(viewportWidthPx, currentZoom), viewportWidthPx / 2f,
                )
            }
        }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        ) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
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
                                    // Remember each down pointer's x even on frames
                                    // where only the other finger moved, so the zoom
                                    // focal stays at the midpoint of the two fingers
                                    // instead of jumping to whichever changed.
                                    val pressedX = HashMap<Long, Float>()
                                    var lastTracedRenderZoom = readerRenderZoom(latestZoom.value)
                                    var pinchWasActive = false
                                    var panPointerId = -1L
                                    var panTotalX = 0f
                                    var panStarted = false
                                    // Fit-zoom page swipe: observe only, never consume.
                                    var swipePointerId = -1L
                                    var swipeTotalX = 0f
                                    var swipeTotalY = 0f
                                    var swipeDelivered = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        event.changes.forEach { change ->
                                            val id = change.id.value
                                            if (change.pressed) {
                                                pressedX[id] = change.position.x
                                                if (seen.add(id)) {
                                                    gate.onDown(id)
                                                    if (seen.size == 1) {
                                                        suppressPageTap.value = false
                                                    }
                                                }
                                            } else if (seen.remove(id)) {
                                                pressedX.remove(id)
                                                gate.onUp(id)
                                            }
                                        }

                                        if (gate.twoFinger) {
                                            pinchActive = true
                                            if (!pinchWasActive) {
                                                pinchWasActive = true
                                                // A second finger invalidates any one-finger
                                                // pan tracking; a retained finger must start
                                                // a fresh drag once the pinch is over.
                                                panPointerId = -1L
                                                panTotalX = 0f
                                                panStarted = false
                                                swipeDelivered = false
                                                swipeTotalX = 0f
                                                swipeTotalY = 0f
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
                                                val pressedXs = pressedX.values.toList()
                                                val focalX = when (pressedXs.size) {
                                                    0 -> Float.NaN
                                                    1 -> pressedXs[0]
                                                    else -> (pressedXs[0] + pressedXs[1]) / 2f
                                                }
                                                val nextZoom = gatedPinchZoom(
                                                    latestZoom.value,
                                                    zoomChange,
                                                    true,
                                                )
                                                setZoom(nextZoom, focalX, "pinch")
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
                                            if (pinchWasActive) {
                                                // The pinch ended (one finger lifted). Do
                                                // not keep pan disabled just because a
                                                // pointer from the pinch is still down:
                                                // start a clean pan with the retained finger.
                                                panPointerId = -1L
                                                panTotalX = 0f
                                                panStarted = false
                                            }
                                            pinchWasActive = false
                                            pinchActive = false

                                            // One-finger horizontal pan is observed but
                                            // not consumed. Vertical movement continues to
                                            // the LazyColumn, so diagonal drags work.
                                            if (latestZoom.value > READER_ZOOM_MIN) {
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
                                                        val panWidth = listState.layoutInfo
                                                            .viewportSize.width.toFloat()
                                                        val maxPan = readerMaxPanPx(
                                                            panWidth,
                                                            latestZoom.value,
                                                        )
                                                        if (!panStarted &&
                                                            abs(panTotalX) > touchSlop
                                                        ) {
                                                            panStarted = true
                                                            suppressPageTap.value = true
                                                            PdfContinuousTrace.pan(
                                                                "start", id, deltaX, panTotalX,
                                                                panX.floatValue, panX.floatValue,
                                                                latestZoom.value, maxPan,
                                                            )
                                                        }
                                                        if (panStarted) {
                                                            val panBefore = panX.floatValue
                                                            panX.floatValue = panReaderAfterDrag(
                                                                panBefore,
                                                                deltaX,
                                                                latestZoom.value,
                                                                panWidth,
                                                            )
                                                            PdfContinuousTrace.pan(
                                                                "move", id, deltaX, panTotalX,
                                                                panBefore, panX.floatValue,
                                                                latestZoom.value, maxPan,
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                // At fit zoom a horizontal swipe turns
                                                // the page. Observe only: the LazyColumn
                                                // keeps its native vertical fling.
                                                val change = event.changes.firstOrNull {
                                                    it.pressed
                                                }
                                                if (change != null) {
                                                    val id = change.id.value
                                                    if (swipePointerId != id) {
                                                        swipePointerId = id
                                                        swipeTotalX = 0f
                                                        swipeTotalY = 0f
                                                        swipeDelivered = false
                                                    }
                                                    swipeTotalX += change.position.x -
                                                        change.previousPosition.x
                                                    swipeTotalY += change.position.y -
                                                        change.previousPosition.y
                                                    if (!swipeDelivered &&
                                                        abs(swipeTotalX) > touchSlop &&
                                                        abs(swipeTotalX) > abs(swipeTotalY) *
                                                            PageSwipe.AXIS_DOMINANCE
                                                    ) {
                                                        swipeDelivered = true
                                                        // Prevent the page tap detector
                                                        // from opening the single-page view.
                                                        suppressPageTap.value = true
                                                    }
                                                }
                                            }
                                        }

                                        if (event.changes.none { it.pressed }) {
                                            if (swipeDelivered) {
                                                val width = listState.layoutInfo
                                                    .viewportSize.width.toFloat()
                                                if (abs(swipeTotalX) >=
                                                    PageSwipe.thresholdPx(width, density)
                                                ) {
                                                    val dir = PageSwipe.direction(
                                                        swipeTotalX, swipeTotalY, touchSlop,
                                                    )
                                                    val current0 = currentPage - 1
                                                    val target = PageSwipe.target(
                                                        current0, dir, pageCount,
                                                    )
                                                    if (target != current0) {
                                                        PdfContinuousTrace.pageSwipe(
                                                            current0, target, dir,
                                                            swipeTotalX, swipeTotalY,
                                                        )
                                                        currentOnPageChange(target)
                                                    }
                                                }
                                            }
                                            if (panStarted) {
                                                PdfContinuousTrace.pan(
                                                    "end", panPointerId, 0f, panTotalX,
                                                    panX.floatValue, panX.floatValue,
                                                    latestZoom.value,
                                                    readerMaxPanPx(
                                                        listState.layoutInfo.viewportSize.width
                                                            .toFloat(),
                                                        latestZoom.value,
                                                    ),
                                                )
                                            }
                                            pressedX.clear()
                                            gate.clear()
                                            seen.clear()
                                            pinchActive = false
                                            pinchWasActive = false
                                            panPointerId = -1L
                                            panTotalX = 0f
                                            panStarted = false
                                            swipePointerId = -1L
                                            swipeTotalX = 0f
                                            swipeTotalY = 0f
                                            swipeDelivered = false
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
                            // The page bar is a layout row below the list, so the
                            // last page no longer needs room for a floating label.
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            items(count = pageCount, key = { index -> index }) { index ->
                                ReaderPageCard(
                                    index = index,
                                    zoom = zoom,
                                    panX = { panX.floatValue },
                                    cropInsets = cropInsets,
                                    cropOddEven = cropOddEven,
                                    loadPage = loadPage,
                                    onOpenPage = onOpenPage,
                                    onToggleZoom = toggleZoom,
                                    suppressTap = { suppressPageTap.value },
                                    onRendered = { lastRender = it },
                                )
                            }
                        }
                    }

                    // Non-interactive brightness scrim over the pages only; the
                    // current-page indicator and debug HUD below stay fully readable.
                    val dimAlpha = 1f - brightness.coerceIn(0.4f, 1f)
                    if (dimAlpha > 0f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dimAlpha)),
                        )
                    }

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
                                Text(
                                    "DEBUG pan=${panX.floatValue.roundToInt()}/" +
                                        "${readerMaxPanPx(viewportWidthPx, zoom).roundToInt()} " +
                                        "view=${viewportWidthPx.roundToInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            // Page navigation is docked below the page list, never over it.
            // ‹/› scroll one page; the counter opens a numeric page jump.
            if (pageCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                ) {
                    ViewerBottomPageBar(
                        pageIndex = currentPage - 1,
                        pageCount = pageCount,
                        onPrevious = {
                            val target = continuousReaderScrollTarget(currentPage, -1, pageCount)
                            scope.launch { listState.animateScrollToItem(target) }
                        },
                        onNext = {
                            val target = continuousReaderScrollTarget(currentPage, 1, pageCount)
                            scope.launch { listState.animateScrollToItem(target) }
                        },
                        onOpenPages = { showPageJump = true },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
    }

    if (showPageJump) {
        ContinuousPageJumpDialog(
            currentPage = currentPage,
            pageCount = pageCount,
            onDismiss = { showPageJump = false },
            onJump = { index ->
                showPageJump = false
                scope.launch { listState.animateScrollToItem(index) }
            },
        )
    }
}

/**
 * Zero-based scroll target for the bottom bar's ‹/› buttons, from the 1-based
 * [currentPage] the reader derives from the visible list. Clamped at both ends
 * so the first and last page never scroll out of the document.
 */
internal fun continuousReaderScrollTarget(currentPage: Int, direction: Int, pageCount: Int): Int {
    if (pageCount <= 0) return 0
    val current = currentPage.coerceIn(1, pageCount)
    return (current - 1 + direction).coerceIn(0, pageCount - 1)
}

/** Numeric page jump for the continuous reader, sharing [parsePageJump]. */
@Composable
private fun ContinuousPageJumpDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val target = parsePageJump(input, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("페이지 이동") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "현재 ${currentPage}쪽 · 1–$pageCount 쪽으로 이동합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("페이지 번호") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = input.isNotEmpty() && target == null,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { target?.let(onJump) },
                enabled = target != null,
            ) { Text("이동") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun ReaderPageCard(
    index: Int,
    zoom: Float,
    panX: () -> Float,
    cropInsets: CropInsets,
    cropOddEven: Boolean,
    loadPage: suspend (Int, Float) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    onToggleZoom: (Float) -> Unit,
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

    // Crop is view-only: pick the sub-rectangle of the already-rendered bitmap
    // that corresponds to this page's margins, and size the card to it. Odd/even
    // mirroring uses the same pure policy as the single-page canvas.
    val visible = PdfCrop.visibleRect(PdfCrop.cropForPage(cropInsets, index, cropOddEven))
    val cropSrc: (Bitmap) -> CropPixelRect = { bitmap ->
        PdfCrop.cropPixelRect(visible, bitmap.width, bitmap.height)
    }
    val aspectRatio = when (val state = page) {
        is ReaderPageState.Ready -> {
            val src = cropSrc(state.bitmap)
            src.width.toFloat() / src.height.coerceAtLeast(1).toFloat()
        }

        else -> DEFAULT_PAGE_ASPECT
    }.coerceIn(0.15f, 10f)

    // A failed page keeps its retry control usable instead of also opening the
    // single-page viewer when the retry chip is tapped.
    val canOpenPage = page !is ReaderPageState.Failed
    val currentSuppressTap by rememberUpdatedState(suppressTap)
    val currentOnToggleZoom by rememberUpdatedState(onToggleZoom)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        val pageWidth = maxWidth * zoom
        val maxPan = with(LocalDensity.current) {
            readerMaxPanPx(maxWidth.toPx(), zoom)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(index, canOpenPage, onOpenPage) {
                    detectTapGestures(
                        onTap = {
                            if (canOpenPage && !currentSuppressTap()) onOpenPage(index)
                        },
                        // The detector sits on the full-width, unshifted parent
                        // Box, so the tap offset is already in viewport x: the
                        // same space panX and pinch focal points use.
                        onDoubleTap = { offset -> currentOnToggleZoom(offset.x) },
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
                            val bitmap = state.bitmap
                            val src = cropSrc(bitmap)
                            Canvas(
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = "${index + 1}쪽 페이지",
                            ) {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawBitmap(
                                        bitmap,
                                        Rect(
                                            src.left,
                                            src.top,
                                            src.left + src.width,
                                            src.top + src.height,
                                        ),
                                        Rect(
                                            0,
                                            0,
                                            size.width.toInt().coerceAtLeast(1),
                                            size.height.toInt().coerceAtLeast(1),
                                        ),
                                        Paint(Paint.FILTER_BITMAP_FLAG),
                                    )
                                }
                            }
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
