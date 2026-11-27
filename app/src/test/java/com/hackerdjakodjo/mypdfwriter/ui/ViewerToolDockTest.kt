package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerToolDockTest {

    @Test
    fun dockHasExactlyTheSixStudySlotsInOrder() {
        assertEquals(
            listOf(
                ViewerDockSlot.VIEW,
                ViewerDockSlot.HIGHLIGHT,
                ViewerDockSlot.PEN,
                ViewerDockSlot.ERASER,
                ViewerDockSlot.TEXT_SELECT,
                ViewerDockSlot.NOTE,
            ),
            viewerDockSlots,
        )
        assertEquals(6, viewerDockSlots.size)
        assertFalse(ViewerDockSlot.entries.any { it.name == "SHAPE" })
        assertFalse(ViewerDockSlot.entries.any { it.name == "TEXT_BOX" })
        assertFalse(ViewerDockSlot.entries.any { it.name == "ANNOTATION" })
    }

    @Test
    fun dockLabelsMatchTheStudySlotOrder() {
        assertEquals(
            listOf("보기", "형광펜", "펜", "지우개", "텍스트 선택", "노트"),
            viewerDockSlots.map { viewerDockSlotLabel(it) },
        )
        // Contextual actions live above the dock, never as a seventh slot.
        assertFalse(viewerDockSlots.map { viewerDockSlotLabel(it) }.any { it == "스타일" })
    }

    @Test
    fun shapeToolsMatchTheCanvasShapeGestures() {
        assertEquals(
            setOf(Tool.RECT, Tool.TRIANGLE, Tool.ELLIPSE, Tool.LINE, Tool.ARROW),
            viewerShapeTools,
        )
        assertTrue(isShapeTool(Tool.RECT))
        assertTrue(isShapeTool(Tool.TRIANGLE))
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
        assertTrue(isStyleTool(Tool.TRIANGLE))
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
        assertEquals("삼각형", dockAnnotationLabel(Tool.TRIANGLE))
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
            Tool.TRIANGLE,
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

    @Test
    fun longPressTargetsAreOnlyPenAndHighlight() {
        assertEquals(
            listOf(ViewerDockSlot.HIGHLIGHT, ViewerDockSlot.PEN),
            viewerDockSlots.filter(::viewerDockLongPressOpensStyle),
        )
        assertTrue(!viewerDockLongPressOpensStyle(ViewerDockSlot.VIEW))
        assertTrue(!viewerDockLongPressOpensStyle(ViewerDockSlot.ERASER))
    }

    @Test
    fun contextualActionsAppearOnlyWhenTheyApply() {
        assertTrue(viewerContextActions(Tool.VIEW, canUndo = false, canRedo = false).isEmpty())
        assertEquals(
            listOf(ViewerContextAction.STYLE),
            viewerContextActions(Tool.PEN, canUndo = false, canRedo = false),
        )
        assertEquals(
            listOf(ViewerContextAction.STYLE),
            viewerContextActions(Tool.HIGHLIGHT, canUndo = false, canRedo = false),
        )
        assertEquals(
            listOf(ViewerContextAction.UNDO, ViewerContextAction.REDO),
            viewerContextActions(Tool.VIEW, canUndo = true, canRedo = true),
        )
        assertEquals(
            listOf(ViewerContextAction.STYLE, ViewerContextAction.UNDO, ViewerContextAction.REDO),
            viewerContextActions(Tool.PEN, canUndo = true, canRedo = true),
        )
        assertEquals(
            listOf("스타일", "실행 취소", "다시 실행"),
            viewerContextActions(Tool.PEN, canUndo = true, canRedo = true).map { it.label },
        )
    }

    @Test
    fun contextualActionTagsAreStableForAccessibilityTests() {
        assertEquals("pdf_style_action", ViewerContextAction.STYLE.testTag)
        assertEquals("pdf_undo_action", ViewerContextAction.UNDO.testTag)
        assertEquals("pdf_redo_action", ViewerContextAction.REDO.testTag)
    }

    @Test
    fun pagePillAnnouncesPositionAndAction() {
        assertEquals("페이지 3 / 12, 목록 열기", viewerPagePillDescription(2, 12))
        assertEquals("페이지 1 / 1, 목록 열기", viewerPagePillDescription(0, 1))
    }
}
