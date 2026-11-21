package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure geometry and list helpers shared by the PDF canvas gate and card overlay. */
class PdfMemoCardLayerTest {

    private val card = MemoCard(
        id = "m1",
        text = "테스트 메모",
        page = 2,
        x = 0.25f,
        y = 0.25f,
        w = 0.4f,
        h = 0.2f,
    )

    private companion object {
        const val VIEW_W = 1000f
        const val VIEW_H = 800f
        const val PAGE_W = 100f
        const val PAGE_H = 200f
    }

    @Test
    fun screenRectFollowsTheCanvasTransformAtZoomOne() {
        val rect = pdfMemoScreenRect(
            card = card,
            fit = 1f,
            pan = Offset.Zero,
            viewWidth = VIEW_W,
            viewHeight = VIEW_H,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )

        assertEquals(475f, rect.left, 0.001f)
        assertEquals(350f, rect.top, 0.001f)
        assertEquals(40f, rect.width, 0.001f)
        assertEquals(40f, rect.height, 0.001f)
        assertTrue(rect.contains(Offset(480f, 360f)))
        assertFalse(rect.contains(Offset(470f, 360f)))
    }

    @Test
    fun zoomAndPanScaleAndShiftTheCardWithThePage() {
        val rect = pdfMemoScreenRect(
            card = card,
            fit = 2f,
            pan = Offset(30f, -20f),
            viewWidth = VIEW_W,
            viewHeight = VIEW_H,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )

        // (1000/2 + 30) + (0.25*100 - 50)*2 = 480; height 0.2*200*2 = 80.
        assertEquals(480f, rect.left, 0.001f)
        assertEquals(280f, rect.top, 0.001f)
        assertEquals(80f, rect.width, 0.001f)
        assertEquals(80f, rect.height, 0.001f)
    }

    @Test
    fun minimumTapSizeIsEnforcedForTinyCards() {
        val rect = pdfMemoScreenRect(
            card = card.copy(w = 0.001f, h = 0.001f),
            fit = 0.5f,
            pan = Offset.Zero,
            viewWidth = VIEW_W,
            viewHeight = VIEW_H,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
            minWidthPx = 92f,
            minHeightPx = 56f,
        )

        assertEquals(92f, rect.width, 0.001f)
        assertEquals(56f, rect.height, 0.001f)
    }

    @Test
    fun moveAndResizeConvertPixelsToNormalizedAndStayOnPage() {
        val moved = pdfMemoMovedCard(card, Offset(20f, -10f), 2f, PAGE_W, PAGE_H)
        assertEquals(0.35f, moved.x, 0.0001f)
        assertEquals(0.225f, moved.y, 0.0001f)
        assertEquals(2, moved.page)
        assertEquals(card.w, moved.w, 0.0001f)

        val resized = pdfMemoResizedCard(card, Offset(40f, 20f), 2f, PAGE_W, PAGE_H)
        assertEquals(0.6f, resized.w, 0.0001f)
        assertEquals(0.25f, resized.h, 0.0001f)

        val huge = pdfMemoMovedCard(card, Offset(10_000f, 10_000f), 1f, PAGE_W, PAGE_H)
        assertEquals(1f - card.w, huge.x, 0.0001f)
        assertEquals(1f - card.h, huge.y, 0.0001f)
    }

    @Test
    fun pageFilterAndEntriesKeepPageOrder() {
        val cards = listOf(
            card.copy(id = "b", page = 5),
            card.copy(id = "a", page = 0),
            card.copy(id = "c", page = 2),
        )

        assertEquals(listOf("c"), pdfMemoCardsForPage(cards, 2).map { it.id })
        assertTrue(pdfMemoCardsForPage(cards, 1).isEmpty())
        assertEquals(listOf("a", "c", "b"), pdfMemoCardEntries(cards).map { it.card.id })
        assertEquals(listOf(0, 2, 5), pdfMemoCardEntries(cards).map { it.pageIndex })
    }

    @Test
    fun editorPercentAndPlacementLabelAreStable() {
        assertEquals(0.25f, pdfMemoPercent("25", 0.9f), 0.0001f)
        assertEquals(0.4f, pdfMemoPercent("  40  ", 0.9f), 0.0001f)
        assertEquals(0.9f, pdfMemoPercent("", 0.9f), 0.0001f)
        assertEquals(0.9f, pdfMemoPercent("nope", 0.9f), 0.0001f)

        assertEquals("X 25% · Y 25% · W 40% · H 20%", pdfMemoPlacementLabel(card))
    }
}
