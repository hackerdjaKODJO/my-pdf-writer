package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.DEFAULT_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.HighlightInk
import com.hackerdjakodjo.mypdfwriter.data.InkMotionHistoryBuffer
import com.hackerdjakodjo.mypdfwriter.data.InkMotionSample
import com.hackerdjakodjo.mypdfwriter.data.MAX_INK_PRESSURE
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCalibrator
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.PressureSmoother
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.clampInkPressure
import com.hackerdjakodjo.mypdfwriter.data.pressureCurveRange
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import com.hackerdjakodjo.mypdfwriter.pdf.EraserGeometry
import com.hackerdjakodjo.mypdfwriter.pdf.InkQuality
import com.hackerdjakodjo.mypdfwriter.pdf.StrokeMotionGate
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.min

private const val NOTE_PAGE_SCALE = 2.5f
private const val MAX_NOTE_ZOOM = 6f
private const val CANVAS_TRACE_TAG = "NoteCanvas"

/** Layered drop shadow alphas for a floating sheet (screen only, never exported). */
internal fun sheetShadowAlphas(base: Int, layers: Int): List<Int> {
    val safeBase = base.coerceIn(0, 255)
    if (layers <= 1) return listOf(safeBase)
    return (0 until layers).map { index ->
        val t = 1f - index.toFloat() / layers
        (safeBase * t * t).toInt().coerceIn(0, 255)
    }
}

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
    highlightOpacity: Float = 1f,
    /** Preview stabilization strength; zero is the natural default. */
    stabilizeStrength: Float = 0f,
    gestureUndoEnabled: Boolean = NOTE_GESTURE_UNDO_DEFAULT,
    /** Preview-only refinement settings; commit keeps the editor's own policy. */
    smoothHandwriting: Boolean = NOTE_SMOOTH_HANDWRITING_DEFAULT,
    simplifyPoints: Boolean = NOTE_SIMPLIFY_POINTS_DEFAULT,
    selectionBounds: NoteSelectionBounds? = null,
    /** Active layer controls wet-ink opacity and its position in the layer stack. */
    activeLayerId: String? = null,
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
    onErase: (List<NormalizedPoint>, Float, Boolean) -> Unit,
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
    // EasyNotes accepts a new centerline point only after roughly 1 dp of travel.
    // Using density-aware pixels keeps the feel consistent across phone screens.
    val inkJitterPx = with(memoDensity) { 1.dp.toPx() }

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
    var eraserPressure by remember { mutableFloatStateOf(1f) }
    var eraserPressureSensitive by remember { mutableStateOf(false) }
    val eraserTrail = remember { mutableStateListOf<NormalizedPoint>() }
    val lassoPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var motionCancelled by remember { mutableStateOf(false) }
    val motionHistory = remember { InkMotionHistoryBuffer() }

    LaunchedEffect(page.id, page.orientation) {
        livePoints.clear()
        eraserTrail.clear()
        lassoPoints.clear()
        shapeStart = null
        shapeEnd = null
        eraserPoint = null
        motionCancelled = false
        motionHistory.clear()
        pan = Offset.Zero
        zoom = 1f
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fit = min(viewW / pageWidth, viewH / pageHeight)
        val total = fit * zoom
        val renderedAnnotations = remember(page) { NoteLayers.renderAnnotations(page) }
        val activeLayerIndex = remember(page.layers, activeLayerId) {
            val index = page.layers.indexOfFirst { it.id == activeLayerId }
            if (index >= 0) index else page.layers.lastIndex.coerceAtLeast(0)
        }
        val annotationsBelowActive = remember(renderedAnnotations, activeLayerIndex) {
            renderedAnnotations.filter { annotationLayerIndex(page, it) <= activeLayerIndex }
        }
        val annotationsAboveActive = remember(renderedAnnotations, activeLayerIndex) {
            renderedAnnotations.filter { annotationLayerIndex(page, it) > activeLayerIndex }
        }
        val activeLayerOpacity = remember(page.layers, activeLayerIndex) {
            page.layers.getOrNull(activeLayerIndex)?.opacity?.coerceIn(0f, 1f) ?: 1f
        }

        DisposableEffect(
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
            onDispose {
                livePoints.clear()
                eraserTrail.clear()
                lassoPoints.clear()
                shapeStart = null
                shapeEnd = null
                eraserPoint = null
                        motionCancelled = false
                motionHistory.clear()
            }
        }

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
        val studyOverlayHitTest: (Offset) -> Boolean = { position ->
            memoHitTest(position) ||
                (showMaskingCards && maskingCards.any { card ->
                    studyOverlayScreenRect(
                        card.x, card.y, card.w, card.h, total, pan,
                        viewW, viewH, pageWidth, pageHeight,
                    ).contains(position)
                }) ||
                (showChecklists && checklists.any { card ->
                    studyOverlayScreenRect(
                        card.x, card.y, card.w, card.h, total, pan,
                        viewW, viewH, pageWidth, pageHeight,
                    ).contains(position)
                })
        }
        val currentMemoHitTest by rememberUpdatedState(studyOverlayHitTest)

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

            // One calibrator per input session (this coroutine is recreated when
            // the page/tool/pressure keys above change). It adapts to the pen's
            // reported range across strokes and is never persisted to JSON.
            val pressureCalibrator = PressureCalibrator(bootstrapSamples = 3)
            // Temporal pressure low-pass, reset at every stroke start so each
            // stroke's first sample is exact. Session-scoped, never persisted.
            val pressureSmoother = PressureSmoother()

            fun normalize(
                position: Offset,
                pressure: Float = 1f,
                timeMs: Long? = null,
            ): NormalizedPoint {
                val scale = (fit * zoom).coerceAtLeast(0.0001f)
                val x = (position.x - widthPx / 2f - pan.x) / scale + pageWidth / 2f
                val y = (position.y - heightPx / 2f - pan.y) / scale + pageHeight / 2f
                return NormalizedPoint(
                    (x / pageWidth).coerceIn(0f, 1f),
                    (y / pageHeight).coerceIn(0f, 1f),
                    pressure,
                    timeMs,
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
                motionHistory.clear()
                        livePoints.clear()
                eraserTrail.clear()
                lassoPoints.clear()
                shapeStart = null
                shapeEnd = null
                eraserPoint = null
                eraserPressure = 1f
                eraserPressureSensitive = false
            }

            var lastTapAt = 0L
            var lastTapPosition = Offset.Zero

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                motionHistory.clear()
                motionCancelled = false
                val stylusDown = down.type == PointerType.Stylus
                val eraserStylus = down.type == PointerType.Eraser
                val gestureTool = gestureToolForPointer(tool, down.type)
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
                val motionGate = StrokeMotionGate(inkJitterPx).apply {
                    reset(down.position.x, down.position.y)
                }
                val gestureStart = down.uptimeMillis
                val strokeStartUptimeMs = down.uptimeMillis
                val gestureStartPan = pan
                val gestureStartZoom = zoom

                // Raw pressure is tracked only for the concise trace below. The
                // stored point receives the calibrated, curved effective value.
                // Each sample reads the change's own current pointer type instead of
                // the type latched at pointer-down, so a stylus that starts writing
                // part-way through a finger stroke is sampled too.
                var rawPressureMin = Float.POSITIVE_INFINITY
                var rawPressureMax = Float.NEGATIVE_INFINITY
                var lastPressureUptimeMs = strokeStartUptimeMs
                var previousEffectivePressure = DEFAULT_INK_PRESSURE
                fun capturePressureValue(
                    pointerType: PointerType,
                    rawPressure: Float,
                    pressed: Boolean,
                    eventUptimeMs: Long,
                ): Float {
                    val elapsedMs = (eventUptimeMs - lastPressureUptimeMs).coerceAtLeast(0L)
                    lastPressureUptimeMs = maxOf(lastPressureUptimeMs, eventUptimeMs)
                    val effective = safeEffectiveInkPressure(
                        pointerType = pointerType,
                        rawPressure = rawPressure,
                        pressureEnabled = pressureEnabled,
                        pressureCurve = pressureCurve,
                        calibrator = pressureCalibrator,
                        pressed = pressed,
                        smoother = pressureSmoother,
                        elapsedMs = elapsedMs,
                        previousEffectivePressure = previousEffectivePressure,
                    )
                    previousEffectivePressure = effective
                    if (pressureEnabled && pointerType == PointerType.Stylus &&
                        pressed && rawPressure.isFinite()
                    ) {
                        val raw = clampInkPressure(rawPressure)
                        rawPressureMin = minOf(rawPressureMin, raw)
                        rawPressureMax = maxOf(rawPressureMax, raw)
                    }
                    return effective
                }

                fun capturePressure(
                    change: PointerInputChange,
                    eventUptimeMs: Long = change.uptimeMillis,
                ): Float = capturePressureValue(
                    pointerType = change.type,
                    rawPressure = change.pressure,
                    pressed = change.pressed,
                    eventUptimeMs = eventUptimeMs,
                )

                // A memo tap, a navigation gesture or a VIEW touch never writes ink,
                // so it must not teach the calibrator anything either. Sampling those
                // pointers used to drag the learned minimum toward the pointer-up
                // pressure of zero and make every later stroke render too bold.
                // Do NOT reset here: the calibrator is scoped to the pointerInput
                // session and must keep the pen's learned range across strokes,
                // otherwise every stroke starts with the wrong ink weight.
                // The temporal smoother, in contrast, starts fresh every stroke.
                if (canDraw) pressureSmoother.reset()
                val downPressure = if (canDraw) capturePressure(down) else DEFAULT_INK_PRESSURE
                var lastPressure = downPressure
                var lastEmittedAtUptimeMs = strokeStartUptimeMs
                var stationaryEmitted = 0
                var lastPrimaryChange: PointerInputChange? = null

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
                            elapsedMs = (nowUptimeMs - lastEmittedAtUptimeMs).coerceAtLeast(0L),
                            emitted = stationaryEmitted,
                        )
                    ) {
                        return
                    }
                    stationaryEmitted++
                    livePoints.add(
                        normalize(
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

                fun emitMotionSample(
                    sample: InkMotionSample,
                    pointerType: PointerType,
                ) {
                    if (sample.uptimeMs < strokeStartUptimeMs ||
                        sample.uptimeMs <= lastEmittedAtUptimeMs
                    ) {
                        return
                    }
                    val position = Offset(sample.x, sample.y)
                    if (!motionGate.accept(sample.x, sample.y)) return
                    val movement = position - lastPosition
                    traveled += movement.getDistance()
                    lastPosition = position
                    val pointTimeMs = relativePointTimeMs(
                        sample.uptimeMs,
                        strokeStartUptimeMs,
                        lastEmittedAtUptimeMs - strokeStartUptimeMs,
                    )
                    val pressure = capturePressureValue(
                        pointerType,
                        sample.pressure,
                        pressed = true,
                        eventUptimeMs = sample.uptimeMs,
                    )
                    when (gestureTool) {
                        Tool.TEXT -> Unit
                        Tool.ERASER -> {
                            eraserPressure = eraserInputPressure(pointerType, sample.pressure, pressureEnabled)
                            eraserPressureSensitive = eraserUsesPressure(pointerType, pressureEnabled)
                            val point = normalize(position, eraserPressure, pointTimeMs)
                            eraserTrail.add(point)
                            eraserPoint = point
                        }

                        Tool.RECT,

                        Tool.TRIANGLE,
                        Tool.ARROW,
                        Tool.LINE,
                        Tool.ELLIPSE,
                        Tool.UNDERLINE,
                        Tool.STRIKEOUT,
                        Tool.REDACT,
                        Tool.LASSO_RECT -> shapeEnd = normalize(position, pressure, pointTimeMs)

                        Tool.LASSO -> lassoPoints.add(normalize(position, pressure, pointTimeMs))

                        else -> livePoints.add(normalize(position, pressure, pointTimeMs))
                    }
                    lastPressure = pressure
                    lastEmittedAtUptimeMs = maxOf(lastEmittedAtUptimeMs, sample.uptimeMs)
                }

                if (canDraw) {
                    down.consume()
                    when (gestureTool) {
                        Tool.TEXT -> Unit
                        Tool.ERASER -> {
                            eraserPressure = eraserInputPressure(down.type, down.pressure, pressureEnabled)
                            eraserPressureSensitive = eraserUsesPressure(down.type, pressureEnabled)
                            val point = normalize(
                                down.position,
                                eraserPressure,
                                relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                            )
                            eraserTrail.clear()
                            eraserTrail.add(point)
                            eraserPoint = point
                        }

                        Tool.RECT,

                        Tool.TRIANGLE,
                        Tool.ARROW,
                        Tool.LINE,
                        Tool.ELLIPSE,
                        Tool.UNDERLINE,
                        Tool.STRIKEOUT,
                        Tool.REDACT,
                        Tool.LASSO_RECT -> {
                            val point = normalize(
                                down.position,
                                downPressure,
                                relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                            )
                            shapeStart = point
                            shapeEnd = point
                        }

                        Tool.LASSO -> {
                            lassoPoints.clear()
                            lassoPoints.add(
                                normalize(
                                    down.position,
                                    downPressure,
                                    relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                                ),
                            )
                        }

                        else -> {
                            livePoints.clear()
                            livePoints.add(
                                normalize(
                                    down.position,
                                    downPressure,
                                    relativePointTimeMs(down.uptimeMillis, strokeStartUptimeMs),
                                ),
                            )
                        }
                    }
                }

                while (true) {
                    val event = withTimeoutOrNull(InkQuality.MIN_STATIONARY_SAMPLE_MS) {
                        awaitPointerEvent()
                    }
                    if (event == null) {
                        // A perfectly stationary stylus may produce no MotionEvent
                        // at all. Wake on the dwell cadence and lay down bounded
                        // samples from the last real pointer state.
                        val dwell = lastPrimaryChange
                        if (drawing && dwell != null && dwell.pressed && pressureEnabled &&
                            dwell.type == PointerType.Stylus &&
                            (gestureTool == Tool.PEN || gestureTool == Tool.HIGHLIGHT)
                        ) {
                            val now = SystemClock.uptimeMillis()
                            val pressure = capturePressure(dwell, now)
                            emitStationarySample(dwell, pressure, now)
                        }
                        continue
                    }
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
                    if (primary != null) lastPrimaryChange = primary

                    if (drawing && primary != null) {
                        val action = recordMotionEvent(event, primary, motionHistory)
                        if (action == MotionEvent.ACTION_CANCEL) motionCancelled = true
                        if (!motionCancelled) {
                            val historical = motionHistory.samplesBefore(primary.uptimeMillis)
                            historical.forEach { emitMotionSample(it, primary.type) }
                            // EasyNotes-style natural ink uses only observed samples.
                            // The buffer's prediction API remains available, but the
                            // note canvas never feeds predicted points into geometry.
                            // Compose's current sample represents the same physical
                            // frame as MotionEvent's current sample. Drop the raw
                            // current sample so clock skew cannot replay it later.
                            motionHistory.clear()
                        }
                    }

                    if (motionCancelled) {
                        clearPreview()
                        break
                    }

                    if (drawing) {
                        if (primary == null) {
                            clearPreview()
                            break
                        }
                        val otherPointerDown = event.changes.any { it.pressed && it.id != down.id }
                        val primaryPressure = capturePressure(primary)

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
                            if (
                                (gestureTool == Tool.PEN || gestureTool == Tool.HIGHLIGHT) &&
                                primary.uptimeMillis <= lastEmittedAtUptimeMs
                            ) {
                                primary.consume()
                                continue
                            }
                            val movement = primary.position - lastPosition
                            if (motionGate.accept(primary.position.x, primary.position.y)) {
                                traveled += movement.getDistance()
                                lastPosition = primary.position
                                val pointTimeMs = relativePointTimeMs(
                                    primary.uptimeMillis,
                                    strokeStartUptimeMs,
                                    lastEmittedAtUptimeMs - strokeStartUptimeMs,
                                )
                                when (gestureTool) {
                                    Tool.TEXT -> Unit
                                    Tool.ERASER -> {
                                        eraserPressure = eraserInputPressure(primary.type, primary.pressure, pressureEnabled)
                                        eraserPressureSensitive = eraserUsesPressure(primary.type, pressureEnabled)
                                        val point = normalize(primary.position, eraserPressure, pointTimeMs)
                                        eraserTrail.add(point)
                                        eraserPoint = point
                                    }

                                    Tool.RECT,

                                    Tool.TRIANGLE,
                                    Tool.ARROW,
                                    Tool.LINE,
                                    Tool.ELLIPSE,
                                    Tool.UNDERLINE,
                                    Tool.STRIKEOUT,
                                    Tool.REDACT,
                                    Tool.LASSO_RECT -> shapeEnd = normalize(
                                        primary.position,
                                        primaryPressure,
                                        pointTimeMs,
                                    )

                                    Tool.LASSO -> lassoPoints.add(
                                        normalize(primary.position, primaryPressure, pointTimeMs),
                                    )

                                    else -> {
                                        livePoints.add(
                                            normalize(primary.position, primaryPressure, pointTimeMs),
                                        )
                                        lastEmittedAtUptimeMs = maxOf(
                                            lastEmittedAtUptimeMs,
                                            primary.uptimeMillis,
                                        )
                                    }
                                }
                                lastPressure = primaryPressure
                                primary.consume()
                            } else {
                                // Slow intentional movement below the 1px gate is
                                // accumulated against the last accepted point. Only a
                                // truly stationary nib (<0.1px) emits dwell ink.
                                if (movement.getDistance() < 0.1f) {
                                    emitStationarySample(primary, primaryPressure, primary.uptimeMillis)
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
                                    if (eraserTrail.isNotEmpty()) {
                                        currentErase(
                                            EraserGeometry.densifyTrail(eraserTrail.toList()),
                                            eraserPressure,
                                            eraserPressureSensitive,
                                        )
                                    }
                                }

                                Tool.RECT,

                                Tool.TRIANGLE,
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
                                        val durationMs = inkStrokeDurationMs(livePoints)
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

        // Dry layer: paper, background and committed ink. It does not observe live
        // pointer state, so Compose can skip it while the wet layer follows the pen.
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

                sheetShadowAlphas(34, 3).forEachIndexed { layer, alpha ->
                    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.argb(alpha, 0, 0, 0)
                        style = Paint.Style.FILL
                    }
                    val offsetX = 6f + 2f * layer
                    val offsetY = 8f + 2f * layer
                    native.drawRect(
                        offsetX,
                        offsetY,
                        pageWidth + offsetX,
                        pageHeight + offsetY,
                        shadow,
                    )
                }

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
                    annotations = annotationsBelowActive,
                    width = pageWidth,
                    height = pageHeight,
                    progress = annotationProgress,
                )
                native.restore()
                native.restore()
            }
        }

        // Wet layer: only the active stroke/tool preview. Pointer samples invalidate
        // this layer instead of repainting the paper and every committed annotation.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                native.scale(total, total)
                native.translate(-pageWidth / 2f, -pageHeight / 2f)
                native.clipRect(0f, 0f, pageWidth, pageHeight)

                val previewPoints = notePreviewPoints(
                    livePoints.toList(),
                    stabilizeStrength = stabilizeStrength,
                    smoothHandwriting = smoothHandwriting,
                    simplifyPoints = simplifyPoints,
                )
                if (previewPoints.isNotEmpty()) {
                    val previewType = if (tool == Tool.HIGHLIGHT) {
                        AnnotationType.HIGHLIGHT
                    } else {
                        AnnotationType.PEN
                    }
                    AnnotationPainter.drawOne(
                        canvas = native,
                        annotation = notePreviewAnnotation(
                            type = previewType,
                            color = color,
                            width = noteStrokeWidth(previewType, strokeWidth),
                            points = previewPoints,
                            opacity = if (previewType == AnnotationType.HIGHLIGHT) {
                                HighlightInk.clampOpacity(highlightOpacity)
                            } else {
                                opacity
                            },
                            penStyle = if (previewType == AnnotationType.PEN) {
                                penStyle
                            } else {
                                PenStyle.BALLPOINT
                            },
                            layerOpacity = activeLayerOpacity,
                        ),
                        width = pageWidth,
                        height = pageHeight,
                        livePreview = true,
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
                            opacity = (
                                (if (previewType == AnnotationType.REDACT) 1f else opacity) *
                                    activeLayerOpacity
                            ).coerceIn(0f, 1f),
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
                    drawSharedEraserPreview(
                        native,
                        eraser,
                        pageWidth,
                        pageHeight,
                        pressureSensitive = eraserPressureSensitive,
                    )
                }

                native.restore()
            }
        }

        // Committed layers above the active layer stay above wet ink. They are dry
        // and do not observe pointer state, so a lower-layer preview cannot cover
        // opaque upper-layer content or jump on commit.
        if (annotationsAboveActive.isNotEmpty() || selectionBounds != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                    native.scale(total, total)
                    native.translate(-pageWidth / 2f, -pageHeight / 2f)
                    native.clipRect(0f, 0f, pageWidth, pageHeight)
                    AnnotationPainter.draw(
                        canvas = native,
                        annotations = annotationsAboveActive,
                        width = pageWidth,
                        height = pageHeight,
                        progress = annotationProgress,
                    )
                    selectionBounds?.let { drawSelectionBounds(native, it, pageWidth, pageHeight) }
                    native.restore()
                }
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

/**
 * Page-space bounds of an eraser preview. The radius comes from the shared
 * [EraserGeometry] kernel, applied to each axis, so a preview can never disagree
 * with the strokes it is about to remove on portrait or landscape pages.
 */
internal data class NoteEraserPreviewOval(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal fun noteEraserPreviewOval(
    center: NormalizedPoint,
    pageWidth: Float,
    pageHeight: Float,
    radius: Float = EraserGeometry.radiusForPressure(
        EraserGeometry.NORMALIZED_RADIUS,
        center.pressure,
        pressureSensitive = false,
    ),
): NoteEraserPreviewOval {
    val centerX = center.x * pageWidth
    val centerY = center.y * pageHeight
    val radiusX = radius * pageWidth
    val radiusY = radius * pageHeight
    return NoteEraserPreviewOval(
        left = centerX - radiusX,
        top = centerY - radiusY,
        right = centerX + radiusX,
        bottom = centerY + radiusY,
    )
}

/** One visual kernel for note and PDF eraser previews. */
internal fun drawSharedEraserPreview(
    canvas: android.graphics.Canvas,
    center: NormalizedPoint,
    pageWidth: Float,
    pageHeight: Float,
    pressureSensitive: Boolean,
) {
    val radius = EraserGeometry.radiusForPressure(
        EraserGeometry.NORMALIZED_RADIUS,
        center.pressure,
        pressureSensitive,
    )
    val oval = noteEraserPreviewOval(center, pageWidth, pageHeight, radius)
    val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = 2f
        this.color = android.graphics.Color.argb(190, 70, 70, 70)
    }
    canvas.drawOval(oval.left, oval.top, oval.right, oval.bottom, outline)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = android.graphics.Color.argb(40, 70, 70, 70)
    }
    canvas.drawOval(oval.left, oval.top, oval.right, oval.bottom, fillPaint)
}

private fun annotationLayerIndex(page: NotePage, annotation: Annotation): Int {
    val effectiveId = annotation.layerId ?: page.layers.firstOrNull()?.id
    val index = page.layers.indexOfFirst { it.id == effectiveId }
    return if (index >= 0) index else 0
}

/** Stylus eraser contact overrides the selected drawing tool on both canvases. */
internal fun gestureToolForPointer(selectedTool: Tool, pointerType: PointerType): Tool =
    if (pointerType == PointerType.Eraser) Tool.ERASER else selectedTool

private fun motionPointerIndex(motion: MotionEvent, primary: PointerInputChange): Int {
    if (motion.pointerCount <= 0) return -1
    val expectedToolType = when (primary.type) {
        PointerType.Stylus -> MotionEvent.TOOL_TYPE_STYLUS
        PointerType.Eraser -> MotionEvent.TOOL_TYPE_ERASER
        PointerType.Mouse -> MotionEvent.TOOL_TYPE_MOUSE
        else -> MotionEvent.TOOL_TYPE_FINGER
    }
    val matches = (0 until motion.pointerCount).filter { index ->
        motion.getToolType(index) == expectedToolType
    }
    if (matches.isEmpty()) return -1
    if (matches.size == 1) return matches.first()
    val actionIndex = motion.actionIndex.takeIf { it in matches }
    return actionIndex ?: matches.first()
}

/**
 * Copies coalesced platform history into the stroke buffer. The raw MotionEvent may
 * be in root or local coordinates, so the current sample's difference from Compose's
 * local position gives the exact offset to apply to every historical sample.
 */
private fun recordMotionEvent(
    event: PointerEvent,
    primary: PointerInputChange,
    buffer: InkMotionHistoryBuffer,
): Int {
    val motion = event.motionEvent
        ?: return if (primary.pressed) MotionEvent.ACTION_MOVE else MotionEvent.ACTION_CANCEL
    val action = motion.actionMasked
    if (action == MotionEvent.ACTION_CANCEL) {
        buffer.clear()
        return action
    }
    val pointerIndex = motionPointerIndex(motion, primary)
    if (pointerIndex !in 0 until motion.pointerCount) return action
    val offset = primary.position - Offset(motion.getX(pointerIndex), motion.getY(pointerIndex))
    for (historyIndex in 0 until motion.historySize) {
        buffer.append(
            InkMotionSample(
                x = motion.getHistoricalX(pointerIndex, historyIndex) + offset.x,
                y = motion.getHistoricalY(pointerIndex, historyIndex) + offset.y,
                pressure = motion.getHistoricalPressure(pointerIndex, historyIndex),
                uptimeMs = motion.getHistoricalEventTime(historyIndex),
            ),
        )
    }
    // The up event's current pressure is synthetic zero. Keep its history, but let
    // Compose's release branch handle the lift without teaching calibration.
    if (action == MotionEvent.ACTION_MOVE) {
        buffer.append(
            InkMotionSample(
                x = motion.getX(pointerIndex) + offset.x,
                y = motion.getY(pointerIndex) + offset.y,
                pressure = motion.getPressure(pointerIndex),
                uptimeMs = motion.eventTime,
            ),
        )
    }
    return action
}

/** Compose reports the stylus back end as ERASER rather than STYLUS. */
internal fun isStylusPointer(pointerType: PointerType): Boolean =
    pointerType == PointerType.Stylus || pointerType == PointerType.Eraser

internal fun eraserUsesPressure(pointerType: PointerType, pressureEnabled: Boolean): Boolean =
    pressureEnabled && isStylusPointer(pointerType)

/** Raw eraser pressure is independent of the pen curve and calibration. */
internal fun eraserInputPressure(
    pointerType: PointerType,
    rawPressure: Float,
    pressureEnabled: Boolean,
): Float = if (eraserUsesPressure(pointerType, pressureEnabled)) {
    clampInkPressure(rawPressure)
} else {
    DEFAULT_INK_PRESSURE
}

/** Pointer uptime relative to down, safe for persistence and replay. */
internal fun relativePointTimeMs(
    eventUptimeMs: Long,
    strokeStartUptimeMs: Long,
    minimumTimeMs: Long = 0L,
): Long = (eventUptimeMs - strokeStartUptimeMs)
    .coerceAtLeast(minimumTimeMs.coerceAtLeast(0L))

/** Duration follows the last emitted sample rather than a later idle up frame. */
internal fun inkStrokeDurationMs(points: List<NormalizedPoint>): Long =
    points.lastOrNull()?.timeMs?.coerceAtLeast(0L) ?: 0L

/**
 * Effective ink pressure for one captured pointer sample.
 *
 * Only a stylus carries pressure: fingers, mice, erasers and unknown pointers
 * always store the neutral [DEFAULT_INK_PRESSURE]. The enable gate, the optional
 * [calibrator] (which learns this stylus's reported range) and the
 * [pressureCurve] are all applied here, once, at capture time; the renderer
 * never re-applies either step. Calibration is applied to the raw sample before
 * the curve so a pen reporting only 0.2..0.7 can still reach full response.
 * Passing a null calibrator keeps the original clamped-curve behaviour.
 * Reading [pointerType] from the current sample (not from a type latched at
 * pointer-down) keeps a stylus that joins an already started finger stroke
 * pressure-aware.
 *
 * A sample is only allowed to teach [calibrator] while the pointer is actually
 * down. Compose reports pointer-up with `pressure = 0` and `pressed = false`;
 * feeding that synthetic sample into the learned range would pull the observed
 * minimum toward zero and make every later stroke render too bold. For an up
 * sample the raw value is still curved (TEXT taps use it) but the calibrator is
 * left untouched.
 *
 * An optional [smoother] low-pass filters the final effective value using
 * [elapsedMs] since the previous sample (digitizer noise cannot shimmer the
 * ink). It runs AFTER calibration and the curve so the learned raw range is
 * never distorted, only sees pressed stylus samples, and must be reset by the
 * caller at each stroke start. A null smoother keeps the original behaviour.
 * Non-finite raw pressure returns neutral without reaching the calibrator,
 * curve or smoother, so one bad sensor frame cannot reset learned state.
 */
internal fun effectiveInkPressure(
    pointerType: PointerType,
    rawPressure: Float,
    pressureEnabled: Boolean,
    pressureCurve: PressureCurve,
    calibrator: PressureCalibrator? = null,
    pressed: Boolean = true,
    smoother: PressureSmoother? = null,
    elapsedMs: Long = PressureSmoother.NOMINAL_FRAME_MS,
): Float {
    if (!pressureEnabled || pointerType != PointerType.Stylus) return DEFAULT_INK_PRESSURE
    if (!rawPressure.isFinite()) return DEFAULT_INK_PRESSURE
    val calibrated = if (calibrator != null && pressed) {
        if (rawPressure.isFinite() && rawPressure >= MAX_INK_PRESSURE) {
            // A true full-press sample still stores neutral 1f, but it teaches
            // the upper bound so the next light sample maps correctly.
            calibrator.observeExtreme(rawPressure)
            DEFAULT_INK_PRESSURE
        } else {
            calibrator.map(rawPressure)
        }
    } else {
        clampInkPressure(rawPressure)
    }
    val curved = pressureCurve.applyTo(calibrated)
    return if (smoother != null && pressed) smoother.filter(curved, elapsedMs) else curved
}

internal fun notePreviewAnnotation(
    type: AnnotationType,
    color: Int,
    width: Float,
    points: List<NormalizedPoint>,
    opacity: Float,
    penStyle: PenStyle,
    layerOpacity: Float = 1f,
): Annotation = Annotation(
    type = type,
    page = 0,
    color = color,
    width = width,
    points = points,
    opacity = (opacity * layerOpacity).coerceIn(0f, 1f),
    penStyle = penStyle,
    timesMs = pointTimesMs(points),
)

internal fun safeEffectiveInkPressure(
    pointerType: PointerType,
    rawPressure: Float,
    pressureEnabled: Boolean,
    pressureCurve: PressureCurve,
    calibrator: PressureCalibrator? = null,
    pressed: Boolean = true,
    smoother: PressureSmoother? = null,
    elapsedMs: Long = PressureSmoother.NOMINAL_FRAME_MS,
    previousEffectivePressure: Float = DEFAULT_INK_PRESSURE,
): Float {
    if (!pressureEnabled || pointerType != PointerType.Stylus) return DEFAULT_INK_PRESSURE
    if (!rawPressure.isFinite()) return clampInkPressure(previousEffectivePressure)
    return effectiveInkPressure(
        pointerType = pointerType,
        rawPressure = rawPressure,
        pressureEnabled = pressureEnabled,
        pressureCurve = pressureCurve,
        calibrator = calibrator,
        pressed = pressed,
        smoother = smoother,
        elapsedMs = elapsedMs,
    )
}

internal fun noteStrokeWidth(type: AnnotationType, strokeWidth: Float): Float =
    if (type == AnnotationType.HIGHLIGHT) maxOf(strokeWidth, 0.018f) else strokeWidth

internal fun noteAnnotationTypeForTool(tool: Tool): AnnotationType? = when (tool) {
    Tool.PEN -> AnnotationType.PEN
    Tool.HIGHLIGHT -> AnnotationType.HIGHLIGHT
    Tool.TEXT -> AnnotationType.TEXT
    Tool.RECT -> AnnotationType.RECT
    Tool.TRIANGLE -> AnnotationType.TRIANGLE
    Tool.ARROW -> AnnotationType.ARROW
    Tool.LINE -> AnnotationType.LINE
    Tool.ELLIPSE -> AnnotationType.ELLIPSE
    Tool.UNDERLINE -> AnnotationType.UNDERLINE
    Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
    Tool.REDACT -> AnnotationType.REDACT
    else -> null
}
