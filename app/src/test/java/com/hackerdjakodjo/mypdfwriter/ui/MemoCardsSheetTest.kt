package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure sheet helpers: list assembly, percent parsing and placement text. */
class MemoCardsSheetTest {

    @Test
    fun entriesFollowPageOrderAndIncludeEveryCard() {
        val notebook = Notebook(
            pages = listOf(
                NotePage(
                    id = "p0",
                    memoCards = listOf(
                        MemoCard(id = "a", text = "첫 카드"),
                        MemoCard(id = "b", text = "둘째 카드"),
                    ),
                ),
                NotePage(id = "p1"),
                NotePage(id = "p2", memoCards = listOf(MemoCard(id = "c", text = "셋재 카드"))),
            ),
        )

        val entries = memoCardEntries(notebook)

        assertEquals(listOf(0, 0, 2), entries.map { it.pageIndex })
        assertEquals(listOf("a", "b", "c"), entries.map { it.card.id })
        assertEquals(listOf("첫 카드", "둘째 카드", "셋재 카드"), entries.map { it.card.text })
        assertTrue(memoCardEntries(Notebook(pages = listOf(NotePage()))).isEmpty())
    }

    @Test
    fun percentFieldsFallBackOnBlankOrMalformedInput() {
        assertEquals(0.42f, memoPercent("42", 0.1f), 0.0001f)
        assertEquals(0.425f, memoPercent(" 42.5 ", 0.1f), 0.0001f)
        assertEquals(-0.05f, memoPercent("-5", 0.1f), 0.0001f)
        assertEquals(0.1f, memoPercent("", 0.1f), 0.0001f)
        assertEquals(0.1f, memoPercent("not-a-number", 0.1f), 0.0001f)
    }

    @Test
    fun placementLabelShowsAllFourNormalizedFields() {
        val card = MemoCard(
            x = 0.255f,
            y = 0.4f,
            w = 0.33f,
            h = 0.205f,
            tint = MemoTint.PINK,
        ).normalized()

        val label = memoPlacementLabel(card)

        assertEquals("X 26% · Y 40% · W 33% · H 21%", label)
    }

    @Test
    fun hideAndRestoreAreSeparateVisibleActions() {
        assertEquals("포스트잇 숨기기", MEMO_HIDE_CARDS_LABEL)
        assertEquals("포스트잇 표시", MEMO_SHOW_CARDS_LABEL)
        assertNotEquals(MEMO_HIDE_CARDS_LABEL, MEMO_SHOW_CARDS_LABEL)
        assertEquals("memo_sheet_hide_cards", MEMO_SHEET_HIDE_TAG)
        assertEquals("memo_sheet_show_cards", MEMO_SHEET_SHOW_TAG)
        assertEquals("memo_cards_restore_pill", MEMO_CARDS_RESTORE_PILL_TAG)
        assertFalse(MEMO_SHOW_CARDS_LABEL.contains("삭제"))
    }

    @Test
    fun hidingTheOverlayKeepsEveryCardInTheNotebookJson() {
        val card = MemoCard(id = "a", text = "지워지면 안 되는 메모")
        val notebook = Notebook(pages = listOf(NotePage(id = "p0", memoCards = listOf(card))))

        val (hidden, hiddenNotebook) = memoOverlayHidden(notebook)
        assertFalse(hidden)
        assertSame("hide is view-only, the notebook must not be rewritten", notebook, hiddenNotebook)
        assertEquals(listOf(card), hiddenNotebook.pages.single().memoCards)

        val (restored, restoredNotebook) = memoOverlayShown(hiddenNotebook)
        assertTrue(restored)
        assertSame(notebook, restoredNotebook)
        assertEquals("지워지면 안 되는 메모", restoredNotebook.pages.single().memoCards.single().text)
    }
}
