package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OrganizeScreenE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun bulkDeleteRequiresConfirmationAndCancelIsSafe() {
        var deleted: Set<Int>? = null
        composeRule.setContent {
            OrganizeScreen(
                docName = "강의.pdf",
                pages = listOf(PageSpec(0), PageSpec(1), PageSpec(2)),
                selected = setOf(0, 1),
                loadThumbnail = { Bitmap.createBitmap(40, 56, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onSelectionChange = {},
                onRotate = { _, _ -> },
                onDelete = { deleted = it },
                onDuplicate = {},
                onMove = { _, _ -> },
                onInsertBlank = {},
                onExtract = {},
                onMerge = {},
                onSplit = {},
                onExport = {},
            )
        }

        composeRule.onNodeWithText("2개 페이지 선택됨").assertExists()
        composeRule.onNodeWithText("삭제").performClick()
        composeRule.onNodeWithText("2개 페이지 삭제").assertExists()
        composeRule.onNodeWithText("취소").performClick()
        assertEquals(null, deleted)
    }
}
