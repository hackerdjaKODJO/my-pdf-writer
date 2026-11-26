package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp

internal const val PDF_EASY_MENU_TAG = "pdf_easy_menu"
internal const val PDF_ALL_TOOLS_TAG = "pdf_all_tools"

/**
 * The first PDF 메뉴 surface: six tasks, a contextual help shortcut where useful,
 * and one clearly labelled handoff to the unchanged expert hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfEasyMenuSheet(
    docName: String,
    pageIndex: Int,
    pageCount: Int,
    onTask: (PdfEasyTask) -> Unit,
    onOpenExpert: () -> Unit,
    onDismiss: () -> Unit,
    bookmarked: Boolean = false,
    taskEnabled: (PdfEasyTask) -> Boolean = { true },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val rowLayout = pdfHubRowLayout(
        widthDp = configuration.smallestScreenWidthDp,
        fontScale = configuration.fontScale,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag(PDF_EASY_MENU_TAG)
                .semantics { paneTitle = UiCopy.PDF_MENU }
                .padding(bottom = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    UiCopy.PDF_MENU,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.sectionHeading(),
                )
                Text(
                    "$docName · ${pageIndex + 1} / $pageCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    "자주 쓰는 여섯 가지 작업을 먼저 보여드려요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
            }

            pdfEasyMenuTasks.forEach { task ->
                EasyTaskRow(
                    task = task,
                    bookmarked = bookmarked && task == PdfEasyTask.BOOKMARK,
                    enabled = taskEnabled(task),
                    minHeightDp = rowLayout.minHeightDp,
                    onClick = {
                        onDismiss()
                        onTask(task)
                    },
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenExpert()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = MIN_TARGET_DP.dp)
                    .testTag(PDF_ALL_TOOLS_TAG),
            ) {
                Text(PDF_EASY_MENU_ALL_TOOLS_LABEL, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EasyTaskRow(
    task: PdfEasyTask,
    bookmarked: Boolean,
    enabled: Boolean,
    minHeightDp: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .heightIn(min = minHeightDp.dp)
            .testTag("pdf_easy_task_${task.name.lowercase()}")
            .semantics {
                if (bookmarked) {
                    selected = true
                    stateDescription = "저장됨"
                }
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        easyTaskIcon(task, bookmarked),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
            Text(
                pdfEasyTaskLabel(task, bookmarked),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (bookmarked) {
                Text(
                    "저장됨",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun easyTaskIcon(task: PdfEasyTask, bookmarked: Boolean): ImageVector = when (task) {
    PdfEasyTask.STUDY_NOTE -> Icons.Outlined.EditNote
    PdfEasyTask.PAGE_MEMO -> Icons.AutoMirrored.Outlined.StickyNote2
    PdfEasyTask.BOOKMARK -> if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder
    PdfEasyTask.EXPORT -> Icons.Outlined.FileDownload
    PdfEasyTask.SHARE -> Icons.Outlined.Share
    PdfEasyTask.HELP -> Icons.AutoMirrored.Outlined.HelpOutline
}
