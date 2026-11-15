package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Paints the annotation layer with the platform canvas.
 *
 * The same code is used for the on-screen overlay and for export, so the exported PDF
 * is pixel-faithful to what the student sees (including Korean text).
 */
object AnnotationPainter {

    fun draw(canvas: Canvas, annotations: List<Annotation>, width: Float, height: Float) {
        annotations.forEach { drawOne(canvas, it, width, height) }
    }

    fun drawOne(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.isEmpty() && annotation.type != AnnotationType.TEXT) return
        when (annotation.type) {
            AnnotationType.PEN -> drawStroke(canvas, annotation, width, height, alpha = 255)
            AnnotationType.HIGHLIGHT -> drawStroke(canvas, annotation, width, height, alpha = 96)
            AnnotationType.RECT -> drawRect(canvas, annotation, width, height)
            AnnotationType.ARROW -> drawArrow(canvas, annotation, width, height)
            AnnotationType.TEXT -> drawText(canvas, annotation, width, height)
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

    private fun basePaint(annotation: Annotation, width: Float, alpha: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = annotation.color
        this.alpha = alpha
        style = Paint.Style.STROKE
        strokeWidth = (annotation.width * width).coerceAtLeast(1.1f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawStroke(canvas: Canvas, annotation: Annotation, width: Float, height: Float, alpha: Int) {
        val paint = basePaint(annotation, width, alpha)
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
        val paint = basePaint(annotation, width, 255)
        canvas.drawRect(
            minOf(a.x, b.x) * width,
            minOf(a.y, b.y) * height,
            maxOf(a.x, b.x) * width,
            maxOf(a.y, b.y) * height,
            paint,
        )
    }

    private fun drawArrow(canvas: Canvas, annotation: Annotation, width: Float, height: Float) {
        if (annotation.points.size < 2) return
        val a = annotation.points[0]
        val b = annotation.points[1]
        val x1 = a.x * width
        val y1 = a.y * height
        val x2 = b.x * width
        val y2 = b.y * height
        val paint = basePaint(annotation, width, 255)
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
        if (annotation.text.isBlank() || annotation.points.isEmpty()) return
        val anchor = annotation.points.first()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = annotation.color
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
}
