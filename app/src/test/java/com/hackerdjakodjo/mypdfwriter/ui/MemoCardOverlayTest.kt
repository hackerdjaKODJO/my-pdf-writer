package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure geometry of the memo overlay: no Compose runtime, same math as the canvas. */
class MemoCardOverlayTest {

    private val card = MemoCard(
        id = "m1",
        text = "테스트 메모",
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
    fun screenRectMatchesPageTransformAtFitOne() {
        val rect = memoScreenRect(
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
    fun zoomAndPanMoveAndScaleTheSameRect() {
        val rect = memoScreenRect(
            card = card,
            fit = 2f,
            pan = Offset(30f, -20f),
            viewWidth = VIEW_W,
            viewHeight = VIEW_H,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )

        assertEquals(480f, rect.left, 0.001f)
        assertEquals(280f, rect.top, 0.001f)
        assertEquals(80f, rect.width, 0.001f)
        assertEquals(80f, rect.height, 0.001f)
        assertTrue(rect.contains(Offset(500f, 300f)))
    }

    @Test
    fun screenRectEnforcesMinimumTapSize() {
        val small = card.copy(w = 0.001f, h = 0.001f)
        val rect = memoScreenRect(
            card = small,
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
    fun moveDeltaConvertsPixelsToNormalizedAndStaysOnPage() {
        val moved = memoMovedCard(
            card = card,
            delta = Offset(20f, -10f),
            fit = 2f,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )

        // 20 px / (100 * 2) = +0.10; -10 px / (200 * 2) = -0.025.
        assertEquals(0.35f, moved.x, 0.0001f)
        assertEquals(0.225f, moved.y, 0.0001f)
        assertEquals(card.w, moved.w, 0.0001f)
        assertEquals(card.text, moved.text)

        val huge = memoMovedCard(
            card = card,
            delta = Offset(10_000f, 10_000f),
            fit = 1f,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )
        assertEquals(1f - card.w, huge.x, 0.0001f)
        assertEquals(1f - card.h, huge.y, 0.0001f)
    }

    @Test
    fun resizeDeltaGrowsShrinksAndClampsToModelBounds() {
        val grown = memoResizedCard(
            card = card,
            delta = Offset(40f, 20f),
            fit = 2f,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )
        assertEquals(0.6f, grown.w, 0.0001f)
        assertEquals(0.25f, grown.h, 0.0001f)
        assertEquals(card.x, grown.x, 0.0001f)

        val tiny = memoResizedCard(
            card = card,
            delta = Offset(-1_000f, -1_000f),
            fit = 1f,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )
        assertEquals(MemoCard.MIN_W, tiny.w, 0.0001f)
        assertEquals(MemoCard.MIN_H, tiny.h, 0.0001f)

        val oversized = memoResizedCard(
            card = card,
            delta = Offset(1_000f, 1_000f),
            fit = 1f,
            pageWidth = PAGE_W,
            pageHeight = PAGE_H,
        )
        assertEquals(1f, oversized.w, 0.0001f)
        assertEquals(1f, oversized.h, 0.0001f)
        assertEquals(0f, oversized.x, 0.0001f)
        assertEquals(0f, oversized.y, 0.0001f)
    }

    @Test
    fun closeButtonUsesAnObviousNonDestructiveLabelAndPerCardTag() {
        assertEquals("memo_card_close_m1", memoCardCloseTag("m1"))
        assertEquals("memo_card_close_a", memoCardCloseTag("a"))
        assertEquals("memo_card_close_b", memoCardCloseTag("b"))
        assertEquals("memo_card_close_", MEMO_CARD_CLOSE_TAG_PREFIX)
        assertEquals("닫기", MEMO_CARD_CLOSE_LABEL)
        assertEquals("메모 카드 닫기", MEMO_CARD_CLOSE_DESCRIPTION)
        assertTrue(MEMO_CARD_CLOSE_DESCRIPTION.contains("닫기"))
        assertFalse("close must not imply deletion", MEMO_CARD_CLOSE_DESCRIPTION.contains("삭제"))
    }

    @Test
    fun headerFitsTheFullSizeCloseTarget() {
        assertEquals(48, MIN_TARGET_DP)
        assertEquals(MIN_TARGET_DP, MEMO_CARD_HEADER_MIN_HEIGHT_DP)
        assertTrue(MEMO_CARD_HEADER_MIN_HEIGHT_DP >= 48)
    }

    @Test
    fun cardDeleteLivesInTheMenuAndStillAsksForConfirmation() {
        assertEquals("카드 삭제", MEMO_CARD_DELETE_MENU_LABEL)
        assertEquals("메모 카드 삭제", MEMO_DELETE_TITLE)
        assertEquals("이 카드의 메모를 삭제합니다. 실행 취소로 되돌릴 수 있습니다.", MEMO_DELETE_MESSAGE)
        assertEquals("삭제", MEMO_DELETE_CONFIRM)
        assertEquals("취소", MEMO_DELETE_CANCEL)
    }
}
