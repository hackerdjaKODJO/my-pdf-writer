package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
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
}
