package com.hackerdjakodjo.mypdfwriter.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class TextActionsDictionaryTest {

    @Test
    fun blankSelectionProducesEmptyQuery() {
        assertEquals("", TextActions.dictionaryQuery(""))
        assertEquals("", TextActions.dictionaryQuery("   "))
        assertEquals("", TextActions.dictionaryQuery("  \n\r\n \t "))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("단어", TextActions.dictionaryQuery("  단어  "))
        assertEquals("hello", TextActions.dictionaryQuery("\n\thello\t\n"))
    }

    @Test
    fun collapsesPdfLineWrapsIntoSingleSpace() {
        assertEquals(
            "첫 줄 둘째 줄 셋째 줄",
            TextActions.dictionaryQuery("첫 줄\n둘째 줄\n\n셋째 줄"),
        )
        assertEquals("alpha beta gamma", TextActions.dictionaryQuery("alpha   beta\t\tgamma"))
    }

    @Test
    fun keepsSelectedWordsAndPunctuationUntouched() {
        val selection = "  사전에서  찾기 — '단어' (test)!  "
        assertEquals("사전에서 찾기 — '단어' (test)!", TextActions.dictionaryQuery(selection))
        assertEquals("Hello, World", TextActions.dictionaryQuery("Hello,   World"))
    }

    @Test
    fun preservesCaseForDictionaryLookup() {
        assertEquals("DataFrame", TextActions.dictionaryQuery("  DataFrame  "))
        assertEquals("HTTP 요청", TextActions.dictionaryQuery("HTTP\n요청"))
    }
}
