package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure layout tests for the post-it export painter. Platform canvas calls are not
 * exercised here (the JVM unit-test source set has no Robolectric); the deterministic
 * half of the painter — geometry, tints, wrap/clip and visibility — is covered.
 */
class NoteCardPainterTest {

    private companion object {
        const val W = 1190f // A4 portrait at NoteExporter.RENDER_SCALE = 2
        const val H = 1684f
        const val EPS = 0.001f
    }

    private val card = MemoCard(
        id = "m1",
        text = "메모",
        x = 0.25f,
        y = 0.2f,
        w = 0.4f,
        h = 0.3f,
    )

    @Test
    fun normalizedRectMapsToTheRenderedPagePixels() {
        val rect = NoteCardPainter.cardRect(card, W, H)

        assertEquals(0.25f * W, rect.left, EPS)
        assertEquals(0.2f * H, rect.top, EPS)
        assertEquals(0.65f * W, rect.right, EPS)
        assertEquals(0.5f * H, rect.bottom, EPS)
        assertEquals(0.4f * W, rect.width, EPS)
        assertEquals(0.3f * H, rect.height, EPS)
    }

    @Test
    fun rectUsesTheSameA4PageAspectAsTheExporter() {
        val portrait = NoteExporter.pageSize(PageOrientation.PORTRAIT, 2f)
        val landscape = NoteExporter.pageSize(PageOrientation.LANDSCAPE, 2f)
        val full = card.copy(x = 0f, y = 0f, w = 1f, h = 1f)

        val p = NoteCardPainter.cardRect(full, portrait.widthPx.toFloat(), portrait.heightPx.toFloat())
        val l = NoteCardPainter.cardRect(full, landscape.widthPx.toFloat(), landscape.heightPx.toFloat())

        assertEquals(NotePaper.aspect(PageOrientation.PORTRAIT), p.width / p.height, 0.0001f)
        assertEquals(NotePaper.aspect(PageOrientation.LANDSCAPE), l.width / l.height, 0.0001f)
        assertEquals(1190f, p.width, EPS)
        assertEquals(1684f, p.height, EPS)
    }

    @Test
    fun layoutPicksTheOriginalModelTintsAndKeepsTextInsideTheCard() {
        MemoTint.entries.forEach { tint ->
            val layout = NoteCardPainter.layout(card.copy(tint = tint), W, H)
            assertEquals(tint.background, layout.fill)
            assertEquals(tint.edge, layout.edge)
            assertEquals(tint.ink, layout.ink)
            assertTrue(layout.textRect.left >= layout.rect.left)
            assertTrue(layout.textRect.top >= layout.rect.top)
            assertTrue(layout.textRect.right <= layout.rect.right)
            assertTrue(layout.textRect.bottom <= layout.rect.bottom)
        }
        assertEquals(0f, NoteCardPainter.layout(card, W, H).tilt, EPS)
    }

    @Test
    fun legacyNonFiniteAndOversizedCardsAreClamped() {
        val legacy = card.copy(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            w = 50f,
            h = -4f,
            tilt = 999f,
            text = "old",
        )
        val layout = NoteCardPainter.layout(legacy, W, H)
        val rect = layout.rect

        assertTrue(rect.left >= 0f && rect.top >= 0f)
        assertTrue(rect.right <= W + EPS && rect.bottom <= H + EPS)
        assertTrue(rect.width >= MemoCard.MIN_W * W - EPS)
        assertTrue(rect.height >= MemoCard.MIN_H * H - EPS)
        assertEquals(MemoCard.MAX_TILT, layout.tilt, EPS)
        assertTrue(layout.textLines.isNotEmpty())

        val degenerateSize = NoteCardPainter.layout(card, -1f, 0f).rect
        assertTrue(degenerateSize.right <= 1f + EPS)
        assertTrue(degenerateSize.bottom <= 1f + EPS)
    }

    @Test
    fun emptyCardListsAndEmptyTextAreNoOps() {
        assertTrue(NoteCardPainter.layoutCards(emptyList(), W, H).isEmpty())
        assertTrue(NoteCardPainter.visibleMemoCards(NotePage()).isEmpty())
        assertTrue(NoteCardPainter.wrapText("", 100f, 12f, 6).isEmpty())
        assertTrue(NoteCardPainter.wrapText("text", 100f, 12f, 0).isEmpty())
    }

    @Test
    fun koreanTextWrapsWithoutSpacesAndClipsWithEllipsis() {
        val lines = NoteCardPainter.wrapText("가".repeat(80), 96f, 12f, 6)

        assertEquals(6, lines.size)
        assertTrue(lines.last().endsWith("…"))
        // One Hangul syllable is one em = 12 px, so eight fit in 96 px.
        lines.forEach { assertTrue("line too wide: $it", it.length <= 8) }
    }

    @Test
    fun explicitNewlinesArePreservedAndLatinWordsBreakAtSpaces() {
        val lines = NoteCardPainter.wrapText("안녕하세요\nhello world again", 60f, 12f, 6)

        assertTrue(lines.size > 1)
        assertEquals("안녕하세요", lines.first())
        lines.forEach { assertTrue("line too wide: $it", lineWidth(it, 12f) <= 60f + EPS) }
    }

    @Test
    fun blankCardUsesTheKoreanPlaceholderInEdgeInk() {
        val layout = NoteCardPainter.layout(card.copy(text = "   "), W, H)

        assertTrue(layout.placeholder)
        assertEquals(listOf("빈 메모"), layout.textLines.map { it.text })
        assertTrue(layout.textSize >= 8f)
    }

    @Test
    fun baselinesAdvanceEvenlyFromTheTopOfTheTextRect() {
        val layout = NoteCardPainter.layout(card.copy(text = "line1\nline2\nline3"), W, H)
        val lines = layout.textLines

        assertEquals(3, lines.size)
        assertEquals(lines.first().x, lines[1].x, EPS)
        val step = lines[1].baseline - lines[0].baseline
        assertEquals(step, lines[2].baseline - lines[1].baseline, EPS)
        assertTrue(lines.first().baseline > layout.textRect.top)
    }

    @Test
    fun exportVisibilityFlagExcludesCardsWithoutTouchingTheModel() {
        val page = NotePage(memoCards = listOf(card, card.copy(id = "m2")))

        assertEquals(2, NoteCardPainter.visibleMemoCards(page).size)
        assertTrue(NoteCardPainter.visibleMemoCards(page, includeMemoCards = false).isEmpty())
        assertEquals(2, page.memoCards.size)
    }

    @Test
    fun legacyPageJsonAndDefaultsStayBackwardCompatible() {
        val legacy = JSONObject("""{"template":"LINED","memo":"hi"}""")
        val page = NotePage.fromJson(legacy)

        assertTrue(page.memoCards.isEmpty())
        assertFalse(page.toJson().has("memoCards"))
        assertTrue(NoteExportOptions().includeHiddenLayers.not())
        assertEquals("", NoteExportOptions().suggestedFileName)
    }

    @Test
    fun layoutIsDeterministicForTheSameInput() {
        assertEquals(NoteCardPainter.layout(card, W, H), NoteCardPainter.layout(card.copy(), W, H))
        assertEquals(
            NoteCardPainter.layoutCards(listOf(card, card.copy(id = "m2")), W, H),
            NoteCardPainter.layoutCards(listOf(card, card.copy(id = "m2")), W, H),
        )
    }

    /** Same conservative advance estimate the painter uses, for wrap assertions. */
    private fun lineWidth(line: String, textSize: Float): Float {
        var units = 0f
        for (ch in line) {
            units += when {
                ch == '\t' -> 0.9f
                ch == ' ' -> 0.32f
                ch.code < 0x20 -> 0f
                ch.code < 0x80 -> 0.58f
                else -> 1f
            }
        }
        return units * textSize
    }
}
