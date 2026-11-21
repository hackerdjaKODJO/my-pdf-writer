package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure policy tests for the unified GoodReader-style location panel. */
class LocationsSheetTest {

    @Test
    fun tabsKeepTheBookmarksOutlineAnnotationsOrder() {
        assertEquals(
            listOf("북마크", "목차", "주석"),
            LOCATIONS_TAB_ORDER.map { it.label },
        )
        assertEquals(
            listOf(LocationsTab.BOOKMARKS, LocationsTab.OUTLINE, LocationsTab.ANNOTATIONS),
            LOCATIONS_TAB_ORDER,
        )
        // The tab order is the TabRow index order, so it must be unique.
        assertEquals(LOCATIONS_TAB_ORDER.size, LOCATIONS_TAB_ORDER.toSet().size)
    }

    @Test
    fun annotationsGroupByAscendingPageAndHonourTheFilter() {
        val input = listOf(
            annotation(AnnotationType.TEXT, page = 2, id = "t2"),
            annotation(AnnotationType.PEN, page = 0, id = "p0"),
            annotation(AnnotationType.REDACT, page = 2, id = "r2"),
            annotation(AnnotationType.HIGHLIGHT, page = 0, id = "h0"),
        )

        val all = locationsAnnotationGroups(input, AnnotationFilter.ALL, pageCount = 4)
        val redact = locationsAnnotationGroups(input, AnnotationFilter.REDACT, pageCount = 4)

        assertEquals(listOf(0, 2), all.keys.toList())
        // Within a page the filter orders by stored type ordinal: PEN before HIGHLIGHT.
        assertEquals(listOf("p0", "h0"), all[0]?.map { it.id })
        assertEquals(listOf("t2", "r2"), all[2]?.map { it.id })
        assertEquals(listOf(2), redact.keys.toList())
        assertEquals(listOf("r2"), redact[2]?.map { it.id })
        assertTrue(locationsAnnotationGroups(input, AnnotationFilter.SHAPES, pageCount = 4).isEmpty())
    }

    @Test
    fun annotationGroupsRespectPageCountAndAreRepeatable() {
        val input = listOf(
            annotation(AnnotationType.PEN, page = 0, id = "p0"),
            annotation(AnnotationType.PEN, page = 1, id = "p1"),
            annotation(AnnotationType.PEN, page = 2, id = "p2"),
        )

        val groups = locationsAnnotationGroups(input, AnnotationFilter.ALL, pageCount = 2)

        assertEquals(listOf(0, 1), groups.keys.toList())
        assertEquals(groups, locationsAnnotationGroups(input, AnnotationFilter.ALL, pageCount = 2))
    }

    @Test
    fun filterCountsCoverEveryChipAndStayConsistentWithTheVisibleRows() {
        val input = listOf(
            annotation(AnnotationType.PEN, page = 0, id = "pen"),
            annotation(AnnotationType.HIGHLIGHT, page = 0, id = "hl"),
            annotation(AnnotationType.TEXT, page = 1, id = "text"),
            annotation(AnnotationType.RECT, page = 1, id = "rect"),
            annotation(AnnotationType.REDACT, page = 2, id = "redact"),
        )

        val counts = PdfAnnotationFilter.counts(input, pageCount = 3)

        assertEquals(5, counts[AnnotationFilter.ALL])
        assertEquals(2, counts[AnnotationFilter.INK])
        assertEquals(1, counts[AnnotationFilter.TEXT])
        assertEquals(1, counts[AnnotationFilter.SHAPES])
        assertEquals(1, counts[AnnotationFilter.REDACT])
        assertEquals(
            "the mutually exclusive type chips partition the 전체 total",
            counts[AnnotationFilter.ALL],
            AnnotationFilter.entries
                .filter { it != AnnotationFilter.ALL }
                .mapNotNull { counts[it] }
                .sum(),
        )
        for (option in AnnotationFilter.entries) {
            assertEquals(
                "chip count must match the rows the filter renders",
                counts[option],
                PdfAnnotationFilter.filter(input, option, pageCount = 3).size,
            )
        }
    }

    @Test
    fun everyRowJumpInvokesTheJumpCallbackAndDismissesExactlyOnce() {
        var jumpedTo: Int? = null
        var jumps = 0
        var dismissals = 0
        var exported: String? = null
        val actions = LocationsActions(
            onJumpToPage = { page -> jumpedTo = page; jumps++ },
            onDismiss = { dismissals++ },
            onExportSummary = { exported = it },
        )

        // Bookmarks, outlines and annotations all route through the same jump.
        actions.jump(7)   // bookmark pageIndex
        actions.jump(11)  // outline pageIndex
        actions.jump(2)   // annotation page

        assertEquals(2, jumpedTo)
        assertEquals(3, jumps)
        assertEquals(3, dismissals)

        actions.export("# 주석 요약\n")
        assertEquals("# 주석 요약\n", exported)
        assertEquals(3, dismissals)

        actions.close()
        assertEquals(4, dismissals)
    }

    @Test
    fun outlineEntriesWithNoPageAreNotJumpable() {
        val withPage = PdfOutlineEntry("서론", pageIndex = 3, level = 0)
        val withoutPage = PdfOutlineEntry("표지", pageIndex = null, level = 0)

        assertEquals(3, withPage.pageIndex)
        assertEquals(null, withoutPage.pageIndex)
    }

    private fun annotation(type: AnnotationType, page: Int, id: String): Annotation = Annotation(
        id = id,
        type = type,
        page = page,
        color = 0xFF1F4E79.toInt(),
        width = 0.0035f,
    )
}
