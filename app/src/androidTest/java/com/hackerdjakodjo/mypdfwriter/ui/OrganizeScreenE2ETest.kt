package com.hackerdjakodjo.mypdfwriter.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.hackerdjakodjo.mypdfwriter.data.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OrganizeScreenE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun undoRedoAreVisibleAccessibleAndDisabledWithoutHistory() {
        setOrganizeContent()

        assertHistoryAction("organize_undo", "실행 취소", enabled = false)
        assertHistoryAction("organize_redo", "다시 실행", enabled = false)
    }

    @Test
    fun undoRedoInvokeCallbacksAndReflectHistoryAvailability() {
        var canUndo by mutableStateOf(true)
        var canRedo by mutableStateOf(false)
        var undoInvoked = false
        var redoInvoked = false

        composeRule.setContent {
            OrganizeScreen(
                docName = "강의.pdf",
                pages = listOf(PageSpec(0), PageSpec(1), PageSpec(2)),
                selected = emptySet(),
                loadThumbnail = { Bitmap.createBitmap(40, 56, Bitmap.Config.ARGB_8888) },
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = {
                    undoInvoked = true
                    canUndo = false
                    canRedo = true
                },
                onRedo = {
                    redoInvoked = true
                    canUndo = true
                    canRedo = false
                },
                onBack = {},
                onSelectionChange = {},
                onRotate = { _, _ -> },
                onDelete = {},
                onDuplicate = {},
                onMove = { _, _ -> },
                onInsertBlank = {},
                onExtract = {},
                onMerge = {},
                onSplit = {},
                onExport = {},
            )
        }

        assertHistoryAction("organize_undo", "실행 취소", enabled = true)
        assertHistoryAction("organize_redo", "다시 실행", enabled = false)

        composeRule.onNodeWithTag("organize_undo").performClick()
        composeRule.runOnIdle { assertTrue(undoInvoked) }
        assertHistoryAction("organize_undo", "실행 취소", enabled = false)
        assertHistoryAction("organize_redo", "다시 실행", enabled = true)

        composeRule.onNodeWithTag("organize_redo").performClick()
        composeRule.runOnIdle { assertTrue(redoInvoked) }
        assertHistoryAction("organize_undo", "실행 취소", enabled = true)
        assertHistoryAction("organize_redo", "다시 실행", enabled = false)
    }

    @Test
    fun bulkDeleteRequiresTruthfulConfirmationAndCancelIsSafe() {
        var deleted: Set<Int>? = null
        setOrganizeContent(selected = setOf(0, 1), onDelete = { deleted = it })

        composeRule.onNodeWithText("2개 페이지 선택됨").assertExists()
        composeRule.onNodeWithText("삭제").performClick()
        composeRule.onNodeWithText("2개 페이지 삭제").assertExists()
        composeRule.onNodeWithText(
            "선택한 페이지가 문서에서 제거됩니다. 실행 취소로 되돌릴 수 있습니다.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "선택한 페이지가 문서에서 제거됩니다. 이 작업은 실행 취소로 되돌릴 수 없습니다.",
        ).assertDoesNotExist()
        composeRule.onNodeWithText("취소").performClick()
        assertNull(deleted)

        // Confirmation remains required after cancel, then applies to exactly
        // the selected pages without changing the caller's selection contract.
        composeRule.onNodeWithText("삭제").performClick()
        composeRule.onNodeWithTag("organize_delete_confirm").performClick()
        assertEquals(setOf(0, 1), deleted)
    }

    private fun setOrganizeContent(
        selected: Set<Int> = emptySet(),
        onDelete: (Set<Int>) -> Unit = {},
    ) {
        composeRule.setContent {
            OrganizeScreen(
                docName = "강의.pdf",
                pages = listOf(PageSpec(0), PageSpec(1), PageSpec(2)),
                selected = selected,
                loadThumbnail = { Bitmap.createBitmap(40, 56, Bitmap.Config.ARGB_8888) },
                onBack = {},
                onSelectionChange = {},
                onRotate = { _, _ -> },
                onDelete = onDelete,
                onDuplicate = {},
                onMove = { _, _ -> },
                onInsertBlank = {},
                onExtract = {},
                onMerge = {},
                onSplit = {},
                onExport = {},
            )
        }
    }

    private fun assertHistoryAction(tag: String, label: String, enabled: Boolean) {
        val action = composeRule.onNodeWithTag(tag)
        action.assertExists()
            .assertIsDisplayed()
            .assertContentDescriptionEquals(label)
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        if (enabled) {
            action.assertIsEnabled()
        } else {
            action.assertIsNotEnabled()
        }
    }
}
