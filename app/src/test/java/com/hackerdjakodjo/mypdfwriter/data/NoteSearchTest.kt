package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteSearchTest {

    private fun annotation(
        id: String,
        type: AnnotationType,
        text: String = "",
        stamp: String = "",
    ) = Annotation(
        id = id,
        type = type,
        page = 0,
        color = 0,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.2f, 0.2f)),
        text = text,
        stamp = stamp,
    )

    private fun notebook(vararg pages: NotePage) =
        Notebook(id = "notebook", title = "생물 노트", pages = pages.toList())

    @Test
    fun blankQueryReturnsNoResults() {
        val source = notebook(
            NotePage(memo = "세포막", annotations = listOf(annotation("a", AnnotationType.TEXT, "세포"))),
        )

        assertTrue(NoteSearch.search(source, "").isEmpty())
        assertTrue(NoteSearch.search(source, "   ").isEmpty())
        assertTrue(NoteSearch.search(source, "\n\t").isEmpty())
    }

    @Test
    fun memoAndTextStampSignatureMatchCaseInsensitively() {
        val source = notebook(
            NotePage(
                memo = "Chapter NEEDLE summary",
                annotations = listOf(
                    annotation("text", AnnotationType.TEXT, "a needle in the text"),
                    annotation("stamp", AnnotationType.STAMP, stamp = "NEEDLE stamp"),
                    annotation("signature", AnnotationType.SIGNATURE, text = "Needle signature"),
                    annotation("legacy-signature", AnnotationType.SIGNATURE, stamp = "needle stamp fallback"),
                ),
            ),
        )

        val results = NoteSearch.search(source, "needle")

        assertEquals(
            listOf(NoteSearchType.MEMO, NoteSearchType.TEXT, NoteSearchType.STAMP,
                NoteSearchType.SIGNATURE, NoteSearchType.SIGNATURE),
            results.map { it.type },
        )
        assertEquals(listOf(null, "text", "stamp", "signature", "legacy-signature"),
            results.map { it.annotationId })
        assertEquals(listOf(0, 0, 0, 0, 0), results.map { it.pageIndex })
        assertTrue(results.all { it.pageId == source.pages.single().id })
    }

    @Test
    fun onlyTextBearingAnnotationTypesAndMemosAreSearched() {
        val source = notebook(
            NotePage(
                memo = "",
                annotations = listOf(
                    annotation("pen", AnnotationType.PEN, text = "needle in ink metadata"),
                    annotation("rect", AnnotationType.RECT, text = "needle in shape"),
                    annotation("redact", AnnotationType.REDACT, text = "needle redaction"),
                    annotation("text", AnnotationType.TEXT, text = "needle text"),
                ),
            ),
        )

        val results = NoteSearch.search(source, "needle")

        assertEquals(listOf("text"), results.map { it.annotationId })
        assertEquals(NoteSearchType.TEXT, results.single().type)
    }

    @Test
    fun resultsAreDeterministicByPageThenMemoThenAnnotationOrder() {
        val source = notebook(
            NotePage(
                id = "p0",
                memo = "needle memo",
                annotations = listOf(
                    annotation("a0", AnnotationType.TEXT, "needle first"),
                    annotation("a1", AnnotationType.TEXT, "needle second"),
                ),
            ),
            NotePage(
                id = "p1",
                memo = "nothing here",
                annotations = listOf(
                    annotation("b0", AnnotationType.STAMP, stamp = "needle third"),
                ),
            ),
            NotePage(
                id = "p2",
                memo = "needle fourth",
            ),
        )

        val first = NoteSearch.search(source, "NEEDLE")
        val second = NoteSearch.search(source, "needle")

        assertEquals(first, second)
        assertEquals(
            listOf("p0" to NoteSearchType.MEMO, "p0" to NoteSearchType.TEXT,
                "p0" to NoteSearchType.TEXT, "p1" to NoteSearchType.STAMP,
                "p2" to NoteSearchType.MEMO),
            first.map { it.pageId to it.type },
        )
        assertEquals(listOf("a0", "a1", "b0"),
            first.mapNotNull { it.annotationId })
    }

    @Test
    fun snippetCollapsesWhitespaceAndCropsAroundTheMatch() {
        val memo = "앞부분 ".repeat(30) + "중요한 검색어" + " 뒷부분".repeat(30)
        val source = notebook(NotePage(memo = memo, annotations = emptyList()))

        val snippet = NoteSearch.search(source, "검색어").single().snippet

        assertTrue("snippet should mark a cropped start", snippet.startsWith("…"))
        assertTrue("snippet should mark a cropped end", snippet.endsWith("…"))
        assertTrue(snippet.contains("검색어"))
        assertTrue("snippet should be short", snippet.length <= NoteSearch.SNIPPET_MAX_LENGTH + 2)
        assertTrue("snippet should be one line", '\n' !in snippet)
    }

    @Test
    fun shortTextStaysCompleteAndUnmarked() {
        val short = NoteSearch.snippet("a  short\nmemo", "short")

        assertEquals("a short memo", short)
    }

    @Test
    fun emptyNotebookAndPagesWithoutTextAreSafe() {
        assertTrue(NoteSearch.search(Notebook(pages = emptyList()), "needle").isEmpty())
        assertTrue(
            NoteSearch.search(
                notebook(NotePage(memo = "", annotations = listOf(annotation("p", AnnotationType.PEN)))),
                "needle",
            ).isEmpty(),
        )
    }
}
