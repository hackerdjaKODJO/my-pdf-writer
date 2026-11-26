package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.hackerdjakodjo.mypdfwriter.data.PenStyle

/**
 * Original instrument artwork shared by pen selectors, the selected-pen portrait
 * and PDF/note dock slots. Geometry comes from the pure [penIllustrationSpec].
 */
@Composable
internal fun PenInstrumentArtwork(
    style: PenStyle,
    color: Int,
    modifier: Modifier = Modifier,
    bodyColor: Color? = null,
    cutoutColor: Color? = null,
    showSampleStroke: Boolean = true,
) {
    val spec = remember(style) { penIllustrationSpec(style) }
    val ink = Color(color)
    val foreground = bodyColor ?: androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val surfaceColor = cutoutColor ?: androidx.compose.material3.MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val side = minOf(w, h)
        val ox = (w - side) / 2f
        val oy = (h - side) / 2f
        fun x(n: Float) = ox + n * side
        fun y(n: Float) = oy + n * side
        fun r(n: Float) = (n * side).coerceAtLeast(0.5f)

        fun partColor(role: PenPartColor): Color = when (role) {
            PenPartColor.BODY -> foreground
            PenPartColor.BODY_SHADE -> foreground.copy(alpha = 0.72f)
            PenPartColor.METAL -> Color(0xFF9AA0A6)
            PenPartColor.GOLD -> Color(0xFFC9A227)
            PenPartColor.WOOD -> Color(0xFFD9B98A)
            PenPartColor.PENCIL_BODY -> Color(0xFFD9A441)
            PenPartColor.GRAPHITE -> Color(0xFF3A3A3A)
            PenPartColor.ERASER -> Color(0xFFE58A8A)
            PenPartColor.INK -> ink
            PenPartColor.CUTOUT -> surfaceColor
            PenPartColor.HIGHLIGHT -> Color.White.copy(alpha = 0.38f)
        }

        rotate(spec.rotationDeg, pivot = Offset(w / 2f, h / 2f)) {
            spec.parts.forEach { part ->
                when (part) {
                    is PenPart.RoundRect -> drawRoundRect(
                        color = partColor(part.color),
                        topLeft = Offset(x(part.x0), y(part.y0)),
                        size = Size(r(part.x1 - part.x0), r(part.y1 - part.y0)),
                        cornerRadius = CornerRadius(r(part.corner)),
                    )
                    is PenPart.Polygon -> {
                        val points = part.points
                        if (points.size >= 3) {
                            val path = Path().apply {
                                moveTo(x(points.first().x), y(points.first().y))
                                points.drop(1).forEach { lineTo(x(it.x), y(it.y)) }
                                close()
                            }
                            drawPath(path, partColor(part.color))
                        }
                    }
                    is PenPart.Circle -> drawCircle(
                        color = partColor(part.color),
                        radius = r(part.radius),
                        center = Offset(x(part.center.x), y(part.center.y)),
                    )
                    is PenPart.Line -> drawLine(
                        color = partColor(part.color),
                        start = Offset(x(part.from.x), y(part.from.y)),
                        end = Offset(x(part.to.x), y(part.to.y)),
                        strokeWidth = r(part.width),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        if (!showSampleStroke) return@Canvas
        val trail = spec.trail
        if (trail.size >= 2) {
            val trailPath = Path().apply {
                moveTo(x(trail.first().x), y(trail.first().y))
                trail.drop(1).forEach { lineTo(x(it.x), y(it.y)) }
            }
            val strokeWidth = r(spec.trailWidth)
            drawPath(
                trailPath,
                foreground.copy(alpha = 0.18f),
                style = Stroke(width = strokeWidth + 2f, cap = StrokeCap.Round),
            )
            drawPath(
                trailPath,
                ink,
                alpha = spec.trailAlpha,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (spec.trailOverlay) {
                val mid = trail[trail.size / 2]
                val end = trail.last()
                drawLine(
                    ink,
                    Offset(x(mid.x), y(mid.y)),
                    Offset(x(end.x), y(end.y)),
                    strokeWidth = r(0.010f),
                    cap = StrokeCap.Round,
                )
            }
            spec.trailDots.forEach { dot ->
                drawCircle(
                    ink,
                    radius = r(0.010f),
                    center = Offset(x(dot.x), y(dot.y)),
                    alpha = 0.7f,
                )
            }
        }
    }
}
