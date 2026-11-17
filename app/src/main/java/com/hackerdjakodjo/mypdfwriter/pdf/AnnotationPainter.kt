package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.inkPressureWidthFactor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Paints the annotation layer with the platform canvas.
 *
 * The same code is used for the on-screen overlay and for export, so the exported PDF
 * is pixel-faithful to what the student sees (including Korean text, stamps, signatures
 * and redaction blackouts). [progress] lets replay reveal each annotation gradually;
 * callers that want the normal result keep the default of `1f`.
 */
object AnnotationPainter {

    fun draw(
        canvas: Canvas,
        annotations: List<Annotation>,
        width: Float,
        height: Float,
        progress: (Annotation) -> Float = { 1f },
    ) {
        annotations.forEach { drawOne(canvas, it, width, height, progress(it)) }
    }

    fun drawOne(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float = 1f,
    ) {
        if (annotation.points.isEmpty()) return
        val reveal = fraction.coerceIn(0f, 1f)
        if (reveal <= 0f) return
        if (reveal < 1f) {
            drawPartial(canvas, annotation, width, height, reveal)
            return
        }
        when (annotation.type) {
            AnnotationType.PEN -> drawPen(canvas, annotation, width, height)
            AnnotationType.HIGHLIGHT -> drawStroke(canvas, annotation, width, height, baseAlpha = 96)
            AnnotationType.RECT -> drawRect(canvas, annotation, width, height)
            AnnotationType.ARROW -> drawArrow(canvas, annotation, width, height)
            AnnotationType.TEXT -> drawText(canvas, annotation, width, height)
            AnnotationType.UNDERLINE, AnnotationType.STRIKEOUT -> drawMarkupLine(canvas, annotation, width, height)
            AnnotationType.ELLIPSE -> drawEllipse(canvas, annotation, width, height)
            AnnotationType.LINE -> drawStraightLine(canvas, annotation, width, height)
            AnnotationType.STAMP -> drawStamp(canvas, annotation, width, height)
            AnnotationType.SIGNATURE -> drawSignature(canvas, annotation, width, height)
            AnnotationType.REDACT -> drawRedact(canvas, annotation, width, height)
            AnnotationType.COVER_TEXT -> drawCoverText(canvas, annotation, width, height)
        }
    }

    /**
     * Replay rendering for `0 < fraction < 1`. Strokes expose the first
     * `ceil(points * fraction)` samples, two-point shapes grow their endpoint,
     * and text-like annotations fade in. Fraction `1f` still uses the exact
     * original rendering path above.
     */
    private fun drawPartial(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        fraction: Float,
    ) {
        when (annotation.type) {
            AnnotationType.PEN, AnnotationType.HIGHLIGHT -> {
                val points = revealedPoints(annotation.points, fraction)
                val partial = annotation.copy(points = points)
                if (annotation.type == AnnotationType.PEN) {
                    drawPen(canvas, partial, width, height)
                } else {
                    drawStroke(canvas, partial, width, height, baseAlpha = 96)
                }
            }

            AnnotationType.RECT,
            AnnotationType.ELLIPSE,
            AnnotationType.ARROW,
            AnnotationType.LINE,
            AnnotationType.REDACT -> {
                val points = revealedEndpoints(annotation.points, fraction) ?: return
                val partial = annotation.copy(points = points)
                when (annotation.type) {
                    AnnotationType.RECT -> drawRect(canvas, partial, width, height)
                    AnnotationType.ELLIPSE -> drawEllipse(canvas, partial, width, height)
                    AnnotationType.ARROW -> drawArrow(canvas, partial, width, height)
                    AnnotationType.LINE -> drawStraightLine(canvas, partial, width, height)
                    AnnotationType.REDACT -> drawRedact(canvas, partial, width, height)
                    else -> Unit
                }
            }

            AnnotationType.UNDERLINE, AnnotationType.STRIKEOUT -> {
                val points = if (annotation.points.size == 2) {
                    revealedEndpoints(annotation.points, fraction) ?: return
                } else {
                    revealedPoints(annotation.points, fraction)
                }
                drawMarkupLine(canvas, annotation.copy(points = points), width, height)
            }

            AnnotationType.TEXT -> drawText(canvas, annotation.faded(fraction), width, height)
            AnnotationType.STAMP -> drawStamp(canvas, annotation.faded(fraction), width, height)
            // Text-like reveal: fade the whole cover + replacement in together.
            AnnotationType.COVER_TEXT -> drawCoverText(canvas, annotation.faded(fraction), width, height)

            AnnotationType.SIGNATURE -> {
                if (annotation.points.size > 1) {
                    val partial = annotation.copy(points = revealedPoints(annotation.points, fraction))
                    drawSignature(canvas, partial, width, height)
                } else {
                    drawSignature(canvas, annotation.faded(fraction), width, height)
                }
            }
        }
    }

    private fun revealedPoints(points: List<NormalizedPoint>, fraction: Float): List<NormalizedPoint> {
        val count = ceil(points.size * fraction).toInt().coerceIn(1, points.size)
        return if (count >= points.size) points else points.subList(0, count).toList()
    }

    /** Interpolates the end of a two-point shape from its start point. */
    private fun revealedEndpoints(
        points: List<NormalizedPoint>,
        fraction: Float,
    ): List<NormalizedPoint>? {
        if (points.size < 2) return null
        val start = points.first()
        val end = points.last()
        val t = fraction.coerceIn(0f, 1f)
        return listOf(
            start,
            end.copy(
                x = start.x + (end.x - start.x) * t,
                y = start.y + (end.y - start.y) * t,
            ),
        )
    }

    private fun Annotation.faded(fraction: Float): Annotation =
        copy(opacity = (opacity * fraction.coerceIn(0f, 1f)).coerceIn(0f, 1f))

    fun drawEraserPreview(canvas: Canvas, point: NormalizedPoint, radiusPx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(190, 70, 70, 70)
        }
        canvas.drawCircle(point.x, point.y, radiusPx, paint)
        paint.color = Color.argb(40, 70, 70, 70)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(point.x, point.y, radiusPx, paint)
    }

    private fun opacityAlpha(annotation: Annotation, baseAlpha: Int = 255): Int =
        (baseAlpha * annotation.opacity.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

    private fun basePaint(annotation: Annotation, width: Float, baseAlpha: Int = 255): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation, baseAlpha)
            style = Paint.Style.STROKE
            strokeWidth = (annotation.width * width).coerceAtLeast(1.1f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    /**
     * PEN keeps the original smooth constant-width stroke for BALLPOINT, so PDF
     * annotations (which always use the default style) render exactly as before.
     */
    private fun drawPen(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        when (annotation.penStyle) {
            PenStyle.BALLPOINT -> drawStroke(canvas, annotation, width, height, baseAlpha = 255)
            PenStyle.FOUNTAIN -> drawFountain(canvas, annotation, width, height)
            PenStyle.BRUSH -> drawBrush(canvas, annotation, width, height)
            PenStyle.PENCIL -> drawPencil(canvas, annotation, width, height)
        }
    }

    /**
     * Per-point width multipliers from stylus pressure, smoothed with the same
     * 1:2:1 neighbourhood used by the fountain nib. Pressure 1 everywhere is an
     * exact identity, so legacy and finger strokes keep their old appearance.
     */
    private fun pressureWidths(points: List<NormalizedPoint>): FloatArray {
        val count = points.size
        if (count == 0) return FloatArray(0)
        val factors = FloatArray(count) { index ->
            inkPressureWidthFactor(points[index].pressure)
        }
        if (count < 3) return factors
        return FloatArray(count) { index ->
            val previous = factors[(index - 1).coerceAtLeast(0)]
            val current = factors[index]
            val next = factors[(index + 1).coerceAtMost(count - 1)]
            (previous + current * 2f + next) * 0.25f
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        annotation: Annotation,
        width: Float,
        height: Float,
        baseAlpha: Int,
    ) {
        val paint = basePaint(annotation, width, baseAlpha)
        val points = annotation.points
        if (points.size == 1) {
            val p = points.first()
            val dot = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(p.x * width, p.y * height, paint.strokeWidth / 2f, dot)
            return
        }
        val path = smoothPath(points, width, height)
        canvas.drawPath(path, paint)
    }

    private fun smoothPath(points: List<NormalizedPoint>, width: Float, height: Float): Path {
        val path = Path()
        val first = points.first()
        path.moveTo(first.x * width, first.y * height)
        if (points.size == 2) {
            path.lineTo(points[1].x * width, points[1].y * height)
            return path
        }
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val current = points[i]
            val midX = (prev.x + current.x) / 2f * width
            val midY = (prev.y + current.y) / 2f * height
            path.quadTo(prev.x * width, prev.y * height, midX, midY)
        }
        val last = points.last()
        path.lineTo(last.x * width, last.y * height)
        return path
    }

    /**
     * Calligraphic pen: a filled ribbon whose width follows the angle between the
     * movement direction and a fixed nib angle. Strokes along the nib stay thin,
     * strokes across it swell, which produces the familiar italic-nib feel without
     * needing a custom font or texture.
     */
    private fun drawFountain(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1.1f)
        val pressure = pressureWidths(points)
        if (points.size == 1) {
            drawPenDot(
                canvas,
                annotation,
                points.first(),
                width,
                height,
                base * 0.52f * pressure.first(),
            )
            return
        }
        val nibAngle = (PI / 4.0).toFloat()
        val factors = FloatArray(points.size) { index ->
            val prev = points[(index - 1).coerceAtLeast(0)]
            val next = points[(index + 1).coerceAtMost(points.lastIndex)]
            val dx = (next.x - prev.x) * width
            val dy = (next.y - prev.y) * height
            if (abs(dx) + abs(dy) < 0.0001f) {
                0.72f
            } else {
                0.30f + 0.95f * abs(sin(atan2(dy, dx) - nibAngle))
            }
        }
        // Neighbour averaging removes single-point width spikes at sharp corners.
        val smoothed = FloatArray(points.size) { index ->
            val a = factors[(index - 1).coerceAtLeast(0)]
            val b = factors[index]
            val c = factors[(index + 1).coerceAtMost(points.lastIndex)]
            (a + b + b + c) / 4f
        }
        val path = ribbonPath(points, width, height) { index ->
            base * smoothed[index] * pressure[index] * 0.5f
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            base * smoothed.first() * pressure.first() * 0.5f,
            paint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            base * smoothed.last() * pressure.last() * 0.5f,
            paint,
        )
    }

    /**
     * Soft brush: a broad translucent pass under a tapered ink core. The envelope
     * thins the ends and swells the middle while the halo keeps the edge fuzzy.
     */
    private fun drawBrush(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1.1f)
        val pressure = pressureWidths(points)
        if (points.size == 1) {
            drawPenDot(
                canvas,
                annotation,
                points.first(),
                width,
                height,
                base * 0.58f * pressure.first(),
            )
            return
        }
        val lastIndex = points.lastIndex
        val envelope = FloatArray(points.size) { index ->
            val t = if (lastIndex == 0) 0.5f else index.toFloat() / lastIndex
            0.38f + 0.72f * sin(PI * t).pow(0.6).toFloat()
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = (opacityAlpha(annotation) * 0.20f).roundToInt().coerceIn(0, 255)
            style = Paint.Style.FILL
        }
        val halo = ribbonPath(points, width, height) { index ->
            base * envelope[index] * pressure[index] * 0.80f
        }
        canvas.drawPath(halo, haloPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            style = Paint.Style.FILL
        }
        val core = ribbonPath(points, width, height) { index ->
            base * envelope[index] * pressure[index] * 0.5f
        }
        canvas.drawPath(core, corePaint)
        canvas.drawCircle(
            points.first().x * width,
            points.first().y * height,
            base * envelope.first() * pressure.first() * 0.5f,
            corePaint,
        )
        canvas.drawCircle(
            points.last().x * width,
            points.last().y * height,
            base * envelope.last() * pressure.last() * 0.5f,
            corePaint,
        )
    }

    /**
     * Pencil: three thin, slightly jittered passes at low alpha. The offsets are
     * deterministic so the editor preview and the exported PDF look the same.
     */
    private fun drawPencil(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val points = annotation.points
        val base = (annotation.width * width).coerceAtLeast(1f)
        if (points.size == 1) {
            drawPenDot(canvas, annotation, points.first(), width, height, base * 0.34f)
            return
        }
        val alpha = opacityAlpha(annotation)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            style = Paint.Style.STROKE
            strokeWidth = base * 0.62f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val offsets = floatArrayOf(-0.55f, 0f, 0.55f)
        offsets.forEachIndexed { pass, offset ->
            paint.alpha = (alpha * if (pass == 1) 0.52f else 0.34f)
                .roundToInt()
                .coerceIn(1, 255)
            val grain = if (pass == 1) 0.0004f else 0.0011f
            val jittered = if (pass == 1) {
                points
            } else {
                points.mapIndexed { index, point ->
                    val wobble = sin(index * 1.7 + pass * 4.3).toFloat()
                    point.copy(
                        x = (point.x + wobble * grain).coerceIn(0f, 1f),
                        y = (point.y + wobble * grain * 0.65f).coerceIn(0f, 1f),
                    )
                }
            }
            canvas.save()
            canvas.translate(offset, offset * 0.35f)
            canvas.drawPath(smoothPath(jittered, width, height), paint)
            canvas.restore()
        }
    }

    /** Builds a closed ribbon around a centre line using per-vertex half-widths. */
    private fun ribbonPath(
        points: List<NormalizedPoint>,
        width: Float,
        height: Float,
        halfWidthAt: (Int) -> Float,
    ): Path {
        val count = points.size
        val xs = FloatArray(count) { points[it].x * width }
        val ys = FloatArray(count) { points[it].y * height }
        val leftX = FloatArray(count)
        val leftY = FloatArray(count)
        val rightX = FloatArray(count)
        val rightY = FloatArray(count)
        for (i in 0 until count) {
            val prev = (i - 1).coerceAtLeast(0)
            val next = (i + 1).coerceAtMost(count - 1)
            var dx = xs[next] - xs[prev]
            var dy = ys[next] - ys[prev]
            var length = hypot(dx, dy)
            if (length < 0.0001f) {
                dx = 1f
                dy = 0f
                length = 1f
            }
            val nx = -dy / length
            val ny = dx / length
            val half = halfWidthAt(i).coerceAtLeast(0.15f)
            leftX[i] = xs[i] + nx * half
            leftY[i] = ys[i] + ny * half
            rightX[i] = xs[i] - nx * half
            rightY[i] = ys[i] - ny * half
        }
        return Path().apply {
            moveTo(leftX[0], leftY[0])
            for (i in 1 until count) lineTo(leftX[i], leftY[i])
            for (i in count - 1 downTo 0) lineTo(rightX[i], rightY[i])
            close()
        }
    }

    private fun drawPenDot(
        canvas: Canvas,
        annotation: Annotation,
        point: NormalizedPoint,
        width: Float,
        height: Float,
        radius: Float,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(point.x * width, point.y * height, radius, paint)
    }

    private fun drawRect(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = basePaint(annotation, width)
        canvas.drawRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            paint,
        )
    }

    private fun drawEllipse(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = basePaint(annotation, width)
        val rect = RectF(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
        )
        canvas.drawOval(rect, paint)
    }

    /**
     * UNDERLINE / STRIKEOUT share the same geometry; the semantic type is preserved
     * so the annotation list can label them differently.
     */
    private fun drawMarkupLine(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val points = annotation.points
        if (points.size < 2) return
        val paint = basePaint(annotation, width)
        if (points.size == 2) {
            canvas.drawLine(
                points[0].x * width,
                points[0].y * height,
                points[1].x * width,
                points[1].y * height,
                paint,
            )
            return
        }
        val path = Path().apply {
            moveTo(points[0].x * width, points[0].y * height)
            for (i in 1 until points.size) {
                lineTo(points[i].x * width, points[i].y * height)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawStraightLine(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points.first()
        val b = annotation.points.last()
        canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, basePaint(annotation, width))
    }

    private fun drawArrow(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val x1 = a.x * width
        val y1 = a.y * height
        val x2 = b.x * width
        val y2 = b.y * height
        val paint = basePaint(annotation, width)
        canvas.drawLine(x1, y1, x2, y2, paint)

        val angle = atan2(y2 - y1, x2 - x1)
        val length = hypot(x2 - x1, y2 - y1).coerceAtLeast(1f)
        val head = (paint.strokeWidth * 4.2f).coerceAtMost(length * 0.45f)
        val spread = (Math.PI / 7.0).toFloat()
        val headPaint = Paint(paint).apply { style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(x2, y2)
            lineTo(x2 - head * cos(angle - spread), y2 - head * sin(angle - spread))
            lineTo(x2 - head * cos(angle + spread), y2 - head * sin(angle + spread))
            close()
        }
        canvas.drawPath(path, headPaint)
    }

    private fun drawText(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.text.isBlank()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            textSize = (annotation.fontSize * height).coerceAtLeast(8f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val lineHeight = paint.textSize * 1.3f
        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())
        var baseline = paint.textSize
        annotation.text.split('\n').forEach { line ->
            canvas.drawText(line, 0f, baseline, paint)
            baseline += lineHeight
        }
        canvas.restore()
    }

    private fun drawStamp(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        val label = annotation.stamp.ifBlank { "APPROVED" }.replace('\n', ' ').trim()
        if (label.isEmpty()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            style = Paint.Style.FILL
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
        }

        var textSize = (annotation.fontSize * height).coerceAtLeast(10f)
        textSize = min(textSize, height * 0.16f)
        paint.textSize = textSize
        var textWidth = paint.measureText(label)
        val maxTextWidth = width * 0.72f
        if (textWidth > maxTextWidth && textWidth > 0f) {
            textSize *= maxTextWidth / textWidth
            paint.textSize = textSize
            textWidth = paint.measureText(label)
        }

        val boxWidth = textWidth + textSize * 1.2f
        val boxHeight = textSize * 1.9f
        val radius = boxHeight * 0.24f

        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())

        val fill = Paint(paint).apply {
            style = Paint.Style.FILL
            alpha = opacityAlpha(annotation, baseAlpha = 36)
        }
        canvas.drawRoundRect(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f, radius, radius, fill)

        val border = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (annotation.width * width * 1.4f).coerceIn(1.5f, boxHeight * 0.16f)
            alpha = opacityAlpha(annotation)
        }
        canvas.drawRoundRect(-boxWidth / 2f, -boxHeight / 2f, boxWidth / 2f, boxHeight / 2f, radius, radius, border)

        paint.alpha = opacityAlpha(annotation)
        val baseline = -(paint.ascent() + paint.descent()) / 2f
        canvas.drawText(label, 0f, baseline, paint)
        canvas.restore()
    }

    private fun drawSignature(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size > 1) {
            val paint = basePaint(annotation, width).apply {
                strokeWidth = (annotation.width * width * 1.4f).coerceAtLeast(1.6f)
            }
            canvas.drawPath(smoothPath(annotation.points, width, height), paint)
            return
        }
        val signature = annotation.text.ifBlank { annotation.stamp }
        if (signature.isBlank()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            typeface = Typeface.create("cursive", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }

        var textSize = (annotation.fontSize * height).coerceAtLeast(12f)
        textSize = min(textSize, height * 0.14f)
        paint.textSize = textSize
        var textWidth = paint.measureText(signature)
        val maxTextWidth = width * 0.8f
        if (textWidth > maxTextWidth && textWidth > 0f) {
            textSize *= maxTextWidth / textWidth
            paint.textSize = textSize
            textWidth = paint.measureText(signature)
        }

        canvas.save()
        canvas.translate(anchor.x * width, anchor.y * height)
        canvas.rotate(annotation.textRotation.toFloat())
        val baseline = textSize
        canvas.drawText(signature, 0f, baseline, paint)
        val underline = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (annotation.width * width * 1.2f).coerceAtLeast(1.2f)
            alpha = opacityAlpha(annotation, baseAlpha = 190)
        }
        val underlineY = baseline + textSize * 0.22f
        canvas.drawLine(0f, underlineY, textWidth, underlineY, underline)
        canvas.restore()
    }

    /**
     * The blackout must stay fully opaque: the raster export flattens it into the page,
     * so a translucent fill would leak the underlying text.
     */
    private fun drawRedact(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 255
        }
        canvas.drawRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            paint,
        )
    }

    private val COVER_TEXT_ON_LIGHT = 0xFF1A1A1A.toInt()
    private val COVER_TEXT_ON_DARK = 0xFFFFFFFF.toInt()

    /**
     * Visual text replacement: an opaque paper-coloured rectangle followed by the
     * replacement text in a contrasting colour. This is a pure overlay drawn by the
     * same painter in the editor and in flattened export; it never modifies PDF text
     * bytes, and the covered original words remain in the content stream.
     *
     * The rectangle is axis-aligned in display coordinates (the geometry produced by
     * `PdfTextEdit`), so no text rotation is applied. The replacement font shrinks to
     * fit the cover when it is longer than the original selection.
     */
    private fun drawCoverText(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2 || annotation.text.isBlank()) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val left = minOf(a.x, b.x) * width
        val right = maxOf(a.x, b.x) * width
        val top = minOf(a.y, b.y) * height
        val bottom = maxOf(a.y, b.y) * height
        if (right <= left || bottom <= top) return

        val cover = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
            alpha = opacityAlpha(annotation)
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, top, right, bottom, cover)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isLightColor(annotation.color)) COVER_TEXT_ON_LIGHT else COVER_TEXT_ON_DARK
            // Keep the replacement in step with the cover during replay fades.
            alpha = opacityAlpha(annotation)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        val boxWidth = right - left
        val boxHeight = bottom - top
        val padding = min(boxWidth, boxHeight) * COVER_PADDING_FRACTION
        val maxWidth = (boxWidth - padding * 2f).coerceAtLeast(1f)
        val maxHeight = (boxHeight - padding * 2f).coerceAtLeast(1f)
        val lines = annotation.text.replace('\r', '\n').split('\n')

        textPaint.textSize = (annotation.fontSize * height).coerceAtLeast(8f)
        val longest = lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
        val proportionalHeight = textPaint.textSize * LINE_HEIGHT_FACTOR * lines.size
        val scale = min(
            if (longest > 0f) (maxWidth / longest).coerceAtMost(1f) else 1f,
            (maxHeight / proportionalHeight).coerceAtMost(1f),
        )
        textPaint.textSize = (textPaint.textSize * scale).coerceAtLeast(6f)

        val lineHeight = textPaint.textSize * LINE_HEIGHT_FACTOR
        val blockHeight = lineHeight * lines.size
        var baseline = top + padding + (maxHeight - blockHeight) / 2f - textPaint.ascent()
        lines.forEach { line ->
            canvas.drawText(line, left + padding, baseline, textPaint)
            baseline += lineHeight
        }
    }

    /** Perceived brightness of the cover colour, used to pick readable text ink. */
    private fun isLightColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (0.299f * r + 0.587f * g + 0.114f * b) > 150f
    }

    private const val COVER_PADDING_FRACTION = 0.12f
    private const val LINE_HEIGHT_FACTOR = 1.22f
}
