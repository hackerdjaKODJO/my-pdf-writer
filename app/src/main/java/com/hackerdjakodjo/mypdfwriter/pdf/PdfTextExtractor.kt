package com.hackerdjakodjo.mypdfwriter.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Page-by-page text extraction used by the reflow reader and read-aloud mode. */
object PdfTextExtractor {

    suspend fun pages(source: File): List<String> = withContext(Dispatchers.IO) {
        PDDocument.load(source).use { document ->
            val count = document.numberOfPages
            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            (1..count).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                runCatching { stripper.getText(document) }.getOrDefault("").trim()
            }
        }
    }
}
