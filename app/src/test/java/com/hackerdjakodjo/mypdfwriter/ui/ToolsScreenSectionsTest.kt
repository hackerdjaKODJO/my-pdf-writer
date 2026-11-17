package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
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
}
