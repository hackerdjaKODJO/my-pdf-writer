package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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

/** Compose views of the original flat post-it ARGB colours in [MemoTint]. */
private val MemoTint.pdfBg: Color get() = Color(background)
private val MemoTint.pdfLine: Color get() = Color(edge)
private val MemoTint.pdfInk: Color get() = Color(ink)

// ---------------------------------------------------------------- pure geometry
// These thin wrappers reuse the note-side pure helpers so the PDF pen-input gate and
// the drawn card can never disagree about a card's rectangle. They are kept here as
// named, JVM-testable functions for the PDF layer.

internal fun pdfMemoScreenRect(
    card: MemoCard,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
    minWidthPx: Float = 0f,
    minHeightPx: Float = 0f,
): Rect = memoScreenRect(
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

internal fun pdfMemoMovedCard(
    card: MemoCard,
    delta: Offset,
    fit: Float,
    pageWidth: Float,
    pageHeight: Float,
): MemoCard = memoMovedCard(card, delta, fit, pageWidth, pageHeight)

internal fun pdfMemoResizedCard(
    card: MemoCard,
    delta: Offset,
    fit: Float,
    pageWidth: Float,
    pageHeight: Float,
): MemoCard = memoResizedCard(card, delta, fit, pageWidth, pageHeight)

internal fun pdfMemoCardsForPage(cards: List<MemoCard>, page: Int): List<MemoCard> =
    memoCardsOnPage(cards, page)

internal fun pdfMemoPlacementLabel(card: MemoCard): String =
    "X ${(card.x * 100).roundToInt()}% · Y ${(card.y * 100).roundToInt()}% · " +
        "W ${(card.w * 100).roundToInt()}% · H ${(card.h * 100).roundToInt()}%"

/** Parses an X/Y/W/H percent field; a blank or malformed field keeps its old value. */
internal fun pdfMemoPercent(text: String, fallback: Float): Float =
    text.trim().toFloatOrNull()?.div(100f) ?: fallback

// -------------------------------------------------------------------- overlay

/**
 * Post-it cards drawn above the PDF canvas, sharing its exact pan/zoom transform.
 *
 * The full-size parent has no pointer input, so pen strokes outside a card pass
 * straight through to the canvas. Only each card's own small box consumes touches;
 * its top strip drags, body taps open the editor and the bottom-right grip resizes.
 */
@Composable
internal fun PdfMemoCardLayer(
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
    onDragActiveChange: (Boolean) -> Unit = {},
) {
    if (cards.isEmpty() || fit <= 0f) return
    Box(Modifier.fillMaxSize().clipToBounds()) {
        cards.forEach { card ->
            PdfMemoCardView(
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
                onDragActiveChange = onDragActiveChange,
            )
        }
    }
}

/** A single post-it. A drag reports only its accumulated delta at gesture end. */
@Composable
private fun PdfMemoCardView(
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
    onDragActiveChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val rect = pdfMemoScreenRect(
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

    val currentChange by rememberUpdatedState(onCardChange)
    val commit: (MemoCard) -> Unit = remember {
        { updated -> currentChange(updated.copy(updatedAt = System.currentTimeMillis())) }
    }
    val moveModifier = pdfMemoDragModifier(key = "pdf-memo-move-${card.id}", onDragActiveChange) { delta ->
        commit(pdfMemoMovedCard(card, delta, fit, pageWidth, pageHeight))
    }
    val resizeModifier = pdfMemoDragModifier(key = "pdf-memo-resize-${card.id}", onDragActiveChange) { delta ->
        commit(pdfMemoResizedCard(card, delta, fit, pageWidth, pageHeight))
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(widthDp, heightDp)
            .graphicsLayer(rotationZ = card.tilt)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = Color(0x22000000),
                    topLeft = Offset(3f, 4f),
                    size = androidx.compose.ui.geometry.Size(size.width - 3f, size.height - 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                )
            }
            .background(card.tint.pdfBg)
            .border(1.dp, card.tint.pdfLine, shape)
            .testTag("pdf_memo_card_${card.id}"),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Drag strip. A one-tap close affordance makes cards quick to dismiss.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .background(card.tint.pdfLine.copy(alpha = 0.14f))
                    .then(moveModifier)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.labelSmall,
                    color = card.tint.pdfLine,
                    modifier = Modifier.padding(start = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { onDelete(card.id) },
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("pdf_memo_card_delete_${card.id}"),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "메모 카드 삭제",
                        tint = card.tint.pdfLine,
                        modifier = Modifier.size(13.dp),
                    )
                }
                IconButton(
                    onClick = onHide,
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("pdf_memo_card_close_${card.id}"),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "페이지의 메모 카드 숨기기",
                        tint = card.tint.pdfLine,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onOpen(card.id) }
                        .testTag("pdf_memo_card_body_${card.id}")
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = card.text.ifBlank { "빈 메모 · 탭하여 입력" },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (card.text.isBlank()) card.tint.pdfLine else card.tint.pdfInk,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Folded-paper corner accent, drawn without any image asset.
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
                            drawPath(fold, color = card.tint.pdfLine.copy(alpha = 0.28f))
                        },
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .then(resizeModifier)
                        .drawBehind {
                            val accent = card.tint.pdfLine
                            for (offset in listOf(3f, 7f, 11f)) {
                                drawLine(
                                    color = accent,
                                    start = Offset(size.width - offset - 3f, size.height - 2f),
                                    end = Offset(size.width - 2f, size.height - offset - 3f),
                                    strokeWidth = 1.5f,
                                )
                            }
                        }
                        .testTag("pdf_memo_card_resize_${card.id}"),
                )
            }
        }
    }
}

/**
 * One listener per drag target. The down and all moves are consumed so neither the
 * body click nor the canvas gesture can also claim the pointer; only the final
 * accumulated delta is reported, making one drag exactly one viewer undo step.
 */
@Composable
private fun pdfMemoDragModifier(
    key: Any,
    onDragActiveChange: (Boolean) -> Unit,
    onDragEnd: (Offset) -> Unit,
): Modifier {
    val currentEnd by rememberUpdatedState(onDragEnd)
    val currentActive by rememberUpdatedState(onDragActiveChange)
    return Modifier.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            currentActive(true)
            val pointerId = down.id
            var previous = down.position
            var total = Offset.Zero
            var cancelled = false
            try {
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
            } finally {
                currentActive(false)
            }
        }
    }
}

// ------------------------------------------------------------------ manager

internal data class PdfMemoPageCard(val pageIndex: Int, val card: MemoCard)

/** Page-ordered snapshot of every PDF memo card. Pure so the sheet list is testable. */
internal fun pdfMemoCardEntries(cards: List<MemoCard>): List<PdfMemoPageCard> =
    cards.sortedWith(compareBy({ it.page }, { it.createdAt }, { it.id }))
        .map { PdfMemoPageCard(it.page, it) }

private data class PdfMemoEditorTarget(val card: MemoCard?)

/**
 * Manager sheet for PDF post-it cards: current-page highlight, add/edit/delete,
 * an on-page visibility toggle and a jump to the card's page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfMemoCardsSheet(
    cards: List<MemoCard>,
    currentPage: Int,
    pageCount: Int,
    showOnPage: Boolean,
    onToggleShowOnPage: (Boolean) -> Unit,
    onCardChange: (MemoCard) -> Unit,
    onDeleteCard: (String) -> Unit,
    onJump: (Int) -> Unit,
    editCardId: String? = null,
    onEditHandled: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = remember(cards) { pdfMemoCardEntries(cards) }
    var editorTarget by remember { mutableStateOf<PdfMemoEditorTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<PdfMemoPageCard?>(null) }

    // Tapping a card on the canvas opens that card directly in the sheet.
    LaunchedEffect(editCardId, cards) {
        if (editCardId != null && editorTarget == null && pendingDelete == null) {
            entries.firstOrNull { it.card.id == editCardId }?.let { entry ->
                editorTarget = PdfMemoEditorTarget(entry.card)
            }
            onEditHandled()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("메모 카드", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "PDF 페이지에 붙여 둔 짧은 메모를 한곳에서 관리",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("페이지에 카드 표시", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (showOnPage) "카드가 있는 페이지에 메모를 겹쳐 표시" else "카드 숨김 · 필기 도구가 페이지를 완전히 사용",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showOnPage, onCheckedChange = onToggleShowOnPage)
                }
            }

            item {
                Button(
                    onClick = { editorTarget = PdfMemoEditorTarget(MemoCard(page = currentPage)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("현재 페이지에 카드 추가")
                }
            }

            item {
                Text(
                    "카드는 PDF 문서 JSON과 로컬 백업에 저장됩니다. 페이지에서 숨기기는 보기 전용입니다. " +
                        "PDF 내보내기에는 아직 카드가 포함되지 않습니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "아직 카드가 없습니다. 위 버튼으로 첫 메모를 붙여 보세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            itemsIndexed(entries, key = { _, entry -> entry.card.id }) { _, entry ->
                PdfMemoCardRow(
                    entry = entry,
                    current = entry.pageIndex == currentPage,
                    onJump = { onJump(entry.pageIndex) },
                    onEdit = { editorTarget = PdfMemoEditorTarget(entry.card) },
                    onDelete = { pendingDelete = entry },
                )
            }
        }
    }

    editorTarget?.let { target ->
        PdfMemoEditorDialog(
            initial = target.card,
            pageCount = pageCount,
            fallbackPage = currentPage,
            onDismiss = { editorTarget = null },
            onSave = { saved ->
                onCardChange(saved)
                editorTarget = null
            },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("메모 카드 삭제") },
            text = { Text("이 카드의 메모를 삭제합니다. 실행 취소로 되돌릴 수 있습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCard(entry.card.id)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun PdfMemoCardRow(
    entry: PdfMemoPageCard,
    current: Boolean,
    onJump: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (current) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onJump),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                    .background(entry.card.tint.pdfBg),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.card.text.ifBlank { "(빈 메모)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pdfMemoPlacementLabel(entry.card),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${entry.pageIndex + 1}쪽${if (current) " · 현재" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "메모 수정")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "메모 삭제")
            }
        }
    }
}

@Composable
private fun PdfMemoEditorDialog(
    initial: MemoCard?,
    pageCount: Int,
    fallbackPage: Int,
    onDismiss: () -> Unit,
    onSave: (MemoCard) -> Unit,
) {
    val base = initial ?: MemoCard(page = fallbackPage)
    var text by remember { mutableStateOf(base.text) }
    var tint by remember { mutableStateOf(base.tint) }
    var page by remember { mutableStateOf(base.page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)).toString()) }
    var x by remember { mutableStateOf((base.x * 100).roundToInt().toString()) }
    var y by remember { mutableStateOf((base.y * 100).roundToInt().toString()) }
    var w by remember { mutableStateOf((base.w * 100).roundToInt().toString()) }
    var h by remember { mutableStateOf((base.h * 100).roundToInt().toString()) }
    var tilt by remember { mutableStateOf(base.tilt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "메모 카드 추가" else "메모 카드 수정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= MemoCard.MAX_TEXT_LENGTH) text = value
                    },
                    label = { Text("메모 내용") },
                    supportingText = { Text("${text.length} / ${MemoCard.MAX_TEXT_LENGTH}") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Spacer(Modifier.height(10.dp))
                Text("배경 색상", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MemoTint.entries.forEach { option ->
                        FilterChip(
                            selected = tint == option,
                            onClick = { tint = option },
                            label = { Text(option.label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("카드 위치와 크기 (페이지 크기 대비 %)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PdfMemoPlacementField("X", x, Modifier.weight(1f)) { x = it }
                    PdfMemoPlacementField("Y", y, Modifier.weight(1f)) { y = it }
                    PdfMemoPlacementField("W", w, Modifier.weight(1f)) { w = it }
                    PdfMemoPlacementField("H", h, Modifier.weight(1f)) { h = it }
                }
                Spacer(Modifier.height(8.dp))
                PdfMemoPlacementField(
                    "쪽",
                    page,
                    Modifier.fillMaxWidth(),
                ) { page = it }
                Text(
                    "0~100 사이 값. 비워 두면 이전 값을 사용합니다. 쪽은 1부터 페이지 수까지.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("기울기 ${tilt.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = tilt,
                    valueRange = -MemoCard.MAX_TILT..MemoCard.MAX_TILT,
                    onValueChange = { tilt = it.roundToInt().toFloat() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val pageNumber = page.trim().toIntOrNull() ?: (base.page + 1)
                onSave(
                    base.copy(
                        text = text,
                        tint = tint,
                        page = (pageNumber - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
                        x = pdfMemoPercent(x, base.x),
                        y = pdfMemoPercent(y, base.y),
                        w = pdfMemoPercent(w, base.w),
                        h = pdfMemoPercent(h, base.h),
                        tilt = tilt,
                        updatedAt = System.currentTimeMillis(),
                    ).normalized(),
                )
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun PdfMemoPlacementField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier.height(64.dp),
    )
}
