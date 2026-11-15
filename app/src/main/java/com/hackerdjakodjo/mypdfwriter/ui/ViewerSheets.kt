package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.page_actions), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_pages_short)) }
            }
            Text(
                stringResource(R.string.page_delete_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(state.pages.size) { index ->
                    val spec = state.pages[index]
                    PageRow(
                        index = index,
                        spec = spec,
                        selected = index == state.pageIndex,
                        hasNote = state.notes[index]?.isNotBlank() == true,
                        onClick = { vm.goToPage(index) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                SheetAction(Icons.Outlined.RotateLeft, stringResource(R.string.rotate_left), enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    vm.rotatePage(clockwise = false)
                }
                SheetAction(Icons.Outlined.RotateRight, stringResource(R.string.rotate_right), enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    vm.rotatePage(clockwise = true)
                }
                SheetAction(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up), enabled = !state.busy && state.pageIndex > 0, modifier = Modifier.weight(1f)) {
                    vm.movePage(state.pageIndex, -1)
                }
                SheetAction(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down), enabled = !state.busy && state.pageIndex < state.pageCount - 1, modifier = Modifier.weight(1f)) {
                    vm.movePage(state.pageIndex, 1)
                }
                SheetAction(Icons.Outlined.Add, stringResource(R.string.insert_blank), enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    vm.insertBlankPage(state.pageIndex)
                }
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.delete_page_action), enabled = !state.busy && state.pageCount > 1, modifier = Modifier.weight(1f)) {
                    confirmDelete = true
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_page)) },
            text = { Text(stringResource(R.string.page_delete_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deletePage(state.pageIndex)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_pages)) },
            text = { Text(stringResource(R.string.reset_pages_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetPages()
                }) { Text(stringResource(R.string.reset_pages)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PageRow(index: Int, spec: PageSpec, selected: Boolean, hasNote: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.page_number, index + 1),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(48.dp),
            )
            Column(Modifier.weight(1f)) {
                val label = when {
                    spec.sourcePage == null -> stringResource(R.string.blank_page)
                    else -> stringResource(R.string.original_page, spec.sourcePage + 1)
                }
                Text(label, style = MaterialTheme.typography.bodyMedium)
                if (spec.extraRotation != 0) {
                    Text("${spec.extraRotation}°", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (hasNote) {
                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    Column(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
        Spacer(Modifier.size(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .horizontalScrollIfNeeded(),
        ) {
            Text(stringResource(R.string.notes_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.page_number, state.pageIndex + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(R.string.notes_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = state.noteDraft,
                onValueChange = vm::updateNoteDraft,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.note_hint)) },
                minLines = 3,
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(4.dp))
                Button(onClick = {
                    vm.saveNote()
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            }

            val noted = state.notes.entries.sortedBy { it.key }
            if (noted.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    stringResource(R.string.note_saved_pages),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(noted, key = { it.key }) { entry ->
                        Surface(
                            onClick = {
                                vm.goToPage(entry.key)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.page_number, entry.key + 1),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(52.dp),
                                )
                                Text(
                                    entry.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(320)
        runCatching { focusRequester.requestFocus() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.size(10.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = vm::search,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            )
            Spacer(Modifier.size(8.dp))

            when {
                state.searching -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.searching), style = MaterialTheme.typography.bodyMedium)
                }

                state.searchFailed -> {
                    Text(
                        stringResource(R.string.search_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                state.searchQuery.isBlank() -> {
                    Text(
                        stringResource(R.string.search_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.searchHits.isEmpty() -> {
                    Text(
                        stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    Text(
                        stringResource(R.string.search_results, state.searchHits.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.searchHits, key = { it.page }) { hit ->
                            SearchHitRow(hit, state.searchQuery.trim()) {
                                vm.goToPage(hit.page)
                                onDismiss()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.page_number, hit.page + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${hit.count}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(4.dp))
            val snippet = hit.snippet
            val annotated = remember(snippet, query) {
                buildAnnotatedString {
                    if (query.isEmpty()) {
                        append(snippet)
                        return@buildAnnotatedString
                    }
                    val lower = snippet.lowercase()
                    val needle = query.lowercase()
                    var cursor = 0
                    while (cursor < snippet.length) {
                        val index = lower.indexOf(needle, cursor)
                        if (index < 0 || needle.isEmpty()) {
                            append(snippet.substring(cursor))
                            break
                        }
                        if (index > cursor) append(snippet.substring(cursor, index))
                        withStyle(SpanStyle(background = Color(0x332F6B9A), fontWeight = FontWeight.Bold)) {
                            append(snippet.substring(index, (index + needle.length).coerceAtMost(snippet.length)))
                        }
                        cursor = index + needle.length
                    }
                }
            }
            Text(annotated, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val annotations = remember(state.annotations) {
        state.annotations.sortedWith(compareBy<Annotation>({ it.page }, { it.type.ordinal }))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.annotations_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.annotations_count, annotations.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(10.dp))
            if (annotations.isEmpty()) {
                Text(
                    stringResource(R.string.annotations_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(annotations, key = { it.id }) { annotation ->
                        AnnotationRow(
                            annotation = annotation,
                            onOpen = {
                                vm.goToPage(annotation.page)
                                onDismiss()
                            },
                            onDelete = { vm.deleteAnnotation(annotation.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AnnotationRow(annotation: Annotation, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (annotation.type == AnnotationType.REDACT) Color.Black else Color(annotation.color),
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(annotationTypeLabelRes(annotation.type)),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.page_number, annotation.page + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(2.dp))
                Text(
                    annotationSummary(annotation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.annotation_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun annotationSummary(annotation: Annotation): String {
    val text = annotation.text.trim()
    val stamp = annotation.stamp.trim()
    return when {
        annotation.type == AnnotationType.STAMP && stamp.isNotEmpty() ->
            stringResource(R.string.annotation_summary_stamp, stamp)
        annotation.type == AnnotationType.SIGNATURE && text.isNotEmpty() ->
            stringResource(R.string.annotation_summary_signature, text)
        text.isNotEmpty() -> text
        stamp.isNotEmpty() -> stamp
        annotation.type == AnnotationType.PEN -> stringResource(R.string.annotation_summary_pen)
        annotation.type == AnnotationType.HIGHLIGHT -> stringResource(R.string.annotation_summary_highlight)
        annotation.type == AnnotationType.REDACT -> stringResource(R.string.annotation_summary_redact)
        else -> stringResource(R.string.annotation_summary_shape)
    }
}

private fun annotationTypeLabelRes(type: AnnotationType): Int = when (type) {
    AnnotationType.PEN -> R.string.annotation_type_pen
    AnnotationType.HIGHLIGHT -> R.string.annotation_type_highlight
    AnnotationType.TEXT -> R.string.annotation_type_text
    AnnotationType.RECT -> R.string.annotation_type_rect
    AnnotationType.ARROW -> R.string.annotation_type_arrow
    AnnotationType.UNDERLINE -> R.string.annotation_type_underline
    AnnotationType.STRIKEOUT -> R.string.annotation_type_strikeout
    AnnotationType.ELLIPSE -> R.string.annotation_type_ellipse
    AnnotationType.LINE -> R.string.annotation_type_line
    AnnotationType.STAMP -> R.string.annotation_type_stamp
    AnnotationType.SIGNATURE -> R.string.annotation_type_signature
    AnnotationType.REDACT -> R.string.annotation_type_redact
}

@Composable
fun StampDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    val presets = listOf(
        stringResource(R.string.stamp_preset_approved),
        stringResource(R.string.stamp_preset_draft),
        stringResource(R.string.stamp_preset_confidential),
        stringResource(R.string.stamp_preset_reviewed),
    )
    var value by remember { mutableStateOf(presets.first()) }
    var color by remember(initialColor) { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Approval, contentDescription = null) },
        title = { Text(stringResource(R.string.stamp_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = value == preset,
                            onClick = { value = preset },
                            label = { Text(preset) },
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.stamp_custom)) },
                    placeholder = { Text(stringResource(R.string.stamp_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.stamp_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    annotationPalette.forEach { swatch ->
                        ColorDot(color = swatch, selected = color == swatch, onClick = { color = swatch })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim(), color) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun SignatureDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    var color by remember(initialColor) { mutableStateOf(initialColor) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Draw, contentDescription = null) },
        title = { Text(stringResource(R.string.signature_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.signature_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    stringResource(R.string.signature_preview),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            value.ifBlank { stringResource(R.string.signature_hint) },
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Cursive),
                            color = Color(color),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        HorizontalDivider(Modifier.padding(top = 4.dp), color = Color(color))
                    }
                }
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    annotationPalette.forEach { swatch ->
                        ColorDot(color = swatch, selected = color == swatch, onClick = { color = swatch })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim(), color) }, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun TextAnnotationDialog(
    point: NormalizedPoint,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
        title = { Text(stringResource(R.string.text_dialog_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.text_hint)) },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Small helper kept for layout readability in dialogs/sheets. */
private fun Modifier.horizontalScrollIfNeeded(): Modifier = this
