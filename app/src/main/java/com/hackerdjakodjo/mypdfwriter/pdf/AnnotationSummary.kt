package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import java.io.IOException
import java.io.OutputStream

/**
 * Pure, offline builder for the text of the `주석 요약 내보내기` action in the
 * unified Locations panel.
 *
 * It only reads annotations and returns strings: it never writes a file, never
 * touches the Android framework and never mutates the source list. The caller
 * hands the returned text to SAF (or any other export path) later.
 */
object AnnotationSummary {

    /** One display row: page, type, color name and a single-line snippet. */
    data class Row(
        val page: Int,
        val type: AnnotationType,
        val colorName: String,
        val snippet: String,
    )

    /** Stable Korean label for every stored annotation type. */
    fun typeLabel(type: AnnotationType): String = when (type) {
        AnnotationType.PEN -> "필기"
        AnnotationType.HIGHLIGHT -> "형광펜"
        AnnotationType.UNDERLINE -> "밑줄"
        AnnotationType.STRIKEOUT -> "취소선"
        AnnotationType.SQUIGGLY -> "물결선"
        AnnotationType.SIGNATURE -> "서명"
        AnnotationType.TEXT -> "글상자"
        AnnotationType.STAMP -> "도장"
        AnnotationType.COVER_TEXT -> "덮어쓰기"
        AnnotationType.RECT -> "사각형"
        AnnotationType.TRIANGLE -> "삼각형"
        AnnotationType.ELLIPSE -> "타원"
        AnnotationType.LINE -> "직선"
        AnnotationType.ARROW -> "화살표"
        AnnotationType.REDACT -> "가림"
    }

    private data class NamedColor(val label: String, val rgb: Int)

    /**
     * Small fixed palette. Nearest-colour matching keeps the output independent
     * of the device locale and of the exact swatch the user picked. Palette order
     * is the tie-breaker so equal distances always resolve the same way.
     */
    private val palette = listOf(
        NamedColor("검정", 0x000000),
        NamedColor("회색", 0x757575),
        NamedColor("하양", 0xFFFFFF),
        NamedColor("빨강", 0xE53935),
        NamedColor("주황", 0xFB8C00),
        NamedColor("노랑", 0xFDD835),
        NamedColor("초록", 0x43A047),
        NamedColor("파랑", 0x1E88E5),
        NamedColor("남색", 0x3949AB),
        NamedColor("보라", 0x8E24AA),
        NamedColor("분홍", 0xD81B60),
    )

    /** Human-readable name for the closest palette colour (alpha ignored). */
    fun colorName(color: Int): String {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        var best = palette.first()
        var bestDistance = Long.MAX_VALUE
        palette.forEach { named ->
            val pr = (named.rgb shr 16) and 0xFF
            val pg = (named.rgb shr 8) and 0xFF
            val pb = named.rgb and 0xFF
            val dr = (r - pr).toLong()
            val dg = (g - pg).toLong()
            val db = (b - pb).toLong()
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = named
            }
        }
        return best.label
    }

    /** Stable, single-line snippet; fills in a short cue for textless marks. */
    fun snippet(annotation: Annotation): String {
        val text = collapse(annotation.text)
        val stamp = collapse(annotation.stamp)
        return when {
            annotation.type == AnnotationType.STAMP && stamp.isNotEmpty() -> stamp
            annotation.type == AnnotationType.SIGNATURE && text.isNotEmpty() -> text
            annotation.type == AnnotationType.COVER_TEXT && text.isNotEmpty() -> text
            text.isNotEmpty() -> text
            stamp.isNotEmpty() -> stamp
            annotation.type == AnnotationType.PEN -> "필기 획"
            annotation.type == AnnotationType.HIGHLIGHT -> "형광 표시"
            annotation.type == AnnotationType.REDACT -> "가림 영역"
            else -> "도형"
        }
    }

    /**
     * Sanitized rows ordered exactly like the Locations panel: ascending page,
     * then stored type order, with Kotlin's stable sort preserving the document
     * order inside a page/type group. No random id/text tiebreak — adding one
     * made the export disagree with the panel on visually identical rows.
     * Blanks, duplicate ids and out-of-range pages are dropped just like the
     * panel filter, so the export can never contain a row the UI cannot show.
     */
    fun rows(annotations: List<Annotation>, pageCount: Int = Int.MAX_VALUE): List<Row> {
        val limit = pageCount.coerceAtLeast(0)
        if (annotations.isEmpty() || limit == 0) return emptyList()
        val seenIds = HashSet<String>(annotations.size)
        return annotations
            .asSequence()
            .filter { it.id.isNotBlank() && it.page >= 0 && it.page < limit }
            .filter { seenIds.add(it.id) }
            .sortedWith(compareBy<Annotation>({ it.page }, { it.type.ordinal }))
            .map { Row(it.page, it.type, colorName(it.color), snippet(it)) }
            .toList()
    }

    /** Markdown/text lines grouped with a `## N쪽` heading per page. */
    fun lines(annotations: List<Annotation>, pageCount: Int = Int.MAX_VALUE): List<String> {
        val grouped = rows(annotations, pageCount).groupBy { it.page }.toSortedMap()
        val out = ArrayList<String>()
        out.add("# 주석 요약")
        if (grouped.isEmpty()) {
            out.add("")
            out.add("주석이 없습니다.")
            return out
        }
        grouped.forEach { (page, pageRows) ->
            out.add("")
            out.add("## ${page + 1}쪽")
            out.add("")
            pageRows.forEach { row ->
                val pageNumber = page + 1
                out.add(
                    "- **${typeLabel(row.type)}** · ${pageNumber}쪽 · ${row.colorName} — ${escape(row.snippet)}",
                )
            }
        }
        return out
    }

    /** Complete Markdown document, ending in exactly one newline. */
    fun buildMarkdown(annotations: List<Annotation>, pageCount: Int = Int.MAX_VALUE): String =
        lines(annotations, pageCount).joinToString("\n").trimEnd() + "\n"

    private fun collapse(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun escape(value: String): String =
        value.replace("|", "\\|").replace("\r", " ").replace("\n", " ")
}

/**
 * Pure handoff between the annotation-summary text and the SAF write action.
 *
 * The composable keeps the pending text in `rememberSaveable` so it survives the
 * Activity recreation that a `CreateDocument` picker causes. This object owns
 * the two decisions the callback needs — whether to write, and the exact UTF-8
 * payload — so both can be unit-tested without Android. The caller always clears
 * the pending text afterwards, including when the picker is cancelled.
 */
object SummarySafWriter {

    enum class Outcome { WRITE, CLEAR }

    /** A cancelled/blank request must clear the pending text rather than keep it. */
    fun outcomeForResult(uriGranted: Boolean, pending: String): Outcome =
        if (uriGranted && pending.isNotBlank()) Outcome.WRITE else Outcome.CLEAR

    /** Exact bytes handed to SAF: UTF-8, no BOM, no locale-dependent charset. */
    fun utf8(pending: String): ByteArray = pending.toByteArray(Charsets.UTF_8)

    /** Writes the UTF-8 payload; false only when the stream itself rejects it. */
    fun write(out: OutputStream, pending: String): Boolean = try {
        out.write(utf8(pending))
        out.flush()
        true
    } catch (_: IOException) {
        false
    }
}
