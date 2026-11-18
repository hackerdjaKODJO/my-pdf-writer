package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.clampInkPressure
import com.hackerdjakodjo.mypdfwriter.data.pressureCurveRange
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.abs
import kotlin.math.min

private const val NOTE_PAGE_SCALE = 2.5f
private const val MAX_NOTE_ZOOM = 6f
private const val ERASER_RADIUS_FRACTION = 0.028f
private const val CANVAS_TRACE_TAG = "NoteCanvas"

/**
 * A template page plus its annotation layer.
 *
 * The paper is painted in page space at 2.5x PDF points; pan/zoom is applied by the
 * native canvas transform. Drawing previews use the same painter as the committed
 * annotations, so what the user sees is what gets saved and exported.
 *
 * Orientation is per page: portrait is 595 x 842 pt and landscape is 842 x 595 pt.
 * Gesture normalization, pan limits, selection bounds and the eraser preview all
 * use that page's dimensions, so one notebook can mix page orientations.
 *
 * Input rules:
 *  - penOnly = true: only a stylus (or a stylus eraser) draws. Fingers pan/pinch.
 *  - penOnly = false: one finger draws; a second finger switches that gesture to
 *    pan/pinch. A stylus always draws and ignores palm/finger contacts.
 *  - zoomLock = true: pan/zoom and double-tap reset are disabled while writing.
 *  - pressureEnabled = true: stylus pressure is stored with each point and drives
 *    fountain/brush width. [pressureCurve] maps raw stylus pressure to effective
 *    pressure before storage; curve presets preserve 1f exactly. Fingers and
 *    unknown pointers always store 1f.
 *  - gestureUndoEnabled = true: a short two-finger tap requests undo and a
 *    three-finger tap requests redo when [onGestureUndo]/[onGestureRedo] are
 *    supplied. Taps are ignored after drawing, panning, zooming, lasso or any
 *    stylus contact.
 *  - memo cards: when [showMemoCardsOnPage] is on, a touch inside a card rectangle
 *    is owned by the overlay and never starts a stroke, pan, zoom or undo gesture.
 */
@Composable
fun NoteCanvas(
    page: NotePage,
    tool: Tool,
    color: Int,
    strokeWidth: Float,
    fontSize: Float,
    opacity: Float,
    penStyle: PenStyle = PenStyle.BALLPOINT,
    penOnly: Boolean,
    zoomLock: Boolean,
    pressureEnabled: Boolean = true,
    pressureCurve: PressureCurve = PressureCurve.DEFAULT,
    gestureUndoEnabled: Boolean = NOTE_GESTURE_UNDO_DEFAULT,
    selectionBounds: NoteSelectionBounds? = null,
    annotationProgress: (Annotation) -> Float = { 1f },
    // Study-only overlay. Defaults keep every existing caller byte-for-byte intact.
    maskingCards: List<MaskingCard> = emptyList(),
    showMaskingCards: Boolean = false,
    onRevealMaskingCard: (String) -> Unit = {},
    onMarkMaskingCard: (String, Boolean) -> Unit = { _, _ -> },
    // Interactive checklist overlay. Defaults keep every existing caller unchanged.
    checklists: List<ChecklistCard> = emptyList(),
    showChecklists: Boolean = false,
    onToggleChecklistItem: (String, String) -> Unit = { _, _ -> },
    onHideChecklistOverlay: () -> Unit = {},
    // Original post-it cards. Defaults keep all other callers unchanged; the
    // overlay sits above the pen canvas and consumes its own touches.
    memoCards: List<MemoCard> = emptyList(),
    showMemoCardsOnPage: Boolean = false,
    onOpenMemoCard: (String) -> Unit = {},
    onMemoCardChange: (MemoCard) -> Unit = {},
    onHideMemoCardOverlay: () -> Unit = {},
    onDeleteMemoCard: (String) -> Unit = {},
    backgroundBitmap: Bitmap? = null,
    onAddStroke: (List<NormalizedPoint>, Long) -> Unit,
    onErase: (List<NormalizedPoint>) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onLassoSelect: (polygon: List<NormalizedPoint>, rectangular: Boolean) -> Unit = { _, _ -> },
    onGestureUndo: (() -> Unit)? = null,
    onGestureRedo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // Notebooks are a paper metaphor: keep the chosen sheet tint even in a dark app
    // theme so dark ink and the exported PDF match what is seen here.
    var pan by remember(page.id) { mutableStateOf(Offset.Zero) }
    var zoom by remember(page.id) { mutableStateOf(1f) }

    val pageWidth = PaperBackground.pageWidthPx(NOTE_PAGE_SCALE, page.orientation)
    val pageHeight = PaperBackground.pageHeightPx(NOTE_PAGE_SCALE, page.orientation)
    // Minimum tappable size for a memo card, in pixels. Shared by the hit-test
    // gate below and by the drawn overlay so they agree on every card's bounds.
    val memoDensity = LocalDensity.current
    val minMemoWidthPx = with(memoDensity) { 92.dp.toPx() }
    val minMemoHeightPx = with(memoDensity) { 56.dp.toPx() }

    // The pointerInput coroutine below lives across strokes and does not restart when
    // the parent recomposes. Always read the newest callbacks so a stroke commits onto
    // the latest notebook snapshot rather than the one captured at first composition.
    val currentAddStroke by rememberUpdatedState(onAddStroke)
    val currentErase by rememberUpdatedState(onErase)
    val currentRequestText by rememberUpdatedState(onRequestText)
    val currentLassoSelect by rememberUpdatedState(onLassoSelect)
    val currentGestureUndo by rememberUpdatedState(onGestureUndo)
    val currentGestureRedo by rememberUpdatedState(onGestureRedo)

    val livePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var shapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var shapeEnd by remember { mutableStateOf<NormalizedPoint?>(null) }
    var eraserPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    val eraserTrail = remember { mutableStateListOf<NormalizedPoint>() }
    val lassoPoints = remember { mutableStateListOf<NormalizedPoint>() }

    LaunchedEffect(page.id, page.orientation) {
        livePoints.clear()
        eraserTrail.clear()
        lassoPoints.clear()
        shapeStart = null
        shapeEnd = null
        eraserPoint = null
        pan = Offset.Zero
        zoom = 1f
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fit = min(viewW / pageWidth, viewH / pageHeight)
        val total = fit * zoom

        LaunchedEffect(total, viewW, viewH, pageWidth, pageHeight) {
            val limitX = ((pageWidth * total - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((pageHeight * total - viewH) / 2f).coerceAtLeast(0f)
            val clamped = Offset(
                pan.x.coerceIn(-limitX, limitX),
                pan.y.coerceIn(-limitY, limitY),
            )
            if (clamped != pan) pan = clamped
        }

        // A touch that lands on a visible memo card belongs to that card. The
        // pen gesture must not start a stroke (or pan/zoom/undo) for it, even
        // though the pointer is also delivered to the canvas below the overlay.
        val memoHitTest: (Offset) -> Boolean = { position ->
            showMemoCardsOnPage && memoCards.isNotEmpty() && fit > 0f &&
                memoCards.any { card ->
                    memoScreenRect(
                        card = card,
                        fit = total,
                        pan = pan,
                        viewWidth = viewW,
                        viewHeight = viewH,
                        pageWidth = pageWidth,
                        pageHeight = pageHeight,
                        minWidthPx = minMemoWidthPx,
                        minHeightPx = minMemoHeightPx,
                    ).contains(position)
                }
        }
        val currentMemoHitTest by rememberUpdatedState(memoHitTest)

        val gestureModifier = Modifier.pointerInput(
            page.id,
            page.orientation,
            tool,
            penOnly,
            zoomLock,
            pressureEnabled,
            pressureCurve,
            gestureUndoEnabled,
            fit,
        ) {
            val widthPx = size.width.toFloat().coerceAtLeast(1f)
            val heightPx = size.height.toFloat().coerceAtLeast(1f)

            fun normalize(position: Offset, pressure: Float = 1f): NormalizedPoint {
                val scale = (fit * zoom).coerceAtLeast(0.0001f)
                val x = (position.x - widthPx / 2f - pan.x) / scale + pageWidth / 2f
                val y = (position.y - heightPx / 2f - pan.y) / scale + pageHeight / 2f
                return NormalizedPoint(
                    (x / pageWidth).coerceIn(0f, 1f),
                    (y / pageHeight).coerceIn(0f, 1f),
                    pressure,
                )
            }

            fun clampPan(candidate: Offset, scale: Float): Offset {
                val limitX = ((pageWidth * scale - widthPx) / 2f).coerceAtLeast(0f)
                val limitY = ((pageHeight * scale - heightPx) / 2f).coerceAtLeast(0f)
                return Offset(
                    candidate.x.coerceIn(-limitX, limitX),
                    candidate.y.coerceIn(-limitY, limitY),
                )
            }

            fun clearPreview() {
                livePoints.clear()
                eraserTrail.clear()
                lassoPoints.clear()
                shapeStart = null
                shapeEnd = null
                eraserPoint = null
            }

            var lastTapAt = 0L
            var lastTapPosition = Offset.Zero

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val stylusDown = down.type == PointerType.Stylus
                val eraserStylus = down.type == PointerType.Eraser
                val gestureTool = if (eraserStylus) Tool.ERASER else tool
                val memoIntercepted = currentMemoHitTest(down.position)
                val canDraw = !memoIntercepted && gestureTool != Tool.VIEW &&
                    (!penOnly || stylusDown || eraserStylus)
                var drawing = canDraw
                var tapCandidate = !canDraw && !memoIntercepted
                val drawingSeen = canDraw
                var stylusSeen = stylusDown || eraserStylus
                var maxPointers = 1
                var traveled = 0f
                var navigationMovement = 0f
                var viewChanged = false
                var lastPosition = down.position
                val gestureStart = SystemClock.uptimeMillis()
                val strokeStartMs = SystemClock.elapsedRealtime()
                val gestureStartPan = pan
                val gestureStartZoom = zoom

                // Raw pressure is tracked only for the concise trace below. The
                // stored point always receives the selected curve's effective value.
                var rawPressureMin = Float.POSITIVE_INFINITY
                var rawPressureMax = Float.NEGATIVE_INFINITY
                fun capturePressure(change: PointerInputChange, stylus: Boolean): Float {
                    if (!pressureEnabled || !stylus) return 1f
                    val raw = clampInkPressure(change.pressure)
                    if (change.pressed) {
                        rawPressureMin = minOf(rawPressureMin, raw)
                        rawPressureMax = maxOf(rawPressureMax, raw)
                    }
                    return pressureCurve.applyTo(raw)
                }

                val downPressure = capturePressure(down, stylusDown)

                if (canDraw) {
                    down.consume()
                    when (gestureTool) {
                        Tool.TEXT -> Unit
                        Tool.ERASER -> {
                            val point = normalize(down.position, downPressure)
                            eraserTrail.clear()
                            eraserTrail.add(point)
                            eraserPoint = point
                        }

                        Tool.RECT,
                        Tool.ARROW,
                        Tool.LINE,
                        Tool.ELLIPSE,
                        Tool.UNDERLINE,
                        Tool.STRIKEOUT,
                        Tool.REDACT,
                        Tool.LASSO_RECT -> {
                            val point = normalize(down.position, downPressure)
                            shapeStart = point
                            shapeEnd = point
                        }

                        Tool.LASSO -> {
                            lassoPoints.clear()
                            lassoPoints.add(normalize(down.position, downPressure))
                        }

                        else -> {
                            livePoints.clear()
                            livePoints.add(normalize(down.position, downPressure))
                        }
                    }
                }

                while (true) {
                    val event = awaitPointerEvent()
                    if (memoIntercepted) {
                        // The card overlay owns this gesture (tap, move, resize or
                        // menu). Swallow the rest of it so no pan/zoom leaks out.
                        event.changes.firstOrNull { it.id == down.id }?.consume()
                        if (event.changes.none { it.pressed }) break
                        continue
                    }
                    val pressed = event.changes.filter { it.pressed }
                    maxPointers = maxOf(maxPointers, pressed.size)
                    if (event.changes.any {
                            it.type == PointerType.Stylus || it.type == PointerType.Eraser
                        }
                    ) {
                        stylusSeen = true
                    }
                    val primary = event.changes.firstOrNull { it.id == down.id }

                    if (drawing) {
                        if (primary == null) {
                            clearPreview()
                            break
                        }
                        val otherPointerDown = event.changes.any { it.pressed && it.id != down.id }
                        val primaryPressure = capturePressure(primary, stylusDown)

                        // A second finger while drawing with a finger turns the rest of
                        // this gesture into pan/zoom. Never cancel a stylus stroke when a
                        // palm or finger touches the page, and never cancel writing just
                        // because zoom lock is on.
                        if (otherPointerDown && !stylusDown && !eraserStylus && !zoomLock) {
                            clearPreview()
                            drawing = false
                            tapCandidate = false
                            continue
                        }

                        if (primary.pressed) {
                            if (primary.positionChanged()) {
                                traveled += (primary.position - lastPosition).getDistance()
                                lastPosition = primary.position
                                when (gestureTool) {
                                    Tool.TEXT -> Unit
                                    Tool.ERASER -> {
                                        val point = normalize(primary.position, primaryPressure)
                                        eraserTrail.add(point)
                                        eraserPoint = point
                                    }

                                    Tool.RECT,
                                    Tool.ARROW,
                                    Tool.LINE,
                                    Tool.ELLIPSE,
                                    Tool.UNDERLINE,
                                    Tool.STRIKEOUT,
                                    Tool.REDACT,
                                    Tool.LASSO_RECT -> shapeEnd = normalize(primary.position, primaryPressure)

                                    Tool.LASSO -> lassoPoints.add(normalize(primary.position, primaryPressure))

                                    else -> livePoints.add(normalize(primary.position, primaryPressure))
                                }
                                primary.consume()
                            }
                        } else {
                            primary.consume()
                            when (gestureTool) {
                                Tool.TEXT -> {
                                    val movement = (primary.position - down.position).getDistance()
                                    if (movement <= viewConfiguration.touchSlop) {
                                        currentRequestText(normalize(primary.position, primaryPressure))
                                    }
                                }

                                Tool.ERASER -> {
                                    if (eraserTrail.isNotEmpty()) currentErase(eraserTrail.toList())
                                }

                                Tool.RECT,
                                Tool.ARROW,
                                Tool.LINE,
                                Tool.ELLIPSE,
                                Tool.UNDERLINE,
                                Tool.STRIKEOUT,
                                Tool.REDACT -> {
                                    val start = shapeStart
                                    val end = shapeEnd ?: start
                                    if (start != null && end != null) {
                                        val dx = end.x - start.x
                                        val dy = end.y - start.y
                                        if (dx * dx + dy * dy > 0.000025f) {
                                            currentAddStroke(listOf(start, end), 0L)
                                        }
                                    }
                                }

                                Tool.LASSO -> {
                                    if (lassoPoints.size >= 3) {
                                        currentLassoSelect(lassoPoints.toList(), false)
                                    }
                                }

                                Tool.LASSO_RECT -> {
                                    val start = shapeStart
                                    val end = shapeEnd ?: start
                                    if (start != null && end != null) {
                                        val dx = end.x - start.x
                                        val dy = end.y - start.y
                                        if (dx * dx + dy * dy > 0.000025f) {
                                            currentLassoSelect(listOf(start, end), true)
                                        }
                                    }
                                }

                                else -> {
                                    if (livePoints.isNotEmpty()) {
                                        val durationMs = (SystemClock.elapsedRealtime() - strokeStartMs)
                                            .coerceAtLeast(0L)
                                        currentAddStroke(livePoints.toList(), durationMs)
                                    }
                                }
                            }
                            clearPreview()
                            break
                        }
                        continue
                    }

                    // Navigation mode.
                    if (pressed.isEmpty()) break
                    if (pressed.size > 1) tapCandidate = false

                    val navPrimary = primary ?: pressed.first()
                    if (navPrimary.pressed) {
                        traveled += (navPrimary.position - lastPosition).getDistance()
                        lastPosition = navPrimary.position
                    }

                    // Track centroid movement and pinch intent even while zoom is locked,
                    // so a pan/zoom attempt can never be mistaken for an undo tap.
                    val navigationPan = event.calculatePan()
                    val zoomFactor = event.calculateZoom()
                    navigationMovement += navigationPan.getDistance()
                    if (zoomFactor.isFinite() && abs(zoomFactor - 1f) > 0.02f) {
                        viewChanged = true
                    }

                    if (!zoomLock) {
                        val oldZoom = zoom
                        val oldTotal = fit * oldZoom
                        val newZoom = (oldZoom * zoomFactor).coerceIn(1f, MAX_NOTE_ZOOM)
                        val newTotal = fit * newZoom
                        val centroid = event.calculateCentroid(useCurrent = true)
                        val pivot = centroid - Offset(widthPx / 2f, heightPx / 2f)
                        val scaledPan = if (oldTotal > 0.0001f) {
                            pivot - (pivot - pan) * (newTotal / oldTotal)
                        } else {
                            pan
                        }
                        val updatedPan = clampPan(scaledPan + navigationPan, newTotal)
                        if (newZoom != oldZoom) zoom = newZoom
                        if (updatedPan != pan) pan = updatedPan
                    }
                }

                // A memo-card touch has already been consumed above: no stroke,
                // pan, gesture-undo or double-tap reset may come out of it.
                if (memoIntercepted) return@awaitEachGesture

                val gestureEnd = SystemClock.uptimeMillis()
                val gestureDuration = (gestureEnd - gestureStart).coerceAtLeast(0L)

                if (pressureEnabled && stylusSeen &&
                    rawPressureMin.isFinite() && rawPressureMax.isFinite() &&
                    (gestureTool == Tool.PEN || gestureTool == Tool.HIGHLIGHT)
                ) {
                    val range = pressureCurveRange(pressureCurve, rawPressureMin, rawPressureMax)
                    NoteTrace.event(
                        CANVAS_TRACE_TAG,
                        "pressure-range",
                        "curve" to range.curve.name,
                        "rawMin" to range.rawMin,
                        "rawMax" to range.rawMax,
                        "effectiveMin" to range.effectiveMin,
                        "effectiveMax" to range.effectiveMax,
                    )
                }

                val gestureAction = NoteGestureClassifier.classify(
                    NoteGestureSample(
                        maxPointers = maxPointers,
                        durationMs = gestureDuration,
                        movementPx = maxOf(traveled, navigationMovement),
                        touchSlopPx = viewConfiguration.touchSlop,
                        enabled = gestureUndoEnabled,
                        drawingSeen = drawingSeen,
                        stylusInvolved = stylusSeen,
                        viewChanged = viewChanged,
                    ),
                )
                val gestureHandler: (() -> Unit)? = when (gestureAction) {
                    NoteGestureAction.UNDO -> currentGestureUndo
                    NoteGestureAction.REDO -> currentGestureRedo
                    null -> null
                }
                if (gestureAction != null && gestureHandler != null) {
                    // A tap must not leave a sub-slop pan behind.
                    pan = gestureStartPan
                    zoom = gestureStartZoom
                    NoteTrace.event(
                        CANVAS_TRACE_TAG,
                        "gesture",
                        "action" to gestureAction.name,
                        "pointers" to maxPointers,
                    )
                    gestureHandler()
                }

                // Double-tap reset is only armed for a navigation gesture (finger in
                // pen-only mode, or VIEW). A tap during drawing never resets the view.
                if (!drawing && !zoomLock && maxPointers == 1 &&
                    traveled <= viewConfiguration.touchSlop
                ) {
                    val now = SystemClock.uptimeMillis()
                    val closeToPrevious =
                        (down.position - lastTapPosition).getDistance() <
                            viewConfiguration.touchSlop * 3f
                    val recent = now - lastTapAt in 1..400
                    val quick = now - gestureStart <= 350
                    if (recent && closeToPrevious && quick) {
                        pan = Offset.Zero
                        zoom = 1f
                        lastTapAt = 0L
                    } else {
                        lastTapAt = now
                        lastTapPosition = down.position
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("note_canvas")
                .then(gestureModifier),
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                native.scale(total, total)
                native.translate(-pageWidth / 2f, -pageHeight / 2f)

                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = android.graphics.Color.argb(34, 0, 0, 0)
                    style = Paint.Style.FILL
                }
                native.drawRect(6f, 8f, pageWidth + 6f, pageHeight + 8f, shadow)

                PaperBackground.draw(
                    canvas = native,
                    template = page.template,
                    width = pageWidth,
                    height = pageHeight,
                    dark = false,
                    tint = page.tint,
                )
                backgroundBitmap?.let { bitmap ->
                    PaperBackground.drawCustomBackground(
                        canvas = native,
                        bitmap = bitmap,
                        width = pageWidth,
                        height = pageHeight,
                    )
                }

                native.save()
                native.clipRect(0f, 0f, pageWidth, pageHeight)
                AnnotationPainter.draw(
                    canvas = native,
                    annotations = page.annotations,
                    width = pageWidth,
                    height = pageHeight,
                    progress = annotationProgress,
                )

                selectionBounds?.let { drawSelectionBounds(native, it, pageWidth, pageHeight) }

                val previewPoints = livePoints.toList()
                if (previewPoints.isNotEmpty()) {
                    val previewType = if (tool == Tool.HIGHLIGHT) {
                        AnnotationType.HIGHLIGHT
                    } else {
                        AnnotationType.PEN
                    }
                    AnnotationPainter.drawOne(
                        canvas = native,
                        annotation = Annotation(
                            type = previewType,
                            page = 0,
                            color = color,
                            width = noteStrokeWidth(previewType, strokeWidth),
                            points = previewPoints,
                            opacity = if (previewType == AnnotationType.HIGHLIGHT) 1f else opacity,
                            penStyle = if (previewType == AnnotationType.PEN) {
                                penStyle
                            } else {
                                PenStyle.BALLPOINT
                            },
                        ),
                        width = pageWidth,
                        height = pageHeight,
                    )
                }

                val start = shapeStart
                val end = shapeEnd
                val previewType = noteAnnotationTypeForTool(tool)
                if (start != null && end != null && previewType != null) {
                    AnnotationPainter.drawOne(
                        canvas = native,
                        annotation = Annotation(
                            type = previewType,
                            page = 0,
                            color = if (previewType == AnnotationType.REDACT) {
                                android.graphics.Color.BLACK
                            } else {
                                color
                            },
                            width = maxOf(strokeWidth, 0.0025f),
                            points = listOf(start, end),
                            opacity = if (previewType == AnnotationType.REDACT) 1f else opacity,
                        ),
                        width = pageWidth,
                        height = pageHeight,
                    )
                }

                if (tool == Tool.LASSO && lassoPoints.size >= 2) {
                    drawLassoPreview(native, lassoPoints.toList(), rectangular = false, width = pageWidth, height = pageHeight)
                }
                if (tool == Tool.LASSO_RECT && start != null && end != null) {
                    drawLassoPreview(native, listOf(start, end), rectangular = true, width = pageWidth, height = pageHeight)
                }

                val eraser = eraserPoint
                if (eraser != null) {
                    AnnotationPainter.drawEraserPreview(
                        canvas = native,
                        point = NormalizedPoint(
                            eraser.x * pageWidth,
                            eraser.y * pageHeight,
                        ),
                        radiusPx = ERASER_RADIUS_FRACTION * pageWidth,
                    )
                }

                native.restore()
                native.restore()
            }
        }

        // Study-only tape overlays share this exact transform, so they stay pinned
        // to their page rectangle through pan/zoom. Disabled adds no touch targets.
        if (showMaskingCards && maskingCards.isNotEmpty()) {
            // Clipped to the canvas so a panned-off tape never paints over chrome.
            Box(Modifier.fillMaxSize().clipToBounds()) {
                MaskingTapeLayer(
                    cards = maskingCards,
                    fit = total,
                    pan = pan,
                    viewWidth = viewW,
                    viewHeight = viewH,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    onReveal = onRevealMaskingCard,
                    onMark = onMarkMaskingCard,
                )
            }
        }

        // Checklist cards share the same transform. Disabled adds no touch targets,
        // so the pen tools keep the whole page.
        if (showChecklists && checklists.isNotEmpty()) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                ChecklistOverlayLayer(
                    cards = checklists,
                    fit = total,
                    pan = pan,
                    viewWidth = viewW,
                    viewHeight = viewH,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    onToggleItem = onToggleChecklistItem,
                    onHide = onHideChecklistOverlay,
                )
            }
        }

        // Original post-it cards share the exact pen-canvas transform. The
        // hit-test gate above and this layer use the same memoScreenRect helper,
        // so a card can never be drawn under the pen or missed by a tap.
        if (showMemoCardsOnPage && memoCards.isNotEmpty()) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                MemoCardLayer(
                    cards = memoCards,
                    fit = total,
                    pan = pan,
                    viewWidth = viewW,
                    viewHeight = viewH,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    minWidthPx = minMemoWidthPx,
                    minHeightPx = minMemoHeightPx,
                    onOpen = onOpenMemoCard,
                    onCardChange = onMemoCardChange,
                    onHide = onHideMemoCardOverlay,
                    onDelete = onDeleteMemoCard,
                )
            }
        }
    }
}

/** Dashed bounding frame for the current lasso selection. */
private fun drawSelectionBounds(
    canvas: android.graphics.Canvas,
    bounds: NoteSelectionBounds,
    width: Float,
    height: Float,
) {
    val left = bounds.left * width
    val top = bounds.top * height
    val right = bounds.right * width
    val bottom = bounds.bottom * height
    if (right - left < 0.5f || bottom - top < 0.5f) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 31, 78, 121)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    canvas.drawRect(left, top, right, bottom, paint)
}

/** Subtle dashed selection preview while the user drags a lasso. */
private fun drawLassoPreview(
    canvas: android.graphics.Canvas,
    points: List<NormalizedPoint>,
    rectangular: Boolean,
    width: Float,
    height: Float,
) {
    if (points.size < 2) return
    val path = Path()
    if (rectangular) {
        val a = points.first()
        val b = points.last()
        path.addRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            Path.Direction.CW,
        )
    } else {
        path.moveTo(points.first().x * width, points.first().y * height)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * width, points[i].y * height)
        }
        path.close()
    }
    if (rectangular || points.size >= 3) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(24, 31, 78, 121)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fill)
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(190, 31, 78, 121)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 7f), 0f)
    }
    canvas.drawPath(path, stroke)
}

internal fun noteStrokeWidth(type: AnnotationType, strokeWidth: Float): Float =
    if (type == AnnotationType.HIGHLIGHT) maxOf(strokeWidth, 0.018f) else strokeWidth

internal fun noteAnnotationTypeForTool(tool: Tool): AnnotationType? = when (tool) {
    Tool.PEN -> AnnotationType.PEN
    Tool.HIGHLIGHT -> AnnotationType.HIGHLIGHT
    Tool.TEXT -> AnnotationType.TEXT
    Tool.RECT -> AnnotationType.RECT
    Tool.ARROW -> AnnotationType.ARROW
    Tool.LINE -> AnnotationType.LINE
    Tool.ELLIPSE -> AnnotationType.ELLIPSE
    Tool.UNDERLINE -> AnnotationType.UNDERLINE
    Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
    Tool.REDACT -> AnnotationType.REDACT
    else -> null
}
