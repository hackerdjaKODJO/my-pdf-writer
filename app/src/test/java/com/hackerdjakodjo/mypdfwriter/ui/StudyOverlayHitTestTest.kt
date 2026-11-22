package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyOverlayHitTestTest {

    @Test
    fun `a study card rectangle follows pan and zoom`() {
        fun rect(pan: Offset, fit: Float) = studyOverlayScreenRect(
            x = 0.2f, y = 0.3f, w = 0.4f, h = 0.2f,
            fit = fit, pan = pan,
            viewWidth = 1000f, viewHeight = 1000f,
            pageWidth = 1000f, pageHeight = 1000f,
        )
        val base = rect(Offset.Zero, 1f)
        assertTrue(base.contains(Offset(400f, 400f)))
        assertFalse(base.contains(Offset(100f, 100f)))

        val zoomed = rect(Offset.Zero, 2f)
        assertTrue(zoomed.contains(Offset(400f, 300f)))
        assertFalse(zoomed.contains(Offset(800f, 800f)))

        val panned = rect(Offset(100f, -100f), 1f)
        assertTrue(panned.contains(Offset(500f, 250f)))
        assertFalse(base.contains(Offset(500f, 200f)))
    }
}
