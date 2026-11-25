package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                "60초 빠른 시작",
                "PDF 열기/읽기",
                "텍스트 선택·형광펜",
                "PDF 위 학습 노트 + 높이 드래그",
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

    @Test
    fun quickCardsAreFourInDocumentedOrder() {
        assertEquals(
            listOf("1 PDF 열기", "2 표시하며 읽기", "3 노트에 담기", "4 복습하기"),
            quickCards().map { it.label },
        )
        assertEquals(4, quickCards().size)
        assertEquals(quickCards().map { it.key }.toSet().size, quickCards().size)
        assertEquals(
            listOf(
                QuickCardKey.OPEN_PDF,
                QuickCardKey.MARK_WHILE_READ,
                QuickCardKey.CAPTURE_TO_NOTE,
                QuickCardKey.REVIEW,
            ),
            quickCards().map { it.key },
        )
    }

    @Test
    fun quickCardsAreOneSentenceWithTapDestinations() {
        quickCards().forEach { card ->
            assertTrue("${card.key} sentence is blank", card.sentence.isNotBlank())
            assertTrue("${card.key} sentence should be Korean", containsKorean(card.sentence))
            assertEquals(
                "${card.key} sentence must be one sentence: ${card.sentence}",
                1,
                card.sentence.count { it == '.' },
            )
            assertTrue("${card.key} sentence must end with a period", card.sentence.endsWith("."))
            assertTrue("${card.key} action label is blank", card.actionLabel.isNotBlank())
            assertTrue(
                "${card.key} must point at a legacy topic",
                helpTopics().any { it.key == card.topicKey },
            )
        }
        assertEquals(
            listOf(
                QuickDestination.OPEN_PDF,
                QuickDestination.MARKUP_TOOLS,
                QuickDestination.NOTE_PANE,
                QuickDestination.REVIEW_CARDS,
            ),
            quickCards().map { it.destination },
        )
        assertEquals(
            listOf(
                HelpTopicKey.OPEN_PDF,
                HelpTopicKey.TEXT_SELECTION,
                HelpTopicKey.STUDY_NOTE,
                HelpTopicKey.WRITE_TOOLS,
            ),
            quickCards().map { it.topicKey },
        )
    }

    @Test
    fun showMoreRevealsAllEightLegacyTopics() {
        assertEquals("더보기", SHOW_MORE_LABEL)
        assertTrue("legacy topics must be hidden until 더보기", visibleLegacyTopics(false).isEmpty())
        assertEquals(helpTopics(), visibleLegacyTopics(true))
        assertEquals(8, visibleLegacyTopics(true).size)
        assertEquals(
            setOf(
                HelpTopicKey.QUICK_START,
                HelpTopicKey.OPEN_PDF,
                HelpTopicKey.TEXT_SELECTION,
                HelpTopicKey.STUDY_NOTE,
                HelpTopicKey.WRITE_TOOLS,
                HelpTopicKey.SEARCH_NAV,
                HelpTopicKey.BACKUP,
                HelpTopicKey.TROUBLESHOOT,
            ),
            visibleLegacyTopics(true).map { it.key }.toSet(),
        )
    }

    @Test
    fun helpUsesCurrentToolAndGestureVocabulary() {
        val all = helpTopics().joinToString("\n") { it.allText() }
        assertTrue("dock slot is text selection", all.contains("텍스트 선택·노트"))
        assertTrue("selection bar action", all.contains("복사·노트에 담기"))
        assertTrue("view tool", all.contains("보기 도구"))
        assertFalse("no ghost view mode", all.contains("보기 모드"))
        assertFalse("no 발췌 on the selection bar", all.contains("복사·발췌"))
        listOf(
            UiCopy.STUDY_NOTE,
            UiCopy.STUDY_NOTE_DESCRIPTION,
            UiCopy.PAGE_MEMO,
            UiCopy.PAGE_MEMO_DESCRIPTION,
            UiCopy.POST_IT,
            UiCopy.POST_IT_DESCRIPTION,
            UiCopy.CAPTURE_TO_NOTE,
            UiCopy.CAPTURE_TO_NOTE_DESCRIPTION,
        ).forEach { canonical ->
            assertTrue("missing canonical copy '$canonical'", all.contains(canonical))
        }
    }

    @Test
    fun helpTextNamesShippedLocationsOnly() {
        val all = helpTopics().joinToString("\n") { it.allText() } +
            quickCards().joinToString("\n") { it.sentence }
        listOf(
            "5분 빠른 시작", "PDF 상단의 노트", "홈의 백업/복원",
            "학습·도구·보기", "학습 메뉴의 기타", "하단 페이지 바",
            "설정 → 파일 도구에 페이지 잠금·문서 정보·PDF 내보내기가",
        ).forEach { stale ->
            assertFalse("stale help path '$stale'", all.contains(stale))
        }
        listOf(
            "60초 빠른 시작", "하단 도구 독의 노트", "홈 설정의 백업/복원",
            "보기·주석·노트·파일·설정", "PDF 내보내기", "추가 도구", "위쪽 페이지 이동", "스와이프",
        ).forEach { current ->
            assertTrue("missing current help path '$current'", all.contains(current))
        }
    }

    @Test
    fun contextualTopicPolicySelectsRevealsAndScrollsToRequestedTopic() {
        val topics = helpTopics()
        val normal = initialHelpSelection(topics, initialTopic = null)
        assertEquals(HelpTopicKey.QUICK_START, normal.selectedKey)
        assertFalse(normal.showMore)
        assertNull(normal.scrollTopicKey)

        val backup = initialHelpSelection(topics, initialTopic = HelpTopicKey.BACKUP)
        assertEquals(HelpTopicKey.BACKUP, backup.selectedKey)
        assertTrue(backup.showMore)
        assertEquals(HelpTopicKey.BACKUP, backup.scrollTopicKey)

        val topicIndex = topics.indexOfFirst { it.key == HelpTopicKey.BACKUP }
        assertEquals(topicIndex + 4, helpTopicScrollIndex(hasReplay = false, topicIndex = topicIndex))
        assertEquals(topicIndex + 5, helpTopicScrollIndex(hasReplay = true, topicIndex = topicIndex))
    }

    @Test
    fun navigationZoomAndUndoGuidanceAreModeAccurate() {
        val topics = helpTopics().associateBy { it.key }
        val openPdf = topics.getValue(HelpTopicKey.OPEN_PDF).allText()
        val writeTools = topics.getValue(HelpTopicKey.WRITE_TOOLS).allText()
        assertTrue(openPdf.contains(PDF_HINT_PAGE_NAVIGATION_TEXT))
        assertTrue(openPdf.contains(PDF_HINT_VIEW_ZOOM_TEXT))
        assertFalse(openPdf.contains("하단 페이지 바"))
        assertTrue(writeTools.contains("노트 편집기에서만 두 손가락 탭"))
        assertTrue(writeTools.contains("PDF에서는 실행 취소·다시 실행 버튼"))
        assertTrue(writeTools.contains(PDF_HINT_STYLE_UNDO_TEXT))
        assertTrue(helpTopics().first { it.key == HelpTopicKey.STUDY_NOTE }.allText().contains("기본 높이는 70%"))
        assertFalse(writeTools.contains("두 손가락 탭은 실행 취소, 세 손가락 탭은 다시 실행입니다(필기 중에는 동작하지 않음)."))
    }

    @Test
    fun exportGuidanceDistinguishesSavedWorkFromANewPdf() {
        val backup = helpTopics().first { it.key == HelpTopicKey.BACKUP }.allText()
        assertTrue(backup.contains("앱 안에 저장"))
        assertTrue(backup.contains("주석을 포함한 새 PDF"))
    }

    private fun containsKorean(value: String): Boolean =
        value.any { it.code in 0xAC00..0xD7A3 }
}
