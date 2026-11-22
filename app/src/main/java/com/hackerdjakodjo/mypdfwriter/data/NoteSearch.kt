package com.hackerdjakodjo.mypdfwriter.data

/** Kind of local note content that matched a search query. */
enum class NoteSearchType(val label: String) {
    MEMO("메모"),
    TEXT("텍스트"),
    STAMP("도장"),
    SIGNATURE("서명"),
    POSTIT("포스트잇"),
    MASKING("가리기 카드"),
    CHECKLIST("체크리스트"),
}

/**
 * One notebook search hit. [pageId] is the stable page identity, so a caller can
 * jump to a page even if the notebook was reordered while the sheet was open.
 * [annotationId] is null for a page memo.
 */
data class NoteSearchResult(
    val pageIndex: Int,
    val pageId: String,
    val type: NoteSearchType,
    val annotationId: String?,
    val snippet: String,
)

/**
 * Pure, offline note-content search.
 *
 * Only local notebook fields are read: page memo and the visible text payload of
 * TEXT, STAMP and SIGNATURE annotations. Handwriting strokes, PDF text, OCR and
 * network sources are deliberately out of scope. Results are deterministic:
 * page order first, then the page memo, then annotations in their stored order.
 * A blank or whitespace-only query always returns no results.
 */
object NoteSearch {

    const val SNIPPET_MAX_LENGTH = 72
    private val whitespace = Regex("\\s+")

    fun search(notebook: Notebook, query: String): List<NoteSearchResult> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()

        val results = ArrayList<NoteSearchResult>()
        notebook.pages.forEachIndexed { pageIndex, page ->
            if (page.memo.contains(needle, ignoreCase = true)) {
                results.add(
                    NoteSearchResult(
                        pageIndex = pageIndex,
                        pageId = page.id,
                        type = NoteSearchType.MEMO,
                        annotationId = null,
                        snippet = snippet(page.memo, needle),
                    ),
                )
            }
            page.annotations.forEach { annotation ->
                val type = when (annotation.type) {
                    AnnotationType.TEXT -> NoteSearchType.TEXT
                    AnnotationType.STAMP -> NoteSearchType.STAMP
                    AnnotationType.SIGNATURE -> NoteSearchType.SIGNATURE
                    else -> null
                } ?: return@forEach
                val text = when (type) {
                    NoteSearchType.TEXT -> annotation.text
                    NoteSearchType.STAMP -> annotation.stamp
                    NoteSearchType.SIGNATURE -> annotation.text.ifBlank { annotation.stamp }
                    else -> ""
                }
                if (text.isNotBlank() && text.contains(needle, ignoreCase = true)) {
                    results.add(
                        NoteSearchResult(
                            pageIndex = pageIndex,
                            pageId = page.id,
                            type = type,
                            annotationId = annotation.id,
                            snippet = snippet(text, needle),
                        ),
                    )
                }
            }
            // Study cards are typed content too: a post-it IS a memo to a
            // student, and flashcard/checklist text was previously invisible.
            page.memoCards.forEach { card ->
                if (card.text.contains(needle, ignoreCase = true)) {
                    results.add(
                        NoteSearchResult(
                            pageIndex, page.id, NoteSearchType.POSTIT, card.id,
                            snippet(card.text, needle),
                        ),
                    )
                }
            }
            page.maskingCards.forEach { card ->
                val text = listOf(card.prompt, card.answer)
                    .filter { it.isNotBlank() }.joinToString(" ")
                if (text.contains(needle, ignoreCase = true)) {
                    results.add(
                        NoteSearchResult(
                            pageIndex, page.id, NoteSearchType.MASKING, card.id,
                            snippet(text, needle),
                        ),
                    )
                }
            }
            page.checklists.forEach { card ->
                val text = (listOf(card.title) + card.items.map { it.text })
                    .filter { it.isNotBlank() }.joinToString(" ")
                if (text.contains(needle, ignoreCase = true)) {
                    results.add(
                        NoteSearchResult(
                            pageIndex, page.id, NoteSearchType.CHECKLIST, card.id,
                            snippet(text, needle),
                        ),
                    )
                }
            }
        }
        return results
    }

    /**
     * A one-line excerpt around the first match. Whitespace is collapsed before
     * searching, the excerpt is bounded to [SNIPPET_MAX_LENGTH] characters and an
     * ellipsis marks a cropped start or end.
     */
    fun snippet(text: String, query: String): String {
        val clean = text.replace(whitespace, " ").trim()
        val needle = query.trim()
        if (clean.isEmpty()) return ""
        if (needle.isEmpty()) return clean.take(SNIPPET_MAX_LENGTH) +
            if (clean.length > SNIPPET_MAX_LENGTH) "…" else ""

        val match = clean.indexOf(needle, ignoreCase = true).coerceAtLeast(0)
        if (clean.length <= SNIPPET_MAX_LENGTH) return clean

        val context = ((SNIPPET_MAX_LENGTH - needle.length) / 2)
            .coerceAtLeast(0)
        var start = (match - context).coerceAtLeast(0)
        var end = (start + SNIPPET_MAX_LENGTH).coerceAtMost(clean.length)
        if (end == clean.length) {
            start = (end - SNIPPET_MAX_LENGTH).coerceAtLeast(0)
        }
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < clean.length) "…" else ""
        return prefix + clean.substring(start, end) + suffix
    }
}
