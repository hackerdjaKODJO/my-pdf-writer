package com.hackerdjakodjo.mypdfwriter.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min

data class PdfMetadata(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val pageCount: Int = 0,
    val encrypted: Boolean = false,
)

data class PdfOutlineEntry(val title: String, val pageIndex: Int?, val level: Int)

enum class PdfFormFieldType { TEXT, CHECKBOX, RADIO, COMBO, LIST, SIGNATURE, UNKNOWN }

data class PdfFormField(
    val name: String,
    val type: PdfFormFieldType,
    val value: String,
    val options: List<String> = emptyList(),
    val readOnly: Boolean = false,
)

/**
 * Offline PDF operations backed by PDFBox-Android.
 *
 * Every function owns and closes its [PDDocument]s. Full saves are performed with
 * [PDDocument.save], which also closes the supplied [OutputStream].
 */
object PdfTools {

    private const val PASSWORD_REQUIRED = "암호가 필요합니다"
    private const val MAX_IMAGE_DIMENSION = 2400
    private const val JPEG_QUALITY = 0.88f

    suspend fun isEncrypted(source: File): Boolean = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(source, "").use { it.isEncrypted }
        } catch (_: InvalidPasswordException) {
            true
        }
    }

    suspend fun pageCount(source: File, password: String? = null): Int = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { it.numberOfPages }
    }

    suspend fun merge(inputs: List<File>, out: OutputStream) = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "병합할 PDF가 없습니다" }
        PDDocument().use { target ->
            for (file in inputs) {
                loadPdf(file).use { source ->
                    for (index in 0 until source.numberOfPages) {
                        target.importPage(source.getPage(index))
                    }
                }
            }
            require(target.numberOfPages > 0) { "병합할 페이지가 없습니다" }
            target.save(out)
        }
    }

    suspend fun extract(
        source: File,
        pageIndices: List<Int>,
        out: OutputStream,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        require(pageIndices.isNotEmpty()) { "추출할 페이지가 없습니다" }
        loadPdf(source, password).use { src ->
            val count = src.numberOfPages
            for (index in pageIndices) {
                require(index in 0 until count) { "페이지 범위를 벗어났습니다: ${index + 1}" }
            }
            PDDocument().use { target ->
                for (index in pageIndices) target.importPage(src.getPage(index))
                target.save(out)
            }
        }
    }

    suspend fun split(
        source: File,
        chunks: List<IntRange>,
        outDir: File,
        baseName: String,
    ): List<File> = withContext(Dispatchers.IO) {
        require(chunks.isNotEmpty()) { "분할 범위가 없습니다" }
        require(baseName.isNotBlank()) { "파일 이름이 필요합니다" }
        if (!outDir.isDirectory && !outDir.mkdirs()) {
            throw IllegalArgumentException("출력 폴더를 만들 수 없습니다")
        }

        loadPdf(source).use { src ->
            val count = src.numberOfPages
            for (range in chunks) {
                require(
                    !range.isEmpty() &&
                        range.first in 0 until count &&
                        range.last in 0 until count,
                ) { "페이지 범위를 벗어났습니다" }
            }
            chunks.mapIndexed { chunkIndex, range ->
                val target = File(outDir, "$baseName-${(chunkIndex + 1).toString().padStart(2, '0')}.pdf")
                PDDocument().use { part ->
                    for (index in range) part.importPage(src.getPage(index))
                    part.save(target)
                }
                target
            }
        }
    }

    suspend fun imagesToPdf(
        images: List<File>,
        out: OutputStream,
        marginPt: Float = 18f,
    ) = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "변환할 이미지가 없습니다" }
        require(marginPt >= 0f) { "여백은 0 이상이어야 합니다" }

        val pageWidth = PDRectangle.A4.width
        val pageHeight = PDRectangle.A4.height
        require(marginPt * 2 < min(pageWidth, pageHeight)) { "여백이 너무 큽니다" }

        PDDocument().use { doc ->
            for (file in images) {
                val bitmap = decodeSampled(file)
                try {
                    val page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    val image = createJpeg(doc, bitmap)
                    val scale = min(
                        (pageWidth - marginPt * 2) / bitmap.width,
                        (pageHeight - marginPt * 2) / bitmap.height,
                    )
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val x = (pageWidth - drawWidth) / 2f
                    val y = (pageHeight - drawHeight) / 2f
                    PDPageContentStream(doc, page).use { content ->
                        content.drawImage(image, x, y, drawWidth, drawHeight)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            doc.save(out)
        }
    }

    suspend fun extractText(source: File, password: String? = null): String = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { doc ->
            val count = doc.numberOfPages
            if (count == 0) return@use ""

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            val text = StringBuilder()
            for (page in 1..count) {
                if (text.isNotEmpty()) text.append("\n\n")
                text.append("--- ").append(page).append(" / ").append(count).append(" ---\n")
                stripper.startPage = page
                stripper.endPage = page
                text.append(runCatching { stripper.getText(doc) }.getOrDefault("").trimEnd())
            }
            text.toString()
        }
    }

    suspend fun metadata(source: File, password: String? = null): PdfMetadata = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { doc ->
            val info = doc.documentInformation
            PdfMetadata(
                title = info.title.orEmpty(),
                author = info.author.orEmpty(),
                subject = info.subject.orEmpty(),
                keywords = info.keywords.orEmpty(),
                creator = info.creator.orEmpty(),
                producer = info.producer.orEmpty(),
                pageCount = doc.numberOfPages,
                encrypted = doc.isEncrypted,
            )
        }
    }

    suspend fun setMetadata(
        source: File,
        meta: PdfMetadata,
        out: OutputStream,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { doc ->
            val info = doc.documentInformation
            info.title = meta.title
            info.author = meta.author
            info.subject = meta.subject
            info.keywords = meta.keywords
            info.creator = meta.creator
            info.producer = meta.producer
            // A loaded encrypted document keeps its encryption dictionary; its
            // password cannot be re-applied without the owner password.
            doc.setAllSecurityToBeRemoved(true)
            doc.save(out)
        }
    }

    suspend fun outline(source: File, password: String? = null): List<PdfOutlineEntry> =
        withContext(Dispatchers.IO) {
            loadPdf(source, password).use { doc ->
                val root = doc.documentCatalog.documentOutline ?: return@use emptyList()
                val entries = ArrayList<PdfOutlineEntry>()
                collectOutline(doc, root, 0, entries)
                entries
            }
        }

    suspend fun forms(source: File, password: String? = null): List<PdfFormField> =
        withContext(Dispatchers.IO) {
            loadPdf(source, password).use { doc ->
                val acroForm = doc.documentCatalog.acroForm ?: return@use emptyList()
                val result = ArrayList<PdfFormField>()
                val iterator = acroForm.fieldIterator
                while (iterator.hasNext()) {
                    val field = iterator.next()
                    runCatching { field.toPdfFormField() }.getOrNull()?.let(result::add)
                }
                result
            }
        }

    suspend fun fillForms(
        source: File,
        values: Map<String, String>,
        checks: Map<String, Boolean>,
        out: OutputStream,
        flatten: Boolean = true,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { doc ->
            val acroForm = doc.documentCatalog.acroForm
                ?: throw IllegalArgumentException("PDF에 채울 양식이 없습니다")
            if (acroForm.hasXFA()) throw IllegalArgumentException("XFA 양식은 지원하지 않습니다")

            val iterator = acroForm.fieldIterator
            while (iterator.hasNext()) {
                val field = iterator.next()
                val name = runCatching {
                    field.fullyQualifiedName ?: field.partialName
                }.getOrNull().orEmpty()
                if (name.isBlank()) continue
                runCatching { applyFieldValue(field, values[name], checks[name]) }
            }

            if (flatten) acroForm.flatten()
            doc.setAllSecurityToBeRemoved(true)
            doc.save(out)
        }
    }

    @Suppress("DEPRECATION")
    suspend fun protect(
        source: File,
        userPassword: String,
        ownerPassword: String,
        allowPrinting: Boolean = true,
        allowCopying: Boolean = true,
        out: OutputStream,
    ) = withContext(Dispatchers.IO) {
        require(userPassword.isNotBlank()) { "사용자 암호를 입력하세요" }
        require(ownerPassword.isNotBlank()) { "소유자 암호를 입력하세요" }

        loadPdf(source).use { doc ->
            val permission = AccessPermission().apply {
                setCanPrint(allowPrinting)
                setCanPrintDegraded(allowPrinting)
                setCanPrintFaithful(allowPrinting)
                setCanExtractContent(allowCopying)
                setCanExtractForAccessibility(allowCopying)
                setCanModify(false)
                setCanModifyAnnotations(false)
                setCanAssembleDocument(false)
                setCanFillInForm(false)
            }
            val policy = StandardProtectionPolicy(ownerPassword, userPassword, permission)
            policy.setPreferAES(true)
            try {
                policy.setEncryptionKeyLength(256)
            } catch (_: IllegalArgumentException) {
                policy.setEncryptionKeyLength(128)
            }
            doc.protect(policy)
            doc.save(out)
        }
    }

    suspend fun unlock(source: File, password: String, out: OutputStream) = withContext(Dispatchers.IO) {
        loadPdf(source, password).use { doc ->
            doc.setAllSecurityToBeRemoved(true)
            doc.save(out)
        }
    }

    suspend fun optimize(source: File, out: OutputStream, password: String? = null) =
        withContext(Dispatchers.IO) {
            loadPdf(source, password).use { doc ->
                doc.setDocumentInformation(PDDocumentInformation())
                for (index in 0 until doc.numberOfPages) {
                    doc.getPage(index).getCOSObject().removeItem(COSName.THUMB)
                }
                doc.setAllSecurityToBeRemoved(true)
                doc.save(out)
            }
        }

    private fun loadPdf(source: File, password: String? = null): PDDocument {
        if (!source.isFile) throw IllegalArgumentException("PDF 파일을 찾을 수 없습니다: ${source.name}")
        return try {
            PDDocument.load(source, password ?: "")
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException(PASSWORD_REQUIRED, e)
        }
    }

    private fun decodeSampled(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("이미지를 읽을 수 없습니다: ${file.name}")
        }

        var sampleSize = 1
        val longestSide = max(width, height)
        while (longestSide / sampleSize > MAX_IMAGE_DIMENSION) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw IllegalArgumentException("이미지를 읽을 수 없습니다: ${file.name}")
    }

    private fun createJpeg(doc: PDDocument, bitmap: Bitmap): PDImageXObject {
        if (!bitmap.hasAlpha()) {
            return JPEGFactory.createFromImage(doc, bitmap, JPEG_QUALITY)
        }
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(flattened).apply {
                drawColor(Color.WHITE)
                drawBitmap(bitmap, 0f, 0f, null)
            }
            return JPEGFactory.createFromImage(doc, flattened, JPEG_QUALITY)
        } finally {
            flattened.recycle()
        }
    }

    private fun collectOutline(
        doc: PDDocument,
        parent: PDOutlineNode,
        level: Int,
        out: MutableList<PdfOutlineEntry>,
    ) {
        var item: PDOutlineItem? = parent.firstChild
        while (item != null) {
            val title = runCatching { item.title }.getOrNull().orEmpty()
            val pageIndex = runCatching { resolvePageIndex(doc, item) }.getOrNull()
            if (pageIndex != null) out.add(PdfOutlineEntry(title, pageIndex, level))
            collectOutline(doc, item, level + 1, out)
            item = item.nextSibling
        }
    }

    private fun resolvePageIndex(doc: PDDocument, item: PDOutlineItem): Int? {
        val destination = item.destination ?: return null
        val pageDestination = when (destination) {
            is PDPageDestination -> destination
            is PDNamedDestination -> doc.documentCatalog.findNamedDestinationPage(destination)
            else -> null
        } ?: return null
        val page = pageDestination.page ?: return null
        return doc.pages.indexOf(page).takeIf { it in 0 until doc.numberOfPages }
    }

    private fun PDField.toPdfFormField(): PdfFormField {
        val type = when (this) {
            is PDTextField -> PdfFormFieldType.TEXT
            is PDCheckBox -> PdfFormFieldType.CHECKBOX
            is PDRadioButton -> PdfFormFieldType.RADIO
            is PDComboBox -> PdfFormFieldType.COMBO
            is PDListBox -> PdfFormFieldType.LIST
            is PDSignatureField -> PdfFormFieldType.SIGNATURE
            else -> PdfFormFieldType.UNKNOWN
        }
        val value = when (this) {
            is PDCheckBox -> if (isChecked) {
                valueAsString.ifBlank { onValue ?: "On" }
            } else {
                "Off"
            }
            is PDSignatureField -> signature?.name.orEmpty().ifBlank { valueAsString.orEmpty() }
            else -> valueAsString.orEmpty()
        }
        return PdfFormField(
            name = fullyQualifiedName?.takeIf { it.isNotBlank() } ?: partialName.orEmpty(),
            type = type,
            value = value,
            options = fieldOptions(),
            readOnly = isReadOnly,
        )
    }

    private fun PDField.fieldOptions(): List<String> = when (this) {
        is PDRadioButton -> (exportValues.orEmpty() + onValues.orEmpty())
            .filter { it.isNotBlank() }
            .distinct()
        is PDComboBox -> optionsDisplayValues.orEmpty()
            .ifEmpty { options.orEmpty() }
            .filter { it.isNotBlank() }
        is PDListBox -> optionsDisplayValues.orEmpty()
            .ifEmpty { options.orEmpty() }
            .filter { it.isNotBlank() }
        else -> emptyList()
    }

    private fun applyFieldValue(field: PDField, text: String?, checked: Boolean?) {
        when (field) {
            is PDTextField -> if (text != null) field.setValue(text)
            is PDCheckBox -> when (checked) {
                true -> field.check()
                false -> field.unCheck()
                null -> Unit
            }
            is PDRadioButton -> {
                if (checked == false) {
                    field.setValue("Off")
                } else if (checked == true || text != null) {
                    val selected = text?.takeIf { it.isNotBlank() }
                        ?: field.exportValues.orEmpty().firstOrNull { it.isNotBlank() }
                        ?: field.onValues.orEmpty().firstOrNull { it.isNotBlank() }
                    if (selected != null) field.setValue(selected)
                }
            }
            is PDComboBox -> if (text != null) field.setValue(text)
            is PDListBox -> if (text != null) field.setValue(text)
            else -> Unit
        }
    }
}
