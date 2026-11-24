package com.hackerdjakodjo.mypdfwriter.pdf

import com.hackerdjakodjo.mypdfwriter.debug.DevLog

/**
 * Typed helpers for high-value PDF flow events in the durable [DevLog].
 *
 * Mirrors the style of `PdfContinuousTrace`: every helper is a single call with
 * canonical field names. Only counts, kinds, opaque ids and timings are logged;
 * no PDF text, file names, paths, URIs, annotation text or search queries.
 */
object PdfFlowTrace {
    const val COMPONENT = "PDF_FLOW"

    /** An open was requested. [uriKind] is a kind (e.g. "file"/"content"), never a URI. */
    fun openStart(uriKind: String, addToRecents: Boolean) {
        DevLog.event(
            COMPONENT,
            "open_start",
            "uriKind" to uriKind,
            "addToRecents" to addToRecents,
        )
    }

    /** An open completed. [docId] is an opaque id, never a file name or path. */
    fun openDone(docId: String, pageCount: Int, elapsedMs: Long) {
        DevLog.event(
            COMPONENT,
            "open_done",
            "docId" to docId,
            "pageCount" to pageCount,
            "elapsedMs" to elapsedMs,
        )
    }

    /** An open failed. [reason] is a short machine-readable category. */
    fun openFailed(reason: String) {
        DevLog.event(COMPONENT, "open_fail", "reason" to reason)
    }

    /** A document was closed. */
    fun close(docId: String, annotationCount: Int) {
        DevLog.event(
            COMPONENT,
            "close",
            "docId" to docId,
            "annotationCount" to annotationCount,
        )
    }

    /** An annotation was committed. [type] is a kind; [annotationId] is opaque. */
    fun annotate(type: String, annotationId: String, page: Int, total: Int) {
        DevLog.event(
            COMPONENT,
            "annotate",
            "type" to type,
            "annotationId" to annotationId,
            "page" to page,
            "total" to total,
        )
    }

    /** An annotation history action; [annotationId] may be null for bulk actions. */
    fun history(action: String, annotationId: String?, remaining: Int) {
        DevLog.event(
            COMPONENT,
            "history",
            "action" to action,
            "annotationId" to annotationId,
            "remaining" to remaining,
        )
    }

    /** A page-level edit (rotate/crop/insert/delete). */
    fun pageEdit(kind: String, pageCount: Int, revision: Long) {
        DevLog.event(
            COMPONENT,
            "page_edit",
            "kind" to kind,
            "pageCount" to pageCount,
            "revision" to revision,
        )
    }

    /** A search was run. Only lengths/counts are logged, never the query text. */
    fun search(queryLength: Int, hitCount: Int) {
        DevLog.event(
            COMPONENT,
            "search",
            "queryLength" to queryLength,
            "hitCount" to hitCount,
        )
    }

    /** An export started. */
    fun exportStart() {
        DevLog.event(COMPONENT, "export_start")
    }

    /** An export finished. */
    fun exportDone(bytes: Long, flattened: Boolean) {
        DevLog.event(
            COMPONENT,
            "export_done",
            "bytes" to bytes,
            "flattened" to flattened,
        )
    }
}
