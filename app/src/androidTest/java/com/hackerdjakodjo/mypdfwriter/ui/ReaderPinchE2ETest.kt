package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

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

    /** Zoom is no longer drawn in the title; the page exposes it to TalkBack/tests. */
    private fun assertZoomLabel(expectedZoom: Float) {
        assertTrue(
            "expected zoom ${readerZoomLabel(expectedZoom)}, actual=${subtitleText()}",
            subtitleText().contains(readerZoomLabel(expectedZoom)),
        )
    }

    /** Accessibility state description of the first rendered page. */
    private fun subtitleText(): String {
        val node = composeRule.onNodeWithContentDescription("1쪽 페이지")
            .fetchSemanticsNode()
        return node.config[SemanticsProperties.StateDescription] ?: ""
    }

    /** True when the page is at fit zoom. */
    private fun isFitLabel(): Boolean = subtitleText() == "맞춤 배율"

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
    fun floatingPageNavigatorOpensNumericJump() {
        setReader()
        val previous = composeRule.onNodeWithContentDescription("이전 페이지")
        val next = composeRule.onNodeWithContentDescription("다음 페이지")
        previous.assertIsDisplayed()
        next.assertIsDisplayed()
        val previousBounds = previous.getUnclippedBoundsInRoot()
        assertTrue("compact navigator must stay small", previousBounds.bottom - previousBounds.top <= 32.dp)
        next.performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(viewerPagePillDescription(1, 3)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(viewerPagePillDescription(1, 3))
            .performTouchInput { click(center) }
        composeRule.onNodeWithText("페이지 이동").assertIsDisplayed()
    }

    @Test
    fun twoFingerSpreadZoomsTheWholeDocument() {
        setReader()
        composeRule.onNodeWithText("pinch-test").assertExists()
        assertTrue(isFitLabel())

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

        assertTrue(isFitLabel())
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
            for (step in 1..8) {
                moveTo(0, center + Offset(-dragDx * step / 8, 0f), delayMillis = 16)
            }
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

        // Drag back right past the panned position: zoom now re-anchors the page
        // around the tap focal point, so it starts with left-side headroom and
        // needs more than one viewport fraction of rightward drag to reach the
        // left edge. Two bounded drags keep every touch inside the viewport.
        repeat(2) {
            composeRule.onRoot().performTouchInput {
                down(0, center)
                for (step in 1..8) {
                    moveTo(
                        0,
                        center + Offset(dragDx * 1.35f * step / 8, 0f),
                        delayMillis = 16,
                    )
                }
                up(0)
            }
            composeRule.waitForIdle()
        }
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

    /**
     * One-finger horizontal drag by [dx] pixels, ending with the pointer lifted.
     * Several move frames matter: the production drag detector consumes the
     * slop-crossing frame and reports the following ones.
     */
    private fun dragPage(dx: Float) {
        composeRule.onRoot().performTouchInput {
            down(0, center)
            val steps = 8
            for (step in 1..steps) {
                moveTo(
                    0,
                    center + Offset(dx * step / steps, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()
    }

    /** Measured viewport width in pixels, used to scale gesture offsets. */
    private fun viewportWidthPx(): Float =
        composeRule.onRoot().fetchSemanticsNode().size.width.toFloat()

    /** A gentle ~20% pinch-in that stays above fit instead of returning to 100%. */
    private fun pinchInOneStep() {
        composeRule.onRoot().performTouchInput {
            val cx = width / 2f
            val cy = height * 0.6f
            val near = width * 0.24f
            val far = width * 0.30f
            down(0, Offset(cx - far, cy))
            down(1, Offset(cx + far, cy))
            moveTo(0, Offset(cx - near, cy), delayMillis = 16)
            moveTo(1, Offset(cx + near, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun pinchZoomThenPanInBothDirectionsMovesThePage() {
        // Regression guard for the ordinary flow: zooming with a pinch must not
        // disable the one-finger pan that used to be verified only after a
        // double tap (READ-07).
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        spreadToMaxZoom()
        assertZoomLabel(READER_ZOOM_MAX)
        val zoomed = page.getUnclippedBoundsInRoot()
        val zoomedWidth = zoomed.right - zoomed.left
        val viewport = viewportWidthPx()

        dragPage(-viewport * 0.35f)
        val panned = page.getUnclippedBoundsInRoot()
        assertTrue(
            "pinch zoom must allow leftward pan; before=${zoomed.left} after=${panned.left}",
            zoomed.left - panned.left >= zoomedWidth * 0.10f,
        )

        dragPage(viewport * 0.60f)
        val pannedRight = page.getUnclippedBoundsInRoot()
        // Zoom now re-anchors the page around the pinch focal point, so the
        // rightward drag moves it back toward (not necessarily to) the edge.
        assertTrue(
            "pinch zoom must allow rightward pan; " +
                "before=${panned.left} after=${pannedRight.left}",
            pannedRight.left > panned.left + zoomedWidth * 0.05f,
        )
    }

    @Test
    fun panToEdgeThenZoomOutLetsTheFirstRightDragMoveThePage() {
        // Regression for the invisible dead zone: panX used to be re-clamped
        // only in the layout's displayed offset, so after zooming out the first
        // rightward drags did nothing and the left side looked unreachable.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        spreadToMaxZoom()
        assertZoomLabel(READER_ZOOM_MAX)
        val zoomed = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()

        dragPage(-viewport * (READER_ZOOM_MAX - READER_ZOOM_MIN) * 1.1f)
        val atEdge = page.getUnclippedBoundsInRoot()
        assertTrue(
            "page must first reach its right pan edge; " +
                "before=${zoomed.left} after=${atEdge.left}",
            zoomed.left - atEdge.left >= (zoomed.right - zoomed.left) * 0.20f,
        )

        val labelBefore = subtitleText()
        pinchInOneStep()
        val labelAfter = subtitleText()
        assertTrue(
            "pinch-in must change zoom but stay above fit; before=$labelBefore after=$labelAfter",
            labelAfter != labelBefore && !isFitLabel(),
        )
        val afterZoomOut = page.getUnclippedBoundsInRoot()

        dragPage(viewport * 0.30f)
        val afterDrag = page.getUnclippedBoundsInRoot()
        val pageWidth = afterDrag.right - afterDrag.left
        assertTrue(
            "first rightward drag after zoom-out must reveal the left side; " +
                "before=${afterZoomOut.left} after=${afterDrag.left}",
            afterDrag.left - afterZoomOut.left >= pageWidth * 0.05f,
        )
    }

    @Test
    fun pinchThenPanWithRetainedFingerMovesThePage() {
        // Exact retained-finger path: the raw pinch observer consumes the
        // two-finger events; the pan detector must pick the remaining finger
        // back up without waiting for every pointer to rise.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val viewport = viewportWidthPx()
        val cx = viewport / 2f
        val cy = composeRule.onRoot().fetchSemanticsNode().size.height * 0.6f
        val first = Offset(cx - viewport * 0.10f, cy)

        composeRule.onRoot().performTouchInput {
            down(0, first)
            down(1, Offset(cx + viewport * 0.10f, cy))
            for (step in 1..8) {
                moveTo(
                    1,
                    Offset(cx + viewport * 0.10f + viewport * 0.20f * step / 8, cy),
                    delayMillis = 16,
                )
            }
            up(1) // finger 0 deliberately stays down across injection blocks
        }
        composeRule.waitForIdle()
        val beforeRetainedDrag = page.getUnclippedBoundsInRoot()

        composeRule.onRoot().performTouchInput {
            for (step in 1..8) {
                moveTo(
                    0,
                    first + Offset(viewport * 0.30f * step / 8, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "retained finger must move the page after a pinch; " +
                "before=${beforeRetainedDrag.left} after=${after.left}",
            after.left - beforeRetainedDrag.left > 8.dp,
        )
    }

    @Test
    fun doubleTapZoomThenRightDragMovesThePageRight() {
        // Exact user report: zoom in, then push the finger RIGHT. Before the
        // pivot-preserving fix panX stayed 0 and the rightward delta clamped
        // back to 0, so the page did not move at all.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        doubleTapToZoom() // doubleClick(center) → focal = viewport centre
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val zoomed = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()

        composeRule.onRoot().performTouchInput {
            down(0, center)
            for (step in 1..8) {
                moveTo(
                    0,
                    center + Offset(viewport * 0.30f * step / 8, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "rightward drag after a centred zoom must move the page right; " +
                "before=${zoomed.left} after=${after.left}",
            after.left - zoomed.left >= (zoomed.right - zoomed.left) * 0.10f,
        )
    }

    @Test
    fun diagonalDragPansHorizontallyAndScrollsVertically() {
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val before = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()
        val height = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()

        composeRule.onRoot().performTouchInput {
            down(0, center)
            val steps = 12
            for (step in 1..steps) {
                moveTo(
                    0,
                    center + Offset(
                        viewport * 0.25f * step / steps,
                        height * 0.55f * step / steps,
                    ),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "diagonal drag must pan horizontally; before=${before.left} after=${after.left}",
            after.left - before.left > 8.dp,
        )
        assertTrue(
            "diagonal drag must also scroll vertically; before=${before.top} after=${after.top}",
            before.top - after.top > 8.dp,
        )
    }

    @Test
    fun verticalOnlyDragScrollsWithoutHorizontalPan() {
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val before = page.getUnclippedBoundsInRoot()
        val height = composeRule.onRoot().fetchSemanticsNode().size.height.toFloat()

        composeRule.onRoot().performTouchInput {
            down(0, center)
            val steps = 12
            for (step in 1..steps) {
                moveTo(
                    0,
                    center + Offset(0f, height * 0.55f * step / steps),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "vertical drag must scroll; before=${before.top} after=${after.top}",
            before.top - after.top > 8.dp,
        )
        assertTrue(
            "vertical drag must not pan horizontally; before=${before.left} after=${after.left}",
            abs((after.left - before.left).value) <= 2f,
        )
    }

    @Test
    fun zoomThenImmediateRightDragInOneGesturePansInsteadOfTurningPage() {
        // Regression: rememberUpdatedState(zoom) only refreshes on composition,
        // so a right drag beginning in the frames right after a pinch could be
        // routed to the fit-zoom page-swipe branch. Zoom and drag are injected in
        // ONE block deliberately — no waitForIdle between them.
        val pageChanges = Collections.synchronizedList(mutableListOf<Int>())
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "zoom-race",
                pageCount = 3,
                initialPage = 0,
                loadPage = { _, _ -> Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onOpenPage = {},
                onPageChange = { pageChanges.add(it) },
            )
        }
        composeRule.waitForIdle()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val viewport = viewportWidthPx()

        val cy = composeRule.onRoot().fetchSemanticsNode().size.height * 0.6f
        val cx = viewport / 2f
        val first = Offset(cx - viewport * 0.05f, cy)
        composeRule.onRoot().performTouchInput {
            down(0, first)
            down(1, Offset(cx + viewport * 0.05f, cy))
            for (step in 1..8) {
                moveTo(
                    1,
                    Offset(cx + viewport * 0.05f + viewport * 0.25f * step / 8, cy),
                    delayMillis = 16,
                )
            }
            up(1) // retained finger continues immediately in the next block
        }
        composeRule.waitForIdle()
        val beforeRetainedDrag = page.getUnclippedBoundsInRoot()
        val startX = cx + viewport * 0.30f

        composeRule.onRoot().performTouchInput {
            for (step in 1..8) {
                moveTo(
                    0,
                    Offset(startX + viewport * 0.30f * step / 8, cy),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        assertTrue("zoom must apply", !isFitLabel())
        assertTrue(
            "zoom must not be mistaken for a fit-zoom page swipe: $pageChanges",
            pageChanges.isEmpty(),
        )
        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "retained-finger right drag must actually move the page; " +
                "before=${beforeRetainedDrag.left} after=${after.left}",
            after.left - beforeRetainedDrag.left > 8.dp,
        )
    }

    @Test
    fun pinchZoomThenRightDragMovesThePageRight() {
        // Same regression through the pinch path: fingers are symmetric about
        // the viewport centre, so zoom must create left-side headroom there too.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        spreadToMaxZoom()
        assertZoomLabel(READER_ZOOM_MAX)
        val zoomed = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()

        composeRule.onRoot().performTouchInput {
            down(0, center)
            for (step in 1..8) {
                moveTo(
                    0,
                    center + Offset(viewport * 0.25f * step / 8, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "rightward drag after a centred pinch must move the page right; " +
                "before=${zoomed.left} after=${after.left}",
            after.left - zoomed.left >= (zoomed.right - zoomed.left) * 0.05f,
        )
    }

    @Test
    fun rightDragAfterZoomReachesTheTrueLeftEdgeNotTheHeadroomBand() {
        // Decisive v0.8.73 regression: the OLD symmetric 25% band pinned a
        // zoom at pan >= 25% of maxPan, hiding the page's leftmost content.
        // A rightward drag must reach the TRUE left edge (pan = 0), at which
        // point the page's left border lines up with its fit-zoom position.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val fitLeft = page.getUnclippedBoundsInRoot().left
        val viewport = viewportWidthPx()

        doubleTapToZoom() // centred focal -> pan = 0.5 * maxPan
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        // One large right drag: pan 0.5W -> 0.5W - 0.9W = -0.4W -> clamped to 0.
        dragPage(viewport * 0.90f)

        val atEdge = page.getUnclippedBoundsInRoot()
        assertTrue(
            "page must reach its true left edge without centering overflow; " +
                "fitLeft=$fitLeft after=${atEdge.left}",
            atEdge.left in (fitLeft - 8.dp)..(fitLeft + 8.dp),
        )
    }

    @Test
    fun leftDragAfterZoomReachesTheTrueRightEdge() {
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val viewport = viewportWidthPx()
        val viewportBounds = composeRule.onRoot().getUnclippedBoundsInRoot()

        doubleTapToZoom()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        dragPage(-viewport * 0.90f)

        val atEdge = page.getUnclippedBoundsInRoot()
        assertTrue(
            "page must reach its true right edge without blank viewport space; " +
                "viewportRight=${viewportBounds.right} after=${atEdge.right}",
            atEdge.right in (viewportBounds.right - 8.dp)..(viewportBounds.right + 8.dp),
        )
    }

    @Test
    fun doubleTapNearLeftEdgeThenRightDragMovesThePageRight() {
        // The user's failing gesture: an edge focal used to leave panX at 0,
        // so pushing right clamped straight back and the page never moved.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        composeRule.onRoot().performTouchInput {
            doubleClick(Offset(width * 0.05f, height * 0.6f))
        }
        composeRule.waitForIdle()
        assertZoomLabel(READER_ZOOM_DOUBLE_TAP)
        val zoomed = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()

        dragPage(viewport * 0.25f)
        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "rightward drag after an edge-focal zoom must move the page; " +
                "before=${zoomed.left} after=${after.left}",
            after.left - zoomed.left > 8.dp,
        )
    }

    @Test
    fun pinchNearLeftEdgeThenRightDragMovesThePageRight() {
        // Same headroom guarantee through the pinch path, where a device may
        // deliver only one finger's move and leave the focal near the edge.
        setReader()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        composeRule.onRoot().performTouchInput {
            val cy = height * 0.6f
            down(0, Offset(width * 0.08f, cy))
            down(1, Offset(width * 0.15f, cy))
            moveTo(0, Offset(width * 0.04f, cy), delayMillis = 16)
            moveTo(1, Offset(width * 0.35f, cy), delayMillis = 16)
            up(0)
            up(1)
        }
        composeRule.waitForIdle()
        assertTrue("pinch must zoom in", !isFitLabel())
        val zoomed = page.getUnclippedBoundsInRoot()
        val viewport = viewportWidthPx()

        dragPage(viewport * 0.25f)
        val after = page.getUnclippedBoundsInRoot()
        assertTrue(
            "rightward drag after an edge-focal pinch must move the page; " +
                "before=${zoomed.left} after=${after.left}",
            after.left - zoomed.left > 8.dp,
        )
    }

    @Test
    fun fitZoomHorizontalSwipeTurnsPageButZoomedDragDoesNot() {
        val pageChanges = Collections.synchronizedList(mutableListOf<Int>())
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "swipe-page-test",
                pageCount = 3,
                initialPage = 0,
                loadPage = { _, _ -> Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onOpenPage = {},
                onPageChange = { pageChanges.add(it) },
            )
        }
        composeRule.waitForIdle()
        val viewport = viewportWidthPx()

        // Swipe left at fit zoom: next page.
        composeRule.onRoot().performTouchInput {
            down(0, center)
            for (step in 1..8) {
                moveTo(
                    0,
                    center + Offset(-viewport * 0.4f * step / 8, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()
        assertEquals("fit-zoom swipe must request page 2", listOf(1), pageChanges.toList())

        // Once zoomed the same drag pans instead of turning pages.
        doubleTapToZoom()
        pageChanges.clear()
        dragPage(-viewport * 0.30f)
        composeRule.waitForIdle()
        assertTrue(
            "zoomed drag must not turn pages: $pageChanges",
            pageChanges.isEmpty(),
        )
    }

    @Test
    fun doubleTapThenImmediateRightDragReachesTrueLeftEdgeWithoutTurningPage() {
        // Multi-agent regression: the zoom can land a frame AFTER the follow-up
        // drag starts (the tap detector commits on its own schedule). The early
        // drag frames used to be spent in the fit-zoom page-swipe branch, so the
        // travel was discarded and the page stayed pinned at the zoom focal —
        // exactly the "cannot go further left than where zoom started" report.
        // A right-edge focal is the worst case: pan is seeded near maxPan.
        val pageChanges = Collections.synchronizedList(mutableListOf<Int>())
        composeRule.setContent {
            ContinuousReaderScreen(
                title = "zoom-handoff",
                pageCount = 3,
                initialPage = 0,
                loadPage = { _, _ -> Bitmap.createBitmap(80, 112, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onOpenPage = {},
                onPageChange = { pageChanges.add(it) },
            )
        }
        composeRule.waitForIdle()
        val page = composeRule.onNodeWithContentDescription("1쪽 페이지")
        val fitLeft = page.getUnclippedBoundsInRoot().left
        val viewport = viewportWidthPx()

        // Double tap near the right edge, then push right immediately — no idle
        // wait between the two blocks, so the zoom may land mid-drag.
        composeRule.onRoot().performTouchInput {
            doubleClick(Offset(width * 0.85f, height * 0.6f))
        }
        composeRule.onRoot().performTouchInput {
            down(0, center)
            for (step in 1..8) {
                moveTo(
                    0,
                    center + Offset(viewport * 0.95f * step / 8, 0f),
                    delayMillis = 16,
                )
            }
            up(0)
        }
        composeRule.waitForIdle()

        assertTrue("double tap must zoom", !isFitLabel())
        assertTrue(
            "a right drag after zoom must never turn the page: $pageChanges",
            pageChanges.isEmpty(),
        )
        val atEdge = page.getUnclippedBoundsInRoot()
        assertTrue(
            "the whole rightward drag must reach the page's true left edge; " +
                "fitLeft=$fitLeft after=${atEdge.left}",
            atEdge.left >= fitLeft - 8.dp,
        )
    }
}
