package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatchLayoutTest {

    @Test
    fun singleWordMatchReturnsThatWordsRect() {
        val hello = word("Hello", 0.10f, 0.10f, 0.20f, 0.13f)
        val world = word("world", 0.21f, 0.10f, 0.30f, 0.13f)
        val line = line(0, hello, world)

        val hits = SearchMatchLayout.matches(listOf(line), "world")

        assertEquals(1, hits.size)
        assertEquals(0, hits[0].pageIndex)
        assertEquals(world.rect, hits[0].rect)
        assertEquals("world", hits[0].text)
    }

    @Test
    fun matchIgnoresCaseAndSurroundingWhitespace() {
        val hello = word("Hello", 0.10f, 0.10f, 0.20f, 0.13f)
        val line = line(0, hello)

        val hits = SearchMatchLayout.matches(listOf(line), "  HeLLo \t")

        assertEquals(1, hits.size)
        assertEquals(hello.rect, hits[0].rect)
        assertEquals("Hello", hits[0].text)
    }

    @Test
    fun multiWordQueryUnionsAdjacentWordRects() {
        val hello = word("Hello", 0.10f, 0.10f, 0.20f, 0.13f)
        val world = word("world", 0.21f, 0.10f, 0.30f, 0.13f)
        val line = line(0, hello, world)

        val hits = SearchMatchLayout.matches(listOf(line), "hello   world")

        assertEquals(1, hits.size)
        assertEquals(hello.rect.union(world.rect), hits[0].rect)
        assertEquals("Hello world", hits[0].text)
    }

    @Test
    fun koreanAndMixedLanguageTextMatches() {
        val greeting = word("안녕하세요", 0.10f, 0.10f, 0.28f, 0.13f)
        val format = word("PDF", 0.29f, 0.10f, 0.36f, 0.13f)
        val target = word("세계", 0.37f, 0.10f, 0.45f, 0.13f)
        val line = line(0, greeting, format, target)

        val single = SearchMatchLayout.matches(listOf(line), "세계")
        assertEquals(1, single.size)
        assertEquals(target.rect, single[0].rect)
        assertEquals("세계", single[0].text)

        val spanning = SearchMatchLayout.matches(listOf(line), "하세요 pdf 세계")
        assertEquals(1, spanning.size)
        assertEquals(greeting.rect.union(format.rect).union(target.rect), spanning[0].rect)
        assertEquals("하세요 PDF 세계", spanning[0].text)
    }

    @Test
    fun blankAndMissingQueriesReturnNoMatches() {
        val line = line(0, word("Hello", 0.10f, 0.10f, 0.20f, 0.13f))

        assertTrue(SearchMatchLayout.matches(listOf(line), "").isEmpty())
        assertTrue(SearchMatchLayout.matches(listOf(line), "   \n\t").isEmpty())
        assertTrue(SearchMatchLayout.matches(listOf(line), "missing").isEmpty())
        assertTrue(SearchMatchLayout.matches(emptyList(), "hello").isEmpty())
    }

    @Test
    fun matchesForPageKeepsOnlyRequestedPage() {
        val first = line(0, word("Alpha", 0.10f, 0.10f, 0.20f, 0.13f))
        val second = line(
            1,
            word("Alpha", 0.10f, 0.10f, 0.20f, 0.13f, page = 1),
            word("beta", 0.21f, 0.10f, 0.30f, 0.13f, page = 1),
        )
        val third = line(2, word("Beta", 0.10f, 0.10f, 0.20f, 0.13f, page = 2))
        val lines = listOf(first, second, third)

        assertEquals(2, SearchMatchLayout.matches(lines, "alpha").size)

        val pageOne = SearchMatchLayout.matchesForPage(lines, 1, "alpha")
        assertEquals(1, pageOne.size)
        assertEquals(1, pageOne[0].pageIndex)
        assertEquals("Alpha", pageOne[0].text)

        assertEquals(1, SearchMatchLayout.matchesForPage(lines, 0, "alpha").size)
        assertTrue(SearchMatchLayout.matchesForPage(lines, 9, "alpha").isEmpty())
        assertTrue(SearchMatchLayout.matchesForPage(lines, 1, "  ").isEmpty())
    }

    @Test
    fun touchingMatchRectsOnOneLineAreMerged() {
        val first = word("aa", 0.10f, 0.10f, 0.20f, 0.13f)
        val second = word("aa", 0.20f, 0.10f, 0.30f, 0.13f)
        val line = line(0, first, second)

        val hits = SearchMatchLayout.matches(listOf(line), "aa")

        assertEquals(1, hits.size)
        assertEquals(first.rect.union(second.rect), hits[0].rect)
        assertEquals("aa aa", hits[0].text)
    }

    @Test
    fun separatedMatchesKeepSeparateRects() {
        val first = word("aa", 0.10f, 0.10f, 0.20f, 0.13f)
        val second = word("aa", 0.25f, 0.10f, 0.35f, 0.13f)
        val line = line(0, first, second)

        val hits = SearchMatchLayout.matches(listOf(line), "aa")

        assertEquals(2, hits.size)
        assertEquals(first.rect, hits[0].rect)
        assertEquals(second.rect, hits[1].rect)
        assertEquals("aa", hits[0].text)
        assertEquals("aa", hits[1].text)
    }

    private fun line(pageIndex: Int, vararg words: PdfTextWord): PdfTextLine {
        val rect = words.drop(1).fold(words.first().rect) { acc, word -> acc.union(word.rect) }
        return PdfTextLine(
            pageIndex = pageIndex,
            text = words.joinToString(" ") { it.text },
            words = words.toList(),
            rect = rect,
        )
    }

    private fun word(
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        page: Int = 0,
    ): PdfTextWord = PdfTextWord(
        pageIndex = page,
        text = text,
        rect = NormalizedRect(left, top, right, bottom),
    )
}
