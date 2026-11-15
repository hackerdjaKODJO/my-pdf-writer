package com.hackerdjakodjo.mypdfwriter.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFormField
import com.hackerdjakodjo.mypdfwriter.pdf.PdfFormFieldType
import com.hackerdjakodjo.mypdfwriter.pdf.PdfMetadata
import com.hackerdjakodjo.mypdfwriter.pdf.PdfRange
import java.io.File

private sealed interface PendingOutput {
    data class Extract(val source: File, val pages: List<Int>) : PendingOutput
    data class Split(val source: File, val every: Int) : PendingOutput
    data class Merge(val sources: List<Uri>) : PendingOutput
    data class ImagesToPdf(val images: List<Uri>) : PendingOutput
    data class ExtractText(val source: File) : PendingOutput
    data class Optimize(val source: File) : PendingOutput
    data class Protect(val source: File, val user: String, val owner: String) : PendingOutput
    data class Unlock(val source: File, val password: String) : PendingOutput
    data class SetMetadata(val source: File, val meta: PdfMetadata) : PendingOutput
    data class FillForms(val source: File, val values: Map<String, String>, val checks: Map<String, Boolean>) : PendingOutput
    data class Images(val source: File) : PendingOutput
}

private data class ToolItem(
    val title: String,
    val body: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onBack: () -> Unit,
    onOpen: (Uri) -> Unit,
    initialSource: File? = null,
    initialName: String = "",
) {
    val vm: ToolsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var pending by remember { mutableStateOf<PendingOutput?>(null) }
    var showExtract by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    var showProtect by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var showMetadata by remember { mutableStateOf(false) }
    var showForms by remember { mutableStateOf(false) }
    var rangeText by remember { mutableStateOf("1-3") }
    var everyText by remember { mutableStateOf("10") }
    var userPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var unlockPassword by remember { mutableStateOf("") }
    var metaTitle by remember { mutableStateOf("") }
    var metaAuthor by remember { mutableStateOf("") }
    var metaSubject by remember { mutableStateOf("") }
    var metaKeywords by remember { mutableStateOf("") }
    val formValues = remember { mutableStateMapOf<String, String>() }
    val formChecks = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(initialSource) {
        if (initialSource != null) vm.selectSource(initialSource, initialName)
    }

    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val out = uri ?: run { pending = null; return@rememberLauncherForActivityResult }
        when (val action = pending) {
            is PendingOutput.Extract -> vm.extract(action.source, action.pages, out)
            is PendingOutput.Split -> vm.split(action.source, action.every, out)
            is PendingOutput.Merge -> vm.merge(action.sources, out)
            is PendingOutput.ImagesToPdf -> vm.imagesToPdf(action.images, out)
            is PendingOutput.Optimize -> vm.optimize(action.source, out)
            is PendingOutput.Protect -> vm.protect(action.source, action.user, action.owner, out)
            is PendingOutput.Unlock -> vm.unlock(action.source, action.password, out)
            is PendingOutput.SetMetadata -> vm.setMetadata(action.source, action.meta, out)
            is PendingOutput.FillForms -> vm.fillForms(action.source, action.values, action.checks, out)
            is PendingOutput.ExtractText, is PendingOutput.Images, null -> Unit
        }
        pending = null
    }

    val createText = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val out = uri ?: run { pending = null; return@rememberLauncherForActivityResult }
        (pending as? PendingOutput.ExtractText)?.let { vm.extractText(it.source, out) }
        pending = null
    }

    val openTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            pending = null
            return@rememberLauncherForActivityResult
        }
        when (val action = pending) {
            is PendingOutput.Split -> vm.split(action.source, action.every, uri)
            is PendingOutput.Images -> vm.pdfToImages(action.source, uri)
            else -> Unit
        }
        pending = null
    }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::selectSource) }

    val pickMultiplePdfs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pending = PendingOutput.Merge(uris)
            createPdf.launch("merged.pdf")
        }
    }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            pending = PendingOutput.ImagesToPdf(uris)
            createPdf.launch("images.pdf")
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = message,
            actionLabel = if (state.lastOutput != null) "열기" else null,
        )
        if (result == SnackbarResult.ActionPerformed) {
            state.lastOutput?.let(onOpen)
        }
        vm.clearMessage()
    }

    LaunchedEffect(state.metadata) {
        state.metadata?.let {
            metaTitle = it.title
            metaAuthor = it.author
            metaSubject = it.subject
            metaKeywords = it.keywords
        }
    }

    LaunchedEffect(state.forms) {
        formValues.clear()
        formChecks.clear()
        state.forms?.forEach { field ->
            if (field.type == PdfFormFieldType.CHECKBOX) {
                formChecks[field.name] = field.value.isNotBlank() &&
                    !field.value.equals("Off", true) &&
                    !field.value.equals("false", true)
            } else {
                formValues[field.name] = field.value
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PDF 도구")
                        Text(
                            "오프라인 · 파일은 기기 안에서만",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { SourceCard(state, onPick = { pickPdf.launch(arrayOf("application/pdf")) }) }

                item {
                    ToolSection(
                        title = "새로 만들기",
                        items = listOf(
                            ToolItem("PDF 합치기", "여러 PDF를 한 파일로", Icons.Outlined.CallMerge, {
                                pickMultiplePdfs.launch(arrayOf("application/pdf"))
                            }),
                            ToolItem("이미지 → PDF", "사진을 페이지로", Icons.Outlined.AddPhotoAlternate, {
                                pickImages.launch(arrayOf("image/*"))
                            }),
                        ),
                    )
                }

                item {
                    val source = state.sourceFile
                    val enabled = source != null
                    ToolSection(
                        title = "선택한 PDF로",
                        items = listOf(
                            ToolItem("페이지 추출", "원하는 페이지만 새 PDF로", Icons.Outlined.FileDownload, {
                                if (source != null) showExtract = true
                            }, enabled),
                            ToolItem("문서 분할", "N쪽마다 파일 나누기", Icons.Outlined.CallSplit, {
                                if (source != null) showSplit = true
                            }, enabled),
                            ToolItem("PDF → 이미지", "모든 페이지를 PNG로", Icons.Outlined.Image, {
                                if (source != null) {
                                    pending = PendingOutput.Images(source)
                                    openTree.launch(null)
                                }
                            }, enabled),
                            ToolItem("텍스트 추출", "복사 가능한 TXT로", Icons.Outlined.TextSnippet, {
                                if (source != null) {
                                    pending = PendingOutput.ExtractText(source)
                                    createText.launch("document.txt")
                                }
                            }, enabled),
                            ToolItem("최적화", "메타데이터를 정리해 다시 저장", Icons.Outlined.Compress, {
                                if (source != null) {
                                    pending = PendingOutput.Optimize(source)
                                    createPdf.launch("optimized.pdf")
                                }
                            }, enabled),
                            ToolItem("문서 정보", "제목·작성자·페이지 수", Icons.Outlined.Info, {
                                if (source != null) {
                                    vm.loadMetadata()
                                    showMetadata = true
                                }
                            }, enabled),
                            ToolItem("암호 설정", "열기 암호로 보호", Icons.Outlined.Lock, {
                                if (source != null) showProtect = true
                            }, enabled),
                            ToolItem("암호 해제", "암호를 풀어 새 PDF로", Icons.Outlined.LockOpen, {
                                if (source != null) showUnlock = true
                            }, enabled),
                            ToolItem("양식 채우기", "AcroForm 입력 후 저장", Icons.Outlined.FactCheck, {
                                if (source != null) {
                                    vm.loadForms()
                                    showForms = true
                                }
                            }, enabled),
                            ToolItem("인쇄", "Android 인쇄로 보내기", Icons.Outlined.Print, {
                                source?.let(vm::print)
                            }, enabled),
                            ToolItem("공유", "PDF를 다른 앱으로", Icons.Outlined.Share, {
                                source?.let(vm::share)
                            }, enabled),
                        ),
                    )
                }

                item {
                    Text(
                        "도구는 선택한 파일을 임시로 읽고, 결과는 사용자가 고른 위치에만 저장합니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.busy) {
                Box(
                    Modifier.fillMaxSize().clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(12.dp))
                            Text(state.label ?: "처리 중…", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (showExtract) {
        RangeDialog(
            title = "페이지 추출",
            value = rangeText,
            hint = "예: 1-3,5 (총 ${state.pageCount}쪽)",
            onValueChange = { rangeText = it },
            onDismiss = { showExtract = false },
            onConfirm = {
                val source = state.sourceFile ?: return@RangeDialog
                val pages = PdfRange.parse(rangeText, state.pageCount)
                if (pages.isEmpty()) return@RangeDialog
                showExtract = false
                pending = PendingOutput.Extract(source, pages)
                createPdf.launch("extract.pdf")
            },
        )
    }

    if (showSplit) {
        RangeDialog(
            title = "문서 분할",
            value = everyText,
            hint = "몇 쪽마다 나눌까요?",
            numeric = true,
            onValueChange = { everyText = it.filter(Char::isDigit) },
            onDismiss = { showSplit = false },
            onConfirm = {
                val source = state.sourceFile ?: return@RangeDialog
                val every = everyText.toIntOrNull() ?: return@RangeDialog
                if (every < 1) return@RangeDialog
                showSplit = false
                pending = PendingOutput.Split(source, every)
                openTree.launch(null)
            },
        )
    }

    if (showProtect) {
        PasswordDialog(
            title = "암호 설정",
            user = userPassword,
            owner = ownerPassword,
            onUser = { userPassword = it },
            onOwner = { ownerPassword = it },
            onDismiss = { showProtect = false },
            onConfirm = {
                val source = state.sourceFile ?: return@PasswordDialog
                if (userPassword.isBlank()) return@PasswordDialog
                showProtect = false
                pending = PendingOutput.Protect(source, userPassword, ownerPassword.ifBlank { userPassword })
                createPdf.launch("protected.pdf")
            },
        )
    }

    if (showUnlock) {
        OnePasswordDialog(
            title = "암호 해제",
            value = unlockPassword,
            onValueChange = { unlockPassword = it },
            onDismiss = { showUnlock = false },
            onConfirm = {
                val source = state.sourceFile ?: return@OnePasswordDialog
                showUnlock = false
                pending = PendingOutput.Unlock(source, unlockPassword)
                createPdf.launch("unlocked.pdf")
            },
        )
    }

    if (showMetadata) {
        MetadataDialog(
            state.metadata,
            title = metaTitle,
            author = metaAuthor,
            subject = metaSubject,
            keywords = metaKeywords,
            onTitle = { metaTitle = it },
            onAuthor = { metaAuthor = it },
            onSubject = { metaSubject = it },
            onKeywords = { metaKeywords = it },
            onDismiss = { showMetadata = false },
            onSave = {
                val source = state.sourceFile ?: return@MetadataDialog
                val current = state.metadata ?: PdfMetadata()
                showMetadata = false
                pending = PendingOutput.SetMetadata(
                    source,
                    current.copy(
                        title = metaTitle,
                        author = metaAuthor,
                        subject = metaSubject,
                        keywords = metaKeywords,
                    ),
                )
                createPdf.launch("metadata.pdf")
            },
        )
    }

    if (showForms) {
        FormsDialog(
            fields = state.forms.orEmpty(),
            values = formValues,
            checks = formChecks,
            onDismiss = { showForms = false },
            onSave = {
                val source = state.sourceFile ?: return@FormsDialog
                showForms = false
                pending = PendingOutput.FillForms(source, formValues.toMap(), formChecks.toMap())
                createPdf.launch("filled.pdf")
            },
        )
    }
}

@Composable
private fun SourceCard(state: ToolsUiState, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.sourceName.isBlank()) "PDF를 선택하세요" else state.sourceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.pageCount > 0) "${state.pageCount}쪽 · 선택한 파일" else "도구를 쓰려면 먼저 PDF를 여세요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onPick) { Text(if (state.sourceFile == null) "선택" else "변경") }
        }
    }
}

@Composable
private fun ToolSection(title: String, items: List<ToolItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    ToolCard(item, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem, modifier: Modifier = Modifier) {
    Surface(
        onClick = item.onClick,
        enabled = item.enabled,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(item.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                item.body,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RangeDialog(
    title: String,
    value: String,
    hint: String,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(hint) },
                keyboardOptions = if (numeric) {
                    androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                } else {
                    androidx.compose.foundation.text.KeyboardOptions.Default
                },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("계속") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    user: String,
    owner: String,
    onUser: (String) -> Unit,
    onOwner: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = onUser,
                    label = { Text("열기 암호") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = onOwner,
                    label = { Text("소유자 암호 (비우면 동일)") },
                    singleLine = true,
                )
                Text(
                    "암호를 잊으면 파일을 열 수 없습니다. 안전한 곳에 보관하세요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun OnePasswordDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("PDF 암호") },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("열기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun MetadataDialog(
    metadata: PdfMetadata?,
    title: String,
    author: String,
    subject: String,
    keywords: String,
    onTitle: (String) -> Unit,
    onAuthor: (String) -> Unit,
    onSubject: (String) -> Unit,
    onKeywords: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문서 정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (metadata == null) "정보를 읽는 중…" else "${metadata.pageCount}쪽 · ${if (metadata.encrypted) "암호 보호됨" else "암호 없음"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = title, onValueChange = onTitle, label = { Text("제목") }, singleLine = true)
                OutlinedTextField(value = author, onValueChange = onAuthor, label = { Text("작성자") }, singleLine = true)
                OutlinedTextField(value = subject, onValueChange = onSubject, label = { Text("주제") }, singleLine = true)
                OutlinedTextField(value = keywords, onValueChange = onKeywords, label = { Text("키워드") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun FormsDialog(
    fields: List<PdfFormField>,
    values: MutableMap<String, String>,
    checks: MutableMap<String, Boolean>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("양식 채우기") },
        text = {
            if (fields.isEmpty()) {
                Text("입력 가능한 양식 필드가 없습니다.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(fields.size) { index ->
                        val field = fields[index]
                        when (field.type) {
                            PdfFormFieldType.CHECKBOX -> Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(field.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = checks[field.name] == true,
                                    onCheckedChange = { checks[field.name] = it },
                                )
                            }
                            PdfFormFieldType.SIGNATURE -> Text(
                                "${field.name} · 서명 필드는 앱에서 직접 채울 수 없습니다.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> OutlinedTextField(
                                value = values[field.name].orEmpty(),
                                onValueChange = { values[field.name] = it },
                                label = { Text(field.name) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = fields.isNotEmpty()) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
