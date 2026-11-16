package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioNoteTest {

    @Test
    fun audioNoteJsonRoundTripKeepsOwnerPageAndMarkers() {
        val note = AudioNote(
            id = "audio-1",
            ownerId = "notebook-7",
            pageIndex = 3,
            filePath = "/data/user/0/app/files/audio/notebook-7/audio-1.m4a",
            durationMs = 95_000L,
            createdAt = 1_760_000_000_000L,
            markers = listOf(
                AudioMarker(id = "m2", pageIndex = 5, offsetMs = 42_500L, title = "둘째"),
                AudioMarker(id = "m1", pageIndex = 3, offsetMs = 1_200L, title = "첫째"),
            ),
        )

        val restored = AudioNote.fromJson(JSONObject(note.toJson().toString()))

        assertEquals(note.id, restored.id)
        assertEquals(note.ownerId, restored.ownerId)
        assertEquals(3, restored.pageIndex)
        assertEquals(note.filePath, restored.filePath)
        assertEquals(95_000L, restored.durationMs)
        assertEquals(note.createdAt, restored.createdAt)
        assertEquals(listOf("m1", "m2"), restored.markers.map { it.id })
        assertEquals("첫째", restored.markers.first().title)
    }

    @Test
    fun missingOptionalFieldsGetSafeDefaults() {
        val restored = AudioNote.fromJson(
            JSONObject("""{"ownerId":"doc-1","page":2,"filePath":"/tmp/a.m4a"}"""),
        )

        assertTrue(restored.id.isNotBlank())
        assertEquals("doc-1", restored.ownerId)
        assertEquals(2, restored.pageIndex)
        assertEquals(0L, restored.durationMs)
        assertTrue(restored.createdAt > 0L)
        assertTrue(restored.markers.isEmpty())
    }

    @Test
    fun malformedMarkersAreSkippedAndNegativesAreClamped() {
        val restored = AudioNote.fromJson(
            JSONObject(
                """
                {
                  "ownerId":"doc-1",
                  "page":-4,
                  "filePath":"/tmp/a.m4a",
                  "durationMs":-10,
                  "markers":[
                    {"id":"ok","page":-1,"offsetMs":-50,"title":"  제목  "},
                    "not-an-object"
                  ]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(0, restored.pageIndex)
        assertEquals(0L, restored.durationMs)
        assertEquals(1, restored.markers.size)
        assertEquals(0, restored.markers.single().pageIndex)
        assertEquals(0L, restored.markers.single().offsetMs)
        assertEquals("제목", restored.markers.single().title)
    }

    @Test
    fun listJsonRoundTripPreservesContent() {
        val notes = listOf(
            AudioNote(
                id = "a",
                ownerId = "doc-1",
                pageIndex = 0,
                filePath = "/tmp/a.m4a",
                durationMs = 1_000L,
                createdAt = 10L,
            ),
            AudioNote(
                id = "b",
                ownerId = "doc-1",
                pageIndex = 2,
                filePath = "/tmp/b.m4a",
                durationMs = 2_000L,
                createdAt = 20L,
            ),
        )

        val restored = AudioNote.listFromJson(AudioNote.listToJson(notes))

        assertEquals(notes.map { it.id }, restored.map { it.id })
        assertEquals(notes, restored)
    }

    @Test
    fun ownerAndFileNamesCannotEscapeTheAudioDirectory() {
        assertEquals("notebook-7", audioOwnerDirectoryName("notebook-7"))
        assertEquals(".._.._etc_passwd", audioOwnerDirectoryName("../../etc/passwd"))
        assertEquals("_..", audioOwnerDirectoryName(".."))
        assertEquals("default", audioOwnerDirectoryName("   "))
        assertFalse(audioOwnerDirectoryName("../../etc/passwd").contains('/'))

        assertEquals("note-1.m4a", audioFileName("note-1"))
        assertEquals(".._.._secret.m4a", audioFileName("../../secret"))
        assertFalse(audioFileName("../../secret").contains('/'))
    }
}
