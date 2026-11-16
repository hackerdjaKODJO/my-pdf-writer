package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteRotationTest {

    private fun stroke(vararg points: Triple<Float, Float, Float>) = Annotation(
        id = "stroke",
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1F4E79.toInt(),
        width = 0.004f,
        points = points.map { NormalizedPoint(it.first, it.second, it.third) },
        fontSize = 0.02f,
        penStyle = PenStyle.FOUNTAIN,
        layerId = "layer-1",
        createdAt = 1234L,
        durationMs = 567L,
    )

    @Test
    fun clockwiseSwapsA4AndRotatesPointsAboutThePageCentre() {
        val page = NotePage(
            id = "p1",
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(
                stroke(
                    Triple(0.2f, 0.3f, 0.25f),
                    Triple(0.4f, 0.5f, 0.85f),
                ),
            ),
        )

        val rotated = NotePaper.rotatePage(page, clockwise = true)
        val points = rotated.annotations.single().points

        assertEquals(PageOrientation.LANDSCAPE, rotated.orientation)
        assertEquals(1f - 0.3f, points[0].x, 0.000001f)
        assertEquals(0.2f, points[0].y, 0.000001f)
        assertEquals(1f - 0.5f, points[1].x, 0.000001f)
        assertEquals(0.4f, points[1].y, 0.000001f)
        assertEquals(listOf(0.25f, 0.85f), points.map { it.pressure })
        assertEquals(1, rotated.annotations.size)
    }

    @Test
    fun counterClockwiseUsesTheOppositeMapping() {
        val page = NotePage(orientation = PageOrientation.PORTRAIT)

        val rotated = NotePaper.rotatePage(page, clockwise = false)

        assertEquals(PageOrientation.LANDSCAPE, rotated.orientation)
    }

    @Test
    fun counterClockwiseMapsPointsAndSwapsBack() {
        val page = NotePage(
            orientation = PageOrientation.LANDSCAPE,
            annotations = listOf(stroke(Triple(0.2f, 0.3f, 1f))),
        )

        val rotated = NotePaper.rotatePage(page, clockwise = false)
        val point = rotated.annotations.single().points.single()

        assertEquals(PageOrientation.PORTRAIT, rotated.orientation)
        assertEquals(0.3f, point.x, 0.000001f)
        assertEquals(1f - 0.2f, point.y, 0.000001f)
    }

    @Test
    fun rotatingClockwiseThenCounterClockwiseIsTheIdentity() {
        val original = NotePage(
            id = "round-trip",
            template = PaperTemplate.GRID,
            orientation = PageOrientation.PORTRAIT,
            tint = PaperTint.PALE_YELLOW,
            customBackground = "tpl-8e5a.png",
            customBackgroundName = "내 사진",
            memo = "메모",
            sourcePageIndex = 7,
            layers = listOf(NoteLayer(id = "layer-1", name = "필기", opacity = 0.7f)),
            annotations = listOf(
                stroke(
                    Triple(0.05f, 0.15f, 0.2f),
                    Triple(0.95f, 0.85f, 0.9f),
                ),
                Annotation(
                    id = "text",
                    type = AnnotationType.TEXT,
                    page = 0,
                    color = 0,
                    width = 0.004f,
                    points = listOf(NormalizedPoint(0.3f, 0.4f)),
                    text = "회전",
                    fontSize = 0.02f,
                    textRotation = 30,
                    layerId = "layer-1",
                ),
            ),
        )

        val roundTrip = NotePaper.rotatePage(
            NotePaper.rotatePage(original, clockwise = true),
            clockwise = false,
        )

        assertEquals(original.id, roundTrip.id)
        assertEquals(original.template, roundTrip.template)
        assertEquals(original.orientation, roundTrip.orientation)
        assertEquals(original.tint, roundTrip.tint)
        assertEquals(original.customBackground, roundTrip.customBackground)
        assertEquals(original.customBackgroundName, roundTrip.customBackgroundName)
        assertEquals(original.memo, roundTrip.memo)
        assertEquals(original.sourcePageIndex, roundTrip.sourcePageIndex)
        assertEquals(original.layers, roundTrip.layers)
        original.annotations.zip(roundTrip.annotations).forEach { (before, after) ->
            assertEquals(before.id, after.id)
            assertEquals(before.points.size, after.points.size)
            before.points.zip(after.points).forEach { (a, b) ->
                assertEquals(a.x, b.x, 0.000001f)
                assertEquals(a.y, b.y, 0.000001f)
                assertEquals(a.pressure, b.pressure, 0f)
            }
            assertEquals(before.width, after.width, 0.000001f)
            assertEquals(before.fontSize, after.fontSize, 0.000001f)
            assertEquals(before.textRotation, after.textRotation)
        }
    }

    @Test
    fun textRotationFollowsTheSheetAndNormalizesAt360() {
        val text = Annotation(
            type = AnnotationType.TEXT,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.2f, 0.3f)),
            text = "label",
            textRotation = 270,
        )
        val page = NotePage(annotations = listOf(text))

        val clockwise = NotePaper.rotatePage(page, clockwise = true).annotations.single()
        val counter = NotePaper.rotatePage(page, clockwise = false).annotations.single()

        assertEquals(0, clockwise.textRotation)
        assertEquals(180, counter.textRotation)
    }

    @Test
    fun physicalStrokeWidthFontSizeAndPressureArePreserved() {
        val page = NotePage(
            orientation = PageOrientation.PORTRAIT,
            annotations = listOf(
                stroke(Triple(0.4f, 0.6f, 0.35f)),
            ),
        )

        val landscape = NotePaper.rotatePage(page, clockwise = true)
        val annotation = landscape.annotations.single()

        assertEquals(0.004f * NotePaper.PORTRAIT_WIDTH_PT,
            annotation.width * NotePaper.LANDSCAPE_WIDTH_PT, 0.00001f)
        assertEquals(0.02f * NotePaper.PORTRAIT_HEIGHT_PT,
            annotation.fontSize * NotePaper.LANDSCAPE_HEIGHT_PT, 0.0001f)
        assertEquals(0.35f, annotation.points.single().pressure, 0f)
        assertEquals("stroke", annotation.id)
        assertEquals("layer-1", annotation.layerId)
        assertEquals(1234L, annotation.createdAt)
        assertEquals(567L, annotation.durationMs)
        assertEquals(PenStyle.FOUNTAIN, annotation.penStyle)
    }

    @Test
    fun customFieldsLayersBookmarksAndPageIdentitySurvive() {
        val layer = NoteLayer(id = "layer-1", name = "레이어", visible = false, opacity = 0.4f)
        val page = NotePage(
            id = "kept-page",
            template = PaperTemplate.CORNELL,
            orientation = PageOrientation.PORTRAIT,
            tint = PaperTint.LIGHT_GRAY,
            customBackground = "tpl-photo.png",
            customBackgroundName = "사진 배경",
            memo = "페이지 메모",
            sourcePageIndex = 12,
            layers = listOf(layer),
            annotations = listOf(
                Annotation(
                    id = "stamp",
                    type = AnnotationType.STAMP,
                    page = 0,
                    color = 0,
                    width = 0.004f,
                    points = listOf(NormalizedPoint(0.5f, 0.5f)),
                    stamp = "DRAFT",
                    fontSize = 0.03f,
                    textRotation = 45,
                    opacity = 0.65f,
                    layerId = "layer-1",
                ),
            ),
        )

        val rotated = NotePaper.rotatePage(page, clockwise = true)
        val stamp = rotated.annotations.single()

        assertEquals("kept-page", rotated.id)
        assertEquals(PaperTemplate.CORNELL, rotated.template)
        assertEquals(PaperTint.LIGHT_GRAY, rotated.tint)
        assertEquals("tpl-photo.png", rotated.customBackground)
        assertEquals("사진 배경", rotated.customBackgroundName)
        assertEquals("페이지 메모", rotated.memo)
        assertEquals(12, rotated.sourcePageIndex)
        assertSame(layer, rotated.layers.single())
        assertEquals("DRAFT", stamp.stamp)
        assertEquals(0.65f, stamp.opacity, 0f)
        assertEquals(135, stamp.textRotation)
        assertEquals(PageOrientation.LANDSCAPE, rotated.orientation)
    }

    @Test
    fun emptyPageStillSwapsOrientationAndKeepsPaperSettings() {
        val page = NotePage(
            id = "empty",
            template = PaperTemplate.DOTTED,
            orientation = PageOrientation.LANDSCAPE,
            tint = PaperTint.WHITE,
        )

        val rotated = NotePaper.rotatePage(page, clockwise = true)

        assertEquals(PageOrientation.PORTRAIT, rotated.orientation)
        assertEquals(PaperTemplate.DOTTED, rotated.template)
        assertEquals(PaperTint.WHITE, rotated.tint)
        assertTrue(rotated.annotations.isEmpty())
        assertFalse(NotePaper.hasInk(rotated))
    }
}
