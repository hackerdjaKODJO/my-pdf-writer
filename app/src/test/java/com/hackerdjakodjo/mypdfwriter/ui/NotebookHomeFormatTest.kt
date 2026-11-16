package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
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
}
