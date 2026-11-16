package com.hackerdjakodjo.mypdfwriter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
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
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.ui.BackupSheet
import com.hackerdjakodjo.mypdfwriter.ui.BackupViewModel
import com.hackerdjakodjo.mypdfwriter.ui.NoteEditorScreen
import com.hackerdjakodjo.mypdfwriter.ui.NoteTraceSheet
import com.hackerdjakodjo.mypdfwriter.ui.NotebookHomeScreen
import com.hackerdjakodjo.mypdfwriter.ui.NotebookViewModel
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.ui.StudyPaneSheet
import com.hackerdjakodjo.mypdfwriter.ui.StudySource
import com.hackerdjakodjo.mypdfwriter.ui.ToolsScreen
import com.hackerdjakodjo.mypdfwriter.ui.ViewerScreen
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()
    private val notebookViewModel: NotebookViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private var backupIncludeSources = false
    /** Set only by the PDF study pane; the activity result then exports this detached note. */
    private var pendingNoteExport: Notebook? = null
    /** Page-range/layer choices paired with [pendingNoteExport] or the current editor. */
    private var pendingNoteExportOptions: NoteExportOptions = NoteExportOptions()

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
        val detached = pendingNoteExport
        val options = pendingNoteExportOptions
        pendingNoteExport = null
        pendingNoteExportOptions = NoteExportOptions()
        uri?.let {
            if (detached != null) {
                notebookViewModel.exportDetached(detached, it, options)
            } else {
                notebookViewModel.exportCurrent(it, options)
            }
        }
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
                var showTrace by remember { mutableStateOf(false) }
                val viewer by viewModel.viewer.collectAsStateWithLifecycle()
                val notebooks by notebookViewModel.notebooks.collectAsStateWithLifecycle()
                val currentNote by notebookViewModel.current.collectAsStateWithLifecycle()
                val notePage by notebookViewModel.pageIndex.collectAsStateWithLifecycle()
                val pdfs by viewModel.library.collectAsStateWithLifecycle()
                val backupState by backupViewModel.state.collectAsStateWithLifecycle()
                var studyPaneNotebook by remember { mutableStateOf<Notebook?>(null) }
                var studyPanePageIndex by remember { mutableStateOf(0) }
                var studyPaneDocId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(backupState.restoredAt) {
                    if (backupState.restoredAt != null) {
                        notebookViewModel.refresh()
                        viewModel.reloadLibrary()
                        backupViewModel.consumeRestored()
                    }
                }
                // A detached pane belongs to one open PDF. If the document closes or
                // is replaced elsewhere, save the note and drop the stale pane.
                LaunchedEffect(viewer?.docId) {
                    val pane = studyPaneNotebook ?: return@LaunchedEffect
                    val docId = viewer?.docId
                    if (docId == null || studyPaneDocId != docId) {
                        notebookViewModel.saveDetached(pane)
                        studyPaneNotebook = null
                        studyPanePageIndex = 0
                        studyPaneDocId = null
                    }
                }
                val openStudyPane: (Notebook) -> Unit = { notebook ->
                    studyPaneNotebook = notebook
                    studyPanePageIndex = 0
                    studyPaneDocId = viewer?.docId
                }
                val closeStudyPane: () -> Unit = {
                    studyPaneNotebook?.let { notebookViewModel.saveDetached(it) }
                    studyPaneNotebook = null
                    studyPanePageIndex = 0
                    studyPaneDocId = null
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
                            onExportPdf = { options ->
                                pendingNoteExport = null
                                pendingNoteExportOptions = options
                                exportNote.launch(
                                    options.suggestedFileName.ifBlank {
                                        notebookViewModel.exportSuggestedName()
                                    },
                                )
                            },
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
                            onOpenTrace = { showTrace = true },
                        )
                        else -> Box(Modifier.fillMaxSize()) {
                            ViewerScreen(
                                vm = viewModel,
                                onExportRequest = { suggestedName -> exportPdf.launch(suggestedName) },
                                onOpenTools = { showTools = true },
                                onCreateStudyNote = { notebook ->
                                    // Keep the PDF open: the captured note opens in
                                    // the detached pane over this page.
                                    openStudyPane(notebookViewModel.saveDetached(notebook))
                                },
                                onAppendStudyExcerpt = { text, page ->
                                    val updated = notebookViewModel.appendStudyExcerpt(
                                        StudySource(
                                            docName = viewer?.docName.orEmpty(),
                                            docUri = viewModel.currentDocumentUri(),
                                        ),
                                        text,
                                        page,
                                    )
                                    val pane = studyPaneNotebook
                                    if (pane != null && pane.id == updated.id) {
                                        studyPaneNotebook = updated
                                        studyPanePageIndex = studyPanePageIndex.coerceIn(
                                            0,
                                            (updated.pages.size - 1).coerceAtLeast(0),
                                        )
                                    }
                                },
                                onOpenStudyNote = { source ->
                                    val notebook = notebookViewModel.studyNoteFor(source)
                                        ?: notebookViewModel.createBlankStudyNote(source)
                                    openStudyPane(notebook)
                                },
                            )
                            studyPaneNotebook?.let { pane ->
                                StudyPaneSheet(
                                    notebook = pane,
                                    pageIndex = studyPanePageIndex,
                                    onDismiss = closeStudyPane,
                                    onChange = { updated ->
                                        studyPaneNotebook = notebookViewModel.saveDetached(updated)
                                    },
                                    onPageIndexChange = { index ->
                                        val latest = studyPaneNotebook
                                        studyPanePageIndex = index.coerceIn(
                                            0,
                                            ((latest?.pages?.size ?: 1) - 1).coerceAtLeast(0),
                                        )
                                    },
                                    onExportPdf = { options ->
                                        val exportNotebook = studyPaneNotebook
                                        if (exportNotebook != null) {
                                            pendingNoteExport = exportNotebook
                                            pendingNoteExportOptions = options
                                            exportNote.launch(
                                                options.suggestedFileName.ifBlank {
                                                    notebookViewModel.exportSuggestedName(exportNotebook)
                                                },
                                            )
                                        }
                                    },
                                    onOpenSourcePdf = { page ->
                                        studyPaneNotebook?.let { notebookViewModel.saveDetached(it) }
                                        studyPaneNotebook = null
                                        studyPanePageIndex = 0
                                        studyPaneDocId = null
                                        viewModel.goToPage(page)
                                    },
                                )
                            }
                        }
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
                if (BuildConfig.DEBUG && showTrace) {
                    NoteTraceSheet(onDismiss = { showTrace = false })
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
