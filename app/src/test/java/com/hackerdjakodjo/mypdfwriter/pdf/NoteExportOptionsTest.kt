package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteExportOptionsTest {

    private val pages = listOf(
        NotePage(id = "p0"),
        NotePage(id = "p1"),
        NotePage(id = "p2"),
    )

    @Test
    fun allPagesIsTheDefaultAndKeepsNotebookOrder() {
        val options = NoteExportOptions()

        assertEquals(NoteExportPageRange.ALL, options.pageRange)
        assertFalse(options.includeHiddenLayers)
        assertEquals(listOf(0, 1, 2), options.pageIndices(pages))
    }

    @Test
    fun currentPageIsClampedToTheNotebook() {
        assertEquals(
            listOf(1),
            NoteExportOptions(pageRange = NoteExportPageRange.CURRENT, currentPageIndex = 1)
                .pageIndices(pages),
        )
        assertEquals(
            listOf(2),
            NoteExportOptions(pageRange = NoteExportPageRange.CURRENT, currentPageIndex = 99)
                .pageIndices(pages),
        )
        assertEquals(
            listOf(0),
            NoteExportOptions(pageRange = NoteExportPageRange.CURRENT, currentPageIndex = -4)
                .pageIndices(pages),
        )
    }

    @Test
    fun selectedPagesUsePageOrderAndIgnoreUnknownIds() {
        val options = NoteExportOptions(
            pageRange = NoteExportPageRange.SELECTED,
            selectedPageIds = setOf("p2", "unknown", "p0"),
        )

        assertEquals(listOf(0, 2), options.pageIndices(pages))
    }

    @Test
    fun selectedRangeWithNoSurvivingIdsIsEmptyForTheExporterFallback() {
        val options = NoteExportOptions(
            pageRange = NoteExportPageRange.SELECTED,
            selectedPageIds = setOf("removed"),
        )

        assertTrue(options.pageIndices(pages).isEmpty())
        assertTrue(
            NoteExportOptions(pageRange = NoteExportPageRange.ALL)
                .pageIndices(emptyList())
                .isEmpty(),
        )
    }

    @Test
    fun hiddenLayerChoiceIsExplicitAndDefaultsOff() {
        val include = NoteExportOptions(includeHiddenLayers = true)

        assertFalse(NoteExportOptions().includeHiddenLayers)
        assertTrue(NoteExportOptions().includeChecklists)
        assertFalse(NoteExportOptions(includeChecklists = false).includeChecklists)
        assertTrue(include.includeHiddenLayers)
    }

    @Test
    fun suggestedFileNameMatchesTheExistingDelegatedNameRule() {
        assertEquals("노트.pdf", noteExportFileName(null))
        assertEquals("노트.pdf", noteExportFileName("   "))
        assertEquals("회의_2026-11-16.pdf", noteExportFileName("회의/2026-11-16"))
        assertEquals("a_b_c_d_e_f_g_h\\i.pdf", noteExportFileName("a:b*c?d\"e<f>g|h\\i"))

        val long = noteExportFileName("가".repeat(100))
        assertEquals(64, long.length)
        assertTrue(long.endsWith(".pdf"))
    }
}
