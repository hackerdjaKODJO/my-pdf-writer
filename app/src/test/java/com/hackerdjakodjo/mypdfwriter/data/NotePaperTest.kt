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

    private fun timedStroke() = Annotation(
        id = "timed-stroke",
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = listOf(
            NormalizedPoint(x = 0.2f, y = 0.3f, pressure = 0.15f, timeMs = 0L),
            NormalizedPoint(x = 0.4f, y = 0.5f, pressure = 0.95f, timeMs = 75L),
            NormalizedPoint(x = 0.7f, y = 0.8f, pressure = 0.35f, timeMs = 240L),
        ),
        fontSize = 0.02f,
        textRotation = 15,
        durationMs = 240L,
        timesMs = listOf(0L, 75L, 240L),
    )

    private fun assertTimingAndPressureSurvive(original: Annotation, transformed: Annotation) {
        assertEquals(original.durationMs, transformed.durationMs)
        assertEquals(original.timesMs, transformed.timesMs)
        assertEquals(original.points.map { it.timeMs }, transformed.points.map { it.timeMs })
        assertEquals(original.points.map { it.pressure }, transformed.points.map { it.pressure })
        assertEquals(original.points.minOf { it.pressure }, transformed.points.minOf { it.pressure }, 0f)
        assertEquals(original.points.maxOf { it.pressure }, transformed.points.maxOf { it.pressure }, 0f)
    }

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
    fun reorientPreservesPointTimestampsPressureExtremaAndAnnotationTimes() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(timedStroke()),
        )
        val original = page.annotations.single()

        val landscape = NotePaper.reorient(page, PageOrientation.LANDSCAPE)
        val transformed = landscape.annotations.single()

        assertTimingAndPressureSurvive(original, transformed)
        assertEquals(original.textRotation, transformed.textRotation)
        original.points.zipWithNext().forEachIndexed { index, (from, to) ->
            val movedFrom = transformed.points[index]
            val movedTo = transformed.points[index + 1]
            assertEquals((to.x - from.x) * 595f, (movedTo.x - movedFrom.x) * 842f, 0.01f)
            assertEquals((to.y - from.y) * 842f, (movedTo.y - movedFrom.y) * 595f, 0.01f)
        }
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
    fun rotatePagePreservesPointTimestampsPressureExtremaAndAnnotationTimes() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(timedStroke()),
        )
        val original = page.annotations.single()

        listOf(true, false).forEach { clockwise ->
            val rotated = NotePaper.rotatePage(page, clockwise = clockwise)
            val transformed = rotated.annotations.single()

            assertTimingAndPressureSurvive(original, transformed)
            transformed.points.forEachIndexed { index, point ->
                val source = original.points[index]
                if (clockwise) {
                    assertEquals(1f - source.y, point.x, 0.000001f)
                    assertEquals(source.x, point.y, 0.000001f)
                } else {
                    assertEquals(source.y, point.x, 0.000001f)
                    assertEquals(1f - source.x, point.y, 0.000001f)
                }
            }
            assertEquals(
                PageSpec.normalizeRotation(original.textRotation + if (clockwise) 90 else 270),
                transformed.textRotation,
            )
        }
    }

    @Test
    fun rotatePageClockwiseThenCounterClockwiseRestoresGeometryAndTiming() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(timedStroke()),
        )
        val original = page.annotations.single()

        val restored = NotePaper.rotatePage(
            NotePaper.rotatePage(page, clockwise = true),
            clockwise = false,
        )
        val roundTrip = restored.annotations.single()

        assertEquals(PageOrientation.PORTRAIT, restored.orientation)
        assertTimingAndPressureSurvive(original, roundTrip)
        roundTrip.points.forEachIndexed { index, point ->
            assertEquals(original.points[index].x, point.x, 0.000001f)
            assertEquals(original.points[index].y, point.y, 0.000001f)
        }
        assertEquals(original.width, roundTrip.width, 0.000001f)
        assertEquals(original.fontSize, roundTrip.fontSize, 0.000001f)
        assertEquals(original.textRotation, roundTrip.textRotation)
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
