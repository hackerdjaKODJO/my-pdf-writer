package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import java.util.UUID

/** Offset applied to a pasted selection, in normalized page units. */
const val NOTE_CLIPBOARD_PASTE_OFFSET = 0.012f

/**
 * Session-only clipboard for lasso selections.
 *
 * Nothing here is written to the notebook file or app storage. The editor owns one
 * instance for as long as the notebook screen is open, so copying on one page and
 * pasting on another works without a file-format change and without leaving any
 * user content behind after the screen closes.
 *
 * A paste always creates fresh annotation ids, remaps the annotation to page 0
 * (notebook pages store page-local annotations), assigns the destination layer,
 * applies a small offset, and resets audio replay timing so copied ink appears
 * immediately during replay.
 */
data class NoteClipboard(
    val annotations: List<Annotation> = emptyList(),
    val sourcePageIndex: Int = 0,
) {
    val size: Int get() = annotations.size
    val isEmpty: Boolean get() = annotations.isEmpty()
    val isNotEmpty: Boolean get() = annotations.isNotEmpty()

    fun paste(
        targetLayerId: String?,
        offsetX: Float = NOTE_CLIPBOARD_PASTE_OFFSET,
        offsetY: Float = NOTE_CLIPBOARD_PASTE_OFFSET,
        now: Long = System.currentTimeMillis(),
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): List<Annotation> {
        if (annotations.isEmpty()) return emptyList()
        val dx = if (offsetX.isFinite()) offsetX else 0f
        val dy = if (offsetY.isFinite()) offsetY else 0f
        return annotations.map { original ->
            original.copy(
                id = idFactory(),
                page = 0,
                points = original.points.map { point ->
                    point.copy(
                        x = (point.x + dx).coerceIn(0f, 1f),
                        y = (point.y + dy).coerceIn(0f, 1f),
                    )
                },
                layerId = targetLayerId,
                // Copied ink is new ink: replay must show it at paste time.
                createdAt = now,
                durationMs = 0L,
            )
        }
    }

    companion object {
        /**
         * Captures the current selection. [sourcePageIndex] is only used for the
         * editor status line and trace output; it never changes the annotation data.
         */
        fun fromSelection(
            annotations: List<Annotation>,
            sourcePageIndex: Int,
        ): NoteClipboard = NoteClipboard(
            annotations = annotations.toList(),
            sourcePageIndex = sourcePageIndex.coerceAtLeast(0),
        )
    }
}
