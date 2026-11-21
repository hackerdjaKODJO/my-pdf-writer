@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey
import com.hackerdjakodjo.mypdfwriter.data.onboardingTopicOrder

/** Button labels kept as constants so the UI and the JVM tests stay in sync. */
internal object OnboardingLabels {
    const val NEXT = "다음"
    const val PREVIOUS = "이전"
    const val SKIP = "건너뛰기"
    const val START = "시작"
}

/** Framing shared with the help center's four-card quick start. */
internal const val ONBOARDING_QUICK_START_LABEL = "60초 빠른 시작"

/** One short onboarding card: a title and exactly one sentence of guidance. */
internal data class OnboardingCard(
    val key: OnboardingTopicKey,
    val title: String,
    val body: String,
)

/** First-run walkthrough is short: at most three cards, never five. */
internal const val ONBOARDING_CARD_LIMIT = 3

/**
 * The three topics a student actually needs on day one: resume where they left
 * off, mark a passage once, and capture it into a linked note. Backup and the
 * full tool tour stay in 도움말 instead of padding the walkthrough.
 */
internal val ONBOARDING_CARD_TOPICS: List<OnboardingTopicKey> = listOf(
    OnboardingTopicKey.HOME_QUICK_ACTIONS,
    OnboardingTopicKey.SELECT_HIGHLIGHT,
    OnboardingTopicKey.NOTE_PANE,
)

/**
 * Original, offline copy derived from the app's own usage guide. Every card is
 * a single sentence; the illustrations below are drawn with Compose primitives
 * rather than copied assets or third-party artwork.
 */
internal fun onboardingCards(): List<OnboardingCard> {
    val byKey = onboardingCardsByKey()
    return onboardingTopicOrder()
        .filter { it in ONBOARDING_CARD_TOPICS }
        .take(ONBOARDING_CARD_LIMIT)
        .map { byKey.getValue(it) }
}

private fun onboardingCardsByKey(): Map<OnboardingTopicKey, OnboardingCard> = mapOf(
    OnboardingTopicKey.HOME_QUICK_ACTIONS to OnboardingCard(
        key = OnboardingTopicKey.HOME_QUICK_ACTIONS,
        title = "홈에서 바로 시작",
        body = "홈의 이어서 읽기와 새 노트 두 버튼으로 바로 시작하고, 백업은 설정에 있어요.",
    ),
    OnboardingTopicKey.OPEN_READ_PDF to OnboardingCard(
        key = OnboardingTopicKey.OPEN_READ_PDF,
        title = "PDF 열고 읽기",
        body = "홈의 PDF 가져오기로 파일을 고르고, 두 손가락으로 확대하며 자주 쓰는 도구를 찾을 수 있어요.",
    ),
    OnboardingTopicKey.SELECT_HIGHLIGHT to OnboardingCard(
        key = OnboardingTopicKey.SELECT_HIGHLIGHT,
        title = "선택하고 하이라이트",
        body = "텍스트 PDF에서 단어를 고른 뒤 플로팅 바의 형광펜으로 한 번에 표시할 수 있어요.",
    ),
    OnboardingTopicKey.NOTE_PANE to OnboardingCard(
        key = OnboardingTopicKey.NOTE_PANE,
        title = "노트에 담기",
        body = "노트 버튼으로 아래에 노트를 펼치고, 담은 내용은 원본 페이지 링크와 함께 저장돼요.",
    ),
    OnboardingTopicKey.HELP_BACKUP to OnboardingCard(
        key = OnboardingTopicKey.HELP_BACKUP,
        title = "도움말과 백업",
        body = "어려울 때는 도움말의 4단계 빠른 시작을 다시 보고, 홈의 백업·복원으로 작업을 기기 안에 보관할 수 있어요.",
    ),
)

/**
 * First-run walkthrough: a single Material 3 bottom sheet that swaps its one
 * visible card as the user taps 다음/이전. 건너뛰기 and 시작 close it; there is
 * no stack of dialogs and no navigation or network dependency.
 */
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    val cards = remember { onboardingCards() }
    var index by remember { mutableIntStateOf(0) }
    val card = cards[index.coerceIn(cards.indices)]
    val isLast = index == cards.lastIndex
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("처음 사용법", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "$ONBOARDING_QUICK_START_LABEL · 도움말에서 다시 볼 수 있어요",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${index + 1} / ${cards.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 1.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    OnboardingIllustration(card.key)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        card.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cards.forEachIndexed { position, _ ->
                    val active = position == index
                    Box(
                        modifier = Modifier
                            .width(if (active) 18.dp else 7.dp)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(OnboardingLabels.SKIP) }
                Spacer(Modifier.weight(1f))
                if (index > 0) {
                    TextButton(onClick = { index -= 1 }) { Text(OnboardingLabels.PREVIOUS) }
                }
                Button(
                    onClick = {
                        if (isLast) {
                            onComplete()
                        } else {
                            index += 1
                        }
                    },
                ) {
                    Text(if (isLast) OnboardingLabels.START else OnboardingLabels.NEXT)
                }
            }
        }
    }
}

@Composable
private fun OnboardingIllustration(key: OnboardingTopicKey) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (key) {
                OnboardingTopicKey.HOME_QUICK_ACTIONS -> {
                    box(onSurface.copy(alpha = 0.12f), 0.16f, 0.05f, 0.68f, 0.9f, 11f)
                    box(primary, 0.16f, 0.05f, 0.68f, 0.14f, 11f)
                    box(surface, 0.22f, 0.24f, 0.56f, 0.16f, 4f)
                    line(outline, 0.28f, 0.32f, 0.58f, 0.32f, 2f)
                    for (row in 0..1) {
                        for (col in 0..1) {
                            val tile = if (row == 0 && col == 0) {
                                secondary
                            } else {
                                primary.copy(alpha = 0.55f)
                            }
                            box(tile, 0.22f + col * 0.29f, 0.46f + row * 0.23f, 0.26f, 0.19f, 4f)
                        }
                    }
                }

                OnboardingTopicKey.OPEN_READ_PDF -> {
                    box(surface, 0.16f, 0.06f, 0.6f, 0.88f, 4f)
                    outlineBox(outline, 0.16f, 0.06f, 0.6f, 0.88f, 4f)
                    line(primary, 0.63f, 0.06f, 0.76f, 0.19f, 2f)
                    line(outline, 0.23f, 0.3f, 0.66f, 0.3f, 2f)
                    line(outline, 0.23f, 0.42f, 0.6f, 0.42f, 2f)
                    line(outline, 0.23f, 0.54f, 0.66f, 0.54f, 2f)
                    line(outline, 0.23f, 0.66f, 0.52f, 0.66f, 2f)
                    drawCircle(
                        color = primary,
                        radius = 13.dp.toPx(),
                        center = Offset(0.72f * size.width, 0.42f * size.height),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                    line(primary, 0.82f, 0.54f, 0.89f, 0.62f, 3f)
                }

                OnboardingTopicKey.SELECT_HIGHLIGHT -> {
                    box(surface, 0.1f, 0.12f, 0.8f, 0.76f, 4f)
                    outlineBox(outline, 0.1f, 0.12f, 0.8f, 0.76f, 4f)
                    box(secondary.copy(alpha = 0.45f), 0.18f, 0.36f, 0.56f, 0.18f, 3f)
                    outlineBox(outline, 0.18f, 0.25f, 0.18f, 0.16f, 3f)
                    outlineBox(primary, 0.4f, 0.36f, 0.22f, 0.16f, 3f, 2f)
                    outlineBox(outline, 0.66f, 0.36f, 0.16f, 0.16f, 3f)
                    line(outline, 0.18f, 0.68f, 0.7f, 0.68f, 2f)
                    line(outline, 0.18f, 0.78f, 0.56f, 0.78f, 2f)
                    line(primary, 0.24f, 0.23f, 0.5f, 0.42f, 2f)
                    dot(primary, 0.5f, 0.42f, 4f)
                    drawCircle(
                        color = primary.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = Offset(0.24f * size.width, 0.23f * size.height),
                    )
                    dot(primary, 0.24f, 0.23f, 4f)
                }

                OnboardingTopicKey.NOTE_PANE -> {
                    box(surface, 0.1f, 0.08f, 0.8f, 0.28f, 4f)
                    outlineBox(outline, 0.1f, 0.08f, 0.8f, 0.28f, 4f)
                    line(outline, 0.16f, 0.2f, 0.7f, 0.2f, 2f)
                    box(tertiary, 0.43f, 0.39f, 0.14f, 0.05f, 3f)
                    box(surface, 0.1f, 0.48f, 0.8f, 0.44f, 4f)
                    outlineBox(outline, 0.1f, 0.48f, 0.8f, 0.44f, 4f)
                    for (row in 0..2) {
                        val y = 0.62f + row * 0.1f
                        line(outline, 0.16f, y, 0.84f, y, 1.4f)
                    }
                    line(primary, 0.2f, 0.58f, 0.34f, 0.52f, 3f)
                    line(primary, 0.34f, 0.52f, 0.48f, 0.6f, 3f)
                }

                OnboardingTopicKey.HELP_BACKUP -> {
                    drawCircle(
                        color = primary.copy(alpha = 0.16f),
                        radius = 24.dp.toPx(),
                        center = Offset(0.3f * size.width, 0.5f * size.height),
                    )
                    drawArc(
                        color = primary,
                        startAngle = 200f,
                        sweepAngle = 280f,
                        useCenter = false,
                        topLeft = Offset(
                            0.3f * size.width - 18.dp.toPx(),
                            0.5f * size.height - 18.dp.toPx(),
                        ),
                        size = Size(36.dp.toPx(), 36.dp.toPx()),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                    dot(primary, 0.3f, 0.63f, 2.5f)
                    box(surface, 0.55f, 0.24f, 0.35f, 0.52f, 5f)
                    outlineBox(outline, 0.55f, 0.24f, 0.35f, 0.52f, 5f)
                    line(primary, 0.58f, 0.34f, 0.87f, 0.34f, 2f)
                    line(secondary, 0.725f, 0.47f, 0.725f, 0.66f, 3f)
                    line(secondary, 0.725f, 0.66f, 0.66f, 0.59f, 3f)
                    line(secondary, 0.725f, 0.66f, 0.79f, 0.59f, 3f)
                }
            }
        }
    }
}

/** Fractional-coordinate helpers keep all illustrations asset-free and theme-aware. */
private fun DrawScope.box(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float = 6f,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(width * size.width, height * size.height),
        cornerRadius = CornerRadius(radius.dp.toPx()),
    )
}

private fun DrawScope.outlineBox(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float = 6f,
    stroke: Float = 1.2f,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x * size.width, y * size.height),
        size = Size(width * size.width, height * size.height),
        cornerRadius = CornerRadius(radius.dp.toPx()),
        style = Stroke(width = stroke.dp.toPx()),
    )
}

private fun DrawScope.line(
    color: Color,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    width: Float = 1.4f,
) {
    drawLine(
        color = color,
        start = Offset(x1 * size.width, y1 * size.height),
        end = Offset(x2 * size.width, y2 * size.height),
        strokeWidth = width.dp.toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.dot(color: Color, x: Float, y: Float, radius: Float = 3f) {
    drawCircle(
        color = color,
        radius = radius.dp.toPx(),
        center = Offset(x * size.width, y * size.height),
    )
}
