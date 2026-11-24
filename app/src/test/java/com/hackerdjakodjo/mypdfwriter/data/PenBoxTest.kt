package com.hackerdjakodjo.mypdfwriter.data

import com.hackerdjakodjo.mypdfwriter.ui.penBoxFavoriteToggleLabel
import com.hackerdjakodjo.mypdfwriter.ui.penBoxReorderLabel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenBoxTest {

    @Test
    fun defaultsCoverTheFourPenStylesAndHighlighter() {
        val presets = defaultPenPresets()

        assertEquals(listOf("볼펜", "만년필", "붓", "연필", "형광펜"), presets.map { it.name })
        assertEquals(
            listOf(PenStyle.BALLPOINT, PenStyle.FOUNTAIN, PenStyle.BRUSH, PenStyle.PENCIL),
            presets.filter { it.tool == Tool.PEN }.map { it.penStyle },
        )
        assertEquals(Tool.HIGHLIGHT, presets.last().tool)
        assertTrue(presets.all { it.width in 0.001f..0.02f })
        assertTrue(presets.all { it.opacity in 0.1f..1f })
    }

    @Test
    fun penPresetJsonRoundTripKeepsStyleAndTool() {
        val preset = PenPreset(
            id = "brush-test",
            name = "굵은 붓",
            color = 0xFF3E2B23.toInt(),
            width = 0.014f,
            opacity = 0.8f,
            penStyle = PenStyle.BRUSH,
            tool = Tool.PEN,
            pressureCurve = PressureCurve.FIRM,
            pressureInk = false,
        )

        val restored = PenPreset.fromJson(JSONObject(preset.toJson().toString()))

        assertEquals(preset.id, restored.id)
        assertEquals("굵은 붓", restored.name)
        assertEquals(preset.color, restored.color)
        assertEquals(0.014f, restored.width, 0.00001f)
        assertEquals(0.8f, restored.opacity, 0.00001f)
        assertEquals(PenStyle.BRUSH, restored.penStyle)
        assertEquals(Tool.PEN, restored.tool)
        assertEquals(PressureCurve.FIRM, restored.pressureCurve)
        assertEquals(false, restored.pressureInk)
    }

    @Test
    fun legacyPresetWithoutToolDefaultsToPen() {
        val restored = PenPreset.fromJson(
            JSONObject("""{"id":"legacy","name":"볼펜","color":1,"width":0.004,"opacity":1,"style":"BALLPOINT"}"""),
        )
        assertEquals(Tool.PEN, restored.tool)
        assertEquals(PenStyle.BALLPOINT, restored.penStyle)
    }

    @Test
    fun favoritesUseTheSameBackupCoveredPenBoxFile() {
        assertEquals("pen_box", PenBoxStore.PREFS)
        assertEquals("presets_v1", PenBoxStore.KEY)
        assertEquals("favorites_v1", FavoritePens.KEY)
        assertTrue("pen_box.xml" in BackupArchive.PREFERENCE_FILES)
        assertEquals("${PenBoxStore.PREFS}.xml", BackupArchive.PREFERENCE_FILES.first())
    }

    @Test
    fun missingFavoritesKeySuggestsFirstFourDefaultPens() {
        val presets = defaultPenPresets()

        val favorites = FavoritePens.fromRaw(null, presets)

        assertEquals(presets.take(4).map { it.id }, favorites.map { it.id })
        assertFalse(favorites.any { it.tool == Tool.HIGHLIGHT })
    }

    @Test
    fun presentButEmptyOrBrokenFavoritesKeyStaysCleared() {
        val presets = defaultPenPresets()

        assertTrue(FavoritePens.fromRaw(FavoritePens.encode(emptyList()), presets).isEmpty())
        assertTrue(FavoritePens.fromRaw("[]", presets).isEmpty())
        assertTrue(FavoritePens.fromRaw("not-json", presets).isEmpty())
        assertTrue(FavoritePens.decode(null) == null)
        assertEquals(emptyList<String>(), FavoritePens.decode("[]"))
    }

    @Test
    fun favoritesAreCappedAtSixAndRoundTripThroughStorage() {
        val presets = (0 until 8).map { index ->
            PenPreset(id = "p$index", name = "펜 $index")
        }
        var ids = FavoritePens.defaultIds(presets)
        ids = FavoritePens.toggle(ids, "p4", presets)
        ids = FavoritePens.toggle(ids, "p5", presets)
        ids = FavoritePens.toggle(ids, "p6", presets)

        assertEquals(FavoritePens.MAX_SLOTS, ids.size)
        assertFalse("p6" in ids)
        val restored = FavoritePens.decode(FavoritePens.encode(ids))
        assertEquals(ids, restored)
        assertEquals(ids, FavoritePens.prune(restored!!, presets))
    }

    @Test
    fun pruningDropsDeletedIdsAndDuplicates() {
        val presets = listOf(
            PenPreset(id = "a", name = "A"),
            PenPreset(id = "b", name = "B"),
            PenPreset(id = "c", name = "C"),
        )
        val stale = listOf("a", "deleted", "b", "a", "c")

        assertEquals(listOf("a", "b", "c"), FavoritePens.prune(stale, presets))
        assertEquals(listOf("a", "b", "c"), FavoritePens.resolve(stale, presets).map { it.id })
    }

    @Test
    fun toggleAndMoveKeepOrderAndIgnoreInvalidRequests() {
        val presets = (0 until 6).map { index -> PenPreset(id = "p$index", name = "펜 $index") }
        val ids = listOf("p0", "p1", "p2")

        assertEquals(listOf("p1", "p0", "p2"), FavoritePens.move(ids, 0, 1))
        assertEquals(ids, FavoritePens.move(ids, 0, 0))
        assertEquals(ids, FavoritePens.move(ids, -1, 2))
        assertEquals(ids, FavoritePens.move(ids, 0, 99))

        assertEquals(listOf("p0", "p2"), FavoritePens.toggle(ids, "p1", presets))
        assertEquals(ids, FavoritePens.toggle(ids, "not-a-preset", presets))
        assertEquals(ids + "p3", FavoritePens.toggle(ids, "p3", presets))
    }

    @Test
    fun samePenStyleComparesInkSettingsRatherThanIdentity() {
        val pen = PenPreset(id = "one", color = 1, width = 0.004f, opacity = 0.9f)
        val clone = pen.copy(id = "two")
        val otherColor = pen.copy(color = 2)

        assertTrue(samePenStyle(pen, clone))
        assertFalse(samePenStyle(pen, otherColor))
        assertFalse(samePenStyle(pen, pen.copy(penStyle = PenStyle.BRUSH)))
        assertFalse(samePenStyle(pen, pen.copy(tool = Tool.HIGHLIGHT)))
        val firm = pen.copy(pressureCurve = PressureCurve.FIRM, pressureInk = true)
        assertFalse(samePenStyle(firm, firm.copy(pressureCurve = PressureCurve.SOFT)))
        assertFalse(samePenStyle(firm, firm.copy(pressureInk = false)))
    }

    @Test
    fun reorderAndStarLabelsIncludeSlotPosition() {
        assertEquals(
            "내 펜 2번 앞으로 (1번으로 이동)",
            penBoxReorderLabel("앞으로", 2, 1, "내 펜"),
        )
        assertEquals(
            "내 펜 2번 뒤로 (3번으로 이동)",
            penBoxReorderLabel("뒤로", 2, 3, "내 펜"),
        )
        assertEquals(
            "내 펜 2번 즐겨찾기 해제",
            penBoxFavoriteToggleLabel("내 펜", 2, favorite = true),
        )
        assertEquals(
            "내 펜 3번 즐겨찾기에 추가",
            penBoxFavoriteToggleLabel("내 펜", 3, favorite = false),
        )
    }
}
