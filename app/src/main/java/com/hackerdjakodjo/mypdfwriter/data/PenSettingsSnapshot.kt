package com.hackerdjakodjo.mypdfwriter.data

/**
 * One immutable snapshot shared by the PDF viewer, note editor and pen registry.
 *
 * The raw favorite value is retained so a missing first-run key and an explicit
 * empty registry keep their different meanings. All reducers are pure and use
 * the latest snapshot supplied by the caller, which prevents lost field updates.
 */
data class PenSettingsSnapshot(
    val ink: InkSettings = InkSettings.DEFAULT,
    val presets: List<PenPreset> = defaultPenPresets(),
    val favoriteIdsRaw: String? = null,
) {
    val favoriteIds: List<String>
        get() = FavoritePens.decode(favoriteIdsRaw) ?: FavoritePens.defaultIds(presets)

    val favorites: List<PenPreset>
        get() = FavoritePens.resolve(favoriteIds, presets)

    fun updateInk(transform: (InkSettings) -> InkSettings): PenSettingsSnapshot =
        copy(ink = transform(ink).normalized())

    fun applyPreset(preset: PenPreset): PenSettingsSnapshot =
        updateInk { InkPolicy.fromPreset(preset, it) }

    fun updatePresets(transform: (List<PenPreset>) -> List<PenPreset>): PenSettingsSnapshot {
        val next = ensureRequiredPresets(transform(presets))
        val raw = favoriteIdsRaw
        val nextRaw = if (raw == null) {
            null
        } else {
            FavoritePens.encode(FavoritePens.prune(FavoritePens.decode(raw) ?: emptyList(), next))
        }
        return copy(presets = next, favoriteIdsRaw = nextRaw)
    }

    fun updateFavorites(transform: (List<String>) -> List<String>): PenSettingsSnapshot =
        copy(
            favoriteIdsRaw = FavoritePens.encode(
                FavoritePens.prune(transform(favoriteIds), presets),
            ),
        )

    fun toggleFavorite(presetId: String): PenSettingsSnapshot =
        updateFavorites { FavoritePens.toggle(it, presetId, presets) }

    fun moveFavorite(from: Int, to: Int): PenSettingsSnapshot =
        updateFavorites { FavoritePens.move(it, from, to) }
}
