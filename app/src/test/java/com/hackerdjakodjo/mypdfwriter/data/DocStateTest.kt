package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocStateTest {

    private fun annotation(page: Int) = Annotation(
        type = AnnotationType.PEN,
        page = page,
        color = 0,
        width = 0.004f,
        points = listOf(NormalizedPoint(0.5f, 0.5f)),
    )

    private fun memo(page: Int, id: String, text: String = "메모") = MemoCard(
        id = id,
        text = text,
        tint = MemoTint.MINT,
        page = page,
        x = 0.25f,
        y = 0.4f,
        w = 0.4f,
        h = 0.2f,
    )

    private fun state(
        pages: List<PageSpec>,
        annotations: List<Annotation> = emptyList(),
        notes: Map<Int, String> = emptyMap(),
        displayPrefs: DisplayPrefs = DisplayPrefs(),
        memoCards: List<MemoCard> = emptyList(),
    ) = DocState(
        docId = "doc",
        displayName = "lecture.pdf",
        pages = pages,
        annotations = annotations,
        notes = notes,
        displayPrefs = displayPrefs,
        memoCards = memoCards,
    )

    @Test
    fun normalized_keepsEverythingWhenSourceIsUnchanged() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(1)),
            annotations = listOf(annotation(0), annotation(1)),
            notes = mapOf(0 to "a", 1 to "b"),
        )

        val result = original.normalized(2)

        assertEquals(original.pages, result.pages)
        assertEquals(original.annotations, result.annotations)
        assertEquals(original.notes, result.notes)
    }

    @Test
    fun normalized_dropsMissingSourcePagesAndRemapsIndexes() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(1), PageSpec(5), PageSpec(null)),
            annotations = listOf(annotation(0), annotation(2), annotation(3)),
            notes = mapOf(1 to "kept", 2 to "dropped", 3 to "blank"),
        )

        val result = original.normalized(2)

        assertEquals(listOf(PageSpec(0), PageSpec(1), PageSpec(null)), result.pages)
        assertEquals(listOf(0, 2), result.annotations.map { it.page })
        assertEquals(mapOf(1 to "kept", 2 to "blank"), result.notes)
    }

    @Test
    fun normalized_resetsReferencesWhenNoPageSurvives() {
        val original = state(
            pages = listOf(PageSpec(5)),
            annotations = listOf(annotation(0)),
            notes = mapOf(0 to "gone"),
        )

        val result = original.normalized(2)

        assertEquals(listOf(PageSpec(0), PageSpec(1)), result.pages)
        assertTrue(result.annotations.isEmpty())
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun normalized_handlesEmptySource() {
        val original = state(
            pages = listOf(PageSpec(0)),
            annotations = listOf(annotation(0)),
            notes = mapOf(0 to "gone"),
        )

        val result = original.normalized(0)

        assertEquals(listOf(PageSpec(null)), result.pages)
        assertTrue(result.annotations.isEmpty())
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun jsonRoundTrip_keepsDisplayPrefs() {
        val original = state(
            pages = listOf(PageSpec(0)),
            displayPrefs = DisplayPrefs(nightMode = true, brightness = 0.55f),
        )

        val parsed = DocState.fromJson("doc", JSONObject(original.toJson().toString()))

        assertEquals(DisplayPrefs(nightMode = true, brightness = 0.55f), parsed.displayPrefs)
    }

    @Test
    fun legacyJsonWithoutDisplay_loadsDefaultsAndDoesNotWriteTheField() {
        val legacy = JSONObject().apply {
            put("version", 1)
            put("name", "lecture.pdf")
            put("pages", org.json.JSONArray())
            put("annotations", org.json.JSONArray())
            put("notes", JSONObject())
        }

        val parsed = DocState.fromJson("doc", legacy)

        assertEquals(DisplayPrefs(), parsed.displayPrefs)
        assertFalse(parsed.toJson().has("display"))
    }

    @Test
    fun legacyPdfJsonWithoutMemoCards_loadsEmptyAndDoesNotWriteTheField() {
        val legacy = JSONObject().apply {
            put("version", 1)
            put("name", "lecture.pdf")
            put("pages", org.json.JSONArray())
            put("annotations", org.json.JSONArray())
            put("notes", JSONObject())
        }

        val parsed = DocState.fromJson("doc", legacy)

        assertTrue(parsed.memoCards.isEmpty())
        assertFalse(parsed.toJson().has("memoCards"))
    }

    @Test
    fun jsonRoundTrip_keepsEveryMemoCardFieldAndItsPage() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(1)),
            memoCards = listOf(memo(0, "a", "첫 메모"), memo(1, "b", "두 번째")),
        )

        val restored = DocState.fromJson("doc", JSONObject(original.toJson().toString()))

        assertEquals(2, restored.memoCards.size)
        assertEquals("a", restored.memoCards[0].id)
        assertEquals(0, restored.memoCards[0].page)
        assertEquals("첫 메모", restored.memoCards[0].text)
        assertEquals(MemoTint.MINT, restored.memoCards[0].tint)
        assertEquals(0.25f, restored.memoCards[0].x, 0.0001f)
        assertEquals(0.4f, restored.memoCards[0].y, 0.0001f)
        assertEquals(0.4f, restored.memoCards[0].w, 0.0001f)
        assertEquals(0.2f, restored.memoCards[0].h, 0.0001f)
        assertEquals(1, restored.memoCards[1].page)
        assertEquals("두 번째", restored.memoCards[1].text)
    }

    @Test
    fun normalized_remapsMemoCardsAndDropsCardsOnMissingPages() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(1), PageSpec(5)),
            memoCards = listOf(memo(0, "a"), memo(2, "gone"), memo(5, "also-gone")),
        )

        val result = original.normalized(2)

        assertEquals(1, result.memoCards.size)
        assertEquals("a", result.memoCards[0].id)
        assertEquals(0, result.memoCards[0].page)
    }

    @Test
    fun normalized_resetsMemoCardsWhenNoPageSurvives() {
        val original = state(
            pages = listOf(PageSpec(7)),
            memoCards = listOf(memo(0, "a")),
        )

        assertTrue(original.normalized(2).memoCards.isEmpty())
    }

    @Test
    fun normalized_preservesDisplayPrefs() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(5)),
            displayPrefs = DisplayPrefs(nightMode = true, brightness = 0.4f),
        )

        assertEquals(original.displayPrefs, original.normalized(3).displayPrefs)
    }
}
