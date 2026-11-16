package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import kotlinx.coroutines.launch

/**
 * The pane keeps the PDF visible in the strip above it. The sheet drag handle
 * adds a little height on top, so 0.80 lands around 82% of the screen.
 */
internal const val STUDY_PANE_HEIGHT_FRACTION = 0.80f

/**
 * Phone-first study-note bottom pane. It hosts the real [NoteEditorScreen] over
 * the open PDF, so handwriting, layers, page navigation, audio and export keep
 * their existing behavior. The PDF is not closed or replaced; dismissing the
 * sheet returns to the same page.
 *
 * The sheet intentionally uses a light scrim: the remaining PDF strip stays
 * readable so the reader remembers which document the note belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPaneSheet(
    notebook: Notebook,
    pageIndex: Int,
    onDismiss: () -> Unit,
    onChange: (Notebook) -> Unit,
    onPageIndexChange: (Int) -> Unit,
    onExportPdf: (NoteExportOptions) -> Unit,
    onOpenSourcePdf: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Animate explicit close/back out before the caller drops the pane state.
    val closePane: () -> Unit = {
        scope.launch {
            runCatching { sheetState.hide() }
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.12f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(STUDY_PANE_HEIGHT_FRACTION),
        ) {
            StudyPaneHeader(notebookTitle = notebook.title, onClose = closePane)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                NoteEditorScreen(
                    notebook = notebook,
                    pageIndex = pageIndex,
                    onBack = closePane,
                    onChange = onChange,
                    onPageIndexChange = onPageIndexChange,
                    onExportPdf = onExportPdf,
                    onOpenSourcePdf = onOpenSourcePdf,
                )
            }
        }
    }
}

@Composable
private fun StudyPaneHeader(notebookTitle: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "PDF 위 스터디 노트",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                notebookTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "스터디 노트 닫기",
            )
        }
    }
}
