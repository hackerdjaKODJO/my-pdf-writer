package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.StudySource
import com.hackerdjakodjo.mypdfwriter.data.StudySourceLink
import com.hackerdjakodjo.mypdfwriter.pdf.NormalizedRect

/** Explicit outcome of resolving a durable note link against the active PDF. */
sealed interface StudyLinkResolution {
    data class Found(
        val viewPageIndex: Int,
        val rect: NormalizedRect?,
        val ambiguous: Boolean = false,
    ) : StudyLinkResolution

    data object WrongDocument : StudyLinkResolution
    data object SourceChanged : StudyLinkResolution
    data object PageMissing : StudyLinkResolution
    data object SourceUnavailable : StudyLinkResolution
}

/**
 * Resolves through source ordinals so insert/delete/move/rotate/reset operations do
 * not invalidate a link. Duplicate source pages resolve to the first current page
 * deterministically and are marked ambiguous rather than silently discarded.
 */
fun resolveStudyLink(
    link: StudySourceLink,
    currentDocId: String?,
    currentFingerprint: String?,
    pages: List<PageSpec>,
): StudyLinkResolution {
    val linkDocId = link.docId?.trim()?.takeIf { it.isNotEmpty() }
    val activeDocId = currentDocId?.trim()?.takeIf { it.isNotEmpty() }
    if (link.uri.isBlank() || (!linkDocId.isNullOrBlank() && activeDocId.isNullOrBlank())) {
        return StudyLinkResolution.SourceUnavailable
    }
    if (!linkDocId.isNullOrBlank() && linkDocId != activeDocId) {
        return StudyLinkResolution.WrongDocument
    }

    val linkFingerprint = link.fingerprint?.trim()?.takeIf { it.isNotEmpty() }
    val activeFingerprint = currentFingerprint?.trim()?.takeIf { it.isNotEmpty() }
    if (!linkFingerprint.isNullOrBlank() && !activeFingerprint.isNullOrBlank() &&
        linkFingerprint != activeFingerprint
    ) {
        return StudyLinkResolution.SourceChanged
    }

    val matches = pages.indices.filter { pages[it].sourcePage == link.sourcePage }
    if (matches.isEmpty()) return StudyLinkResolution.PageMissing
    return StudyLinkResolution.Found(
        viewPageIndex = matches.first(),
        rect = link.rect,
        ambiguous = matches.size > 1,
    )
}

/**
 * Matches a study source without ever using display names to bridge two known
 * URIs. Name/title fallback is limited to legacy notebooks with no URI identity
 * and no explicit link that cannot be reconciled with [source].
 */
fun studySourcesMatch(notebook: Notebook, source: StudySource): Boolean {
    val sourceUri = source.docUri?.trim().orEmpty()
    val notebookUri = notebook.sourceDocumentUri.trim()
    val explicitLinks = notebook.pages.mapNotNull { it.sourceLink?.normalized() }
    val explicitUris = explicitLinks.map { it.uri }.filter { it.isNotBlank() }.distinct()
    val notebookUris = (listOf(notebookUri) + explicitUris).filter { it.isNotBlank() }.distinct()
    if (notebookUris.size > 1) return false

    val sourceDocId = source.docId?.trim()?.takeIf { it.isNotEmpty() }
    val explicitDocIds = explicitLinks.mapNotNull { it.docId }.distinct()
    if (explicitDocIds.size > 1) return false
    if (!sourceDocId.isNullOrBlank() && explicitDocIds.any { it != sourceDocId }) return false

    val sourceFingerprint = source.contentFingerprint?.trim()?.takeIf { it.isNotEmpty() }
    val explicitFingerprints = explicitLinks.mapNotNull { it.fingerprint }.distinct()
    if (explicitFingerprints.size > 1) return false
    if (!sourceFingerprint.isNullOrBlank() && explicitFingerprints.any { it != sourceFingerprint }) {
        return false
    }

    if (sourceUri.isNotBlank()) {
        if (notebookUris.size == 1) return notebookUris.single() == sourceUri
        if (explicitDocIds.isNotEmpty() && sourceDocId == null) return false
        if (explicitFingerprints.isNotEmpty() && sourceFingerprint == null) return false
    } else {
        if (notebookUris.isNotEmpty()) {
            return !sourceDocId.isNullOrBlank() && explicitDocIds.singleOrNull() == sourceDocId
        }
        if (explicitDocIds.isNotEmpty() && sourceDocId == null) return false
        if (explicitFingerprints.isNotEmpty() && sourceFingerprint == null) return false
    }

    if (!sourceDocId.isNullOrBlank() && explicitDocIds.singleOrNull() == sourceDocId) return true

    // Only a URI-less legacy notebook may use its display name/title as a fallback.
    if (notebookUri.isNotBlank() || explicitUris.isNotEmpty()) return false
    val docName = source.docName.trim()
    if (docName.isEmpty()) return false
    return notebook.sourceDocumentName.trim().equals(docName, ignoreCase = true) ||
        notebook.title.trim().equals(StudyNoteBuilder.suggestedTitle(source), ignoreCase = true)
}
