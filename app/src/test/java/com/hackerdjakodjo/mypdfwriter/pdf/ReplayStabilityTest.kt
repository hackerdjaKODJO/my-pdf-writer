package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.revealPointCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Replay regressions for dynamics that used to be recomputed from the visible
 * prefix. Canvas pixels are platform-dependent, so these compare the complete
 * pure draw plan that feeds the pencil renderer.
 */
class ReplayStabilityTest {

    private val points = listOf(
        NormalizedPoint(0.05f, 0.20f, 1f),
        NormalizedPoint(0.12f, 0.24f, 0.92f),
        NormalizedPoint(0.22f, 0.18f, 0.78f),
        NormalizedPoint(0.35f, 0.30f, 0.55f),
        NormalizedPoint(0.48f, 0.19f, 0.42f),
        NormalizedPoint(0.60f, 0.34f, 0.68f),
        NormalizedPoint(0.74f, 0.22f, 0.48f),
        NormalizedPoint(0.86f, 0.31f, 0.76f),
        NormalizedPoint(0.95f, 0.25f, 0.90f),
    )

    private fun annotation(timed: Boolean): Annotation = Annotation(
        type = AnnotationType.PEN,
        page = 0,
        color = 0xFF222222.toInt(),
        width = 0.004f,
        points = points,
        penStyle = PenStyle.PENCIL,
        durationMs = if (timed) 700L else 0L,
        timesMs = if (timed) {
            listOf(0L, 10L, 30L, 90L, 150L, 240L, 360L, 520L, 700L)
        } else {
            null
        },
    )

    private fun context(annotation: Annotation, livePreview: Boolean = false) =
        AnnotationPainter.inkContext(
            points = annotation.points,
            penStyle = annotation.penStyle,
            width = 1000f,
            height = 1400f,
            timesMs = annotation.timesMs,
            livePreview = livePreview,
        )

    private fun revealedContext(
        ink: AnnotationPainter.InkContext,
        count: Int,
    ): AnnotationPainter.InkContext = ink.copy(
        series = ink.series.take(count),
        outlineWidths = ink.outlineWidths.copyOf(count),
        fountainSmoothed = ink.fountainSmoothed.copyOf(count),
        brushEnvelope = ink.brushEnvelope.copyOf(count),
        speeds = ink.speeds.copyOf(count),
        arcLengths = ink.arcLengths.copyOf(count),
        segmentSpeeds = ink.segmentSpeeds.copyOf(count),
        pools = InkDynamics.poolsForReveal(ink.pools, count),
    )

    private fun plans(
        ink: AnnotationPainter.InkContext,
        visiblePoints: List<NormalizedPoint>,
        pass: Int,
        revealedSamples: Int = minOf(ink.totalSamples, visiblePoints.size),
    ) = AnnotationPainter.pencilSegmentPlans(
        ink = ink,
        points = visiblePoints,
        pass = pass,
        base = 4f,
        baseAlpha = 255,
        revealedSamples = revealedSamples,
    )

    @Test
    fun `pencil prefix width alpha and deposit equal the full stroke plan at every fraction`() {
        listOf(true, false).forEach { timed ->
            val annotation = annotation(timed)
            val full = context(annotation)
            val fullPlans = (0..2).map { pass -> plans(full, points, pass) }
            val fractions = listOf(0.1f, 0.2f, 0.35f, 0.5f, 0.7f, 0.85f, 0.95f)

            fractions.forEach { fraction ->
                val count = annotation.revealPointCount(fraction).coerceIn(1, points.size)
                val prefix = revealedContext(full, count)
                assertEquals("full sample count", full.totalSamples, prefix.totalSamples)
                assertEquals("full arc length", full.totalArcLength, prefix.totalArcLength, 0f)
                assertEquals("full pace", full.segmentSpeedReference, prefix.segmentSpeedReference, 0f)

                (0..2).forEach { pass ->
                    assertEquals(
                        "timed=$timed fraction=$fraction pass=$pass",
                        fullPlans[pass].take(count - 1),
                        plans(prefix, points.take(count), pass),
                    )
                }
            }
        }
    }

    @Test
    fun `full legacy pencil plan keeps the original untimed formulas`() {
        val annotation = annotation(timed = false)
        val full = context(annotation)
        val index = 5
        val pass = 0
        val plan = AnnotationPainter.pencilSegmentPlan(
            full,
            index,
            pass,
            4f,
            255,
            points[index].pressure,
        )
        val local = full.series[index]
        val oldTip = InkQuality.nibEndProfile(
            AnnotationPainter.inkEndPosition(full, index, points.size),
            full.variation,
        )
        val oldSpeed = InkDynamics.velocityWidthFactor(
            full.speeds[index],
            full.speedReference,
            InkDynamics.PENCIL_SPEED_INFLUENCE,
            full.variation,
        )
        val oldReference = full.totalArcLength / points.size.toFloat()
        val wave = PenStrokeGeometry.graphiteJitterByArc(
            index,
            full.arcLengths[index],
            pass,
            local.grain,
            index * 2.3 + 7.1,
        )
        val deposit = PenStrokeGeometry.graphiteDeposit(
            full.arcLengths[index], pass, local.grain, wave,
        )
        val gain = InkQuality.depositGain(
            full.arcLengths[index] - full.arcLengths[index - 1],
            oldReference,
            points[index].pressure,
            full.variation,
        )
        assertEquals(
            AnnotationPainter.pencilStrokeWidth(4f, local.width) * oldTip * oldSpeed,
            plan.width,
            0f,
        )
        assertEquals(deposit, plan.deposit, 0f)
        assertEquals(gain, plan.depositGain, 0f)
        assertEquals(
            (255f * PenStrokeGeometry.pencilSegmentAlpha(pass, local.flow) * deposit * gain)
                .roundToInt().coerceIn(1, 255),
            plan.alpha,
        )
    }

    @Test
    fun `timed pencil deposit uses the full stroke central physical speed reference`() {
        val annotation = annotation(timed = true)
        val full = context(annotation)
        val index = 5
        val plan = AnnotationPainter.pencilSegmentPlan(
            full,
            index,
            0,
            4f,
            255,
            points[index].pressure,
        )
        assertEquals(
            InkQuality.depositGain(
                speed = full.speeds[index],
                reference = full.speedReference,
                pressure = points[index].pressure,
                variation = full.variation,
            ),
            plan.depositGain,
            0f,
        )
    }

    @Test
    fun `pencil regression rejects the old prefix-local tip and deposit references`() {
        val annotation = annotation(timed = true)
        val full = context(annotation)
        val count = annotation.revealPointCount(0.5f)
        val prefix = revealedContext(full, count)
        val index = count - 1
        val plan = AnnotationPainter.pencilSegmentPlan(
            ink = prefix,
            index = index,
            pass = 0,
            base = 4f,
            baseAlpha = 255,
            pressure = points[index].pressure,
        )

        // Before this patch, t and the deposit reference were divided by the
        // visible prefix. Both values differ from the committed full-stroke plan.
        val oldPrefixTip = InkQuality.nibEndProfile(
            t = index.toFloat() / (count - 1).toFloat(),
            variation = full.variation,
        )
        val oldPrefixReference = (prefix.arcLengths.lastOrNull() ?: 0f) / count.toFloat()
        val oldPrefixGain = InkQuality.depositGain(
            speed = prefix.arcLengths[index] - prefix.arcLengths[index - 1],
            reference = oldPrefixReference,
            pressure = points[index].pressure,
            variation = full.variation,
        )
        assertTrue("old prefix retapers the tip", abs(oldPrefixTip - plan.tip) > 0.01f)
        assertTrue(
            "old prefix changes deposit pace",
            abs(oldPrefixGain - plan.depositGain) > 0.001f,
        )
    }

    @Test
    fun `last replay frame matches full pencil rendering within one sample tip guard`() {
        listOf(true, false).forEach { timed ->
            val annotation = annotation(timed)
            val full = context(annotation)
            val finalCount = annotation.revealPointCount(0.999f)
            assertTrue(
                "final replay frame is at most one sample short",
                finalCount >= points.size - TIP_GUARD_SAMPLES,
            )
            val prefix = revealedContext(full, finalCount)
            (0..2).forEach { pass ->
                val fullPlans = plans(full, points, pass)
                val finalPlans = plans(prefix, points.take(finalCount), pass)
                assertEquals(fullPlans.take(finalCount - 1), finalPlans)
                if (finalCount == points.size) {
                    assertEquals("complete final frame", fullPlans, finalPlans)
                }
            }
            // The next frame (fraction 1) takes the unchanged full-render path.
            assertEquals(full.totalSamples, annotation.revealPointCount(1f))
        }
    }

    @Test
    fun `live pencil preview does not apply a growing final-length tip profile`() {
        val annotation = annotation(timed = true)
        (3..points.size).forEach { count ->
            val growing = annotation.copy(
                points = points.take(count),
                timesMs = annotation.timesMs?.take(count),
            )
            val preview = context(growing, livePreview = true)
            val plan = AnnotationPainter.pencilSegmentPlan(
                ink = preview,
                index = count - 1,
                pass = 0,
                base = 4f,
                baseAlpha = 255,
                pressure = points[count - 1].pressure,
            )
            assertEquals("preview tip stays uncommitted at count=$count", 1f, plan.tip, 0f)
        }
    }

    private companion object {
        /** A timed final frame may stop one sample short; fraction 1 is exact. */
        const val TIP_GUARD_SAMPLES = 1
    }
}
