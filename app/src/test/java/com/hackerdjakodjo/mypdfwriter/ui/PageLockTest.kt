package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure coverage for the page-lock navigation gate used by PdfViewModel. */
class PageLockTest {

    @Test
    fun unlockedViewerAllowsEveryNavigation() {
        assertTrue(PageLockGate.allowsNavigation(pageLocked = false))
        assertTrue(
            PageLockGate.allowsNavigation(
                pageLocked = false,
                explicitSourceJump = true,
            ),
        )
    }

    @Test
    fun lockedViewerBlocksOrdinaryNavigation() {
        assertFalse(PageLockGate.allowsNavigation(pageLocked = true))
    }

    @Test
    fun lockedViewerStillAllowsStudySourceJumps() {
        assertTrue(
            PageLockGate.allowsNavigation(
                pageLocked = true,
                explicitSourceJump = true,
            ),
        )
    }
}
