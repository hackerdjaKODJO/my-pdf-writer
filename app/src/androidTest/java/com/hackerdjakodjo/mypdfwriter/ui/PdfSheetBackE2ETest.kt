package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * System Back must dismiss the topmost viewer layer instead of closing the PDF.
 * [ViewerScreen] builds [ViewerSheetFlags] once and registers
 * `BackHandler(enabled = !sheetOpen)`, so a flag forgotten there closes the whole
 * document while its sheet is still on screen. The 0.8.37 unified 위치 panel and
 * the 설정 → 파일 도구 sub-sheet are explicitly part of this contract.
 */
class PdfSheetBackE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everySheetFlagIncludingLocationsAndFileToolsInterceptsBack() {
        val idle = ViewerSheetFlags()
        assertFalse("idle viewer must let Back close the document", idle.anyOpen)

        val layers = listOf(
            idle.copy(showPages = true),
            idle.copy(showNotes = true),
            idle.copy(showBookmarks = true),
            idle.copy(showSearch = true),
            idle.copy(showAnnotations = true),
            idle.copy(showOrganize = true),
            idle.copy(showExcerpts = true),
            idle.copy(showContinuous = true),
            idle.copy(showSpread = true),
            idle.copy(showPopup = true),
            idle.copy(showAudioNotes = true),
            idle.copy(showAudioReplay = true),
            idle.copy(showStudyNote = true),
            idle.copy(showReflow = true),
            idle.copy(showReadAloud = true),
            idle.copy(showProperties = true),
            idle.copy(showStyle = true),
            idle.copy(showDisplaySettings = true),
            idle.copy(showHub = true),
            idle.copy(showReadModes = true),
            idle.copy(showAudioMenu = true),
            idle.copy(showToolMore = true),
            idle.copy(showToolStyle = true),
            idle.copy(showSelectionMore = true),
            idle.copy(showTextEditDialog = true),
            idle.copy(showMemoCardsSheet = true),
            idle.copy(showViewerPenBox = true),
            idle.copy(showRecentDocs = true),
            idle.copy(showTabOverflow = true),
            idle.copy(showLocations = true),
            idle.copy(showFileTools = true),
            idle.copy(textTargetOpen = true),
            idle.copy(stampTargetOpen = true),
            idle.copy(signatureTargetOpen = true),
        )
        assertEquals(34, layers.size)
        layers.forEachIndexed { index, flags ->
            assertTrue("viewer layer #$index must intercept Back", flags.anyOpen)
        }
        assertTrue(ViewerSheetFlags(showLocations = true).anyOpen)
        assertTrue(ViewerSheetFlags(showFileTools = true).anyOpen)
    }

    @Test
    fun backHandlerGateDefersClosingWhileLocationsOrFileToolsAreOpen() {
        var closed = 0
        var flags by mutableStateOf(ViewerSheetFlags(showLocations = true))
        composeRule.setContent {
            MaterialTheme {
                // The exact gate ViewerScreen registers around its BackHandler.
                BackHandler(enabled = !flags.anyOpen) { closed += 1 }
                Text("viewer")
            }
        }

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals("위치 sheet is open", 0, closed)

        composeRule.runOnIdle { flags = ViewerSheetFlags(showFileTools = true) }
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals("파일 도구 sheet is open", 0, closed)

        composeRule.runOnIdle { flags = ViewerSheetFlags() }
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals("idle viewer", 1, closed)
    }

    @Test
    fun unifiedLocationsSheetIsVisibleWhileItsBackFlagIntercepts() {
        composeRule.setContent {
            MaterialTheme {
                LocationsSheet(
                    bookmarks = listOf(PdfBookmark(id = "b1", pageIndex = 1, title = "1쪽 북마크")),
                    outline = emptyList(),
                    annotations = listOf(
                        Annotation(
                            type = AnnotationType.PEN,
                            page = 1,
                            color = 0xFF1F4E79.toInt(),
                            width = 0.004f,
                            points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.2f)),
                        ),
                    ),
                    pageCount = 6,
                    currentPage = 0,
                    onJumpToPage = {},
                    onExportSummary = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("위치").assertExists()
        LOCATIONS_TAB_ORDER.forEach { tab ->
            composeRule.onNodeWithText(tab.label).assertExists()
        }
        assertTrue(ViewerSheetFlags(showLocations = true).anyOpen)
    }

    @Test
    fun fileToolsSubSheetIsVisibleWhileItsBackFlagIntercepts() {
        val actions = listOf(
            PdfHubAction("쪽 편집", Icons.Outlined.EditNote) {},
            PdfHubAction("PDF 내보내기", Icons.Outlined.FileDownload) {},
        )
        composeRule.setContent {
            MaterialTheme {
                PdfHubSubSheet(
                    title = "파일 도구",
                    subtitle = "쪽 편집·쪽 관리, PDF 도구, 내보내기와 문서 설정",
                    actions = actions,
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("파일 도구").assertExists()
        composeRule.onNodeWithText("쪽 편집").assertExists()
        composeRule.onNodeWithText("PDF 내보내기").assertExists()
        assertTrue(ViewerSheetFlags(showFileTools = true).anyOpen)
    }
}
