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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Paints the annotation layer with the platform canvas.
 *
 * The same code is used for the on-screen overlay and for export, so the exported PDF
 * is pixel-faithful to what the student sees (including Korean text, stamps, signatures
 * and redaction blackouts).
 */
object AnnotationPainter {

    fun draw(canvas: Canvas, annotations: List<Annotation>, width: Float, height: Float) {
        annotations.forEach { drawOne(canvas, it, width, height) }
    }

    fun drawOne(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.isEmpty()) return
        when (annotation.type) {
            AnnotationType.PEN -> drawStroke(canvas, annotation, width, height, baseAlpha = 255)
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
        }
    }

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
}
