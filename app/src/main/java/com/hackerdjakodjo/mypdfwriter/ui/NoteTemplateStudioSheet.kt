package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.CoverStyle
import com.hackerdjakodjo.mypdfwriter.data.CustomTemplateStore
import com.hackerdjakodjo.mypdfwriter.data.NotePage
import com.hackerdjakodjo.mypdfwriter.data.NotePaper
import com.hackerdjakodjo.mypdfwriter.data.Notebook
import com.hackerdjakodjo.mypdfwriter.data.PageOrientation
import com.hackerdjakodjo.mypdfwriter.data.PaperTemplate
import com.hackerdjakodjo.mypdfwriter.data.PaperTint
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlinx.coroutines.launch

/**
 * Original Template Studio for one notebook.
 *
 * Template, tint and orientation edit the current page or every page as one
 * notebook change. Cover colour/style are notebook-level and are saved in the
 * same change. The preview uses [NotePaper.reorient] and the real paper painter,
 * so the orientation warning matches what the editor and PDF export will show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTemplateStudioSheet(
    notebook: Notebook,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onApply: (NoteTemplateStudioChoice) -> Unit,
    templateStore: CustomTemplateStore? = null,
) {
    val pages = notebook.pages.ifEmpty { listOf(NotePage()) }
    val safeIndex = currentIndex.coerceIn(0, pages.lastIndex)
    val currentPage = pages[safeIndex]
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val store = remember(context, templateStore) {
        templateStore ?: CustomTemplateStore(context)
    }
    val scope = rememberCoroutineScope()

    var template by remember(notebook.id, currentPage.id) { mutableStateOf(currentPage.template) }
    var tint by remember(notebook.id, currentPage.id) { mutableStateOf(currentPage.tint) }
    var orientation by remember(notebook.id, currentPage.id) {
        mutableStateOf(currentPage.orientation)
    }
    var applyToAll by remember(notebook.id) { mutableStateOf(false) }
    var coverColor by remember(notebook.id) { mutableStateOf(notebook.coverColor) }
    var coverStyle by remember(notebook.id) { mutableStateOf(notebook.coverStyle) }
    var customBackground by remember(notebook.id, currentPage.id) {
        mutableStateOf(currentPage.customBackground)
    }
    var customBackgroundName by remember(notebook.id, currentPage.id) {
        mutableStateOf(currentPage.customBackgroundName)
    }
    var importing by remember(notebook.id) { mutableStateOf(false) }
    var importError by remember(notebook.id) { mutableStateOf<String?>(null) }
    // Assets created in this sheet but not applied yet. They are deleted on cancel
    // or replacement so a dismissed import does not leave an orphan file behind.
    var sessionAssets by remember(notebook.id) { mutableStateOf<Set<String>>(emptySet()) }

    fun deleteSessionAsset(key: String?) {
        val safe = key ?: return
        if (safe in sessionAssets) {
            store.delete(safe)
            sessionAssets = sessionAssets - safe
        }
    }

    fun discardSessionAssets(keep: String? = null) {
        sessionAssets.filter { it != keep }.forEach(store::delete)
        sessionAssets = sessionAssets.filterTo(LinkedHashSet()) { it == keep }
    }

    fun acceptImportedAsset(key: String, name: String) {
        deleteSessionAsset(customBackground)
        customBackground = key
        customBackgroundName = name
        sessionAssets = sessionAssets + key
        importError = null
    }

    fun importFrom(uri: android.net.Uri, pdf: Boolean) {
        importError = null
        importing = true
        scope.launch {
            val result = runCatching {
                if (pdf) store.importPdfFirstPage(uri) else store.importImage(uri)
            }
            importing = false
            result.onSuccess { asset ->
                acceptImportedAsset(asset.key, asset.displayName)
            }.onFailure { error ->
                importError = error.message ?: "배경을 가져오지 못했습니다."
            }
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { importFrom(it, pdf = false) } }
    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { importFrom(it, pdf = true) } }

    val closeSheet: () -> Unit = {
        discardSessionAssets()
        onDismiss()
    }

    val choice = NoteTemplateStudioChoice(
        template = template,
        tint = tint,
        orientation = orientation,
        applyToAllPages = applyToAll,
        coverColor = coverColor,
        coverStyle = coverStyle,
        customBackground = customBackground,
        customBackgroundName = customBackgroundName,
    )
    val previewPage = remember(
        currentPage,
        template,
        tint,
        orientation,
        customBackground,
        customBackgroundName,
    ) {
        NoteTemplateStudio.styledPage(currentPage, choice)
    }
    val previewBackground = remember(customBackground) {
        customBackground?.let { store.loadThumbnail(it, 1024) }
    }
    val previewAspect = NotePaper.aspect(orientation)
    val cropRiskCount = NoteTemplateStudio.cropRiskCount(notebook, safeIndex, choice)
    val reorientsInk = NoteTemplateStudio.reorientsExistingInk(notebook, safeIndex, choice)
    val backgroundLabel = if (customBackground != null) {
        customBackgroundName.ifBlank { "커스텀 배경" }
    } else {
        template.label
    }

    ModalBottomSheet(onDismissRequest = closeSheet, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("템플릿 스튜디오", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${safeIndex + 1} / ${pages.size}쪽 · $backgroundLabel · " +
                                "${tint.label} · ${orientation.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = closeSheet,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("닫기")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StudioSectionLabel("미리보기", Modifier.weight(1f))
                        Text(
                            if (applyToAll) "${pages.size}쪽 전체" else "현재 페이지",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .height(220.dp)
                                .aspectRatio(previewAspect),
                        ) {
                            StudioPagePreview(
                                page = previewPage,
                                backgroundBitmap = previewBackground,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSectionLabel("커스텀 배경")
                    Text(
                        "내 사진(JPEG/PNG/WebP)이나 PDF 첫 페이지를 앱 안에 복사해 종이 " +
                            "배경으로 사용합니다. 원본 파일을 옮기거나 이름을 바꿔도 노트는 그대로 열립니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (customBackground != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Image,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        customBackgroundName.ifBlank { "커스텀 배경" },
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "현재 배경 · 페이지를 가득 채우는 cover",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        deleteSessionAsset(customBackground)
                                        customBackground = null
                                        customBackgroundName = ""
                                        importError = null
                                    },
                                    modifier = Modifier.heightIn(min = 44.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("제거")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = customBackgroundName,
                            onValueChange = { customBackgroundName = it.take(80) },
                            label = { Text("배경 이름") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "이미지는 비율을 유지한 채 중앙에서 잘려 페이지를 덮습니다. 투명 PNG는 " +
                                "아래 종이 색이 비치고, 선택한 종이 템플릿·색은 배경 아래에 유지됩니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "가져온 배경 없음 · 현재 종이 템플릿과 색이 그대로 보입니다.",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { pickImage.launch(arrayOf("image/*")) },
                            enabled = !importing,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        ) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("이미지")
                        }
                        OutlinedButton(
                            onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                            enabled = !importing,
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        ) {
                            Icon(
                                Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("PDF 첫 페이지")
                        }
                    }
                    if (importing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Text(
                                "배경을 가져오는 중…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    importError?.let { message ->
                        StudioNotice(
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                            icon = Icons.Filled.Warning,
                            title = "가져오지 못했습니다",
                            body = message,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSectionLabel("종이 템플릿")
                    PaperTemplate.entries.chunked(3).forEach { rowTemplates ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowTemplates.forEach { paper ->
                                StudioTemplateCard(
                                    template = paper,
                                    tint = tint,
                                    selected = template == paper,
                                    onClick = { template = paper },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - rowTemplates.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioSectionLabel("종이 색")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PaperTint.entries.forEach { paperTint ->
                            FilterChip(
                                selected = tint == paperTint,
                                onClick = { tint = paperTint },
                                label = { Text(paperTint.label) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(paperTint.paperColor))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                ),
                                                CircleShape,
                                            ),
                                    )
                                },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSectionLabel("용지 방향")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = orientation == PageOrientation.PORTRAIT,
                            onClick = { orientation = PageOrientation.PORTRAIT },
                            label = { Text("세로") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.CropPortrait,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                        FilterChip(
                            selected = orientation == PageOrientation.LANDSCAPE,
                            onClick = { orientation = PageOrientation.LANDSCAPE },
                            label = { Text("가로") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.CropLandscape,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                    Text(
                        "방향을 바꿔도 필기는 늘리거나 돌리지 않습니다. 실제 크기를 유지한 채 " +
                            "새 용지 안으로 옮기고, 용지 밖으로 나가는 부분은 화면과 PDF에서 잘립니다. " +
                            "적용은 실행 취소할 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cropRiskCount > 0) {
                        StudioNotice(
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                            icon = Icons.Filled.Warning,
                            title = "필기가 잘릴 수 있습니다",
                            body = "${cropRiskCount}쪽의 기존 필기가 ${orientation.label} 용지 밖으로 " +
                                "나갑니다. 미리보기를 확인하고 적용하세요.",
                        )
                    } else if (reorientsInk) {
                        StudioNotice(
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = Icons.Filled.Info,
                            title = "필기 재배치",
                            body = "기존 필기는 같은 크기로 새 용지 안에 들어갑니다. " +
                                "잘리는 필기는 없습니다.",
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioSectionLabel("표지")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioCoverPalette.take(4).forEach { swatch ->
                            StudioCoverSwatch(
                                color = swatch,
                                selected = coverColor == swatch,
                                onClick = { coverColor = swatch },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StudioCoverPalette.drop(4).forEach { swatch ->
                            StudioCoverSwatch(
                                color = swatch,
                                selected = coverColor == swatch,
                                onClick = { coverColor = swatch },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverStyle.entries.forEach { style ->
                            FilterChip(
                                selected = coverStyle == style,
                                onClick = { coverStyle = style },
                                label = { Text(studioCoverStyleLabel(style)) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioSectionLabel("적용 범위")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !applyToAll,
                            onClick = { applyToAll = false },
                            label = { Text("현재 페이지") },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                        FilterChip(
                            selected = applyToAll,
                            onClick = { applyToAll = true },
                            label = { Text("모든 페이지 (${pages.size})") },
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                    Text(
                        "표지 색과 무늬는 노트 전체에 적용됩니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = closeSheet,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("취소")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            discardSessionAssets(keep = customBackground)
                            onApply(choice)
                        },
                        enabled = !importing,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Text("적용")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StudioTemplateCard(
    template: PaperTemplate,
    tint: PaperTint,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NotePaper.PORTRAIT_ASPECT)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            drawIntoCanvas { canvas ->
                PaperBackground.draw(
                    canvas = canvas.nativeCanvas,
                    template = template,
                    width = size.width,
                    height = size.height,
                    dark = false,
                    tint = tint,
                )
            }
        }
        Text(
            template.label,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StudioPagePreview(
    page: NotePage,
    backgroundBitmap: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clipToBounds()) {
        drawIntoCanvas { canvas ->
            PaperBackground.draw(
                canvas = canvas.nativeCanvas,
                template = page.template,
                width = size.width,
                height = size.height,
                dark = false,
                tint = page.tint,
            )
            backgroundBitmap?.let { bitmap ->
                PaperBackground.drawCustomBackground(
                    canvas = canvas.nativeCanvas,
                    bitmap = bitmap,
                    width = size.width,
                    height = size.height,
                )
            }
            AnnotationPainter.draw(
                canvas = canvas.nativeCanvas,
                annotations = page.annotations,
                width = size.width,
                height = size.height,
            )
        }
    }
}

@Composable
private fun StudioNotice(
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
) {
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(body, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StudioCoverSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(color)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "선택된 표지 색",
                tint = if (swatch.luminance() > 0.55f) Color(0xFF1B1C19) else Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun studioCoverStyleLabel(style: CoverStyle): String = when (style) {
    CoverStyle.SOLID -> "단색"
    CoverStyle.GRID -> "모눈"
    CoverStyle.DOTS -> "점"
    CoverStyle.STRIPES -> "줄무늬"
}

/** Same original cover palette as the notebook create dialog. */
private val StudioCoverPalette: List<Int> = listOf(
    0xFFD8E6F3.toInt(),
    0xFFDCE8DC.toInt(),
    0xFFF6E3D4.toInt(),
    0xFFF3DCE2.toInt(),
    0xFFE3DFF1.toInt(),
    0xFFEDE6D2.toInt(),
    0xFFD7E7E4.toInt(),
    0xFFF3EBCF.toInt(),
)
