package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PenStudioE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun penStudioShowsLivePressurePreviewAndUpdatesResponse() {
        var selectedCurve = PressureCurve.LINEAR
        var pressureEnabled = true
        composeRule.setContent {
            NoteStyleSheet(
                tool = Tool.PEN,
                color = 0xFF1B1C19.toInt(),
                onColor = {},
                strokeWidth = 0.006f,
                onStrokeWidth = {},
                fontSize = 0.028f,
                onFontSize = {},
                opacity = 0.9f,
                onOpacity = {},
                penStyle = PenStyle.FOUNTAIN,
                onPenStyle = {},
                pressureInk = pressureEnabled,
                onPressureInk = { pressureEnabled = it },
                pressureCurve = selectedCurve,
                onPressureCurve = { selectedCurve = it },
                highlightWidth = 0.02f,
                onHighlightWidth = {},
                highlightOpacity = 0.75f,
                onHighlightOpacity = {},
                onOpenPenBox = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("펜 설정").assertExists()
        composeRule.onNodeWithContentDescription("필압 미리보기. 보통 반응.").assertExists()
        composeRule.onNodeWithText("불투명도").assertExists()
        composeRule.onNodeWithText("민감").performClick()
        composeRule.waitForIdle()
        assertEquals(PressureCurve.SOFT, selectedCurve)
    }

    @Test
    fun selectedPenArtworkTracksPenStyleChanges() {
        var penStyle by mutableStateOf(PenStyle.BALLPOINT)
        composeRule.setContent {
            NoteStyleSheet(
                tool = Tool.PEN,
                color = 0xFF1B1C19.toInt(),
                onColor = {},
                strokeWidth = 0.006f,
                onStrokeWidth = {},
                fontSize = 0.028f,
                onFontSize = {},
                opacity = 0.9f,
                onOpacity = {},
                penStyle = penStyle,
                onPenStyle = { penStyle = it },
                pressureInk = true,
                onPressureInk = {},
                pressureCurve = PressureCurve.LINEAR,
                onPressureCurve = {},
                highlightWidth = 0.02f,
                onHighlightWidth = {},
                highlightOpacity = 0.75f,
                onHighlightOpacity = {},
                onOpenPenBox = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithContentDescription("선택한 펜 볼펜").assertExists()
        composeRule.onNodeWithContentDescription("연필").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("선택한 펜 연필").assertExists()
        composeRule.onNodeWithContentDescription("연필, 선택됨").assertExists()
    }

    @Test
    fun highlighterStudioExposesIndependentOpacityAndNoPenType() {
        composeRule.setContent {
            NoteStyleSheet(
                tool = Tool.HIGHLIGHT,
                color = 0xFFF2C94C.toInt(),
                onColor = {},
                strokeWidth = 0.006f,
                onStrokeWidth = {},
                fontSize = 0.028f,
                onFontSize = {},
                opacity = 0.9f,
                onOpacity = {},
                penStyle = PenStyle.BALLPOINT,
                onPenStyle = {},
                pressureInk = true,
                onPressureInk = {},
                pressureCurve = PressureCurve.LINEAR,
                onPressureCurve = {},
                highlightWidth = 0.026f,
                onHighlightWidth = {},
                highlightOpacity = 0.55f,
                onHighlightOpacity = {},
                onOpenPenBox = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("펜 설정").assertExists()
        composeRule.onNodeWithText("불투명도").assertExists()
        composeRule.onNodeWithText("55%").assertExists()
        composeRule.onNodeWithText("펜 종류").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("선택한 펜 볼펜").assertDoesNotExist()
    }
}
