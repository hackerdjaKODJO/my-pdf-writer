package com.hackerdjakodjo.mypdfwriter.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.hackerdjakodjo.mypdfwriter.data.PenStyle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The realistic instrument illustrations are decorative Canvas drawings: the
 * chip must keep the exact spoken label and selected state so the PDF and note
 * style-sheet pickers remain accessible after the visual change.
 */
class PenIllustrationE2ETest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun illustratedChipsKeepLabelsSelectionAndClicks() {
        var selected by mutableStateOf(PenStyle.FOUNTAIN)
        val picked = mutableListOf<PenStyle>()
        composeRule.setContent {
            MaterialTheme {
                Row {
                    PenStyle.entries.forEach { style ->
                        PenStyleGlyph(
                            style = style,
                            selected = selected == style,
                            color = 0xFF1B1C19.toInt(),
                            onClick = {
                                picked += style
                                selected = style
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        listOf("볼펜", "만년필, 선택됨", "붓", "연필").forEach { label ->
            composeRule.onNodeWithContentDescription(label).assertExists()
        }

        composeRule.onNodeWithContentDescription("연필").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(PenStyle.PENCIL), picked)
        composeRule.onNodeWithContentDescription("연필, 선택됨").assertExists()
    }
}
