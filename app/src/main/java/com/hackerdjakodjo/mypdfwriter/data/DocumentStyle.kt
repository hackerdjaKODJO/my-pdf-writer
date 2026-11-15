package com.hackerdjakodjo.mypdfwriter.data

import org.json.JSONObject

enum class PageNumberPosition { BOTTOM_CENTER, BOTTOM_RIGHT, TOP_CENTER }

enum class PageNumberFormat { PLAIN, OF_TOTAL, DASHED }

data class WatermarkStyle(
    val text: String = "",
    val opacity: Float = 0.14f,
    val rotation: Int = -32,
    val color: Int = 0xFFB3261E.toInt(),
) {
    val enabled: Boolean get() = text.isNotBlank()
}

data class PageNumberStyle(
    val enabled: Boolean = false,
    val format: PageNumberFormat = PageNumberFormat.OF_TOTAL,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val startNumber: Int = 1,
    val color: Int = 0xFF4B4A44.toInt(),
) {
    fun label(pageIndex: Int, pageCount: Int): String {
        val number = pageIndex + startNumber
        val last = pageCount + startNumber - 1
        return when (format) {
            PageNumberFormat.PLAIN -> "$number"
            PageNumberFormat.OF_TOTAL -> "$number / $last"
            PageNumberFormat.DASHED -> "- $number -"
        }
    }
}

data class DocumentStyle(
    val watermark: WatermarkStyle = WatermarkStyle(),
    val pageNumbers: PageNumberStyle = PageNumberStyle(),
) {
    val isDefault: Boolean
        get() = !watermark.enabled && !pageNumbers.enabled

    fun toJson(): JSONObject = JSONObject().apply {
        put("watermark", JSONObject().apply {
            put("text", watermark.text)
            put("opacity", watermark.opacity.toDouble())
            put("rotation", watermark.rotation)
            put("color", watermark.color)
        })
        put("pageNumbers", JSONObject().apply {
            put("enabled", pageNumbers.enabled)
            put("format", pageNumbers.format.name)
            put("position", pageNumbers.position.name)
            put("start", pageNumbers.startNumber)
            put("color", pageNumbers.color)
        })
    }

    companion object {
        fun fromJson(o: JSONObject): DocumentStyle {
            val w = o.optJSONObject("watermark")
            val p = o.optJSONObject("pageNumbers")
            return DocumentStyle(
                watermark = WatermarkStyle(
                    text = w?.optString("text").orEmpty(),
                    opacity = w?.optDouble("opacity", 0.14)?.toFloat() ?: 0.14f,
                    rotation = w?.optInt("rotation", -32) ?: -32,
                    color = w?.optInt("color", 0xFFB3261E.toInt()) ?: 0xFFB3261E.toInt(),
                ),
                pageNumbers = PageNumberStyle(
                    enabled = p?.optBoolean("enabled", false) ?: false,
                    format = runCatching {
                        PageNumberFormat.valueOf(p?.optString("format") ?: "")
                    }.getOrDefault(PageNumberFormat.OF_TOTAL),
                    position = runCatching {
                        PageNumberPosition.valueOf(p?.optString("position") ?: "")
                    }.getOrDefault(PageNumberPosition.BOTTOM_CENTER),
                    startNumber = p?.optInt("start", 1) ?: 1,
                    color = p?.optInt("color", 0xFF4B4A44.toInt()) ?: 0xFF4B4A44.toInt(),
                ),
            )
        }
    }
}
