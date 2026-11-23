package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.PenStyle

/**
 * Original, normalized geometry for the realistic writing-instrument drawings
 * shown in the pen-style picker. Pure Kotlin on purpose: no Compose imports, so
 * the geometry, palette roles and TalkBack contract can be unit-tested on JVM.
 *
 * Every coordinate is a fraction (0..1) of a square box. The pen lies along the
 * horizontal axis with its writing tip at the left; the renderer rotates the
 * whole drawing -45° so it reads as an instrument resting on the page. Because
 * widths are fractions too, the result is density-independent (unlike raw px).
 */

/** A point inside the normalized 0..1 illustration box. */
internal data class IllustrationPoint(val x: Float, val y: Float)

/** Theme-independent colour role; the renderer maps it to actual colours. */
internal enum class PenPartColor {
    BODY,
    BODY_SHADE,
    METAL,
    GOLD,
    WOOD,
    PENCIL_BODY,
    GRAPHITE,
    ERASER,
    INK,
    CUTOUT,
    HIGHLIGHT,
}

/** One drawable piece of an instrument, in normalized coordinates. */
internal sealed class PenPart {
    data class RoundRect(
        val x0: Float, val y0: Float, val x1: Float, val y1: Float,
        val corner: Float, val color: PenPartColor,
    ) : PenPart()

    data class Polygon(
        val points: List<IllustrationPoint>, val color: PenPartColor,
    ) : PenPart()

    data class Circle(
        val center: IllustrationPoint, val radius: Float, val color: PenPartColor,
    ) : PenPart()

    data class Line(
        val from: IllustrationPoint, val to: IllustrationPoint,
        val width: Float, val color: PenPartColor,
    ) : PenPart()
}

internal data class PenIllustrationSpec(
    val label: String,
    val hint: String,
    val texture: StrokeTexture,
    /** Counter-clockwise tilt that makes the horizontal pen read as diagonal. */
    val rotationDeg: Float,
    /** Ordered body parts; drawing order is the list order. */
    val parts: List<PenPart>,
    /** Sampled quadratic arc below the pen showing the stroke character. */
    val trail: List<IllustrationPoint>,
    val trailWidth: Float,
    val trailAlpha: Float,
    /** Tapered overlay from the middle of the trail to its end. */
    val trailOverlay: Boolean = false,
    /** Graphite wash speckles. */
    val trailDots: List<IllustrationPoint> = emptyList(),
)

/** Shared normalized anchors so every instrument has the same writing tip. */
internal object PenIllustrationLayout {
    const val TIP_X = 0.10f
    const val TIP_Y = 0.50f
    const val CONE_BASE_X = 0.28f
    const val CONE_TOP_Y = 0.40f
    const val CONE_BOTTOM_Y = 0.60f
    const val BARREL_END_X = 0.86f

    val TIP = IllustrationPoint(TIP_X, TIP_Y)
}

private fun trailFor(texture: StrokeTexture): List<IllustrationPoint> {
    // Quadratic from (0.14,0.86) via control (0.50,0.74) to (0.88,0.86).
    val start = IllustrationPoint(0.14f, 0.86f)
    val control = IllustrationPoint(0.50f, 0.74f)
    val end = IllustrationPoint(0.88f, 0.86f)
    return (0..8).map { i ->
        val t = i / 8f
        val u = 1f - t
        IllustrationPoint(
            x = u * u * start.x + 2f * u * t * control.x + t * t * end.x,
            y = u * u * start.y + 2f * u * t * control.y + t * t * end.y,
        )
    }
}

private fun roundRect(
    x0: Float, y0: Float, x1: Float, y1: Float,
    corner: Float, color: PenPartColor,
): PenPart = PenPart.RoundRect(x0, y0, x1, y1, corner, color)

private fun line(
    from: IllustrationPoint, to: IllustrationPoint,
    width: Float, color: PenPartColor,
): PenPart = PenPart.Line(from, to, width, color)

private val p = { x: Float, y: Float -> IllustrationPoint(x, y) }

/** The realistic illustration for one pen style. */
internal fun penIllustrationSpec(style: PenStyle): PenIllustrationSpec {
    val base = penStyleGlyphSpec(style)
    val trail = trailFor(base.texture)
    val parts = when (style) {
        PenStyle.BALLPOINT -> listOf(
            // Pocket clip and clicker make it read as a retractable ballpoint.
            line(p(0.78f, 0.43f), p(0.52f, 0.43f), 0.014f, PenPartColor.BODY_SHADE),
            roundRect(0.86f, 0.45f, 0.92f, 0.55f, 0.03f, PenPartColor.METAL),
            roundRect(0.28f, 0.43f, 0.86f, 0.57f, 0.04f, PenPartColor.BODY),
            line(p(0.32f, 0.45f), p(0.84f, 0.45f), 0.010f, PenPartColor.HIGHLIGHT),
            PenPart.Polygon(
                listOf(PenIllustrationLayout.TIP, p(0.28f, 0.43f), p(0.28f, 0.57f)),
                PenPartColor.METAL,
            ),
            PenPart.Circle(PenIllustrationLayout.TIP, 0.018f, PenPartColor.INK),
        )
        PenStyle.FOUNTAIN -> listOf(
            roundRect(0.28f, 0.38f, 0.86f, 0.62f, 0.06f, PenPartColor.BODY),
            // Posted cap sits on the back of the barrel.
            roundRect(0.64f, 0.35f, 0.90f, 0.65f, 0.06f, PenPartColor.BODY_SHADE),
            line(p(0.66f, 0.35f), p(0.66f, 0.65f), 0.022f, PenPartColor.GOLD),
            line(p(0.32f, 0.42f), p(0.60f, 0.42f), 0.012f, PenPartColor.HIGHLIGHT),
            PenPart.Polygon(
                listOf(PenIllustrationLayout.TIP, p(0.34f, 0.40f), p(0.34f, 0.60f)),
                PenPartColor.GOLD,
            ),
            // Breather hole and the ink slit running to the point.
            PenPart.Circle(p(0.24f, 0.50f), 0.022f, PenPartColor.CUTOUT),
            line(p(0.24f, 0.50f), PenIllustrationLayout.TIP, 0.012f, PenPartColor.INK),
        )
        PenStyle.BRUSH -> listOf(
            roundRect(0.34f, 0.44f, 0.90f, 0.56f, 0.05f, PenPartColor.BODY),
            line(p(0.38f, 0.46f), p(0.86f, 0.46f), 0.010f, PenPartColor.HIGHLIGHT),
            roundRect(0.28f, 0.40f, 0.36f, 0.60f, 0.01f, PenPartColor.METAL),
            PenPart.Polygon(
                listOf(p(0.28f, 0.42f), PenIllustrationLayout.TIP, p(0.28f, 0.58f)),
                PenPartColor.BODY_SHADE,
            ),
            PenPart.Circle(PenIllustrationLayout.TIP, 0.012f, PenPartColor.INK),
        )
        PenStyle.PENCIL -> listOf(
            // Ferrule and pink eraser at the back of a yellow hexagonal body.
            roundRect(0.84f, 0.42f, 0.88f, 0.58f, 0.01f, PenPartColor.METAL),
            roundRect(0.88f, 0.42f, 0.95f, 0.58f, 0.03f, PenPartColor.ERASER),
            roundRect(0.28f, 0.42f, 0.84f, 0.58f, 0.02f, PenPartColor.PENCIL_BODY),
            line(p(0.32f, 0.44f), p(0.80f, 0.44f), 0.010f, PenPartColor.HIGHLIGHT),
            PenPart.Polygon(
                listOf(PenIllustrationLayout.TIP, p(0.28f, 0.43f), p(0.28f, 0.57f)),
                PenPartColor.WOOD,
            ),
            PenPart.Polygon(
                listOf(PenIllustrationLayout.TIP, p(0.16f, 0.47f), p(0.16f, 0.53f)),
                PenPartColor.GRAPHITE,
            ),
        )
    }
    return PenIllustrationSpec(
        label = base.label,
        hint = base.hint,
        texture = base.texture,
        rotationDeg = -45f,
        parts = parts,
        trail = trail,
        trailWidth = when (base.texture) {
            StrokeTexture.EVEN -> 0.022f
            StrokeTexture.TAPERED -> 0.030f
            StrokeTexture.WASH -> 0.045f
            StrokeTexture.GRAIN -> 0.020f
        },
        trailAlpha = when (base.texture) {
            StrokeTexture.EVEN -> 1f
            StrokeTexture.TAPERED -> 0.55f
            StrokeTexture.WASH -> 0.35f
            StrokeTexture.GRAIN -> 0.80f
        },
        trailOverlay = base.texture == StrokeTexture.TAPERED,
        trailDots = if (base.texture == StrokeTexture.GRAIN) {
            listOf(p(0.30f, 0.78f), p(0.72f, 0.76f))
        } else {
            emptyList()
        },
    )
}

/** Scales a normalized point into a pixel box; tolerates zero/negative sizes. */
internal fun scaleIllustrationPoint(
    point: IllustrationPoint,
    width: Float,
    height: Float,
): IllustrationPoint {
    val safeW = width.coerceAtLeast(0f)
    val safeH = height.coerceAtLeast(0f)
    val side = minOf(safeW, safeH)
    val ox = (safeW - side) / 2f
    val oy = (safeH - side) / 2f
    return IllustrationPoint(ox + point.x * side, oy + point.y * side)
}

/** Reuses the shared TalkBack contract byte-for-byte. */
internal fun penIllustrationTalkBackLabel(style: PenStyle, selected: Boolean): String =
    penStyleTalkBackLabel(style, selected)
