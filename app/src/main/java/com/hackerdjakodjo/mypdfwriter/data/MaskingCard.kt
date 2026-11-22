package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/** Study progress of one masking card. HIDDEN is "not started", REVEALED is "seen but not graded". */
enum class MaskingCardState { HIDDEN, REVEALED, CORRECT, WRONG }

private const val MASK_DEFAULT_X = 0.34f
private const val MASK_DEFAULT_Y = 0.18f
private const val MASK_DEFAULT_W = 0.32f
private const val MASK_DEFAULT_H = 0.10f
private const val MASK_MIN_W = 0.05f
private const val MASK_MIN_H = 0.04f

/**
 * An original, offline study card that lives on a notebook page.
 *
 * The card stores only text, a normalized page rectangle and study state. It never
 * stores an absolute path, a content URI or a device identifier, so the JSON is
 * portable across devices and local backups. The rectangle is a fraction of the
 * A4 page box (0..1), matching [Annotation] coordinates. H/W are clamped to a small
 * minimum so a card stays tappable on a phone.
 */
/** Keeps a card's whole rectangle on the page, matching [MemoCard.normalized]. */
internal fun clampCardOrigin(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
): Pair<Float, Float> {
    val safeW = w.coerceIn(0f, 1f)
    val safeH = h.coerceIn(0f, 1f)
    return x.coerceIn(0f, (1f - safeW).coerceAtLeast(0f)) to
        y.coerceIn(0f, (1f - safeH).coerceAtLeast(0f))
}

data class MaskingCard(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String = "",
    val answer: String = "",
    val x: Float = MASK_DEFAULT_X,
    val y: Float = MASK_DEFAULT_Y,
    val w: Float = MASK_DEFAULT_W,
    val h: Float = MASK_DEFAULT_H,
    val state: MaskingCardState = MaskingCardState.HIDDEN,
    val createdAt: Long = System.currentTimeMillis(),
) {
    /** Clamps geometry and repairs missing ids/state. Safe for old or hand-edited JSON. */
    fun normalized(): MaskingCard {
        val safeW = finite(w, MASK_DEFAULT_W).coerceIn(MASK_MIN_W, 1f)
        val safeH = finite(h, MASK_DEFAULT_H).coerceIn(MASK_MIN_H, 1f)
        val (safeX, safeY) = clampCardOrigin(
            finite(x, MASK_DEFAULT_X), finite(y, MASK_DEFAULT_Y), safeW, safeH,
        )
        return copy(
            id = id.ifBlank { UUID.randomUUID().toString() },
            prompt = prompt.trim(),
            answer = answer.trim(),
            x = safeX,
            y = safeY,
            w = safeW,
            h = safeH,
            createdAt = if (createdAt > 0L) createdAt else System.currentTimeMillis(),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("prompt", prompt)
        put("answer", answer)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        put("state", state.name)
        put("createdAt", createdAt)
    }

    companion object {
        const val DEFAULT_X = MASK_DEFAULT_X
        const val DEFAULT_Y = MASK_DEFAULT_Y
        const val DEFAULT_W = MASK_DEFAULT_W
        const val DEFAULT_H = MASK_DEFAULT_H
        const val MIN_W = MASK_MIN_W
        const val MIN_H = MASK_MIN_H

        fun fromJson(o: JSONObject): MaskingCard = MaskingCard(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            prompt = o.optString("prompt"),
            answer = o.optString("answer"),
            x = o.optDouble("x", MASK_DEFAULT_X.toDouble()).toFloat(),
            y = o.optDouble("y", MASK_DEFAULT_Y.toDouble()).toFloat(),
            w = o.optDouble("w", MASK_DEFAULT_W.toDouble()).toFloat(),
            h = o.optDouble("h", MASK_DEFAULT_H.toDouble()).toFloat(),
            state = runCatching {
                MaskingCardState.valueOf(o.optString("state"))
            }.getOrDefault(MaskingCardState.HIDDEN),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        ).normalized()

        private fun finite(value: Float, fallback: Float): Float =
            if (value.isFinite()) value else fallback
    }
}

/** Aggregate study counts for a set of cards, independent of page order. */
data class MaskingScore(
    val total: Int,
    val hidden: Int,
    val revealed: Int,
    val correct: Int,
    val wrong: Int,
) {
    /** Cards that received an O/X grade. */
    val graded: Int get() = correct + wrong

    /** Cards the student has interacted with at all. */
    val started: Int get() = graded + revealed

    /** Fraction of all cards that have been started (0..1). */
    val progress: Float get() = if (total == 0) 0f else started.toFloat() / total

    /** Correct share of graded cards (0..1). */
    val accuracy: Float get() = if (graded == 0) 0f else correct.toFloat() / graded
}

/**
 * Pure state transitions and scoring for masking cards.
 *
 * Kept free of Android/Compose so the full study loop has JVM coverage. Transitions
 * never mutate a card; the caller commits the returned card as one notebook change.
 */
object MaskingCards {

    fun reveal(card: MaskingCard): MaskingCard =
        if (card.state == MaskingCardState.HIDDEN) {
            card.copy(state = MaskingCardState.REVEALED)
        } else {
            card
        }

    fun conceal(card: MaskingCard): MaskingCard =
        if (card.state == MaskingCardState.HIDDEN) {
            card
        } else {
            card.copy(state = MaskingCardState.HIDDEN)
        }

    fun mark(card: MaskingCard, correct: Boolean): MaskingCard = card.copy(
        state = if (correct) MaskingCardState.CORRECT else MaskingCardState.WRONG,
    )

    fun score(cards: Collection<MaskingCard>): MaskingScore {
        var hidden = 0
        var revealed = 0
        var correct = 0
        var wrong = 0
        cards.forEach { card ->
            when (card.state) {
                MaskingCardState.HIDDEN -> hidden++
                MaskingCardState.REVEALED -> revealed++
                MaskingCardState.CORRECT -> correct++
                MaskingCardState.WRONG -> wrong++
            }
        }
        return MaskingScore(
            total = cards.size,
            hidden = hidden,
            revealed = revealed,
            correct = correct,
            wrong = wrong,
        )
    }

    /** Fresh ids so a duplicated page's cards are independent study records. */
    fun freshIds(cards: List<MaskingCard>): List<MaskingCard> =
        cards.map { it.copy(id = UUID.randomUUID().toString()) }

    /** Short Korean summary used by the card sheet header. */
    fun progressText(score: MaskingScore): String =
        "진행 ${(score.progress * 100).roundToInt()}% · 정답 ${score.correct}/${score.graded} " +
            "(${Math.round(score.accuracy * 100)}%)"
}
