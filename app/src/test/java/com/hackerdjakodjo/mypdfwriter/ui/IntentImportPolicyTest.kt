package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.IntentCarrierSink
import com.hackerdjakodjo.mypdfwriter.clearIntentCarriers
import com.hackerdjakodjo.mypdfwriter.onBackupRestored
import com.hackerdjakodjo.mypdfwriter.shouldAddToRecents
import com.hackerdjakodjo.mypdfwriter.shouldCloseAfterRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentImportPolicyTest {

    private val view = "android.intent.action.VIEW"
    private val send = "android.intent.action.SEND"

    @Test
    fun viewIntentPrefersDataOverOtherUriSources() {
        assertEquals(
            "content://data/file.pdf",
            resolveImportUri(
                ImportIntentSnapshot(
                    action = view,
                    dataUri = "content://data/file.pdf",
                    streamUri = "content://stream/file.pdf",
                    clipUris = listOf("content://clip/file.pdf"),
                ),
            ),
        )
    }

    @Test
    fun sendIntentFallsBackToExtraStream() {
        assertEquals(
            "content://stream/file.pdf",
            resolveImportUri(
                ImportIntentSnapshot(
                    action = send,
                    streamUri = "content://stream/file.pdf",
                    clipUris = listOf("content://clip/file.pdf"),
                ),
            ),
        )
    }

    @Test
    fun clipDataIsLastResortAndSkipsBlankItems() {
        assertEquals(
            "content://clip/file.pdf",
            resolveImportUri(
                ImportIntentSnapshot(
                    action = send,
                    dataUri = "   ",
                    streamUri = "",
                    clipUris = listOf("", "  ", "content://clip/file.pdf", "content://next/file.pdf"),
                ),
            ),
        )
    }

    @Test
    fun resolvedUriIsTrimmed() {
        assertEquals(
            "content://stream/file.pdf",
            resolveImportUri(
                ImportIntentSnapshot(action = send, streamUri = "  content://stream/file.pdf\n"),
            ),
        )
    }

    @Test
    fun unrelatedOrMissingActionsAreIgnored() {
        assertNull(
            resolveImportUri(
                ImportIntentSnapshot(
                    action = "android.intent.action.MAIN",
                    dataUri = "content://data/file.pdf",
                ),
            ),
        )
        assertNull(resolveImportUri(ImportIntentSnapshot(action = null, dataUri = "content://data/file.pdf")))
    }

    @Test
    fun intentWithoutAnyUriReturnsNull() {
        assertNull(resolveImportUri(ImportIntentSnapshot(action = send)))
        assertNull(
            resolveImportUri(
                ImportIntentSnapshot(
                    action = view,
                    dataUri = " ",
                    streamUri = "",
                    clipUris = listOf("  "),
                ),
            ),
        )
    }

    private class FakeCarriers : IntentCarrierSink {
        var data: String? = "content://data/file.pdf"
        var stream: String? = "content://stream/file.pdf"
        var clip: List<String> = listOf("content://clip/file.pdf")
        var dataClears = 0
        var streamClears = 0
        var clipClears = 0

        override fun clearData() {
            dataClears++
            data = null
        }

        override fun clearExtraStream() {
            streamClears++
            stream = null
        }

        override fun clearClipData() {
            clipClears++
            clip = emptyList()
        }
    }

    @Test
    fun clearingIntentCarriersRemovesEveryCarrierAndIsIdempotent() {
        val carriers = FakeCarriers()
        clearIntentCarriers(carriers)

        assertNull(carriers.data)
        assertNull(carriers.stream)
        assertTrue(carriers.clip.isEmpty())
        assertEquals(1, carriers.dataClears)
        assertEquals(1, carriers.streamClears)
        assertEquals(1, carriers.clipClears)

        // A redelivered intent must not reopen the same PDF or mutate state again.
        clearIntentCarriers(carriers)
        assertNull(carriers.data)
        assertNull(carriers.stream)
        assertTrue(carriers.clip.isEmpty())
        assertEquals(2, carriers.dataClears)
        assertEquals(2, carriers.streamClears)
        assertEquals(2, carriers.clipClears)
    }

    @Test
    fun sendAndViewIntentsJoinRecentsSoResumeWorks() {
        assertTrue(shouldAddToRecents(send))
        assertTrue("opening from another app must join recents", shouldAddToRecents(view))
        assertFalse(shouldAddToRecents(null))
        assertFalse(shouldAddToRecents("android.intent.action.MAIN"))
    }

    @Test
    fun restoreEventRefreshesListsClosesViewerAndConsumesEvent() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_000L,
            refreshNotebook = { calls += "notebook" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
        )
        assertEquals(listOf("notebook", "library", "close", "consume"), calls)
    }

    @Test
    fun missingRestoreEventDoesNothing() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = null,
            refreshNotebook = { calls += "notebook" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
        )
        assertTrue(calls.isEmpty())
    }

    @Test
    fun restoreCompletionKeepsAPdfOpenedDuringRestore() {
        // A different PDF was opened while the restore was running: it must survive.
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_000L,
            refreshNotebook = { calls += "notebook" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
            shouldCloseDocument = { shouldCloseAfterRestore("old.pdf", "new.pdf") },
        )
        assertEquals(listOf("notebook", "library", "consume"), calls)

        // The original document is still active: it is stale and must be discarded.
        val sameDocCalls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_001L,
            refreshNotebook = { sameDocCalls += "notebook" },
            reloadLibrary = { sameDocCalls += "library" },
            closeDocument = { sameDocCalls += "close" },
            consumeRestored = { sameDocCalls += "consume" },
            shouldCloseDocument = { shouldCloseAfterRestore("old.pdf", "old.pdf") },
        )
        assertEquals(listOf("notebook", "library", "close", "consume"), sameDocCalls)

        // Restore launched from Home: no document to close even if one opens later.
        assertFalse(shouldCloseAfterRestore(null, "new.pdf"))
        assertTrue(shouldCloseAfterRestore("old.pdf", "old.pdf"))
    }

    @Test
    fun restoreCompletionDiscardsStaleNotebookBeforeRefresh() {
        val calls = mutableListOf<String>()
        onBackupRestored(
            restoredAt = 1_700_000_000_002L,
            discardNotebook = { calls += "discard" },
            refreshNotebook = { calls += "refresh" },
            reloadLibrary = { calls += "library" },
            closeDocument = { calls += "close" },
            consumeRestored = { calls += "consume" },
        )
        assertEquals(listOf("discard", "refresh", "library", "close", "consume"), calls)
    }
}
