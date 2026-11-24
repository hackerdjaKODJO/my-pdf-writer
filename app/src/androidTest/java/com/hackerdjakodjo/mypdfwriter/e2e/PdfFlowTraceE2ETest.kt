package com.hackerdjakodjo.mypdfwriter.e2e

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.hackerdjakodjo.mypdfwriter.MainActivity
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.debug.DevLog
import com.hackerdjakodjo.mypdfwriter.debug.DevLogEntry
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFlowTrace
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.ui.ViewerUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Real-file E2E coverage for the structured **PDF flow trace** written by
 * [PdfFlowTrace] into the durable [DevLog].
 *
 * The setup mirrors `PdfRealFileE2ETest`: the rule launches the real
 * [MainActivity], the [ViewModelProvider] lookup returns the *same* ViewModel
 * instance the Activity uses, and every step drives real PDFBox/AssetManager IO
 * against a genuinely generated 3-page [PdfFixture] PDF. Nothing is mocked —
 * the assertions read the log produced by the ViewModel's own open/annotate/
 * page-edit/search/export/close calls.
 *
 * Robustness contract: the app also emits render/pinch traces under other
 * components, and a step may legitimately emit more than one event. Therefore
 * the test filters [DevLog.snapshot] to `component == PdfFlowTrace.COMPONENT`
 * and checks the expected names as an **ordered subsequence** (extra events in
 * between are allowed), rather than assuming exact counts. Field values are
 * read from [DevLogEntry.fields] directly — no `org.json` dependency — and the
 * exported JSONL text is checked with plain string containment.
 */
class PdfFlowTraceE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var ctx: Context
    private lateinit var vm: PdfViewModel
    private lateinit var fixtureFile: File
    private lateinit var fixtureUri: Uri
    private lateinit var unique: String

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBox() {
            PdfFixture.init(ApplicationProvider.getApplicationContext<Context>())
        }
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // Fresh state for every scenario. The rule creates the Activity before
        // @Before, so clear the *cached* SharedPreferences instances too.
        listOf("library", "onboarding_v1", "pen_box").forEach { name ->
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(ctx.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        PdfFixture.emptyDirectoryTree(ctx.filesDir)
        // Fresh log header: clear(true) drops old files and emits session_start.
        DevLog.enabled = true
        DevLog.clear(includeFiles = true)
        PdfFixture.emptyDirectoryTree(ctx.cacheDir)
        OnboardingStore(ctx).markSeen()

        // Unique name keeps URI-derived docIds distinct and, importantly, avoids
        // leaking the literal fixture text marker into any logged document name.
        unique = "${System.currentTimeMillis()}-${UUID.randomUUID().toString().replace("-", "")}"
        fixtureFile = File(ctx.filesDir, "devlog-flow-$unique.pdf")
        PdfFixture.writeFixture(fixtureFile)
        fixtureUri = PdfFixture.uriFor(ctx, fixtureFile)

        vm = ViewModelProvider(composeRule.activity).get(PdfViewModel::class.java)
        vm.reloadLibrary()
        onUi { clearLibraryMessage() }
    }

    @After
    fun tearDown() {
        composeRule.activity.runOnUiThread { vm.closeDocument() }
        PdfFixture.awaitUntil("document closed in tearDown") { vm.viewer.value == null }
    }

    // ------------------------------------------------------------------ helpers

    /** Posts a VM call to the main looper, exactly as the UI would. */
    private fun onUi(block: PdfViewModel.() -> Unit) {
        composeRule.activity.runOnUiThread { block(vm) }
    }

    /** Waits for a non-null, non-busy viewer (open + first render finished). */
    private fun awaitIdleViewer(): ViewerUiState {
        PdfFixture.awaitUntil("viewer to open and finish rendering") {
            val state = vm.viewer.value
            state != null && !state.busy
        }
        return vm.viewer.value!!
    }

    /** Only the real PDF-flow component; other debug components are ignored. */
    private fun flowEvents(): List<DevLogEntry> =
        DevLog.snapshot().filter { it.component == PdfFlowTrace.COMPONENT }

    private fun firstFlowEvent(event: String): DevLogEntry {
        val events = flowEvents()
        return events.firstOrNull { it.event == event }
            ?: throw AssertionError(
                "Missing ${PdfFlowTrace.COMPONENT}/$event: ${events.map { "${it.sequence}:${it.event}" }}",
            )
    }

    /**
     * Ordered-subsequence assertion: every expected name must appear after the
     * previous one, but unrelated events (and duplicate flow events) may sit
     * between them. This is deliberately count-agnostic.
     */
    private fun assertOrderedSubsequence(actual: List<String>, expected: List<String>) {
        var cursor = 0
        expected.forEach { wanted ->
            val at = actual.indexOf(wanted, cursor)
            assertTrue(
                "Expected ordered flow event '$wanted' after index $cursor; actual=$actual",
                at >= 0,
            )
            cursor = at + 1
        }
    }

    // -------------------------------------------------------------------- tests

    /**
     * Intent: a complete real PDF session must leave an ordered, structured
     * trace on disk that a developer can keep referencing during development.
     * APIs: `openDocument`, `setTool`, `addStroke`, `rotatePage`, `search`,
     * `captureExportSnapshot`, `exportTo`, `closeDocument`, then `DevLog.snapshot`.
     * Asserts: the PDF_FLOW events appear in open -> annotate -> page-edit ->
     * search -> export -> close order with the documented field values, export
     * starts before it finishes with bytes > 0, and no PDF text is logged.
     */
    @Test
    fun realPdfFlowProducesOrderedDevLogTrace() {
        // ---- open a real 3-page PDF
        onUi { openDocument(fixtureUri, addToRecents = false) }
        val opened = awaitIdleViewer()
        assertEquals(PdfFixture.PAGE_COUNT, opened.pageCount)

        // ---- annotate with a real PEN stroke
        onUi {
            setTool(Tool.PEN)
            addStroke(
                listOf(
                    NormalizedPoint(0.12f, 0.52f),
                    NormalizedPoint(0.28f, 0.60f),
                    NormalizedPoint(0.44f, 0.55f),
                ),
            )
        }
        PdfFixture.awaitUntil("pen stroke committed") {
            vm.viewer.value?.annotations?.size == 1
        }

        // ---- edit pages
        val revisionBefore = vm.viewer.value!!.revision
        onUi { rotatePage(true) }
        PdfFixture.awaitUntil("page rotation committed") {
            val state = vm.viewer.value
            state != null && state.revision > revisionBefore &&
                state.pages.getOrNull(0)?.extraRotation == 90 && !state.busy
        }

        // ---- search real extracted PDF text (one hit per fixture page)
        onUi { search("FIXTURE") }
        PdfFixture.awaitUntil("search finished with three hits") {
            val state = vm.viewer.value
            state != null && !state.searching && state.searchHits.size == PdfFixture.PAGE_COUNT
        }

        // ---- export to a real content-URI output
        val snapshot = vm.captureExportSnapshot()
        assertNotNull("captureExportSnapshot must return the active document", snapshot)
        val exported = File(ctx.filesDir, "devlog-flow-export-$unique.pdf")
        val exportedUri = PdfFixture.uriFor(ctx, exported)
        ctx.grantUriPermission(
            ctx.packageName,
            exportedUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        onUi { exportTo(snapshot!!, exportedUri) }
        PdfFixture.awaitUntil("export completed") {
            exported.exists() && exported.length() > 0L && vm.viewer.value?.busy == false
        }

        // ---- close
        onUi { closeDocument() }
        PdfFixture.awaitUntil("document closed") { vm.viewer.value == null }

        // ---- ordered-subsequence trace assertion (count-agnostic by design)
        val events = flowEvents().map { it.event }
        assertOrderedSubsequence(
            events,
            listOf(
                "open_start",
                "open_done",
                "annotate",
                "page_edit",
                "search",
                "export_start",
                "export_done",
                "close",
            ),
        )

        // ---- structured field assertions via DevLogEntry.fields (no org.json)
        val openDone = firstFlowEvent("open_done")
        assertEquals(
            "open_done must record the real page count: ${openDone.fields}",
            PdfFixture.PAGE_COUNT.toString(),
            openDone.fields["pageCount"],
        )

        val annotate = firstFlowEvent("annotate")
        assertTrue(
            "annotate type must be PEN: ${annotate.fields}",
            annotate.fields["type"]?.equals("PEN", ignoreCase = true) == true,
        )
        assertEquals(
            "annotate must record the first stroke: ${annotate.fields}",
            "1",
            annotate.fields["total"],
        )

        val search = firstFlowEvent("search")
        assertEquals(
            "search must record three hits: ${search.fields}",
            PdfFixture.PAGE_COUNT.toString(),
            search.fields["hitCount"],
        )

        val exportDone = firstFlowEvent("export_done")
        assertEquals(
            "export_done must record a flattened export: ${exportDone.fields}",
            "true",
            exportDone.fields["flattened"],
        )
        val bytes = exportDone.fields["bytes"]?.toLongOrNull()
        assertTrue(
            "export_done must record bytes > 0: ${exportDone.fields}",
            bytes != null && bytes > 0L,
        )

        val jsonl = DevLog.exportedJsonl()
        assertFalse("PDF text must never appear in the developer log: $jsonl", jsonl.contains("FIXTURE"))
    }

    /**
     * Intent: opening a missing file must be traced as a failure with a reason,
     * giving developers the same ordered evidence for error paths as for success.
     * APIs: `PdfViewModel.openDocument` -> `PdfFlowTrace.openStart/openFailed`,
     * `libraryMessage`, `viewer`.
     * Asserts: `open_start` precedes `open_fail` and the failure carries a
     * nonblank reason. The strict assertion is justified by the confirmed
     * production path: `PdfViewModel.kt`'s `openDocument` wraps the IO in an
     * inner `runCatching`; on failure `keepViewerOnFailedOpen(null, null)` is
     * false, so `_viewer` is cleared and `_libraryMessage` receives the error.
     * That `onFailure` branch is the reliable hook for `openFailed(reason)`.
     */
    @Test
    fun failedOpenIsTraced() {
        val missing = File(ctx.filesDir, "missing-flow-$unique.pdf")
        assertFalse("missing fixture must not exist", missing.exists())
        val missingUri = PdfFixture.uriFor(ctx, missing)

        onUi { openDocument(missingUri, addToRecents = false) }

        PdfFixture.awaitUntil("failed open to surface an error message") {
            vm.libraryMessage.value != null
        }

        val events = flowEvents().map { it.event }
        assertOrderedSubsequence(events, listOf("open_start", "open_fail"))

        val start = firstFlowEvent("open_start")
        assertEquals(
            "open_start must record addToRecents=false: ${start.fields}",
            "false",
            start.fields["addToRecents"],
        )

        val fail = firstFlowEvent("open_fail")
        val reason = fail.fields["reason"]
        assertTrue("open_fail must carry a nonblank reason: ${fail.fields}", !reason.isNullOrBlank())
    }
}
