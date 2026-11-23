package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.hackerdjakodjo.mypdfwriter.e2e.PdfFixture
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Device-only smoke coverage for the PDF tools workbench. These tests compile
 * with `assembleDebugAndroidTest`; running them needs an emulator/device.
 * They avoid SAF pickers by using the initialSource handoff from MainActivity.
 */
class ToolsScreenE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    companion object {
        @JvmStatic
        @BeforeClass
        fun initPdfBox() {
            PdfFixture.init(ApplicationProvider.getApplicationContext())
        }
    }

    private var backCount = 0
    private var openCount = 0

    private fun render(source: File? = null, name: String = "") {
        backCount = 0
        openCount = 0
        composeRule.setContent {
            MyPdfWriterTheme {
                ToolsScreen(
                    onBack = { backCount++ },
                    onOpen = { openCount++ },
                    initialSource = source,
                    initialName = name,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun allFifteenCardsExistUnderSixOrderedSections() {
        render()
        composeRule.onNodeWithText("PDF 도구").assertExists()
        val cards = listOf(
            "PDF 합치기", "이미지 → PDF", "PDF → 이미지",
            "페이지 추출", "문서 분할",
            "OCR 텍스트", "검색 가능한 PDF", "텍스트 추출",
            "최적화", "문서 정보", "암호 설정", "암호 해제",
            "양식 채우기", "인쇄", "공유",
        )
        assertEquals(15, cards.size)
        cards.forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertExists()
            composeRule.waitForIdle()
        }
        PdfToolSections.order.forEach { section ->
            composeRule.onNodeWithText(section).performScrollTo().assertExists()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun withoutASourceEveryToolIsDisabled() {
        render()
        composeRule.onNodeWithText("도구를 쓰려면 먼저 PDF를 여세요").assertExists()
        listOf("페이지 추출", "문서 분할", "최적화", "문서 정보", "암호 설정", "양식 채우기")
            .forEach { label ->
                composeRule.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
                composeRule.waitForIdle()
            }
    }

    @Test
    fun initialSourceEnablesToolsAndRangeDialogOpensAndCancels() {
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "tools-input.pdf")
        PdfFixture.writeFixture(file)
        render(source = file, name = "lecture.pdf")

        composeRule.waitUntil("source selected", PdfFixture.DEFAULT_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("3쪽 · 선택한 파일")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("페이지 추출").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("계속").assertExists()
        composeRule.onNodeWithText("취소").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("계속").assertDoesNotExist()
    }

    @Test
    fun passwordDialogsOpenAndCancelWithoutProducingOutput() {
        val file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "tools-lock.pdf")
        PdfFixture.writeFixture(file)
        render(source = file, name = "secure.pdf")
        composeRule.waitUntil("source selected", PdfFixture.DEFAULT_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("3쪽 · 선택한 파일")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("암호 설정").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("열기 암호").assertExists()
        composeRule.onNodeWithText("소유자 암호 (비우면 동일)").assertExists()
        composeRule.onNodeWithText("취소").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("암호 해제").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("PDF 암호").assertExists()
        composeRule.onNodeWithText("취소").performClick()
        composeRule.waitForIdle()
        assertEquals(0, openCount)
    }

    @Test
    fun topBarBackAndSystemBackBothCallOnBack() {
        render()
        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backCount)
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
        assertEquals(2, backCount)
        assertTrue(openCount == 0)
    }
}
