package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeLibraryPolicyTest {

    private val tags = listOf(
        "영어" to 4,
        "영어 독해" to 2,
        "수학" to 3,
    )

    @Test
    fun blankQueryKeepsEveryTag() {
        assertSame(tags, filterTagCounts(tags, ""))
        assertSame(tags, filterTagCounts(tags, "   "))
    }

    @Test
    fun queryMatchesCaseInsensitiveSubstrings() {
        assertEquals(
            listOf("영어" to 4, "영어 독해" to 2),
            filterTagCounts(tags, "영어"),
        )
        assertEquals(emptyList<Pair<String, Int>>(), filterTagCounts(tags, "과학"))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(
            listOf("수학" to 3),
            filterTagCounts(tags, "  수학  "),
        )
    }
}
