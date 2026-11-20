package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerToolDockTest {

    @Test
    fun dockHasExactlyTheSixPrimarySlots() {
        assertEquals(
            listOf(
                ViewerDockSlot.VIEW,
                ViewerDockSlot.PEN,
                ViewerDockSlot.HIGHLIGHT,
                ViewerDockSlot.ERASER,
                ViewerDockSlot.TEXT_SELECT,
                ViewerDockSlot.ANNOTATION,
            ),
            viewerDockSlots,
        )
        assertEquals(6, viewerDockSlots.size)
        assertFalse(ViewerDockSlot.entries.any { it.name == "SHAPE" })
        assertFalse(ViewerDockSlot.entries.any { it.name == "TEXT_BOX" })
    }

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
    fun annotationSlotShowsTheActiveExtraToolName() {
        assertEquals("글상자", dockAnnotationLabel(Tool.TEXT))
        assertEquals("사각형", dockAnnotationLabel(Tool.RECT))
        assertEquals("화살표", dockAnnotationLabel(Tool.ARROW))
        assertEquals("직선", dockAnnotationLabel(Tool.LINE))
        assertEquals("타원", dockAnnotationLabel(Tool.ELLIPSE))
        assertEquals("밑줄", dockAnnotationLabel(Tool.UNDERLINE))
        assertEquals("취소선", dockAnnotationLabel(Tool.STRIKEOUT))
        assertEquals("스탬프", dockAnnotationLabel(Tool.STAMP))
        assertEquals("서명", dockAnnotationLabel(Tool.SIGNATURE))
        assertEquals("가림", dockAnnotationLabel(Tool.REDACT))
        assertEquals("주석", dockAnnotationLabel(Tool.PEN))
        assertEquals("주석", dockAnnotationLabel(Tool.VIEW))
    }

    @Test
    fun everyPaletteSwatchHasASpokenName() {
        assertEquals("검정", annotationColorName(annotationPalette[0]))
        assertEquals("빨강", annotationColorName(annotationPalette[1]))
        assertEquals("파랑", annotationColorName(annotationPalette[2]))
        assertEquals("초록", annotationColorName(annotationPalette[3]))
        assertEquals("주황", annotationColorName(annotationPalette[4]))
        assertEquals("보라", annotationColorName(annotationPalette[5]))
        assertEquals("청록", annotationColorName(annotationPalette[6]))
        assertEquals("노랑", annotationColorName(annotationPalette[7]))
        assertEquals("사용자 색상", annotationColorName(0xFF123456.toInt()))
    }

    @Test
    fun dockAndSwatchDescriptionsAnnounceState() {
        assertEquals("펜", stateLabel("펜", selected = false))
        assertEquals("펜, 선택됨", stateLabel("펜", selected = true))
        assertEquals("빨강", colorChoiceLabel("빨강", selected = false))
        assertEquals("빨강, 선택됨", colorChoiceLabel("빨강", selected = true))
    }

    @Test
    fun annotationToolsMatchTheAnnotationSheetEntries() {
        listOf(
            Tool.TEXT,
            Tool.RECT,
            Tool.ARROW,
            Tool.LINE,
            Tool.ELLIPSE,
            Tool.UNDERLINE,
            Tool.STRIKEOUT,
            Tool.STAMP,
            Tool.SIGNATURE,
            Tool.REDACT,
        ).forEach { tool ->
            assertTrue("$tool should live in the annotation sheet", isDockAnnotationTool(tool))
        }
        assertFalse(isDockAnnotationTool(Tool.PEN))
        assertFalse(isDockAnnotationTool(Tool.HIGHLIGHT))
        assertFalse(isDockAnnotationTool(Tool.ERASER))
        assertFalse(isDockAnnotationTool(Tool.VIEW))
    }
}
