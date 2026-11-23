package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure checks for the realistic instrument drawings in the pen picker. */
class PenIllustrationTest {

    private val specs = PenStyle.entries.map(::penIllustrationSpec)

    @Test
    fun everyStyleHasAnIllustrationWithBodyParts() {
        assertEquals(PenStyle.entries.size, specs.size)
        assertEquals(
            listOf("볼펜", "만년필", "붓", "연필"),
            specs.map { it.label },
        )
        specs.forEach { spec ->
            assertTrue("${spec.label} must have body parts", spec.parts.size >= 3)
            assertTrue("${spec.label} must have a trail", spec.trail.size >= 3)
        }
    }

    @Test
    fun eachInstrumentHasItsOwnSilhouette() {
        assertEquals(specs.size, specs.map { it.parts }.toSet().size)
        // Distinguishing details a student can actually see in the chip.
        assertTrue(specs[0].parts.any { it is PenPart.RoundRect && it.color == PenPartColor.METAL && it.x0 >= 0.86f }) // ballpoint clicker
        assertTrue(specs[1].parts.any { it is PenPart.Polygon && it.color == PenPartColor.GOLD }) // fountain nib
        assertTrue(specs[1].parts.any { it is PenPart.Circle && it.color == PenPartColor.CUTOUT }) // breather hole
        assertTrue(specs[2].parts.any { it is PenPart.RoundRect && it.color == PenPartColor.METAL }) // brush ferrule
        assertTrue(specs[3].parts.any { it is PenPart.RoundRect && it.color == PenPartColor.ERASER }) // pencil eraser
        assertTrue(specs[3].parts.any { it is PenPart.Polygon && it.color == PenPartColor.GRAPHITE }) // graphite
    }

    @Test
    fun allInstrumentsShareTheSameWritingTipAndStayInsideTheBox() {
        specs.forEach { spec ->
            assertTrue(spec.parts.any { part ->
                when (part) {
                    is PenPart.Circle -> part.center == PenIllustrationLayout.TIP
                    is PenPart.Polygon -> part.points.first() == PenIllustrationLayout.TIP
                    else -> false
                }
            })
            spec.parts.forEach { part ->
                val points = when (part) {
                    is PenPart.RoundRect -> listOf(
                        IllustrationPoint(part.x0, part.y0),
                        IllustrationPoint(part.x1, part.y1),
                    )
                    is PenPart.Polygon -> part.points
                    is PenPart.Circle -> listOf(part.center)
                    is PenPart.Line -> listOf(part.from, part.to)
                }
                points.forEach { point ->
                    assertTrue("${spec.label} x out of range: ${point.x}", point.x in 0f..1f)
                    assertTrue("${spec.label} y out of range: ${point.y}", point.y in 0f..1f)
                }
            }
            assertTrue("sample stroke must sit below the pen", spec.trail.all { it.y > 0.5f })
        }
        assertEquals(PenIllustrationLayout.TIP, IllustrationPoint(0.10f, 0.50f))
    }

    @Test
    fun labelsAndHintsMatchTheSharedPenStyleSpec() {
        PenStyle.entries.forEachIndexed { index, style ->
            val illustration = penIllustrationSpec(style)
            val base = penStyleGlyphSpec(style)
            assertEquals(penStyleLabel(style), illustration.label)
            assertEquals(base.label, illustration.label)
            assertEquals(base.hint, illustration.hint)
            assertEquals(base.texture, illustration.texture)
            assertEquals(-45f, illustration.rotationDeg, 0f)
            assertEquals(index, PenStyle.entries.indexOf(style))
        }
    }

    @Test
    fun illustrationTalkBackDelegatesToTheSharedContract() {
        PenStyle.entries.forEach { style ->
            listOf(false, true).forEach { selected ->
                assertEquals(
                    penStyleTalkBackLabel(style, selected),
                    penIllustrationTalkBackLabel(style, selected),
                )
            }
        }
        assertEquals("볼펜, 선택됨", penIllustrationTalkBackLabel(PenStyle.BALLPOINT, true))
        assertEquals("연필", penIllustrationTalkBackLabel(PenStyle.PENCIL, false))
    }

    @Test
    fun strokeCharacterIsEncodedInTheIllustration() {
        val even = penIllustrationSpec(PenStyle.BALLPOINT)
        val tapered = penIllustrationSpec(PenStyle.FOUNTAIN)
        val wash = penIllustrationSpec(PenStyle.BRUSH)
        val grain = penIllustrationSpec(PenStyle.PENCIL)
        assertTrue(even.trailWidth < tapered.trailWidth)
        assertTrue(wash.trailWidth > even.trailWidth)
        assertTrue(wash.trailAlpha < grain.trailAlpha)
        assertTrue(tapered.trailOverlay)
        assertEquals(2, grain.trailDots.size)
        assertTrue(even.trailDots.isEmpty())
    }

    @Test
    fun scalingToleratesZeroAndNegativeSizes() {
        val tip = PenIllustrationLayout.TIP
        assertEquals(IllustrationPoint(0f, 0f), scaleIllustrationPoint(tip, 0f, 0f))
        assertEquals(IllustrationPoint(0f, 0f), scaleIllustrationPoint(tip, -4f, -8f))
        val scaled = scaleIllustrationPoint(tip, 40f, 40f)
        assertEquals(4f, scaled.x, 0.0001f)
        assertEquals(20f, scaled.y, 0.0001f)
        val wide = scaleIllustrationPoint(tip, 60f, 40f)
        assertEquals(14f, wide.x, 0.0001f) // centred square inside the wider box
        assertEquals(20f, wide.y, 0.0001f)
    }
}
