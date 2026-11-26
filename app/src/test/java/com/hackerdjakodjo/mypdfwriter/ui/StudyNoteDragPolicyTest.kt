package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNoteDragPolicyTest {
    @Test
    fun completeCaptureRoundTripsThroughLabelAndItemText() {
        val capture = PdfStudyCapture(
            text = "selected text",
            sourcePage = 7,
            rect = NormalizedRect(left = 0.125f, top = 0.25f, right = 0.875f, bottom = 0.75f),
            excerptId = "excerpt|%7C",
            source = StudySource(
                docName = "",
                docUri = null,
                docId = "doc|%25",
                contentFingerprint = "fingerprint|%",
            ),
            rotation = 270,
        )

        val label = studyDragLabel(capture)
        assertFalse(label.contains(capture.text))
        assertEquals(capture, parseStudyDragLabel(label, "  ${capture.text}\n"))
    }

    @Test
    fun labelEscapesSeparatorPercentAndEscapeLookalikesWithoutCollision() {
        val capture = capture(
            docId = "doc|with%separator%7C%25%GG%",
            fingerprint = "fp|%7c%25",
            excerptId = "excerpt|%25|%",
        )

        val label = studyDragLabel(capture)
        assertEquals(8, label.substringAfter(':').count { it == '|' })
        assertEquals(capture, parseStudyDragLabel(label, capture.text))
    }

    @Test
    fun blankOptionalIdentityAndExcerptRemainNull() {
        val capture = capture(docId = "", fingerprint = "   ", excerptId = "")
        val parsed = requireNotNull(parseStudyDragLabel(studyDragLabel(capture), capture.text))

        assertNull(parsed?.source?.docId)
        assertNull(parsed?.source?.contentFingerprint)
        assertNull(parsed?.excerptId)
    }

    @Test
    fun missingRectangleRoundTripsAsNull() {
        val capture = capture(rect = null)
        assertEquals(capture, parseStudyDragLabel(studyDragLabel(capture), capture.text))
    }

    @Test
    fun malformedLabelsAreRejected() {
        assertNull(parseStudyDragLabel(null, "text"))
        assertNull(parseStudyDragLabel("another-app:2|doc-a|fp-a||||90|excerpt-a", "text"))
        assertNull(parseStudyDragLabel("mypdfwriter-study-page:2|doc-a|fp-a", "text"))
        assertNull(parseStudyDragLabel("mypdfwriter-study-capture:v2:2|doc-a", "text"))
        assertNull(
            parseStudyDragLabel(
                label(page = "2", fingerprint = "fp-a", excerptId = "extra|field"),
                "text",
            ),
        )
    }

    @Test
    fun malformedEscapedFieldsAreRejected() {
        for (badEscape in listOf("%", "%2", "%GG", "%7")) {
            assertNull(parseStudyDragLabel(label(docId = "doc$badEscape"), "text"))
            assertNull(parseStudyDragLabel(label(fingerprint = "fp$badEscape"), "text"))
            assertNull(parseStudyDragLabel(label(excerptId = "excerpt$badEscape"), "text"))
        }
    }

    @Test
    fun negativeOrMalformedPageAndRotationAreRejected() {
        for (badNumber in listOf("-1", "+1", "", "page", "1.5", "2147483648")) {
            assertNull(parseStudyDragLabel(label(page = badNumber), "text"))
            assertNull(parseStudyDragLabel(label(rotation = badNumber), "text"))
        }
        assertNull(
            parseStudyDragLabel(
                studyDragLabel(capture(sourcePage = -1, rotation = -90)),
                "text",
            ),
        )
    }

    @Test
    fun blankTextIsRejected() {
        val label = studyDragLabel(capture())
        assertNull(parseStudyDragLabel(label, null))
        assertNull(parseStudyDragLabel(label, ""))
        assertNull(parseStudyDragLabel(label, " \n\t "))
    }

    @Test
    fun partialOrMalformedRectangleIsRejected() {
        assertNull(parseStudyDragLabel(label(left = "0.1"), "text"))
        assertNull(parseStudyDragLabel(label(top = "0.1"), "text"))
        assertNull(parseStudyDragLabel(label(right = "0.9"), "text"))
        assertNull(parseStudyDragLabel(label(bottom = "0.9"), "text"))
        for (badCoordinate in listOf("rect", "1,5")) {
            assertNull(parseStudyDragLabel(label(left = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(top = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(right = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(bottom = badCoordinate), "text"))
        }
    }

    @Test
    fun nonFiniteOutOfRangeOrUnorderedRectangleIsRejected() {
        val badCoordinates = listOf("NaN", "Infinity", "-Infinity", "1e999", "-0.01", "1.01")
        for (badCoordinate in badCoordinates) {
            assertNull(parseStudyDragLabel(label(left = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(top = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(right = badCoordinate), "text"))
            assertNull(parseStudyDragLabel(label(bottom = badCoordinate), "text"))
        }
        assertNull(
            parseStudyDragLabel(label(left = "0.9", right = "0.1", bottom = "1"), "text"),
        )
        assertNull(
            parseStudyDragLabel(label(top = "0.9", bottom = "0.1", right = "1"), "text"),
        )
    }

    @Test
    fun normalizedRectangleBoundariesAreAccepted() {
        val rectangles = listOf(
            NormalizedRect(0f, 0f, 0f, 0f),
            NormalizedRect(0f, 0f, 1f, 1f),
            NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f),
        )
        for (rect in rectangles) {
            val capture = capture(rect = rect)
            assertEquals(capture, parseStudyDragLabel(studyDragLabel(capture), capture.text))
        }
    }

    @Test
    fun payloadMustMatchKnownDocumentAndFingerprint() {
        val payload = StudyDragPayload(capture(docId = "doc-a", fingerprint = "fp-a"))
        assertTrue(studyDragPayloadMatches(payload, "doc-a", "fp-a"))
        assertFalse(studyDragPayloadMatches(payload, "doc-b", "fp-a"))
        assertFalse(studyDragPayloadMatches(payload, "doc-a", "fp-b"))
        assertFalse(studyDragPayloadMatches(payload, " doc-a ", "fp-a"))
        assertFalse(
            studyDragPayloadMatches(
                StudyDragPayload(capture(docId = null, fingerprint = "fp-a")),
                "doc-a",
                null,
            ),
        )
    }

    @Test
    fun blankExpectedIdentityDoesNotRejectUnboundPayload() {
        val payload = StudyDragPayload(capture(docId = null, fingerprint = null))
        assertTrue(studyDragPayloadMatches(payload, "", "   "))
        assertTrue(studyDragPayloadMatches(payload, null, null))
    }

    @Test
    fun dropReturnsTrueOnlyWhenPersistenceCallbackSucceeds() {
        val capture = capture()
        val payload = StudyDragPayload(capture)
        var delivered: PdfStudyCapture? = null

        assertTrue(
            dispatchStudyNoteDrop(
                payload = payload,
                onDrop = {
                    delivered = it
                    true
                },
                expectedDocId = "doc-a",
                expectedFingerprint = "fp-a",
            ),
        )
        assertEquals(capture, delivered)
        assertFalse(
            dispatchStudyNoteDrop(
                payload = payload,
                onDrop = { false },
                expectedDocId = "doc-a",
                expectedFingerprint = "fp-a",
            ),
        )
    }

    @Test
    fun rejectedDropNeverInvokesPersistenceCallback() {
        var calls = 0
        val payload = StudyDragPayload(capture())

        assertFalse(dispatchStudyNoteDrop(null, onDrop = { calls++; true }))
        assertFalse(
            dispatchStudyNoteDrop(
                payload = payload,
                onDrop = { calls++; true },
                expectedDocId = "doc-b",
                expectedFingerprint = "fp-a",
            ),
        )
        assertFalse(
            dispatchStudyNoteDrop(
                payload = payload,
                onDrop = { calls++; true },
                expectedDocId = "doc-a",
                expectedFingerprint = "fp-b",
            ),
        )
        assertEquals(0, calls)
    }

    private fun capture(
        docId: String? = "doc-a",
        fingerprint: String? = "fp-a",
        excerptId: String? = "excerpt-a",
        rect: NormalizedRect? = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f),
        sourcePage: Int = 2,
        rotation: Int = 90,
    ): PdfStudyCapture = PdfStudyCapture(
        text = "text",
        sourcePage = sourcePage,
        rect = rect,
        excerptId = excerptId,
        source = StudySource(
            docName = "",
            docUri = null,
            docId = docId,
            contentFingerprint = fingerprint,
        ),
        rotation = rotation,
    )

    private fun label(
        page: String = "2",
        docId: String = "doc-a",
        fingerprint: String = "fp-a",
        left: String = "",
        top: String = "",
        right: String = "",
        bottom: String = "",
        rotation: String = "90",
        excerptId: String = "excerpt-a",
    ): String = "mypdfwriter-study-capture:v2:" + listOf(
        page,
        docId,
        fingerprint,
        left,
        top,
        right,
        bottom,
        rotation,
        excerptId,
    ).joinToString("|")
}
