package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose checks for the floating split chrome. These compile with
 * `assembleDebugAndroidTest`; running them requires a device.
 */
class PdfSplitChromeE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun floatingToolbarRoutesEveryPdfControl() {
        var previous = 0
        var next = 0
        var pages = 0
        var selection = 0
        var bookmark = 0
        var tools = 0
        var hub = 0

        composeRule.setContent {
            MaterialTheme {
                SplitPdfToolbar(
                    pageIndex = 2,
                    pageCount = 12,
                    bookmarked = false,
                    textSelectMode = false,
                    textLoading = false,
                    hasWords = true,
                    onPrevious = { previous += 1 },
                    onNext = { next += 1 },
                    onOpenPages = { pages += 1 },
                    onToggleTextSelection = { selection += 1 },
                    onToggleBookmark = { bookmark += 1 },
                    onOpenTools = { tools += 1 },
                    onOpenHub = { hub += 1 },
                )
            }
        }

        composeRule.onNodeWithText("3 / 12").assertExists()
        composeRule.onNodeWithContentDescription("이전 페이지").performClick()
        composeRule.onNodeWithContentDescription("다음 페이지").performClick()
        composeRule.onNodeWithContentDescription("페이지 선택").performClick()
        composeRule.onNodeWithContentDescription("텍스트 선택").performClick()
        composeRule.onNodeWithContentDescription("현재 페이지 북마크").performClick()
        composeRule.onNodeWithContentDescription("PDF 도구").performClick()
        composeRule.onNodeWithContentDescription("PDF 허브").performClick()

        assertEquals(1, previous)
        assertEquals(1, next)
        assertEquals(1, pages)
        assertEquals(1, selection)
        assertEquals(1, bookmark)
        assertEquals(1, tools)
        assertEquals(1, hub)
    }

    @Test
    fun selectionBarKeepsMarkupCopyAndMoreReachable() {
        var highlight = 0
        var underline = 0
        var strikeout = 0
        var copy = 0
        var dictionary = 0
        var editText = 0
        var more = 0
        var dismiss = 0

        composeRule.setContent {
            MaterialTheme {
                SplitSelectionActionBar(
                    selectedText = "선택한 텍스트",
                    pageIndex = 4,
                    hasSelection = true,
                    loading = false,
                    hasWords = true,
                    wordCount = 12,
                    onCopy = { copy += 1 },
                    onDictionary = { dictionary += 1 },
                    onEditText = { editText += 1 },
                    onHighlight = { highlight += 1 },
                    onUnderline = { underline += 1 },
                    onStrikeout = { strikeout += 1 },
                    onMore = { more += 1 },
                    onDismiss = { dismiss += 1 },
                )
            }
        }

        composeRule.onNodeWithText("7자").assertExists()
        composeRule.onNodeWithContentDescription("형광펜 표시").performClick()
        composeRule.onNodeWithContentDescription("밑줄 표시").performClick()
        composeRule.onNodeWithContentDescription("취소선 표시").performClick()
        composeRule.onNodeWithContentDescription("선택한 텍스트 복사").performClick()
        composeRule.onNodeWithContentDescription("사전에서 찾기").performClick()
        composeRule.onNodeWithContentDescription("선택한 텍스트 수정").performClick()
        composeRule.onNodeWithContentDescription("선택 도구 더보기").performClick()
        composeRule.onNodeWithContentDescription("선택 해제").performClick()

        assertEquals(1, highlight)
        assertEquals(1, underline)
        assertEquals(1, strikeout)
        assertEquals(1, copy)
        assertEquals(1, dictionary)
        assertEquals(1, editText)
        assertEquals(1, more)
        assertEquals(1, dismiss)
    }

    @Test
    fun emptySelectionBarReportsTheStateAndDisablesMarkup() {
        composeRule.setContent {
            MaterialTheme {
                SplitSelectionActionBar(
                    selectedText = "",
                    pageIndex = 0,
                    hasSelection = false,
                    loading = false,
                    hasWords = false,
                    wordCount = 0,
                    onCopy = {},
                    onHighlight = {},
                    onUnderline = {},
                    onStrikeout = {},
                    onMore = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("없음").assertExists()
        composeRule.onNodeWithContentDescription("형광펜 표시").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("사전에서 찾기").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("선택한 텍스트 수정").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("텍스트 선택 종료").assertExists()
    }
}
