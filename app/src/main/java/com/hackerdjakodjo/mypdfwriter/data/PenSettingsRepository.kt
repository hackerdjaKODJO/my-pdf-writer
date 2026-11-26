package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PenSettingsWriteResult {
    data class Applied(val snapshot: PenSettingsSnapshot) : PenSettingsWriteResult
    data object Unchanged : PenSettingsWriteResult
    data object Failed : PenSettingsWriteResult
    data object Restoring : PenSettingsWriteResult
}

/**
 * Process-wide owner for active ink, saved pens and favorites.
 *
 * Every mutation is a read-modify-write against the latest snapshot under one
 * lock. The preference listener keeps already-open PDF/note surfaces in sync and
 * also observes backup restore. The existing pen_box.xml keys remain unchanged.
 */
class PenSettingsRepository internal constructor(
    private val persistence: PenSettingsPersistence,
) {
    private val lock = Any()
    private val restoreLock = ReentrantReadWriteLock()
    @Volatile
    private var restoring = false
    private val _state = MutableStateFlow(load())
    val state: StateFlow<PenSettingsSnapshot> = _state.asStateFlow()
    val snapshot: PenSettingsSnapshot get() = _state.value

    private val unregister = persistence.observe(::refresh)

    init {
        // Close the read/register race: a restore between construction and
        // listener registration must still be visible immediately.
        refresh()
    }

    fun updateInk(transform: (InkSettings) -> InkSettings): PenSettingsWriteResult =
        updateState({ it.updateInk(transform) }) { next ->
            persistence.writeInk(next.ink.toJson().toString())
        }

    fun applyPreset(preset: PenPreset): PenSettingsWriteResult =
        updateState({ it.applyPreset(preset) }) { next ->
            persistence.writeInk(next.ink.toJson().toString())
        }

    fun updatePresets(transform: (List<PenPreset>) -> List<PenPreset>): PenSettingsWriteResult =
        updateState({ it.updatePresets(transform) }) { next ->
            persistence.writeRegistry(next.presets, next.favoriteIdsRaw)
        }

    fun updateFavorites(transform: (List<String>) -> List<String>): PenSettingsWriteResult =
        updateState({ it.updateFavorites(transform) }) { next ->
            persistence.writeFavorites(next.favoriteIdsRaw ?: FavoritePens.encode(emptyList()))
        }

    fun toggleFavorite(presetId: String): PenSettingsWriteResult =
        updateState({ it.toggleFavorite(presetId) }) { next ->
            persistence.writeFavorites(next.favoriteIdsRaw ?: FavoritePens.encode(emptyList()))
        }

    fun moveFavorite(from: Int, to: Int): PenSettingsWriteResult =
        updateState({ it.moveFavorite(from, to) }) { next ->
            persistence.writeFavorites(next.favoriteIdsRaw ?: FavoritePens.encode(emptyList()))
        }

    /** Called for own writes, external writes, preference clear and restore. */
    fun refresh() {
        synchronized(lock) {
            _state.value = load()
        }
    }

    /** Reject UI writes while restore replaces preferences, then always reconcile. */
    fun <T> withRestoreBarrier(block: () -> T): T {
        restoreLock.writeLock().lock()
        try {
            return block()
        } finally {
            runCatching { refresh() }
            restoreLock.writeLock().unlock()
        }
    }

    suspend fun <T> withRestoreBarrierSuspend(block: suspend () -> T): T {
        restoring = true
        try {
            return block()
        } finally {
            runCatching { refresh() }
            restoring = false
        }
    }

    fun close() = unregister()

    private fun load(): PenSettingsSnapshot {
        val raw = persistence.read()
        return PenSettingsSnapshot(
            ink = InkSettings.fromRaw(raw.ink).normalized(),
            presets = decodePenPresets(raw.presets),
            favoriteIdsRaw = raw.favorites,
        )
    }

    private inline fun updateState(
        transform: (PenSettingsSnapshot) -> PenSettingsSnapshot,
        write: (PenSettingsSnapshot) -> Boolean,
    ): PenSettingsWriteResult {
        if (restoring || !restoreLock.readLock().tryLock()) {
            return PenSettingsWriteResult.Restoring
        }
        return try {
            synchronized(lock) {
                val current = _state.value
                val next = transform(current)
                if (next == current) return PenSettingsWriteResult.Unchanged
                if (!write(next)) return PenSettingsWriteResult.Failed
                _state.value = next
                PenSettingsWriteResult.Applied(next)
            }
        } finally {
            restoreLock.readLock().unlock()
        }
    }

    companion object {
        @Volatile
        private var instance: PenSettingsRepository? = null

        /** Exactly one repository/listener per app process. */
        fun get(context: Context): PenSettingsRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: PenSettingsRepository(
                    SharedPreferencesPenSettingsPersistence(app),
                ).also { instance = it }
            }
        }
    }
}
