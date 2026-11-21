package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookHomeFormatTest {

    private val now = 1_700_000_000_000L

    @Test
    fun relativeTimeUsesCompactKoreanLabels() {
        assertEquals("방금", relativeNoteTime(now, now))
        assertEquals("방금", relativeNoteTime(now + 5_000L, now))
        assertEquals("5분 전", relativeNoteTime(now - 5 * 60_000L, now))
        assertEquals("2시간 전", relativeNoteTime(now - 2 * 3_600_000L, now))
        assertEquals("3일 전", relativeNoteTime(now - 3 * 86_400_000L, now))
    }

    @Test
    fun relativeTimeFallsBackToCalendarDateAfterAWeek() {
        val old = now - 30L * 86_400_000L
        val text = relativeNoteTime(old, now)
        assertEquals(true, text.matches(Regex("""\d{4}\. \d{1,2}\. \d{1,2}\.""")))
    }

    @Test
    fun tagsAreSplitTrimmedDedupedAndHashStripped() {
        assertEquals(
            listOf("시험", "정리", "아이디어"),
            parseTagInput(" 시험, #정리;아이디어\n시험 "),
        )
    }

    @Test
    fun emptyTagInputReturnsNoTags() {
        assertEquals(emptyList<String>(), parseTagInput(""))
        assertEquals(emptyList<String>(), parseTagInput(" , ; # \n "))
    }

    @Test
    fun homeHasOnePrimaryActionDrivenByStudentState() {
        assertEquals(HomePrimaryAction.IMPORT, homePrimaryAction(false))
        assertEquals(HomePrimaryAction.CONTINUE, homePrimaryAction(true))
        assertEquals("PDF 가져오기", homePrimaryAction(false).label)
        assertEquals("이어서 읽기", homePrimaryAction(true).label)
        assertEquals(2, HomePrimaryAction.entries.size)
    }

    @Test
    fun homeQuickButtonsCollapseToTwoWithPrimaryFirst() {
        assertEquals(listOf("PDF 가져오기", "새 노트"), homeQuickActionLabels(false))
        assertEquals(listOf("이어서 읽기", "새 노트"), homeQuickActionLabels(true))
        homeQuickActionLabels(false).let { labels ->
            assertEquals(2, labels.size)
            assertEquals(2, labels.toSet().size)
            assertEquals(homePrimaryAction(false).label, labels[0])
            assertEquals(HOME_NEW_NOTE_LABEL, labels[1])
        }
        homeQuickActionLabels(true).let { labels ->
            assertEquals(2, labels.size)
            assertEquals(homePrimaryAction(true).label, labels[0])
        }
        // Tools, backup and empty-PDF creation are not quick buttons.
        listOf("PDF 도구", "백업/복원", "빈 PDF").forEach { label ->
            assertTrue(label !in homeQuickActionLabels(true))
        }
    }

    @Test
    fun resumeOpensTheMostRecentlyUsedDocument() {
        val empty = HomeContinuation(lastPdf = null, lastNotebook = null)
        assertEquals(HomeResumeTarget.IMPORT, homeResumeTarget(empty))

        val pdf = RecentDoc("content://docs/lecture.pdf", "lecture.pdf", 12, 2_000L)
        val notebook = Notebook(id = "n1", title = "정리", updatedAt = 1_000L)
        assertEquals(
            HomeResumeTarget.PDF,
            homeResumeTarget(HomeContinuation(pdf, null)),
        )
        assertEquals(
            HomeResumeTarget.NOTEBOOK,
            homeResumeTarget(HomeContinuation(null, notebook)),
        )
        assertEquals(
            HomeResumeTarget.PDF,
            homeResumeTarget(HomeContinuation(pdf, notebook)),
        )
        assertEquals(
            HomeResumeTarget.NOTEBOOK,
            homeResumeTarget(HomeContinuation(pdf.copy(lastOpenedAt = 500L), notebook)),
        )
    }

    @Test
    fun homeQuickLabelsUseAtMostTwoLinesAt320DpAndLargeFonts() {
        val label = "PDF 가져오기"
        assertEquals(1, homeQuickLabelMaxLines(220, label))
        assertEquals(2, homeQuickLabelMaxLines(80, label))
        assertEquals(2, homeQuickLabelMaxLines(80, label, 2f))
        assertTrue(homeQuickLabelMaxLines(48, label, 3f) <= STUDENT_LABEL_MAX_LINES)
    }
}
