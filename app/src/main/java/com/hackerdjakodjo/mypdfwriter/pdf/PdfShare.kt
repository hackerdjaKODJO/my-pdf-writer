package com.hackerdjakodjo.mypdfwriter.pdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Share and print helpers. Both work on local files and never use the network. */
object PdfShare {

    suspend fun sharePdf(
        context: Context,
        source: File,
        displayName: String = source.name,
        chooserTitle: String = "PDF 공유",
    ) = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val target = File(dir, safeName(displayName, ".pdf"))
        source.copyTo(target, overwrite = true)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target,
        )
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(send, chooserTitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    suspend fun shareText(
        context: Context,
        text: String,
        subject: String = "My PDF Writer",
        chooserTitle: String = "텍스트 공유",
    ) = withContext(Dispatchers.Main) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun printPdf(context: Context, source: File, jobName: String = source.nameWithoutExtension) {
        val manager = context.getSystemService(PrintManager::class.java) ?: return
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print(jobName.ifBlank { "My PDF Writer" }, FilePrintAdapter(source), attributes)
    }

    private class FilePrintAdapter(private val source: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(source.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            try {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                FileOutputStream(destination.fileDescriptor).use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) {
                callback.onWriteFailed(error.message ?: "인쇄 파일을 만들 수 없습니다.")
            }
        }
    }

    fun safeName(name: String, extension: String): String {
        val base = name.substringBeforeLast('.').ifBlank { "document" }
        val safe = base.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
            .trim()
            .take(80)
            .ifBlank { "document" }
        return if (safe.lowercase().endsWith(extension)) safe else "$safe$extension"
    }
}
