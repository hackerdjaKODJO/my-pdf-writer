package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.data.NotePage

/** Page range chosen in the note export sheet. */
enum class NoteExportPageRange {
    ALL,
    CURRENT,
    SELECTED,
}

/**
 * Export choices for a local notebook.
 *
 * [selectedPageIds] are stable [NotePage.id] values from the page manager.
 * [currentPageIndex] is resolved by the editor when the sheet is confirmed, so
 * the exporter does not need any UI state. Hidden layers are excluded by
 * default to keep the shipped WYSIWYG behavior; when they are included their
 * normal layer opacity is still applied.
 *
 * [suggestedFileName] keeps the SAF launcher's existing title-based filename.
 */
data class NoteExportOptions(
    val pageRange: NoteExportPageRange = NoteExportPageRange.ALL,
    val currentPageIndex: Int = 0,
    val selectedPageIds: Set<String> = emptySet(),
    val includeHiddenLayers: Boolean = false,
    val suggestedFileName: String = "",
) {
    /**
     * Zero-based page indices for this option, in notebook order.
     *
     * A `SELECTED` range with no surviving ids returns an empty list. The
     * exporter treats that as "all pages" so it can never write a zero-page PDF;
     * the export sheet disables the selected chip in that case.
     */
    fun pageIndices(pages: List<NotePage>): List<Int> = when (pageRange) {
        NoteExportPageRange.ALL -> pages.indices.toList()
        NoteExportPageRange.CURRENT -> {
            if (pages.isEmpty()) emptyList()
            else listOf(currentPageIndex.coerceIn(0, pages.lastIndex))
        }
        NoteExportPageRange.SELECTED -> pages.mapIndexedNotNull { index, page ->
            index.takeIf { page.id in selectedPageIds }
        }
    }
}

/** Existing notebook export filename rule, shared by the full editor and study pane. */
fun noteExportFileName(title: String?): String {
    val safeTitle = title?.ifBlank { "노트" } ?: "노트"
    return safeTitle.replace(Regex("[\\/:*?\"<>|]"), "_").take(60) + ".pdf"
}
