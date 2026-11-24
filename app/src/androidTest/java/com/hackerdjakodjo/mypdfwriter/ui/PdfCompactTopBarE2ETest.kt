package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.ui.theme.MyPdfWriterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PdfCompactTopBarE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun titleRowNavigatorAndCountAreFlushAndSameHeight() {
        composeRule.setContent {
            MyPdfWriterTheme {
                PdfCompactTopBar(
                    title = "SIP_강의자료.pdf",
                    pageIndex = 97,
                    pageCount = 654,
                    onBack = {},
                    onPrevious = {},
                    onNext = {},
                    onOpenPages = {},
                    onSearch = {},
                    onOpenMenu = {},
                )
            }
        }

        val row = composeRule.onNodeWithTag(PDF_TOP_BAR_ROW_TAG)
        val navigator = composeRule.onNodeWithTag(PDF_PAGE_NAVIGATOR_TAG)
        val count = composeRule.onNodeWithTag(PDF_PAGE_COUNT_TAG)
        row.assertHeightIsEqualTo(PDF_COMPACT_TOP_BAR_HEIGHT_DP.dp)
        navigator.assertHeightIsEqualTo(PDF_COMPACT_TOP_BAR_HEIGHT_DP.dp)
        count.assertHeightIsEqualTo(PDF_COMPACT_TOP_BAR_HEIGHT_DP.dp)

        val rowBounds = row.getUnclippedBoundsInRoot()
        val navigatorBounds = navigator.getUnclippedBoundsInRoot()
        assertEquals(rowBounds.top, navigatorBounds.top)
        assertEquals(rowBounds.bottom, navigatorBounds.bottom)
    }
}
