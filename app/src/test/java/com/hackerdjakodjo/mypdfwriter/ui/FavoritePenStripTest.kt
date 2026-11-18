package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure contracts behind the favorite strip: no Android/Compose runtime needed. */
class FavoritePenStripTest {

    private fun preset(style: PenStyle, tool: Tool = Tool.PEN, name: String = "내 펜") = PenPreset(
        id = "${style.name}-${tool.name}",
        name = name,
        penStyle = style,
        tool = tool,
    )

    @Test
    fun penTypeLabelsCoverEveryPenStyleAndTheHighlighter() {
        assertEquals("볼펜", favoritePenTypeLabel(preset(PenStyle.BALLPOINT)))
        assertEquals("만년필", favoritePenTypeLabel(preset(PenStyle.FOUNTAIN)))
        assertEquals("붓", favoritePenTypeLabel(preset(PenStyle.BRUSH)))
        assertEquals("연필", favoritePenTypeLabel(preset(PenStyle.PENCIL)))
        assertEquals("형광펜", favoritePenTypeLabel(preset(PenStyle.BALLPOINT, Tool.HIGHLIGHT)))
    }

    @Test
    fun slotDescriptionCarriesNameAndTypeForAccessibility() {
        assertEquals(
            "즐겨찾는 펜 내 펜, 붓",
            favoriteSlotDescription(preset(PenStyle.BRUSH)),
        )
        assertEquals(
            "즐겨찾는 펜 형광펜, 형광펜",
            favoriteSlotDescription(preset(PenStyle.BALLPOINT, Tool.HIGHLIGHT, "형광펜")),
        )
    }

    @Test
    fun stripRendersAtMostSixFavoriteSlots() {
        val many = (0 until 10).map { index -> PenPreset(id = "p$index", name = "펜 $index") }

        val slots = favoriteSlots(many)

        assertEquals(6, slots.size)
        assertEquals(many.take(6), slots)
        assertTrue(favoriteSlots(emptyList()).isEmpty())
    }
}
