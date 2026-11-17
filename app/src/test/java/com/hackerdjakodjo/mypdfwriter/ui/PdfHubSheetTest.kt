package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHubSheetTest {

    @Test
    fun menuGroupsKeepTheRequestedOrderAndOnlyAdvancedCollapses() {
        assertEquals(
            listOf("읽기", "페이지", "보기", "내보내기", "고급"),
            pdfMenuTopLevelGroups.map { it.title },
        )
        assertEquals(
            listOf(false, false, false, false, true),
            pdfMenuTopLevelGroups.map { it.expandable },
        )
        assertFalse(PDF_HUB_ADVANCED_DEFAULT_EXPANDED)
    }

    @Test
    fun readingGroupKeepsSearchAndEveryReadingAction() {
        assertEquals(
            listOf("검색", "목차", "북마크 목록", "페이지 메모", "발췌·번역", "텍스트 선택", "스터디 노트"),
            pdfMenuActions(PdfMenuGroup.READING).map { it.label },
        )
    }

    @Test
    fun pageViewAndExportGroupsMatchTheRequestedSections() {
        assertEquals(
            listOf("페이지 관리", "페이지 편집", "주석 목록", "주석 숨기기/표시"),
            pdfMenuActions(PdfMenuGroup.PAGES).map { it.label },
        )
        assertEquals(
            listOf("화면 설정", "읽기 모드", "오디오"),
            pdfMenuActions(PdfMenuGroup.VIEW).map { it.label },
        )
        assertEquals(
            listOf("PDF 내보내기", "인쇄", "공유"),
            pdfMenuActions(PdfMenuGroup.EXPORT).map { it.label },
        )
    }

    @Test
    fun advancedGroupKeepsEveryFormerDocumentAction() {
        assertEquals(
            listOf(
                "페이지 잠금",
                "텍스트 복사",
                "화면에 맞추기",
                "문서 정보",
                "문서 꾸미기",
                "PDF 도구",
                "도움말",
            ),
            pdfMenuActions(PdfMenuGroup.ADVANCED).map { it.label },
        )
    }

    @Test
    fun readModeAndAudioSubSheetsKeepTheirActions() {
        assertEquals(
            listOf("텍스트 모드", "연속 보기", "스프레드 보기", "팝업 보기", "소리 내어 읽기"),
            pdfReadModeActions.map { it.label },
        )
        assertEquals(
            listOf("오디오 노트", "오디오 재생"),
            pdfAudioActions.map { it.label },
        )
    }

    @Test
    fun everyMenuActionIsReachableFromExactlyOneGroupList() {
        val topLevel = pdfMenuTopLevelGroups.flatMap { pdfMenuActions(it) }
        val subSheets = pdfReadModeActions + pdfAudioActions
        val reachable = topLevel + subSheets

        assertEquals(PdfMenuAction.entries.size, reachable.size)
        assertEquals(PdfMenuAction.entries.toSet(), reachable.toSet())
        assertTrue(topLevel.isNotEmpty())
        assertTrue(subSheets.isNotEmpty())
    }

    @Test
    fun stateDependentRowsKeepTheirFamiliarWording() {
        assertEquals(
            "페이지 잠금",
            pdfMenuActionLabel(PdfMenuAction.PAGE_LOCK, pageLocked = false),
        )
        assertEquals(
            "페이지 잠금 해제",
            pdfMenuActionLabel(PdfMenuAction.PAGE_LOCK, pageLocked = true),
        )
        assertEquals(
            "주석 숨기기",
            pdfMenuActionLabel(PdfMenuAction.TOGGLE_ANNOTATIONS, annotationsHidden = false),
        )
        assertEquals(
            "주석 표시",
            pdfMenuActionLabel(PdfMenuAction.TOGGLE_ANNOTATIONS, annotationsHidden = true),
        )
        assertEquals("검색", pdfMenuActionLabel(PdfMenuAction.SEARCH))
    }

    @Test
    fun collapsedAdvancedGroupStaysReachableButHiddenUntilExpanded() {
        val search = action("검색")
        val pageLock = action("페이지 잠금")
        val sections = listOf(
            PdfHubSection("읽기", listOf(search)),
            PdfHubSection("고급", listOf(pageLock), expandable = true),
        )

        assertEquals(listOf("검색"), hubVisibleActions(sections, emptySet()).map { it.label })
        assertEquals(
            listOf("검색", "페이지 잠금"),
            hubVisibleActions(sections, setOf("고급")).map { it.label },
        )
        assertEquals(listOf("검색", "페이지 잠금"), hubAllActions(sections).map { it.label })
    }

    private fun action(label: String) = PdfHubAction(
        label = label,
        icon = Icons.Outlined.Search,
        onClick = {},
    )
}
