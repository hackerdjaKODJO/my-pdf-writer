package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

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
}
