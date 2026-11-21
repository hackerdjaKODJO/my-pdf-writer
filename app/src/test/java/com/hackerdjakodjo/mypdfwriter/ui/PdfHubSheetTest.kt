package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy tests for the student-first four-group 학습 메뉴. */
class PdfHubSheetTest {

    @Test
    fun firstLevelHasExactlyFourGroupsInTheStudentOrder() {
        assertEquals(PDF_HUB_MAX_TOP_LEVEL_GROUPS, 4)
        assertEquals(
            listOf("읽기", "필기", "담기", "설정"),
            pdfMenuTopLevelGroups.map { it.title },
        )
        assertEquals(4, pdfMenuTopLevelGroups.size)
        assertEquals(pdfMenuTopLevelGroups.toSet(), pdfMenuTopLevelGroups.toSet())
        // Nothing at the first level collapses: students should see every group.
        assertTrue(pdfMenuTopLevelGroups.none { it.expandable })
    }

    @Test
    fun readGroupFoldsAllNavigationIntoOneLocationRow() {
        assertEquals(
            listOf("위치", "현재 쪽 북마크", "읽기 모드", "오디오", "화면 설정", "화면에 맞추기"),
            pdfMenuActions(PdfMenuGroup.READ).map { it.label },
        )
        assertEquals(1, pdfMenuActions(PdfMenuGroup.READ).count { it == PdfMenuAction.LOCATIONS })
        assertEquals(PDF_HUB_LOCATIONS_LABEL, PdfMenuAction.LOCATIONS.label)
        assertEquals(PdfMenuGroup.READ, PdfMenuAction.LOCATIONS.group)
    }

    @Test
    fun noDuplicateNavigationRowsRemain() {
        val labels = PdfMenuAction.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        listOf("목차", "북마크 목록", "주석 목록", "발췌·번역").forEach { oldLabel ->
            assertFalse("'$oldLabel' must be folded into 위치 / 담은 목록", oldLabel in labels)
        }
        assertEquals(1, labels.count { it == PDF_HUB_LOCATIONS_LABEL })
        assertEquals(1, labels.count { it == PDF_HUB_CAPTURE_LABEL })
    }

    @Test
    fun writeAndCaptureGroupsKeepEveryStudentAction() {
        assertEquals(
            listOf("주석 도구", "포스트잇", "주석 숨기기/표시", "페이지 메모", "텍스트 선택", "문서 꾸미기"),
            pdfMenuActions(PdfMenuGroup.WRITE).map { it.label },
        )
        assertEquals(
            listOf(PDF_HUB_CAPTURE_LABEL, "담은 목록", "텍스트 복사"),
            pdfMenuActions(PdfMenuGroup.CAPTURE).map { it.label },
        )
        assertEquals(PdfMenuGroup.WRITE, PdfMenuAction.MEMO_CARDS.group)
        assertEquals("포스트잇", PdfMenuAction.MEMO_CARDS.label)
        assertEquals(PdfMenuGroup.WRITE, PdfMenuAction.TOOL_MORE.group)
        assertTrue(PdfMenuAction.TOOL_MORE in pdfMenuActions(PdfMenuGroup.WRITE))
    }

    @Test
    fun settingsKeepsChromeToggleAndPushesAdvancedFileOpsOneLevelDown() {
        assertEquals(
            listOf("파일 도구", "메뉴 자동 숨김", "도움말"),
            pdfMenuActions(PdfMenuGroup.SETTINGS).map { it.label },
        )
        assertEquals(
            listOf(
                "쪽 편집", "쪽 관리", "PDF 도구", "PDF 내보내기", "인쇄", "공유", "문서 정보", "페이지 잠금",
            ),
            pdfMenuActions(PdfMenuGroup.FILE_TOOLS).map { it.label },
        )
        assertEquals("쪽 편집", PdfMenuAction.PAGE_EDIT.label)
        assertEquals(PdfMenuGroup.FILE_TOOLS, PdfMenuAction.PAGE_EDIT.group)
        assertTrue(PdfMenuAction.EXPORT in pdfMenuActions(PdfMenuGroup.FILE_TOOLS))
        assertFalse(PdfMenuAction.EXPORT in pdfMenuActions(PdfMenuGroup.SETTINGS))
    }

    @Test
    fun readModeAudioAndFileToolsSubSheetsKeepTheirActions() {
        assertEquals(
            listOf("텍스트 모드", "연속 보기", "스프레드 보기", "팝업 보기", "소리 내어 읽기"),
            pdfReadModeActions.map { it.label },
        )
        assertEquals(listOf("오디오 노트", "오디오 재생"), pdfAudioActions.map { it.label })
        assertEquals(8, pdfFileToolsActions.size)
        assertEquals(PdfMenuAction.PAGE_EDIT, pdfFileToolsActions.first())
    }

    /**
     * Migration audit: no action was deleted or silently duplicated when the
     * menu was regrouped. SEARCH is accounted for explicitly as top-bar-owned.
     */
    @Test
    fun migrationAuditEveryActionAppearsExactlyOnce() {
        val topLevel = pdfMenuTopLevelGroups.flatMap { pdfMenuActions(it) }
        val subSheets = pdfReadModeActions + pdfAudioActions + pdfFileToolsActions
        val topBarOwned = pdfTopBarOwnedActions.toList()
        val reachable = topLevel + subSheets + topBarOwned

        assertEquals(PdfMenuAction.entries.size, reachable.size)
        assertEquals(PdfMenuAction.entries.toSet(), reachable.toSet())
        assertEquals("no action may appear twice", reachable.size, reachable.toSet().size)
        assertEquals(setOf(PdfMenuAction.SEARCH), topBarOwned.toSet())
        assertFalse("SEARCH is owned by the top bar, not the menu", PdfMenuAction.SEARCH in topLevel)
        assertFalse("SEARCH is owned by the top bar, not a sub-sheet", PdfMenuAction.SEARCH in subSheets)
    }

    @Test
    fun searchStaysInTheTopBarOnly() {
        assertEquals(setOf(PdfMenuAction.SEARCH), pdfTopBarOwnedActions)
        assertEquals("검색", PdfMenuAction.SEARCH.label)
        assertEquals(PdfMenuGroup.READ, PdfMenuAction.SEARCH.group)
        pdfMenuTopLevelGroups.forEach { group ->
            assertFalse(
                "SEARCH must not be listed in ${group.title}",
                PdfMenuAction.SEARCH in pdfMenuActions(group),
            )
        }
    }

    @Test
    fun stateDependentRowsKeepTheirFamiliarWording() {
        assertEquals("페이지 잠금", pdfMenuActionLabel(PdfMenuAction.PAGE_LOCK))
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
        assertEquals(
            "메뉴 자동 숨김 켜기",
            pdfMenuActionLabel(PdfMenuAction.AUTO_HIDE, autoHideEnabled = false),
        )
        assertEquals(
            "메뉴 자동 숨김 끄기",
            pdfMenuActionLabel(PdfMenuAction.AUTO_HIDE, autoHideEnabled = true),
        )
        assertEquals(
            "현재 쪽 북마크 해제",
            pdfMenuActionLabel(PdfMenuAction.BOOKMARK_TOGGLE, bookmarked = true),
        )
        assertEquals("검색", pdfMenuActionLabel(PdfMenuAction.SEARCH))
    }

    @Test
    fun narrowAndLargeFontHubRowsWrapToTwoLinesWith48dpTargets() {
        val narrow = pdfHubRowLayout(PDF_HUB_NARROW_WIDTH_DP, 2f)
        val largeFont = pdfHubRowLayout(360, 1.5f)
        val normal = pdfHubRowLayout(360, 1f)

        assertEquals(2, narrow.labelMaxLines)
        assertEquals(2, largeFont.labelMaxLines)
        assertEquals(1, normal.labelMaxLines)
        listOf(narrow, largeFont, normal).forEach { layout ->
            assertTrue("hub row target must be at least 48dp", isAccessibleTarget(layout.minHeightDp))
            assertEquals(layout.minHeightDp, accessibleTarget(layout.minHeightDp))
        }
        assertTrue(narrow.minHeightDp >= MIN_TARGET_DP)
    }

    @Test
    fun hubAllAndVisibleActionsRespectCollapsedSections() {
        val location = pdfLocationsAction {}
        val help = action("도움말")
        val sections = listOf(
            PdfHubSection("읽기", listOf(location)),
            PdfHubSection("설정", listOf(help), expandable = true),
        )

        assertEquals(listOf("위치"), hubVisibleActions(sections, emptySet()).map { it.label })
        assertEquals(
            listOf("위치", "도움말"),
            hubVisibleActions(sections, setOf("설정")).map { it.label },
        )
        assertEquals(listOf("위치", "도움말"), hubAllActions(sections).map { it.label })
    }

    @Test
    fun locationsActionDispatchesExactlyOneOpen() {
        var opened = 0
        val location = pdfLocationsAction { opened++ }
        val sections = listOf(PdfHubSection(PDF_HUB_READ_GROUP_TITLE, listOf(location)))

        assertEquals(
            listOf(PDF_HUB_LOCATIONS_LABEL),
            hubAllActions(sections).map { it.label },
        )
        hubAllActions(sections).single().onClick()
        assertEquals(1, opened)
    }

    private fun action(label: String) = PdfHubAction(
        label = label,
        icon = Icons.Outlined.Search,
        onClick = {},
    )
}
