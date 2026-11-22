package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.RecentDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibraryPolicyTest {

    private val tags = listOf(
        "영어" to 4,
        "영어 독해" to 2,
        "수학" to 3,
    )

    private fun note(id: String, title: String, updatedAt: Long, pages: Int = 1) = Notebook(
        id = id,
        title = title,
        pages = List(pages) { NotePage() },
        updatedAt = updatedAt,
    )

    private fun pdf(uri: String, name: String, openedAt: Long, pages: Int = 1) = RecentDoc(
        uri = uri,
        name = name,
        lastOpenedAt = openedAt,
        pageCount = pages,
    )

    @Test
    fun blankQueryKeepsEveryTag() {
        assertSame(tags, filterTagCounts(tags, ""))
        assertSame(tags, filterTagCounts(tags, "   "))
    }

    @Test
    fun queryMatchesCaseInsensitiveSubstrings() {
        assertEquals(
            listOf("영어" to 4, "영어 독해" to 2),
            filterTagCounts(tags, "영어"),
        )
        assertEquals(emptyList<Pair<String, Int>>(), filterTagCounts(tags, "과학"))
    }

    @Test
    fun pdfEmptyStateOnlySaysFilteredWhenTheLibraryHasPdfs() {
        assertEquals(false, pdfEmptyStateIsFiltered(hasAnyPdf = false, query = "강의"))
        assertEquals(false, pdfEmptyStateIsFiltered(hasAnyPdf = true, query = "  "))
        assertEquals(true, pdfEmptyStateIsFiltered(hasAnyPdf = true, query = "강의"))
    }

    @Test
    fun queryIsTrimmed() {
        assertEquals(
            listOf("수학" to 3),
            filterTagCounts(tags, "  수학  "),
        )
    }

    @Test
    fun sortLabelsUseKorean() {
        assertEquals(
            listOf("최근 수정순", "이름순", "최근 열기순", "페이지 수순"),
            LibrarySort.entries.map { it.label },
        )
    }

    @Test
    fun notebooksSortByRecentlyModifiedAndRecentOpenUsesTheSameProxy() {
        val items = listOf(
            note("a", "A", 100L),
            note("b", "B", 300L),
            note("c", "C", 200L),
        )
        val expected = listOf(items[1], items[2], items[0])
        assertEquals(expected, sortNotebooks(items, LibrarySort.RECENTLY_MODIFIED))
        assertEquals(expected, sortNotebooks(items, LibrarySort.RECENTLY_OPENED))
    }

    @Test
    fun notebooksSortByNameCaseInsensitively() {
        val items = listOf(
            note("a", "beta", 100L),
            note("b", "Alpha", 200L),
            note("c", "alpha", 300L),
            note("d", "Zeta", 400L),
        )
        assertEquals(
            listOf("Alpha", "alpha", "beta", "Zeta"),
            sortNotebooks(items, LibrarySort.NAME).map { it.title },
        )
    }

    @Test
    fun notebooksSortByPageCountDescending() {
        val items = listOf(
            note("a", "A", 100L, pages = 3),
            note("b", "B", 300L, pages = 12),
            note("c", "C", 200L, pages = 7),
        )
        assertEquals(listOf("B", "C", "A"), sortNotebooks(items, LibrarySort.PAGE_COUNT).map { it.title })
    }

    @Test
    fun pdfsSortByRecentlyOpenedAndRecentModifiedUsesTheSameProxy() {
        val items = listOf(
            pdf("a", "A", 100L),
            pdf("b", "B", 300L),
            pdf("c", "C", 200L),
        )
        val expected = listOf(items[1], items[2], items[0])
        assertEquals(expected, sortPdfs(items, LibrarySort.RECENTLY_OPENED))
        assertEquals(expected, sortPdfs(items, LibrarySort.RECENTLY_MODIFIED))
    }

    @Test
    fun pdfsSortByNameCaseInsensitively() {
        val items = listOf(
            pdf("a", "beta", 100L),
            pdf("b", "Alpha", 200L),
            pdf("c", "alpha", 300L),
            pdf("d", "Zeta", 400L),
        )
        assertEquals(
            listOf("Alpha", "alpha", "beta", "Zeta"),
            sortPdfs(items, LibrarySort.NAME).map { it.name },
        )
    }

    @Test
    fun pdfsSortByPageCountDescending() {
        val items = listOf(
            pdf("a", "A", 100L, pages = 3),
            pdf("b", "B", 300L, pages = 12),
            pdf("c", "C", 200L, pages = 7),
        )
        assertEquals(listOf("B", "C", "A"), sortPdfs(items, LibrarySort.PAGE_COUNT).map { it.name })
    }

    @Test
    fun tiesKeepTheOriginalOrderForBothLibraries() {
        val notes = listOf(
            note("a", "Same", 100L, pages = 5),
            note("b", "same", 100L, pages = 5),
            note("c", "SAME", 100L, pages = 5),
        )
        assertEquals(notes, sortNotebooks(notes, LibrarySort.RECENTLY_MODIFIED))
        assertEquals(notes, sortNotebooks(notes, LibrarySort.NAME))
        assertEquals(notes, sortNotebooks(notes, LibrarySort.PAGE_COUNT))

        val pdfs = listOf(
            pdf("a", "Same", 100L, pages = 5),
            pdf("b", "same", 100L, pages = 5),
            pdf("c", "SAME", 100L, pages = 5),
        )
        assertEquals(pdfs, sortPdfs(pdfs, LibrarySort.RECENTLY_OPENED))
        assertEquals(pdfs, sortPdfs(pdfs, LibrarySort.NAME))
        assertEquals(pdfs, sortPdfs(pdfs, LibrarySort.PAGE_COUNT))
    }

    @Test
    fun sortingReturnsACopyAndLeavesTheSourceOrderAlone() {
        val notes = mutableListOf(note("a", "A", 100L), note("b", "B", 300L))
        val sortedNotes = sortNotebooks(notes, LibrarySort.RECENTLY_MODIFIED)
        assertNotSame(notes, sortedNotes)
        assertEquals(listOf("a", "b"), notes.map { it.id })

        val pdfs = mutableListOf(pdf("a", "A", 100L), pdf("b", "B", 300L))
        val sortedPdfs = sortPdfs(pdfs, LibrarySort.RECENTLY_OPENED)
        assertNotSame(pdfs, sortedPdfs)
        assertEquals(listOf("a", "b"), pdfs.map { it.uri })
    }

    @Test
    fun defaultFilterStateHasNoActiveFiltersOrChips() {
        val state = HomeFilterState()

        assertEquals(0, state.activeCount)
        assertFalse(state.hasActiveFilters)
        assertTrue(homeFilterChips(state).isEmpty())
        assertEquals(NoteScope.ALL, state.scope)
    }

    @Test
    fun blankSearchDoesNotCountAsAnActiveFilter() {
        assertEquals(0, HomeFilterState(query = "   ").activeCount)
        assertTrue(homeFilterChips(HomeFilterState(query = "\n")).isEmpty())
    }

    @Test
    fun activeFilterCountCoversScopeFolderTagAndSearch() {
        val state = HomeFilterState(
            scope = NoteScope.FAVORITES,
            folder = "강의",
            tag = "영어",
            query = "시험",
        )

        assertEquals(4, state.activeCount)
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun activeFilterChipsUseTheCompactHomeLabels() {
        val chips = homeFilterChips(
            HomeFilterState(
                scope = NoteScope.FAVORITES,
                folder = "강의",
                tag = "영어",
                query = "  기말  ",
            ),
        )

        assertEquals(
            listOf(
                HomeFilterKind.SCOPE to "즐겨찾기",
                HomeFilterKind.FOLDER to "폴더 · 강의",
                HomeFilterKind.TAG to "태그 · 영어",
                HomeFilterKind.QUERY to "검색 · “기말”",
            ),
            chips.map { it.kind to it.label },
        )
    }

    @Test
    fun uncategorizedFolderChipIsNamed() {
        val chips = homeFilterChips(HomeFilterState(folder = ""))

        assertEquals(listOf(HomeFilterChip(HomeFilterKind.FOLDER, "폴더 · 미분류")), chips)
    }

    @Test
    fun removingOneChipLeavesEveryOtherFilterAlone() {
        val state = HomeFilterState(
            scope = NoteScope.TRASH,
            folder = "강의",
            tag = "영어",
            query = "기말",
        )

        assertEquals(NoteScope.ALL, state.without(HomeFilterKind.SCOPE).scope)
        assertEquals(null, state.without(HomeFilterKind.FOLDER).folder)
        assertEquals(null, state.without(HomeFilterKind.TAG).tag)
        assertEquals("", state.without(HomeFilterKind.QUERY).query)

        val withoutTag = state.without(HomeFilterKind.TAG)
        assertEquals(NoteScope.TRASH, withoutTag.scope)
        assertEquals("강의", withoutTag.folder)
        assertEquals("기말", withoutTag.query)
    }

    @Test
    fun clearingRestoresEveryFilterDefault() {
        val state = HomeFilterState(
            scope = NoteScope.FAVORITES,
            folder = "강의",
            tag = "영어",
            query = "기말",
        )

        assertEquals(HomeFilterState(), state.cleared())
        assertEquals(0, state.cleared().activeCount)
    }

    @Test
    fun filterChipsSpeakTheirSelectedState() {
        assertEquals("즐겨찾기", homeFilterChipDescription("즐겨찾기", false))
        assertEquals("즐겨찾기, 선택됨", homeFilterChipDescription("즐겨찾기", true))
        assertEquals("영어 · 4, 선택됨", homeFilterChipDescription("영어 · 4", true))
    }

    @Test
    fun homeSectionsAreThreeCompactTopSegments() {
        assertEquals(listOf("노트", "PDF", "태그"), homeSectionLabels())
    }

    @Test
    fun `library controls are hidden until there is something to filter`() {
        assertFalse(homeShowsLibraryControls(0, false))
        assertTrue(homeShowsLibraryControls(0, true))
        assertTrue(homeShowsLibraryControls(1, false))
        assertTrue(homeShowsLibraryControls(3, true))
    }
}
