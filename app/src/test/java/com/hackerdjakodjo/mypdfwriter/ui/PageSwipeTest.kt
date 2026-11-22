package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSwipeTest {

    @Test
    fun `threshold respects both the dp floor and the viewport fraction`() {
        assertEquals(64f, PageSwipe.thresholdPx(200f, 1f), 0.0001f)
        assertEquals(180f, PageSwipe.thresholdPx(1000f, 1f), 0.0001f)
        assertEquals(128f, PageSwipe.thresholdPx(200f, 2f), 0.0001f)
    }

    @Test
    fun `direction needs slop and horizontal dominance`() {
        assertEquals(0, PageSwipe.direction(4f, 0f, 8f))
        assertEquals(0, PageSwipe.direction(30f, 80f, 8f))
        assertEquals(1, PageSwipe.direction(-120f, 10f, 8f))
        assertEquals(-1, PageSwipe.direction(120f, 10f, 8f))
        assertEquals(0, PageSwipe.direction(Float.NaN, 10f, 8f))
    }

    @Test
    fun `target clamps at document edges`() {
        assertEquals(1, PageSwipe.target(0, 1, 3))
        assertEquals(0, PageSwipe.target(1, -1, 3))
        assertEquals(2, PageSwipe.target(2, 1, 3))
        assertEquals(0, PageSwipe.target(0, -1, 3))
        assertEquals(0, PageSwipe.target(0, 1, 1))
    }
}
