package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.FavoritePens
import com.hackerdjakodjo.mypdfwriter.data.PenPreset
import com.hackerdjakodjo.mypdfwriter.data.Tool
import com.hackerdjakodjo.mypdfwriter.data.samePenStyle

/** Short phone-width label for a pen type. Pure so it is JVM-testable. */
internal fun favoritePenTypeLabel(preset: PenPreset): String =
    if (preset.tool == Tool.HIGHLIGHT) "형광펜" else penStyleLabel(preset.penStyle)

/**
 * Accessible name for one favorite slot. The selected variant appends
 * ", 선택됨" so TalkBack can tell which preset is currently applied.
 */
internal fun favoriteSlotDescription(preset: PenPreset, selected: Boolean = false): String =
    stateLabel("즐겨찾는 펜 ${preset.name}, ${favoritePenTypeLabel(preset)}", selected)

/** The slot list actually rendered: never more than the registry cap. */
internal fun favoriteSlots(favorites: List<PenPreset>): List<PenPreset> =
    favorites.take(FavoritePens.MAX_SLOTS)

/**
 * Horizontally scrollable quick-access strip of favorite pens, followed by a `+`
 * slot. Tap applies a preset; long-press (or `+`) opens the registry sheet where
 * pens can be starred, renamed and reordered. Everything is local and offline.
 */
@Composable
fun FavoritePenStrip(
    favorites: List<PenPreset>,
    current: PenPreset,
    onApply: (PenPreset) -> Unit,
    onOpenRegistry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        favoriteSlots(favorites).forEach { preset ->
            FavoriteSlot(
                preset = preset,
                active = samePenStyle(preset, current),
                onApply = { onApply(preset) },
                onOpenRegistry = onOpenRegistry,
            )
        }
        FavoriteAddSlot(onOpenRegistry = onOpenRegistry)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteSlot(
    preset: PenPreset,
    active: Boolean,
    onApply: () -> Unit,
    onOpenRegistry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        border = BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .sizeIn(
                minWidth = maxOf(MIN_TARGET_DP, 56).dp,
                minHeight = maxOf(MIN_TARGET_DP, 56).dp,
            )
            .combinedClickable(onClick = onApply, onLongClick = onOpenRegistry)
            .semantics {
                contentDescription = favoriteSlotDescription(preset, active)
                this.selected = active
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PenBoxPreview(preset = preset, modifier = Modifier.width(44.dp).height(26.dp))
            Spacer(Modifier.height(2.dp))
            Text(
                favoritePenTypeLabel(preset),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteAddSlot(onOpenRegistry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .sizeIn(minWidth = maxOf(MIN_TARGET_DP, 56).dp, minHeight = maxOf(MIN_TARGET_DP, 56).dp)
            .combinedClickable(onClick = onOpenRegistry, onLongClick = onOpenRegistry)
            .semantics { contentDescription = "즐겨찾는 펜 편집" },
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
