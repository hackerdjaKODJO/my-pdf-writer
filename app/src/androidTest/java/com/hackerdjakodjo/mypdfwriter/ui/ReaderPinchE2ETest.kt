package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
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
 *
 * Gesture offsets are fractions of the measured viewport (never absolute
 * screen pixels) so the tests behave the same on a 360dp emulator and a large
 * tablet. Expected labels come from the production zoom policy, and the label
 * is read from the subtitle node only: the debug HUD also prints the same
 * percentage, so a broad `onNodeWithText("250%")` matches several nodes.
 * Pinch helpers repeat bounded passes until the production label reaches the
 * target because this emulator applies only one of the two move deltas.
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

    /** The subtitle is the only text node carrying the "연속 보기" prefix. */
    private fun assertZoomLabel(expectedZoom: Float) {
        composeRule.onNodeWithText("연속 보기", substring = true)
            .assertTextContains(readerZoomLabel(expectedZoom), substring = true)
    }

    /** Live text of the subtitle, read from semantics after the UI is idle. */
    private fun subtitleText(): String {
        val node = composeRule.onNodeWithText("연속 보기", substring = true)
            .fetchSemanticsNode()
        return node.config[SemanticsProperties.Text]
            ?.joinToString(" ") { it.toString() }
            ?: ""
    }

    /** True when the subtitle is the plain fit label, i.e. zoom == READER_ZOOM_MIN. */
    private fun isFitLabel(): Boolean {
        val text = subtitleText()
        return text.contains("연속 보기") && !text.contains("%")
    }

    /**
     * One two-pointer spread from 20% to 75% of the viewport width. Endpoints
     * at 12.5% / 87.5% stay clear of the system edge-gesture zones.
     *
     * On this emulator the observer only applies the *second* pointer move's
     * zoom delta (540/342 ~= 1.58), so a single pass is not enough to reach
     * READER_ZOOM_MAX. [spreadToMaxZoom] repeats bounded passes and checks the
     * production label after each one instead of asserting a gesture ratio.
     */
    private fun spreadPass() {
        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            val near = width * 0.10f
            val far = width * 0.375f
            down(0, Offset(cx - near, cy))
            down(1, Offset(cx + near, cy))
            moveTo(0, Offset(cx - far, cy), delayMillis = 16)
            moveTo(1, Offset(cx + far, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
    }

    /** Spreads until the reader reports the real max zoom label, up to 3 passes. */
    private fun spreadToMaxZoom() {
        val maxLabel = readerZoomLabel(READER_ZOOM_MAX)
        for (pass in 0 until 3) {
            if (subtitleText().contains(maxLabel)) break
            spreadPass()
        }
    }

    /** One two-pointer pinch back from 75% to 20% of the viewport width. */
    private fun pinchInPass() {
        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            val near = width * 0.10f
            val far = width * 0.375f
            down(0, Offset(cx - far, cy))
            down(1, Offset(cx + far, cy))
            moveTo(0, Offset(cx - near, cy), delayMillis = 16)
            moveTo(1, Offset(cx + near, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
    }

    /** Pinches in until the zoom suffix disappears, up to 3 passes. */
    private fun pinchBackToFit() {
        for (pass in 0 until 3) {
            if (isFitLabel()) break
            pinchInPass()
        }
    }

    /**
     * `click(center); click(center)` inside one injection block has zero delay
     * between taps, so detectTapGestures never sees a double tap. The dedicated
     * doubleClick helper supplies the required inter-tap delay.
     */
    private fun doubleTapToZoom() {
        composeRule.onRoot().performTouchInput { doubleClick(center) }
        composeRule.waitForIdle()
    }

    @Test
    fun twoFingerSpreadZoomsTheWholeDocument() {
        setReader()
        // Fit-to-width: the subtitle carries no zoom percentage yet.
        composeRule.onNodeWithText("전체 3쪽 · 연속 보기", substring = true).assertExists()

        spreadToMaxZoom()

        assertZoomLabel(READER_ZOOM_MAX)
    }

    @Test
    fun twoFingerPinchBackToFitClearsTheZoomLabel() {
        setReader()
        // Start already zoomed via a spread, then pinch back in.
        spreadToMaxZoom()
        assertZoomLabel(READER_ZOOM_MAX)

        pinchBackToFit()

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

        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)

        val after = page.getUnclippedBoundsInRoot()
        val beforeWidth = before.right - before.left
        val afterWidth = after.right - after.left
        assertTrue(
            "page width must grow after zoom; before=$beforeWidth after=$afterWidth",
            afterWidth > beforeWidth * 1.5f,
        )
    }

    @Test
    fun zoomKeepsTheOldPageVisibleWhileTheNewBitmapRenders() {
        // Before 0.8.22 the producer reset its state to Loading on every
        // render-zoom change, so pinch/zoom flashed a blank page until the new
        // bitmap arrived. The previous bitmap must stay on screen.
        val renderGate = CompletableDeferred<Unit>()
        val loadCount = AtomicInteger()
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "flicker-test",
                pageCount = 1,
                initialPage = 0,
                loadPage = { _, _ ->
                    if (loadCount.incrementAndGet() > 1) renderGate.await()
                    Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888)
                },
                onBack = {},
                onOpenPage = {},
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("1쪽 페이지").assertExists()

        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        // The second render is deliberately suspended; a slow emulator can take
        // a while to reach it, so allow generous headroom.
        composeRule.waitUntil(timeoutMillis = 15_000) { loadCount.get() >= 2 }

        // The old bitmap is still displayed while loadPage() is suspended.
        composeRule.onNodeWithContentDescription("1쪽 페이지").assertExists()
        renderGate.complete(Unit)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("1쪽 페이지").assertExists()
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
        // Double tap to READER_ZOOM_DOUBLE_TAP so there is horizontal overflow
        // to pan.
        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val zoomed = page.getUnclippedBoundsInRoot()
        val zoomedWidth = zoomed.right - zoomed.left

        // Drag left by ~35% of the measured viewport, well past touch slop but
        // inside the available overflow at 200% zoom.
        val viewportWidthPx = composeRule.onRoot().fetchSemanticsNode().size.width
        val dragDx = viewportWidthPx * 0.35f
        composeRule.onRoot().performTouchInput {
            down(0, center)
            moveTo(0, center + Offset(-dragDx, 0f), delayMillis = 16)
            up(0)
        }
        composeRule.waitForIdle()

        val panned = page.getUnclippedBoundsInRoot()
        assertTrue(
            "horizontal drag must move the zoomed page left by at least 10% " +
                "of its width; before=${zoomed.left} after=${panned.left}",
            zoomed.left - panned.left >= zoomedWidth * 0.10f,
        )
        assertEquals("horizontal pan must not open the page", -1, opened.get())

        // Drag back right further than we panned: the page must return all the
        // way to the left edge (no 14 dp content-padding gap and no system
        // gesture steal). Allow a small density-independent tolerance.
        composeRule.onRoot().performTouchInput {
            down(0, center)
            moveTo(0, center + Offset(dragDx * 1.5f, 0f), delayMillis = 16)
            up(0)
        }
        composeRule.waitForIdle()
        val backAtLeft = page.getUnclippedBoundsInRoot()
        assertTrue(
            "page must return to the left edge; before=${zoomed.left} after=${backAtLeft.left}",
            backAtLeft.left <= zoomed.left + 8.dp,
        )
        assertEquals("pan back must not open the page", -1, opened.get())
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

        spreadToMaxZoom()

        // The top-bar percentage changed, derived from the production policy.
        assertZoomLabel(READER_ZOOM_MAX)
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
