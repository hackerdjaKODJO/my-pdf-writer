package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.CropPixelRect
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/**
 * Read-only two-page/four-page spread reader.
 *
 * [initialPage] and every callback index are zero-based page indices. Spreads are
 * aligned to zero-based even group starts, so the first pair is pages 1–2, the
 * second is 3–4, and a four-page grid is pages 1–4, 5–8, and so on. The spread
 * containing [initialPage] is shown first.
 *
 * Layout is width driven: at least 900 dp shows a 2×2 four-page grid, at least
 * 600 dp shows two pages side by side, and narrower phones keep a vertical
 * two-page pair. Pages are rendered independently through [loadPage] while
 * visible, carry no annotation or zoom chrome, and open the single-page viewer
 * through [onOpenPage] when tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadReaderScreen(
    title: String,
    pageCount: Int,
    initialPage: Int,
    /** Screen dimming factor in 0.4..1.0; 1f draws no scrim. */
    brightness: Float = 1f,
    /**
     * View-only crop margins in normalized page fractions. Each spread page
     * draws only its kept sub-rectangle; the source PDF and exports stay whole.
     * Defaults keep the uncropped path.
     */
    cropInsets: CropInsets = CropInsets.NONE,
    /** Mirror left/right margins on even (1-based) pages. */
    cropOddEven: Boolean = false,
    loadPage: suspend (Int) -> Bitmap?,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    val initialIndex = if (pageCount > 0) initialPage.coerceIn(0, pageCount - 1) else 0
    var focusPage by rememberSaveable(title, pageCount, initialIndex) {
        mutableStateOf(initialIndex)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = when {
            maxWidth >= 900.dp -> SpreadLayout.FOUR_PAGE
            maxWidth >= 600.dp -> SpreadLayout.SIDE_BY_SIDE
            else -> SpreadLayout.STACKED
        }
        val groupSize = if (layout == SpreadLayout.FOUR_PAGE) 4 else 2
        val safeFocus = if (pageCount > 0) focusPage.coerceIn(0, pageCount - 1) else 0
        val start = spreadStart(safeFocus, groupSize)
        val endExclusive = minOf(start + groupSize, pageCount)

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "뒤로",
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                title.ifBlank { "PDF" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (pageCount > 0) {
                                    "${groupSize}쪽 배치 · 전체 ${pageCount}쪽"
                                } else {
                                    "스프레드 보기"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            bottomBar = {
                if (pageCount > 0) {
                    SpreadNavigationBar(
                        start = start,
                        endExclusive = endExclusive,
                        pageCount = pageCount,
                        onPrevious = { focusPage = (start - 1).coerceAtLeast(0) },
                        onNext = { focusPage = endExclusive.coerceAtMost(pageCount - 1) },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
            ) {
                if (pageCount <= 0) {
                    EmptySpreadReader(onBack = onBack)
                } else {
                    SpreadPages(
                        layout = layout,
                        start = start,
                        endExclusive = endExclusive,
                        cropInsets = cropInsets,
                        cropOddEven = cropOddEven,
                        loadPage = loadPage,
                        onOpenPage = onOpenPage,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
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
    }
}

@Composable
private fun SpreadPages(
    layout: SpreadLayout,
    start: Int,
    endExclusive: Int,
    cropInsets: CropInsets,
    cropOddEven: Boolean,
    loadPage: suspend (Int) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (layout) {
        SpreadLayout.STACKED -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) { offset ->
                SpreadPageCell(
                    index = start + offset,
                    endExclusive = endExclusive,
                    cropInsets = cropInsets,
                    cropOddEven = cropOddEven,
                    loadPage = loadPage,
                    onOpenPage = onOpenPage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }

        SpreadLayout.SIDE_BY_SIDE -> Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) { offset ->
                SpreadPageCell(
                    index = start + offset,
                    endExclusive = endExclusive,
                    cropInsets = cropInsets,
                    cropOddEven = cropOddEven,
                    loadPage = loadPage,
                    onOpenPage = onOpenPage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        SpreadLayout.FOUR_PAGE -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(2) { row ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(2) { column ->
                        SpreadPageCell(
                            index = start + row * 2 + column,
                            endExclusive = endExclusive,
                            cropInsets = cropInsets,
                            cropOddEven = cropOddEven,
                            loadPage = loadPage,
                            onOpenPage = onOpenPage,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpreadPageCell(
    index: Int,
    endExclusive: Int,
    cropInsets: CropInsets,
    cropOddEven: Boolean,
    loadPage: suspend (Int) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (index >= endExclusive) {
        // A final odd/partial spread keeps its empty slot so the remaining page
        // stays aligned with the reading order.
        Spacer(modifier)
        return
    }

    SpreadPageCard(
        index = index,
        cropInsets = cropInsets,
        cropOddEven = cropOddEven,
        loadPage = loadPage,
        onOpenPage = onOpenPage,
        modifier = modifier,
    )
}

@Composable
private fun SpreadPageCard(
    index: Int,
    cropInsets: CropInsets,
    cropOddEven: Boolean,
    loadPage: suspend (Int) -> Bitmap?,
    onOpenPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var attempt by remember(index) { mutableIntStateOf(0) }
    val currentLoadPage = rememberUpdatedState(loadPage)
    val page by produceState<SpreadPageState>(
        initialValue = SpreadPageState.Loading,
        index,
        attempt,
    ) {
        value = SpreadPageState.Loading
        val bitmap = try {
            currentLoadPage.value(index)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        value = if (bitmap != null) SpreadPageState.Ready(bitmap) else SpreadPageState.Failed
    }

    // Same view-only crop policy as the continuous reader: size the cell to the
    // kept bitmap window so the sub-rectangle fills it exactly.
    val visible = PdfCrop.visibleRect(PdfCrop.cropForPage(cropInsets, index, cropOddEven))
    val cropSrc: (Bitmap) -> CropPixelRect = { bitmap ->
        PdfCrop.cropPixelRect(visible, bitmap.width, bitmap.height)
    }
    val aspectRatio = when (val state = page) {
        is SpreadPageState.Ready -> {
            val src = cropSrc(state.bitmap)
            src.width.toFloat() / src.height.coerceAtLeast(1).toFloat()
        }

        else -> DEFAULT_SPREAD_PAGE_ASPECT
    }.coerceIn(0.15f, 10f)

    BoxWithConstraints(
        modifier = modifier.padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val fitsByWidth = maxWidth / aspectRatio <= maxHeight
        val cardModifier = if (fitsByWidth) {
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
        } else {
            Modifier
                .fillMaxHeight()
                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = true)
        }

        Surface(
            onClick = { onOpenPage(index) },
            modifier = cardModifier,
            shape = RoundedCornerShape(2.dp),
            color = Color.White,
            contentColor = Color(0xFF3C4043),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
            shadowElevation = 6.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val state = page) {
                    is SpreadPageState.Ready -> {
                        val bitmap = state.bitmap
                        val src = cropSrc(bitmap)
                        Canvas(
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = "${index + 1}쪽 페이지",
                        ) {
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawBitmap(
                                    bitmap,
                                    Rect(
                                        src.left,
                                        src.top,
                                        src.left + src.width,
                                        src.top + src.height,
                                    ),
                                    Rect(
                                        0,
                                        0,
                                        size.width.toInt().coerceAtLeast(1),
                                        size.height.toInt().coerceAtLeast(1),
                                    ),
                                    Paint(Paint.FILTER_BITMAP_FLAG),
                                )
                            }
                        }
                    }

                    SpreadPageState.Loading -> SpreadLoadingPage()
                    SpreadPageState.Failed -> SpreadFailedPage(onRetry = { attempt++ })
                }

                SpreadPageNumber(
                    page = index + 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SpreadLoadingPage() {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = Color(0xFF3F5F7F),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "페이지를 불러오는 중…",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6B7075),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun SpreadFailedPage(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = Color(0xFFB3261E),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "페이지를 불러오지 못했습니다",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF3C4043),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            onClick = onRetry,
            shape = RoundedCornerShape(50),
            color = Color(0xFFE8EEF5),
            contentColor = Color(0xFF1F4E79),
        ) {
            Text(
                "다시 시도",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SpreadPageNumber(
    page: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 1.dp,
    ) {
        Text(
            "${page}쪽",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun SpreadNavigationBar(
    start: Int,
    endExclusive: Int,
    pageCount: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val hasPrevious = start > 0
    val hasNext = endExclusive < pageCount

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "이전 스프레드",
                )
            }
            Text(
                "현재 ${start + 1}–${endExclusive} / ${pageCount}쪽",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            IconButton(onClick = onNext, enabled = hasNext) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "다음 스프레드",
                )
            }
        }
    }
}

@Composable
private fun EmptySpreadReader(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "읽을 페이지가 없습니다",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "PDF를 다시 열거나 페이지를 추가한 뒤 이용해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) {
            Text("돌아가기")
        }
    }
}

private fun spreadStart(page: Int, groupSize: Int): Int {
    if (groupSize <= 1) return page
    return page - page % groupSize
}

private enum class SpreadLayout {
    STACKED,
    SIDE_BY_SIDE,
    FOUR_PAGE,
}

private sealed interface SpreadPageState {
    data object Loading : SpreadPageState
    data class Ready(val bitmap: Bitmap) : SpreadPageState
    data object Failed : SpreadPageState
}

private const val DEFAULT_SPREAD_PAGE_ASPECT = 0.707f
