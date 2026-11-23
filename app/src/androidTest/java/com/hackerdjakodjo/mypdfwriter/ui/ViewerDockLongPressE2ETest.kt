package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Device-only: long-press pen/highlight dock slots open that tool's style sheet. */
class ViewerDockLongPressE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<Tool>()
    private val longPressed = mutableListOf<Tool>()

    private fun render(initial: Tool = Tool.PEN) {
        selected.clear()
        longPressed.clear()
        composeRule.setContent {
            var sheet by remember { mutableStateOf(false) }
            MaterialTheme {
                ViewerToolDock(
                    state = ViewerUiState(tool = initial),
                    onSelectTool = { selected.add(it) },
                    onLongPressTool = { tool ->
                        longPressed.add(tool)
                        sheet = true
                    },
                    onTextSelect = {},
                    onNote = {},
                )
                if (sheet) {
                    val target = longPressed.lastOrNull() ?: initial
                    ViewerStyleSheet(
                        state = ViewerUiState(tool = target),
                        onColor = {},
                        onStrokeWidth = {},
                        onFontSize = {},
                        onOpacity = {},
                        onPenStyle = {},
                        onHighlightWidth = {},
                        onDismiss = { sheet = false },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun longPressPenOpensItsStyleSheetWithoutSelectingTwice() {
        render(Tool.PEN)
        composeRule.onNodeWithContentDescription("펜").performTouchInput { longClick(center) }
        composeRule.waitUntil("style sheet", 4_000L) {
            composeRule.onAllNodesWithText("펜 종류").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf(Tool.PEN), longPressed)
        assertTrue("long press must not also select the tool: $selected", selected.isEmpty())
    }

    @Test
    fun shortClickSelectsHighlightAndDoesNotOpenTheStyleSheet() {
        render(Tool.PEN)
        composeRule.onNodeWithContentDescription("형광펜").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(Tool.HIGHLIGHT), selected)
        assertTrue(longPressed.isEmpty())
        composeRule.onNodeWithText("펜 종류").assertDoesNotExist()
    }

    @Test
    fun longPressHighlightShowsWidthButNoPenTypePicker() {
        render(Tool.HIGHLIGHT)
        composeRule.onNodeWithContentDescription("형광펜")
            .performTouchInput { longClick(center) }
        composeRule.waitUntil("style sheet", 4_000L) {
            composeRule.onAllNodesWithText("형광펜 도구의 색상과 크기를 조정합니다.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("굵기").assertExists()
        composeRule.onNodeWithText("펜 종류").assertDoesNotExist()
        assertEquals(listOf(Tool.HIGHLIGHT), longPressed)
    }
}
