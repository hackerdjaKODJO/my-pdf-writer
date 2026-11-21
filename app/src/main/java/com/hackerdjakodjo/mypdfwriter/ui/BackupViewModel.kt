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

/**
 * UI state for a local ZIP backup or restore.
 *
 * [restoredAt] is a one-shot event marker. Integrations should refresh their
 * notebook/library state when it changes and then call [consumeRestored].
 * [lastBackupInfo] describes the archive that was last created or restored.
 */
data class BackupUiState(
    val busy: Boolean = false,
    val progress: Int = 0,
    val message: String? = null,
    val lastBackupInfo: BackupSummary? = null,
    val restoredAt: Long? = null,
)

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
 * Runs SAF stream I/O and ZIP work off the main thread. It deliberately has no
 * reference to [NotebookViewModel] or [PdfViewModel]; restore completion is
 * exposed as the [BackupUiState.restoredAt] event for the integration layer to
 * refresh whichever view models it owns.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val operationGate = BackupOperationGate()

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Suggested name for an ACTION_CREATE_DOCUMENT launcher. */
    fun suggestedBackupName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "MyPDFWriter-backup-$stamp.zip"
    }

    fun backup(uri: Uri, includeSources: Boolean) {
        if (!operationGate.tryStart()) return
        _state.update { it.copy(busy = true, progress = 0, message = null, restoredAt = null) }
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
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 100,
                        lastBackupInfo = summary,
                        message = "백업을 만들었습니다. 파일 ${summary.fileCount}개" +
                            if (summary.includeSources) " (PDF 원본 포함)." else ".",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 0,
                        message = "백업하지 못했습니다: ${error.readableMessage()}",
                    )
                }
            } finally {
                operationGate.finish()
            }
        }
    }

    fun restore(uri: Uri) {
        if (!operationGate.tryStart()) return
        _state.update { it.copy(busy = true, progress = 0, message = null, restoredAt = null) }
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
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 100,
                        lastBackupInfo = summary,
                        restoredAt = System.currentTimeMillis(),
                        message = "복원했습니다. 파일 ${summary.fileCount}개. 목록을 새로 고칩니다.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        busy = false,
                        progress = 0,
                        message = "복원하지 못했습니다: ${error.readableMessage()}",
                    )
                }
            } finally {
                operationGate.finish()
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    /** Clears the restore event after the caller has refreshed its lists. */
    fun consumeRestored() {
        _state.update { it.copy(restoredAt = null) }
    }

    private fun percent(done: Int, total: Int): Int {
        if (total <= 0) return 0
        return ((done.toLong() * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun Exception.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "알 수 없는 오류"
}
