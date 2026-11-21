package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure checks for the note-menu index. The sheet is intentionally a short index:
 * page operations belong to the page manager, study overlays to their own sheets,
 * and the ten ink options to one sub-sheet.
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
    fun `quick actions are three slots above the indexed rows`() {
        assertEquals(
            listOf(
                NoteMenuQuickAction.UNDO,
                NoteMenuQuickAction.REDO,
                NoteMenuQuickAction.STYLE,
            ),
            NOTE_MENU_QUICK_ACTIONS,
        )
        assertEquals(3, NOTE_MENU_QUICK_ACTIONS.toSet().size)
        assertEquals(
            listOf("실행 취소", "다시 실행", "스타일"),
            NOTE_MENU_QUICK_ACTIONS.map(::noteMenuQuickActionLabel),
        )
        // Quick actions are pinned outside the sectioned index: they must not
        // inflate the eleven-row / four-section contract.
        assertEquals(11, noteMenuEntries(hasRename = true, hasSourcePdf = true).size)
        assertEquals(4, NoteMenuSection.entries.size)
    }

    @Test
    fun `student facing menu terms replace legacy study labels`() {
        assertEquals("복습 카드", NOTE_REVIEW_CARDS_LABEL)
        assertEquals("가리기 카드", NOTE_MASKING_REVIEW_CARD_LABEL)
        assertEquals("필기 설정", NOTE_INK_SETTINGS_LABEL)
        assertFalse(NOTE_REVIEW_CARDS_LABEL.contains("학습"))
        assertFalse(NOTE_MASKING_REVIEW_CARD_LABEL.contains("가림"))
    }

    @Test
    fun `rename and source pdf rows are conditional`() {
        assertFalse(noteMenuEntries(false, true).contains(NoteMenuEntry.RENAME))
        assertTrue(noteMenuEntries(true, true).contains(NoteMenuEntry.RENAME))
        assertFalse(noteMenuEntries(true, false).contains(NoteMenuEntry.SOURCE_PDF))
        assertTrue(noteMenuEntries(true, true).contains(NoteMenuEntry.SOURCE_PDF))
    }

    @Test
    fun `ink settings keep all ten options`() {
        assertEquals(10, NOTE_INK_SETTINGS.size)
        assertEquals(10, NOTE_INK_SETTINGS.toSet().size)
        assertEquals(
            listOf(
                NoteInkSetting.FINGER_WRITING,
                NoteInkSetting.ZOOM_LOCK,
                NoteInkSetting.STROKE_STABILIZATION,
                NoteInkSetting.SMOOTH_HANDWRITING,
                NoteInkSetting.SIMPLIFY_POINTS,
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
    fun inkSettingsLabelUsesActualOptionCount() {
        val label = noteInkSettingsSupportingLabel()

        assertTrue("label must include the real option count: $label", label.contains("${NOTE_INK_SETTINGS.size}가지"))
        assertTrue("stale hardcoded 8 must be gone: $label", !label.contains("8가지") || NOTE_INK_SETTINGS.size == 8)
        assertEquals(10, NOTE_INK_SETTINGS.size)
    }

    @Test
    fun `refinement toggles are labelled in Korean with session defaults`() {
        assertEquals("부드러운 필기", NOTE_SMOOTH_HANDWRITING_LABEL)
        assertEquals("점 간소화", NOTE_SIMPLIFY_POINTS_LABEL)
        // Smooth handwriting is on by default; RDP simplification stays opt-in.
        assertTrue(NOTE_SMOOTH_HANDWRITING_DEFAULT)
        assertFalse(NOTE_SIMPLIFY_POINTS_DEFAULT)
        assertTrue(NoteInkSetting.SMOOTH_HANDWRITING in NOTE_INK_SETTINGS)
        assertTrue(NoteInkSetting.SIMPLIFY_POINTS in NOTE_INK_SETTINGS)
        assertEquals(
            noteStateAwareLabel(NOTE_SMOOTH_HANDWRITING_LABEL, true),
            noteStateAwareLabel("부드러운 필기", NOTE_SMOOTH_HANDWRITING_DEFAULT),
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
        assertEquals(NoteMenuSection.REVIEW, NoteMenuEntry.STUDY_CARDS.section)
        assertEquals(NoteMenuSection.REVIEW, NoteMenuEntry.AUDIO.section)
        assertEquals(NoteMenuSection.REVIEW, NoteMenuEntry.INK_SETTINGS.section)
        assertEquals(NoteMenuSection.EXPORT, NoteMenuEntry.EXPORT_PDF.section)
        assertEquals(NoteMenuSection.EXPORT, NoteMenuEntry.SOURCE_PDF.section)
        assertEquals(NoteMenuSection.HELP, NoteMenuEntry.HELP.section)

        // Sections only move forward once through the list.
        val sections = entries.map { it.section }
        val sectionOrder = sections.distinct()
        assertEquals(
            listOf(
                NoteMenuSection.NOTE,
                NoteMenuSection.REVIEW,
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

    @Test
    fun `favorite and toggle labels announce both states`() {
        assertEquals("즐겨찾기, 선택됨", noteStateAwareLabel("즐겨찾기", true))
        assertEquals("즐겨찾기, 선택 안 됨", noteStateAwareLabel("즐겨찾기", false))
        assertEquals("손가락 필기, 선택됨", noteStateAwareLabel("손가락 필기", true))
        assertEquals("손가락 필기, 선택 안 됨", noteStateAwareLabel("손가락 필기", false))

        assertEquals("선택됨", noteToggleStateDescription(true))
        assertEquals("선택 안 됨", noteToggleStateDescription(false))
    }

    @Test
    fun `menu rows are at least 56 dp tall`() {
        assertTrue(isAccessibleTarget(NOTE_MENU_ROW_MIN_HEIGHT_DP))
        assertEquals(56, NOTE_MENU_ROW_MIN_HEIGHT_DP)
        assertTrue(NOTE_MENU_ROW_MIN_HEIGHT_DP >= MIN_TARGET_DP)
    }

    @Test
    fun `menu has four groups in student reading order`() {
        assertEquals(
            listOf("노트", "복습", "내보내기", "도움말"),
            NOTE_MENU_SECTION_ORDER.map { it.label },
        )
        assertEquals(4, NOTE_MENU_SECTION_ORDER.size)
        assertEquals(NoteMenuSection.entries, NOTE_MENU_SECTION_ORDER)
        assertFalse(NOTE_MENU_SECTION_ORDER.any { it.label == "학습" })

        // Every row's group is one of the four, and the rows stay contiguous.
        val sections = noteMenuEntries(true, true).map { it.section }
        assertTrue(sections.all { it in NOTE_MENU_SECTION_ORDER })
        assertEquals(NOTE_MENU_SECTION_ORDER, sections.distinct())
    }

    @Test
    fun `primary review action and reachable card tools use student labels`() {
        assertEquals("복습 카드", NOTE_REVIEW_CARDS_LABEL)
        assertEquals("복습", NoteMenuSection.REVIEW.label)
        assertFalse(NoteMenuSection.REVIEW.label.contains("학습"))

        // The sub-sheets behind 복습 카드 keep these tools one tap away.
        assertTrue(NoteMenuEntry.STUDY_CARDS in noteMenuEntries(true, true))
        assertTrue(NoteMenuEntry.AUDIO in noteMenuEntries(true, true))
        assertTrue(NoteMenuEntry.INK_SETTINGS in noteMenuEntries(true, true))
        assertEquals("포스트잇", NOTE_MEMO_CARD_LABEL)
        assertEquals("체크리스트", NOTE_CHECKLIST_LABEL)
        assertEquals("필기 설정", NOTE_INK_SETTINGS_LABEL)
    }

    @Test
    fun `capture vocabulary unifies note rows and keeps handoffs separate`() {
        assertEquals("노트에 담기", NOTE_CAPTURE_LABEL)
        assertEquals("노트에 담기", studentCaptureLabel())
        assertEquals("노트에 담기", NOTE_CAPTURE_VOCABULARY.first())
        assertEquals(listOf("번역", "사전"), STUDENT_HANDOFF_LABELS)
        assertEquals(
            listOf("노트에 담기", "번역", "사전"),
            NOTE_CAPTURE_VOCABULARY,
        )
        assertFalse(NOTE_CAPTURE_VOCABULARY.contains("발췌로 저장"))
        assertFalse(NOTE_CAPTURE_VOCABULARY.contains("노트 패널에 추가"))
    }
}
