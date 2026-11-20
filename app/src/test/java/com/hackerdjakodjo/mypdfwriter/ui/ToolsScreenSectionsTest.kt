package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsScreenSectionsTest {

    @Test
    fun pdfToolsUseTheRequiredSectionOrder() {
        assertEquals(
            listOf(
                "가져오기·변환",
                "페이지",
                "OCR·검색",
                "보안·정보",
                "양식",
                "공유·인쇄",
            ),
            PdfToolSections.order,
        )
    }

    @Test
    fun sectionTitlesAreUnique() {
        assertEquals(PdfToolSections.order.size, PdfToolSections.order.distinct().size)
    }

    @Test
    fun filePickerButtonNamesItsAction() {
        assertEquals("PDF 파일 선택", toolSourceButtonLabel(false))
        assertEquals("PDF 파일 변경", toolSourceButtonLabel(true))
    }

    @Test
    fun toolRowsMeetTheMinimumTouchTargetAtEveryFontScale() {
        assertEquals(56, toolRowMinHeightDp(1f))
        assertEquals(56, toolRowMinHeightDp(1.5f))
        assertEquals(56, toolRowMinHeightDp(3f))
        assertTrue(toolRowMinHeightDp(1.2f) >= 48)
    }
}
