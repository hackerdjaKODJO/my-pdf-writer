package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure checks for the note-menu index. The sheet is intentionally a short index:
 * page operations belong to the page manager, study overlays to their own sheets,
 * and the eight ink options to one sub-sheet.
 */
class NoteEditorMenuTest {

    @Test
    fun `main note menu stays between nine and eleven top rows`() {
        val full = noteMenuEntries(hasRename = true, hasSourcePdf = true)
        val compactOnly = noteMenuEntries(hasRename = true, hasSourcePdf = false)
        val minimal = noteMenuEntries(hasRename = false, hasSourcePdf = false)

        assertEquals(11, full.size)
        assertEquals(10, compactOnly.size)
        assertEquals(9, minimal.size)
        assertTrue(full.size in 9..11)
        assertTrue(compactOnly.size in 9..11)
        assertTrue(minimal.size in 9..11)
        assertEquals(full.size, full.toSet().size)
        assertTrue(full.contains(NoteMenuEntry.PAGES))
        assertTrue(full.contains(NoteMenuEntry.STUDY_CARDS))
        assertTrue(full.contains(NoteMenuEntry.AUDIO))
        assertTrue(full.contains(NoteMenuEntry.INK_SETTINGS))
        assertTrue(full.contains(NoteMenuEntry.EXPORT_PDF))
        assertTrue(full.contains(NoteMenuEntry.HELP))
    }

    @Test
    fun `rename and source pdf rows are conditional`() {
        assertFalse(noteMenuEntries(false, true).contains(NoteMenuEntry.RENAME))
        assertTrue(noteMenuEntries(true, true).contains(NoteMenuEntry.RENAME))
        assertFalse(noteMenuEntries(true, false).contains(NoteMenuEntry.SOURCE_PDF))
        assertTrue(noteMenuEntries(true, true).contains(NoteMenuEntry.SOURCE_PDF))
    }

    @Test
    fun `ink settings keep all eight existing options`() {
        assertEquals(8, NOTE_INK_SETTINGS.size)
        assertEquals(8, NOTE_INK_SETTINGS.toSet().size)
        assertEquals(
            listOf(
                NoteInkSetting.FINGER_WRITING,
                NoteInkSetting.ZOOM_LOCK,
                NoteInkSetting.STROKE_STABILIZATION,
                NoteInkSetting.SHAPE_RECOGNITION,
                NoteInkSetting.PARTIAL_ERASER,
                NoteInkSetting.PRESSURE_INK,
                NoteInkSetting.PRESSURE_CURVE,
                NoteInkSetting.GESTURE_UNDO,
            ),
            NOTE_INK_SETTINGS,
        )
    }

    @Test
    fun `menu sections group note study export and help rows`() {
        val entries = noteMenuEntries(hasRename = true, hasSourcePdf = true)
        assertEquals(NoteMenuSection.NOTE, NoteMenuEntry.RENAME.section)
        assertEquals(NoteMenuSection.NOTE, NoteMenuEntry.PAGES.section)
        assertEquals(NoteMenuSection.NOTE, NoteMenuEntry.TEMPLATES.section)
        assertEquals(NoteMenuSection.NOTE, NoteMenuEntry.LAYERS.section)
        assertEquals(NoteMenuSection.NOTE, NoteMenuEntry.SEARCH.section)
        assertEquals(NoteMenuSection.STUDY, NoteMenuEntry.STUDY_CARDS.section)
        assertEquals(NoteMenuSection.STUDY, NoteMenuEntry.AUDIO.section)
        assertEquals(NoteMenuSection.STUDY, NoteMenuEntry.INK_SETTINGS.section)
        assertEquals(NoteMenuSection.EXPORT, NoteMenuEntry.EXPORT_PDF.section)
        assertEquals(NoteMenuSection.EXPORT, NoteMenuEntry.SOURCE_PDF.section)
        assertEquals(NoteMenuSection.HELP, NoteMenuEntry.HELP.section)

        // Sections only move forward once through the list.
        val sections = entries.map { it.section }
        val sectionOrder = sections.distinct()
        assertEquals(
            listOf(
                NoteMenuSection.NOTE,
                NoteMenuSection.STUDY,
                NoteMenuSection.EXPORT,
                NoteMenuSection.HELP,
            ),
            sectionOrder,
        )
        sectionOrder.forEach { section ->
            val indices = sections.indices.filter { sections[it] == section }
            assertEquals(
                "section $section must stay contiguous",
                (indices.first()..indices.last()).toList(),
                indices,
            )
        }
    }
}
