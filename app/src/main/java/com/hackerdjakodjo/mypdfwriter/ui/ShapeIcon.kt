package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Tool

/** Shared, resolution-independent shape glyphs for note and PDF tool pickers. */
@Composable
internal fun ShapeIcon(
    tool: Tool,
    modifier: Modifier = Modifier.size(24.dp),
    tint: Color = LocalContentColor.current,
) {
    val kind = shapeKindOrNull(tool) ?: return
    Canvas(modifier) {
        // Geometry is authored in a 24 dp viewport and scales with the actual
        // slot, including 17–22 dp compact dock/context icons.
        val unit = size.minDimension / 24f
        val stroke = Stroke(
            width = 1.8f * unit,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (kind) {
            ShapeKind.RECT -> drawRoundRect(
                color = tint,
                topLeft = Offset(4f * unit, 5f * unit),
                size = Size(16f * unit, 14f * unit),
                cornerRadius = CornerRadius(2f * unit),
                style = stroke,
            )
            ShapeKind.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(12f * unit, 4f * unit)
                    lineTo(20f * unit, 19f * unit)
                    lineTo(4f * unit, 19f * unit)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }
            ShapeKind.ELLIPSE -> drawOval(
                color = tint,
                topLeft = Offset(4f * unit, 6f * unit),
                size = Size(16f * unit, 12f * unit),
                style = stroke,
            )
            ShapeKind.LINE -> drawLine(
                color = tint,
                start = Offset(5f * unit, 19f * unit),
                end = Offset(19f * unit, 5f * unit),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            ShapeKind.ARROW -> {
                drawLine(
                    color = tint,
                    start = Offset(5f * unit, 19f * unit),
                    end = Offset(19f * unit, 5f * unit),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                val head = Path().apply {
                    moveTo(11f * unit, 5f * unit)
                    lineTo(19f * unit, 5f * unit)
                    lineTo(19f * unit, 13f * unit)
                }
                drawPath(head, tint, style = stroke)
            }
        }
    }
}
