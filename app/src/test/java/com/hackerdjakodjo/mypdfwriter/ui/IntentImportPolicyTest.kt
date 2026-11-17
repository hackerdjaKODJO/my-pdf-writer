package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
