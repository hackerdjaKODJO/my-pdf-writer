package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextExtractor
import java.io.File

/** Extracts working-PDF page text before handing it to the TTS sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudLoaderSheet(
    workingFile: File,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pages by remember(workingFile) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(workingFile) {
        pages = PdfTextExtractor.pages(workingFile)
    }

    val loaded = pages
    if (loaded == null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    "읽을 텍스트를 준비하는 중…",
                    modifier = Modifier.padding(top = 56.dp),
                )
            }
        }
    } else {
        ReadAloudSheet(
            pages = loaded,
            initialPage = initialPage,
            onPageChange = onPageChange,
            onDismiss = onDismiss,
        )
    }
}
