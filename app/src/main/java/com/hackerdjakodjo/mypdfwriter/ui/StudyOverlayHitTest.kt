package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Screen rectangle of a normalized study-card region under the canvas transform.
 * Shared by drawing and input gating so a tap on a masking tape or checklist can
 * never also start a pen stroke underneath it.
 */
internal fun studyOverlayScreenRect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    fit: Float,
    pan: Offset,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float,
): Rect {
    val left = viewWidth / 2f + pan.x + (x * pageWidth - pageWidth / 2f) * fit
    val top = viewHeight / 2f + pan.y + (y * pageHeight - pageHeight / 2f) * fit
    return Rect(
        left = left,
        top = top,
        right = left + w * pageWidth * fit,
        bottom = top + h * pageHeight * fit,
    )
}
