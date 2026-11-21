package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * System Back must dismiss the topmost viewer layer. Closing the document while a
 * sheet is open is the regression covered here, with special attention to the two
 * sheets that were originally missing from the predicate.
 */
class ViewerScreenTest {

    @Test
    fun idleViewerLetsBackCloseTheDocument() {
        assertFalse(ViewerSheetFlags().anyOpen)
    }

    @Test
    fun memoCardsSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showMemoCardsSheet = true).anyOpen)
    }

    @Test
    fun penBoxSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showViewerPenBox = true).anyOpen)
    }

    @Test
    fun recentDocumentsSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showRecentDocs = true).anyOpen)
    }

    @Test
    fun tabOverflowSheetInterceptsBack() {
        assertTrue(ViewerSheetFlags(showTabOverflow = true).anyOpen)
    }

    @Test
    fun everyViewerLayerInterceptsBack() {
        val idle = ViewerSheetFlags()
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
            idle.copy(showOutline = true),
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
            idle.copy(textTargetOpen = true),
            idle.copy(stampTargetOpen = true),
            idle.copy(signatureTargetOpen = true),
        )

        layers.forEachIndexed { index, flags ->
            assertTrue("viewer layer #$index must intercept Back", flags.anyOpen)
        }
    }
}
