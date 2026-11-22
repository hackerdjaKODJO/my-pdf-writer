package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import kotlin.math.max

/**
 * Original, generic paper patterns for notebook pages.
 *
 * The painter is deliberately independent from the PDF viewer: the same call is used
 * for the editor, page thumbnails and raster PDF export, so a page looks identical in
 * all three places. Every measurement is relative to the supplied width/height, which
 * keeps the templates legible even at thumbnail sizes.
 */
object PaperBackground {

    const val A4_WIDTH_PT = NotePaper.PORTRAIT_WIDTH_PT
    const val A4_HEIGHT_PT = NotePaper.PORTRAIT_HEIGHT_PT
    const val A4_ASPECT = NotePaper.PORTRAIT_ASPECT

    /** The editor renders the writing surface at 2.5x PDF points. */
    const val DEFAULT_SCALE = 2.5f

    fun pageAspect(
        template: PaperTemplate,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
    ): Float = NotePaper.aspect(orientation)

    fun pageAspect(orientation: PageOrientation): Float = NotePaper.aspect(orientation)

    fun pageWidthPx(
        scale: Float = DEFAULT_SCALE,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
    ): Float = NotePaper.widthPt(orientation) * scale

    fun pageHeightPx(
        scale: Float = DEFAULT_SCALE,
        orientation: PageOrientation = PageOrientation.PORTRAIT,
    ): Float = NotePaper.heightPt(orientation) * scale

    /**
     * Draws an imported background over the paper using the shared **cover**
     * policy: uniform scale, centred, clipped by the page. An opaque image hides
     * the template/tint; a transparent image lets the selected paper show through.
     * The editor, page thumbnails, studio preview and PDF export call this method,
     * so all four surfaces crop identically.
     */
    fun drawCustomBackground(
        canvas: Canvas,
        bitmap: Bitmap,
        width: Float,
        height: Float,
    ) {
        if (bitmap.isRecycled || width <= 0f || height <= 0f) return
        val placement = CustomTemplateGeometry.cover(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            pageWidth = width,
            pageHeight = height,
        ) ?: return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(
                placement.left,
                placement.top,
                placement.left + placement.width,
                placement.top + placement.height,
            ),
            paint,
        )
        canvas.restore()
    }

    fun draw(
        canvas: Canvas,
        template: PaperTemplate,
        width: Float,
        height: Float,
        dark: Boolean = false,
        tint: PaperTint = PaperTint.WARM,
    ) {
        if (width <= 0f || height <= 0f) return
        val colors = colors(tint, dark)
        canvas.drawRect(0f, 0f, width, height, fillPaint(colors.paper))

        // A faint, deterministic fibre layer is the strongest "this is paper"
        // cue. It lives in the shared painter so editor, thumbnails, studio and
        // export all show the same sheet.
        val toothAlpha = PaperTooth.alpha(tint, dark)
        if (toothAlpha > 0) {
            val toothPaint = fillPaint(colors.shade).apply { alpha = toothAlpha }
            PaperTooth.marks().forEach { mark ->
                canvas.drawCircle(
                    mark.x * width,
                    mark.y * height,
                    mark.radius,
                    toothPaint,
                )
            }
        }

        when (template) {
            PaperTemplate.BLANK -> Unit
            PaperTemplate.LINED -> drawLined(canvas, width, height, colors)
            PaperTemplate.GRID -> drawGrid(canvas, width, height, colors)
            PaperTemplate.DOTTED -> drawDotted(canvas, width, height, colors)
            PaperTemplate.CORNELL -> drawCornell(canvas, width, height, colors)
            PaperTemplate.CHECKLIST -> drawChecklist(canvas, width, height, colors)
            PaperTemplate.FOUR_QUADRANT -> drawFourQuadrant(canvas, width, height, colors)
            PaperTemplate.READING -> drawReading(canvas, width, height, colors)
            PaperTemplate.MEETING -> drawMeeting(canvas, width, height, colors)
            PaperTemplate.PLANNER -> drawPlanner(canvas, width, height, colors)
            PaperTemplate.MUSIC -> drawMusic(canvas, width, height, colors)
        }

        val edge = max(1f, width * 0.0012f)
        canvas.drawRect(
            edge / 2f,
            edge / 2f,
            width - edge / 2f,
            height - edge / 2f,
            strokePaint(colors.edge, edge),
        )
    }

    // ---------------------------------------------------------------------
    // Templates
    // ---------------------------------------------------------------------

    private fun drawLined(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val line = strokePaint(c.minor, max(1f, w * 0.0009f))
        val margin = strokePaint(c.accent, max(1f, w * 0.0015f))
        val left = w * 0.08f
        val right = w * 0.93f
        val top = h * 0.07f
        val bottom = h * 0.95f
        val spacing = h / 32f
        var y = top
        while (y <= bottom) {
            canvas.drawLine(left, y, right, y, line)
            y += spacing
        }
        canvas.drawLine(w * 0.12f, top * 0.88f, w * 0.12f, bottom + h * 0.012f, margin)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val spacing = w / 24f
        val minor = strokePaint(c.minor, max(1f, w * 0.00075f))
        val major = strokePaint(c.major, max(1f, w * 0.00115f))

        var i = 1
        var x = spacing
        while (x < w - 1f) {
            canvas.drawLine(x, 0f, x, h, if (i % 5 == 0) major else minor)
            i++
            x += spacing
        }
        var j = 1
        var y = spacing
        while (y < h - 1f) {
            canvas.drawLine(0f, y, w, y, if (j % 5 == 0) major else minor)
            j++
            y += spacing
        }
    }

    private fun drawDotted(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val spacing = w / 24f
        val radius = max(1.25f, w * 0.0014f)
        val dot = fillPaint(c.major)
        var row = 0
        var y = spacing / 2f
        while (y < h) {
            var col = 0
            var x = spacing / 2f
            while (x < w) {
                val r = if ((row + col) % 5 == 0) radius * 1.35f else radius
                canvas.drawCircle(x, y, r, dot)
                col++
                x += spacing
            }
            row++
            y += spacing
        }
    }

    private fun drawCornell(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.08f
        val right = w * 0.92f
        val top = h * 0.125f
        val cueX = w * 0.31f
        val summaryY = h * 0.78f
        val major = strokePaint(c.major, max(1.4f, w * 0.0018f))
        val line = strokePaint(c.minor, max(1f, w * 0.00085f))

        canvas.drawRect(left, h * 0.052f, right, top, fillPaint(c.shade))
        drawLabel(canvas, "코넬 노트", left + w * 0.012f, h * 0.094f, h * 0.026f, c.ink, bold = true)
        canvas.drawLine(cueX, top, cueX, summaryY, major)
        canvas.drawLine(left, top, right, top, major)
        canvas.drawLine(left, summaryY, right, summaryY, major)

        val spacing = h / 32f
        var y = top + spacing
        while (y < summaryY - spacing * 0.4f) {
            canvas.drawLine(left, y, cueX - w * 0.018f, y, line)
            canvas.drawLine(cueX + w * 0.018f, y, right, y, line)
            y += spacing
        }
        y = summaryY + spacing
        while (y < h * 0.95f) {
            canvas.drawLine(left, y, right, y, line)
            y += spacing
        }

        drawLabel(canvas, "단서", left + w * 0.012f, top - h * 0.008f, h * 0.014f, c.ink)
        drawLabel(canvas, "노트", cueX + w * 0.02f, top - h * 0.008f, h * 0.014f, c.ink)
        drawLabel(canvas, "요약", left + w * 0.012f, summaryY - h * 0.008f, h * 0.014f, c.ink)
    }

    private fun drawChecklist(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.08f
        val right = w * 0.92f
        val top = h * 0.10f
        val bottom = h * 0.95f
        val line = strokePaint(c.minor, max(1f, w * 0.00085f))
        val box = strokePaint(c.accent, max(1.2f, w * 0.0015f))
        val major = strokePaint(c.major, max(1.2f, w * 0.0016f))

        drawLabel(canvas, "체크리스트", left, h * 0.072f, h * 0.027f, c.ink, bold = true)
        canvas.drawLine(left, h * 0.085f, right, h * 0.085f, major)

        val rows = 18
        val rowHeight = (bottom - top) / rows
        val boxSize = minOf(rowHeight * 0.40f, w * 0.028f)
        for (i in 0 until rows) {
            val baseline = top + rowHeight * (i + 1)
            val boxTop = baseline - rowHeight * 0.66f
            canvas.drawRoundRect(
                left,
                boxTop,
                left + boxSize,
                boxTop + boxSize,
                boxSize * 0.20f,
                boxSize * 0.20f,
                box,
            )
            canvas.drawLine(left + boxSize + w * 0.018f, baseline, right, baseline, line)
        }
    }

    private fun drawFourQuadrant(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.07f
        val right = w * 0.93f
        val top = h * 0.06f
        val bottom = h * 0.94f
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f
        val major = strokePaint(c.major, max(1.6f, w * 0.002f))
        val accent = strokePaint(c.accent, max(1f, w * 0.0011f))

        canvas.drawLine(centerX, top, centerX, bottom, major)
        canvas.drawLine(left, centerY, right, centerY, major)

        val pad = w * 0.014f
        val labelSize = h * 0.021f
        drawLabel(canvas, "할 일", left + pad, top + h * 0.035f, labelSize, c.ink, bold = true)
        drawLabel(canvas, "메모", centerX + pad, top + h * 0.035f, labelSize, c.ink, bold = true)
        drawLabel(canvas, "아이디어", left + pad, centerY + h * 0.035f, labelSize, c.ink, bold = true)
        drawLabel(canvas, "질문", centerX + pad, centerY + h * 0.035f, labelSize, c.ink, bold = true)

        val tick = w * 0.022f
        canvas.drawLine(left, top, left + tick, top, accent)
        canvas.drawLine(left, top, left, top + tick, accent)
        canvas.drawLine(right, bottom, right - tick, bottom, accent)
        canvas.drawLine(right, bottom, right, bottom - tick, accent)
    }

    private fun drawReading(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.08f
        val right = w * 0.92f
        val major = strokePaint(c.major, max(1.4f, w * 0.0018f))
        val line = strokePaint(c.minor, max(1f, w * 0.00085f))
        val accent = strokePaint(c.accent, max(1.4f, w * 0.0016f))

        drawLabel(canvas, "독서 노트", left, h * 0.092f, h * 0.030f, c.ink, bold = true)
        canvas.drawLine(left, h * 0.108f, right, h * 0.108f, major)

        drawLabel(canvas, "지은이", left, h * 0.145f, h * 0.013f, c.ink)
        canvas.drawLine(left + w * 0.10f, h * 0.149f, left + w * 0.49f, h * 0.149f, line)
        drawLabel(canvas, "날짜", w * 0.61f, h * 0.145f, h * 0.013f, c.ink)
        canvas.drawLine(w * 0.68f, h * 0.149f, right, h * 0.149f, line)

        val boxTop = h * 0.17f
        val boxBottom = h * 0.235f
        canvas.drawRoundRect(
            left,
            boxTop,
            right,
            boxBottom,
            w * 0.012f,
            w * 0.012f,
            accent,
        )
        drawLabel(canvas, "한 줄 요약", left + w * 0.02f, boxTop + h * 0.045f, h * 0.014f, c.ink)

        canvas.drawLine(left, h * 0.26f, right, h * 0.26f, major)
        val spacing = h / 32f
        var y = h * 0.295f
        while (y < h * 0.95f) {
            canvas.drawLine(left, y, right, y, line)
            y += spacing
        }
        canvas.drawLine(left, h * 0.108f, left, h * 0.149f, accent)
    }

    private fun drawMeeting(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.08f
        val right = w * 0.92f
        val major = strokePaint(c.major, max(1.4f, w * 0.0018f))
        val line = strokePaint(c.minor, max(1f, w * 0.00085f))

        drawLabel(canvas, "회의록", left, h * 0.092f, h * 0.030f, c.ink, bold = true)
        canvas.drawLine(left, h * 0.108f, right, h * 0.108f, major)

        drawLabel(canvas, "일시", left, h * 0.142f, h * 0.013f, c.ink)
        canvas.drawLine(left + w * 0.085f, h * 0.146f, left + w * 0.47f, h * 0.146f, line)
        drawLabel(canvas, "장소", w * 0.55f, h * 0.142f, h * 0.013f, c.ink)
        canvas.drawLine(w * 0.61f, h * 0.146f, right, h * 0.146f, line)

        drawLabel(canvas, "참석", left, h * 0.181f, h * 0.013f, c.ink)
        canvas.drawLine(left + w * 0.085f, h * 0.185f, right, h * 0.185f, line)
        drawLabel(canvas, "안건", left, h * 0.220f, h * 0.013f, c.ink)
        canvas.drawLine(left + w * 0.085f, h * 0.224f, right, h * 0.224f, line)

        canvas.drawLine(left, h * 0.252f, right, h * 0.252f, major)
        val spacing = h / 32f
        var y = h * 0.285f
        while (y < h * 0.95f) {
            canvas.drawLine(left, y, right, y, line)
            y += spacing
        }
    }

    private fun drawPlanner(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.05f
        val right = w * 0.95f
        val headerBottom = h * 0.16f
        val bottom = h * 0.95f
        val major = strokePaint(c.major, max(1.3f, w * 0.0017f))
        val line = strokePaint(c.minor, max(1f, w * 0.0008f))
        val accent = strokePaint(c.accent, max(1.2f, w * 0.0014f))

        drawLabel(canvas, "주간 플래너", left, h * 0.095f, h * 0.030f, c.ink, bold = true)
        drawLabel(canvas, "이번 주", w * 0.70f, h * 0.095f, h * 0.013f, c.ink)
        canvas.drawLine(w * 0.79f, h * 0.099f, right, h * 0.099f, line)

        canvas.drawRect(left, h * 0.115f, right, headerBottom, fillPaint(c.shade))
        canvas.drawLine(left, headerBottom, right, headerBottom, major)

        val columns = 7
        val columnWidth = (right - left) / columns
        val days = listOf("월", "화", "수", "목", "금", "토", "일")
        for (i in 0..columns) {
            val x = left + columnWidth * i
            canvas.drawLine(x, h * 0.115f, x, bottom, major)
        }
        days.forEachIndexed { index, day ->
            val x = left + columnWidth * index + columnWidth / 2f
            drawLabel(
                canvas,
                day,
                x,
                h * 0.150f,
                h * 0.018f,
                if (index >= 5) c.accent else c.ink,
                bold = true,
                align = Paint.Align.CENTER,
            )
        }

        canvas.drawLine(left, h * 0.212f, right, h * 0.212f, major)
        val spacing = h / 30f
        var y = h * 0.245f
        while (y < bottom) {
            canvas.drawLine(left, y, right, y, line)
            y += spacing
        }
        canvas.drawLine(left, bottom, right, bottom, accent)
    }

    private fun drawMusic(canvas: Canvas, w: Float, h: Float, c: Colors) {
        val left = w * 0.07f
        val right = w * 0.94f
        val lineGap = h * 0.0105f
        val staffStep = h * 0.108f
        val firstTop = h * 0.070f
        val staffLine = strokePaint(c.major, max(1f, w * 0.0009f))
        val barLine = strokePaint(c.minor, max(1f, w * 0.0007f))
        val bracket = strokePaint(c.accent, max(1.2f, w * 0.0014f))

        for (staff in 0 until 8) {
            val top = firstTop + staffStep * staff
            for (line in 0 until 5) {
                val y = top + lineGap * line
                canvas.drawLine(left, y, right, y, staffLine)
            }
            canvas.drawLine(left - w * 0.012f, top, left, top, bracket)
            canvas.drawLine(left - w * 0.012f, top, left - w * 0.012f, top + lineGap * 4f, bracket)
            for (bar in 1..3) {
                val x = left + (right - left) * bar / 4f
                canvas.drawLine(x, top, x, top + lineGap * 4f, barLine)
            }
        }
    }

    // ---------------------------------------------------------------------
    // Paint helpers
    // ---------------------------------------------------------------------

    private class Colors(
        val paper: Int,
        val shade: Int,
        val minor: Int,
        val major: Int,
        val accent: Int,
        val ink: Int,
        val edge: Int,
    )

    private val LightColors = Colors(
        paper = 0xFFFCF8ED.toInt(),
        shade = 0xFFF2EBD9.toInt(),
        minor = 0xFFE6DCC7.toInt(),
        major = 0xFFCDBFA3.toInt(),
        accent = 0xFFC88E7B.toInt(),
        ink = 0xFF8A7B63.toInt(),
        edge = 0xFFE3D8C2.toInt(),
    )

    private val DarkColors = Colors(
        paper = 0xFF1C1A17.toInt(),
        shade = 0xFF26221D.toInt(),
        minor = 0xFF38332B.toInt(),
        major = 0xFF50493E.toInt(),
        accent = 0xFF8C6255.toInt(),
        ink = 0xFFA39884.toInt(),
        edge = 0xFF3B362E.toInt(),
    )

    /**
     * Tint changes the sheet fill and its paper-line contrast. The notebook stays
     * paper-like in dark theme: it is dimmed, not turned into a black app surface.
     */
    private fun colors(tint: PaperTint, dark: Boolean): Colors {
        if (!dark) {
            return when (tint) {
                PaperTint.WARM -> LightColors
                PaperTint.WHITE -> Colors(
                    paper = 0xFFFFFFFF.toInt(),
                    shade = 0xFFF4F4F1.toInt(),
                    minor = 0xFFE5E5E0.toInt(),
                    major = 0xFFC9C9C2.toInt(),
                    accent = 0xFFC88E7B.toInt(),
                    ink = 0xFF8A7B63.toInt(),
                    edge = 0xFFE2E2DD.toInt(),
                )

                PaperTint.PALE_YELLOW -> Colors(
                    paper = 0xFFFDF6D8.toInt(),
                    shade = 0xFFF5ECC4.toInt(),
                    minor = 0xFFEADFAF.toInt(),
                    major = 0xFFD3C58D.toInt(),
                    accent = 0xFFC08A6E.toInt(),
                    ink = 0xFF88794F.toInt(),
                    edge = 0xFFE8DCB4.toInt(),
                )

                PaperTint.LIGHT_GRAY -> Colors(
                    paper = 0xFFF2F4F3.toInt(),
                    shade = 0xFFE9ECEA.toInt(),
                    minor = 0xFFDDE1DE.toInt(),
                    major = 0xFFC4CBC7.toInt(),
                    accent = 0xFFB48678.toInt(),
                    ink = 0xFF777F7B.toInt(),
                    edge = 0xFFDCE1DE.toInt(),
                )
            }
        }
        return when (tint) {
            PaperTint.WARM -> DarkColors
            PaperTint.WHITE -> Colors(
                paper = 0xFF242524.toInt(),
                shade = 0xFF2E302F.toInt(),
                minor = 0xFF414442.toInt(),
                major = 0xFF565A57.toInt(),
                accent = 0xFF8C6255.toInt(),
                ink = 0xFFA6ABA8.toInt(),
                edge = 0xFF3D403E.toInt(),
            )

            PaperTint.PALE_YELLOW -> Colors(
                paper = 0xFF2A2719.toInt(),
                shade = 0xFF342F1E.toInt(),
                minor = 0xFF463F2A.toInt(),
                major = 0xFF5C5338.toInt(),
                accent = 0xFF8C6255.toInt(),
                ink = 0xFFB0A582.toInt(),
                edge = 0xFF423B27.toInt(),
            )

            PaperTint.LIGHT_GRAY -> Colors(
                paper = 0xFF232625.toInt(),
                shade = 0xFF2C302E.toInt(),
                minor = 0xFF3D4240.toInt(),
                major = 0xFF4F5552.toInt(),
                accent = 0xFF8C6255.toInt(),
                ink = 0xFFA0A9A5.toInt(),
                edge = 0xFF363B39.toInt(),
            )
        }
    }

    private fun fillPaint(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        textSize: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = max(3f, textSize)
            textAlign = align
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        canvas.drawText(text, x, y, paint)
    }
}
