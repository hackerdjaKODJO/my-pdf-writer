package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.MaskingCard
import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import kotlin.math.roundToInt

private val MaskTapeStrip = Color(0x558CC49A)
private val MaskHiddenBg = Color(0xFFFFF3CD)
private val MaskHiddenEdge = Color(0xFFC79A2E)
private val MaskRevealedBg = Color(0xFFDDEBF7)
private val MaskRevealedEdge = Color(0xFF42779C)
private val MaskCorrectBg = Color(0xFFDDF3E0)
private val MaskCorrectEdge = Color(0xFF3A8A4E)
private val MaskWrongBg = Color(0xFFFBE1DE)
private val MaskWrongEdge = Color(0xFFB44A40)

/**
 * Small, interactive tape overlays drawn on top of [NoteCanvas].
 *
 * The composable lives inside NoteCanvas' BoxWithConstraints and receives the exact
 * pan/zoom transform used for the paper, so every card stays pinned to its normalized
 * page rectangle while the user draws, pans or zooms. It renders nothing unless the
 * editor enables it and the current page actually has cards; switching it off removes
 * every overlay node, returning the full canvas to the pen tools.
 */
@Composable
internal fun MaskingTapeLayer(
    cards: List<MaskingCard>,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    onReveal: (String) -> Unit,
    onMark: (String, Boolean) -> Unit,
) {
    if (cards.isEmpty() || fit <= 0f) return
    val density = LocalDensity.current
    val minWidthPx = with(density) { 92.dp.toPx() }
    val minHeightPx = with(density) { 42.dp.toPx() }

    cards.forEach { card ->
        val left = viewWidth / 2f + pan.x + (card.x * pageWidth - pageWidth / 2f) * fit
        val top = viewHeight / 2f + pan.y + (card.y * pageHeight - pageHeight / 2f) * fit
        val width = (card.w * pageWidth * fit).coerceAtLeast(minWidthPx)
        val height = (card.h * pageHeight * fit).coerceAtLeast(minHeightPx)
        val widthDp = (width / density.density).dp
        val heightDp = (height / density.density).dp
        val (background, edge) = maskTapeColors(card.state)

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(widthDp, heightDp)
                .drawBehind {
                    // Two pale strips sell the "masking tape" idea without any asset.
                    val stripW = size.width * 0.2f
                    val stripH = size.height * 0.2f
                    drawRect(
                        color = MaskTapeStrip,
                        topLeft = Offset(-stripW * 0.25f, -stripH * 0.25f),
                        size = Size(stripW, stripH),
                    )
                    drawRect(
                        color = MaskTapeStrip,
                        topLeft = Offset(size.width - stripW * 0.75f, size.height - stripH * 0.75f),
                        size = Size(stripW, stripH),
                    )
                }
                .clip(RoundedCornerShape(3.dp))
                .background(background)
                .border(1.dp, edge, RoundedCornerShape(3.dp))
                .clickable { onReveal(card.id) }
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            if (card.state == MaskingCardState.HIDDEN) {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        text = card.prompt.ifBlank { "질문" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "탭하여 정답",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = card.answer.ifBlank { "정답 없음" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    MaskMarkButton(correct = true, edge = edge) { onMark(card.id, true) }
                    MaskMarkButton(correct = false, edge = edge) { onMark(card.id, false) }
                }
            }
        }
    }
}

@Composable
private fun MaskMarkButton(
    correct: Boolean,
    edge: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(edge.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (correct) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (correct) "정답" else "오답",
            tint = edge,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun maskTapeColors(state: MaskingCardState): Pair<Color, Color> = when (state) {
    MaskingCardState.HIDDEN -> MaskHiddenBg to MaskHiddenEdge
    MaskingCardState.REVEALED -> MaskRevealedBg to MaskRevealedEdge
    MaskingCardState.CORRECT -> MaskCorrectBg to MaskCorrectEdge
    MaskingCardState.WRONG -> MaskWrongBg to MaskWrongEdge
}
