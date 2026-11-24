package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
