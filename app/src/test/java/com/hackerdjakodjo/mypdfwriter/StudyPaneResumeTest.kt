package com.hackerdjakodjo.mypdfwriter

import org.junit.Assert.assertEquals
import org.junit.Test

/** A capture into an open pane must not snap the pane back to page 1. */
class StudyPaneResumeTest {

    @Test
    fun `same notebook keeps the captured page and a new one starts at zero`() {
        assertEquals(3, studyPanePageIndexAfterOpen("n1", "n1", 3))
        assertEquals(0, studyPanePageIndexAfterOpen(null, "n1", 0))
        assertEquals(0, studyPanePageIndexAfterOpen("n1", "n2", 3))
        assertEquals(0, studyPanePageIndexAfterOpen("n1", "n1", -2))
    }
}
