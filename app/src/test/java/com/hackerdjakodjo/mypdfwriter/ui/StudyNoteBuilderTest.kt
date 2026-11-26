package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNoteBuilderTest {

    @Test
    fun fromExcerptsCreatesOneTextPagePerExcerptWithSourceLink() {
        val excerpts = listOf(
            Excerpt(id = "excerpt-1", pageIndex = 2, text = "첫 번째 발췌문입니다.", title = "개념"),
            Excerpt(id = "excerpt-2", pageIndex = 7, text = "두 번째 발췌문입니다."),
        )

        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "세포 생물학",
            source = StudySource(
                docName = "biology.pdf",
                docUri = "content://docs/biology",
                docId = "doc-biology",
                contentFingerprint = "fingerprint-biology",
            ),
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
            val link = page.sourceLink!!
            assertEquals("content://docs/biology", link.uri)
            assertEquals("doc-biology", link.docId)
            assertEquals(excerpt.pageIndex, link.sourcePage)
            assertEquals("fingerprint-biology", link.fingerprint)
            assertEquals(excerpt.id, link.excerptId)
            assertEquals(PageOrientation.PORTRAIT, page.orientation)
            assertEquals(PaperTint.WARM, page.tint)
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
    fun excerptsWithoutAnySourceIdentityStillCreateNotePages() {
        val notebook = StudyNoteBuilder.fromExcerpts(
            title = "출처 없음",
            source = StudySource(""),
            excerpts = listOf(Excerpt(pageIndex = 1, text = "남겨둘 발췌")),
        )

        assertEquals(1, notebook.pageCount)
        assertEquals(1, notebook.pages.single().sourcePageIndex)
        assertNull(notebook.pages.single().sourceLink)
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
    fun blankStudyNoteStartsWithOneEditablePageLinkedToTheSource() {
        val notebook = StudyNoteBuilder.blankStudyNote(
            StudySource("lecture.pdf", " content://docs/lecture "),
        )

        assertEquals("lecture 스터디 노트", notebook.title)
        assertEquals("lecture.pdf", notebook.sourceDocumentName)
        assertEquals("content://docs/lecture", notebook.sourceDocumentUri)
        assertEquals(1, notebook.pageCount)
        assertEquals(PaperTemplate.LINED, notebook.pages.single().template)
        assertTrue(notebook.pages.single().annotations.isEmpty())
        assertNull(notebook.pages.single().sourcePageIndex)
        assertEquals("content://docs/lecture", notebook.pages.single().sourceLink?.uri)
        assertEquals(0, notebook.pages.single().sourceLink?.sourcePage)
    }

    @Test
    fun blankStudyNotePersistsDocumentIdentityForReplacementDetection() {
        val source = StudySource(
            docName = "lecture.pdf",
            docUri = "content://docs/lecture",
            docId = "doc-a",
            contentFingerprint = "fingerprint-a",
        )
        val link = StudyNoteBuilder.blankStudyNote(source).pages.single().sourceLink!!
        assertEquals("doc-a", link.docId)
        assertEquals("fingerprint-a", link.fingerprint)
    }

    @Test
    fun blankStudyNoteWithoutADocumentNameStillGetsAUsableTitle() {
        val notebook = StudyNoteBuilder.blankStudyNote(StudySource("  "))

        assertEquals("스터디 노트", notebook.title)
        assertEquals("", notebook.sourceDocumentName)
        assertEquals(1, notebook.pageCount)
    }

    @Test
    fun captureLookupRejectsLegacyUriOnlyNotebookWhenFingerprintIsKnown() {
        val source = StudySource(
            docName = "lecture.pdf",
            docUri = "content://docs/lecture",
            docId = "doc-a",
            contentFingerprint = "fingerprint-a",
        )
        val legacy = Notebook(
            id = "legacy",
            sourceDocumentName = "lecture.pdf",
            sourceDocumentUri = "content://docs/lecture",
        )
        val bound = legacy.copy(
            id = "bound",
            pages = listOf(
                NotePage(
                    sourceLink = StudySourceLink(
                        uri = "content://docs/lecture",
                        docId = "doc-a",
                        sourcePage = 0,
                        fingerprint = "fingerprint-a",
                    ),
                ),
            ),
        )

        assertNull(findStudyNotebookForCapture(listOf(legacy), source))
        assertEquals("bound", findStudyNotebookForCapture(listOf(legacy, bound), source)?.id)
    }

    @Test
    fun studyNoteLookupPrefersSourceUriAndIgnoresTrashedNotebooks() {
        val source = StudySource("lecture.pdf", "content://docs/lecture")
        val byTitle = Notebook(
            id = "title",
            title = "lecture 스터디 노트",
        )
        val byName = Notebook(
            id = "name",
            sourceDocumentName = "lecture.pdf",
        )
        val byUri = Notebook(
            id = "uri",
            sourceDocumentUri = "content://docs/lecture",
        )
        val trashed = byUri.copy(id = "trashed", trashed = true)

        assertEquals(
            "uri",
            StudyNoteBuilder.findStudyNote(listOf(byTitle, byName, byUri, trashed), source)?.id,
        )
        assertEquals(
            "name",
            StudyNoteBuilder.findStudyNote(listOf(byName, byTitle), source)?.id,
        )
        assertEquals(
            "title",
            StudyNoteBuilder.findStudyNote(listOf(byTitle), source)?.id,
        )
        assertNull(StudyNoteBuilder.findStudyNote(listOf(trashed), source))
        assertNull(StudyNoteBuilder.findStudyNote(emptyList(), source))
    }

    @Test
    fun studyNoteLookupWithoutDocumentNameUsesTheUriOnly() {
        val source = StudySource("   ", "content://docs/lecture")
        val byUri = Notebook(id = "uri", sourceDocumentUri = "content://docs/lecture")
        val byTitle = Notebook(id = "title", title = "스터디 노트")

        assertEquals("uri", StudyNoteBuilder.findStudyNote(listOf(byTitle, byUri), source)?.id)
        assertNull(StudyNoteBuilder.findStudyNote(listOf(byTitle), source))
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

    @Test
    fun captureBuildersRetainRectExcerptIdIdentityAndNormalizedRotation() {
        val source = StudySource(
            docName = "lecture.pdf",
            docUri = "content://docs/lecture",
            docId = "doc-lecture",
            contentFingerprint = "fingerprint-lecture",
        )
        val rect = NormalizedRect(0.15f, 0.25f, 0.65f, 0.75f)
        val capture = StudyCapture(
            text = "선택한 발췌",
            source = source,
            sourcePageIndex = 3,
            rect = rect,
            excerptId = "excerpt-selected",
            rotation = 450,
        )

        val notebook = StudyNoteBuilder.fromCapture(title = "강의 노트", capture = capture)
        val link = notebook.pages.single().sourceLink!!

        assertEquals(source.docUri, link.uri)
        assertEquals(source.docId, link.docId)
        assertEquals(source.contentFingerprint, link.fingerprint)
        assertEquals(3, link.sourcePage)
        assertEquals(rect, link.rect)
        assertEquals("excerpt-selected", link.excerptId)
        assertEquals(90, link.rotation)
    }

    @Test
    fun captureBuilderDropsInvalidRectButKeepsTheDocumentLink() {
        val notebook = StudyNoteBuilder.fromCapture(
            title = "강의 노트",
            capture = StudyCapture(
                text = "발췌",
                source = StudySource("lecture.pdf", "content://docs/lecture"),
                sourcePageIndex = 1,
                rect = NormalizedRect(0.9f, 0f, 0.1f, 1f),
            ),
        )

        val link = notebook.pages.single().sourceLink!!
        assertEquals(1, link.sourcePage)
        assertNull(link.rect)
    }

    @Test
    fun appendRejectsSameNameDifferentUriAndChangedFingerprint() {
        val source = StudySource(
            docName = "biology.pdf",
            docUri = "content://docs/biology-a",
            docId = "doc-a",
            contentFingerprint = "fingerprint-a",
        )
        val base = StudyNoteBuilder.fromExcerpts(
            title = "생물학",
            source = source,
            excerpts = listOf(Excerpt(id = "first", pageIndex = 0, text = "첫 발췌")),
        ).copy(updatedAt = 1L)

        val wrongUri = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source.copy(docUri = "content://docs/biology-b", docId = "doc-b"),
            excerpt = Excerpt(pageIndex = 1, text = "다른 PDF"),
        )
        val changedSource = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source.copy(contentFingerprint = "fingerprint-replacement"),
            excerpt = Excerpt(pageIndex = 1, text = "교체된 PDF"),
        )

        assertEquals(base, wrongUri)
        assertEquals(base, changedSource)
    }

    @Test
    fun appendRetainsExcerptIdAndAllowsExactUriWhenDisplayNameChanges() {
        val source = StudySource("old-name.pdf", "content://docs/biology", docId = "doc-a")
        val base = StudyNoteBuilder.fromExcerpts(
            title = "생물학",
            source = source,
            excerpts = listOf(Excerpt(pageIndex = 0, text = "첫 발췌")),
        )

        val appended = StudyNoteBuilder.appendExcerpt(
            notebook = base,
            source = source.copy(docName = "renamed.pdf"),
            excerpt = Excerpt(id = "excerpt-appended", pageIndex = 2, text = "둘째 발췌"),
        )

        assertEquals(2, appended.pageCount)
        assertEquals("excerpt-appended", appended.pages[1].sourceLink?.excerptId)
        assertEquals(source.docId, appended.pages[1].sourceLink?.docId)
        assertEquals("old-name.pdf", appended.sourceDocumentName)
    }

    @Test
    fun legacyNameFallbackIsBoundedByConflictingExplicitLinks() {
        val source = StudySource("lecture.pdf", "content://docs/lecture")
        val legacy = Notebook(
            title = "lecture 스터디 노트",
            sourceDocumentName = "lecture.pdf",
            pages = listOf(NotePage(id = "legacy-page", memo = "옛 노트")),
        )
        val bound = StudyNoteBuilder.appendExcerpt(
            notebook = legacy,
            source = source,
            excerpt = Excerpt(pageIndex = 1, text = "새 발췌"),
        )
        assertEquals("content://docs/lecture", bound.sourceDocumentUri)

        val conflicting = legacy.copy(
            pages = listOf(
                NotePage(
                    id = "explicit-page",
                    sourceLink = StudySourceLink(
                        uri = "content://docs/another-lecture",
                        docId = "doc-other",
                        sourcePage = 1,
                    ),
                ),
            ),
        )
        assertEquals(
            conflicting,
            StudyNoteBuilder.appendExcerpt(
                notebook = conflicting,
                source = source,
                excerpt = Excerpt(pageIndex = 2, text = "거부되어야 함"),
            ),
        )
    }

    @Test
    fun sourcePagePrefersTheDurableLinkOverLegacyViewerData() {
        val notebook = Notebook(
            sourceDocumentUri = "content://docs/legacy",
            pages = listOf(
                NotePage(
                    sourcePageIndex = 8,
                    sourceLink = StudySourceLink(
                        uri = "content://docs/current",
                        sourcePage = 2,
                    ),
                ),
            ),
        )

        assertEquals(2, StudyNoteBuilder.sourcePage(notebook, 0))
    }

    @Test
    fun studyNoteLookupNeverBridgesDifferentNonblankUrisByName() {
        val source = StudySource("lecture.pdf", "content://docs/lecture-a")
        val wrongUri = Notebook(
            id = "wrong",
            title = "lecture 스터디 노트",
            sourceDocumentName = "lecture.pdf",
            sourceDocumentUri = "content://docs/lecture-b",
        )
        val exactUri = wrongUri.copy(id = "exact", sourceDocumentUri = source.docUri.orEmpty())

        assertEquals(
            "exact",
            StudyNoteBuilder.findStudyNote(listOf(wrongUri, exactUri), source)?.id,
        )
        assertNull(StudyNoteBuilder.findStudyNote(listOf(wrongUri), source))
    }

    @Test
    fun captureBuilderRejectsMixedDocumentIdentities() {
        val source = StudySource(
            docName = "lecture.pdf",
            docUri = "content://docs/lecture",
            docId = "doc-a",
            contentFingerprint = "fingerprint-a",
        )
        val notebook = StudyNoteBuilder.fromCaptures(
            title = "강의 노트",
            source = source,
            captures = listOf(
                StudyCapture("맞는 발췌", source, sourcePageIndex = 1),
                StudyCapture(
                    "다른 문서",
                    source.copy(docId = "doc-b"),
                    sourcePageIndex = 2,
                ),
            ),
        )

        assertEquals(1, notebook.pageCount)
        assertEquals("doc-a", notebook.pages.single().sourceLink?.docId)
    }

    @Test
    fun emptyCaptureSelectionKeepsTheProvidedSourceMetadata() {
        val source = StudySource("lecture.pdf", "content://docs/lecture")
        val notebook = StudyNoteBuilder.fromCaptures(
            title = "빈 노트",
            source = source,
            captures = emptyList(),
        )

        assertEquals("lecture.pdf", notebook.sourceDocumentName)
        assertEquals("content://docs/lecture", notebook.sourceDocumentUri)
        assertNull(notebook.pages.single().sourceLink)
    }
}
