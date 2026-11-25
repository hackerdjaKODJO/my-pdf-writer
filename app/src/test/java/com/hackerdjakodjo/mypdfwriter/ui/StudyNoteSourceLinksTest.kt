package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNoteSourceLinksTest {

    private fun text(value: String): Annotation = Annotation(
        type = AnnotationType.TEXT,
        page = 0,
        color = 0xFF1B1C19.toInt(),
        width = 0.0025f,
        text = value,
    )

    private fun page(
        id: String = java.util.UUID.randomUUID().toString(),
        sourcePageIndex: Int? = null,
        memo: String = "",
        text: String = "",
        sourceLink: StudySourceLink? = null,
    ): NotePage = NotePage(
        id = id,
        sourcePageIndex = sourcePageIndex,
        memo = memo,
        annotations = if (text.isBlank()) emptyList() else listOf(text(text)),
        sourceLink = sourceLink,
    )

    private val sourceUri = "content://docs/source"

    @Test
    fun pagesWithoutSourceLinksAreIgnored() {
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                page(sourcePageIndex = null, text = "일반 노트"),
                page(sourcePageIndex = 3, text = "PDF 발췌"),
                page(sourcePageIndex = -1),
            ),
        )

        val links = StudyNoteSourceLinks.fromNotebook(notebook)

        assertEquals(1, links.size)
        assertEquals(1, links.single().notePageIndex)
        assertEquals(3, links.single().sourcePageIndex)
        assertEquals("PDF 발췌", links.single().preview)
    }

    @Test
    fun linksAreSortedByPdfPageThenNotePageRegardlessOfNotebookOrder() {
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                page(sourcePageIndex = 7, text = "노트 0"),
                page(sourcePageIndex = 2, text = "노트 1"),
                page(sourcePageIndex = 7, text = "노트 2"),
                page(sourcePageIndex = 0, text = "노트 3"),
                page(sourcePageIndex = 2, text = "노트 4"),
            ),
        )

        val links = StudyNoteSourceLinks.fromNotebook(notebook)

        assertEquals(listOf(0, 2, 2, 7, 7), links.map { it.sourcePageIndex })
        assertEquals(listOf(3, 1, 4, 0, 2), links.map { it.notePageIndex })
    }

    @Test
    fun groupingKeepsPdfPagesAscendingAndNotePagesAscendingInsideAGroup() {
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                page(sourcePageIndex = 5, text = "다섯 A"),
                page(sourcePageIndex = 1, text = "하나"),
                page(sourcePageIndex = 5, text = "다섯 B"),
                page(sourcePageIndex = 1, text = "하나 B"),
            ),
        )

        val groups = StudyNoteSourceLinks.grouped(notebook)

        assertEquals(listOf(1, 5), groups.map { it.sourcePageIndex })
        assertEquals(listOf(1, 3), groups[0].links.map { it.notePageIndex })
        assertEquals(listOf(0, 2), groups[1].links.map { it.notePageIndex })
        assertEquals(listOf("하나", "하나 B"), groups[0].links.map { it.preview })
    }

    @Test
    fun emptyNotebookOrPlainNotebookHasNoGroups() {
        assertTrue(StudyNoteSourceLinks.grouped(Notebook()).isEmpty())
        assertTrue(
            StudyNoteSourceLinks.grouped(
                Notebook(pages = listOf(page(sourcePageIndex = null, text = "메모"))),
            ).isEmpty(),
        )
    }

    @Test
    fun previewCollapsesWhitespaceAndFallsBackToThePageMemo() {
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                page(sourcePageIndex = 0, text = "첫 줄\n\n둘째   줄\t셋째"),
                page(sourcePageIndex = 1, memo = "원본 PDF p.2"),
            ),
        )

        val links = StudyNoteSourceLinks.fromNotebook(notebook)

        assertEquals("첫 줄 둘째 줄 셋째", links[0].preview)
        assertEquals("원본 PDF p.2", links[1].preview)
        assertEquals("원본 PDF p.2", links[1].memo)
    }

    @Test
    fun previewUsesTheFirstTextAnnotationAndIsBounded() {
        val longText = "가".repeat(200)
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                NotePage(
                    sourcePageIndex = 4,
                    annotations = listOf(
                        Annotation(
                            type = AnnotationType.PEN,
                            page = 0,
                            color = 0,
                            width = 0.004f,
                        ),
                        text("  첫 번째\n텍스트  "),
                        text(longText),
                    ),
                ),
                NotePage(
                    sourcePageIndex = 5,
                    annotations = listOf(text(longText)),
                ),
            ),
        )

        val links = StudyNoteSourceLinks.fromNotebook(notebook)

        assertEquals("첫 번째 텍스트", links[0].preview)
        assertEquals(120, links[1].preview.length)
    }

    @Test
    fun linksExposeStableNoteAndPageIdentityAcrossReordering() {
        val first = page(id = "page-a", sourcePageIndex = 2, text = "A")
        val second = page(id = "page-b", sourcePageIndex = 1, text = "B")
        val notebook = Notebook(
            id = "note-1",
            sourceDocumentUri = sourceUri,
            pages = listOf(first, second),
        )

        val before = StudyNoteSourceLinks.fromNotebook(notebook)
        val reordered = StudyNoteSourceLinks.fromNotebook(
            notebook.copy(pages = listOf(second, first)),
        )

        assertEquals(listOf("note-1", "note-1"), before.map { it.noteId })
        assertEquals(listOf("page-b", "page-a"), before.map { it.pageId })
        assertEquals(listOf("page-b", "page-a"), reordered.map { it.pageId })
        assertEquals(listOf("page-b", "page-a"), reordered.map { it.stableKey })
        assertEquals(listOf(0, 1), reordered.map { it.notePageIndex })
    }

    @Test
    fun durableLinkTakesPrecedenceOverLegacyPageIndexAndRootUri() {
        val durable = StudySourceLink(
            uri = "content://docs/durable",
            docId = "doc-durable",
            sourcePage = 2,
        )
        val notebook = Notebook(
            sourceDocumentUri = sourceUri,
            pages = listOf(
                page(
                    id = "page-durable",
                    sourcePageIndex = 8,
                    sourceLink = durable,
                ),
            ),
        )

        val link = StudyNoteSourceLinks.fromNotebook(notebook).single()
        assertEquals(2, link.sourcePageIndex)
        assertEquals(durable, link.sourceLink)
    }

    @Test
    fun orphanLegacyLinksAreOmittedButOrphanDurableLinksRemainListed() {
        val durable = StudySourceLink(
            uri = "content://docs/durable",
            sourcePage = 4,
        )
        val notebook = Notebook(
            id = "note-orphans",
            pages = listOf(
                page(id = "legacy-orphan", sourcePageIndex = 1, text = "legacy"),
                page(id = "durable-orphan", sourceLink = durable, text = "durable"),
            ),
        )

        val links = StudyNoteSourceLinks.fromNotebook(notebook)
        assertEquals(listOf("durable-orphan"), links.map { it.pageId })
        assertEquals(4, links.single().sourcePageIndex)
        assertEquals(durable, links.single().sourceLink)
    }
}
