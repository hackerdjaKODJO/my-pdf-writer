package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerToolDockTest {

    @Test
    fun shapeToolsMatchTheCanvasShapeGestures() {
        assertEquals(
            setOf(Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE),
            viewerShapeTools,
        )
        assertTrue(isShapeTool(Tool.RECT))
        assertTrue(isShapeTool(Tool.ELLIPSE))
        assertFalse(isShapeTool(Tool.PEN))
        assertFalse(isShapeTool(Tool.REDACT))
    }

    @Test
    fun styleToolsAreOnlyTheToolsThatUseStyleState() {
        assertTrue(isStyleTool(Tool.PEN))
        assertTrue(isStyleTool(Tool.HIGHLIGHT))
        assertTrue(isStyleTool(Tool.TEXT))
        assertTrue(isStyleTool(Tool.RECT))
        assertTrue(isStyleTool(Tool.STAMP))
        assertTrue(isStyleTool(Tool.SIGNATURE))
        assertFalse(isStyleTool(Tool.VIEW))
        assertFalse(isStyleTool(Tool.ERASER))
        assertFalse(isStyleTool(Tool.REDACT))
    }

    @Test
    fun extraToolsMatchTheMoreSheetEntries() {
        listOf(
            Tool.STAMP,
            Tool.SIGNATURE,
            Tool.REDACT,
            Tool.UNDERLINE,
            Tool.STRIKEOUT,
        ).forEach { tool ->
            assertTrue("$tool should live in the more sheet", isExtraTool(tool))
        }
        assertFalse(isExtraTool(Tool.PEN))
        assertFalse(isExtraTool(Tool.RECT))
        assertFalse(isExtraTool(Tool.TEXT))
    }
}
