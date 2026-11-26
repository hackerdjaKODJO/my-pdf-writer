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
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect

internal const val STUDY_NOTE_ADDED_MESSAGE = "스터디 노트에 추가했습니다"

/** A complete document-bound capture travelling inside the drag [ClipData]. */
internal data class StudyDragPayload(val capture: PdfStudyCapture)

private const val STUDY_DRAG_LABEL_PREFIX = "mypdfwriter-study-capture:v2:"
private const val STUDY_DRAG_FIELD_SEPARATOR = "|"
private const val STUDY_DRAG_FIELD_COUNT = 9

private fun encodeDragField(value: String?): String =
    value.orEmpty().replace("%", "%25").replace(STUDY_DRAG_FIELD_SEPARATOR, "%7C")

/** Decodes only escapes emitted by [encodeDragField]; malformed escapes are rejected. */
private fun decodeDragField(value: String): String? {
    val decoded = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char != '%') {
            decoded.append(char)
            index++
            continue
        }
        if (index + 2 >= value.length) return null
        when (value.substring(index + 1, index + 3)) {
            "25" -> decoded.append('%')
            "7C", "7c" -> decoded.append(STUDY_DRAG_FIELD_SEPARATOR)
            else -> return null
        }
        index += 3
    }
    return decoded.toString()
}

private fun parseNonNegativeInt(value: String): Int? =
    value.takeIf { field -> field.isNotEmpty() && field.all { it in '0'..'9' } }
        ?.toIntOrNull()
        ?.takeIf { it >= 0 }

/**
 * Pure label codec so the complete capture contract can be regression-tested
 * without Android. Text deliberately stays in the [ClipData] item, not the label.
 */
internal fun studyDragLabel(capture: PdfStudyCapture): String =
    STUDY_DRAG_LABEL_PREFIX + listOf(
        capture.sourcePage.toString(),
        encodeDragField(capture.source.docId),
        encodeDragField(capture.source.contentFingerprint),
        capture.rect?.left?.toString().orEmpty(),
        capture.rect?.top?.toString().orEmpty(),
        capture.rect?.right?.toString().orEmpty(),
        capture.rect?.bottom?.toString().orEmpty(),
        capture.rotation.toString(),
        encodeDragField(capture.excerptId),
    ).joinToString(STUDY_DRAG_FIELD_SEPARATOR)

/** Parses label metadata plus item text, rejecting incomplete or unsafe captures. */
internal fun parseStudyDragLabel(label: String?, text: String?): PdfStudyCapture? {
    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return null
    val raw = label?.takeIf { it.startsWith(STUDY_DRAG_LABEL_PREFIX) }
        ?.removePrefix(STUDY_DRAG_LABEL_PREFIX)
        ?: return null
    val fields = raw.split(STUDY_DRAG_FIELD_SEPARATOR)
    if (fields.size != STUDY_DRAG_FIELD_COUNT) return null

    val sourcePage = parseNonNegativeInt(fields[0]) ?: return null
    val docId = if (fields[1].isEmpty()) {
        null
    } else {
        val decoded = decodeDragField(fields[1]) ?: return null
        decoded.takeIf { it.isNotBlank() }
    }
    val fingerprint = if (fields[2].isEmpty()) {
        null
    } else {
        val decoded = decodeDragField(fields[2]) ?: return null
        decoded.takeIf { it.isNotBlank() }
    }
    val rotation = parseNonNegativeInt(fields[7])?.takeIf { it <= 360 } ?: return null
    val excerptId = if (fields[8].isEmpty()) {
        null
    } else {
        val decoded = decodeDragField(fields[8]) ?: return null
        decoded.takeIf { it.isNotBlank() }
    }

    val rectFields = fields.subList(3, 7)
    val rect = if (rectFields.all { it.isEmpty() }) {
        null
    } else {
        if (rectFields.any { it.isEmpty() }) return null
        val coordinates = rectFields.map { field -> field.toFloatOrNull() ?: return null }
        if (coordinates.any { !it.isFinite() || it !in 0f..1f }) return null
        val (left, top, right, bottom) = coordinates
        if (left > right || top > bottom) return null
        NormalizedRect(left = left, top = top, right = right, bottom = bottom)
    }

    return PdfStudyCapture(
        text = value,
        sourcePage = sourcePage,
        rect = rect,
        excerptId = excerptId,
        // Display name and URI are not drag metadata. The drop owner reattaches
        // its canonical StudySource after the strict identity check succeeds.
        source = StudySource(
            docName = "",
            docUri = null,
            docId = docId,
            contentFingerprint = fingerprint,
        ),
        rotation = rotation,
    )
}

/** A payload may be dropped only onto the document that produced it. */
internal fun studyDragPayloadMatches(
    payload: StudyDragPayload,
    expectedDocId: String?,
    expectedFingerprint: String?,
): Boolean {
    val expectedId = expectedDocId?.takeIf { it.isNotBlank() }
    val expectedFp = expectedFingerprint?.takeIf { it.isNotBlank() }
    if (expectedId != null && payload.capture.source.docId != expectedId) return false
    if (expectedFp != null && payload.capture.source.contentFingerprint != expectedFp) return false
    return true
}

/** Shared drop dispatch keeps the persistence callback's failure result intact. */
internal fun dispatchStudyNoteDrop(
    payload: StudyDragPayload?,
    onDrop: (PdfStudyCapture) -> Boolean,
    expectedDocId: String? = null,
    expectedFingerprint: String? = null,
): Boolean {
    if (payload == null || !studyDragPayloadMatches(payload, expectedDocId, expectedFingerprint)) {
        return false
    }
    return onDrop(payload.capture)
}

/**
 * Builds the in-app drag payload. Source location and identity ride in the
 * [ClipData] label so the drop target can reject stale cross-document content.
 */
internal fun studyDragClipData(capture: PdfStudyCapture): ClipData =
    ClipData.newPlainText(
        studyDragLabel(capture),
        capture.text.trim(),
    )

/** Makes a preview or excerpt card draggable to a document-bound drop target. */
internal fun Modifier.studyNoteDragSource(capture: PdfStudyCapture?): Modifier {
    if (capture == null || capture.text.isBlank()) return this
    return dragAndDropSource { _: Offset ->
        DragAndDropTransferData(
            clipData = studyDragClipData(capture),
        )
    }
}

internal fun studyDragPayload(event: DragAndDropEvent): StudyDragPayload? =
    event.toAndroidDragEvent().clipData?.let(::studyDragPayload)

private fun studyDragPayload(clipData: ClipData): StudyDragPayload? {
    if (clipData.itemCount <= 0) return null
    val text = clipData.getItemAt(0).text?.toString()
    val label = clipData.description?.label?.toString()
    return parseStudyDragLabel(label, text)?.let(::StudyDragPayload)
}

/**
 * Drop target modifier shared by the large capture card and the floating study
 * pane header. [onActiveChange] lets a caller highlight a tiny header while a PDF
 * excerpt hovers over it, so a permanent full-width capture strip is unnecessary.
 * [onDrop] must report whether persistence actually succeeded.
 */
@Composable
internal fun rememberStudyNoteDropTarget(
    onDrop: (PdfStudyCapture) -> Boolean,
    onActiveChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    expectedDocId: String? = null,
    expectedFingerprint: String? = null,
): Modifier {
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnActive by rememberUpdatedState(onActiveChange)
    val currentDocId by rememberUpdatedState(expectedDocId)
    val currentFingerprint by rememberUpdatedState(expectedFingerprint)
    fun accepts(event: DragAndDropEvent): Boolean {
        val payload = studyDragPayload(event) ?: return false
        return studyDragPayloadMatches(payload, currentDocId, currentFingerprint)
    }
    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                currentOnActive(accepts(event))
            }

            override fun onEntered(event: DragAndDropEvent) {
                currentOnActive(accepts(event))
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
                return dispatchStudyNoteDrop(
                    payload = payload,
                    onDrop = currentOnDrop,
                    expectedDocId = currentDocId,
                    expectedFingerprint = currentFingerprint,
                )
            }
        }
    }
    return if (enabled) {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> accepts(event) },
            target = target,
        )
    } else {
        Modifier
    }
}

/**
 * Capture target shown by the viewer, excerpt sheet and study pane header.
 * [onDrop] receives the complete capture and returns true only when persistence
 * succeeds; [enabled] keeps the card visible but inert when there is nothing to
 * drop yet; [compact] uses a single-line row that fits the pane header.
 */
@Composable
internal fun StudyNoteDropTarget(
    onDrop: (PdfStudyCapture) -> Boolean,
    modifier: Modifier = Modifier,
    caption: String? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    expectedDocId: String? = null,
    expectedFingerprint: String? = null,
) {
    var active by remember { mutableStateOf(false) }
    val dropModifier = rememberStudyNoteDropTarget(
        onDrop = onDrop,
        onActiveChange = { active = it },
        enabled = enabled,
        expectedDocId = expectedDocId,
        expectedFingerprint = expectedFingerprint,
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
