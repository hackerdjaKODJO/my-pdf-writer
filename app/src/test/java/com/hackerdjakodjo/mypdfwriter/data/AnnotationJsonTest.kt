package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationJsonTest {

    @Test
    fun fromJson_legacyAnnotation_defaultsNewFields() {
        val legacy = JSONObject(
            """{"id":"old","t":"PEN","p":2,"c":-16777216,"w":0.004,
               "pts":[0.1,0.2,0.3,0.4],"tx":"note","fs":0.024,"tr":270}""",
        )

        val annotation = Annotation.fromJson(legacy)

        assertNotNull(annotation)
        annotation!!
        assertEquals(AnnotationType.PEN, annotation.type)
        assertEquals(2, annotation.page)
        assertEquals("note", annotation.text)
        assertEquals(1f, annotation.opacity, 1e-6f)
        assertEquals("", annotation.stamp)
        assertEquals(270, annotation.textRotation)
        assertEquals(0L, annotation.createdAt)
        assertEquals(0L, annotation.durationMs)
    }

    @Test
    fun durationMs_roundTripsAsDur() {
        val original = Annotation(
            type = AnnotationType.PEN,
            page = 1,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
            createdAt = 1_760_000_100_000L,
            durationMs = 1_250L,
        )

        val json = original.toJson()
        val restored = Annotation.fromJson(json)

        assertEquals(1_250L, json.optLong("dur"))
        assertNotNull(restored)
        assertEquals(1_250L, restored!!.durationMs)
        assertEquals(original, restored)
    }

    @Test
    fun createdAt_roundTripsAsCt() {
        val original = Annotation(
            type = AnnotationType.PEN,
            page = 1,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = listOf(NormalizedPoint(0.1f, 0.2f)),
            createdAt = 1_760_000_123_456L,
        )

        val json = original.toJson()
        val restored = Annotation.fromJson(json)

        assertEquals(1_760_000_123_456L, json.optLong("ct"))
        assertNotNull(restored)
        assertEquals(1_760_000_123_456L, restored!!.createdAt)
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_persistsOpacityAndStamp() {
        val original = Annotation(
            type = AnnotationType.STAMP,
            page = 3,
            color = 0xFFD32F2F.toInt(),
            width = 0.004f,
            points = listOf(NormalizedPoint(0.4f, 0.6f)),
            fontSize = 0.03f,
            textRotation = 90,
            opacity = 0.42f,
            stamp = "CONFIDENTIAL",
        )

        val restored = Annotation.fromJson(original.toJson())

        assertNotNull(restored)
        assertEquals(original, restored)
    }

    @Test
    fun fromJson_parsesAllNewAnnotationTypes() {
        val newTypes = listOf(
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKEOUT,
            AnnotationType.ELLIPSE,
            AnnotationType.LINE,
            AnnotationType.STAMP,
            AnnotationType.SIGNATURE,
            AnnotationType.REDACT,
        )

        newTypes.forEach { type ->
            val json = JSONObject(
                """{"id":"${type.name}","t":"${type.name}","p":0,"c":-16777216,"w":0.004,
                   "pts":[0.1,0.1,0.9,0.9],"fs":0.024,"tr":0}""",
            )
            assertEquals(type, Annotation.fromJson(json)?.type)
        }
    }

    @Test
    fun fromJson_unknownTypeIsIgnored() {
        val json = JSONObject("""{"t":"TELEPORT","p":0,"c":0,"w":0.004,"pts":[]}""")

        assertNull(Annotation.fromJson(json))
    }
}
