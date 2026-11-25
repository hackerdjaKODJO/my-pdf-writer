package com.hackerdjakodjo.mypdfwriter.data

import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect
import org.json.JSONObject

/**
 * A durable pointer from a note page to its original PDF page.
 *
 * [sourcePage] is the source ordinal, not a viewer index. Viewer indices change
 * when pages are inserted, deleted, moved or reset; the source ordinal does not.
 */
data class StudySourceLink(
    val uri: String,
    val docId: String? = null,
    val sourcePage: Int,
    val fingerprint: String? = null,
    val rect: NormalizedRect? = null,
    val excerptId: String? = null,
    val rotation: Int = 0,
) {
    /** Returns a safe link, or null when required identity/location data is invalid. */
    fun normalized(): StudySourceLink? {
        val safeUri = uri.trim()
        if (safeUri.isEmpty() || sourcePage < 0) return null
        if (rect != null && !rect.isFiniteNormalized()) return null
        return copy(
            uri = safeUri,
            docId = docId?.trim()?.takeIf { it.isNotEmpty() },
            sourcePage = sourcePage,
            fingerprint = fingerprint?.trim()?.takeIf { it.isNotEmpty() },
            rect = rect,
            excerptId = excerptId?.trim()?.takeIf { it.isNotEmpty() },
            rotation = PageSpec.normalizeRotation(rotation),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("docId", docId ?: JSONObject.NULL)
        put("sourcePage", sourcePage)
        put("fingerprint", fingerprint ?: JSONObject.NULL)
        if (rect == null) {
            put("rect", JSONObject.NULL)
        } else {
            put(
                "rect",
                JSONObject().apply {
                    put("left", rect.left.toDouble())
                    put("top", rect.top.toDouble())
                    put("right", rect.right.toDouble())
                    put("bottom", rect.bottom.toDouble())
                },
            )
        }
        put("excerptId", excerptId ?: JSONObject.NULL)
        put("rotation", PageSpec.normalizeRotation(rotation))
    }

    companion object {
        /** Malformed optional link data is ignored by callers, never used to discard a page. */
        fun fromJson(o: JSONObject): StudySourceLink? = runCatching {
            val uri = o.requiredString("uri")
            val sourcePage = o.integer("sourcePage")?.takeIf { it >= 0 } ?: return null
            val rotation = o.integer("rotation") ?: 0
            val rect = if (!o.has("rect") || o.isNull("rect")) {
                null
            } else {
                rectFromJson(o.getJSONObject("rect"))
            }
            StudySourceLink(
                uri = uri,
                docId = o.optionalString("docId"),
                sourcePage = sourcePage,
                fingerprint = o.optionalString("fingerprint"),
                rect = rect,
                excerptId = o.optionalString("excerptId"),
                rotation = rotation,
            ).normalized()
        }.getOrNull()

        private fun rectFromJson(o: JSONObject): NormalizedRect {
            val rect = NormalizedRect(
                left = o.finiteCoordinate("left"),
                top = o.finiteCoordinate("top"),
                right = o.finiteCoordinate("right"),
                bottom = o.finiteCoordinate("bottom"),
            )
            return rect.takeIf { it.isFiniteNormalized() }
                ?: throw IllegalArgumentException("Invalid normalized source rectangle")
        }

        private fun JSONObject.requiredString(key: String): String =
            (get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Missing source link $key")

        private fun JSONObject.optionalString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return (get(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw IllegalArgumentException("Invalid source link $key")
        }

        private fun JSONObject.integer(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            val value = (get(key) as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid source link $key")
            if (!value.isFinite() || value != kotlin.math.floor(value)) {
                throw IllegalArgumentException("Invalid source link $key")
            }
            if (value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble()) {
                throw IllegalArgumentException("Invalid source link $key")
            }
            return value.toInt()
        }

        private fun JSONObject.finiteCoordinate(key: String): Float {
            val value = (get(key) as? Number)?.toDouble()
                ?: throw IllegalArgumentException("Invalid source rectangle $key")
            if (!value.isFinite()) {
                throw IllegalArgumentException("Invalid source rectangle $key")
            }
            return value.toFloat().also { coordinate ->
                if (!coordinate.isFinite()) {
                    throw IllegalArgumentException("Invalid source rectangle $key")
                }
            }
        }
    }
}

/** True only for a finite, ordered rectangle whose coordinates are normalized to 0..1. */
internal fun NormalizedRect.isFiniteNormalized(): Boolean =
    left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
        left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
        left <= right && top <= bottom
