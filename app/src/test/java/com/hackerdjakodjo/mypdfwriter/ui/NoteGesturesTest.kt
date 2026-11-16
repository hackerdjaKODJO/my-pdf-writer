package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteGesturesTest {

    private val slop = 24f

    private fun sample(
        pointers: Int,
        durationMs: Long = 120L,
        movementPx: Float = 0f,
        enabled: Boolean = NOTE_GESTURE_UNDO_DEFAULT,
        drawingSeen: Boolean = false,
        stylusInvolved: Boolean = false,
        viewChanged: Boolean = false,
    ) = NoteGestureSample(
        maxPointers = pointers,
        durationMs = durationMs,
        movementPx = movementPx,
        touchSlopPx = slop,
        enabled = enabled,
        drawingSeen = drawingSeen,
        stylusInvolved = stylusInvolved,
        viewChanged = viewChanged,
    )

    @Test
    fun `two finger tap is undo and three finger tap is redo`() {
        assertEquals(NoteGestureAction.UNDO, NoteGestureClassifier.classify(sample(2)))
        assertEquals(NoteGestureAction.REDO, NoteGestureClassifier.classify(sample(3)))
    }

    @Test
    fun `other pointer counts never trigger`() {
        listOf(0, 1, 4, 5).forEach { pointers ->
            assertNull("pointers=$pointers", NoteGestureClassifier.classify(sample(pointers)))
        }
    }

    @Test
    fun `gesture is enabled by default and can be switched off`() {
        // The default session toggle is on, so an otherwise valid tap classifies.
        assertTrue(NOTE_GESTURE_UNDO_DEFAULT)
        assertTrue(sample(2).enabled)
        assertEquals(NoteGestureAction.UNDO, NoteGestureClassifier.classify(sample(2)))

        assertNull(NoteGestureClassifier.classify(sample(2, enabled = false)))
        assertNull(NoteGestureClassifier.classify(sample(3, enabled = false)))
    }

    @Test
    fun `movement threshold accepts jitter and rejects a drag`() {
        val atLimit = slop * NOTE_GESTURE_TAP_MAX_SLOP_FACTOR
        assertEquals(
            NoteGestureAction.UNDO,
            NoteGestureClassifier.classify(sample(2, movementPx = atLimit)),
        )
        assertNull(
            NoteGestureClassifier.classify(sample(2, movementPx = atLimit + 0.1f)),
        )
        assertNull(
            NoteGestureClassifier.classify(sample(3, movementPx = slop * 2f)),
        )
    }

    @Test
    fun `duration threshold rejects a hold and impossible durations`() {
        assertEquals(
            NoteGestureAction.UNDO,
            NoteGestureClassifier.classify(
                sample(2, durationMs = NOTE_GESTURE_TAP_MAX_DURATION_MS),
            ),
        )
        assertNull(
            NoteGestureClassifier.classify(
                sample(2, durationMs = NOTE_GESTURE_TAP_MAX_DURATION_MS + 1L),
            ),
        )
        assertNull(NoteGestureClassifier.classify(sample(2, durationMs = -1L)))
    }

    @Test
    fun `no trigger after drawing pan zoom stylus or lasso-like movement`() {
        assertNull(NoteGestureClassifier.classify(sample(2, drawingSeen = true)))
        assertNull(NoteGestureClassifier.classify(sample(3, drawingSeen = true)))
        assertNull(NoteGestureClassifier.classify(sample(2, stylusInvolved = true)))
        assertNull(NoteGestureClassifier.classify(sample(3, stylusInvolved = true)))
        assertNull(NoteGestureClassifier.classify(sample(2, viewChanged = true)))
        assertNull(NoteGestureClassifier.classify(sample(3, viewChanged = true)))
        assertNull(NoteGestureClassifier.classify(sample(2, movementPx = slop * 5f)))
    }

    @Test
    fun `non finite movement is rejected safely`() {
        assertNull(
            NoteGestureClassifier.classify(sample(2, movementPx = Float.NaN)),
        )
        assertNull(
            NoteGestureClassifier.classify(
                sample(2, movementPx = Float.POSITIVE_INFINITY),
            ),
        )
        val badSlop = NoteGestureSample(
            maxPointers = 2,
            durationMs = 100L,
            movementPx = 1f,
            touchSlopPx = Float.NaN,
        )
        assertNull(NoteGestureClassifier.classify(badSlop))
    }

    @Test
    fun `pointer count mapping matches the classifier`() {
        assertEquals(NoteGestureAction.UNDO, NoteGestureClassifier.actionForPointerCount(2))
        assertEquals(NoteGestureAction.REDO, NoteGestureClassifier.actionForPointerCount(3))
        assertNull(NoteGestureClassifier.actionForPointerCount(1))
        assertNull(NoteGestureClassifier.actionForPointerCount(4))
    }
}
