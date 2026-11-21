package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM checks for the offline help content. The sheet itself is Compose UI,
 * but the topic/step data it renders is plain Kotlin and is verified here.
 */
class HelpCenterContentTest {

    @Test
    fun containsAllEightRequestedTopicsInOrder() {
        assertEquals(
            listOf(
                "5분 빠른 시작",
                "PDF 열기/읽기",
                "텍스트 선택·형광펜",
                "PDF 위 스터디 노트 + 높이 드래그",
                "필기 도구·가리기 카드",
                "검색 다음/이전",
                "백업/내보내기",
                "문제 해결",
            ),
            helpTopics().map { it.title },
        )
    }

    @Test
    fun topicKeysAreUniqueAndStable() {
        val topics = helpTopics()
        assertEquals(8, topics.size)
        assertEquals(topics.map { it.key }.toSet().size, topics.size)
        assertEquals(
            listOf(
                HelpTopicKey.QUICK_START,
                HelpTopicKey.OPEN_PDF,
                HelpTopicKey.TEXT_SELECTION,
                HelpTopicKey.STUDY_NOTE,
                HelpTopicKey.WRITE_TOOLS,
                HelpTopicKey.SEARCH_NAV,
                HelpTopicKey.BACKUP,
                HelpTopicKey.TROUBLESHOOT,
            ),
            topics.map { it.key },
        )
    }

    @Test
    fun everyTopicHasShortActionableKoreanSteps() {
        helpTopics().forEach { topic ->
            assertTrue("${topic.key} must have steps", topic.steps.isNotEmpty())
            assertTrue("${topic.key} must have a chip label", topic.chip.isNotBlank())
            assertTrue("${topic.key} must have a title", topic.title.isNotBlank())
            assertTrue("${topic.key} must have a subtitle", topic.subtitle.isNotBlank())
            topic.steps.forEach { step ->
                assertTrue("${topic.key} step is blank", step.isNotBlank())
                assertTrue(
                    "${topic.key} step should be a short Korean instruction: $step",
                    containsKorean(step),
                )
            }
        }
    }

    @Test
    fun quickStartIsASixStepWalkthrough() {
        val quickStart = helpTopics().first { it.key == HelpTopicKey.QUICK_START }
        assertEquals(6, quickStart.steps.size)
        assertTrue(quickStart.note!!.contains("기기 안에서만"))
    }

    @Test
    fun guidanceIsFullyOfflineWithNoExternalLinks() {
        val allText = helpTopics().joinToString("\n") { it.allText() }.lowercase()
        listOf("http", "www.", "https", "mailto:", ".html", "play.google", "github.com")
            .forEach { marker ->
                assertFalse("help content must not reference '$marker'", allText.contains(marker))
            }
    }

    @Test
    fun cautionTopicsCallOutScanOcrAndBackupEncryption() {
        val topics = helpTopics().associateBy { it.key }
        assertTrue(topics[HelpTopicKey.TEXT_SELECTION]!!.caution!!.contains("OCR"))
        assertTrue(topics[HelpTopicKey.WRITE_TOOLS]!!.caution!!.contains("PDF 내보내기"))
        assertTrue(topics[HelpTopicKey.BACKUP]!!.caution!!.contains("암호화되지"))
    }

    private fun containsKorean(value: String): Boolean =
        value.any { it.code in 0xAC00..0xD7A3 }
}
