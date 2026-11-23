package com.hackerdjakodjo.mypdfwriter.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hackerdjakodjo.mypdfwriter.audio.AudioNoteController
import com.hackerdjakodjo.mypdfwriter.audio.AudioNoteControllerState
import com.hackerdjakodjo.mypdfwriter.data.AudioMarker
import com.hackerdjakodjo.mypdfwriter.data.AudioNote
import com.hackerdjakodjo.mypdfwriter.data.AudioNoteStore
import com.hackerdjakodjo.mypdfwriter.data.audioNoteStartedAt
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only voice notes for one notebook or PDF document.
 *
 * `ownerId` scopes storage and `currentPage` is the page shown in the reader.
 * Recordings and page markers never leave the device. Full stroke-replay sync
 * is intentionally out of scope for this pass.
 *
 * The sheet owns the recorder/player lifecycle and releases both on dispose.
 * The manifest must declare `RECORD_AUDIO` before the permission request can
 * actually succeed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioNotesSheet(
    ownerId: String,
    currentPage: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val store = remember(context, ownerId) { AudioNoteStore(context) }

    var notes by remember(ownerId) { mutableStateOf(store.load(ownerId)) }
    var audioState by remember { mutableStateOf(AudioNoteControllerState()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pendingRecord by remember { mutableStateOf(false) }
    var recordingPage by remember { mutableIntStateOf(currentPage.coerceAtLeast(0)) }
    var draftMarkers by remember { mutableStateOf<List<AudioMarker>>(emptyList()) }
    var markerTarget by remember { mutableStateOf<AudioMarkerTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<AudioNote?>(null) }

    val controller = remember(context) {
        AudioNoteController(
            context = context,
            onState = { audioState = it },
            onError = { errorMessage = it },
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingRecord = true
        } else {
            errorMessage =
                "마이크 권한이 없어 녹음할 수 없습니다. Android 설정에서 My PDF Writer의 마이크 권한을 허용하세요."
        }
    }

    LaunchedEffect(pendingRecord) {
        if (!pendingRecord) return@LaunchedEffect
        pendingRecord = false
        if (audioState.recording) return@LaunchedEffect
        recordingPage = currentPage.coerceAtLeast(0)
        draftMarkers = emptyList()
        val file = store.createRecordingFile(ownerId)
        if (!controller.startRecording(file)) {
            store.deleteAudioFile(ownerId, file.absolutePath)
        }
    }

    fun requestRecording() {
        if (audioState.recording || audioState.playing || audioState.preparing) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            pendingRecord = true
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopAndSave() {
        if (!audioState.recording) return
        val result = controller.stopRecording()
        if (result == null) {
            draftMarkers = emptyList()
            return
        }
        val note = AudioNote(
            ownerId = ownerId,
            pageIndex = recordingPage,
            filePath = result.filePath,
            durationMs = result.durationMs,
            createdAt = audioNoteStartedAt(System.currentTimeMillis(), result.durationMs),
            markers = draftMarkers,
        )
        val updated = (notes.filterNot { it.id == note.id } + note)
            .sortedByDescending { it.createdAt }
        if (store.save(ownerId, updated)) {
            notes = updated
        } else {
            errorMessage = "녹음 파일은 만들어졌지만 목록을 저장하지 못했습니다. 잠시 후 다시 시도하세요."
        }
        draftMarkers = emptyList()
    }

    fun dismissSheet() {
        if (audioState.recording) stopAndSave()
        controller.stopPlayback()
        onDismiss()
    }

    fun togglePlayback(note: AudioNote) {
        if (audioState.recording) {
            errorMessage = "녹음 중에는 재생할 수 없습니다. 먼저 녹음을 중지하세요."
            return
        }
        val active = audioState.playingNoteId == note.id
        when {
            active && audioState.preparing -> Unit
            active && audioState.playing -> controller.pause()
            active -> controller.resume()
            else -> controller.play(
                noteId = note.id,
                filePath = note.filePath,
                startMs = 0L,
                durationHintMs = note.durationMs,
            )
        }
    }

    fun seekMarker(note: AudioNote, marker: AudioMarker) {
        if (audioState.recording) {
            errorMessage = "녹음 중에는 재생할 수 없습니다. 먼저 녹음을 중지하세요."
            return
        }
        controller.play(
            noteId = note.id,
            filePath = note.filePath,
            startMs = marker.offsetMs,
            durationHintMs = note.durationMs,
        )
    }

    val activeNote = audioState.playingNoteId?.let { id -> notes.firstOrNull { it.id == id } }

    fun openMarkerDialog() {
        if (audioState.recording) {
            markerTarget = AudioMarkerTarget(
                noteId = null,
                forRecording = true,
                pageIndex = currentPage.coerceAtLeast(0),
                offsetMs = audioState.recordingElapsedMs,
            )
            return
        }
        val note = activeNote ?: return
        val offset = if (note.durationMs > 0L) {
            audioState.positionMs.coerceIn(0L, note.durationMs)
        } else {
            audioState.positionMs.coerceAtLeast(0L)
        }
        markerTarget = AudioMarkerTarget(
            noteId = note.id,
            forRecording = false,
            pageIndex = currentPage.coerceAtLeast(0),
            offsetMs = offset,
        )
    }

    fun addMarker(title: String) {
        val target = markerTarget ?: return
        markerTarget = null
        val marker = AudioMarker(
            pageIndex = target.pageIndex,
            offsetMs = target.offsetMs,
            title = title.trim(),
        )
        if (target.forRecording) {
            draftMarkers = (draftMarkers + marker).sortedBy { it.offsetMs }
            return
        }
        val noteId = target.noteId ?: return
        val note = notes.firstOrNull { it.id == noteId } ?: return
        val updatedNote = note.copy(
            markers = (note.markers + marker).sortedBy { it.offsetMs },
        )
        val updated = store.upsert(ownerId, updatedNote)
        if (updated == null) {
            errorMessage = "페이지 표시를 저장하지 못했습니다. 잠시 후 다시 시도하세요."
        } else {
            notes = updated
        }
    }

    fun deleteNote(note: AudioNote) {
        if (audioState.playingNoteId == note.id) controller.stopPlayback()
        if (store.delete(ownerId, note.id)) {
            notes = notes.filterNot { it.id == note.id }
        } else {
            errorMessage = "녹음을 삭제하지 못했습니다. 잠시 후 다시 시도하세요."
        }
    }

    val markerAvailable = audioState.recording || activeNote != null
    val markerHint = when {
        audioState.recording -> "녹음 중 현재 경과 시간에 페이지 표시를 추가합니다."
        activeNote != null -> "재생 중 현재 위치에 페이지 표시를 추가합니다."
        else -> "녹음 중이거나 재생 중일 때 현재 위치를 페이지 표시로 남길 수 있습니다."
    }

    ModalBottomSheet(onDismissRequest = { dismissSheet() }, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        Text("오디오 노트", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "녹음 · 재생 · 페이지 표시",
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
                    "현재 ${currentPage + 1}쪽입니다. 녹음과 페이지 표시는 이 기기에만 저장됩니다.",
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
                            "녹음 파일은 이 기기의 앱 저장 공간(files/audio)에만 저장되며 서버로 " +
                                "업로드되지 않습니다. My PDF Writer는 인터넷 권한을 사용하지 않습니다.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
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

            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (audioState.recording) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    contentColor = if (audioState.recording) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(
                                        if (audioState.recording) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        CircleShape,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    audioState.recording ->
                                        "녹음 중 · ${formatAudioDuration(audioState.recordingElapsedMs)}"
                                    audioState.preparing -> "재생 준비 중…"
                                    audioState.playing -> "재생 중"
                                    else -> "이 페이지에 오디오 노트 남기기"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (audioState.recording) {
                                Text(
                                    "시작 ${recordingPage + 1}쪽",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (audioState.recording) {
                            Button(
                                onClick = { stopAndSave() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Outlined.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("녹음 중지 및 저장")
                            }
                        } else {
                            Button(
                                onClick = { requestRecording() },
                                enabled = !audioState.playing && !audioState.preparing,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(
                                    Icons.Outlined.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("녹음 시작")
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { openMarkerDialog() },
                    enabled = markerAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (audioState.recording) {
                            "현재 ${formatAudioDuration(audioState.recordingElapsedMs)}에 페이지 표시"
                        } else {
                            "현재 위치에 페이지 표시"
                        },
                    )
                }
            }

            item {
                Text(
                    markerHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (audioState.recording && draftMarkers.isNotEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
                            Text(
                                "이번 녹음의 페이지 표시 ${draftMarkers.size}개",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.height(4.dp))
                            draftMarkers.forEach { marker ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        formatAudioDuration(marker.offsetMs),
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
                                    IconButton(
                                        onClick = {
                                            draftMarkers = draftMarkers.filterNot { it.id == marker.id }
                                        },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "페이지 표시 삭제",
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
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

            if (notes.isEmpty()) {
                item {
                    Text(
                        "아직 녹음이 없습니다. 녹음을 시작하면 현재 페이지에 연결된 오디오 노트가 여기에 표시됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    AudioNoteRow(
                        note = note,
                        active = audioState.playingNoteId == note.id,
                        playing = audioState.playingNoteId == note.id && audioState.playing,
                        preparing = audioState.playingNoteId == note.id && audioState.preparing,
                        positionMs = audioState.positionMs,
                        durationMs = audioState.durationMs,
                        seekEnabled = !audioState.recording,
                        onPlayPause = { togglePlayback(note) },
                        onStop = { controller.stopPlayback() },
                        onDelete = { deleteTarget = note },
                        onSeek = { marker -> seekMarker(note, marker) },
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

    markerTarget?.let { target ->
        AudioMarkerDialog(
            target = target,
            onDismiss = { markerTarget = null },
            onConfirm = { title -> addMarker(title) },
        )
    }

    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("녹음 삭제") },
            text = {
                Text(
                    "${note.pageIndex + 1}쪽 녹음과 페이지 표시 ${note.markers.size}개를 삭제할까요? " +
                        "이 작업은 되돌릴 수 없습니다.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        deleteNote(note)
                    },
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun AudioNoteRow(
    note: AudioNote,
    active: Boolean,
    playing: Boolean,
    preparing: Boolean,
    positionMs: Long,
    durationMs: Long,
    seekEnabled: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onSeek: (AudioMarker) -> Unit,
) {
    val date = remember(note.createdAt) {
        SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(note.createdAt))
    }
    val missing = remember(note.filePath) {
        runCatching { !File(note.filePath).isFile }.getOrDefault(true)
    }
    val totalMs = when {
        active && durationMs > 0L -> durationMs
        note.durationMs > 0L -> note.durationMs
        else -> 0L
    }
    val progress = if (active && totalMs > 0L) {
        (positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
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
                        "${note.pageIndex + 1}쪽 · ${formatAudioDuration(note.durationMs)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        if (missing) "파일을 찾을 수 없음 · $date" else date,
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
                    modifier = Modifier.size(MIN_TARGET_DP.dp),
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
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "녹음 삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (active) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${formatAudioDuration(positionMs)} / ${formatAudioDuration(totalMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "페이지 표시 ${note.markers.size}개",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (note.markers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                note.markers.sortedBy { it.offsetMs }.forEach { marker ->
                    Surface(
                        onClick = { if (seekEnabled) onSeek(marker) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, end = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatAudioDuration(marker.offsetMs),
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

@Composable
private fun AudioMarkerDialog(
    target: AudioMarkerTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("페이지 표시 추가") },
        text = {
            Column {
                Text(
                    "${target.pageIndex + 1}쪽 · ${formatAudioDuration(target.offsetMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "이 시점으로 돌아올 수 있도록 페이지 표시를 남깁니다. 제목은 비워 두어도 됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제목 (선택)") },
                    placeholder = { Text("예: 중요한 설명") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) { Text("표시 추가") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

private data class AudioMarkerTarget(
    val noteId: String?,
    val forRecording: Boolean,
    val pageIndex: Int,
    val offsetMs: Long,
)

private fun formatAudioDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L).toInt()
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    val minuteText = if (hours > 0) minutes.toString().padStart(2, '0') else minutes.toString()
    val secondText = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$minuteText:$secondText" else "$minuteText:$secondText"
}
