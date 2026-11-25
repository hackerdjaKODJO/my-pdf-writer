package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyTest {
    @Test
    fun `document menu and page terms are canonical`() {
        assertEquals("PDF 메뉴", UiCopy.PDF_MENU)
        assertEquals("최근 PDF 메뉴", UiCopy.RECENT_PDF_MENU)
        assertEquals("페이지 찾기", UiCopy.PAGE_FIND)
        assertEquals("페이지 편집", UiCopy.PAGE_EDIT)
        assertEquals("필기 다시보기", UiCopy.REPLAY)
        assertFalse(UiCopy.PDF_MENU.contains("학습"))
        assertFalse(UiCopy.RECENT_PDF_MENU.contains("학습"))
    }

    @Test
    fun `note choices keep distinct canonical labels and descriptions`() {
        assertEquals("학습 노트", UiCopy.STUDY_NOTE)
        assertEquals("PDF와 연결된 노트 패널", UiCopy.STUDY_NOTE_DESCRIPTION)
        assertEquals("페이지 메모", UiCopy.PAGE_MEMO)
        assertEquals("한 PDF 페이지에 남기는 텍스트 메모", UiCopy.PAGE_MEMO_DESCRIPTION)
        assertEquals("포스트잇", UiCopy.POST_IT)
        assertEquals("페이지 위에 자유롭게 배치하는 카드 메모", UiCopy.POST_IT_DESCRIPTION)
        assertEquals("노트에 담기", UiCopy.CAPTURE_TO_NOTE)
        assertEquals("선택한 내용을 학습 노트에 저장", UiCopy.CAPTURE_TO_NOTE_DESCRIPTION)
    }

    @Test
    fun `pdf hint copy names current navigation zoom and action locations`() {
        assertEquals("위쪽 페이지 이동", PDF_HINT_PAGE_NAVIGATION_TEXT)
        assertTrue(PDF_HINT_VIEW_ZOOM_TEXT.contains("보기 도구"))
        assertTrue(PDF_HINT_VIEW_ZOOM_TEXT.contains("두 손가락"))
        assertTrue(PDF_HINT_VIEW_ZOOM_TEXT.contains("더블 탭"))
        assertTrue(PDF_HINT_STYLE_UNDO_TEXT.contains("도구 독 위"))
        assertTrue(PDF_HINT_STYLE_UNDO_TEXT.contains("실행 취소"))
        assertFalse(PDF_HINT_STYLE_UNDO_TEXT.contains("하단 페이지 바"))
    }
}
