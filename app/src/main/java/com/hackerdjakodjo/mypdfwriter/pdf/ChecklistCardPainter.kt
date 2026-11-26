package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.ChecklistItem
import com.hackerdjakodjo.mypdfwriter.data.ChecklistProgress
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Asset-free checklist renderer for flattened notebook exports.
 *
 * The interactive editor intentionally previews only a few rows. Export is a
 * different surface: [itemsForExport] keeps every stored row, including its
 * completed state, and the layout gives each row a slot inside the saved card.
 * Long rows wrap when there is room and ellipsize when there is not.
 *
 * Geometry, text wrapping and all drawing decisions are plain Kotlin so they can
 * be checked on the JVM. Android objects appear only in [draw] and its private
 * helpers. The palette and hand-drawn check mark are original to this painter.
 */
object ChecklistCardPainter {

    /** A plain rectangle used by the JVM-testable half of the painter. */
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

        val isValid: Boolean
            get() = width.isFinite() && height.isFinite() && width >= 0f && height >= 0f

        /** Positive-area intersection, useful for asserting export clip bounds. */
        fun intersection(other: FRect): FRect? {
            val l = max(left, other.left)
            val t = max(top, other.top)
            val r = min(right, other.right)
            val b = min(bottom, other.bottom)
            return if (r > l && b > t) FRect(l, t, r, b) else null
        }
    }

    /** One text line. [width] is the same conservative estimate used for wrapping. */
    data class TextLine(
        val text: String,
        val x: Float,
        val baseline: Float,
        val width: Float,
    )

    /** One exported checklist row; checked state is copied, never recomputed. */
    data class ItemLayout(
        val id: String,
        val checked: Boolean,
        val placeholder: Boolean,
        val rowRect: FRect,
        val checkboxRect: FRect,
        val textRect: FRect,
        val textSize: Float,
        val lineHeight: Float,
        val lines: List<TextLine>,
    )

    /** Everything needed to paint a card without consulting Android or mutable state. */
    data class CardLayout(
        val id: String,
        val rect: FRect,
        val cornerRadius: Float,
        val borderWidth: Float,
        val fill: Int,
        val edge: Int,
        val accent: Int,
        val ink: Int,
        val mutedInk: Int,
        val checkInk: Int,
        val title: String,
        val titlePlaceholder: Boolean,
        val titleRect: FRect,
        val titleSize: Float,
        val titleLines: List<TextLine>,
        val progress: ChecklistProgress,
        val progressText: String,
        val progressRect: FRect,
        val progressSize: Float,
        val progressLines: List<TextLine>,
        val progressTrackRect: FRect,
        val contentRect: FRect,
        val totalItems: Int,
        val items: List<ItemLayout>,
    ) {
        val progressFraction: Float get() = progress.fraction.coerceIn(0f, 1f)
        val allItemsRendered: Boolean get() = items.size == totalItems
    }

    /**
     * Export visibility is deliberately independent of the editor's five-row preview.
     * The model's edit-time limit still applies when items are created, but hand-edited
     * or legacy data is never silently discarded by the renderer.
     */
    fun itemsForExport(card: ChecklistCard): List<ChecklistItem> = card.items

    /** Maps a repaired normalized card rectangle into page pixels. */
    fun cardRect(card: ChecklistCard, width: Float, height: Float): FRect {
        val pageWidth = pageDimension(width)
        val pageHeight = pageDimension(height)
        val rawWidth = finiteOr(card.w, ChecklistCard.DEFAULT_W)
        val rawHeight = finiteOr(card.h, ChecklistCard.DEFAULT_H)
        val safeWidth = if (rawWidth > 0f) {
            rawWidth.coerceIn(ChecklistCard.MIN_W, 1f)
        } else {
            ChecklistCard.DEFAULT_W
        }
        val safeHeight = if (rawHeight > 0f) {
            rawHeight.coerceIn(ChecklistCard.MIN_H, 1f)
        } else {
            ChecklistCard.DEFAULT_H
        }
        val safeX = finiteOr(card.x, ChecklistCard.DEFAULT_X)
            .coerceIn(0f, (1f - safeWidth).coerceAtLeast(0f))
        val safeY = finiteOr(card.y, ChecklistCard.DEFAULT_Y)
            .coerceIn(0f, (1f - safeHeight).coerceAtLeast(0f))
        return FRect(
            left = safeX * pageWidth,
            top = safeY * pageHeight,
            right = (safeX + safeWidth).coerceIn(0f, 1f) * pageWidth,
            bottom = (safeY + safeHeight).coerceIn(0f, 1f) * pageHeight,
        )
    }

    fun layoutCards(
        cards: List<ChecklistCard>,
        width: Float,
        height: Float,
    ): List<CardLayout> = cards.map { layout(it, width, height) }

    /** Pure layout for one card, including every item and its finite clip rectangles. */
    fun layout(card: ChecklistCard, width: Float, height: Float): CardLayout {
        val rect = cardRect(card, width, height)
        val minSide = min(rect.width, rect.height)
        val padding = (minSide * 0.045f).coerceIn(2f, 18f)
        val inner = inset(rect, padding)
        val baseTitleSize = (minSide * 0.058f).coerceIn(6f, 22f)
        val baseItemSize = (minSide * 0.043f).coerceIn(5f, 16f)
        val titleSize = baseTitleSize.coerceAtMost(max(0.5f, inner.height * 0.42f))
        val progressSize = (baseTitleSize * 0.76f)
            .coerceAtMost(max(0.4f, inner.height * 0.34f))
        val gap = (minSide * 0.014f).coerceIn(2f, 7f)
        val headerHeight = max(titleSize * 1.24f, progressSize * 1.24f)
            .coerceAtMost(inner.height * 0.72f)

        val progress = Checklists.progress(card)
        val progressText = "${progress.checked}/${progress.total} · ${progress.percent}%"
        val maxProgressWidth = inner.width * 0.54f
        val progressLines = if (maxProgressWidth > 0f) {
            wrapText(progressText, maxProgressWidth, progressSize, maxLines = 1)
        } else {
            emptyList()
        }
        val progressWidth = minOf(
            maxProgressWidth,
            progressLines.firstOrNull()?.let { estimatedTextWidth(it, progressSize) } ?: 0f,
        ).coerceAtLeast(0f)
        val progressRect = FRect(
            left = inner.right - progressWidth,
            top = inner.top,
            right = inner.right,
            bottom = inner.top + headerHeight,
        )
        val progressBaseline = minOf(
            progressRect.bottom,
            progressRect.centerY + progressSize * 0.35f,
        )
        val resolvedProgressLines = progressLines.map { line ->
            TextLine(
                text = line,
                x = progressRect.left,
                baseline = progressBaseline,
                width = estimatedTextWidth(line, progressSize),
            )
        }

        val titleWidth = (inner.width - progressWidth - gap).coerceAtLeast(0f)
        val titleRect = FRect(
            left = inner.left,
            top = inner.top,
            right = inner.left + titleWidth,
            bottom = inner.top + headerHeight,
        )
        val titleText = cleanText(card.title, multiline = false)
        val titleSource = titleText.ifBlank { Checklists.DEFAULT_TITLE }
        val titlePlaceholder = titleText.isBlank()
        val titleBaseline = titleRect.top + min(titleSize, titleRect.height * 0.86f)
        val titleLines = if (titleWidth > 0f) {
            wrapText(titleSource, titleWidth, titleSize, maxLines = 1).map { line ->
                TextLine(
                    text = line,
                    x = titleRect.left,
                    baseline = titleBaseline,
                    width = estimatedTextWidth(line, titleSize),
                )
            }
        } else {
            emptyList()
        }

        val innerTop = minOf(inner.top, inner.bottom)
        val innerBottom = maxOf(inner.top, inner.bottom)
        val barGap = min(gap * 0.6f, max(0f, innerBottom - innerTop) * 0.25f)
        val barTop = min(innerTop + headerHeight + barGap, innerBottom)
        val barHeight = min(
            (minSide * 0.009f).coerceIn(2f, 6f),
            (innerBottom - barTop).coerceAtLeast(0f),
        )
        val progressTrackRect = FRect(inner.left, barTop, inner.right, barTop + barHeight)
        val contentTop = if (innerBottom > innerTop) {
            (progressTrackRect.bottom + gap * 0.6f).coerceIn(innerTop, innerBottom)
        } else {
            innerTop
        }
        val contentRect = FRect(inner.left, contentTop, inner.right, innerBottom)

        val exportItems = itemsForExport(card)
        val slotHeight = if (exportItems.isEmpty()) 0f else contentRect.height / exportItems.size
        val rowGap = min(
            (minSide * 0.006f).coerceIn(1f, 3f),
            slotHeight * 0.12f,
        )
        val rowHeight = (slotHeight - rowGap).coerceAtLeast(0f)
        val itemLayouts = exportItems.mapIndexed { index, item ->
            layoutItem(
                item = item,
                index = index,
                contentRect = contentRect,
                slotHeight = slotHeight,
                rowHeight = rowHeight,
                baseTextSize = baseItemSize,
                gap = gap,
            )
        }

        return CardLayout(
            id = card.id,
            rect = rect,
            cornerRadius = min(14f, minSide * 0.055f),
            borderWidth = min(2f, minSide * 0.006f),
            fill = CARD_FILL,
            edge = CARD_EDGE,
            accent = CARD_ACCENT,
            ink = CARD_INK,
            mutedInk = CARD_MUTED_INK,
            checkInk = CARD_CHECK_INK,
            title = titleSource,
            titlePlaceholder = titlePlaceholder,
            titleRect = titleRect,
            titleSize = titleSize,
            titleLines = titleLines,
            progress = progress,
            progressText = progressText,
            progressRect = progressRect,
            progressSize = progressSize,
            progressLines = resolvedProgressLines,
            progressTrackRect = progressTrackRect,
            contentRect = contentRect,
            totalItems = exportItems.size,
            items = itemLayouts,
        )
    }

    /** Draws every card in input order. An empty list performs no canvas operations. */
    fun draw(canvas: Canvas, cards: List<ChecklistCard>, width: Float, height: Float) {
        if (cards.isEmpty()) return
        cards.forEach { drawCard(canvas, layout(it, width, height)) }
    }

    /** Android-only half of the painter. */
    internal fun drawCard(canvas: Canvas, card: CardLayout) {
        val r = card.rect
        if (!r.isValid || r.width <= 0f || r.height <= 0f) return
        val rounded = Path().apply {
            addRoundRect(
                RectF(r.left, r.top, r.right, r.bottom),
                card.cornerRadius,
                card.cornerRadius,
                Path.Direction.CW,
            )
        }
        canvas.save()
        try {
            canvas.clipPath(rounded)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = card.fill
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                r.left, r.top, r.right, r.bottom,
                card.cornerRadius, card.cornerRadius, fill,
            )

            drawProgress(canvas, card)
            drawTextLines(
                canvas = canvas,
                lines = card.titleLines,
                clip = card.titleRect,
                textSize = card.titleSize,
                color = if (card.titlePlaceholder) card.edge else card.ink,
                bold = true,
            )
            drawTextLines(
                canvas = canvas,
                lines = card.progressLines,
                clip = card.progressRect,
                textSize = card.progressSize,
                color = card.accent,
                bold = false,
            )
            card.items.forEach { drawItem(canvas, card, it) }

            if (card.borderWidth > 0f) {
                val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = card.edge
                    alpha = 170
                    style = Paint.Style.STROKE
                    strokeWidth = card.borderWidth
                }
                canvas.drawRoundRect(
                    r.left, r.top, r.right, r.bottom,
                    card.cornerRadius, card.cornerRadius, border,
                )
            }
        } finally {
            canvas.restore()
        }
    }

    private fun layoutItem(
        item: ChecklistItem,
        index: Int,
        contentRect: FRect,
        slotHeight: Float,
        rowHeight: Float,
        baseTextSize: Float,
        gap: Float,
    ): ItemLayout {
        val rowTop = contentRect.top + index * slotHeight
        val rowRect = FRect(contentRect.left, rowTop, contentRect.right, rowTop + rowHeight)
        val checkboxSide = min(baseTextSize * 1.25f, rowHeight * 0.70f).coerceAtLeast(0f)
        val checkboxRect = FRect(
            left = rowRect.left,
            top = rowRect.centerY - checkboxSide / 2f,
            right = rowRect.left + checkboxSide,
            bottom = rowRect.centerY + checkboxSide / 2f,
        )
        val textGap = min(checkboxSide * 0.45f, min(rowRect.width * 0.08f, gap))
        val textLeft = min(rowRect.right, checkboxRect.right + textGap)
        val textRect = FRect(textLeft, rowRect.top, rowRect.right, rowRect.bottom)
        val textSize = min(baseTextSize, rowHeight * 0.72f).coerceAtLeast(0f)
        val lineHeight = textSize * LINE_HEIGHT_FACTOR
        val verticalInset = min(rowHeight * 0.08f, textSize * 0.12f)
        val fittingLines = if (lineHeight > 0f) {
            floor(((rowHeight - verticalInset) / lineHeight).toDouble()).toInt()
        } else {
            0
        }
        val maxLines = fittingLines.coerceIn(0, MAX_ITEM_LINES)
        val source = cleanText(item.text, multiline = true)
        val placeholder = source.isBlank()
        val displayText = if (placeholder) BLANK_ITEM else source
        val firstBaseline = textRect.top + verticalInset + min(
            textSize,
            (textRect.height - verticalInset).coerceAtLeast(0f),
        )
        val lines = if (maxLines > 0 && textRect.width > 0f) {
            wrapText(displayText, textRect.width, textSize, maxLines).mapIndexed { lineIndex, line ->
                TextLine(
                    text = line,
                    x = textRect.left,
                    baseline = firstBaseline + lineIndex * lineHeight,
                    width = estimatedTextWidth(line, textSize),
                )
            }
        } else {
            emptyList()
        }
        return ItemLayout(
            id = item.id,
            checked = item.done,
            placeholder = placeholder,
            rowRect = rowRect,
            checkboxRect = checkboxRect,
            textRect = textRect,
            textSize = textSize,
            lineHeight = lineHeight,
            lines = lines,
        )
    }

    private fun drawProgress(canvas: Canvas, card: CardLayout) {
        val track = card.progressTrackRect
        if (!track.isValid || track.height <= 0f || track.width <= 0f) return
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.edge
            alpha = 44
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            track.left, track.top, track.right, track.bottom,
            track.height / 2f, track.height / 2f, trackPaint,
        )
        val filledWidth = track.width * card.progressFraction
        if (filledWidth <= 0f) return
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = card.accent
            style = Paint.Style.FILL
        }
        canvas.save()
        try {
            canvas.clipRect(RectF(track.left, track.top, track.right, track.bottom))
            canvas.drawRoundRect(
                track.left, track.top, track.left + filledWidth, track.bottom,
                track.height / 2f, track.height / 2f, progressPaint,
            )
        } finally {
            canvas.restore()
        }
    }

    private fun drawItem(canvas: Canvas, card: CardLayout, item: ItemLayout) {
        val box = item.checkboxRect
        if (box.isValid && box.width > 0f && box.height > 0f) {
            val radius = min(4f, box.width * 0.22f)
            val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (item.checked) card.accent else card.fill
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                box.left, box.top, box.right, box.bottom, radius, radius, boxFill,
            )
            val boxEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = card.edge
                style = Paint.Style.STROKE
                strokeWidth = max(0.6f, box.width * 0.075f)
            }
            canvas.drawRoundRect(
                box.left, box.top, box.right, box.bottom, radius, radius, boxEdge,
            )
            if (item.checked) {
                val check = Path().apply {
                    moveTo(box.left + box.width * 0.22f, box.centerY)
                    lineTo(box.left + box.width * 0.43f, box.top + box.height * 0.72f)
                    lineTo(box.left + box.width * 0.80f, box.top + box.height * 0.27f)
                }
                val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = card.checkInk
                    style = Paint.Style.STROKE
                    strokeWidth = max(0.8f, box.width * 0.13f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(check, checkPaint)
            }
        }
        val textColor = when {
            item.placeholder -> card.edge
            item.checked -> card.mutedInk
            else -> card.ink
        }
        drawTextLines(
            canvas = canvas,
            lines = item.lines,
            clip = item.textRect,
            textSize = item.textSize,
            color = textColor,
            bold = false,
        )
    }

    private fun drawTextLines(
        canvas: Canvas,
        lines: List<TextLine>,
        clip: FRect,
        textSize: Float,
        color: Int,
        bold: Boolean,
    ) {
        if (lines.isEmpty() || !clip.isValid || clip.width <= 0f || clip.height <= 0f ||
            !textSize.isFinite() || textSize <= 0f
        ) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            style = Paint.Style.FILL
            isSubpixelText = true
        }
        canvas.save()
        try {
            canvas.clipRect(RectF(clip.left, clip.top, clip.right, clip.bottom))
            lines.forEach { canvas.drawText(it.text, it.x, it.baseline, paint) }
        } finally {
            canvas.restore()
        }
    }

    /**
     * Greedy, font-independent wrapping. Words break at spaces when possible and
     * Hangul words break by code point when necessary. The final kept line gets an
     * ellipsis; the platform text clip remains a second guard against font metric
     * differences between the estimate and Android's actual sans-serif face.
     */
    internal fun wrapText(
        text: String,
        maxWidth: Float,
        textSize: Float,
        maxLines: Int,
    ): List<String> {
        if (text.isEmpty() || maxLines <= 0) return emptyList()
        val limit = if (maxWidth.isFinite() && maxWidth > 0f) maxWidth else 1f
        val size = if (textSize.isFinite() && textSize > 0f) textSize else 1f
        val wrapped = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { paragraph ->
            val words = paragraph.split(' ', '\t').filter { it.isNotEmpty() }
            if (words.isEmpty()) {
                wrapped += ""
                return@forEach
            }
            var current = ""
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (estimatedTextWidth(candidate, size) <= limit) {
                    current = candidate
                    return@forEach
                }
                if (current.isNotEmpty()) {
                    wrapped += current
                    current = ""
                }
                if (estimatedTextWidth(word, size) <= limit) {
                    current = word
                    return@forEach
                }
                val chunks = splitByWidth(word, limit, size)
                if (chunks.isNotEmpty()) {
                    wrapped += chunks.dropLast(1)
                    current = chunks.last()
                }
            }
            if (current.isNotEmpty()) wrapped += current
        }
        if (wrapped.size <= maxLines) return wrapped
        val kept = wrapped.take(maxLines).toMutableList()
        kept[kept.lastIndex] = withEllipsis(kept[kept.lastIndex], limit, size)
        return kept
    }

    /** Conservative advance estimate: Hangul/CJK one em, Latin about 0.58 em. */
    internal fun estimatedTextWidth(text: String, textSize: Float): Float {
        val size = if (textSize.isFinite() && textSize > 0f) textSize else 1f
        var units = 0f
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            units += codePointAdvance(codePoint)
            index += Character.charCount(codePoint)
        }
        return units * size
    }

    private fun splitByWidth(word: String, limit: Float, textSize: Float): List<String> {
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        var currentWidth = 0f
        var index = 0
        while (index < word.length) {
            val codePoint = word.codePointAt(index)
            val count = Character.charCount(codePoint)
            val pieceWidth = codePointAdvance(codePoint) * textSize
            if (current.isNotEmpty() && currentWidth + pieceWidth > limit) {
                chunks += current.toString()
                current.setLength(0)
                currentWidth = 0f
            }
            current.appendCodePoint(codePoint)
            currentWidth += pieceWidth
            index += count
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun withEllipsis(line: String, limit: Float, textSize: Float): String {
        val suffixWidth = estimatedTextWidth(ELLIPSIS, textSize)
        var end = line.length
        var width = estimatedTextWidth(line, textSize)
        while (end > 0 && width + suffixWidth > limit) {
            val codePoint = line.codePointBefore(end)
            val count = Character.charCount(codePoint)
            end -= count
            width -= codePointAdvance(codePoint) * textSize
        }
        val kept = line.substring(0, end).trimEnd()
        return if (kept.isEmpty()) ELLIPSIS else kept + ELLIPSIS
    }

    private fun codePointAdvance(codePoint: Int): Float = when {
        codePoint == '\t'.code -> 0.9f
        codePoint == ' '.code -> 0.34f
        codePoint < 0x20 || codePoint in 0x7F..0x9F -> 0f
        codePoint < 0x80 -> 0.58f
        else -> 1f
    }

    /** Removes controls and lone surrogates without splitting an emoji code point. */
    private fun cleanText(value: String, multiline: Boolean): String {
        val out = StringBuilder(min(value.length, MAX_RENDER_CHARS))
        var index = 0
        while (index < value.length && out.length < MAX_RENDER_CHARS) {
            val codePoint = value.codePointAt(index)
            val count = Character.charCount(codePoint)
            if (out.length + count > MAX_RENDER_CHARS) break
            when {
                multiline && codePoint == '\n'.code -> out.append('\n')
                codePoint == '\r'.code || codePoint == '\n'.code || codePoint == '\t'.code ->
                    out.append(' ')
                Character.isISOControl(codePoint) -> out.append(' ')
                codePoint in 0xD800..0xDFFF -> out.append('\uFFFD')
                else -> out.appendCodePoint(codePoint)
            }
            index += count
        }
        return out.toString().trim()
    }

    private fun inset(rect: FRect, amount: Float): FRect {
        val safeAmount = amount.coerceIn(0f, max(0f, min(rect.width, rect.height) / 2f))
        return FRect(
            left = rect.left + safeAmount,
            top = rect.top + safeAmount,
            right = rect.right - safeAmount,
            bottom = rect.bottom - safeAmount,
        )
    }

    private fun pageDimension(value: Float): Float =
        if (value.isFinite() && value > 0f) value.coerceIn(1f, MAX_PAGE_DIMENSION) else 1f

    private fun finiteOr(value: Float, fallback: Float): Float =
        if (value.isFinite()) value else fallback

    // Original muted sage palette; no drawable, font file or third-party asset.
    private val CARD_FILL = 0xFFF5F8F3.toInt()
    private val CARD_EDGE = 0xFF4C6B60.toInt()
    private val CARD_ACCENT = 0xFF2F7861.toInt()
    private val CARD_INK = 0xFF263832.toInt()
    private val CARD_MUTED_INK = 0xFF687A72.toInt()
    private val CARD_CHECK_INK = 0xFFFFFFFF.toInt()

    private const val BLANK_ITEM = "빈 항목"
    private const val ELLIPSIS = "…"
    private const val LINE_HEIGHT_FACTOR = 1.18f
    private const val MAX_ITEM_LINES = 4
    private const val MAX_RENDER_CHARS = 2048
    private const val MAX_PAGE_DIMENSION = 8192f
}
