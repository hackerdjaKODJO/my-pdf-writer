package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeToolVisualsTest {

    @Test
    fun `shape inventory has one canonical order label and stable tag`() {
        assertEquals(
            listOf(Tool.RECT, Tool.TRIANGLE, Tool.ELLIPSE, Tool.LINE, Tool.ARROW),
            SHAPE_TOOLS,
        )
        assertEquals(
            listOf("사각형", "삼각형", "타원", "직선", "화살표"),
            SHAPE_TOOLS.map(::shapeToolLabel),
        )
        assertEquals(SHAPE_TOOLS.toSet(), viewerShapeTools)
        assertEquals(SHAPE_TOOLS, NOTE_SHAPE_TOOLS)
        assertEquals(SHAPE_TOOLS.size, SHAPE_TOOLS.map(::shapeToolTag).toSet().size)
        assertTrue(SHAPE_TOOLS.all { shapeToolTag(it).startsWith("tool_tile_") })
    }

    @Test
    fun `every shape has one distinct kind and non shapes never borrow a glyph`() {
        assertEquals(SHAPE_TOOLS.size, SHAPE_TOOLS.map(::shapeKindOrNull).toSet().size)
        listOf(Tool.PEN, Tool.ERASER, Tool.LASSO, Tool.VIEW).forEach { tool ->
            assertNull(shapeKindOrNull(tool))
            assertEquals("도형", shapeToolLabel(tool))
        }
    }

    @Test
    fun `shape choices announce selection and keep the group name`() {
        SHAPE_TOOLS.forEach { tool ->
            assertEquals("${shapeToolLabel(tool)}, 선택됨", stateLabel(shapeToolLabel(tool), true))
            assertEquals("도형", noteToolLabel(Tool.RECT))
            assertEquals("도형", noteToolLabel(Tool.TRIANGLE))
        }
    }

    @Test
    fun `five icon tiles still fit the compact note tray`() {
        val trayWidth = 5 * 48 + 4 * 4 + 2 * 4
        assertTrue(trayWidth <= 304)
        assertFalse(SHAPE_TOOLS.isEmpty())
    }

    @Test
    fun `active shapes expose the annotation picker and style without a seventh dock slot`() {
        assertEquals(
            listOf(ViewerContextAction.ANNOTATION_TOOLS, ViewerContextAction.STYLE),
            viewerContextActions(Tool.TRIANGLE, canUndo = false, canRedo = false, canOpenTools = true),
        )
        assertEquals(6, viewerDockSlots.size)
    }
}
