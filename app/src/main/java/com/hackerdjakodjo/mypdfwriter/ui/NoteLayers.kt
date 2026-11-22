package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.NoteLayer
import com.hackerdjakodjo.mypdfwriter.data.NotePage

/**
 * Pure layer operations for one notebook page.
 *
 * Layer order is bottom-to-top: the first layer in [NotePage.layers] is the default
 * layer, and the last layer is painted above the others. The layers panel shows the
 * same stack top-first, so a positive [moveLayer] direction moves a layer visually
 * up (toward the end of the list).
 *
 * Layers only group annotations; they never own page content. Deleting a layer
 * therefore reassigns its annotations to a neighbouring layer instead of deleting
 * ink. Annotations with a null `layerId` belong to the first layer.
 */
object NoteLayers {
    /**
     * Locked (or hidden) layer ink may be drawn and exported, but selection
     * edits must not move, recolour, duplicate or delete it. Unknown layer ids
     * fall back to the default visible/unlocked layer, matching [visibleAnnotations].
     */
    fun editableSelection(page: NotePage, ids: Set<String>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        val layers = page.layers.associateBy { it.id }
        return ids.filter { id ->
            val annotation = page.annotations.firstOrNull { it.id == id } ?: return@filter false
            val layer = annotation.layerId?.let { layers[it] }
            layer?.locked != true && layer?.visible != false
        }.toSet()
    }

    const val DEFAULT_LAYER_NAME = "새 레이어"

    private const val MIN_OPACITY = 0.05f
    private const val MAX_OPACITY = 1f

    /** Adds a fresh layer above the current top layer. */
    fun addLayer(page: NotePage, name: String = DEFAULT_LAYER_NAME): NotePage {
        val safeName = name.trim().ifBlank { DEFAULT_LAYER_NAME }
        return page.copy(layers = page.layers + NoteLayer(name = safeName))
    }

    /** Renames one layer. Blank names and unknown ids leave the page unchanged. */
    fun renameLayer(page: NotePage, id: String, name: String): NotePage {
        val safeName = name.trim()
        if (safeName.isEmpty()) return page
        return updateLayer(page, id) { it.copy(name = safeName) }
    }

    /**
     * Deletes a layer and moves its annotations to the nearest lower layer, or to
     * the next layer when the bottom layer is deleted. Deleting the only layer is
     * a no-op because a page must always keep one layer.
     */
    fun deleteLayer(page: NotePage, id: String): NotePage {
        if (page.layers.size <= 1) return page
        val index = page.layers.indexOfFirst { it.id == id }
        if (index < 0) return page

        val target = if (index > 0) page.layers[index - 1] else page.layers[index + 1]
        val defaultId = page.layers.first().id
        return page.copy(
            layers = page.layers.filterIndexed { layerIndex, _ -> layerIndex != index },
            annotations = page.annotations.map { annotation ->
                val effectiveId = annotation.layerId ?: defaultId
                if (effectiveId == id) annotation.copy(layerId = target.id) else annotation
            },
        )
    }

    /** Hides or shows a layer. Locked and hidden layers keep their annotations. */
    fun setVisible(page: NotePage, id: String, visible: Boolean): NotePage =
        updateLayer(page, id) { it.copy(visible = visible) }

    /** Locks or unlocks a layer. Locking never hides existing ink. */
    fun setLocked(page: NotePage, id: String, locked: Boolean): NotePage =
        updateLayer(page, id) { it.copy(locked = locked) }

    /** Sets layer opacity, clamped to the range persisted by [NoteLayer.fromJson]. */
    fun setOpacity(page: NotePage, id: String, opacity: Float): NotePage {
        if (!opacity.isFinite()) return page
        val safeOpacity = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
        return updateLayer(page, id) { it.copy(opacity = safeOpacity) }
    }

    /**
     * Moves a layer one step. A positive [direction] moves it up in the visual
     * stack (toward the end of the list); a negative value moves it down. The
     * move is ignored at the ends of the stack and for direction zero.
     */
    fun moveLayer(page: NotePage, id: String, direction: Int): NotePage {
        if (direction == 0) return page
        val index = page.layers.indexOfFirst { it.id == id }
        if (index < 0) return page

        val step = if (direction > 0) 1 else -1
        val target = (index + step).coerceIn(0, page.layers.lastIndex)
        if (target == index) return page

        val reordered = page.layers.toMutableList()
        val layer = reordered.removeAt(index)
        reordered.add(target, layer)
        return page.copy(layers = reordered)
    }

    /**
     * Returns the annotations that should be painted for this page. Hidden layers
     * are skipped; locked layers still render. Null layer ids map to the first
     * layer, and annotations that point at an unknown layer stay visible so stale
     * data never silently loses ink.
     */
    fun visibleAnnotations(page: NotePage): List<Annotation> {
        val layers = page.layers
        if (layers.isEmpty()) return page.annotations
        val defaultId = layers.first().id
        val layerById = layers.associateBy { it.id }
        return page.annotations.filter { annotation ->
            val effectiveId = annotation.layerId ?: defaultId
            layerById[effectiveId]?.visible ?: true
        }
    }

    /**
     * Resolves the active layer. A stale id falls back to the first/default layer
     * so the controlled panel always has a selection on a non-empty page.
     */
    fun activeLayer(page: NotePage, activeId: String): NoteLayer? =
        page.layers.firstOrNull { it.id == activeId } ?: page.layers.firstOrNull()

    private inline fun updateLayer(
        page: NotePage,
        id: String,
        transform: (NoteLayer) -> NoteLayer,
    ): NotePage {
        if (page.layers.none { it.id == id }) return page
        return page.copy(
            layers = page.layers.map { if (it.id == id) transform(it) else it },
        )
    }
}
