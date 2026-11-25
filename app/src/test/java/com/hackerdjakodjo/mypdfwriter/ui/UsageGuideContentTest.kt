package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure JVM checks that the offline HTML guide matches the shipped 0.8.89 app
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
            "PDF 상단의 <b>노트</b>", "보기 모드", "Google 번역", "· 텍스트 · 노트", "확대 메뉴는 없고", "1x로 돌아갑니다", "0.8.45", "0.8.48", "0.8.49", "0.8.50", "0.8.51", "0.8.52", "0.8.53", "0.8.54", "0.8.55", "0.8.56", "0.8.57", "0.8.58", "0.8.59", "0.8.60", "0.8.61", "0.8.62", "0.8.63", "0.8.64", "0.8.65", "0.8.66", "0.8.67", "0.8.68",
            "하단 페이지 바", "작은 페이지 탐색", "안내 3장이 자동으로", "설정 → 사용법/도움말", "패널에 추가",
        ).forEach { stale ->
            assertFalse("guide must not use stale '$stale'", guide.contains(stale))
        }
    }

    @Test
    fun guideUsesCurrentMenuGroupsDockAndHideState() {
        val guide = usageGuide()
        assertTrue(guide.contains("보기 · 주석 · 노트 · 파일·설정"))
        assertTrue(guide.contains("보기 · 형광펜 · 펜 · 지우개 · 텍스트 선택 · 노트"))
        assertTrue(guide.contains("켜져 있는지"))
        assertTrue(guide.contains("노트 메뉴 → 카드"))
        assertTrue(guide.contains("하단 도구 독의"))
        assertTrue(guide.contains("PDF 내보내기"))
        assertTrue(guide.contains("위쪽 페이지 이동"))
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

    @Test
    fun guideExplainsOptionalTutorialAndCurrentNoteChoices() {
        val guide = usageGuide()
        assertTrue(guide.contains("처음 사용하나요? 60초 안내"))
        assertTrue(guide.contains("자동으로 열리지 않습니다"))
        assertTrue(guide.contains("튜토리얼 다시 보기"))
        listOf(
            "학습 노트", "PDF와 연결된 노트 패널", "페이지 메모",
            "한 PDF 페이지에 남기는 텍스트 메모", "포스트잇",
            "페이지 위에 자유롭게 배치하는 카드 메모", "노트에 담기",
        ).forEach { term -> assertTrue("guide must explain '$term'", guide.contains(term)) }
    }

    @Test
    fun guideSeparatesSavingWorkFromPdfExport() {
        val guide = usageGuide()
        assertTrue(guide.contains("저장과 PDF 내보내기는 다릅니다"))
        assertTrue(guide.contains("앱 안의 작업 데이터로 저장"))
        assertTrue(guide.contains("주석을 포함한 <b>새 PDF</b>"))
    }

    @Test
    fun guideTargetsVersion089AndCurrentPdfGestures() {
        val guide = usageGuide()
        assertTrue(guide.contains("<title>My PDF Writer 0.8.89 사용법</title>"))
        assertTrue(guide.contains("버전 0.8.89"))
        assertTrue(guide.contains("보기</b> 도구를 선택한 뒤"))
        assertTrue(guide.contains("<b>노트 편집기에서만</b> 두 손가락"))
        assertTrue(guide.contains("도구 독 위의 스타일"))
        assertTrue(guide.contains("도구 독 위의 실행 취소·다시 실행"))
        assertTrue(guide.contains("전체 도구 보기"))
        assertTrue(guide.contains("70%입니다"))
        assertFalse(guide.contains("85%입니다"))
        assertFalse(guide.contains("0.8.68"))
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
