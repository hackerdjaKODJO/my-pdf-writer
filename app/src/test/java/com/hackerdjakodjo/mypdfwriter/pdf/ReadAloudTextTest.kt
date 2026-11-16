package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTextTest {

    private val pages = listOf(
        "첫 번째 페이지입니다.",
        "",
        "   ",
        "네 번째 페이지입니다.",
        "",
        "여섯 번째 페이지입니다.",
    )

    @Test
    fun chunksReturnsEmptyForBlankText() {
        assertEquals(emptyList<String>(), ReadAloudText.chunks(""))
        assertEquals(emptyList<String>(), ReadAloudText.chunks("  \n\r\n \t "))
    }

    @Test
    fun chunksKeepsShortTextAsOnePiece() {
        val text = " 짧은 문장입니다.\n둘째 줄입니다. "
        assertEquals(listOf("짧은 문장입니다.\n둘째 줄입니다."), ReadAloudText.chunks(text, 200))
    }

    @Test
    fun chunksRespectsLimitAndKeepsAllWords() {
        val paragraph = "이 문장은 충분히 길어서 여러 조각으로 나뉘어야 합니다. ".repeat(120)
        val chunks = ReadAloudText.chunks(paragraph, 100)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 100 })
        assertEquals(words(paragraph), words(chunks.joinToString(" ")))
    }

    @Test
    fun chunksPrefersSentenceBoundaries() {
        val sentence = "가나다라마바사아자차카타파하. "
        val text = sentence.repeat(20)
        val chunks = ReadAloudText.chunks(text, 80)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.dropLast(1).all { it.endsWith(".") })
    }

    @Test
    fun chunksHardSplitsTextWithoutWhitespace() {
        val chunks = ReadAloudText.chunks("A".repeat(250), 100)
        assertEquals(listOf("A".repeat(100), "A".repeat(100), "A".repeat(50)), chunks)
    }

    @Test
    fun firstReadablePageSkipsBlankPages() {
        assertEquals(0, ReadAloudText.firstReadablePage(pages, 0))
        assertEquals(3, ReadAloudText.firstReadablePage(pages, 1))
        assertEquals(5, ReadAloudText.firstReadablePage(pages, 4))
        assertNull(ReadAloudText.firstReadablePage(pages, 6))
        assertNull(ReadAloudText.firstReadablePage(emptyList(), 0))
    }

    @Test
    fun previousReadablePageSkipsBlankPages() {
        assertEquals(3, ReadAloudText.previousReadablePage(pages, 4))
        assertEquals(0, ReadAloudText.previousReadablePage(pages, 2))
        assertNull(ReadAloudText.previousReadablePage(pages, -1))
    }

    @Test
    fun nearestReadablePageFallsBackWhenOnlyEarlierPagesHaveText() {
        assertEquals(5, ReadAloudText.nearestReadablePage(pages, 4))
        assertEquals(5, ReadAloudText.nearestReadablePage(pages, 5))
        assertEquals(0, ReadAloudText.nearestReadablePage(listOf("first", "", " "), 1))
        assertNull(ReadAloudText.nearestReadablePage(listOf("", " "), 0))
    }

    @Test
    fun languageTagsAreStable() {
        assertEquals("ko-KR", ReadAloudLanguage.KOREAN.localeTag)
        assertEquals("en-US", ReadAloudLanguage.ENGLISH.localeTag)
    }

    private fun words(value: String): List<String> =
        value.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
