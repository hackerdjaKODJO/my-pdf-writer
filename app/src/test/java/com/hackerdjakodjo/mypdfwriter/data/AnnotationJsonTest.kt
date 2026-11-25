package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertNull(annotation.timesMs)
        assertTrue(annotation.points.all { it.pressure == 1f && it.timeMs == null })
    }

    @Test
    fun pressure_roundTripsThroughOptionalParallelArray() {
        val original = Annotation(
            type = AnnotationType.PEN,
            page = 1,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = listOf(
                NormalizedPoint(0.1f, 0.2f, 0.35f),
                NormalizedPoint(0.2f, 0.3f, 1f),
                NormalizedPoint(0.3f, 0.4f, 0.72f),
            ),
            penStyle = PenStyle.FOUNTAIN,
        )

        val json = original.toJson()
        val pressures = json.getJSONArray("prs")
        val restored = Annotation.fromJson(json)

        assertEquals(3, pressures.length())
        assertEquals(0.35, pressures.getDouble(0), 1e-6)
        assertEquals(1.0, pressures.getDouble(1), 1e-6)
        assertEquals(0.72, pressures.getDouble(2), 1e-6)
        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals(
            listOf(0.35f, 1f, 0.72f),
            restored!!.points.map { it.pressure },
        )
    }

    @Test
    fun pressureArray_isOmittedWhenEveryPointIsNeutral() {
        val annotation = Annotation(
            type = AnnotationType.PEN,
            page = 0,
            color = 0,
            width = 0.004f,
            points = listOf(NormalizedPoint(0.1f, 0.2f), NormalizedPoint(0.3f, 0.4f)),
        )

        val json = annotation.toJson()

        assertFalse(json.has("prs"))
        assertEquals(4, json.getJSONArray("pts").length())
        assertEquals(annotation, Annotation.fromJson(json))
    }

    @Test
    fun pressureJson_isClampedOnRead() {
        val json = JSONObject(
            """{"id":"p","t":"PEN","p":0,"c":0,"w":0.004,
               "pts":[0.1,0.2,0.3,0.4,0.5,0.6],"prs":[-2.0,2.0]}""",
        )

        val annotation = Annotation.fromJson(json)

        assertNotNull(annotation)
        assertEquals(
            listOf(0f, 1f, 1f),
            annotation!!.points.map { it.pressure },
        )
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
    fun pointTimes_roundTripThroughOptionalPtmsArray() {
        val original = Annotation(
            type = AnnotationType.PEN,
            page = 1,
            color = 0xFF000000.toInt(),
            width = 0.004f,
            points = listOf(
                NormalizedPoint(0.1f, 0.2f, 0.35f, 0L),
                NormalizedPoint(0.2f, 0.3f, 1f, 40L),
                NormalizedPoint(0.3f, 0.4f, 0.72f, 400L),
            ),
            durationMs = 450L,
            timesMs = listOf(0L, 40L, 400L),
        )

        val json = original.toJson()
        val restored = Annotation.fromJson(json)

        assertEquals(3, json.getJSONArray("ptms").length())
        assertEquals(0L, json.getJSONArray("ptms").getLong(0))
        assertEquals(40L, json.getJSONArray("ptms").getLong(1))
        assertEquals(400L, json.getJSONArray("ptms").getLong(2))
        assertEquals(original, restored)
    }

    @Test
    fun pointTimes_areOmittedWhenNull() {
        val annotation = timedAnnotation(timesMs = null)

        assertFalse(annotation.toJson().has("ptms"))
        assertNull(Annotation.fromJson(annotation.toJson())!!.timesMs)
    }

    @Test
    fun invalidPointTimes_fallBackToNullWithoutLosingLegacyPoints() {
        val invalid = listOf(
            JSONArray().put(0L).put(-1L).put(2L),
            JSONArray().put(0L).put(2L).put(1L),
            JSONArray().put(0.0).put(1.9).put(1.1),
            JSONArray().put(0L).put(2L),
            JSONArray().put(0L).put("bad").put(2L),
            JSONArray().put(0L).put(Double.NaN).put(2L),
            JSONArray().put(0L).put(Double.POSITIVE_INFINITY).put(2L),
        )
        invalid.forEachIndexed { index, times ->
            val json = timedAnnotation().toJson().put("ptms", times)
            val restored = Annotation.fromJson(json)!!
            assertNull("invalid timing case $index must be ignored", restored.timesMs)
            assertEquals(3, restored.points.size)
            assertEquals(listOf(0.35f, 1f, 0.72f), restored.points.map { it.pressure })
        }
    }

    @Test
    fun pointTimes_ignoreUnknownJsonFields() {
        val json = timedAnnotation().toJson()
            .put("future-pressure-field", JSONObject().put("enabled", true))

        assertEquals(listOf(0L, 10L, 900L), Annotation.fromJson(json)!!.timesMs)
    }

    @Test
    fun revealPointCount_usesNonUniformTimingAndDwellGaps() {
        val annotation = timedAnnotation()

        assertEquals(1, annotation.revealPointCount(0f))
        assertEquals(1, annotation.revealPointCount(0.005f))
        assertEquals(2, annotation.revealPointCount(0.01f))
        assertEquals(2, annotation.revealPointCount(0.5f))
        assertEquals(2, annotation.revealPointCount(0.899f))
        assertEquals(3, annotation.revealPointCount(0.9f))
        assertEquals(3, annotation.revealPointCount(1f))

        val dwell = annotation.copy(
            points = annotation.points + NormalizedPoint(0.4f, 0.5f, 1f, 10L),
            timesMs = listOf(0L, 10L, 10L, 900L),
        )
        // Every sample at the same dwell boundary becomes visible together.
        assertEquals(3, dwell.revealPointCount(0.01f))
        assertEquals(3, dwell.revealPointCount(0.5f))
    }

    @Test
    fun revealPointCount_legacyFallbackAndClamping() {
        val legacy = timedAnnotation(timesMs = null)
        assertEquals(1, legacy.revealPointCount(Float.NEGATIVE_INFINITY))
        assertEquals(1, legacy.revealPointCount(0f))
        assertEquals(1, legacy.revealPointCount(0.0001f))
        assertEquals(2, legacy.revealPointCount(0.5f))
        assertEquals(3, legacy.revealPointCount(Float.POSITIVE_INFINITY))
        assertEquals(1, legacy.revealPointCount(Float.NaN))

        val invalid = legacy.copy(timesMs = listOf(0L, 500L, 100L))
        assertEquals(2, invalid.revealPointCount(0.5f))
        assertEquals(3, legacy.copy(timesMs = listOf(0L, 900L)).revealPointCount(0.95f))
        assertEquals(3, legacy.copy(timesMs = listOf(0L, -1L, 900L)).revealPointCount(0.95f))
        val empty = legacy.copy(points = emptyList(), timesMs = emptyList())
        assertEquals(0, empty.revealPointCount(1f))
    }

    private fun timedAnnotation(timesMs: List<Long>? = listOf(0L, 10L, 900L)) = Annotation(
        type = AnnotationType.PEN,
        page = 0,
        color = 0,
        width = 0.004f,
        points = listOf(
            NormalizedPoint(0.1f, 0.2f, 0.35f, 0L),
            NormalizedPoint(0.2f, 0.3f, 1f, 10L),
            NormalizedPoint(0.3f, 0.4f, 0.72f, 900L),
        ),
        durationMs = 1_000L,
        timesMs = timesMs,
    )

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
            AnnotationType.COVER_TEXT,
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
    fun coverText_roundTripsReplacementAndCoverGeometry() {
        val original = Annotation(
            type = AnnotationType.COVER_TEXT,
            page = 2,
            color = -1,
            width = 0f,
            points = listOf(
                NormalizedPoint(0.10f, 0.20f),
                NormalizedPoint(0.40f, 0.24f),
            ),
            text = "새 문구",
            fontSize = 0.024f,
            opacity = 1f,
        )

        val restored = Annotation.fromJson(original.toJson())

        assertNotNull(restored)
        assertEquals(original, restored)
        assertEquals(2, restored!!.points.size)
        assertEquals("새 문구", restored.text)
    }

    @Test
    fun fromJson_unknownTypeIsIgnored() {
        val json = JSONObject("""{"t":"TELEPORT","p":0,"c":0,"w":0.004,"pts":[]}""")

        assertNull(Annotation.fromJson(json))
    }
}
