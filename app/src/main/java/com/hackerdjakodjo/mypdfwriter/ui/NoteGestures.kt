package com.hackerdjakodjo.mypdfwriter.ui

/** Session default for the "제스처 실행 취소" setting. */
const val NOTE_GESTURE_UNDO_DEFAULT = true

/** Maximum duration of a multi-pointer tap. */
const val NOTE_GESTURE_TAP_MAX_DURATION_MS = 400L

/** How far the gesture may drift, as a multiple of the platform touch slop. */
const val NOTE_GESTURE_TAP_MAX_SLOP_FACTOR = 1.2f

/** Undo/redo actions reachable through a two- or three-finger tap. */
enum class NoteGestureAction {
    UNDO,
    REDO,
}

/**
 * Immutable inputs for [NoteGestureClassifier]. Pixel values are intentionally
 * passed in so the classifier stays platform-free and unit-testable; the Compose
 * layer supplies its density-scaled touch slop.
 *
 * [drawingSeen] must be true whenever the gesture began as a drawable stroke,
 * even if a second finger later switched that gesture to navigation. This keeps
 * finger-writing and stylus strokes from being reinterpreted as undo/redo.
 */
data class NoteGestureSample(
    val maxPointers: Int,
    val durationMs: Long,
    val movementPx: Float,
    val touchSlopPx: Float,
    val enabled: Boolean = NOTE_GESTURE_UNDO_DEFAULT,
    val drawingSeen: Boolean = false,
    val stylusInvolved: Boolean = false,
    val viewChanged: Boolean = false,
)

/**
 * Pure multi-pointer tap classifier for note undo/redo.
 *
 * A gesture is accepted only when it is a short tap with exactly two (undo) or
 * three (redo) pointers, the pointer centroid did not move beyond a small
 * multiple of the touch slop, no stroke drawing occurred, no stylus was involved
 * (palm rejection) and pan/zoom did not change the view. Everything else returns
 * `null`, so lasso, one-finger pan, pinch zoom and double-tap reset keep their
 * existing behavior.
 */
object NoteGestureClassifier {

    fun classify(sample: NoteGestureSample): NoteGestureAction? {
        if (!sample.enabled) return null
        if (sample.drawingSeen || sample.stylusInvolved || sample.viewChanged) return null
        if (sample.durationMs < 0L || sample.durationMs > NOTE_GESTURE_TAP_MAX_DURATION_MS) {
            return null
        }
        if (!sample.movementPx.isFinite() || !sample.touchSlopPx.isFinite()) return null
        if (sample.movementPx < 0f) return null
        val allowedMovement =
            sample.touchSlopPx.coerceAtLeast(0f) * NOTE_GESTURE_TAP_MAX_SLOP_FACTOR
        if (sample.movementPx > allowedMovement) return null
        return when (sample.maxPointers) {
            2 -> NoteGestureAction.UNDO
            3 -> NoteGestureAction.REDO
            else -> null
        }
    }

    /** Convenience mapping used by the integration and its tests. */
    fun actionForPointerCount(pointers: Int): NoteGestureAction? = when (pointers) {
        2 -> NoteGestureAction.UNDO
        3 -> NoteGestureAction.REDO
        else -> null
    }
}
