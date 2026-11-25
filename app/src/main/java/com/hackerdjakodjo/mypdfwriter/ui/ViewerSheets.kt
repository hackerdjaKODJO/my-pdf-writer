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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Approval
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerdjakodjo.mypdfwriter.R
import com.hackerdjakodjo.mypdfwriter.data.Annotation
import com.hackerdjakodjo.mypdfwriter.data.AnnotationType
import com.hackerdjakodjo.mypdfwriter.data.DisplayPrefs
import com.hackerdjakodjo.mypdfwriter.data.NormalizedPoint
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import com.hackerdjakodjo.mypdfwriter.data.PdfBookmark
import com.hackerdjakodjo.mypdfwriter.data.SearchHit
import com.hackerdjakodjo.mypdfwriter.pdf.AnnotationSummary
import com.hackerdjakodjo.mypdfwriter.pdf.CropInsets
import com.hackerdjakodjo.mypdfwriter.pdf.PdfCrop
import com.hackerdjakodjo.mypdfwriter.pdf.PdfOutlineEntry
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The crop controls are advanced and rarely needed, so they start collapsed.
 * Keeping the default in one pure constant makes the "calm first screen"
 * contract testable without a Compose test.
 */
internal const val PDF_DISPLAY_CROP_DEFAULT_EXPANDED = false

/** Primary 화면 row controls vs. the advanced crop section, for layout audits. */
internal val DISPLAY_PRIMARY_CONTROLS = listOf("주간", "야간", "밝기")
internal val CROP_SECTION_CONTROLS = listOf("여백 자르기", "홀수·짝수", "여백 초기화")

internal fun displayCropSectionVisible(expanded: Boolean): Boolean = expanded

/**
 * Material3 screen-settings sheet. The primary 화면 row pairs 주간/야간 with 밝기;
 * the advanced 여백 자르기 controls (including 홀수·짝수 and 초기화) live behind a
 * collapsed section. Both display values are saved in the document's display
 * prefs on change, so they survive closing and reopening the document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var cropExpanded by remember { mutableStateOf(PDF_DISPLAY_CROP_DEFAULT_EXPANDED) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("화면 설정", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(4.dp))
            Text(
                "이 문서에만 적용되며 다시 열어도 유지됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(18.dp))

            // Primary row: day/night and brightness are the two decisions a
            // student actually makes when a page is too dark or too bright.
            Text("화면", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !state.nightMode,
                            onClick = { if (state.nightMode) vm.toggleNightMode() },
                            label = { Text("주간") },
                            modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                        )
                        FilterChip(
                            selected = state.nightMode,
                            onClick = { if (!state.nightMode) vm.toggleNightMode() },
                            label = { Text("야간") },
                            modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("밝기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            "${(state.brightness * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = state.brightness,
                        valueRange = DisplayPrefs.MIN_BRIGHTNESS..DisplayPrefs.MAX_BRIGHTNESS,
                        onValueChange = vm::setBrightness,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                val valueLabel = "${(state.brightness * 100).roundToInt()}%"
                                contentDescription = sliderLabel("밝기", valueLabel)
                                stateDescription = valueLabel
                            },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "어둡게",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { vm.setBrightness(DisplayPrefs.DEFAULT_BRIGHTNESS) },
                            modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                        ) { Text("기본값") }
                    }
                }
            }
            Spacer(Modifier.size(14.dp))

            // Secondary section: one full-width 48dp header, everything inside
            // (odd/even, reset) only exists when the student expands it.
            Surface(
                onClick = { cropExpanded = !cropExpanded },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = MIN_TARGET_DP.dp)
                    .semantics { stateDescription = expandStateDescription(cropExpanded) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("여백 자르기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text(
                        if (cropExpanded) "접기" else "펼치기",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (cropExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (displayCropSectionVisible(cropExpanded)) {
                Spacer(Modifier.size(4.dp))
                Text(
                    "보기에서만 여백을 숨깁니다. 원본 PDF와 내보내기는 그대로입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val crop = state.cropInsets
                val cropMax = 0.3f
                listOf<Triple<String, Float, (Float) -> Unit>>(
                    Triple("왼쪽", crop.left) { v -> vm.setCrop(crop.copy(left = v), state.cropOddEven) },
                    Triple("위", crop.top) { v -> vm.setCrop(crop.copy(top = v), state.cropOddEven) },
                    Triple("오른쪽", crop.right) { v -> vm.setCrop(crop.copy(right = v), state.cropOddEven) },
                    Triple("아래", crop.bottom) { v -> vm.setCrop(crop.copy(bottom = v), state.cropOddEven) },
                ).forEach { (label, value, update) ->
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        Text(
                            "${(value * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = value.coerceIn(0f, cropMax),
                        valueRange = 0f..cropMax,
                        onValueChange = update,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                val valueLabel = "${(value * 100).roundToInt()}%"
                                contentDescription = sliderLabel("${label} 여백", valueLabel)
                                stateDescription = valueLabel
                            },
                    )
                }
                FilterChip(
                    selected = state.cropOddEven,
                    onClick = { vm.setCrop(crop, !state.cropOddEven) },
                    label = { Text("홀수·짝수 페이지 다르게") },
                    modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                )
                TextButton(
                    onClick = { vm.setCrop(CropInsets.NONE, false) },
                    modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                ) { Text("여백 초기화") }
            }
        }
    }
}

/** Parses the student's 1-based page number into a 0-based index, or null. */
internal fun parsePageJump(input: String, pageCount: Int): Int? {
    val number = input.trim().toIntOrNull() ?: return null
    return if (pageCount > 0 && number in 1..pageCount) number - 1 else null
}

/** A one-shot lazy-list destination, expressed without a Compose dependency. */
internal data class InitialListScrollTarget(val index: Int, val offsetPx: Int = 0)

/**
 * Guards position restoration against repeated equivalent requests. The caller
 * marks a target applied only after its scroll completes, so cancellation can
 * retry while an ordinary recomposition cannot pull the list away from the user.
 */
internal class InitialListScrollPolicy {
    private var applied: InitialListScrollTarget? = null

    fun shouldApply(target: InitialListScrollTarget?): Boolean =
        target != null && target != applied

    fun markApplied(target: InitialListScrollTarget) {
        applied = target
    }
}

/** Current page aligned to the top; an empty document has no destination. */
internal fun pageSheetInitialScrollTarget(currentPage: Int, pageCount: Int): InitialListScrollTarget? {
    if (pageCount <= 0) return null
    return InitialListScrollTarget(index = currentPage.coerceIn(0, pageCount - 1))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageSheet(state: ViewerUiState, vm: PdfViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val initialScroll = pageSheetInitialScrollTarget(state.pageIndex, state.pages.size)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialScroll?.index ?: 0)
    val scrollPolicy = remember { InitialListScrollPolicy() }

    // Page changes are navigation, not recomposition: jump only when the current
    // page (or a page edit that changes the valid index range) actually changes.
    LaunchedEffect(state.pageIndex, state.pages.size) {
        val target = pageSheetInitialScrollTarget(state.pageIndex, state.pages.size)
            ?: return@LaunchedEffect
        if (!scrollPolicy.shouldApply(target)) return@LaunchedEffect
        listState.scrollToItem(target.index, target.offsetPx)
        scrollPolicy.markApplied(target)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Navigation is the primary purpose; editing lives below.
                Text("페이지", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reset_pages_short)) }
            }
            Text(
                stringResource(R.string.page_delete_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))

            // Jump straight to a known page number instead of scrolling a long list.
            var jumpInput by remember { mutableStateOf("") }
            val jumpIndex = parsePageJump(jumpInput, state.pages.size)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it },
                    label = { Text("페이지 번호") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Go,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (jumpIndex != null) {
                            vm.goToPage(jumpIndex)
                            onDismiss()
                        }
                    },
                    enabled = jumpIndex != null,
                ) { Text("이동") }
            }
            Spacer(Modifier.size(12.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(count = state.pages.size, key = { index -> index }) { index ->
                    val spec = state.pages[index]
                    PageRow(
                        index = index,
                        spec = spec,
                        isSelected = index == state.pageIndex,
                        hasNote = state.notes[index]?.isNotBlank() == true,
                        onClick = {
                            vm.goToPage(index)
                            onDismiss()
                        },
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
private fun PageRow(index: Int, spec: PageSpec, isSelected: Boolean, hasNote: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            // "Current page" was colour-only; expose it as a real state.
            .semantics { selected = isSelected },
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
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                        .semantics { contentDescription = "페이지 메모 있음" },
                )
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

/**
 * A search result row paired with a key that is unique even when several hits
 * share a page. Keying the LazyColumn by `SearchHit.page` alone collides as soon
 * as a page has more than one match; the row position is stable for a given list.
 */
internal data class KeyedSearchHit(val key: String, val index: Int, val hit: SearchHit)

internal fun keyedSearchHits(hits: List<SearchHit>): List<KeyedSearchHit> =
    hits.mapIndexed { index, hit -> KeyedSearchHit(key = "search-hit-$index", index = index, hit = hit) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(
    state: ViewerUiState,
    vm: PdfViewModel,
    onDismiss: () -> Unit,
    /** Optional offline OCR shortcut for empty/no-result states. */
    onOpenOcr: (() -> Unit)? = null,
) {
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
                label = { Text(PDF_SEARCH_FIELD_LABEL) },
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
                    SearchOcrTip(onOpenOcr)
                }

                state.searchHits.isEmpty() -> {
                    Text(
                        stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SearchOcrTip(onOpenOcr)
                }

                else -> {
                    val currentIndex =
                        SearchNavigation.clamp(state.currentSearchHit, state.searchHits.size).coerceAtLeast(0)
                    val rows = remember(state.searchHits) { keyedSearchHits(state.searchHits) }
                    Text(
                        stringResource(R.string.search_results, state.searchHits.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                    SearchResultNav(
                        current = currentIndex,
                        total = state.searchHits.size,
                        onPrevious = {
                            vm.previousSearchHit()
                            onDismiss()
                        },
                        onNext = {
                            vm.nextSearchHit()
                            onDismiss()
                        },
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            SearchHitRow(
                                row.hit,
                                state.searchQuery.trim(),
                                active = row.index == currentIndex,
                            ) {
                                vm.goToSearchHit(row.index)
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

/**
 * Local hint for blank/zero-result searches. The OCR action is optional so the
 * sheet still works when the host has no tools callback to hand off to.
 */
@Composable
private fun SearchOcrTip(onOpenOcr: (() -> Unit)?) {
    Spacer(Modifier.size(4.dp))
    Text(
        SEARCH_EMPTY_OCR_TIP,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (onOpenOcr != null) {
        TextButton(onClick = onOpenOcr) {
            Icon(
                Icons.Outlined.TextFields,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(PDF_HINT_OCR_ACTION)
        }
    }
}

/** Explicit, keyboard-independent result stepping with a `current / total` counter. */
@Composable
private fun SearchResultNav(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val navBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val navContent = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            onClick = onPrevious,
            shape = RoundedCornerShape(12.dp),
            color = navBackground,
            contentColor = navContent,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.search_previous_result),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            stringResource(R.string.search_result_counter, current + 1, total),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(14.dp))
        Surface(
            onClick = onNext,
            shape = RoundedCornerShape(12.dp),
            color = navBackground,
            contentColor = navContent,
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.search_next_result),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
    Spacer(Modifier.size(8.dp))
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    query: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
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
fun AnnotationSheet(
    state: ViewerUiState,
    vm: PdfViewModel,
    annotationsHidden: Boolean = false,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filter by remember { mutableStateOf(AnnotationFilter.ALL) }
    val allAnnotations = remember(state.annotations, state.pageCount) {
        PdfAnnotationFilter.filter(state.annotations, AnnotationFilter.ALL, state.pageCount)
    }
    val annotations = remember(allAnnotations, filter, state.pageCount) {
        PdfAnnotationFilter.filter(allAnnotations, filter, state.pageCount)
    }
    val pageGroups = remember(annotations) { annotations.groupBy { it.page } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.annotations_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    if (filter == AnnotationFilter.ALL) {
                        stringResource(R.string.annotations_count, allAnnotations.size)
                    } else {
                        "${annotations.size} / ${allAnnotations.size}개"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(2.dp))
            AnnotationVisibilityStatus(hidden = annotationsHidden)
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AnnotationFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text(option.label) },
                        modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            when {
                allAnnotations.isEmpty() -> {
                    Text(
                        stringResource(R.string.annotations_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                annotations.isEmpty() -> {
                    Text(
                        "선택한 유형의 주석이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }

                else -> {
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        pageGroups.forEach { (page, pageAnnotations) ->
                            item(key = "page_header_$page") {
                                AnnotationPageHeader(page = page, count = pageAnnotations.size)
                            }
                            items(pageAnnotations, key = { it.id }) { annotation ->
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
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AnnotationVisibilityStatus(hidden: Boolean) {
    val tint = if (hidden) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (hidden) "숨김 · 화면에서만 가림, 데이터 유지" else "표시 중",
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Composable
private fun AnnotationPageHeader(page: Int, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text(
                stringResource(R.string.page_number, page + 1),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "${count}개",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
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
        annotation.type == AnnotationType.COVER_TEXT && text.isNotEmpty() ->
            stringResource(R.string.annotation_summary_cover_text, text)
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
    AnnotationType.COVER_TEXT -> R.string.annotation_type_cover_text
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
                            modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
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

/**
 * Prefilled replacement dialog for the selection action bars.
 *
 * The confirmation only creates a visual `COVER_TEXT` overlay; it never edits the
 * PDF content stream. The notice is shown in the dialog so the limitation is clear
 * before the user commits the change.
 */
@Composable
fun TextEditDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // initialText is only the starting value: a selection cleared while the
    // dialog is open must not wipe what the student has already typed.
    var value by remember { mutableStateOf(initialText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.TextFields, contentDescription = null) },
        title = { Text(stringResource(R.string.text_edit_dialog_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(stringResource(R.string.text_edit_dialog_hint)) },
                    minLines = 2,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.text_edit_dialog_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

// ---------------------------------------------------------------------------
// GoodReader-style unified Locations panel
// ---------------------------------------------------------------------------

/** The three unified location tabs in their fixed visible order. */
internal enum class LocationsTab(val label: String) {
    BOOKMARKS("북마크"),
    OUTLINE("목차"),
    ANNOTATIONS("주석"),
}

internal val LOCATIONS_TAB_ORDER = listOf(
    LocationsTab.BOOKMARKS,
    LocationsTab.OUTLINE,
    LocationsTab.ANNOTATIONS,
)

/**
 * Callback router for every row in [LocationsSheet]. Keeping the jump/dismiss
 * policy in one small object makes it testable without a Compose test and makes
 * every tap behave the same: jump to the page, then dismiss the panel.
 */
internal class LocationsActions(
    private val onJumpToPage: (Int) -> Unit,
    private val onDismiss: () -> Unit,
    private val onExportSummary: (String) -> Unit,
) {
    fun jump(page: Int) {
        onJumpToPage(page)
        onDismiss()
    }

    fun export(text: String) = onExportSummary(text)

    fun close() = onDismiss()
}

/** Sanitized, page-ascending groups used by the 주석 tab. */
internal fun locationsAnnotationGroups(
    annotations: List<Annotation>,
    filter: AnnotationFilter = AnnotationFilter.ALL,
    pageCount: Int = Int.MAX_VALUE,
): Map<Int, List<Annotation>> =
    PdfAnnotationFilter.filter(annotations, filter, pageCount)
        .groupBy { it.page }
        .toSortedMap()

/** Share of the visible outline viewport kept above the current-position row. */
internal const val LOCATIONS_OUTLINE_CURRENT_VIEWPORT_FRACTION = 0.40f

/**
 * Exact current-page entry wins; otherwise the closest page entry wins. Ties and
 * duplicate page entries keep the earlier outline position, and entries without
 * a usable page cannot represent the reader's current position.
 */
internal fun nearestOutlineEntryIndex(entries: List<PdfOutlineEntry>, currentPage: Int): Int? {
    if (currentPage < 0) return null
    var nearestIndex: Int? = null
    var nearestDistance = Int.MAX_VALUE
    entries.forEachIndexed { index, entry ->
        val page = entry.pageIndex?.takeIf { it >= 0 } ?: return@forEachIndexed
        val distance = abs(page - currentPage)
        if (distance < nearestDistance) {
            nearestIndex = index
            nearestDistance = distance
        }
    }
    return nearestIndex
}

/** Negative lazy-list offset places the row about 40% down the viewport. */
internal fun locationsOutlineScrollOffsetPx(viewportHeightPx: Int): Int =
    -(viewportHeightPx.coerceAtLeast(0) * LOCATIONS_OUTLINE_CURRENT_VIEWPORT_FRACTION).roundToInt()

internal fun locationsOutlineInitialScrollTarget(
    nearestIndex: Int?,
    viewportHeightPx: Int,
): InitialListScrollTarget? = nearestIndex?.let { index ->
    InitialListScrollTarget(
        index = index.coerceAtLeast(0),
        offsetPx = locationsOutlineScrollOffsetPx(viewportHeightPx),
    )
}

/**
 * One panel for 북마크 / 목차 / 주석 with the tabs pinned above a scrolling list.
 * Bookmarks and outlines are the lists already loaded by the viewer; this sheet
 * never edits them. The 주석 tab groups by page, offers the standard five filter
 * chips and builds an offline Markdown summary without writing a file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsSheet(
    bookmarks: List<PdfBookmark>,
    outline: List<PdfOutlineEntry>,
    annotations: List<Annotation>,
    pageCount: Int,
    currentPage: Int = 0,
    onJumpToPage: (Int) -> Unit,
    onExportSummary: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Optional full managers; the unified panel stays read-only without them. */
    onManageBookmarks: (() -> Unit)? = null,
    onManageAnnotations: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(LocationsTab.BOOKMARKS) }
    var filter by remember { mutableStateOf(AnnotationFilter.ALL) }
    val actions = remember(onJumpToPage, onDismiss, onExportSummary) {
        LocationsActions(onJumpToPage, onDismiss, onExportSummary)
    }
    val sortedBookmarks = remember(bookmarks) {
        bookmarks.sortedWith(compareBy<PdfBookmark>({ it.pageIndex }, { it.createdAt }))
    }
    val allAnnotations = remember(annotations, pageCount) {
        PdfAnnotationFilter.filter(annotations, AnnotationFilter.ALL, pageCount)
    }
    val counts = remember(annotations, pageCount) {
        PdfAnnotationFilter.counts(annotations, pageCount)
    }
    val groups = remember(annotations, filter, pageCount) {
        locationsAnnotationGroups(annotations, filter, pageCount)
    }
    val summary = remember(annotations, pageCount) {
        AnnotationSummary.buildMarkdown(annotations, pageCount)
    }

    ModalBottomSheet(onDismissRequest = { actions.close() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("위치", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${bookmarks.size} 북마크 · ${outline.size} 목차 · ${allAnnotations.size} 주석",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                "북마크·목차·주석을 한 곳에서 보고 원하는 쪽으로 바로 이동하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(10.dp))

            // Sticky tabs: the tab row lives outside the scrolling list below.
            PrimaryTabRow(selectedTabIndex = LOCATIONS_TAB_ORDER.indexOf(selectedTab)) {
                LOCATIONS_TAB_ORDER.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                    )
                }
            }
            Spacer(Modifier.size(10.dp))

            when (selectedTab) {
                LocationsTab.BOOKMARKS -> LocationsBookmarksTab(
                    bookmarks = sortedBookmarks,
                    currentPage = currentPage,
                    onJump = actions::jump,
                    onManage = onManageBookmarks,
                )

                LocationsTab.OUTLINE -> LocationsOutlineTab(
                    entries = outline,
                    currentPage = currentPage,
                    onJump = actions::jump,
                )

                LocationsTab.ANNOTATIONS -> LocationsAnnotationsTab(
                    allCount = allAnnotations.size,
                    counts = counts,
                    filter = filter,
                    onFilterChange = { filter = it },
                    groups = groups,
                    summary = summary,
                    onExport = actions::export,
                    onManage = onManageAnnotations,
                    onJump = actions::jump,
                )
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LocationsBookmarksTab(
    bookmarks: List<PdfBookmark>,
    currentPage: Int,
    onJump: (Int) -> Unit,
    onManage: (() -> Unit)?,
) {
    if (onManage != null) {
        TextButton(
            onClick = onManage,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = MIN_TARGET_DP.dp),
        ) { Text("북마크 추가·이름 변경·삭제") }
        Spacer(Modifier.size(4.dp))
    }
    if (bookmarks.isEmpty()) {
        Text(
            "아직 북마크가 없습니다. PDF 메뉴에서 현재 페이지를 북마크해 두면 여기에서 바로 찾을 수 있습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 400.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(bookmarks, key = { it.id }) { bookmark ->
            Surface(
                onClick = { onJump(bookmark.pageIndex) },
                shape = RoundedCornerShape(12.dp),
                color = if (bookmark.pageIndex == currentPage) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        bookmark.title.ifBlank { "${bookmark.pageIndex + 1}쪽 북마크" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    LocationsPageChip(bookmark.pageIndex)
                }
            }
        }
    }
}

@Composable
private fun LocationsOutlineTab(
    entries: List<PdfOutlineEntry>,
    currentPage: Int,
    onJump: (Int) -> Unit,
) {
    if (entries.isEmpty()) {
        Text(
            "이 PDF에는 목차가 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }
    val nearestIndex = remember(entries, currentPage) { nearestOutlineEntryIndex(entries, currentPage) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = nearestIndex ?: 0)
    val scrollPolicy = remember { InitialListScrollPolicy() }

    // The tab is recreated on each open. Seed the nearest row, then place it at
    // 40% once the actual viewport is measured; recomposition never repositions it.
    LaunchedEffect(nearestIndex) {
        if (nearestIndex == null) return@LaunchedEffect
        val viewportHeightPx = snapshotFlow {
            val layout = listState.layoutInfo
            (layout.viewportEndOffset - layout.viewportStartOffset -
                layout.beforeContentPadding - layout.afterContentPadding).coerceAtLeast(0)
        }.first { it > 0 }
        val target = locationsOutlineInitialScrollTarget(nearestIndex, viewportHeightPx)
            ?: return@LaunchedEffect
        if (!scrollPolicy.shouldApply(target)) return@LaunchedEffect
        listState.scrollToItem(target.index, target.offsetPx)
        scrollPolicy.markApplied(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(entries) { index, entry ->
            val page = entry.pageIndex
            val isNearest = index == nearestIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isNearest) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            Color.Transparent
                        },
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = page != null) { page?.let(onJump) }
                    .semantics(mergeDescendants = true) {
                        if (isNearest) {
                            stateDescription = if (page == currentPage) {
                                "현재 페이지 목차"
                            } else {
                                "현재 페이지와 가장 가까운 목차"
                            }
                        }
                    }
                    .padding(
                        start = (entry.level.coerceIn(0, 6) * 16).dp,
                        top = 10.dp,
                        bottom = 10.dp,
                        end = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (page != null) {
                    Spacer(Modifier.width(8.dp))
                    LocationsPageChip(page)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun LocationsAnnotationsTab(
    allCount: Int,
    counts: Map<AnnotationFilter, Int>,
    filter: AnnotationFilter,
    onFilterChange: (AnnotationFilter) -> Unit,
    groups: Map<Int, List<Annotation>>,
    summary: String,
    onExport: (String) -> Unit,
    onManage: (() -> Unit)?,
    onJump: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("주석", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            if (filter == AnnotationFilter.ALL) "${allCount}개" else "${counts[filter] ?: 0} / ${allCount}개",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Spacer(Modifier.size(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AnnotationFilter.entries.forEach { option ->
            FilterChip(
                selected = filter == option,
                onClick = { onFilterChange(option) },
                label = { Text("${option.label} ${counts[option] ?: 0}") },
                modifier = Modifier.sizeIn(minHeight = MIN_TARGET_DP.dp),
            )
        }
    }
    Spacer(Modifier.size(10.dp))
    Button(
        onClick = { onExport(summary) },
        enabled = allCount > 0,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            Icons.Outlined.FileDownload,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("주석 요약 내보내기")
    }
    if (onManage != null) {
        Spacer(Modifier.size(4.dp))
        TextButton(
            onClick = onManage,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = MIN_TARGET_DP.dp),
        ) { Text("주석 목록·삭제") }
    }
    Spacer(Modifier.size(10.dp))

    when {
        allCount == 0 -> Text(
            "이 문서에는 주석이 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 20.dp),
        )

        groups.isEmpty() -> Text(
            "선택한 유형의 주석이 없습니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 20.dp),
        )

        else -> LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            groups.forEach { (page, pageAnnotations) ->
                item(key = "loc_page_header_$page") {
                    AnnotationPageHeader(page = page, count = pageAnnotations.size)
                }
                items(pageAnnotations, key = { it.id }) { annotation ->
                    LocationsAnnotationRow(annotation = annotation, onJump = onJump)
                }
            }
        }
    }
}

@Composable
private fun LocationsAnnotationRow(annotation: Annotation, onJump: (Int) -> Unit) {
    Surface(
        onClick = { onJump(annotation.page) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Color chip with a type icon: black stays black for redactions.
            Box(
                Modifier
                    .size(30.dp)
                    .background(
                        if (annotation.type == AnnotationType.REDACT) Color.Black else Color(annotation.color),
                        RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    locationsAnnotationIcon(annotation.type),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(annotationTypeLabelRes(annotation.type)),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    annotationSummary(annotation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            LocationsPageChip(annotation.page)
        }
    }
}

@Composable
private fun LocationsPageChip(page: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            stringResource(R.string.page_number, page + 1),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun locationsAnnotationIcon(type: AnnotationType): ImageVector = when (type) {
    AnnotationType.PEN,
    AnnotationType.SIGNATURE,
    -> Icons.Outlined.Draw

    AnnotationType.HIGHLIGHT -> Icons.Outlined.Highlight
    AnnotationType.UNDERLINE -> Icons.Outlined.FormatUnderlined
    AnnotationType.STRIKEOUT -> Icons.Outlined.FormatStrikethrough
    AnnotationType.TEXT,
    AnnotationType.STAMP,
    AnnotationType.COVER_TEXT,
    -> Icons.Outlined.TextFields

    AnnotationType.RECT -> Icons.Outlined.CropSquare
    AnnotationType.ELLIPSE -> Icons.Outlined.Circle
    AnnotationType.LINE -> Icons.Outlined.HorizontalRule
    AnnotationType.ARROW -> Icons.Outlined.NorthEast
    AnnotationType.REDACT -> Icons.Outlined.VisibilityOff
}
