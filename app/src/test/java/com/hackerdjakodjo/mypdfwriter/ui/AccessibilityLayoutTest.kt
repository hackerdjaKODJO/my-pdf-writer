package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityLayoutTest {

    @Test
    fun narrowBoundaryIsAtThreeSixtyDp() {
        val narrow = chromeScale(359, 1f)
        val wide = chromeScale(360, 1f)

        assertTrue(narrow.showSubtitle)
        assertTrue(narrow.showDockLabels)
        assertEquals(1, narrow.titleMaxLines)
        assertEquals(2, narrow.inlineActionCount)
        assertEquals(2, narrow.quickActionColumns)
        assertEquals(48, narrow.minRowHeightDp)

        assertTrue(wide.showSubtitle)
        assertTrue(wide.showDockLabels)
        assertEquals(1, wide.titleMaxLines)
        assertEquals(3, wide.inlineActionCount)
        assertEquals(4, wide.quickActionColumns)
        assertEquals(48, wide.minRowHeightDp)
    }

    @Test
    fun widthsBelowThreeHundredTwentyAreClamped() {
        assertEquals(chromeScale(320, 1f), chromeScale(0, 1f))
        assertEquals(chromeScale(320, 1f), chromeScale(-100, 1f))
        assertEquals(chromeScale(320, 2f), chromeScale(319, 2f))
    }

    @Test
    fun largeFontBoundaryIsAtOnePointFive() {
        val below = chromeScale(400, 1.49f)
        val at = chromeScale(400, 1.5f)

        assertTrue(below.showSubtitle)
        assertTrue(below.showDockLabels)
        assertEquals(1, below.titleMaxLines)
        assertEquals(3, below.inlineActionCount)
        assertEquals(4, below.quickActionColumns)
        assertEquals(48, below.minRowHeightDp)

        assertFalse(at.showSubtitle)
        assertFalse(at.showDockLabels)
        assertEquals(2, at.titleMaxLines)
        assertEquals(2, at.inlineActionCount)
        assertEquals(2, at.quickActionColumns)
        assertEquals(56, at.minRowHeightDp)
    }

    @Test
    fun hugeFontBoundaryIsAtTwo() {
        val justBelow = chromeScale(400, 1.99f)
        val hugeWide = chromeScale(400, 2f)
        val hugeNarrow = chromeScale(359, 2f)

        assertEquals(2, justBelow.inlineActionCount)
        assertEquals(2, hugeWide.inlineActionCount)
        assertFalse(hugeWide.showSubtitle)
        assertFalse(hugeWide.showDockLabels)
        assertEquals(2, hugeWide.titleMaxLines)
        assertEquals(56, hugeWide.minRowHeightDp)
        assertEquals(2, hugeWide.quickActionColumns)

        // Narrow + huge is the only case that drops to a single inline action.
        assertEquals(1, hugeNarrow.inlineActionCount)
    }

    @Test
    fun fontScaleIsClampedToOneThroughThree() {
        assertEquals(chromeScale(400, 1f), chromeScale(400, 0.5f))
        assertEquals(chromeScale(400, 1f), chromeScale(400, -2f))
        assertEquals(chromeScale(400, 2f), chromeScale(400, 3f))
        assertEquals(chromeScale(400, 2f), chromeScale(400, 10f))
        assertEquals(chromeScale(400, 1f), chromeScale(400, Float.NaN))
    }

    @Test
    fun inlineActionCountFollowsTheNarrowAndLargeMatrix() {
        // wide + normal -> 3
        assertEquals(3, chromeScale(360, 1f).inlineActionCount)
        // narrow + normal -> 2
        assertEquals(2, chromeScale(359, 1f).inlineActionCount)
        // wide + large -> 2
        assertEquals(2, chromeScale(360, 1.5f).inlineActionCount)
        // narrow + large -> 2
        assertEquals(2, chromeScale(359, 1.5f).inlineActionCount)
        // wide + huge -> 2
        assertEquals(2, chromeScale(360, 2f).inlineActionCount)
        // narrow + huge -> 1
        assertEquals(1, chromeScale(359, 2f).inlineActionCount)
    }

    @Test
    fun largeFontHidesSubtitleAndDockLabelsButAddsTitleLines() {
        val normal = chromeScale(359, 1f)
        val large = chromeScale(359, 1.5f)

        assertTrue(normal.showSubtitle)
        assertTrue(normal.showDockLabels)
        assertEquals(1, normal.titleMaxLines)

        assertFalse(large.showSubtitle)
        assertFalse(large.showDockLabels)
        assertEquals(2, large.titleMaxLines)
    }

    @Test
    fun quickActionColumnsCollapseWhenNarrowOrLarge() {
        assertEquals(4, chromeScale(360, 1.49f).quickActionColumns)
        assertEquals(2, chromeScale(359, 1f).quickActionColumns)
        assertEquals(2, chromeScale(360, 1.5f).quickActionColumns)
        assertEquals(2, chromeScale(359, 2f).quickActionColumns)
    }

    @Test
    fun rowHeightsAlwaysMeetTheMinimumTarget() {
        for (width in listOf(320, 359, 360, 480)) {
            for (scale in listOf(1f, 1.49f, 1.5f, 2f, 3f)) {
                assertTrue(chromeScale(width, scale).minRowHeightDp >= MIN_TARGET_DP)
            }
        }
    }

    @Test
    fun chromeScaleIsADataClassThatCanBeCopied() {
        val base = chromeScale(359, 2f)
        val wider = base.copy(showDockLabels = true)

        assertFalse(base.showDockLabels)
        assertTrue(wider.showDockLabels)
        assertEquals(base.titleMaxLines, wider.titleMaxLines)
        assertEquals(base.minRowHeightDp, wider.minRowHeightDp)
    }
}
