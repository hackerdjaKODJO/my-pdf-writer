package com.hackerdjakodjo.mypdfwriter.e2e

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.hackerdjakodjo.mypdfwriter.MainActivity
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DocStateStore
import com.hackerdjakodjo.mypdfwriter.data.LibraryStore
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.data.PdfReadingStore
import com.hackerdjakodjo.mypdfwriter.ui.PdfViewModel
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.ui.ViewerUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Real-file E2E coverage for the PDF workbench.
 *
 * Every test opens a genuinely generated 3-page PDF through [PdfViewModel] /
 * [MainActivity], then inspects the real ViewModel StateFlows and the real
 * sidecar/export files on disk. The rule launches the real activity and the
 * [ViewModelProvider] lookup below returns the *same* ViewModel instance the
 * activity uses, so no production code is substituted.
 *
 * Intent delivery uses [MainActivity.onNewIntent] on the UI thread. That is the
 * exact path Android uses for a `singleTop` activity receiving a new ACTION_VIEW
 * or ACTION_SEND intent, and it lets us keep the production rule/activity intact.
 */
class PdfRealFileE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var ctx: Context
    private lateinit var vm: PdfViewModel
    private lateinit var fixtureFile: File
    private lateinit var fixtureUri: Uri

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBox() {
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            PdfFixture.init(appContext)
        }
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // Fresh state for every scenario. The rule creates the activity before
        // @Before, so clear the *cached* SharedPreferences instances too: deleting
        // only the files would leave stale in-memory recents in the VM's store.
        listOf("library", "onboarding_v1", "pen_box").forEach { name ->
            ctx.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(ctx.dataDir, "shared_prefs").listFiles()?.forEach { it.delete() }
        PdfFixture.emptyDirectoryTree(ctx.filesDir)
        PdfFixture.emptyDirectoryTree(ctx.cacheDir)
        OnboardingStore(ctx).markSeen()

        fixtureFile = File(ctx.filesDir, "fixture.pdf")
        PdfFixture.writeFixture(fixtureFile)
        fixtureUri = PdfFixture.uriFor(ctx, fixtureFile)

        vm = ViewModelProvider(composeRule.activity).get(PdfViewModel::class.java)
        vm.reloadLibrary()
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

    private fun openFixture() {
        onUi { openDocument(fixtureUri, addToRecents = false) }
    }

    /** Waits for a non-null, non-busy viewer (open + first render finished). */
    private fun awaitIdleViewer(): ViewerUiState {
        PdfFixture.awaitUntil("viewer to open and finish rendering") {
            val state = vm.viewer.value
            state != null && !state.busy
        }
        return vm.viewer.value!!
    }

    /** Delivers an import intent to the already-launched singleTop activity. */
    private fun deliverImportIntent(action: String, useStream: Boolean) {
        val intent = Intent(action).apply {
            component = ComponentName(ctx, MainActivity::class.java)
            type = "application/pdf"
            addCategory(Intent.CATEGORY_DEFAULT)
            if (useStream) {
                putExtra(Intent.EXTRA_STREAM, fixtureUri)
            } else {
                data = fixtureUri
            }
        }
        // Drive MainActivity's real import handler without calling setIntent():
        // a synthetic external intent left on the rule's Activity prevents
        // ActivityScenarioRule from moving it to DESTROYED during teardown.
        composeRule.activity.runOnUiThread {
            PdfFixture.deliverViewIntent(composeRule.activity, intent)
        }
    }

    // -------------------------------------------------------------------- tests

    /**
     * Intent: an external viewer hands us a real content-URI PDF via ACTION_VIEW.
     * APIs: MainActivity.handleViewIntent -> PdfViewModel.openDocument(uri,
     * addToRecents = false), currentSourceFile(), library.
     * Asserts: 3 real pages rendered, the byte-for-byte source copy exists, and
     * recents are untouched (the documented ACTION_VIEW policy).
     */
    @Test
    fun actionViewOpensRealMultiPagePdf() {
        deliverImportIntent(Intent.ACTION_VIEW, useStream = false)

        composeRule.waitUntil("viewer to open", 15_000L) { vm.viewer.value != null }
        val state = awaitIdleViewer()

        assertEquals(PdfFixture.PAGE_COUNT, state.pageCount)
        assertEquals(PdfFixture.PAGE_COUNT, state.sourcePageCount)
        val source = vm.currentSourceFile()
        assertNotNull("ACTION_VIEW must copy the content URI into filesDir/sources", source)
        assertTrue("source copy must exist", source!!.exists())
        assertEquals("source copy must keep all original bytes", fixtureFile.length(), source.length())

        assertTrue(
            "ACTION_VIEW must not add to recents",
            vm.library.value.none { it.uri == fixtureUri.toString() },
        )
        assertTrue("library must stay empty for ACTION_VIEW", vm.library.value.isEmpty())
    }

    /**
     * Intent: another app shares a PDF via ACTION_SEND + EXTRA_STREAM.
     * APIs: MainActivity.handleViewIntent -> openDocument(addToRecents = true),
     * reloadLibrary(), LibraryStore.load().
     * Asserts: the viewer opens and exactly one recent is persisted and reloadable.
     */
    @Test
    fun openDocumentRendersRealPagesAndAddsRecentsForSend() {
        deliverImportIntent(Intent.ACTION_SEND, useStream = true)

        val state = awaitIdleViewer()
        assertEquals(PdfFixture.PAGE_COUNT, state.pageCount)
        assertTrue(
            "working file must exist after render",
            vm.currentWorkingFile()?.exists() == true,
        )

        PdfFixture.awaitUntil("recent persisted to LibraryStore") {
            LibraryStore(ctx).load().size == 1
        }
        assertEquals(1, vm.library.value.size)
        assertEquals(fixtureUri.toString(), vm.library.value.first().uri)

        vm.reloadLibrary()
        assertEquals("reloadLibrary must see the persisted recent", 1, vm.library.value.size)
        assertEquals(fixtureUri.toString(), vm.library.value.first().uri)
    }

    /**
     * Intent: a student annotates a PDF, closes it, then reopens the same URI.
     * APIs: setTool, addStroke, addText, closeDocument, openDocument, viewer.
     * Asserts: pen/highlight/text annotation ids, types, pages, points and text
     * all survive the DocState sidecar round-trip unchanged.
     */
    @Test
    fun penHighlightAndTextAnnotationsPersistAcrossCloseAndReopen() {
        openFixture()
        awaitIdleViewer()

        onUi {
            setTool(Tool.PEN)
            addStroke(
                listOf(
                    NormalizedPoint(0.10f, 0.20f),
                    NormalizedPoint(0.18f, 0.28f),
                    NormalizedPoint(0.26f, 0.34f),
                ),
            )
            setTool(Tool.HIGHLIGHT)
            addStroke(
                listOf(
                    NormalizedPoint(0.30f, 0.44f),
                    NormalizedPoint(0.48f, 0.56f),
                ),
            )
            addText(NormalizedPoint(0.60f, 0.70f), "E2E TEXT")
        }
        PdfFixture.awaitUntil("three annotations committed") {
            vm.viewer.value?.annotations?.size == 3
        }
        val before = vm.viewer.value!!.annotations
        assertEquals(
            listOf(AnnotationType.PEN, AnnotationType.HIGHLIGHT, AnnotationType.TEXT),
            before.map { it.type },
        )

        val docId = vm.viewer.value!!.docId
        PdfFixture.awaitUntil("annotated DocState written to disk") {
            DocStateStore(ctx).load(docId)?.annotations?.size == 3
        }

        onUi { closeDocument() }
        PdfFixture.awaitUntil("document closed") { vm.viewer.value == null }

        onUi { openDocument(fixtureUri, addToRecents = false) }
        PdfFixture.awaitUntil("reopened with all annotations") {
            vm.viewer.value?.annotations?.size == 3
        }
        val after = vm.viewer.value!!.annotations

        assertEquals(before.map { it.id }, after.map { it.id })
        assertEquals(before.map { it.type }, after.map { it.type })
        assertEquals(before.map { it.page }, after.map { it.page })
        assertEquals(before[0].points, after[0].points)
        assertEquals(before[1].points, after[1].points)
        assertEquals(before[2].points, after[2].points)
        assertEquals(before[2].text, after[2].text)
    }

    /**
     * Intent: verify undo/redo is identity-stable, not just count-stable.
     * APIs: setTool(PEN), addStroke, undo, redo, viewer.canUndo/canRedo.
     * Asserts: the exact same annotation ids return after an undo/redo cycle.
     */
    @Test
    fun undoRedoRestoresTheSameAnnotationIds() {
        openFixture()
        awaitIdleViewer()

        onUi {
            setTool(Tool.PEN)
            addStroke(listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.2f)))
        }
        PdfFixture.awaitUntil("first stroke added") {
            vm.viewer.value?.annotations?.size == 1
        }
        val firstId = vm.viewer.value!!.annotations.single().id

        onUi {
            addStroke(listOf(NormalizedPoint(0.3f, 0.3f), NormalizedPoint(0.4f, 0.4f)))
        }
        PdfFixture.awaitUntil("second stroke added") {
            vm.viewer.value?.annotations?.size == 2
        }
        val bothIds = vm.viewer.value!!.annotations.map { it.id }
        assertTrue(bothIds.contains(firstId))
        assertTrue(vm.viewer.value!!.canUndo)

        onUi { undo() }
        PdfFixture.awaitUntil("undo back to one stroke") {
            vm.viewer.value?.annotations?.size == 1
        }
        assertEquals(firstId, vm.viewer.value!!.annotations.single().id)
        assertTrue(vm.viewer.value!!.canRedo)

        onUi { redo() }
        PdfFixture.awaitUntil("redo back to two strokes") {
            vm.viewer.value?.annotations?.size == 2
        }
        assertEquals(bothIds, vm.viewer.value!!.annotations.map { it.id })
    }

    /**
     * Intent: rotate, insert a blank page and delete a page, then reopen the file.
     * APIs: rotatePage, insertBlankPage, deletePage, closeDocument, openDocument.
     * Asserts: the edited PageSpec list (rotation, blank marker, source mapping)
     * survives the working-file assemble and the DocState sidecar round-trip.
     */
    @Test
    fun pageOpsAndRotationPersistAcrossReopen() {
        openFixture()
        awaitIdleViewer()

        val revisionBeforeRotate = vm.viewer.value!!.revision
        onUi { rotatePage(true) }
        PdfFixture.awaitUntil("page 0 rotated") {
            val state = vm.viewer.value
            state != null && state.revision > revisionBeforeRotate &&
                state.pages.getOrNull(0)?.extraRotation == 90 && !state.busy
        }

        val revisionBeforeInsert = vm.viewer.value!!.revision
        onUi { insertBlankPage(0) }
        PdfFixture.awaitUntil("blank page inserted after page 0") {
            val state = vm.viewer.value
            state != null && state.revision > revisionBeforeInsert &&
                state.pageCount == 4 && !state.busy
        }

        val revisionBeforeDelete = vm.viewer.value!!.revision
        onUi { deletePage(2) }
        PdfFixture.awaitUntil("source page 1 deleted") {
            val state = vm.viewer.value
            state != null && state.revision > revisionBeforeDelete &&
                state.pageCount == 3 && !state.busy
        }

        val before = vm.viewer.value!!
        assertEquals(3, before.pages.size)
        assertEquals(90, before.pages[0].extraRotation)
        assertNull("inserted page must be blank", before.pages[1].sourcePage)
        assertEquals(2, before.pages[2].sourcePage)

        val docId = before.docId
        PdfFixture.awaitUntil("edited page layout written to disk") {
            DocStateStore(ctx).load(docId)?.pages?.size == 3
        }

        onUi { closeDocument() }
        PdfFixture.awaitUntil("document closed") { vm.viewer.value == null }

        onUi { openDocument(fixtureUri, addToRecents = false) }
        PdfFixture.awaitUntil("reopened edited document") {
            val state = vm.viewer.value
            state != null && state.docId == docId && !state.busy && state.pageCount == 3
        }
        assertEquals(before.pages, vm.viewer.value!!.pages)
    }

    /**
     * Intent: after structural edits the user asks to restore the original PDF.
     * APIs: rotatePage, insertBlankPages, resetPages.
     * Asserts: pageCount is back to 3, every spec points at its own source page
     * and no extra rotation remains.
     */
    @Test
    fun resetPagesRestoresOriginalPageCount() {
        openFixture()
        awaitIdleViewer()

        val r0 = vm.viewer.value!!.revision
        onUi { rotatePage(true) }
        PdfFixture.awaitUntil("a page is rotated") {
            val state = vm.viewer.value
            state != null && state.revision > r0 &&
                state.pages.getOrNull(0)?.extraRotation == 90 && !state.busy
        }

        val r1 = vm.viewer.value!!.revision
        onUi { insertBlankPages(1, 2) }
        PdfFixture.awaitUntil("two blank pages inserted") {
            val state = vm.viewer.value
            state != null && state.revision > r1 && state.pageCount == 5 && !state.busy
        }

        val r2 = vm.viewer.value!!.revision
        onUi { resetPages() }
        PdfFixture.awaitUntil("pages reset to the original layout") {
            val state = vm.viewer.value
            state != null && state.revision > r2 && !state.busy &&
                state.pageCount == PdfFixture.PAGE_COUNT &&
                state.pages.all { it.sourcePage != null && it.extraRotation == 0 }
        }

        val state = vm.viewer.value!!
        assertEquals(PdfFixture.PAGE_COUNT, state.pageCount)
        assertEquals(listOf(0, 1, 2), state.pages.map { it.sourcePage })
        assertTrue(state.pages.all { it.extraRotation == 0 })
    }

    /**
     * Intent: search real extracted PDF text and navigate the result list.
     * APIs: search, moveSearchHit, goToSearchHit, viewer.searchHits/currentSearchHit/pageIndex.
     * Asserts: three hits (one per fixture page), wrap-around stepping and page jumps.
     */
    @Test
    fun searchRealPdfTextFindsFixtureWords() {
        openFixture()
        awaitIdleViewer()

        onUi { search("FIXTURE") }
        PdfFixture.awaitUntil("search finished with three hits") {
            val state = vm.viewer.value
            state != null && !state.searching && state.searchHits.size == PdfFixture.PAGE_COUNT
        }
        val hits = vm.viewer.value!!.searchHits
        assertEquals(listOf(0, 1, 2), hits.map { it.page })
        assertTrue(hits.all { it.count >= 1 })

        val initial = vm.viewer.value!!.currentSearchHit
        assertEquals(0, initial)
        onUi { moveSearchHit(1) }
        PdfFixture.awaitUntil("next search hit selected") {
            vm.viewer.value?.currentSearchHit == 1
        }
        composeRule.waitUntil("reader followed the hit", 15_000L) {
            vm.viewer.value?.pageIndex == 1 && vm.viewer.value?.busy == false
        }

        onUi { goToSearchHit(2) }
        PdfFixture.awaitUntil("third search hit selected") {
            vm.viewer.value?.currentSearchHit == 2
        }
        composeRule.waitUntil("reader jumped to page 3", 15_000L) {
            vm.viewer.value?.pageIndex == 2 && vm.viewer.value?.busy == false
        }
    }

    /**
     * Intent: export an annotated PDF and confirm the ink is really flattened.
     * APIs: setTool, addStroke, captureExportSnapshot, exportTo, then openDocument.
     * Asserts: PDFBox sees 3 pages with zero /Annots, PdfRenderer sees non-white
     * ink in the middle band, and reopening the export yields no annotations.
     */
    @Test
    fun exportFlattensAnnotationsAndReopensAsPdf() {
        openFixture()
        awaitIdleViewer()
        val originalDocId = vm.viewer.value!!.docId

        onUi {
            setTool(Tool.PEN)
            addStroke(
                listOf(
                    NormalizedPoint(0.15f, 0.52f),
                    NormalizedPoint(0.25f, 0.60f),
                    NormalizedPoint(0.35f, 0.55f),
                    NormalizedPoint(0.45f, 0.68f),
                    NormalizedPoint(0.55f, 0.62f),
                    NormalizedPoint(0.65f, 0.75f),
                    NormalizedPoint(0.75f, 0.70f),
                ),
            )
        }
        PdfFixture.awaitUntil("pen stroke added") {
            vm.viewer.value?.annotations?.size == 1
        }

        val snapshot = vm.captureExportSnapshot()
        assertNotNull("captureExportSnapshot must return the active document", snapshot)

        val exported = File(ctx.filesDir, "exported.pdf")
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
        PdfFixture.assertFlattenedPdf(exported, PdfFixture.PAGE_COUNT, annotatedPage = 0)

        onUi { openDocument(exportedUri, addToRecents = false) }
        PdfFixture.awaitUntil("exported file reopened") {
            val state = vm.viewer.value
            state != null && state.docId != originalDocId && !state.busy
        }
        val reopened = vm.viewer.value!!
        assertEquals(PdfFixture.PAGE_COUNT, reopened.pageCount)
        assertTrue(
            "a flattened export has no app annotations to restore",
            reopened.annotations.isEmpty(),
        )
    }

    /**
     * HIGH-RISK REGRESSION: a bookmark must follow its *logical page*, not its
     * numeric index, when an earlier page is deleted.
     *
     * Production semantics (verified by reading the code):
     *  - [PdfBookmark.pageIndex] is a raw, zero-based index; `addCurrentPageBookmark`
     *    stores `viewer.pageIndex` verbatim (PdfViewModel.kt:1770-1783).
     *  - `PageOps.delete` remaps annotations, notes and memo cards through
     *    `oldToNew` (PageOps.kt:66-109, PdfViewModel.pageEditWithMemos:1166-1205),
     *    but nothing remaps `_bookmarks`.
     *  - `saveReadingNow` therefore persists the stale index, and on reopen
     *    `PdfReadingState.normalized(pages.size)` drops it when it is out of range
     *    (PdfReading.kt:55-63).
     *
     * Desired behaviour asserted below: after deleting page 0, the bookmark that
     * pointed at original page 2 (viewer.pages[2].sourcePage == 2) must now point
     * at index 1, keeping the same id and title. Current code is expected to FAIL
     * here (it leaves pageIndex == 2, or drops the bookmark on reopen). Do not
     * "fix" this by weakening the assertion — production code is out of scope.
     */
    @Test
    fun bookmarkFollowsPageDeletion() {
        openFixture()
        awaitIdleViewer()

        onUi { goToPage(2) }
        PdfFixture.awaitUntil("reader on original page 3") {
            vm.viewer.value?.pageIndex == 2 && vm.viewer.value?.busy == false
        }
        val sourcePageAtBookmark = vm.viewer.value!!.pages[2].sourcePage
        assertEquals(2, sourcePageAtBookmark)

        onUi { addCurrentPageBookmark("Lecture page 3") }
        PdfFixture.awaitUntil("bookmark added on page 3") {
            vm.bookmarks.value.size == 1
        }
        val before = vm.bookmarks.value.single()
        assertEquals(2, before.pageIndex)
        assertEquals("Lecture page 3", before.title)

        onUi { deletePage(0) }
        PdfFixture.awaitUntil("page 0 deleted") {
            val state = vm.viewer.value
            state != null && state.pageCount == 2 && !state.busy
        }

        // The logical source page moved from index 2 to index 1.
        val pagesAfterDelete = vm.viewer.value!!.pages
        assertEquals(1, pagesAfterDelete.indexOfFirst { it.sourcePage == sourcePageAtBookmark })

        PdfFixture.awaitUntil("bookmark remapped after page deletion") {
            vm.bookmarks.value.singleOrNull()?.pageIndex == 1
        }
        val after = vm.bookmarks.value.single()
        assertEquals("bookmark id must survive the page edit", before.id, after.id)
        assertEquals("bookmark title must survive the page edit", before.title, after.title)
        assertEquals(1, after.pageIndex)

        // Persistence check: the remapped bookmark must also survive close/reopen.
        val docId = vm.viewer.value!!.docId
        PdfFixture.awaitUntil("remapped bookmark written to disk") {
            PdfReadingStore(ctx).load(docId)?.bookmarks?.singleOrNull()?.pageIndex == 1
        }
        onUi { closeDocument() }
        PdfFixture.awaitUntil("document closed") { vm.viewer.value == null }
        onUi { openDocument(fixtureUri, addToRecents = false) }
        PdfFixture.awaitUntil("reopened after bookmark edit") {
            vm.viewer.value?.docId == docId && vm.viewer.value?.busy == false
        }
        assertEquals(1, vm.bookmarks.value.singleOrNull()?.pageIndex)
    }
}
