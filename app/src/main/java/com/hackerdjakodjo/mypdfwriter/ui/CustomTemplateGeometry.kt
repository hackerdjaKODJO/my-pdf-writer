package com.hackerdjakodjo.mypdfwriter.ui

import kotlin.math.max

/**
 * Destination rectangle for the custom background's **cover** policy.
 *
 * The imported image is scaled uniformly until both page dimensions are covered,
 * then centred and clipped by the page. It is never stretched or letterboxed. A
 * portrait image applied to a landscape page therefore crops the top/bottom (or
 * sides) instead of distorting the handwriting surface. The editor, page
 * thumbnails and raster PDF export all call this same math.
 */
data class CustomTemplatePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

object CustomTemplateGeometry {

    fun cover(
        imageWidth: Int,
        imageHeight: Int,
        pageWidth: Float,
        pageHeight: Float,
    ): CustomTemplatePlacement? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        if (!pageWidth.isFinite() || !pageHeight.isFinite()) return null
        if (pageWidth <= 0f || pageHeight <= 0f) return null

        val scale = max(
            pageWidth / imageWidth.toFloat(),
            pageHeight / imageHeight.toFloat(),
        )
        if (!scale.isFinite() || scale <= 0f) return null
        val width = imageWidth * scale
        val height = imageHeight * scale
        if (!width.isFinite() || !height.isFinite()) return null
        return CustomTemplatePlacement(
            left = (pageWidth - width) / 2f,
            top = (pageHeight - height) / 2f,
            width = width,
            height = height,
        )
    }
}
