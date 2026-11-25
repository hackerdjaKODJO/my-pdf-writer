package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.data.BackupManager
import com.hackerdjakodjo.mypdfwriter.data.BackupSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** UI state for a local ZIP backup or restore. */
data class BackupUiState(
    val busy: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val canRetry: Boolean = false,
    val lastBackupInfo: BackupSummary? = null,
    val restoredAt: Long? = null,
    val operation: BackupOperationKind? = null,
)

enum class BackupOperationKind { BACKUP, RESTORE }

/** Which operation a failed state can retry. */
val BackupUiState.retryKind: BackupOperationKind? get() = operation

/**
 * Single-entry gate for local backup/restore. The old `busy` check-then-set
 * raced when a second SAF result arrived before the first job observed the
 * state update, starting a second manager call. [tryStart] is atomic; callers
 * must [finish] on every exit path (success, failure and cancellation).
 */
internal class BackupOperationGate {
    private val active = AtomicBoolean(false)

    val isActive: Boolean get() = active.get()

    fun tryStart(): Boolean = active.compareAndSet(false, true)

    fun finish() {
        active.set(false)
    }
}

/**
 * Host-side mutation lease. It deliberately reuses the same tiny atomic gate
 * pattern rather than taking notebook/PDF writer locks. The lease is acquired
 * before the pre-flush and remains held until the host has finished restoring
 * its in-memory state, so there is no writer-lock inversion and no window for
 * an editor/capture/external-open mutation.
 */
internal class BackupMutationLease(private val gate: BackupOperationGate = BackupOperationGate()) {
    val isActive: Boolean get() = gate.isActive

    /** Returns false when another lease is active or the pre-flush failed. */
    fun begin(flush: () -> Boolean): Boolean {
        if (!gate.tryStart()) return false
        return flush()
    }

    /** Idempotent; must run on success, failure and cancellation. */
    fun finish() = gate.finish()
}

private data class BackupRetryRequest(
    val kind: BackupOperationKind,
    val uri: Uri,
    val includeSources: Boolean = false,
)

/**
 * Runs SAF stream I/O and ZIP work off the main thread. It deliberately has no
 * reference to [NotebookViewModel] or [PdfViewModel]; restore completion is
 * exposed as the [BackupUiState.restoredAt] event for the integration layer to
 * refresh whichever view models it owns.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val operationGate = BackupOperationGate()
    private var retryRequest: BackupRetryRequest? = null
    private var awaitingRestoreConsumption = false

    /**
     * Legacy host hook retained for callers that historically threw on failure.
     * New hosts should install [flushPendingSavesBeforeBackupResult] so a false
     * result aborts before the archive/output stream is touched.
     */
    var flushPendingSavesBeforeBackup: () -> Unit = {}

    /** Boolean pre-flush used by the host mutation/lifecycle integration. */
    var flushPendingSavesBeforeBackupResult: (() -> Boolean)? = null

    /**
     * Called after every operation has released its manager gate. A successful
     * restore reports only after the host calls [consumeRestored], because the
     * in-memory notebook/viewer refresh is part of the mutation-exclusive work.
     */
    var onOperationFinished: (Boolean) -> Unit = {}

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Suggested name for an ACTION_CREATE_DOCUMENT launcher. */
    fun suggestedBackupName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "MyPDFWriter-backup-$stamp.zip"
    }

    fun backup(uri: Uri, includeSources: Boolean) {
        if (!operationGate.tryStart()) return
        val request = BackupRetryRequest(BackupOperationKind.BACKUP, uri, includeSources)
        try {
            withFlushedSaves(::flushPendingSaves) {
                _state.update {
                    it.copy(
                        busy = true,
                        progress = 0,
                        message = null,
                        error = null,
                        canRetry = false,
                        lastBackupInfo = null,
                        restoredAt = null,
                        operation = BackupOperationKind.BACKUP,
                    )
                }
            }
        } catch (error: CancellationException) {
            operationGate.finish()
            notifyOperationFinished(false)
            throw error
        } catch (error: Exception) {
            abortBeforeLaunch(request, error, "백업하지 못했습니다")
            return
        }
        viewModelScope.launch {
            var succeeded = false
            try {
                var lastPercent = -1
                val reporter: (Int, Int) -> Unit = { done, total ->
                    val percent = percent(done, total)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.update { it.copy(progress = percent) }
                    }
                }
                val summary = withContext(Dispatchers.IO) {
                    val stream = resolver.openOutputStream(uri, "wt")
                        ?: throw IOException("출력 파일을 열 수 없습니다.")
                    stream.use { output ->
                        BackupManager.create(
                            context = getApplication(),
                            out = output,
                            includeSources = includeSources,
                            onProgress = reporter,
                        )
                    }
                }
                retryRequest = null
                succeeded = true
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 100,
                        lastBackupInfo = summary,
                        message = "백업을 만들었습니다. 파일 ${summary.fileCount}개" +
                            if (summary.includeSources) " (PDF 원본 포함)." else ".",
                        error = null,
                        canRetry = false,
                        operation = null,
                    )
                }
            } catch (error: CancellationException) {
                _state.update {
                    it.copy(busy = false, progress = 0, operation = null, canRetry = false)
                }
                throw error
            } catch (error: Exception) {
                failAfterLaunch(request, error, "백업하지 못했습니다")
            } finally {
                operationGate.finish()
                notifyOperationFinished(succeeded)
            }
        }
    }

    fun restore(uri: Uri) {
        if (!operationGate.tryStart()) return
        val request = BackupRetryRequest(BackupOperationKind.RESTORE, uri)
        // Land all pending writes BEFORE replacing files, otherwise an in-flight
        // debounced save can land after the restore and overwrite restored data.
        try {
            withFlushedSaves(::flushPendingSaves) {
                _state.update {
                    it.copy(
                        busy = true,
                        progress = 0,
                        message = null,
                        error = null,
                        canRetry = false,
                        lastBackupInfo = null,
                        restoredAt = null,
                        operation = BackupOperationKind.RESTORE,
                    )
                }
            }
        } catch (error: CancellationException) {
            operationGate.finish()
            notifyOperationFinished(false)
            throw error
        } catch (error: Exception) {
            abortBeforeLaunch(request, error, "복원하지 못했습니다")
            return
        }
        viewModelScope.launch {
            try {
                var lastPercent = -1
                val reporter: (Int, Int) -> Unit = { done, total ->
                    val percent = percent(done, total)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.update { it.copy(progress = percent) }
                    }
                }
                val summary = withContext(Dispatchers.IO) {
                    val stream = resolver.openInputStream(uri)
                        ?: throw IOException("백업 파일을 열 수 없습니다.")
                    stream.use { input ->
                        BackupManager.restore(
                            context = getApplication(),
                            input = input,
                            onProgress = reporter,
                        )
                    }
                }
                retryRequest = null
                awaitingRestoreConsumption = true
                // Keep both gates and the sheet busy until the host has discarded
                // the detached pane, refreshed its libraries and dropped stale
                // viewer/notebook state. This is part of restore completion.
                _state.update {
                    it.copy(
                        busy = true,
                        progress = 100,
                        lastBackupInfo = summary,
                        restoredAt = System.currentTimeMillis(),
                        message = "복원했습니다. 파일 ${summary.fileCount}개. 목록을 새로 고칩니다.",
                        error = null,
                        canRetry = false,
                        operation = BackupOperationKind.RESTORE,
                    )
                }
            } catch (error: CancellationException) {
                awaitingRestoreConsumption = false
                _state.update {
                    it.copy(busy = false, progress = 0, operation = null, canRetry = false)
                }
                operationGate.finish()
                notifyOperationFinished(false)
                throw error
            } catch (error: Exception) {
                awaitingRestoreConsumption = false
                failAfterLaunch(request, error, "복원하지 못했습니다")
                operationGate.finish()
                notifyOperationFinished(false)
            }
        }
    }

    /** Records a SAF-callback failure without throwing out of the Activity callback. */
    fun reportBackupFailure(uri: Uri, includeSources: Boolean, error: Throwable) {
        abortBeforeLaunch(
            BackupRetryRequest(BackupOperationKind.BACKUP, uri, includeSources),
            error as? Exception ?: RuntimeException(error),
            "백업하지 못했습니다",
        )
    }

    /** Records a restore failure with an exact retry snapshot. */
    fun reportRestoreFailure(uri: Uri, error: Throwable) {
        abortBeforeLaunch(
            BackupRetryRequest(BackupOperationKind.RESTORE, uri),
            error as? Exception ?: RuntimeException(error),
            "복원하지 못했습니다",
        )
    }

    /** Retries the exact URI/options snapshot; never substitutes current mutable state. */
    fun retry() {
        val request = retryRequest ?: return
        when (request.kind) {
            BackupOperationKind.BACKUP -> backup(request.uri, request.includeSources)
            BackupOperationKind.RESTORE -> restore(request.uri)
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Clears the restore event after the caller has refreshed its lists. This is
     * also the successful-restore completion point for the manager/host gates.
     */
    fun consumeRestored() {
        if (!awaitingRestoreConsumption && _state.value.restoredAt == null) return
        awaitingRestoreConsumption = false
        _state.update { it.copy(busy = false, restoredAt = null, operation = null) }
        operationGate.finish()
        notifyOperationFinished(true)
    }

    private fun flushPendingSaves(): Boolean =
        flushPendingSavesBeforeBackupResult?.invoke() ?: run {
            flushPendingSavesBeforeBackup()
            true
        }

    private fun abortBeforeLaunch(
        request: BackupRetryRequest,
        error: Exception,
        prefix: String,
    ) {
        retryRequest = request
        _state.update {
            it.copy(
                busy = false,
                progress = 0,
                message = null,
                error = "$prefix: ${error.readableMessage()}",
                canRetry = true,
                lastBackupInfo = null,
                restoredAt = null,
                operation = null,
            )
        }
        operationGate.finish()
        notifyOperationFinished(false)
    }

    private fun failAfterLaunch(
        request: BackupRetryRequest,
        error: Exception,
        prefix: String,
    ) {
        retryRequest = request
        _state.update {
            it.copy(
                busy = false,
                progress = 0,
                message = null,
                error = "$prefix: ${error.readableMessage()}",
                canRetry = true,
                lastBackupInfo = null,
                restoredAt = null,
                operation = null,
            )
        }
    }

    private fun notifyOperationFinished(succeeded: Boolean) {
        // A host callback is bookkeeping, never a reason to strand the manager
        // gate or crash an ActivityResult callback after the real work finished.
        runCatching { onOperationFinished(succeeded) }
    }

    private fun percent(done: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((done.toLong() * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun Exception.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "알 수 없는 오류"
}

/** Thrown only inside BackupViewModel when a Boolean pre-flush reports failure. */
internal class BackupFlushException : IllegalStateException("저장을 완료하지 못했습니다.")

/**
 * Runs [flush] before [action] exactly once. A false flush result aborts without
 * touching the archive/output stream; BackupViewModel turns it into retryable UI
 * error state instead of letting it escape an ActivityResult callback.
 */
internal inline fun <T> withFlushedSaves(flush: () -> Boolean, action: () -> T): T {
    if (!flush()) throw BackupFlushException()
    return action()
}
