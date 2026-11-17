package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.audio.AudioReplayController
import com.hackerdjakodjo.mypdfwriter.audio.AudioReplayState
import com.hackerdjakodjo.mypdfwriter.audio.PlaybackSpeed
import com.hackerdjakodjo.mypdfwriter.data.AudioMarker
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import com.hackerdjakodjo.mypdfwriter.data.AudioNoteStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replays local recordings while exposing the playback clock to the viewer.
 *
 * Notes on [currentPage] are listed first and highlighted. Playback itself is
 * handled by [AudioReplayController], which reuses the recorder sheet's
 * [com.hackerdjakodjo.mypdfwriter.audio.AudioNoteController] and its 200 ms
 * position ticker.
 *
 * [onPlaybackState] is called for every playback callback with the active note
 * (or null after stop), the elapsed position, and whether audio is playing.
 * The viewer can use the note and position to reveal annotations whose
 * [com.hackerdjakodjo.mypdfwriter.data.Annotation.createdAt] has been reached.
 * A compact rate chip row (0.5x … 2x) applies the selected speed immediately,
 * including to the active note, and keeps that selection for the sheet session
 * at a 1.0x default. It never changes a note's stored data.
 *
 * The sheet stops playback and reports a cleared state before dismissing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioReplaySheet(
    ownerId: String,
    currentPage: Int,
    onPlaybackState: (note: AudioNote?, positionMs: Long, playing: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val store = remember(context, ownerId) { AudioNoteStore(context) }
    val safePage = currentPage.coerceAtLeast(0)

    var notes by remember(ownerId) { mutableStateOf(store.load(ownerId)) }
    var replayState by remember { mutableStateOf(AudioReplayState()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Session-scoped playback rate: resets to 1.0x when the sheet is opened
    // again, but survives note changes, seeking and stop within this session.
    var playbackSpeed by remember(ownerId) { mutableFloatStateOf(PlaybackSpeed.DEFAULT) }

    val latestOnPlaybackState by rememberUpdatedState(onPlaybackState)

    val controller = remember(context) {
        AudioReplayController(
            context = context,
            onState = { state ->
                replayState = state
                latestOnPlaybackState(state.note, state.positionMs, state.playing)
            },
            onError = { message -> errorMessage = message },
        )
    }

    LaunchedEffect(notes) { controller.updateNotes(notes) }
    // Apply once on open and immediately on every chip tap, before play and
    // while playing. Selection lives here; the controller mirrors it in state.
    LaunchedEffect(playbackSpeed) { controller.setPlaybackSpeed(playbackSpeed) }
    LaunchedEffect(ownerId) {
        // Opening replay with nothing selected should leave the viewer unfiltered.
        latestOnPlaybackState(null, 0L, false)
    }
    DisposableEffect(controller) {
        // Dismissal goes through dismissSheet(), which stops and clears playback.
        onDispose { controller.release() }
    }

    val orderedNotes = remember(notes, safePage) {
        notes.sortedWith(
            compareByDescending<AudioNote> { it.pageIndex == safePage }
                .thenByDescending { it.createdAt },
        )
    }

    fun dismissSheet() {
        controller.stop()
        latestOnPlaybackState(null, 0L, false)
        onDismiss()
    }

    fun toggleNote(note: AudioNote) {
        errorMessage = null
        controller.toggle(note)
    }

    fun seekNote(note: AudioNote, positionMs: Long) {
        errorMessage = null
        controller.seekTo(note, positionMs)
    }

    ModalBottomSheet(onDismissRequest = { dismissSheet() }, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 660.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("필기 다시보기", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "오디오 타임라인 · 점 단위 필기",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${notes.size}개",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                Text(
                    "현재 ${safePage + 1}쪽 녹음을 먼저 보여 줍니다. 재생 중에는 그 시점까지 " +
                        "기록된 필기가 점 단위로 차례로 나타납니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "녹음 중에 만든 필기는 재생 위치에 맞춰 획이 시작된 시점부터 점 단위로 " +
                                "나타납니다. 획 길이가 기록되지 않은 예전 필기는 해당 시점에 " +
                                "도달하면 한 번에 표시됩니다. 녹음과 주석은 이 기기에만 저장되고 " +
                                "서버로 업로드되지 않습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "재생 속도",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                PlaybackSpeed.label(playbackSpeed),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        PlaybackSpeedChips(
                            speed = playbackSpeed,
                            onSpeedChange = { value -> playbackSpeed = value },
                        )
                    }
                }
            }

            errorMessage?.let { message ->
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                                end = 4.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "오류 닫기")
                            }
                        }
                    }
                }
            }

            replayState.note?.let { active ->
                item {
                    AudioReplayTransport(
                        note = active,
                        state = replayState,
                        speed = replayState.playbackSpeed,
                        onPlayPause = { toggleNote(active) },
                        onStop = { controller.stop() },
                        onSeek = { positionMs -> seekNote(active, positionMs) },
                        onSeekMarker = { marker -> seekNote(active, marker.offsetMs) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider()
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "녹음 목록",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "이 기기에만 저장",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (orderedNotes.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "아직 녹음이 없습니다.",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "음성 메모에서 녹음을 시작하면 이곳에서 필기와 함께 " +
                                    "다시 볼 수 있습니다. 현재 페이지에 연결된 녹음이 " +
                                    "먼저 표시됩니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(orderedNotes, key = { it.id }) { note ->
                    val active = replayState.note?.id == note.id
                    AudioReplayNoteRow(
                        note = note,
                        currentPage = safePage,
                        active = active,
                        playing = active && replayState.playing,
                        preparing = active && replayState.preparing,
                        positionMs = if (active) replayState.positionMs else 0L,
                        durationMs = if (active && replayState.durationMs > 0L) {
                            replayState.durationMs
                        } else {
                            note.durationMs
                        },
                        onPlayPause = { toggleNote(note) },
                        onStop = { controller.stop() },
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { dismissSheet() }, modifier = Modifier.fillMaxWidth()) {
                    Text("닫기")
                }
            }
        }
    }
}

/** Compact, single-select row of the six supported playback rates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSpeedChips(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlaybackSpeed.ALLOWED.forEach { value ->
            val selected = PlaybackSpeed.normalize(speed) == value
            Surface(
                onClick = { onSpeedChange(value) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Text(
                    PlaybackSpeed.label(value),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioReplayTransport(
    note: AudioNote,
    state: AudioReplayState,
    speed: Float,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekMarker: (AudioMarker) -> Unit,
) {
    val missing = remember(note.filePath) {
        runCatching { !File(note.filePath).isFile }.getOrDefault(true)
    }
    val date = remember(note.createdAt) { formatReplayDate(note.createdAt) }
    val totalMs = when {
        state.durationMs > 0L -> state.durationMs
        note.durationMs > 0L -> note.durationMs
        else -> 0L
    }
    var scrubbing by remember(note.id) { mutableStateOf(false) }
    var scrubMs by remember(note.id) { mutableLongStateOf(0L) }
    val displayedMs = if (scrubbing) scrubMs else state.positionMs.coerceIn(
        0L,
        if (totalMs > 0L) totalMs else Long.MAX_VALUE,
    )
    val sliderMax = totalMs.coerceAtLeast(1L).toFloat()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${note.pageIndex + 1}쪽에서 녹음한 필기",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = !missing && !state.preparing,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (state.preparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.playing) "일시정지" else "재생",
                        )
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Outlined.Stop, contentDescription = "재생 정지")
                }
            }

            Spacer(Modifier.height(10.dp))

            if (missing) {
                Text(
                    "녹음 파일을 찾을 수 없습니다. 파일이 이동되었거나 삭제되었을 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatReplayTime(displayedMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatReplayTime(totalMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = displayedMs.toFloat().coerceIn(0f, sliderMax),
                    onValueChange = {
                        scrubbing = true
                        scrubMs = it.toLong().coerceAtLeast(0L)
                    },
                    onValueChangeFinished = {
                        scrubbing = false
                        onSeek(scrubMs)
                    },
                    valueRange = 0f..sliderMax,
                    enabled = totalMs > 0L,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    "속도 ${PlaybackSpeed.label(speed)} · 이 위치까지 기록된 주석만 표시됩니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    "페이지 표시 ${note.markers.size}개",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (note.markers.isEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "음성 메모에서 페이지 표시를 추가하면 여기서 바로 이동할 수 있습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    note.markers.sortedBy { it.offsetMs }.forEach { marker ->
                        Surface(
                            onClick = { onSeekMarker(marker) },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatReplayTime(marker.offsetMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(52.dp),
                                )
                                Text(
                                    "${marker.pageIndex + 1}쪽",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(42.dp),
                                )
                                Text(
                                    marker.title.ifBlank { "${marker.pageIndex + 1}쪽 표시" },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "표시 위치부터 재생",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioReplayNoteRow(
    note: AudioNote,
    currentPage: Int,
    active: Boolean,
    playing: Boolean,
    preparing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
) {
    val missing = remember(note.filePath) {
        runCatching { !File(note.filePath).isFile }.getOrDefault(true)
    }
    val date = remember(note.createdAt) { formatReplayDate(note.createdAt) }
    val onCurrentPage = note.pageIndex == currentPage

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            onCurrentPage -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .background(
                            when {
                                active && playing -> MaterialTheme.colorScheme.primary
                                active -> MaterialTheme.colorScheme.tertiary
                                onCurrentPage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outline
                            },
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${note.pageIndex + 1}쪽 · ${formatReplayTime(durationMs)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (onCurrentPage) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ) {
                                Text(
                                    "현재 페이지",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        when {
                            missing -> "파일을 찾을 수 없음 · $date"
                            active -> "${formatReplayTime(positionMs)} / " +
                                "${formatReplayTime(durationMs)} · $date"
                            else -> date
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (missing) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = !missing && !preparing,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (preparing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "일시정지" else "재생",
                        )
                    }
                }
                IconButton(onClick = onStop, enabled = active) {
                    Icon(Icons.Outlined.Stop, contentDescription = "재생 정지")
                }
            }
        }
    }
}

private fun formatReplayDate(createdAt: Long): String =
    if (createdAt <= 0L) {
        "기록 시각 없음"
    } else {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(createdAt))
    }

private fun formatReplayTime(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val minuteText = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    val secondText = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minuteText:$secondText" else "$minuteText:$secondText"
}
