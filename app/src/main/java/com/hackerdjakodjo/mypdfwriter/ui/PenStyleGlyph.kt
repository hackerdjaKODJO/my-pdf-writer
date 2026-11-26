package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PenStyle

/**
 * A selectable pen-style chip with original instrument artwork and a sample
 * stroke. The artwork itself is shared with Pen Studio and both tool docks.
 */
@Composable
internal fun PenStyleGlyph(
    style: PenStyle,
    selected: Boolean,
    color: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = remember(style) { penIllustrationSpec(style) }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            PenInstrumentArtwork(
                style = style,
                color = color,
                modifier = Modifier.size(40.dp),
                bodyColor = foreground,
                cutoutColor = MaterialTheme.colorScheme.surface,
                showSampleStroke = true,
            )
        },
        label = {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier
            .heightIn(min = MIN_TARGET_DP.dp)
            .semantics { penStyleDescription(style, selected) },
    )
}
