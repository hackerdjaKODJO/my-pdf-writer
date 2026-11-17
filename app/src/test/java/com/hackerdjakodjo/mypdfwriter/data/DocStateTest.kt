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

    private fun state(
        pages: List<PageSpec>,
        annotations: List<Annotation> = emptyList(),
        notes: Map<Int, String> = emptyMap(),
        displayPrefs: DisplayPrefs = DisplayPrefs(),
    ) = DocState(
        docId = "doc",
        displayName = "lecture.pdf",
        pages = pages,
        annotations = annotations,
        notes = notes,
        displayPrefs = displayPrefs,
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
    fun normalized_preservesDisplayPrefs() {
        val original = state(
            pages = listOf(PageSpec(0), PageSpec(5)),
            displayPrefs = DisplayPrefs(nightMode = true, brightness = 0.4f),
        )

        assertEquals(original.displayPrefs, original.normalized(3).displayPrefs)
    }
}
