package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PressureCurve
import com.hackerdjakodjo.mypdfwriter.data.pressureCurveRange

/** Shared label so the study-card sub-sheet and tests use one vocabulary. */
internal const val NOTE_MEMO_CARD_LABEL = "포스트잇"

/**
 * Local capture and external hand-off are different actions. Every row that puts
 * selected content into the student's note says [NOTE_CAPTURE_LABEL]; 번역/사전
 * remain explicit hand-offs to another app and are de-emphasised in menus.
 */
internal val NOTE_CAPTURE_VOCABULARY: List<String> =
    listOf(NOTE_CAPTURE_LABEL) + STUDENT_HANDOFF_LABELS

/**
 * Student-facing menu vocabulary. The legacy terms were `학습 카드`/`가림 카드`;
 * the menu now says `복습 카드`/`가리기 카드` while the underlying cards, sheets
 * and identifiers stay exactly the same. `필기 설정` is already student-friendly
 * and keeps its name.
 */
internal const val NOTE_REVIEW_CARDS_LABEL = "복습 카드"
internal const val NOTE_MASKING_REVIEW_CARD_LABEL = "가리기 카드"
internal const val NOTE_INK_SETTINGS_LABEL = "필기 설정"

/** Session-only refinement labels and defaults (never written to a notebook). */
internal const val NOTE_SMOOTH_HANDWRITING_LABEL = "부드러운 필기"
internal const val NOTE_SIMPLIFY_POINTS_LABEL = "점 간소화"
internal const val NOTE_SMOOTH_HANDWRITING_DEFAULT = true
internal const val NOTE_SIMPLIFY_POINTS_DEFAULT = false

/** Quick actions pinned above the eleven indexed rows of 노트 메뉴. */
internal enum class NoteMenuQuickAction {
    UNDO,
    REDO,
}

internal val NOTE_MENU_QUICK_ACTIONS = listOf(
    NoteMenuQuickAction.UNDO,
    NoteMenuQuickAction.REDO,
)

internal fun noteMenuQuickActionLabel(action: NoteMenuQuickAction): String = when (action) {
    NoteMenuQuickAction.UNDO -> "실행 취소"
    NoteMenuQuickAction.REDO -> "다시 실행"
}

/** Every menu row is tall enough for TalkBack and large fonts. */
internal const val NOTE_MENU_ROW_MIN_HEIGHT_DP = 56

/**
 * Two-state Korean label used for favourites and toggle rows. [stateLabel]
 * supplies the separator and ordering so the spoken strings stay consistent
 * across the top bar, the note menu and the ink settings sheet.
 */
internal fun noteStateAwareLabel(label: String, selected: Boolean): String =
    stateLabel(label, true, if (selected) "선택됨" else "선택 안 됨")

/** State-only description for a switch row, kept separate from its name. */
internal fun noteToggleStateDescription(checked: Boolean): String =
    stateLabel("", true, if (checked) "선택됨" else "선택 안 됨")

/**
 * The main note menu is a short index, not a junk drawer. Page operations live in
 * the page manager, card overlays live in the study-card sheets, and the ten ink
 * switches live in the ink-settings sub-sheet.
 */
internal enum class NoteMenuSection(val label: String) {
    NOTE("노트"),
    REVIEW("복습"),
    EXPORT("내보내기"),
    HELP("도움말"),
}

/** Four groups in reading order; kept explicit so tests and UI cannot drift. */
internal val NOTE_MENU_SECTION_ORDER: List<NoteMenuSection> = NoteMenuSection.entries

internal enum class NoteMenuEntry(val section: NoteMenuSection) {
    RENAME(NoteMenuSection.NOTE),
    PAGES(NoteMenuSection.NOTE),
    TEMPLATES(NoteMenuSection.NOTE),
    LAYERS(NoteMenuSection.NOTE),
    SEARCH(NoteMenuSection.NOTE),
    STUDY_CARDS(NoteMenuSection.REVIEW),
    AUDIO(NoteMenuSection.REVIEW),
    INK_SETTINGS(NoteMenuSection.REVIEW),
    EXPORT_PDF(NoteMenuSection.EXPORT),
    SOURCE_PDF(NoteMenuSection.EXPORT),
    HELP(NoteMenuSection.HELP),
}

/** Rows shown in the main sheet, in order: 9–11 depending on the note. */
internal fun noteMenuEntries(
    hasRename: Boolean,
    hasSourcePdf: Boolean,
): List<NoteMenuEntry> = buildList {
    if (hasRename) add(NoteMenuEntry.RENAME)
    add(NoteMenuEntry.PAGES)
    add(NoteMenuEntry.TEMPLATES)
    add(NoteMenuEntry.LAYERS)
    add(NoteMenuEntry.SEARCH)
    add(NoteMenuEntry.STUDY_CARDS)
    add(NoteMenuEntry.AUDIO)
    add(NoteMenuEntry.INK_SETTINGS)
    add(NoteMenuEntry.EXPORT_PDF)
    if (hasSourcePdf) add(NoteMenuEntry.SOURCE_PDF)
    add(NoteMenuEntry.HELP)
}

/**
 * Every ink option the editor owns. Pressure curve is a choice row rather than a
 * switch, but it belongs to the same settings group and is listed with the other
 * options so none can silently disappear. The two refinement toggles are
 * session-only: they live in Compose state and are never persisted to a notebook.
 */
internal enum class NoteInkSetting {
    FINGER_WRITING,
    ZOOM_LOCK,
    STROKE_STABILIZATION,
    SMOOTH_HANDWRITING,
    SIMPLIFY_POINTS,
    SHAPE_RECOGNITION,
    PARTIAL_ERASER,
    PRESSURE_INK,
    PRESSURE_CURVE,
    GESTURE_UNDO,
}

internal val NOTE_INK_SETTINGS = NoteInkSetting.entries

/** Frequent, easy-to-understand switches stay first; fine ink tuning is folded. */
internal fun noteInkSettingIsAdvanced(setting: NoteInkSetting): Boolean = when (setting) {
    NoteInkSetting.STROKE_STABILIZATION,
    NoteInkSetting.SMOOTH_HANDWRITING,
    NoteInkSetting.SIMPLIFY_POINTS,
    NoteInkSetting.PRESSURE_INK,
    NoteInkSetting.PRESSURE_CURVE,
    -> true
    else -> false
}

/** Supporting line for the ink-settings row; the count must track the enum. */
internal fun noteInkSettingsSupportingLabel(): String =
    "손가락 필기·보정·필압 등 ${NOTE_INK_SETTINGS.size}가지"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteMenuSheet(
    notebookTitle: String,
    pageIndex: Int,
    pageCount: Int,
    templateLabel: String,
    layerName: String,
    layerCount: Int,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRename: (() -> Unit)?,
    onPages: () -> Unit,
    onTemplates: () -> Unit,
    onLayers: () -> Unit,
    onSearch: () -> Unit,
    onStudyCards: () -> Unit,
    onAudio: () -> Unit,
    onInkSettings: () -> Unit,
    onExportPdf: () -> Unit,
    onOpenSourcePdf: (() -> Unit)?,
    onOpenHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entries = noteMenuEntries(
        hasRename = onRename != null,
        hasSourcePdf = onOpenSourcePdf != null,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("노트 메뉴", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "$notebookTitle · ${pageIndex + 1} / $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // One favourite action, owned by the menu in both the full
                    // editor and the compact study pane (neither top bar has it).
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = noteStateAwareLabel("즐겨찾기", favorite),
                            tint = if (favorite) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }

            // Student quick actions sit above the indexed rows. Undo/redo are the
            // only path back to history in the compact pane, which has no app bar.
            item {
                NoteMenuQuickActions(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onUndo = onUndo,
                    onRedo = onRedo,
                )
            }

            var previousSection: NoteMenuSection? = null
            entries.forEach { entry ->
                if (entry.section != previousSection) {
                    if (previousSection != null) item { NoteMenuDivider() }
                    item { NoteMenuSectionHeader(entry.section.label) }
                    previousSection = entry.section
                }
                item {
                    when (entry) {
                        NoteMenuEntry.RENAME -> NoteMenuRow(
                            icon = Icons.Filled.Edit,
                            title = "이름 변경",
                            supporting = "이 노트의 제목 수정",
                            onClick = onRename ?: {},
                        )

                        NoteMenuEntry.PAGES -> NoteMenuRow(
                            icon = Icons.Filled.Bookmarks,
                            title = NOTE_PAGE_MANAGER_LABEL,
                            supporting = "$pageCount 쪽 · 현재 ${pageIndex + 1}쪽 · 순서·회전·복제",
                            onClick = onPages,
                        )

                        NoteMenuEntry.TEMPLATES -> NoteMenuRow(
                            icon = Icons.Filled.Dashboard,
                            title = "템플릿",
                            supporting = "빠른 템플릿 · 스튜디오 · 현재: $templateLabel",
                            onClick = onTemplates,
                        )

                        NoteMenuEntry.LAYERS -> NoteMenuRow(
                            icon = Icons.Filled.Layers,
                            title = "레이어",
                            supporting = "${layerCount}개 · $layerName",
                            onClick = onLayers,
                        )

                        NoteMenuEntry.SEARCH -> NoteMenuRow(
                            icon = Icons.Filled.Search,
                            title = "노트 검색",
                            supporting = "메모·글상자·도장·서명·포스트잇·카드에서 찾기",
                            onClick = onSearch,
                        )

                        NoteMenuEntry.STUDY_CARDS -> NoteMenuRow(
                            icon = Icons.Filled.Checklist,
                            title = NOTE_REVIEW_CARDS_LABEL,
                            supporting = "$NOTE_MEMO_CARD_LABEL · $NOTE_MASKING_REVIEW_CARD_LABEL · $NOTE_CHECKLIST_LABEL",
                            onClick = onStudyCards,
                        )

                        NoteMenuEntry.AUDIO -> NoteMenuRow(
                            icon = Icons.Filled.Mic,
                            title = "오디오",
                            supporting = "오디오 녹음 · 필기 다시보기",
                            onClick = onAudio,
                        )

                        NoteMenuEntry.INK_SETTINGS -> NoteMenuRow(
                            icon = Icons.Filled.Draw,
                            title = NOTE_INK_SETTINGS_LABEL,
                            supporting = noteInkSettingsSupportingLabel(),
                            onClick = onInkSettings,
                        )

                        NoteMenuEntry.EXPORT_PDF -> NoteMenuRow(
                            icon = Icons.Filled.FileDownload,
                            title = "PDF로 내보내기",
                            supporting = "현재 노트를 PDF 파일로 저장",
                            onClick = onExportPdf,
                        )

                        NoteMenuEntry.SOURCE_PDF -> NoteMenuRow(
                            icon = Icons.Filled.PictureAsPdf,
                            title = "원본 PDF 열기",
                            supporting = "연결된 PDF에서 열기",
                            onClick = onOpenSourcePdf ?: {},
                        )

                        NoteMenuEntry.HELP -> NoteMenuRow(
                            icon = Icons.AutoMirrored.Outlined.HelpOutline,
                            title = "도움말",
                            supporting = "오프라인 사용 가이드 · 문제 해결",
                            onClick = onOpenHelp,
                        )
                    }
                }
            }
        }
    }
}

/** Three one-tap actions pinned to the top of the note menu. */
@Composable
private fun NoteMenuQuickActions(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TARGET_DP.dp)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NOTE_MENU_QUICK_ACTIONS.forEach { action ->
            val label = noteMenuQuickActionLabel(action)
            val enabled = when (action) {
                NoteMenuQuickAction.UNDO -> canUndo
                NoteMenuQuickAction.REDO -> canRedo
            }
            val icon = when (action) {
                NoteMenuQuickAction.UNDO -> Icons.AutoMirrored.Filled.Undo
                NoteMenuQuickAction.REDO -> Icons.AutoMirrored.Filled.Redo
            }
            val description = when (action) {
                NoteMenuQuickAction.UNDO ->
                    if (canUndo) label else disabledReasonLabel(label, "취소할 내용 없음")
                NoteMenuQuickAction.REDO ->
                    if (canRedo) label else disabledReasonLabel(label, "다시 실행할 내용 없음")
            }
            val onClick = when (action) {
                NoteMenuQuickAction.UNDO -> onUndo
                NoteMenuQuickAction.REDO -> onRedo
            }
            Surface(
                onClick = onClick,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MIN_TARGET_DP.dp)
                    .semantics { contentDescription = description },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteMenuSubSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
            content()
        }
    }
}

/** One template entry that leads to both the quick sheet and the studio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteTemplateMenuSheet(
    templateLabel: String,
    onQuickTemplates: () -> Unit,
    onStudio: () -> Unit,
    onDismiss: () -> Unit,
) {
    NoteMenuSubSheet(
        title = "템플릿",
        subtitle = "현재: $templateLabel",
        onDismiss = onDismiss,
    ) {
        NoteMenuRow(
            icon = Icons.Filled.Dashboard,
            title = "빠른 템플릿",
            supporting = "종이·방향·색을 한 번에 바꾸기",
            onClick = onQuickTemplates,
        )
        NoteMenuDivider()
        NoteMenuRow(
            icon = Icons.Filled.Palette,
            title = "템플릿 스튜디오",
            supporting = "방향·색조·표지·커스텀 배경",
            onClick = onStudio,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteStudyCardMenuSheet(
    onMaskingCards: () -> Unit,
    onChecklists: () -> Unit,
    onMemoCards: () -> Unit,
    onDismiss: () -> Unit,
) {
    NoteMenuSubSheet(
        title = NOTE_REVIEW_CARDS_LABEL,
        subtitle = "오프라인 복습 · 페이지에 표시 여부는 각 카드 화면에서",
        onDismiss = onDismiss,
    ) {
        NoteMenuRow(
            icon = Icons.Filled.Dashboard,
            title = NOTE_MEMO_CARD_LABEL,
            supporting = "페이지에 붙여 둔 짧은 쪽지 · 색상·크기 조절",
            onClick = onMemoCards,
        )
        NoteMenuDivider()
        NoteMenuRow(
            icon = Icons.Filled.AutoFixNormal,
            title = NOTE_MASKING_REVIEW_CARD_LABEL,
            supporting = "질문을 가리고 정답을 확인하는 복습 카드",
            onClick = onMaskingCards,
        )
        NoteMenuDivider()
        NoteMenuRow(
            icon = Icons.Filled.Checklist,
            title = NOTE_CHECKLIST_LABEL,
            supporting = "항목을 완료하며 진행률을 확인",
            onClick = onChecklists,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteAudioMenuSheet(
    onAudioNotes: () -> Unit,
    onAudioReplay: () -> Unit,
    onDismiss: () -> Unit,
) {
    NoteMenuSubSheet(
        title = "오디오",
        subtitle = "이 페이지에 녹음하고 필기와 함께 다시 보기",
        onDismiss = onDismiss,
    ) {
        NoteMenuRow(
            icon = Icons.Filled.Mic,
            title = "오디오 녹음",
            supporting = "이 페이지에 녹음 추가·재생",
            onClick = onAudioNotes,
        )
        NoteMenuDivider()
        NoteMenuRow(
            icon = Icons.Filled.PlayArrow,
            title = "필기 다시보기",
            supporting = "녹음과 필기를 함께 재생",
            onClick = onAudioReplay,
        )
    }
}

/** All ten ink options, grouped in one predictable sub-sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteInkSettingsSheet(
    fingerWriting: Boolean,
    zoomLock: Boolean,
    strokeStabilization: Boolean,
    smoothHandwriting: Boolean,
    simplifyPoints: Boolean,
    shapeRecognition: Boolean,
    partialEraser: Boolean,
    pressureInk: Boolean,
    pressureCurve: PressureCurve,
    gestureUndo: Boolean,
    onFingerWriting: (Boolean) -> Unit,
    onZoomLock: (Boolean) -> Unit,
    onStrokeStabilization: (Boolean) -> Unit,
    onSmoothHandwriting: (Boolean) -> Unit,
    onSimplifyPoints: (Boolean) -> Unit,
    onShapeRecognition: (Boolean) -> Unit,
    onPartialEraser: (Boolean) -> Unit,
    onPressureInk: (Boolean) -> Unit,
    onPressureCurve: (PressureCurve) -> Unit,
    onGestureUndo: (Boolean) -> Unit,
    onOpenTrace: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    NoteMenuSubSheet(
        title = NOTE_INK_SETTINGS_LABEL,
        subtitle = "모든 변경은 이 기기에서 즉시 적용됩니다",
        onDismiss = onDismiss,
    ) {
        var showAdvanced by remember { mutableStateOf(false) }
        NOTE_INK_SETTINGS.filter { !noteInkSettingIsAdvanced(it) || showAdvanced }
            .forEach { setting ->
            when (setting) {
                NoteInkSetting.FINGER_WRITING -> NoteMenuToggleRow(
                    icon = Icons.Filled.Draw,
                    title = "손가락 필기",
                    supporting = if (fingerWriting) {
                        "손가락으로 그리고 두 손가락으로 이동"
                    } else {
                        "스타일러스로 그리고 손가락으로 이동"
                    },
                    checked = fingerWriting,
                    onCheckedChange = onFingerWriting,
                )

                NoteInkSetting.ZOOM_LOCK -> NoteMenuToggleRow(
                    icon = Icons.Filled.Lock,
                    title = "줌 잠금",
                    supporting = "필기 중 페이지 확대·이동 잠금",
                    checked = zoomLock,
                    onCheckedChange = onZoomLock,
                )

                NoteInkSetting.STROKE_STABILIZATION -> NoteMenuToggleRow(
                    icon = Icons.Filled.Draw,
                    title = "필기 보정",
                    supporting = "획의 미세한 떨림을 부드럽게 정리 (강도 25%)",
                    checked = strokeStabilization,
                    onCheckedChange = onStrokeStabilization,
                )

                NoteInkSetting.SMOOTH_HANDWRITING -> NoteMenuToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = NOTE_SMOOTH_HANDWRITING_LABEL,
                    supporting = if (smoothHandwriting) {
                        "레이지 브러시와 차이킨 보정으로 획을 매끄럽게 저장"
                    } else {
                        "획을 추가로 매끄럽게 하지 않고 그대로 저장"
                    },
                    checked = smoothHandwriting,
                    onCheckedChange = onSmoothHandwriting,
                )

                NoteInkSetting.SIMPLIFY_POINTS -> NoteMenuToggleRow(
                    icon = Icons.Filled.AutoFixNormal,
                    title = NOTE_SIMPLIFY_POINTS_LABEL,
                    supporting = if (simplifyPoints) {
                        "RDP로 불필요한 점을 줄여 획과 내보내기 파일을 가볍게"
                    } else {
                        "모든 샘플 점을 유지"
                    },
                    checked = simplifyPoints,
                    onCheckedChange = onSimplifyPoints,
                )

                NoteInkSetting.SHAPE_RECOGNITION -> NoteMenuToggleRow(
                    icon = Icons.Filled.Palette,
                    title = "도형 자동 인식",
                    supporting = "펜으로 그린 선·사각형·원을 도형으로 변환",
                    checked = shapeRecognition,
                    onCheckedChange = onShapeRecognition,
                )

                NoteInkSetting.PARTIAL_ERASER -> NoteMenuToggleRow(
                    icon = Icons.Filled.AutoFixNormal,
                    title = "부분 지우개",
                    supporting = if (partialEraser) {
                        "닿은 부분만 지우고 획을 나눔 · 글상자/도장/도형은 통째로"
                    } else {
                        "닿은 주석을 통째로 지움"
                    },
                    checked = partialEraser,
                    onCheckedChange = onPartialEraser,
                )

                NoteInkSetting.PRESSURE_INK -> NoteMenuToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = "필압 잉크",
                    supporting = if (pressureInk) {
                        "스타일러스 필압으로 굵기·잉크 농도·붓 번짐·연필 진하기 변화"
                    } else {
                        "모든 점을 압력 1로 기록"
                    },
                    checked = pressureInk,
                    onCheckedChange = onPressureInk,
                )

                NoteInkSetting.PRESSURE_CURVE -> NotePressureCurveRow(
                    curve = pressureCurve,
                    enabled = pressureInk,
                    onCurve = onPressureCurve,
                )

                NoteInkSetting.GESTURE_UNDO -> NoteMenuToggleRow(
                    icon = Icons.Filled.Gesture,
                    title = "제스처 실행 취소",
                    supporting = if (gestureUndo) {
                        "두 손가락 탭 실행 취소 · 세 손가락 탭 다시 실행"
                    } else {
                        "다중 손가락 탭 제스처 사용 안 함"
                    },
                    checked = gestureUndo,
                    onCheckedChange = onGestureUndo,
                )
            }
            }

        NoteMenuToggleRow(
            icon = Icons.Filled.Tune,
            title = "고급 필기 설정",
            supporting = "보정·필압 등 ${
                NOTE_INK_SETTINGS.count(::noteInkSettingIsAdvanced)
            }가지",
            checked = showAdvanced,
            onCheckedChange = { showAdvanced = it },
        )

        if (onOpenTrace != null) {
            NoteMenuDivider()
            NoteMenuSectionHeader("디버그")
            NoteMenuRow(
                icon = Icons.Filled.BugReport,
                title = "필기 추적",
                supporting = "획·저장·주석 수 변화 기록 보기",
                onClick = onOpenTrace,
            )
        }
    }
}

@Composable
private fun NoteMenuSectionHeader(label: String) {
    Text(
        label,
        modifier = Modifier
            .padding(start = 10.dp, top = 10.dp, bottom = 2.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun NoteMenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun NoteMenuRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NOTE_MENU_ROW_MIN_HEIGHT_DP.dp)
            .semantics { contentDescription = menuLabel(title, supporting.orEmpty()) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (!supporting.isNullOrBlank()) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = studentLabelMaxLines(220, supporting.length * 7),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteMenuToggleRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        // One focus target only: the row owns the click, the name and the state.
        // The child Switch stays tappable but clears its own semantics so
        // TalkBack never offers a second, identical toggle.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NOTE_MENU_ROW_MIN_HEIGHT_DP.dp)
            .semantics {
                contentDescription = noteStateAwareLabel(title, checked)
                stateDescription = noteToggleStateDescription(checked)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

private fun pressureCurveLabel(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "부드럽게"
    PressureCurve.LINEAR -> "선형"
    PressureCurve.FIRM -> "강하게"
}

private fun pressureCurveDescription(curve: PressureCurve): String = when (curve) {
    PressureCurve.SOFT -> "가벼운 필압을 더 굵게 반영"
    PressureCurve.LINEAR -> "필압에 비례해 굵기 변화"
    PressureCurve.FIRM -> "충분히 눌러야 굵어짐"
}

@Composable
private fun NotePressureCurveRow(
    curve: PressureCurve,
    enabled: Boolean,
    onCurve: (PressureCurve) -> Unit,
) {
    // 25% and 75% are a compact, device-neutral illustration only. Real stylus
    // digitizers still need on-device pressure tuning.
    val sample = remember(curve) { pressureCurveRange(curve, 0.25f, 0.75f) }
    val diagnostic = "필압 25→75% = 반영 " +
        "${(sample.effectiveMin * 100).toInt()}→${(sample.effectiveMax * 100).toInt()}%"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NOTE_MENU_ROW_MIN_HEIGHT_DP.dp)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Draw,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("필압 곡선", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${pressureCurveDescription(curve)} · $diagnostic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 34.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PressureCurve.entries.forEach { option ->
                FilterChip(
                    selected = curve == option,
                    onClick = { onCurve(option) },
                    enabled = enabled,
                    label = { Text(pressureCurveLabel(option)) },
                    modifier = Modifier.heightIn(min = MIN_TARGET_DP.dp),
                )
            }
        }
    }
}
