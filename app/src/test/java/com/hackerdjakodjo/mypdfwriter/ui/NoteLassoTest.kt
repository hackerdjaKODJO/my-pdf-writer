package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLassoTest {

    @Test
    fun freeLassoSelectsInsideStrokeAndCrossingLine() {
        val annotations = listOf(
            annotation(
                id = "inside",
                type = AnnotationType.PEN,
                points = listOf(point(0.20f, 0.20f), point(0.24f, 0.26f), point(0.28f, 0.22f)),
            ),
            annotation(
                id = "outside",
                type = AnnotationType.PEN,
                points = listOf(point(0.70f, 0.70f), point(0.75f, 0.72f)),
            ),
            annotation(
                id = "crossing",
                type = AnnotationType.LINE,
                points = listOf(point(0.05f, 0.25f), point(0.60f, 0.25f)),
            ),
            annotation(
                id = "far-line",
                type = AnnotationType.ARROW,
                points = listOf(point(0.62f, 0.62f), point(0.86f, 0.86f)),
            ),
        )

        val selected = noteLassoSelection(annotations, square(), rectangular = false)

        assertEquals(setOf("inside", "crossing"), selected)
    }

    @Test
    fun rectangularLassoSelectsShapesTextStampAndSignature() {
        val annotations = listOf(
            annotation(
                id = "rect",
                type = AnnotationType.RECT,
                points = listOf(point(0.25f, 0.25f), point(0.45f, 0.45f)),
            ),
            annotation(
                id = "ellipse",
                type = AnnotationType.ELLIPSE,
                points = listOf(point(0.26f, 0.26f), point(0.44f, 0.44f)),
            ),
            annotation(
                id = "text",
                type = AnnotationType.TEXT,
                points = listOf(point(0.30f, 0.30f)),
                text = "요약",
            ),
            annotation(
                id = "stamp",
                type = AnnotationType.STAMP,
                points = listOf(point(0.36f, 0.36f)),
                stamp = "확인",
            ),
            annotation(
                id = "signature",
                type = AnnotationType.SIGNATURE,
                points = listOf(point(0.30f, 0.44f)),
                text = "홍길동",
            ),
            annotation(
                id = "outside-text",
                type = AnnotationType.TEXT,
                points = listOf(point(0.72f, 0.72f)),
                text = "밖",
            ),
        )

        val selected = noteLassoSelection(annotations, rectangleSelection(), rectangular = true)

        assertTrue(selected.containsAll(listOf("rect", "ellipse", "text", "stamp", "signature")))
        assertTrue("outside-text" !in selected)
    }

    @Test
    fun rectangularLassoInsideClosedShapeSelectsShape() {
        val annotations = listOf(
            annotation(
                id = "large-rect",
                type = AnnotationType.RECT,
                points = listOf(point(0.05f, 0.05f), point(0.90f, 0.90f)),
            ),
        )

        val selected = noteLassoSelection(
            annotations,
            listOf(point(0.30f, 0.30f), point(0.50f, 0.50f)),
            rectangular = true,
        )

        assertEquals(setOf("large-rect"), selected)
    }

    @Test
    fun emptyOrTinyLassoSelectsNothing() {
        val annotations = listOf(
            annotation(
                id = "stroke",
                type = AnnotationType.PEN,
                points = listOf(point(0.2f, 0.2f), point(0.3f, 0.3f)),
            ),
        )

        assertTrue(noteLassoSelection(annotations, emptyList(), rectangular = false).isEmpty())
        assertTrue(
            noteLassoSelection(
                annotations,
                listOf(point(0.2f, 0.2f), point(0.3f, 0.3f)),
                rectangular = false,
            ).isEmpty(),
        )
        assertTrue(
            noteLassoSelection(
                annotations,
                listOf(point(0.2f, 0.2f)),
                rectangular = true,
            ).isEmpty(),
        )
    }

    @Test
    fun underlineAndHighlighterUseStrokeGeometry() {
        val annotations = listOf(
            annotation(
                id = "underline",
                type = AnnotationType.UNDERLINE,
                points = listOf(point(0.15f, 0.22f), point(0.35f, 0.22f)),
            ),
            annotation(
                id = "highlight",
                type = AnnotationType.HIGHLIGHT,
                points = listOf(point(0.14f, 0.30f), point(0.36f, 0.30f)),
            ),
        )
        val selected = noteLassoSelection(annotations, square(), rectangular = false)
        assertEquals(setOf("underline", "highlight"), selected)
    }

    private fun annotation(
        id: String,
        type: AnnotationType,
        points: List<NormalizedPoint>,
        text: String = "",
        stamp: String = "",
    ) = Annotation(
        id = id,
        type = type,
        page = 0,
        color = 0xFF1B1C19.toInt(),
        width = 0.004f,
        points = points,
        text = text,
        stamp = stamp,
        fontSize = 0.02f,
    )

    private fun point(x: Float, y: Float) = NormalizedPoint(x, y)

    private fun square(): List<NormalizedPoint> = listOf(
        point(0.10f, 0.10f),
        point(0.40f, 0.10f),
        point(0.40f, 0.40f),
        point(0.10f, 0.40f),
    )

    private fun rectangleSelection(): List<NormalizedPoint> = listOf(
        point(0.20f, 0.20f),
        point(0.50f, 0.50f),
    )
}
