package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.PageNumberPosition
import kotlin.math.roundToInt

/**
 * Draws document-level decoration (watermark and page numbers) with the same
 * coordinates on screen and in the rasterized export. Keeping this out of the
 * stored annotations means the decoration can never be erased by mistake.
 */
object DocumentOverlayPainter {

    fun draw(
        canvas: Canvas,
        style: DocumentStyle,
        pageIndex: Int,
        pageCount: Int,
        width: Float,
        height: Float,
    ) {
        if (style.watermark.enabled) {
            drawWatermark(canvas, style, width, height)
        }
        if (style.pageNumbers.enabled) {
            drawPageNumber(canvas, style, pageIndex, pageCount, width, height)
        }
    }

    private fun drawWatermark(canvas: Canvas, style: DocumentStyle, width: Float, height: Float) {
        val watermark = style.watermark
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = watermark.color
            alpha = (watermark.opacity.coerceIn(0.02f, 0.6f) * 255).roundToInt()
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
        }
        val lines = watermark.text.split('\n').filter { it.isNotBlank() }
        if (lines.isEmpty()) return
        val widest = lines.maxOf { paint.measureText(it) }.coerceAtLeast(1f)
        val desired = height * 0.13f
        paint.textSize = if (widest > width * 0.82f) desired * (width * 0.82f / widest) else desired
        val lineHeight = paint.textSize * 1.18f
        val blockHeight = lineHeight * lines.size
        canvas.save()
        canvas.rotate(watermark.rotation.toFloat(), width / 2f, height / 2f)
        var baseline = height / 2f - blockHeight / 2f + paint.textSize
        lines.forEach { line ->
            canvas.drawText(line, width / 2f, baseline, paint)
            baseline += lineHeight
        }
        canvas.restore()
    }

    private fun drawPageNumber(
        canvas: Canvas,
        style: DocumentStyle,
        pageIndex: Int,
        pageCount: Int,
        width: Float,
        height: Float,
    ) {
        val page = style.pageNumbers
        val label = page.label(pageIndex, pageCount)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = page.color
            textSize = (height * 0.022f).coerceIn(8f, 28f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = when (page.position) {
                PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.TOP_CENTER -> Paint.Align.CENTER
                PageNumberPosition.BOTTOM_RIGHT -> Paint.Align.RIGHT
            }
            isSubpixelText = true
        }
        val margin = height * 0.045f
        val metrics = paint.fontMetrics
        val baseline = when (page.position) {
            PageNumberPosition.TOP_CENTER -> margin - metrics.ascent
            PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.BOTTOM_RIGHT -> height - margin - metrics.descent
        }
        val x = when (page.position) {
            PageNumberPosition.BOTTOM_CENTER, PageNumberPosition.TOP_CENTER -> width / 2f
            PageNumberPosition.BOTTOM_RIGHT -> width - margin
        }
        // A pale halo keeps the number readable on dark scans.
        val halo = Paint(paint).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = (paint.textSize * 0.09f).coerceAtLeast(1.5f)
            color = Color.WHITE
            alpha = 170
        }
        canvas.drawText(label, x, baseline, halo)
        canvas.drawText(label, x, baseline, paint)
    }
}
