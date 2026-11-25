@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Stable identity for a help topic; also used as the Compose lazy-list key. */
enum class HelpTopicKey {
    QUICK_START,
    OPEN_PDF,
    TEXT_SELECTION,
    STUDY_NOTE,
    WRITE_TOOLS,
    SEARCH_NAV,
    BACKUP,
    TROUBLESHOOT,
}

/**
 * One offline help topic. Content is plain strings only (no links, web views,
 * assets, fonts or analytics) so it is fully usable without a network and easy
 * to verify in a pure JVM test.
 */
internal data class HelpTopic(
    val key: HelpTopicKey,
    val chip: String,
    val title: String,
    val subtitle: String,
    val steps: List<String>,
    val note: String? = null,
    val caution: String? = null,
) {
    /** Every user-visible string, flattened for offline/link content checks. */
    internal fun allText(): String = buildString {
        append(chip).append('\n')
        append(title).append('\n')
        append(subtitle).append('\n')
        steps.forEach { append(it).append('\n') }
        note?.let { append(it).append('\n') }
        caution?.let { append(it) }
    }
}

/**
 * Canonical in-app guidance, derived from `docs/usage-guide.html` but kept
 * short and actionable. This is the single source of truth for both the sheet
 * and its JVM tests.
 */
internal fun helpTopics(): List<HelpTopic> = listOf(
    HelpTopic(
        key = HelpTopicKey.QUICK_START,
        chip = "60초 빠른 시작",
        title = "60초 빠른 시작",
        subtitle = "처음 60초면 읽기·표시·노트·복습까지 한 번에",
        steps = listOf(
            "홈에서 PDF 가져오기 또는 이어서 읽기로 강의자료를 엽니다.",
            "하단 도구 독의 텍스트 선택을 누르고 중요한 문장을 드래그합니다.",
            "플로팅 바에서 형광펜을 눌러 표시를 PDF에 저장합니다.",
            "하단 도구 독의 노트를 눌러 ${UiCopy.STUDY_NOTE}(${UiCopy.STUDY_NOTE_DESCRIPTION})를 엽니다.",
            "가운데 손잡이를 위로 올려 노트를 넓히고 펜으로 필기합니다.",
            "노트 헤더 링크로 원본 페이지를 오가고, 홈 설정의 백업/복원으로 작업을 보관합니다.",
        ),
        note = "광고·계정·인터넷 없이 기기 안에서만 동작합니다.",
    ),
    HelpTopic(
        key = HelpTopicKey.OPEN_PDF,
        chip = "PDF 열기/읽기",
        title = "PDF 열기/읽기",
        subtitle = "파일을 열고 페이지를 탐색하는 가장 빠른 길",
        steps = listOf(
            "홈의 PDF 가져오기 또는 PDF 탭에서 파일 선택기로 PDF를 고릅니다.",
            "다른 앱에서 PDF를 공유하면 공유 시트에서 My PDF Writer를 선택해 바로 가져올 수 있습니다.",
            "홈의 정렬 버튼으로 이름·최근 열기·페이지 수순을 바꿀 수 있습니다.",
            "${PDF_HINT_VIEW_ZOOM_TEXT}. 페이지를 더블 탭하면 화면 맞춤과 200%가 전환됩니다. 화면에 맞추기에서는 좌우로 스와이프해 페이지를 넘기고, 확대 후에는 한 손가락으로 페이지를 이동합니다. 페이지 이동은 ${PDF_HINT_PAGE_NAVIGATION_TEXT}(‹ 번호 ›)으로 하며 PDF를 가리지 않습니다.",
            "상단 검색 아이콘은 PDF 검색을 바로 열고, PDF 메뉴는 학습 노트·페이지 메모·북마크·PDF 내보내기·공유·도움말 여섯 작업을 먼저 보여 줍니다.",
            "전체 도구 보기 뒤 보기·주석·노트·파일·설정 묶음이 이어지고, 페이지 잠금·문서 정보·텍스트 복사는 추가 도구에 모여 있습니다.",
            "${UiCopy.PAGE_MEMO}는 ${UiCopy.PAGE_MEMO_DESCRIPTION}이고, ${UiCopy.POST_IT}는 ${UiCopy.POST_IT_DESCRIPTION}입니다.",
            "화면 설정에서 야간 모드와 문서별 밝기를 조절할 수 있습니다.",
            "페이지 잠금을 켜면 실수로 페이지가 넘어가지 않습니다. 아래 잠금 배지의 해제로 풉니다.",
            "내부 링크가 있는 PDF는 보기 도구에서 링크를 탭하면 해당 페이지로 이동합니다.",
        ),
        note = "PDF 메뉴의 전체 도구 보기 뒤 그룹: 보기 · 주석 · 노트 · 파일·설정, 자주 쓰지 않는 도구는 추가 도구에서 엽니다.",
    ),
    HelpTopic(
        key = HelpTopicKey.TEXT_SELECTION,
        chip = "텍스트 선택·형광펜",
        title = "텍스트 선택·형광펜",
        subtitle = "텍스트 PDF에서 단어를 고르고 바로 표시하기",
        steps = listOf(
            "하단 도구의 텍스트 선택을 누르면 단어 박스가 표시됩니다.",
            "한 단어는 짧게 탭하고, 여러 단어는 첫 단어에서 끝 단어까지 드래그합니다.",
            "선택 바에서 형광펜·밑줄·취소선을 눌러 표시를 저장합니다.",
            "같은 바의 복사·${UiCopy.CAPTURE_TO_NOTE}를 쓰거나 더보기에서 번역·사전·공유를 실행할 수 있습니다. ${UiCopy.CAPTURE_TO_NOTE}는 ${UiCopy.CAPTURE_TO_NOTE_DESCRIPTION}.",
        ),
        note = "표시가 안 보이면 PDF 메뉴의 주석 숨기기가 켜져 있는지 확인하세요. 숨김은 화면에만 적용되고 주석은 유지됩니다.",
        caution = "이미지(스캔) PDF는 텍스트 레이어가 없어 선택할 수 없습니다. 홈 설정의 PDF 도구에서 OCR을 먼저 사용하세요.",
    ),
    HelpTopic(
        key = HelpTopicKey.STUDY_NOTE,
        chip = "${UiCopy.STUDY_NOTE} + 높이",
        title = "PDF 위 ${UiCopy.STUDY_NOTE} + 높이 드래그",
        subtitle = "PDF와 연결된 학습 노트를 펼치고 두 영역의 높이를 조절하기",
        steps = listOf(
            "하단 도구 독의 노트를 누르면 아래에 ${UiCopy.STUDY_NOTE}(${UiCopy.STUDY_NOTE_DESCRIPTION})가 열리고 위에는 PDF가 남습니다.",
            "기본 높이는 70%입니다. 두 영역 사이의 가운데 손잡이를 위아래로 드래그하세요.",
            "위로 드래그하면 노트가 커지고(최대 85%), 아래로 드래그하면 PDF가 커집니다(노트 최소 35%).",
            "노트 헤더의 크기 팝업에서 50/70/85%를 고를 수도 있습니다.",
            "분할 보기에서 페이지 이동은 ${PDF_HINT_PAGE_NAVIGATION_TEXT}(‹ 번호 ›)에 있어 PDF를 가리지 않습니다.",
            "PDF에서 선택한 내용을 ${UiCopy.CAPTURE_TO_NOTE}로 ${UiCopy.STUDY_NOTE}에 저장하고, 링크 아이콘으로 원본 PDF 페이지를 오갑니다.",
        ),
        note = "${UiCopy.STUDY_NOTE}: ${UiCopy.STUDY_NOTE_DESCRIPTION} · ${UiCopy.PAGE_MEMO}: ${UiCopy.PAGE_MEMO_DESCRIPTION} · ${UiCopy.POST_IT}: ${UiCopy.POST_IT_DESCRIPTION}. 노트를 닫아도 자동 저장되고 PDF는 같은 페이지에 남습니다.",
    ),
    HelpTopic(
        key = HelpTopicKey.WRITE_TOOLS,
        chip = "필기 도구·가리기 카드",
        title = "필기 도구·가리기 카드",
        subtitle = "펜·지우개·도형과 오프라인 가리기 카드 학습",
        steps = listOf(
            "PDF 하단 독의 여섯 슬롯은 보기·형광펜·펜·지우개·텍스트 선택·노트입니다. 펜·형광펜을 길게 누르면 해당 도구의 스타일 설정이 바로 열립니다.",
            "PDF의 글상자·도형·밑줄·스탬프는 주석 도구 시트에서 고릅니다.",
            "노트 하단 독은 펜·형광펜·지우개·선택·글상자·도형입니다.",
            "${PDF_HINT_STYLE_UNDO_TEXT} 있습니다. 펜·형광펜을 길게 눌러 스타일을 바로 열 수도 있습니다.",
            "스타일에서 색·굵기·펜 종류(볼펜/만년필/붓/연필)를 조절합니다. 필압 반응 곡선은 노트 메뉴 → 필기 설정에서 바꿀 수 있고, 펜을 누르면 필압 범위가 자동으로 보정됩니다. 필압은 굵기뿐 아니라 잉크 농도, 붓 번짐, 연필 진하기에도 반영됩니다. 빠른 획은 가늘고 옅게, 느린 획은 굵고 진하게 그려지고, 펜을 한곳에 멈추고 누르면 만년필·붓에 잉크가 고입니다. 필압 잡음은 자동으로 부드럽게 걸러집니다. 손가락·마우스는 압력 1로 저장되므로 곡선이 적용되지 않습니다.",
            "노트 편집기에서만 두 손가락 탭은 실행 취소, 세 손가락 탭은 다시 실행입니다. 필기 중에는 동작하지 않으며, PDF에서는 실행 취소·다시 실행 버튼을 사용하세요.",
            "노트 메뉴 → 카드 → 가리기 카드에서 질문과 정답을 입력해 카드를 만듭니다.",
            "페이지의 테이프를 탭해 정답을 확인하고 O / X로 정답 여부를 표시합니다.",
        ),
        note = "카드 목록에서 진행률과 정답률을 확인하고, 카드를 수정·삭제하거나 해당 페이지로 이동할 수 있습니다.",
        caution = "가리기 카드는 학습용 오버레이라 PDF 내보내기에는 포함되지 않습니다. 노트 JSON과 로컬 백업에는 저장됩니다.",
    ),
    HelpTopic(
        key = HelpTopicKey.SEARCH_NAV,
        chip = "검색 다음/이전",
        title = "검색 다음/이전",
        subtitle = "검색 결과 사이를 앞뒤로 이동하기",
        steps = listOf(
            "상단 검색에서 페이지별 결과와 하이라이트를 확인합니다.",
            "결과 목록의 이전 결과 / 다음 결과를 누르면 해당 페이지로 이동합니다.",
            "화면 상단의 검색 n / 전체 칩에서도 다음 결과로 이동할 수 있습니다.",
        ),
        note = "결과는 마지막에서 처음으로 이어집니다. 검색어를 바꾸면 첫 결과부터 다시 시작합니다.",
    ),
    HelpTopic(
        key = HelpTopicKey.BACKUP,
        chip = "백업/내보내기",
        title = "백업/내보내기",
        subtitle = "작업을 ZIP으로 보관하고 PDF로 내보내기",
        steps = listOf(
            "홈 설정의 백업/복원을 누르고 백업을 선택해 ZIP 저장 위치를 고릅니다.",
            "PDF 원본 포함 여부를 선택할 수 있고, 커스텀 템플릿도 백업에 포함됩니다.",
            "복원은 같은 화면에서 ZIP을 선택하면 기존 데이터가 안전하게 교체됩니다.",
            "PDF 작업은 앱 안에 저장되고, PDF 메뉴의 PDF 내보내기는 주석을 포함한 새 PDF를 다른 위치에 저장합니다.",
            "노트의 PDF로 내보내기는 전체/현재/선택 페이지와 숨긴 레이어 포함 여부를 고를 수 있습니다.",
        ),
        caution = "백업 ZIP은 암호화되지 않습니다. 개인 정보가 들어 있을 수 있으니 신뢰하는 저장소에 보관하세요.",
    ),
    HelpTopic(
        key = HelpTopicKey.TROUBLESHOOT,
        chip = "문제 해결",
        title = "문제 해결",
        subtitle = "자주 막히는 상황과 확인 순서",
        steps = listOf(
            "텍스트가 선택되지 않으면: 스캔 PDF일 수 있습니다. 홈 설정의 PDF 도구에서 OCR로 검색 가능한 PDF를 만든 뒤 다시 여세요.",
            "하이라이트가 안 보이면: PDF 메뉴의 주석 숨기기를 확인하세요. 숨김은 화면 표시만 바꾸며 데이터는 유지됩니다.",
            "노트가 너무 작으면: 가운데 손잡이를 위로 드래그하거나 노트 헤더의 85% 크기 프리셋을 누르세요.",
            "필기가 사라졌으면: 0.8.2에서 다중 획 유실 버그를 수정했습니다. 백업이 있으면 백업/복원으로 되살릴 수 있습니다.",
            "필압이 안 먹으면: 노트 메뉴 → 필기 설정에서 '필압 잉크'가 켜져 있는지 확인하세요. 손가락과 마우스에는 필압이 적용되지 않습니다.",
            "앱이 설치되지 않으면: arm64 전용 APK입니다. 저장 공간과 알 수 없는 앱 설치 허용을 확인하세요.",
        ),
    ),
)

/**
 * The four 60-second quick-start actions that replace the old eight-chip grid.
 * Each card is one sentence plus the place it takes the user; all eight legacy
 * topics stay reachable behind 더보기 so nothing is removed.
 */
internal enum class QuickCardKey {
    OPEN_PDF,
    MARK_WHILE_READ,
    CAPTURE_TO_NOTE,
    REVIEW,
}

/** Optional deep-link target for a quick card. The host decides what to open. */
enum class QuickDestination {
    OPEN_PDF,
    MARKUP_TOOLS,
    NOTE_PANE,
    REVIEW_CARDS,
}

internal data class QuickCard(
    val key: QuickCardKey,
    val label: String,
    val sentence: String,
    val destination: QuickDestination,
    val actionLabel: String,
    val topicKey: HelpTopicKey,
)

/** Labels of the disclosure that reveals (and hides) the full legacy list. */
internal const val SHOW_MORE_LABEL = "더보기"
internal const val SHOW_LESS_LABEL = "접기"

/** The four primary quick cards, in the exact order shown in the sheet. */
internal fun quickCards(): List<QuickCard> = listOf(
    QuickCard(
        key = QuickCardKey.OPEN_PDF,
        label = "1 PDF 열기",
        sentence = "홈에서 PDF 가져오기를 눌러 강의자료를 엽니다.",
        destination = QuickDestination.OPEN_PDF,
        actionLabel = "자세히",
        topicKey = HelpTopicKey.OPEN_PDF,
    ),
    QuickCard(
        key = QuickCardKey.MARK_WHILE_READ,
        label = "2 표시하며 읽기",
        sentence = "하단의 텍스트 선택으로 문장을 고르고 형광펜으로 표시합니다.",
        destination = QuickDestination.MARKUP_TOOLS,
        actionLabel = "자세히",
        topicKey = HelpTopicKey.TEXT_SELECTION,
    ),
    QuickCard(
        key = QuickCardKey.CAPTURE_TO_NOTE,
        label = "3 노트에 담기",
        sentence = "하단 도구 독의 노트로 ${UiCopy.STUDY_NOTE}(${UiCopy.STUDY_NOTE_DESCRIPTION})를 열어 담습니다.",
        destination = QuickDestination.NOTE_PANE,
        actionLabel = "자세히",
        topicKey = HelpTopicKey.STUDY_NOTE,
    ),
    QuickCard(
        key = QuickCardKey.REVIEW,
        label = "4 복습하기",
        sentence = "노트 메뉴의 카드에서 가리기 카드로 스스로 확인합니다.",
        destination = QuickDestination.REVIEW_CARDS,
        actionLabel = "자세히",
        topicKey = HelpTopicKey.WRITE_TOOLS,
    ),
)

/**
 * Legacy topics stay hidden until 더보기 is tapped. This pure function is the
 * single source of truth for that disclosure and is checked by JVM tests.
 */
internal fun visibleLegacyTopics(expanded: Boolean): List<HelpTopic> =
    if (expanded) helpTopics() else emptyList()

/** Initial selection policy for a contextual help deep link. */
internal data class HelpOpenSelection(
    val selectedKey: HelpTopicKey,
    val showMore: Boolean,
    val scrollTopicKey: HelpTopicKey?,
)

internal fun initialHelpSelection(
    topics: List<HelpTopic>,
    initialTopic: HelpTopicKey?,
): HelpOpenSelection {
    val fallback = topics.firstOrNull()?.key ?: HelpTopicKey.QUICK_START
    val matchedTopic = initialTopic?.let { candidate ->
        topics.firstOrNull { it.key == candidate }
    }
    return HelpOpenSelection(
        selectedKey = matchedTopic?.key ?: fallback,
        showMore = matchedTopic != null,
        scrollTopicKey = matchedTopic?.key,
    )
}

/**
 * Header, optional replay, quick cards, 더보기 and the chip row precede topic
 * items. Keeping the index policy pure lets JVM tests pin contextual scrolling.
 */
internal fun helpTopicScrollIndex(hasReplay: Boolean, topicIndex: Int): Int =
    topicIndex + if (hasReplay) 5 else 4

/** Label for the optional replay entry in the help center. */
internal const val REPLAY_TUTORIAL_LABEL = "튜토리얼 다시 보기"

/**
 * Fully offline, Material 3 bottom-sheet help center. The primary surface is
 * four 60-second quick cards; the eight legacy topics live behind 더보기 so
 * nothing is removed while the first screen stays uncluttered. Every section
 * shows short numbered steps plus optional note/caution callouts. No external
 * links, assets, fonts, web views or analytics.
 *
 * [onReplayTutorial] is optional: when supplied, the sheet offers a replay of
 * the first-run tutorial. [initialTopic] opens the full topic list with that
 * topic selected and scrolled into view. [onQuickAction] is optional: when
 * supplied, tapping a quick card hands its [QuickDestination] to the host for a
 * deep link; without it, the card expands 더보기 and scrolls to the matching
 * legacy topic. The caller owns closing this sheet and showing any follow-up
 * screen.
 */
@Composable
fun HelpCenterSheet(
    onDismiss: () -> Unit,
    onReplayTutorial: (() -> Unit)? = null,
    onQuickAction: ((QuickDestination) -> Unit)? = null,
    initialTopic: HelpTopicKey? = null,
) {
    val topics = remember { helpTopics() }
    val quickCards = remember { quickCards() }
    val initialSelection = remember(initialTopic) { initialHelpSelection(topics, initialTopic) }
    var selectedKey by remember(initialTopic) { mutableStateOf(initialSelection.selectedKey) }
    var showMore by remember(initialTopic) { mutableStateOf(initialSelection.showMore) }
    var pendingScrollKey by remember(initialTopic) { mutableStateOf(initialSelection.scrollTopicKey) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Scroll only after the topic list has been revealed by 더보기 or a deep link.
    LaunchedEffect(showMore, pendingScrollKey, onReplayTutorial != null) {
        val key = pendingScrollKey
        if (showMore && key != null) {
            val index = topics.indexOfFirst { it.key == key }
            if (index >= 0) {
                listState.animateScrollToItem(helpTopicScrollIndex(onReplayTutorial != null, index))
            }
            pendingScrollKey = null
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "도움말",
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                "60초 빠른 시작 · 기기 안에서만 동작합니다",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text("닫기") }
                    }

                    if (onReplayTutorial != null) {
                        TextButton(
                            onClick = { onReplayTutorial() },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .padding(start = 14.dp, bottom = 6.dp),
                        ) {
                            Text(REPLAY_TUTORIAL_LABEL)
                        }
                    }

                    // Four primary quick cards, each a 48dp+ target with a two-line label.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickCards.forEach { card ->
                            QuickCardRow(
                                card = card,
                                onClick = {
                                    selectedKey = card.topicKey
                                    if (onQuickAction != null) {
                                        onQuickAction(card.destination)
                                    } else {
                                        showMore = true
                                        pendingScrollKey = card.topicKey
                                    }
                                },
                            )
                        }
                    }

                    TextButton(
                        onClick = { showMore = !showMore },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(start = 12.dp),
                    ) {
                        Text(if (showMore) SHOW_LESS_LABEL else SHOW_MORE_LABEL)
                    }
                }
            }

            if (showMore) {
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        topics.forEach { topic ->
                            FilterChip(
                                selected = selectedKey == topic.key,
                                onClick = {
                                    selectedKey = topic.key
                                    val index = topics.indexOfFirst { it.key == topic.key }
                                    if (index >= 0) {
                                        scope.launch {
                                            listState.animateScrollToItem(
                                                helpTopicScrollIndex(onReplayTutorial != null, index),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(topic.chip, maxLines = 2) },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                items(visibleLegacyTopics(true), key = { it.key.name }) { topic ->
                    HelpTopicSection(topic, Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickCardRow(card: QuickCard, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    card.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${card.actionLabel} ▾",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                card.sentence,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun HelpTopicSection(topic: HelpTopic, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                topic.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                topic.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            topic.steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        step,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (index != topic.steps.lastIndex) Spacer(Modifier.height(8.dp))
            }
            topic.note?.let { note ->
                Spacer(Modifier.height(12.dp))
                HelpCallout(text = note, caution = false)
            }
            topic.caution?.let { caution ->
                Spacer(Modifier.height(8.dp))
                HelpCallout(text = caution, caution = true)
            }
        }
    }
}

@Composable
private fun HelpCallout(text: String, caution: Boolean) {
    val container = if (caution) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (caution) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Text(
            if (caution) "주의: $text" else text,
            modifier = Modifier
                .padding(12.dp)
                .then(if (caution) Modifier.semantics { heading() } else Modifier),
            color = onContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
