package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure JVM checks that the offline HTML guide matches the shipped 0.8.58 app
 * and leads with a short quick start. No network or Android runtime required.
 */
class UsageGuideContentTest {

    @Test
    fun guideHasNoStaleVocabulary() {
        val guide = usageGuide()
        listOf(
            "고급",
            "스터디",
            "꺼져 있는지",
            "읽기 · 페이지 · 보기 · 내보내기",
            "텍스트 선택 · 주석",
            "학습 · 위치 · 보기 · 내보내기 · 기타",
            "5분 빠른 시작",
            "안내 5장",
            "홈의 <b>백업/복원</b>",
            "PDF 상단의 <b>노트</b>", "보기 모드", "Google 번역", "· 텍스트 · 노트", "확대 메뉴는 없고", "1x로 돌아갑니다", "0.8.45", "0.8.48", "0.8.49", "0.8.50", "0.8.51", "0.8.52", "0.8.53", "0.8.54", "0.8.55", "0.8.56", "0.8.57",
        ).forEach { stale ->
            assertFalse("guide must not use stale '$stale'", guide.contains(stale))
        }
    }

    @Test
    fun guideUsesCurrentMenuGroupsDockAndHideState() {
        val guide = usageGuide()
        assertTrue(guide.contains("읽기 · 필기 · 담기 · 설정"))
        assertTrue(guide.contains("보기 · 형광펜 · 펜 · 지우개 · 텍스트 선택 · 노트"))
        assertTrue(guide.contains("켜져 있는지"))
        assertTrue(guide.contains("복습 카드"))
        assertTrue(guide.contains("하단 도구 독의"))
        assertTrue(guide.contains("설정 → PDF 내보내기"))
        assertTrue(guide.contains("± 버튼"))
        assertTrue(guide.contains("스와이프"))
        assertTrue(guide.contains("최근 수정순"))
        assertTrue(guide.contains("보기 도구"))
    }

    @Test
    fun guideMentionsReadingConveniences() {
        val guide = usageGuide()
        listOf("Locations", "문서 탭", "자동 숨김", "여백 자르기")
            .forEach { term -> assertTrue("guide must mention '$term'", guide.contains(term)) }
    }

    @Test
    fun guideOpensWithAFiveStepQuickStart() {
        val guide = usageGuide()
        val mainAt = guide.indexOf("<main>")
        val quickAt = guide.indexOf("<section id=\"quickstart\"")
        assertTrue("quick start must be the first section in <main>", mainAt in 0..quickAt)
        assertTrue(
            "quick start must precede onboarding",
            quickAt < guide.indexOf("<section id=\"onboarding\""),
        )
        val section = sectionById(guide, "quickstart")
        assertEquals(5, section.countOccurrences("<li>"))
        assertEquals(5, section.countOccurrences("</li>"))
    }

    private fun sectionById(guide: String, id: String): String {
        val start = guide.indexOf("<section id=\"$id\"")
        assertTrue("missing section #$id", start >= 0)
        val end = guide.indexOf("</section>", start)
        assertTrue("unterminated section #$id", end > start)
        return guide.substring(start, end)
    }

    private fun String.countOccurrences(token: String): Int = split(token).size - 1

    /**
     * Gradle runs JVM tests with the module directory as the working directory;
     * walk upward so the repo-root docs folder is found either way.
     */
    private fun usageGuide(): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (true) {
            val candidate = File(dir, "docs/usage-guide.html")
            if (candidate.isFile) return candidate.readText()
            val parent = dir.parentFile ?: error("docs/usage-guide.html not found from $dir")
            dir = parent
        }
    }
}
