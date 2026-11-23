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

    @Test
    fun textOnlyResultsHaveNoViewerOpenAction() {
        org.junit.Assert.assertFalse(hasMeaningfulText("--- 1 / 1 ---\n"))
        org.junit.Assert.assertFalse(
            hasMeaningfulText("--- 1 / 2 ---\n\n--- 2 / 2 ---\n"),
        )
        org.junit.Assert.assertTrue(hasMeaningfulText("--- 1 / 1 ---\nPAGE 1"))
        org.junit.Assert.assertFalse(hasMeaningfulText(""))
    }

    @Test
    fun onlyPasswordLoadFailuresNeedUnlocking() {
        org.junit.Assert.assertTrue(needsPasswordFromError("암호가 필요합니다"))
        org.junit.Assert.assertFalse(needsPasswordFromError("파일을 읽을 수 없습니다"))
        org.junit.Assert.assertFalse(needsPasswordFromError(null))
    }
}
