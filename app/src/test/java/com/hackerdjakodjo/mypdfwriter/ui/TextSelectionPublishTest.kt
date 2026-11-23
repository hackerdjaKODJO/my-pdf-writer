package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The drag must publish only on real word-set changes, never on finger jitter. */
class TextSelectionPublishTest {

    private fun word(text: String, left: Float) = PdfTextWord(
        pageIndex = 0,
        text = text,
        rect = NormalizedRect(left, 0.1f, left + 0.08f, 0.12f),
    )

    private val hello = word("Hello", 0.1f)
    private val world = word("world", 0.2f)

    @Test
    fun jitterInsideTheSameWordIsNotPublished() {
        val first = listOf(hello)
        val jittered = listOf(hello) // fresh list, identical content
        assertTrue(TextSelectionPublish.shouldPublish(emptyList(), first))
        assertFalse(TextSelectionPublish.shouldPublish(first, jittered))
    }

    @Test
    fun addingOrRemovingWordsIsPublished() {
        assertTrue(TextSelectionPublish.shouldPublish(listOf(hello), listOf(hello, world)))
        assertTrue(TextSelectionPublish.shouldPublish(listOf(hello, world), listOf(world)))
        assertTrue(TextSelectionPublish.shouldPublish(listOf(world), emptyList()))
    }

    @Test
    fun orderChangesArePublished() {
        assertTrue(
            TextSelectionPublish.shouldPublish(
                listOf(hello, world),
                listOf(world, hello),
            ),
        )
    }

    @Test
    fun identicalListsStayStableAcrossManyFrames() {
        var published = emptyList<PdfTextWord>()
        var publishes = 0
        repeat(60) {
            val next = listOf(hello, world)
            if (TextSelectionPublish.shouldPublish(published, next)) {
                published = next
                publishes++
            }
        }
        assertEquals(1, publishes)
    }
}
