package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationBounds
import com.hackerdjakodjo.mypdfwriter.pdf.annotationHitTest
import com.hackerdjakodjo.mypdfwriter.pdf.annotationRotationDegrees
import com.hackerdjakodjo.mypdfwriter.pdf.annotationSelectionBounds
import com.hackerdjakodjo.mypdfwriter.pdf.resizeAnnotation
import com.hackerdjakodjo.mypdfwriter.pdf.rotateAnnotation
import com.hackerdjakodjo.mypdfwriter.pdf.translateAnnotation
import kotlin.math.abs

internal const val PDF_ANNOTATION_EDIT_OVERLAY_TAG = "pdf_annotation_edit_overlay"

private enum class AnnotationHandle { NW, NE, SW, SE, ROTATE }

@Composable
internal fun PdfAnnotationEditOverlay(
    annotations: List<Annotation>,
    selectedAnnotationId: String?,
    preview: Annotation?,
    pageWidth: Float,
    pageHeight: Float,
    visibleClip: Rect,
    normalize: (Offset) -> NormalizedPoint,
    toScreen: (NormalizedPoint) -> Offset,
    onSelect: (String?) -> Unit,
    onTransform: (Annotation) -> Unit,
    onPreview: (Annotation?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val touchRadius = with(density) { 24.dp.toPx() }
    val visualRadius = with(density) { 7.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag(PDF_ANNOTATION_EDIT_OVERLAY_TAG)
            .semantics { contentDescription = "주석 편집 캔버스" }
            .pointerInput(annotations, selectedAnnotationId, pageWidth, pageHeight, visibleClip) {
                awaitEachGesture {
                    try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                        if (!visibleClip.contains(down.position)) {
                            down.consume()
                            return@awaitEachGesture
                        }
                        val start = normalize(down.position)
                        val original = annotations.firstOrNull { it.id == selectedAnnotationId }
                        val bounds = original?.let { annotationSelectionBounds(it, pageWidth, pageHeight) }
                        val handle = if (original != null && bounds != null) {
                            handleAt(down.position, bounds, toScreen, touchRadius)
                        } else {
                            null
                        }
                        val mode = handle ?: if (
                            original != null && bounds != null && bounds.contains(start)
                        ) {
                            AnnotationHandle.NW
                        } else {
                            null
                        }
                        var latest = original
                        var moved = false
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                pressed = false
                                if (mode != null) change.consume()
                                break
                            }
                            if (mode != null && change.positionChanged() && original != null && bounds != null) {
                                moved = true
                                change.consume()
                                val current = normalize(change.position)
                                latest = when {
                                    handle == AnnotationHandle.ROTATE -> {
                                        val degrees = annotationRotationDegrees(
                                            start = start,
                                            current = current,
                                            centerX = bounds.centerX,
                                            centerY = bounds.centerY,
                                            pageWidth = pageWidth,
                                            pageHeight = pageHeight,
                                        )
                                        rotateAnnotation(
                                            original,
                                            Math.toRadians(degrees.toDouble()).toFloat(),
                                            pageWidth,
                                            pageHeight,
                                        )
                                    }
                                    handle != null -> {
                                        val target = resizedBounds(
                                            bounds = bounds,
                                            handle = handle,
                                            point = current,
                                            proportional = isSingleAnchorTextLike(original),
                                        )
                                        resizeAnnotation(
                                            original,
                                            bounds,
                                            target,
                                            pageWidth,
                                            pageHeight,
                                        )
                                    }
                                    else -> translateAnnotation(
                                        original,
                                        current.x - start.x,
                                        current.y - start.y,
                                        pageWidth,
                                        pageHeight,
                                    )
                                }
                                onPreview(latest)
                            }
                        }
                        if (mode != null) {
                            if (moved && latest != null && latest != original) onTransform(latest)
                            onPreview(null)
                        } else if (!moved) {
                            val hit = annotations.lastOrNull {
                                annotationHitTest(
                                    annotation = it,
                                    point = start,
                                    pageWidth = pageWidth,
                                    pageHeight = pageHeight,
                                    slack = touchRadius / maxOf(pageWidth, pageHeight),
                                )
                            }
                            onSelect(hit?.id)
                        }
                    } finally {
                        onPreview(null)
                    }
                }
            },
    ) {
        clipRect(
            left = visibleClip.left,
            top = visibleClip.top,
            right = visibleClip.right,
            bottom = visibleClip.bottom,
        ) {
            val selected = preview ?: annotations.firstOrNull { it.id == selectedAnnotationId }
                ?: return@clipRect
            val bounds = annotationSelectionBounds(selected, pageWidth, pageHeight)
                ?: return@clipRect
            val topLeft = toScreen(NormalizedPoint(bounds.left, bounds.top))
            val bottomRight = toScreen(NormalizedPoint(bounds.right, bounds.bottom))
            val rect = Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
            val primary = Color(0xFF1F4E79)
            val handleFill = Color.White
            drawRect(
                color = primary.copy(alpha = 0.10f),
                topLeft = rect.topLeft,
                size = rect.size,
            )
            drawRect(
                color = primary,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = 2f),
            )
            val rotateCenter = Offset(rect.center.x, rect.top - touchRadius * 1.2f)
            drawLine(primary, Offset(rect.center.x, rect.top), rotateCenter, strokeWidth = 2f)
            listOf(
                AnnotationHandle.NW to rect.topLeft,
                AnnotationHandle.NE to rect.topRight,
                AnnotationHandle.SW to rect.bottomLeft,
                AnnotationHandle.SE to rect.bottomRight,
                AnnotationHandle.ROTATE to rotateCenter,
            ).forEach { (_, center) ->
                drawCircle(handleFill, radius = visualRadius, center = center)
                drawCircle(primary, radius = visualRadius, center = center, style = Stroke(width = 2f))
                drawCircle(primary, radius = with(density) { 3.dp.toPx() }, center = center)
            }
        }
    }
}

private fun AnnotationBounds.contains(point: NormalizedPoint): Boolean =
    point.x in left..right && point.y in top..bottom

private fun handleAt(
    position: Offset,
    bounds: AnnotationBounds,
    toScreen: (NormalizedPoint) -> Offset,
    touchRadius: Float,
): AnnotationHandle? {
    val rect = Rect(
        toScreen(NormalizedPoint(bounds.left, bounds.top)),
        toScreen(NormalizedPoint(bounds.right, bounds.bottom)),
    )
    val rotate = Offset(rect.center.x, rect.top - touchRadius * 1.2f)
    val handles = listOf(
        AnnotationHandle.NW to rect.topLeft,
        AnnotationHandle.NE to rect.topRight,
        AnnotationHandle.SW to rect.bottomLeft,
        AnnotationHandle.SE to rect.bottomRight,
        AnnotationHandle.ROTATE to rotate,
    )
    return handles.minByOrNull { (_, center) -> (position - center).getDistance() }
        ?.takeIf { (_, center) -> (position - center).getDistance() <= touchRadius }
        ?.first
}

private fun isSingleAnchorTextLike(annotation: Annotation): Boolean =
    annotation.points.size == 1 && (
        annotation.type == AnnotationType.TEXT ||
            annotation.type == AnnotationType.STAMP ||
            annotation.type == AnnotationType.SIGNATURE
        )

private fun resizedBounds(
    bounds: AnnotationBounds,
    handle: AnnotationHandle,
    point: NormalizedPoint,
    proportional: Boolean,
): AnnotationBounds {
    val minSize = 0.01f
    val left = when (handle) {
        AnnotationHandle.NW, AnnotationHandle.SW -> minOf(point.x, bounds.right - minSize)
        else -> bounds.left
    }
    val right = when (handle) {
        AnnotationHandle.NE, AnnotationHandle.SE -> maxOf(point.x, bounds.left + minSize)
        else -> bounds.right
    }
    val top = when (handle) {
        AnnotationHandle.NW, AnnotationHandle.NE -> minOf(point.y, bounds.bottom - minSize)
        else -> bounds.top
    }
    val bottom = when (handle) {
        AnnotationHandle.SW, AnnotationHandle.SE -> maxOf(point.y, bounds.top + minSize)
        else -> bounds.bottom
    }
    val target = AnnotationBounds(left, top, right, bottom)
    if (!proportional) return target

    val scale = if (
        abs(target.width - bounds.width) >= abs(target.height - bounds.height)
    ) {
        target.width / bounds.width
    } else {
        target.height / bounds.height
    }
    val safeScale = scale.coerceIn(0.05f, 20f)
    val width = bounds.width * safeScale
    val height = bounds.height * safeScale
    return AnnotationBounds(
        left = if (handle == AnnotationHandle.NW || handle == AnnotationHandle.SW) bounds.right - width else bounds.left,
        top = if (handle == AnnotationHandle.NW || handle == AnnotationHandle.NE) bounds.bottom - height else bounds.top,
        right = if (handle == AnnotationHandle.NW || handle == AnnotationHandle.SW) bounds.right else bounds.left + width,
        bottom = if (handle == AnnotationHandle.NW || handle == AnnotationHandle.NE) bounds.bottom else bounds.top + height,
    )
}
