package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupManagerTest {

    @Test
    fun safeEntryPath_acceptsRelativePathsAndRejectsEscapes() {
        assertEquals(
            "files/notebooks/note.json",
            BackupArchive.safeEntryPath("files/notebooks/note.json"),
        )
        assertEquals("files/notebooks", BackupArchive.safeEntryPath("files/notebooks/"))
        assertEquals(
            "shared_prefs/pen_box.xml",
            BackupArchive.safeEntryPath("shared_prefs/pen_box.xml"),
        )

        val rejected = listOf(
            "",
            ".",
            "..",
            "../escape.txt",
            "files/../../escape.txt",
            "/data/data/com.example/files/notebooks/a.json",
            "\\windows\\escape.txt",
            "C:/escape.txt",
            "files//notebooks/a.json",
            "files/./notebooks/a.json",
            "files\\notebooks\\a.json",
            "files/notebooks/a\u0000b.json",
        )
        rejected.forEach { path ->
            assertNull("should reject: $path", BackupArchive.safeEntryPath(path))
        }
    }

    @Test
    fun manifest_roundTripsAndValidatesFormat() {
        val manifest = BackupArchive.Manifest(
            formatVersion = BackupArchive.FORMAT_VERSION,
            createdAt = 1_700_000_000_000L,
            includeSources = true,
            appVersion = "0.7.0-test",
            fileCount = 3,
            directories = listOf("notebooks", "audio", "sources"),
        )

        val parsed = BackupArchive.parseManifest(manifest.toJson().toString())
        assertEquals(manifest, parsed)

        val wrongVersion = manifest.toJson().apply { put("formatVersion", 99) }
        assertThrows(BackupException::class.java) {
            BackupArchive.parseManifest(wrongVersion.toString())
        }

        val sourcesWithoutFlag = manifest.copy(includeSources = false)
        assertThrows(BackupException::class.java) {
            BackupArchive.parseManifest(sourcesWithoutFlag.toJson().toString())
        }
    }

    @Test
    fun zipRoundTrip_restoresKnownDirectoriesAndLeavesOtherFilesAlone() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "notebooks/note.json"), "{\"title\":\"one\"}")
            write(File(files, "docstates/doc.json"), "{}")
            write(File(files, "audio/owner/index.json"), "[]")
            write(File(files, "audio/owner/voice.m4a"), "audio-bytes")
            write(File(files, "sources/hidden.pdf"), "pdf-bytes")
            write(File(files, "notebooks/transient.json.tmp"), "skip")
            write(File(files, "unrelated.txt"), "keep-root")

            val prefs = File(root, "prefs")
            write(File(prefs, "pen_box.xml"), PEN_BOX_XML)
            write(File(prefs, "library.xml"), "<map />")

            val archive = ByteArrayOutputStream()
            var createDone = 0
            var createTotal = 0
            val summary = BackupArchive.create(
                filesDir = files,
                prefsDir = prefs,
                includeSources = false,
                appVersion = "test",
                out = archive,
                onProgress = { done, total ->
                    createDone = done
                    createTotal = total
                },
            )

            assertEquals(6, summary.fileCount)
            assertEquals(summary.fileCount + 1, createTotal)
            assertEquals(createTotal, createDone)

            val target = File(root, "target")
            write(File(target, "notebooks/old.json"), "old")
            write(File(target, "sources/keep.pdf"), "keep")
            write(File(target, "source-identities/local.json"), "stale-fingerprint")
            write(File(target, "unrelated.txt"), "keep")

            val work = File(root, "work")
            var restoreDone = 0
            var restoreTotal = 0
            val extracted = BackupArchive.extract(
                input = ByteArrayInputStream(archive.toByteArray()),
                workDir = work,
                onProgress = { done, total ->
                    restoreDone = done
                    restoreTotal = total
                },
            )

            assertFalse(extracted.manifest.includeSources)
            assertFalse(File(extracted.dataDir, "sources").exists())
            assertEquals(
                listOf("notebooks", "docstates", "audio"),
                extracted.manifest.directories,
            )
            assertEquals(summary.fileCount + 1, restoreTotal)
            assertEquals(restoreTotal, restoreDone)
            assertTrue(File(extracted.prefsDir, "pen_box.xml").isFile)

            BackupArchive.replaceDirectories(
                filesDir = target,
                stagedFilesDir = extracted.dataDir,
                directories = extracted.manifest.directories,
                rollbackDir = File(work, "rollback"),
            )

            assertEquals("{\"title\":\"one\"}", File(target, "notebooks/note.json").readText())
            assertEquals("audio-bytes", File(target, "audio/owner/voice.m4a").readText())
            assertFalse(File(target, "notebooks/old.json").exists())
            assertFalse(File(target, "notebooks/transient.json.tmp").exists())
            assertEquals("keep", File(target, "sources/keep.pdf").readText())
            assertEquals("stale-fingerprint", File(target, "source-identities/local.json").readText())
            assertEquals("keep", File(target, "unrelated.txt").readText())
            assertFalse(File(extracted.dataDir, "notebooks/note.json").exists())
        }
    }

    @Test
    fun sourceIdentitiesAreBackedUpEvenWithoutSourcePdfs() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "source-identities/doc.json"), "{\"fingerprint\":\"fp-a\"}")
            val archive = ByteArrayOutputStream()
            BackupArchive.create(
                filesDir = files,
                prefsDir = null,
                includeSources = false,
                appVersion = "test",
                out = archive,
                onProgress = { _, _ -> },
            )
            val extracted = BackupArchive.extract(
                input = ByteArrayInputStream(archive.toByteArray()),
                workDir = File(root, "work"),
                onProgress = { _, _ -> },
            )
            assertTrue(extracted.manifest.directories.contains("source-identities"))
            assertEquals(
                "{\"fingerprint\":\"fp-a\"}",
                File(extracted.dataDir, "source-identities/doc.json").readText(),
            )
        }
    }

    @Test
    fun zipRoundTrip_withSourcesReplacesOnlyIncludedRoots() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "notebooks/note.json"), "note")
            write(File(files, "sources/new.pdf"), "new-pdf")

            val archive = ByteArrayOutputStream()
            val summary = BackupArchive.create(
                filesDir = files,
                prefsDir = null,
                includeSources = true,
                appVersion = "test",
                out = archive,
                onProgress = { _, _ -> },
            )
            assertEquals(2, summary.fileCount)

            val target = File(root, "target")
            write(File(target, "notebooks/old.json"), "old")
            write(File(target, "sources/old.pdf"), "old-pdf")
            write(File(target, "source-identities/local.json"), "stale-fingerprint")

            val work = File(root, "work")
            val extracted = BackupArchive.extract(
                ByteArrayInputStream(archive.toByteArray()),
                work,
                onProgress = { _, _ -> },
            )
            assertTrue(extracted.manifest.includeSources)
            assertEquals(
                listOf("notebooks", "sources"),
                extracted.manifest.directories,
            )

            BackupArchive.replaceDirectories(
                filesDir = target,
                stagedFilesDir = extracted.dataDir,
                directories = extracted.manifest.directories,
                rollbackDir = File(work, "rollback"),
            )

            assertEquals("new-pdf", File(target, "sources/new.pdf").readText())
            assertFalse(File(target, "sources/old.pdf").exists())
            assertEquals("note", File(target, "notebooks/note.json").readText())
            assertFalse(File(target, "source-identities/local.json").exists())
        }
    }

    @Test
    fun zipRoundTrip_includesAndRestoresCustomTemplateAssets() {
        withTempRoot { root ->
            val files = File(root, "files")
            val assetBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
            writeBytes(File(files, "templates/tpl-custom.png"), assetBytes)
            write(File(files, "notebooks/note.json"), "{\"id\":\"n\"}")

            val archive = ByteArrayOutputStream()
            val summary = BackupArchive.create(
                filesDir = files,
                prefsDir = null,
                includeSources = false,
                appVersion = "test",
                out = archive,
                onProgress = { _, _ -> },
            )
            assertEquals(2, summary.fileCount)

            val target = File(root, "target")
            write(File(target, "templates/tpl-old.png"), "old-asset")
            write(File(target, "notebooks/old.json"), "old")
            write(File(target, "unrelated.txt"), "keep")

            val work = File(root, "work")
            val extracted = BackupArchive.extract(
                input = ByteArrayInputStream(archive.toByteArray()),
                workDir = work,
                onProgress = { _, _ -> },
            )
            assertEquals(listOf("notebooks", "templates"), extracted.manifest.directories)
            assertTrue(File(extracted.dataDir, "templates/tpl-custom.png").isFile)

            BackupArchive.replaceDirectories(
                filesDir = target,
                stagedFilesDir = extracted.dataDir,
                directories = extracted.manifest.directories,
                rollbackDir = File(work, "rollback"),
            )

            val restored = File(target, "templates/tpl-custom.png")
            assertTrue(restored.isFile)
            assertTrue(assetBytes.contentEquals(restored.readBytes()))
            assertFalse(File(target, "templates/tpl-old.png").exists())
            assertEquals("keep", File(target, "unrelated.txt").readText())
            assertEquals("{\"id\":\"n\"}", File(target, "notebooks/note.json").readText())
        }
    }

    @Test
    fun templateEntryTraversal_isRejected() {
        withTempRoot { root ->
            val archive = ByteArrayOutputStream()
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry("files/templates/../../escape.png"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            val error = assertThrows(BackupException::class.java) {
                BackupArchive.extract(
                    ByteArrayInputStream(archive.toByteArray()),
                    File(root, "work"),
                    onProgress = { _, _ -> },
                )
            }
            assertTrue(error.message.orEmpty().contains("안전하지 않은"))
            assertFalse(File(root, "escape.png").exists())
        }
    }

    @Test
    fun corruptZip_isReportedAsBackupException() {
        withTempRoot { root ->
            val work = File(root, "work")
            val error = assertThrows(BackupException::class.java) {
                BackupArchive.extract(
                    ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                    work,
                    onProgress = { _, _ -> },
                )
            }
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    @Test
    fun traverseEntry_isRejectedBeforeWritingAnything() {
        withTempRoot { root ->
            val archive = ByteArrayOutputStream()
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry("../escape.txt"))
                zip.write("bad".toByteArray())
                zip.closeEntry()
            }
            val work = File(root, "work")
            val error = assertThrows(BackupException::class.java) {
                BackupArchive.extract(
                    ByteArrayInputStream(archive.toByteArray()),
                    work,
                    onProgress = { _, _ -> },
                )
            }
            assertTrue(error.message.orEmpty().contains("안전하지 않은"))
            assertFalse(File(root, "escape.txt").exists())
        }
    }

    @Test
    fun manifestCountMismatch_isRejected() {
        withTempRoot { root ->
            val archive = ByteArrayOutputStream()
            val manifest = BackupArchive.Manifest(
                formatVersion = BackupArchive.FORMAT_VERSION,
                createdAt = System.currentTimeMillis(),
                includeSources = false,
                appVersion = "test",
                fileCount = 2,
                directories = listOf("notebooks"),
            )
            ZipOutputStream(archive).use { zip ->
                zip.putNextEntry(ZipEntry(BackupArchive.MANIFEST_NAME))
                zip.write(manifest.toJson().toString().toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("files/notebooks/one.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
            }
            val error = assertThrows(BackupException::class.java) {
                BackupArchive.extract(
                    ByteArrayInputStream(archive.toByteArray()),
                    File(root, "work"),
                    onProgress = { _, _ -> },
                )
            }
            assertTrue(error.message.orEmpty().contains("파일 수"))
        }
    }

    @Test
    fun preferenceFailureRollsBackReplacedDirectoriesBeforePropagating() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "notebooks/old.json"), "old")
            write(File(files, "docstates/old.json"), "old-state")
            write(File(files, "unrelated.txt"), "keep")

            val staged = File(root, "staged")
            write(File(staged, "notebooks/new.json"), "new")
            write(File(staged, "docstates/new.json"), "new-state")

            val failure = IllegalStateException("pref commit failed")
            val error = assertThrows(IllegalStateException::class.java) {
                installRestoredData(
                    filesDir = files,
                    stagedFilesDir = staged,
                    directories = listOf("notebooks", "docstates"),
                    rollbackDir = File(root, "rollback"),
                    applyPreferences = { throw failure },
                )
            }

            assertSame(failure, error)
            assertEquals("old", File(files, "notebooks/old.json").readText())
            assertEquals("old-state", File(files, "docstates/old.json").readText())
            assertFalse(File(files, "notebooks/new.json").exists())
            assertFalse(File(files, "docstates/new.json").exists())
            assertEquals("keep", File(files, "unrelated.txt").readText())
        }
    }

    @Test
    fun preferenceFailureRemovesDirectoriesThatDidNotExistBefore() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "notebooks/old.json"), "old")

            val staged = File(root, "staged")
            write(File(staged, "docstates/new.json"), "new-state")
            write(File(staged, "notebooks/new.json"), "new")

            var rollbackFailed = false
            assertThrows(IllegalStateException::class.java) {
                installRestoredData(
                    filesDir = files,
                    stagedFilesDir = staged,
                    directories = listOf("docstates", "notebooks"),
                    rollbackDir = File(root, "rollback"),
                    applyPreferences = { throw IllegalStateException("pref commit failed") },
                    onRollbackFailed = { rollbackFailed = true },
                )
            }

            assertFalse(rollbackFailed)
            assertFalse(File(files, "docstates").exists())
            assertEquals("old", File(files, "notebooks/old.json").readText())
            assertFalse(File(files, "notebooks/new.json").exists())
        }
    }

    @Test
    fun applyingPreferencesSuccessfullyKeepsRestoredDirectories() {
        withTempRoot { root ->
            val files = File(root, "files")
            write(File(files, "notebooks/old.json"), "old")

            val staged = File(root, "staged")
            write(File(staged, "notebooks/new.json"), "new")

            var applied = false
            installRestoredData(
                filesDir = files,
                stagedFilesDir = staged,
                directories = listOf("notebooks"),
                rollbackDir = File(root, "rollback"),
                applyPreferences = { applied = true },
            )

            assertTrue(applied)
            assertEquals("new", File(files, "notebooks/new.json").readText())
            assertFalse(File(files, "notebooks/old.json").exists())
        }
    }

    private fun withTempRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("pdfw-backup-test").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun writeBytes(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private companion object {
        val PEN_BOX_XML = """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            |<map>
            |    <string name="presets_v1">[]</string>
            |</map>
        """.trimMargin()
    }
}
