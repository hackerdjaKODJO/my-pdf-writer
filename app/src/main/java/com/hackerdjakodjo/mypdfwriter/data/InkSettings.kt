package com.hackerdjakodjo.mypdfwriter.data

import android.content.Context
import org.json.JSONObject

/**
 * The active pen ink settings shared by the PDF viewer and the note editor.
 *
 * Only ink itself is shared: pen type, colour, width, opacity and pressure
 * curve. Tools, font size, note-only toggles and persisted strokes are NOT part
 * of this setting — they stay per surface / per document.
 */
data class InkSettings(
    val penStyle: PenStyle = PenStyle.BALLPOINT,
    val color: Int = DEFAULT_COLOR,
    val width: Float = DEFAULT_WIDTH,
    val opacity: Float = DEFAULT_OPACITY,
    val pressureCurve: PressureCurve = PressureCurve.DEFAULT,
    val highlightWidth: Float = HighlightInk.DEFAULT_WIDTH,
    /** Stylus pressure response; finger/mouse always record neutral pressure. */
    val pressureInk: Boolean = true,
    /** Highlighter opacity is independent from pen opacity. */
    val highlightOpacity: Float = DEFAULT_OPACITY,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("style", penStyle.name)
        put("color", color)
        put("width", width.toDouble())
        put("opacity", opacity.toDouble())
        put("curve", pressureCurve.name)
        put("hw", highlightWidth.toDouble())
        if (!pressureInk) put("pi", false)
        if (highlightOpacity != DEFAULT_OPACITY) put("ho", highlightOpacity.toDouble())
    }

    companion object {
        /** Stored in pen_box.xml so the existing backup contract keeps working. */
        const val KEY = "ink_v1"
        const val DEFAULT_COLOR = 0xFF1B1C19.toInt()
        const val DEFAULT_WIDTH = 0.004f
        const val DEFAULT_OPACITY = 1f
        const val MIN_WIDTH = 0.001f
        const val MAX_WIDTH = 0.03f
        const val MIN_OPACITY = 0.1f
        const val MAX_OPACITY = 1f

        val DEFAULT = InkSettings()

        fun clampWidth(value: Float): Float =
            if (value.isFinite()) value.coerceIn(MIN_WIDTH, MAX_WIDTH) else DEFAULT_WIDTH

        fun clampOpacity(value: Float): Float =
            if (value.isFinite()) value.coerceIn(MIN_OPACITY, MAX_OPACITY) else DEFAULT_OPACITY

        fun fromJson(o: JSONObject?): InkSettings {
            if (o == null) return DEFAULT
            return InkSettings(
                penStyle = runCatching {
                    PenStyle.valueOf(o.optString("style"))
                }.getOrDefault(DEFAULT.penStyle),
                color = o.optInt("color", DEFAULT_COLOR),
                width = clampWidth(o.optDouble("width", DEFAULT_WIDTH.toDouble()).toFloat()),
                opacity = clampOpacity(
                    o.optDouble("opacity", DEFAULT_OPACITY.toDouble()).toFloat(),
                ),
                pressureCurve = runCatching {
                    PressureCurve.valueOf(o.optString("curve"))
                }.getOrDefault(DEFAULT.pressureCurve),
                highlightWidth = HighlightInk.clamp(
                    o.optDouble("hw", HighlightInk.DEFAULT_WIDTH.toDouble()).toFloat(),
                ),
                pressureInk = o.optBoolean("pi", true),
                highlightOpacity = clampOpacity(
                    o.optDouble("ho", DEFAULT_OPACITY.toDouble()).toFloat(),
                ),
            )
        }

        fun fromRaw(raw: String?): InkSettings {
            if (raw.isNullOrBlank()) return DEFAULT
            return runCatching { fromJson(JSONObject(raw)) }.getOrDefault(DEFAULT)
        }
    }
}

/**
 * Highlighter width is deliberately separate from pen width: both surfaces used
 * to force 0.02f at capture while sharing the pen width state, so changing one
 * silently fattened the other pen. The range starts at the old note floor and
 * ends at the global width cap; opacity is independent from pen opacity.
 */
object HighlightInk {
    const val DEFAULT_WIDTH = 0.02f
    const val MIN_WIDTH = 0.018f
    const val MAX_WIDTH = 0.03f
    val range: ClosedFloatingPointRange<Float> = MIN_WIDTH..MAX_WIDTH

    fun clamp(value: Float): Float =
        if (value.isFinite()) value.coerceIn(MIN_WIDTH, MAX_WIDTH) else DEFAULT_WIDTH

    fun clampOpacity(value: Float): Float =
        if (value.isFinite()) value.coerceIn(InkSettings.MIN_OPACITY, InkSettings.MAX_OPACITY)
        else InkSettings.DEFAULT_OPACITY
}

/** Pure conversions between shared settings and named/synthetic pen presets. */
object InkPolicy {
    const val CURRENT_PEN_ID = "current-pen"
    const val CURRENT_PEN_NAME = "현재 펜"

    /**
     * Applying a preset restores its complete ink behavior. Legacy presets have
     * no pressure curve and inherit the current one; highlighter width/opacity
     * never overwrite the pen's independent values.
     */
    fun fromPreset(preset: PenPreset, current: InkSettings = InkSettings.DEFAULT): InkSettings =
        InkSettings(
            penStyle = preset.penStyle,
            color = preset.color,
            width = if (preset.tool == Tool.HIGHLIGHT) {
                current.width
            } else {
                InkSettings.clampWidth(preset.width)
            },
            opacity = if (preset.tool == Tool.HIGHLIGHT) {
                current.opacity
            } else {
                InkSettings.clampOpacity(preset.opacity)
            },
            pressureCurve = preset.pressureCurve ?: current.pressureCurve,
            highlightWidth = if (preset.tool == Tool.HIGHLIGHT) {
                HighlightInk.clamp(preset.width)
            } else {
                current.highlightWidth
            },
            pressureInk = preset.pressureInk ?: current.pressureInk,
            highlightOpacity = if (preset.tool == Tool.HIGHLIGHT) {
                HighlightInk.clampOpacity(preset.opacity)
            } else {
                current.highlightOpacity
            },
        )

    /** Transient preset used by the "current pen" strip and save-current action. */
    fun currentPen(settings: InkSettings, tool: Tool): PenPreset = when (tool) {
        Tool.HIGHLIGHT -> PenPreset(
            id = CURRENT_PEN_ID,
            name = CURRENT_PEN_NAME,
            color = settings.color,
            width = HighlightInk.clamp(settings.highlightWidth),
            opacity = HighlightInk.clampOpacity(settings.highlightOpacity),
            penStyle = PenStyle.BALLPOINT,
            tool = Tool.HIGHLIGHT,
            pressureCurve = settings.pressureCurve,
            pressureInk = settings.pressureInk,
        )
        else -> PenPreset(
            id = CURRENT_PEN_ID,
            name = CURRENT_PEN_NAME,
            color = settings.color,
            width = settings.width,
            opacity = settings.opacity,
            penStyle = settings.penStyle,
            tool = Tool.PEN,
            pressureCurve = settings.pressureCurve,
            pressureInk = settings.pressureInk,
        )
    }
}

/** SharedPreferences wrapper. Reuses pen_box.xml; commits synchronously like PenBoxStore. */
class InkSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PenBoxStore.PREFS, Context.MODE_PRIVATE)

    fun load(): InkSettings = InkSettings.fromRaw(prefs.getString(InkSettings.KEY, null))

    fun save(settings: InkSettings) {
        prefs.edit()
            .putString(InkSettings.KEY, settings.toJson().toString())
            .commit()
    }
}
