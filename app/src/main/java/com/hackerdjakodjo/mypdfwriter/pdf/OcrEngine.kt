package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class OcrLanguage(val label: String) {
    KOREAN("한국어"),
    LATIN("English"),
}

data class OcrLine(val text: String, val box: Rect)

data class OcrResult(val lines: List<OcrLine>) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * On-device ML Kit text recognition. The Korean recognizer is bundled in the
 * APK and also covers Latin characters; the Latin recognizer is available for
 * English-only scans. No image ever leaves the device.
 */
object OcrEngine {

    suspend fun recognize(
        bitmap: Bitmap,
        language: OcrLanguage = OcrLanguage.KOREAN,
    ): OcrResult = withContext(Dispatchers.IO) {
        val recognizer = createRecognizer(language)
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.process(image).awaitResult()
            OcrResult(extractLines(text))
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private fun createRecognizer(language: OcrLanguage): TextRecognizer = when (language) {
        OcrLanguage.KOREAN -> TextRecognition.getClient(
            KoreanTextRecognizerOptions.Builder().build(),
        )
        OcrLanguage.LATIN -> TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS,
        )
    }

    private fun extractLines(text: Text): List<OcrLine> =
        text.textBlocks.flatMap { block -> block.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box)
            }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
