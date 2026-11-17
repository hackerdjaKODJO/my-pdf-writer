package com.hackerdjakodjo.mypdfwriter.ui

/** Scope chips shown inside the Home filter sheet. */
internal enum class NoteScope(val label: String) {
    ALL("전체"),
    FAVORITES("즐겨찾기"),
    TRASH("휴지통"),
}

/**
 * The complete Home library filter state. Search lives here too because the
 * compact active-filter row shows it alongside scope/folder/tag chips even
 * though the text field itself stays in the top bar.
 */
internal data class HomeFilterState(
    val scope: NoteScope = NoteScope.ALL,
    val folder: String? = null,
    val tag: String? = null,
    val query: String = "",
) {
    /** Number of filters that actually narrow the list. */
    val activeCount: Int
        get() = (if (scope != NoteScope.ALL) 1 else 0) +
            (if (folder != null) 1 else 0) +
            (if (tag != null) 1 else 0) +
            (if (query.isNotBlank()) 1 else 0)

    val hasActiveFilters: Boolean get() = activeCount > 0
}

/** One removable chip in the active-filter row. */
internal enum class HomeFilterKind { SCOPE, FOLDER, TAG, QUERY }

internal data class HomeFilterChip(
    val kind: HomeFilterKind,
    val label: String,
)

/**
 * Chips shown while filters are active, in a stable scope → folder → tag →
 * search order. A null folder means "all folders"; the empty string is the
 * explicit "uncategorized" folder.
 */
internal fun homeFilterChips(state: HomeFilterState): List<HomeFilterChip> = buildList {
    if (state.scope != NoteScope.ALL) {
        add(HomeFilterChip(HomeFilterKind.SCOPE, state.scope.label))
    }
    state.folder?.let { folder ->
        add(
            HomeFilterChip(
                kind = HomeFilterKind.FOLDER,
                label = "폴더 · ${folder.ifBlank { "미분류" }}",
            ),
        )
    }
    state.tag?.let { tag ->
        add(HomeFilterChip(HomeFilterKind.TAG, "태그 · $tag"))
    }
    val needle = state.query.trim()
    if (needle.isNotEmpty()) {
        add(HomeFilterChip(HomeFilterKind.QUERY, "검색 · “$needle”"))
    }
}

/** Removes exactly one filter and leaves every other choice untouched. */
internal fun HomeFilterState.without(kind: HomeFilterKind): HomeFilterState = when (kind) {
    HomeFilterKind.SCOPE -> copy(scope = NoteScope.ALL)
    HomeFilterKind.FOLDER -> copy(folder = null)
    HomeFilterKind.TAG -> copy(tag = null)
    HomeFilterKind.QUERY -> copy(query = "")
}

/** Resets every filter, including the note scope. */
internal fun HomeFilterState.cleared(): HomeFilterState = HomeFilterState()

/**
 * Home shows continuation cards and quick actions only on the unfiltered note
 * list. Keeping this decision pure prevents the empty-state and filter-row
 * logic from drifting apart.
 */
internal fun HomeFilterState.showsHomeStart(): Boolean =
    scope == NoteScope.ALL &&
        query.isBlank() &&
        folder == null &&
        tag == null
