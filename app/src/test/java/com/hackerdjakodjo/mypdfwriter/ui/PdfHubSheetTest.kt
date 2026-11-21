package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfHubSheetTest {

    @Test
    fun menuGroupsKeepTheStudyFirstOrderAndOnlyOtherCollapses() {
        assertEquals(
            listOf("학습", "도구", "보기·페이지", "내보내기", "기타"),
            pdfMenuTopLevelGroups.map { it.title },
        )
        assertEquals(
            listOf(false, false, false, false, true),
            pdfMenuTopLevelGroups.map { it.expandable },
        )
        assertFalse(PDF_HUB_OTHER_DEFAULT_EXPANDED)
    }

    @Test
    fun studyGroupKeepsEveryStudyAction() {
        assertEquals(
            listOf("검색", "목차", "북마크 목록", "페이지 메모", "발췌·번역", "텍스트 선택", "스터디 노트"),
            PdfMenuAction.entries.filter { it.group == PdfMenuGroup.STUDY }.map { it.label },
        )
        // SEARCH is still an action, but the viewer top bar owns it.
        assertEquals(
            listOf("목차", "북마크 목록", "페이지 메모", "발췌·번역", "텍스트 선택", "스터디 노트"),
            pdfMenuActions(PdfMenuGroup.STUDY).map { it.label },
        )
    }

    @Test
    fun postItsLiveUnderToolsAndKeepTheirAction() {
        assertEquals(PdfMenuGroup.TOOLS, PdfMenuAction.MEMO_CARDS.group)
        assertEquals("포스트잇", PdfMenuAction.MEMO_CARDS.label)
        assertTrue(PdfMenuAction.MEMO_CARDS in pdfMenuActions(PdfMenuGroup.TOOLS))
        // The dock's annotation slot was replaced by 노트; 주석 도구 is the entry
        // point for 글상자, shapes, markup, stamps and favorite pens.
        assertEquals(PdfMenuGroup.TOOLS, PdfMenuAction.TOOL_MORE.group)
        assertEquals("주석 도구", PdfMenuAction.TOOL_MORE.label)
        assertTrue(PdfMenuAction.TOOL_MORE in pdfMenuActions(PdfMenuGroup.TOOLS))
    }

    @Test
    fun toolsGroupKeepsAnnotationAndPdfToolActions() {
        assertEquals(
            listOf(
                "포스트잇", "주석 도구", "주석 목록", "주석 숨기기/표시", "오디오", "PDF 도구",
            ),
            pdfMenuActions(PdfMenuGroup.TOOLS).map { it.label },
        )
    }

    @Test
    fun pageViewExportAndOtherGroupsMatchTheRequestedSections() {
        assertEquals(
            listOf("화면 설정", "읽기 모드", "페이지 관리", "페이지", "화면에 맞추기"),
            pdfMenuActions(PdfMenuGroup.VIEW_PAGES).map { it.label },
        )
        assertEquals("페이지", PdfMenuAction.PAGE_EDIT.label)
        assertEquals(
            listOf("PDF 내보내기", "인쇄", "공유"),
            pdfMenuActions(PdfMenuGroup.EXPORT).map { it.label },
        )
        assertEquals(
            listOf("페이지 잠금", "텍스트 복사", "문서 정보", "문서 꾸미기", "도움말"),
            pdfMenuActions(PdfMenuGroup.OTHER).map { it.label },
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

    /**
     * Migration audit: no action was deleted or silently duplicated when the
     * menu was regrouped. SEARCH is accounted for explicitly as top-bar-owned.
     */
    @Test
    fun migrationAuditEveryActionAppearsExactlyOnce() {
        val topLevel = pdfMenuTopLevelGroups.flatMap { pdfMenuActions(it) }
        val subSheets = pdfReadModeActions + pdfAudioActions
        val topBarOwned = pdfTopBarOwnedActions.toList()
        val reachable = topLevel + subSheets + topBarOwned

        assertEquals(PdfMenuAction.entries.size, reachable.size)
        assertEquals(PdfMenuAction.entries.toSet(), reachable.toSet())
        assertEquals("no action may appear twice", reachable.size, reachable.toSet().size)
        assertEquals(setOf(PdfMenuAction.SEARCH), topBarOwned.toSet())
        assertFalse("SEARCH is owned by the top bar, not the menu", PdfMenuAction.SEARCH in topLevel)
        assertFalse("SEARCH is owned by the top bar, not a sub-sheet", PdfMenuAction.SEARCH in subSheets)
        assertTrue(topLevel.isNotEmpty())
        assertTrue(subSheets.isNotEmpty())
    }

    @Test
    fun searchStaysInTheTopBarOnly() {
        assertEquals(setOf(PdfMenuAction.SEARCH), pdfTopBarOwnedActions)
        assertEquals("검색", PdfMenuAction.SEARCH.label)
        assertEquals(PdfMenuGroup.STUDY, PdfMenuAction.SEARCH.group)
        pdfMenuTopLevelGroups.forEach { group ->
            assertFalse("SEARCH must not be listed in ${group.title}", PdfMenuAction.SEARCH in pdfMenuActions(group))
        }
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
    fun collapsedOtherGroupStaysReachableButHiddenUntilExpanded() {
        val search = action("검색")
        val pageLock = action("페이지 잠금")
        val sections = listOf(
            PdfHubSection("학습", listOf(search)),
            PdfHubSection("기타", listOf(pageLock), expandable = true),
        )

        assertEquals(listOf("검색"), hubVisibleActions(sections, emptySet()).map { it.label })
        assertEquals(
            listOf("검색", "페이지 잠금"),
            hubVisibleActions(sections, setOf("기타")).map { it.label },
        )
        assertEquals(listOf("검색", "페이지 잠금"), hubAllActions(sections).map { it.label })
    }

    @Test
    fun locationsActionLivesInTheReadOnlyGroupOfTheHub() {
        var opened = 0
        val sections = listOf(
            PdfHubSection("학습", listOf(action("검색"))),
            PdfHubSection("도구", listOf(action("포스트잇"))),
        )

        val withLocations = pdfHubSectionsWithLocations(sections) { opened++ }

        assertEquals(
            listOf("학습", "읽기", "도구"),
            withLocations.map { it.title },
        )
        val readGroup = withLocations[1]
        assertEquals(listOf("위치"), readGroup.actions.map { it.label })
        assertFalse(readGroup.expandable)
        readGroup.actions.single().onClick()
        assertEquals(1, opened)
    }

    @Test
    fun locationsActionDoesNotChangeAnyExistingGroupOrAction() {
        val study = listOf(action("검색"), action("목차"))
        val sections = listOf(
            PdfHubSection("학습", study),
            PdfHubSection("기타", listOf(action("도움말")), expandable = true),
        )

        val withLocations = pdfHubSectionsWithLocations(sections) {}

        assertEquals(listOf("검색", "목차"), withLocations.first().actions.map { it.label })
        assertEquals(sections.last(), withLocations.last())
        assertEquals(
            listOf("위치"),
            withLocations.filter { it.title == PDF_HUB_READ_GROUP_TITLE }
                .flatMap { it.actions }
                .map { it.label },
        )
        assertEquals(PDF_HUB_LOCATIONS_LABEL, pdfLocationsAction {} .label)
    }

    @Test
    fun emptyHubStillGetsTheLocationsAction() {
        val withLocations = pdfHubSectionsWithLocations(emptyList()) {}

        assertEquals(listOf("읽기"), withLocations.map { it.title })
        assertEquals(listOf("위치"), hubAllActions(withLocations).map { it.label })
        assertEquals(listOf("위치"), hubVisibleActions(withLocations, emptySet()).map { it.label })
    }

    private fun action(label: String) = PdfHubAction(
        label = label,
        icon = Icons.Outlined.Search,
        onClick = {},
    )
}
