package com.hackerdjakodjo.mypdfwriter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hackerdjakodjo.mypdfwriter.data.Excerpt
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.ui.BackupSheet
import com.hackerdjakodjo.mypdfwriter.ui.BackupViewModel
import com.hackerdjakodjo.mypdfwriter.ui.HelpCenterSheet
import com.hackerdjakodjo.mypdfwriter.ui.HelpTopicKey
import com.hackerdjakodjo.mypdfwriter.ui.ImportIntentSnapshot
import com.hackerdjakodjo.mypdfwriter.ui.NoteEditorScreen
import com.hackerdjakodjo.mypdfwriter.ui.toolHandoffFile
import com.hackerdjakodjo.mypdfwriter.ui.NoteTraceSheet
import com.hackerdjakodjo.mypdfwriter.ui.NotebookHomeScreen
import com.hackerdjakodjo.mypdfwriter.ui.NotebookViewModel
import com.hackerdjakodjo.mypdfwriter.ui.OnboardingSheet
import com.hackerdjakodjo.mypdfwriter.ui.PdfExportSnapshot
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.ui.QuickDestination
import com.hackerdjakodjo.mypdfwriter.ui.STUDY_PANE_DEFAULT_FRACTION
import com.hackerdjakodjo.mypdfwriter.ui.StudyNoteBuilder
import com.hackerdjakodjo.mypdfwriter.ui.StudyPaneSheet
import com.hackerdjakodjo.mypdfwriter.ui.StudySource
import com.hackerdjakodjo.mypdfwriter.ui.ToolsScreen
import com.hackerdjakodjo.mypdfwriter.ui.ViewerScreen
import com.hackerdjakodjo.mypdfwriter.ui.MULTI_IMPORT_NOTICE
import com.hackerdjakodjo.mypdfwriter.ui.clampStudyPaneFraction
import com.hackerdjakodjo.mypdfwriter.ui.isMultiImport
import com.hackerdjakodjo.mypdfwriter.ui.pdfImportActions
import com.hackerdjakodjo.mypdfwriter.ui.resolveImportUri
import com.hackerdjakodjo.mypdfwriter.ui.showHomeOnboardingPrompt
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme

/** Reopening the same study notebook keeps the page (e.g. a just-captured excerpt). */
internal fun studyPanePageIndexAfterOpen(
    currentId: String?,
    incomingId: String,
    currentIndex: Int,
): Int = if (currentId == incomingId) currentIndex.coerceAtLeast(0) else 0

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewModel by viewModels()
    private val notebookViewModel: NotebookViewModel by viewModels()
    private val backupViewModel: BackupViewModel by viewModels()
    private val onboardingStore by lazy { OnboardingStore(applicationContext) }
    private var backupIncludeSources = false
    /** Set only by the PDF study pane; the activity result then exports this detached note. */
    private var pendingNoteExport: Notebook? = null
    /** Page-range/layer choices paired with [pendingNoteExport] or the current editor. */
    private var pendingNoteExportOptions: NoteExportOptions = NoteExportOptions()
    /** Captured when the SAF export picker opens, so a doc switch cannot redirect it. */
    private var pendingPdfExport: PdfExportSnapshot? = null
    /** Document showing when restore began; only that doc may be discarded on completion. */
    private var restoreStartDocId: String? = null

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.openDocument(it) }
    }

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { viewModel.createBlankDocument(it) }
    }

    private val exportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = pendingPdfExport
        pendingPdfExport = null
        if (uri != null && snapshot != null) viewModel.exportTo(snapshot, uri)
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
        backupViewModel.flushPendingSavesBeforeBackup = {
            notebookViewModel.saveNow()
            // PDF doc-state writes are normally debounced on IO; drain them now
            // so the backup ZIP cannot miss the newest annotation/page state.
            viewModel.flushPdfSavesNow()
        }
        handleViewIntent(intent)

        setContent {
            val libraryMessage by viewModel.libraryMessage.collectAsStateWithLifecycle()
            MyPdfWriterTheme {
                var showTools by remember { mutableStateOf(false) }
                var showBackup by remember { mutableStateOf(false) }
                var showTrace by remember { mutableStateOf(false) }
                var showHelp by remember { mutableStateOf(false) }
                var helpTopic by remember { mutableStateOf<HelpTopicKey?>(null) }
                var showOnboarding by remember { mutableStateOf(false) }
                var onboardingSeen by remember { mutableStateOf(onboardingStore.hasSeen()) }
                val homeStateHolder = rememberSaveableStateHolder()
                val viewer by viewModel.viewer.collectAsStateWithLifecycle()
                val notebooks by notebookViewModel.notebooks.collectAsStateWithLifecycle()
                val currentNote by notebookViewModel.current.collectAsStateWithLifecycle()
                val notePage by notebookViewModel.pageIndex.collectAsStateWithLifecycle()
                val pdfs by viewModel.library.collectAsStateWithLifecycle()
                val backupState by backupViewModel.state.collectAsStateWithLifecycle()
                var studyPaneNotebook by remember { mutableStateOf<Notebook?>(null) }
                var studyPanePageIndex by remember { mutableStateOf(0) }
                var studyPaneDocId by remember { mutableStateOf<String?>(null) }
                // Session-only: survives note page changes and PDF page changes
                // while the pane stays open, and resets with activity recreation.
                var studyPaneFraction by remember {
                    mutableFloatStateOf(STUDY_PANE_DEFAULT_FRACTION)
                }
                LaunchedEffect(backupState.restoredAt) {
                    onBackupRestored(
                        restoredAt = backupState.restoredAt,
                        discardNotebook = { notebookViewModel.discardCurrent() },
                        refreshNotebook = { notebookViewModel.refresh() },
                        reloadLibrary = { viewModel.reloadLibrary() },
                        closeDocument = { viewModel.discardDocument() },
                        shouldCloseDocument = {
                            shouldCloseAfterRestore(restoreStartDocId, viewer?.docId)
                        },
                        consumeRestored = {
                            backupViewModel.consumeRestored()
                            restoreStartDocId = null
                        },
                    )
                }
                val markOnboardingSeen: () -> Unit = {
                    onboardingStore.markSeen()
                    onboardingSeen = true
                }
                val openOnboarding: () -> Unit = {
                    // Starting the tutorial is an explicit first-run action; merely
                    // rendering Home or Help never consumes the optional prompt.
                    markOnboardingSeen()
                    showOnboarding = true
                }
                val closeOnboarding: () -> Unit = {
                    markOnboardingSeen()
                    showOnboarding = false
                }
                val startOnboardingImport: () -> Unit = {
                    closeOnboarding()
                    openPdf.launch(arrayOf("application/pdf"))
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
                    // Capturing to an already-open pane appends a page and moves
                    // the pane there. Reopening the same notebook must not snap
                    // back to page 1 and hide the excerpt the user just added.
                    studyPanePageIndex = studyPanePageIndexAfterOpen(
                        studyPaneNotebook?.id,
                        notebook.id,
                        studyPanePageIndex,
                    )
                    studyPaneNotebook = notebook
                    studyPaneDocId = viewer?.docId
                }
                val closeStudyPane: () -> Unit = {
                    studyPaneNotebook?.let { notebookViewModel.saveDetached(it) }
                    studyPaneNotebook = null
                    studyPanePageIndex = 0
                    studyPaneDocId = null
                }
                val openViewerStudyPane: () -> Unit = {
                    val doc = viewer
                    if (doc != null) {
                        val source = StudySource(
                            docName = doc.docName,
                            docUri = viewModel.currentDocumentUri(),
                        )
                        (notebookViewModel.studyNoteFor(source)
                            ?: notebookViewModel.createBlankStudyNote(source))?.let { openStudyPane(it) }
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
                            onOpenHelp = {
                                helpTopic = HelpTopicKey.WRITE_TOOLS
                                showHelp = true
                            },
                        )
                        showTools -> ToolsScreen(
                            onBack = { showTools = false },
                            onOpen = { uri ->
                                showTools = false
                                viewModel.openDocument(uri)
                            },
                            // Prefer the assembled working file: after page edits the
                            // pristine source would silently discard them in tools.
                            initialSource = toolHandoffFile(
                                viewModel.currentWorkingFile(),
                                viewModel.currentSourceFile(),
                            ),
                            initialName = viewer?.docName.orEmpty(),
                        )
                        viewer == null -> homeStateHolder.SaveableStateProvider("home") {
                            NotebookHomeScreen(
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
                                onOpenHelp = {
                                    helpTopic = null
                                    showHelp = true
                                },
                                showOnboardingPrompt = showHomeOnboardingPrompt(onboardingSeen),
                                onOpenOnboarding = openOnboarding,
                                onDismissOnboardingPrompt = markOnboardingSeen,
                                onRemovePdfRecent = { uri ->
                                    viewModel.removeRecent(uri, deleteAnnotations = false)
                                },
                                libraryMessage = libraryMessage,
                                onDismissLibraryMessage = { viewModel.clearLibraryMessage() },
                            )
                        }
                        else -> Box(Modifier.fillMaxSize()) {
                            val studySource = StudySource(
                                docName = viewer?.docName.orEmpty(),
                                docUri = viewModel.currentDocumentUri(),
                            )
                            // With the pane open, captures go straight to the pane
                            // notebook instead of re-resolving a library match.
                            val appendToOpenPane: (String, Int) -> Boolean = { text, page ->
                                val pane = studyPaneNotebook
                                if (pane == null) {
                                    false
                                } else {
                                    val updated = StudyNoteBuilder.appendExcerpt(
                                        notebook = pane,
                                        source = studySource,
                                        excerpt = Excerpt(
                                            pageIndex = page.coerceAtLeast(0),
                                            text = text.trim(),
                                        ),
                                    )
                                    if (updated == pane) {
                                        false
                                    } else {
                                        val saved = notebookViewModel.saveDetached(updated)
                                        studyPaneNotebook = saved
                                        studyPanePageIndex =
                                            (saved.pages.size - 1).coerceAtLeast(0)
                                        true
                                    }
                                }
                            }
                            val paneNotebook = studyPaneNotebook
                            ViewerScreen(
                                vm = viewModel,
                                onExportRequest = { suggestedName ->
                                    pendingPdfExport = viewModel.captureExportSnapshot()
                                    exportPdf.launch(suggestedName)
                                },
                                onOpenTools = { showTools = true },
                                onCreateStudyNote = { notebook ->
                                    // Keep the PDF open: the captured note opens in
                                    // the detached pane over this page.
                                    openStudyPane(notebookViewModel.saveDetached(notebook))
                                },
                                onAppendStudyExcerpt = { text, page ->
                                    if (studyPaneNotebook == null) {
                                        notebookViewModel.appendStudyExcerpt(
                                            studySource,
                                            text,
                                            page,
                                        )
                                        true
                                    } else {
                                        appendToOpenPane(text, page)
                                    }
                                },
                                onOpenStudyNote = { source ->
                                    (notebookViewModel.studyNoteFor(source)
                                        ?: notebookViewModel.createBlankStudyNote(source))?.let { openStudyPane(it) }
                                },
                                onOpenHelp = {
                                    helpTopic = null
                                    showHelp = true
                                },
                                onOpenHelpTopic = { topic ->
                                    helpTopic = topic
                                    showHelp = true
                                },
                                studyPaneFraction = studyPaneFraction,
                                onCloseStudyPane = closeStudyPane,
                                onStudyPaneFractionChange = { fraction ->
                                    studyPaneFraction = clampStudyPaneFraction(fraction)
                                },
                                studyPane = if (paneNotebook != null) {
                                    {
                                        StudyPaneSheet(
                                            notebook = paneNotebook,
                                            pageIndex = studyPanePageIndex,
                                            heightFraction = studyPaneFraction,
                                            onHeightFractionChange = { fraction ->
                                                studyPaneFraction =
                                                    clampStudyPaneFraction(fraction)
                                            },
                                            onDismiss = closeStudyPane,
                                            onChange = { updated ->
                                                studyPaneNotebook =
                                                    notebookViewModel.saveDetached(updated)
                                            },
                                            onPageIndexChange = { index ->
                                                val latest = studyPaneNotebook
                                                studyPanePageIndex = index.coerceIn(
                                                    0,
                                                    ((latest?.pages?.size ?: 1) - 1)
                                                        .coerceAtLeast(0),
                                                )
                                            },
                                            onExportPdf = { options ->
                                                val exportNotebook = studyPaneNotebook
                                                if (exportNotebook != null) {
                                                    pendingNoteExport = exportNotebook
                                                    pendingNoteExportOptions = options
                                                    exportNote.launch(
                                                        options.suggestedFileName.ifBlank {
                                                            notebookViewModel.exportSuggestedName(
                                                                exportNotebook,
                                                            )
                                                        },
                                                    )
                                                }
                                            },
                                            onOpenSourcePdf = { page ->
                                                closeStudyPane()
                                                viewModel.jumpToSourcePage(page)
                                            },
                                            onCaptureExcerpt = appendToOpenPane,
                                            onJumpToSourcePage = { sourcePage, notePage ->
                                                studyPaneNotebook?.let {
                                                    notebookViewModel.saveDetached(it)
                                                }
                                                studyPanePageIndex = notePage.coerceIn(
                                                    0,
                                                    ((studyPaneNotebook?.pages?.size ?: 1) - 1)
                                                        .coerceAtLeast(0),
                                                )
                                                // Keep the pane open so the note and the
                                                // PDF page move together. Source-link jumps
                                                // stay available even while the page is locked.
                                                viewModel.jumpToSourcePage(sourcePage)
                                            },
                                            onOpenHelp = {
                                                helpTopic = HelpTopicKey.STUDY_NOTE
                                                showHelp = true
                                            },
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
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
                            restoreStartDocId = viewer?.docId
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
                if (showHelp) {
                    HelpCenterSheet(
                        onDismiss = {
                            showHelp = false
                            helpTopic = null
                        },
                        onReplayTutorial = {
                            showHelp = false
                            openOnboarding()
                        },
                        onQuickAction = { destination ->
                            when (destination) {
                                QuickDestination.OPEN_PDF -> {
                                    if (currentNote == null) {
                                        showHelp = false
                                        helpTopic = null
                                        openPdf.launch(arrayOf("application/pdf"))
                                    } else {
                                        // Leave the note editor intact if the student
                                        // cancels the picker; explain the Home path instead.
                                        helpTopic = HelpTopicKey.OPEN_PDF
                                    }
                                }
                                QuickDestination.MARKUP_TOOLS -> {
                                    if (viewer != null) {
                                        showHelp = false
                                        viewModel.setTool(Tool.HIGHLIGHT)
                                    } else {
                                        helpTopic = HelpTopicKey.TEXT_SELECTION
                                    }
                                }
                                QuickDestination.NOTE_PANE -> {
                                    if (viewer != null) {
                                        showHelp = false
                                        openViewerStudyPane()
                                    } else {
                                        helpTopic = HelpTopicKey.STUDY_NOTE
                                    }
                                }
                                QuickDestination.REVIEW_CARDS -> {
                                    // The note editor owns the card sheet; until it is
                                    // open, keep Help focused on the exact path.
                                    helpTopic = HelpTopicKey.WRITE_TOOLS
                                }
                            }
                        },
                        initialTopic = helpTopic,
                    )
                }
                if (showOnboarding) {
                    OnboardingSheet(
                        onDismiss = closeOnboarding,
                        onComplete = closeOnboarding,
                        onStartImport = startOnboardingImport,
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
        // Resolve through a pure helper so URI precedence stays unit-testable.
        val snapshot = ImportIntentSnapshot(
            action = intent?.action,
            dataUri = intent?.data?.toString(),
            streamUri = intent?.let {
                IntentCompat.getParcelableExtra(it, Intent.EXTRA_STREAM, Uri::class.java)
            }?.toString(),
            clipUris = intent?.clipData?.let { clip ->
                (0 until clip.itemCount).mapNotNull { index ->
                    clip.getItemAt(index)?.uri?.toString()
                }
            } ?: emptyList(),
        )
        val rawUri = resolveImportUri(snapshot) ?: return
        if (isMultiImport(snapshot.clipUris.size)) {
            viewModel.postLibraryMessage(MULTI_IMPORT_NOTICE)
        }
        // Consume every URI carrier: a redelivered intent (onNewIntent or
        // recreation with the same Intent) must not open the same PDF twice.
        clearIntentCarriers(object : IntentCarrierSink {
            override fun clearData() {
                intent?.data = null
            }

            override fun clearExtraStream() {
                intent?.removeExtra(Intent.EXTRA_STREAM)
            }

            override fun clearClipData() {
                intent?.clipData = null
            }
        })
        val uri = Uri.parse(rawUri)
        notebookViewModel.close()
        // Any PDF the student opens (picker, share sheet, or external viewer)
        // joins recents so Home can resume it at the same page.
        viewModel.openDocument(uri, addToRecents = shouldAddToRecents(intent?.action))
    }
}

/** The three URI carriers a real Intent can hold, exposed without Android types. */
internal interface IntentCarrierSink {
    fun clearData()
    fun clearExtraStream()
    fun clearClipData()
}

/** Removes every URI carrier so a redelivered intent cannot re-import a PDF. Idempotent. */
internal fun clearIntentCarriers(sink: IntentCarrierSink) {
    sink.clearData()
    sink.clearExtraStream()
    sink.clearClipData()
}

/** Every supported import path joins local recents so resume works everywhere. */
internal fun shouldAddToRecents(action: String?): Boolean = action in pdfImportActions

/**
 * A PDF opened *during* a restore must survive its completion. Only discard the
 * viewer when it still shows the document that was active when restore started;
 * a null start document (Home) means there is nothing to discard.
 */
internal fun shouldCloseAfterRestore(docIdAtStart: String?, activeDocId: String?): Boolean =
    docIdAtStart != null && docIdAtStart == activeDocId

/**
 * One-shot restore bookkeeping. Refresh the notebook/library lists, then drop
 * the stale viewer so the next open reads the restored sidecars from disk.
 * No forced navigation home is needed: closing the viewer returns to Home.
 */
internal fun onBackupRestored(
    restoredAt: Long?,
    refreshNotebook: () -> Unit,
    reloadLibrary: () -> Unit,
    discardNotebook: () -> Unit = {},
    closeDocument: () -> Unit,
    consumeRestored: () -> Unit,
    shouldCloseDocument: () -> Boolean = { true },
) {
    if (restoredAt == null) return
    // A stale in-memory working notebook can be written back over restored JSON.
    discardNotebook()
    refreshNotebook()
    reloadLibrary()
    if (shouldCloseDocument()) closeDocument()
    consumeRestored()
}
