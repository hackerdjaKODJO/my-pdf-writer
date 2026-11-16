package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTemplateGeometryTest {

    private val portraitWidth = NotePaper.PORTRAIT_WIDTH_PT
    private val portraitHeight = NotePaper.PORTRAIT_HEIGHT_PT

    @Test
    fun wideImage_onPortraitPage_fillsWidthAndCropsTopBottom() {
        val placement = CustomTemplateGeometry.cover(
            imageWidth = 1000,
            imageHeight = 500,
            pageWidth = portraitWidth,
            pageHeight = portraitHeight,
        )!!

        assertEquals(portraitHeight, placement.height, 0.01f)
        assertTrue(placement.width > portraitWidth)
        assertTrue(placement.left < 0f)
        assertEquals(0f, placement.top, 0.01f)
        assertEquals(portraitWidth, placement.width + placement.left * 2f, 0.01f)
    }

    @Test
    fun tallImage_onLandscapePage_fillsHeightAndCropsSides() {
        val placement = CustomTemplateGeometry.cover(
            imageWidth = 500,
            imageHeight = 1000,
            pageWidth = NotePaper.LANDSCAPE_WIDTH_PT,
            pageHeight = NotePaper.LANDSCAPE_HEIGHT_PT,
        )!!

        assertEquals(NotePaper.LANDSCAPE_WIDTH_PT, placement.width, 0.01f)
        assertTrue(placement.height > NotePaper.LANDSCAPE_HEIGHT_PT)
        assertTrue(placement.top < 0f)
        assertEquals(0f, placement.left, 0.01f)
        assertEquals(
            NotePaper.LANDSCAPE_HEIGHT_PT,
            placement.height + placement.top * 2f,
            0.01f,
        )
    }

    @Test
    fun sameAspectImage_mapsExactlyOntoThePage() {
        val placement = CustomTemplateGeometry.cover(
            imageWidth = 595,
            imageHeight = 842,
            pageWidth = portraitWidth,
            pageHeight = portraitHeight,
        )!!

        assertEquals(0f, placement.left, 0.001f)
        assertEquals(0f, placement.top, 0.001f)
        assertEquals(portraitWidth, placement.width, 0.001f)
        assertEquals(portraitHeight, placement.height, 0.001f)
    }

    @Test
    fun coverAlwaysCoversBothPageDimensions() {
        val samples = listOf(
            800 to 600,
            600 to 800,
            1200 to 1200,
            200 to 3000,
            3000 to 200,
        )
        samples.forEach { (imageWidth, imageHeight) ->
            val placement = CustomTemplateGeometry.cover(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                pageWidth = NotePaper.LANDSCAPE_WIDTH_PT,
                pageHeight = NotePaper.LANDSCAPE_HEIGHT_PT,
            )!!
            assertTrue(placement.left <= 0.001f)
            assertTrue(placement.top <= 0.001f)
            assertTrue(placement.left + placement.width >= NotePaper.LANDSCAPE_WIDTH_PT - 0.001f)
            assertTrue(placement.top + placement.height >= NotePaper.LANDSCAPE_HEIGHT_PT - 0.001f)
        }
    }

    @Test
    fun invalidDimensions_returnNullInsteadOfBreakingThePage() {
        assertNull(CustomTemplateGeometry.cover(0, 100, 595f, 842f))
        assertNull(CustomTemplateGeometry.cover(100, -1, 595f, 842f))
        assertNull(CustomTemplateGeometry.cover(100, 100, 0f, 842f))
        assertNull(CustomTemplateGeometry.cover(100, 100, Float.NaN, 842f))
        assertNull(CustomTemplateGeometry.cover(100, 100, 595f, Float.POSITIVE_INFINITY))
    }
}
