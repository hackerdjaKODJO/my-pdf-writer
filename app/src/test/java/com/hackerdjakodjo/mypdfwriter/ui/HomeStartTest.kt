package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartTest {

    private fun pdf(name: String, openedAt: Long) = RecentDoc(
        uri = "content://docs/$name",
        name = name,
        pageCount = 3,
        lastOpenedAt = openedAt,
    )

    private fun notebook(id: String, updatedAt: Long, trashed: Boolean = false) = Notebook(
        id = id,
        title = id,
        updatedAt = updatedAt,
        trashed = trashed,
    )

    @Test
    fun emptyLibraryHasNoContinuation() {
        val continuation = selectHomeContinuation(emptyList(), emptyList())

        assertNull(continuation.lastPdf)
        assertNull(continuation.lastNotebook)
        assertFalse(continuation.hasContent)
    }

    @Test
    fun picksLastOpenedPdfAndLastEditedNotebook() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(
                notebook("old", updatedAt = 100L),
                notebook("recent", updatedAt = 300L),
                notebook("middle", updatedAt = 200L),
            ),
            pdfs = listOf(
                pdf("first.pdf", openedAt = 50L),
                pdf("last.pdf", openedAt = 400L),
            ),
        )

        assertEquals("last.pdf", continuation.lastPdf?.name)
        assertEquals("recent", continuation.lastNotebook?.id)
        assertTrue(continuation.hasContent)
    }

    @Test
    fun trashedNotebooksAreNeverContinued() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(
                notebook("active", updatedAt = 100L),
                notebook("trashed", updatedAt = 900L, trashed = true),
            ),
            pdfs = emptyList(),
        )

        assertEquals("active", continuation.lastNotebook?.id)
        assertNull(continuation.lastPdf)
    }

    @Test
    fun onlyTrashedNotebooksLeavesNotebookSlotEmpty() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(notebook("trashed", updatedAt = 900L, trashed = true)),
            pdfs = listOf(pdf("reading.pdf", openedAt = 20L)),
        )

        assertNull(continuation.lastNotebook)
        assertEquals("reading.pdf", continuation.lastPdf?.name)
        assertTrue(continuation.hasContent)
    }

    @Test
    fun homeStartIsVisibleOnlyWithoutFilters() {
        assertTrue(HomeFilterState().showsHomeStart())
        assertTrue(HomeFilterState(query = "   ").showsHomeStart())
    }

    @Test
    fun anyActiveFilterHidesHomeStart() {
        assertFalse(HomeFilterState(scope = NoteScope.FAVORITES).showsHomeStart())
        assertFalse(HomeFilterState(folder = "").showsHomeStart())
        assertFalse(HomeFilterState(tag = "영어").showsHomeStart())
        assertFalse(HomeFilterState(query = "기말").showsHomeStart())
    }

    @Test
    fun quickActionsUseTwoColumnsWhenNarrowOrLarge() {
        assertEquals(2, homeQuickActionColumns(320, 1f))
        assertEquals(2, homeQuickActionColumns(359, 1f))
        assertEquals(4, homeQuickActionColumns(400, 1f))
        assertEquals(2, homeQuickActionColumns(400, 1.5f))
        assertEquals(2, homeQuickActionColumns(720, 2f))
    }

    @Test
    fun quickActionLabelsWrapToTwoLinesWithoutEllipsis() {
        assertEquals(2, homeQuickActionLabelMaxLines(48, "PDF 가져오기"))
        assertEquals(1, homeQuickActionLabelMaxLines(96, "PDF 가져오기"))
        assertEquals(1, homeQuickActionLabelMaxLines(60, "새 노트"))
        assertEquals(2, homeQuickActionLabelMaxLines(100, "PDF 가져오기", 2f))
    }

    @Test
    fun homeHasOnlyTwoPrimaryQuickActions() {
        assertEquals(listOf("이어서 읽기", "새 노트"), homeQuickActionLabels())
    }

    @Test
    fun continuationCardsShareOneStudentFirstLabel() {
        assertEquals("이어서 읽기", HOME_CONTINUATION_TITLE)
        assertEquals("이어서 읽기: lecture.pdf", continuationLabel("lecture.pdf"))
        assertEquals("이어서 읽기: 시험 노트", continuationLabel("시험 노트"))
    }

    @Test
    fun optionalOnboardingPromptIsShownOnlyBeforeItIsSeen() {
        assertTrue(showHomeOnboardingPrompt(hasSeenOnboarding = false))
        assertFalse(showHomeOnboardingPrompt(hasSeenOnboarding = true))
        assertEquals("처음 사용하나요? 60초 안내", UiCopy.HOME_ONBOARDING_PROMPT)
        assertEquals("60초 안내 닫기", UiCopy.HOME_ONBOARDING_DISMISS)
    }

    @Test
    fun favoriteStarDescriptionCarriesSelectedState() {
        assertEquals("즐겨찾기", favoriteToggleDescription(false))
        assertEquals("즐겨찾기, 선택됨", favoriteToggleDescription(true))
    }
}
