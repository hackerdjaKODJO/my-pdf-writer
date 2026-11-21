package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkResponseTest {

    private val styles = PenStyle.values().toList()
    private val flags = listOf(false, true)
    private val neutral = InkResponse(1f, 1f, 1f)

    /** The same 1:2:1 kernel as AnnotationPainter.pressureWidths, written independently. */
    private fun smooth(values: List<Float>): List<Float> {
        if (values.size < 3) return values
        return values.indices.map { index ->
            val previous = values[(index - 1).coerceAtLeast(0)]
            val current = values[index]
            val next = values[(index + 1).coerceAtMost(values.lastIndex)]
            (previous + current * 2f + next) * 0.25f
        }
    }

    @Test
    fun `neutral pressure is exact identity for every style and flag`() {
        styles.forEach { style ->
            flags.forEach { highlight ->
                assertEquals("$style/$highlight response", neutral, inkResponse(style, highlight, 1f))
                assertEquals("$style/$highlight width", 1f, inkWidthFactor(style, highlight, 1f), 0f)
                assertEquals("$style/$highlight flow", 1f, inkFlowFactor(style, highlight, 1f), 0f)
                assertEquals("$style/$highlight grain", 1f, inkGrainFactor(style, highlight, 1f), 0f)

                // A clamped value above neutral must also land on exact identity.
                assertEquals("$style/$highlight above range", neutral, inkResponse(style, highlight, 7f))
            }
        }
    }

    @Test
    fun `non finite pressure falls back to exact neutral identity`() {
        val nonFinite = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        styles.forEach { style ->
            flags.forEach { highlight ->
                nonFinite.forEach { value ->
                    assertEquals("$style/$highlight/$value", neutral, inkResponse(style, highlight, value))
                    assertEquals("$style/$highlight/$value width", 1f, inkWidthFactor(style, highlight, value), 0f)
                    assertEquals("$style/$highlight/$value flow", 1f, inkFlowFactor(style, highlight, value), 0f)
                    assertEquals("$style/$highlight/$value grain", 1f, inkGrainFactor(style, highlight, value), 0f)
                }
            }
        }
    }

    @Test
    fun `all factors stay finite bounded and monotonically non decreasing`() {
        val wild = listOf(-3f, 0f, 0.13f, 0.5f, 0.87f, 1f, 4f,
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)
        styles.forEach { style ->
            flags.forEach { highlight ->
                wild.forEach { pressure ->
                    val response = inkResponse(style, highlight, pressure)
                    assertTrue("$style/$highlight/$pressure width finite", response.width.isFinite())
                    assertTrue("$style/$highlight/$pressure flow finite", response.flow.isFinite())
                    assertTrue("$style/$highlight/$pressure grain finite", response.grain.isFinite())
                    assertTrue("$style/$highlight/$pressure width floor", response.width >= MIN_PRESSURE_WIDTH_FACTOR)
                    assertTrue("$style/$highlight/$pressure width bound", response.width <= 1f)
                    assertTrue("$style/$highlight/$pressure flow positive", response.flow > 0f && response.flow <= 1f)
                    assertTrue("$style/$highlight/$pressure grain positive", response.grain > 0f && response.grain <= 1f)
                }

                var previous = inkResponse(style, highlight, 0f)
                for (step in 1..20) {
                    val current = inkResponse(style, highlight, step / 20f)
                    assertTrue("$style/$highlight width monotonic", current.width >= previous.width)
                    assertTrue("$style/$highlight flow monotonic", current.flow >= previous.flow)
                    assertTrue("$style/$highlight grain monotonic", current.grain >= previous.grain)
                    previous = current
                }
            }
        }
    }

    @Test
    fun `ballpoint and highlight keep constant width while flow varies`() {
        val pressures = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        pressures.forEach { pressure ->
            assertEquals("ballpoint width", 1f, inkWidthFactor(PenStyle.BALLPOINT, false, pressure), 0f)
            styles.forEach { style ->
                assertEquals("highlight overrides $style width", 1f, inkWidthFactor(style, true, pressure), 0f)
                assertEquals(
                    "highlight overrides $style response",
                    inkResponse(PenStyle.BALLPOINT, true, pressure),
                    inkResponse(style, true, pressure),
                )
            }
        }

        assertEquals(0.45f, inkFlowFactor(PenStyle.BALLPOINT, false, 0f), 0f)
        assertEquals(0.70f, inkFlowFactor(PenStyle.BALLPOINT, true, 0f), 0f)
        assertTrue(inkFlowFactor(PenStyle.BALLPOINT, false, 0.5f) > 0.45f)
        assertTrue(inkFlowFactor(PenStyle.BALLPOINT, false, 0.5f) < 1f)
        assertTrue(inkFlowFactor(PenStyle.BALLPOINT, true, 0.9f) > inkFlowFactor(PenStyle.BALLPOINT, true, 0.2f))
    }

    @Test
    fun `fountain and brush width match the legacy width factor across pressures`() {
        val pressures = listOf(-2f, 0f, 0.07f, 0.25f, 0.5f, 0.9f, 1f, 3f,
            Float.NaN, Float.POSITIVE_INFINITY)
        pressures.forEach { pressure ->
            val legacy = inkPressureWidthFactor(pressure)
            assertEquals("fountain width at $pressure", legacy, inkWidthFactor(PenStyle.FOUNTAIN, false, pressure), 0f)
            assertEquals("brush width at $pressure", legacy, inkWidthFactor(PenStyle.BRUSH, false, pressure), 0f)
        }
    }

    @Test
    fun `pencil responds in width flow and grain`() {
        val zero = inkResponse(PenStyle.PENCIL, false, 0f)
        assertEquals(0.55f, zero.width, 0f)
        assertEquals(0.35f, zero.flow, 0f)
        assertEquals(0.40f, zero.grain, 0f)

        val mid = inkResponse(PenStyle.PENCIL, false, 0.5f)
        assertTrue("pencil width responds", mid.width > zero.width && mid.width < 1f)
        assertTrue("pencil flow responds", mid.flow > zero.flow && mid.flow < 1f)
        assertTrue("pencil grain responds", mid.grain > zero.grain && mid.grain < 1f)
        assertEquals(neutral, inkResponse(PenStyle.PENCIL, false, 1f))

        // Brush is the only other profile with a non-neutral grain response.
        assertEquals(0.50f, inkGrainFactor(PenStyle.BRUSH, false, 0f), 0f)
        assertEquals(1f, inkGrainFactor(PenStyle.FOUNTAIN, false, 0f), 0f)
    }

    @Test
    fun `response series applies the same one two one smoothing kernel`() {
        val pressures = listOf(0f, 0.5f, 1f, 0.25f)
        val points = pressures.map { NormalizedPoint(0.1f, 0.2f, it) }
        val raw = pressures.map { inkResponse(PenStyle.BALLPOINT, false, it) }
        val expectedWidths = smooth(raw.map { it.width })
        val expectedFlows = smooth(raw.map { it.flow })
        val expectedGrains = smooth(raw.map { it.grain })

        val series = inkResponseSeries(points, PenStyle.BALLPOINT, false)
        assertEquals(pressures.size, series.size)
        series.indices.forEach { index ->
            assertEquals("width[$index]", expectedWidths[index], series[index].width, 1e-6f)
            assertEquals("flow[$index]", expectedFlows[index], series[index].flow, 1e-6f)
            assertEquals("grain[$index]", expectedGrains[index], series[index].grain, 1e-6f)
        }

        // Short strokes are returned verbatim, matching pressureWidths.
        val shortPoints = listOf(NormalizedPoint(0f, 0f, 0f), NormalizedPoint(1f, 1f, 1f))
        val shortSeries = inkResponseSeries(shortPoints, PenStyle.BALLPOINT, false)
        assertEquals(2, shortSeries.size)
        assertEquals(inkFlowFactor(PenStyle.BALLPOINT, false, 0f), shortSeries[0].flow, 0f)
        assertEquals(1f, shortSeries[1].flow, 0f)
        assertTrue(inkResponseSeries(emptyList(), PenStyle.BALLPOINT, false).isEmpty())

        // A uniform neutral stroke stays exactly neutral after smoothing.
        val neutralPoints = List(6) { NormalizedPoint(0f, 0f, 1f) }
        inkResponseSeries(neutralPoints, PenStyle.BRUSH, false).forEach {
            assertEquals(neutral, it)
        }
    }

    @Test
    fun `mean flow is one for empty and neutral strokes`() {
        assertEquals(1f, meanInkFlow(emptyList(), PenStyle.PENCIL, false), 0f)

        val neutralPoints = List(5) { NormalizedPoint(0.1f * it, 0.2f, 1f) }
        styles.forEach { style ->
            flags.forEach { highlight ->
                assertEquals("$style/$highlight neutral mean", 1f,
                    meanInkFlow(neutralPoints, style, highlight), 0f)
            }
        }

        val lightPoints = List(5) { NormalizedPoint(0f, 0f, 0f) }
        assertEquals(0.45f, meanInkFlow(lightPoints, PenStyle.BALLPOINT, false), 1e-6f)
        assertTrue(meanInkFlow(lightPoints, PenStyle.PENCIL, false) < 0.45f)
    }

    @Test
    fun `response composes from the individual factors`() {
        val pressures = listOf(0f, 0.2f, 0.5f, 0.8f, 1f, Float.NaN)
        styles.forEach { style ->
            flags.forEach { highlight ->
                pressures.forEach { pressure ->
                    assertEquals(
                        "$style/$highlight/$pressure composition",
                        InkResponse(
                            inkWidthFactor(style, highlight, pressure),
                            inkFlowFactor(style, highlight, pressure),
                            inkGrainFactor(style, highlight, pressure),
                        ),
                        inkResponse(style, highlight, pressure),
                    )
                }
            }
        }
    }

    @Test
    fun `legacy json with uniform pressure stays exactly neutral`() {
        val legacy = JSONObject(
            """{"t":"PEN","p":0,"c":-1,"w":0.004,"pts":[0.1,0.2,0.3,0.4,0.5,0.6],"prs":[1.0,1.0,1.0],"ps":"BRUSH"}""",
        )
        val annotation = Annotation.fromJson(legacy)!!
        assertEquals(3, annotation.points.size)
        val highlight = annotation.type == AnnotationType.HIGHLIGHT

        val series = inkResponseSeries(annotation.points, annotation.penStyle, highlight)
        assertEquals(3, series.size)
        series.forEach { assertEquals(neutral, it) }
        assertEquals(1f, meanInkFlow(annotation.points, annotation.penStyle, highlight), 0f)

        // A document with no prs array at all defaults every sample to neutral.
        val noPressure = JSONObject(
            """{"t":"PEN","p":0,"c":-1,"w":0.004,"pts":[0.1,0.2,0.3,0.4],"ps":"PENCIL"}""",
        )
        val defaulted = Annotation.fromJson(noPressure)!!
        inkResponseSeries(defaulted.points, defaulted.penStyle, false).forEach {
            assertEquals(neutral, it)
        }
    }

    @Test
    fun `calibrator neutral outputs keep curves and responses exactly neutral`() {
        val calibrator = PressureCalibrator(alpha = 1f, deadZone = 0f)
        calibrator.map(0.2f)
        calibrator.map(0.7f)

        listOf(
            calibrator.map(1f),
            calibrator.map(Float.NaN),
            calibrator.map(Float.POSITIVE_INFINITY),
            calibrator.map(Float.NEGATIVE_INFINITY),
        ).forEach { neutralInput ->
            assertEquals(1f, neutralInput, 0f)
            PressureCurve.entries.forEach { curve ->
                assertEquals("$curve full pressure", 1f, curve.applyTo(neutralInput), 0f)
            }
            styles.forEach { style ->
                flags.forEach { highlight ->
                    assertEquals(
                        "$style/$highlight response",
                        neutral,
                        inkResponse(style, highlight, neutralInput),
                    )
                }
            }
        }
    }
}
