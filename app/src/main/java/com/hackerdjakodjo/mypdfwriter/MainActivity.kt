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
import com.hackerdjakodjo.mypdfwriter.data.effectiveStudyLink
import com.hackerdjakodjo.mypdfwriter.pdf.NoteExportOptions
import com.hackerdjakodjo.mypdfwriter.ui.BackupMutationLease
import com.hackerdjakodjo.mypdfwriter.ui.BackupSheet
import com.hackerdjakodjo.mypdfwriter.ui.BackupOperationKind
import com.hackerdjakodjo.mypdfwriter.ui.BackupViewModel
import com.hackerdjakodjo.mypdfwriter.ui.retryKind
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
import com.hackerdjakodjo.mypdfwriter.ui.StudyLinkResolution
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.ui.QuickDestination
import com.hackerdjakodjo.mypdfwriter.ui.STUDY_PANE_DEFAULT_FRACTION
import com.hackerdjakodjo.mypdfwriter.ui.StudyNoteBuilder
import com.hackerdjakodjo.mypdfwriter.ui.StudyPaneSheet
import com.hackerdjakodjo.mypdfwriter.ui.StudySource
import com.hackerdjakodjo.mypdfwriter.ui.ToolsScreen
import com.hackerdjakodjo.mypdfwriter.ui.ViewerScreen
import com.hackerdjakodjo.mypdfwriter.ui.MULTI_IMPORT_NOTICE
import com.hackerdjakodjo.mypdfwriter.ui.NOTE_SAVE_FAILURE as NOTE_SAVE_FAILURE_MESSAGE
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

internal data class PendingViewIntent(val uri: String)

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
    /** Acquired before the pre-flush and held until restore host state is refreshed. */
    private val mutationLease = BackupMutationLease()
    private var pendingHostRestore = false
    private val queuedViewIntents = ArrayDeque<PendingViewIntent>()

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
        // The launch snapshot is authoritative. If process recreation lost it,
        // fail closed instead of exporting a different mutable current notebook.
        if (uri != null && detached != null) {
            notebookViewModel.exportDetached(detached, uri, options)
        }
    }

    private val saveBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        pendingHostRestore = false
        if (uri != null) {
            runCatching { backupViewModel.backup(uri, backupIncludeSources) }.onFailure { error ->
                backupViewModel.reportBackupFailure(uri, backupIncludeSources, error)
                finishHostOperation()
            }
        }
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            restoreStartDocId = null
            pendingHostRestore = false
        } else {
            pendingHostRestore = true
            runCatching { backupViewModel.restore(uri) }.onFailure { error ->
                backupViewModel.reportRestoreFailure(uri, error)
                finishHostOperation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        backupViewModel.flushPendingSavesBeforeBackupResult = {
            mutationLease.begin { flushHostState() }
        }
        backupViewModel.onOperationFinished = { succeeded ->
            if (pendingHostRestore && succeeded) {
                // consumeRestored releases after pane/viewer/notebook refresh.
            } else {
                finishHostOperation()
            }
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
                        // Never save a pre-restore detached pane over restored JSON.
                        discardStudyPane = {
                            studyPaneNotebook = null
                            studyPanePageIndex = 0
                            studyPaneDocId = null
                        },
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
                            finishHostOperation()
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
                // is replaced elsewhere, save the note and drop the stale pane. A
                // failed verified save is surfaced and the stale pane is still dropped
                // so it can never accept another document's capture.
                LaunchedEffect(viewer?.docId) {
                    val pane = studyPaneNotebook ?: return@LaunchedEffect
                    val docId = viewer?.docId
                    if (docId == null || studyPaneDocId != docId) {
                        if (notebookViewModel.trySaveDetached(pane) == null) {
                            viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                        }
                        studyPaneNotebook = null
                        studyPanePageIndex = 0
                        studyPaneDocId = null
                    }
                }
                val openStudyPane: (Notebook) -> Unit = { notebook ->
                    if (!mutationLease.isActive) {
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
                }
                val closeStudyPane: () -> Boolean = {
                    val pane = studyPaneNotebook
                    if (pane == null) {
                        true
                    } else if (mutationLease.isActive) {
                        false
                    } else if (notebookViewModel.trySaveDetached(pane) == null) {
                        viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                        false
                    } else {
                        studyPaneNotebook = null
                        studyPanePageIndex = 0
                        studyPaneDocId = null
                        true
                    }
                }
                val openViewerStudyPane: () -> Unit = {
                    if (!mutationLease.isActive) {
                        val doc = viewer
                        if (doc != null) {
                            val source = StudySource(
                                docName = doc.docName,
                                docUri = viewModel.currentDocumentUri(),
                                docId = doc.docId,
                                contentFingerprint = doc.contentFingerprint,
                            )
                            val notebook = notebookViewModel.studyNoteFor(source)
                                ?: notebookViewModel.createBlankStudyNote(source)
                            if (notebook != null) {
                                openStudyPane(notebook)
                            } else {
                                viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                            }
                        }
                    }
                }
                val runMutation: (() -> Unit) -> Unit = { action ->
                    if (!mutationLease.isActive) action()
                }
                val jumpToNoteSource: (Notebook, Int) -> Boolean = { notebook, page ->
                    if (mutationLease.isActive) {
                        false
                    } else {
                        val link = notebook.pages.getOrNull(page)?.effectiveStudyLink(notebook)
                        if (link != null) {
                            when (val resolution = viewModel.jumpToStudyLink(link)) {
                                is StudyLinkResolution.Found -> true
                                StudyLinkResolution.WrongDocument -> {
                                    viewModel.postLibraryMessage("현재 PDF와 다른 문서의 링크입니다. 노트는 열어 둡니다.")
                                    false
                                }
                                StudyLinkResolution.SourceChanged -> {
                                    viewModel.postLibraryMessage("원본 PDF가 바뀌었습니다. 노트는 열어 둡니다.")
                                    false
                                }
                                StudyLinkResolution.PageMissing -> {
                                    viewModel.postLibraryMessage("원본 페이지를 찾을 수 없습니다. 노트는 열어 둡니다.")
                                    false
                                }
                                StudyLinkResolution.SourceUnavailable -> {
                                    viewModel.postLibraryMessage("원본 PDF를 사용할 수 없습니다. 노트는 열어 둡니다.")
                                    false
                                }
                            }
                        } else {
                            val uri = notebook.sourceDocumentUri
                            if (uri.isBlank()) {
                                viewModel.postLibraryMessage("원본 PDF를 사용할 수 없습니다. 노트는 열어 둡니다.")
                                false
                            } else {
                                // Legacy notes have no durable locator; preserve their
                                // root URI/page fallback without claiming a verified link.
                                viewModel.openDocumentAt(Uri.parse(uri), page)
                                true
                            }
                        }
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val note = currentNote
                    when {
                        note != null -> NoteEditorScreen(
                            notebook = note,
                            pageIndex = notePage,
                            onBack = {
                                runMutation {
                                    if (!notebookViewModel.close()) {
                                        viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                    }
                                }
                            },
                            onChange = { updated -> runMutation { notebookViewModel.updateCurrent(updated) } },
                            onPageIndexChange = { index ->
                                runMutation { notebookViewModel.setPageIndex(index) }
                            },
                            onExportPdf = { options ->
                                runMutation {
                                    pendingNoteExport = note
                                    pendingNoteExportOptions = options
                                    exportNote.launch(
                                        options.suggestedFileName.ifBlank {
                                            notebookViewModel.exportSuggestedName()
                                        },
                                    )
                                }
                            },
                            onOpenSourcePdf = { page ->
                                runMutation {
                                    if (jumpToNoteSource(note, page) && notebookViewModel.close()) {
                                        showTools = false
                                    }
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
                                runMutation {
                                    showTools = false
                                    viewModel.openDocument(uri)
                                }
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
                                onOpenNotebook = { runMutation { notebookViewModel.open(it) } },
                                onCreateNotebook = { draft ->
                                    runMutation { notebookViewModel.create(draft) }
                                },
                                onOpenPdf = { doc ->
                                    runMutation { viewModel.openDocument(Uri.parse(doc.uri)) }
                                },
                                onImportPdf = {
                                    runMutation { openPdf.launch(arrayOf("application/pdf")) }
                                },
                                onCreatePdf = {
                                    runMutation { createPdf.launch("새 문서.pdf") }
                                },
                                onOpenTools = { runMutation { showTools = true } },
                                onToggleFavorite = { runMutation { notebookViewModel.toggleFavorite(it) } },
                                onTrash = { runMutation { notebookViewModel.trash(it) } },
                                onRestore = { runMutation { notebookViewModel.restore(it) } },
                                onDeleteForever = { runMutation { notebookViewModel.deleteForever(it) } },
                                onRename = { id, title ->
                                    runMutation { notebookViewModel.rename(id, title) }
                                },
                                onMoveFolder = { id, folder ->
                                    runMutation { notebookViewModel.moveFolder(id, folder) }
                                },
                                onSetTags = { id, tags ->
                                    runMutation { notebookViewModel.setTags(id, tags) }
                                },
                                onOpenBackup = { runMutation { showBackup = true } },
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
                                docId = viewer?.docId,
                                contentFingerprint = viewer?.contentFingerprint,
                            )
                            val appendStudyExcerpt: (String, Int) -> Boolean = { text, page ->
                                if (mutationLease.isActive || viewer == null) {
                                    false
                                } else {
                                    val result = notebookViewModel.appendStudyExcerptResult(
                                        studySource,
                                        text,
                                        page,
                                    )
                                    if (result.saved) {
                                        openStudyPane(result.notebook)
                                        studyPanePageIndex = result.pageIndex.coerceAtLeast(0)
                                        true
                                    } else {
                                        result.error?.let(viewModel::postLibraryMessage)
                                        false
                                    }
                                }
                            }
                            val paneNotebook = studyPaneNotebook
                            ViewerScreen(
                                vm = viewModel,
                                onExportRequest = { suggestedName ->
                                    runMutation {
                                        pendingPdfExport = viewModel.captureExportSnapshot()
                                        if (pendingPdfExport != null) exportPdf.launch(suggestedName)
                                    }
                                },
                                onOpenTools = { runMutation { showTools = true } },
                                onCreateStudyNote = { notebook ->
                                    runMutation {
                                        // Keep the PDF open: the captured note opens in
                                        // the detached pane only after verified persistence.
                                        val saved = notebookViewModel.trySaveDetached(notebook)
                                        if (saved != null) {
                                            openStudyPane(saved)
                                        } else {
                                            viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                        }
                                    }
                                },
                                onAppendStudyExcerpt = appendStudyExcerpt,
                                onOpenStudyNote = { source ->
                                    runMutation {
                                        val notebook = notebookViewModel.studyNoteFor(source)
                                            ?: notebookViewModel.createBlankStudyNote(source)
                                        if (notebook != null) {
                                            openStudyPane(notebook)
                                        } else {
                                            viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                        }
                                    }
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
                                onCloseStudyPane = { closeStudyPane() },
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
                                            onDismiss = { closeStudyPane() },
                                            onChange = { updated ->
                                                if (!mutationLease.isActive) {
                                                    val saved = notebookViewModel.trySaveDetached(updated)
                                                    if (saved != null) {
                                                        studyPaneNotebook = saved
                                                    } else {
                                                        viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                                    }
                                                }
                                            },
                                            onPageIndexChange = { index ->
                                                if (!mutationLease.isActive) {
                                                    val latest = studyPaneNotebook
                                                    if (latest != null) {
                                                        val saved = notebookViewModel.setDetachedPageIndex(
                                                            latest,
                                                            index,
                                                        )
                                                        if (saved != null) {
                                                            studyPaneNotebook = saved
                                                            studyPanePageIndex = saved.lastPageIndex
                                                        } else {
                                                            viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                                        }
                                                    }
                                                }
                                            },
                                            onExportPdf = { options ->
                                                if (!mutationLease.isActive) {
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
                                                }
                                            },
                                            onOpenSourcePdf = { page ->
                                                val pane = studyPaneNotebook
                                                if (pane != null && jumpToNoteSource(pane, page)) {
                                                    closeStudyPane()
                                                }
                                            },
                                            onCaptureExcerpt = appendStudyExcerpt,
                                            onJumpToSourcePage = { _, notePage ->
                                                val pane = studyPaneNotebook
                                                if (pane != null && jumpToNoteSource(pane, notePage)) {
                                                    val saved = notebookViewModel.setDetachedPageIndex(
                                                        pane,
                                                        notePage,
                                                    )
                                                    if (saved != null) {
                                                        studyPaneNotebook = saved
                                                        studyPanePageIndex = saved.lastPageIndex
                                                    } else {
                                                        viewModel.postLibraryMessage(NOTE_SAVE_FAILURE_MESSAGE)
                                                    }
                                                }
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
                        onDismiss = {
                            if (!backupState.busy && !mutationLease.isActive) showBackup = false
                        },
                        onBackup = { includeSources ->
                            if (!backupState.busy && !mutationLease.isActive) {
                                backupIncludeSources = includeSources
                                saveBackup.launch(backupViewModel.suggestedBackupName())
                            }
                        },
                        onRestore = {
                            if (!backupState.busy && !mutationLease.isActive) {
                                restoreStartDocId = viewer?.docId
                                openBackup.launch(arrayOf("application/zip", "application/octet-stream"))
                            }
                        },
                        busy = backupState.busy,
                        progress = backupState.progress,
                        message = backupState.message,
                        error = backupState.error,
                        canRetry = backupState.canRetry,
                        onRetry = {
                            if (!backupState.busy && !mutationLease.isActive) {
                                pendingHostRestore = backupState.retryKind == BackupOperationKind.RESTORE
                                if (pendingHostRestore) restoreStartDocId = viewer?.docId
                                backupViewModel.retry()
                            }
                        },
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

    private fun flushHostState(): Boolean {
        val notebookOk = notebookViewModel.saveNow()
        viewModel.flushPdfSavesNow()
        return notebookOk
    }

    private fun finishHostOperation() {
        mutationLease.finish()
        while (queuedViewIntents.isNotEmpty()) {
            viewModel.openDocument(Uri.parse(queuedViewIntents.removeFirst().uri), addToRecents = false)
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
        if (mutationLease.isActive) {
            queuedViewIntents.add(PendingViewIntent(rawUri))
            return
        }
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
    discardStudyPane: () -> Unit = {},
    closeDocument: () -> Unit,
    consumeRestored: () -> Unit,
    shouldCloseDocument: () -> Boolean = { true },
) {
    if (restoredAt == null) return
    // A stale in-memory working notebook or detached pane can be written back
    // over restored JSON. Drop both before any refresh or viewer close.
    discardStudyPane()
    discardNotebook()
    refreshNotebook()
    reloadLibrary()
    if (shouldCloseDocument()) closeDocument()
    consumeRestored()
}
