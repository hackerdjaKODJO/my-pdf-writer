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
    private const val PAN_LOG_TAG = "PdfContinuousPan"

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

    /** One pointer frame of horizontal panning, including the applied clamp. */
    fun pan(
        phase: String,
        pointerId: Long,
        deltaX: Float,
        panTotalX: Float,
        panBefore: Float,
        panAfter: Float,
        zoom: Float,
        maxPan: Float,
    ) {
        NoteTrace.event(
            TRACE_TAG,
            "pan",
            "phase" to phase,
            "pointerId" to pointerId,
            "deltaX" to deltaX,
            "panTotalX" to panTotalX,
            "panBefore" to panBefore,
            "panAfter" to panAfter,
            "zoom" to zoom,
            "maxPan" to maxPan,
        )
        logDebug(
            PAN_LOG_TAG,
            "$phase id=$pointerId dx=$deltaX total=$panTotalX " +
                "pan=$panBefore->$panAfter zoom=$zoom maxPan=$maxPan",
        )
    }

    /** Pan re-anchoring caused by a zoom change or viewport change. */
    fun zoomPan(
        reason: String,
        zoomBefore: Float,
        zoomAfter: Float,
        panBefore: Float,
        panAfter: Float,
        maxPan: Float,
        focalX: Float,
    ) {
        NoteTrace.event(
            TRACE_TAG,
            "zoomPan",
            "reason" to reason,
            "zoomBefore" to zoomBefore,
            "zoomAfter" to zoomAfter,
            "panBefore" to panBefore,
            "panAfter" to panAfter,
            "maxPan" to maxPan,
            "focalX" to focalX,
        )
        logDebug(
            PAN_LOG_TAG,
            "zoomPan reason=$reason zoom=$zoomBefore->$zoomAfter " +
                "pan=$panBefore->$panAfter maxPan=$maxPan focal=$focalX",
        )
    }

    /** A fit-zoom horizontal swipe that was resolved into a page turn. */
    fun pageSwipe(
        fromPage: Int,
        toPage: Int,
        direction: Int,
        totalX: Float,
        totalY: Float,
    ) {
        NoteTrace.event(
            TRACE_TAG,
            "pageSwipe",
            "from" to fromPage,
            "to" to toPage,
            "direction" to direction,
            "totalX" to totalX,
            "totalY" to totalY,
        )
        logDebug(
            PAN_LOG_TAG,
            "pageSwipe $fromPage->$toPage dir=$direction dx=$totalX dy=$totalY",
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
