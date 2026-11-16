package com.hackerdjakodjo.mypdfwriter.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A rectangle in normalized page-display coordinates. (0,0) is the top-left. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun intersects(other: NormalizedRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    fun union(other: NormalizedRect): NormalizedRect = NormalizedRect(
        left = min(left, other.left),
        top = min(top, other.top),
        right = max(right, other.right),
        bottom = max(bottom, other.bottom),
    )
}

/** One selectable word on a page, in display coordinates after the page's own /Rotate. */
data class PdfTextWord(
    val pageIndex: Int,
    val text: String,
    val rect: NormalizedRect,
    /** Page /Rotate for this word, used to orient line markup. */
    val rotation: Int = 0,
)

/** One visual line of selectable text. */
data class PdfTextLine(
    val pageIndex: Int,
    val text: String,
    val words: List<PdfTextWord>,
    val rect: NormalizedRect,
)

/**
 * Extracts per-word page geometry for direct text selection.
 *
 * The coordinates come from PDFBox's [TextPosition], which already accounts for the
 * crop box and page /Rotate. We keep working in PDF user space until the final
 * normalization so rotated text remains aligned with the rendered bitmap.
 */
object PdfTextLayout {

    suspend fun lines(source: File): List<PdfTextLine> = withContext(Dispatchers.IO) {
        PDDocument.load(source).use { document ->
            val stripper = LayoutStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
                wordSeparator = " "
            }
            // getText drives the extraction; LayoutStripper collects its own lines.
            stripper.getText(document)
            stripper.collectedLines()
        }
    }

    suspend fun words(source: File): List<PdfTextWord> = lines(source).flatMap { it.words }

    /** Joins selected words in visual reading order for copy/translate/excerpt actions. */
    fun selectionText(words: List<PdfTextWord>): String {
        if (words.isEmpty()) return ""
        val groups = groupByLine(words)
        return groups.joinToString("\n") { line ->
            line.joinToString(" ") { it.text }
        }
    }

    /**
     * Groups selected words by their original line when [lines] is available, and by
     * vertical overlap otherwise. The returned lists preserve reading order.
     */
    fun groupByLine(
        words: List<PdfTextWord>,
        lines: List<PdfTextLine> = emptyList(),
    ): List<List<PdfTextWord>> {
        if (words.isEmpty()) return emptyList()

        val remaining = LinkedHashSet(words)
        val groups = ArrayList<List<PdfTextWord>>()
        for (line in lines) {
            val selected = line.words.filter { it in remaining }
            if (selected.isNotEmpty()) {
                groups.add(selected)
                remaining.removeAll(selected.toSet())
            }
        }

        val leftover = ArrayList<MutableList<PdfTextWord>>()
        for (word in remaining.sortedWith(compareBy({ it.pageIndex }, { it.rect.top }, { it.rect.left }))) {
            val line = leftover.lastOrNull()
            if (line != null && line.first().pageIndex == word.pageIndex &&
                sameLine(line.first().rect, word.rect)
            ) {
                line.add(word)
            } else {
                leftover.add(mutableListOf(word))
            }
        }
        for (line in leftover) {
            groups.add(line.sortedBy { it.rect.left })
        }
        return groups
    }

    internal fun sameLine(a: NormalizedRect, b: NormalizedRect): Boolean {
        val minHeight = min(a.height, b.height).coerceAtLeast(0.00001f)
        val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        if (overlap >= minHeight * 0.4f) return true
        val centerA = (a.top + a.bottom) / 2f
        val centerB = (b.top + b.bottom) / 2f
        return abs(centerA - centerB) <= minHeight * 0.55f
    }

    private class PendingWord(
        val text: String,
        val rect: NormalizedRect,
        val separatorBefore: Boolean,
    ) {
        fun merge(other: PendingWord): PendingWord = PendingWord(
            text = text + other.text,
            rect = rect.union(other.rect),
            separatorBefore = separatorBefore,
        )
    }

    private data class PageGeometry(
        val width: Float,
        val height: Float,
        val rotation: Int,
    ) {
        fun wordRect(positions: List<TextPosition>): NormalizedRect? {
            var union: NormalizedRect? = null
            for (position in positions) {
                val unicode = position.unicode
                if (unicode.isNullOrBlank()) continue

                val matrix = position.textMatrix
                val advanceLength = hypot(matrix.scaleX, matrix.shearY)
                val upLength = hypot(matrix.shearX, matrix.scaleY)
                val advanceX = if (advanceLength > 0.00001f) matrix.scaleX / advanceLength else 1f
                val advanceY = if (advanceLength > 0.00001f) matrix.shearY / advanceLength else 0f
                val upX = if (upLength > 0.00001f) matrix.shearX / upLength else 0f
                val upY = if (upLength > 0.00001f) matrix.scaleY / upLength else 1f

                // getWidth() is unreliable on rotated pages in PDFBox 2.0.x; the
                // direction-adjusted width follows the text baseline and is correct.
                val advance = position.widthDirAdj
                val glyphHeight = position.heightDir
                val rect = PdfTextGeometry.glyphRect(
                    baselineX = matrix.translateX,
                    baselineY = matrix.translateY,
                    advanceX = advanceX * advance,
                    advanceY = advanceY * advance,
                    upX = upX * glyphHeight,
                    upY = upY * glyphHeight,
                    pageWidth = width,
                    pageHeight = height,
                    rotation = rotation,
                ) ?: continue
                union = union?.union(rect) ?: rect
            }
            return union
        }

        companion object {
            fun from(page: PDPage): PageGeometry? {
                val box = page.cropBox ?: return null
                if (box.width <= 0f || box.height <= 0f) return null
                val rotation = normalizeRotation(page.rotation)
                // Anything other than a right-angle /Rotate would need a full affine
                // transform to match the rasterizer; skip it rather than draw wrong boxes.
                if (rotation % 90 != 0) return null
                return PageGeometry(box.width, box.height, rotation)
            }
        }
    }

    private class LayoutStripper : PDFTextStripper() {
        private val result = ArrayList<PdfTextLine>()
        private val currentLine = ArrayList<PendingWord>()
        private var geometry: PageGeometry? = null
        private var pageIndex = -1
        private var pendingLineBreak = false
        private var pendingWordBreak = false

        fun collectedLines(): List<PdfTextLine> = result.toList()

        override fun startPage(page: PDPage) {
            super.startPage(page)
            flushLine()
            pageIndex = currentPageNo - 1
            geometry = PageGeometry.from(page)
            pendingLineBreak = false
            pendingWordBreak = false
        }

        override fun endPage(page: PDPage) {
            flushLine()
            geometry = null
            super.endPage(page)
        }

        override fun writeLineSeparator() {
            pendingLineBreak = true
        }

        override fun writeWordSeparator() {
            pendingWordBreak = true
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            val pageGeometry = geometry ?: return
            val value = text.trim()
            if (value.isEmpty() || textPositions.isEmpty()) return
            val rect = pageGeometry.wordRect(textPositions) ?: return

            val separatorBefore = pendingWordBreak
            pendingWordBreak = false
            if (pendingLineBreak) {
                flushLine()
                pendingLineBreak = false
                pendingWordBreak = false
            }

            if (currentLine.isNotEmpty() && !sameLine(currentLine.last().rect, rect)) {
                flushLine()
            }
            currentLine.add(PendingWord(value, rect, separatorBefore))
        }

        private fun flushLine() {
            if (currentLine.isEmpty()) return
            val merged = ArrayList<PendingWord>()
            for (word in currentLine) {
                val previous = merged.lastOrNull()
                val gap = if (previous != null) {
                    max(
                        word.rect.left - previous.rect.right,
                        previous.rect.left - word.rect.right,
                    )
                } else {
                    Float.MAX_VALUE
                }
                val mergeLimit = if (previous != null) {
                    max(previous.rect.height, word.rect.height) * 0.12f + 0.0005f
                } else {
                    0f
                }
                if (previous != null && !word.separatorBefore && gap <= mergeLimit) {
                    merged[merged.lastIndex] = previous.merge(word)
                } else {
                    merged.add(word)
                }
            }

            val pageRotation = geometry?.rotation ?: 0
            val words = merged.map { PdfTextWord(pageIndex, it.text, it.rect, pageRotation) }
            if (words.isNotEmpty()) {
                val rect = words.drop(1).fold(words.first().rect) { acc, word -> acc.union(word.rect) }
                result.add(
                    PdfTextLine(
                        pageIndex = pageIndex,
                        text = words.joinToString(" ") { it.text },
                        words = words,
                        rect = rect,
                    ),
                )
            }
            currentLine.clear()
        }
    }
}

/**
 * Pure page-rotation math, kept free of PDFBox and Android so it can be unit-tested.
 */
internal object PdfTextGeometry {

    /**
     * Returns the normalized display box of one glyph run.
     *
     * [baselineX]/[baselineY] and the advance/up vectors are in PDF user space
     * (origin bottom-left, crop-box translated). [rotation] is the page /Rotate.
     */
    fun glyphRect(
        baselineX: Float,
        baselineY: Float,
        advanceX: Float,
        advanceY: Float,
        upX: Float,
        upY: Float,
        pageWidth: Float,
        pageHeight: Float,
        rotation: Int,
        descentFraction: Float = 0.18f,
    ): NormalizedRect? {
        if (pageWidth <= 0f || pageHeight <= 0f) return null
        val normalizedRotation = normalizeRotation(rotation)
        if (normalizedRotation % 90 != 0) return null

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        fun add(x: Float, y: Float): Boolean {
            if (!x.isFinite() || !y.isFinite()) return false
            val point = mapPoint(x, y, pageWidth, pageHeight, normalizedRotation) ?: return false
            minX = min(minX, point.first)
            minY = min(minY, point.second)
            maxX = max(maxX, point.first)
            maxY = max(maxY, point.second)
            return true
        }

        val descentX = upX * descentFraction
        val descentY = upY * descentFraction
        val ok = add(baselineX, baselineY) &&
            add(baselineX + advanceX, baselineY + advanceY) &&
            add(baselineX - descentX, baselineY - descentY) &&
            add(baselineX + advanceX - descentX, baselineY + advanceY - descentY) &&
            add(baselineX + upX, baselineY + upY) &&
            add(baselineX + advanceX + upX, baselineY + advanceY + upY)
        if (!ok) return null

        // Text outside the crop box is invisible; ignore it rather than pinning it to an edge.
        if (maxX <= 0f || maxY <= 0f || minX >= 1f || minY >= 1f) return null

        val left = minX.coerceIn(0f, 1f)
        val top = minY.coerceIn(0f, 1f)
        val right = maxX.coerceIn(0f, 1f)
        val bottom = maxY.coerceIn(0f, 1f)
        if (right - left < 0.00001f || bottom - top < 0.00001f) return null
        return NormalizedRect(left, top, right, bottom)
    }

    /** Maps a PDF user-space point to normalized display coordinates. */
    fun mapPoint(
        x: Float,
        y: Float,
        pageWidth: Float,
        pageHeight: Float,
        rotation: Int,
    ): Pair<Float, Float>? {
        if (pageWidth <= 0f || pageHeight <= 0f) return null
        return when (normalizeRotation(rotation)) {
            0 -> x / pageWidth to 1f - y / pageHeight
            90 -> y / pageHeight to x / pageWidth
            180 -> 1f - x / pageWidth to y / pageHeight
            270 -> 1f - y / pageHeight to 1f - x / pageWidth
            else -> null
        }
    }

    private fun normalizeRotation(value: Int): Int = ((value % 360) + 360) % 360
}

private fun normalizeRotation(value: Int): Int = ((value % 360) + 360) % 360
