package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import kotlin.math.min

/**
 * Original, asset-free notebook cover preview. The cover uses one of four
 * generic patterns and shows a small paper thumbnail so the first page
 * template is recognizable in the library.
 */
@Composable
internal fun NotebookCover(
    coverColor: Int,
    coverStyle: CoverStyle,
    template: PaperTemplate,
    modifier: Modifier = Modifier,
) {
    val base = Color(coverColor)
    Canvas(modifier.fillMaxSize()) {
        drawRect(base)
        val ink = if (base.luminance() > 0.55f) Color(0xFF1B1C19) else Color.White

        when (coverStyle) {
            CoverStyle.SOLID -> {
                drawCircle(
                    color = ink.copy(alpha = 0.05f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.88f, size.height * 0.12f),
                )
                drawCircle(
                    color = ink.copy(alpha = 0.035f),
                    radius = size.minDimension * 0.32f,
                    center = Offset(size.width * 0.08f, size.height * 0.96f),
                )
            }

            CoverStyle.GRID -> {
                val step = 18.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(ink.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(ink.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
            }

            CoverStyle.DOTS -> {
                val step = 16.dp.toPx()
                val radius = 1.25.dp.toPx()
                var y = step
                while (y < size.height) {
                    var x = step
                    while (x < size.width) {
                        drawCircle(ink.copy(alpha = 0.16f), radius, Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }

            CoverStyle.STRIPES -> {
                val step = 14.dp.toPx()
                val stroke = 3.dp.toPx()
                var start = -size.height
                while (start < size.width) {
                    drawLine(
                        color = ink.copy(alpha = 0.075f),
                        start = Offset(start, size.height),
                        end = Offset(start + size.height, 0f),
                        strokeWidth = stroke,
                    )
                    start += step
                }
            }
        }

        // Generic book spine shading.
        val spineWidth = (size.width * 0.055f).coerceIn(3.dp.toPx(), 9.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.10f), size = Size(spineWidth, size.height))
        drawLine(
            color = Color.White.copy(alpha = 0.14f),
            start = Offset(spineWidth, 0f),
            end = Offset(spineWidth, size.height),
            strokeWidth = 1.dp.toPx(),
        )

        // Paper thumbnail with the notebook's first-page template.
        val pad = min(size.width, size.height) * 0.07f
        val pageWidth = (size.width * 0.47f).coerceAtLeast(8.dp.toPx())
        val pageHeight = (size.height * 0.55f).coerceAtLeast(8.dp.toPx())
        val pageLeft = size.width - pageWidth - pad
        val pageTop = size.height - pageHeight - pad
        val pageCorner = 4.dp.toPx()

        drawRoundRect(
            color = Color(0xFFFDFBF4),
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageWidth, pageHeight),
            cornerRadius = CornerRadius(pageCorner),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.12f),
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageWidth, pageHeight),
            cornerRadius = CornerRadius(pageCorner),
            style = Stroke(1.dp.toPx()),
        )
        clipRect(pageLeft, pageTop, pageLeft + pageWidth, pageTop + pageHeight) {
            val inset = (pageWidth * 0.10f).coerceAtLeast(2.dp.toPx())
            drawPaperPattern(
                template = template,
                left = pageLeft + inset,
                top = pageTop + inset,
                width = pageWidth - inset * 2f,
                height = pageHeight - inset * 2f,
                color = Color(0xFF41505E),
            )
        }
    }
}

private fun DrawScope.drawPaperPattern(
    template: PaperTemplate,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    if (width <= 0f || height <= 0f) return
    val line = color.copy(alpha = 0.38f)
    val faint = color.copy(alpha = 0.20f)
    val stroke = 1f
    val step = (height / 9f).coerceAtLeast(2.5f)

    fun horizontal(y: Float, startX: Float = left, endX: Float = left + width, c: Color = line) {
        if (y in top..(top + height)) drawLine(c, Offset(startX, y), Offset(endX, y), stroke)
    }

    fun vertical(x: Float, startY: Float = top, endY: Float = top + height, c: Color = line) {
        if (x in left..(left + width)) drawLine(c, Offset(x, startY), Offset(x, endY), stroke)
    }

    fun ruled(startY: Float = top + step) {
        var y = startY
        while (y < top + height) {
            horizontal(y)
            y += step
        }
    }

    when (template) {
        PaperTemplate.BLANK -> Unit

        PaperTemplate.LINED -> ruled(top + step * 0.5f)

        PaperTemplate.GRID -> {
            var y = top
            while (y <= top + height) {
                horizontal(y, c = faint)
                y += step
            }
            var x = left
            while (x <= left + width) {
                vertical(x, c = faint)
                x += step
            }
        }

        PaperTemplate.DOTTED -> {
            var y = top + step * 0.5f
            while (y < top + height) {
                var x = left + step * 0.3f
                while (x < left + width) {
                    drawCircle(faint, radius = 1.1f, center = Offset(x, y))
                    x += step * 0.7f
                }
                y += step * 0.7f
            }
        }

        PaperTemplate.CORNELL -> {
            val headerBottom = top + height * 0.24f
            horizontal(headerBottom, c = faint)
            vertical(left + width * 0.32f, startY = headerBottom)
            ruled(headerBottom + step)
        }

        PaperTemplate.CHECKLIST -> {
            var y = top + step * 0.5f
            while (y < top + height) {
                drawRect(
                    color = faint,
                    topLeft = Offset(left, y - step * 0.28f),
                    size = Size(step * 0.55f, step * 0.55f),
                    style = Stroke(stroke),
                )
                horizontal(y, startX = left + step * 0.85f)
                y += step
            }
        }

        PaperTemplate.FOUR_QUADRANT -> {
            horizontal(top + height / 2f, c = faint)
            vertical(left + width / 2f, c = faint)
        }

        PaperTemplate.READING -> {
            vertical(left + width * 0.24f, c = faint)
            ruled(top + step * 0.5f)
        }

        PaperTemplate.MEETING -> {
            val headerBottom = top + height * 0.20f
            horizontal(headerBottom)
            ruled(headerBottom + step)
        }

        PaperTemplate.PLANNER -> {
            val headerBottom = top + height * 0.18f
            horizontal(headerBottom)
            vertical(left + width / 2f, startY = top, endY = headerBottom, c = faint)
            ruled(headerBottom + step)
            vertical(left + width * 0.26f, startY = headerBottom, c = faint)
        }

        PaperTemplate.MUSIC -> {
            val gap = (height / 12f).coerceAtLeast(2f)
            val groupGap = gap * 2.2f
            var staffTop = top + gap
            while (staffTop + gap * 4f < top + height) {
                for (i in 0..4) {
                    horizontal(staffTop + gap * i, c = faint)
                }
                staffTop += gap * 5f + groupGap
            }
        }
    }
}
