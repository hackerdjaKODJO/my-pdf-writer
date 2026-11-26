package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import android.content.SharedPreferences

/** Raw values kept in the existing pen_box.xml backup contract. */
internal data class PenSettingsRaw(
    val ink: String?,
    val presets: String?,
    val favorites: String?,
)

/** Small persistence boundary so repository synchronization is JVM-testable. */
internal interface PenSettingsPersistence {
    fun read(): PenSettingsRaw
    fun writeInk(raw: String): Boolean
    fun writeRegistry(presets: List<PenPreset>, favoriteIdsRaw: String?): Boolean
    fun writeFavorites(raw: String): Boolean
    fun observe(listener: () -> Unit): () -> Unit
}

internal class SharedPreferencesPenSettingsPersistence(
    context: Context,
) : PenSettingsPersistence {
    private val prefs = context.getSharedPreferences(PenBoxStore.PREFS, Context.MODE_PRIVATE)

    override fun read(): PenSettingsRaw = PenSettingsRaw(
        ink = prefs.getString(InkSettings.KEY, null),
        presets = prefs.getString(PenBoxStore.KEY, null),
        favorites = prefs.getString(FavoritePens.KEY, null),
    )

    override fun writeInk(raw: String): Boolean =
        prefs.edit().putString(InkSettings.KEY, raw).commit()

    override fun writeRegistry(presets: List<PenPreset>, favoriteIdsRaw: String?): Boolean {
        val editor = prefs.edit().putString(PenBoxStore.KEY, encodePenPresets(presets))
        if (favoriteIdsRaw == null) {
            editor.remove(FavoritePens.KEY)
        } else {
            editor.putString(FavoritePens.KEY, favoriteIdsRaw)
        }
        return editor.commit()
    }

    override fun writeFavorites(raw: String): Boolean =
        prefs.edit().putString(FavoritePens.KEY, raw).commit()

    override fun observe(listener: () -> Unit): () -> Unit {
        val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == InkSettings.KEY ||
                key == PenBoxStore.KEY || key == FavoritePens.KEY
            ) {
                listener()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(prefsListener) }
    }
}
