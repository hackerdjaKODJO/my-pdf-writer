package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PenSettingsRepositoryTest {
    private class FakePersistence(
        initial: PenSettingsRaw = PenSettingsRaw(null, null, null),
    ) : PenSettingsPersistence {
        var raw = initial
        var writeCount = 0
        var succeed = true
        private val listeners = mutableListOf<() -> Unit>()

        override fun read(): PenSettingsRaw = raw

        override fun writeInk(raw: String): Boolean = write {
            this.raw = this.raw.copy(ink = raw)
        }

        override fun writeRegistry(presets: List<PenPreset>, favoriteIdsRaw: String?): Boolean =
            write {
                this.raw = raw.copy(
                    presets = encodePenPresets(presets),
                    favorites = favoriteIdsRaw,
                )
            }

        override fun writeFavorites(raw: String): Boolean = write {
            this.raw = this.raw.copy(favorites = raw)
        }

        override fun observe(listener: () -> Unit): () -> Unit {
            listeners += listener
            return { listeners -= listener }
        }

        fun external(next: PenSettingsRaw) {
            raw = next
            listeners.toList().forEach { it() }
        }

        private fun write(block: () -> Unit): Boolean {
            if (!succeed) return false
            writeCount++
            block()
            listeners.toList().forEach { it() }
            return true
        }
    }

    @Test
    fun `field updates preserve unrelated values and write once`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        repository.updateInk { it.copy(color = 0xFF123456.toInt()) }
        repository.updateInk { it.copy(width = 0.009f) }

        assertEquals(2, persistence.writeCount)
        assertEquals(0xFF123456.toInt(), repository.snapshot.ink.color)
        assertEquals(0.009f, repository.snapshot.ink.width, 0f)
    }

    @Test
    fun `preset application is one atomic write and restores pressure`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        val preset = PenPreset(
            name = "무압 연필",
            color = 0xFF444444.toInt(),
            width = 0.006f,
            opacity = 0.7f,
            penStyle = PenStyle.PENCIL,
            pressureCurve = PressureCurve.FIRM,
            pressureInk = false,
        )

        val result = repository.applyPreset(preset)

        assertTrue(result is PenSettingsWriteResult.Applied)
        assertEquals(1, persistence.writeCount)
        assertEquals(PenStyle.PENCIL, repository.snapshot.ink.penStyle)
        assertEquals(PressureCurve.FIRM, repository.snapshot.ink.pressureCurve)
        assertFalse(repository.snapshot.ink.pressureInk)
    }

    @Test
    fun `external preference writes and clear refresh open consumers`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        repository.updateInk { it.copy(color = 0xFF111111.toInt()) }

        persistence.external(
            PenSettingsRaw(
                ink = InkSettings(penStyle = PenStyle.BRUSH, width = 0.011f).toJson().toString(),
                presets = null,
                favorites = null,
            ),
        )
        assertEquals(PenStyle.BRUSH, repository.snapshot.ink.penStyle)
        assertEquals(0.011f, repository.snapshot.ink.width, 0f)

        persistence.external(PenSettingsRaw(null, null, null))
        assertEquals(InkSettings.DEFAULT, repository.snapshot.ink)
    }

    @Test
    fun `failed persistence does not publish a new value`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        persistence.succeed = false

        val result = repository.updateInk { it.copy(color = 0xFF222222.toInt()) }

        assertEquals(PenSettingsWriteResult.Failed, result)
        assertEquals(InkSettings.DEFAULT, repository.snapshot.ink)
    }

    @Test
    fun `concurrent field updates do not lose either change`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        val start = java.util.concurrent.CountDownLatch(1)
        val first = Thread { start.await(); repository.updateInk { it.copy(color = 0xFF333333.toInt()) } }
        val second = Thread { start.await(); repository.updateInk { it.copy(width = 0.008f) } }
        first.start()
        second.start()
        start.countDown()
        first.join(5_000)
        second.join(5_000)

        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(0xFF333333.toInt(), repository.snapshot.ink.color)
        assertEquals(0.008f, repository.snapshot.ink.width, 0f)
    }

    @Test
    fun `restore barrier rejects writes and refreshes after replacement`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        val result = java.util.concurrent.atomic.AtomicReference<PenSettingsWriteResult>()
        repository.withRestoreBarrier {
            val worker = Thread {
                result.set(repository.updateInk { it.copy(color = 0xFF555555.toInt()) })
            }
            worker.start()
            worker.join(5_000)
            assertFalse(worker.isAlive)
            persistence.external(
                PenSettingsRaw(
                    ink = InkSettings(penStyle = PenStyle.FOUNTAIN).toJson().toString(),
                    presets = null,
                    favorites = FavoritePens.encode(emptyList()),
                ),
            )
        }

        assertEquals(PenSettingsWriteResult.Restoring, result.get())
        assertEquals(PenStyle.FOUNTAIN, repository.snapshot.ink.penStyle)
        assertTrue(repository.snapshot.favorites.isEmpty())
    }

    @Test
    fun `missing and explicitly empty favorites keep different meanings`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        assertNull(repository.snapshot.favoriteIdsRaw)
        assertEquals(4, repository.snapshot.favorites.size)

        repository.updateFavorites { emptyList() }
        assertEquals("[]", repository.snapshot.favoriteIdsRaw)
        assertTrue(repository.snapshot.favorites.isEmpty())
    }

    @Test
    fun `preset deletion prunes favorites in the same registry write`() {
        val persistence = FakePersistence()
        val repository = PenSettingsRepository(persistence)
        val target = repository.snapshot.presets.last()
        repository.updateFavorites { listOf(target.id) }

        repository.updatePresets { presets -> presets.filterNot { it.id == target.id } }

        assertTrue(repository.snapshot.favorites.isEmpty())
        assertEquals(2, persistence.writeCount)
    }
}
