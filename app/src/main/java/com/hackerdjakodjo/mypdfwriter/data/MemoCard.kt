package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import java.util.UUID

/**
 * Original pastel tints for an offline post-it card.
 *
 * These are our own flat colours with a matching edge and ink shade; they are not
 * sampled from any third-party app. [background] is an ARGB fill, [edge] a thin
 * border/accent and [ink] the default text colour used by the Compose overlay.
 */
enum class MemoTint(
    val label: String,
    val background: Int,
    val edge: Int,
    val ink: Int,
) {
    YELLOW("노랑", 0xFFFFF0B8.toInt(), 0xFFC79A35.toInt(), 0xFF4A3B12.toInt()),
    MINT("민트", 0xFFD6F1E2.toInt(), 0xFF4F9C74.toInt(), 0xFF173D2B.toInt()),
    PINK("분홍", 0xFFFBE0E7.toInt(), 0xFFC4768E.toInt(), 0xFF4E2230.toInt()),
    SKY("하늘", 0xFFDCEBFA.toInt(), 0xFF5C8CB8.toInt(), 0xFF173349.toInt()),
    WHITE("흰색", 0xFFFFFFFF.toInt(), 0xFFA9B6C0.toInt(), 0xFF26313A.toInt()),
}

private const val MEMO_DEFAULT_X = 0.33f
private const val MEMO_DEFAULT_Y = 0.14f
private const val MEMO_DEFAULT_W = 0.34f
private const val MEMO_DEFAULT_H = 0.20f
private const val MEMO_MIN_W = 0.10f
private const val MEMO_MIN_H = 0.08f
private const val MEMO_MAX_TILT = 6f

/**
 * A small, offline post-it note pinned to a notebook page.
 *
 * Only portable data lives here: text, an original tint choice, a normalized page
 * rectangle (0..1, matching [Annotation] coordinates), an optional slight tilt and
 * timestamps. No absolute path, content URI or device identifier is stored, so the
 * JSON travels safely through local backups and duplicated pages.
 */
data class MemoCard(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val tint: MemoTint = MemoTint.YELLOW,
    val x: Float = MEMO_DEFAULT_X,
    val y: Float = MEMO_DEFAULT_Y,
    val w: Float = MEMO_DEFAULT_W,
    val h: Float = MEMO_DEFAULT_H,
    val tilt: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Zero-based document page this card is pinned to. Note pages keep their cards in
     * [NotePage.memoCards] and leave this at the default; the PDF viewer stores all of
     * its cards in one flat `DocState.memoCards` list and uses [page] to place them.
     * Older JSON has no key and loads as page 0.
     */
    val page: Int = 0,
) {
    /**
     * Repairs hand-edited or legacy JSON: drops excess text, replaces non-finite
     * numbers, keeps the whole rectangle on the page and bounds the tilt.
     */
    fun normalized(): MemoCard {
        val safeW = finite(w, MEMO_DEFAULT_W).coerceIn(MEMO_MIN_W, 1f)
        val safeH = finite(h, MEMO_DEFAULT_H).coerceIn(MEMO_MIN_H, 1f)
        val safeCreated = if (createdAt > 0L) createdAt else System.currentTimeMillis()
        return copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            text = text.trim().take(MAX_TEXT_LENGTH),
            x = finite(x, MEMO_DEFAULT_X).coerceIn(0f, 1f - safeW),
            y = finite(y, MEMO_DEFAULT_Y).coerceIn(0f, 1f - safeH),
            w = safeW,
            h = safeH,
            tilt = finite(tilt, 0f).coerceIn(-MEMO_MAX_TILT, MEMO_MAX_TILT),
            createdAt = safeCreated,
            updatedAt = if (updatedAt > 0L) updatedAt else safeCreated,
            page = page.coerceAtLeast(0),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("tint", tint.name)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        put("tilt", tilt.toDouble())
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        // Omitted for note-page cards (page 0) so their JSON shape is unchanged.
        if (page > 0) put("page", page)
    }

    companion object {
        /** Generous enough for a note, short enough to stay readable on a card. */
        const val MAX_TEXT_LENGTH = 600
        const val DEFAULT_X = MEMO_DEFAULT_X
        const val DEFAULT_Y = MEMO_DEFAULT_Y
        const val DEFAULT_W = MEMO_DEFAULT_W
        const val DEFAULT_H = MEMO_DEFAULT_H
        const val MIN_W = MEMO_MIN_W
        const val MIN_H = MEMO_MIN_H
        const val MAX_TILT = MEMO_MAX_TILT

        fun fromJson(o: JSONObject): MemoCard = MemoCard(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            text = o.optString("text"),
            tint = runCatching {
                MemoTint.valueOf(o.optString("tint"))
            }.getOrDefault(MemoTint.YELLOW),
            x = o.optDouble("x", MEMO_DEFAULT_X.toDouble()).toFloat(),
            y = o.optDouble("y", MEMO_DEFAULT_Y.toDouble()).toFloat(),
            w = o.optDouble("w", MEMO_DEFAULT_W.toDouble()).toFloat(),
            h = o.optDouble("h", MEMO_DEFAULT_H.toDouble()).toFloat(),
            tilt = o.optDouble("tilt", 0.0).toFloat(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", o.optLong("createdAt", System.currentTimeMillis())),
            page = o.optInt("page", 0),
        ).normalized()

        private fun finite(value: Float, fallback: Float): Float =
            if (value.isFinite()) value else fallback
    }
}

/** Pure helpers for memo cards; free of Android/Compose so they are JVM-tested. */
object Memos {
    /** Fresh ids so a duplicated page's cards are independent notes. */
    fun freshIds(cards: List<MemoCard>): List<MemoCard> =
        cards.map { it.copy(id = UUID.randomUUID().toString()) }
}
