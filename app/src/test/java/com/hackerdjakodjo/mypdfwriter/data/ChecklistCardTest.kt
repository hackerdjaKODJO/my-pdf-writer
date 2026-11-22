package com.hackerdjakodjo.mypdfwriter.data

import com.hackerdjakodjo.mypdfwriter.ui.NotePageManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistCardTest {

    private fun item(id: String, text: String, done: Boolean = false) =
        ChecklistItem(id = id, text = text, done = done)

    private fun card(
        id: String = "list-1",
        items: List<ChecklistItem> = listOf(
            item("i1", "세포분열 복습", done = true),
            item("i2", "실험 문제 풀이"),
            item("i3", "오답 정리", done = true),
        ),
    ) = ChecklistCard(
        id = id,
        title = "생물학 할 일",
        items = items,
        x = 0.2f,
        y = 0.35f,
        w = 0.5f,
        h = 0.28f,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun legacyPageWithoutChecklistsLoadsEmptyListAndWritesNoKey() {
        val page = NotePage.fromJson(JSONObject("""{"id":"p","template":"GRID"}"""))
        assertTrue(page.checklists.isEmpty())
        assertFalse(page.toJson().has("checklists"))

        val notebook = Notebook.fromJson(JSONObject("""{"id":"nb","pages":[{"id":"p"}]}"""))
        assertTrue(notebook.pages.single().checklists.isEmpty())
    }

    @Test
    fun checklistsRoundTripThroughNotebookJson() {
        val notebook = Notebook(
            id = "nb",
            pages = listOf(
                NotePage(id = "p0", checklists = listOf(card("c1"), card("c2", items = emptyList()))),
                NotePage(id = "p1"),
            ),
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals(2, restored.pages[0].checklists.size)
        assertTrue(restored.pages[1].checklists.isEmpty())
        val first = restored.pages[0].checklists[0]
        assertEquals("c1", first.id)
        assertEquals("생물학 할 일", first.title)
        assertEquals(3, first.items.size)
        assertEquals("i1", first.items[0].id)
        assertEquals("세포분열 복습", first.items[0].text)
        assertTrue(first.items[0].done)
        assertFalse(first.items[1].done)
        assertEquals(0.2f, first.x)
        assertEquals(0.35f, first.y)
        assertEquals(0.5f, first.w)
        assertEquals(0.28f, first.h)
        assertEquals(1_700_000_000_000L, first.createdAt)
        assertEquals("c2", restored.pages[0].checklists[1].id)
        assertTrue(restored.pages[0].checklists[1].items.isEmpty())

        val json = notebook.toJson().toString()
        assertFalse(json.contains("/data/data/"))
        assertFalse(json.contains("content://"))
    }

    @Test
    fun malformedAndOutOfRangeChecklistJsonGetsRepaired() {
        val parsed = ChecklistCard.fromJson(
            JSONObject(
                """{"id":"  ","title":"  제목  ","createdAt":0,""" +
                    """"x":-4,"y":9,"w":80,"h":0,"items":[
                        {"id":"dup","text":"  첫 항목  ","done":true},
                        {"id":"dup","text":"둘","done":false},
                        {"id":"  ","text":"셋"},
                        "not-an-object"
                    ]}""",
            ),
        )

        assertTrue(parsed.id.isNotBlank())
        assertEquals("제목", parsed.title)
        assertEquals(0f, parsed.x)
        assertEquals(1f - ChecklistCard.MIN_H, parsed.y, 0.0001f)
        assertEquals(1f, parsed.w)
        assertEquals(ChecklistCard.MIN_H, parsed.h)
        assertTrue("whole rect on page", parsed.x + parsed.w <= 1f && parsed.y + parsed.h <= 1f)
        assertTrue(parsed.createdAt > 0L)
        assertEquals(3, parsed.items.size)
        assertEquals("첫 항목", parsed.items[0].text)
        assertTrue(parsed.items[0].done)
        assertEquals("dup", parsed.items[0].id)
        assertNotEquals("dup", parsed.items[1].id)
        assertTrue(parsed.items[2].id.isNotBlank())
        assertNotEquals(parsed.items[1].id, parsed.items[2].id)

        val defaults = ChecklistCard.fromJson(JSONObject("{}"))
        assertTrue(defaults.id.isNotBlank())
        assertEquals(Checklists.DEFAULT_TITLE, defaults.title)
        assertEquals(ChecklistCard.DEFAULT_X, defaults.x)
        assertEquals(ChecklistCard.DEFAULT_W, defaults.w)
        assertTrue(defaults.items.isEmpty())
    }

    @Test
    fun nonFiniteGeometryAndOversizedListsAreClamped() {
        val repaired = ChecklistCard(
            x = Float.NaN,
            y = Float.POSITIVE_INFINITY,
            w = -1f,
            h = Float.NEGATIVE_INFINITY,
            items = (1..(Checklists.MAX_ITEMS + 5)).map { item("i$it", "항목") },
        ).normalized()

        // Non-finite values fall back to the documented defaults; finite but
        // out-of-range widths clamp to the tappable minimum.
        assertEquals(ChecklistCard.DEFAULT_X, repaired.x)
        assertEquals(ChecklistCard.DEFAULT_Y, repaired.y)
        assertEquals(ChecklistCard.MIN_W, repaired.w)
        assertEquals(ChecklistCard.DEFAULT_H, repaired.h)
        assertEquals(Checklists.MAX_ITEMS, repaired.items.size)
    }

    @Test
    fun togglingOneItemIsImmutableAndUnknownIdsAreNoOps() {
        val original = card()
        val toggled = Checklists.toggle(original, "i2")

        assertTrue(toggled.items.first { it.id == "i2" }.done)
        assertFalse(original.items.first { it.id == "i2" }.done)
        assertEquals(original.title, toggled.title)
        assertEquals(original.createdAt, toggled.createdAt)
        assertEquals(original.x, toggled.x)
        assertSame(original, Checklists.toggle(original, "missing"))
        assertEquals(original, Checklists.toggle(toggled, "i2"))
    }

    @Test
    fun addRenameAndRemoveItemsKeepStableIds() {
        val original = card(items = listOf(item("keep", "원래 항목")))
        val added = Checklists.addItem(original, "  새 항목  ")
        assertEquals(2, added.items.size)
        assertEquals("keep", added.items[0].id)
        assertEquals("새 항목", added.items[1].text)
        assertTrue(added.items[1].id.isNotBlank())
        assertNotEquals("keep", added.items[1].id)

        val renamed = Checklists.renameItem(added, added.items[1].id, "  수정  ")
        assertEquals("수정", renamed.items[1].text)
        assertEquals(added.items[1].id, renamed.items[1].id)

        val removed = Checklists.removeItem(renamed, added.items[1].id)
        assertEquals(listOf("keep"), removed.items.map { it.id })
        assertEquals(original, Checklists.removeItem(original, "missing"))
    }

    @Test
    fun progressCountsCheckedItemsAndAggregatesAcrossCards() {
        val single = Checklists.progress(card())
        assertEquals(2, single.checked)
        assertEquals(3, single.total)
        assertEquals(2f / 3f, single.fraction, 0.0001f)
        assertEquals(67, single.percent)
        assertEquals("완료 2/3 · 67%", Checklists.progressText(single))

        val aggregate = Checklists.progress(
            listOf(card(), card("c2", items = listOf(item("x", "항목", done = true)))),
        )
        assertEquals(3, aggregate.checked)
        assertEquals(4, aggregate.total)
        assertEquals(0.75f, aggregate.fraction, 0.0001f)

        val empty = Checklists.progress(emptyList())
        assertEquals(0, empty.total)
        assertEquals(0f, empty.fraction)
        assertEquals(0, empty.percent)
        assertEquals("항목 없음 · 0%", Checklists.progressText(empty))
    }

    @Test
    fun freshIdsGiveDuplicatedPagesIndependentChecklists() {
        val originals = listOf(card("c1"), card("c2", items = listOf(item("i1", "항목", done = true))))
        val copies = Checklists.freshIds(originals)

        assertEquals(2, copies.size)
        assertNotEquals("c1", copies[0].id)
        assertNotEquals("c2", copies[1].id)
        assertNotEquals(copies[0].id, copies[1].id)
        assertNotEquals(originals[0].items[0].id, copies[0].items[0].id)
        assertNotEquals(copies[0].items[0].id, copies[1].items[0].id)
        // Study content and rect travel unchanged; only identity is replaced.
        assertEquals(originals[0].title, copies[0].title)
        assertEquals(originals[0].x, copies[0].x)
        assertEquals(originals[1].items[0].done, copies[1].items[0].done)
    }

    @Test
    fun pageManagerDuplicationAlsoRefreshesChecklistIds() {
        val source = Notebook(
            id = "nb",
            pages = listOf(NotePage(id = "p0", checklists = listOf(card("c1")))),
        )

        val result = NotePageManager.duplicatePages(
            notebook = source,
            selectedPageIds = setOf("p0"),
            currentIndex = 0,
        )

        assertEquals(2, result.notebook.pages.size)
        val original = result.notebook.pages[0].checklists.single()
        val copy = result.notebook.pages[1].checklists.single()
        assertEquals("c1", original.id)
        assertNotEquals(original.id, copy.id)
        assertNotEquals(original.items[0].id, copy.items[0].id)
        assertEquals(original.title, copy.title)
        assertEquals(original.items[0].done, copy.items[0].done)
    }
}
