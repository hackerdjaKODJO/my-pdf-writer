package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PenStyle

/**
 * A compact, original graphic for one pen type: a drawn nib silhouette plus a
 * sample stroke showing that nib's character. Shown in both the PDF and note
 * style sheets so a student can see what they are selecting.
 */
@Composable
internal fun PenStyleGlyph(
    style: PenStyle,
    selected: Boolean,
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = penStyleGlyphSpec(style)
    val ink = Color(color)
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Canvas(Modifier.size(width = 26.dp, height = 20.dp)) {
                val w = size.width
                val h = size.height
                when (spec.nib) {
                    PenNibKind.BALL -> {
                        drawLine(
                            foreground, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.42f),
                            strokeWidth = 3f, cap = StrokeCap.Round,
                        )
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.34f, h * 0.40f)
                                lineTo(w * 0.66f, h * 0.40f)
                                lineTo(w * 0.5f, h * 0.78f)
                                close()
                            },
                            foreground,
                        )
                        drawCircle(ink, radius = 2.4f, center = Offset(w * 0.5f, h * 0.82f))
                    }
                    PenNibKind.SPLIT -> {
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.28f, h * 0.12f)
                                lineTo(w * 0.72f, h * 0.12f)
                                lineTo(w * 0.5f, h * 0.86f)
                                close()
                            },
                            foreground,
                        )
                        drawCircle(
                            surfaceColor,
                            radius = 2f,
                            center = Offset(w * 0.5f, h * 0.42f),
                        )
                    }
                    PenNibKind.BRISTLE -> {
                        drawRect(foreground, Offset(w * 0.32f, h * 0.10f), Size(w * 0.36f, h * 0.20f))
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.32f, h * 0.30f)
                                quadraticBezierTo(w * 0.36f, h * 0.70f, w * 0.5f, h * 0.86f)
                                quadraticBezierTo(w * 0.64f, h * 0.70f, w * 0.68f, h * 0.30f)
                                close()
                            },
                            foreground.copy(alpha = 0.7f),
                        )
                    }
                    PenNibKind.GRAPHITE -> {
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.30f, h * 0.26f)
                                lineTo(w * 0.70f, h * 0.26f)
                                lineTo(w * 0.5f, h * 0.82f)
                                close()
                            },
                            Color(0xFFD9B98A),
                        )
                        drawPath(
                            Path().apply {
                                moveTo(w * 0.42f, h * 0.62f)
                                lineTo(w * 0.58f, h * 0.62f)
                                lineTo(w * 0.5f, h * 0.88f)
                                close()
                            },
                            ink,
                        )
                    }
                }
                // Sample stroke under the nib: a gentle pressure ramp exercises
                // the visual character of each style (display only).
                val strokePath = Path().apply {
                    moveTo(2f, h - 3f)
                    quadraticBezierTo(w * 0.5f, h * 0.55f, w - 2f, h - 5f)
                }
                when (spec.texture) {
                    StrokeTexture.EVEN -> drawPath(
                        strokePath, ink, style = Stroke(width = 2.2f, cap = StrokeCap.Round),
                    )
                    StrokeTexture.TAPERED -> {
                        drawPath(strokePath, ink, alpha = 0.5f,
                            style = Stroke(width = 3.4f, cap = StrokeCap.Round))
                        drawLine(ink, Offset(w * 0.5f, h * 0.7f), Offset(w - 2f, h - 5f),
                            strokeWidth = 1.2f, cap = StrokeCap.Round)
                    }
                    StrokeTexture.WASH -> drawPath(
                        strokePath, ink, alpha = 0.35f,
                        style = Stroke(width = 5f, cap = StrokeCap.Round),
                    )
                    StrokeTexture.GRAIN -> {
                        drawPath(strokePath, ink, alpha = 0.7f,
                            style = Stroke(width = 1.8f, cap = StrokeCap.Round))
                        drawCircle(ink, radius = 1f, center = Offset(w * 0.28f, h * 0.8f), alpha = 0.5f)
                        drawCircle(ink, radius = 1f, center = Offset(w * 0.72f, h * 0.72f), alpha = 0.5f)
                    }
                }
            }
        },
        label = {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier
            .heightIn(min = MIN_TARGET_DP.dp)
            .semantics { penStyleDescription(style, selected) },
    )
}
