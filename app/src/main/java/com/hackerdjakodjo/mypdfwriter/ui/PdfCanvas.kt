package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DEFAULT_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.HighlightInk
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCalibrator
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.PressureSmoother
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.DocumentOverlayPainter
import com.hackerdjakodjo.mypdfwriter.pdf.EraserGeometry
import com.hackerdjakodjo.mypdfwriter.pdf.InkQuality
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import com.hackerdjakodjo.mypdfwriter.pdf.PdfLink
import com.hackerdjakodjo.mypdfwriter.pdf.PdfLinks
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextLayout
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import com.hackerdjakodjo.mypdfwriter.pdf.SearchMatchRect
import com.hackerdjakodjo.mypdfwriter.ui.SearchHighlightOverlay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Share of the symmetric pan range deliberately left reachable after a zoom
 * change. A pivot near a viewport edge can park pan exactly on its clamp; the
 * reserved 25% guarantees that the first drag in either direction still moves
 * the page. Pure drag frames are not clamped to this band.
 */
private const val PAN_HEADROOM_BAND = 0.75f

/**
 * Identity of the page a canvas is showing. A document switch to a new file
 * that happens to be on the same page index must still start with a fresh pan;
 * keying state by page index alone leaked the previous document's pan.
 */
internal data class CanvasPageKey(val docId: String, val pageIndex: Int)

/**
 * Top-anchors a page that fits vertically. When the page overflows, the offset
 * is zero and the existing centred pan/zoom model is preserved.
 */
internal fun pdfTopAnchorOffset(
    visibleHeightPx: Float,
    scale: Float,
    viewportHeightPx: Float,
): Float = ((visibleHeightPx * scale - viewportHeightPx) / 2f).coerceAtMost(0f)

private class CanvasTransform {
    var total = 1f
    var fit = 1f
    var zoom = 1f
    var pan = Offset.Zero
    var viewW = 1f
    var viewH = 1f
    var topAnchorOffset = 0f
    /** On-screen scale denominator: size of the *visible* crop in local units. */
    var visibleW = 1f
    var visibleH = 1f
    var insets: CropInsets = CropInsets.NONE

    /**
     * Screen position -> normalized page coordinate (where ink is stored).
     *
     * The screen point is first expressed relative to the visible crop in
     * display space, then [PdfCrop.displayToPage] inverse-maps it into page
     * space. With zero insets this is exactly the old page-centred formula.
     */
    fun normalize(
        position: Offset,
        pressure: Float = DEFAULT_INK_PRESSURE,
        timeMs: Long? = null,
    ): NormalizedPoint {
        val displayX = (position.x - viewW / 2f - pan.x) / (visibleW * total) + 0.5f
        val displayY = (position.y - viewH / 2f - topAnchorOffset - pan.y) /
            (visibleH * total) + 0.5f
        val page = PdfCrop.displayToPage(displayX, displayY, insets)
        return NormalizedPoint(
            page.x.coerceIn(0f, 1f),
            page.y.coerceIn(0f, 1f),
            pressure,
            timeMs,
        )
    }

    /** Normalized page coordinate -> screen position, including crop/zoom/pan. */
    fun toScreen(point: NormalizedPoint): Offset {
        val display = PdfCrop.pageToDisplay(point.x, point.y, insets)
        return Offset(
            viewW / 2f + pan.x + (display.x - 0.5f) * visibleW * total,
            viewH / 2f + topAnchorOffset + pan.y + (display.y - 0.5f) * visibleH * total,
        )
    }
}

@Composable
fun PdfCanvas(
    bitmap: Bitmap?,
    /** Document identity; together with [pageIndex] it keys all transient canvas state. */
    docId: String,
    pageIndex: Int,
    displayWidthPt: Float,
    displayHeightPt: Float,
    annotations: List<Annotation>,
    tool: Tool,
    color: Int,
    strokeWidth: Float,
    fontSize: Float,
    opacity: Float,
    zoom: Float,
    documentStyle: DocumentStyle = DocumentStyle(),
    pageCount: Int = 1,
    nightMode: Boolean = false,
    /** Screen dimming factor in 0.4..1.0; 1f draws no scrim. */
    brightness: Float = 1f,
    /** Nib style for new pen annotations and the live preview. */
    penStyle: PenStyle = PenStyle.BALLPOINT,
    /** Stylus pressure capture; false keeps finger/mouse ink neutral. */
    pressureEnabled: Boolean = true,
    pressureCurve: PressureCurve = PressureCurve.DEFAULT,
    /** Independent highlighter width used by preview and commit. */
    highlightWidth: Float = HighlightInk.DEFAULT_WIDTH,
    /** Independent highlighter opacity used by preview and commit. */
    highlightOpacity: Float = 1f,
    /**
     * View-only crop margins in normalized page fractions. Applied uniformly to
     * the rendered page and every overlay (ink, markup, text, search, links and
     * memo cards); hit-testing inverse-maps with the same transform. Defaults
     * keep all existing callers on the uncropped path.
     */
    cropInsets: CropInsets = CropInsets.NONE,
    /** Mirror left/right margins on even (1-based) pages via [PdfCrop.cropForPage]. */
    cropOddEven: Boolean = false,
    /** Link annotations for this page. Only consulted for short taps in VIEW mode. */
    links: List<PdfLink> = emptyList(),
    textWords: List<PdfTextWord> = emptyList(),
    textSelectMode: Boolean = false,
    selectedWords: List<PdfTextWord> = emptyList(),
    searchHighlights: List<SearchMatchRect> = emptyList(),
    annotationProgress: (Annotation) -> Float = { 1f },
    /** Post-it cards pinned to this page. Defaults keep every other caller unchanged. */
    memoCards: List<MemoCard> = emptyList(),
    showMemoCardsOnPage: Boolean = false,
    onOpenMemoCard: (String) -> Unit = {},
    onMemoCardChange: (MemoCard) -> Unit = {},
    onHideMemoCardOverlay: () -> Unit = {},
    onDeleteMemoCard: (String) -> Unit = {},
    onZoomChange: (Float) -> Unit,
    onAddStroke: (List<NormalizedPoint>, Long) -> Unit,
    /** One normalized eraser contact plus raw pressure and stylus policy. */
    onEraseStroke: (NormalizedPoint, Float, Boolean) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onRequestStamp: (NormalizedPoint) -> Unit,
    onRequestSignature: (NormalizedPoint) -> Unit,
    onTextSelection: (List<PdfTextWord>) -> Unit = {},
    /**
     * Long press while in VIEW mode. The host decides whether to enter text-selection
     * mode and, when word geometry is already available, which word to preselect.
     * Never fired while drawing or while the selection gesture is active.
     */
    onRequestSelectionAt: (NormalizedPoint) -> Unit = {},
    /** Called only when a short VIEW-mode tap lands on a link rectangle. */
    onLinkTapped: (PdfLink) -> Unit = {},
    /** Fit-zoom horizontal swipe in VIEW mode turns the page. */
    onPreviousPage: () -> Unit = {},
    onNextPage: () -> Unit = {},
    /** App-created annotation object editing; disabled by default. */
    annotationEditMode: Boolean = false,
    selectedAnnotationId: String? = null,
    onSelectAnnotation: (String?) -> Unit = {},
    onTransformAnnotation: (Annotation) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val pageW = (bitmap?.width?.toFloat() ?: (displayWidthPt * 2f)).coerceAtLeast(1f)
    val pageH = (bitmap?.height?.toFloat() ?: (displayHeightPt * 2f)).coerceAtLeast(1f)
    // Shared by the hit-test gate and the drawn card so they agree on every rectangle.
    val memoDensity = LocalDensity.current
    val minMemoWidthPx = with(memoDensity) { 92.dp.toPx() }
    val minMemoHeightPx = with(memoDensity) { 56.dp.toPx() }
    // Set by the overlay while a card strip/handle is being dragged, so a VIEW-mode
    // pan/zoom gesture cannot also move the page under the user's finger.
    val memoGestureActive = remember { mutableStateOf(false) }
    val currentOnPreviousPage by rememberUpdatedState(onPreviousPage)
    val currentOnNextPage by rememberUpdatedState(onNextPage)
    val currentZoom by rememberUpdatedState(zoom)
    val currentSelectedWords by rememberUpdatedState(selectedWords)
    // One learned pressure range per document/page/settings combination. A fresh
    // calibrator per stroke made every PDF stroke restart from raw pressure.
    val inkCalibrator = remember(docId, pageIndex, tool, pressureEnabled, pressureCurve) {
        PressureCalibrator(bootstrapSamples = 3)
    }
    // Temporal pressure low-pass shared with the note canvas. Reset at every
    // stroke start below; never persisted.
    val inkSmoother = remember(docId, pageIndex, tool, pressureEnabled, pressureCurve) {
        PressureSmoother()
    }

    val pageKey = CanvasPageKey(docId, pageIndex)
    // Pan is transient per *document page*, not just per index: switching to
    // another document that happens to be at the same page must not inherit it.
    var pan by remember(pageKey) { mutableStateOf(Offset.Zero) }
    val livePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var shapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var shapeEnd by remember { mutableStateOf<NormalizedPoint?>(null) }
    var eraserPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    var eraserPressureSensitive by remember { mutableStateOf(false) }
    var selectionStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var selectionEnd by remember { mutableStateOf<NormalizedPoint?>(null) }
    var annotationPreview by remember(pageKey) { mutableStateOf<Annotation?>(null) }
    val eraserTrail = remember { mutableStateListOf<NormalizedPoint>() }
    val selectedWordSet = remember(selectedWords) { selectedWords.toSet() }
    // Filtering per page used to allocate two new lists on every draw frame.
    val pageAnnotations = remember(annotations, pageIndex) {
        annotations.filter { it.page == pageIndex }
    }
    val renderedPageAnnotations = remember(pageAnnotations, annotationPreview) {
        val preview = annotationPreview
        if (preview == null) pageAnnotations else pageAnnotations.map { if (it.id == preview.id) preview else it }
    }
    val pageWords = remember(textWords, pageIndex) {
        textWords.filter { it.pageIndex == pageIndex }
    }
    // Gesture hit-testing needs non-blank words only. Computed once per page so
    // a drag never re-filters the whole document on a pointer-move frame.
    val selectablePageWords = remember(textWords, pageIndex) {
        textWords.filter { it.pageIndex == pageIndex && it.text.isNotBlank() }
    }
    val transform = remember { CanvasTransform() }
    // The VIEW-mode pointer handler uses a stable key, so read the host callback
    // through a live state to avoid capturing a stale page/selection snapshot.
    val currentRequestSelectionAt by rememberUpdatedState(onRequestSelectionAt)
    val currentLinks by rememberUpdatedState(links)
    val currentOnLinkTapped by rememberUpdatedState(onLinkTapped)
    val currentOnEraseStroke by rememberUpdatedState(onEraseStroke)

    LaunchedEffect(pageKey) {
        pan = Offset.Zero
        memoGestureActive.value = false
        livePoints.clear()
        eraserTrail.clear()
        shapeStart = null
        shapeEnd = null
        eraserPoint = null
        eraserPressureSensitive = false
        selectionStart = null
        selectionEnd = null
        annotationPreview = null
    }

    LaunchedEffect(annotationEditMode) {
        if (!annotationEditMode) annotationPreview = null
    }

    LaunchedEffect(textSelectMode) {
        if (!textSelectMode) {
            selectionStart = null
            selectionEnd = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Crop is expressed in page-normalized fractions, then converted to the
        // local pixel space the canvas already draws in. Everything below (the
        // canvas transform, pan limits and hit-tests) uses the visible rect as
        // the effective page, so zoom=1 still fits the kept area to the screen.
        val effectiveCrop = PdfCrop.cropForPage(cropInsets, pageIndex, cropOddEven)
        val visible = PdfCrop.visibleRect(effectiveCrop)
        val cropLeftPx = visible.left * pageW
        val cropTopPx = visible.top * pageH
        val cropRightPx = visible.right * pageW
        val cropBottomPx = visible.bottom * pageH
        val visW = (cropRightPx - cropLeftPx).coerceAtLeast(1f)
        val visH = (cropBottomPx - cropTopPx).coerceAtLeast(1f)
        val originX = (cropLeftPx + cropRightPx) / 2f
        val originY = (cropTopPx + cropBottomPx) / 2f
        val hasCrop = visible.left > 0f || visible.top > 0f ||
            visible.right < 1f || visible.bottom < 1f
        val fit = min(viewW / visW, viewH / visH)
        val total = fit * zoom
        val topAnchorOffset = pdfTopAnchorOffset(visH, total, viewH)

        // The memo-card helpers assume the full page centre sits at the viewport
        // centre. Shifting pan by the crop-centre delta lets them reuse their
        // existing geometry without changing the note-side shared code.
        val memoPan = Offset(
            pan.x + (pageW / 2f - originX) * total,
            pan.y + topAnchorOffset + (pageH / 2f - originY) * total,
        )

        // A touch on a visible card belongs to that card: no stroke, erase, shape
        // or text selection may start underneath it.
        val currentMemoHitTest by rememberUpdatedState { position: Offset ->
            showMemoCardsOnPage && memoCards.isNotEmpty() && fit > 0f &&
                memoCards.any { card ->
                    pdfMemoScreenRect(
                        card = card,
                        fit = total,
                        pan = memoPan,
                        viewWidth = viewW,
                        viewHeight = viewH,
                        pageWidth = pageW,
                        pageHeight = pageH,
                        minWidthPx = minMemoWidthPx,
                        minHeightPx = minMemoHeightPx,
                    ).contains(position)
                }
        }

        LaunchedEffect(total, viewW, viewH, visW, visH) {
            val limitX = ((visW * total - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((visH * total - viewH) / 2f).coerceAtLeast(0f)
            if (pan.x !in -limitX..limitX || pan.y !in -limitY..limitY) {
                pan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
            }
        }

        SideEffect {
            transform.total = total
            transform.fit = fit
            transform.zoom = zoom
            transform.pan = pan
            transform.viewW = viewW
            transform.viewH = viewH
            transform.topAnchorOffset = topAnchorOffset
            transform.visibleW = visW
            transform.visibleH = visH
            transform.insets = effectiveCrop
        }

        fun clampPan(candidate: Offset, scale: Float): Offset {
            val limitX = ((visW * scale - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((visH * scale - viewH) / 2f).coerceAtLeast(0f)
            return Offset(candidate.x.coerceIn(-limitX, limitX), candidate.y.coerceIn(-limitY, limitY))
        }

        /**
         * Zoom-frame counterpart of [clampPan]. A pivot near a viewport edge
         * otherwise parks pan on the clamp, so a rightward (or leftward) drag
         * adds more offset in the same direction and clamps straight back —
         * the page appears stuck. Reserving an inner band leaves travel in both
         * directions. Ordinary drag frames must keep using [clampPan] so page
         * edges remain reachable with a following gesture.
         */
        fun clampPanWithHeadroom(candidate: Offset, scale: Float): Offset {
            val fullX = ((visW * scale - viewW) / 2f).coerceAtLeast(0f)
            val fullY = ((visH * scale - viewH) / 2f).coerceAtLeast(0f)
            val limitX = fullX * PAN_HEADROOM_BAND
            val limitY = fullY * PAN_HEADROOM_BAND
            return Offset(candidate.x.coerceIn(-limitX, limitX), candidate.y.coerceIn(-limitY, limitY))
        }

        fun wordsInSelection(a: NormalizedPoint, b: NormalizedPoint): List<PdfTextWord> =
            PdfTextLayout.wordsForGestureOnPage(
                pageWords = selectablePageWords,
                startX = a.x,
                startY = a.y,
                endX = b.x,
                endY = b.y,
            )

        val gestureModifier = if (annotationEditMode) {
            Modifier
        } else if (textSelectMode && textWords.isNotEmpty()) {
            Modifier.pointerInput(pageIndex, textWords) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentMemoHitTest(down.position)) {
                        down.consume()
                        return@awaitEachGesture
                    }
                    down.consume()
                    val start = transform.normalize(down.position)
                    selectionStart = start
                    selectionEnd = start
                    // Publish only when the covered words actually change. Jitter
                    // inside one word must not recompose the whole viewer on every
                    // touch frame — the live drag rectangle is drawn locally.
                    var lastPublished: List<PdfTextWord> = currentSelectedWords
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            pressed = false
                        } else if (change.positionChanged()) {
                            val point = transform.normalize(change.position)
                            selectionEnd = point
                            change.consume()
                            val next = wordsInSelection(start, point)
                            if (TextSelectionPublish.shouldPublish(lastPublished, next)) {
                                lastPublished = next
                                onTextSelection(next)
                            }
                        }
                    }
                    val end = selectionEnd ?: start
                    val final = wordsInSelection(start, end)
                    // Compared against what the host held at gesture start, so an
                    // empty final result still clears a previous selection while an
                    // unchanged one costs no extra recomposition.
                    if (TextSelectionPublish.shouldPublish(lastPublished, final)) {
                        onTextSelection(final)
                    }
                    selectionStart = null
                    selectionEnd = null
                }
            }
        } else if (tool == Tool.VIEW) {
            Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        if (memoGestureActive.value) return@detectTransformGestures
                        val oldTotal = transform.total
                        val newZoom = (transform.zoom * zoomChange).coerceIn(1f, 6f)
                        val newTotal = transform.fit * newZoom
                        // Capture before mutating transform.zoom below.
                        val zoomChanged = newZoom != transform.zoom
                        val oldAnchor = pdfTopAnchorOffset(
                            transform.visibleH, oldTotal, size.height.toFloat(),
                        )
                        val newAnchor = pdfTopAnchorOffset(
                            transform.visibleH, newTotal, size.height.toFloat(),
                        )
                        val pivot = centroid - Offset(
                            size.width / 2f,
                            size.height / 2f + oldAnchor,
                        )
                        val scaledPan = if (oldTotal > 0f) {
                            val ratio = newTotal / oldTotal
                            Offset(
                                pivot.x - (pivot.x - pan.x) * ratio,
                                pivot.y + oldAnchor - newAnchor -
                                    (pivot.y - pan.y) * ratio,
                            )
                        } else {
                            pan
                        }
                        val candidate = scaledPan + panChange
                        // Only zoom frames reserve headroom; pure drags keep the
                        // full clamp so page edges stay reachable.
                        val updated = if (zoomChanged) {
                            clampPanWithHeadroom(candidate, newTotal)
                        } else {
                            clampPan(candidate, newTotal)
                        }
                        pan = updated
                        transform.pan = updated
                        if (zoomChanged) {
                            transform.zoom = newZoom
                            transform.total = newTotal
                            onZoomChange(newZoom)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            // Links are checked only for short taps in VIEW mode;
                            // a card consumes its own taps before this handler.
                            if (!currentMemoHitTest(position)) {
                                val point = transform.normalize(position)
                                val link = PdfLinks.linkAt(currentLinks, point.x, point.y)
                                if (link != null) currentOnLinkTapped(link)
                            }
                        },
                        onDoubleTap = { tapPosition ->
                            // Match the continuous reader: double tap TOGGLES
                            // fit <-> 200%. Resetting to fit here left pan range
                            // at zero, so "zoom then drag" never moved the page.
                            val oldZoom = transform.zoom
                            val newZoom = toggleReaderZoom(oldZoom)
                            val newTotal = transform.fit * newZoom
                            val ratio = if (transform.total > 0f) {
                                newTotal / transform.total
                            } else 1f
                            val oldAnchor = pdfTopAnchorOffset(
                                transform.visibleH, transform.total, size.height.toFloat(),
                            )
                            val newAnchor = pdfTopAnchorOffset(
                                transform.visibleH, newTotal, size.height.toFloat(),
                            )
                            val pivot = tapPosition - Offset(
                                size.width / 2f,
                                size.height / 2f + oldAnchor,
                            )
                            val anchored = Offset(
                                pivot.x - (pivot.x - pan.x) * ratio,
                                pivot.y + oldAnchor - newAnchor -
                                    (pivot.y - pan.y) * ratio,
                            )
                            pan = clampPanWithHeadroom(anchored, newTotal)
                            // Update the cache synchronously: the next gesture
                            // frame must not read stale zoom/pan before SideEffect.
                            transform.zoom = newZoom
                            transform.total = newTotal
                            transform.pan = pan
                            onZoomChange(newZoom)
                        },
                        onLongPress = { position ->
                            if (!currentMemoHitTest(position)) {
                                currentRequestSelectionAt(transform.normalize(position))
                            }
                        },
                    )
                }
                // Fit-zoom page swipe. Observe only (never consume): the tap,
                // long-press and transform detectors above stay untouched, and
                // zoomed pages keep using the same drag for panning.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // transform.zoom is the synchronous source of truth: a
                        // double tap writes it immediately while the parent's
                        // `zoom` prop only updates on the next composition.
                        if (currentMemoHitTest(down.position) || memoGestureActive.value ||
                            currentZoom > 1f || transform.zoom > 1f
                        ) return@awaitEachGesture
                        val id = down.id.value
                        var totalX = 0f
                        var totalY = 0f
                        var delivered = false
                        val slop = viewConfiguration.touchSlop
                        val threshold = PageSwipe.thresholdPx(
                            size.width.toFloat(), memoDensity.density,
                        )
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id.value == id }
                            if (pointer == null || !pointer.pressed) break
                            if (event.changes.count { it.pressed } > 1) return@awaitEachGesture
                            totalX += pointer.position.x - pointer.previousPosition.x
                            totalY += pointer.position.y - pointer.previousPosition.y
                            val dir = PageSwipe.direction(totalX, totalY, slop)
                            if (!delivered && dir != 0 && abs(totalX) >= threshold) {
                                delivered = true
                                if (dir > 0) currentOnNextPage() else currentOnPreviousPage()
                            }
                        }
                    }
                }
        } else {
            Modifier.pointerInput(tool, docId, pageIndex, pressureEnabled, pressureCurve) {
                when (tool) {
                    else -> awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (currentMemoHitTest(down.position)) {
                            down.consume()
                            return@awaitEachGesture
                        }
                        down.consume()
                        val strokeStartUptimeMs = down.uptimeMillis
                        val gestureTool = gestureToolForPointer(tool, down.type)

                        if (gestureTool == Tool.ERASER) {
                            eraserTrail.clear()
                            eraserPressureSensitive = eraserUsesPressure(down.type, pressureEnabled)
                            val startPressure = eraserInputPressure(
                                down.type,
                                down.pressure,
                                pressureEnabled,
                            )
                            val start = transform.normalize(
                                down.position,
                                startPressure,
                                relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                            )
                            eraserTrail.add(start)
                            eraserPoint = start
                            var lastPosition = down.position
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    pressed = false
                                } else {
                                    val movement = change.position - lastPosition
                                    val pressure = eraserInputPressure(
                                        change.type,
                                        change.pressure,
                                        pressureEnabled,
                                    )
                                    if (InkQuality.hasMeaningfulPositionChange(movement.x, movement.y)) {
                                        lastPosition = change.position
                                        val point = transform.normalize(
                                            change.position,
                                            pressure,
                                            relativePointTimeMs(
                                                change.uptimeMillis,
                                                strokeStartUptimeMs,
                                            ),
                                        )
                                        eraserTrail.add(point)
                                        eraserPoint = point
                                    } else {
                                        // Pressure may change while the eraser is
                                        // still; keep its preview kernel live without
                                        // adding jitter geometry to the trail.
                                        eraserPoint = transform.normalize(
                                            lastPosition,
                                            pressure,
                                            relativePointTimeMs(
                                                change.uptimeMillis,
                                                strokeStartUptimeMs,
                                            ),
                                        )
                                    }
                                    change.consume()
                                }
                            }
                            // Preserve commit-on-up semantics while feeding the
                            // ViewModel's shared, pressure-aware point API.
                            val pressureSensitive = eraserPressureSensitive
                            EraserGeometry.densifyTrail(eraserTrail.toList()).forEach { point ->
                                currentOnEraseStroke(point, point.pressure, pressureSensitive)
                            }
                            eraserTrail.clear()
                            eraserPoint = null
                            eraserPressureSensitive = false
                            return@awaitEachGesture
                        }

                        if (gestureTool == Tool.TEXT || gestureTool == Tool.STAMP ||
                            gestureTool == Tool.SIGNATURE
                        ) {
                            var traveled = 0f
                            var lastPosition = down.position
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    pressed = false
                                    if (traveled <= viewConfiguration.touchSlop) {
                                        val point = transform.normalize(change.position)
                                        when (gestureTool) {
                                            Tool.TEXT -> onRequestText(point)
                                            Tool.STAMP -> onRequestStamp(point)
                                            Tool.SIGNATURE -> onRequestSignature(point)
                                            else -> Unit
                                        }
                                    }
                                } else {
                                    val movement = change.position - lastPosition
                                    traveled += movement.getDistance()
                                    lastPosition = change.position
                                    change.consume()
                                }
                            }
                            return@awaitEachGesture
                        }

                        val isShape = when (gestureTool) {
                            Tool.RECT,
                            Tool.TRIANGLE,
                            Tool.ARROW,
                            Tool.LINE,
                            Tool.ELLIPSE,
                            Tool.UNDERLINE,
                            Tool.STRIKEOUT,
                            Tool.SQUIGGLY,
                            Tool.REDACT -> true
                            else -> false
                        }
                        if (isShape) {
                            val start = transform.normalize(
                                down.position,
                                DEFAULT_INK_PRESSURE,
                                relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                            )
                            shapeStart = start
                            shapeEnd = start
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    pressed = false
                                } else if (change.positionChanged()) {
                                    shapeEnd = transform.normalize(
                                        change.position,
                                        DEFAULT_INK_PRESSURE,
                                        relativePointTimeMs(
                                            change.uptimeMillis,
                                            strokeStartUptimeMs,
                                        ),
                                    )
                                    change.consume()
                                }
                            }
                            val startPoint = shapeStart
                            val endPoint = shapeEnd ?: startPoint
                            if (startPoint != null && endPoint != null) {
                                val dx = endPoint.x - startPoint.x
                                val dy = endPoint.y - startPoint.y
                                if (dx * dx + dy * dy > 0.000025f) {
                                    onAddStroke(listOf(startPoint, endPoint), 0L)
                                }
                            }
                            shapeStart = null
                            shapeEnd = null
                            return@awaitEachGesture
                        }

                        // Shared, session-scoped calibration keeps ink weight
                        // consistent stroke-to-stroke. Finger/mouse stay neutral.
                        var lastPressureUptimeMs = strokeStartUptimeMs
                        fun capture(
                            change: PointerInputChange,
                            eventUptimeMs: Long = change.uptimeMillis,
                        ): Float {
                            val elapsedMs = (eventUptimeMs - lastPressureUptimeMs).coerceAtLeast(0L)
                            lastPressureUptimeMs = maxOf(lastPressureUptimeMs, eventUptimeMs)
                            return effectiveInkPressure(
                                pointerType = change.type,
                                rawPressure = change.pressure,
                                pressureEnabled = pressureEnabled,
                                pressureCurve = pressureCurve,
                                calibrator = inkCalibrator,
                                pressed = change.pressed,
                                smoother = inkSmoother,
                                elapsedMs = elapsedMs,
                            )
                        }

                        livePoints.clear()
                        // Each stroke starts with an exact first pressure sample.
                        inkSmoother.reset()
                        val downPressure = capture(down)
                        livePoints.add(
                            transform.normalize(
                                down.position,
                                downPressure,
                                relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                            ),
                        )
                        var lastPressure = downPressure
                        var lastEmittedAtUptimeMs = strokeStartUptimeMs
                        var lastPosition = down.position
                        var lastChange = down
                        var stationaryEmitted = 0

                        fun emitStationarySample(
                            change: PointerInputChange,
                            pressure: Float,
                            nowUptimeMs: Long,
                        ) {
                            if (!pressureEnabled || change.type != PointerType.Stylus ||
                                !change.pressed || !change.pressure.isFinite() ||
                                (gestureTool != Tool.PEN && gestureTool != Tool.HIGHLIGHT)
                            ) {
                                return
                            }
                            if (!InkQuality.shouldEmitPressureSample(
                                    previousPressure = lastPressure,
                                    nextPressure = pressure,
                                    positionChanged = false,
                                    elapsedMs = (nowUptimeMs - lastEmittedAtUptimeMs)
                                        .coerceAtLeast(0L),
                                    emitted = stationaryEmitted,
                                )
                            ) {
                                return
                            }
                            stationaryEmitted++
                            livePoints.add(
                                transform.normalize(
                                    lastPosition,
                                    pressure,
                                    relativePointTimeMs(
                                        nowUptimeMs,
                                        strokeStartUptimeMs,
                                        lastEmittedAtUptimeMs - strokeStartUptimeMs,
                                    ),
                                ),
                            )
                            lastPressure = pressure
                            lastEmittedAtUptimeMs = nowUptimeMs
                        }

                        var pressed = true
                        while (pressed) {
                            val event = withTimeoutOrNull(InkQuality.MIN_STATIONARY_SAMPLE_MS) {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                if (pressureEnabled && lastChange.type == PointerType.Stylus &&
                                    (gestureTool == Tool.PEN || gestureTool == Tool.HIGHLIGHT)
                                ) {
                                    val now = SystemClock.uptimeMillis()
                                    val pressure = capture(lastChange, now)
                                    emitStationarySample(lastChange, pressure, now)
                                }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastChange = change
                            if (!change.pressed) {
                                change.consume()
                                pressed = false
                            } else {
                                val pressure = capture(change)
                                val movement = change.position - lastPosition
                                if (InkQuality.hasMeaningfulPositionChange(movement.x, movement.y)) {
                                    lastPosition = change.position
                                    livePoints.add(
                                        transform.normalize(
                                            change.position,
                                            pressure,
                                            relativePointTimeMs(
                                                change.uptimeMillis,
                                                strokeStartUptimeMs,
                                                lastEmittedAtUptimeMs - strokeStartUptimeMs,
                                            ),
                                        ),
                                    )
                                    lastPressure = pressure
                                    lastEmittedAtUptimeMs = maxOf(
                                        lastEmittedAtUptimeMs,
                                        change.uptimeMillis,
                                    )
                                } else if (movement.getDistance() < 0.1f) {
                                    emitStationarySample(change, pressure, change.uptimeMillis)
                                }
                                change.consume()
                            }
                        }
                        if (livePoints.isNotEmpty()) {
                            onAddStroke(livePoints.toList(), inkStrokeDurationMs(livePoints))
                        }
                        livePoints.clear()
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            val cx = size.width / 2f + pan.x
            val cy = size.height / 2f + topAnchorOffset + pan.y
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(cx, cy)
                native.scale(total, total)
                // Centre the visible crop, not the full page. The page is drawn
                // from `left = -pageW/2`, so its centre already sits at the
                // origin; shifting by (page centre - crop centre) puts the kept
                // region there instead. Every layer below is drawn in original
                // page-local pixels, so this one affine transform keeps the
                // bitmap, ink, markup, text, search and links aligned while the
                // clip hides the removed blank edges. With zero insets this is
                // a no-op and the old full-page rendering is reproduced exactly.
                native.translate(pageW / 2f - originX, pageH / 2f - originY)

                val left = -pageW / 2f
                val top = -pageH / 2f

                // A full-page shadow would peek out from the cropped edge; with
                // a crop active the visible page is its own hard boundary.
                if (!hasCrop) {
                    val shadowPaint = Paint().apply {
                        this.color = android.graphics.Color.argb(38, 0, 0, 0)
                        maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                    }
                    native.drawRect(left + 3f, top + 5f, left + pageW + 3f, top + pageH + 5f, shadowPaint)
                }

                native.save()
                // The crop centre was translated to the local origin above, so the
                // clip must be centred there too. Raw page pixels would clip just
                // one quadrant (and disagree with transform.normalize).
                val clip = PdfCrop.clipRectForCanvas(visible, pageW, pageH)
                native.clipRect(clip.left, clip.top, clip.right, clip.bottom)

                if (bitmap != null) {
                    val bitmapPaint = Paint().apply {
                        if (nightMode) {
                            colorFilter = ColorMatrixColorFilter(
                                ColorMatrix().apply {
                                    set(
                                        floatArrayOf(
                                            -1f, 0f, 0f, 0f, 255f,
                                            0f, -1f, 0f, 0f, 255f,
                                            0f, 0f, -1f, 0f, 255f,
                                            0f, 0f, 0f, 1f, 0f,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    native.drawBitmap(bitmap, left, top, bitmapPaint)
                } else {
                    val paperPaint = Paint().apply {
                        this.color = if (nightMode) android.graphics.Color.rgb(22, 25, 30)
                        else android.graphics.Color.WHITE
                    }
                    native.drawRect(left, top, left + pageW, top + pageH, paperPaint)
                }

                native.save()
                native.translate(left, top)
                DocumentOverlayPainter.draw(
                    canvas = native,
                    style = documentStyle,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    width = pageW,
                    height = pageH,
                )
                SearchHighlightOverlay.draw(
                    canvas = native,
                    rects = searchHighlights,
                    width = pageW,
                    height = pageH,
                )
                AnnotationPainter.draw(
                    native,
                    renderedPageAnnotations,
                    pageW,
                    pageH,
                    progress = annotationProgress,
                )


                val previewPoints = livePoints.toList()
                if (previewPoints.isNotEmpty()) {
                    val type = if (tool == Tool.HIGHLIGHT) AnnotationType.HIGHLIGHT else AnnotationType.PEN
                    AnnotationPainter.drawOne(
                        native,
                        Annotation(
                            type = type,
                            page = pageIndex,
                            color = color,
                            width = if (type == AnnotationType.HIGHLIGHT) {
                                HighlightInk.clamp(highlightWidth)
                            } else {
                                strokeWidth
                            },
                            points = previewPoints,
                            penStyle = if (type == AnnotationType.PEN) penStyle else PenStyle.BALLPOINT,
                            opacity = if (type == AnnotationType.HIGHLIGHT) {
                                HighlightInk.clampOpacity(highlightOpacity)
                            } else {
                                opacity
                            },
                        ),
                        pageW,
                        pageH,
                        livePreview = true,
                    )
                }

                val start = shapeStart
                val end = shapeEnd
                val previewType = annotationTypeForTool(tool)
                if (start != null && end != null && previewType != null) {
                    AnnotationPainter.drawOne(
                        native,
                        Annotation(
                            type = previewType,
                            page = pageIndex,
                            color = if (previewType == AnnotationType.REDACT) {
                                android.graphics.Color.BLACK
                            } else {
                                color
                            },
                            width = maxOf(strokeWidth, 0.0025f),
                            points = listOf(start, end),
                            opacity = if (previewType == AnnotationType.REDACT) 1f else opacity,
                        ),
                        pageW,
                        pageH,
                    )
                }

                val eraser = eraserPoint
                if (eraser != null) {
                    drawSharedEraserPreview(
                        native,
                        eraser,
                        pageW,
                        pageH,
                        pressureSensitive = eraserPressureSensitive,
                    )
                }

                // Brightness is a non-interactive dark scrim over the rendered page.
                // It is drawn last so the page, night-mode inversion and all ink share
                // the same screen dimming, and it never consumes pointer input.
                val dimAlpha = ((1f - brightness.coerceIn(0.4f, 1f)) * 255f).roundToInt()
                if (dimAlpha > 0) {
                    native.drawRect(
                        0f, 0f, pageW, pageH,
                        Paint().apply {
                            this.color = android.graphics.Color.argb(dimAlpha, 0, 0, 0)
                            style = Paint.Style.FILL
                        },
                    )
                }

                native.restore() // page-origin translate (ink/text overlays)
                native.restore() // crop clip
                native.restore() // viewport translate + scale + crop centre
            }
        }

        // Selection highlight lives in its own Canvas. The page bitmap, ink,
        // search and overlays above are NOT redrawn while a finger drags the
        // selection rectangle — only this small overlay is invalidated. The
        // transform below mirrors the page Canvas exactly so every rect aligns.
        if (textSelectMode && textWords.isNotEmpty()) {
            Canvas(Modifier.matchParentSize()) {
                val cx = size.width / 2f + pan.x
                val cy = size.height / 2f + topAnchorOffset + pan.y
                val pageLeft = -pageW / 2f
                val pageTop = -pageH / 2f
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(cx, cy)
                    native.scale(total, total)
                    native.translate(pageW / 2f - originX, pageH / 2f - originY)
                    native.save()
                    val clip = PdfCrop.clipRectForCanvas(visible, pageW, pageH)
                    native.clipRect(clip.left, clip.top, clip.right, clip.bottom)
                    native.translate(pageLeft, pageTop)

                    val subtleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        this.color = android.graphics.Color.argb(16, 31, 78, 121)
                    }
                    val selectedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        this.color = android.graphics.Color.argb(72, 31, 78, 121)
                    }
                    val selectedBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        this.color = android.graphics.Color.argb(190, 31, 78, 121)
                        this.strokeWidth = (1.2f / total).coerceAtLeast(0.2f)
                    }
                    pageWords.forEach { word ->
                        val left = word.rect.left * pageW
                        val top = word.rect.top * pageH
                        val right = word.rect.right * pageW
                        val bottom = word.rect.bottom * pageH
                        if (word in selectedWordSet) {
                            native.drawRect(left, top, right, bottom, selectedFill)
                            native.drawRect(left, top, right, bottom, selectedBorder)
                        } else {
                            native.drawRect(left, top, right, bottom, subtleFill)
                        }
                    }

                    val start = selectionStart
                    val end = selectionEnd
                    if (start != null && end != null) {
                        val drag = NormalizedRect(
                            minOf(start.x, end.x),
                            minOf(start.y, end.y),
                            maxOf(start.x, end.x),
                            maxOf(start.y, end.y),
                        )
                        if (drag.width > 0.002f || drag.height > 0.002f) {
                            val dragFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.FILL
                                this.color = android.graphics.Color.argb(36, 31, 78, 121)
                            }
                            val dragBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                this.color = android.graphics.Color.argb(170, 31, 78, 121)
                                this.strokeWidth = (1.5f / total).coerceAtLeast(0.25f)
                            }
                            native.drawRect(
                                drag.left * pageW,
                                drag.top * pageH,
                                drag.right * pageW,
                                drag.bottom * pageH,
                                dragFill,
                            )
                            native.drawRect(
                                drag.left * pageW,
                                drag.top * pageH,
                                drag.right * pageW,
                                drag.bottom * pageH,
                                dragBorder,
                            )
                        }
                    }

                    native.restore() // crop clip
                    native.restore() // viewport translate + scale + crop centre
                }
            }
        }

        // Post-it cards share the canvas transform and sit above all ink. The layer's
        // full-size parent has no pointer input, so strokes outside a card still reach
        // the canvas below; only the card's own small box consumes touches.
        if (showMemoCardsOnPage && memoCards.isNotEmpty()) {
            // Cards share the crop transform, and the layer is clipped to the
            // visible rectangle so a card pinned to a removed margin cannot
            // float over the cropped page. Its pan is expressed relative to the
            // crop box; the hit-test above keeps the full-screen equivalent.
            val cropBoxLeft = viewW / 2f + pan.x - visW * total / 2f
            val cropBoxTop = viewH / 2f + topAnchorOffset + pan.y - visH * total / 2f
            val cropBoxWidth = with(memoDensity) { (visW * total).toDp() }
            val cropBoxHeight = with(memoDensity) { (visH * total).toDp() }
            Box(
                Modifier
                    .offset {
                        IntOffset(cropBoxLeft.roundToInt(), cropBoxTop.roundToInt())
                    }
                    .size(cropBoxWidth, cropBoxHeight)
                    .clipToBounds(),
            ) {
                PdfMemoCardLayer(
                    cards = memoCards,
                    fit = total,
                    pan = Offset(
                        (pageW / 2f - originX) * total,
                        (pageH / 2f - originY) * total,
                    ),
                    viewWidth = visW * total,
                    viewHeight = visH * total,
                    pageWidth = pageW,
                    pageHeight = pageH,
                    minWidthPx = minMemoWidthPx,
                    minHeightPx = minMemoHeightPx,
                    onOpen = onOpenMemoCard,
                    onCardChange = onMemoCardChange,
                    onHide = onHideMemoCardOverlay,
                    onDelete = onDeleteMemoCard,
                    onDragActiveChange = { memoGestureActive.value = it },
                )
            }
        }

        if (annotationEditMode) {
            PdfAnnotationEditOverlay(
                annotations = pageAnnotations,
                selectedAnnotationId = selectedAnnotationId,
                preview = annotationPreview,
                pageWidth = pageW,
                pageHeight = pageH,
                visibleClip = Rect(
                    viewW / 2f + pan.x - visW * total / 2f,
                    viewH / 2f + topAnchorOffset + pan.y - visH * total / 2f,
                    viewW / 2f + pan.x + visW * total / 2f,
                    viewH / 2f + topAnchorOffset + pan.y + visH * total / 2f,
                ),
                normalize = { transform.normalize(it) },
                toScreen = { transform.toScreen(it) },
                onSelect = onSelectAnnotation,
                onTransform = onTransformAnnotation,
                onPreview = { annotationPreview = it },
            )
        }
    }
}

private fun annotationTypeForTool(tool: Tool): AnnotationType? = when (tool) {
    Tool.RECT -> AnnotationType.RECT
    Tool.TRIANGLE -> AnnotationType.TRIANGLE
    Tool.ARROW -> AnnotationType.ARROW
    Tool.LINE -> AnnotationType.LINE
    Tool.ELLIPSE -> AnnotationType.ELLIPSE
    Tool.UNDERLINE -> AnnotationType.UNDERLINE
    Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
    Tool.SQUIGGLY -> AnnotationType.SQUIGGLY
    Tool.REDACT -> AnnotationType.REDACT
    else -> null
}
