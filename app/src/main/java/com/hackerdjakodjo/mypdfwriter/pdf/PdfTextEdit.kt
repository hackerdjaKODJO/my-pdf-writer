package com.hackerdjakodjo.mypdfwriter.pdf

import kotlin.math.min

/**
 * Pure geometry helpers for the offline **visual** text-replacement overlay.
 *
 * This does not edit PDF text: it only turns the currently selected [PdfTextWord]s
 * into an opaque cover rectangle and a matching font size for a `COVER_TEXT`
 * annotation. The source file's bytes are never touched, and a flattened export
 * simply rasterizes the overlay over the original page. In particular the original
 * words stay in the content stream and remain searchable/extractable elsewhere.
 */
object PdfTextEdit {

    /** Geometry of one cover built from a single-page word selection. */
    data class Geometry(
        val page: Int,
        val rect: NormalizedRect,
        /** Font size as a fraction of page height, ready for `Annotation.fontSize`. */
        val fontSize: Float,
    )

    /**
     * Returns the padded cover rectangle and replacement font size for [words],
     * or `null` when there is no usable selection.
     *
     * Only words from the first word's page participate, so a stale multi-page
     * selection can never produce a rectangle spanning two pages. Blank and
     * degenerate extraction fragments are ignored. The font size is derived from
     * the median of each word's thinner dimension: that is the glyph height for
     * horizontal text and the glyph width for text on a /Rotate 90/270 page.
     */
    fun geometry(words: List<PdfTextWord>): Geometry? {
        val usable = words.filter {
            it.text.isNotBlank() && it.rect.width > 0f && it.rect.height > 0f
        }
        if (usable.isEmpty()) return null

        val page = usable.first().pageIndex
        val samePage = usable.filter { it.pageIndex == page }
        val bounds = samePage.drop(1).fold(samePage.first().rect) { acc, word ->
            acc.union(word.rect)
        }

        val thickness = samePage
            .map { min(it.rect.width, it.rect.height) }
            .sorted()[samePage.size / 2]
            .coerceIn(MIN_THICKNESS, MAX_THICKNESS)
        val padX = thickness * HORIZONTAL_PAD_FACTOR
        val padY = thickness * VERTICAL_PAD_FACTOR
        val rect = NormalizedRect(
            left = (bounds.left - padX).coerceIn(0f, 1f),
            top = (bounds.top - padY).coerceIn(0f, 1f),
            right = (bounds.right + padX).coerceIn(0f, 1f),
            bottom = (bounds.bottom + padY).coerceIn(0f, 1f),
        )
        if (rect.width <= 0f || rect.height <= 0f) return null

        return Geometry(
            page = page,
            rect = rect,
            fontSize = (thickness * FONT_SIZE_FACTOR).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        )
    }

    /** Extra width on each side, relative to a word's glyph thickness. */
    private const val HORIZONTAL_PAD_FACTOR = 0.22f

    /** Extra height on each side, relative to a word's glyph thickness. */
    private const val VERTICAL_PAD_FACTOR = 0.14f

    /** The word box includes ascender/descender space; visible glyphs are smaller. */
    private const val FONT_SIZE_FACTOR = 0.86f

    private const val MIN_THICKNESS = 0.004f
    private const val MAX_THICKNESS = 0.20f
    private const val MIN_FONT_SIZE = 0.012f
    private const val MAX_FONT_SIZE = 0.06f
}
