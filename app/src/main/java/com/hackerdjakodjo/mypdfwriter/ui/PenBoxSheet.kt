package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.FavoritePens
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import com.hackerdjakodjo.mypdfwriter.data.isRequiredPreset
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.samePenStyle
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationPainter
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

internal fun penStyleLabel(style: PenStyle): String = when (style) {
    PenStyle.BALLPOINT -> "볼펜"
    PenStyle.FOUNTAIN -> "만년필"
    PenStyle.BRUSH -> "붓"
    PenStyle.PENCIL -> "연필"
}

/**
 * Reorder slots look identical, so their spoken names carry the one-based
 * position and the destination. TalkBack can then tell the two arrows apart.
 */
internal fun penBoxReorderLabel(direction: String, from: Int, to: Int, name: String): String =
    "$name ${from}번 $direction (${to}번으로 이동)"

/** Star label that names both the pen and its slot position. */
internal fun penBoxFavoriteToggleLabel(name: String, position: Int, favorite: Boolean): String =
    "$name ${position}번 " + if (favorite) "즐겨찾기 해제" else "즐겨찾기에 추가"

/**
 * Bottom sheet for saved pens. Applying a preset updates the active tool, colour,
 * width, opacity and pen style in one tap. Presets and the ordered favorites
 * registry both stay local in PenBoxStore. The extra favorites parameters have
 * defaults so older call sites keep working until the integrator wires them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PenBoxSheet(
    presets: List<PenPreset>,
    current: PenPreset,
    onDismiss: () -> Unit,
    onApply: (PenPreset) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDelete: (PenPreset) -> Unit,
    onRename: (PenPreset, String) -> Unit,
    favorites: List<PenPreset> = presets.take(FavoritePens.DEFAULT_FAVORITE_COUNT),
    onToggleFavorite: (PenPreset) -> Unit = {},
    onMoveFavorite: (from: Int, to: Int) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PenPreset?>(null) }

    // Optimistic local copy gives instant feedback even before the store recomposes.
    val favoriteIds = favorites.map { it.id }.joinToString(",")
    var localFavorites by remember(favoriteIds) {
        mutableStateOf(favorites.take(FavoritePens.MAX_SLOTS))
    }

    fun toggleFavorite(preset: PenPreset) {
        // Resolve against every preset so starring a pen that is not yet a favorite works.
        val ids = FavoritePens.toggle(localFavorites.map { it.id }, preset.id, presets)
        localFavorites = FavoritePens.resolve(ids, presets)
        onToggleFavorite(preset)
    }

    fun moveFavorite(from: Int, to: Int) {
        val ids = FavoritePens.move(localFavorites.map { it.id }, from, to)
        localFavorites = FavoritePens.resolve(ids, presets)
        onMoveFavorite(from, to)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(NOTE_PEN_PRESETS_LABEL, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "저장한 펜을 눌러 바로 사용하세요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PenBoxPreview(preset = current, modifier = Modifier.width(88.dp).height(36.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // Compact favorites section, always above the full pen list.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("즐겨찾는 펜", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${localFavorites.size}/${FavoritePens.MAX_SLOTS}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (localFavorites.isEmpty()) {
                Text(
                    "별을 눌러 자주 쓰는 펜을 담아보세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    localFavorites.forEachIndexed { index, preset ->
                        FavoritePenChip(
                            preset = preset,
                            position = index + 1,
                            active = samePenStyle(preset, current),
                            canMoveLeft = index > 0,
                            canMoveRight = index < localFavorites.lastIndex,
                            onApply = { onApply(preset) },
                            onToggleFavorite = { toggleFavorite(preset) },
                            onMoveLeft = { moveFavorite(index, index - 1) },
                            onMoveRight = { moveFavorite(index, index + 1) },
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(presets, key = { _, preset -> preset.id }) { index, preset ->
                    PenBoxRow(
                        preset = preset,
                        position = index + 1,
                        active = samePenStyle(preset, current),
                        favorite = preset.id in localFavorites.map { it.id },
                        canDelete = presets.size > 1 && !isRequiredPreset(preset.id),
                        onApply = { onApply(preset) },
                        onRename = { renameTarget = preset },
                        onDelete = { onDelete(preset) },
                        onToggleFavorite = { toggleFavorite(preset) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { saveDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("현재 펜을 내 펜에 저장")
            }
        }
    }

    if (saveDialog) {
        PenNameDialog(
            title = "내 펜에 저장",
            initial = "내 펜 ${presets.size + 1}",
            confirmLabel = "저장",
            onDismiss = { saveDialog = false },
            onConfirm = { name ->
                saveDialog = false
                onSaveCurrent(name)
            },
        )
    }

    val target = renameTarget
    if (target != null) {
        PenNameDialog(
            title = "펜 이름 변경",
            initial = target.name,
            confirmLabel = "변경",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                onRename(target, name)
            },
        )
    }
}

/** One favorite in the registry section: ink preview, star and simple reorder buttons. */
@Composable
private fun FavoritePenChip(
    preset: PenPreset,
    position: Int,
    active: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onApply: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            if (active) 1.5.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.semantics {
            contentDescription = stateLabel(preset.name, active)
            this.selected = active
        },
    ) {
        Column(
            // Three 48 dp controls plus padding: the wide chip scrolls inside the
            // registry row instead of clipping its move/star buttons.
            modifier = Modifier.width(160.dp).padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PenBoxPreview(preset = preset, modifier = Modifier.fillMaxWidth().height(24.dp))
            Text(
                preset.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier.size(MIN_TARGET_DP.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = penBoxReorderLabel(
                            "앞으로", position, position - 1, preset.name,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(MIN_TARGET_DP.dp),
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = penBoxFavoriteToggleLabel(
                            preset.name, position, favorite = true,
                        ),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier.size(MIN_TARGET_DP.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = penBoxReorderLabel(
                            "뒤로", position, position + 1, preset.name,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PenBoxRow(
    preset: PenPreset,
    position: Int,
    active: Boolean,
    favorite: Boolean,
    canDelete: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        onClick = onApply,
        shape = RoundedCornerShape(10.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = if (active) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = Modifier.semantics {
            contentDescription = stateLabel(preset.name, active)
            this.selected = active
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(Color(preset.color))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        if (preset.tool == Tool.HIGHLIGHT) {
                            append("형광펜")
                        } else {
                            append(penStyleLabel(preset.penStyle))
                        }
                        append(" · ")
                        append(preset.width.times(1000).roundToInt())
                        append(" · ")
                        append((preset.opacity * 100).roundToInt())
                        append("%")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PenBoxPreview(
                preset = preset,
                modifier = Modifier.width(60.dp).height(30.dp),
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(MIN_TARGET_DP.dp),
            ) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = penBoxFavoriteToggleLabel(
                        preset.name, position, favorite,
                    ),
                    modifier = Modifier.size(18.dp),
                    tint = if (favorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(MIN_TARGET_DP.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = menuLabel("이름 변경", "${position}번 ${preset.name}"),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier.size(MIN_TARGET_DP.dp),
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = menuLabel("펜 삭제", "${position}번 ${preset.name}"),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Shared ink-stroke swatch. Internal so the favorite strip reuses the exact look. */
@Composable
internal fun PenBoxPreview(preset: PenPreset, modifier: Modifier = Modifier) {
    val type = if (preset.tool == Tool.HIGHLIGHT) AnnotationType.HIGHLIGHT else AnnotationType.PEN
    val points = remember {
        (0..10).map { index ->
            val t = index / 10f
            NormalizedPoint(
                x = 0.06f + t * 0.88f,
                y = 0.5f + sin(t * PI * 1.6).toFloat() * 0.16f,
            )
        }
    }
    Canvas(modifier) {
        drawIntoCanvas { canvas ->
            AnnotationPainter.drawOne(
                canvas = canvas.nativeCanvas,
                annotation = Annotation(
                    type = type,
                    page = 0,
                    color = preset.color,
                    width = noteStrokeWidth(type, preset.width),
                    points = points,
                    opacity = preset.opacity,
                    penStyle = preset.penStyle,
                ),
                width = size.width,
                height = size.height,
            )
        }
    }
}

@Composable
private fun PenNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim().ifBlank { initial }) },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}
