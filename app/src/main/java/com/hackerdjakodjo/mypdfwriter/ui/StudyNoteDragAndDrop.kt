package com.hackerdjakodjo.mypdfwriter.ui

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val STUDY_NOTE_ADDED_MESSAGE = "스터디 노트에 추가했습니다"

/** Text and source page travelling inside the drag [ClipData]. */
internal data class StudyDragPayload(val text: String, val pageIndex: Int)

private const val STUDY_DRAG_LABEL_PREFIX = "mypdfwriter-study-page:"

/**
 * Builds the in-app drag payload. The page rides in the [ClipData] label so the
 * drop target can link the new note page back to the original PDF page without
 * relying on any transient state.
 */
internal fun studyDragClipData(text: String, pageIndex: Int): ClipData =
    ClipData.newPlainText(
        "$STUDY_DRAG_LABEL_PREFIX${pageIndex.coerceAtLeast(0)}",
        text.trim(),
    )

/** Makes a preview or excerpt card draggable to a study-note drop target. */
internal fun Modifier.studyNoteDragSource(text: String, pageIndex: Int): Modifier {
    val value = text.trim()
    if (value.isEmpty()) return this
    return dragAndDropSource { _: Offset ->
        DragAndDropTransferData(clipData = studyDragClipData(value, pageIndex))
    }
}

internal fun studyDragPayload(event: DragAndDropEvent): StudyDragPayload? =
    event.toAndroidDragEvent().clipData?.let(::studyDragPayload)

private fun studyDragPayload(clipData: ClipData): StudyDragPayload? {
    if (clipData.itemCount <= 0) return null
    val text = clipData.getItemAt(0).text?.toString()?.trim().orEmpty()
    if (text.isEmpty()) return null
    val label = clipData.description?.label?.toString().orEmpty()
    val page = label
        .takeIf { it.startsWith(STUDY_DRAG_LABEL_PREFIX) }
        ?.removePrefix(STUDY_DRAG_LABEL_PREFIX)
        ?.toIntOrNull()
        ?.coerceAtLeast(0)
        ?: 0
    return StudyDragPayload(text, page)
}

/**
 * Drop target modifier shared by the large capture card and the floating study
 * pane header. [onActiveChange] lets a caller highlight a tiny header while a PDF
 * excerpt hovers over it, so a permanent full-width capture strip is unnecessary.
 */
@Composable
internal fun rememberStudyNoteDropTarget(
    onDrop: (text: String, pageIndex: Int) -> Unit,
    onActiveChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
): Modifier {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnActive by rememberUpdatedState(onActiveChange)
    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                currentOnActive(studyDragPayload(event) != null)
            }

            override fun onEntered(event: DragAndDropEvent) {
                currentOnActive(studyDragPayload(event) != null)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnActive(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnActive(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnActive(false)
                val payload = studyDragPayload(event) ?: return false
                currentOnDrop(payload.text, payload.pageIndex)
                return true
            }
        }
    }
    return if (enabled) {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> studyDragPayload(event) != null },
            target = target,
        )
    } else {
        Modifier
    }
}

/**
 * Capture target shown by the viewer, excerpt sheet and study pane header.
 * [onDrop] receives the dragged text and the page encoded at drag start;
 * [enabled] keeps the card visible but inert when there is nothing to drop yet;
 * [compact] uses a single-line row that fits the pane header.
 */
@Composable
internal fun StudyNoteDropTarget(
    onDrop: (text: String, pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    var active by remember { mutableStateOf(false) }
    val dropModifier = rememberStudyNoteDropTarget(
        onDrop = onDrop,
        onActiveChange = { active = it },
        enabled = enabled,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(dropModifier),
        shape = RoundedCornerShape(if (compact) 10.dp else 14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = BorderStroke(
            width = if (active) 2.dp else 1.dp,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = if (compact) 0.dp else 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = if (compact) 5.dp else 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Notes,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 16.dp else 22.dp),
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(if (compact) 6.dp else 10.dp))
            Column {
                Text(
                    if (compact) "PDF 발췌 놓기" else "스터디 노트에 놓기",
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (caption != null) {
                    Text(
                        caption,
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
