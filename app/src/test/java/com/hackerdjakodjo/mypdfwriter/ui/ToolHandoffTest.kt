package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ToolHandoffTest {

    @Test
    fun `tools prefer the edited working file over the pristine source`() {
        val working = File("/tmp/working.pdf")
        val source = File("/tmp/source.pdf")
        assertEquals(working, toolHandoffFile(working, source))
        assertEquals(source, toolHandoffFile(null, source))
        assertEquals(working, toolHandoffFile(working, null))
        assertNull(toolHandoffFile(null, null))
    }

    @Test
    fun `a protected PDF enables only the unlock tool`() {
        org.junit.Assert.assertFalse(toolsEnabledFor(true, isUnlock = false))
        org.junit.Assert.assertTrue(toolsEnabledFor(true, isUnlock = true))
        org.junit.Assert.assertTrue(toolsEnabledFor(false, isUnlock = false))
        org.junit.Assert.assertTrue(toolsEnabledFor(false, isUnlock = true))
    }
}
