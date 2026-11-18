package com.hackerdjakodjo.mypdfwriter.data

import kotlin.math.pow

/** Default pressure for fingers, mice, unknown pointers and legacy documents. */
const val DEFAULT_INK_PRESSURE = 1f

/** Lowest stored/rendered pressure. Zero-pressure samples stay visible. */
const val MIN_INK_PRESSURE = 0f

/** Highest accepted stylus pressure. */
const val MAX_INK_PRESSURE = 1f

/**
 * Narrowest a pressure-sensitive stroke may become. This is deliberately well
 * above zero so a light or missing sample can never collapse ink to a hairline
 * or a NaN width.
 */
const val MIN_PRESSURE_WIDTH_FACTOR = 0.35f

/**
 * Keeps a stored or captured pressure inside the supported range. Non-finite
 * values fall back to [fallback] (itself clamped) instead of leaking NaN into
 * geometry or JSON.
 */
fun clampInkPressure(
    value: Float,
    fallback: Float = DEFAULT_INK_PRESSURE,
): Float {
    val safeFallback = if (fallback.isFinite()) {
        fallback.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
    } else {
        DEFAULT_INK_PRESSURE
    }
    if (!value.isFinite()) return safeFallback
    return value.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
}

/**
 * Maps 0..1 stylus pressure to a safe width multiplier for fountain/brush ink.
 * Pressure 1 returns exactly 1, so legacy strokes render byte-for-byte as they
 * did before pressure support. Pressure 0 returns [MIN_PRESSURE_WIDTH_FACTOR].
 */
fun inkPressureWidthFactor(pressure: Float): Float {
    val safe = clampInkPressure(pressure)
    return MIN_PRESSURE_WIDTH_FACTOR + (1f - MIN_PRESSURE_WIDTH_FACTOR) * safe
}

/**
 * Original, device-neutral stylus response presets.
 *
 * A curve maps raw pressure 0..1 to effective pressure 0..1 before the value is
 * stored in `prs`. All presets are exact identities at 0 and 1, so a fully
 * pressed sample, a finger sample and a legacy document (pressure 1) stay
 * byte-for-byte neutral. Between those endpoints:
 *
 *  - [SOFT] lifts light pressure (gamma < 1), making faint contact swell sooner.
 *  - [LINEAR] is the identity and the default.
 *  - [FIRM] compresses light pressure (gamma > 1), requiring a firmer press.
 *
 * These are stable presets, not hardware calibration. Real stylus digitizers
 * report different pressure ranges and linearity, so on-device tuning remains a
 * separate task.
 */
enum class PressureCurve {
    SOFT,
    LINEAR,
    FIRM,
    ;

    /**
     * Maps one raw pressure sample to effective pressure. Out-of-range values are
     * clamped and non-finite values fall back to the neutral pressure instead of
     * leaking NaN into stored points or geometry.
     */
    fun applyTo(rawPressure: Float): Float {
        val safe = clampInkPressure(rawPressure)
        return when (this) {
            SOFT -> safe.pow(SOFT_GAMMA)
            LINEAR -> safe
            FIRM -> safe.pow(FIRM_GAMMA)
        }.coerceIn(MIN_INK_PRESSURE, MAX_INK_PRESSURE)
    }

    companion object {
        /** Default for new sessions; matches the pre-curve 0.8.4 behavior. */
        val DEFAULT = LINEAR

        // Chosen so a light 25% press lands near 41% (SOFT) or 12% (FIRM).
        private const val SOFT_GAMMA = 0.65f
        private const val FIRM_GAMMA = 1.55f
    }
}

/**
 * A concise raw-to-effective pressure summary for the debug trace. It contains
 * ranges only, never page coordinates or stroke content.
 */
data class PressureCurveRange(
    val curve: PressureCurve,
    val rawMin: Float,
    val rawMax: Float,
    val effectiveMin: Float,
    val effectiveMax: Float,
)

/**
 * Builds a safe diagnostic range. Inputs are clamped independently, so an
 * inverted range still yields a finite, ordered summary.
 */
fun pressureCurveRange(
    curve: PressureCurve,
    rawMin: Float,
    rawMax: Float,
): PressureCurveRange {
    val low = clampInkPressure(rawMin)
    val high = clampInkPressure(rawMax)
    val effectiveLow = curve.applyTo(low)
    val effectiveHigh = curve.applyTo(high)
    return PressureCurveRange(
        curve = curve,
        rawMin = minOf(low, high),
        rawMax = maxOf(low, high),
        effectiveMin = minOf(effectiveLow, effectiveHigh),
        effectiveMax = maxOf(effectiveLow, effectiveHigh),
    )
}

/**
 * Normalized, per-sample ink response. Every component is a multiplier that is
 * safe to feed straight into rendering: [width] never drops below
 * [MIN_PRESSURE_WIDTH_FACTOR], while [flow] (darkness/opacity) and [grain]
 * (edge texture) stay in (0, 1].
 *
 * `InkResponse(1f, 1f, 1f)` is the neutral legacy response: a fully pressed
 * sample, a finger/mouse sample and a pre-pressure document all map to it, so
 * existing strokes render exactly as they did before response profiles.
 */
data class InkResponse(
    val width: Float,
    val flow: Float,
    val grain: Float,
)

/**
 * Immutable, device-neutral response profile for one tool.
 *
 * Each component is the multiplier at zero pressure; a component of 1f means
 * "does not respond to pressure". The ramp is linear and reaches exactly 1f at
 * full pressure. [legacyWidth] replays the original fountain/brush width curve
 * verbatim so those strokes stay byte-for-byte compatible with old documents.
 */
private data class InkProfile(
    val widthAtZero: Float,
    val flowAtZero: Float,
    val grainAtZero: Float,
    val legacyWidth: Boolean = false,
) {
    companion object {
        // A constant-width pen: only darkness responds to pressure.
        val BALLPOINT = InkProfile(widthAtZero = 1f, flowAtZero = 0.45f, grainAtZero = 1f)

        // Fountain/brush reuse the legacy nib width; brush also adds texture.
        val FOUNTAIN = InkProfile(1f, 0.65f, 1f, legacyWidth = true)
        val BRUSH = InkProfile(1f, 0.40f, 0.50f, legacyWidth = true)

        // Pencil thins and lightens with pressure and gains a grainy edge.
        val PENCIL = InkProfile(0.55f, 0.35f, 0.40f)

        // Highlighter keeps a constant width; only its tint intensity varies.
        val HIGHLIGHT = InkProfile(1f, 0.70f, 1f)
    }
}

/** Highlight always wins over the underlying pen style of an annotation. */
private fun inkProfileFor(penStyle: PenStyle, isHighlight: Boolean): InkProfile =
    if (isHighlight) {
        InkProfile.HIGHLIGHT
    } else {
        when (penStyle) {
            PenStyle.BALLPOINT -> InkProfile.BALLPOINT
            PenStyle.FOUNTAIN -> InkProfile.FOUNTAIN
            PenStyle.BRUSH -> InkProfile.BRUSH
            PenStyle.PENCIL -> InkProfile.PENCIL
        }
    }

/**
 * Linear ramp from [atZero] at zero pressure to exactly 1f at full pressure.
 * The explicit endpoint keeps legacy strokes bit-identical even if a future
 * constant would otherwise round the sum to 0.99999994f.
 */
private fun rampedFactor(atZero: Float, safePressure: Float): Float {
    if (safePressure >= MAX_INK_PRESSURE) return 1f
    return atZero + (1f - atZero) * safePressure
}

/** Width multiplier for one pressure sample; always finite and >= 0.35. */
fun inkWidthFactor(penStyle: PenStyle, isHighlight: Boolean, pressure: Float): Float {
    val safe = clampInkPressure(pressure)
    val profile = inkProfileFor(penStyle, isHighlight)
    return if (profile.legacyWidth) {
        inkPressureWidthFactor(safe)
    } else {
        rampedFactor(profile.widthAtZero, safe)
    }
}

/** Flow (darkness/opacity) multiplier, in (0, 1], for one pressure sample. */
fun inkFlowFactor(penStyle: PenStyle, isHighlight: Boolean, pressure: Float): Float {
    val safe = clampInkPressure(pressure)
    return rampedFactor(inkProfileFor(penStyle, isHighlight).flowAtZero, safe)
}

/** Grain (texture) multiplier, in (0, 1], for one pressure sample. */
fun inkGrainFactor(penStyle: PenStyle, isHighlight: Boolean, pressure: Float): Float {
    val safe = clampInkPressure(pressure)
    return rampedFactor(inkProfileFor(penStyle, isHighlight).grainAtZero, safe)
}

/** Complete response for one pressure sample. Non-finite input is neutral. */
fun inkResponse(penStyle: PenStyle, isHighlight: Boolean, pressure: Float): InkResponse =
    InkResponse(
        width = inkWidthFactor(penStyle, isHighlight, pressure),
        flow = inkFlowFactor(penStyle, isHighlight, pressure),
        grain = inkGrainFactor(penStyle, isHighlight, pressure),
    )

/**
 * Smooths one component series with the same 1:2:1 neighbourhood kernel used
 * by the legacy width path in `AnnotationPainter`. Neighbour indices are
 * clamped, so the first and last samples are kept (never wrapped or dropped)
 * and a uniform neutral series stays exactly 1f end to end.
 */
private fun smoothResponseComponent(values: FloatArray): FloatArray {
    val count = values.size
    if (count < 3) return values
    return FloatArray(count) { index ->
        val previous = values[(index - 1).coerceAtLeast(0)]
        val current = values[index]
        val next = values[(index + 1).coerceAtMost(count - 1)]
        (previous + current * 2f + next) * 0.25f
    }
}

/**
 * Per-point responses for a whole stroke. Each component is smoothed with the
 * identical 1:2:1 kernel, so flow and grain cannot flicker between samples.
 * Returns an empty list for an empty stroke and leaves 1-2 point strokes
 * unsmoothed, matching the legacy width behaviour.
 */
fun inkResponseSeries(
    points: List<NormalizedPoint>,
    penStyle: PenStyle,
    isHighlight: Boolean,
): List<InkResponse> {
    val count = points.size
    if (count == 0) return emptyList()
    val raw = points.map { point -> inkResponse(penStyle, isHighlight, point.pressure) }
    if (count < 3) return raw

    val widths = smoothResponseComponent(FloatArray(count) { raw[it].width })
    val flows = smoothResponseComponent(FloatArray(count) { raw[it].flow })
    val grains = smoothResponseComponent(FloatArray(count) { raw[it].grain })
    return List(count) { index ->
        InkResponse(widths[index], flows[index], grains[index])
    }
}

/**
 * Average smoothed flow of a stroke, useful as a stable per-stroke darkness.
 * An empty stroke is neutral (1f); a uniform neutral stroke also averages 1f.
 */
fun meanInkFlow(
    points: List<NormalizedPoint>,
    penStyle: PenStyle,
    isHighlight: Boolean,
): Float {
    val responses = inkResponseSeries(points, penStyle, isHighlight)
    if (responses.isEmpty()) return 1f
    var total = 0f
    responses.forEach { total += it.flow }
    return (total / responses.size).coerceIn(0f, 1f)
}
