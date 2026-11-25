package com.hackerdjakodjo.mypdfwriter.data

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotebookStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun saveVerifiesCanonicalReadbackAndRemovesTmpOnSuccess() {
        val store = NotebookStore(temporaryFolder.newFolder("notebooks"))
        val notebook = Notebook(
            id = "notebook-1",
            title = "  검증된 노트  ",
            pages = listOf(NotePage(memo = "내용")),
            lastPageIndex = 0,
        )

        assertTrue(store.save(notebook))
        assertEquals(notebook.normalized(), store.load(notebook.id))
        assertFalse(File(temporaryFolder.root, "notebooks/${notebook.id}.json.tmp").exists())
    }

    @Test
    fun injectedReadFailurePreservesCompleteTmpAndReportsFailure() {
        val io = FaultyNotebookStoreIo(failRead = true)
        val store = NotebookStore(temporaryFolder.newFolder("notebooks-failure"), io)
        val notebook = Notebook(id = "notebook-failure", title = "보존되어야 함")

        assertFalse(store.save(notebook))
        val tmp = File(temporaryFolder.root, "notebooks-failure/${notebook.id}.json.tmp")
        assertTrue(tmp.isFile)
        assertEquals(notebook.normalized().toJson().toString(), tmp.readText())
    }

    @Test
    fun injectedWriteFailurePreservesPartialTmpForRecoveryAttempts() {
        val io = FaultyNotebookStoreIo(failWrite = true)
        val store = NotebookStore(temporaryFolder.newFolder("notebooks-write-failure"), io)

        assertFalse(store.save(Notebook(id = "notebook-write-failure")))
        val tmp = File(temporaryFolder.root, "notebooks-write-failure/notebook-write-failure.json.tmp")
        assertTrue(tmp.isFile)
        assertEquals("partial", tmp.readText())
    }

    @Test
    fun permanentDeleteRemovesValidTmpSoItCannotResurrectTheNotebook() {
        val dir = temporaryFolder.newFolder("notebooks-delete")
        val store = NotebookStore(dir)
        val id = "deleted-notebook"
        val target = File(dir, "$id.json")
        val tmp = File(target.path + ".tmp")
        target.writeText(Notebook(id = id, title = "canonical").toJson().toString())
        tmp.writeText(Notebook(id = id, title = "newer tmp").toJson().toString())

        assertTrue(store.delete(id))
        assertFalse(target.exists())
        assertFalse(tmp.exists())
        assertNull(store.load(id))
    }

    @Test
    fun allJsonSidecarStoresDeleteCanonicalAndTmpTogether() {
        val id = "owner"
        val docDir = temporaryFolder.newFolder("docstates-delete")
        val docStore = DocStateStore(docDir)
        val docJson = DocState(
            docId = id,
            displayName = "doc.pdf",
            pages = emptyList(),
            annotations = emptyList(),
            notes = emptyMap(),
        ).toJson().toString()
        File(docDir, "$id.json").writeText(docJson)
        File(docDir, "$id.json.tmp").writeText(docJson)
        assertTrue(docStore.delete(id))
        assertFalse(File(docDir, "$id.json").exists())
        assertFalse(File(docDir, "$id.json.tmp").exists())
        assertNull(docStore.load(id))

        val styleDir = temporaryFolder.newFolder("styles-delete")
        val styleStore = DocumentStyleStore(styleDir)
        val styleJson = DocumentStyle().toJson().toString()
        File(styleDir, "$id.json").writeText(styleJson)
        File(styleDir, "$id.json.tmp").writeText(styleJson)
        assertTrue(styleStore.delete(id))
        assertFalse(File(styleDir, "$id.json").exists())
        assertFalse(File(styleDir, "$id.json.tmp").exists())

        val excerptDir = temporaryFolder.newFolder("excerpts-delete")
        val excerptStore = ExcerptStore(excerptDir)
        val excerptJson = Excerpt.listToJson(emptyList()).toString()
        File(excerptDir, "$id.json").writeText(excerptJson)
        File(excerptDir, "$id.json.tmp").writeText(excerptJson)
        assertTrue(excerptStore.delete(id))
        assertFalse(File(excerptDir, "$id.json").exists())
        assertFalse(File(excerptDir, "$id.json.tmp").exists())
        assertTrue(excerptStore.load(id).isEmpty())
    }

    private class FaultyNotebookStoreIo(
        private val failRead: Boolean = false,
        private val failWrite: Boolean = false,
    ) : NotebookStoreIo {
        override fun writeTmp(tmp: File, content: String) {
            if (failWrite) {
                tmp.writeText("partial")
                throw IOException("injected tmp write failure")
            }
            FileNotebookStoreIo.writeTmp(tmp, content)
        }

        override fun publish(tmp: File, target: File) {
            FileNotebookStoreIo.publish(tmp, target)
        }

        override fun readCanonical(target: File): String {
            if (failRead) throw IOException("injected readback failure")
            return FileNotebookStoreIo.readCanonical(target)
        }
    }
}
