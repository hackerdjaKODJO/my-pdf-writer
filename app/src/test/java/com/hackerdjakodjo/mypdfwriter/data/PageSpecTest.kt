package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PageSpecTest {

    @Test
    fun normalizeRotation_wrapsEveryDirection() {
        assertEquals(0, PageSpec.normalizeRotation(0))
        assertEquals(90, PageSpec.normalizeRotation(90))
        assertEquals(180, PageSpec.normalizeRotation(-180))
        assertEquals(270, PageSpec.normalizeRotation(-90))
        assertEquals(0, PageSpec.normalizeRotation(360))
        assertEquals(90, PageSpec.normalizeRotation(450))
        assertEquals(270, PageSpec.normalizeRotation(-450))
    }

    @Test
    fun pageSpec_roundTripsNullSourcePage() {
        val spec = PageSpec(sourcePage = null, extraRotation = 270)
        val json = spec.toJson()

        assertEquals(PageSpec(null, 270), PageSpec.fromJson(json))
    }
}
