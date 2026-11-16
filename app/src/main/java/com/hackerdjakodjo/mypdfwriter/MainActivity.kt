package com.hackerdjakodjo.mypdfwriter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.ui.BackupSheet
import com.hackerdjakodjo.mypdfwriter.ui.BackupViewModel
import com.hackerdjakodjo.mypdfwriter.ui.NoteEditorScreen
import com.hackerdjakodjo.mypdfwriter.ui.NotebookHomeScreen
import com.hackerdjakodjo.mypdfwriter.ui.NotebookViewModel
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.ui.StudySource
import com.hackerdjakodjo.mypdfwriter.ui.ToolsScreen
import com.hackerdjakodjo.mypdfwriter.ui.ViewerScreen
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()
    private val notebookViewModel: NotebookViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private var backupIncludeSources = false

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openDocument(it) }
    }

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.createBlankDocument(it) }
    }

    private val exportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.exportTo(it) }
    }

    private val exportNote = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { notebookViewModel.exportCurrent(it) }
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { backupViewModel.backup(it, backupIncludeSources) }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backupViewModel.restore(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleViewIntent(intent)

        setContent {
            MyPdfWriterTheme {
                var showTools by remember { mutableStateOf(false) }
                var showBackup by remember { mutableStateOf(false) }
                val viewer by viewModel.viewer.collectAsStateWithLifecycle()
                val notebooks by notebookViewModel.notebooks.collectAsStateWithLifecycle()
                val currentNote by notebookViewModel.current.collectAsStateWithLifecycle()
                val notePage by notebookViewModel.pageIndex.collectAsStateWithLifecycle()
                val pdfs by viewModel.library.collectAsStateWithLifecycle()
                val backupState by backupViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(backupState.restoredAt) {
                    if (backupState.restoredAt != null) {
                        notebookViewModel.refresh()
                        viewModel.reloadLibrary()
                        backupViewModel.consumeRestored()
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val note = currentNote
                    when {
                        note != null -> NoteEditorScreen(
                            notebook = note,
                            pageIndex = notePage,
                            onBack = { notebookViewModel.close() },
                            onChange = { notebookViewModel.updateCurrent(it) },
                            onPageIndexChange = { notebookViewModel.setPageIndex(it) },
                            onExportPdf = { exportNote.launch(notebookViewModel.exportSuggestedName()) },
                            onOpenSourcePdf = { page ->
                                val uri = note.sourceDocumentUri
                                if (uri.isNotBlank()) {
                                    notebookViewModel.close()
                                    viewModel.openDocumentAt(Uri.parse(uri), page)
                                }
                            },
                        )
                        showTools -> ToolsScreen(
                            onBack = { showTools = false },
                            onOpen = { uri ->
                                showTools = false
                                viewModel.openDocument(uri)
                            },
                            initialSource = viewModel.currentSourceFile(),
                            initialName = viewer?.docName.orEmpty(),
                        )
                        viewer == null -> NotebookHomeScreen(
                            notebooks = notebooks,
                            pdfs = pdfs,
                            onOpenNotebook = { notebookViewModel.open(it) },
                            onCreateNotebook = { draft -> notebookViewModel.create(draft) },
                            onOpenPdf = { doc -> viewModel.openDocument(Uri.parse(doc.uri)) },
                            onImportPdf = { openPdf.launch(arrayOf("application/pdf")) },
                            onCreatePdf = { createPdf.launch("새 문서.pdf") },
                            onOpenTools = { showTools = true },
                            onToggleFavorite = { notebookViewModel.toggleFavorite(it) },
                            onTrash = { notebookViewModel.trash(it) },
                            onRestore = { notebookViewModel.restore(it) },
                            onDeleteForever = { notebookViewModel.deleteForever(it) },
                            onRename = { id, title -> notebookViewModel.rename(id, title) },
                            onMoveFolder = { id, folder -> notebookViewModel.moveFolder(id, folder) },
                            onSetTags = { id, tags -> notebookViewModel.setTags(id, tags) },
                            onOpenBackup = { showBackup = true },
                        )
                        else -> ViewerScreen(
                            vm = viewModel,
                            onExportRequest = { suggestedName -> exportPdf.launch(suggestedName) },
                            onOpenTools = { showTools = true },
                            onCreateStudyNote = { notebook ->
                                notebookViewModel.importNotebook(notebook)
                                viewModel.closeDocument()
                            },
                            onAppendStudyExcerpt = { text, page ->
                                notebookViewModel.appendStudyExcerpt(
                                    StudySource(
                                        docName = viewer?.docName.orEmpty(),
                                        docUri = viewModel.currentDocumentUri(),
                                    ),
                                    text,
                                    page,
                                )
                            },
                        )
                    }
                }
                if (showBackup) {
                    BackupSheet(
                        onDismiss = { showBackup = false },
                        onBackup = { includeSources ->
                            backupIncludeSources = includeSources
                            saveBackup.launch(backupViewModel.suggestedBackupName())
                        },
                        onRestore = {
                            openBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                        },
                        busy = backupState.busy,
                        progress = backupState.progress,
                        message = backupState.message,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        notebookViewModel.close()
        viewModel.openDocument(uri, addToRecents = false)
        intent.data = null
    }
}
