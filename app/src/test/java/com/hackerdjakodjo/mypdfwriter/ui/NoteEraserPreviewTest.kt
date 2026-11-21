package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure coverage for the eraser preview geometry on mixed-orientation pages. */
class NoteEraserPreviewTest {

    @Test
    fun eraserPreviewUsesSharedRadiusOnBothAxes() {
        val pageWidth = 595f
        val pageHeight = 842f // portrait page: the old W-based circle was wrong here
        val center = NormalizedPoint(0.5f, 0.25f)

        val oval = noteEraserPreviewOval(center, pageWidth, pageHeight)

        val centerX = center.x * pageWidth
        val centerY = center.y * pageHeight
        val radiusX = NOTE_ERASE_RADIUS * pageWidth
        val radiusY = NOTE_ERASE_RADIUS * pageHeight
        assertEquals(centerX - radiusX, oval.left, 1e-5f)
        assertEquals(centerX + radiusX, oval.right, 1e-5f)
        assertEquals(centerY - radiusY, oval.top, 1e-5f)
        assertEquals(centerY + radiusY, oval.bottom, 1e-5f)
        assertTrue(
            "portrait preview must be an ellipse, not a W-based circle: $oval",
            oval.bottom - oval.top > oval.right - oval.left,
        )
    }

    @Test
    fun eraserPreviewRadiusMatchesCommittedEraserConstant() {
        val pageWidth = 842f
        val pageHeight = 595f // landscape page
        val center = NormalizedPoint(0.25f, 0.5f)

        val oval = noteEraserPreviewOval(center, pageWidth, pageHeight)

        // Subtracting two large page-space bounds loses a little float precision.
        assertEquals(NOTE_ERASE_RADIUS * 2f * pageWidth, oval.right - oval.left, 1e-3f)
        assertEquals(NOTE_ERASE_RADIUS * 2f * pageHeight, oval.bottom - oval.top, 1e-3f)
        assertEquals(0.03f, NOTE_ERASE_RADIUS, 0f)
    }
}
