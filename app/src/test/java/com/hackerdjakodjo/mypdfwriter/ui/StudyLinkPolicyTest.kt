package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.StudySource
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLinkPolicyTest {

    private fun link(
        uri: String = "content://docs/a",
        docId: String? = "doc-a",
        sourcePage: Int = 2,
        fingerprint: String? = "fingerprint-a",
        rect: NormalizedRect? = null,
    ): StudySourceLink = StudySourceLink(
        uri = uri,
        docId = docId,
        sourcePage = sourcePage,
        fingerprint = fingerprint,
        rect = rect,
    )

    @Test
    fun wrongDocumentWinsOverChangedFingerprintAndMissingPage() {
        assertEquals(
            StudyLinkResolution.WrongDocument,
            resolveStudyLink(
                link = link(),
                currentDocId = "doc-b",
                currentFingerprint = "fingerprint-b",
                pages = emptyList(),
            ),
        )
    }

    @Test
    fun matchingIdentityResolvesSourceOrdinalAndReturnsRect() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.7f, 0.8f)
        assertEquals(
            StudyLinkResolution.Found(viewPageIndex = 1, rect = rect),
            resolveStudyLink(
                link = link(rect = rect),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-a",
                pages = listOf(PageSpec(0), PageSpec(2), PageSpec(3)),
            ),
        )
    }

    @Test
    fun legacyLinkWithoutDocIdStillResolvesAgainstCurrentPages() {
        assertEquals(
            StudyLinkResolution.Found(viewPageIndex = 0, rect = null),
            resolveStudyLink(
                link = link(docId = null, fingerprint = null),
                currentDocId = "doc-current",
                currentFingerprint = "fingerprint-current",
                pages = listOf(PageSpec(2)),
            ),
        )
    }

    @Test
    fun changedFingerprintIsExplicitAndBlankFingerprintsAreUnknown() {
        assertEquals(
            StudyLinkResolution.SourceChanged,
            resolveStudyLink(
                link = link(),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-replacement",
                pages = listOf(PageSpec(2)),
            ),
        )
        assertEquals(
            StudyLinkResolution.SourceChanged,
            resolveStudyLink(
                link = link(),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-replacement",
                pages = emptyList(),
            ),
        )
        assertEquals(
            StudyLinkResolution.Found(viewPageIndex = 0, rect = null),
            resolveStudyLink(
                link = link(fingerprint = "  "),
                currentDocId = "doc-a",
                currentFingerprint = null,
                pages = listOf(PageSpec(2)),
            ),
        )
    }

    @Test
    fun unavailableSourceIsDistinctFromWrongDocumentAndMissingPage() {
        assertEquals(
            StudyLinkResolution.SourceUnavailable,
            resolveStudyLink(
                link = link(),
                currentDocId = null,
                currentFingerprint = null,
                pages = listOf(PageSpec(2)),
            ),
        )
        assertEquals(
            StudyLinkResolution.SourceUnavailable,
            resolveStudyLink(
                link = link(uri = "  "),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-a",
                pages = listOf(PageSpec(2)),
            ),
        )
    }

    @Test
    fun resolutionFollowsSourcePageThroughInsertDeleteMoveRotateAndReset() {
        val sourceLink = link(sourcePage = 2)

        assertEquals(
            StudyLinkResolution.Found(1, null),
            resolveStudyLink(
                sourceLink,
                "doc-a",
                "fingerprint-a",
                listOf(PageSpec(0), PageSpec(1), PageSpec(2), PageSpec(3)),
            ),
        )
        assertEquals(
            StudyLinkResolution.Found(3, null),
            resolveStudyLink(
                sourceLink,
                "doc-a",
                "fingerprint-a",
                listOf(PageSpec(null), PageSpec(0), PageSpec(1), PageSpec(2), PageSpec(3)),
            ),
        )
        assertEquals(
            StudyLinkResolution.Found(2, null),
            resolveStudyLink(
                sourceLink,
                "doc-a",
                "fingerprint-a",
                listOf(PageSpec(null), PageSpec(0), PageSpec(2), PageSpec(3)),
            ),
        )
        assertEquals(
            StudyLinkResolution.Found(0, null),
            resolveStudyLink(
                sourceLink,
                "doc-a",
                "fingerprint-a",
                listOf(PageSpec(2, 270), PageSpec(null), PageSpec(0), PageSpec(3)),
            ),
        )
        assertEquals(
            StudyLinkResolution.Found(2, null),
            resolveStudyLink(
                sourceLink,
                "doc-a",
                "fingerprint-a",
                listOf(PageSpec(0), PageSpec(1), PageSpec(2, 0), PageSpec(3)),
            ),
        )
    }

    @Test
    fun missingSourcePageNeverClampsToTheLastViewerPage() {
        assertEquals(
            StudyLinkResolution.PageMissing,
            resolveStudyLink(
                link = link(sourcePage = 9),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-a",
                pages = listOf(PageSpec(0), PageSpec(1)),
            ),
        )
        assertEquals(
            StudyLinkResolution.PageMissing,
            resolveStudyLink(
                link = link(sourcePage = 9),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-a",
                pages = emptyList(),
            ),
        )
    }

    @Test
    fun duplicateSourcePagesChooseTheFirstDeterministicallyAndReportAmbiguity() {
        assertEquals(
            StudyLinkResolution.Found(viewPageIndex = 1, rect = null, ambiguous = true),
            resolveStudyLink(
                link = link(),
                currentDocId = "doc-a",
                currentFingerprint = "fingerprint-a",
                pages = listOf(
                    PageSpec(0),
                    PageSpec(2, 90),
                    PageSpec(null),
                    PageSpec(2, 270),
                ),
            ),
        )
    }

    @Test
    fun sameNameDifferentUriNeverMatchesAndExactUriDoes() {
        val notebook = Notebook(
            sourceDocumentName = "lecture.pdf",
            sourceDocumentUri = "content://docs/lecture-a",
        )

        assertFalse(
            studySourcesMatch(
                notebook,
                StudySource("lecture.pdf", "content://docs/lecture-b"),
            ),
        )
        assertTrue(
            studySourcesMatch(
                notebook,
                StudySource("renamed.pdf", "content://docs/lecture-a"),
            ),
        )
        assertFalse(
            studySourcesMatch(
                notebook,
                StudySource("lecture.pdf", "CONTENT://docs/lecture-a"),
            ),
        )
    }

    @Test
    fun exactUriRemainsCompatibleWhenOptionalIdentityIsAbsent() {
        val notebook = Notebook(
            sourceDocumentUri = "content://docs/lecture",
            pages = listOf(
                NotePage(
                    sourceLink = link(
                        uri = "content://docs/lecture",
                        docId = "doc-lecture",
                        fingerprint = "fingerprint-lecture",
                    ),
                ),
            ),
        )

        assertTrue(studySourcesMatch(notebook, StudySource("lecture.pdf", notebook.sourceDocumentUri)))
        assertFalse(
            studySourcesMatch(
                notebook,
                StudySource(
                    "lecture.pdf",
                    notebook.sourceDocumentUri,
                    docId = "different-doc",
                ),
            ),
        )
        assertFalse(
            studySourcesMatch(
                notebook,
                StudySource(
                    "lecture.pdf",
                    notebook.sourceDocumentUri,
                    contentFingerprint = "different-fingerprint",
                ),
            ),
        )
    }

    @Test
    fun legacyBlankUriNameAndTitleFallbackIsAllowedOnlyWithoutKnownUriIdentity() {
        val legacy = Notebook(
            title = "lecture 스터디 노트",
            sourceDocumentName = "lecture.pdf",
        )
        val source = StudySource("lecture.pdf", "content://docs/lecture")

        assertTrue(studySourcesMatch(legacy, source))
        assertTrue(studySourcesMatch(legacy, StudySource("lecture.pdf")))
        assertTrue(
            studySourcesMatch(
                legacy.copy(sourceDocumentName = "old-name.pdf"),
                source,
            ),
        )
        assertFalse(studySourcesMatch(legacy, StudySource("different.pdf")))
        assertFalse(
            studySourcesMatch(
                legacy.copy(sourceDocumentUri = "content://docs/lecture"),
                StudySource("lecture.pdf"),
            ),
        )
    }

    @Test
    fun blankUriFallbackRejectsConflictingOrUnverifiableExplicitLinks() {
        val source = StudySource("lecture.pdf", "content://docs/lecture")
        val legacy = Notebook(
            title = "lecture 스터디 노트",
            sourceDocumentName = "lecture.pdf",
        )
        val conflicting = legacy.copy(
            pages = listOf(
                NotePage(
                    sourceLink = link(
                        uri = "content://docs/other",
                        docId = "doc-other",
                        fingerprint = "fingerprint-other",
                    ),
                ),
            ),
        )
        val matching = legacy.copy(
            pages = listOf(
                NotePage(
                    sourceLink = link(
                        uri = source.docUri!!,
                        docId = "doc-lecture",
                        fingerprint = "fingerprint-lecture",
                    ),
                ),
            ),
        )
        val idOnly = legacy.copy(
            pages = listOf(NotePage(sourceLink = link(uri = source.docUri!!, docId = "doc-lecture"))),
        )

        assertFalse(studySourcesMatch(conflicting, source))
        assertTrue(studySourcesMatch(matching, source))
        assertFalse(studySourcesMatch(idOnly, StudySource("lecture.pdf")))
        assertTrue(
            studySourcesMatch(
                idOnly,
                StudySource("renamed.pdf", docId = "doc-lecture"),
            ),
        )
    }

    @Test
    fun mixedExplicitDocumentIdentitiesAreNeverMergedByName() {
        val notebook = Notebook(
            title = "lecture 스터디 노트",
            sourceDocumentName = "lecture.pdf",
            pages = listOf(
                NotePage(sourceLink = link(uri = "content://docs/a", docId = "doc-a")),
                NotePage(sourceLink = link(uri = "content://docs/b", docId = "doc-b")),
            ),
        )

        assertFalse(
            studySourcesMatch(
                notebook,
                StudySource("lecture.pdf", "content://docs/a", docId = "doc-a"),
            ),
        )
    }
}
