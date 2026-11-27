package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorControlsTest {

    @Test
    fun `shape group contains only shape tools`() {
        assertTrue(noteIsShapeTool(Tool.RECT))
        assertTrue(noteIsShapeTool(Tool.TRIANGLE))
        assertTrue(noteIsShapeTool(Tool.ARROW))
        assertTrue(noteIsShapeTool(Tool.LINE))
        assertTrue(noteIsShapeTool(Tool.ELLIPSE))

        assertFalse(noteIsShapeTool(Tool.PEN))
        assertFalse(noteIsShapeTool(Tool.LASSO))
        assertFalse(noteIsShapeTool(Tool.TEXT))
    }

    @Test
    fun `lasso group contains both lasso modes`() {
        assertTrue(noteIsLassoTool(Tool.LASSO))
        assertTrue(noteIsLassoTool(Tool.LASSO_RECT))
        assertFalse(noteIsLassoTool(Tool.PEN))
        assertFalse(noteIsLassoTool(Tool.RECT))
    }

    @Test
    fun `style sheet only offers controls used by the active tool`() {
        assertTrue(noteStyleShowsColor(Tool.PEN))
        assertTrue(noteStyleShowsColor(Tool.TEXT))
        assertFalse(noteStyleShowsColor(Tool.ERASER))
        assertFalse(noteStyleShowsColor(Tool.LASSO))

        assertTrue(noteStyleShowsPenType(Tool.PEN))
        assertFalse(noteStyleShowsPenType(Tool.HIGHLIGHT))
        assertFalse(noteStyleShowsPenType(Tool.RECT))

        assertTrue(noteStyleShowsThickness(Tool.PEN))
        assertTrue(noteStyleShowsThickness(Tool.HIGHLIGHT))
        assertTrue(noteStyleShowsThickness(Tool.ARROW))
        assertTrue(noteStyleShowsThickness(Tool.TRIANGLE))
        assertFalse(noteStyleShowsThickness(Tool.TEXT))
        assertFalse(noteStyleShowsThickness(Tool.ERASER))

        assertTrue(noteStyleShowsOpacity(Tool.PEN))
        assertTrue(noteStyleShowsOpacity(Tool.TEXT))
        assertTrue(noteStyleShowsOpacity(Tool.ELLIPSE))
        assertTrue(noteStyleShowsOpacity(Tool.TRIANGLE))
        assertFalse(noteStyleShowsOpacity(Tool.HIGHLIGHT))

        assertTrue(noteStyleShowsFontSize(Tool.TEXT))
        assertFalse(noteStyleShowsFontSize(Tool.PEN))
    }

    @Test
    fun `page pill disables arrows at the ends`() {
        assertFalse(notePagePreviousEnabled(0))
        assertTrue(notePagePreviousEnabled(1))
        assertTrue(notePageNextEnabled(0, 3))
        assertTrue(notePageNextEnabled(1, 3))
        assertFalse(notePageNextEnabled(2, 3))
        assertFalse(notePageNextEnabled(0, 1))
        assertFalse(notePageNextEnabled(0, 0))
    }

    @Test
    fun `compact dock keeps six unique primary tools in one row`() {
        assertEquals(
            listOf(
                Tool.PEN,
                Tool.HIGHLIGHT,
                Tool.ERASER,
                Tool.LASSO,
                Tool.TEXT,
                Tool.RECT,
            ),
            NOTE_COMPACT_DOCK_TOOLS,
        )
        assertEquals(6, NOTE_COMPACT_DOCK_TOOLS.toSet().size)
        assertEquals(NOTE_DOCK_TOOLS, NOTE_COMPACT_DOCK_TOOLS)
        assertEquals(NOTE_DOCK_TOOLS, NOTE_FULL_DOCK_TOOLS)
        assertEquals(
            listOf("펜", "형광펜", "지우개", "선택", NOTE_TEXT_TOOL_LABEL, "도형"),
            NOTE_DOCK_TOOLS.map(::noteToolLabel),
        )
        val labels = NOTE_COMPACT_DOCK_TOOLS.map(::noteCompactToolLabel)
        assertEquals(6, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals("펜", noteCompactToolLabel(Tool.PEN))
        assertEquals("형광펜", noteCompactToolLabel(Tool.HIGHLIGHT))
        assertEquals("지우개", noteCompactToolLabel(Tool.ERASER))
        assertEquals("선택", noteCompactToolLabel(Tool.LASSO))
        assertEquals("선택", noteToolLabel(Tool.LASSO_RECT))
        assertEquals(NOTE_TEXT_TOOL_LABEL, noteCompactToolLabel(Tool.TEXT))
        assertEquals(NOTE_TEXT_TOOL_LABEL, noteToolLabel(Tool.TEXT))
        assertEquals("도형", noteCompactToolLabel(Tool.RECT))
        assertEquals(
            "the compact and full docks must share one label source",
            NOTE_FULL_DOCK_TOOLS.map(::noteToolLabel),
            labels,
        )
    }

    @Test
    fun `compact dock has one style slot and keeps favourite out`() {
        assertEquals(
            listOf(
                NoteDockActionSlot.STYLE,
                NoteDockActionSlot.UNDO,
                NoteDockActionSlot.REDO,
                NoteDockActionSlot.MENU,
            ),
            NOTE_COMPACT_ACTION_SLOTS,
        )
        assertEquals(1, NOTE_COMPACT_ACTION_SLOTS.count { it == NoteDockActionSlot.STYLE })
        assertFalse(NoteDockActionSlot.entries.any { it.name == "FAVORITE" })
        assertEquals(NoteDockActionSlot.entries.toSet(), NOTE_COMPACT_ACTION_SLOTS.toSet())

        // The full dock is one row: only the trailing style pill remains here.
        assertEquals(listOf(NoteDockActionSlot.STYLE), NOTE_FULL_DOCK_ACTION_SLOTS)
        assertEquals(1, NOTE_FULL_DOCK_ACTION_SLOTS.count { it == NoteDockActionSlot.STYLE })
        assertFalse(NoteDockActionSlot.UNDO in NOTE_FULL_DOCK_ACTION_SLOTS)
        assertFalse(NoteDockActionSlot.REDO in NOTE_FULL_DOCK_ACTION_SLOTS)
    }

    @Test
    fun `top bar has undo redo menu and no duplicate favourite or export`() {
        assertEquals(
            listOf(NoteTopBarAction.MENU, NoteTopBarAction.SEARCH, NoteTopBarAction.UNDO, NoteTopBarAction.REDO),
            NOTE_TOP_BAR_ACTIONS,
        )
        assertEquals(4, NOTE_TOP_BAR_ACTIONS.toSet().size)
        assertTrue(NoteTopBarAction.FAVORITE !in NOTE_TOP_BAR_ACTIONS)
        assertTrue(NoteTopBarAction.EXPORT !in NOTE_TOP_BAR_ACTIONS)
        val labels = NOTE_TOP_BAR_ACTIONS.map(::noteTopBarActionLabel)
        assertEquals(listOf("노트 메뉴", "노트 검색", "실행 취소", "다시 실행"), labels)
        assertFalse("즐겨찾기" in labels)
        assertFalse("PDF로 내보내기" in labels)
    }

    @Test
    fun `note vocabulary keeps study card names stable`() {
        assertEquals("글상자", NOTE_TEXT_TOOL_LABEL)
        assertEquals("페이지", NOTE_PAGE_MANAGER_LABEL)
        assertEquals("내 펜", NOTE_PEN_PRESETS_LABEL)
        // The legacy card constants are untouched; the menu uses student-facing aliases.
        assertEquals("가리기 카드", NOTE_MASKING_CARD_LABEL)
        assertEquals("카드", NOTE_REVIEW_CARDS_LABEL)
        assertEquals("가리기 카드", NOTE_MASKING_REVIEW_CARD_LABEL)
        assertEquals("필기 설정", NOTE_INK_SETTINGS_LABEL)
        assertEquals("체크리스트", NOTE_CHECKLIST_LABEL)
        assertEquals("포스트잇", NOTE_MEMO_CARD_LABEL)
        assertTrue(noteStyleTitle(Tool.TEXT).contains(NOTE_TEXT_TOOL_LABEL))
        assertEquals("삼각형 스타일", noteStyleTitle(Tool.TRIANGLE))
        assertTrue(noteToolHint(Tool.TEXT).contains(NOTE_TEXT_TOOL_LABEL))
    }

    @Test
    fun `full dock is one tool row while compact dock keeps its action row`() {
        val compact = noteCompactDockHeightDp(showContextTray = false)
        val full = noteFullDockHeightDp(showContextTray = false)

        // Full screen moved undo/redo to the top bar and floats the page pill.
        assertEquals(NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP, full)
        assertEquals(
            full + NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP,
            noteFullDockHeightDp(showContextTray = true),
        )
        // The compact pane has no top bar, so its dock keeps a 48 dp action row.
        assertTrue("compact rows must meet 48 dp, was $compact", compact >= MIN_TARGET_DP * 2)
        assertTrue("compact dock should stay under 120 dp, was $compact", compact <= 120)
        assertEquals(
            compact + NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP,
            noteCompactDockHeightDp(showContextTray = true),
        )
        assertTrue(full < compact)
    }

    @Test
    fun `restore pill label is distinct from every compact tool label`() {
        // The pill is announced by its "도구" text; no tool may steal that label,
        // otherwise the study-pane E2E target becomes ambiguous.
        assertTrue(noteCompactToolLabel(Tool.PEN) != "도구")
        assertTrue(NOTE_COMPACT_DOCK_TOOLS.none { noteCompactToolLabel(it) == "도구" })
        assertEquals(NoteCompactChromeMode.RESTORE_PILL, noteCompactChromeMode(false))
    }

    @Test
    fun `compact hint is hidden for pen tools and stays one line`() {
        assertFalse(noteCompactHintVisible(Tool.PEN))
        assertFalse(noteCompactHintVisible(Tool.HIGHLIGHT))
        assertTrue(noteCompactHintVisible(Tool.TEXT))
        assertTrue(noteCompactHintVisible(Tool.ERASER))
        assertTrue(noteCompactHintVisible(Tool.LASSO))

        val hint = noteCompactHint(
            tool = Tool.TEXT,
            layerName = "레이어 1",
            layerLocked = true,
            zoomLock = true,
        )
        assertTrue(hint.isNotBlank())
        assertFalse(hint.contains('\n'))
        assertTrue(hint.contains(NOTE_TEXT_TOOL_LABEL))
        assertTrue(hint.contains("레이어 1"))
        assertTrue(hint.contains("잠김"))
        assertTrue(hint.contains("줌 잠금"))
    }

    @Test
    fun `dock and page descriptions name the tool state and page`() {
        assertEquals("펜", noteToolDescription("펜", false))
        assertEquals("펜, 선택됨", noteToolDescription("펜", true))
        assertEquals("형광펜, 선택됨", noteToolDescription("형광펜", true))

        assertEquals("페이지 1 / 3", notePagePillDescription(0, 3))
        assertEquals("페이지 3 / 3", notePagePillDescription(2, 3))
        assertEquals("페이지 1", notePageThumbnailDescription(0))
        assertEquals("페이지 3", notePageThumbnailDescription(2))
        assertEquals("페이지 3, 선택됨", stateLabel(notePageThumbnailDescription(2), true))
    }

    @Test
    fun `style controls describe colors and slider values`() {
        assertEquals("검정", noteColorName(NOTE_PALETTE[0]))
        assertEquals("노랑", noteColorName(NOTE_PALETTE.last()))
        assertEquals("사용자 색상", noteColorName(0xFF123456.toInt()))

        assertEquals("검정, 선택됨", colorChoiceLabel(noteColorName(NOTE_PALETTE[0]), true))
        assertEquals("굵기 4", noteStyleSliderDescription("굵기", "4"))
        assertEquals("투명도 80%", noteStyleSliderDescription("투명도", "80%"))
    }

    @Test
    fun `memo close hide and restore stay distinct and accessible`() {
        assertEquals("닫기", MEMO_CARD_CLOSE_LABEL)
        assertEquals("포스트잇 닫기", MEMO_CARD_CLOSE_DESCRIPTION)
        assertEquals("memo_card_close_m1", memoCardCloseTag("m1"))
        assertEquals("포스트잇 숨기기", MEMO_HIDE_CARDS_LABEL)
        assertEquals("포스트잇 표시", MEMO_SHOW_CARDS_LABEL)
        assertEquals("memo_cards_restore_pill", MEMO_CARDS_RESTORE_PILL_TAG)
        assertTrue(MEMO_CARD_HEADER_MIN_HEIGHT_DP >= MIN_TARGET_DP)
        assertFalse(MEMO_CARD_CLOSE_DESCRIPTION.contains("삭제"))
        assertFalse(MEMO_HIDE_CARDS_LABEL.contains("삭제"))
    }

    @Test
    fun `pen is the primary dock tool in both six slot docks`() {
        assertEquals(Tool.PEN, NOTE_PRIMARY_DOCK_TOOL)
        assertTrue(noteToolIsPrimary(Tool.PEN))
        NOTE_DOCK_TOOLS.forEach { tool ->
            assertEquals("only pen is primary", tool == Tool.PEN, noteToolIsPrimary(tool))
        }
        assertEquals(1, NOTE_DOCK_TOOLS.count(::noteToolIsPrimary))
        // Primary is chrome only: the spoken label stays the short tool name.
        assertEquals("펜", noteToolLabel(NOTE_PRIMARY_DOCK_TOOL))
        assertEquals("펜", noteCompactToolLabel(NOTE_PRIMARY_DOCK_TOOL))
    }

    @Test
    fun `student labels wrap to two lines and never more`() {
        assertEquals(2, STUDENT_LABEL_MAX_LINES)
        assertEquals(1, studentLabelMaxLines(220, 80))
        assertEquals(2, studentLabelMaxLines(80, 220))
        assertEquals(2, studentLabelMaxLines(80, 220, 2f))
        // Large font can only trade a line; it never adds a third.
        (1..5).forEach { scale ->
            assertTrue(studentLabelMaxLines(48, 400, scale.toFloat()) <= STUDENT_LABEL_MAX_LINES)
        }
        assertEquals(1, studentLabelMaxLines(300, 80, 3f))
        assertEquals(STUDENT_LABEL_MAX_LINES, minOf(labelMaxLines(1, 2), STUDENT_LABEL_MAX_LINES))
    }

    @Test
    fun longPressTargetsAreOnlyPenAndHighlight() {
        assertEquals(
            listOf(Tool.PEN, Tool.HIGHLIGHT),
            NOTE_DOCK_TOOLS.filter(::noteDockLongPressOpensStyle),
        )
        assertTrue(!noteDockLongPressOpensStyle(Tool.ERASER))
        assertTrue(!noteDockLongPressOpensStyle(Tool.TEXT))
    }

    @Test
    fun `one tap add page lives on the pill only on phones`() {
        assertTrue("phone full editor", notePagePillShowsAdd(wide = false, compact = false))
        assertFalse("phone compact dock owns add", notePagePillShowsAdd(wide = false, compact = true))
        assertFalse("wide rail owns add", notePagePillShowsAdd(wide = true, compact = false))
        assertFalse("wide compact", notePagePillShowsAdd(wide = true, compact = true))
    }
}
