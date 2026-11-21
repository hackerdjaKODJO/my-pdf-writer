package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.onBackupRestored
import com.hackerdjakodjo.mypdfwriter.shouldCloseAfterRestore
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Backup/restore E2E coverage of the two race fixes: the atomic single-entry gate
 * that stops a second SAF result from launching a second manager call, the flush
 * that lands pending notebook saves before a ZIP is made, and the one-shot restore
 * completion that discards stale editor state without closing a document opened
 * while the restore was running.
 *
 * Gate/ordering checks are plain JUnit; the sheet checks use a Compose rule.
 */
class BackupRestoreE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderSheet(
        busy: Boolean,
        progress: Int = 0,
        message: String? = null,
        onBackup: (Boolean) -> Unit = {},
        onRestore: () -> Unit = {},
    ) {
        composeRule.setContent {
            MyPdfWriterTheme {
                BackupSheet(
                    onDismiss = {},
                    onBackup = onBackup,
                    onRestore = onRestore,
                    busy = busy,
                    progress = progress,
                    message = message,
                )
            }
        }
        composeRule.waitForIdle()
    }

    // ------------------------------------------------------- double-entry gate

    @Test
    fun twoSimultaneousRestoreResultsStartOnlyOneManagerCall() {
        val gate = BackupOperationGate()
        val barrier = CyclicBarrier(2)
        val acquired = AtomicInteger(0)
        val managerCalls = AtomicInteger(0)

        // Two SAF results delivered back-to-back before either job reaches its
        // `finally` must not both call BackupManager.restore.
        val workers = (1..2).map {
            Thread {
                barrier.await()
                if (gate.tryStart()) {
                    acquired.incrementAndGet()
                    managerCalls.incrementAndGet()
                }
            }
        }
        workers.forEach { it.start() }
        workers.forEach { it.join(5_000) }

        assertEquals(1, acquired.get())
        assertEquals(1, managerCalls.get())
        assertTrue(gate.isActive)

        // The first job's finally clears the gate; a later restore is allowed.
        gate.finish()
        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        managerCalls.incrementAndGet()
        gate.finish()
        assertEquals(2, managerCalls.get())
    }

    @Test
    fun gateIsReleasedOnFailureAndCancellationExit() {
        val gate = BackupOperationGate()
        assertTrue(gate.tryStart())
        try {
            throw IllegalStateException("simulated ZIP read failure")
        } catch (ignored: Exception) {
            // surfaced as a UI message by BackupViewModel
        } finally {
            gate.finish()
        }
        assertFalse(gate.isActive)
        assertTrue(gate.tryStart())
        gate.finish()
    }

    // ---------------------------------------------------------- flush before ZIP

    @Test
    fun backupFlushesPendingNotebookSavesBeforeArchiving() {
        val order = mutableListOf<String>()
        val summary = withFlushedSaves(
            { order += "flush" },
            {
                order += "archive"
                "zip-summary"
            },
        )

        assertEquals("zip-summary", summary)
        assertEquals(listOf("flush", "archive"), order)
    }

    @Test
    fun flushUsesTheSameNotebookWriteGateAsPendingDebouncedSaves() {
        val order = mutableListOf<String>()
        val writeGate = NotebookWriteGate { order += "write:${it.id}" }

        // MainActivity wires flushPendingSavesBeforeBackup = { saveNow() }, which
        // funnels through this same lock so the ZIP never sees a torn .tmp file.
        withFlushedSaves(
            { writeGate.locked { writeGate.writeLocked(Notebook(id = "pending")) } },
            { order += "archive" },
        )
        assertEquals(listOf("write:pending", "archive"), order)
    }

    // ------------------------------------------------------- restore completion

    @Test
    fun restoreCompletionDiscardsStaleNotebookBeforeRefresh() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_000L,
            discardNotebook = { calls += "discard" },
            refreshNotebook = { calls += "refresh" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
        )
        assertEquals(listOf("discard", "refresh", "library", "close", "consume"), calls)

        val discarded = discardNotebookState(7L)
        assertNull(discarded.current)
        assertEquals(0, discarded.pageIndex)
        assertEquals(8L, discarded.saveGeneration)
        assertFalse(isLatestSaveGeneration(7L, discarded.saveGeneration))
        assertTrue(isLatestSaveGeneration(discarded.saveGeneration, discarded.saveGeneration))
    }

    @Test
    fun restoreKeepsPdfOpenedDuringRestoreAndClosesOnlyTheOriginal() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_001L,
            discardNotebook = { calls += "discard" },
            refreshNotebook = { calls += "refresh" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
            shouldCloseDocument = { shouldCloseAfterRestore("doc-a", "doc-b") },
        )
        // The document opened during restore is not the stale one: no close.
        assertEquals(listOf("discard", "refresh", "library", "consume"), calls)

        assertFalse(shouldCloseAfterRestore(null, "doc-b"))
        assertFalse(shouldCloseAfterRestore("doc-a", "doc-b"))
        assertTrue(shouldCloseAfterRestore("doc-a", "doc-a"))
    }

    @Test
    fun restoreStartedFromHomeDoesNotCloseTheNewlyOpenedPdf() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_002L,
            refreshNotebook = { calls += "refresh" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
            shouldCloseDocument = { shouldCloseAfterRestore(null, "doc-b") },
        )
        assertEquals(listOf("refresh", "library", "consume"), calls)
    }

    @Test
    fun missingRestoreEventDoesNothing() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = null,
            discardNotebook = { calls += "discard" },
            refreshNotebook = { calls += "refresh" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
        )
        assertTrue(calls.isEmpty())
    }

    // -------------------------------------------------------------- sheet (UI)

    @Test
    fun backupSheetDisablesBothActionsWhileBusy() {
        renderSheet(busy = true, progress = 25, message = "백업을 만들고 있습니다.")

        composeRule.onNodeWithText("백업/복원").assertExists()
        composeRule.onNodeWithText("작업 중… 25%").assertExists()
        composeRule.onNodeWithText("백업을 만들고 있습니다.").assertExists()
        composeRule.onNodeWithText("백업 파일 만들기").assertIsNotEnabled()
        composeRule.onNodeWithText("백업 파일에서 복원").assertIsNotEnabled()
    }

    @Test
    fun backupSheetRoutesBackupAndConfirmedRestoreCallbacks() {
        var backups = 0
        var includeSources = false
        var restores = 0
        renderSheet(
            busy = false,
            onBackup = { includeSources = it; backups++ },
            onRestore = { restores++ },
        )

        composeRule.onNodeWithText("백업 파일 만들기").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backups)
        assertFalse(includeSources)

        composeRule.onNodeWithText("PDF 원본 포함").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("백업 파일 만들기").performClick()
        composeRule.waitForIdle()
        assertEquals(2, backups)
        assertTrue(includeSources)

        composeRule.onNodeWithText("백업 파일에서 복원").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("백업에서 복원").assertExists()
        composeRule.onNodeWithText("복원").performClick()
        composeRule.waitForIdle()
        assertEquals(1, restores)
    }
}
