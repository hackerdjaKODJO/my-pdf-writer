package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class InkMotionHistoryTest {

    @Test
    fun `default capacity retains the newest 128 samples in order`() {
        val buffer = InkMotionHistoryBuffer()

        repeat(130) { index ->
            assertTrue(buffer.append(sample(index.toLong(), x = index.toFloat())))
        }

        val retained = buffer.snapshot()
        assertEquals(128, retained.size)
        assertEquals((2L..129L).toList(), retained.map { it.uptimeMs })
        assertEquals((2..129).map { it.toFloat() }, retained.map { it.x })
    }

    @Test
    fun `capacity must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { InkMotionHistoryBuffer(0) }
        assertThrows(IllegalArgumentException::class.java) { InkMotionHistoryBuffer(-1) }
    }

    @Test
    fun `capacity one always keeps the newest accepted sample`() {
        val buffer = InkMotionHistoryBuffer(maxSamples = 1)

        assertTrue(buffer.append(sample(0L)))
        assertTrue(buffer.append(sample(1L)))
        assertFalse(buffer.append(sample(1L)))
        assertEquals(listOf(sample(1L)), buffer.snapshot())
        assertNull(buffer.predict())

        assertTrue(buffer.append(sample(2L)))
        assertEquals(listOf(sample(2L)), buffer.snapshot())
    }

    @Test
    fun `append requires strictly increasing timestamps`() {
        val buffer = InkMotionHistoryBuffer()

        assertTrue(buffer.append(sample(0L)))
        assertFalse(buffer.append(sample(0L)))
        assertTrue(buffer.append(sample(5L)))
        assertFalse(buffer.append(sample(4L)))
        assertFalse(buffer.append(sample(5L)))
        assertTrue(buffer.append(sample(6L)))

        assertEquals(listOf(sample(0L), sample(5L), sample(6L)), buffer.snapshot())
    }

    @Test
    fun `finite negative coordinates and pressure are accepted unchanged`() {
        val buffer = InkMotionHistoryBuffer()
        val first = sample(0L, x = -12.5f, y = -0.25f, pressure = -3f)
        val second = sample(1L, x = -11f, y = 0f, pressure = 2f)

        assertTrue(buffer.append(first))
        assertTrue(buffer.append(second))

        assertEquals(listOf(first, second), buffer.snapshot())
    }

    @Test
    fun `append rejects non-finite coordinates and pressure without mutation`() {
        val buffer = InkMotionHistoryBuffer()
        val valid = sample(10L)
        assertTrue(buffer.append(valid))

        val nonFinite = listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY)
        val invalid = nonFinite.flatMap { value ->
            listOf(
                valid.copy(uptimeMs = 11L, x = value),
                valid.copy(uptimeMs = 11L, y = value),
                valid.copy(uptimeMs = 11L, pressure = value),
            )
        }
        invalid.forEach { candidate ->
            assertFalse("must reject $candidate", buffer.append(candidate))
            assertEquals(listOf(valid), buffer.snapshot())
        }

        assertTrue(buffer.append(sample(11L)))
        assertEquals(listOf(valid, sample(11L)), buffer.snapshot())
    }

    @Test
    fun `append rejects negative uptime without resetting the stroke`() {
        val buffer = InkMotionHistoryBuffer()

        assertTrue(buffer.append(sample(10L)))
        assertFalse(buffer.append(sample(-1L)))
        assertFalse(buffer.append(sample(Long.MIN_VALUE)))
        assertTrue(buffer.append(sample(11L)))

        assertEquals(listOf(sample(10L), sample(11L)), buffer.snapshot())
    }

    @Test
    fun `invalid append does not evict the oldest sample when full`() {
        val buffer = InkMotionHistoryBuffer(maxSamples = 1)
        val retained = sample(0L)
        assertTrue(buffer.append(retained))

        assertFalse(buffer.append(sample(1L, pressure = Float.NaN)))
        assertFalse(buffer.append(sample(0L)))
        assertEquals(listOf(retained), buffer.snapshot())

        assertTrue(buffer.append(sample(1L)))
        assertEquals(listOf(sample(1L)), buffer.snapshot())
    }

    @Test
    fun `appendAll preserves order and counts samples accepted during eviction`() {
        val buffer = InkMotionHistoryBuffer(maxSamples = 3)

        val accepted = buffer.appendAll((0L..4L).map { sample(it) })

        assertEquals(5, accepted)
        assertEquals((2L..4L).map { sample(it) }, buffer.snapshot())
    }

    @Test
    fun `appendAll skips invalid entries and continues from the newest accepted time`() {
        val buffer = InkMotionHistoryBuffer(maxSamples = 4)
        val candidates = listOf(
            sample(0L),
            sample(1L, x = Float.NaN),
            sample(0L),
            sample(-1L),
            sample(2L),
            sample(1L),
            sample(3L),
        )

        assertEquals(3, buffer.appendAll(candidates))
        assertEquals(listOf(sample(0L), sample(2L), sample(3L)), buffer.snapshot())
        assertEquals(0, buffer.appendAll(emptyList()))
    }

    @Test
    fun `appendAll accepts a single-pass iterable`() {
        val buffer = InkMotionHistoryBuffer()
        val candidates = Iterable { listOf(sample(0L), sample(1L), sample(2L)).iterator() }

        assertEquals(3, buffer.appendAll(candidates))
        assertEquals(listOf(sample(0L), sample(1L), sample(2L)), buffer.snapshot())
    }

    @Test
    fun `samplesBefore is strict and never consumes`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(10L), sample(20L)))

        assertTrue(buffer.samplesBefore(0L).isEmpty())
        assertEquals(listOf(sample(0L)), buffer.samplesBefore(10L))
        val expected = listOf(sample(0L), sample(10L))
        assertEquals(expected, buffer.samplesBefore(11L))
        assertEquals(listOf(sample(0L), sample(10L), sample(20L)), buffer.samplesBefore(Long.MAX_VALUE))
        assertEquals(expected, buffer.samplesBefore(11L))
        assertEquals(listOf(sample(0L), sample(10L), sample(20L)), buffer.snapshot())
    }

    @Test
    fun `samplesBefore returns an independent ordered snapshot`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(10L), sample(20L)))
        val snapshot = buffer.samplesBefore(30L)

        buffer.append(sample(30L))
        buffer.consumeThrough(10L)

        assertEquals(listOf(sample(10L), sample(20L)), snapshot)
        assertEquals(listOf(sample(20L), sample(30L)), buffer.snapshot())
    }

    @Test
    fun `consumeThrough removes only samples at or before the cutoff`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(10L), sample(20L)))

        buffer.consumeThrough(-1L)
        assertEquals(listOf(sample(0L), sample(10L), sample(20L)), buffer.snapshot())
        buffer.consumeThrough(9L)
        assertEquals(listOf(sample(10L), sample(20L)), buffer.snapshot())
        buffer.consumeThrough(10L)
        assertEquals(listOf(sample(20L)), buffer.snapshot())
        buffer.consumeThrough(10L)
        assertEquals(listOf(sample(20L)), buffer.snapshot())
        buffer.consumeThrough(20L)
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun `consumeThrough can clear the buffer at maximum uptime`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(Long.MAX_VALUE)))

        buffer.consumeThrough(Long.MAX_VALUE)

        assertTrue(buffer.snapshot().isEmpty())
        assertNull(buffer.predict())
    }

    @Test
    fun `consumeThrough does not rewind per-stroke timestamp ordering`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(10L), sample(20L)))

        buffer.consumeThrough(20L)

        assertTrue(buffer.snapshot().isEmpty())
        assertFalse(buffer.append(sample(15L)))
        assertFalse(buffer.append(sample(20L)))
        assertTrue(buffer.append(sample(21L)))
        assertEquals(listOf(sample(21L)), buffer.snapshot())

        buffer.clear()
        assertTrue(buffer.append(sample(0L)))
        assertEquals(listOf(sample(0L)), buffer.snapshot())
    }

    @Test
    fun `clear resets retained samples ordering and prediction state`() {
        val buffer = InkMotionHistoryBuffer(maxSamples = 2)
        buffer.appendAll(listOf(sample(0L), sample(10L, x = 10f)))

        buffer.clear()
        buffer.clear()

        assertTrue(buffer.snapshot().isEmpty())
        assertNull(buffer.predict())
        assertTrue(buffer.append(sample(0L)))
        assertTrue(buffer.append(sample(1L, x = 1f)))
        assertEquals(listOf(sample(0L), sample(1L, x = 1f)), buffer.snapshot())
        assertEquals(sample(uptimeMs = 9L, x = 9f), buffer.predict())
    }

    @Test
    fun `predict returns null with fewer than two samples`() {
        val buffer = InkMotionHistoryBuffer()

        assertNull(buffer.predict())
        assertTrue(buffer.append(sample(0L)))
        assertNull(buffer.predict())
        assertEquals(listOf(sample(0L)), buffer.snapshot())
    }

    @Test
    fun `predict returns null for stationary input`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(
            listOf(
                sample(0L, x = 5f, y = -2f, pressure = 0.2f),
                sample(10L, x = 5f, y = -2f, pressure = 0.9f),
            ),
        )

        assertNull(buffer.predict())
        assertEquals(2, buffer.snapshot().size)
    }

    @Test
    fun `predict extrapolates from only the last two samples`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(
            listOf(
                sample(0L, x = 1_000f, y = -1_000f),
                sample(10L, x = 0f, y = 0f),
                sample(20L, x = 10f, y = 20f, pressure = 0.7f),
            )
        )

        assertEquals(
            InkMotionSample(x = 12f, y = 24f, pressure = 0.7f, uptimeMs = 22L),
            buffer.predict(horizonMs = 2f),
        )
    }

    @Test
    fun `default prediction horizon is eight milliseconds`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(100L, x = 0f, y = 0f), sample(110L, x = 3f, y = 4f, pressure = 0.6f)))

        val predicted = requireNotNull(buffer.predict())

        assertEquals(5.4f, predicted.x, 1e-6f)
        assertEquals(7.2f, predicted.y, 1e-6f)
        assertEquals(0.6f, predicted.pressure, 0f)
        assertEquals(118L, predicted.uptimeMs)
    }

    @Test
    fun `predict clamps the horizon to zero through twelve milliseconds`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(100L, x = 0f), sample(110L, x = 10f)))
        val newest = sample(110L, x = 10f)

        assertEquals(newest, buffer.predict(horizonMs = -10f))
        assertEquals(newest, buffer.predict(horizonMs = 0f))
        assertEquals(sample(122L, x = 22f), buffer.predict(horizonMs = 12f))
        assertEquals(sample(122L, x = 22f), buffer.predict(horizonMs = 100f))
    }

    @Test
    fun `predict caps Euclidean travel at maxDistancePx`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(10L, x = 30f, y = 40f)))

        val predicted = requireNotNull(buffer.predict(horizonMs = 12f, maxDistancePx = 12f))

        assertEquals(37.2f, predicted.x, 1e-6f)
        assertEquals(49.6f, predicted.y, 1e-6f)
        assertEquals(12f, hypot(predicted.x - 30f, predicted.y - 40f), 1e-5f)
        assertEquals(22L, predicted.uptimeMs)
    }

    @Test
    fun `predict accepts travel exactly at the distance limit`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(10L, x = 10f)))

        assertEquals(sample(22L, x = 22f), buffer.predict(horizonMs = 12f, maxDistancePx = 12f))
        assertEquals(
            sample(22L, x = 21.999f),
            buffer.predict(horizonMs = 12f, maxDistancePx = 11.999f),
        )
    }

    @Test
    fun `predict rejects non-finite limits and non-positive distance limits`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(listOf(sample(0L), sample(10L, x = 10f)))

        listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach { horizon ->
            assertNull(buffer.predict(horizonMs = horizon))
        }
        listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1f, 0f).forEach {
            maxDistance ->
            assertNull(buffer.predict(maxDistancePx = maxDistance))
        }
        assertEquals(2, buffer.snapshot().size)
    }

    @Test
    fun `predict rejects velocity that overflows finite float range`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(
            listOf(
                sample(0L, x = -Float.MAX_VALUE),
                sample(1L, x = Float.MAX_VALUE),
            )
        )

        assertNull(buffer.predict())
        val yBuffer = InkMotionHistoryBuffer()
        yBuffer.appendAll(
            listOf(
                sample(0L, y = Float.MAX_VALUE),
                sample(1L, y = -Float.MAX_VALUE),
            )
        )
        assertNull(yBuffer.predict())
    }

    @Test
    fun `predict returns null rather than an infinite coordinate`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(
            listOf(
                sample(0L, x = 0f),
                sample(1L, x = 3e38f),
            )
        )

        assertNull(buffer.predict(horizonMs = 12f, maxDistancePx = Float.MAX_VALUE))
        assertEquals(2, buffer.snapshot().size)
    }

    @Test
    fun `predict never persists or consumes samples`() {
        val buffer = InkMotionHistoryBuffer()
        val samples = listOf(sample(0L), sample(10L, x = 10f))
        buffer.appendAll(samples)

        val first = buffer.predict()
        val second = buffer.predict()

        assertEquals(first, second)
        assertEquals(samples, buffer.snapshot())
        assertEquals(samples, buffer.samplesBefore(Long.MAX_VALUE))
    }

    @Test
    fun `predict handles maximum uptime without overflowing`() {
        val buffer = InkMotionHistoryBuffer()
        buffer.appendAll(
            listOf(
                sample(Long.MAX_VALUE - 1L, x = 0f),
                sample(Long.MAX_VALUE, x = 1f),
            )
        )

        assertNull(buffer.predict(horizonMs = 1f))
        assertEquals(sample(Long.MAX_VALUE, x = 1f), buffer.predict(horizonMs = 0f))
        assertEquals(2, buffer.snapshot().size)
    }

    private fun sample(
        uptimeMs: Long,
        x: Float = uptimeMs.toFloat(),
        y: Float = 0f,
        pressure: Float = 1f,
    ): InkMotionSample = InkMotionSample(x = x, y = y, pressure = pressure, uptimeMs = uptimeMs)
}
