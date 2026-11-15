package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PageTransformsTest {

    private fun annotation(
        x: Float = 0.25f,
        y: Float = 0.4f,
        width: Float = 0.004f,
        fontSize: Float = 0.02f,
        textRotation: Int = 0,
    ) = Annotation(
        type = AnnotationType.TEXT,
        page = 0,
        color = 0,
        width = width,
        points = listOf(NormalizedPoint(x, y)),
        text = "test",
        fontSize = fontSize,
        textRotation = textRotation,
    )

    @Test
    fun rotate90_mapsPointAndSwapsScaleBasis() {
        val result = PageTransforms.rotate(annotation(), delta = 90, aspect = 0.5f)

        assertEquals(0.6f, result.points[0].x, 1e-6f)
        assertEquals(0.25f, result.points[0].y, 1e-6f)
        assertEquals(0.002f, result.width, 1e-6f)
        assertEquals(0.04f, result.fontSize, 1e-6f)
        assertEquals(90, result.textRotation)
    }

    @Test
    fun rotate180_mirrorsPointWithoutScaling() {
        val result = PageTransforms.rotate(annotation(), delta = 180, aspect = 0.5f)

        assertEquals(0.75f, result.points[0].x, 1e-6f)
        assertEquals(0.6f, result.points[0].y, 1e-6f)
        assertEquals(0.004f, result.width, 1e-6f)
        assertEquals(0.02f, result.fontSize, 1e-6f)
        assertEquals(180, result.textRotation)
    }

    @Test
    fun rotate270_mapsPointAndSwapsScaleBasis() {
        val result = PageTransforms.rotate(annotation(), delta = 270, aspect = 0.5f)

        assertEquals(0.4f, result.points[0].x, 1e-6f)
        assertEquals(0.75f, result.points[0].y, 1e-6f)
        assertEquals(0.002f, result.width, 1e-6f)
        assertEquals(0.04f, result.fontSize, 1e-6f)
        assertEquals(270, result.textRotation)
    }

    @Test
    fun rotateZero_isIdentity() {
        val original = annotation(textRotation = 45)
        val result = PageTransforms.rotate(original, delta = 0, aspect = 0.5f)

        assertEquals(original.points, result.points)
        assertEquals(original.width, result.width, 1e-6f)
        assertEquals(original.fontSize, result.fontSize, 1e-6f)
        assertEquals(45, result.textRotation)
    }

    @Test
    fun rotateStamp_keepsAnchorRotationAndMarkupFields() {
        val stamp = Annotation(
            type = AnnotationType.STAMP,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.2f, 0.3f)),
            fontSize = 0.02f,
            textRotation = 10,
            opacity = 0.5f,
            stamp = "DRAFT",
        )

        val result = PageTransforms.rotate(stamp, delta = 90, aspect = 0.5f)

        assertEquals(0.7f, result.points[0].x, 1e-6f)
        assertEquals(0.2f, result.points[0].y, 1e-6f)
        assertEquals(0.002f, result.width, 1e-6f)
        assertEquals(0.04f, result.fontSize, 1e-6f)
        assertEquals(100, result.textRotation)
        assertEquals(0.5f, result.opacity, 1e-6f)
        assertEquals("DRAFT", result.stamp)
    }

    @Test
    fun rotateNegativeDelta_normalizesToClockwiseRotation() {
        val result = PageTransforms.rotate(annotation(), delta = -90, aspect = 0.5f)

        assertEquals(0.4f, result.points[0].x, 1e-6f)
        assertEquals(0.75f, result.points[0].y, 1e-6f)
        assertEquals(270, result.textRotation)
    }
}
