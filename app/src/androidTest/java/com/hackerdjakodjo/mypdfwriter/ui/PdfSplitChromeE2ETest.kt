package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
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
        var search = 0
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
                    onOpenSearch = { search += 1 },
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
        composeRule.onNodeWithContentDescription("검색").performClick()
        composeRule.onNodeWithContentDescription("텍스트 선택").performClick()
        composeRule.onNodeWithContentDescription("현재 페이지 북마크").performClick()
        composeRule.onNodeWithContentDescription("주석").performClick()
        composeRule.onNodeWithContentDescription("학습 메뉴").performClick()

        assertEquals(1, previous)
        assertEquals(1, next)
        assertEquals(1, pages)
        assertEquals(1, search)
        assertEquals(1, selection)
        assertEquals(1, bookmark)
        assertEquals(1, tools)
        assertEquals(1, hub)
    }

    @Test
    fun compactToolbarKeepsSearchAndPageNavigationButHidesSecondaryTools() {
        var previous = 0
        var next = 0
        var pages = 0
        var search = 0
        var selection = 0
        var bookmark = 0
        var tools = 0
        var hub = 0

        composeRule.setContent {
            MaterialTheme {
                SplitPdfToolbar(
                    pageIndex = 3,
                    pageCount = 9,
                    bookmarked = true,
                    textSelectMode = false,
                    textLoading = false,
                    hasWords = true,
                    onPrevious = { previous += 1 },
                    onNext = { next += 1 },
                    onOpenPages = { pages += 1 },
                    onToggleTextSelection = { selection += 1 },
                    onOpenSearch = { search += 1 },
                    onToggleBookmark = { bookmark += 1 },
                    onOpenTools = { tools += 1 },
                    onOpenHub = { hub += 1 },
                    compact = true,
                )
            }
        }

        composeRule.onNodeWithText("4 / 9").assertExists()
        composeRule.onNodeWithContentDescription("이전 페이지").performClick()
        composeRule.onNodeWithContentDescription("다음 페이지").performClick()
        composeRule.onNodeWithContentDescription("페이지 선택").performClick()
        // The default 85% study pane is ESSENTIAL/compact; search must survive it.
        composeRule.onNodeWithContentDescription("검색").performClick()
        listOf("텍스트 선택", "현재 페이지 북마크", "주석", "학습 메뉴").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsNotDisplayed()
        }

        assertEquals(1, previous)
        assertEquals(1, next)
        assertEquals(1, pages)
        assertEquals(1, search)
        assertEquals(0, selection)
        assertEquals(0, bookmark)
        assertEquals(0, tools)
        assertEquals(0, hub)
    }

    @Test
    fun splitBackButtonDispatchesClose() {
        var back = 0
        composeRule.setContent {
            MaterialTheme { SplitPdfBackButton(onBack = { back += 1 }) }
        }

        composeRule.onNodeWithContentDescription("PDF 닫기").assertIsEnabled().performClick()
        assertEquals(1, back)
    }

    @Test
    fun dividerShowsPercentAndRoutesThePdfToolsChip() {
        var toggled = 0
        composeRule.setContent {
            MaterialTheme {
                SplitDivider(
                    paneFraction = 0.70f,
                    totalHeightPx = 1000f,
                    pdfToolsVisible = true,
                    onFractionChange = {},
                    onPresetSelected = {},
                    onTogglePdfTools = { toggled += 1 },
                )
            }
        }

        composeRule.onNodeWithText("노트 70%").assertExists()
        composeRule
            .onNodeWithContentDescription("노트 크기 조절 핸들, 현재 70%, 탭하면 크기 선택")
            .assertExists()
        composeRule.onNodeWithContentDescription("PDF 도구 숨기기").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun hiddenPdfToolsChipAnnouncesItsRestoreAction() {
        var toggled = 0
        composeRule.setContent {
            MaterialTheme {
                SplitDivider(
                    paneFraction = 0.70f,
                    totalHeightPx = 1000f,
                    pdfToolsVisible = false,
                    onFractionChange = {},
                    onPresetSelected = {},
                    onTogglePdfTools = { toggled += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("PDF 도구 표시").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun dividerTapOpensThePresetPopupAndAppliesAChoice() {
        var selected = -1f
        composeRule.setContent {
            MaterialTheme {
                SplitDivider(
                    paneFraction = 0.70f,
                    totalHeightPx = 1000f,
                    pdfToolsVisible = false,
                    onFractionChange = {},
                    onPresetSelected = { selected = it },
                    onTogglePdfTools = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("노트 크기 조절 핸들, 현재 70%, 탭하면 크기 선택")
            .performClick()
        composeRule.onNodeWithContentDescription("노트 크기 50%").performClick()
        assertEquals(0.50f, selected, 0.0001f)
    }

    @Test
    fun presetRowsDispatchEveryPresetWithDescriptions() {
        val picked = mutableListOf<Float>()
        composeRule.setContent {
            MaterialTheme {
                StudyPaneSizePresets(
                    currentFraction = 0.70f,
                    onPresetSelected = { picked += it },
                )
            }
        }

        listOf(0.50f, 0.70f, 0.85f).forEach { preset ->
            val label = "${(preset * 100).toInt()}%"
            composeRule.onNodeWithText(label).assertExists()
            composeRule.onNodeWithContentDescription("노트 크기 $label").performClick()
        }
        assertEquals(listOf(0.50f, 0.70f, 0.85f), picked)
    }

    @Test
    fun selectionBarKeepsMarkupCopyExcerptAndMoreReachable() {
        var highlight = 0
        var underline = 0
        var strikeout = 0
        var copy = 0
        var excerpt = 0
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
                    onExcerpt = { excerpt += 1 },
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
        composeRule.onNodeWithContentDescription("노트에 담기").performClick()
        composeRule.onNodeWithContentDescription("선택 도구 더보기").performClick()
        composeRule.onNodeWithContentDescription("선택 해제").performClick()

        assertEquals(1, highlight)
        assertEquals(1, underline)
        assertEquals(1, strikeout)
        assertEquals(1, copy)
        assertEquals(1, excerpt)
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
                    onExcerpt = {},
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
        composeRule.onNodeWithContentDescription("선택한 텍스트 복사").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("노트에 담기").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("선택 도구 더보기").assertExists()
        composeRule.onNodeWithContentDescription("텍스트 선택 종료").assertExists()
    }

    @Test
    fun moreSheetRoutesEverySecondarySelectionAction() {
        var translate = 0
        var dictionary = 0
        var excerpt = 0
        var edit = 0
        var share = 0
        var addToPane = 0
        var clear = 0
        var dismiss = 0

        composeRule.setContent {
            MaterialTheme {
                TextSelectionMoreSheet(
                    selectedText = "강의 노트",
                    pageIndex = 0,
                    hasSelection = true,
                    canAddToPane = true,
                    onTranslate = { translate += 1 },
                    onDictionary = { dictionary += 1 },
                    onExcerpt = { excerpt += 1 },
                    onEditText = { edit += 1 },
                    onShare = { share += 1 },
                    onAddToPane = { addToPane += 1 },
                    onDismissSelection = { clear += 1 },
                    onDismiss = { dismiss += 1 },
                )
            }
        }

        listOf("번역", "사전", "노트에 담기", "발췌로 저장", "텍스트 편집", "공유", "선택 해제")
            .forEach { composeRule.onNodeWithText(it).assertExists() }

        composeRule.onNodeWithText("번역").performClick()
        composeRule.onNodeWithText("사전").performClick()
        composeRule.onNodeWithText("노트에 담기").performClick()
        composeRule.onNodeWithText("발췌로 저장").performClick()
        composeRule.onNodeWithText("텍스트 편집").performClick()
        composeRule.onNodeWithText("공유").performClick()
        composeRule.onNodeWithText("선택 해제").performClick()

        assertEquals(1, translate)
        assertEquals(1, dictionary)
        assertEquals(1, addToPane)
        assertEquals(1, excerpt)
        assertEquals(1, edit)
        assertEquals(1, share)
        assertEquals(1, clear)
        assertEquals(7, dismiss)
    }

    @Test
    fun documentTabStripRoutesSwitchCloseOverflowAndHide() {
        var selected = ""
        var closed = ""
        var menu = 0
        var hidden = 0
        val tabs = (1..4).map { OpenDocumentTab("doc$it", "content://docs/$it", "$it.pdf") }

        composeRule.setContent {
            MaterialTheme {
                DocumentTabStrip(
                    state = DocumentTabsState(tabs = tabs, activeDocId = "doc1"),
                    onSelectTab = { selected = it.docId },
                    onCloseTab = { closed = it.docId },
                    onOpenMenu = { menu += 1 },
                    onHideStrip = { hidden += 1 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("1.pdf, 선택됨").assertExists().performClick()
        composeRule.onNodeWithContentDescription("탭 닫기: 1.pdf").performClick()
        composeRule.onNodeWithContentDescription("숨겨진 탭 1개").assertExists().performClick()
        composeRule.onNodeWithContentDescription("탭 메뉴").performClick()
        composeRule.onNodeWithContentDescription("탭 숨기기").performClick()

        assertEquals("doc1", selected)
        assertEquals("doc1", closed)
        assertEquals(2, menu)
        assertEquals(1, hidden)
    }

    @Test
    fun tabOverflowSheetKeepsEverySwitchAndManagementAction() {
        var switched = ""
        var others = 0
        var all = 0
        var bar = 0
        var autoHide = 0
        var recents = 0
        var locations = 0
        var dismissed = 0
        val tabs = (1..4).map { OpenDocumentTab("doc$it", "content://docs/$it", "$it.pdf") }

        composeRule.setContent {
            MaterialTheme {
                DocumentTabOverflowSheet(
                    tabs = tabs,
                    activeDocId = "doc1",
                    tabBarVisible = true,
                    autoHideMode = ChromeAutoHideMode.ON,
                    onSelectTab = { switched = it.docId },
                    onCloseOthers = { others += 1 },
                    onCloseAll = { all += 1 },
                    onToggleBar = { bar += 1 },
                    onToggleAutoHide = { autoHide += 1 },
                    onOpenRecents = { recents += 1 },
                    onOpenLocations = { locations += 1 },
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        composeRule.onNodeWithText("문서 탭").assertExists()
        listOf("다른 탭 닫기", "모든 탭 닫기", "탭 표시/숨기기", "메뉴 자동 숨김", "최근 문서", "위치 · 북마크/목차/주석")
            .forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithContentDescription("문서 탭 열기: 4.pdf").performClick()
        composeRule.onNodeWithText("다른 탭 닫기").performClick()
        composeRule.onNodeWithText("모든 탭 닫기").performClick()
        composeRule.onNodeWithText("탭 표시/숨기기").performClick()
        composeRule.onNodeWithText("메뉴 자동 숨김").performClick()
        composeRule.onNodeWithText("최근 문서").performClick()
        composeRule.onNodeWithText("위치 · 북마크/목차/주석").performClick()

        assertEquals("doc4", switched)
        assertEquals(1, others)
        assertEquals(1, all)
        assertEquals(1, bar)
        assertEquals(1, autoHide)
        assertEquals(1, recents)
        assertEquals(1, locations)
        assertEquals(0, dismissed)
    }

    @Test
    fun recentDocumentsSheetRoutesOpenAndBothChromeToggles() {
        var opened = ""
        var bar = 0
        var autoHide = 0
        var dismissed = 0
        val docs = listOf(
            RecentDoc("content://docs/1", "강의.pdf", 22, 300L),
            RecentDoc("content://docs/2", "참고.pdf", 9, 200L),
        )

        composeRule.setContent {
            MaterialTheme {
                RecentDocumentsSheet(
                    documents = recentDocumentsForSwitcher(docs),
                    activeUri = "content://docs/2",
                    tabBarVisible = false,
                    autoHideMode = ChromeAutoHideMode.OFF,
                    onOpenDocument = { opened = it.name },
                    onToggleTabBar = { bar += 1 },
                    onToggleAutoHide = { autoHide += 1 },
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        composeRule.onNodeWithText("최근 문서").assertExists()
        composeRule.onNodeWithContentDescription("최근 문서 열기: 강의.pdf").performClick()
        composeRule.onNodeWithContentDescription("최근 문서 열기: 참고.pdf").assertExists()
        composeRule.onNodeWithText("탭 표시/숨기기").performClick()
        composeRule.onNodeWithText("메뉴 자동 숨김").performClick()

        assertEquals("강의.pdf", opened)
        assertEquals(1, bar)
        assertEquals(1, autoHide)
        assertEquals(0, dismissed)
    }
}
