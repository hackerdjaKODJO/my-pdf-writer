package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFormField
import com.hackerdjakodjo.mypdfwriter.pdf.PdfMetadata
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import com.hackerdjakodjo.mypdfwriter.pdf.PdfRange
import com.hackerdjakodjo.mypdfwriter.pdf.PdfShare
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

data class ToolsUiState(
    val busy: Boolean = false,
    val label: String? = null,
    val message: String? = null,
    val sourceName: String = "",
    val sourceFile: File? = null,
    val pageCount: Int = 0,
    val metadata: PdfMetadata? = null,
    val outline: List<PdfOutlineEntry>? = null,
    val forms: List<PdfFormField>? = null,
    val lastOutput: Uri? = null,
)

/**
 * One-shot document tools. Everything runs on local files; the only Android
 * integration is SAF (for picking and writing) and the PdfRenderer.
 */
class ToolsViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val tempDir = File(application.cacheDir, "tools").apply { mkdirs() }
    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

    fun clearMessage() = _state.update { it.copy(message = null, lastOutput = null) }

    fun selectSource(uri: Uri) {
        viewModelScope.launch {
            runTool("PDF를 읽는 중…") {
                val name = queryName(uri)
                val file = copyToTemp(uri, name)
                val pages = PdfTools.pageCount(file)
                _state.update {
                    it.copy(sourceName = name, sourceFile = file, pageCount = pages, metadata = null, outline = null, forms = null)
                }
                "‘$name’ · ${pages}쪽"
            }
        }
    }

    /** Used when the viewer already has a working PDF open. */
    fun selectSource(file: File, name: String = file.name) {
        viewModelScope.launch {
            runTool("PDF를 읽는 중…") {
                val pages = PdfTools.pageCount(file)
                _state.update {
                    it.copy(
                        sourceName = name.ifBlank { file.name },
                        sourceFile = file,
                        pageCount = pages,
                        metadata = null,
                        outline = null,
                        forms = null,
                    )
                }
                "‘${name.ifBlank { file.name }}’ · ${pages}쪽"
            }
        }
    }

    fun loadMetadata() {
        val file = _state.value.sourceFile ?: return
        viewModelScope.launch {
            runTool("문서 정보를 읽는 중…") {
                val meta = PdfTools.metadata(file)
                _state.update { it.copy(metadata = meta) }
                null
            }
        }
    }

    fun loadOutline() {
        val file = _state.value.sourceFile ?: return
        viewModelScope.launch {
            runTool("목차를 읽는 중…") {
                val outline = PdfTools.outline(file)
                _state.update { it.copy(outline = outline) }
                if (outline.isEmpty()) "이 PDF에는 목차가 없습니다." else "목차 ${outline.size}개"
            }
        }
    }

    fun loadForms() {
        val file = _state.value.sourceFile ?: return
        viewModelScope.launch {
            runTool("양식을 읽는 중…") {
                val forms = PdfTools.forms(file)
                _state.update { it.copy(forms = forms) }
                if (forms.isEmpty()) "입력 가능한 AcroForm 양식이 없습니다." else "양식 ${forms.size}개"
            }
        }
    }

    fun merge(uris: List<Uri>, out: Uri) {
        if (uris.size < 2) {
            _state.update { it.copy(message = "PDF를 2개 이상 골라주세요.") }
            return
        }
        viewModelScope.launch {
            runTool("PDF를 합치는 중…") {
                val files = uris.mapIndexed { index, uri -> copyToTemp(uri, "merge-${index + 1}.pdf") }
                writeTo(out) { PdfTools.merge(files, it) }
                _state.update { it.copy(lastOutput = out) }
                "PDF ${files.size}개를 합쳤습니다."
            }
        }
    }

    fun imagesToPdf(uris: List<Uri>, out: Uri) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runTool("이미지를 PDF로 만드는 중…") {
                val files = uris.mapIndexed { index, uri -> copyToTemp(uri, "image-${index + 1}") }
                writeTo(out) { PdfTools.imagesToPdf(files, it) }
                _state.update { it.copy(lastOutput = out) }
                "이미지 ${files.size}장을 PDF로 만들었습니다."
            }
        }
    }

    fun extract(source: File, pages: List<Int>, out: Uri) {
        if (pages.isEmpty()) {
            _state.update { it.copy(message = "페이지 범위를 입력해주세요.") }
            return
        }
        viewModelScope.launch {
            runTool("페이지를 추출하는 중…") {
                writeTo(out) { PdfTools.extract(source, pages, it) }
                _state.update { it.copy(lastOutput = out) }
                "페이지 ${pages.size}장을 추출했습니다."
            }
        }
    }

    fun split(source: File, every: Int, tree: Uri) {
        if (every < 1) return
        viewModelScope.launch {
            runTool("문서를 나누는 중…") {
                val pageCount = PdfTools.pageCount(source)
                val chunks = PdfRange.chunks(pageCount, every)
                val outDir = File(tempDir, "split-${System.currentTimeMillis()}").apply { mkdirs() }
                val files = PdfTools.split(source, chunks, outDir, PdfShare.safeName(source.name, "").removeSuffix(".pdf"))
                val treeDir = DocumentFile.fromTreeUri(getApplication(), tree)
                    ?: error("저장 폴더를 열 수 없습니다.")
                files.forEach { file ->
                    val doc = treeDir.createFile("application/pdf", file.name)
                        ?: error("파일을 만들 수 없습니다: ${file.name}")
                    resolver.openOutputStream(doc.uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("출력 파일을 열 수 없습니다.")
                }
                "PDF ${files.size}개로 나눴습니다."
            }
        }
    }

    fun extractText(source: File, out: Uri) {
        viewModelScope.launch {
            runTool("텍스트를 추출하는 중…") {
                val text = PdfTools.extractText(source)
                resolver.openOutputStream(out, "wt")?.use { output ->
                    output.writer(Charsets.UTF_8).use { it.write(text) }
                } ?: error("출력 파일을 열 수 없습니다.")
                _state.update { it.copy(lastOutput = out) }
                if (text.isBlank()) "텍스트가 없습니다. 스캔 PDF일 수 있습니다." else "텍스트를 저장했습니다."
            }
        }
    }

    fun optimize(source: File, out: Uri) {
        viewModelScope.launch {
            runTool("PDF를 최적화하는 중…") {
                writeTo(out) { PdfTools.optimize(source, it) }
                _state.update { it.copy(lastOutput = out) }
                "최적화한 PDF를 저장했습니다."
            }
        }
    }

    fun protect(source: File, userPassword: String, ownerPassword: String, out: Uri) {
        if (userPassword.isBlank()) {
            _state.update { it.copy(message = "열기 암호를 입력해주세요.") }
            return
        }
        viewModelScope.launch {
            runTool("암호를 설정하는 중…") {
                writeTo(out) {
                    PdfTools.protect(
                        source = source,
                        userPassword = userPassword,
                        ownerPassword = ownerPassword.ifBlank { userPassword },
                        out = it,
                    )
                }
                // Opening a password-protected result needs the password first.
                _state.update { it.copy(lastOutput = null) }
                "암호를 설정했습니다."
            }
        }
    }

    fun unlock(source: File, password: String, out: Uri) {
        viewModelScope.launch {
            runTool("암호를 확인하는 중…") {
                writeTo(out) { PdfTools.unlock(source, password, it) }
                _state.update { it.copy(lastOutput = out) }
                "암호를 해제한 PDF를 저장했습니다."
            }
        }
    }

    fun setMetadata(source: File, meta: PdfMetadata, out: Uri) {
        viewModelScope.launch {
            runTool("문서 정보를 저장하는 중…") {
                writeTo(out) { PdfTools.setMetadata(source, meta, it) }
                _state.update { it.copy(lastOutput = out) }
                "문서 정보를 저장했습니다."
            }
        }
    }

    fun fillForms(source: File, values: Map<String, String>, checks: Map<String, Boolean>, out: Uri) {
        viewModelScope.launch {
            runTool("양식을 저장하는 중…") {
                writeTo(out) { PdfTools.fillForms(source, values, checks, it) }
                _state.update { it.copy(lastOutput = out) }
                "양식을 채운 PDF를 저장했습니다."
            }
        }
    }

    fun pdfToImages(source: File, tree: Uri, scale: Float = 2f) {
        viewModelScope.launch {
            runTool("페이지를 이미지로 만드는 중…") {
                val treeDir = DocumentFile.fromTreeUri(getApplication(), tree)
                    ?: error("저장 폴더를 열 수 없습니다.")
                var count = 0
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val width = (page.width * scale).toInt().coerceIn(320, 2400)
                                val height = (page.height * scale).toInt().coerceIn(320, 3200)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val name = "page-%03d.png".format(index + 1)
                                val doc = treeDir.createFile("image/png", name)
                                    ?: error("이미지 파일을 만들 수 없습니다.")
                                resolver.openOutputStream(doc.uri)?.use { output ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                                } ?: error("출력 파일을 열 수 없습니다.")
                                bitmap.recycle()
                                count++
                            }
                        }
                    }
                }
                "PNG ${count}장을 저장했습니다."
            }
        }
    }

    fun print(source: File) = PdfShare.printPdf(getApplication(), source, source.nameWithoutExtension)

    fun share(source: File) {
        viewModelScope.launch {
            runTool(null) {
                PdfShare.sharePdf(getApplication(), source)
                null
            }
        }
    }

    private suspend fun runTool(label: String?, block: suspend () -> String?) {
        _state.update { it.copy(busy = true, label = label, message = null) }
        runCatching { block() }
            .onSuccess { message ->
                _state.update { it.copy(busy = false, label = null, message = message) }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        busy = false,
                        label = null,
                        message = error.message ?: "작업을 완료하지 못했습니다.",
                    )
                }
            }
    }

    private suspend fun copyToTemp(uri: Uri, name: String): File = withContext(Dispatchers.IO) {
        val safe = PdfShare.safeName(name, "")
        val target = File(tempDir, "${System.nanoTime()}-$safe")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("파일을 읽을 수 없습니다: $name")
        target
    }

    private suspend fun writeTo(uri: Uri, block: suspend (OutputStream) -> Unit) = withContext(Dispatchers.IO) {
        val output = resolver.openOutputStream(uri, "wt") ?: error("출력 파일을 열 수 없습니다.")
        output.use { stream -> block(stream) }
    }

    private fun queryName(uri: Uri): String {
        var name = "document.pdf"
        runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.let { name = it }
                    }
                }
        }
        return name
    }
}
