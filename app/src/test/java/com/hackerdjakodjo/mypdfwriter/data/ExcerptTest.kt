package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcerptTest {

    @Test
    fun excerptJsonRoundTripKeepsFields() {
        val excerpt = Excerpt(
            id = "excerpt-1",
            pageIndex = 7,
            text = "세포 호흡은 미토콘드리아에서 일어난다.",
            title = "생물 7쪽",
            createdAt = 1_760_000_000_000L,
        )

        val restored = Excerpt.fromJson(JSONObject(excerpt.toJson().toString()))

        assertEquals(excerpt, restored)
    }

    @Test
    fun missingIdGetsFreshIdAndDefaultsAreSafe() {
        val restored = Excerpt.fromJson(JSONObject("""{"page":2,"text":"발췌문"}"""))

        assertTrue(restored.id.isNotBlank())
        assertEquals(2, restored.pageIndex)
        assertEquals("발췌문", restored.text)
        assertEquals("", restored.title)
        assertTrue(restored.createdAt > 0L)
    }

    @Test
    fun listJsonRoundTripPreservesOrderAndContent() {
        val excerpts = listOf(
            Excerpt(id = "a", pageIndex = 0, text = "첫 번째", title = "1", createdAt = 10L),
            Excerpt(id = "b", pageIndex = 3, text = "두 번째", createdAt = 20L),
        )

        val restored = Excerpt.listFromJson(Excerpt.listToJson(excerpts))

        assertEquals(excerpts, restored)
    }
}
