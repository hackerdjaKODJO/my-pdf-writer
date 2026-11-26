package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Device-only: visible style action and long-press pen/highlight both open style. */
class ViewerDockLongPressE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<Tool>()
    private val longPressed = mutableListOf<Tool>()
    private val appliedFavorites = mutableListOf<PenPreset>()
    private var registryOpened = 0

    private fun render(initial: Tool = Tool.PEN) {
        selected.clear()
        longPressed.clear()
        appliedFavorites.clear()
        registryOpened = 0
        composeRule.setContent {
            var active by remember { mutableStateOf(initial) }
            var penStyle by remember { mutableStateOf(PenStyle.BALLPOINT) }
            var sheet by remember { mutableStateOf(false) }
            val favorite = remember { PenPreset(id = "e2e-favorite", name = "시험 펜") }
            MaterialTheme {
                Column {
                    ViewerContextActionRow(
                        state = ViewerUiState(tool = active),
                        onStyle = { sheet = true },
                        onUndo = {},
                        onRedo = {},
                    )
                    ViewerToolDock(
                        state = ViewerUiState(tool = active, penStyle = penStyle),
                        onSelectTool = {
                            selected.add(it)
                            active = it
                        },
                        onLongPressTool = { tool ->
                            longPressed.add(tool)
                            active = tool
                            sheet = true
                        },
                        onTextSelect = {},
                        onNote = {},
                    )
                }
                if (sheet) {
                    ViewerStyleSheet(
                        state = ViewerUiState(tool = active, penStyle = penStyle),
                        onColor = {},
                        onStrokeWidth = {},
                        onFontSize = {},
                        onOpacity = {},
                        onPenStyle = { penStyle = it },
                        onHighlightWidth = {},
                        onDismiss = { sheet = false },
                        favorites = listOf(favorite),
                        currentPen = favorite,
                        onApplyFavorite = { appliedFavorites.add(it) },
                        onOpenPenRegistry = { registryOpened += 1 },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun penDockArtworkAndPortraitTrackSelectedStyle() {
        render(Tool.PEN)
        composeRule.onNodeWithContentDescription("펜")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "볼펜"))
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).performClick()
        composeRule.onNodeWithContentDescription("연필").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("펜")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "연필"))
        composeRule.onNodeWithContentDescription("선택한 펜 연필").assertExists()
    }

    @Test
    fun styleActionIsVisibleForWritingToolsAndHiddenForView() {
        render(Tool.VIEW)
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("펜").performClick()
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).assertExists().performClick()
        composeRule.waitUntil("style sheet", 4_000L) {
            composeRule.onAllNodesWithText("펜 종류").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(listOf(Tool.PEN), selected)
    }

    @Test
    fun longPressPenOpensItsStyleSheetWithoutSelectingTwice() {
        render(Tool.PEN)
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).assertExists()
        composeRule.onNodeWithContentDescription("펜").performTouchInput { longClick(center) }
        composeRule.waitUntil("style sheet", 4_000L) {
            composeRule.onAllNodesWithText("펜 종류").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("즐겨찾는 펜").assertDoesNotExist()
        assertEquals(listOf(Tool.PEN), longPressed)
        assertTrue("long press must not also select the tool: $selected", selected.isEmpty())
    }

    @Test
    fun favoritePensBelowAnnotationToolsAreGone() {
        render(Tool.PEN)
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).performClick()
        composeRule.waitUntil("style sheet", 4_000L) {
            composeRule.onAllNodesWithText("펜 종류").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("즐겨찾는 펜").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("즐겨찾는 펜 시험 펜, 볼펜, 선택됨")
            .assertDoesNotExist()
    }

    @Test
    fun shortClickSelectsHighlightAndDoesNotOpenTheStyleSheet() {
        render(Tool.PEN)
        composeRule.onNodeWithContentDescription("형광펜").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(Tool.HIGHLIGHT), selected)
        assertTrue(longPressed.isEmpty())
        composeRule.onNodeWithTag(PDF_STYLE_ACTION_TAG).assertExists()
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
        composeRule.onNodeWithText("즐겨찾는 펜").assertDoesNotExist()
        assertEquals(listOf(Tool.HIGHLIGHT), longPressed)
    }
}
