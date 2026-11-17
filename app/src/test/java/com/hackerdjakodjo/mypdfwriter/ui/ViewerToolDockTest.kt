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
    fun dockShowsTheActiveExtraToolName() {
        assertEquals("밑줄", dockMoreLabel(Tool.UNDERLINE))
        assertEquals("취소선", dockMoreLabel(Tool.STRIKEOUT))
        assertEquals("스탬프", dockMoreLabel(Tool.STAMP))
        assertEquals("서명", dockMoreLabel(Tool.SIGNATURE))
        assertEquals("가림", dockMoreLabel(Tool.REDACT))
        assertEquals("더보기", dockMoreLabel(Tool.PEN))
        assertEquals("더보기", dockMoreLabel(Tool.VIEW))
    }

    @Test
    fun dockShowsTheActiveShapeName() {
        assertEquals("사각형", dockShapeLabel(Tool.RECT))
        assertEquals("화살표", dockShapeLabel(Tool.ARROW))
        assertEquals("직선", dockShapeLabel(Tool.LINE))
        assertEquals("타원", dockShapeLabel(Tool.ELLIPSE))
        assertEquals("도형", dockShapeLabel(Tool.PEN))
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
