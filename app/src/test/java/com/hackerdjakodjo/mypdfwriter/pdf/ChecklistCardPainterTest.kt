package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.ChecklistItem
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-only coverage for checklist export geometry, text and completed state. */
class ChecklistCardPainterTest {

    private companion object {
        const val W = 1190f // A4 portrait at NoteExporter.RENDER_SCALE = 2
        const val H = 1684f
        const val EPS = 0.001f
    }

    private val card = ChecklistCard(
        id = "check-1",
        title = "오늘 공부",
        items = listOf(
            ChecklistItem(id = "a", text = "국어", done = true),
            ChecklistItem(id = "b", text = "수학", done = false),
            ChecklistItem(id = "c", text = "영어", done = true),
        ),
        x = 0.25f,
        y = 0.20f,
        w = 0.40f,
        h = 0.30f,
    )

    @Test
    fun normalizedRectMapsToRenderedPagePixels() {
        val rect = ChecklistCardPainter.cardRect(card, W, H)

        assertEquals(0.25f * W, rect.left, EPS)
        assertEquals(0.20f * H, rect.top, EPS)
        assertEquals(0.65f * W, rect.right, EPS)
        assertEquals(0.50f * H, rect.bottom, EPS)
        assertEquals(0.40f * W, rect.width, EPS)
        assertEquals(0.30f * H, rect.height, EPS)
    }

    @Test
    fun invalidNormalizedGeometryIsRepairedAndKeptOnPage() {
        val outside = card.copy(x = -4f, y = 9f, w = 8f, h = -3f)
        val outsideRect = ChecklistCardPainter.cardRect(outside, W, H)

        assertEquals(0f, outsideRect.left, EPS)
        assertEquals((1f - ChecklistCard.DEFAULT_H) * H, outsideRect.top, EPS)
        assertEquals(W, outsideRect.right, EPS)
        assertEquals(H, outsideRect.bottom, EPS)

        val nonFinite = card.copy(
            x = Float.NaN,
            y = Float.NEGATIVE_INFINITY,
            w = Float.POSITIVE_INFINITY,
            h = Float.NaN,
        )
        val repaired = ChecklistCardPainter.cardRect(nonFinite, W, H)
        assertTrue(repaired.isValid)
        assertTrue(repaired.left >= 0f && repaired.top >= 0f)
        assertTrue(repaired.right <= W + EPS && repaired.bottom <= H + EPS)
        assertEquals(ChecklistCard.DEFAULT_W * W, repaired.width, EPS)
        assertEquals(ChecklistCard.DEFAULT_H * H, repaired.height, EPS)

        val repairedLayout = ChecklistCardPainter.layout(nonFinite, W, H)
        assertTrue(repairedLayout.titleRect.isValid)
        assertTrue(repairedLayout.progressRect.isValid)
        assertTrue(repairedLayout.items.all { item ->
            item.rowRect.isValid && item.checkboxRect.isValid && item.textRect.isValid
        })
    }

    @Test
    fun invalidPageDimensionsAreBoundedAndNeverProduceNonFinitePixels() {
        val full = card.copy(x = 0f, y = 0f, w = 1f, h = 1f)
        val invalid = ChecklistCardPainter.cardRect(full, Float.NaN, -10f)
        val infinite = ChecklistCardPainter.cardRect(full, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        val huge = ChecklistCardPainter.cardRect(full, 1e20f, 1e20f)

        assertEquals(1f, invalid.width, EPS)
        assertEquals(1f, invalid.height, EPS)
        assertEquals(1f, infinite.width, EPS)
        assertEquals(1f, infinite.height, EPS)
        assertEquals(8192f, huge.width, EPS)
        assertEquals(8192f, huge.height, EPS)
        assertTrue(ChecklistCardPainter.layout(card, -1f, 0f).rect.isValid)
    }

    @Test
    fun exportIncludesEveryItemBeyondBothPreviewAndEditCaps() {
        val items = (0 until 48).map { index ->
            ChecklistItem(id = "item-$index", text = "항목 $index", done = index % 3 == 0)
        }
        val many = card.copy(items = items, x = 0.20f, y = 0.10f, w = 0.60f, h = 0.30f)
        val layout = ChecklistCardPainter.layout(many, W, H)

        assertSame(items, ChecklistCardPainter.itemsForExport(many))
        assertEquals(48, layout.totalItems)
        assertEquals(48, layout.items.size)
        assertTrue(layout.allItemsRendered)
        assertEquals(items.map { it.id }, layout.items.map { it.id })
        assertEquals(items.map { it.text }, layout.items.map { it.lines.single().text })
        assertEquals(items.map { it.done }, layout.items.map { it.checked })
        assertTrue(layout.items.all { item ->
            item.rowRect.height > 0f && item.checkboxRect.width > 0f && item.lines.isNotEmpty()
        })
        assertEquals(48, layout.progress.total)
        assertEquals(16, layout.progress.checked)
    }

    @Test
    fun completedStateAndProgressArePreservedWithoutMutatingTheCard() {
        val originalItems = card.items.toList()
        val layout = ChecklistCardPainter.layout(card, W, H)

        assertEquals(listOf(true, false, true), layout.items.map { it.checked })
        assertEquals(2, layout.progress.checked)
        assertEquals(3, layout.progress.total)
        assertEquals(67, layout.progress.percent)
        assertEquals(2f / 3f, layout.progressFraction, EPS)
        assertEquals("2/3 · 67%", layout.progressText)
        assertEquals(originalItems, card.items)
    }

    @Test
    fun blankTitleAndItemsUseStableKoreanFallbacks() {
        val blank = card.copy(
            title = " \t\n ",
            items = listOf(
                ChecklistItem(id = "blank", text = "\r\n\t", done = true),
                ChecklistItem(id = "empty", text = ""),
            ),
        )
        val layout = ChecklistCardPainter.layout(blank, W, H)

        assertTrue(layout.titlePlaceholder)
        assertEquals(Checklists.DEFAULT_TITLE, layout.title)
        assertEquals(listOf(Checklists.DEFAULT_TITLE), layout.titleLines.map { it.text })
        assertTrue(layout.items.all { it.placeholder })
        assertEquals(listOf("빈 항목", "빈 항목"), layout.items.flatMap { item -> item.lines.map { it.text } })
        assertEquals(listOf(true, false), layout.items.map { it.checked })
    }

    @Test
    fun longKoreanTextWrapsAndTruncatesInsideFiniteClipRects() {
        val long = card.copy(
            title = "제목".repeat(300),
            items = (0 until 8).map { index ->
                ChecklistItem(id = "long-$index", text = "가".repeat(500) + index, done = index == 2)
            },
            x = 0.20f,
            y = 0.10f,
            w = 0.60f,
            h = 0.70f,
        )
        val layout = ChecklistCardPainter.layout(long, W, H)

        assertEquals(8, layout.items.size)
        assertTrue(layout.titleLines.single().text.endsWith("…"))
        assertTrue(layout.items.all { item -> item.lines.isNotEmpty() })
        assertTrue(layout.items.all { item -> item.lines.last().text.endsWith("…") })
        layout.items.forEach { item ->
            assertTrue(item.lines.all { line ->
                line.baseline >= item.textRect.top - EPS &&
                    line.baseline <= item.textRect.bottom + EPS &&
                    ChecklistCardPainter.estimatedTextWidth(line.text, item.textSize) <=
                    item.textRect.width + EPS
            })
        }

        assertEquals(layout.titleRect, layout.rect.intersection(layout.titleRect))
        assertEquals(layout.progressRect, layout.rect.intersection(layout.progressRect))
        assertEquals(layout.progressTrackRect, layout.rect.intersection(layout.progressTrackRect))
        assertEquals(layout.contentRect, layout.contentRect.intersection(layout.rect))
        layout.items.forEach { item ->
            assertEquals(item.rowRect, item.rowRect.intersection(layout.contentRect))
            assertEquals(item.checkboxRect, item.checkboxRect.intersection(item.rowRect))
            assertEquals(item.textRect, item.textRect.intersection(item.rowRect))
        }
    }

    @Test
    fun emojiCodePointsRemainIntact() {
        val emoji = card.copy(items = listOf(ChecklistItem(id = "emoji", text = "완료 😀")))
        val line = ChecklistCardPainter.layout(emoji, W, H).items.single().lines.single()

        assertEquals("완료 😀", line.text)
        assertTrue(line.text.codePointAt(0) != ' '.code)
        assertEquals(2, Character.charCount(line.text.codePointAt(line.text.indexOf("😀"))))
    }

    @Test
    fun hangulWithoutSpacesWrapsByCodePointAndEllipsizes() {
        val lines = ChecklistCardPainter.wrapText("가".repeat(80), 96f, 12f, 6)

        assertEquals(6, lines.size)
        assertTrue(lines.last().endsWith("…"))
        lines.forEach { line ->
            assertTrue(
                "line too wide: $line",
                ChecklistCardPainter.estimatedTextWidth(line, 12f) <= 96f + EPS,
            )
            assertTrue(line.length <= 8)
        }
        assertEquals(listOf("가가가…"), ChecklistCardPainter.wrapText("가".repeat(20), 48f, 12f, 1))
    }

    @Test
    fun latinWordsPreferSpacesAndExplicitNewlinesArePreserved() {
        val lines = ChecklistCardPainter.wrapText("안녕하세요\nhello world again", 60f, 12f, 6)

        assertEquals("안녕하세요", lines.first())
        assertEquals("hello", lines[1])
        assertEquals("world", lines[2])
        assertEquals("again", lines[3])
        assertTrue(lines.all {
            ChecklistCardPainter.estimatedTextWidth(it, 12f) <= 60f + EPS
        })
    }

    @Test
    fun emptyInputsAndInvalidWrapLimitsAreSafe() {
        assertTrue(ChecklistCardPainter.layoutCards(emptyList(), W, H).isEmpty())
        assertTrue(ChecklistCardPainter.wrapText("", 100f, 12f, 4).isEmpty())
        assertTrue(ChecklistCardPainter.wrapText("text", 100f, 12f, 0).isEmpty())
        assertTrue(ChecklistCardPainter.wrapText("text", Float.NaN, Float.NaN, -1).isEmpty())

        val emptyCard = ChecklistCardPainter.layout(card.copy(items = emptyList()), W, H)
        assertTrue(emptyCard.items.isEmpty())
        assertEquals(0, emptyCard.progress.total)
        assertEquals(0f, emptyCard.progressFraction, EPS)
        assertTrue(emptyCard.allItemsRendered)
    }

    @Test
    fun layoutIsDeterministicEvenForLegacyBlankIdsAndInvalidGeometry() {
        val legacy = card.copy(id = "", createdAt = 0L, title = "", x = Float.NaN, h = -1f)
        val first = ChecklistCardPainter.layout(legacy, W, H)
        val second = ChecklistCardPainter.layout(legacy.copy(), W, H)

        assertEquals(first, second)
        assertFalse(first.id.isNotEmpty())
        assertEquals(
            ChecklistCardPainter.layoutCards(listOf(card, legacy), W, H),
            ChecklistCardPainter.layoutCards(listOf(card, legacy.copy()), W, H),
        )
    }
}
