package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each pen style has a distinct original nib graphic and accessible label. */
class PenStyleGlyphTest {

    @Test
    fun everyPenStyleHasADistinctVisualIdentity() {
        val specs = PenStyle.entries.map(::penStyleGlyphSpec)
        assertEquals(PenStyle.entries.size, specs.size)
        assertEquals(specs.size, specs.map { it.nib }.toSet().size)
        assertEquals(specs.size, specs.map { it.texture }.toSet().size)
        assertEquals(specs.size, specs.map { it.hint }.toSet().size)
    }

    @Test
    fun labelsMatchThePenBoxVocabulary() {
        assertEquals("볼펜", penStyleGlyphSpec(PenStyle.BALLPOINT).label)
        assertEquals("만년필", penStyleGlyphSpec(PenStyle.FOUNTAIN).label)
        assertEquals("붓", penStyleGlyphSpec(PenStyle.BRUSH).label)
        assertEquals("연필", penStyleGlyphSpec(PenStyle.PENCIL).label)
        PenStyle.entries.forEach {
            assertEquals(penStyleLabel(it), penStyleGlyphSpec(it).label)
        }
    }

    @Test
    fun talkBackLabelUsesTheSharedStateContract() {
        assertEquals("볼펜", penStyleTalkBackLabel(PenStyle.BALLPOINT, false))
        assertEquals("볼펜, 선택됨", penStyleTalkBackLabel(PenStyle.BALLPOINT, true))
        assertEquals("연필, 선택됨", penStyleTalkBackLabel(PenStyle.PENCIL, true))
    }

    @Test
    fun everyStyleHasAReadableCaption() {
        PenStyle.entries.forEach { style ->
            assertTrue(penStyleGlyphSpec(style).hint.isNotBlank())
        }
    }
}
