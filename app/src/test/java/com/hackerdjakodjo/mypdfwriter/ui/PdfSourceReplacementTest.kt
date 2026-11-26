package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PdfSourceReplacementTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun quarantineMovesCanonicalAndTmpSidecarsForAllStateDirectories() {
        val files = temporaryFolder.newFolder("files")
        val suffix = "replaced-fingerprint-123"
        listOf("docstates", "excerpts", "reading").forEach { directory ->
            listOf("doc.json", "doc.json.tmp").forEach { name ->
                File(files, "$directory").mkdirs()
                File(files, "$directory/$name").writeText("old-$directory-$name")
            }
        }

        assertTrue(quarantineReplacedSidecars(files, "doc", "fingerprint", 123L))

        listOf("docstates", "excerpts", "reading").forEach { directory ->
            listOf("doc.json", "doc.json.tmp").forEach { name ->
                assertFalse(File(files, "$directory/$name").exists())
                assertTrue(File(files, "$directory/$name.$suffix").isFile)
            }
        }
    }

    @Test
    fun quarantineIsSuccessfulWhenThereAreNoSidecars() {
        assertTrue(
            quarantineReplacedSidecars(
                temporaryFolder.newFolder("empty"),
                "doc",
                "fingerprint",
                123L,
            ),
        )
    }

    @Test
    fun quarantineFailsClosedWhenBackupCannotBeCreated() {
        val files = temporaryFolder.newFolder("files")
        val directory = File(files, "docstates").apply { mkdirs() }
        val source = File(directory, "doc.json").apply { writeText("old") }
        val blockedBackup = File(directory, "doc.json.replaced-fingerprint-123")
        assertTrue(blockedBackup.mkdir())
        assertTrue(File(blockedBackup, "keep").apply { writeText("blocked") }.isFile)
        assertFalse(quarantineReplacedSidecars(files, "doc", "fingerprint", 123L))
        val preserved = if (source.isFile) source.readText() else File(blockedBackup, "doc.json").readText()
        assertEquals("old", preserved)
    }
}
