package com.hackerdjakodjo.mypdfwriter.ui

/**
 * Pure, Android-free snapshot of the parts of an incoming Intent that can carry
 * a PDF URI. [com.hackerdjakodjo.mypdfwriter.MainActivity] fills this from the
 * real Intent; unit tests build it directly so import resolution needs no
 * framework mocking.
 */
internal data class ImportIntentSnapshot(
    val action: String?,
    val dataUri: String? = null,
    val streamUri: String? = null,
    val clipUris: List<String> = emptyList(),
)

// Mirrors the framework Intent action strings without depending on Android.
private const val ACTION_VIEW = "android.intent.action.VIEW"
private const val ACTION_SEND = "android.intent.action.SEND"

/**
 * Resolves the single PDF URI carried by an ACTION_VIEW or ACTION_SEND intent.
 * Priority is intent.data, then EXTRA_STREAM, then the first non-blank clip
 * item. Blank values are skipped and the winner is trimmed; a null result means
 * the incoming intent is not a PDF import.
 */
internal fun resolveImportUri(intent: ImportIntentSnapshot): String? {
    if (intent.action !in pdfImportActions) return null
    val candidates = listOfNotNull(intent.dataUri, intent.streamUri) + intent.clipUris
    return candidates.firstOrNull { it.isNotBlank() }?.trim()
}
