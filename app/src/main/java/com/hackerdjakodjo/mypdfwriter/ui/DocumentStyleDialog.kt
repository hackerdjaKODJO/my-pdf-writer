package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.DocumentStyle
import com.hackerdjakodjo.mypdfwriter.data.PageNumberFormat
import com.hackerdjakodjo.mypdfwriter.data.PageNumberPosition
import com.hackerdjakodjo.mypdfwriter.data.PageNumberStyle
import com.hackerdjakodjo.mypdfwriter.data.WatermarkStyle

@Composable
fun DocumentStyleDialog(
    initial: DocumentStyle,
    pageCount: Int,
    onDismiss: () -> Unit,
    onApply: (DocumentStyle) -> Unit,
) {
    var watermarkText by remember { mutableStateOf(initial.watermark.text) }
    var opacity by remember { mutableFloatStateOf(initial.watermark.opacity) }
    var rotation by remember { mutableIntStateOf(initial.watermark.rotation) }
    var numbersOn by remember { mutableStateOf(initial.pageNumbers.enabled) }
    var format by remember { mutableStateOf(initial.pageNumbers.format) }
    var position by remember { mutableStateOf(initial.pageNumbers.position) }
    var startNumber by remember { mutableIntStateOf(initial.pageNumbers.startNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("문서 꾸미기") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("워터마크", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("문구 (비우면 끔)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("투명도 ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.04f..0.4f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("각도 ${rotation}°", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = rotation.toFloat(),
                        onValueChange = { rotation = it.toInt() },
                        valueRange = -90f..90f,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("페이지 번호", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "총 ${pageCount}쪽",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = numbersOn, onCheckedChange = { numbersOn = it })
                }
                if (numbersOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageNumberFormat.entries.forEach { item ->
                            FilterChip(
                                selected = format == item,
                                onClick = { format = item },
                                label = {
                                    Text(
                                        when (item) {
                                            PageNumberFormat.PLAIN -> "1"
                                            PageNumberFormat.OF_TOTAL -> "1 / N"
                                            PageNumberFormat.DASHED -> "- 1 -"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageNumberPosition.entries.forEach { item ->
                            FilterChip(
                                selected = position == item,
                                onClick = { position = item },
                                label = {
                                    Text(
                                        when (item) {
                                            PageNumberPosition.BOTTOM_CENTER -> "아래 가운데"
                                            PageNumberPosition.BOTTOM_RIGHT -> "아래 오른쪽"
                                            PageNumberPosition.TOP_CENTER -> "위 가운데"
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("시작 번호", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = startNumber.toFloat(),
                            onValueChange = { startNumber = it.toInt().coerceAtLeast(1) },
                            valueRange = 1f..(pageCount.coerceAtLeast(1)).toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        Text("$startNumber", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        DocumentStyle(
                            watermark = WatermarkStyle(
                                text = watermarkText.trim(),
                                opacity = opacity,
                                rotation = rotation,
                            ),
                            pageNumbers = PageNumberStyle(
                                enabled = numbersOn,
                                format = format,
                                position = position,
                                startNumber = startNumber.coerceAtLeast(1),
                            ),
                        ),
                    )
                },
            ) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
