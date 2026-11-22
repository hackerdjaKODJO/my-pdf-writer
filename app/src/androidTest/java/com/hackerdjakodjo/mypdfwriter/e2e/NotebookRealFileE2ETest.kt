package com.hackerdjakodjo.mypdfwriter.e2e

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.MaskingCardState
import com.hackerdjakodjo.mypdfwriter.data.MaskingCards
import com.hackerdjakodjo.mypdfwriter.data.NotebookDraft
import com.hackerdjakodjo.mypdfwriter.data.NotebookStore
import com.hackerdjakodjo.mypdfwriter.data.OnboardingStore
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.ui.NotebookViewModel
import com.hackerdjakodjo.mypdfwriter.ui.NoteEditorScreen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Real-disk end-to-end coverage for the **notebook** half of the app.
 *
 * The existing 86 instrumentation cases drive synthetic composables and never
 * touch `files/notebooks/`. Here every test builds a real [NotebookViewModel]
 * over a real [NotebookStore], renders the real [NoteEditorScreen], and then
 * reopens the notebook through a **brand-new** ViewModel/Store so the assertions
 * can only pass if the JSON on disk actually holds the edits.
 *
 * Why `ComponentActivity` instead of the real `MainActivity`: Home -> Editor
 * would require driving the create dialog and SAF-free library navigation while
 * the activity is launched before `@Before` can reset disk state. Hosting the
 * real editor with a real VM keeps the persistence path identical (the VM is the
 * same object `MainActivity` uses via `by viewModels()`) while making the reset
 * deterministic. The UI gestures below are the ones proven in
 * `NoteEditorStrokeE2ETest` / `NoteMenuAndStrokeE2ETest`.
 *
 * Simplification / limitation: instrumented touches cannot inject a stylus, so
 * finger writing is enabled through the note menu in every drawing test. The
 * masking-card test drives the real note-menu -> 복습 -> 가리기 카드 sheet and
 * the on-page tape, but grades only one card with O (정답); the W (오답) branch
 * uses the same `MaskingCards.mark` callback and is covered by JVM tests.
 *
 * APIs used: `NotebookViewModel.create/open/updateCurrent/addPage/close`,
 * `NotebookStore.save/load`, `Notebook.toJson/fromJson`, `NoteEditorScreen`,
 * `MaskingCards.reveal/mark/score`, and Compose `performTouchInput`/`performClick`.
 * No production bug was found. Note that ordinary edits are debounced 350 ms by
 * the VM, so each "leave editor" step goes through `close()` (synchronous
 * `saveNow`) before disk assertions.
 */
class NotebookRealFileE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The VM currently hosted by the single setContent call for this test. */
    private val hostVm = mutableStateOf<NotebookViewModel?>(null)
    private var contentHosted = false

    @Before
    fun resetDiskState() {
        val ctx = composeRule.activity.applicationContext
        // Wipe app-private state so each case starts from a clean library.
        listOf(ctx.filesDir, ctx.cacheDir).forEach { dir ->
            dir.listFiles()?.forEach { child -> child.deleteRecursively() }
        }
        File(ctx.filesDir.parentFile, "shared_prefs").deleteRecursively()
        // Keep onboarding from covering the editor during instrumentation.
        OnboardingStore(ctx).markSeen()
    }

    // -------------------------------------------------------------------------
    // 1. Two pages of strokes survive a real disk reload
    // -------------------------------------------------------------------------
    @Test
    fun notebookWithTwoPagesOfStrokesSurvivesDiskReload() {
        val vm = newViewModel()
        val created = vm.create(
            NotebookDraft(title = "E2E 저장 노트", template = PaperTemplate.BLANK),
        )
        val id = created.id
        showEditor(vm)
        enableFingerWriting()

        // Page 1: one finger stroke through the real NoteCanvas pointer input.
        drawStroke(yFraction = 0.34f)
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("page 1 stroke committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations?.size == 1
        }

        // Add a page through the real page-manager UI so the editor's working
        // notebook and the ViewModel stay in lockstep (no adoption race).
        composeRule.onNodeWithContentDescription("페이지 1 / 1").performClick()
        waitForText("페이지 추가")
        composeRule.onNodeWithText("페이지 추가").performClick()
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("second page created") {
            vm.current.value?.pages?.size == 2
        }
        composeRule.onNodeWithTag(CANVAS_TAG).assertExists()

        // Page 2: a second stroke, committed onto the newly added page.
        drawStroke(yFraction = 0.60f)
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("page 2 stroke committed") {
            vm.current.value?.pages?.getOrNull(1)?.annotations?.size == 1
        }

        // Leave the editor; onBack calls vm.close() which flushes synchronously.
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.waitForIdle()

        // Raw JSON under files/notebooks/<id>.json.
        val jsonFile = File(app().filesDir, "notebooks/$id.json")
        PdfFixture.awaitFile(jsonFile)
        val json = JSONObject(jsonFile.readText())
        val pagesJson = json.getJSONArray("pages")
        assertEquals("two pages must be on disk", 2, pagesJson.length())
        assertEquals(
            "one stroke on page 1 in JSON",
            1,
            pagesJson.getJSONObject(0).getJSONArray("annotations").length(),
        )
        assertEquals(
            "one stroke on page 2 in JSON",
            1,
            pagesJson.getJSONObject(1).getJSONArray("annotations").length(),
        )
        assertEquals(
            "stored stroke type",
            AnnotationType.PEN.name,
            pagesJson.getJSONObject(0).getJSONArray("annotations").getJSONObject(0).getString("t"),
        )

        val onDisk = NotebookStore(app()).load(id)
        assertNotNull(onDisk)
        val saved = onDisk!!
        assertEquals(2, saved.pages.size)
        assertTrue(saved.pages[0].annotations.all { it.type == AnnotationType.PEN && it.points.size >= 2 })
        assertTrue(saved.pages[1].annotations.all { it.type == AnnotationType.PEN && it.points.size >= 2 })

        // Reopen through a brand-new VM/Store: this can only see disk bytes.
        val reloaded = newViewModel()
        reloaded.open(id)
        showEditor(reloaded)
        composeRule.onNodeWithTag(CANVAS_TAG).assertExists()
        val live = reloaded.current.value!!
        assertEquals(2, live.pages.size)
        assertEquals(1, live.pages[0].annotations.size)
        assertEquals(1, live.pages[1].annotations.size)
        reloaded.close()
    }

    // -------------------------------------------------------------------------
    // 2. Masking-card reveal + O grade persist across reload
    // -------------------------------------------------------------------------
    @Test
    fun maskingCardRevealAndScorePersistAcrossReload() {
        val vm = newViewModel()
        val created = vm.create(
            NotebookDraft(title = "E2E 복습 노트", template = PaperTemplate.BLANK),
        )
        val id = created.id
        showEditor(vm)

        // Real UI path: 노트 메뉴 -> 복습 카드 -> 가리기 카드 -> add card.
        composeRule.onNodeWithContentDescription(NOTE_MENU).performClick()
        waitForText(NOTE_MENU)
        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText("복습 카드"))
        composeRule.onNodeWithText("복습 카드").performClick()
        waitForTextGone(NOTE_MENU)
        waitForText("가리기 카드")
        composeRule.onNodeWithText("가리기 카드").performClick()
        waitForText("현재 페이지에 카드 추가")

        composeRule.onNodeWithText("현재 페이지에 카드 추가").performClick()
        waitForText("가리기 카드 추가")
        // The editor dialog has exactly six text fields: 질문, 정답, X, Y, W, H.
        composeRule.onAllNodes(hasSetTextAction())[0].performTextInput("E2E 질문")
        composeRule.onAllNodes(hasSetTextAction())[1].performTextInput("E2E 정답")
        composeRule.onNodeWithText("저장").performClick()
        PdfFixture.awaitUntil("card added through real VM") {
            vm.current.value?.pages?.getOrNull(0)?.maskingCards?.size == 1
        }
        composeRule.onNodeWithText("닫기").performClick()
        waitForTextGone("현재 페이지에 카드 추가")

        // On-page tape: tap to reveal, then grade O (정답). performTouchInput
        // clicks the text/icon bounds without needing a synthetic click action.
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText("탭하여 정답").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("탭하여 정답").performTouchInput { click() }
        PdfFixture.awaitUntil("card revealed") {
            vm.current.value?.pages?.getOrNull(0)?.maskingCards?.firstOrNull()?.state ==
                MaskingCardState.REVEALED
        }
        composeRule.onNodeWithContentDescription("정답").performTouchInput { click() }
        PdfFixture.awaitUntil("card graded O") {
            vm.current.value?.pages?.getOrNull(0)?.maskingCards?.firstOrNull()?.state ==
                MaskingCardState.CORRECT
        }

        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.waitForIdle()

        // Fresh VM/Store round-trip: the grade must be in the JSON on disk.
        val reloaded = newViewModel()
        reloaded.open(id)
        showEditor(reloaded)
        val diskCard = NotebookStore(app()).load(id)!!.pages[0].maskingCards.single()
        assertEquals("reveal + O grade persisted", MaskingCardState.CORRECT, diskCard.state)
        assertEquals("prompt persisted", "E2E 질문", diskCard.prompt)
        assertEquals("answer persisted", "E2E 정답", diskCard.answer)
        val score = MaskingCards.score(listOf(diskCard))
        assertEquals(1, score.total)
        assertEquals(1, score.correct)
        assertEquals(1, score.graded)
        assertEquals(0, score.wrong)
        assertTrue(score.progress == 1f && score.accuracy == 1f)
        reloaded.close()
    }

    // -------------------------------------------------------------------------
    // 3. Undo/redo over real strokes keeps annotation ids stable on disk
    // -------------------------------------------------------------------------
    @Test
    fun noteMenuFingerWritingAndUndoRedoUseRealNotebook() {
        val vm = newViewModel()
        val created = vm.create(
            NotebookDraft(title = "E2E 실행 취소 노트", template = PaperTemplate.BLANK),
        )
        val id = created.id
        showEditor(vm)
        enableFingerWriting()

        drawStroke(yFraction = 0.34f)
        drawStroke(yFraction = 0.60f)
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("two strokes committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations?.size == 2
        }
        val afterTwo = vm.current.value!!.pages[0].annotations
        assertEquals(2, afterTwo.map { it.id }.toSet().size)

        // Always-visible top-bar history actions (not the menu duplicates).
        composeRule.onNodeWithContentDescription("실행 취소").performClick()
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("undo committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations?.size == 1
        }
        val afterUndo = vm.current.value!!.pages[0].annotations
        assertEquals(afterTwo.first().id, afterUndo.single().id)

        composeRule.onNodeWithContentDescription("다시 실행").performClick()
        composeRule.waitForIdle()
        PdfFixture.awaitUntil("redo committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations?.size == 2
        }
        val afterRedo = vm.current.value!!.pages[0].annotations
        assertEquals(
            "redo must restore the same annotation ids, not fresh ones",
            afterTwo.map { it.id }.toSet(),
            afterRedo.map { it.id }.toSet(),
        )

        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.waitForIdle()

        val onDisk = NotebookStore(app()).load(id)!!.pages[0].annotations
        assertEquals("disk keeps the redone strokes", 2, onDisk.size)
        assertEquals(
            "disk annotation ids match the ui ids",
            afterRedo.map { it.id }.toSet(),
            onDisk.map { it.id }.toSet(),
        )
    }

    // -------------------------------------------------------------------------
    // 4. TEXT + RECT annotations round-trip through JSON
    // -------------------------------------------------------------------------
    @Test
    fun noteTextAndRectAnnotationsRoundTrip() {
        val vm = newViewModel()
        val created = vm.create(
            NotebookDraft(title = "E2E 글상자 노트", template = PaperTemplate.BLANK),
        )
        val id = created.id
        showEditor(vm)
        enableFingerWriting()

        // TEXT tool -> tap canvas -> dialog -> type -> 추가.
        composeRule.onNodeWithContentDescription("글상자").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.42f, height * 0.38f))
            up()
        }
        waitForText("글상자 입력")
        composeRule.onNode(hasSetTextAction()).performTextInput("E2E 글상자")
        composeRule.onNodeWithText("추가").performClick()
        PdfFixture.awaitUntil("TEXT annotation committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations
                ?.any { it.type == AnnotationType.TEXT && it.text == "E2E 글상자" } == true
        }

        // RECT tool -> drag a rectangle on the canvas.
        composeRule.onNodeWithContentDescription("도형").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.30f, height * 0.55f))
            moveTo(Offset(width * 0.68f, height * 0.72f))
            up()
        }
        PdfFixture.awaitUntil("RECT annotation committed") {
            vm.current.value?.pages?.getOrNull(0)?.annotations
                ?.any { it.type == AnnotationType.RECT && it.points.size >= 2 } == true
        }

        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.waitForIdle()

        val file = File(app().filesDir, "notebooks/$id.json")
        PdfFixture.awaitFile(file)
        val annotationsJson = JSONObject(file.readText())
            .getJSONArray("pages").getJSONObject(0).getJSONArray("annotations")
        val types = (0 until annotationsJson.length()).map {
            annotationsJson.getJSONObject(it).getString("t")
        }
        assertTrue("TEXT must be stored", AnnotationType.TEXT.name in types)
        assertTrue("RECT must be stored", AnnotationType.RECT.name in types)

        val reloaded = newViewModel()
        reloaded.open(id)
        showEditor(reloaded)
        composeRule.onNodeWithTag(CANVAS_TAG).assertExists()
        val annotations = NotebookStore(app()).load(id)!!.pages[0].annotations
        val text = annotations.single { it.type == AnnotationType.TEXT }
        val rect = annotations.single { it.type == AnnotationType.RECT }
        assertEquals("E2E 글상자", text.text)
        assertEquals(1, text.points.size)
        assertTrue("RECT keeps two corner points", rect.points.size >= 2)
        assertTrue(
            "RECT geometry must be non-degenerate",
            rect.points.first().x != rect.points.last().x ||
                rect.points.first().y != rect.points.last().y,
        )
        reloaded.close()
    }

    // -------------------------------------------------------------------------
    // Harness / helpers
    // -------------------------------------------------------------------------

    private fun app(): Application = composeRule.activity.application

    private fun newViewModel(): NotebookViewModel = NotebookViewModel(app())

    /**
     * Hosts the real editor against a real VM; `current == null` means "left".
     * A Compose rule permits exactly one setContent per test, so reopening with
     * a fresh VM swaps [hostVm] instead of calling setContent a second time.
     * Keying the subtree on the VM instance also forces NoteEditorScreen's
     * `remember(notebook.id)` working state to be rebuilt for the reloaded VM,
     * so the disk round-trip is genuinely re-read rather than reusing old state.
     */
    private fun showEditor(vm: NotebookViewModel) {
        hostVm.value = vm
        if (!contentHosted) {
            composeRule.setContent {
                val host by hostVm
                host?.let { current ->
                    key(current) {
                        EditorHost(vm = current)
                    }
                }
            }
            contentHosted = true
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun EditorHost(vm: NotebookViewModel) {
        val notebook by vm.current.collectAsState()
        val pageIndex by vm.pageIndex.collectAsState()
        val current = notebook
        if (current != null) {
            NoteEditorScreen(
                notebook = current,
                pageIndex = pageIndex,
                onBack = { vm.close() },
                onChange = { vm.updateCurrent(it) },
                onPageIndexChange = { vm.setPageIndex(it) },
                onExportPdf = {},
            )
        }
    }

    /** Instrumentation injects touch only, so enable 손가락 필기 in the ink sheet. */
    private fun enableFingerWriting() {
        composeRule.onNodeWithContentDescription(NOTE_MENU).performClick()
        waitForText(NOTE_MENU)
        val menu = composeRule.onNode(hasScrollToIndexAction())
        menu.performScrollToNode(hasText(INK_SETTINGS))
        composeRule.onNodeWithText(INK_SETTINGS).performClick()
        waitForTextGone(NOTE_MENU)
        waitForText(FINGER_WRITING)
        composeRule.onNodeWithText(FINGER_WRITING).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(CLOSE).performClick()
        waitForTextGone(FINGER_WRITING)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(CANVAS_TAG).assertExists()
    }

    private fun drawStroke(yFraction: Float) {
        composeRule.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(Offset(width * 0.28f, height * yFraction))
            moveTo(Offset(width * 0.45f, height * (yFraction + 0.04f)))
            moveTo(Offset(width * 0.72f, height * yFraction))
            up()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForTextGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 6_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CANVAS_TAG = "note_canvas"
        const val NOTE_MENU = "노트 메뉴"
        const val INK_SETTINGS = "필기 설정"
        const val FINGER_WRITING = "손가락 필기"
        const val CLOSE = "닫기"
    }
}
