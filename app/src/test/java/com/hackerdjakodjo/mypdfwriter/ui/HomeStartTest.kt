package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartTest {

    private fun pdf(name: String, openedAt: Long) = RecentDoc(
        uri = "content://docs/$name",
        name = name,
        pageCount = 3,
        lastOpenedAt = openedAt,
    )

    private fun notebook(id: String, updatedAt: Long, trashed: Boolean = false) = Notebook(
        id = id,
        title = id,
        updatedAt = updatedAt,
        trashed = trashed,
    )

    @Test
    fun emptyLibraryHasNoContinuation() {
        val continuation = selectHomeContinuation(emptyList(), emptyList())

        assertNull(continuation.lastPdf)
        assertNull(continuation.lastNotebook)
        assertFalse(continuation.hasContent)
    }

    @Test
    fun picksLastOpenedPdfAndLastEditedNotebook() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(
                notebook("old", updatedAt = 100L),
                notebook("recent", updatedAt = 300L),
                notebook("middle", updatedAt = 200L),
            ),
            pdfs = listOf(
                pdf("first.pdf", openedAt = 50L),
                pdf("last.pdf", openedAt = 400L),
            ),
        )

        assertEquals("last.pdf", continuation.lastPdf?.name)
        assertEquals("recent", continuation.lastNotebook?.id)
        assertTrue(continuation.hasContent)
    }

    @Test
    fun trashedNotebooksAreNeverContinued() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(
                notebook("active", updatedAt = 100L),
                notebook("trashed", updatedAt = 900L, trashed = true),
            ),
            pdfs = emptyList(),
        )

        assertEquals("active", continuation.lastNotebook?.id)
        assertNull(continuation.lastPdf)
    }

    @Test
    fun onlyTrashedNotebooksLeavesNotebookSlotEmpty() {
        val continuation = selectHomeContinuation(
            notebooks = listOf(notebook("trashed", updatedAt = 900L, trashed = true)),
            pdfs = listOf(pdf("reading.pdf", openedAt = 20L)),
        )

        assertNull(continuation.lastNotebook)
        assertEquals("reading.pdf", continuation.lastPdf?.name)
        assertTrue(continuation.hasContent)
    }
}
