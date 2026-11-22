package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private const val CHECK_DEFAULT_X = 0.30f
private const val CHECK_DEFAULT_Y = 0.12f
private const val CHECK_DEFAULT_W = 0.42f
private const val CHECK_DEFAULT_H = 0.28f
private const val CHECK_MIN_W = 0.16f
private const val CHECK_MIN_H = 0.10f

/** One row of an offline checklist. [id] is stable across edits and JSON round trips. */
data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val done: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("done", done)
    }

    companion object {
        fun fromJson(o: JSONObject): ChecklistItem = ChecklistItem(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            text = o.optString("text"),
            done = o.optBoolean("done", false),
        )
    }
}

/**
 * An original, offline interactive checklist pinned to a notebook page.
 *
 * Only user text, a normalized 0..1 page rectangle and study state are stored: no
 * file paths, URIs or device identifiers, so the JSON stays portable in local
 * backups. The rectangle follows the same A4 page box as [Annotation] and
 * [MaskingCard]; the on-page overlay clamps it to a tappable minimum size.
 */
data class ChecklistCard(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val x: Float = CHECK_DEFAULT_X,
    val y: Float = CHECK_DEFAULT_Y,
    val w: Float = CHECK_DEFAULT_W,
    val h: Float = CHECK_DEFAULT_H,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /**
     * Repairs hand-edited or legacy JSON: fresh ids for blank/duplicate ids, trimmed
     * and length-capped text, finite/clamped geometry and a positive timestamp.
     * Never mutates the receiver.
     */
    fun normalized(): ChecklistCard {
        val usedIds = HashSet<String>()
        val safeItems = items.take(Checklists.MAX_ITEMS).map { item ->
            var id = item.id.trim()
            if (id.isBlank() || !usedIds.add(id)) {
                id = UUID.randomUUID().toString()
                usedIds.add(id)
            }
            item.copy(
                id = id,
                text = item.text.trim().take(Checklists.MAX_ITEM_TEXT),
                done = item.done,
            )
        }
        var safeId = id.trim()
        if (safeId.isBlank()) safeId = UUID.randomUUID().toString()
        val safeW = finite(w, CHECK_DEFAULT_W).coerceIn(CHECK_MIN_W, 1f)
        val safeH = finite(h, CHECK_DEFAULT_H).coerceIn(CHECK_MIN_H, 1f)
        val (safeX, safeY) = clampCardOrigin(
            finite(x, CHECK_DEFAULT_X), finite(y, CHECK_DEFAULT_Y), safeW, safeH,
        )
        return copy(
            id = safeId,
            title = title.trim().take(Checklists.MAX_TITLE_TEXT).ifBlank { Checklists.DEFAULT_TITLE },
            items = safeItems,
            x = safeX,
            y = safeY,
            w = safeW,
            h = safeH,
            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        put("createdAt", createdAt)
    }

    companion object {
        const val DEFAULT_X = CHECK_DEFAULT_X
        const val DEFAULT_Y = CHECK_DEFAULT_Y
        const val DEFAULT_W = CHECK_DEFAULT_W
        const val DEFAULT_H = CHECK_DEFAULT_H
        const val MIN_W = CHECK_MIN_W
        const val MIN_H = CHECK_MIN_H

        fun fromJson(o: JSONObject): ChecklistCard {
            val itemsJson = o.optJSONArray("items") ?: JSONArray()
            val items = ArrayList<ChecklistItem>(itemsJson.length())
            for (i in 0 until itemsJson.length()) {
                itemsJson.optJSONObject(i)?.let { items.add(ChecklistItem.fromJson(it)) }
            }
            return ChecklistCard(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                title = o.optString("title"),
                items = items,
                x = o.optDouble("x", CHECK_DEFAULT_X.toDouble()).toFloat(),
                y = o.optDouble("y", CHECK_DEFAULT_Y.toDouble()).toFloat(),
                w = o.optDouble("w", CHECK_DEFAULT_W.toDouble()).toFloat(),
                h = o.optDouble("h", CHECK_DEFAULT_H.toDouble()).toFloat(),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            ).normalized()
        }

        private fun finite(value: Float, fallback: Float): Float =
            if (value.isFinite()) value else fallback
    }
}

/** Checked/total counts for one checklist or a whole notebook. */
data class ChecklistProgress(val checked: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else checked.toFloat() / total
    val percent: Int get() = (fraction * 100f).roundToInt()
}

/**
 * Pure checklist state transitions and progress calculations.
 *
 * Android/Compose free so the study loop has JVM coverage. Every function returns a
 * new card; the editor commits one returned value as a single notebook undo step.
 */
object Checklists {
    const val DEFAULT_TITLE = "체크리스트"
    const val MAX_ITEMS = 40
    const val MAX_ITEM_TEXT = 200
    const val MAX_TITLE_TEXT = 80

    /** Immutable checkbox toggle; an unknown item id is a no-op. */
    fun toggle(card: ChecklistCard, itemId: String): ChecklistCard {
        var found = false
        val items = card.items.map { item ->
            if (item.id == itemId) {
                found = true
                item.copy(done = !item.done)
            } else {
                item
            }
        }
        return if (found) card.copy(items = items) else card
    }

    fun addItem(card: ChecklistCard, text: String = ""): ChecklistCard =
        if (card.items.size >= MAX_ITEMS) {
            card
        } else {
            card.copy(
                items = card.items + ChecklistItem(
                    text = text.trim().take(MAX_ITEM_TEXT),
                ),
            )
        }

    fun renameItem(card: ChecklistCard, itemId: String, text: String): ChecklistCard =
        card.copy(
            items = card.items.map { item ->
                if (item.id == itemId) {
                    item.copy(text = text.trim().take(MAX_ITEM_TEXT))
                } else {
                    item
                }
            },
        )

    fun removeItem(card: ChecklistCard, itemId: String): ChecklistCard =
        card.copy(items = card.items.filterNot { it.id == itemId })

    fun progress(card: ChecklistCard): ChecklistProgress =
        progress(listOf(card))

    /** Aggregate progress across every checklist, independent of page order. */
    fun progress(cards: Collection<ChecklistCard>): ChecklistProgress {
        var checked = 0
        var total = 0
        cards.forEach { card ->
            card.items.forEach { item ->
                total++
                if (item.done) checked++
            }
        }
        return ChecklistProgress(checked = checked, total = total)
    }

    /** Fresh card and item ids so a duplicated page owns an independent checklist. */
    fun freshIds(cards: List<ChecklistCard>): List<ChecklistCard> =
        cards.map { card ->
            card.copy(
                id = UUID.randomUUID().toString(),
                items = card.items.map { item -> item.copy(id = UUID.randomUUID().toString()) },
            )
        }

    /** Short Korean summary shared by the manager sheet and the on-page card. */
    fun progressText(value: ChecklistProgress): String = if (value.total == 0) {
        "항목 없음 · 0%"
    } else {
        "완료 ${value.checked}/${value.total} · ${value.percent}%"
    }
}
