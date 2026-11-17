package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Device-only checks for two-finger pinch in the continuous reader.
 *
 * These compile with `assembleDebugAndroidTest`; running them requires a device
 * or emulator. The touch API drives two real pointers through the outer gesture
 * Box and asserts the document-wide zoom label appears in the top bar.
 */
class ReaderPinchE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setReader() {
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "pinch-test",
                pageCount = 3,
                initialPage = 0,
                loadPage = { _, _ -> Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onOpenPage = {},
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun twoFingerSpreadZoomsTheWholeDocument() {
        setReader()
        // Fit-to-width: the subtitle carries no zoom percentage yet.
        composeRule.onNodeWithText("전체 3쪽 · 연속 보기", substring = true).assertExists()

        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            // 160 px apart -> 640 px apart (4x) must clamp to READER_ZOOM_MAX.
            down(0, Offset(cx - 80f, cy))
            down(1, Offset(cx + 80f, cy))
            moveTo(0, Offset(cx - 320f, cy), delayMillis = 16)
            moveTo(1, Offset(cx + 320f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("250%", substring = true).assertExists()
    }

    @Test
    fun twoFingerPinchBackToFitClearsTheZoomLabel() {
        setReader()
        // Start already zoomed via a spread, then pinch back in.
        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            down(0, Offset(cx - 80f, cy))
            down(1, Offset(cx + 80f, cy))
            moveTo(0, Offset(cx - 320f, cy), delayMillis = 16)
            moveTo(1, Offset(cx + 320f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("250%", substring = true).assertExists()

        // A fresh pinch back to roughly the original separation returns to fit.
        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            down(0, Offset(cx - 320f, cy))
            down(1, Offset(cx + 320f, cy))
            moveTo(0, Offset(cx - 80f, cy), delayMillis = 16)
            moveTo(1, Offset(cx + 80f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("전체 3쪽 · 연속 보기").assertExists()
    }

    @Test
    fun zoomActuallyEnlargesThePageLayoutNotJustTheLabel() {
        // Regression for the page staying at viewport width after zoom: the
        // page surface must be allowed to measure wider than its parent and be
        // clipped/panned by the reader viewport.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val before = page.getUnclippedBoundsInRoot()

        composeRule.onRoot().performTouchInput {
            click(center)
            click(center)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        val beforeWidth = before.right - before.left
        val afterWidth = after.right - after.left
        assertTrue(
            "page width must grow after zoom; before=$beforeWidth after=$afterWidth",
            afterWidth > beforeWidth * 1.5f,
        )
    }

    @Test
    fun horizontalPanMovesThePageWithoutOpeningIt() {
        val opened = AtomicInteger(-1)
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "pan-test",
                pageCount = 3,
                initialPage = 0,
                loadPage = { _, _ -> Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onOpenPage = { opened.set(it) },
            )
        }
        composeRule.waitForIdle()

        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        // Double tap to 200% so there is horizontal overflow to pan.
        composeRule.onRoot().performTouchInput { click(center); click(center) }
        composeRule.waitForIdle()
        val zoomed = page.getUnclippedBoundsInRoot()

        composeRule.onRoot().performTouchInput {
            down(0, center)
            moveTo(0, center + Offset(-260f, 0f), delayMillis = 16)
            up(0)
        }
        composeRule.waitForIdle()

        val panned = page.getUnclippedBoundsInRoot()
        assertTrue(
            "horizontal drag must move the zoomed page left; " +
                "before=${zoomed.left} after=${panned.left}",
            panned.left < zoomed.left - 1.dp,
        )
        assertEquals("horizontal pan must not open the page", -1, opened.get())
    }

    @Test
    fun pinchActuallyRerendersPageBitmapsAtHigherZoom() {
        // Regression for "percentage changes, page image does not": the render
        // key passed to loadPage must grow after a two-finger spread, not just
        // the zoom label.
        val renderZooms = Collections.synchronizedList(mutableListOf<Float>())
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "pinch-rerender-test",
                pageCount = 1,
                initialPage = 0,
                loadPage = { _, zoom ->
                    renderZooms.add(zoom)
                    // Bitmap width grows with the render scale, mirroring the
                    // real PdfRepository behaviour.
                    val width = (100 * readerRenderScale(readerRenderZoom(zoom))).toInt()
                    Bitmap.createBitmap(width, 140, Bitmap.Config.ARGB_8888)
                },
                onBack = {},
                onOpenPage = {},
            )
        }
        composeRule.waitForIdle()
        val before = renderZooms.toList()
        assertTrue("initial render must happen: $renderZooms", before.isNotEmpty())
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val beforeBounds = page.getUnclippedBoundsInRoot()

        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            down(0, Offset(cx - 40f, cy))
            down(1, Offset(cx + 40f, cy))
            moveTo(0, Offset(cx - 200f, cy), delayMillis = 16)
            moveTo(1, Offset(cx + 200f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()

        // The top-bar percentage changed.
        composeRule.onNodeWithText("250%", substring = true).assertExists()
        // And, unlike the reported bug, the page was re-rendered at a bigger
        // render zoom (not just re-laid out at a bigger screen size).
        val maxAfter = renderZooms.toList().maxOrNull() ?: 1f
        val maxBefore = before.maxOrNull() ?: 1f
        assertTrue(
            "loadPage must be called with a larger render zoom after pinch; " +
                "before=$before after=${renderZooms.toList()}",
            maxAfter > maxBefore + 0.1f,
        )
        val afterBounds = page.getUnclippedBoundsInRoot()
        val beforeWidth = beforeBounds.right - beforeBounds.left
        val afterWidth = afterBounds.right - afterBounds.left
        assertTrue(
            "pinch must enlarge the page layout, not only the zoom label; " +
                "before=$beforeWidth after=$afterWidth",
            afterWidth > beforeWidth * 1.5f,
        )
    }
}
