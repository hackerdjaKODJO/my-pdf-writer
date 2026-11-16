package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteLayersTest {

    private fun layer(
        id: String,
        name: String = id,
        visible: Boolean = true,
        locked: Boolean = false,
        opacity: Float = 1f,
    ) = NoteLayer(
        id = id,
        name = name,
        visible = visible,
        locked = locked,
        opacity = opacity,
    )

    private fun ink(id: String, layerId: String?): Annotation = Annotation(
        id = id,
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF1B1C19.toInt(),
        width = 0.004f,
        points = listOf(),
        layerId = layerId,
    )

    private fun page(
        layers: List<NoteLayer>,
        annotations: List<Annotation> = emptyList(),
    ) = NotePage(id = "page", layers = layers, annotations = annotations)

    // ------------------------------------------------------------------- add

    @Test
    fun addLayer_appendsNamedLayerAboveExistingStack() {
        val original = page(
            layers = listOf(layer("base", name = "기본")),
            annotations = listOf(ink("ink", "base")),
        )

        val result = NoteLayers.addLayer(original)

        assertEquals(listOf("base", result.layers.last().id), result.layers.map { it.id })
        assertEquals("새 레이어", result.layers.last().name)
        assertEquals(original.annotations, result.annotations)
        assertEquals(1, original.layers.size)
        assertNotEquals(result.layers.first().id, result.layers.last().id)
    }

    @Test
    fun addLayer_trimsCustomNameAndFallsBackForBlankName() {
        val original = page(listOf(layer("base")))

        assertEquals("필기", NoteLayers.addLayer(original, "  필기  ").layers.last().name)
        assertEquals("새 레이어", NoteLayers.addLayer(original, "   ").layers.last().name)
    }

    // ---------------------------------------------------------------- delete

    @Test
    fun deleteLayer_movesItsAnnotationsToNearestLowerLayer() {
        val original = page(
            layers = listOf(layer("bottom"), layer("middle"), layer("top")),
            annotations = listOf(
                ink("bottom-ink", "bottom"),
                ink("middle-ink", "middle"),
                ink("top-ink", "top"),
            ),
        )

        val result = NoteLayers.deleteLayer(original, "middle")

        assertEquals(listOf("bottom", "top"), result.layers.map { it.id })
        assertEquals(3, result.annotations.size)
        assertEquals("bottom", result.annotations.first { it.id == "middle-ink" }.layerId)
        assertEquals("bottom", result.annotations.first { it.id == "bottom-ink" }.layerId)
        assertEquals("top", result.annotations.first { it.id == "top-ink" }.layerId)
    }

    @Test
    fun deleteLayer_bottomLayerMovesNullAndOwnedInkToNextLayer() {
        val original = page(
            layers = listOf(layer("bottom"), layer("top")),
            annotations = listOf(
                ink("legacy", null),
                ink("bottom-ink", "bottom"),
            ),
        )

        val result = NoteLayers.deleteLayer(original, "bottom")

        assertEquals(listOf("top"), result.layers.map { it.id })
        assertTrue(result.annotations.all { it.layerId == "top" })
        assertEquals(listOf("legacy", "bottom-ink"), result.annotations.map { it.id })
    }

    @Test
    fun deleteLayer_topLayerMovesInkDown() {
        val original = page(
            layers = listOf(layer("bottom"), layer("top")),
            annotations = listOf(ink("top-ink", "top")),
        )

        val result = NoteLayers.deleteLayer(original, "top")

        assertEquals(listOf("bottom"), result.layers.map { it.id })
        assertEquals("bottom", result.annotations.single().layerId)
    }

    @Test
    fun deleteLayer_onlyLayerOrUnknownIdIsNoOp() {
        val only = page(
            layers = listOf(layer("only")),
            annotations = listOf(ink("ink", null)),
        )

        assertSame(only, NoteLayers.deleteLayer(only, "only"))
        assertSame(only, NoteLayers.deleteLayer(only, "missing"))

        val multi = page(listOf(layer("a"), layer("b")))
        assertSame(multi, NoteLayers.deleteLayer(multi, "missing"))
    }

    @Test
    fun deleteLayer_neverDropsInk() {
        val original = page(
            layers = listOf(layer("a"), layer("b"), layer("c")),
            annotations = (1..20).map { ink("ink-$it", "b") },
        )

        val result = NoteLayers.deleteLayer(original, "b")

        assertEquals(original.annotations.map { it.id }, result.annotations.map { it.id })
        assertTrue(result.annotations.all { it.layerId == "a" })
    }

    // ------------------------------------------------------------------ move

    @Test
    fun moveLayer_positiveDirectionMovesUpTheStack() {
        val original = page(listOf(layer("a"), layer("b"), layer("c")))

        assertEquals(
            listOf("b", "a", "c"),
            NoteLayers.moveLayer(original, "a", 1).layers.map { it.id },
        )
        assertEquals(
            listOf("a", "c", "b"),
            NoteLayers.moveLayer(original, "b", 1).layers.map { it.id },
        )
        assertEquals(
            listOf("a", "c", "b"),
            NoteLayers.moveLayer(original, "c", -1).layers.map { it.id },
        )
    }

    @Test
    fun moveLayer_ignoresEndsZeroAndUnknownId() {
        val original = page(listOf(layer("a"), layer("b"), layer("c")))

        assertSame(original, NoteLayers.moveLayer(original, "c", 1))
        assertSame(original, NoteLayers.moveLayer(original, "a", -1))
        assertSame(original, NoteLayers.moveLayer(original, "b", 0))
        assertSame(original, NoteLayers.moveLayer(original, "missing", 1))
    }

    @Test
    fun moveLayer_preservesAnnotationOrder() {
        val original = page(
            layers = listOf(layer("a"), layer("b")),
            annotations = listOf(ink("one", "a"), ink("two", "b")),
        )

        val result = NoteLayers.moveLayer(original, "a", 1)

        assertEquals(listOf("one", "two"), result.annotations.map { it.id })
        assertEquals(original.annotations, result.annotations)
    }

    // ---------------------------------------------------------- visibility

    @Test
    fun visibleAnnotations_filtersHiddenLayersButKeepsLockedLayers() {
        val original = page(
            layers = listOf(
                layer("base", visible = false),
                layer("hidden", visible = false),
                layer("locked", locked = true),
                layer("shown"),
            ),
            annotations = listOf(
                ink("legacy", null),
                ink("hidden-ink", "hidden"),
                ink("locked-ink", "locked"),
                ink("shown-ink", "shown"),
                ink("stale-ink", "deleted-layer"),
            ),
        )

        val visible = NoteLayers.visibleAnnotations(original)

        assertEquals(
            listOf("locked-ink", "shown-ink", "stale-ink"),
            visible.map { it.id },
        )
    }

    @Test
    fun visibleAnnotations_nullLayerUsesFirstLayerVisibility() {
        val page = page(
            layers = listOf(layer("base", visible = false), layer("top")),
            annotations = listOf(ink("legacy", null), ink("top-ink", "top")),
        )

        assertEquals(
            listOf("top-ink"),
            NoteLayers.visibleAnnotations(page).map { it.id },
        )

        val shown = NoteLayers.setVisible(page, "base", true)
        assertEquals(
            listOf("legacy", "top-ink"),
            NoteLayers.visibleAnnotations(shown).map { it.id },
        )
    }

    @Test
    fun setVisibleAndSetLocked_toggleOnlyTheTargetLayer() {
        val original = page(
            layers = listOf(layer("a"), layer("b", locked = true)),
            annotations = listOf(ink("a-ink", "a"), ink("b-ink", "b")),
        )

        val hidden = NoteLayers.setVisible(original, "a", false)
        assertEquals(false, hidden.layers.first().visible)
        assertTrue(hidden.layers.last().locked)
        assertEquals(original.annotations, hidden.annotations)

        val unlocked = NoteLayers.setLocked(hidden, "b", false)
        assertEquals(false, unlocked.layers.last().locked)
        assertEquals(false, unlocked.layers.first().visible)
        assertEquals(
            listOf("b-ink"),
            NoteLayers.visibleAnnotations(unlocked).map { it.id },
        )
    }

    // -------------------------------------------------------------- opacity

    @Test
    fun setOpacity_clampsAndRejectsNonFiniteValues() {
        val original = page(listOf(layer("a")))

        assertEquals(1f, NoteLayers.setOpacity(original, "a", 4f).layers.single().opacity, 1e-6f)
        assertEquals(0.05f, NoteLayers.setOpacity(original, "a", 0f).layers.single().opacity, 1e-6f)
        assertEquals(0.42f, NoteLayers.setOpacity(original, "a", 0.42f).layers.single().opacity, 1e-6f)
        assertSame(original, NoteLayers.setOpacity(original, "a", Float.NaN))
        assertSame(original, NoteLayers.setOpacity(original, "missing", 0.5f))
    }

    // --------------------------------------------------------------- rename

    @Test
    fun renameLayer_trimsTargetAndIgnoresBlankOrUnknownId() {
        val original = page(listOf(layer("a", name = "기본"), layer("b")))
        val renamed = NoteLayers.renameLayer(original, "a", "  필기  ")

        assertEquals("필기", renamed.layers.first().name)
        assertEquals("b", renamed.layers.last().name)
        assertEquals("기본", original.layers.first().name)
        assertSame(original, NoteLayers.renameLayer(original, "a", "  "))
        assertSame(original, NoteLayers.renameLayer(original, "missing", "필기"))
    }

    // --------------------------------------------------------------- active

    @Test
    fun activeLayer_resolvesIdAndFallsBackToDefault() {
        val page = page(listOf(layer("base"), layer("top")))

        assertEquals("top", NoteLayers.activeLayer(page, "top")?.id)
        assertEquals("base", NoteLayers.activeLayer(page, "missing")?.id)
        assertEquals(null, NoteLayers.activeLayer(page(layers = emptyList()), "top"))
    }
}
