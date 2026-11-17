package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool

/**
 * Pure layout policy for the compact note editor used inside the PDF study pane.
 *
 * The compact editor no longer reserves a full-width bottom bar: the canvas fills
 * the whole pane and a small translucent dock floats over its lower edge. Keeping
 * the row sizes, margins and maximum width here makes the "smaller than the full
 * dock" contract unit-testable without Compose. Every target stays inside the
 * 32–40 dp range requested for the floating chrome.
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

internal const val NOTE_COMPACT_TOOL_ROW_HEIGHT_DP = 34
internal const val NOTE_COMPACT_ACTION_ROW_HEIGHT_DP = 32
internal const val NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP = 38
internal const val NOTE_COMPACT_DOCK_DIVIDER_DP = 1
internal const val NOTE_COMPACT_DOCK_VERTICAL_PADDING_DP = 3
internal const val NOTE_COMPACT_DOCK_HORIZONTAL_PADDING_DP = 3
internal const val NOTE_COMPACT_DOCK_SIDE_MARGIN_DP = 8
internal const val NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP = 6
internal const val NOTE_COMPACT_DOCK_CORNER_DP = 18

/** The floating pill stays narrow on tablets instead of growing into a bar. */
internal const val NOTE_COMPACT_DOCK_MAX_WIDTH_DP = 340

/** Heights of the existing full-screen dock, used only for comparison tests. */
internal const val NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP = 50
internal const val NOTE_FULL_DOCK_ACTION_ROW_HEIGHT_DP = 46

/** Canvas breathing room around the paper; the compact pane keeps it minimal. */
internal const val NOTE_COMPACT_CANVAS_PADDING_DP = 3

internal fun noteCompactDockHeightDp(showContextTray: Boolean): Int =
    NOTE_COMPACT_DOCK_VERTICAL_PADDING_DP * 2 +
        (if (showContextTray) NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP else 0) +
        NOTE_COMPACT_TOOL_ROW_HEIGHT_DP +
        NOTE_COMPACT_DOCK_DIVIDER_DP +
        NOTE_COMPACT_ACTION_ROW_HEIGHT_DP

/** Dock height plus the gap below it, used to keep snackbars above the pill. */
internal fun noteCompactDockObscuredHeightDp(showContextTray: Boolean): Int =
    noteCompactDockHeightDp(showContextTray) + NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP

/**
 * Width available to the floating dock: the pane width minus both side margins,
 * capped so a wide pane still shows a compact Flexcil-like pill.
 */
internal fun noteFloatingDockWidthDp(availableWidthDp: Int): Int {
    val safeWidth = availableWidthDp.coerceAtLeast(0)
    return (safeWidth - NOTE_COMPACT_DOCK_SIDE_MARGIN_DP * 2)
        .coerceIn(0, NOTE_COMPACT_DOCK_MAX_WIDTH_DP)
}

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
