package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNoteBuilderTest {

    @Test
    fun fromExcerptsCreatesOneTextPagePerExcerptWithSourceLink() {
        val excerpts = listOf(
            Excerpt(pageIndex = 2, text = "첫 번째 발췌문입니다.", title = "개념"),
            Excerpt(pageIndex = 7, text = "두 번째 발췌문입니다."),
        )

        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "세포 생물학",
            source = StudySource("biology.pdf", "content://docs/biology"),
            excerpts = excerpts,
        )

        assertEquals("세포 생물학", notebook.title)
        assertEquals("biology.pdf", notebook.sourceDocumentName)
        assertEquals("content://docs/biology", notebook.sourceDocumentUri)
        assertEquals(2, notebook.pageCount)

        notebook.pages.forEachIndexed { index, page ->
            val excerpt = excerpts[index]
            val annotation = page.annotations.single()
            assertEquals(AnnotationType.TEXT, annotation.type)
            assertEquals(excerpt.text, annotation.text)
            assertTrue(annotation.fontSize > 0f)
            assertEquals(1, annotation.points.size)
            assertTrue(annotation.points[0].x in 0f..1f)
            assertTrue(annotation.points[0].y in 0f..1f)
            assertEquals("원본 PDF p.${excerpt.pageIndex + 1}", page.memo)
            assertEquals(excerpt.pageIndex, page.sourcePageIndex)
            assertEquals(excerpt.pageIndex, StudyNoteBuilder.sourcePage(notebook, index))
            assertEquals(1, page.layers.size)
            assertEquals(page.layers.single().id, annotation.layerId)
        }
    }

    @Test
    fun blankExcerptsAreSkippedAndBlankTitleFallsBackToDocumentName() {
        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "   ",
            source = StudySource("lecture.pdf"),
            excerpts = listOf(
                Excerpt(pageIndex = 0, text = "  "),
                Excerpt(pageIndex = 1, text = "남길 발췌"),
            ),
        )

        assertEquals("lecture 스터디 노트", notebook.title)
        assertEquals(1, notebook.pageCount)
        assertEquals("남길 발췌", notebook.pages.single().annotations.single().text)
        assertEquals(1, notebook.pages.single().sourcePageIndex)
    }

    @Test
    fun longKoreanExcerptWrapsIntoReadableLines() {
        val body = "생물학은 살아 있는 생명체의 구조와 기능을 연구하는 자연과학 분야입니다. ".repeat(40)
        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "긴 발췌",
            source = StudySource("long.pdf"),
            excerpts = listOf(Excerpt(pageIndex = 3, text = body)),
        )

        val annotation = notebook.pages.single().annotations.single()
        val lines = annotation.text.lines()
        assertTrue(lines.size > 10)
        assertTrue(annotation.fontSize >= 0.012f)
        assertTrue(annotation.fontSize <= 0.019f)
        assertTrue(lines.all { it.length <= 60 })
    }

    @Test
    fun sourcePageReturnsNullForPlainAndOutOfRangePages() {
        val notebook = Notebook(
            pages = listOf(
                NotePage(),
                NotePage(sourcePageIndex = 4),
            ),
        )

        assertNull(StudyNoteBuilder.sourcePage(notebook, 0))
        assertEquals(4, StudyNoteBuilder.sourcePage(notebook, 1))
        assertNull(StudyNoteBuilder.sourcePage(notebook, 2))
        assertNull(StudyNoteBuilder.sourcePage(notebook, -1))
    }

    @Test
    fun emptySelectionStillProducesAValidNotebook() {
        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "빈 노트",
            source = StudySource(""),
            excerpts = emptyList(),
        )

        assertEquals(1, notebook.pageCount)
        assertNull(notebook.pages.single().sourcePageIndex)
        assertEquals("", notebook.sourceDocumentName)
    }

    @Test
    fun appendExcerptAddsOnePageWithSameLayoutAndSourceLink() {
        val source = StudySource("biology.pdf", "content://docs/biology")
        val base = StudyNoteBuilder.fromExcerpts(
            title = "세포 생물학",
            source = source,
            excerpts = listOf(Excerpt(pageIndex = 0, text = "첫 번째 발췌")),
        ).copy(updatedAt = 1L)

        val appended = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source,
            excerpt = Excerpt(pageIndex = 4, text = "두 번째 발췌"),
            coverColor = 0xFF123456.toInt(),
        )

        assertEquals(2, appended.pageCount)
        assertEquals(base.pages[0], appended.pages[0])
        assertEquals("원본 PDF p.5", appended.pages[1].memo)
        assertEquals(4, appended.pages[1].sourcePageIndex)
        assertEquals(4, StudyNoteBuilder.sourcePage(appended, 1))
        val annotation = appended.pages[1].annotations.single()
        assertEquals(AnnotationType.TEXT, annotation.type)
        assertEquals("두 번째 발췌", annotation.text)
        assertTrue(annotation.fontSize in 0.012f..0.019f)
        assertEquals(appended.pages[1].layers.single().id, annotation.layerId)
        assertEquals(0xFF123456.toInt(), appended.coverColor)
        assertTrue(appended.updatedAt > 1L)
        assertEquals("biology.pdf", appended.sourceDocumentName)
        assertEquals("content://docs/biology", appended.sourceDocumentUri)
    }

    @Test
    fun appendExcerptIgnoresBlankTextAndKeepsExistingCoverByDefault() {
        val source = StudySource("biology.pdf")
        val base = StudyNoteBuilder.fromExcerpts(
            title = "세포 생물학",
            source = source,
            excerpts = listOf(Excerpt(pageIndex = 0, text = "본문")),
        )

        val unchanged = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source,
            excerpt = Excerpt(pageIndex = 2, text = "   "),
        )
        val appended = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source,
            excerpt = Excerpt(pageIndex = 2, text = "추가"),
        )

        assertEquals(base, unchanged)
        assertEquals(base.coverColor, appended.coverColor)
        assertEquals(2, appended.pageCount)
    }
}
