package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the view-only crop transform and its odd/even policy. */
class PdfCropTest {

    private fun assertPoint(
        expectedX: Float,
        expectedY: Float,
        actual: CropPoint,
    ) {
        assertEquals(expectedX, actual.x, 0.0001f)
        assertEquals(expectedY, actual.y, 0.0001f)
    }

    @Test
    fun zeroInsetsAreAnExactIdentity() {
        val zero = CropInsets()
        assertEquals(CropInsets.NONE, PdfCrop.clampInsets(zero))
        assertTrue(PdfCrop.isIdentity(zero))
        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), PdfCrop.visibleRect(zero))

        assertPoint(0.25f, 0.75f, PdfCrop.displayToPage(0.25f, 0.75f, zero))
        assertPoint(0.25f, 0.75f, PdfCrop.pageToDisplay(0.25f, 0.75f, zero))
        val rect = NormalizedRect(0.1f, 0.2f, 0.6f, 0.9f)
        assertEquals(rect, PdfCrop.applyCropToRect(rect, zero))
    }

    @Test
    fun clampInsetsRejectsNonFiniteAndNegativeValues() {
        val clamped = PdfCrop.clampInsets(
            CropInsets(Float.NaN, -0.3f, Float.POSITIVE_INFINITY, -Float.MAX_VALUE),
        )
        assertEquals(CropInsets(), clamped)
    }

    @Test
    fun clampInsetsCapsOneSideAndScalesAnOverlappingPair() {
        val single = PdfCrop.clampInsets(CropInsets(left = 2f))
        assertEquals(0.9f, single.left, 0.0001f)
        assertEquals(CropInsets(), single.copy(left = 0f))

        val pair = PdfCrop.clampInsets(CropInsets(left = 0.8f, right = 0.8f))
        assertEquals(0.45f, pair.left, 0.0001f)
        assertEquals(0.45f, pair.right, 0.0001f)
        val visible = PdfCrop.visibleRect(pair)
        assertEquals(CropInsets.MIN_VISIBLE_FRACTION, visible.width, 0.0001f)

        val vertical = PdfCrop.clampInsets(CropInsets(top = 0.6f, bottom = 0.6f))
        assertEquals(0.45f, vertical.top, 0.0001f)
        assertEquals(0.45f, vertical.bottom, 0.0001f)
        assertEquals(CropInsets.MIN_VISIBLE_FRACTION, PdfCrop.visibleRect(vertical).height, 0.0001f)
    }

    @Test
    fun clampIsIdempotent() {
        val raw = CropInsets(left = 1.4f, top = 0.25f, right = 0.9f, bottom = Float.NaN)
        val once = PdfCrop.clampInsets(raw)
        assertEquals(once, PdfCrop.clampInsets(once))
    }

    @Test
    fun visibleRectMatchesTheKeptPageArea() {
        val visible = PdfCrop.visibleRect(CropInsets(0.1f, 0.2f, 0.15f, 0.25f))
        assertEquals(0.1f, visible.left, 0.0001f)
        assertEquals(0.2f, visible.top, 0.0001f)
        assertEquals(0.85f, visible.right, 0.0001f)
        assertEquals(0.75f, visible.bottom, 0.0001f)
        assertEquals(0.75f, visible.width, 0.0001f)
        assertEquals(0.55f, visible.height, 0.0001f)
    }

    @Test
    fun displayToPageMapsVisibleRectCornersToPageEdges() {
        val insets = CropInsets(0.1f, 0.2f, 0.15f, 0.25f)
        assertPoint(0.1f, 0.2f, PdfCrop.displayToPage(0f, 0f, insets))
        assertPoint(0.85f, 0.75f, PdfCrop.displayToPage(1f, 1f, insets))
        assertPoint(0.475f, 0.475f, PdfCrop.displayToPage(0.5f, 0.5f, insets))
    }

    @Test
    fun forwardAndInverseMappingRoundTrip() {
        val insets = CropInsets(0.12f, 0.08f, 0.2f, 0.16f)
        val samples = listOf(0f, 0.13f, 0.5f, 0.87f, 1f)
        samples.forEach { x ->
            samples.forEach { y ->
                val page = PdfCrop.displayToPage(x, y, insets)
                val display = PdfCrop.pageToDisplay(page.x, page.y, insets)
                assertPoint(x, y, display)

                val backToPage = PdfCrop.displayToPage(display.x, display.y, insets)
                assertPoint(page.x, page.y, backToPage)
            }
        }
    }

    @Test
    fun displayToPageIsUnclampedSoPanningDoesNotStickInkToTheEdge() {
        val insets = CropInsets(0.1f, 0.1f, 0.1f, 0.1f)
        val outside = PdfCrop.displayToPage(-0.2f, 1.4f, insets)
        assertTrue(outside.x < insets.left)
        assertTrue(outside.y > 1f - insets.bottom)
    }

    @Test
    fun applyCropToRectMapsMarkupRectIntoDisplaySpace() {
        val insets = CropInsets(0.1f, 0.2f, 0.15f, 0.25f)
        val markup = NormalizedRect(0.2f, 0.3f, 0.6f, 0.65f)
        val display = PdfCrop.applyCropToRect(markup, insets)

        // (x - left) / keptWidth, (y - top) / keptHeight
        assertEquals(0.1f / 0.75f, display.left, 0.0001f)
        assertEquals(0.1f / 0.55f, display.top, 0.0001f)
        assertEquals(0.5f / 0.75f, display.right, 0.0001f)
        assertEquals(0.45f / 0.55f, display.bottom, 0.0001f)
    }

    @Test
    fun annotationPointStaysAlignedAtCrop() {
        // Ink is stored in page space. Draw/hit-test only ever convert it to and
        // from display space, so the round trip must be exact at any crop.
        val insets = CropInsets(0.18f, 0.05f, 0.07f, 0.22f)
        val stored = CropPoint(0.42f, 0.61f)
        val display = PdfCrop.pageToDisplay(stored.x, stored.y, insets)
        assertTrue(display.x in 0f..1f)
        assertTrue(display.y in 0f..1f)
        val hit = PdfCrop.displayToPage(display.x, display.y, insets)
        assertPoint(stored.x, stored.y, hit)

        // A stroke on the removed margin maps outside display space and is
        // therefore clipped instead of being drawn at the crop boundary.
        val hiddenInk = PdfCrop.pageToDisplay(0.02f, 0.5f, insets)
        assertTrue(hiddenInk.x < 0f)
    }

    @Test
    fun oddEvenPolicyOffKeepsEveryPageIdentical() {
        val insets = CropInsets(0.2f, 0.1f, 0.05f, 0.15f)
        (0 until 6).forEach { index ->
            assertEquals(insets, PdfCrop.cropForPage(insets, index, false))
        }
    }

    @Test
    fun oddEvenPolicyOnMirrorsEvenPagesOnly() {
        val insets = CropInsets(0.2f, 0.1f, 0.05f, 0.15f)
        val first = PdfCrop.cropForPage(insets, 0, true)
        assertEquals(insets, first) // page 1 (1-based) is odd

        val second = PdfCrop.cropForPage(insets, 1, true) // page 2 is even
        assertEquals(insets.right, second.left, 0f)
        assertEquals(insets.left, second.right, 0f)
        assertEquals(insets.top, second.top, 0f)
        assertEquals(insets.bottom, second.bottom, 0f)

        assertEquals(insets, PdfCrop.cropForPage(insets, 2, true))
        assertEquals(second, PdfCrop.cropForPage(insets, 3, true))
    }

    @Test
    fun oddEvenPolicyClampsBeforeMirroring() {
        val raw = CropInsets(left = 1.7f, top = 0.1f, right = Float.NaN, bottom = 0.1f)
        val safe = PdfCrop.clampInsets(raw)
        assertEquals(safe, PdfCrop.cropForPage(raw, 0, true))
        val mirrored = PdfCrop.cropForPage(raw, 1, true)
        assertEquals(safe.right, mirrored.left, 0f)
        assertEquals(safe.left, mirrored.right, 0f)
    }

    @Test
    fun clipRectForCanvasIsCentredOnTheVisibleCrop() {
        val full = PdfCrop.clipRectForCanvas(
            PdfCrop.visibleRect(CropInsets()),
            pageWidth = 1000f,
            pageHeight = 2000f,
        )
        assertEquals(CanvasClipRect(-500f, -1000f, 500f, 1000f), full)

        // Asymmetric margins: the clip still sits at the local origin because
        // the canvas translates the crop centre there. Raw page pixels would
        // put this rectangle at [100, 700] and clip only a quadrant.
        val visible = PdfCrop.visibleRect(CropInsets(0.1f, 0.2f, 0.3f, 0.25f))
        val clip = PdfCrop.clipRectForCanvas(visible, 1000f, 2000f)
        assertEquals(-300f, clip.left, 0.0001f)
        assertEquals(-550f, clip.top, 0.0001f)
        assertEquals(300f, clip.right, 0.0001f)
        assertEquals(550f, clip.bottom, 0.0001f)
        assertEquals(0f, clip.left + clip.right, 0.0001f)
        assertEquals(0f, clip.top + clip.bottom, 0.0001f)
        assertEquals(visible.width * 1000f, clip.right - clip.left, 0.0001f)
        assertEquals(visible.height * 2000f, clip.bottom - clip.top, 0.0001f)
    }

    @Test
    fun cropPixelRectReturnsFullBitmapForZeroInsets() {
        val full = PdfCrop.cropPixelRect(
            PdfCrop.visibleRect(CropInsets()),
            bitmapWidth = 1000,
            bitmapHeight = 2000,
        )
        assertEquals(CropPixelRect(0, 0, 1000, 2000), full)
    }

    @Test
    fun cropPixelRectReturnsTheKeptSubRectangle() {
        val src = PdfCrop.cropPixelRect(
            PdfCrop.visibleRect(CropInsets(0.1f, 0.2f, 0.15f, 0.25f)),
            bitmapWidth = 1000,
            bitmapHeight = 2000,
        )
        assertEquals(CropPixelRect(100, 400, 750, 1100), src)
    }

    @Test
    fun cropPixelRectNeverProducesAnEmptyWindow() {
        val src = PdfCrop.cropPixelRect(
            NormalizedRect(0.999f, 0.999f, 1f, 1f),
            bitmapWidth = 1,
            bitmapHeight = 1,
        )
        assertEquals(1, src.width)
        assertEquals(1, src.height)
        assertFalse(src.left < 0 || src.top < 0)
    }
}
