package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure session-tab policy. No Android/Compose runtime is needed: every decision
 * the viewer makes about adding, switching, closing and hiding tabs is a value
 * transform so regressions are caught without a device.
 */
class DocumentTabsTest {

    private fun tab(id: String, name: String = "$id.pdf") = OpenDocumentTab(
        docId = id,
        uri = "content://docs/$id",
        name = name,
    )

    @Test
    fun seedStartsWithOneActiveTabAndNoStrip() {
        val state = seedDocumentTabs(tab("a"))

        assertEquals(listOf("a"), state.tabs.map { it.docId })
        assertEquals("a", state.activeDocId)
        assertEquals("a.pdf", state.activeTab?.name)
        assertFalse(shouldShowDocumentTabStrip(state))
        assertEquals(0, documentTabOverflowCount(state))
    }

    @Test
    fun upsertAddsDocumentsAndMovesTheOpenedOneToTheFront() {
        var state = seedDocumentTabs(tab("a"))
        state = upsertDocumentTab(state, tab("b"))

        assertEquals(listOf("b", "a"), state.tabs.map { it.docId })
        assertEquals("b", state.activeDocId)

        state = upsertDocumentTab(state, tab("a", "renamed.pdf"))

        assertEquals(listOf("a", "b"), state.tabs.map { it.docId })
        assertEquals("renamed.pdf", state.activeTab?.name)
        assertEquals("content://docs/a", state.activeTab?.uri)
        assertEquals(2, state.tabs.size) // a refresh must not duplicate the entry
    }

    @Test
    fun activatingAnUnknownTabIsANoOp() {
        val state = seedDocumentTabs(tab("a"))

        assertNull(activateDocumentTab(state, "missing"))
        assertEquals("a", state.activeDocId)
    }

    @Test
    fun activatingAnOpenTabMakesItActiveAndMostRecent() {
        val state = upsertDocumentTab(upsertDocumentTab(seedDocumentTabs(tab("a")), tab("b")), tab("c"))

        val active = activateDocumentTab(state, "a")!!

        assertEquals("a", active.activeDocId)
        assertEquals(listOf("a", "c", "b"), active.tabs.map { it.docId })
        assertEquals(state.tabs.toSet(), active.tabs.toSet())
    }

    @Test
    fun closingAnInactiveTabKeepsTheActiveDocument() {
        val state = upsertDocumentTab(upsertDocumentTab(seedDocumentTabs(tab("a")), tab("b")), tab("c"))

        val after = closeDocumentTab(state, "b")

        assertEquals(listOf("c", "a"), after.tabs.map { it.docId })
        assertEquals("c", after.activeDocId)
    }

    @Test
    fun closingTheActiveTabPromotesTheRightNeighbourOrThePreviousTab() {
        val state = upsertDocumentTab(upsertDocumentTab(seedDocumentTabs(tab("a")), tab("b")), tab("c"))
        // MRU order is c, b, a; active is c (first), so its neighbour is b.
        assertEquals("b", closeDocumentTab(state, "c").activeDocId)

        val lastActive = activateDocumentTab(state, "a")!!
        assertEquals("a", lastActive.activeDocId)
        assertEquals("c", closeDocumentTab(lastActive, "a").activeDocId)
    }

    @Test
    fun closingTheLastTabReturnsAnEmptySession() {
        val state = seedDocumentTabs(tab("a"))

        val after = closeDocumentTab(state, "a")

        assertTrue(after.tabs.isEmpty())
        assertEquals("", after.activeDocId)
        assertFalse(shouldShowDocumentTabStrip(after))
    }

    @Test
    fun closingUnknownTabDoesNotMutateTheSession() {
        val state = seedDocumentTabs(tab("a"))

        val after = closeDocumentTab(state, "missing")

        assertEquals(state, after)
    }

    @Test
    fun closeOthersLeavesTheActiveTabOnly() {
        val state = upsertDocumentTab(upsertDocumentTab(seedDocumentTabs(tab("a")), tab("b")), tab("c"))
        val active = activateDocumentTab(state, "b")!!

        val after = closeOtherDocumentTabs(active)

        assertEquals(listOf("b"), after.tabs.map { it.docId })
        assertEquals("b", after.activeDocId)
    }

    @Test
    fun closeAllClearsTabsButKeepsTheUserHideChoice() {
        val shown = upsertDocumentTab(seedDocumentTabs(tab("a")), tab("b"))
        val hidden = setDocumentTabBarVisible(shown, false)

        val after = closeAllDocumentTabs(hidden)

        assertTrue(after.tabs.isEmpty())
        assertEquals("", after.activeDocId)
        assertFalse(after.barVisible)
    }

    @Test
    fun stripAppearsAtTwoTabsAndHonoursTheHideChoice() {
        val one = seedDocumentTabs(tab("a"))
        val two = upsertDocumentTab(one, tab("b"))

        assertTrue(shouldShowDocumentTabStrip(two))
        val hidden = setDocumentTabBarVisible(two, false)
        assertFalse(shouldShowDocumentTabStrip(hidden))
        assertTrue(shouldShowDocumentTabStrip(setDocumentTabBarVisible(hidden, true)))
    }

    @Test
    fun atMostThreeTabsAreVisibleAndTheRestBecomeOverflow() {
        val state = (1..5).fold(DocumentTabsState()) { acc, i ->
            upsertDocumentTab(acc, tab("doc$i"))
        }

        assertEquals(5, state.tabs.size)
        assertEquals(listOf("doc5", "doc4", "doc3"), visibleDocumentTabs(state).map { it.docId })
        assertEquals(listOf("doc2", "doc1"), hiddenDocumentTabs(state).map { it.docId })
        assertEquals(2, documentTabOverflowCount(state))
        assertEquals(3, MAX_VISIBLE_TABS)
        assertEquals(2, MIN_TABS_FOR_STRIP)
    }

    @Test
    fun hiddenTabsAreStillReachableThroughOverflowPanelActions() {
        val state = (1..5).fold(DocumentTabsState()) { acc, i ->
            upsertDocumentTab(acc, tab("doc$i"))
        }

        // The overflow count only limits the strip; the panel lists every tab.
        hiddenDocumentTabs(state).forEach { hidden ->
            assertNotEquals("doc3", hidden.docId)
            assertEquals(hidden.docId, activateDocumentTab(state, hidden.docId)!!.activeDocId)
        }
    }

    @Test
    fun recentSwitcherSortsBoundsAndSkipsUnopenableRows() {
        val recents = listOf(
            RecentDoc("content://docs/old", "old.pdf", 4, 100L),
            RecentDoc("", "blank.pdf", 1, 999L),
            RecentDoc("content://docs/new", "new.pdf", 12, 300L),
            RecentDoc("content://docs/mid", "mid.pdf", 8, 200L),
        )

        assertEquals(
            listOf("new.pdf", "mid.pdf", "old.pdf"),
            recentDocumentsForSwitcher(recents).map { it.name },
        )
        assertEquals(
            listOf("new.pdf", "mid.pdf"),
            recentDocumentsForSwitcher(recents, limit = 2).map { it.name },
        )
        assertTrue(recentDocumentsForSwitcher(recents, limit = 0).isEmpty())
        assertTrue(recentDocumentsForSwitcher(recents, limit = -3).isEmpty())
    }
}
