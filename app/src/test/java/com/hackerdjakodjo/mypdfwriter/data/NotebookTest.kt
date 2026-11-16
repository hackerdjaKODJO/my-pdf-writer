package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebookTest {

    @Test
    fun notebookJsonRoundTripKeepsPagesAnnotationsAndTags() {
        val annotation = Annotation(
            type = AnnotationType.PEN,
            page = 0,
            color = 0xFF1F4E79.toInt(),
            width = 0.004f,
            points = listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.4f, 0.5f)),
        )
        val layer = NoteLayer(name = "필기", opacity = 0.8f)
        val notebook = Notebook(
            title = "생물학 노트",
            folder = "Study",
            tags = listOf("DNA", "시험", "DNA"),
            pages = listOf(
                NotePage(
                    template = PaperTemplate.CORNELL,
                    annotations = listOf(annotation.copy(layerId = layer.id, penStyle = PenStyle.FOUNTAIN)),
                    memo = "복습",
                    layers = listOf(layer),
                ),
                NotePage(template = PaperTemplate.GRID),
            ),
            bookmarks = listOf(PageBookmark(pageIndex = 0, title = "DNA")),
            favorite = true,
        )
        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))
        assertEquals(notebook.id, restored.id)
        assertEquals("생물학 노트", restored.title)
        assertEquals(listOf("DNA", "시험"), restored.tags)
        assertEquals(2, restored.pages.size)
        assertEquals(PaperTemplate.CORNELL, restored.pages[0].template)
        assertEquals(1, restored.pages[0].annotations.size)
        assertEquals("복습", restored.pages[0].memo)
        assertEquals(PenStyle.FOUNTAIN, restored.pages[0].annotations[0].penStyle)
        assertEquals(layer.id, restored.pages[0].annotations[0].layerId)
        assertEquals(1, restored.pages[0].layers.size)
        assertEquals("DNA", restored.bookmarks.single().title)
        assertTrue(restored.favorite)
    }

    @Test
    fun legacyOrEmptyNotebookGetsOnePage() {
        val restored = Notebook.fromJson(JSONObject("""{"id":"x","title":"","pages":[]}"""))
        assertEquals("새 노트", restored.title)
        assertEquals(1, restored.pages.size)
        assertEquals(PaperTemplate.LINED, restored.pages[0].template)
    }

    @Test
    fun sourceLinksRoundTripWithoutChangingPageContent() {
        val notebook = Notebook(
            title = "세포 생물학",
            pages = listOf(
                NotePage(memo = "원본 PDF p.3", sourcePageIndex = 2),
                NotePage(template = PaperTemplate.GRID),
            ),
            sourceDocumentName = "biology.pdf",
            sourceDocumentUri = "content://docs/biology",
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals("biology.pdf", restored.sourceDocumentName)
        assertEquals("content://docs/biology", restored.sourceDocumentUri)
        assertEquals(2, restored.pages[0].sourcePageIndex)
        assertEquals("원본 PDF p.3", restored.pages[0].memo)
        assertEquals(null, restored.pages[1].sourcePageIndex)
    }

    @Test
    fun legacyNotebookGetsBlankSourceMetadataAndNegativePagesNormalize() {
        val legacy = Notebook.fromJson(JSONObject("""{"id":"x","pages":[{"id":"p","memo":"옛 노트"}]}"""))
        assertEquals("", legacy.sourceDocumentName)
        assertEquals("", legacy.sourceDocumentUri)
        assertEquals(null, legacy.pages[0].sourcePageIndex)

        val invalid = Notebook.fromJson(
            JSONObject("""{"id":"y","pages":[{"id":"p","sourcePage":-4}]}"""),
        )
        assertEquals(null, invalid.pages[0].sourcePageIndex)
    }

    @Test
    fun legacyPagesDefaultToPortraitAndWarmPaper() {
        val notebook = Notebook.fromJson(
            JSONObject("""{"id":"legacy","pages":[{"id":"p","template":"GRID"}]}"""),
        )
        val page = notebook.pages.single()

        assertEquals(PageOrientation.PORTRAIT, page.orientation)
        assertEquals(PaperTint.WARM, page.tint)
        assertEquals(PaperTemplate.GRID, page.template)
    }

    @Test
    fun orientationAndTintRoundTripThroughPageJson() {
        val notebook = Notebook(
            title = "가로 노트",
            pages = listOf(
                NotePage(
                    template = PaperTemplate.MUSIC,
                    orientation = PageOrientation.LANDSCAPE,
                    tint = PaperTint.PALE_YELLOW,
                ),
                NotePage(tint = PaperTint.LIGHT_GRAY),
            ),
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals(PageOrientation.LANDSCAPE, restored.pages[0].orientation)
        assertEquals(PaperTint.PALE_YELLOW, restored.pages[0].tint)
        assertEquals(PageOrientation.PORTRAIT, restored.pages[1].orientation)
        assertEquals(PaperTint.LIGHT_GRAY, restored.pages[1].tint)
        assertEquals(notebook.pages[0].id, restored.pages[0].id)
    }

    @Test
    fun unknownPaperFieldsFallBackToLegacyDefaults() {
        val notebook = Notebook.fromJson(
            JSONObject(
                """{"id":"x","pages":[{"id":"p","orientation":"SIDEWAYS","tint":"NEON"}]}""",
            ),
        )
        val page = notebook.pages.single()

        assertEquals(PageOrientation.PORTRAIT, page.orientation)
        assertEquals(PaperTint.WARM, page.tint)
    }

    @Test
    fun legacyPagesHaveNoCustomBackgroundAndWriteNoAssetFields() {
        val notebook = Notebook.fromJson(
            JSONObject("""{"id":"legacy","pages":[{"id":"p","template":"GRID"}]}"""),
        )
        val page = notebook.pages.single()

        assertNull(page.customBackground)
        assertEquals("", page.customBackgroundName)
        val json = page.toJson()
        assertFalse(json.has("customBackground"))
        assertFalse(json.has("customBackgroundName"))
    }

    @Test
    fun customBackgroundRoundTripsAsPortableRelativeKey() {
        val notebook = Notebook(
            title = "커스텀 종이",
            pages = listOf(
                NotePage(
                    template = PaperTemplate.GRID,
                    tint = PaperTint.WHITE,
                    customBackground = "tpl-8e5a.png",
                    customBackgroundName = "  강의   배경 ",
                ),
                NotePage(template = PaperTemplate.LINED),
            ),
        )

        val restored = Notebook.fromJson(JSONObject(notebook.toJson().toString()))

        assertEquals("tpl-8e5a.png", restored.pages[0].customBackground)
        assertEquals("강의 배경", restored.pages[0].customBackgroundName)
        assertEquals(PaperTemplate.GRID, restored.pages[0].template)
        assertEquals(PaperTint.WHITE, restored.pages[0].tint)
        assertEquals(null, restored.pages[1].customBackground)
        assertEquals("", restored.pages[1].customBackgroundName)
        assertFalse(notebook.toJson().toString().contains("/data/data/"))
    }

    @Test
    fun unsafeCustomBackgroundValuesAreDroppedOnLoad() {
        val notebook = Notebook.fromJson(
            JSONObject(
                """{"id":"x","pages":[
                    {"id":"a","customBackground":"../escape.png","customBackgroundName":"bad"},
                    {"id":"b","customBackground":"/sdcard/photo.png","customBackgroundName":"bad"},
                    {"id":"c","customBackground":"files/templates/a.png","customBackgroundName":"bad"},
                    {"id":"d","customBackground":"tpl-ok.png","customBackgroundName":""}
                ]}""",
            ),
        )

        notebook.pages.take(3).forEach { page ->
            assertNull(page.customBackground)
            assertEquals("", page.customBackgroundName)
        }
        assertEquals("tpl-ok.png", notebook.pages[3].customBackground)
        assertEquals(CustomTemplatePaths.DEFAULT_NAME, notebook.pages[3].customBackgroundName)
    }
}
