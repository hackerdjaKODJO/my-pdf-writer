package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM coverage for the pure validation policy used by
 * [PdfViewModel.validatePdfStudyCapture] and `addPdfStudyCapture`.
 *
 * The plain JVM test source set has no Android runtime or Robolectric, so these
 * tests exercise identity/page decisions and rejection messages through the
 * extracted helper. They do not instantiate PdfViewModel, verify StateFlow
 * message publication, persist excerpts, or exercise `flushAllSavesNow` IO.
 */
class PdfStudyCaptureValidationTest {

    private val currentSource = StudySource(
        docName = "current.pdf",
        docUri = "content://docs/current",
        docId = "doc-current",
        contentFingerprint = "fingerprint-current",
    )

    private fun capture(
        source: StudySource = currentSource,
        text: String = "  selected text  ",
        sourcePage: Int = 4,
    ) = PdfStudyCapture(
        text = text,
        sourcePage = sourcePage,
        rect = NormalizedRect(0.1f, 0.2f, 0.7f, 0.8f),
        excerptId = "existing-excerpt",
        source = source,
        rotation = 90,
    )

    private fun validate(
        state: ViewerUiState,
        value: PdfStudyCapture,
        source: StudySource? = currentSource,
        rejected: MutableList<String> = mutableListOf(),
    ): PdfStudyCapture? = validatePdfStudyCaptureAgainst(state, value, source) { rejected.add(it) }

    @Test
    fun matchingIdentityAndSourcePageRebindCaptureToCurrentSource() {
        val staleSource = currentSource.copy(docName = "old-name.pdf", docUri = "content://docs/old")
        val value = capture(source = staleSource)
        val state = ViewerUiState(
            docId = "doc-current",
            contentFingerprint = "fingerprint-current",
            pages = listOf(PageSpec(0), PageSpec(4, 180), PageSpec(null)),
            pageCount = 3,
        )
        val rejected = mutableListOf<String>()

        val validated = validate(state, value, rejected = rejected)!!

        assertEquals(value.copy(source = currentSource), validated)
        assertSame(currentSource, validated.source)
        assertTrue(rejected.isEmpty())
    }

    @Test
    fun wrongDocumentIsRejectedBeforeMissingPageAndReportsExistingMessage() {
        val state = ViewerUiState(pages = listOf(PageSpec(0)))
        val rejected = mutableListOf<String>()

        assertNull(
            validate(
                state,
                capture(source = currentSource.copy(docId = "doc-other"), sourcePage = 99),
                rejected = rejected,
            ),
        )

        assertEquals(listOf("다른 문서 또는 바뀐 PDF의 발췌는 추가할 수 없습니다."), rejected)
    }

    @Test
    fun changedFingerprintIsRejectedAndReportsExistingMessage() {
        val state = ViewerUiState(pages = listOf(PageSpec(4)))
        val rejected = mutableListOf<String>()

        assertNull(
            validate(
                state,
                capture(source = currentSource.copy(contentFingerprint = "fingerprint-replacement")),
                rejected = rejected,
            ),
        )

        assertEquals(listOf("다른 문서 또는 바뀐 PDF의 발췌는 추가할 수 없습니다."), rejected)
    }

    @Test
    fun sourcePageMustExistAfterPageEditsRatherThanMatchAViewerIndex() {
        // Source page 0 still exists, but source page 1 was deleted. Viewer index 1
        // now points at original page 4 and must not make a page-1 capture valid.
        val state = ViewerUiState(pages = listOf(PageSpec(0), PageSpec(4)), pageCount = 2)
        val rejected = mutableListOf<String>()

        assertNull(validate(state, capture(sourcePage = 1), rejected = rejected))

        assertEquals(listOf("원본 PDF에서 해당 페이지를 찾지 못했습니다."), rejected)
    }

    @Test
    fun blankTextNegativePageAndUnavailableSourceAreRejectedWithoutMisleadingSuccess() {
        val state = ViewerUiState(pages = listOf(PageSpec(4)))
        val rejected = mutableListOf<String>()

        assertNull(validate(state, capture(text = " \n\t "), rejected = rejected))
        assertNull(validate(state, capture(sourcePage = -1), rejected = rejected))
        assertNull(validate(state, capture(), source = null, rejected = rejected))

        assertTrue(rejected.isEmpty())
    }
}
