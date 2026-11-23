package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure rules behind the UX-consistency / quality pass. */
class QualityPolicyTest {

    @Test
    fun backClearsSelectionBeforeLeavingTheNoteEditor() {
        assertEquals(NoteBackStep.CLEAR_SELECTION, noteBackStep(true))
        assertEquals(NoteBackStep.LEAVE_EDITOR, noteBackStep(false))
    }

    @Test
    fun maskingCardStatesHaveDistinctSpokenLabels() {
        val labels = MaskingCardState.entries.map(::maskingStateLabel)
        assertEquals(MaskingCardState.entries.size, labels.toSet().size)
        assertEquals(
            listOf("가려짐", "정답 확인", "정답", "오답"),
            labels,
        )
    }

    @Test
    fun bookmarkToggleUsesOneCanonicalLabelEverywhere() {
        assertEquals("현재 페이지 북마크", BOOKMARK_TOGGLE_LABEL)
        assertEquals("현재 페이지 북마크 해제", BOOKMARK_TOGGLE_REMOVE_LABEL)
        assertTrue(BOOKMARK_TOGGLE_REMOVE_LABEL.startsWith(BOOKMARK_TOGGLE_LABEL))
    }

    @Test
    fun everyPdfImportActionIsRecognised() {
        assertTrue(pdfImportActions.contains("android.intent.action.VIEW"))
        assertTrue(pdfImportActions.contains("android.intent.action.SEND"))
        assertTrue("multiple shared PDFs must not be silently ignored",
            pdfImportActions.contains("android.intent.action.SEND_MULTIPLE"))
        assertFalse(pdfImportActions.contains("android.intent.action.MAIN"))
        assertTrue(isMultiImport(5))
        assertFalse(isMultiImport(1))
        assertEquals("여러 PDF는 한 번에 한 개씩 열 수 있습니다.", MULTI_IMPORT_NOTICE)
    }

    @Test
    fun failedOpenDoesNotCloseAnAlreadyOpenDocument() {
        assertTrue(keepViewerOnFailedOpen("doc-1", "content://a.pdf"))
        assertTrue(keepViewerOnFailedOpen("doc-1", null))
        assertTrue(keepViewerOnFailedOpen(null, "content://a.pdf"))
        assertFalse(keepViewerOnFailedOpen(null, null))
    }
}
