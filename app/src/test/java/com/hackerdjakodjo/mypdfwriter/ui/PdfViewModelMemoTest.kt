package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure card-list transitions used by [PdfViewModel.upsertMemoCard] and
 * [PdfViewModel.deleteMemoCard]. They run without an Android ViewModel so the
 * commit behaviour is covered by JVM tests.
 */
class PdfViewModelMemoTest {

    private fun card(id: String, page: Int, text: String = "메모") = MemoCard(
        id = id,
        page = page,
        text = text,
        tint = MemoTint.YELLOW,
    )

    @Test
    fun upsertAddsACardAndKeepsTheRestInOrder() {
        val first = card("a", 0)
        val result = upsertMemoCardIn(listOf(first), card("b", 2))

        assertEquals(listOf("a", "b"), result.map { it.id })
        assertEquals(listOf(0, 2), result.map { it.page })
        assertEquals(first, result[0])
    }

    @Test
    fun upsertReplacesByIdWithoutDuplicatingAndNormalizesGeometry() {
        val original = card("a", 1, "원래")
        val updated = original.copy(
            text = "  고친 메모  ",
            x = 4f,
            w = -2f,
            page = 3,
        )

        val result = upsertMemoCardIn(listOf(original, card("b", 0)), updated)

        assertEquals(2, result.size)
        assertEquals("고친 메모", result.first { it.id == "a" }.text)
        assertEquals(3, result.first { it.id == "a" }.page)
        assertEquals(MemoCard.MIN_W, result.first { it.id == "a" }.w, 0.0001f)
        assertTrue(result.first { it.id == "a" }.x in 0f..1f)
        assertEquals(1, result.count { it.id == "a" })
    }

    @Test
    fun deleteRemovesOnlyTheRequestedCard() {
        val cards = listOf(card("a", 0), card("b", 1), card("c", 1))

        assertEquals(listOf("a", "c"), deleteMemoCardFrom(cards, "b").map { it.id })
        assertEquals(cards, deleteMemoCardFrom(cards, "missing"))
    }

    @Test
    fun pageFilteringReturnsOnlyThatPagesCardsInInsertionOrder() {
        val cards = listOf(card("a", 0), card("b", 2), card("c", 0), card("d", 1))

        assertEquals(listOf("a", "c"), memoCardsOnPage(cards, 0).map { it.id })
        assertEquals(listOf("b"), memoCardsOnPage(cards, 2).map { it.id })
        assertTrue(memoCardsOnPage(cards, 9).isEmpty())
        assertTrue(memoCardsOnPage(cards, -1).isEmpty())
    }

    @Test
    fun repeatedUpsertOfSameIdIsIdempotent() {
        val original = card("a", 0)
        val once = upsertMemoCardIn(listOf(original), original)
        val twice = upsertMemoCardIn(once, original)

        assertEquals(once, twice)
        assertNotEquals(0, twice.size)
    }
}
