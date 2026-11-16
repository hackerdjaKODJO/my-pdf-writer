package com.hackerdjakodjo.mypdfwriter.ui

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.min

private const val NOTE_PAGE_SCALE = 2.5f
private const val MAX_NOTE_ZOOM = 6f
private const val ERASER_RADIUS_FRACTION = 0.028f

private val notePageWidth = PaperBackground.pageWidthPx(NOTE_PAGE_SCALE)
private val notePageHeight = PaperBackground.pageHeightPx(NOTE_PAGE_SCALE)

/**
 * A template page plus its annotation layer.
 *
 * The paper is painted in page space at 2.5x A4 points; pan/zoom is applied by the
 * native canvas transform. Drawing previews use the same painter as the committed
 * annotations, so what the user sees is what gets saved and exported.
 *
 * Input rules:
 *  - penOnly = true: only a stylus (or a stylus eraser) draws. Fingers pan/pinch.
 *  - penOnly = false: one finger draws; a second finger switches that gesture to
 *    pan/pinch. A stylus always draws and ignores palm/finger contacts.
 *  - zoomLock = true: pan/zoom and double-tap reset are disabled while writing.
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
    onAddStroke: (List<NormalizedPoint>) -> Unit,
    onErase: (List<NormalizedPoint>) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onLassoSelect: (polygon: List<NormalizedPoint>, rectangular: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    // Notebooks are a paper metaphor: keep the sheet cream even in a dark app theme so
    // dark ink and the exported PDF match what is seen here.
    var pan by remember(page.id) { mutableStateOf(Offset.Zero) }
    var zoom by remember(page.id) { mutableStateOf(1f) }

    val livePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var shapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var shapeEnd by remember { mutableStateOf<NormalizedPoint?>(null) }
    var eraserPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    val eraserTrail = remember { mutableStateListOf<NormalizedPoint>() }
    val lassoPoints = remember { mutableStateListOf<NormalizedPoint>() }

    LaunchedEffect(page.id) {
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
        val fit = min(viewW / notePageWidth, viewH / notePageHeight)
        val total = fit * zoom

        LaunchedEffect(total, viewW, viewH) {
            val limitX = ((notePageWidth * total - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((notePageHeight * total - viewH) / 2f).coerceAtLeast(0f)
            val clamped = Offset(
                pan.x.coerceIn(-limitX, limitX),
                pan.y.coerceIn(-limitY, limitY),
            )
            if (clamped != pan) pan = clamped
        }

        val gestureModifier = Modifier.pointerInput(page.id, tool, penOnly, zoomLock, fit) {
            val widthPx = size.width.toFloat().coerceAtLeast(1f)
            val heightPx = size.height.toFloat().coerceAtLeast(1f)

            fun normalize(position: Offset): NormalizedPoint {
                val scale = (fit * zoom).coerceAtLeast(0.0001f)
                val x = (position.x - widthPx / 2f - pan.x) / scale + notePageWidth / 2f
                val y = (position.y - heightPx / 2f - pan.y) / scale + notePageHeight / 2f
                return NormalizedPoint(
                    (x / notePageWidth).coerceIn(0f, 1f),
                    (y / notePageHeight).coerceIn(0f, 1f),
                )
            }

            fun clampPan(candidate: Offset, scale: Float): Offset {
                val limitX = ((notePageWidth * scale - widthPx) / 2f).coerceAtLeast(0f)
                val limitY = ((notePageHeight * scale - heightPx) / 2f).coerceAtLeast(0f)
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
                val canDraw = gestureTool != Tool.VIEW && (!penOnly || stylusDown || eraserStylus)
                var drawing = canDraw
                var tapCandidate = !canDraw
                var maxPointers = 1
                var traveled = 0f
                var lastPosition = down.position
                val gestureStart = SystemClock.uptimeMillis()

                if (canDraw) {
                    down.consume()
                    when (gestureTool) {
                        Tool.TEXT -> Unit
                        Tool.ERASER -> {
                            val point = normalize(down.position)
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
                            val point = normalize(down.position)
                            shapeStart = point
                            shapeEnd = point
                        }

                        Tool.LASSO -> {
                            lassoPoints.clear()
                            lassoPoints.add(normalize(down.position))
                        }

                        else -> {
                            livePoints.clear()
                            livePoints.add(normalize(down.position))
                        }
                    }
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    maxPointers = maxOf(maxPointers, pressed.size)
                    val primary = event.changes.firstOrNull { it.id == down.id }

                    if (drawing) {
                        if (primary == null) {
                            clearPreview()
                            break
                        }
                        val otherPointerDown = event.changes.any { it.pressed && it.id != down.id }

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
                                        val point = normalize(primary.position)
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
                                    Tool.LASSO_RECT -> shapeEnd = normalize(primary.position)

                                    Tool.LASSO -> lassoPoints.add(normalize(primary.position))

                                    else -> livePoints.add(normalize(primary.position))
                                }
                                primary.consume()
                            }
                        } else {
                            primary.consume()
                            when (gestureTool) {
                                Tool.TEXT -> {
                                    val movement = (primary.position - down.position).getDistance()
                                    if (movement <= viewConfiguration.touchSlop) {
                                        onRequestText(normalize(primary.position))
                                    }
                                }

                                Tool.ERASER -> {
                                    if (eraserTrail.isNotEmpty()) onErase(eraserTrail.toList())
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
                                            onAddStroke(listOf(start, end))
                                        }
                                    }
                                }

                                Tool.LASSO -> {
                                    if (lassoPoints.size >= 3) {
                                        onLassoSelect(lassoPoints.toList(), false)
                                    }
                                }

                                Tool.LASSO_RECT -> {
                                    val start = shapeStart
                                    val end = shapeEnd ?: start
                                    if (start != null && end != null) {
                                        val dx = end.x - start.x
                                        val dy = end.y - start.y
                                        if (dx * dx + dy * dy > 0.000025f) {
                                            onLassoSelect(listOf(start, end), true)
                                        }
                                    }
                                }

                                else -> {
                                    if (livePoints.isNotEmpty()) onAddStroke(livePoints.toList())
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

                    if (!zoomLock) {
                        val oldZoom = zoom
                        val oldTotal = fit * oldZoom
                        val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, MAX_NOTE_ZOOM)
                        val newTotal = fit * newZoom
                        val centroid = event.calculateCentroid(useCurrent = true)
                        val pivot = centroid - Offset(widthPx / 2f, heightPx / 2f)
                        val scaledPan = if (oldTotal > 0.0001f) {
                            pivot - (pivot - pan) * (newTotal / oldTotal)
                        } else {
                            pan
                        }
                        val updatedPan = clampPan(scaledPan + event.calculatePan(), newTotal)
                        if (newZoom != oldZoom) zoom = newZoom
                        if (updatedPan != pan) pan = updatedPan
                    }
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

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(size.width / 2f + pan.x, size.height / 2f + pan.y)
                native.scale(total, total)
                native.translate(-notePageWidth / 2f, -notePageHeight / 2f)

                val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = android.graphics.Color.argb(34, 0, 0, 0)
                    style = Paint.Style.FILL
                }
                native.drawRect(6f, 8f, notePageWidth + 6f, notePageHeight + 8f, shadow)

                PaperBackground.draw(
                    canvas = native,
                    template = page.template,
                    width = notePageWidth,
                    height = notePageHeight,
                    dark = false,
                )

                native.save()
                native.clipRect(0f, 0f, notePageWidth, notePageHeight)
                AnnotationPainter.draw(
                    canvas = native,
                    annotations = page.annotations,
                    width = notePageWidth,
                    height = notePageHeight,
                )

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
                        width = notePageWidth,
                        height = notePageHeight,
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
                        width = notePageWidth,
                        height = notePageHeight,
                    )
                }

                if (tool == Tool.LASSO && lassoPoints.size >= 2) {
                    drawLassoPreview(native, lassoPoints.toList(), rectangular = false)
                }
                if (tool == Tool.LASSO_RECT && start != null && end != null) {
                    drawLassoPreview(native, listOf(start, end), rectangular = true)
                }

                val eraser = eraserPoint
                if (eraser != null) {
                    AnnotationPainter.drawEraserPreview(
                        canvas = native,
                        point = NormalizedPoint(
                            eraser.x * notePageWidth,
                            eraser.y * notePageHeight,
                        ),
                        radiusPx = ERASER_RADIUS_FRACTION * notePageWidth,
                    )
                }

                native.restore()
                native.restore()
            }
        }
    }
}

/** Subtle dashed selection preview while the user drags a lasso. */
private fun drawLassoPreview(
    canvas: android.graphics.Canvas,
    points: List<NormalizedPoint>,
    rectangular: Boolean,
) {
    if (points.size < 2) return
    val path = Path()
    if (rectangular) {
        val a = points.first()
        val b = points.last()
        path.addRect(
            minOf(a.x, b.x) * notePageWidth,
            minOf(a.y, b.y) * notePageHeight,
            maxOf(a.x, b.x) * notePageWidth,
            maxOf(a.y, b.y) * notePageHeight,
            Path.Direction.CW,
        )
    } else {
        path.moveTo(points.first().x * notePageWidth, points.first().y * notePageHeight)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * notePageWidth, points[i].y * notePageHeight)
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
