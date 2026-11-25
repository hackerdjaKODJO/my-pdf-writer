package com.hackerdjakodjo.mypdfwriter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A complete tmp file is a recovery source, not garbage. */
class TmpRecoveryTest {

    private fun dir(): File =
        File(System.getProperty("java.io.tmpdir"), "tmp-recovery-${System.nanoTime()}").apply { mkdirs() }

    @Test
    fun targetWinsButTmpIsUsedWhenTargetIsMissing() {
        val target = File(dir(), "doc.json")
        target.writeText("new")
        File(target.path + ".tmp").writeText("recovered")
        assertEquals("new", readWithTmpRecovery(target) { it })
        target.delete()
        assertEquals("recovered", readWithTmpRecovery(target) { it })
    }

    @Test
    fun corruptTargetFallsBackToValidTmp() {
        val target = File(dir(), "doc.json")
        target.writeText("{broken")
        File(target.path + ".tmp").writeText("{\"ok\":true}")
        val parse: (String) -> String? = { if (it.startsWith("{broken")) null else it }
        assertEquals("{\"ok\":true}", readWithTmpRecovery(target, parse))
    }

    @Test
    fun sidecarIdAcceptsBothTargetAndTmpNames() {
        assertEquals("doc-a", sidecarId(File("/x/doc-a.json")))
        assertEquals("doc-a", sidecarId(File("/x/doc-a.json.tmp")))
        assertNull(sidecarId(File("/x/doc-a.bak")))
    }

    @Test
    fun deleteRemovesTmpFirstAndKeepsCanonicalWhenTmpCannotBeRemoved() {
        val target = File(dir(), "doc.json")
        target.writeText("canonical")
        val tmp = File(target.path + ".tmp").apply { mkdirs() }
        File(tmp, "blocker").writeText("not empty")

        assertFalse(deleteSidecarWithTmp(target))
        assertEquals("canonical", target.readText())
        assertTrue(tmp.isDirectory)

        File(tmp, "blocker").delete()
        assertTrue(deleteSidecarWithTmp(target))
        assertFalse(target.exists())
        assertFalse(tmp.exists())
        assertNull(readWithTmpRecovery(target) { it })
    }
}
