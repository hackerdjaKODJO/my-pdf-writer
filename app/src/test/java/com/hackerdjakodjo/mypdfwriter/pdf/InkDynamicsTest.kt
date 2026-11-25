package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong

/**
 * Speed-aware pen dynamics: neutral ink stays an exact identity, varied ink
 * responds to pace, and every helper is finite and deterministic.
 */
class InkDynamicsTest {

    private fun line(count: Int, pressure: Float = 1f): List<NormalizedPoint> =
        List(count) { NormalizedPoint(0.1f + 0.6f * it / maxOf(1, count - 1), 0.5f, pressure) }

    // ---- velocityWidthFactor -------------------------------------------------

    @Test
    fun `velocity width is an exact identity for neutral ink`() {
        listOf(0f, 1f, 8f, Float.NaN).forEach { speed ->
            assertEquals(
                1f,
                InkDynamics.velocityWidthFactor(speed, 1f, InkDynamics.BRUSH_SPEED_INFLUENCE, 0f),
                0f,
            )
        }
    }

    @Test
    fun `mean pace is neutral, fast thins and slow swells`() {
        val variation = 0.8f
        val influence = InkDynamics.BRUSH_SPEED_INFLUENCE
        assertEquals(1f, InkDynamics.velocityWidthFactor(2f, 2f, influence, variation), 0f)
        val fast = InkDynamics.velocityWidthFactor(8f, 2f, influence, variation)
        val slow = InkDynamics.velocityWidthFactor(0f, 2f, influence, variation)
        assertTrue("fast $fast < 1", fast < 1f)
        assertTrue("slow $slow > 1", slow > 1f)
        assertTrue("fast bounded", fast >= 1f - influence)
        assertTrue("slow bounded", slow <= 1f + influence)
    }

    @Test
    fun `velocity width is finite and safe for degenerate inputs`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f).forEach { speed ->
            listOf(0f, -2f, Float.NaN).forEach { reference ->
                val factor = InkDynamics.velocityWidthFactor(speed, reference, 0.3f, 0.8f)
                assertEquals(1f, factor, 0f)
            }
        }
        assertEquals(1f, InkDynamics.velocityWidthFactor(1f, 1f, Float.NaN, 0.8f), 0f)
        assertEquals(1f, InkDynamics.velocityWidthFactor(1f, 1f, 0f, 0.8f), 0f)
        val huge = InkDynamics.velocityWidthFactor(1f, 1f, 9f, 0.8f)
        assertTrue("influence capped", huge in 0.5f..1.5f)
    }

    // ---- strokeSpeeds --------------------------------------------------------

    @Test
    fun `uniform line has constant interior speed and endpoints keep a speed`() {
        val speeds = InkDynamics.strokeSpeeds(line(6), 1000f, 1000f)
        assertEquals(6, speeds.size)
        // Central difference: interior samples span two 0.12 steps, clamped
        // endpoints span one.
        (1..4).forEach { assertEquals(120f, speeds[it], 0.001f) }
        assertEquals(60f, speeds[0], 0.001f)
        assertEquals(60f, speeds[5], 0.001f)
        assertEquals(100f, InkDynamics.speedReference(speeds), 0.001f)
    }

    @Test
    fun `speeds are finite for empty, single and corrupt strokes`() {
        assertTrue(InkDynamics.strokeSpeeds(emptyList(), 100f, 100f).isEmpty())
        assertEquals(0f, InkDynamics.speedReference(FloatArray(0)), 0f)
        val single = InkDynamics.strokeSpeeds(listOf(NormalizedPoint(0.5f, 0.5f)), 100f, 100f)
        assertEquals(1, single.size)
        assertEquals(0f, single[0], 0f)
        val corrupt = InkDynamics.strokeSpeeds(
            listOf(NormalizedPoint(Float.NaN, 0.5f), NormalizedPoint(0.6f, Float.NaN)),
            Float.NaN,
            0f,
        )
        corrupt.forEach { assertTrue("finite $it", it.isFinite()) }
    }

    @Test
    fun `timed speed is physical page-point velocity with central and one-sided differences`() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.5f),
            NormalizedPoint(0.2f, 0.5f),
            NormalizedPoint(0.4f, 0.5f),
        )
        val speeds = InkDynamics.strokeSpeeds(points, 1000f, 500f, listOf(0L, 1000L, 3000L))
        assertEquals(100f, speeds[0], 0.001f)
        assertEquals(150f, speeds[1], 0.001f)
        assertEquals(100f, speeds[2], 0.001f)

        val diagonal = InkDynamics.strokeSpeeds(
            listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.4f, 0.5f)),
            1000f,
            500f,
            listOf(10L, 1010L),
        )
        assertEquals(500f, diagonal[0], 0.001f)
    }

    @Test
    fun `same physical velocity at 60 120 and 240 Hz yields equivalent bounded factors`() {
        fun stroke(rate: Int): Pair<List<NormalizedPoint>, List<Long>> {
            val count = rate + 1
            val points = List(count) {
                NormalizedPoint(0.1f + 0.6f * it / (count - 1), 0.5f, 0.65f)
            }
            val times = List(count) { (it * 1000.0 / rate).roundToLong() }
            return points to times
        }

        val captures = listOf(60, 120, 240).map { rate ->
            val (points, times) = stroke(rate)
            Triple(rate, points, InkDynamics.strokeSpeeds(points, 1000f, 800f, times))
        }
        captures.forEach { (_, _, speeds) ->
            assertEquals("physical pace", 600f, InkDynamics.speedReference(speeds), 3f)
            speeds.forEach { speed ->
                val factor = InkDynamics.velocityWidthFactor(
                    speed,
                    600f,
                    InkDynamics.BRUSH_SPEED_INFLUENCE,
                    0.35f,
                )
                assertTrue("bounded factor $factor", factor in 0.65f..1.35f)
                assertTrue("rate invariant factor $factor", factor in 0.98f..1.02f)
            }
        }
        val base = captures.first()
        captures.drop(1).forEach { capture ->
            listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f).forEach { position ->
                val baseIndex = (position * base.third.lastIndex).roundToLong().toInt()
                val index = (position * capture.third.lastIndex).roundToLong().toInt()
                val baseFactor = InkDynamics.velocityWidthFactor(
                    base.third[baseIndex], 600f, InkDynamics.BRUSH_SPEED_INFLUENCE, 0.35f,
                )
                val factor = InkDynamics.velocityWidthFactor(
                    capture.third[index], 600f, InkDynamics.BRUSH_SPEED_INFLUENCE, 0.35f,
                )
                assertEquals("$position at ${capture.first} Hz", baseFactor, factor, 0.01f)
            }
        }
    }

    @Test
    fun `timestamp dt is clamped and invalid timing uses the exact sample fallback`() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.5f),
            NormalizedPoint(0.2f, 0.5f),
            NormalizedPoint(0.3f, 0.5f),
        )
        val clamped = InkDynamics.strokeSpeeds(points, 1000f, 1000f, listOf(0L, 1L, 2L))
        assertEquals(100000f, clamped[0], 1f)
        assertEquals(200000f, clamped[1], 1f)
        assertTrue("1 ms floor prevents a spike", clamped.all { it.isFinite() && it > 0f })

        val fallback = InkDynamics.strokeSpeeds(points, 1000f, 1000f)
        listOf(
            emptyList<Long>(),
            listOf(0L, 10L),
            listOf(0L, 20L, 10L),
            listOf(-10L, 0L, 10L),
        ).forEach { invalid ->
            assertTrue(
                "invalid timing $invalid",
                fallback.contentEquals(InkDynamics.strokeSpeeds(points, 1000f, 1000f, invalid)),
            )
        }
    }

    @Test
    fun `segment pace is timed but keeps the legacy distance fallback`() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.5f),
            NormalizedPoint(0.3f, 0.5f),
            NormalizedPoint(0.4f, 0.5f),
        )
        assertArrayEquals(
            floatArrayOf(0f, 200f, 100f),
            InkDynamics.strokeSegmentSpeeds(points, 1000f, 1000f),
            0.001f,
        )
        assertArrayEquals(
            floatArrayOf(0f, 100f, 200f),
            InkDynamics.strokeSegmentSpeeds(points, 1000f, 1000f, listOf(0L, 2000L, 2500L)),
            0.001f,
        )
    }

    // ---- dwellPools ----------------------------------------------------------

    private fun dwellStroke(): List<NormalizedPoint> = listOf(
        NormalizedPoint(0.1f, 0.5f, 1f),
        NormalizedPoint(0.2f, 0.5f, 0.8f),
        NormalizedPoint(0.3f, 0.5f, 0.6f),
        NormalizedPoint(0.3f, 0.5f, 0.5f),
        NormalizedPoint(0.3f, 0.5f, 0.4f),
        NormalizedPoint(0.3f, 0.5f, 0.3f),
        NormalizedPoint(0.3f, 0.5f, 0.2f),
        NormalizedPoint(0.4f, 0.5f, 1f),
    )

    @Test
    fun `neutral ink never pools`() {
        val stationary = List(8) { NormalizedPoint(0.3f, 0.5f, 1f) }
        assertTrue(
            InkDynamics.dwellPools(stationary, 1000f, 1000f, 0f).isEmpty(),
        )
    }

    @Test
    fun `a dwelling nib pools ink at the end of the stationary run`() {
        val points = dwellStroke()
        val variation = PenStrokeGeometry.pressureVariation(points)
        assertTrue(variation > 0f)
        val pools = InkDynamics.dwellPools(points, 1000f, 1000f, variation)
        assertEquals(1, pools.size)
        assertEquals(6, pools[0].index)
        assertTrue("strength ${pools[0].strength}", pools[0].strength in 0.15f..1f)
        assertEquals(pools, InkDynamics.dwellPools(points, 1000f, 1000f, variation))
    }

    @Test
    fun `a moving stroke never pools and pools are bounded`() {
        assertTrue(
            InkDynamics.dwellPools(line(24, 0.5f), 1000f, 1000f, 0.8f).isEmpty(),
        )
        // Alternating move/dwell segments: many runs, capped pool count.
        val points = ArrayList<NormalizedPoint>()
        var x = 0.05f
        repeat(20) {
            points.add(NormalizedPoint(x, 0.5f, 1f))
            repeat(4) { points.add(NormalizedPoint(x, 0.5f, 0.3f)) }
            x += 0.04f
        }
        val pools = InkDynamics.dwellPools(points, 1000f, 1000f, 0.7f)
        assertTrue("pools ${pools.size}", pools.size in 1..InkDynamics.MAX_DWELL_POOLS)
    }

    private fun timedHold(
        rate: Int,
        durationMs: Int = 200,
        pressure: Float = 0.65f,
    ): Pair<List<NormalizedPoint>, List<Long>> {
        val holdSamples = durationMs * rate / 1000 + 1
        val points = listOf(NormalizedPoint(0.1f, 0.5f, pressure)) +
            List(holdSamples) { NormalizedPoint(0.3f, 0.5f, pressure) } +
            listOf(NormalizedPoint(0.5f, 0.5f, pressure))
        val times = listOf(0L) +
            List(holdSamples) { index -> 20L + (index * 1000.0 / rate).roundToLong() } +
            listOf(20L + durationMs + 20L)
        return points to times
    }

    @Test
    fun `constant pressure timed hold pools with bounded duration based strength`() {
        val (points, times) = timedHold(60)
        assertEquals(0f, PenStrokeGeometry.pressureVariation(points), 0f)
        val pools = InkDynamics.dwellPools(points, 1190f, 1684f, 0f, times)
        assertEquals(1, pools.size)
        assertEquals(1f, pools[0].strength, 0f)

        val (shortPoints, shortTimes) = timedHold(60, durationMs = 50)
        val short = InkDynamics.dwellPools(shortPoints, 1190f, 1684f, 0f, shortTimes)
        assertEquals(1, short.size)
        assertEquals(0.25f, short[0].strength, 0.001f)
        assertTrue(short[0].strength in 0.15f..1f)

        val (neutralPoints, neutralTimes) = timedHold(60, pressure = 1f)
        assertTrue(
            InkDynamics.dwellPools(neutralPoints, 1190f, 1684f, 0f, neutralTimes).isEmpty(),
        )
    }

    @Test
    fun `timed dwell decisions and strength agree across capture rates and page scales`() {
        val holds = listOf(60, 120, 240).map { rate ->
            val (points, times) = timedHold(rate)
            InkDynamics.dwellPools(points, 1190f, 1684f, 0f, times)
        }
        holds.forEach { pools ->
            assertEquals(1, pools.size)
            assertEquals(1f, pools.single().strength, 0f)
        }

        val (points, times) = timedHold(60)
        val expected = InkDynamics.dwellPools(points, 1190f, 1684f, 0f, times)
        listOf(2f, 2.2f, 2.5f).forEach { scale ->
            assertEquals(
                "pools at ${scale}x",
                expected,
                InkDynamics.dwellPools(points, 595f * scale, 842f * scale, 0f, times),
            )
        }
    }

    @Test
    fun `timed dwell duration threshold is independent of capture rate`() {
        listOf(60, 120, 240).forEach { rate ->
            val (shortPoints, shortTimes) = timedHold(rate, durationMs = 20)
            assertTrue(
                "20 ms at $rate Hz is only sensor noise",
                InkDynamics.dwellPools(shortPoints, 1190f, 1684f, 0f, shortTimes).isEmpty(),
            )
            val (holdPoints, holdTimes) = timedHold(rate, durationMs = 40)
            assertEquals(
                "40 ms at $rate Hz pools",
                1,
                InkDynamics.dwellPools(holdPoints, 1190f, 1684f, 0f, holdTimes).size,
            )
        }
    }

    @Test
    fun `a timed moving stroke never pools`() {
        val count = 41
        val points = List(count) { NormalizedPoint(0.1f + it * 0.01f, 0.5f, 0.65f) }
        val times = List(count) { it * 16L }
        assertTrue(InkDynamics.dwellPools(points, 1190f, 1684f, 0f, times).isEmpty())
    }

    @Test
    fun `legacy untimed dwell keeps its bounded sample count strength`() {
        val pools = InkDynamics.dwellPools(dwellStroke(), 1000f, 1000f, 0.8f)
        assertEquals(1, pools.size)
        assertEquals(0.2f, pools.single().strength, 0f)
    }

    @Test
    fun `replay reveals pools only after their anchor sample`() {
        val pools = listOf(InkDynamics.InkPool(6, 0.3f), InkDynamics.InkPool(12, 0.8f))
        assertTrue(InkDynamics.poolsForReveal(pools, 3).isEmpty())
        assertEquals(listOf(pools[0]), InkDynamics.poolsForReveal(pools, 7))
        assertEquals(pools, InkDynamics.poolsForReveal(pools, 13))
        assertTrue(InkDynamics.poolsForReveal(pools, -2).isEmpty())
        // Strength never changes while revealing.
        assertEquals(0.3f, InkDynamics.poolsForReveal(pools, 7)[0].strength, 0f)
    }

    // ---- ballpointSpeedFlow --------------------------------------------------

    @Test
    fun `neutral ballpoint flow is the plain mean`() {
        val flows = listOf(0.9f, 0.6f, 0.3f)
        assertEquals(
            0.6f,
            InkDynamics.ballpointSpeedFlow(flows, floatArrayOf(1f, 1f, 1f), 1f, 0f),
            0.0001f,
        )
        assertEquals(1f, InkDynamics.ballpointSpeedFlow(emptyList(), FloatArray(0), 0f, 0f), 0f)
    }

    @Test
    fun `slow careful writing reads wetter than a quick scribble`() {
        // High flow while slow, low flow while fast — the real correlation.
        val flows = listOf(0.9f, 0.9f, 0.3f, 0.3f)
        val slowFirst = floatArrayOf(0.1f, 0.1f, 4f, 4f)
        val fastFirst = floatArrayOf(4f, 4f, 0.1f, 0.1f)
        val slow = InkDynamics.ballpointSpeedFlow(flows, slowFirst, 1f, 0.8f)
        val fast = InkDynamics.ballpointSpeedFlow(flows, fastFirst, 1f, 0.8f)
        val plain = 0.6f
        assertTrue("slow $slow > plain $plain", slow > plain)
        assertTrue("fast $fast < plain $plain", fast < plain)
        assertTrue("bounded", slow in 0f..1f && fast in 0f..1f)
    }

    @Test
    fun `ballpoint flow is safe for degenerate speeds`() {
        val flows = listOf(0.5f, 0.5f)
        assertEquals(
            0.5f,
            InkDynamics.ballpointSpeedFlow(flows, floatArrayOf(Float.NaN, 1f), 0f, 0.8f),
            0f,
        )
        assertEquals(
            0.5f,
            InkDynamics.ballpointSpeedFlow(flows, FloatArray(1), 1f, 0.8f),
            0f,
        )
    }

    // ---- ballpoint width band ------------------------------------------------

    @Test
    fun `ballpoint width barely swells while the default band stays wide`() {
        val points = listOf(
            NormalizedPoint(0.2f, 0.5f, 1f),
            NormalizedPoint(0.4f, 0.5f, 0.8f),
            NormalizedPoint(0.6f, 0.5f, 0.1f),
            NormalizedPoint(0.8f, 0.5f, 0.6f),
        )
        val variation = PenStrokeGeometry.pressureVariation(points)
        val series = List(4) { 1f } // ballpoint response width is constant 1f
        val ballpoint = PenStrokeGeometry.outlineWidthSeries(
            points, series, variation, 10f, 1f,
            minWidthFactor = InkDynamics.BALLPOINT_MIN_WIDTH_FACTOR,
        )
        // Interior sample (t = 0.5) has no end envelope: width stays near 5.
        assertTrue(
            "ballpoint interior ${ballpoint[1]} stays near neutral 5",
            ballpoint[1] >= 4.7f && ballpoint[1] <= 5f,
        )
        val legacy = PenStrokeGeometry.outlineWidthSeries(points, series, variation, 10f, 1f)
        assertTrue(
            "default band still swells/thins strongly (${legacy[2]})",
            legacy[2] < 4f,
        )
        // Neutral identity is untouched by the new parameter.
        val neutral = List(4) { NormalizedPoint(0.2f * it, 0.5f, 1f) }
        PenStrokeGeometry.outlineWidthSeries(
            neutral, List(4) { 1f }, 0f, 10f, 1f,
            minWidthFactor = InkDynamics.BALLPOINT_MIN_WIDTH_FACTOR,
        ).forEach { assertEquals(5f, it, 0f) }
    }
}
