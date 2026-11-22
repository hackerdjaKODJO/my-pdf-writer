package com.hackerdjakodjo.mypdfwriter.e2e

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hackerdjakodjo.mypdfwriter.MainActivity
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.ui.BackupViewModel
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Real-file E2E coverage for local backup/restore and ACTION_SEND import.
 *
 * Every case writes a genuine PDFBox-Android PDF into the app sandbox, drives
 * the real [MainActivity] and inspects the produced ZIP on disk. Shared PDF and
 * polling helpers come from [PdfFixture]; backup-specific helpers stay private.
 */
class BackupRealFileE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var ctx: Context
    private lateinit var pdfFile: File
    private lateinit var pdfUri: Uri
    private lateinit var pdfVm: PdfViewModel
    private lateinit var backupVm: BackupViewModel

    companion object {
        @BeforeClass
        @JvmStatic
        fun initPdfBox() {
            PdfFixture.init(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)
        }

        /** Mirrors PdfViewModel.docIdFor: SHA-256 of the URI string, lowercase hex. */
        private fun docIdFor(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /** Reads one entry from a backup ZIP, or null when the entry is absent. */
        private fun readZipEntry(zip: File, entry: String): String? = ZipFile(zip).use { archive ->
            val target = archive.getEntry(entry) ?: return null
            archive.getInputStream(target).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        /** Annotation ids stored in a DocState JSON document (key: "annotations"). */
        private fun annotationIds(docStateJson: String): Set<String> {
            val array = JSONObject(docStateJson).optJSONArray("annotations") ?: return emptySet()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id") }.toSet()
        }

        /**
         * Removes every child of shared_prefs, filesDir and cacheDir and re-creates
         * the directories BackupManager archives. Onboarding is marked seen.
         */
        private fun resetAppState(context: Context) {
            val app = context.applicationContext
            val prefsDir = File(app.dataDir, "shared_prefs")
            listOf(prefsDir, app.filesDir, app.cacheDir).forEach { dir ->
                dir.listFiles()?.forEach { child -> child.deleteRecursively() }
            }
            listOf(
                "notebooks", "docstates", "excerpts", "reading", "docstyles",
                "templates", "audio", "sources", "backups",
            ).forEach { File(app.filesDir, it).mkdirs() }
            OnboardingStore(app).markSeen()
        }
    }

    @Before
    fun setUp() {
        ctx = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        resetAppState(ctx)
        pdfFile = File(ctx.filesDir, "e2e-fixtures/backup-source.pdf")
        PdfFixture.writeFixture(pdfFile)
        pdfUri = PdfFixture.uriFor(ctx, pdfFile)
        val activity = composeRule.activity
        pdfVm = ViewModelProvider(activity).get(PdfViewModel::class.java)
        backupVm = ViewModelProvider(activity).get(BackupViewModel::class.java)
        // The rule creates the activity (and its ViewModels) before this @Before,
        // so drop any recents it loaded from the previous test's prefs.
        pdfVm.reloadLibrary()
    }

    // ------------------------------------------------------------------ helpers

    /** Opens the fixture and commits one PEN stroke; returns the new annotation id. */
    private fun openAndAddStroke(): String {
        pdfVm.openDocument(pdfUri)
        PdfFixture.awaitUntil("fixture PDF to open") {
            val state = pdfVm.viewer.value
            state?.docId?.isNotBlank() == true &&
                state.pageCount == PdfFixture.PAGE_COUNT &&
                !state.busy &&
                pdfVm.currentSourceFile() != null
        }
        // openDocument publishes a fresh ViewerUiState with the default VIEW tool.
        pdfVm.setTool(Tool.PEN)
        val before = pdfVm.viewer.value!!.annotations.size
        pdfVm.addStroke(
            listOf(
                NormalizedPoint(0.2f, 0.25f),
                NormalizedPoint(0.45f, 0.5f),
                NormalizedPoint(0.8f, 0.7f),
            ),
        )
        PdfFixture.awaitUntil("stroke to be published in the viewer") {
            pdfVm.viewer.value?.annotations?.size == before + 1
        }
        return pdfVm.viewer.value!!.annotations.last().id
    }

    private fun awaitDocState(annotationId: String): File {
        val stateFile = File(ctx.filesDir, "docstates/${docIdFor(pdfUri)}.json")
        PdfFixture.awaitUntil("doc-state file to contain annotation $annotationId") {
            runCatching {
                stateFile.exists() && annotationIds(stateFile.readText()).contains(annotationId)
            }.getOrDefault(false)
        }
        return stateFile
    }

    private fun awaitBackupDone() {
        PdfFixture.awaitUntil("backup completion") {
            val state = backupVm.state.value
            !state.busy &&
                state.lastBackupInfo != null &&
                state.message?.startsWith("백업을 만들었습니다") == true
        }
    }

    private fun awaitRestoreDone() {
        PdfFixture.awaitUntil("restore completion") {
            val state = backupVm.state.value
            !state.busy &&
                state.lastBackupInfo != null &&
                state.message?.startsWith("복원했습니다") == true
        }
    }

    private fun sendIntent(): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        `package` = ctx.packageName
        component = ComponentName(ctx, MainActivity::class.java)
        putExtra(Intent.EXTRA_STREAM, pdfUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun deliver(intent: Intent) {
        val activity = composeRule.activity
        // Same production import handler, but without setIntent() leaving a
        // synthetic external intent on the rule-owned Activity (which breaks
        // ActivityScenarioRule's DESTROYED teardown).
        activity.runOnUiThread { PdfFixture.deliverViewIntent(activity, intent) }
        composeRule.waitForIdle()
    }

    // -------------------------------------------------------------------- tests

    /**
     * Regression guard for the async PDF save / backup race: the stroke is still
     * only in memory (doc-state writer has not drained) when the ZIP is made.
     * If this fails because the annotation is absent from the archive, that is a
     * P1 production bug — do not paper over it by waiting for the sidecar here.
     */
    @Test
    fun backupZipContainsLatestPdfAnnotationState() {
        val annotationId = openAndAddStroke()
        // Isolate the PDF-side race from the notebook flush MainActivity installs.
        backupVm.flushPendingSavesBeforeBackup = {}

        // Deliberately no wait for the doc-state file: backup must see live state.
        val zip = File(ctx.filesDir, "backups/latest-state.zip")
        backupVm.backup(PdfFixture.uriFor(ctx, zip), includeSources = false)
        awaitBackupDone()

        val entry = "files/docstates/${docIdFor(pdfUri)}.json"
        val json = readZipEntry(zip, entry)
        assertNotNull("backup must contain $entry", json)
        assertTrue(
            "annotation $annotationId was live in the viewer when backup() ran " +
                "but is missing from $entry in the ZIP — " +
                "the PDF doc-state save is async and backup does not flush it (P1)",
            annotationIds(json!!).contains(annotationId),
        )
    }

    /** Full disk round trip: backup -> discard + delete sidecar -> restore -> reopen. */
    @Test
    fun restoreRoundTripReproducesPdfAnnotations() {
        val annotationId = openAndAddStroke()
        val stateFile = awaitDocState(annotationId)

        val zip = File(ctx.filesDir, "backups/round-trip.zip")
        backupVm.backup(PdfFixture.uriFor(ctx, zip), includeSources = false)
        awaitBackupDone()

        // Clear only PDF state; the recent list itself is restored from library.xml.
        pdfVm.discardDocument()
        assertTrue("test setup: doc-state file must be deleted", stateFile.delete())
        PdfFixture.awaitUntil("PDF state to be cleared") {
            !stateFile.exists() && pdfVm.viewer.value == null
        }

        backupVm.restore(PdfFixture.uriFor(ctx, zip))
        awaitRestoreDone()
        PdfFixture.awaitUntil("doc-state file to be restored") {
            runCatching {
                stateFile.exists() && annotationIds(stateFile.readText()).contains(annotationId)
            }.getOrDefault(false)
        }

        pdfVm.openDocument(pdfUri)
        PdfFixture.awaitUntil("restored annotation to be loaded into the viewer") {
            val state = pdfVm.viewer.value
            state != null && !state.busy && state.annotations.any { it.id == annotationId }
        }
        assertTrue(
            "restored viewer must contain annotation $annotationId",
            pdfVm.viewer.value!!.annotations.any { it.id == annotationId },
        )
    }

    /**
     * With includeSources=true the archive must be self-sufficient: after the
     * on-disk source and the session copy are gone, a fresh repository must open
     * from files/sources/<docId>.pdf restored by the ZIP.
     *
     * If this fails because PdfRepository.sourceFileFor re-copies from the now
     * unreadable URI, that is a P1 production bug — keep the desired assertion.
     */
    @Test
    fun backupWithSourcesRestoresOpenablePdf() {
        val annotationId = openAndAddStroke()
        awaitDocState(annotationId)
        val docId = docIdFor(pdfUri)
        val cachedSource = File(ctx.filesDir, "sources/$docId.pdf")
        PdfFixture.awaitUntil("source copy to exist before backup") {
            cachedSource.exists() && cachedSource.length() > 0L
        }

        val zip = File(ctx.filesDir, "backups/with-sources.zip")
        backupVm.backup(PdfFixture.uriFor(ctx, zip), includeSources = true)
        awaitBackupDone()
        assertNotNull(
            "backup with sources must contain files/sources/$docId.pdf",
            readZipEntry(zip, "files/sources/$docId.pdf"),
        )

        // Wipe the live source and the session-only repository cache.
        pdfVm.discardDocument()
        assertTrue("test setup: cached source must be deleted", cachedSource.delete())
        assertTrue("test setup: original fixture must be deleted", pdfFile.delete())
        assertFalse("the URI must be unreadable to exercise the restored-source path", pdfFile.exists())

        // Actually restore the ZIP: this puts files/sources/<docId>.pdf back on
        // disk after the live copy was wiped above.
        backupVm.restore(PdfFixture.uriFor(ctx, zip))
        awaitRestoreDone()
        PdfFixture.awaitUntil("restored source PDF to exist on disk") {
            cachedSource.exists() && cachedSource.length() > 0L
        }

        // A brand-new ViewModel has an empty PdfRepository.sourceCache, like a
        // cold process start after restore; it must fall back to the restored PDF.
        val freshVm = PdfViewModel(ctx.applicationContext as Application)
        freshVm.openDocument(pdfUri)
        PdfFixture.awaitUntil("document to open from restored files/sources/$docId.pdf") {
            val state = freshVm.viewer.value
            state != null && !state.busy && state.pageCount == PdfFixture.PAGE_COUNT &&
                state.annotations.any { it.id == annotationId }
        }
        assertEquals(docId, freshVm.viewer.value!!.docId)
    }

    /** ACTION_SEND import joins recents and the entry survives a fresh activity. */
    @Test
    fun actionSendAddsRecentAndSurvivesRestart() {
        val uriString = pdfUri.toString()
        deliver(sendIntent())

        PdfFixture.awaitUntil("ACTION_SEND import to add a recent") {
            pdfVm.library.value.any { it.uri == uriString && it.pageCount == PdfFixture.PAGE_COUNT }
        }
        PdfFixture.awaitUntil("imported document to open") {
            pdfVm.viewer.value?.docId == docIdFor(pdfUri)
        }

        // Simulate a cold start *without* recreating the rule-owned Activity:
        // a fresh ViewModelStore re-reads the persisted library.xml exactly as a
        // close + relaunch observes. Calling Activity.recreate() here leaves the
        // Compose rule's scenario tracking a stale instance, so its after() can
        // never observe DESTROYED and teardown fails.
        val persisted = LibraryStore(ctx).load()
        assertEquals(1, persisted.count { it.uri == uriString })
        assertEquals(PdfFixture.PAGE_COUNT, persisted.first { it.uri == uriString }.pageCount)

        // A plain, non-import intent delivered to the same Activity must not
        // change the recents list.
        deliver(Intent(Intent.ACTION_MAIN).setPackage(ctx.packageName))
        assertEquals(1, pdfVm.library.value.count { it.uri == uriString })

        val freshVm = PdfViewModel(ctx.applicationContext as Application)
        PdfFixture.awaitUntil("recent to survive restart") {
            freshVm.library.value.any { it.uri == uriString && it.pageCount == PdfFixture.PAGE_COUNT }
        }
        assertEquals(1, freshVm.library.value.count { it.uri == uriString })

        freshVm.openDocument(Uri.parse(uriString))
        PdfFixture.awaitUntil("persisted URI to be reopened after restart") {
            val state = freshVm.viewer.value
            state != null && !state.busy && state.docId == docIdFor(pdfUri) &&
                state.pageCount == PdfFixture.PAGE_COUNT
        }
        assertEquals(PdfFixture.PAGE_COUNT, freshVm.viewer.value!!.pageCount)
    }

    /** A redelivered singleTop ACTION_SEND must not create a second recent. */
    @Test
    fun redeliveredSendIntentDoesNotDuplicateRecents() {
        val uriString = pdfUri.toString()
        val send = sendIntent()
        deliver(send)

        PdfFixture.awaitUntil("first ACTION_SEND import") {
            pdfVm.library.value.count { it.uri == uriString } == 1 &&
                pdfVm.viewer.value?.docId == docIdFor(pdfUri)
        }

        // MainActivity clears every URI carrier before opening; the same Intent
        // delivered again must be a no-op.
        deliver(send)
        assertEquals(
            "redelivered ACTION_SEND must leave exactly one recent",
            1,
            pdfVm.library.value.count { it.uri == uriString },
        )
    }
}
