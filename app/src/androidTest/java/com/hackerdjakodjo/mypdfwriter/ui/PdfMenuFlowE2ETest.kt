package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose coverage for the 0.8.37 student-menu rewrite: four first-level 학습 메뉴
 * groups, one unified 위치 entry, the four-control top chrome, the self-dismissing
 * coach and the session-only document tab strip. Compiles with
 * `assembleDebugAndroidTest`; running the gestures needs a device/emulator.
 */
class PdfMenuFlowE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun learningMenuShowsFourOrderedGroupsAndOneLocationsEntry() {
        // Policy literals first: the composable below draws a compact version of
        // exactly this grouping so every group is visible in one sheet.
        assertEquals(PDF_HUB_MAX_TOP_LEVEL_GROUPS, 4)
        assertEquals(
            listOf("읽기", "필기", "담기", "설정"),
            pdfMenuTopLevelGroups.map { it.title },
        )
        assertEquals(4, pdfMenuTopLevelGroups.size)
        assertTrue(pdfMenuTopLevelGroups.none { it.expandable })

        val labels = PdfMenuAction.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        listOf("목차", "북마크 목록", "주석 목록").forEach { old ->
            assertFalse("'$old' must be folded into 위치", old in labels)
        }
        assertEquals(1, labels.count { it == PDF_HUB_LOCATIONS_LABEL })
        assertEquals(1, pdfMenuActions(PdfMenuGroup.READ).count { it == PdfMenuAction.LOCATIONS })

        var opened = 0
        var dismissed = 0
        val location = pdfLocationsAction { opened += 1 }
        val sections = listOf(
            PdfHubSection(pdfMenuTopLevelGroups[0].title, listOf(location)),
            PdfHubSection(pdfMenuTopLevelGroups[1].title, listOf(menuAction("주석 도구"))),
            PdfHubSection(pdfMenuTopLevelGroups[2].title, listOf(menuAction(PDF_HUB_CAPTURE_LABEL))),
            PdfHubSection(pdfMenuTopLevelGroups[3].title, listOf(menuAction("파일 도구"))),
        )

        composeRule.setContent {
            MaterialTheme {
                PdfHubSheet(
                    docName = "lecture.pdf",
                    pageIndex = 2,
                    pageCount = 10,
                    sections = sections,
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        pdfMenuTopLevelGroups.forEach { group ->
            composeRule.onNodeWithText(group.title).assertExists()
        }
        assertEquals(
            "위치 must be the only navigation entry in the composed hub",
            1,
            composeRule.onAllNodesWithText(PDF_HUB_LOCATIONS_LABEL).fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithText(PDF_HUB_LOCATIONS_LABEL).performClick()

        assertEquals(1, opened)
        assertEquals(1, dismissed)
    }

    @Test
    fun locationsSheetHasThreeTabsAndRoutesJumpAndSummaryExport() {
        assertEquals(
            listOf(LocationsTab.BOOKMARKS, LocationsTab.OUTLINE, LocationsTab.ANNOTATIONS),
            LOCATIONS_TAB_ORDER,
        )
        assertEquals(
            listOf("북마크", "목차", "주석"),
            LOCATIONS_TAB_ORDER.map { it.label },
        )

        var jumped = -1
        var exported = ""
        var dismissed = 0
        val bookmarks = listOf(PdfBookmark(id = "b1", pageIndex = 3, title = "3쪽 북마크"))
        val outline = listOf(PdfOutlineEntry("서론", 1, 0))
        val annotations = listOf(
            Annotation(
                type = AnnotationType.PEN,
                page = 2,
                color = 0xFF1F4E79.toInt(),
                width = 0.004f,
                points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.2f)),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                LocationsSheet(
                    bookmarks = bookmarks,
                    outline = outline,
                    annotations = annotations,
                    pageCount = 10,
                    currentPage = 0,
                    onJumpToPage = { jumped = it },
                    onExportSummary = { exported = it },
                    onDismiss = { dismissed += 1 },
                )
            }
        }

        LOCATIONS_TAB_ORDER.forEach { tab ->
            composeRule.onNodeWithText(tab.label).assertExists()
        }
        // 북마크 jump: one row, one page jump, then the panel closes.
        composeRule.onNodeWithText("3쪽 북마크").performClick()
        assertEquals(3, jumped)
        assertEquals(1, dismissed)

        // 목차 jump uses the same router.
        composeRule.onNodeWithText("목차").performClick()
        composeRule.onNodeWithText("서론").performClick()
        assertEquals(1, jumped)
        assertEquals(2, dismissed)

        // 주석 tab exports the offline Markdown summary; export keeps the sheet open.
        composeRule.onNodeWithText("주석").performClick()
        composeRule.onNodeWithText("주석 요약 내보내기").performClick()
        assertTrue("summary must describe the stored ink", exported.contains("필기"))
        assertEquals(2, dismissed)

        // The router is shared by both tabs and the export button.
        var routerJumped = -1
        var routerExported = ""
        var routerClosed = 0
        val actions = LocationsActions(
            onJumpToPage = { routerJumped = it },
            onDismiss = { routerClosed += 1 },
            onExportSummary = { routerExported = it },
        )
        actions.jump(7)
        actions.export("요약")
        actions.close()
        assertEquals(7, routerJumped)
        assertEquals("요약", routerExported)
        assertEquals(2, routerClosed)
    }

    @Test
    fun topChromeKeepsExactlyFourControlsAndCoachDismissFreesCanvasGestures() {
        assertEquals(PDF_TOP_CHROME_MAX_CONTROLS, 4)
        assertEquals(
            listOf("뒤로", "문서 이름", "검색", "학습 메뉴"),
            pdfTopChromeControls.map { it.label },
        )
        assertFalse(PdfMenuAction.SEARCH in pdfMenuActions(PdfMenuGroup.READ))
        assertEquals(setOf(PdfMenuAction.SEARCH), pdfTopBarOwnedActions)

        val firstVisit = PdfCoachState()
        assertTrue(shouldShowPdfCoach(firstVisit))
        assertFalse(shouldShowPdfCoach(dismissPdfCoach(firstVisit)))

        var dismissed = 0
        var strokes = 0
        var coachVisible by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag(COACH_ROOT_TAG)) {
                    pdfCanvasForCoach(onAddStroke = { strokes += 1 })
                    if (coachVisible) {
                        // Mirrors the production overlay: a small Surface at the
                        // top centre with its own TalkBack label, never a full-page
                        // pointer sink. Only its own bounds intercept touches.
                        Surface(
                            onClick = {
                                coachVisible = false
                                dismissed += 1
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .semantics { contentDescription = PDF_COACH_DISMISS_DESCRIPTION },
                        ) {
                            Text("① 열기 ② 표시 ③ 노트에 담기")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("① 열기 ② 표시 ③ 노트에 담기").assertExists()

        // A stroke in the page centre passes underneath the visible card.
        drawStroke()
        assertEquals("coach must not swallow canvas gestures", 1, strokes)
        assertEquals(0, dismissed)

        composeRule.onNodeWithContentDescription(PDF_COACH_DISMISS_DESCRIPTION).performClick()
        assertEquals(1, dismissed)
        assertFalse(coachVisible)

        // After dismissal the same gesture still reaches the canvas callback.
        drawStroke()
        assertEquals(2, strokes)
    }

    @Test
    fun twoDocumentTabsSwitchCloseHideAndOpenOverflowAndRecents() {
        val tabs = listOf(
            OpenDocumentTab("doc-a", "content://docs/a", "A.pdf"),
            OpenDocumentTab("doc-b", "content://docs/b", "B.pdf"),
        )
        val twoTabs = DocumentTabsState(tabs = tabs, activeDocId = "doc-a")
        assertEquals(2, visibleDocumentTabs(twoTabs).size)
        assertTrue(shouldShowDocumentTabStrip(twoTabs))
        assertEquals(MIN_TABS_FOR_STRIP, 2)

        var selected = ""
        var closed = ""
        var menu = 0
        var hidden = 0
        composeRule.setContent {
            MaterialTheme {
                DocumentTabStrip(
                    state = twoTabs,
                    onSelectTab = { selected = it.docId },
                    onCloseTab = { closed = it.docId },
                    onOpenMenu = { menu += 1 },
                    onHideStrip = { hidden += 1 },
                )
            }
        }

        // Tap the left side of the tab node: its right half is the nested 48dp
        // close target, and a centre click either closes or hits nothing.
        tapTabLeft("A.pdf, 선택됨")
        assertEquals("doc-a", selected)

        tapTabLeft("B.pdf")
        assertEquals("the last switch must be reported", "doc-b", selected)

        composeRule.onNodeWithContentDescription("탭 닫기: B.pdf").performClick()
        composeRule.onNodeWithContentDescription("탭 메뉴").performClick()
        composeRule.onNodeWithContentDescription("탭 숨기기").performClick()

        assertEquals("doc-b", selected)
        assertEquals("doc-b", closed)
        assertEquals(1, menu)
        assertEquals(1, hidden)

        assertFalse(shouldShowDocumentTabStrip(setDocumentTabBarVisible(twoTabs, false)))
    }

    @Test
    fun tabOverflowAndRecentsKeepEveryManagementAction() {
        val tabs = listOf(
            OpenDocumentTab("doc-a", "content://docs/a", "A.pdf"),
            OpenDocumentTab("doc-b", "content://docs/b", "B.pdf"),
        )
        var switched = ""
        var others = 0
        var all = 0
        var bar = 0
        var autoHide = 0
        var recents = 0
        var locations = 0

        composeRule.setContent {
            MaterialTheme {
                DocumentTabOverflowSheet(
                    tabs = tabs,
                    activeDocId = "doc-a",
                    tabBarVisible = true,
                    autoHideMode = ChromeAutoHideMode.ON,
                    onSelectTab = { switched = it.docId },
                    onCloseOthers = { others += 1 },
                    onCloseAll = { all += 1 },
                    onToggleBar = { bar += 1 },
                    onToggleAutoHide = { autoHide += 1 },
                    onOpenRecents = { recents += 1 },
                    onOpenLocations = { locations += 1 },
                    onDismiss = {},
                )
            }
        }

        listOf("다른 탭 닫기", "모든 탭 닫기", "탭 표시/숨기기", "메뉴 자동 숨김", "최근 문서", "위치")
            .forEach { composeRule.onNodeWithText(it).assertExists() }
        composeRule.onNodeWithContentDescription("문서 탭 열기: B.pdf").performClick()
        composeRule.onNodeWithText("위치").performClick()
        composeRule.onNodeWithText("최근 문서").performClick()
        composeRule.onNodeWithText("메뉴 자동 숨김").performClick()

        assertEquals("doc-b", switched)
        assertEquals(1, locations)
        assertEquals(1, recents)
        assertEquals(1, autoHide)
        assertEquals(0, others)
        assertEquals(0, all)
        assertEquals(0, bar)
    }

    @Test
    fun rapidDoubleTabSwitchNeverIssuesTwoOpens() {
        val tabs = listOf(
            OpenDocumentTab("doc-a", "content://docs/a", "A.pdf"),
            OpenDocumentTab("doc-b", "content://docs/b", "B.pdf"),
        )
        var activeDocId by mutableStateOf("doc-a")
        var opens = 0

        composeRule.setContent {
            MaterialTheme {
                DocumentTabStrip(
                    state = DocumentTabsState(tabs = tabs, activeDocId = activeDocId),
                    // Mirrors ViewerScreen.switchToTab: the active tab is not reopened.
                    onSelectTab = { tab ->
                        if (tab.docId != activeDocId) {
                            activeDocId = tab.docId
                            opens += 1
                        }
                    },
                    onCloseTab = {},
                    onOpenMenu = {},
                    onHideStrip = {},
                )
            }
        }

        // Tap the left side of the tab node (the right side is the close button),
        // then re-tap the already active tab twice; only the first tap may open.
        tapTabLeft("B.pdf")
        composeRule.onNodeWithContentDescription("B.pdf, 선택됨").assertExists()
        tapTabLeft("B.pdf, 선택됨")
        tapTabLeft("B.pdf, 선택됨")

        assertEquals("doc-b", activeDocId)
        assertEquals("the second rapid tap must not reopen the same document", 1, opens)

        // Even if two opens did slip through, the generation gate lets only the
        // newest result publish — the 0.8.38 open-race guard.
        val gate = OpenGenerationGate()
        val stale = gate.begin()
        val fresh = gate.begin()
        assertFalse(mayPublishOpenResult(true, stale, gate.current))
        assertTrue(mayPublishOpenResult(true, fresh, gate.current))
    }

    /**
     * Injects a tap near the left edge of a document tab. The tab node's centre
     * overlaps the nested 48dp close button, so a plain [performClick] at the
     * centre does not reach `onSelectTab` for short filenames.
     */
    private fun tapTabLeft(contentDescription: String) {
        composeRule.onNodeWithContentDescription(contentDescription).performTouchInput {
            click(Offset(width * 0.15f, height / 2f))
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun pdfCanvasForCoach(onAddStroke: () -> Unit) {
        PdfCanvas(
            bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888),
            docId = "coach-e2e",
            pageIndex = 0,
            displayWidthPt = 595f,
            displayHeightPt = 595f,
            annotations = emptyList(),
            tool = Tool.PEN,
            color = 0xFF1F4E79.toInt(),
            strokeWidth = 0.004f,
            fontSize = 0.024f,
            opacity = 1f,
            zoom = 1f,
            onZoomChange = {},
            onAddStroke = { _, _ -> onAddStroke() },
            onEraseStroke = {},
            onRequestText = {},
            onRequestStamp = {},
            onRequestSignature = {},
            modifier = Modifier.fillMaxSize().testTag(COACH_CANVAS_TAG),
        )
    }

    private fun drawStroke() {
        composeRule.onNodeWithTag(COACH_CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.30f, height * 0.55f))
            moveTo(Offset(width * 0.50f, height * 0.58f))
            moveTo(Offset(width * 0.70f, height * 0.55f))
            up()
        }
        composeRule.waitForIdle()
    }

    private fun menuAction(label: String): PdfHubAction = PdfHubAction(
        label = label,
        icon = menuActionIcon(),
        onClick = {},
    )

    private fun menuActionIcon(): ImageVector = Icons.Outlined.Search

    private companion object {
        const val COACH_ROOT_TAG = "pdf_coach_root"
        const val COACH_CANVAS_TAG = "pdf_coach_canvas"
    }
}
