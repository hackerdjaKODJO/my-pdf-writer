package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool

/**
 * Pure layout policy for the compact note editor used inside the PDF study pane.
 *
 * The compact editor no longer reserves a full-width bottom bar: the canvas fills
 * the whole pane and a small translucent dock floats over its lower edge. Keeping
 * the row sizes, margins and maximum width here makes the "smaller than the full
 * dock" contract unit-testable without Compose. The dock rows are deliberately
 * dense in appearance, but every interactive slot is at least [MIN_TARGET_DP];
 * the visuals stay small inside that hit target instead.
 */

/**
 * The one-row dock's six primary tools, in display order. Both the full editor
 * and the compact study pane read this single list, so their labels and ordering
 * can never drift. `LASSO` keeps its identifier and lasso behaviour; only the
 * student-facing label is `선택`.
 */
internal val NOTE_DOCK_TOOLS = listOf(
    Tool.PEN,
    Tool.HIGHLIGHT,
    Tool.ERASER,
    Tool.LASSO,
    Tool.TEXT,
    Tool.RECT,
)

/** Kept as an explicit alias so both docks name the same six slots. */
internal val NOTE_FULL_DOCK_TOOLS: List<Tool> = NOTE_DOCK_TOOLS
internal val NOTE_COMPACT_DOCK_TOOLS: List<Tool> = NOTE_DOCK_TOOLS

/**
 * Pen is the primary writing tool in both docks. It keeps the same slot and the
 * same label; the dock only gives it the primary tint so a student can find the
 * default tool without reading every slot. Selection is still a separate state
 * reported by [noteToolDescription].
 */
internal val NOTE_PRIMARY_DOCK_TOOL: Tool = Tool.PEN

internal fun noteToolIsPrimary(tool: Tool): Boolean = tool == NOTE_PRIMARY_DOCK_TOOL

/**
 * Action slots. The colour dot and the style button are one slot, and the note
 * menu owns favourite/export so neither dock duplicates them.
 *
 * The compact pane has no app bar, so its dock keeps a small action row with
 * undo/redo/menu plus the style pill. The full editor is a single tool row: undo,
 * redo and the menu live in the top bar and style is the row's trailing pill.
 */
internal enum class NoteDockActionSlot {
    STYLE,
    UNDO,
    REDO,
    MENU,
}

internal val NOTE_COMPACT_ACTION_SLOTS = listOf(
    NoteDockActionSlot.STYLE,
    NoteDockActionSlot.UNDO,
    NoteDockActionSlot.REDO,
    NoteDockActionSlot.MENU,
)

/** The full dock's only trailing slot is the style colour pill. */
internal val NOTE_FULL_DOCK_ACTION_SLOTS = listOf(NoteDockActionSlot.STYLE)

/**
 * Full editor top-bar actions, after the back arrow and the rename title.
 * Favourite and export are deliberately absent: both stay in the 노트 메뉴, so
 * the top bar never offers a second copy of either action.
 */
internal enum class NoteTopBarAction {
    SEARCH,
    UNDO,
    REDO,
    MENU,
    // Represented here only so the no-duplicate policy can be asserted in tests.
    FAVORITE,
    EXPORT,
}

/**
 * Top bar order follows the student's reading order: 뒤로(back) and 제목(title)
 * are the navigation/title slots owned by the app bar itself, then these quick
 * actions run 메뉴 → 실행 취소 → 다시 실행. Menu stays first so history actions
 * never push it off a 320 dp bar.
 */
internal val NOTE_TOP_BAR_ACTIONS = listOf(
    NoteTopBarAction.MENU,
    NoteTopBarAction.SEARCH,
    NoteTopBarAction.UNDO,
    NoteTopBarAction.REDO,
)

internal fun noteTopBarActionLabel(action: NoteTopBarAction): String = when (action) {
    NoteTopBarAction.SEARCH -> "노트 검색"
    NoteTopBarAction.UNDO -> "실행 취소"
    NoteTopBarAction.REDO -> "다시 실행"
    NoteTopBarAction.MENU -> "노트 메뉴"
    NoteTopBarAction.FAVORITE -> "즐겨찾기"
    NoteTopBarAction.EXPORT -> "PDF로 내보내기"
}

internal const val NOTE_COMPACT_TOOL_ROW_HEIGHT_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_ACTION_ROW_HEIGHT_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_DOCK_DIVIDER_DP = 1
internal const val NOTE_COMPACT_DOCK_VERTICAL_PADDING_DP = 3
internal const val NOTE_COMPACT_DOCK_HORIZONTAL_PADDING_DP = 3
internal const val NOTE_COMPACT_DOCK_SIDE_MARGIN_DP = 8
internal const val NOTE_COMPACT_DOCK_BOTTOM_MARGIN_DP = 6
internal const val NOTE_COMPACT_DOCK_CORNER_DP = 18

/** The floating pill stays narrow on tablets instead of growing into a bar. */
internal const val NOTE_COMPACT_DOCK_MAX_WIDTH_DP = 340

/** Height of the full-screen dock's single tool row (contextual tray excluded). */
internal const val NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP = 56

/**
 * Which piece of tool chrome the compact note pane shows. The study-pane header
 * owns a `도구 표시/숨기기` toggle: while tools are visible the full dock floats on
 * the bottom edge, and while hidden every control is replaced by one small pill in
 * the bottom-end corner so the centred writing hotspot stays clear.
 */
internal enum class NoteCompactChromeMode {
    DOCK,
    RESTORE_PILL,
}

/** Pure policy behind the study-pane header toggle. */
internal fun noteCompactChromeMode(showTools: Boolean): NoteCompactChromeMode =
    if (showTools) NoteCompactChromeMode.DOCK else NoteCompactChromeMode.RESTORE_PILL

/**
 * Restore-pill metrics. The pill is a real touch target ([MIN_TARGET_DP]) even
 * though its visible chrome stays deliberately dense for the study pane. It is
 * anchored to the bottom-end corner, never to the centre of the canvas where the
 * user writes.
 */
internal const val NOTE_COMPACT_RESTORE_PILL_MIN_TARGET_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_RESTORE_PILL_HEIGHT_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_RESTORE_PILL_MIN_WIDTH_DP = MIN_TARGET_DP
internal const val NOTE_COMPACT_RESTORE_PILL_CORNER_DP = 22
internal const val NOTE_COMPACT_RESTORE_PILL_END_MARGIN_DP = 8
internal const val NOTE_COMPACT_RESTORE_PILL_BOTTOM_MARGIN_DP = 10
internal const val NOTE_COMPACT_RESTORE_PILL_HORIZONTAL_PADDING_DP = 12

/** Snackbar clearance depends on which chrome variant is currently shown. */
internal fun noteCompactChromeObscuredHeightDp(
    mode: NoteCompactChromeMode,
    showContextTray: Boolean,
): Int = when (mode) {
    NoteCompactChromeMode.DOCK -> noteCompactDockObscuredHeightDp(showContextTray)
    NoteCompactChromeMode.RESTORE_PILL ->
        NOTE_COMPACT_RESTORE_PILL_HEIGHT_DP + NOTE_COMPACT_RESTORE_PILL_BOTTOM_MARGIN_DP
}

/**
 * A 320 dp pane must still fit the pill plus both end margins. The dock keeps its
 * own eight-dp side margins, so this check covers the smallest supported phone.
 */
internal fun noteCompactRestorePillFitsWidth(availableWidthDp: Int): Boolean =
    availableWidthDp >= NOTE_COMPACT_RESTORE_PILL_MIN_WIDTH_DP +
        NOTE_COMPACT_RESTORE_PILL_END_MARGIN_DP * 2

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

/** The full dock is one tool row; undo/redo moved to the top bar and the page pill floats. */
internal fun noteFullDockHeightDp(showContextTray: Boolean): Int =
    NOTE_FULL_DOCK_TOOL_ROW_HEIGHT_DP +
        if (showContextTray) NOTE_COMPACT_TOOL_TRAY_HEIGHT_DP else 0

/**
 * Whether the dock slots may draw their short text labels. Large accessibility
 * fonts trade those secondary labels for the same 48 dp hit targets, so the
 * decision never changes the slot geometry.
 */
internal fun noteDockShowLabels(widthDp: Int, fontScale: Float): Boolean =
    chromeScale(widthDp, fontScale).showDockLabels

/** Spoken name of a dock slot, including the selected state when it is active. */
internal fun noteToolDescription(label: String, selected: Boolean): String =
    stateLabel(label, selected)

/** Spoken name of the page pill. Always carries the one-based "페이지 n / N". */
internal fun notePagePillDescription(pageIndex: Int, pageCount: Int): String =
    "$NOTE_PAGE_MANAGER_LABEL ${pageIndex + 1} / $pageCount"

/** Spoken name of one page thumbnail; [number] is the zero-based index. */
internal fun notePageThumbnailDescription(number: Int): String =
    "$NOTE_PAGE_MANAGER_LABEL ${number + 1}"

/**
 * Student-first text policy: secondary labels and supporting lines may wrap to
 * two lines, never more. One line is still preferred when it fits, so short
 * labels do not gain artificial height at 320 dp or with large fonts.
 */
internal const val STUDENT_LABEL_MAX_LINES = 2

internal fun studentLabelMaxLines(
    availableWidthDp: Int,
    estimatedTextWidthDp: Int,
    fontScale: Float = 1f,
): Int {
    val scale = if (fontScale.isNaN()) 1f else fontScale.coerceIn(1f, 3f)
    val scaled = (estimatedTextWidthDp * scale).toInt()
    return minOf(STUDENT_LABEL_MAX_LINES, labelMaxLines(availableWidthDp, scaled))
}

/**
 * One capture vocabulary for every "put this in my notes" row. Translation and
 * dictionary stay distinct external hand-offs and are deliberately kept out of
 * this group so they are not mistaken for local capture.
 */
internal const val NOTE_CAPTURE_LABEL = "노트에 담기"
internal val STUDENT_HANDOFF_LABELS: List<String> = listOf("번역", "사전")

internal fun studentCaptureLabel(): String = NOTE_CAPTURE_LABEL

/** One shared label helper so full and compact docks never drift. */
internal fun noteToolLabel(tool: Tool): String = when (tool) {
    Tool.PEN -> "펜"
    Tool.HIGHLIGHT -> "형광펜"
    Tool.ERASER -> "지우개"
    Tool.LASSO, Tool.LASSO_RECT -> "선택"
    Tool.TEXT -> NOTE_TEXT_TOOL_LABEL
    Tool.RECT, Tool.TRIANGLE, Tool.ARROW, Tool.LINE, Tool.ELLIPSE -> "도형"
    else -> "도구"
}

/** Short labels that fit six tools on a 320 dp-wide phone dock. */
internal fun noteCompactToolLabel(tool: Tool): String = noteToolLabel(tool)

/**
 * Pen and highlighter are the default writing tools; their hint is noise in a
 * focused pane. Other tools get one tiny contextual line.
 */
internal fun noteCompactHintVisible(tool: Tool): Boolean =
    tool != Tool.PEN && tool != Tool.HIGHLIGHT

/**
 * The page rail (with its own add button) is only composed on wide layouts.
 * On phones the floating page pill is the natural one-tap home for "new page",
 * except in compact mode where the floating dock owns that action.
 */
internal fun notePagePillShowsAdd(wide: Boolean, compact: Boolean): Boolean =
    !wide && !compact

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
