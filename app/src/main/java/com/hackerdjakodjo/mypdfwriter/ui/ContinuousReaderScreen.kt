package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
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
    // Zoom for the pointer observer is read via `gestureZoom` (synchronous).
    // rememberUpdatedState lags one frame behind the very zoom change that a
    // follow-up drag must react to, so it must NOT be used for gesture routing.
    val gestureZoom = remember { mutableFloatStateOf(zoom) }
    val currentOnPageChange by rememberUpdatedState(onPageChange)
    val scope = rememberCoroutineScope()
    var showPageJump by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density
    val viewportWidthPx = listState.layoutInfo.viewportSize.width.toFloat()
    val setZoom: (Float, Float, String) -> Unit = { value, focalX, reason ->
        val zoomBefore = gestureZoom.floatValue
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
        // Synchronous snapshot for the pointer observer, written BEFORE the
        // composable state so the very next pointer frame sees the new mode.
        gestureZoom.floatValue = zoomAfter
        zoom = zoomAfter
        PdfContinuousTrace.zoomPan(
            reason, zoomBefore, zoomAfter, panBefore, panX.floatValue,
            readerMaxPanPx(width, zoomAfter), focalX,
        )
    }
    val toggleZoom: (Float) -> Unit = { focalX ->
        setZoom(toggleReaderZoom(gestureZoom.floatValue), focalX, "toggle")
    }
    // Zoom survives recreation but pan does not. Seed a centered pan once the
    // viewport is known, then only re-clamp when the viewport shrinks.
    val restorePanSeeded = remember { mutableStateOf(false) }
    LaunchedEffect(viewportWidthPx, zoom) {
        if (viewportWidthPx <= 0f) return@LaunchedEffect
        // Keep the gesture snapshot in step with restored/external zoom changes.
        gestureZoom.floatValue = zoom
        val currentZoom = gestureZoom.floatValue
        // Only re-clamp into the LEGAL range [0, maxPan]. Headroom is a one-time
        // ZOOM seed applied in setZoom; re-applying it here would yank a user's
        // drag back into the middle band and cut off the page's left edge.
        val before = panX.floatValue
        val after = clampReaderPan(before, currentZoom, viewportWidthPx)
        if (abs(after - before) > 0.5f) {
            // Make an otherwise silent state change visible in logcat/trace.
            PdfContinuousTrace.zoomPan(
                "reclamp", currentZoom, currentZoom, before, after,
                readerMaxPanPx(viewportWidthPx, currentZoom), viewportWidthPx / 2f,
            )
        }
        panX.floatValue = after
        if (!restorePanSeeded.value) {
            restorePanSeeded.value = true
            if (currentZoom > READER_ZOOM_MIN) {
                // Restore seed uses the same right-headroom-only zoom policy.
                val seeded = readerRestoredPan(currentZoom, viewportWidthPx)
                panX.floatValue = seeded
                PdfContinuousTrace.zoomPan(
                    "restore", READER_ZOOM_MIN, currentZoom, 0f, seeded,
                    readerMaxPanPx(viewportWidthPx, currentZoom), viewportWidthPx / 2f,
                )
            }
        }
    }

    // Set for the duration of a one-finger pan. The page tap detector reads it
    // at tap time so a horizontal pan cannot also open the page. Reset on the
    // next distinct pointer-down; this avoids consuming the up event and keeps
    // the LazyColumn's native vertical fling intact on diagonal drags.
    val suppressPageTap = remember { mutableStateOf(false) }

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
            PdfCompactTopBar(
                title = title.ifBlank { "PDF" },
                pageIndex = currentPage - 1,
                pageCount = pageCount,
                onBack = onBack,
                onPrevious = {
                    val target = continuousReaderScrollTarget(currentPage, -1, pageCount)
                    scope.launch { listState.animateScrollToItem(target) }
                },
                onNext = {
                    val target = continuousReaderScrollTarget(currentPage, 1, pageCount)
                    scope.launch { listState.animateScrollToItem(target) }
                },
                onOpenPages = { showPageJump = true },
                containerColor = MaterialTheme.colorScheme.background,
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
            Box(Modifier.fillMaxSize()) {
                if (pageCount <= 0) {
                    EmptyReader(onBack = onBack)
                } else {
                    // One raw Initial-pass observer owns pinch zoom, one-finger
                    // horizontal pan and fit-zoom page-swipe detection. Pan is
                    // observed but never consumed: the same pointer delta also
                    // reaches the LazyColumn, so diagonal drags pan and scroll
                    // together while vertical-only drags keep native fling.
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
                                    var lastTracedRenderZoom = readerRenderZoom(gestureZoom.floatValue)
                                    var pinchWasActive = false
                                    // One-finger pan: observe only, never consume.
                                    var panPointerId = -1L
                                    var panTotalX = 0f
                                    var panTotalY = 0f
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
                                                        // A new gesture must not inherit
                                                        // pan or swipe state from a previous one.
                                                        panPointerId = -1L
                                                        panTotalX = 0f
                                                        panTotalY = 0f
                                                        panStarted = false
                                                        swipeDelivered = false
                                                        swipeTotalX = 0f
                                                        swipeTotalY = 0f
                                                    }
                                                }
                                            } else if (seen.remove(id)) {
                                                pressedX.remove(id)
                                                gate.onUp(id)
                                            }
                                        }

                                        if (gate.twoFinger) {
                                                if (!pinchWasActive) {
                                                pinchWasActive = true
                                                // A second finger starts a zoom and
                                                // invalidates one-finger pan tracking.
                                                panPointerId = -1L
                                                panTotalX = 0f
                                                panTotalY = 0f
                                                panStarted = false
                                                swipeDelivered = false
                                                swipeTotalX = 0f
                                                swipeTotalY = 0f
                                                lastTracedRenderZoom =
                                                    readerRenderZoom(gestureZoom.floatValue)
                                                PdfContinuousTrace.pinch(
                                                    gestureZoom.floatValue,
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
                                                    gestureZoom.floatValue,
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
                                                // One finger remains after a pinch.
                                                // Re-arm pan tracking for that retained
                                                // pointer immediately instead of waiting
                                                // for every pointer to rise.
                                                panPointerId = -1L
                                                panTotalX = 0f
                                                panTotalY = 0f
                                                panStarted = false
                                                swipeDelivered = false
                                                swipeTotalX = 0f
                                                swipeTotalY = 0f
                                            }
                                            pinchWasActive = false

                                            val change = event.changes.firstOrNull {
                                                it.pressed && it.id.value == panPointerId
                                            } ?: event.changes.firstOrNull { it.pressed }
                                            if (change != null) {
                                                val id = change.id.value
                                                val deltaX = change.position.x -
                                                    change.previousPosition.x
                                                val deltaY = change.position.y -
                                                    change.previousPosition.y

                                                if (gestureZoom.floatValue > READER_ZOOM_MIN) {
                                                    if (panPointerId != id) {
                                                        panPointerId = id
                                                        panTotalX = 0f
                                                        panTotalY = 0f
                                                        panStarted = false
                                                    }
                                                    panTotalX += deltaX
                                                    panTotalY += deltaY
                                                    if (!panStarted &&
                                                        abs(panTotalX) > touchSlop
                                                    ) {
                                                        panStarted = true
                                                        suppressPageTap.value = true
                                                        PdfContinuousTrace.pan(
                                                            "start", id, deltaX, panTotalX,
                                                            panX.floatValue, panX.floatValue,
                                                            gestureZoom.floatValue,
                                                            readerMaxPanPx(
                                                                listState.layoutInfo.viewportSize.width
                                                                    .toFloat(),
                                                                gestureZoom.floatValue,
                                                            ),
                                                        )
                                                    }
                                                    if (panStarted) {
                                                        val width = listState.layoutInfo
                                                            .viewportSize.width.toFloat()
                                                        val before = panX.floatValue
                                                        panX.floatValue = panReaderAfterDrag(
                                                            before,
                                                            deltaX,
                                                            gestureZoom.floatValue,
                                                            width,
                                                        )
                                                        PdfContinuousTrace.pan(
                                                            "move", id, deltaX, panTotalX,
                                                            before, panX.floatValue,
                                                            gestureZoom.floatValue,
                                                            readerMaxPanPx(width, gestureZoom.floatValue),
                                                        )
                                                    }
                                                    // Deliberately do not consume: the
                                                    // LazyColumn receives deltaY and keeps
                                                    // its native vertical scroll/fling.
                                                } else {
                                                    // At fit zoom a horizontal-dominant
                                                    // swipe turns the page; diagonal
                                                    // movement stays with vertical scroll.
                                                    if (swipePointerId != id) {
                                                        swipePointerId = id
                                                        swipeTotalX = 0f
                                                        swipeTotalY = 0f
                                                        swipeDelivered = false
                                                    }
                                                    swipeTotalX += deltaX
                                                    swipeTotalY += deltaY
                                                    if (!swipeDelivered &&
                                                        abs(swipeTotalX) > touchSlop &&
                                                        abs(swipeTotalX) > abs(swipeTotalY) *
                                                            PageSwipe.AXIS_DOMINANCE
                                                    ) {
                                                        swipeDelivered = true
                                                        suppressPageTap.value = true
                                                    }
                                                }
                                            }
                                        }

                                        if (event.changes.none { it.pressed }) {
                                            // A zoomed gesture is a pan, never a page
                                            // turn, even if the zoom landed mid-swipe.
                                            if (swipeDelivered &&
                                                gestureZoom.floatValue <= READER_ZOOM_MIN
                                            ) {
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
                                                    gestureZoom.floatValue,
                                                    readerMaxPanPx(
                                                        listState.layoutInfo.viewportSize.width
                                                            .toFloat(),
                                                        gestureZoom.floatValue,
                                                    ),
                                                )
                                            }
                                            pressedX.clear()
                                            gate.clear()
                                            seen.clear()
                                            pinchWasActive = false
                                            panPointerId = -1L
                                            panTotalX = 0f
                                            panTotalY = 0f
                                            panStarted = false
                                            swipePointerId = -1L
                                            swipeTotalX = 0f
                                            swipeTotalY = 0f
                                            swipeDelivered = false
                                        }
                                    }
                                }
                            }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // The scroller stays enabled: the Initial-pass pinch observer
                            // consumes only two-finger events, so one-finger vertical
                            // scrolling keeps its native fling behavior.
                            // The page navigator floats over the list and consumes
                            // no layout height; only a small visual inset remains.
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
                                )
                            }
                        }
                    }

                    // Non-interactive brightness scrim over the pages only.
                    val dimAlpha = 1f - brightness.coerceIn(0.4f, 1f)
                    if (dimAlpha > 0f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = dimAlpha)),
                        )
                    }
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
 * Zero-based scroll target for the floating navigator's ‹/› buttons, from the 1-based
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
) {
    var attempt by remember(index) { mutableIntStateOf(0) }
    val renderZoom = readerRenderZoom(zoom)
    val renderScale = readerRenderScale(renderZoom)
    // The loader is a fresh lambda on every parent recomposition. Keep it in an
    // updated state so the render coroutine always calls the current one, and
    // key the render on both the zoom bucket and the derived scale so a change
    // to either can never leave an old bitmap on screen.
    val currentLoadPage by rememberUpdatedState(loadPage)
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

    // Measure and place the zoomed page explicitly. requiredWidth inside a
    // viewport-constrained Box is coerced and then centered by Compose, which
    // put the real page origin at -(pageWidth-viewportWidth)/2 - pan. At pan 0
    // the true left edge was still off-screen, so the left content could not
    // be reached no matter how far the user dragged. This Layout owns both the
    // overflow width and its placement: page x = -pan, exactly matching the
    // pan math in ReaderZoom.kt.
    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .pointerInput(index, canOpenPage, onOpenPage) {
                detectTapGestures(
                    onTap = {
                        if (canOpenPage && !currentSuppressTap()) onOpenPage(index)
                    },
                    // The detector is on the viewport-sized Layout root, so
                    // tap offsets are already in the same x space as panX.
                    onDoubleTap = { offset -> currentOnToggleZoom(offset.x) },
                )
            },
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics {
                                        stateDescription = if (zoom > READER_ZOOM_MIN) {
                                            "확대 ${readerZoomLabel(zoom)}"
                                        } else {
                                            "맞춤 배율"
                                        }
                                    },
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
        },
    ) { measurables, constraints ->
        val viewportWidth = constraints.maxWidth.coerceAtLeast(1)
        val pageWidth = readerPageWidthPx(viewportWidth.toFloat(), zoom)
            .toInt()
            .coerceAtLeast(1)
        val pageHeight = (pageWidth.toFloat() / aspectRatio)
            .roundToInt()
            .coerceAtLeast(1)
        val maxPan = readerMaxPanPx(viewportWidth.toFloat(), zoom)
        val page = measurables.first().measure(
            Constraints.fixed(pageWidth, pageHeight),
        )
        layout(viewportWidth, pageHeight) {
            // Read pan state during placement, not composition: every pan
            // frame re-places the page without recomposing its bitmap.
            val x = -panX().coerceIn(0f, maxPan).roundToInt()
            page.place(x, 0)
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

private const val DEFAULT_PAGE_ASPECT = 0.707f
