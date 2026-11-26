package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickNoteCaptureTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val nowMs = 1_700_000_000_123L

    @Test
    fun kindsHaveDistinctKoreanLabels() {
        assertEquals(
            listOf("빠른 메모", "체크리스트"),
            QuickNoteKind.entries.map { it.label },
        )
    }

    @Test
    fun memoDraftHasOnlyTitleAndBlankTemplate() {
        val draft = QuickNoteCapture.draft(QuickNoteKind.MEMO, nowMs)

        assertEquals(
            NotebookDraft(
                title = "빠른 메모",
                template = PaperTemplate.BLANK,
                quickKind = QuickNoteKind.MEMO,
            ),
            draft,
        )
        assertEquals("", draft.folder)
        assertTrue(draft.tags.isEmpty())
    }

    @Test
    fun checklistDraftHasTitleAndLinedTemplate() {
        val draft = QuickNoteCapture.draft(QuickNoteKind.CHECKLIST, nowMs)

        assertEquals(
            NotebookDraft(
                title = "체크리스트",
                template = PaperTemplate.LINED,
                quickKind = QuickNoteKind.CHECKLIST,
            ),
            draft,
        )
        assertEquals("", draft.folder)
        assertTrue(draft.tags.isEmpty())
    }

    @Test
    fun memoPageIsBlankAndHasNoMandatoryMetadata() {
        val page = QuickNoteCapture.page(QuickNoteKind.MEMO, nowMs)

        assertEquals(PaperTemplate.BLANK, page.template)
        assertEquals(PageOrientation.PORTRAIT, page.orientation)
        assertEquals(PaperTint.WARM, page.tint)
        assertEquals("", page.memo)
        assertTrue(page.annotations.isEmpty())
        assertTrue(page.maskingCards.isEmpty())
        assertTrue(page.checklists.isEmpty())
        assertTrue(page.memoCards.isEmpty())
        assertNull(page.customBackground)
        assertEquals("", page.customBackgroundName)
        assertNull(page.sourcePageIndex)
        assertNull(page.sourceLink)
        assertTrue(page.layers.single().id.isNotBlank())
    }

    @Test
    fun checklistPageSeedsOneCardWithThreeBlankItemsAndZeroProgress() {
        val page = QuickNoteCapture.page(QuickNoteKind.CHECKLIST, nowMs)

        assertEquals(PaperTemplate.LINED, page.template)
        assertEquals(1, page.checklists.size)
        val card = page.checklists.single()
        assertEquals("체크리스트", card.title)
        assertEquals(3, card.items.size)
        assertEquals(listOf("", "", ""), card.items.map { it.text })
        assertTrue(card.items.none { it.done })
        assertTrue(card.items.all { it.id.isNotBlank() })

        val progress = Checklists.progress(card)
        assertEquals(ChecklistProgress(checked = 0, total = 3), progress)
        assertEquals(0f, progress.fraction, 0.0001f)
        assertEquals(0, progress.percent)
        assertEquals("완료 0/3 · 0%", Checklists.progressText(progress))
    }

    @Test
    fun checklistUsesNormalizedGeometryEntirelyInsideThePage() {
        val card = QuickNoteCapture.page(QuickNoteKind.CHECKLIST, nowMs).checklists.single()

        assertEquals(ChecklistCard.DEFAULT_X, card.x, 0.0001f)
        assertEquals(ChecklistCard.DEFAULT_Y, card.y, 0.0001f)
        assertEquals(ChecklistCard.DEFAULT_W, card.w, 0.0001f)
        assertEquals(ChecklistCard.DEFAULT_H, card.h, 0.0001f)
        assertTrue(listOf(card.x, card.y, card.w, card.h).all { it.isFinite() })
        assertTrue(card.x >= 0f && card.y >= 0f)
        assertTrue(card.w >= ChecklistCard.MIN_W && card.h >= ChecklistCard.MIN_H)
        assertTrue(card.x + card.w <= 1f && card.y + card.h <= 1f)
    }

    @Test
    fun checklistTimestampUsesSuppliedClockAndDefaultClockIsCurrent() {
        val supplied = QuickNoteCapture.page(QuickNoteKind.CHECKLIST, nowMs)
        assertEquals(nowMs, supplied.checklists.single().createdAt)

        val before = System.currentTimeMillis()
        val defaultPage = QuickNoteCapture.page(QuickNoteKind.CHECKLIST)
        val after = System.currentTimeMillis()
        assertTrue(defaultPage.checklists.single().createdAt in before..after)
    }

    @Test
    fun everyCallGetsDistinctStableIdsIncludingLayerCardAndItems() {
        val pages = QuickNoteKind.entries.flatMap { kind ->
            listOf(
                QuickNoteCapture.page(kind, nowMs),
                QuickNoteCapture.page(kind, nowMs),
            )
        }
        val allIds = pages.flatMap(::pageIds)

        pages.forEach { page ->
            val ids = pageIds(page)
            assertEquals(ids.size, ids.toSet().size)
        }
        assertEquals(allIds.size, allIds.toSet().size)
    }

    @Test
    fun generatedIdsSurviveNormalizationAndNotebookJsonRoundTrip() {
        QuickNoteKind.entries.forEach { kind ->
            val page = QuickNoteCapture.page(kind, nowMs)
            val ids = pageIds(page)
            assertEquals(ids, pageIds(page.normalized()))

            val notebook = notebook(kind, nowMs, page)
            val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))
            assertEquals(notebook.normalized(), restored)
            assertEquals(ids, pageIds(restored.pages.single()))
        }
    }

    @Test
    fun quickNotesRoundTripThroughNotebookStoreForBothKinds() {
        val store = NotebookStore(temporaryFolder.newFolder("quick-notes"))

        QuickNoteKind.entries.forEachIndexed { index, kind ->
            val notebook = notebook(kind, nowMs + index)
            assertTrue(store.save(notebook))
            assertEquals(notebook.normalized(), store.load(notebook.id))
        }
    }

    private fun notebook(
        kind: QuickNoteKind,
        timestamp: Long,
        page: NotePage = requireNotNull(QuickNoteCapture.draft(kind, timestamp).quickKind).let {
            QuickNoteCapture.page(it, timestamp)
        },
    ): Notebook {
        val draft = QuickNoteCapture.draft(kind, timestamp)
        return Notebook(
            id = "quick-${kind.name}",
            title = draft.title,
            coverColor = draft.coverColor,
            coverStyle = draft.coverStyle,
            folder = draft.folder,
            tags = draft.tags,
            pages = listOf(page),
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }

    private fun pageIds(page: NotePage): List<String> =
        listOf(page.id) +
            page.layers.map { it.id } +
            page.checklists.flatMap { card ->
                listOf(card.id) + card.items.map { it.id }
            }
}
