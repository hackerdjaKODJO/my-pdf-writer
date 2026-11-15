package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.min
import kotlin.math.roundToInt

private class CanvasTransform {
    var total = 1f
    var fit = 1f
    var zoom = 1f
    var pan = Offset.Zero
    var pageW = 1f
    var pageH = 1f
    var viewW = 1f
    var viewH = 1f

    fun normalize(position: Offset): NormalizedPoint {
        val x = (position.x - viewW / 2f - pan.x) / total + pageW / 2f
        val y = (position.y - viewH / 2f - pan.y) / total + pageH / 2f
        return NormalizedPoint((x / pageW).coerceIn(0f, 1f), (y / pageH).coerceIn(0f, 1f))
    }
}

@Composable
fun PdfCanvas(
    bitmap: Bitmap?,
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
    onZoomChange: (Float) -> Unit,
    onAddStroke: (List<NormalizedPoint>) -> Unit,
    onEraseStroke: (List<NormalizedPoint>) -> Unit,
    onRequestText: (NormalizedPoint) -> Unit,
    onRequestStamp: (NormalizedPoint) -> Unit,
    onRequestSignature: (NormalizedPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pageW = (bitmap?.width?.toFloat() ?: (displayWidthPt * 2f)).coerceAtLeast(1f)
    val pageH = (bitmap?.height?.toFloat() ?: (displayHeightPt * 2f)).coerceAtLeast(1f)

    var pan by remember(pageIndex) { mutableStateOf(Offset.Zero) }
    val livePoints = remember { mutableStateListOf<NormalizedPoint>() }
    var shapeStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var shapeEnd by remember { mutableStateOf<NormalizedPoint?>(null) }
    var eraserPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    val eraserTrail = remember { mutableStateListOf<NormalizedPoint>() }
    val transform = remember { CanvasTransform() }

    LaunchedEffect(pageIndex) {
        livePoints.clear()
        eraserTrail.clear()
        shapeStart = null
        shapeEnd = null
        eraserPoint = null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fit = min(viewW / pageW, viewH / pageH)
        val total = fit * zoom

        LaunchedEffect(total, viewW, viewH, pageW, pageH) {
            val limitX = ((pageW * total - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((pageH * total - viewH) / 2f).coerceAtLeast(0f)
            if (pan.x !in -limitX..limitX || pan.y !in -limitY..limitY) {
                pan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
            }
        }

        SideEffect {
            transform.total = total
            transform.fit = fit
            transform.zoom = zoom
            transform.pan = pan
            transform.pageW = pageW
            transform.pageH = pageH
            transform.viewW = viewW
            transform.viewH = viewH
        }

        fun clampPan(candidate: Offset, scale: Float): Offset {
            val limitX = ((pageW * scale - viewW) / 2f).coerceAtLeast(0f)
            val limitY = ((pageH * scale - viewH) / 2f).coerceAtLeast(0f)
            return Offset(candidate.x.coerceIn(-limitX, limitX), candidate.y.coerceIn(-limitY, limitY))
        }

        val gestureModifier = if (tool == Tool.VIEW) {
            Modifier
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, panChange, zoomChange, _ ->
                        val oldTotal = transform.total
                        val newZoom = (transform.zoom * zoomChange).coerceIn(1f, 6f)
                        val newTotal = transform.fit * newZoom
                        val pivot = centroid - Offset(size.width / 2f, size.height / 2f)
                        val scaledPan = if (oldTotal > 0f) {
                            pivot - (pivot - pan) * (newTotal / oldTotal)
                        } else {
                            pan
                        }
                        val updated = clampPan(scaledPan + panChange, newTotal)
                        pan = updated
                        transform.pan = updated
                        if (newZoom != transform.zoom) {
                            transform.zoom = newZoom
                            transform.total = newTotal
                            onZoomChange(newZoom)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            pan = Offset.Zero
                            onZoomChange(1f)
                        },
                    )
                }
        } else {
            Modifier.pointerInput(tool) {
                when (tool) {
                    Tool.TEXT -> detectTapGestures { position ->
                        onRequestText(transform.normalize(position))
                    }

                    Tool.STAMP -> detectTapGestures { position ->
                        onRequestStamp(transform.normalize(position))
                    }

                    Tool.SIGNATURE -> detectTapGestures { position ->
                        onRequestSignature(transform.normalize(position))
                    }

                    Tool.ERASER -> awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        eraserTrail.clear()
                        val start = transform.normalize(down.position)
                        eraserTrail.add(start)
                        eraserPoint = start
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                pressed = false
                            } else if (change.positionChanged()) {
                                val point = transform.normalize(change.position)
                                eraserTrail.add(point)
                                eraserPoint = point
                                change.consume()
                            }
                        }
                        onEraseStroke(eraserTrail.toList())
                        eraserTrail.clear()
                        eraserPoint = null
                    }

                    Tool.RECT,
                    Tool.ARROW,
                    Tool.LINE,
                    Tool.ELLIPSE,
                    Tool.UNDERLINE,
                    Tool.STRIKEOUT,
                    Tool.REDACT -> awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val start = transform.normalize(down.position)
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
                                shapeEnd = transform.normalize(change.position)
                                change.consume()
                            }
                        }
                        val startPoint = shapeStart
                        val endPoint = shapeEnd
                        if (startPoint != null && endPoint != null) {
                            val dx = endPoint.x - startPoint.x
                            val dy = endPoint.y - startPoint.y
                            if (dx * dx + dy * dy > 0.000025f) {
                                onAddStroke(listOf(startPoint, endPoint))
                            }
                        }
                        shapeStart = null
                        shapeEnd = null
                    }

                    else -> awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        livePoints.clear()
                        livePoints.add(transform.normalize(down.position))
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                pressed = false
                            } else if (change.positionChanged()) {
                                livePoints.add(transform.normalize(change.position))
                                change.consume()
                            }
                        }
                        if (livePoints.isNotEmpty()) onAddStroke(livePoints.toList())
                        livePoints.clear()
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            val cx = size.width / 2f + pan.x
            val cy = size.height / 2f + pan.y
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(cx, cy)
                native.scale(total, total)

                val left = -pageW / 2f
                val top = -pageH / 2f

                val shadowPaint = Paint().apply {
                    this.color = android.graphics.Color.argb(38, 0, 0, 0)
                    maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
                }
                native.drawRect(left + 3f, top + 5f, left + pageW + 3f, top + pageH + 5f, shadowPaint)

                if (bitmap != null) {
                    native.drawBitmap(bitmap, left, top, null)
                } else {
                    val paperPaint = Paint().apply { this.color = android.graphics.Color.WHITE }
                    native.drawRect(left, top, left + pageW, top + pageH, paperPaint)
                }

                native.save()
                native.translate(left, top)
                AnnotationPainter.draw(
                    native,
                    annotations.filter { it.page == pageIndex },
                    pageW,
                    pageH,
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
                            width = if (type == AnnotationType.HIGHLIGHT) 0.02f else strokeWidth,
                            points = previewPoints,
                            opacity = if (type == AnnotationType.HIGHLIGHT) 1f else opacity,
                        ),
                        pageW,
                        pageH,
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
                    AnnotationPainter.drawEraserPreview(
                        native,
                        NormalizedPoint(eraser.x * pageW, eraser.y * pageH),
                        0.028f * pageW,
                    )
                }

                native.restore()
                native.restore()
            }
        }
    }
}

private fun annotationTypeForTool(tool: Tool): AnnotationType? = when (tool) {
    Tool.RECT -> AnnotationType.RECT
    Tool.ARROW -> AnnotationType.ARROW
    Tool.LINE -> AnnotationType.LINE
    Tool.ELLIPSE -> AnnotationType.ELLIPSE
    Tool.UNDERLINE -> AnnotationType.UNDERLINE
    Tool.STRIKEOUT -> AnnotationType.STRIKEOUT
    Tool.REDACT -> AnnotationType.REDACT
    else -> null
}
