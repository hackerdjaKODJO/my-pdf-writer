package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure coverage for the no-rescan library update used after every note save. */
class NotebookLibraryMergeTest {

    private fun notebook(id: String, updatedAt: Long) = Notebook(
        id = id,
        title = id,
        updatedAt = updatedAt,
    )

    @Test
    fun `updated notebook replaces the old entry in place`() {
        val older = notebook("a", 100L)
        val newer = notebook("b", 200L)
        val library = listOf(newer, older)

        val merged = mergeNotebookIntoLibrary(library, notebook("a", 300L))

        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals(300L, merged.first().updatedAt)
        assertEquals(2, merged.size)
    }

    @Test
    fun `new notebook is inserted and order stays descending`() {
        val library = listOf(notebook("a", 100L), notebook("b", 50L))
        val merged = mergeNotebookIntoLibrary(library, notebook("c", 75L))
        assertEquals(listOf("a", "c", "b"), merged.map { it.id })
        assertTrue(merged.zipWithNext().all { (first, second) -> first.updatedAt >= second.updatedAt })
    }

    @Test
    fun `merge never aliases the caller list`() {
        val library = mutableListOf(notebook("a", 100L))
        val merged = mergeNotebookIntoLibrary(library, notebook("a", 200L))
        assertNotSame(library, merged)
        assertEquals(1, library.size)
        assertEquals(100L, library.first().updatedAt)
    }
}
