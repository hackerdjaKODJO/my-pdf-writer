package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotePaperTest {

    private fun stroke(vararg points: Pair<Float, Float>) = Annotation(
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = points.map { NormalizedPoint(it.first, it.second) },
        fontSize = 0.02f,
    )

    @Test
    fun portraitAndLandscapeUseTheSamePhysicalA4Sheet() {
        assertEquals(595f, NotePaper.widthPt(PageOrientation.PORTRAIT), 0.001f)
        assertEquals(842f, NotePaper.heightPt(PageOrientation.PORTRAIT), 0.001f)
        assertEquals(842f, NotePaper.widthPt(PageOrientation.LANDSCAPE), 0.001f)
        assertEquals(595f, NotePaper.heightPt(PageOrientation.LANDSCAPE), 0.001f)
        assertEquals(595f / 842f, NotePaper.aspect(PageOrientation.PORTRAIT), 0.00001f)
        assertEquals(842f / 595f, NotePaper.aspect(PageOrientation.LANDSCAPE), 0.00001f)
    }

    @Test
    fun reorientKeepsPhysicalGeometryAndStrokeMetrics() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(stroke(0.2f to 0.3f, 0.4f to 0.5f)),
        )

        val landscape = NotePaper.reorient(page, PageOrientation.LANDSCAPE)
        val annotation = landscape.annotations.single()

        assertEquals(PageOrientation.LANDSCAPE, landscape.orientation)
        assertEquals(0.2f * 595f, annotation.points[0].x * 842f, 0.01f)
        assertEquals(0.3f * 842f, annotation.points[0].y * 595f, 0.01f)
        assertEquals(0.4f * 595f, annotation.points[1].x * 842f, 0.01f)
        assertEquals(0.5f * 842f, annotation.points[1].y * 595f, 0.01f)
        assertEquals(0.004f * 595f, annotation.width * 842f, 0.0001f)
        assertEquals(0.02f * 842f, annotation.fontSize * 595f, 0.001f)
        assertFalse(NotePaper.wouldCrop(page, PageOrientation.LANDSCAPE))

        val restored = NotePaper.reorient(landscape, PageOrientation.PORTRAIT)
        val original = page.annotations.single()
        val roundTrip = restored.annotations.single()
        original.points.forEachIndexed { index, point ->
            assertEquals(point.x, roundTrip.points[index].x, 0.000001f)
            assertEquals(point.y, roundTrip.points[index].y, 0.000001f)
        }
        assertEquals(original.width, roundTrip.width, 0.000001f)
    }

    @Test
    fun fullPageInkWarnsThatLandscapeWillCrop() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(stroke(0.05f to 0.05f, 0.95f to 0.95f)),
        )

        assertTrue(NotePaper.wouldCrop(page, PageOrientation.LANDSCAPE))
        val landscape = NotePaper.reorient(page, PageOrientation.LANDSCAPE)
        val points = landscape.annotations.single().points
        assertTrue(
            "portrait content taller than a landscape sheet stays outside the page",
            points.any { it.y < 0f || it.y > 1f },
        )
    }

    @Test
    fun landscapeToPortraitWarnsOnlyWhenWidthNoLongerFits() {
        val wide = NotePage(
            orientation = PageOrientation.LANDSCAPE,
            annotations = listOf(stroke(0.05f to 0.4f, 0.95f to 0.6f)),
        )
        val narrow = NotePage(
            orientation = PageOrientation.LANDSCAPE,
            annotations = listOf(stroke(0.4f to 0.2f, 0.6f to 0.4f)),
        )

        assertTrue(NotePaper.wouldCrop(wide, PageOrientation.PORTRAIT))
        assertFalse(NotePaper.wouldCrop(narrow, PageOrientation.PORTRAIT))
    }

    @Test
    fun emptyPageChangesOrientationWithoutTouchingAnythingElse() {
        val page = NotePage(id = "p1", template = PaperTemplate.GRID)

        val landscape = NotePaper.reorient(page, PageOrientation.LANDSCAPE)

        assertEquals("p1", landscape.id)
        assertEquals(PaperTemplate.GRID, landscape.template)
        assertEquals(PageOrientation.LANDSCAPE, landscape.orientation)
        assertTrue(landscape.annotations.isEmpty())
    }

    @Test
    fun fitTranslationKeepsInkInPlaceThenClampsIntoTheSheet() {
        assertEquals(0f, NotePaper.fitTranslation(100f, 300f, 595f), 0.001f)
        assertEquals(-105f, NotePaper.fitTranslation(550f, 700f, 595f), 0.001f)
        assertEquals(40f, NotePaper.fitTranslation(-40f, 100f, 595f), 0.001f)
        assertEquals(-102.5f, NotePaper.fitTranslation(0f, 800f, 595f), 0.001f)
    }
}
