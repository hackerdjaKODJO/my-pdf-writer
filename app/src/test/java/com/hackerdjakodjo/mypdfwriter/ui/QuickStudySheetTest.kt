package com.hackerdjakodjo.mypdfwriter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStudySheetTest {

    @Test
    fun quickStudyHasExactlyFourStepsInStudentOrder() {
        assertEquals(
            listOf("PDF 열기", "형광펜으로 표시", "노트에 담기", "복습하기"),
            QuickStudyPolicy.steps.map { it.title },
        )
    }

    @Test
    fun onlyOpenPdfStepUsesExistingCallbacks() {
        assertTrue(QuickStudyPolicy.steps[0].usesExistingPdfActions)
        assertFalse(QuickStudyPolicy.steps[1].usesExistingPdfActions)
        assertFalse(QuickStudyPolicy.steps[2].usesExistingPdfActions)
        assertFalse(QuickStudyPolicy.steps[3].usesExistingPdfActions)
    }

    @Test
    fun everyStepHasHintTextAndNoToolWiring() {
        QuickStudyPolicy.steps.forEach { step ->
            assertTrue("step '${step.title}' must show a hint", step.hint.isNotBlank())
        }
    }

    @Test
    fun progressRunsFromOneOutOfFourToFourOutOfFour() {
        assertEquals(
            listOf("1/4", "2/4", "3/4", "4/4"),
            (0..3).map { QuickStudyPolicy.progress(it) },
        )
    }

    @Test
    fun nextAdvancesUntilTheLastStep() {
        assertEquals(1, QuickStudyPolicy.nextIndex(0))
        assertEquals(2, QuickStudyPolicy.nextIndex(1))
        assertEquals(3, QuickStudyPolicy.nextIndex(2))
        assertNull(QuickStudyPolicy.nextIndex(3))
    }

    @Test
    fun primaryControlChangesFromNextToCompleteOnLastStep() {
        assertEquals("다음", QuickStudyPolicy.primaryLabel(0))
        assertEquals("다음", QuickStudyPolicy.primaryLabel(2))
        assertEquals("완료", QuickStudyPolicy.primaryLabel(3))
        assertTrue(QuickStudyPolicy.isLast(3))
        assertFalse(QuickStudyPolicy.isLast(2))
    }

    @Test
    fun outOfRangeIndicesAreClampedInsteadOfCrashing() {
        assertEquals("1/4", QuickStudyPolicy.progress(-5))
        assertEquals("4/4", QuickStudyPolicy.progress(99))
        assertEquals("PDF 열기", QuickStudyPolicy.step(-5).title)
        assertEquals("복습하기", QuickStudyPolicy.step(99).title)
        assertNull(QuickStudyPolicy.nextIndex(99))
    }
}
