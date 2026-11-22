package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Small, always-visible zoom control for both PDF readers.
 *
 * Pinch and double tap already work but are not discoverable; this pill reuses
 * the existing [stepReaderZoom] policy and gives one-tap ±25% steps with 48 dp
 * targets. It is deliberately standalone so the top-bar and 6-slot dock
 * contracts of the existing UI tests stay unchanged.
 */
@Composable
fun ViewerZoomPill(
    zoom: Float,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.TextButton(
                onClick = { onStep(-1) },
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "축소" },
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                readerZoomLabel(zoom),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "확대 배율" },
            )
            androidx.compose.material3.TextButton(
                onClick = { onStep(1) },
                modifier = Modifier
                    .height(48.dp)
                    .semantics { contentDescription = "확대" },
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
