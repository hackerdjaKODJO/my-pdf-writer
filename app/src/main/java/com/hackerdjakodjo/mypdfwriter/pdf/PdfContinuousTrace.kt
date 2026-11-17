package com.hackerdjakodjo.mypdfwriter.pdf

import android.util.Log
import com.hackerdjakodjo.mypdfwriter.debug.NoteTrace

/**
 * Debug-only tracing for the continuous reader's pinch/render path.
 *
 * Every call writes a compact event to [NoteTrace] (for the in-app trace sheet)
 * and a matching [Log.d] line so `adb logcat -s PdfContinuousPinch` /
 * `adb logcat -s PdfContinuousRender` works on a device. No PDF text or other
 * sensitive data is logged; no persistence, network or permissions are added.
 */
object PdfContinuousTrace {
    private const val TRACE_TAG = "PDF_CONTINUOUS"
    private const val PINCH_LOG_TAG = "PdfContinuousPinch"
    private const val RENDER_LOG_TAG = "PdfContinuousRender"

    fun pinch(zoom: Float, renderZoom: Float, distance: Float) {
        NoteTrace.event(
            TRACE_TAG,
            "pinch",
            "zoom" to zoom,
            "renderZoom" to renderZoom,
            "distance" to distance,
        )
        logDebug(PINCH_LOG_TAG, "zoom=$zoom renderZoom=$renderZoom dist=$distance")
    }

    fun render(
        page: Int,
        zoom: Float,
        renderZoom: Float,
        scale: Float,
        bitmapWidth: Int,
        bitmapHeight: Int,
    ) {
        NoteTrace.event(
            TRACE_TAG,
            "render",
            "page" to page,
            "zoom" to zoom,
            "renderZoom" to renderZoom,
            "scale" to scale,
            "bitmap" to "${bitmapWidth}x${bitmapHeight}",
        )
        logDebug(
            RENDER_LOG_TAG,
            "page=$page zoom=$zoom renderZoom=$renderZoom scale=$scale " +
                "bitmap=${bitmapWidth}x${bitmapHeight}",
        )
    }

    private fun logDebug(tag: String, message: String) {
        if (!NoteTrace.enabled) return
        try {
            Log.d(tag, message)
        } catch (_: RuntimeException) {
            // android.util.Log is stubbed in local JVM unit tests and throws
            // "Method d in android.util.Log not mocked"; keep the helper testable.
        }
    }
}
