package com.hackerdjakodjo.mypdfwriter.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.hackerdjakodjo.mypdfwriter.pdf.PdfTextExtractor
import com.hackerdjakodjo.mypdfwriter.pdf.TextActions
import com.hackerdjakodjo.mypdfwriter.pdf.TranslateTarget
import kotlinx.coroutines.CancellationException
import java.io.File
import kotlin.math.roundToInt

/**
 * Offline text-reflow reader for a PDF.
 *
 * The screen owns only its extraction and presentation state. It never edits
 * the PDF or the viewer: [onOpenPage] hands a source page index back to the
 * caller, and [onBack] returns to the viewer. Typography is intentionally
 * local to this screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflowReaderScreen(
    sourceFile: File,
    docName: String,
    initialPage: Int,
    /** Screen dimming factor in 0.4..1.0; 1f draws no scrim. */
    brightness: Float = 1f,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var pages by remember(sourceFile) { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember(sourceFile) { mutableStateOf(true) }
    var failure by remember(sourceFile) { mutableStateOf<String?>(null) }
    var pageIndex by remember(sourceFile) { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var retry by remember(sourceFile) { mutableIntStateOf(0) }

    var showJump by remember { mutableStateOf(false) }
    var showTranslate by remember { mutableStateOf(false) }
    var showReadAloud by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf(ReflowSettings()) }

    val pageCount = pages.size
    val pageText = pages.getOrNull(pageIndex).orEmpty()
    val palette = reflowPalette(settings.theme)
    val normalized = settings.normalized()

    ReaderSystemBars(dark = settings.theme == ReflowTheme.DARK)

    LaunchedEffect(sourceFile, retry) {
        loading = true
        failure = null
        pages = emptyList()
        try {
            val extracted = PdfTextExtractor.pages(sourceFile)
            pages = extracted
            pageIndex = if (extracted.isEmpty()) 0 else initialPage.coerceIn(0, extracted.lastIndex)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error.message?.takeIf { it.isNotBlank() }
                ?: "PDF에서 텍스트를 읽지 못했습니다."
        }
        loading = false
    }

    LaunchedEffect(pageIndex) {
        scrollState.scrollTo(0)
    }

    BackHandler(enabled = !showJump && !showTranslate && !showReadAloud) { onBack() }

    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            docName.ifBlank { "PDF" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                loading -> "불러오는 중"
                                failure != null -> "—"
                                else -> formatPageCounter(pageIndex, pageCount)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.muted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showReadAloud = true },
                        enabled = pageCount > 0 && !loading && failure == null,
                    ) {
                        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = "소리 내어 읽기")
                    }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            enabled = !loading && failure == null,
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "더보기: $docName")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("소리 내어 읽기") },
                                leadingIcon = {
                                    Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null)
                                },
                                enabled = pageCount > 0,
                                onClick = {
                                    showMenu = false
                                    showReadAloud = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("페이지 텍스트 복사") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                enabled = pageText.isNotBlank(),
                                onClick = {
                                    showMenu = false
                                    copyPageText(context, pageText)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Google 번역") },
                                leadingIcon = { Icon(Icons.Outlined.Translate, contentDescription = null) },
                                enabled = pageText.isNotBlank(),
                                onClick = {
                                    showMenu = false
                                    showTranslate = true
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("PDF 원본 페이지 열기") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenPage(pageIndex.coerceAtLeast(0))
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (showSettings) "읽기 설정 닫기" else "읽기 설정 열기")
                                },
                                leadingIcon = { Icon(Icons.Outlined.FormatSize, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showSettings = !showSettings
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.background,
                    navigationIconContentColor = palette.foreground,
                    titleContentColor = palette.foreground,
                    actionIconContentColor = palette.foreground,
                ),
            )
        },
        bottomBar = {
            ReflowBottomBar(
                pageIndex = pageIndex,
                pageCount = pageCount,
                settings = settings,
                palette = palette,
                settingsOpen = showSettings,
                enabled = !loading && failure == null && pageCount > 0,
                onPrevious = { pageIndex = stepPageIndex(pageIndex, -1, pageCount) },
                onNext = { pageIndex = stepPageIndex(pageIndex, 1, pageCount) },
                onJump = { showJump = true },
                onToggleSettings = { showSettings = !showSettings },
                onSettingsChange = {
                    settings = it.normalized()
                },
            )
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(palette.background),
        ) {
            when {
                loading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = palette.accent)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "페이지 텍스트를 불러오는 중…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                        )
                    }
                }

                failure != null -> {
                    ReaderMessage(
                        palette = palette,
                        title = "텍스트를 읽을 수 없습니다",
                        body = "암호가 걸렸거나 손상된 PDF일 수 있습니다. " +
                            "원본 PDF 화면에서는 계속 볼 수 있습니다.",
                        detail = failure,
                        primaryLabel = "PDF 원본 페이지 열기",
                        onPrimary = { onOpenPage(initialPage.coerceAtLeast(0)) },
                        secondaryLabel = "다시 시도",
                        onSecondary = { retry++ },
                    )
                }

                pages.isEmpty() -> {
                    ReaderMessage(
                        palette = palette,
                        title = "텍스트를 찾지 못했습니다",
                        body = "스캔한 PDF이거나 이미지로만 구성된 페이지는 추출할 텍스트가 없을 수 있습니다. " +
                            "원본 페이지에서 계속 읽어 보세요.",
                        primaryLabel = "PDF 원본 페이지 열기",
                        onPrimary = { onOpenPage(initialPage.coerceAtLeast(0)) },
                        secondaryLabel = "다시 시도",
                        onSecondary = { retry++ },
                    )
                }

                pageText.isBlank() -> {
                    ReaderMessage(
                        palette = palette,
                        title = "이 페이지에는 텍스트가 없습니다",
                        body = "스캔 이미지이거나 텍스트가 없는 페이지입니다. " +
                            "이전/다음 페이지 이동은 그대로 사용할 수 있습니다.",
                        primaryLabel = "PDF 원본 페이지 열기",
                        onPrimary = { onOpenPage(pageIndex) },
                    )
                }

                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SelectionContainer {
                            Text(
                                text = pageText,
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxWidth(),
                                style = TextStyle(
                                    fontSize = normalized.fontSizeSp.sp,
                                    lineHeight = normalized.lineHeightSp.sp,
                                    color = palette.foreground,
                                    fontFamily = FontFamily.Serif,
                                    letterSpacing = 0.1.sp,
                                ),
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
            val dimAlpha = 1f - brightness.coerceIn(0.4f, 1f)
            if (dimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = dimAlpha)),
                )
            }
        }
    }

    if (showJump) {
        PageJumpDialog(
            initialPage = pageIndex,
            pageCount = pageCount,
            onDismiss = { showJump = false },
            onJump = { target ->
                pageIndex = target.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                showJump = false
            },
        )
    }

    if (showTranslate && pageText.isNotBlank()) {
        TranslatePageSheet(
            text = pageText,
            onDismiss = { showTranslate = false },
        )
    }

    if (showReadAloud && pages.isNotEmpty()) {
        ReadAloudSheet(
            pages = pages,
            initialPage = pageIndex,
            onPageChange = { target ->
                pageIndex = target.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
            },
            onDismiss = { showReadAloud = false },
        )
    }
}

@Composable
private fun ReflowBottomBar(
    pageIndex: Int,
    pageCount: Int,
    settings: ReflowSettings,
    palette: ReflowPalette,
    settingsOpen: Boolean,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJump: () -> Unit,
    onToggleSettings: () -> Unit,
    onSettingsChange: (ReflowSettings) -> Unit,
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = palette.muted,
        selectedContainerColor = palette.accent.copy(alpha = 0.18f),
        selectedLabelColor = palette.accent,
    )

    Surface(color = palette.panel, contentColor = palette.foreground) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = enabled && pageIndex > 0) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "이전 페이지")
                }
                TextButton(onClick = onJump, enabled = enabled && pageCount > 1) {
                    Text(
                        formatPageCounter(pageIndex, pageCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled && pageCount > 1) palette.foreground else palette.muted,
                    )
                }
                IconButton(onClick = onNext, enabled = enabled && pageIndex < pageCount - 1) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "다음 페이지")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "${settings.theme.label} · ${formatFontSize(settings.fontSizeSp)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleSettings) {
                    Icon(
                        Icons.Outlined.FormatSize,
                        contentDescription = if (settingsOpen) "읽기 설정 닫기" else "읽기 설정 열기",
                    )
                }
            }

            if (settingsOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "테마",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                    )
                    ReflowTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.theme == theme,
                            onClick = { onSettingsChange(settings.copy(theme = theme)) },
                            label = { Text(theme.label) },
                            colors = chipColors,
                        )
                    }
                }

                ReaderSlider(
                    label = "글자 ${formatFontSize(settings.fontSizeSp)}",
                    value = settings.fontSizeSp,
                    valueRange = ReflowSettings.MIN_FONT_SIZE_SP..ReflowSettings.MAX_FONT_SIZE_SP,
                    steps = ReflowSettings.FONT_SIZE_STEPS,
                    palette = palette,
                    onValueChange = { onSettingsChange(settings.copy(fontSizeSp = it)) },
                )
                ReaderSlider(
                    label = "줄 ${formatLineHeight(settings.lineHeightScale)}",
                    value = settings.lineHeightScale,
                    valueRange = ReflowSettings.MIN_LINE_HEIGHT_SCALE..ReflowSettings.MAX_LINE_HEIGHT_SCALE,
                    steps = ReflowSettings.LINE_HEIGHT_STEPS,
                    palette = palette,
                    onValueChange = { onSettingsChange(settings.copy(lineHeightScale = it)) },
                )
            }
        }
    }
}

@Composable
private fun ReaderSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    palette: ReflowPalette,
    onValueChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.muted,
            modifier = Modifier.width(88.dp),
            maxLines = 1,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.muted.copy(alpha = 0.28f),
            ),
        )
    }
}

@Composable
private fun PageJumpDialog(
    initialPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    var value by remember(initialPage, pageCount) {
        mutableStateOf(initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0)).toFloat())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("페이지 이동") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    formatPageCounter(value.roundToInt(), pageCount),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                if (pageCount > 1) {
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0f..(pageCount - 1).toFloat(),
                        steps = (pageCount - 2).coerceAtLeast(0),
                    )
                } else {
                    Text(
                        "1쪽뿐인 문서입니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onJump(value.roundToInt()) }) { Text("이동") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslatePageSheet(
    text: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var target by remember { mutableStateOf(TranslateTarget.KOREAN) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Google 번역",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            Text(
                "페이지 텍스트를 Google 번역 앱이나 브라우저에서 엽니다. " +
                    "이 앱은 인터넷에 연결하지 않습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text.trim(),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${text.length}자",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))
            Text("번역할 언어", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TranslateTarget.entries.forEach { option ->
                    FilterChip(
                        selected = target == option,
                        onClick = { target = option },
                        label = { Text(option.label) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    TextActions.translate(context, text, target)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Google 번역에서 열기")
            }
        }
    }
}

@Composable
private fun ReaderMessage(
    palette: ReflowPalette,
    title: String,
    body: String,
    detail: String? = null,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = palette.foreground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                textAlign = TextAlign.Center,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (primaryLabel != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onPrimary) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(primaryLabel)
                }
            }
            if (secondaryLabel != null) {
                TextButton(onClick = onSecondary) {
                    Text(secondaryLabel, color = palette.accent)
                }
            }
        }
    }
}

@Composable
private fun ReaderSystemBars(dark: Boolean) {
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            controller?.isAppearanceLightStatusBars = !systemDark
            controller?.isAppearanceLightNavigationBars = !systemDark
        }
    }
}

private data class ReflowPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val panel: Color,
    val accent: Color,
)

private fun reflowPalette(theme: ReflowTheme): ReflowPalette = when (theme) {
    ReflowTheme.PAPER -> ReflowPalette(
        background = Color(0xFFF7F4EC),
        foreground = Color(0xFF24231F),
        muted = Color(0xFF6B675E),
        panel = Color(0xFFEFEAE0),
        accent = Color(0xFF1F4E79),
    )

    ReflowTheme.SEPIA -> ReflowPalette(
        background = Color(0xFFF2E5CB),
        foreground = Color(0xFF46351F),
        muted = Color(0xFF7A674C),
        panel = Color(0xFFEADBBE),
        accent = Color(0xFF8B5A2B),
    )

    ReflowTheme.DARK -> ReflowPalette(
        background = Color(0xFF14181D),
        foreground = Color(0xFFD9DEE5),
        muted = Color(0xFF96A0AC),
        panel = Color(0xFF1D232A),
        accent = Color(0xFFA9C9E8),
    )
}

private fun copyPageText(context: Context, text: String) {
    val value = text.trim()
    if (value.isEmpty()) return

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
        Toast.makeText(context, "클립보드를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
        return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("PDF 페이지 텍스트", value))
    Toast.makeText(context, "이 페이지의 텍스트를 복사했습니다.", Toast.LENGTH_SHORT).show()
}
