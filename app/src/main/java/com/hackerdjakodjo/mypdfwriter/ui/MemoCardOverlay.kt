package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.MemoCard
import com.hackerdjakodjo.mypdfwriter.data.MemoTint
import kotlin.math.roundToInt

/** Compose-side views of the portable ARGB integers in [MemoTint]. */
private val MemoTint.bg: Color get() = Color(background)
private val MemoTint.line: Color get() = Color(edge)
private val MemoTint.inkColor: Color get() = Color(ink)

/**
 * Screen-space rectangle of one memo card, using the exact same page transform as
 * the checklist and masking overlays in [NoteCanvas]. Pure and JVM-tested so the
 * pen-input gate and the drawn card can never disagree about where a card is.
 */
fun memoScreenRect(
    card: MemoCard,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    minWidthPx: Float = 0f,
    minHeightPx: Float = 0f,
): Rect {
    val left = viewWidth / 2f + pan.x + (card.x * pageWidth - pageWidth / 2f) * fit
    val top = viewHeight / 2f + pan.y + (card.y * pageHeight - pageHeight / 2f) * fit
    val width = (card.w * pageWidth * fit).coerceAtLeast(minWidthPx)
    val height = (card.h * pageHeight * fit).coerceAtLeast(minHeightPx)
    return Rect(
        left = left,
        top = top,
        right = left + width,
        bottom = top + height,
    )
}

/** Converts a finished drag into a new normalized position. Pure. */
fun memoMovedCard(
    card: MemoCard,
    delta: Offset,
    fit: Float,
    pageWidth: Float,
    pageHeight: Float,
): MemoCard {
    val scale = fit.coerceAtLeast(0.0001f)
    return card.copy(
        x = card.x + delta.x / (pageWidth * scale),
        y = card.y + delta.y / (pageHeight * scale),
    ).normalized()
}

/** Converts a finished resize drag into a new normalized size. Pure. */
fun memoResizedCard(
    card: MemoCard,
    delta: Offset,
    fit: Float,
    pageWidth: Float,
    pageHeight: Float,
): MemoCard {
    val scale = fit.coerceAtLeast(0.0001f)
    return card.copy(
        w = card.w + delta.x / (pageWidth * scale),
        h = card.h + delta.y / (pageHeight * scale),
    ).normalized()
}

/**
 * Post-it overlays drawn above [NoteCanvas].
 *
 * Cards live inside the canvas' BoxWithConstraints and receive its exact pan/zoom
 * transform, so they stay pinned to their page rectangles. The composable renders
 * nothing when the page has no cards or the editor switched the overlay off; in
 * that case it adds no touch targets and the pen keeps the whole canvas.
 */
@Composable
internal fun MemoCardLayer(
    cards: List<MemoCard>,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    minWidthPx: Float,
    minHeightPx: Float,
    onOpen: (String) -> Unit,
    onCardChange: (MemoCard) -> Unit,
    onHide: () -> Unit,
    onDelete: (String) -> Unit,
) {
    if (cards.isEmpty() || fit <= 0f) return
    cards.forEach { card ->
        MemoCardView(
            card = card,
            fit = fit,
            pan = pan,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
            minWidthPx = minWidthPx,
            minHeightPx = minHeightPx,
            onOpen = onOpen,
            onCardChange = onCardChange,
            onHide = onHide,
            onDelete = onDelete,
        )
    }
}

/**
 * A single card. Owns a one-shot pointer listener for its top strip and resize
 * handle: only the accumulated drag is committed at the end, so a move or resize
 * is exactly one notebook undo step. All other touches on the card body are
 * clicks, consumed by Compose so they cannot leak into the pen canvas.
 */
@Composable
private fun MemoCardView(
    card: MemoCard,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    minWidthPx: Float,
    minHeightPx: Float,
    onOpen: (String) -> Unit,
    onCardChange: (MemoCard) -> Unit,
    onHide: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val density = LocalDensity.current
    val rect = memoScreenRect(
        card = card,
        fit = fit,
        pan = pan,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
        minWidthPx = minWidthPx,
        minHeightPx = minHeightPx,
    )
    val widthDp = (rect.width / density.density).dp
    val heightDp = (rect.height / density.density).dp
    val shape = RoundedCornerShape(10.dp)
    var menuOpen by remember(card.id) { mutableStateOf(false) }

    // Keep the drag callbacks current without restarting the pointer coroutine on
    // every parent recomposition.
    val currentChange by rememberUpdatedState(onCardChange)
    val commit: (MemoCard) -> Unit = remember {
        { updated -> currentChange(updated.copy(updatedAt = System.currentTimeMillis())) }
    }
    val moveModifier = memoDragModifier(key = "memo-move-${card.id}") { delta ->
        commit(memoMovedCard(card, delta, fit, pageWidth, pageHeight))
    }
    val resizeModifier = memoDragModifier(key = "memo-resize-${card.id}") { delta ->
        commit(memoResizedCard(card, delta, fit, pageWidth, pageHeight))
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(widthDp, heightDp)
            .graphicsLayer(rotationZ = card.tilt)
            .clip(shape)
            .drawBehind {
                // Soft, asset-free paper shadow. The offset sliver stays visible
                // outside the clipped card.
                drawRoundRect(
                    color = Color(0x22000000),
                    topLeft = Offset(3f, 4f),
                    size = androidx.compose.ui.geometry.Size(size.width - 3f, size.height - 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
            }
            .background(card.tint.bg)
            .border(1.dp, card.tint.line, shape)
            .testTag("memo_card_${card.id}"),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(card.tint.line.copy(alpha = 0.14f))
                    .then(moveModifier)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.labelSmall,
                    color = card.tint.line,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onHide)
                        .testTag("memo_card_hide_${card.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = "모든 메모 카드 숨기기",
                        tint = card.tint.line,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Box {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { menuOpen = true }
                            .testTag("memo_card_more_${card.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "메모 카드 메뉴",
                            tint = card.tint.line,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MemoTint.entries.forEach { tint ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(tint.bg)
                                                .border(1.dp, tint.line, RoundedCornerShape(3.dp)),
                                        )
                                        Spacer(Modifier.size(8.dp))
                                        Text("배경 ${tint.label}")
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    commit(card.copy(tint = tint))
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("내용 수정") },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onOpen(card.id)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("카드 삭제") },
                            leadingIcon = {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuOpen = false
                                onDelete(card.id)
                            },
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpen(card.id) }
                        .testTag("memo_card_body_${card.id}")
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = card.text.ifBlank { "빈 메모 · 탭하여 입력" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (card.text.isBlank()) {
                            card.tint.line
                        } else {
                            card.tint.inkColor
                        },
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Folded-paper corner accent. Drawn as a child so it sits above
                // the tint fill without any image asset.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .drawBehind {
                            val fold = Path().apply {
                                moveTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                lineTo(0f, 0f)
                                close()
                            }
                            drawPath(fold, color = card.tint.line.copy(alpha = 0.28f))
                        },
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .then(resizeModifier)
                        .drawBehind {
                            val accent = card.tint.line
                            for (offset in listOf(2f, 6f, 10f)) {
                                drawLine(
                                    color = accent,
                                    start = Offset(size.width - offset - 3f, size.height - 2f),
                                    end = Offset(size.width - 2f, size.height - offset - 3f),
                                    strokeWidth = 1.5f,
                                )
                            }
                        }
                        .testTag("memo_card_resize_${card.id}"),
                )
            }
        }
    }
}

/**
 * One pointer listener per drag target. The down is consumed immediately (so a
 * card cannot also be interpreted by the body clickable or by the pen canvas),
 * intermediate moves are consumed, and only the final accumulated delta is
 * reported to the caller.
 */
@Composable
private fun memoDragModifier(
    key: Any,
    onDragEnd: (Offset) -> Unit,
): Modifier {
    val currentEnd by rememberUpdatedState(onDragEnd)
    return Modifier.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val pointerId = down.id
            var previous = down.position
            var total = Offset.Zero
            var cancelled = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed) {
                    change?.consume()
                    break
                }
                change.consume()
                total += change.position - previous
                previous = change.position
                if (event.changes.any { it.id != pointerId && it.pressed }) cancelled = true
            }
            if (!cancelled && total.getDistance() > viewConfiguration.touchSlop) {
                currentEnd(total)
            }
        }
    }
}
