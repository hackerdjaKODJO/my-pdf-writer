package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.pdf.ReadAloudController
import com.hackerdjakodjo.mypdfwriter.pdf.ReadAloudLanguage
import com.hackerdjakodjo.mypdfwriter.pdf.ReadAloudText

/**
 * Read-aloud transport for the reflow reader.
 *
 * The sheet owns the [ReadAloudController] lifecycle: it starts at
 * [initialPage], reports page auto-advance through [onPageChange], and shuts the
 * system TTS engine down when dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudSheet(
    pages: List<String>,
    initialPage: Int,
    onPageChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val safeInitial = if (pages.isEmpty()) 0 else initialPage.coerceIn(0, pages.lastIndex)

    var pageIndex by remember { mutableIntStateOf(safeInitial) }
    var playing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var language by remember { mutableStateOf(ReadAloudLanguage.KOREAN) }
    var rate by remember { mutableFloatStateOf(1f) }

    val currentOnPageChange by rememberUpdatedState(onPageChange)

    val controller = remember(pages) {
        ReadAloudController(
            context = context,
            pages = pages,
            initialPage = safeInitial,
            onState = { page, isPlaying ->
                prepared = true
                playing = isPlaying
                val safe = if (pages.isEmpty()) 0 else page.coerceIn(0, pages.lastIndex)
                if (safe != pageIndex) {
                    pageIndex = safe
                    currentOnPageChange(safe)
                }
                if (isPlaying) error = null
            },
            onError = { message ->
                prepared = true
                playing = false
                error = message
            },
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.shutdown() }
    }

    LaunchedEffect(controller) {
        controller.play(safeInitial)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("소리 내어 읽기", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "기기 TTS · 오프라인",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatPageCounter(pageIndex, pages.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${pages.count { it.isNotBlank() }}쪽 읽을 수 있음",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (pages.isEmpty()) 0f else (pageIndex + 1f) / pages.size
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            val preview = pages.getOrNull(pageIndex).orEmpty().trim()
                .ifBlank { "이 페이지에는 텍스트가 없습니다." }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    preview,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (!prepared) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "음성 엔진을 준비하는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let { message ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val previous = ReadAloudText.previousReadablePage(pages, pageIndex - 1)
            val next = ReadAloudText.firstReadablePage(pages, pageIndex + 1)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { previous?.let(controller::seekTo) }, enabled = prepared && previous != null) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "이전 페이지")
                }
                Spacer(Modifier.width(10.dp))
                FilledIconButton(
                    onClick = {
                        error = null
                        if (playing) controller.pause() else controller.play(pageIndex)
                    },
                    modifier = Modifier.size(60.dp),
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "일시정지" else "재생",
                        modifier = Modifier.size(30.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = { controller.stop() },
                    enabled = prepared && playing,
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "정지")
                }
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { next?.let(controller::seekTo) }, enabled = prepared && next != null) {
                    Icon(Icons.Outlined.SkipNext, contentDescription = "다음 페이지")
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "속도",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(48.dp),
                )
                Slider(
                    value = rate,
                    onValueChange = { rate = it },
                    onValueChangeFinished = { controller.setSpeechRate(rate, restartCurrent = true) },
                    valueRange = ReadAloudController.MIN_SPEECH_RATE..ReadAloudController.MAX_SPEECH_RATE,
                    steps = 14,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatSpeechRate(rate),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp),
                )
            }

            Text(
                "언어",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReadAloudLanguage.entries.forEach { option ->
                    FilterChip(
                        selected = language == option,
                        onClick = {
                            language = option
                            error = null
                            controller.setLanguage(option)
                        },
                        label = { Text(option.label) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "음성은 기기의 TTS 엔진에서 재생됩니다. 필요한 음성 데이터는 " +
                    "Android 설정 > 텍스트 음성 변환에서 설치할 수 있습니다.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
