package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool

/**
 * Pure layout policy for the compact note editor used inside the PDF study pane.
 *
 * The compact editor drops its own top app bar and uses one primary tool row plus
 * one slim action row. Keeping the row contents and heights here makes the
 * "smaller than the full dock" contract unit-testable without Compose.
 */

/** The six primary tools shown in the compact dock, in display order. */
internal val NOTE_COMPACT_DOCK_TOOLS = listOf(
    Tool.PEN,
    Tool.HIGHLIGHT,
    Tool.ERASER,
    Tool.LASSO,
    Tool.TEXT,
    Tool.RECT,
)

internal const val NOTE_COMPACT_TOOL_ROW_HEIGHT_DP = 42
internal const val NOTE_COMPACT_ACTION_ROW_HEIGHT_DP = 34
internal const val NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP = 38
internal const val NOTE_COMPACT_DOCK_DIVIDER_DP = 1

/** Heights of the existing full-screen dock, used only for comparison tests. */
internal const val NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP = 50
internal const val NOTE_FULL_DOCK_ACTION_ROW_HEIGHT_DP = 46

/** Canvas breathing room around the paper; the compact pane keeps it minimal. */
internal const val NOTE_COMPACT_CANVAS_PADDING_DP = 3

internal fun noteCompactDockHeightDp(showContextTray: Boolean): Int =
    NOTE_COMPACT_TOOL_ROW_HEIGHT_DP +
        NOTE_COMPACT_DOCK_DIVIDER_DP +
        NOTE_COMPACT_ACTION_ROW_HEIGHT_DP +
        if (showContextTray) NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP else 0

internal fun noteFullDockHeightDp(showContextTray: Boolean): Int =
    NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP +
        NOTE_COMPACT_DOCK_DIVIDER_DP +
        NOTE_FULL_DOCK_ACTION_ROW_HEIGHT_DP +
        if (showContextTray) NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP else 0

/** Short labels that fit six tools on a 320 dp-wide phone dock. */
internal fun noteCompactToolLabel(tool: Tool): String = when (tool) {
    Tool.PEN -> "펜"
    Tool.HIGHLIGHT -> "형광펜"
    Tool.ERASER -> "지우개"
    Tool.LASSO, Tool.LASSO_RECT -> "올가미"
    Tool.TEXT -> "텍스트"
    Tool.RECT, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> "도형"
    else -> "도구"
}

/**
 * Pen and highlighter are the default writing tools; their hint is noise in a
 * focused pane. Other tools get one tiny contextual line.
 */
internal fun noteCompactHintVisible(tool: Tool): Boolean =
    tool != Tool.PEN && tool != Tool.HIGHLIGHT

/** One short, single-line hint for the compact canvas overlay. */
internal fun noteCompactHint(
    tool: Tool,
    layerName: String,
    layerLocked: Boolean,
    zoomLock: Boolean,
): String = buildString {
    append(noteToolHint(tool))
    val layer = layerName.trim()
    if (layer.isNotEmpty()) {
        append(" · ")
        append(layer)
    }
    if (layerLocked) append(" · 잠김")
    if (zoomLock) append(" · 줌 잠금")
}
