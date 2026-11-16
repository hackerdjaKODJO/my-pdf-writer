package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
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
}
