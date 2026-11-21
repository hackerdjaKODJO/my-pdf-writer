package com.hackerdjakodjo.mypdfwriter.ui

import com.hackerdjakodjo.mypdfwriter.data.OnboardingTopicKey
import com.hackerdjakodjo.mypdfwriter.data.onboardingTopicOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM checks for the offline onboarding copy, ordering and controls. */
class OnboardingContentTest {

    @Test
    fun cardsFollowTheCanonicalTopicOrder() {
        assertEquals(
            ONBOARDING_CARD_TOPICS,
            onboardingCards().map { it.key },
        )
        assertEquals(ONBOARDING_CARD_TOPICS, onboardingTopicOrder().filter {
            it in ONBOARDING_CARD_TOPICS
        })
    }

    @Test
    fun walkthroughIsCappedAtThreeCards() {
        assertEquals(3, ONBOARDING_CARD_LIMIT)
        assertEquals(3, onboardingCards().size)
        assertTrue(onboardingCards().size <= ONBOARDING_CARD_LIMIT)
    }

    @Test
    fun everyCardIsOneShortKoreanSentence() {
        onboardingCards().forEach { card ->
            assertTrue("${card.key} title is blank", card.title.isNotBlank())
            assertTrue("${card.key} body is blank", card.body.isNotBlank())
            assertTrue("${card.key} title should be Korean", containsKorean(card.title))
            assertTrue("${card.key} body should be Korean", containsKorean(card.body))
            assertTrue("${card.key} body must be short", card.body.length <= 70)
            val endings = card.body.count { it == '.' || it == '?' || it == '!' }
            assertEquals("${card.key} body must be a single sentence: ${card.body}", 1, endings)
            assertTrue("${card.key} body must end with a period", card.body.endsWith("."))
        }
    }

    @Test
    fun cardsCoverTheRequestedFeatureAreas() {
        val expectedKeywords = mapOf(
            OnboardingTopicKey.HOME_QUICK_ACTIONS to listOf("홈", "이어서 읽기", "새 노트", "백업"),
            OnboardingTopicKey.SELECT_HIGHLIGHT to listOf("PDF", "형광펜", "한 번에"),
            OnboardingTopicKey.NOTE_PANE to listOf("노트", "링크"),
        )
        onboardingCards().forEach { card ->
            val keywords = expectedKeywords.getValue(card.key)
            keywords.forEach { keyword ->
                assertTrue(
                    "${card.key} should mention '$keyword': ${card.body}",
                    card.body.contains(keyword, ignoreCase = true),
                )
            }
        }
    }

    @Test
    fun controlsUseTheExpectedKoreanLabels() {
        assertEquals("다음", OnboardingLabels.NEXT)
        assertEquals("이전", OnboardingLabels.PREVIOUS)
        assertEquals("건너뛰기", OnboardingLabels.SKIP)
        assertEquals("시작", OnboardingLabels.START)
        assertEquals("튜토리얼 다시 보기", REPLAY_TUTORIAL_LABEL)
    }

    @Test
    fun contentIsFullyOfflineWithNoExternalReferences() {
        val allText = onboardingCards().joinToString("\n") { "${it.title}\n${it.body}" }.lowercase()
        listOf("http", "https", "www.", "mailto:", ".html", "play.google", "github.com")
            .forEach { marker ->
                assertFalse("onboarding content must not reference '$marker'", allText.contains(marker))
            }
    }

    private fun containsKorean(value: String): Boolean =
        value.any { it.code in 0xAC00..0xD7A3 }
}
