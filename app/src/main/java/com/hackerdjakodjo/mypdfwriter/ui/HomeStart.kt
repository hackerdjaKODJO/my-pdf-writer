package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import kotlin.math.roundToInt

/** The two most recent work items shown at the top of the library. */
internal data class HomeContinuation(
    val lastPdf: RecentDoc?,
    val lastNotebook: Notebook?,
) {
    val hasContent: Boolean get() = lastPdf != null || lastNotebook != null
}

/** Last opened PDF plus the most recently edited notebook that is not in the trash. */
internal fun selectHomeContinuation(
    notebooks: List<Notebook>,
    pdfs: List<RecentDoc>,
): HomeContinuation = HomeContinuation(
    lastPdf = pdfs.maxByOrNull { it.lastOpenedAt },
    lastNotebook = notebooks.asSequence()
        .filterNot { it.trashed }
        .maxByOrNull { it.updatedAt },
)

/** Quick actions collapse to two columns on narrow screens or with large text. */
internal fun homeQuickActionColumns(widthDp: Int, fontScale: Float): Int =
    chromeScale(widthDp, fontScale).quickActionColumns

/**
 * Labels use at most two lines and never ellipsize. The 12dp estimate matches
 * the 12sp label style and scales with the user's font size, so a large-text
 * user gets a second line instead of a clipped label.
 */
internal fun homeQuickActionLabelMaxLines(
    availableWidthDp: Int,
    label: String,
    fontScale: Float = 1f,
): Int {
    val scale = if (fontScale.isNaN()) 1f else fontScale.coerceIn(1f, 3f)
    return labelMaxLines(availableWidthDp, (label.length * 12 * scale).roundToInt())
}

/** Star state is spoken alongside the action instead of relying on the icon. */
internal fun favoriteToggleDescription(favorite: Boolean): String =
    stateLabel("즐겨찾기", favorite)

private data class QuickActionSpec(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
internal fun HomeStartContent(
    continuation: HomeContinuation,
    onOpenNotebook: (String) -> Unit,
    onOpenPdf: (RecentDoc) -> Unit,
    onImportPdf: () -> Unit,
    onCreateNote: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (continuation.hasContent) {
            HomeSectionLabel("이어서")
            continuation.lastPdf?.let { doc ->
                ContinuePdfCard(doc = doc, onOpen = { onOpenPdf(doc) })
            }
            if (continuation.lastPdf != null && continuation.lastNotebook != null) {
                Spacer(Modifier.height(8.dp))
            }
            continuation.lastNotebook?.let { notebook ->
                ContinueNotebookCard(
                    notebook = notebook,
                    onOpen = { onOpenNotebook(notebook.id) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val columns = homeQuickActionColumns(maxWidth.value.roundToInt(), fontScale)
            val actions = listOf(
                QuickActionSpec("PDF 가져오기", Icons.Outlined.PictureAsPdf, onImportPdf),
                QuickActionSpec("새 노트", Icons.AutoMirrored.Outlined.NoteAdd, onCreateNote),
                QuickActionSpec("PDF 도구", Icons.AutoMirrored.Outlined.MenuBook, onOpenTools),
                QuickActionSpec("백업/복원", Icons.Outlined.Archive, onOpenBackup),
            )
            val gap = 8.dp
            val cardWidth = (maxWidth - gap * (columns - 1)) / columns
            val labelWidthDp = (cardWidth - 8.dp).value.roundToInt()
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                actions.chunked(columns).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        row.forEach { action ->
                            QuickAction(
                                label = action.label,
                                icon = action.icon,
                                onClick = action.onClick,
                                labelMaxLines = homeQuickActionLabelMaxLines(
                                    labelWidthDp,
                                    action.label,
                                    fontScale,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(bottom = 7.dp)
            .semantics { heading() },
    )
}

@Composable
private fun ContinuePdfCard(doc: RecentDoc, onOpen: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        doc.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "PDF · ${doc.pageCount}페이지 · ${relativeNoteTime(doc.lastOpenedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("이어서 읽기: ${doc.name}", maxLines = 2)
            }
        }
    }
}

@Composable
private fun ContinueNotebookCard(notebook: Notebook, onOpen: () -> Unit) {
    val template = notebook.pages.firstOrNull()?.template ?: PaperTemplate.LINED
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotebookCover(
                    coverColor = notebook.coverColor,
                    coverStyle = notebook.coverStyle,
                    template = template,
                    modifier = Modifier
                        .width(34.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        notebook.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "노트 · ${notebook.pageCount}페이지 · ${relativeNoteTime(notebook.updatedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text("이어서 쓰기: ${notebook.title}", maxLines = 2)
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    labelMaxLines: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.heightIn(min = 72.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
