package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
