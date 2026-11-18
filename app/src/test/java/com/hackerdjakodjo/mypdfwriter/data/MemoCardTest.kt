package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoCardTest {

    private fun card(
        id: String = "memo-1",
        text: String = "우유 사기 · 3층 서랍 정리",
        tint: MemoTint = MemoTint.MINT,
        tilt: Float = 2f,
    ) = MemoCard(
        id = id,
        text = text,
        tint = tint,
        x = 0.25f,
        y = 0.4f,
        w = 0.4f,
        h = 0.22f,
        tilt = tilt,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
    )

    @Test
    fun legacyPageWithoutMemoKeyLoadsEmptyAndWritesNoKey() {
        val page = NotePage.fromJson(JSONObject("""{"id":"p","template":"GRID"}"""))
        assertTrue(page.memoCards.isEmpty())
        assertFalse(page.toJson().has("memoCards"))

        val notebook = Notebook.fromJson(JSONObject("""{"id":"nb","pages":[{"id":"p"}]}"""))
        assertTrue(notebook.pages.single().memoCards.isEmpty())
        assertFalse(notebook.toJson().getJSONArray("pages").getJSONObject(0).has("memoCards"))
    }

    @Test
    fun cardsRoundTripThroughNotebookJson() {
        val notebook = Notebook(
            id = "nb",
            pages = listOf(
                NotePage(
                    id = "p0",
                    memoCards = listOf(
                        card("a", tint = MemoTint.YELLOW),
                        card("b", text = "분홍 카드", tint = MemoTint.PINK, tilt = -4f),
                    ),
                ),
                NotePage(id = "p1", memoCards = listOf(card("c", tint = MemoTint.SKY))),
                NotePage(id = "p2"),
            ),
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals(2, restored.pages[0].memoCards.size)
        assertEquals(1, restored.pages[1].memoCards.size)
        assertTrue(restored.pages[2].memoCards.isEmpty())
        val first = restored.pages[0].memoCards[0]
        assertEquals("a", first.id)
        assertEquals("우유 사기 · 3층 서랍 정리", first.text)
        assertEquals(MemoTint.YELLOW, first.tint)
        assertEquals(0.25f, first.x)
        assertEquals(0.4f, first.y)
        assertEquals(0.4f, first.w)
        assertEquals(0.22f, first.h)
        assertEquals(2f, first.tilt)
        assertEquals(1_700_000_000_000L, first.createdAt)
        assertEquals(1_700_000_100_000L, first.updatedAt)
        assertEquals(MemoTint.PINK, restored.pages[0].memoCards[1].tint)
        assertEquals(-4f, restored.pages[0].memoCards[1].tilt)
        assertEquals(MemoTint.SKY, restored.pages[1].memoCards[0].tint)
        val json = notebook.toJson().toString()
        assertFalse(json.contains("/data/data/"))
        assertFalse(json.contains("content://"))
    }

    @Test
    fun malformedCardJsonRepairsEveryField() {
        val parsed = MemoCard.fromJson(
            JSONObject(
                """{"id":"","text":"  정리  ","tint":"NOT_A_TINT",""" +
                    """"x":"NaN","y":"Infinity","w":-3,"h":99,"tilt":99,""" +
                    """"createdAt":0,"updatedAt":0}""",
            ),
        )

        assertTrue(parsed.id.isNotBlank())
        assertEquals("정리", parsed.text)
        assertEquals(MemoTint.YELLOW, parsed.tint)
        assertTrue(parsed.x.isFinite())
        assertTrue(parsed.y.isFinite())
        assertEquals(MemoCard.DEFAULT_X, parsed.x, 0.0001f)
        assertEquals(0f, parsed.y, 0.0001f)
        assertEquals(MemoCard.MIN_W, parsed.w)
        assertEquals(1f, parsed.h)
        assertEquals(MemoCard.MAX_TILT, parsed.tilt)
        assertTrue(parsed.createdAt > 0L)
        assertTrue(parsed.updatedAt > 0L)

        val defaults = MemoCard.fromJson(JSONObject("{}"))
        assertEquals(MemoCard.DEFAULT_X, defaults.x)
        assertEquals(MemoCard.DEFAULT_Y, defaults.y)
        assertEquals(MemoCard.DEFAULT_W, defaults.w)
        assertEquals(MemoCard.DEFAULT_H, defaults.h)
        assertEquals(0f, defaults.tilt)
        assertEquals(MemoTint.YELLOW, defaults.tint)
        assertTrue(defaults.id.isNotBlank())
    }

    @Test
    fun normalizedKeepsWholeCardOnPageBoundsTextAndTilt() {
        val card = MemoCard(
            id = "  ",
            text = "  ${"가".repeat(MemoCard.MAX_TEXT_LENGTH + 50)}  ",
            x = 0.95f,
            y = -0.2f,
            w = 2f,
            h = -1f,
            tilt = 40f,
            createdAt = -5L,
            updatedAt = -1L,
        ).normalized()

        assertTrue(card.id.isNotBlank())
        assertEquals(MemoCard.MAX_TEXT_LENGTH, card.text.length)
        assertEquals(1f, card.w)
        assertEquals(0f, card.x)
        assertEquals(MemoCard.MIN_H, card.h)
        assertEquals(0f, card.y)
        assertEquals(MemoCard.MAX_TILT, card.tilt)
        assertTrue(card.createdAt > 0L)
        assertTrue(card.updatedAt >= card.createdAt)

        val negativeTilt = MemoCard(tilt = -40f).normalized()
        assertEquals(-MemoCard.MAX_TILT, negativeTilt.tilt)
    }

    @Test
    fun freshIdsProduceIndependentCardsWithoutChangingContent() {
        val originals = listOf(card("a"), card("b", tint = MemoTint.SKY))
        val copies = Memos.freshIds(originals)

        assertEquals(2, copies.size)
        assertNotEquals(originals[0].id, copies[0].id)
        assertNotEquals(originals[1].id, copies[1].id)
        assertNotEquals(copies[0].id, copies[1].id)
        assertEquals(originals[0].tint, copies[0].tint)
        assertEquals(originals[1].text, copies[1].text)
        assertEquals(originals[0].x, copies[0].x)
        assertEquals(originals[0].createdAt, copies[0].createdAt)
    }

    @Test
    fun originalTintPaletteHasFiveDistinctChoices() {
        assertEquals(5, MemoTint.entries.size)
        assertEquals(5, MemoTint.entries.map { it.label }.toSet().size)
        assertEquals(5, MemoTint.entries.map { it.background }.toSet().size)
        assertTrue(MemoTint.entries.any { it.label == "노랑" })
        assertTrue(MemoTint.entries.any { it.label == "민트" })
        assertTrue(MemoTint.entries.any { it.label == "분홍" })
        assertTrue(MemoTint.entries.any { it.label == "하늘" })
        assertTrue(MemoTint.entries.any { it.label == "흰색" })
    }
}
