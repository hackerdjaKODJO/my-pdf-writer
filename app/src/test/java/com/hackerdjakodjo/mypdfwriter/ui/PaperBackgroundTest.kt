package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperBackgroundTest {

    @Test
    fun everyTemplateUsesTheA4AspectRatio() {
        PaperTemplate.entries.forEach { template ->
            assertEquals(
                PaperBackground.A4_WIDTH_PT / PaperBackground.A4_HEIGHT_PT,
                PaperBackground.pageAspect(template),
                0.00001f,
            )
        }
    }

    @Test
    fun editorPixelSizeIsA4AtTwoPointFiveX() {
        assertEquals(1487.5f, PaperBackground.pageWidthPx(2.5f), 0.01f)
        assertEquals(2105f, PaperBackground.pageHeightPx(2.5f), 0.01f)
    }

    @Test
    fun exporterPixelSizeIsA4AtTwoX() {
        assertEquals(1190, com.hackerdjakodjo.mypdfwriter.pdf.NoteExporter.renderWidthPx(2f))
        assertEquals(1684, com.hackerdjakodjo.mypdfwriter.pdf.NoteExporter.renderHeightPx(2f))
    }

    @Test
    fun allEditorToolsMapToPainterTypes() {
        val tools = listOf(
            Tool.PEN,
            Tool.HIGHLIGHT,
            Tool.TEXT,
            Tool.RECT,
            Tool.ARROW,
            Tool.LINE,
            Tool.ELLIPSE,
        )
        tools.forEach { tool ->
            assertTrue("$tool should map to an annotation type", noteAnnotationTypeForTool(tool) != null)
        }
        assertNull(noteAnnotationTypeForTool(Tool.ERASER))
        assertNull(noteAnnotationTypeForTool(Tool.VIEW))
    }

    @Test
    fun highlighterKeepsAMinimumWidth() {
        assertEquals(0.018f, noteStrokeWidth(AnnotationType.HIGHLIGHT, 0.002f), 0.00001f)
        assertEquals(0.021f, noteStrokeWidth(AnnotationType.HIGHLIGHT, 0.021f), 0.00001f)
        assertEquals(0.004f, noteStrokeWidth(AnnotationType.PEN, 0.004f), 0.00001f)
    }
}
