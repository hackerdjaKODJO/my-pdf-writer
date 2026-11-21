package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.CanvasClipRect
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.CropPoint
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import com.hackerdjakodjo.mypdfwriter.pdf.SearchMatchLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 0.8.38 view-only crop regressions plus the search-highlight query/pan policies
 * that share the same page-space transform. The clip assertions are pure; the
 * composable checks use a square synthetic page so normalized hit-tests are exact.
 */
class PdfCropAndPanE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun canvasClipRectIsCentredForIdentityAndAsymmetricInsets() {
        val identity = CropInsets.NONE
        assertTrue(PdfCrop.isIdentity(identity))
        assertEquals(
            CanvasClipRect(-500f, -400f, 500f, 400f),
            PdfCrop.clipRectForCanvas(PdfCrop.visibleRect(identity), 1000f, 800f),
        )

        val cropped = CropInsets(left = 0.10f, top = 0.20f, right = 0.30f, bottom = 0.40f)
        assertFalse(PdfCrop.isIdentity(cropped))
        assertEquals(
            NormalizedRect(0.10f, 0.20f, 0.70f, 0.60f),
            PdfCrop.visibleRect(cropped),
        )
        // Width 0.60 * 1000 = 600px, height 0.40 * 800 = 320px, both centred on
        // the local origin after PdfCanvas translates the kept crop centre there.
        assertEquals(
            CanvasClipRect(-300f, -160f, 300f, 160f),
            PdfCrop.clipRectForCanvas(PdfCrop.visibleRect(cropped), 1000f, 800f),
        )

        // Clamping still leaves a positive window and a centred clip.
        val clamped = PdfCrop.clampInsets(CropInsets(2f, -1f, 2f, Float.NaN))
        assertEquals(0.9f, clamped.left + clamped.right, 0.0001f)
        assertEquals(0f, clamped.top)
        assertEquals(0f, clamped.bottom)
        val clipped = PdfCrop.clipRectForCanvas(PdfCrop.visibleRect(clamped), 1000f, 800f)
        assertEquals(-50f, clipped.left, 0.0001f)
        assertEquals(50f, clipped.right, 0.0001f)
    }

    @Test
    fun centreTapWithCropMapsToDisplayToPageCentre() {
        val crop = CropInsets(left = 0.10f, top = 0.20f, right = 0.30f, bottom = 0.40f)
        var requested: NormalizedPoint? = null

        composeRule.setContent {
            PdfCanvas(
                bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
                docId = "crop-e2e",
                pageIndex = 0,
                displayWidthPt = 595f,
                displayHeightPt = 595f,
                annotations = emptyList(),
                tool = Tool.TEXT,
                color = 0xFF1F4E79.toInt(),
                strokeWidth = 0.0035f,
                fontSize = 0.024f,
                opacity = 1f,
                zoom = 1f,
                cropInsets = crop,
                onZoomChange = {},
                onAddStroke = { _, _ -> },
                onEraseStroke = {},
                onRequestText = { requested = it },
                onRequestStamp = {},
                onRequestSignature = {},
                modifier = Modifier.fillMaxSize().testTag(CROP_CANVAS_TAG),
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CROP_CANVAS_TAG).performTouchInput {
            down(center)
            up()
        }
        composeRule.waitForIdle()

        val expected: CropPoint = PdfCrop.displayToPage(0.5f, 0.5f, crop)
        assertEquals(0.40f, expected.x, 0.0001f)
        assertEquals(0.40f, expected.y, 0.0001f)
        val point = requireNotNull(requested) { "centre tap must reach the hit-test callback" }
        assertEquals("centre tap maps through displayToPage(0.5, 0.5)", expected.x, point.x, 0.02f)
        assertEquals("centre tap maps through displayToPage(0.5, 0.5)", expected.y, point.y, 0.02f)
    }

    @Test
    fun panResetsWhenDocIdChangesWhilePageIndexStaysZero() {
        val crop = CropInsets(left = 0.10f, top = 0.20f, right = 0.30f, bottom = 0.40f)
        val expected = PdfCrop.displayToPage(0.5f, 0.5f, crop)
        assertNotEquals(
            CanvasPageKey("doc-a", 0),
            CanvasPageKey("doc-b", 0),
        )

        var docId by mutableStateOf("doc-a")
        var requested: NormalizedPoint? = null
        composeRule.setContent {
            PdfCanvas(
                bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
                docId = docId,
                pageIndex = 0,
                displayWidthPt = 595f,
                displayHeightPt = 595f,
                annotations = emptyList(),
                tool = Tool.VIEW,
                color = 0xFF1F4E79.toInt(),
                strokeWidth = 0.0035f,
                fontSize = 0.024f,
                opacity = 1f,
                zoom = 2f,
                cropInsets = crop,
                onZoomChange = {},
                onAddStroke = { _, _ -> },
                onEraseStroke = {},
                onRequestText = {},
                onRequestStamp = {},
                onRequestSignature = {},
                onRequestSelectionAt = { requested = it },
                modifier = Modifier.fillMaxSize().testTag(PAN_CANVAS_TAG),
            )
        }
        composeRule.waitForIdle()

        fun centrePoint(): NormalizedPoint {
            requested = null
            composeRule.onNodeWithTag(PAN_CANVAS_TAG).performTouchInput { longClick(center) }
            composeRule.waitForIdle()
            return requireNotNull(requested) { "long press must reach the page hit-test" }
        }

        val beforePan = centrePoint()
        assertEquals(expected.x, beforePan.x, 0.02f)
        assertEquals(expected.y, beforePan.y, 0.02f)

        composeRule.onNodeWithTag(PAN_CANVAS_TAG).performTouchInput {
            down(center)
            moveTo(center + Offset(160f, 80f))
            up()
        }
        composeRule.waitForIdle()
        val afterPan = centrePoint()
        assertTrue(
            "drag must move the page under a fixed screen point",
            kotlin.math.abs(afterPan.x - beforePan.x) > 0.01f,
        )

        // Same page index, different document: the LaunchedEffect keyed by
        // CanvasPageKey(docId, pageIndex) must drop the old document's pan.
        composeRule.runOnIdle { docId = "doc-b" }
        composeRule.waitForIdle()
        val afterSwitch = centrePoint()
        assertEquals("doc switch must reset pan on page 0", expected.x, afterSwitch.x, 0.02f)
        assertEquals("doc switch must reset pan on page 0", expected.y, afterSwitch.y, 0.02f)
    }

    @Test
    fun searchHighlightUsesTrimmedQueryAndStaleHitsAreClearedImmediately() {
        val raw = "  주석  "
        val query = viewerHighlightQuery(raw)
        assertEquals("주석", query)
        assertEquals("hello  world", viewerHighlightQuery("\n\t hello  world \n"))
        assertEquals("", viewerHighlightQuery("   "))

        // The overlay and the ViewModel search both normalize the same way, so a
        // padded query cannot highlight results the result list never reports.
        val pages = listOf("주석이 있는 쪽", "빈 페이지", "주석 또 다른 쪽")
        assertEquals(
            SearchNavigation.hitsForPages(pages, "주석"),
            SearchNavigation.hitsForPages(pages, query),
        )
        assertEquals(2, SearchNavigation.hitsForPages(pages, query).size)

        // A blank query clears the overlay before any debounce can publish hits.
        assertTrue(SearchMatchLayout.matches(emptyList(), viewerHighlightQuery("  ")).isEmpty())
        assertTrue(SearchMatchLayout.matchesForPage(emptyList(), 0, "").isEmpty())

        // A new query drops the previous query's hit index instead of carrying it.
        val staleHits = SearchNavigation.hitsForPages(pages, "주석")
        val staleIndex = SearchNavigation.clamp(1, staleHits.size)
        assertEquals(1, staleIndex)
        assertEquals(
            0,
            SearchNavigation.indexAfterQueryChange(staleIndex, "주석", "  다른 쪽  ", staleHits.size),
        )
        assertEquals(-1, SearchNavigation.clamp(staleIndex, 0))
        assertEquals(
            staleIndex,
            SearchNavigation.indexAfterQueryChange(staleIndex, "주석", "주석", staleHits.size),
        )
    }

    private companion object {
        const val CROP_CANVAS_TAG = "pdf_crop_canvas_e2e"
        const val PAN_CANVAS_TAG = "pdf_pan_canvas_e2e"
    }
}
