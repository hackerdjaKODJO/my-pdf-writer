package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Tool

/** Canonical shape vocabulary shared by the PDF and note tool pickers. */
internal enum class ShapeKind { RECT, TRIANGLE, ELLIPSE, LINE, ARROW }

internal val SHAPE_TOOLS: List<Tool> = listOf(Tool.RECT, Tool.TRIANGLE, Tool.ELLIPSE, Tool.LINE, Tool.ARROW)

internal fun shapeKindOrNull(tool: Tool): ShapeKind? = when (tool) {
    Tool.RECT -> ShapeKind.RECT
    Tool.TRIANGLE -> ShapeKind.TRIANGLE
    Tool.ARROW -> ShapeKind.ARROW
    Tool.LINE -> ShapeKind.LINE
    Tool.ELLIPSE -> ShapeKind.ELLIPSE
    else -> null
}

internal fun shapeToolLabel(tool: Tool): String = when (shapeKindOrNull(tool)) {
    ShapeKind.RECT -> "사각형"
    ShapeKind.TRIANGLE -> "삼각형"
    ShapeKind.ARROW -> "화살표"
    ShapeKind.LINE -> "직선"
    ShapeKind.ELLIPSE -> "타원"
    null -> "도형"
}

internal fun shapeToolTag(tool: Tool): String = "tool_tile_${tool.name.lowercase()}"
