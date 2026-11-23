package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.ChecklistCard
import com.hackerdjakodjo.mypdfwriter.data.Checklists
import kotlin.math.roundToInt

private val ChecklistBackground = Color(0xF4F8FBFF)
private val ChecklistEdge = Color(0xFF55789A)
private val ChecklistAccent = Color(0xFF3A7D5D)
private val ChecklistDoneBackground = Color(0xFF3A7D5D)
private const val OVERLAY_MAX_ITEMS = 5

/**
 * Small interactive checklist cards drawn on top of [NoteCanvas].
 *
 * The composable lives inside NoteCanvas' BoxWithConstraints and receives the exact
 * pan/zoom transform used for the paper, so every card stays pinned to its normalized
 * page rectangle while the user draws, pans or zooms. It renders nothing unless the
 * editor enables it and the current page actually has checklists; switching it off
 * removes every overlay node and touch target, returning the canvas to the pen.
 */
@Composable
internal fun ChecklistOverlayLayer(
    cards: List<ChecklistCard>,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    onToggleItem: (String, String) -> Unit,
    onHide: () -> Unit,
) {
    if (cards.isEmpty() || fit <= 0f) return
    val density = LocalDensity.current
    val minWidthPx = with(density) { 150.dp.toPx() }

    cards.forEach { card ->
        val visibleItems = card.items.take(OVERLAY_MAX_ITEMS)
        val minHeightPx = with(density) {
            (46.dp + 24.dp * visibleItems.size.coerceAtLeast(1)).toPx()
        }
        val left = viewWidth / 2f + pan.x + (card.x * pageWidth - pageWidth / 2f) * fit
        val top = viewHeight / 2f + pan.y + (card.y * pageHeight - pageHeight / 2f) * fit
        val widthPx = (card.w * pageWidth * fit).coerceAtLeast(minWidthPx)
        val heightPx = (card.h * pageHeight * fit).coerceAtLeast(minHeightPx)
        val widthDp = (widthPx / density.density).dp
        val heightDp = (heightPx / density.density).dp
        val progress = Checklists.progress(card)

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(widthDp, heightDp)
                .clip(RoundedCornerShape(10.dp))
                .background(ChecklistBackground)
                .border(1.dp, ChecklistEdge, RoundedCornerShape(10.dp))
                // Consume taps on the card body so they never start a pen stroke.
                .clickable { }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${progress.checked}/${progress.total} · ${progress.percent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChecklistAccent,
                    )
                    Box(
                        modifier = Modifier
                            .size(MIN_TARGET_DP.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .clickable(onClick = onHide),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "체크리스트 숨기기",
                            tint = ChecklistEdge,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    color = ChecklistAccent,
                    trackColor = ChecklistEdge.copy(alpha = 0.18f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
                Spacer(Modifier.height(4.dp))
                visibleItems.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MIN_TARGET_DP.dp)
                            .clickable { onToggleItem(card.id, item.id) }
                            .semantics {
                                contentDescription = item.text
                                stateDescription = if (item.done) "완료" else "미완료"
                            }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (item.done) ChecklistDoneBackground
                                    else Color.Transparent,
                                )
                                .border(1.dp, ChecklistEdge, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.done) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "완료",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = item.text.ifBlank { "(항목)" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (card.items.size > OVERLAY_MAX_ITEMS) {
                    Text(
                        text = "+${card.items.size - OVERLAY_MAX_ITEMS}개 더 · 전체 목록은 체크리스트 메뉴",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
