package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import kotlin.math.min

/**
 * Draws offline post-it memo cards onto a platform [Canvas].
 *
 * The math is split in two on purpose. [layout] and [layoutCards] are pure Kotlin
 * (no Android types), so rect mapping, tint choice and Korean-safe line wrapping are
 * deterministic and JVM-testable; [draw] is a thin platform wrapper that turns those
 * layouts into `Paint`/`Path` calls. The editor's Compose card and this painter share
 * the same data model and the same original ARGB tints, but neither copies assets,
 * fonts or colours from any third-party app.
 *
 * Coordinates are the normalized 0..1 [MemoCard] rectangle multiplied by the render
 * width/height. Callers pass the dimensions from [NoteExporter.pageSize], which are
 * proportional to the A4 PDF point box, so a card keeps the same relative place and
 * shape in thumbnails and in the flattened PDF. This is the same mapping used by
 * `AnnotationPainter` for ink on the same canvas.
 */
object NoteCardPainter {

    /** Plain float rectangle; avoids Android types in the testable layout layer. */
    data class FRect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /** One clipped text line with its canvas baseline already resolved. */
    data class Line(
        val text: String,
        val x: Float,
        val baseline: Float,
    )

    /** Everything needed to paint one card, computed without touching Android. */
    data class CardLayout(
        val id: String,
        val rect: FRect,
        val cornerRadius: Float,
        val borderWidth: Float,
        val fill: Int,
        val edge: Int,
        val ink: Int,
        val tilt: Float,
        val headerHeight: Float,
        val foldSize: Float,
        val textRect: FRect,
        val textSize: Float,
        val textLines: List<Line>,
        val placeholder: Boolean,
    )

    /**
     * Cards to show for an export render. The editor's transient "hide overlay"
     * toggle is not persisted note data, so all cards on the page are exported by
     * default. A caller that explicitly opts out gets an empty list; the same list
     * is handed to [draw], so the flag cannot leak past this decision point.
     */
    fun visibleMemoCards(
        page: NotePage,
        includeMemoCards: Boolean = true,
    ): List<MemoCard> = if (includeMemoCards) page.memoCards else emptyList()

    /** Normalized rectangle of one card in render pixels. */
    fun cardRect(card: MemoCard, width: Float, height: Float): FRect {
        val safe = card.normalized()
        val w = positive(width)
        val h = positive(height)
        return FRect(
            left = safe.x * w,
            top = safe.y * h,
            right = (safe.x + safe.w) * w,
            bottom = (safe.y + safe.h) * h,
        )
    }

    fun layoutCards(
        cards: List<MemoCard>,
        width: Float,
        height: Float,
    ): List<CardLayout> = cards.map { layout(it, width, height) }

    /** Pure layout of one card: rect, colours, fold, wrap and clipped baselines. */
    fun layout(card: MemoCard, width: Float, height: Float): CardLayout {
        val safe = card.normalized()
        val rect = cardRect(safe, width, height)
        val minSide = min(rect.width, rect.height)
        val textSize = (minSide * 0.105f).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        val cornerRadius = (minSide * 0.055f).coerceIn(4f, 26f)
        val borderWidth = (minSide * 0.006f).coerceIn(0.9f, 3f)
        val foldSize = (minSide * 0.085f).coerceIn(6f, 34f)
        val headerHeight = minOf(
            rect.height * 0.28f,
            maxOf(8f, textSize * 1.3f, rect.height * 0.14f),
        )
        val padding = (minSide * 0.055f).coerceIn(3f, 20f)
        val textTop = minOf(rect.top + headerHeight + padding * 0.5f, rect.bottom - 1f)
        val textRect = FRect(
            left = rect.left + padding,
            top = textTop,
            right = maxOf(rect.left + 1f, rect.right - padding - foldSize * 0.35f),
            bottom = maxOf(textTop + 1f, rect.bottom - padding),
        )

        val placeholder = safe.text.isBlank()
        val source = if (placeholder) PLACEHOLDER else safe.text
        val lines = wrapText(
            text = source,
            maxWidth = textRect.width,
            textSize = textSize,
            maxLines = if (placeholder) 2 else MAX_LINES,
        )
        val lineHeight = textSize * LINE_HEIGHT_FACTOR
        val firstBaseline = textRect.top + textSize
        val textLines = lines.mapIndexed { index, line ->
            Line(line, textRect.left, firstBaseline + index * lineHeight)
        }
        return CardLayout(
            id = safe.id,
            rect = rect,
            cornerRadius = cornerRadius,
            borderWidth = borderWidth,
            fill = safe.tint.background,
            edge = safe.tint.edge,
            ink = safe.tint.ink,
            tilt = safe.tilt,
            headerHeight = headerHeight,
            foldSize = foldSize,
            textRect = textRect,
            textSize = textSize,
            textLines = textLines,
            placeholder = placeholder,
        )
    }

    /** Draws every card. An empty list performs no canvas operations at all. */
    fun draw(canvas: Canvas, cards: List<MemoCard>, width: Float, height: Float) {
        if (cards.isEmpty()) return
        cards.forEach { drawCard(canvas, layout(it, width, height)) }
    }

    /** Platform-only half of the painter; kept internal so tests stay JVM-pure. */
    internal fun drawCard(canvas: Canvas, card: CardLayout) {
        val r = card.rect
        canvas.save()
        if (card.tilt != 0f) {
            canvas.translate(r.centerX, r.centerY)
            canvas.rotate(card.tilt)
            canvas.translate(-r.centerX, -r.centerY)
        }

        // Asset-free paper shadow, offset so a sliver remains outside the fill.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(34, 32, 27, 20)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            r.left + SHADOW_DX,
            r.top + SHADOW_DY,
            r.right + SHADOW_DX,
            r.bottom + SHADOW_DY,
            card.cornerRadius,
            card.cornerRadius,
            shadow,
        )

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.fill
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            r.left, r.top, r.right, r.bottom,
            card.cornerRadius, card.cornerRadius, fill,
        )

        // Header band, grip dots and text share the rounded clip so nothing spills
        // past a corner. The border and fold are drawn after the clip is released.
        canvas.save()
        val rounded = Path().apply {
            addRoundRect(
                RectF(r.left, r.top, r.right, r.bottom),
                card.cornerRadius,
                card.cornerRadius,
                Path.Direction.CW,
            )
        }
        canvas.clipPath(rounded)
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.edge
            alpha = 34
            style = Paint.Style.FILL
        }
        canvas.drawRect(r.left, r.top, r.right, r.top + card.headerHeight, header)
        val grip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.edge
            alpha = 170
            style = Paint.Style.FILL
        }
        val gripY = r.top + card.headerHeight / 2f
        val gripGap = (card.headerHeight * 0.28f).coerceAtLeast(1.5f)
        repeat(3) { index ->
            canvas.drawCircle(
                r.left + card.cornerRadius + gripGap * (index + 1),
                gripY,
                gripGap * 0.28f,
                grip,
            )
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (card.placeholder) card.edge else card.ink
            alpha = if (card.placeholder) 170 else 255
            textSize = card.textSize
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.clipRect(
            RectF(card.textRect.left, card.textRect.top, card.textRect.right, card.textRect.bottom),
        )
        card.textLines.forEach { line ->
            canvas.drawText(line.text, line.x, line.baseline, textPaint)
        }
        canvas.restore()
        canvas.restore()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.edge
            alpha = 150
            style = Paint.Style.STROKE
            strokeWidth = card.borderWidth
        }
        canvas.drawRoundRect(
            r.left, r.top, r.right, r.bottom,
            card.cornerRadius, card.cornerRadius, border,
        )

        val fold = Path().apply {
            moveTo(r.right, r.top)
            lineTo(r.right, r.top + card.foldSize)
            lineTo(r.right - card.foldSize, r.top)
            close()
        }
        val foldFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.edge
            alpha = 82
            style = Paint.Style.FILL
        }
        canvas.drawPath(fold, foldFill)
        val crease = Paint(border).apply { alpha = 130 }
        canvas.drawLine(
            r.right - card.foldSize, r.top,
            r.right, r.top + card.foldSize,
            crease,
        )
        canvas.restore()
    }

    /**
     * Greedy wrap that breaks between words when a space is available and anywhere
     * otherwise. Korean text has no spaces, so the fallback break is required for a
     * readable result with the default system font. Overlong content is clipped to
     * [maxLines] with an ellipsis on the last line; the canvas clip is a second guard.
     */
    internal fun wrapText(
        text: String,
        maxWidth: Float,
        textSize: Float,
        maxLines: Int,
    ): List<String> {
        if (text.isEmpty() || maxLines <= 0) return emptyList()
        val limit = if (maxWidth.isFinite() && maxWidth > 0f) maxWidth else 1f
        val size = if (textSize.isFinite() && textSize > 0f) textSize else MIN_TEXT_SIZE
        val result = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
            var current = StringBuilder()
            var currentWidth = 0f
            var lastSpace = -1
            var index = 0
            while (index < paragraph.length) {
                val ch = paragraph[index]
                val charWidth = charWidth(ch) * size
                if (current.isNotEmpty() && currentWidth + charWidth > limit) {
                    if (lastSpace > 0 && lastSpace < current.length - 1) {
                        val word = current.substring(lastSpace + 1)
                        result.add(current.substring(0, lastSpace))
                        current = StringBuilder(word)
                        currentWidth = measuredWidth(word, size)
                    } else {
                        result.add(current.toString())
                        current = StringBuilder()
                        currentWidth = 0f
                    }
                    lastSpace = -1
                    continue
                }
                if (ch == ' ' || ch == '\t') lastSpace = current.length
                current.append(ch)
                currentWidth += charWidth
                index++
            }
            result.add(current.toString())
        }
        if (result.size <= maxLines) return result
        val kept = result.take(maxLines).toMutableList()
        val lastIndex = kept.lastIndex
        kept[lastIndex] = withEllipsis(kept[lastIndex], limit, size)
        return kept
    }

    private fun withEllipsis(line: String, limit: Float, textSize: Float): String {
        var kept = line
        while (kept.isNotEmpty() && measuredWidth(kept, textSize) + ELLIPSIS_WIDTH * textSize > limit) {
            kept = kept.dropLast(1)
        }
        return kept + ELLIPSIS
    }

    private fun measuredWidth(text: String, textSize: Float): Float {
        var units = 0f
        for (ch in text) units += charWidth(ch)
        return units * textSize
    }

    /** Conservative advance estimate: CJK/Hangul one em, Latin about 0.58 em. */
    private fun charWidth(ch: Char): Float = when {
        ch == '\t' -> 0.9f
        ch == ' ' -> 0.32f
        ch.code < 0x20 -> 0f
        ch.code < 0x80 -> 0.58f
        else -> 1f
    }

    private fun positive(value: Float): Float =
        if (value.isFinite() && value > 0f) value else 1f

    private const val PLACEHOLDER = "빈 메모"
    private const val ELLIPSIS = "…"
    private const val ELLIPSIS_WIDTH = 1f
    private const val MIN_TEXT_SIZE = 8f
    private const val MAX_TEXT_SIZE = 30f
    private const val MAX_LINES = 6
    private const val LINE_HEIGHT_FACTOR = 1.22f
    private const val SHADOW_DX = 2f
    private const val SHADOW_DY = 3f
}
