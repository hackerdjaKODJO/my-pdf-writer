package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHubSheetTest {

    @Test
    fun simpleModeShowsOnlyPrimaryActionsAndCapsTheGrid() {
        val sections = listOf(
            section(
                "읽기",
                action("검색", primary = true),
                action("목차"),
                action("텍스트 선택", primary = true),
            ),
            section(
                "문서",
                action("페이지 관리", primary = true),
                action("주석 목록", primary = true),
                action("페이지 편집"),
            ),
            section(
                "내보내기",
                action("PDF 내보내기", primary = true),
                action("인쇄"),
            ),
        )

        assertEquals(
            listOf("검색", "텍스트 선택", "페이지 관리", "주석 목록", "PDF 내보내기"),
            hubPrimaryActions(sections).map { it.label },
        )
    }

    @Test
    fun simpleModeNeverGrowsBeyondSevenActions() {
        val primaries = (0 until 9).map { index -> action("주요 $index", primary = true) }
        val sections = listOf(PdfHubSection("문서", primaries))

        assertEquals(PDF_HUB_PRIMARY_LIMIT, hubPrimaryActions(sections).size)
        assertTrue(PDF_HUB_PRIMARY_LIMIT in 5..7)
    }

    @Test
    fun expandedModeKeepsEveryActionReachableInSectionOrder() {
        val first = action("검색", primary = true)
        val second = action("목차")
        val third = action("스터디 노트", primary = true)
        val sections = listOf(
            PdfHubSection("읽기", listOf(first, second)),
            PdfHubSection("문서", listOf(third)),
        )
        val all = hubAllActions(sections)

        assertEquals(listOf(first, second, third), all)
        assertEquals(listOf("검색", "목차", "스터디 노트"), all.map { it.label })
        assertTrue("the expanded list must include every primary", all.containsAll(hubPrimaryActions(sections)))
    }

    private fun section(title: String, vararg actions: PdfHubAction) =
        PdfHubSection(title, actions.toList())

    private fun action(label: String, primary: Boolean = false) = PdfHubAction(
        label = label,
        icon = Icons.Outlined.Search,
        primary = primary,
        onClick = {},
    )
}
