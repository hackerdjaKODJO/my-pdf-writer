package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskingCardTest {

    private fun card(
        id: String = "card-1",
        prompt: String = "세포분열의 단계는?",
        answer: String = "간기·전기·중기·후기·말기",
        state: MaskingCardState = MaskingCardState.HIDDEN,
    ) = MaskingCard(
        id = id,
        prompt = prompt,
        answer = answer,
        x = 0.25f,
        y = 0.4f,
        w = 0.5f,
        h = 0.12f,
        state = state,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun legacyPageWithoutCardsLoadsEmptyListAndWritesNoKey() {
        val page = NotePage.fromJson(JSONObject("""{"id":"p","template":"GRID"}"""))
        assertTrue(page.maskingCards.isEmpty())
        assertFalse(page.toJson().has("maskingCards"))

        val notebook = Notebook.fromJson(JSONObject("""{"id":"nb","pages":[{"id":"p"}]}"""))
        assertTrue(notebook.pages.single().maskingCards.isEmpty())
    }

    @Test
    fun cardsRoundTripThroughNotebookJson() {
        val notebook = Notebook(
            id = "nb",
            pages = listOf(
                NotePage(
                    id = "p0",
                    maskingCards = listOf(
                        card("c1", state = MaskingCardState.HIDDEN),
                        card("c2", state = MaskingCardState.CORRECT),
                    ),
                ),
                NotePage(id = "p1", maskingCards = listOf(card("c3", state = MaskingCardState.WRONG))),
                NotePage(id = "p2"),
            ),
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals(2, restored.pages[0].maskingCards.size)
        assertEquals(1, restored.pages[1].maskingCards.size)
        assertTrue(restored.pages[2].maskingCards.isEmpty())
        val first = restored.pages[0].maskingCards[0]
        assertEquals("c1", first.id)
        assertEquals("세포분열의 단계는?", first.prompt)
        assertEquals("간기·전기·중기·후기·말기", first.answer)
        assertEquals(0.25f, first.x)
        assertEquals(0.4f, first.y)
        assertEquals(0.5f, first.w)
        assertEquals(0.12f, first.h)
        assertEquals(MaskingCardState.HIDDEN, first.state)
        assertEquals(1_700_000_000_000L, first.createdAt)
        assertEquals(MaskingCardState.CORRECT, restored.pages[0].maskingCards[1].state)
        assertEquals(MaskingCardState.WRONG, restored.pages[1].maskingCards[0].state)
        assertFalse(notebook.toJson().toString().contains("/data/data/"))
        assertFalse(notebook.toJson().toString().contains("content://"))
    }

    @Test
    fun malformedAndOutOfRangeCardJsonFallsBackToSafeDefaults() {
        val parsed = MaskingCard.fromJson(
            JSONObject(
                """{"id":"","prompt":"  질문  ","answer":"  정답  ",""" +
                    """"x":-4,"y":9,"w":80,"h":0,"state":"NOT_A_STATE","createdAt":0}""",
            ),
        )

        assertTrue(parsed.id.isNotBlank())
        assertEquals("질문", parsed.prompt)
        assertEquals("정답", parsed.answer)
        assertEquals(0f, parsed.x)
        assertEquals(1f, parsed.y)
        assertEquals(1f, parsed.w)
        assertEquals(MaskingCard.MIN_H, parsed.h)
        assertEquals(MaskingCardState.HIDDEN, parsed.state)
        assertTrue(parsed.createdAt > 0L)

        val defaults = MaskingCard.fromJson(JSONObject("{}"))
        assertEquals(MaskingCard.DEFAULT_X, defaults.x)
        assertEquals(MaskingCard.DEFAULT_W, defaults.w)
        assertEquals(MaskingCardState.HIDDEN, defaults.state)
        assertTrue(defaults.id.isNotBlank())
    }

    @Test
    fun revealMarkAndConcealAreImmutableTransitions() {
        val hidden = card()
        val revealed = MaskingCards.reveal(hidden)
        assertEquals(MaskingCardState.REVEALED, revealed.state)
        assertEquals(MaskingCardState.HIDDEN, hidden.state)
        assertEquals(revealed, MaskingCards.reveal(revealed))

        val correct = MaskingCards.mark(revealed, true)
        val wrong = MaskingCards.mark(revealed, false)
        assertEquals(MaskingCardState.CORRECT, correct.state)
        assertEquals(MaskingCardState.WRONG, wrong.state)
        assertEquals(MaskingCardState.REVEALED, revealed.state)

        assertEquals(MaskingCardState.HIDDEN, MaskingCards.conceal(wrong).state)
        assertEquals(hidden, MaskingCards.conceal(hidden))
        assertEquals("세포분열의 단계는?", wrong.prompt)
        assertEquals(0.25f, wrong.x)
    }

    @Test
    fun scoreTracksProgressAndAccuracyAcrossStates() {
        val cards = listOf(
            card("a", state = MaskingCardState.HIDDEN),
            card("b", state = MaskingCardState.HIDDEN),
            card("c", state = MaskingCardState.REVEALED),
            card("d", state = MaskingCardState.CORRECT),
            card("e", state = MaskingCardState.CORRECT),
            card("f", state = MaskingCardState.WRONG),
        )
        val score = MaskingCards.score(cards)

        assertEquals(6, score.total)
        assertEquals(2, score.hidden)
        assertEquals(1, score.revealed)
        assertEquals(2, score.correct)
        assertEquals(1, score.wrong)
        assertEquals(3, score.graded)
        assertEquals(4, score.started)
        assertEquals(4f / 6f, score.progress, 0.0001f)
        assertEquals(2f / 3f, score.accuracy, 0.0001f)
        assertTrue(MaskingCards.progressText(score).contains("정답 2/3"))

        val empty = MaskingCards.score(emptyList())
        assertEquals(0, empty.total)
        assertEquals(0f, empty.progress)
        assertEquals(0f, empty.accuracy)
    }

    @Test
    fun freshIdsProduceIndependentCopiesWithoutChangingContent() {
        val original = listOf(card("a"), card("b", state = MaskingCardState.CORRECT))
        val copies = MaskingCards.freshIds(original)

        assertEquals(2, copies.size)
        assertNotEquals(original[0].id, copies[0].id)
        assertNotEquals(original[1].id, copies[1].id)
        assertNotEquals(copies[0].id, copies[1].id)
        assertEquals(original[0].prompt, copies[0].prompt)
        assertEquals(original[1].answer, copies[1].answer)
        assertEquals(original[1].state, copies[1].state)
        assertEquals(original[0].x, copies[0].x)
        assertEquals(original[0].createdAt, copies[0].createdAt)
    }

    @Test
    fun normalizedKeepsRectInsidePageAndNeverReturnsBlankId() {
        val card = MaskingCard(id = "  ", x = 0.95f, y = -0.2f, w = 2f, h = -1f).normalized()
        assertTrue(card.id.isNotBlank())
        assertEquals(0.95f, card.x)
        assertEquals(0f, card.y)
        assertEquals(1f, card.w)
        assertEquals(MaskingCard.MIN_H, card.h)
    }
}
