package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure checks for the floating compact study chrome: the note dock must be a
 * small inset pill with 32–40 dp targets, the header toggle must switch between
 * that dock and a 44 dp restore pill, and the study header must be much shorter
 * than the old stacked full-width header.
 */
class NoteFloatingChromeTest {

    @Test
    fun `floating dock rows meet the 48 dp minimum target`() {
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_TOOL_ROW_HEIGHT_DP)
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_ACTION_ROW_HEIGHT_DP)
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP)
        assertTrue(isAccessibleTarget(NOTE_COMPACT_TOOL_ROW_HEIGHT_DP))
        assertTrue(isAccessibleTarget(NOTE_COMPACT_ACTION_ROW_HEIGHT_DP))
        assertTrue(isAccessibleTarget(NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP))
        assertTrue(isAccessibleTarget(NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP))
        assertTrue(isAccessibleTarget(NOTE_FULL_DOCK_ACTION_ROW_HEIGHT_DP))
    }

    @Test
    fun `floating dock height stays compact with and without a tool tray`() {
        val closed = noteCompactDockHeightDp(showContextTray = false)
        val expected = NOTE_COMPACT_DOCK_VERTICAL_PADDING_DP * 2 +
            NOTE_COMPACT_TOOL_ROW_HEIGHT_DP +
            NOTE_COMPACT_DOCK_DIVIDER_DP +
            NOTE_COMPACT_ACTION_ROW_HEIGHT_DP

        assertEquals(expected, closed)
        assertTrue("each dock row must be a full 48 dp target, was $closed", closed >= MIN_TARGET_DP * 2)
        assertTrue("compact dock should stay under 120 dp, was $closed", closed <= 120)
        assertEquals(103, closed)

        val tray = noteCompactDockHeightDp(showContextTray = true)
        assertEquals(closed + NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP, tray)
        assertTrue("tray dock should stay under 200 dp, was $tray", tray <= 200)
        assertEquals(closed + NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP, noteCompactDockObscuredHeightDp(false))
    }

    @Test
    fun `floating dock keeps side margins and a compact maximum width`() {
        assertTrue(NOTE_COMPACT_DOCK_SIDE_MARGIN_DP >= 4)
        assertTrue(NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP >= 4)
        assertTrue(NOTE_COMPACT_DOCK_CORNER_DP >= 12)
        assertEquals(340, NOTE_COMPACT_DOCK_MAX_WIDTH_DP)

        // A 320 dp phone leaves an 8 dp margin on each side.
        assertEquals(304, noteFloatingDockWidthDp(320))
        assertTrue(noteFloatingDockWidthDp(320) < 320)
        // A 360 dp phone still uses the compact pill cap.
        assertEquals(NOTE_COMPACT_DOCK_MAX_WIDTH_DP, noteFloatingDockWidthDp(360))
        // Wider panes stop growing so the pill never becomes a full bar.
        assertEquals(NOTE_COMPACT_DOCK_MAX_WIDTH_DP, noteFloatingDockWidthDp(412))
        assertEquals(NOTE_COMPACT_DOCK_MAX_WIDTH_DP, noteFloatingDockWidthDp(1200))
        // Degenerate widths clamp to zero instead of going negative.
        assertEquals(0, noteFloatingDockWidthDp(NOTE_COMPACT_DOCK_SIDE_MARGIN_DP * 2))
        assertEquals(0, noteFloatingDockWidthDp(-40))
    }

    @Test
    fun `floating dock is shorter than the full-screen dock`() {
        val floating = noteCompactDockHeightDp(showContextTray = false)
        val full = noteFullDockHeightDp(showContextTray = false)

        assertTrue(floating < full)
        assertTrue(floating + NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP < full)
    }

    @Test
    fun `header toggle selects the dock or the restore pill`() {
        assertEquals(NoteCompactChromeMode.DOCK, noteCompactChromeMode(true))
        assertEquals(NoteCompactChromeMode.RESTORE_PILL, noteCompactChromeMode(false))

        assertEquals(
            noteCompactDockObscuredHeightDp(false),
            noteCompactChromeObscuredHeightDp(NoteCompactChromeMode.DOCK, false),
        )
        assertEquals(
            noteCompactDockObscuredHeightDp(true),
            noteCompactChromeObscuredHeightDp(NoteCompactChromeMode.DOCK, true),
        )
        assertEquals(
            NOTE_COMPACT_RESTORE_PILL_HEIGHT_DP + NOTE_COMPACT_RESTORE_PILL_BOTTOM_MARGIN_DP,
            noteCompactChromeObscuredHeightDp(NoteCompactChromeMode.RESTORE_PILL, true),
        )
        // Hidden tools never leave a taller snackbar gap than the visible dock.
        assertTrue(
            noteCompactChromeObscuredHeightDp(NoteCompactChromeMode.RESTORE_PILL, false) <
                noteCompactChromeObscuredHeightDp(NoteCompactChromeMode.DOCK, false),
        )
    }

    @Test
    fun `restore pill is a 44 dp target clear of the writing hotspot`() {
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_RESTORE_PILL_MIN_TARGET_DP)
        assertTrue(isAccessibleTarget(NOTE_COMPACT_RESTORE_PILL_MIN_TARGET_DP))
        assertEquals(
            NOTE_COMPACT_RESTORE_PILL_MIN_TARGET_DP,
            NOTE_COMPACT_RESTORE_PILL_HEIGHT_DP,
        )
        assertTrue(
            NOTE_COMPACT_RESTORE_PILL_MIN_WIDTH_DP >= NOTE_COMPACT_RESTORE_PILL_MIN_TARGET_DP,
        )
        assertTrue(
            NOTE_COMPACT_RESTORE_PILL_BOTTOM_MARGIN_DP >= NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP,
        )
        assertTrue(
            "restore pill must be far smaller than the dock it replaces",
            NOTE_COMPACT_RESTORE_PILL_HEIGHT_DP < noteCompactDockHeightDp(false),
        )
    }

    @Test
    fun `restore pill fits a 320 dp pane with both end margins`() {
        assertTrue(noteCompactRestorePillFitsWidth(320))
        assertTrue(noteCompactRestorePillFitsWidth(360))
        val minimum = NOTE_COMPACT_RESTORE_PILL_MIN_WIDTH_DP +
            NOTE_COMPACT_RESTORE_PILL_END_MARGIN_DP * 2
        assertTrue(noteCompactRestorePillFitsWidth(minimum))
        assertFalse(noteCompactRestorePillFitsWidth(minimum - 1))
        assertFalse(noteCompactRestorePillFitsWidth(0))
    }

    @Test
    fun `study pane floating header keeps one compact row`() {
        val header = studyPaneFloatingHeaderHeightDp()
        val expected = STUDY_PANE_HEADER_ROW_HEIGHT_DP +
            STUDY_PANE_HEADER_VERTICAL_PADDING_DP * 2

        assertEquals(expected, header)
        assertEquals(44, header)
        assertTrue("floating header should fit one compact row, was $header", header <= 48)
        assertTrue(
            "floating header should be at most half the old stack",
            header * 2 <= STUDY_PANE_LEGACY_STACKED_HEADER_HEIGHT_DP,
        )
    }

    @Test
    fun `compact hint inset clears the floating header`() {
        val header = studyPaneFloatingHeaderHeightDp()
        val inset = studyPaneFloatingHeaderInsetDp()

        assertEquals(STUDY_PANE_HEADER_TOP_MARGIN_DP + header, inset)
        assertTrue(inset >= header)
        assertTrue("hint inset should stay small, was $inset", inset < 60)
        assertEquals(48, inset)
    }

    @Test
    fun `study pane header margins preserve the floating look`() {
        assertTrue(STUDY_PANE_HEADER_SIDE_MARGIN_DP >= 4)
        assertTrue(STUDY_PANE_HEADER_TOP_MARGIN_DP >= 2)
        assertTrue(STUDY_PANE_HEADER_ROW_HEIGHT_DP <= 40)
    }

    @Test
    fun `dock labels follow chrome scale but targets never shrink`() {
        assertTrue(noteDockShowLabels(360, 1f))
        assertTrue(noteDockShowLabels(412, 1.4f))
        assertFalse(noteDockShowLabels(360, 1.5f))
        assertFalse(noteDockShowLabels(320, 2f))

        // Losing the label never buys a smaller hit target.
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_TOOL_ROW_HEIGHT_DP)
        assertEquals(MIN_TARGET_DP, NOTE_COMPACT_ACTION_ROW_HEIGHT_DP)
    }
}
