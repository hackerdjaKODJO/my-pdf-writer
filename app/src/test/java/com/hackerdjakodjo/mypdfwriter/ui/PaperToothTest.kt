package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic paper fibre must be identical in editor, studio and export. */
class PaperToothTest {

    @Test
    fun `marks are deterministic bounded and finite`() {
        val first = PaperTooth.marks()
        assertEquals(PaperTooth.MARK_COUNT, first.size)
        assertEquals(first, PaperTooth.marks())
        first.forEach { mark ->
            assertTrue("x ${mark.x}", mark.x in 0f..1f)
            assertTrue("y ${mark.y}", mark.y in 0f..1f)
            assertTrue("radius ${mark.radius}", mark.radius in 0.35f..1.1f)
        }
    }

    @Test
    fun `alpha is subtle and ordered by tint`() {
        assertTrue(PaperTooth.alpha(PaperTint.WHITE, false) < PaperTooth.alpha(PaperTint.WARM, false))
        assertTrue(PaperTooth.alpha(PaperTint.LIGHT_GRAY, false) < PaperTooth.alpha(PaperTint.WARM, false))
        PaperTint.entries.forEach { tint ->
            assertTrue("alpha $tint", PaperTooth.alpha(tint, false) in 1..31)
            assertTrue("dark alpha $tint", PaperTooth.alpha(tint, true) <= PaperTooth.alpha(tint, false))
        }
    }

    @Test
    fun `sheet shadow fades across layers`() {
        assertEquals(listOf(34), sheetShadowAlphas(34, 1))
        val layers = sheetShadowAlphas(34, 3)
        assertEquals(listOf(34, 15, 3), layers)
        assertTrue(layers.zipWithNext().all { (a, b) -> a > b })
        assertTrue(sheetShadowAlphas(-5, 3).all { it == 0 })
    }
}
