package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPolicyTest {

    @Test
    fun minimumTargetIsFortyEightDp() {
        assertEquals(48, MIN_TARGET_DP)
    }

    @Test
    fun accessibleTargetNeverShrinksSmallControls() {
        for (size in listOf(26, 28, 32, 34, 36, 47)) {
            assertEquals(MIN_TARGET_DP, accessibleTarget(size))
        }
        assertEquals(MIN_TARGET_DP, accessibleTarget(0))
        assertEquals(MIN_TARGET_DP, accessibleTarget(-12))
    }

    @Test
    fun accessibleTargetKeepsAlreadyLargeControls() {
        assertEquals(48, accessibleTarget(48))
        assertEquals(56, accessibleTarget(56))
        assertEquals(120, accessibleTarget(120))
    }

    @Test
    fun isAccessibleTargetMatchesTheMinimum() {
        for (size in listOf(26, 28, 32, 34, 36, 47)) {
            assertFalse(isAccessibleTarget(size))
        }
        assertTrue(isAccessibleTarget(48))
        assertTrue(isAccessibleTarget(56))
    }

    @Test
    fun unselectedStateLabelIsJustTheLabel() {
        assertEquals("저장", stateLabel("저장", false))
        assertEquals("이어서 읽기", stateLabel("이어서 읽기", false, "현재 페이지"))
    }

    @Test
    fun selectedStateLabelAppendsTheSuffixAfterAComma() {
        assertEquals("저장, 선택됨", stateLabel("저장", true))
        assertEquals("펜, 현재 도구", stateLabel("펜", true, "현재 도구"))
    }

    @Test
    fun stateLabelHandlesBlankLabelsAndSuffixesGracefully() {
        // No dangling ", 선택됨" for a blank label.
        assertEquals("선택됨", stateLabel("   ", true))
        assertEquals("", stateLabel("", false))
        // A blank suffix must not leave a trailing comma.
        assertEquals("저장", stateLabel("저장", true, "  "))
        assertEquals("", stateLabel("  ", true, ""))
        // Surrounding whitespace is trimmed from both halves.
        assertEquals("저장, 선택됨", stateLabel("  저장 \n", true, " 선택됨 "))
    }

    @Test
    fun colorChoiceLabelUsesTheDefaultSelectedSuffix() {
        assertEquals("빨강", colorChoiceLabel("빨강", false))
        assertEquals("빨강, 선택됨", colorChoiceLabel("빨강", true))
        assertEquals(stateLabel("파랑", true), colorChoiceLabel("파랑", true))
        assertEquals("선택됨", colorChoiceLabel(" ", true))
    }

    @Test
    fun menuLabelCombinesActionAndSubject() {
        assertEquals("삭제: 문서", menuLabel("삭제", "문서"))
        assertEquals("내보내기: 현재 페이지", menuLabel(" 내보내기 ", " 현재 페이지 "))
    }

    @Test
    fun menuLabelFallsBackToActionForBlankSubject() {
        assertEquals("삭제", menuLabel("삭제", ""))
        assertEquals("삭제", menuLabel("삭제", "   "))
        assertEquals("", menuLabel("  ", "  "))
    }

    @Test
    fun sliderLabelCombinesLabelAndValueWithOneSpace() {
        assertEquals("글자 크기 14", sliderLabel("글자 크기", "14"))
        assertEquals("두께 2.5", sliderLabel(" 두께 ", " 2.5 "))
    }

    @Test
    fun sliderLabelOmitsBlankHalves() {
        assertEquals("글자 크기", sliderLabel("글자 크기", "  "))
        assertEquals("14", sliderLabel("", "14"))
        assertEquals("", sliderLabel(" ", "\n"))
    }

    @Test
    fun disabledReasonLabelWrapsReasonInParentheses() {
        assertEquals("내보내기 (권한 필요)", disabledReasonLabel("내보내기", "권한 필요"))
        assertEquals("재생 (오디오 없음)", disabledReasonLabel(" 재생 ", " 오디오 없음 "))
    }

    @Test
    fun disabledReasonLabelOmitsBlankHalves() {
        assertEquals("내보내기", disabledReasonLabel("내보내기", ""))
        assertEquals("권한 필요", disabledReasonLabel("  ", "권한 필요"))
        assertEquals("", disabledReasonLabel("", " "))
    }

    @Test
    fun labelUsesOneLineOnlyWhenItFits() {
        assertEquals(1, labelMaxLines(100, 100))
        assertEquals(1, labelMaxLines(100, 60))
        assertEquals(2, labelMaxLines(100, 101))
        assertEquals(2, labelMaxLines(64, 120))
    }

    @Test
    fun labelMaxLinesClampsAvailableWidthToOne() {
        assertEquals(1, labelMaxLines(0, 1))
        assertEquals(1, labelMaxLines(-40, 1))
        assertEquals(2, labelMaxLines(0, 2))
        assertEquals(2, labelMaxLines(-40, 2))
    }

    private fun estimatedWidthDp(text: String, charDp: Int = 16): Int =
        text.trim().length * charDp

    @Test
    fun koreanLongLabelsWrapOnlyWhenTooWide() {
        val resume = "이어서 읽기"
        val backup = "백업/복원"
        val zoomHint = "두 손가락으로 확대·이동하세요"

        // Short Korean labels fit their natural width on one line...
        assertEquals(1, labelMaxLines(estimatedWidthDp(resume), estimatedWidthDp(resume)))
        assertEquals(1, labelMaxLines(estimatedWidthDp(backup), estimatedWidthDp(backup)))
        // ...but wrap when the chrome gives them less room.
        assertEquals(2, labelMaxLines(estimatedWidthDp(resume) - 16, estimatedWidthDp(resume)))
        assertEquals(2, labelMaxLines(estimatedWidthDp(backup) - 16, estimatedWidthDp(backup)))

        // The long gesture hint needs two lines at narrow widths and one only
        // when the whole string can be shown.
        val hintWidth = estimatedWidthDp(zoomHint)
        assertTrue(hintWidth > 120)
        assertEquals(2, labelMaxLines(120, hintWidth))
        assertEquals(1, labelMaxLines(hintWidth, hintWidth))
    }

    @Test
    fun expandableHeadersAnnounceTheirState() {
        assertEquals("펼쳐짐", expandStateDescription(true))
        assertEquals("접힘", expandStateDescription(false))
    }
}
