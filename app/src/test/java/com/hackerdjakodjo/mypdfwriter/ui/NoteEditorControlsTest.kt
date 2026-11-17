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
        assertFalse(noteStyleShowsThickness(Tool.TEXT))
        assertFalse(noteStyleShowsThickness(Tool.ERASER))

        assertTrue(noteStyleShowsOpacity(Tool.PEN))
        assertTrue(noteStyleShowsOpacity(Tool.TEXT))
        assertTrue(noteStyleShowsOpacity(Tool.ELLIPSE))
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
        val labels = NOTE_COMPACT_DOCK_TOOLS.map(::noteCompactToolLabel)
        assertEquals(6, labels.toSet().size)
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals("펜", noteCompactToolLabel(Tool.PEN))
        assertEquals("형광펜", noteCompactToolLabel(Tool.HIGHLIGHT))
        assertEquals(NOTE_TEXT_TOOL_LABEL, noteCompactToolLabel(Tool.TEXT))
        assertEquals(NOTE_TEXT_TOOL_LABEL, noteToolLabel(Tool.TEXT))
        assertEquals(
            "the compact and full docks must share one label source",
            NOTE_COMPACT_DOCK_TOOLS.map(::noteToolLabel),
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

        assertEquals(
            listOf(
                NoteDockActionSlot.STYLE,
                NoteDockActionSlot.UNDO,
                NoteDockActionSlot.REDO,
            ),
            NOTE_FULL_DOCK_ACTION_SLOTS,
        )
        assertEquals(1, NOTE_FULL_DOCK_ACTION_SLOTS.count { it == NoteDockActionSlot.STYLE })
    }

    @Test
    fun `note vocabulary keeps study card names stable`() {
        assertEquals("글상자", NOTE_TEXT_TOOL_LABEL)
        assertEquals("페이지", NOTE_PAGE_MANAGER_LABEL)
        assertEquals("내 펜", NOTE_PEN_PRESETS_LABEL)
        assertEquals("가림 카드", NOTE_MASKING_CARD_LABEL)
        assertEquals("체크리스트", NOTE_CHECKLIST_LABEL)
        assertTrue(noteStyleTitle(Tool.TEXT).contains(NOTE_TEXT_TOOL_LABEL))
        assertTrue(noteToolHint(Tool.TEXT).contains(NOTE_TEXT_TOOL_LABEL))
    }

    @Test
    fun `compact dock is shorter than the full dock`() {
        val compact = noteCompactDockHeightDp(showContextTray = false)
        val full = noteFullDockHeightDp(showContextTray = false)

        assertTrue(compact < full)
        assertTrue("compact dock should stay under 80 dp, was $compact", compact <= 80)
        assertEquals(
            compact + NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP,
            noteCompactDockHeightDp(showContextTray = true),
        )
        assertEquals(
            full + NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP,
            noteFullDockHeightDp(showContextTray = true),
        )
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
}
