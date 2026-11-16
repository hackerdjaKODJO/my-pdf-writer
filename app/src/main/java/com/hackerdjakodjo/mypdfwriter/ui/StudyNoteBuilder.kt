package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import kotlin.math.floor

/** The PDF a study notebook was captured from. [docUri] is optional on purpose. */
data class StudySource(val docName: String, val docUri: String? = null)

/**
 * Turns PDF excerpts into an ordinary [Notebook] so the existing note editor,
 * layers, export and library flows keep working unchanged.
 *
 * Each excerpt becomes one page whose TEXT annotation holds the excerpt and whose
 * [NotePage.sourcePageIndex] remembers the source PDF page for a future backlink.
 */
object StudyNoteBuilder {

    val DEFAULT_COVER_COLOR: Int = 0xFFD8E6F3.toInt()

    private const val INK_COLOR = 0xFF1B1C19.toInt()
    /** A4 portrait, matching PaperBackground.A4_ASPECT. */
    private const val PAGE_ASPECT = NotePaper.PORTRAIT_ASPECT
    private const val TEXT_LEFT = 0.075f
    private const val TEXT_TOP = 0.09f
    private const val TEXT_WIDTH_FRACTION = 0.86f
    private const val TEXT_HEIGHT_FRACTION = 0.84f
    private const val LINE_HEIGHT_FACTOR = 1.3f
    private const val BASE_FONT_SIZE = 0.019f
    private const val MIN_FONT_SIZE = 0.012f

    fun fromExcerpts(
        title: String,
        source: StudySource,
        excerpts: List<Excerpt>,
        coverColor: Int = DEFAULT_COVER_COLOR,
        coverStyle: CoverStyle = CoverStyle.SOLID,
    ): Notebook {
        val now = System.currentTimeMillis()
        val selected = excerpts.filter { it.text.isNotBlank() }
        return Notebook(
            title = title.trim().ifBlank { suggestedTitle(source) },
            coverColor = coverColor,
            coverStyle = coverStyle,
            pages = selected.map(::excerptPage),
            sourceDocumentName = source.docName.trim(),
            sourceDocumentUri = source.docUri?.trim().orEmpty(),
            createdAt = now,
            updatedAt = now,
        ).normalized()
    }

    /**
     * Appends one captured excerpt to an existing study notebook. The new page uses
     * the exact layout of [fromExcerpts] so a drag-and-drop capture and a button
     * capture open identically in the note editor.
     *
     * [coverColor] is optional; pass it only when the caller is initializing a
     * notebook cover, otherwise the notebook keeps its current cover.
     */
    fun appendExcerpt(
        notebook: Notebook,
        source: StudySource,
        excerpt: Excerpt,
        coverColor: Int? = null,
    ): Notebook {
        if (excerpt.text.isBlank()) return notebook
        val base = notebook.normalized()
        return base.copy(
            coverColor = coverColor ?: base.coverColor,
            pages = base.pages + excerptPage(excerpt),
            sourceDocumentName = base.sourceDocumentName.ifBlank { source.docName.trim() },
            sourceDocumentUri = base.sourceDocumentUri.ifBlank { source.docUri?.trim().orEmpty() },
            updatedAt = System.currentTimeMillis(),
        ).normalized()
    }

    /**
     * Source PDF page for a notebook page, or null for plain pages. The integration
     * can use this to show a "원본 PDF로" action without depending on note internals.
     */
    fun sourcePage(notebook: Notebook, pageIndex: Int): Int? =
        notebook.pages.getOrNull(pageIndex)?.sourcePageIndex

    fun suggestedTitle(source: StudySource): String {
        val base = source.docName
            .substringBeforeLast('.', source.docName)
            .trim()
            .take(40)
        return if (base.isBlank()) "스터디 노트" else "$base 스터디 노트"
    }

    /**
     * A blank, editable notebook linked to [source]. The PDF study pane uses this
     * when the reader has no study note for the open document yet. It is not
     * persisted here; the caller decides whether to save it detached.
     */
    fun blankStudyNote(source: StudySource): Notebook = Notebook(
        title = suggestedTitle(source),
        coverColor = DEFAULT_COVER_COLOR,
        pages = listOf(NotePage(template = PaperTemplate.LINED)),
        sourceDocumentName = source.docName.trim(),
        sourceDocumentUri = source.docUri?.trim().orEmpty(),
    ).normalized()

    /**
     * Finds the notebook already linked to [source] in a library snapshot. A
     * source URI match wins over a document-name or suggested-title match, and
     * trashed notebooks are never selected. Pure so the PDF pane's detached
     * lookup can be tested without an Android view model.
     */
    fun findStudyNote(notebooks: List<Notebook>, source: StudySource): Notebook? {
        val candidates = notebooks.filterNot { it.trashed }
        val uri = source.docUri?.trim().orEmpty()
        if (uri.isNotBlank()) {
            candidates.firstOrNull { it.sourceDocumentUri.equals(uri, ignoreCase = true) }
                ?.let { return it }
        }
        val docName = source.docName.trim()
        if (docName.isBlank()) return null
        val suggestedTitle = suggestedTitle(source)
        return candidates.firstOrNull { notebook ->
            notebook.sourceDocumentName.equals(docName, ignoreCase = true) ||
                notebook.title.equals(suggestedTitle, ignoreCase = true)
        }
    }

    private fun excerptPage(excerpt: Excerpt): NotePage {
        val sourcePage = excerpt.pageIndex.takeIf { it >= 0 }
        val layer = NoteLayer(name = "발췌")
        val layout = layoutExcerpt(excerpt.text)
        val annotation = Annotation(
            type = AnnotationType.TEXT,
            page = 0,
            color = INK_COLOR,
            width = 0.0025f,
            points = listOf(NormalizedPoint(TEXT_LEFT, TEXT_TOP)),
            text = layout.lines.joinToString("\n"),
            fontSize = layout.fontSize,
            opacity = 1f,
            layerId = layer.id,
        )
        return NotePage(
            template = PaperTemplate.LINED,
            annotations = listOf(annotation),
            memo = sourcePage?.let { "원본 PDF p.${it + 1}" } ?: "원본 PDF",
            sourcePageIndex = sourcePage,
            layers = listOf(layer),
        )
    }

    private data class ExcerptLayout(val fontSize: Float, val lines: List<String>)

    /**
     * The text painter draws unwrapped lines, so wrap here. Short notes keep the
     * comfortable base size; very long captures shrink to [MIN_FONT_SIZE] and only
     * pathological excerpts are shortened with an ellipsis.
     */
    private fun layoutExcerpt(text: String): ExcerptLayout {
        var fontSize = BASE_FONT_SIZE
        var lines = wrapText(text, fontSize)
        repeat(4) {
            val maxLines = maxLinesFor(fontSize)
            if (lines.size <= maxLines) return ExcerptLayout(fontSize, lines)
            val fitted = (fontSize * maxLines.toFloat() / lines.size)
                .coerceAtLeast(MIN_FONT_SIZE)
            if (fitted >= fontSize - 0.0001f) return@repeat
            fontSize = fitted
            lines = wrapText(text, fontSize)
        }
        val maxLines = maxLinesFor(fontSize)
        if (lines.size > maxLines) {
            val shortened = lines.take(maxLines).toMutableList()
            if (shortened.isNotEmpty()) {
                shortened[shortened.lastIndex] = shortened.last().trimEnd() + " …"
            }
            lines = shortened
        }
        return ExcerptLayout(fontSize, lines)
    }

    private fun maxLinesFor(fontSize: Float): Int =
        floor(TEXT_HEIGHT_FRACTION / (fontSize * LINE_HEIGHT_FACTOR)).toInt().coerceAtLeast(1)

    private fun wrapText(text: String, fontSize: Float): List<String> {
        val maxWidth = TEXT_WIDTH_FRACTION * PAGE_ASPECT / fontSize
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isEmpty()) return listOf("")
        return normalized.split('\n').flatMap { wrapParagraph(it.trim(), maxWidth) }
    }

    private fun wrapParagraph(paragraph: String, maxWidth: Float): List<String> {
        if (paragraph.isEmpty()) return listOf("")
        val words = paragraph.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return listOf("")

        val lines = ArrayList<String>()
        val current = StringBuilder()
        var currentWidth = 0f
        val spaceWidth = charWidth(' ')

        fun flush() {
            if (current.isNotEmpty()) {
                lines += current.toString()
                current.setLength(0)
                currentWidth = 0f
            }
        }

        words.forEach { word ->
            val wordWidth = textWidth(word)
            if (wordWidth > maxWidth) {
                flush()
                var chunk = StringBuilder()
                var chunkWidth = 0f
                word.forEach { ch ->
                    val width = charWidth(ch)
                    if (chunk.isNotEmpty() && chunkWidth + width > maxWidth) {
                        lines += chunk.toString()
                        chunk = StringBuilder()
                        chunkWidth = 0f
                    }
                    chunk.append(ch)
                    chunkWidth += width
                }
                current.append(chunk)
                currentWidth = chunkWidth
            } else {
                if (current.isNotEmpty() && currentWidth + spaceWidth + wordWidth > maxWidth) {
                    flush()
                }
                if (current.isNotEmpty()) {
                    current.append(' ')
                    currentWidth += spaceWidth
                }
                current.append(word)
                currentWidth += wordWidth
            }
        }
        flush()
        return if (lines.isEmpty()) listOf("") else lines
    }

    private val WHITESPACE = Regex("\\s+")

    private fun textWidth(text: String): Float {
        var width = 0f
        text.forEach { width += charWidth(it) }
        return width
    }

    /**
     * Approximate advance in em. Wide (CJK) glyphs take one em, Latin lowercase
     * about half, which keeps the estimate conservative for mixed Korean text.
     */
    private fun charWidth(ch: Char): Float = when {
        ch == ' ' -> 0.30f
        ch == '\t' -> 0.90f
        ch.code < 0x20 -> 0f
        ch.code < 0x80 -> when (ch) {
            in 'A'..'Z' -> 0.62f
            'i', 'l', 'j', 't', 'f', 'r', 'I' -> 0.32f
            'm', 'w', 'M', 'W' -> 0.92f
            in '0'..'9' -> 0.56f
            '.', ',', ':', ';', '\'', '|' -> 0.28f
            else -> 0.52f
        }
        ch.isWideGlyph() -> 1f
        else -> 0.72f
    }

    private fun Char.isWideGlyph(): Boolean {
        val value = code
        return value in 0x1100..0x115F ||
            value in 0x2E80..0xA4CF ||
            value in 0xAC00..0xD7A3 ||
            value in 0xF900..0xFAFF ||
            value in 0xFE10..0xFE6F ||
            value in 0xFF00..0xFF60 ||
            value in 0xFFE0..0xFFE6
    }
}
