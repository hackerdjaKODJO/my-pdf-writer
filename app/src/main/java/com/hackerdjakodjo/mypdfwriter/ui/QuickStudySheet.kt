@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.hackerdjakodjo.mypdfwriter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One replayable onboarding step. Only the first step performs a real action. */
internal data class QuickStudyStep(
    val title: String,
    val hint: String,
    val usesExistingPdfActions: Boolean = false,
)

/**
 * Pure policy for the four-step quick-study walkthrough. It owns no Android
 * state, so the JVM tests can assert the exact step order, progress text and
 * next/complete transitions.
 */
internal object QuickStudyPolicy {
    val steps: List<QuickStudyStep> = listOf(
        QuickStudyStep(
            title = "PDF 열기",
            hint = "최근에 본 PDF를 열거나 기기에서 PDF를 가져와 보세요.",
            usesExistingPdfActions = true,
        ),
        QuickStudyStep(
            title = "형광펜으로 표시",
            hint = "PDF에서 중요한 문장을 형광펜으로 표시해 보세요.",
        ),
        QuickStudyStep(
            title = "노트에 담기",
            hint = "표시한 내용을 노트에 담아 나만의 말로 정리해 보세요.",
        ),
        QuickStudyStep(
            title = "복습하기",
            hint = "만든 노트를 다시 열어 천천히 복습해 보세요.",
        ),
    )

    fun step(index: Int): QuickStudyStep = steps[index.coerceIn(steps.indices)]

    fun progress(index: Int): String =
        "${index.coerceIn(steps.indices) + 1}/${steps.size}"

    fun isLast(index: Int): Boolean = index >= steps.lastIndex

    fun primaryLabel(index: Int): String = if (isLast(index)) "완료" else "다음"

    /** Null on the last step: the host must dismiss instead of advancing. */
    fun nextIndex(index: Int): Int? =
        if (isLast(index)) null else (index + 1).coerceAtMost(steps.lastIndex)
}

/**
 * Dismissible, replayable four-step guide. The host renders this only after the
 * user taps the "빠른 학습" card, so nothing blocks startup. Reopening remounts
 * the sheet and restarts at step 1/4.
 */
@Composable
internal fun QuickStudySheet(
    onDismiss: () -> Unit,
    onOpenPdf: () -> Unit,
    onImportPdf: () -> Unit,
    hasRecentPdf: Boolean,
) {
    var index by rememberSaveable { mutableStateOf(0) }
    val step = QuickStudyPolicy.step(index)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "빠른 학습",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "네 단계로 공부를 시작해 보세요.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    QuickStudyPolicy.progress(index),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "${QuickStudyPolicy.progress(index).substringBefore('/')}. ${step.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        step.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (step.usesExistingPdfActions) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (hasRecentPdf) {
                                Button(
                                    onClick = onOpenPdf,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                ) {
                                    Text("최근 PDF 열기", maxLines = 2)
                                }
                            }
                            OutlinedButton(
                                onClick = onImportPdf,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) {
                                Text("PDF 가져오기", maxLines = 2)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("건너뛰기", maxLines = 2)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val next = QuickStudyPolicy.nextIndex(index)
                        if (next == null) {
                            onDismiss()
                        } else {
                            index = next
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(QuickStudyPolicy.primaryLabel(index), maxLines = 2)
                }
            }
        }
    }
}
